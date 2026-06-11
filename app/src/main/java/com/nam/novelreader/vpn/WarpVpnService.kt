package com.nam.novelreader.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer

/**
 * WarpVpnService — DNS-over-VPN với Cloudflare 1.1.1.1
 *
 * Tạo VPN tunnel:
 * - Route DNS qua 1.1.1.1 (bypass DNS blocking)
 * - Auto-reconnect để thay đổi session Cloudflare
 */
class WarpVpnService : VpnService() {

    companion object {
        const val TAG = "WarpVpnService"
        const val NOTIFICATION_ID = 9001
        const val CHANNEL_ID = "warp_vpn_channel"

        const val ACTION_START = "com.nam.novelreader.vpn.START"
        const val ACTION_STOP = "com.nam.novelreader.vpn.STOP"
        const val ACTION_RECONNECT = "com.nam.novelreader.vpn.RECONNECT"

        const val BROADCAST_STATE = "com.nam.novelreader.vpn.STATE"
        const val EXTRA_CONNECTED = "connected"
        const val EXTRA_IP = "ip"

        @Volatile var isRunning = false
            private set
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var dnsJob: Job? = null

    private val DNS_PRIMARY = "1.1.1.1"
    private val DNS_SECONDARY = "1.0.0.1"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                return START_NOT_STICKY
            }
            ACTION_RECONNECT -> {
                serviceScope.launch { reconnect() }
                return START_STICKY
            }
            else -> startVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        try {
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, buildNotification("🔒 WARP đang kết nối..."))

            val builder = Builder()
                .setSession("Cloudflare WARP")
                .addAddress("10.2.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer(DNS_PRIMARY)
                .addDnsServer(DNS_SECONDARY)
                .setMtu(1280)
                .addDisallowedApplication(packageName)

            vpnInterface = builder.establish()

            if (vpnInterface == null) {
                Log.e(TAG, "Failed to establish VPN interface")
                stopSelf()
                return
            }

            isRunning = true
            broadcastState(true, DNS_PRIMARY)
            updateNotification("🟢 WARP đang hoạt động — DNS: $DNS_PRIMARY")
            startDnsProxy()
            Log.i(TAG, "WARP VPN started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting VPN: ${e.message}")
            stopSelf()
        }
    }

    private fun startDnsProxy() {
        val pfd = vpnInterface ?: return
        dnsJob = serviceScope.launch {
            val inputStream = FileInputStream(pfd.fileDescriptor)
            val outputStream = FileOutputStream(pfd.fileDescriptor)
            val buffer = ByteArray(32767)

            while (isActive && isRunning) {
                try {
                    val length = inputStream.read(buffer)
                    if (length <= 0) continue
                    if (length < 28) continue // Too short for IP+UDP+DNS

                    // Check IPv4 (version = 4)
                    val ipVersion = (buffer[0].toInt() and 0xFF) shr 4
                    if (ipVersion != 4) continue

                    // Check UDP protocol (17)
                    val protocol = buffer[9].toInt() and 0xFF
                    if (protocol != 17) continue

                    // Check destination port 53 (DNS)
                    val destPort = ((buffer[22].toInt() and 0xFF) shl 8) or (buffer[23].toInt() and 0xFF)
                    if (destPort != 53) continue

                    // Extract DNS payload (IP=20 bytes, UDP=8 bytes)
                    val dnsPayload = buffer.copyOfRange(28, length)
                    val response = forwardDns(dnsPayload) ?: continue

                    val responsePacket = buildResponsePacket(buffer, length, response)
                    outputStream.write(responsePacket)
                } catch (e: Exception) {
                    if (isActive) Log.w(TAG, "DNS proxy: ${e.message}")
                }
            }
        }
    }

    private suspend fun forwardDns(query: ByteArray): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val socket = DatagramSocket()
            socket.soTimeout = 3000
            protect(socket)
            val addr = InetAddress.getByName(DNS_PRIMARY)
            socket.send(DatagramPacket(query, query.size, addr, 53))
            val buf = ByteArray(512)
            val recv = DatagramPacket(buf, buf.size)
            socket.receive(recv)
            socket.close()
            buf.copyOf(recv.length)
        } catch (e: Exception) {
            null
        }
    }

    private fun buildResponsePacket(orig: ByteArray, origLen: Int, dnsResp: ByteArray): ByteArray {
        val total = 28 + dnsResp.size
        val pkt = ByteArray(total)

        // IPv4 header
        pkt[0] = 0x45.toByte()
        pkt[1] = 0
        pkt[2] = (total shr 8).toByte()
        pkt[3] = (total and 0xFF).toByte()
        pkt[4] = 0; pkt[5] = 0
        pkt[6] = 0; pkt[7] = 0
        pkt[8] = 64
        pkt[9] = 17 // UDP
        // Swap src/dst IPs
        System.arraycopy(orig, 16, pkt, 12, 4)
        System.arraycopy(orig, 12, pkt, 16, 4)

        // UDP header - swap ports
        pkt[20] = orig[22]; pkt[21] = orig[23]
        pkt[22] = orig[20]; pkt[23] = orig[21]
        val udpLen = 8 + dnsResp.size
        pkt[24] = (udpLen shr 8).toByte()
        pkt[25] = (udpLen and 0xFF).toByte()
        pkt[26] = 0; pkt[27] = 0

        // DNS payload
        System.arraycopy(dnsResp, 0, pkt, 28, dnsResp.size)

        // IP checksum
        var cs = 0
        var i = 0
        while (i < 20) {
            cs += ((pkt[i].toInt() and 0xFF) shl 8) or (pkt[i + 1].toInt() and 0xFF)
            i += 2
        }
        while ((cs shr 16) != 0) cs = (cs and 0xFFFF) + (cs shr 16)
        cs = cs.inv() and 0xFFFF
        pkt[10] = (cs shr 8).toByte()
        pkt[11] = (cs and 0xFF).toByte()

        return pkt
    }

    private suspend fun reconnect() {
        Log.i(TAG, "Reconnecting WARP...")
        dnsJob?.cancel()
        try { vpnInterface?.close() } catch (_: Exception) {}
        delay(800)
        startVpn()
    }

    private fun stopVpn() {
        isRunning = false
        dnsJob?.cancel()
        try { vpnInterface?.close() } catch (_: Exception) {}
        vpnInterface = null
        broadcastState(false, "")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun broadcastState(connected: Boolean, ip: String) {
        sendBroadcast(Intent(BROADCAST_STATE).apply {
            putExtra(EXTRA_CONNECTED, connected)
            putExtra(EXTRA_IP, ip)
            setPackage(packageName)
        })
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Cloudflare WARP", NotificationManager.IMPORTANCE_LOW)
            ch.setShowBadge(false)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(text: String): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, WarpVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, CHANNEL_ID)
        else @Suppress("DEPRECATION") Notification.Builder(this)

        return b.setContentTitle("Cloudflare WARP")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .addAction(Notification.Action.Builder(null, "Tắt", stopIntent).build())
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        dnsJob?.cancel()
        serviceScope.cancel()
    }

    override fun onRevoke() {
        super.onRevoke()
        stopVpn()
    }
}
