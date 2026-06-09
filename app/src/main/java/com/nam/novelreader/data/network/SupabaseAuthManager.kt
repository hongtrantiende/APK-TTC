package com.nam.novelreader.data.network

import com.nam.novelreader.data.preferences.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseAuthManager @Inject constructor(
    private val client: OkHttpClient,
    private val prefs: AppPreferences
) {
    private val mediaTypeJson = "application/json; charset=utf-8".toMediaType()

    private fun getUrl(): String {
        return prefs.supabaseUrl.trim().trimEnd('/')
    }

    private fun getAnonKey(): String {
        return prefs.supabaseAnonKey.trim()
    }

    private fun getServiceRoleKey(): String {
        return prefs.supabaseServiceRoleKey.trim()
    }

    fun isConfigured(): Boolean {
        return prefs.supabaseUrl.isNotBlank() && prefs.supabaseAnonKey.isNotBlank()
    }

    /**
     * Đăng ký tài khoản mới.
     */
    suspend fun signUp(email: String, password: String, displayName: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(Exception("Chưa cấu hình Supabase URL hoặc Anon Key"))
        }

        val url = "${getUrl()}/auth/v1/signup"
        val jsonBody = JSONObject().apply {
            put("email", email)
            put("password", password)
            put("data", JSONObject().apply {
                put("display_name", displayName)
            })
        }

        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toString().toRequestBody(mediaTypeJson))
            .header("apikey", getAnonKey())
            .header("Content-Type", "application/json")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errorMsg = parseError(bodyStr, "Đăng ký thất bại")
                    return@withContext Result.failure(Exception(errorMsg))
                }

                // Nếu đăng ký tự động đăng nhập (auto-confirm enabled), response sẽ có session
                try {
                    val jsonObj = JSONObject(bodyStr)
                    if (jsonObj.has("session") && !jsonObj.isNull("session")) {
                        val session = jsonObj.getJSONObject("session")
                        saveSession(session)
                    }
                } catch (_: Exception) {}

                Result.success(Unit)
            }
        } catch (e: IOException) {
            Result.failure(Exception("Lỗi kết nối mạng: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Đăng nhập với email và password.
     */
    suspend fun login(email: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(Exception("Chưa cấu hình Supabase URL hoặc Anon Key"))
        }

        val url = "${getUrl()}/auth/v1/token?grant_type=password"
        val jsonBody = JSONObject().apply {
            put("email", email)
            put("password", password)
        }

        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toString().toRequestBody(mediaTypeJson))
            .header("apikey", getAnonKey())
            .header("Content-Type", "application/json")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errorMsg = parseError(bodyStr, "Đăng nhập thất bại. Kiểm tra email hoặc mật khẩu.")
                    return@withContext Result.failure(Exception(errorMsg))
                }

                val jsonObj = JSONObject(bodyStr)
                saveSession(jsonObj)
                syncVipStatus()
                Result.success(Unit)
            }
        } catch (e: IOException) {
            Result.failure(Exception("Lỗi kết nối mạng: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Đăng xuất, xoá session cục bộ và gọi API thu hồi token.
     */
    suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
        val accessToken = prefs.supabaseAccessToken
        val url = "${getUrl()}/auth/v1/logout"

        if (isConfigured() && accessToken.isNotBlank()) {
            val request = Request.Builder()
                .url(url)
                .post("{}".toRequestBody(mediaTypeJson))
                .header("apikey", getAnonKey())
                .header("Authorization", "Bearer $accessToken")
                .build()
            try {
                client.newCall(request).execute().close()
            } catch (_: Exception) {}
        }

        // Luôn xoá session cục bộ kể cả khi gọi API lỗi mạng
        clearLocalSession()
        Result.success(Unit)
    }

    /**
     * Gia hạn Access Token sử dụng Refresh Token.
     * Trả về true nếu refresh thành công, false nếu không đăng nhập hoặc lỗi.
     */
    suspend fun refreshSession(): Result<Boolean> = withContext(Dispatchers.IO) {
        val refreshToken = prefs.supabaseRefreshToken
        if (refreshToken.isBlank() || !isConfigured()) {
            return@withContext Result.success(false)
        }

        val url = "${getUrl()}/auth/v1/token?grant_type=refresh_token"
        val jsonBody = JSONObject().apply {
            put("refresh_token", refreshToken)
        }

        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toString().toRequestBody(mediaTypeJson))
            .header("apikey", getAnonKey())
            .header("Content-Type", "application/json")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    // Nếu refresh token hết hạn hoặc lỗi nghiêm trọng (400/401), đăng xuất cục bộ
                    if (response.code in 400..403) {
                        clearLocalSession()
                        return@withContext Result.success(false)
                    }
                    val errorMsg = parseError(bodyStr, "Lỗi gia hạn session")
                    return@withContext Result.failure(Exception(errorMsg))
                }

                val jsonObj = JSONObject(bodyStr)
                saveSession(jsonObj)
                syncVipStatus()
                Result.success(true)
            }
        } catch (e: IOException) {
            // Lỗi mạng tạm thời, giữ nguyên session nhưng báo lỗi
            Result.failure(Exception("Lỗi mạng khi gia hạn session: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Tải tủ sách lên database của Supabase (Bảng user_backups).
     */
    suspend fun backupLibrary(backupJson: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!prefs.supabaseIsLoggedIn) {
            return@withContext Result.failure(Exception("Chưa đăng nhập tài khoản"))
        }
        if (!isConfigured()) {
            return@withContext Result.failure(Exception("Chưa cấu hình Supabase URL hoặc Anon Key"))
        }

        val userId = prefs.supabaseUserId
        val accessToken = prefs.supabaseAccessToken
        val url = "${getUrl()}/rest/v1/user_backups?user_id=eq.$userId"

        val jsonBody = JSONObject().apply {
            put("user_id", userId)
            put("backup_data", backupJson)
            // Cập nhật timestamp hiện tại
            val nowStr = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", java.util.Locale.US).format(java.util.Date())
            put("updated_at", nowStr)
        }

        // Sử dụng PUT để thực hiện upsert dựa trên điều kiện query user_id=eq.$userId
        val request = Request.Builder()
            .url(url)
            .put(jsonBody.toString().toRequestBody(mediaTypeJson))
            .header("apikey", getAnonKey())
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", "application/json")
            .header("Prefer", "return=minimal")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errorMsg = parseError(bodyStr, "Lưu sao lưu lên đám mây thất bại")
                    return@withContext Result.failure(Exception(errorMsg))
                }
                Result.success(Unit)
            }
        } catch (e: IOException) {
            Result.failure(Exception("Lỗi kết nối mạng: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Khôi phục tủ sách từ database của Supabase (Bảng user_backups).
     */
    suspend fun restoreLibrary(): Result<String> = withContext(Dispatchers.IO) {
        if (!prefs.supabaseIsLoggedIn) {
            return@withContext Result.failure(Exception("Chưa đăng nhập tài khoản"))
        }
        if (!isConfigured()) {
            return@withContext Result.failure(Exception("Chưa cấu hình Supabase URL hoặc Anon Key"))
        }

        val userId = prefs.supabaseUserId
        val accessToken = prefs.supabaseAccessToken
        val url = "${getUrl()}/rest/v1/user_backups?user_id=eq.$userId&select=backup_data"

        val request = Request.Builder()
            .url(url)
            .get()
            .header("apikey", getAnonKey())
            .header("Authorization", "Bearer $accessToken")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errorMsg = parseError(bodyStr, "Lấy bản sao lưu từ đám mây thất bại")
                    return@withContext Result.failure(Exception(errorMsg))
                }

                val jsonArray = JSONArray(bodyStr)
                if (jsonArray.length() == 0) {
                    return@withContext Result.failure(Exception("Không tìm thấy bản sao lưu nào trên đám mây cho tài khoản này."))
                }

                val backupObj = jsonArray.getJSONObject(0)
                val backupData = backupObj.optString("backup_data", "")
                if (backupData.isBlank()) {
                    return@withContext Result.failure(Exception("Dữ liệu sao lưu trên đám mây bị rỗng."))
                }

                Result.success(backupData)
            }
        } catch (e: IOException) {
            Result.failure(Exception("Lỗi kết nối mạng: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Cập nhật thông tin User Metadata (Tên hiển thị) lên Supabase.
     */
    suspend fun updateUserMetadata(displayName: String?, avatarBase64: String?): Result<Unit> = withContext(Dispatchers.IO) {
        if (!prefs.supabaseIsLoggedIn) {
            return@withContext Result.failure(Exception("Chưa đăng nhập tài khoản"))
        }
        if (!isConfigured()) {
            return@withContext Result.failure(Exception("Chưa cấu hình Supabase URL hoặc Anon Key"))
        }

        val url = "${getUrl()}/auth/v1/user"
        val jsonBody = JSONObject().apply {
            put("data", JSONObject().apply {
                if (displayName != null) {
                    put("display_name", displayName)
                }
                if (avatarBase64 != null) {
                    put("avatar_base64", avatarBase64)
                }
            })
        }

        val request = Request.Builder()
            .url(url)
            .put(jsonBody.toString().toRequestBody(mediaTypeJson))
            .header("apikey", getAnonKey())
            .header("Authorization", "Bearer ${prefs.supabaseAccessToken}")
            .header("Content-Type", "application/json")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errorMsg = parseError(bodyStr, "Cập nhật hồ sơ thất bại")
                    return@withContext Result.failure(Exception(errorMsg))
                }

                // Cập nhật tên hiển thị cục bộ
                if (displayName != null) {
                    prefs.prefs.edit()
                        .putString("user_display_name", displayName)
                        .apply()
                }

                Result.success(Unit)
            }
        } catch (e: IOException) {
            Result.failure(Exception("Lỗi kết nối mạng: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ========== PRIVATE HELPERS ==========

    private suspend fun syncVipStatus() {
        if (!prefs.supabaseIsLoggedIn || prefs.supabaseUserId.isBlank() || !isConfigured()) return
        
        val url = "${getUrl()}/rest/v1/profiles?id=eq.${prefs.supabaseUserId}&select=vip_until"
        val request = Request.Builder()
            .url(url)
            .get()
            .header("apikey", getAnonKey())
            .header("Authorization", "Bearer ${prefs.supabaseAccessToken}")
            .build()
            
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return
                val bodyStr = response.body?.string() ?: return
                val jsonArray = JSONArray(bodyStr)
                if (jsonArray.length() > 0) {
                    val item = jsonArray.getJSONObject(0)
                    val vipUntilStr = if (item.isNull("vip_until")) null else item.optString("vip_until", null as String?)
                    if (!vipUntilStr.isNullOrBlank()) {
                        try {
                            // Extract date part e.g. 2024-12-31 from 2024-12-31T23:59:59Z
                            val formatter = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                            val dateStr = vipUntilStr.substringBefore("T")
                            val date = formatter.parse(dateStr)
                            if (date != null) {
                                // Add 24h to expiration to represent end of the day, matching Supabase logic usually T23:59:59Z
                                prefs.vipExpiryTimestamp = date.time + 24 * 60 * 60 * 1000L - 1000L
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("SupabaseAuthManager", "Error parsing vip_until: $vipUntilStr")
                        }
                    } else {
                        prefs.vipExpiryTimestamp = 0L
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun saveSession(jsonObj: JSONObject) {
        val accessToken = jsonObj.getString("access_token")
        val refreshToken = jsonObj.getString("refresh_token")
        
        val user = jsonObj.getJSONObject("user")
        val userId = user.getString("id")
        val email = user.getString("email")
        
        val userMetadata = user.optJSONObject("user_metadata")
        val displayName = userMetadata?.optString("display_name") ?: email.substringBefore("@")
        val avatarBase64 = userMetadata?.optString("avatar_base64") ?: ""

        prefs.supabaseAccessToken = accessToken
        prefs.supabaseRefreshToken = refreshToken
        prefs.supabaseUserId = userId
        prefs.supabaseUserEmail = email
        prefs.supabaseIsLoggedIn = true

        // Đồng bộ các trường fake/VBook cũ để UI hiển thị thống nhất
        prefs.prefs.edit()
            .putBoolean("user_is_logged_in", true)
            .putString("user_display_name", displayName)
            .putString("user_subtitle", email)
            .apply()

        // Khôi phục avatar từ cloud user_metadata nếu có
        if (avatarBase64.isNotBlank()) {
            try {
                val cleanStr = if (avatarBase64.startsWith("data:image")) {
                    avatarBase64.substringAfter(",")
                } else {
                    avatarBase64
                }
                val decodedBytes = android.util.Base64.decode(cleanStr, android.util.Base64.DEFAULT)
                val avatarFile = java.io.File(prefs.context.filesDir, "user_avatar.jpg")
                avatarFile.outputStream().use { it.write(decodedBytes) }
                prefs.userAvatarPath = avatarFile.absolutePath
            } catch (_: Exception) {}
        }
    }

    private fun clearLocalSession() {
        val path = prefs.userAvatarPath
        if (path.isNotBlank()) {
            try {
                val file = java.io.File(path)
                if (file.exists()) file.delete()
            } catch (_: Exception) {}
        }

        prefs.supabaseAccessToken = ""
        prefs.supabaseRefreshToken = ""
        prefs.supabaseUserId = ""
        prefs.supabaseUserEmail = ""
        prefs.supabaseIsLoggedIn = false
        prefs.userAvatarPath = ""
        prefs.vipExpiryTimestamp = 0L

        // Clear fake/VBook preferences
        prefs.prefs.edit()
            .putBoolean("user_is_logged_in", false)
            .putString("user_display_name", "Khách")
            .putString("user_subtitle", "Chưa đăng nhập")
            .putLong("vip_expiry_timestamp", 0L)
            .apply()
    }

    /**
     * Lấy danh sách nhóm chat công khai.
     */
    suspend fun getChatGroups(): Result<org.json.JSONArray> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(Exception("Chưa cấu hình Supabase URL hoặc Anon Key"))
        }

        val url = "${getUrl()}/rest/v1/chat_groups?select=*&is_public=eq.true&order=is_pinned.desc,created_at.desc"
        val request = Request.Builder()
            .url(url)
            .get()
            .header("apikey", getAnonKey())
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errorMsg = parseError(bodyStr, "Lấy danh sách nhóm chat thất bại")
                    return@withContext Result.failure(Exception(errorMsg))
                }
                Result.success(org.json.JSONArray(bodyStr))
            }
        } catch (e: IOException) {
            Result.failure(Exception("Lỗi kết nối mạng: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Tạo nhóm chat mới.
     */
    suspend fun createChatGroup(name: String, avatarBase64: String, isPublic: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        if (!prefs.supabaseIsLoggedIn) {
            return@withContext Result.failure(Exception("Chưa đăng nhập tài khoản"))
        }
        if (!isConfigured()) {
            return@withContext Result.failure(Exception("Chưa cấu hình Supabase URL hoặc Anon Key"))
        }

        val url = "${getUrl()}/rest/v1/chat_groups"
        val jsonBody = JSONObject().apply {
            put("name", name)
            put("avatar_url", avatarBase64)
            put("is_public", isPublic)
            put("created_by", prefs.supabaseUserId)
        }

        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toString().toRequestBody(mediaTypeJson))
            .header("apikey", getAnonKey())
            .header("Authorization", "Bearer ${prefs.supabaseAccessToken}")
            .header("Content-Type", "application/json")
            .header("Prefer", "return=minimal")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errorMsg = parseError(bodyStr, "Tạo nhóm chat thất bại")
                    return@withContext Result.failure(Exception(errorMsg))
                }
                Result.success(Unit)
            }
        } catch (e: IOException) {
            Result.failure(Exception("Lỗi kết nối mạng: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Xóa nhóm chat.
     */
    suspend fun deleteChatGroup(groupId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!prefs.supabaseIsLoggedIn) {
            return@withContext Result.failure(Exception("Chưa đăng nhập tài khoản"))
        }
        if (!isConfigured()) {
            return@withContext Result.failure(Exception("Chưa cấu hình Supabase URL hoặc Anon Key"))
        }

        val url = "${getUrl()}/rest/v1/chat_groups?id=eq.$groupId"
        val request = Request.Builder()
            .url(url)
            .delete()
            .header("apikey", getAnonKey())
            .header("Authorization", "Bearer ${prefs.supabaseAccessToken}")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errorMsg = parseError(bodyStr, "Xóa nhóm chat thất bại")
                    return@withContext Result.failure(Exception(errorMsg))
                }
                Result.success(Unit)
            }
        } catch (e: IOException) {
            Result.failure(Exception("Lỗi kết nối mạng: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Ghim / Bỏ ghim nhóm chat.
     */
    suspend fun pinChatGroup(groupId: String, isPinned: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        if (!prefs.supabaseIsLoggedIn) {
            return@withContext Result.failure(Exception("Chưa đăng nhập tài khoản"))
        }
        if (!isConfigured()) {
            return@withContext Result.failure(Exception("Chưa cấu hình Supabase URL hoặc Anon Key"))
        }

        val url = "${getUrl()}/rest/v1/chat_groups?id=eq.$groupId"
        val jsonBody = JSONObject().apply {
            put("is_pinned", isPinned)
        }

        val request = Request.Builder()
            .url(url)
            .patch(jsonBody.toString().toRequestBody(mediaTypeJson))
            .header("apikey", getAnonKey())
            .header("Authorization", "Bearer ${prefs.supabaseAccessToken}")
            .header("Content-Type", "application/json")
            .header("Prefer", "return=minimal")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errorMsg = parseError(bodyStr, "Ghim nhóm chat thất bại")
                    return@withContext Result.failure(Exception(errorMsg))
                }
                Result.success(Unit)
            }
        } catch (e: IOException) {
            Result.failure(Exception("Lỗi kết nối mạng: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Lấy các tin nhắn thuộc nhóm chat.
     */
    suspend fun getChatMessages(groupId: String): Result<org.json.JSONArray> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(Exception("Chưa cấu hình Supabase URL hoặc Anon Key"))
        }

        val url = "${getUrl()}/rest/v1/chat_messages?group_id=eq.$groupId&select=*&order=created_at.asc"
        val request = Request.Builder()
            .url(url)
            .get()
            .header("apikey", getAnonKey())
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errorMsg = parseError(bodyStr, "Lấy tin nhắn thất bại")
                    return@withContext Result.failure(Exception(errorMsg))
                }
                Result.success(org.json.JSONArray(bodyStr))
            }
        } catch (e: IOException) {
            Result.failure(Exception("Lỗi kết nối mạng: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Gửi tin nhắn mới.
     */
    suspend fun sendChatMessage(groupId: String, content: String, senderAvatarBase64: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!prefs.supabaseIsLoggedIn) {
            return@withContext Result.failure(Exception("Chưa đăng nhập tài khoản"))
        }
        if (!isConfigured()) {
            return@withContext Result.failure(Exception("Chưa cấu hình Supabase URL hoặc Anon Key"))
        }

        val url = "${getUrl()}/rest/v1/chat_messages"
        val jsonBody = JSONObject().apply {
            put("group_id", groupId)
            put("sender_id", prefs.supabaseUserId)
            put("sender_name", prefs.prefs.getString("user_display_name", "Khách") ?: "Khách")
            put("sender_email", prefs.supabaseUserEmail)
            put("sender_avatar", senderAvatarBase64)
            put("content", content)
        }

        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toString().toRequestBody(mediaTypeJson))
            .header("apikey", getAnonKey())
            .header("Authorization", "Bearer ${prefs.supabaseAccessToken}")
            .header("Content-Type", "application/json")
            .header("Prefer", "return=minimal")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errorMsg = parseError(bodyStr, "Gửi tin nhắn thất bại")
                    return@withContext Result.failure(Exception(errorMsg))
                }
                Result.success(Unit)
            }
        } catch (e: IOException) {
            Result.failure(Exception("Lỗi kết nối mạng: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Lấy tổng số lượng người dùng đã đăng ký tài khoản (từ view user_count).
     */
    suspend fun getTotalUserCount(): Result<Int> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.success(0)
        }

        val url = "${getUrl()}/rest/v1/user_count"
        val request = Request.Builder()
            .url(url)
            .get()
            .header("apikey", getAnonKey())
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext Result.success(0)
                }
                val jsonArr = org.json.JSONArray(bodyStr)
                if (jsonArr.length() > 0) {
                    val count = jsonArr.getJSONObject(0).optInt("count", 0)
                    Result.success(count)
                } else {
                    Result.success(0)
                }
            }
        } catch (_: Exception) {
            Result.success(0)
        }
    }

    private fun parseError(bodyStr: String, fallback: String): String {
        android.util.Log.e("SupabaseAuthManager", "Error body: $bodyStr")
        return try {
            val json = JSONObject(bodyStr)
            json.optString("error_description", json.optString("message", fallback))
        } catch (_: Exception) {
            fallback
        }
    }

    /**
     * Lấy danh sách hồ sơ người dùng (Dành cho Admin).
     */
    suspend fun getAllProfiles(): Result<List<SupabaseProfile>> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(Exception("Chưa cấu hình Supabase URL hoặc Anon Key"))
        }
        if (!prefs.supabaseIsLoggedIn) {
            return@withContext Result.failure(Exception("Chưa đăng nhập tài khoản"))
        }

        val url = "${getUrl()}/rest/v1/profiles?select=*&order=email.asc"
        val serviceKey = getServiceRoleKey()
        val useAdminKey = serviceKey.isNotBlank()
        
        val requestBuilder = Request.Builder()
            .url(url)
            .get()
            
        if (useAdminKey) {
            requestBuilder.header("apikey", serviceKey)
            requestBuilder.header("Authorization", "Bearer $serviceKey")
        } else {
            requestBuilder.header("apikey", getAnonKey())
            requestBuilder.header("Authorization", "Bearer ${prefs.supabaseAccessToken}")
        }
        
        val request = requestBuilder.build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errorMsg = parseError(bodyStr, "Lấy danh sách người dùng thất bại")
                    return@withContext Result.failure(Exception(errorMsg))
                }

                val jsonArray = JSONArray(bodyStr)
                val profilesList = mutableListOf<SupabaseProfile>()
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    profilesList.add(
                        SupabaseProfile(
                            id = item.optString("id", ""),
                            email = item.optString("email", ""),
                            displayName = item.optString("display_name", ""),
                            vipUntil = if (item.isNull("vip_until")) null else item.optString("vip_until", null as String?),
                            adminAssignedModel = if (item.isNull("admin_assigned_model")) null else item.optString("admin_assigned_model", null as String?),
                            adminModelQuota = item.optInt("admin_model_quota", 0),
                            adminDailyQuotaLimit = item.optInt("admin_daily_quota_limit", 0),
                            avatarUrl = if (item.isNull("avatar_url")) null else item.optString("avatar_url", null as String?)
                        )
                    )
                }
                Result.success(profilesList)
            }
        } catch (e: IOException) {
            Result.failure(Exception("Lỗi kết nối mạng: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Cập nhật thông tin profile người dùng (Dành cho Admin).
     */
    suspend fun updateUserProfile(
        id: String,
        displayName: String,
        vipUntil: String?,
        adminModelQuota: Int,
        adminDailyQuotaLimit: Int,
        adminAssignedModel: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(Exception("Chưa cấu hình Supabase URL hoặc Anon Key"))
        }
        if (!prefs.supabaseIsLoggedIn) {
            return@withContext Result.failure(Exception("Chưa đăng nhập tài khoản"))
        }

        val url = "${getUrl()}/rest/v1/profiles?id=eq.$id"
        val jsonBody = JSONObject().apply {
            put("display_name", displayName.trim())
            put("vip_until", vipUntil ?: JSONObject.NULL)
            put("admin_model_quota", adminModelQuota)
            put("admin_daily_quota_limit", adminDailyQuotaLimit)
            put("admin_assigned_model", adminAssignedModel ?: JSONObject.NULL)
            
            val currentVnDate = java.text.SimpleDateFormat("EEE MMM dd yyyy", java.util.Locale.US).format(java.util.Date())
            put("admin_quota_last_reset", currentVnDate)
        }

        val serviceKey = getServiceRoleKey()
        val useAdminKey = serviceKey.isNotBlank()

        val requestBuilder = Request.Builder()
            .url(url)
            .patch(jsonBody.toString().toRequestBody(mediaTypeJson))
            .header("Content-Type", "application/json")
            .header("Prefer", "return=minimal")
            
        if (useAdminKey) {
            requestBuilder.header("apikey", serviceKey)
            requestBuilder.header("Authorization", "Bearer $serviceKey")
        } else {
            requestBuilder.header("apikey", getAnonKey())
            requestBuilder.header("Authorization", "Bearer ${prefs.supabaseAccessToken}")
        }
        
        val request = requestBuilder.build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errorMsg = parseError(bodyStr, "Cập nhật tài khoản thất bại")
                    return@withContext Result.failure(Exception(errorMsg))
                }
                Result.success(Unit)
            }
        } catch (e: IOException) {
            Result.failure(Exception("Lỗi kết nối mạng: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

data class SupabaseProfile(
    val id: String,
    val email: String,
    val displayName: String,
    val vipUntil: String?,
    val adminAssignedModel: String?,
    val adminModelQuota: Int,
    val adminDailyQuotaLimit: Int,
    val avatarUrl: String?
)
