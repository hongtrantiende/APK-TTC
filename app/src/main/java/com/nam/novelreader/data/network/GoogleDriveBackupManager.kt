package com.nam.novelreader.data.network

import com.nam.novelreader.BuildConfig
import com.nam.novelreader.data.preferences.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleDriveBackupManager @Inject constructor(
    private val client: OkHttpClient,
    private val prefs: AppPreferences
) {
    private val mediaTypeJson = "application/json; charset=utf-8".toMediaType()

    private var cachedToken: String? = null
    private var tokenExpiryTime: Long = 0

    // ========== CACHING HELPERS ==========

    private fun getCachedId(key: String): String? {
        return prefs.prefs.getString(key, null)
    }

    private fun saveCachedId(key: String, value: String) {
        prefs.prefs.edit().putString(key, value).apply()
    }

    private fun clearCachedId(key: String) {
        prefs.prefs.edit().remove(key).apply()
    }

    // ========== CORE OAUTH & DRIVE METHODS ==========

    private suspend fun getAccessToken(): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (cachedToken != null && now < tokenExpiryTime) {
            return@withContext cachedToken!!
        }

        val clientId = BuildConfig.GOOGLE_CLIENT_ID
        val clientSecret = BuildConfig.GOOGLE_CLIENT_SECRET
        val refreshToken = BuildConfig.GOOGLE_REFRESH_TOKEN

        if (clientId.isBlank() || clientSecret.isBlank() || refreshToken.isBlank()) {
            throw Exception("Thiếu thông tin xác thực Google Drive trong build config.")
        }

        val formBody = FormBody.Builder()
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("refresh_token", refreshToken)
            .add("grant_type", "refresh_token")
            .build()

        val request = Request.Builder()
            .url("https://oauth2.googleapis.com/token")
            .post(formBody)
            .build()

        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw IOException("Lỗi lấy Access Token từ Google: ${response.code} - $bodyStr")
            }

            val json = JSONObject(bodyStr)
            val token = json.getString("access_token")
            val expiresIn = json.getLong("expires_in")

            cachedToken = token
            // Expire 1 phút trước thời gian thật để an toàn
            tokenExpiryTime = System.currentTimeMillis() + (expiresIn - 60) * 1000
            token
        }
    }

    private suspend fun findOrCreateFolder(name: String, parentId: String?): String = withContext(Dispatchers.IO) {
        val token = getAccessToken()
        val safeName = name.replace("'", "\\'")
        var q = "name = '$safeName' and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
        if (parentId != null) {
            q += " and '$parentId' in parents"
        }

        val url = "https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder()
            .addQueryParameter("q", q)
            .addQueryParameter("fields", "files(id)")
            .build()

        val searchRequest = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", "Bearer $token")
            .build()

        client.newCall(searchRequest).execute().use { response ->
            val bodyStr = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val json = JSONObject(bodyStr)
                val files = json.getJSONArray("files")
                if (files.length() > 0) {
                    return@withContext files.getJSONObject(0).getString("id")
                }
            }
        }

        // Tạo thư mục mới nếu không tìm thấy
        val jsonBody = JSONObject().apply {
            put("name", name)
            put("mimeType", "application/vnd.google-apps.folder")
            if (parentId != null) {
                val parentsArr = org.json.JSONArray().apply { put(parentId) }
                put("parents", parentsArr)
            }
        }

        val createRequest = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files")
            .post(jsonBody.toString().toRequestBody(mediaTypeJson))
            .header("Authorization", "Bearer $token")
            .build()

        client.newCall(createRequest).execute().use { response ->
            val bodyStr = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw IOException("Lỗi tạo thư mục $name: ${response.code} - $bodyStr")
            }
            val json = JSONObject(bodyStr)
            json.getString("id")
        }
    }

    private suspend fun getUserFolderId(userIdentifier: String): String {
        val cacheKey = "gdrive_folder_$userIdentifier"
        getCachedId(cacheKey)?.let { return it }

        val masterFolderId = findOrCreateFolder("Kho_chua_du_lieu_App", null)
        val novelRootFolderId = findOrCreateFolder("Truyen_nguoi_dung_APK", masterFolderId)
        val folderId = findOrCreateFolder(userIdentifier, novelRootFolderId)
        
        saveCachedId(cacheKey, folderId)
        return folderId
    }

    private suspend fun getExtensionFolderId(): String {
        val cacheKey = "gdrive_folder_Tien_ich_APK"
        getCachedId(cacheKey)?.let { return it }

        val masterFolderId = findOrCreateFolder("Kho_chua_du_lieu_App", null)
        val folderId = findOrCreateFolder("Tien_ich_APK", masterFolderId)
        
        saveCachedId(cacheKey, folderId)
        return folderId
    }

    private fun getUserIdentifier(): String {
        val email = prefs.supabaseUserEmail
        return if (prefs.supabaseIsLoggedIn && email.isNotBlank()) {
            email.replace("@", "_").replace(".", "_")
        } else {
            val syncId = prefs.prefs.getString("sync_id", null) ?: run {
                val newId = java.util.UUID.randomUUID().toString()
                prefs.prefs.edit().putString("sync_id", newId).apply()
                newId
            }
            "device_$syncId"
        }
    }

    // ========== PUBLIC SYNC & FILE METHODS ==========

    suspend fun backupLibrary(backupJson: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAccessToken()
            val userIdentifier = getUserIdentifier()
            val filename = "library_backup.json"
            val fileCacheKey = "gdrive_file_library_backup_$userIdentifier"
            val folderCacheKey = "gdrive_folder_$userIdentifier"

            var userFolderId = getCachedId(folderCacheKey)
            var fileId = getCachedId(fileCacheKey)

            if (userFolderId == null || fileId == null) {
                userFolderId = getUserFolderId(userIdentifier)
                
                val q = "name = '$filename' and '$userFolderId' in parents and trashed = false"
                val url = "https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder()
                    .addQueryParameter("q", q)
                    .addQueryParameter("fields", "files(id)")
                    .build()

                val searchRequest = Request.Builder()
                    .url(url)
                    .get()
                    .header("Authorization", "Bearer $token")
                    .build()

                client.newCall(searchRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val json = JSONObject(response.body?.string() ?: "")
                        val files = json.getJSONArray("files")
                        if (files.length() > 0) {
                            fileId = files.getJSONObject(0).getString("id")
                            saveCachedId(fileCacheKey, fileId!!)
                        }
                    }
                }
            }

            if (fileId == null) {
                val jsonBody = JSONObject().apply {
                    put("name", filename)
                    val parentsArr = org.json.JSONArray().apply { put(userFolderId) }
                    put("parents", parentsArr)
                }

                val createRequest = Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files")
                    .post(jsonBody.toString().toRequestBody(mediaTypeJson))
                    .header("Authorization", "Bearer $token")
                    .build()

                client.newCall(createRequest).execute().use { response ->
                    val bodyStr = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("Lỗi khởi tạo file trên Drive: ${response.code} - $bodyStr"))
                    }
                    fileId = JSONObject(bodyStr).getString("id")
                    saveCachedId(fileCacheKey, fileId!!)
                }
            }

            val uploadUrl = "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media"
            val uploadRequest = Request.Builder()
                .url(uploadUrl)
                .patch(backupJson.toRequestBody(mediaTypeJson))
                .header("Authorization", "Bearer $token")
                .build()

            client.newCall(uploadRequest).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    if (response.code == 404 || response.code == 403) {
                        clearCachedId(fileCacheKey)
                        clearCachedId(folderCacheKey)
                        return@withContext backupLibrary(backupJson)
                    }
                    return@withContext Result.failure(Exception("Lỗi tải lên dữ liệu Drive: ${response.code} - $bodyStr"))
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreLibrary(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val token = getAccessToken()
            val userIdentifier = getUserIdentifier()
            val filename = "library_backup.json"
            val fileCacheKey = "gdrive_file_library_backup_$userIdentifier"
            val folderCacheKey = "gdrive_folder_$userIdentifier"

            var userFolderId = getCachedId(folderCacheKey)
            var fileId = getCachedId(fileCacheKey)

            if (userFolderId == null || fileId == null) {
                userFolderId = getUserFolderId(userIdentifier)
                val q = "name = '$filename' and '$userFolderId' in parents and trashed = false"
                val url = "https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder()
                    .addQueryParameter("q", q)
                    .addQueryParameter("fields", "files(id)")
                    .build()

                val searchRequest = Request.Builder()
                    .url(url)
                    .get()
                    .header("Authorization", "Bearer $token")
                    .build()

                client.newCall(searchRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val json = JSONObject(response.body?.string() ?: "")
                        val files = json.getJSONArray("files")
                        if (files.length() > 0) {
                            fileId = files.getJSONObject(0).getString("id")
                            saveCachedId(fileCacheKey, fileId!!)
                        }
                    }
                }
            }

            if (fileId == null) {
                return@withContext Result.failure(Exception("Không tìm thấy bản sao lưu nào trên Kho chung."))
            }

            val downloadRequest = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
                .get()
                .header("Authorization", "Bearer $token")
                .build()

            client.newCall(downloadRequest).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    if (response.code == 404 || response.code == 403) {
                        clearCachedId(fileCacheKey)
                        clearCachedId(folderCacheKey)
                        return@withContext restoreLibrary()
                    }
                    return@withContext Result.failure(Exception("Lỗi tải tệp sao lưu: ${response.code} - $bodyStr"))
                }
                Result.success(bodyStr)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadPublicExtensionsConfig(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val token = getAccessToken()
            val filename = "public_extensions.json"
            val fileCacheKey = "gdrive_file_public_extensions"
            val folderCacheKey = "gdrive_folder_Tien_ich_APK"

            var extensionFolderId = getCachedId(folderCacheKey)
            var fileId = getCachedId(fileCacheKey)

            if (extensionFolderId == null || fileId == null) {
                extensionFolderId = getExtensionFolderId()
                val q = "name = '$filename' and '$extensionFolderId' in parents and trashed = false"
                val url = "https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder()
                    .addQueryParameter("q", q)
                    .addQueryParameter("fields", "files(id)")
                    .build()

                val searchRequest = Request.Builder()
                    .url(url)
                    .get()
                    .header("Authorization", "Bearer $token")
                    .build()

                client.newCall(searchRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val json = JSONObject(response.body?.string() ?: "")
                        val files = json.getJSONArray("files")
                        if (files.length() > 0) {
                            fileId = files.getJSONObject(0).getString("id")
                            saveCachedId(fileCacheKey, fileId!!)
                        }
                    }
                }
            }

            if (fileId == null) {
                return@withContext Result.failure(Exception("Không tìm thấy cấu hình public extensions."))
            }

            val downloadRequest = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
                .get()
                .header("Authorization", "Bearer $token")
                .build()

            client.newCall(downloadRequest).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    if (response.code == 404 || response.code == 403) {
                        clearCachedId(fileCacheKey)
                        clearCachedId(folderCacheKey)
                        return@withContext downloadPublicExtensionsConfig()
                    }
                    return@withContext Result.failure(Exception("Lỗi tải tệp public extensions: ${response.code}"))
                }
                Result.success(bodyStr)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadPublicExtensionsConfig(jsonContent: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAccessToken()
            val filename = "public_extensions.json"
            val fileCacheKey = "gdrive_file_public_extensions"
            val folderCacheKey = "gdrive_folder_Tien_ich_APK"

            var extensionFolderId = getCachedId(folderCacheKey)
            var fileId = getCachedId(fileCacheKey)

            if (extensionFolderId == null || fileId == null) {
                extensionFolderId = getExtensionFolderId()
                val q = "name = '$filename' and '$extensionFolderId' in parents and trashed = false"
                val url = "https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder()
                    .addQueryParameter("q", q)
                    .addQueryParameter("fields", "files(id)")
                    .build()

                val searchRequest = Request.Builder()
                    .url(url)
                    .get()
                    .header("Authorization", "Bearer $token")
                    .build()

                client.newCall(searchRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val json = JSONObject(response.body?.string() ?: "")
                        val files = json.getJSONArray("files")
                        if (files.length() > 0) {
                            fileId = files.getJSONObject(0).getString("id")
                            saveCachedId(fileCacheKey, fileId!!)
                        }
                    }
                }
            }

            if (fileId == null) {
                val jsonBody = JSONObject().apply {
                    put("name", filename)
                    val parentsArr = org.json.JSONArray().apply { put(extensionFolderId) }
                    put("parents", parentsArr)
                }

                val createRequest = Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files")
                    .post(jsonBody.toString().toRequestBody(mediaTypeJson))
                    .header("Authorization", "Bearer $token")
                    .build()

                client.newCall(createRequest).execute().use { response ->
                    val bodyStr = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("Lỗi tạo file: ${response.code}"))
                    }
                    fileId = JSONObject(bodyStr).getString("id")
                    saveCachedId(fileCacheKey, fileId!!)
                }
            }

            val uploadUrl = "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media"
            val uploadRequest = Request.Builder()
                .url(uploadUrl)
                .patch(jsonContent.toRequestBody(mediaTypeJson))
                .header("Authorization", "Bearer $token")
                .build()

            client.newCall(uploadRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code == 404 || response.code == 403) {
                        clearCachedId(fileCacheKey)
                        clearCachedId(folderCacheKey)
                        return@withContext uploadPublicExtensionsConfig(jsonContent)
                    }
                    return@withContext Result.failure(Exception("Lỗi upload dữ liệu: ${response.code}"))
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadPluginCatalog(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val token = getAccessToken()
            val filename = "plugin.json"
            val fileCacheKey = "gdrive_file_plugin"
            val folderCacheKey = "gdrive_folder_Tien_ich_APK"

            var extensionFolderId = getCachedId(folderCacheKey)
            var fileId = getCachedId(fileCacheKey)

            if (extensionFolderId == null || fileId == null) {
                extensionFolderId = getExtensionFolderId()
                val q = "name = '$filename' and '$extensionFolderId' in parents and trashed = false"
                val url = "https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder()
                    .addQueryParameter("q", q)
                    .addQueryParameter("fields", "files(id)")
                    .build()

                val searchRequest = Request.Builder()
                    .url(url)
                    .get()
                    .header("Authorization", "Bearer $token")
                    .build()

                client.newCall(searchRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val json = JSONObject(response.body?.string() ?: "")
                        val files = json.getJSONArray("files")
                        if (files.length() > 0) {
                            fileId = files.getJSONObject(0).getString("id")
                            saveCachedId(fileCacheKey, fileId!!)
                        }
                    }
                }
            }

            if (fileId == null) {
                return@withContext Result.failure(Exception("Không tìm thấy tệp danh mục plugin.json trên Drive."))
            }

            val downloadRequest = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
                .get()
                .header("Authorization", "Bearer $token")
                .build()

            client.newCall(downloadRequest).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    if (response.code == 404 || response.code == 403) {
                        clearCachedId(fileCacheKey)
                        clearCachedId(folderCacheKey)
                        return@withContext downloadPluginCatalog()
                    }
                    return@withContext Result.failure(Exception("Lỗi tải tệp danh mục: ${response.code}"))
                }
                Result.success(bodyStr)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadPluginCatalog(jsonContent: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAccessToken()
            val filename = "plugin.json"
            val fileCacheKey = "gdrive_file_plugin"
            val folderCacheKey = "gdrive_folder_Tien_ich_APK"

            var extensionFolderId = getCachedId(folderCacheKey)
            var fileId = getCachedId(fileCacheKey)

            if (extensionFolderId == null || fileId == null) {
                extensionFolderId = getExtensionFolderId()
                val q = "name = '$filename' and '$extensionFolderId' in parents and trashed = false"
                val url = "https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder()
                    .addQueryParameter("q", q)
                    .addQueryParameter("fields", "files(id)")
                    .build()

                val searchRequest = Request.Builder()
                    .url(url)
                    .get()
                    .header("Authorization", "Bearer $token")
                    .build()

                client.newCall(searchRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val json = JSONObject(response.body?.string() ?: "")
                        val files = json.getJSONArray("files")
                        if (files.length() > 0) {
                            fileId = files.getJSONObject(0).getString("id")
                            saveCachedId(fileCacheKey, fileId!!)
                        }
                    }
                }
            }

            if (fileId == null) {
                val jsonBody = JSONObject().apply {
                    put("name", filename)
                    val parentsArr = org.json.JSONArray().apply { put(extensionFolderId) }
                    put("parents", parentsArr)
                }

                val createRequest = Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files")
                    .post(jsonBody.toString().toRequestBody(mediaTypeJson))
                    .header("Authorization", "Bearer $token")
                    .build()

                client.newCall(createRequest).execute().use { response ->
                    val bodyStr = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("Lỗi tạo file danh mục: ${response.code}"))
                    }
                    fileId = JSONObject(bodyStr).getString("id")
                    saveCachedId(fileCacheKey, fileId!!)
                }
            }

            val uploadUrl = "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media"
            val uploadRequest = Request.Builder()
                .url(uploadUrl)
                .patch(jsonContent.toRequestBody(mediaTypeJson))
                .header("Authorization", "Bearer $token")
                .build()

            client.newCall(uploadRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code == 404 || response.code == 403) {
                        clearCachedId(fileCacheKey)
                        clearCachedId(folderCacheKey)
                        return@withContext uploadPluginCatalog(jsonContent)
                    }
                    return@withContext Result.failure(Exception("Lỗi upload danh mục: ${response.code}"))
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun prefetchExtensionFolderFileIds(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAccessToken()
            val extensionFolderId = getExtensionFolderId()
            
            val url = "https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder()
                .addQueryParameter("q", "'$extensionFolderId' in parents and trashed = false")
                .addQueryParameter("pageSize", "1000")
                .addQueryParameter("fields", "files(id, name)")
                .build()

            val request = Request.Builder()
                .url(url)
                .get()
                .header("Authorization", "Bearer $token")
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Failed to list extension folder files: ${response.code}"))
                }
                val json = JSONObject(bodyStr)
                if (json.has("files")) {
                    val files = json.getJSONArray("files")
                    val editor = prefs.prefs.edit()
                    for (i in 0 until files.length()) {
                        val file = files.getJSONObject(i)
                        val id = file.getString("id")
                        val name = file.getString("name")
                        editor.putString("gdrive_file_$name", id)
                    }
                    editor.apply()
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadFileFromExtensionFolder(filename: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val token = getAccessToken()
            val fileCacheKey = "gdrive_file_$filename"
            val folderCacheKey = "gdrive_folder_Tien_ich_APK"

            var extensionFolderId = getCachedId(folderCacheKey)
            var fileId = getCachedId(fileCacheKey)

            if (extensionFolderId == null || fileId == null) {
                extensionFolderId = getExtensionFolderId()
                val q = "name = '$filename' and '$extensionFolderId' in parents and trashed = false"
                val url = "https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder()
                    .addQueryParameter("q", q)
                    .addQueryParameter("fields", "files(id)")
                    .build()

                val searchRequest = Request.Builder()
                    .url(url)
                    .get()
                    .header("Authorization", "Bearer $token")
                    .build()

                client.newCall(searchRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val json = JSONObject(response.body?.string() ?: "")
                        val files = json.getJSONArray("files")
                        if (files.length() > 0) {
                            fileId = files.getJSONObject(0).getString("id")
                            saveCachedId(fileCacheKey, fileId!!)
                        }
                    }
                }
            }

            if (fileId == null) {
                return@withContext Result.failure(Exception("Không tìm thấy tệp $filename trên Drive Kho chung."))
            }

            val downloadRequest = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
                .get()
                .header("Authorization", "Bearer $token")
                .build()

            client.newCall(downloadRequest).execute().use { response ->
                val bodyBytes = response.body?.bytes()
                if (!response.isSuccessful || bodyBytes == null) {
                    if (response.code == 404 || response.code == 403) {
                        clearCachedId(fileCacheKey)
                        clearCachedId(folderCacheKey)
                        return@withContext downloadFileFromExtensionFolder(filename)
                    }
                    return@withContext Result.failure(Exception("Lỗi tải tệp $filename: ${response.code}"))
                }
                Result.success(bodyBytes)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
