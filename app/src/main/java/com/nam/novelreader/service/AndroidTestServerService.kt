package com.nam.novelreader.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.nam.novelreader.MainActivity
import com.nam.novelreader.data.local.dao.ExtensionDao
import com.nam.novelreader.data.local.entity.ExtensionEntity
import com.nam.novelreader.extension.loader.ExtensionLoader
import com.nam.novelreader.extension.model.ExtensionResult
import com.nam.novelreader.extension.model.LoadedExtension
import com.nam.novelreader.extension.model.PluginJson
import com.nam.novelreader.extension.runtime.VBookJsExtensionRunner
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import javax.inject.Inject

@AndroidEntryPoint
class AndroidTestServerService : Service() {

    companion object {
        private const val TAG = "TestServer"
        const val CHANNEL_ID = "developer_mode_channel"
        const val NOTIFICATION_ID = 1002

        const val ACTION_START = "com.nam.novelreader.action.START_SERVER"
        const val ACTION_STOP = "com.nam.novelreader.action.STOP_SERVER"
        const val PORT = 8080

        @Volatile
        var isServiceRunning = false
            private set
    }

    @Inject
    lateinit var extensionLoader: ExtensionLoader

    @Inject
    lateinit var jsExtensionRunner: VBookJsExtensionRunner

