package com.nam.novelreader.vpn

import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

/**
 * WarpManager — quản lý kết nối Cloudflare WARP
 * Tích hợp API WARP để lấy WireGuard credentials và đổi IP
 */
object WarpManager {

    private const val TAG = "WarpManager"
    private const val API_BASE = "https://api.cloudflareclient.com/v0a1922"
    private const val PREFS_NAME = "warp_prefs"

    // WARP endpoint của Cloudflare
    val WARP_ENDPOINT = "engage.cloudflareclient.com:2408"
    val WARP_DNS1 = "1.1.1.1"
    val WARP_DNS2 = "1.0.0.1"

    data class WarpConfig(
        val privateKey: String,
        val publicKey: String,
        val clientId: String,
        val deviceId: String,
        val peerPublicKey: String,
        val clientIpv4: String,
        val endpoint: String
    )

    private fun generateKeyPair(): Pair<String, String> {
        // Sinh WireGuard keypair (X25519 via Diffie-Hellman)
        val keyGen = KeyPairGenerator.getInstance("EC")
        keyGen.initialize(ECGenParameterSpec("secp256r1"))
        val kp = keyGen.generateKeyPair()
        val privBytes = kp.private.encoded.takeLast(32).toByteArray()
        val pubBytes = kp.public.encoded.takeLast(32).toByteArray()
        // Clamp private key per WireGuard spec
        privBytes[0] = (privBytes[0].toInt() and 248.toByte().toInt()).toByte()
        privBytes[31] = ((privBytes[31].toInt() and 127) or 64).toByte()
        return Pair(
            Base64.encodeToString(privBytes, Base64.NO_WRAP),
            Base64.encodeToString(pubBytes, Base64.NO_WRAP)
        )
    }

    /**
     * Đăng ký thiết bị mới với WARP API → nhận IP mới
     */
    suspend fun registerDevice(): WarpConfig? = withContext(Dispatchers.IO) {
        try {
            val (privKey, pubKey) = generateKeyPair()

            val payload = JSONObject().apply {
                put("key", pubKey)
                put("install_id", java.util.UUID.randomUUID().toString())
                put("fcm_token", "")
                put("tos", "2023-11-01T00:00:00.000Z")
                put("type", "Android")
                put("model", android.os.Build.MODEL)
                put("locale", "vi-VN")
            }

            val url = URL("$API_BASE/reg")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "okhttp/3.12.1")
            conn.doOutput = true
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000

            conn.outputStream.use { os ->
                os.write(payload.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = conn.responseCode
            if (responseCode != 200 && responseCode != 201) {
                Log.w(TAG, "WARP register failed: $responseCode")
                return@withContext null
            }

            val response = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(response)
            val config = json.getJSONObject("config")
            val peers = config.getJSONArray("peers")
            val peer = peers.getJSONObject(0)
            val peerPubKey = peer.getString("public_key")
            val endpoint = peer.getJSONObject("endpoint").getString("host")
            val clientIface = config.getJSONObject("interface")
            val clientIp = clientIface.getJSONArray("addresses")
                .getString(0).substringBefore("/")
            val deviceId = json.getString("id")

            WarpConfig(
                privateKey = privKey,
                publicKey = pubKey,
                clientId = json.optString("token", ""),
                deviceId = deviceId,
                peerPublicKey = peerPubKey,
                clientIpv4 = clientIp,
                endpoint = endpoint
            )
        } catch (e: Exception) {
            Log.e(TAG, "WARP register error: ${e.message}")
            null
        }
    }

    fun saveConfig(context: Context, config: WarpConfig) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putString("private_key", config.privateKey)
            putString("public_key", config.publicKey)
            putString("client_id", config.clientId)
            putString("device_id", config.deviceId)
            putString("peer_pub_key", config.peerPublicKey)
            putString("client_ip", config.clientIpv4)
            putString("endpoint", config.endpoint)
            apply()
        }
    }

    fun loadConfig(context: Context): WarpConfig? {
        val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val privKey = p.getString("private_key", null) ?: return null
        return WarpConfig(
            privateKey = privKey,
            publicKey = p.getString("public_key", "") ?: "",
            clientId = p.getString("client_id", "") ?: "",
            deviceId = p.getString("device_id", "") ?: "",
            peerPublicKey = p.getString("peer_pub_key", "") ?: "",
            clientIpv4 = p.getString("client_ip", "172.16.0.2") ?: "172.16.0.2",
            endpoint = p.getString("endpoint", WARP_ENDPOINT) ?: WARP_ENDPOINT
        )
    }

    fun clearConfig(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
