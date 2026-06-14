package com.nam.novelreader.data.network

import com.nam.novelreader.BuildConfig
import com.nam.novelreader.data.preferences.AppPreferences
import com.nam.novelreader.data.local.entity.NovelEntity
import com.nam.novelreader.data.local.entity.ChapterEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.io.IOException
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
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

    private suspend fun getUserNovelFolderId(userIdentifier: String): String {
        val cacheKey = "gdrive_novel_folder_$userIdentifier"
        getCachedId(cacheKey)?.let { return it }

        val masterFolderId = findOrCreateFolder("Kho_chua_du_lieu_App", null)
        val novelRootFolderId = findOrCreateFolder("Truyen_nguoi_dung", masterFolderId)
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

    suspend fun uploadNovelToWarehouse(
        novel: NovelEntity,
        chapters: List<ChapterEntity>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAccessToken()
            val userIdentifier = getUserIdentifier()
            
            // 1. Tìm hoặc tạo thư mục Truyen_nguoi_dung/{userIdentifier}
            val userFolderId = getUserNovelFolderId(userIdentifier)
            
            // 2. Tạo JSON dữ liệu truyện (NovelExportData)
            val novelId = java.util.UUID.nameUUIDFromBytes(novel.url.toByteArray()).toString()
            
            // Map chapters sang format tương thích
            val chaptersJsonArray = JSONArray()
            val scenesJsonArray = JSONArray()
            
            chapters.forEach { ch ->
                val chId = java.util.UUID.nameUUIDFromBytes(ch.url.toByteArray()).toString()
                val sceneId = java.util.UUID.nameUUIDFromBytes((ch.url + "_scene").toByteArray()).toString()
                // Chapter metadata object
                val chObj = JSONObject().apply {
                    put("id", chId)
                    put("novelId", novelId)
                    put("title", ch.title)
                    put("order", ch.index)
                    put("url", ch.url)
                    put("novelUrl", ch.novelUrl)
                    put("contentType", ch.contentType)
                    if (!ch.images.isNullOrBlank()) {
                        try {
                            put("images", org.json.JSONArray(ch.images))
                        } catch (e: Exception) {
                            put("images", ch.images)
                        }
                    }
                    put("createdAt", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", java.util.Locale.US).format(java.util.Date(ch.downloadedAt ?: System.currentTimeMillis())))
                    put("updatedAt", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", java.util.Locale.US).format(java.util.Date(ch.downloadedAt ?: System.currentTimeMillis())))
                }
                chaptersJsonArray.put(chObj)
                
                val cleanContent = if (!ch.content.isNullOrBlank()) {
                    ch.content
                } else if (!ch.images.isNullOrBlank()) {
                    try {
                        val arr = JSONArray(ch.images)
                        val sb = StringBuilder()
                        for (i in 0 until arr.length()) {
                            sb.append("![](").append(arr.getString(i)).append(")")
                            if (i < arr.length() - 1) sb.append("\n\n")
                        }
                        sb.toString()
                    } catch (e: Exception) {
                        ""
                    }
                } else {
                    ""
                }

                var cjkCount = 0
                val cleanText = cleanContent.replace(Regex("[\\u4e00-\\u9fa5]")) {
                    cjkCount++
                    " "
                }
                val words = cleanText.split(Regex("\\s+")).filter { it.isNotBlank() }.size
                val wordsCount = cjkCount + words

                // Scene content object
                val sceneObj = JSONObject().apply {
                    put("id", sceneId)
                    put("novelId", novelId)
                    put("chapterId", chId)
                    put("title", ch.title)
                    put("content", cleanContent)
                    put("wordCount", wordsCount)
                    put("order", 1)
                    put("version", 1)
                    put("isActive", 1)
                    put("versionType", "manual")
                    put("url", ch.url)
                    put("novelUrl", ch.novelUrl)
                    put("createdAt", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", java.util.Locale.US).format(java.util.Date(ch.downloadedAt ?: System.currentTimeMillis())))
                    put("updatedAt", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", java.util.Locale.US).format(java.util.Date(ch.downloadedAt ?: System.currentTimeMillis())))
                }
                scenesJsonArray.put(sceneObj)
            }
            
            val novelJsonObj = JSONObject().apply {
                put("id", novelId)
                put("url", novel.url)
                put("sourceUrl", novel.url) // Thêm cho Web App
                put("extensionId", novel.extensionId)
                put("title", novel.title)
                put("author", novel.author)
                put("cover", novel.cover)
                put("coverImage", novel.cover) // Thêm cho Web App
                put("synopsis", novel.description)
                put("description", novel.description) // Thêm cho Web App
                put("genres", JSONArray(novel.genres))
                put("status", novel.status)
                put("type", novel.type)
                put("createdAt", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", java.util.Locale.US).format(java.util.Date()))
                put("updatedAt", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", java.util.Locale.US).format(java.util.Date()))
            }
            
            val exportDataObj = JSONObject().apply {
                put("version", 2)
                put("exportedAt", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", java.util.Locale.US).format(java.util.Date()))
                put("novel", novelJsonObj)
                put("chapters", chaptersJsonArray)
                put("scenes", scenesJsonArray)
                put("characters", JSONArray())
                put("notes", JSONArray())
            }
            
            val exportJsonStr = exportDataObj.toString(2)
            
            // 3. Upload file dữ liệu truyện: {novelTitle}.json lên thư mục userFolderId
            val dataFilename = "${novel.title}.json"
            
            // Tìm xem file data đã có chưa để lấy fileId cập nhật (hoặc upload ghi đè)
            var dataFileId: String? = null
            val safeDataFilename = dataFilename.replace("'", "\\'")
            val qData = "name = '$safeDataFilename' and '$userFolderId' in parents and trashed = false"
            val urlData = "https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder()
                .addQueryParameter("q", qData)
                .addQueryParameter("fields", "files(id)")
                .build()
            
            val searchDataRequest = Request.Builder()
                .url(urlData)
                .get()
                .header("Authorization", "Bearer $token")
                .build()
                
            client.newCall(searchDataRequest).execute().use { resp ->
                if (resp.isSuccessful) {
                    val files = JSONObject(resp.body?.string() ?: "").getJSONArray("files")
                    if (files.length() > 0) {
                        dataFileId = files.getJSONObject(0).getString("id")
                    }
                }
            }
            
            if (dataFileId == null) {
                // Tạo file mới
                val jsonBody = JSONObject().apply {
                    put("name", dataFilename)
                    put("parents", JSONArray().apply { put(userFolderId) })
                }
                val createRequest = Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files")
                    .post(jsonBody.toString().toRequestBody(mediaTypeJson))
                    .header("Authorization", "Bearer $token")
                    .build()
                client.newCall(createRequest).execute().use { resp ->
                    if (!resp.isSuccessful) throw Exception("Lỗi tạo file dữ liệu trên Drive: ${resp.code}")
                    dataFileId = JSONObject(resp.body?.string() ?: "").getString("id")
                }
            }
            
            // Upload nội dung file data
            val uploadUrl = "https://www.googleapis.com/upload/drive/v3/files/$dataFileId?uploadType=media"
            val uploadRequest = Request.Builder()
                .url(uploadUrl)
                .patch(exportJsonStr.toRequestBody(mediaTypeJson))
                .header("Authorization", "Bearer $token")
                .build()
            client.newCall(uploadRequest).execute().use { resp ->
                if (!resp.isSuccessful) throw Exception("Lỗi upload dữ liệu truyện lên Drive: ${resp.code}")
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ========== ACCOUNT-BASED EXTENSIONS BACKUP/RESTORE ==========

    fun zipDirectory(dir: File): ByteArray {
        val byteArrayOutputStream = ByteArrayOutputStream()
        ZipOutputStream(byteArrayOutputStream).use { zos ->
            zipFile(dir, dir, zos)
        }
        return byteArrayOutputStream.toByteArray()
    }

    private fun zipFile(root: File, file: File, zos: ZipOutputStream) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
                zipFile(root, child, zos)
            }
        } else {
            val entryName = file.relativeTo(root).path.replace('\\', '/')
            val entry = ZipEntry(entryName)
            zos.putNextEntry(entry)
            file.inputStream().use { input ->
                input.copyTo(zos)
            }
            zos.closeEntry()
        }
    }

    suspend fun uploadUserExtension(extId: String, zipBytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAccessToken()
            val userIdentifier = getUserIdentifier()
            
            // 1. Tìm hoặc tạo thư mục Truyen_nguoi_dung/{userIdentifier}/Tien_ich
            val masterFolderId = findOrCreateFolder("Kho_chua_du_lieu_App", null)
            val novelRootFolderId = findOrCreateFolder("Truyen_nguoi_dung", masterFolderId)
            val userFolderId = findOrCreateFolder(userIdentifier, novelRootFolderId)
            val userExtFolderId = findOrCreateFolder("Tien_ich", userFolderId)
            
            val filename = "$extId.zip"
            
            // 2. Tìm xem file zip đã tồn tại chưa để ghi đè (PATCH) hoặc tạo mới (POST)
            var fileId: String? = null
            val safeFilename = filename.replace("'", "\\'")
            val q = "name = '$safeFilename' and '$userExtFolderId' in parents and trashed = false"
            val url = "https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder()
                .addQueryParameter("q", q)
                .addQueryParameter("fields", "files(id)")
                .build()
                
            val searchRequest = Request.Builder()
                .url(url)
                .get()
                .header("Authorization", "Bearer $token")
                .build()
                
            client.newCall(searchRequest).execute().use { resp ->
                if (resp.isSuccessful) {
                    val files = JSONObject(resp.body?.string() ?: "").getJSONArray("files")
                    if (files.length() > 0) {
                        fileId = files.getJSONObject(0).getString("id")
                    }
                }
            }
            
            if (fileId == null) {
                // Tạo file mới
                val jsonBody = JSONObject().apply {
                    put("name", filename)
                    put("parents", JSONArray().apply { put(userExtFolderId) })
                }
                val createRequest = Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files")
                    .post(jsonBody.toString().toRequestBody(mediaTypeJson))
                    .header("Authorization", "Bearer $token")
                    .build()
                client.newCall(createRequest).execute().use { resp ->
                    if (!resp.isSuccessful) throw Exception("Lỗi tạo file zip tiện ích trên Drive: ${resp.code}")
                    fileId = JSONObject(resp.body?.string() ?: "").getString("id")
                }
            }
            
            // Upload nội dung file zip
            val uploadUrl = "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media"
            val mediaTypeZip = "application/zip".toMediaType()
            val uploadRequest = Request.Builder()
                .url(uploadUrl)
                .patch(zipBytes.toRequestBody(mediaTypeZip))
                .header("Authorization", "Bearer $token")
                .build()
            client.newCall(uploadRequest).execute().use { resp ->
                if (!resp.isSuccessful) throw Exception("Lỗi upload tiện ích lên Drive: ${resp.code}")
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listUserExtensions(): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val token = getAccessToken()
            val userIdentifier = getUserIdentifier()
            
            val masterFolderId = findOrCreateFolder("Kho_chua_du_lieu_App", null)
            val novelRootFolderId = findOrCreateFolder("Truyen_nguoi_dung", masterFolderId)
            val userFolderId = findOrCreateFolder(userIdentifier, novelRootFolderId)
            val userExtFolderId = findOrCreateFolder("Tien_ich", userFolderId)
            
            val q = "'$userExtFolderId' in parents and mimeType = 'application/zip' and trashed = false"
            val url = "https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder()
                .addQueryParameter("q", q)
                .addQueryParameter("fields", "files(name)")
                .addQueryParameter("pageSize", "100")
                .build()

            val request = Request.Builder()
                .url(url)
                .get()
                .header("Authorization", "Bearer $token")
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Lỗi liệt kê tiện ích trên Drive: ${response.code}"))
                }
                val json = JSONObject(bodyStr)
                val files = json.getJSONArray("files")
                val resultList = mutableListOf<String>()
                for (i in 0 until files.length()) {
                    val file = files.getJSONObject(i)
                    val name = file.getString("name")
                    if (name.endsWith(".zip")) {
                        resultList.add(name.substringBeforeLast(".zip"))
                    }
                }
                Result.success(resultList)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadUserExtension(extId: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val token = getAccessToken()
            val userIdentifier = getUserIdentifier()
            
            val masterFolderId = findOrCreateFolder("Kho_chua_du_lieu_App", null)
            val novelRootFolderId = findOrCreateFolder("Truyen_nguoi_dung", masterFolderId)
            val userFolderId = findOrCreateFolder(userIdentifier, novelRootFolderId)
            val userExtFolderId = findOrCreateFolder("Tien_ich", userFolderId)
            
            val filename = "$extId.zip"
            
            // Tìm fileId
            var fileId: String? = null
            val safeFilename = filename.replace("'", "\\'")
            val q = "name = '$safeFilename' and '$userExtFolderId' in parents and trashed = false"
            val url = "https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder()
                .addQueryParameter("q", q)
                .addQueryParameter("fields", "files(id)")
                .build()
                
            val searchRequest = Request.Builder()
                .url(url)
                .get()
                .header("Authorization", "Bearer $token")
                .build()
                
            client.newCall(searchRequest).execute().use { resp ->
                if (resp.isSuccessful) {
                    val files = JSONObject(resp.body?.string() ?: "").getJSONArray("files")
                    if (files.length() > 0) {
                        fileId = files.getJSONObject(0).getString("id")
                    }
                }
            }
            
            if (fileId == null) {
                return@withContext Result.failure(Exception("Không tìm thấy tiện ích $extId trên Drive."))
            }
            
            val downloadRequest = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
                .get()
                .header("Authorization", "Bearer $token")
                .build()

            client.newCall(downloadRequest).execute().use { response ->
                val bodyBytes = response.body?.bytes()
                if (!response.isSuccessful || bodyBytes == null) {
                    return@withContext Result.failure(Exception("Lỗi tải tiện ích từ Drive: ${response.code}"))
                }
                Result.success(bodyBytes)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ========== OFFLINE NOVELS SYNC/RESTORE ==========

    suspend fun listUserNovels(): Result<List<Pair<String, String>>> = withContext(Dispatchers.IO) {
        try {
            val token = getAccessToken()
            val userIdentifier = getUserIdentifier()
            val userFolderId = getUserNovelFolderId(userIdentifier)
            
            // Lấy tất cả tệp .json trong thư mục user, loại trừ library_backup.json
            val q = "'$userFolderId' in parents and mimeType = 'application/json' and name != 'library_backup.json' and trashed = false"
            val url = "https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder()
                .addQueryParameter("q", q)
                .addQueryParameter("fields", "files(id, name)")
                .addQueryParameter("pageSize", "100")
                .build()

            val request = Request.Builder()
                .url(url)
                .get()
                .header("Authorization", "Bearer $token")
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Lỗi liệt kê danh sách truyện trên Drive: ${response.code}"))
                }
                val json = JSONObject(bodyStr)
                val files = json.getJSONArray("files")
                val resultList = mutableListOf<Pair<String, String>>()
                for (i in 0 until files.length()) {
                    val file = files.getJSONObject(i)
                    resultList.add(file.getString("id") to file.getString("name"))
                }
                Result.success(resultList)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadFileContent(fileId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val token = getAccessToken()
            val downloadRequest = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
                .get()
                .header("Authorization", "Bearer $token")
                .build()

            client.newCall(downloadRequest).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Lỗi tải file $fileId: ${response.code}"))
                }
                Result.success(bodyStr)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