    @Inject
    lateinit var extensionDao: ExtensionDao

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    
    private var serverSocket: ServerSocket? = null
    private var isRunning = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startServer()
            ACTION_STOP -> stopServer()
        }
        return START_NOT_STICKY
    }

    private fun startServer() {
        if (isRunning) return
        isRunning = true
        isServiceRunning = true

        ensureForeground()
        Log.d(TAG, "Starting developer test server on port $PORT...")

        serviceScope.launch {
            try {
                serverSocket = ServerSocket(PORT).apply {
                    reuseAddress = true
                }
                while (isRunning) {
                    val socket = try {
                        serverSocket?.accept()
                    } catch (e: Exception) {
                        null
                    } ?: break

                    launch {
                        handleClient(socket)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server socket error", e)
            } finally {
                stopSelf()
            }
        }
    }

    private fun stopServer() {
        if (!isRunning) return
        isRunning = false
        isServiceRunning = false
        Log.d(TAG, "Stopping developer test server...")
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // ignore
        }
        serverSocket = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun handleClient(socket: Socket) = withContext(Dispatchers.IO) {
        try {
            val inputStream = socket.getInputStream()
            
            // Đọc header bằng cách đọc từng byte cho tới khi gặp \r\n\r\n hoặc \n\n
            val headerStream = java.io.ByteArrayOutputStream()
            var b: Int
            while (inputStream.read().also { b = it } != -1) {
                headerStream.write(b)
                val data = headerStream.toByteArray()
                if (data.size >= 4 && 
                    data[data.size - 4] == '\r'.code.toByte() && data[data.size - 3] == '\n'.code.toByte() &&
                    data[data.size - 2] == '\r'.code.toByte() && data[data.size - 1] == '\n'.code.toByte()) {
                    break
                }
                if (data.size >= 2 && 
                    data[data.size - 2] == '\n'.code.toByte() && data[data.size - 1] == '\n'.code.toByte()) {
                    break
                }
            }
            
            val headerStr = headerStream.toString("UTF-8")
            val headerLines = headerStr.split(Regex("\r?\n"))
            if (headerLines.isEmpty() || headerLines[0].isBlank()) return@withContext
            
            val firstLine = headerLines[0]
            val parts = firstLine.split(" ")
            if (parts.size < 2) return@withContext
            val method = parts[0]
            val path = parts[1]
            
            var contentLength = 0
            for (i in 1 until headerLines.size) {
                val line = headerLines[i]
                if (line.lowercase().startsWith("content-length:")) {
                    contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                }
            }
            
            // Đọc body thô dạng bytes để không bị lệch kích thước ký tự UTF-8
            val body = if (contentLength > 0) {
                val bodyBytes = ByteArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val r = inputStream.read(bodyBytes, read, contentLength - read)
                    if (r == -1) break
                    read += r
                }
                String(bodyBytes, Charsets.UTF_8)
            } else {
                ""
            }

            Log.d(TAG, "Request: $method $path, body length: ${body.length}")

            when {
                (path == "/connect" || path == "/ping") && method == "GET" -> {
                    sendResponse(socket, 200, "{\"success\":true}")
                }
                (path == "/extension/install" || path == "/install") && method == "POST" -> {
                    val res = handleInstall(body)
                    sendResponse(socket, 200, res)
                }
                (path == "/extension/test" || path == "/test") && method == "POST" -> {
                    val res = handleTest(body)
                    sendResponse(socket, 200, res)
                }
                else -> {
                    sendResponse(socket, 404, "{\"error\":\"Not Found\"}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling client connection", e)
        } finally {
            try {
                socket.close()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private suspend fun handleInstall(body: String): String = withContext(Dispatchers.IO) {
        try {
            val jsonPayload = JSONObject(body)
            val pluginStr = jsonPayload.getString("plugin")
            val srcStr = jsonPayload.getString("src")
            val iconStr = jsonPayload.optString("icon")

            val pluginObj = JSONObject(pluginStr)
            val metadata = pluginObj.getJSONObject("metadata")
            val name = metadata.getString("name")
            val author = metadata.optString("author", "Unknown")
            val version = metadata.optInt("version", 1)
            val source = metadata.optString("source", "")
            val type = metadata.optString("type", "novel")
            val locale = metadata.optString("locale", "vi_VN")
            val description = metadata.optString("description", "")

            val extId = name.toSlug()
            val extDir = File(File(filesDir, "extensions"), extId)
            extDir.mkdirs()

            // 1. Write plugin.json
            File(extDir, "plugin.json").writeText(pluginStr)

            // 2. Write icon.png
            if (iconStr.contains("base64,")) {
                val base64Data = iconStr.substringAfter("base64,")
                val imageBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                File(extDir, "icon.png").writeBytes(imageBytes)
            }

            // 3. Write source files
            val srcDir = File(extDir, "src")
            srcDir.mkdirs()
            val srcJson = JSONObject(srcStr)
            val keys = srcJson.keys()
            while (keys.hasNext()) {
                val fileName = keys.next()
                val fileContent = srcJson.getString(fileName)
                val outFile = File(srcDir, fileName)
                outFile.parentFile?.mkdirs()
                outFile.writeText(fileContent)
            }

            // 4. Save to db
            extensionDao.insert(
                ExtensionEntity(
                    id = extId,
                    name = name,
                    author = author,
                    version = version,
                    source = source,
                    type = type,
                    locale = locale,
                    description = description,
                    localPath = extDir.absolutePath,
                    iconPath = if (File(extDir, "icon.png").exists()) File(extDir, "icon.png").absolutePath else null,
                    isInstalled = true,
                    isEnabled = true,
                )
            )

            // 5. Invalidate cache
            extensionLoader.clearCache(extId)
            jsExtensionRunner.clearCache(extId)

            Log.d(TAG, "Successfully installed debug extension: $name")
            "{\"success\":true,\"code\":200}"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install debug extension", e)
            "{\"success\":false,\"message\":\"${e.message}\"}"
        }
    }

    private suspend fun handleTest(body: String): String = withContext(Dispatchers.IO) {
        val tempDir = File(cacheDir, "debug_extension_${System.currentTimeMillis()}_${java.util.UUID.randomUUID()}")
        try {
            val jsonPayload = JSONObject(body)
            val pluginStr = jsonPayload.getString("plugin")
            val srcStr = jsonPayload.getString("src")
            val iconStr = jsonPayload.optString("icon")
            val inputStr = jsonPayload.optString("input")

            tempDir.mkdirs()

            // 1. Write plugin.json
            File(tempDir, "plugin.json").writeText(pluginStr)

            // 2. Write icon.png
            if (iconStr.contains("base64,")) {
                val base64Data = iconStr.substringAfter("base64,")
                val imageBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                File(tempDir, "icon.png").writeBytes(imageBytes)
            }

            // 3. Write source files
            val srcDir = File(tempDir, "src")
            srcDir.mkdirs()
            val srcJson = JSONObject(srcStr)
            val keys = srcJson.keys()
            while (keys.hasNext()) {
                val fileName = keys.next()
                val fileContent = srcJson.getString(fileName)
                val outFile = File(srcDir, fileName)
                outFile.parentFile?.mkdirs()
                outFile.writeText(fileContent)
            }

            // 4. Parse input params
            val inputObj = JSONObject(inputStr)
            val scriptName = inputObj.getString("script")
            val varargArray = inputObj.getJSONArray("vararg")
            val args = Array(varargArray.length()) { i -> varargArray.getString(i) }

            // 5. Execute
            // FIX: Override metadata.name with tempDir unique name so LoadedExtension.id
            // becomes the sandbox ID (e.g. "debug-extension-...") instead of the real
            // extension ID. This prevents clearCache() from evicting the real extension's
            // compiled-script cache and avoids race conditions with the main app.
            val sandboxName = tempDir.name // unique per request
            val sandboxPluginStr = run {
                val obj = JSONObject(pluginStr)
                val meta = obj.optJSONObject("metadata") ?: JSONObject()
                meta.put("name", sandboxName)
                obj.put("metadata", meta)
                obj.toString()
            }
            val pluginJson = json.decodeFromString<PluginJson>(sandboxPluginStr)
            val loadedExtension = LoadedExtension(pluginJson, tempDir)

            val logList = mutableListOf<String>()
            // clearCache uses sandbox ID — never touches real extension caches
            jsExtensionRunner.clearCache(loadedExtension.id)
            val result = jsExtensionRunner.execute(loadedExtension, scriptName, *args, logCollector = logList)

            val responseJson = JSONObject()
            val logsArray = org.json.JSONArray()
            logList.forEach { logsArray.put(it) }
            responseJson.put("log", logsArray)

            when (result) {
                is ExtensionResult.Success -> {
                    responseJson.put("result", result.data)
                    try {
                        val trimmedData = result.data.trim()
                        if (trimmedData.startsWith("[")) {
                            responseJson.put("data", org.json.JSONArray(trimmedData))
                        } else if (trimmedData.startsWith("{")) {
                            responseJson.put("data", org.json.JSONObject(trimmedData))
                        } else {
                            responseJson.put("data", result.data)
                        }
                    } catch (e: Exception) {
                        responseJson.put("data", result.data)
                    }
                }
                is ExtensionResult.Error -> {
                    responseJson.put("exception", result.message)
                }
            }

            responseJson.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to run test script", e)
            val errJson = JSONObject()
            errJson.put("exception", e.message ?: "Unknown test error")
            errJson.put("log", org.json.JSONArray())
            errJson.toString()
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun sendResponse(socket: Socket, statusCode: Int, body: String) {
        try {
            val bodyBytes = body.toByteArray(Charsets.UTF_8)
            val statusText = when (statusCode) {
                200 -> "OK"
                404 -> "Not Found"
                else -> "Internal Server Error"
            }
            val out = socket.getOutputStream()
            val header = "HTTP/1.1 $statusCode $statusText\r\n" +
                    "Content-Type: application/json; charset=UTF-8\r\n" +
                    "Content-Length: ${bodyBytes.size}\r\n" +
                    "Connection: close\r\n\r\n"
            out.write(header.toByteArray(Charsets.UTF_8))
            out.write(bodyBytes)
            out.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send HTTP response", e)
        }
    }

    private fun ensureForeground() {
        createNotificationChannel()
        val openAppIntent = Intent(this, MainActivity::class.java)
        val piOpenApp = PendingIntent.getActivity(
            this, 
            0, 
            openAppIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val ipAddress = getLocalIpAddress() ?: "127.0.0.1"
        val serverUrl = "http://$ipAddress:$PORT"

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Novel Reader Developer Mode")
            .setContentText("Test server: $serverUrl")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(piOpenApp)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Developer Test Server",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                        return address.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get local IP address", e)
        }
        return null
    }

    private fun String.toSlug(): String {
        val normalized = java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD)
            .replace(Regex("[\\p{InCombiningDiacriticalMarks}]"), "")
        return normalized
            .lowercase()
            .replace("đ", "d").replace("Đ", "d")
            .replace(Regex("[^a-z0-9]"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
    }

    override fun onDestroy() {
        isRunning = false
        isServiceRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // ignore
        }
        serviceJob.cancel()
        super.onDestroy()
    }
}

