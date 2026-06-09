package com.nam.novelreader.di

import android.content.Context
import androidx.room.Room
import com.nam.novelreader.data.local.AppDatabase
import com.nam.novelreader.data.local.dao.*
import com.nam.novelreader.data.network.InMemoryCookieJar
import com.nam.novelreader.data.network.RetryInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor
import java.net.InetAddress
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private var cronetEngine: org.chromium.net.CronetEngine? = null
    private var cronetInitialized = false

    private fun getCronetEngine(context: Context): org.chromium.net.CronetEngine? {
        if (cronetInitialized) return cronetEngine
        synchronized(this) {
            if (cronetInitialized) return cronetEngine
            try {
                com.google.android.gms.net.CronetProviderInstaller.installProvider(context)
                cronetEngine = org.chromium.net.CronetEngine.Builder(context)
                    .enableHttp2(true)
                    .enableQuic(true)
                    .build()
                android.util.Log.d("Cronet", "Cronet Engine initialized successfully via Play Services")
            } catch (e: Exception) {
                android.util.Log.e("Cronet", "Failed to initialize Cronet Engine: ${e.message}", e)
                cronetEngine = null
            }
            cronetInitialized = true
            return cronetEngine
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient {
        val prefs = context.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE)

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val timeoutSeconds = prefs.getInt("conn_timeout", 30).toLong()
        val baseBuilder = OkHttpClient.Builder()
            .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .cookieJar(com.nam.novelreader.data.network.WebViewCookieJar())
            .addInterceptor(logging)
            .addInterceptor(RetryInterceptor(context))
            .addInterceptor(com.nam.novelreader.data.network.ProxyRotationInterceptor(context))

        // === Tên miền tùy chỉnh (Domain Override) ===
        val overrideJson = prefs.getString("connection_url_override", "{}") ?: "{}"
        if (overrideJson != "{}" && overrideJson.isNotBlank()) {
            try {
                val overrides = org.json.JSONObject(overrideJson)
                baseBuilder.addInterceptor { chain ->
                    val request = chain.request()
                    val host = request.url.host
                    if (overrides.has(host)) {
                        val newHost = overrides.getString(host)
                        val newUrl = request.url.newBuilder().host(newHost).build()
                        val newRequest = request.newBuilder()
                            .url(newUrl)
                            .header("Host", newHost)
                            .build()
                        chain.proceed(newRequest)
                    } else {
                        chain.proceed(request)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AppModule", "Failed to parse domain overrides: ${e.message}")
            }
        }

        // === HTTP Proxy ===
        val proxyEnabled = prefs.getBoolean("proxy_enabled", false)
        val proxyHost = prefs.getString("proxy_host", "") ?: ""
        val proxyPort = prefs.getInt("proxy_port", 0)
        
        if (proxyEnabled && proxyHost.isNotBlank() && proxyPort > 0) {
            try {
                baseBuilder.proxy(java.net.Proxy(java.net.Proxy.Type.HTTP, java.net.InetSocketAddress(proxyHost, proxyPort)))
            } catch (e: Exception) {
                android.util.Log.e("AppModule", "Failed to setup proxy: ${e.message}")
            }
        }

        // === Giao thức kết nối ===
        val protocol = prefs.getString("conn_protocol", "cronet") ?: "cronet"
        when (protocol) {
            "http1" -> baseBuilder.protocols(listOf(Protocol.HTTP_1_1))
            "http2" -> baseBuilder.protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            else -> baseBuilder.protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
        }

        // === DNS tùy chỉnh ===
        val dns = prefs.getString("conn_dns", "cloudflare") ?: "cloudflare"
        when (dns) {
            "google" -> baseBuilder.dns(CustomDns("google"))
            "cloudflare" -> baseBuilder.dns(CustomDns("cloudflare"))
        }

        // === SSL bypass if enabled ===
        val ignoreSsl = prefs.getBoolean("conn_ignore_ssl", true)
        if (ignoreSsl) {
            try {
                val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                })
                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, trustAllCerts, SecureRandom())
                baseBuilder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                baseBuilder.hostnameVerifier { _, _ -> true }
            } catch (_: Exception) {
                // Fallback to default SSL if setup fails
            }
        }

        // === Hiệu suất ===
        val performance = prefs.getString("conn_performance", "balanced") ?: "balanced"
        when (performance) {
            "performance" -> {
                baseBuilder.connectionPool(okhttp3.ConnectionPool(10, 5, TimeUnit.MINUTES))
            }
            "battery" -> {
                baseBuilder.connectionPool(okhttp3.ConnectionPool(2, 1, TimeUnit.MINUTES))
                baseBuilder.connectTimeout(60, TimeUnit.SECONDS)
                baseBuilder.readTimeout(60, TimeUnit.SECONDS)
            }
            // "balanced" → keep defaults
        }

        val pureClient = baseBuilder.build()

        val useCronet = protocol == "cronet" && !(proxyEnabled && proxyHost.isNotBlank() && proxyPort > 0)
        var cronetInterceptorAdded = false

        if (useCronet) {
            val engine = getCronetEngine(context)
            if (engine != null) {
                try {
                    val cronetClientBuilder = pureClient.newBuilder()
                    
                    // Application Interceptor chạy trước CronetInterceptor để chuẩn bị headers và bypass nae.vn
                    cronetClientBuilder.addInterceptor { chain ->
                        val original = chain.request()
                        val host = original.url.host
                        val extensionId = original.header("X-Extension-Id")
                        var requestBuilder = original.newBuilder()

                        // Đồng bộ User-Agent tương ứng của extension hoặc dùng mặc định
                        val customUserAgent = if (!extensionId.isNullOrBlank()) {
                            prefs.getString("ext_user_agent_$extensionId", null)
                        } else {
                            prefs.getString("conn_user_agent", null)
                        }
                        
                        val defaultUserAgent = if (!customUserAgent.isNullOrBlank()) {
                            customUserAgent
                        } else {
                            com.nam.novelreader.util.UserAgentUtils.getCleanUserAgent(context)
                        }

                        if (original.header("User-Agent") == null) {
                            requestBuilder = requestBuilder.header("User-Agent", defaultUserAgent)
                        } else if (!extensionId.isNullOrBlank() && !customUserAgent.isNullOrBlank()) {
                            requestBuilder = requestBuilder.header("User-Agent", customUserAgent)
                        }

                        val finalUserAgent = requestBuilder.build().header("User-Agent") ?: defaultUserAgent
                        val isBrowserUa = finalUserAgent.contains("Mozilla", ignoreCase = true) || 
                                          finalUserAgent.contains("Chrome", ignoreCase = true)
                        if (isBrowserUa) {
                            requestBuilder = requestBuilder
                                .header("Accept-Language", "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7")
                                .header("Sec-Ch-Ua-Mobile", "?1")
                                .header("Sec-Ch-Ua-Platform", "\"Android\"")
                        }

                        // Xoá header tạm thời X-Extension-Id
                        requestBuilder.removeHeader("X-Extension-Id")

                        // Đưa Cookie thời gian thực từ CookieManager (ưu tiên base domain) + Preferences + request-original vào request
                        val baseDomainUrl = "https://$host"
                        val cookieManager = android.webkit.CookieManager.getInstance()
                        val webViewCookie = cookieManager.getCookie(baseDomainUrl)
                        val savedCookie = if (!extensionId.isNullOrBlank()) {
                            prefs.getString("ext_cookies_$extensionId", null)
                        } else null

                        val existingCookie = original.header("Cookie")
                        var finalCookie = if (isBrowserUa) {
                            mergeCookies(webViewCookie, savedCookie)
                        } else {
                            savedCookie ?: ""
                        }
                        finalCookie = mergeCookies(finalCookie, existingCookie)

                        // Lọc bỏ laravel_session bẩn khi request gửi tới nae.vn
                        if (host.contains("nae.vn", ignoreCase = true) && finalCookie.isNotBlank()) {
                            finalCookie = finalCookie.split(";")
                                .map { it.trim() }
                                .filter { !it.startsWith("laravel_session=", ignoreCase = true) }
                                .joinToString("; ")
                        }

                        if (finalCookie.isNotBlank()) {
                            requestBuilder.header("Cookie", finalCookie)
                        } else {
                            requestBuilder.removeHeader("Cookie")
                        }

                        val request = requestBuilder.build()

                        // NẾU HOST LÀ NAE.VN (TÀNG THƯ VIỆN), SUPABASE, GOOGLE HOẶC SANGTACVIET (STV), BỎ QUA CRONET, DÙNG OKHTTP THUẦN TÚY
                        val isSupabase = host.contains("supabase.co", ignoreCase = true)
                        val isGoogle = host.contains("googleapis.com", ignoreCase = true) || host.contains("google.com", ignoreCase = true)
                        val isStv = host.contains("sangtacviet", ignoreCase = true) || 
                                    host.contains("14.225.254.182") || 
                                    host.contains("103.82.20.93") || 
                                    host.contains("stv-appdomain", ignoreCase = true)
                                    
                        if (host.contains("nae.vn", ignoreCase = true) || isSupabase || isGoogle || isStv) {
                            android.util.Log.d("OkHttpDebug", "Bypassing Cronet for $host. URL: ${request.url}")
                            
                            val response = pureClient.newCall(request).execute()
                            
                            // Đồng bộ ngược Set-Cookie về CookieManager và SharedPreferences (cho tất cả các host)
                            val setCookies = response.headers("Set-Cookie")
                            if (setCookies.isNotEmpty()) {
                                setCookies.forEach { cookieHeader ->
                                    cookieManager.setCookie(baseDomainUrl, cookieHeader)
                                }
                                cookieManager.flush()

                                if (!extensionId.isNullOrBlank()) {
                                    val updatedWebViewCookie = cookieManager.getCookie(baseDomainUrl)
                                    val updatedSavedCookie = prefs.getString("ext_cookies_$extensionId", null)
                                    val newMerged = mergeCookies(updatedWebViewCookie, updatedSavedCookie)
                                    if (newMerged.isNotBlank()) {
                                        prefs.edit().putString("ext_cookies_$extensionId", newMerged).apply()
                                    }
                                }
                            }
                            return@addInterceptor response
                        }

                        android.util.Log.d("OkHttpDebug", "Cronet Request URL: ${request.url} | UA: ${request.header("User-Agent")} | Cookie: ${request.header("Cookie")} | X-Ext-Id: $extensionId")
                        if (com.nam.novelreader.data.network.AdBlocker.isAd(request.url.toString(), checkStaticResources = false)) {
                            android.util.Log.d("AdBlocker", "Blocked ad in OkHttp (Cronet): ${request.url}")
                            return@addInterceptor okhttp3.Response.Builder()
                                .request(request)
                                .protocol(okhttp3.Protocol.HTTP_1_1)
                                .code(204)
                                .message("Blocked by AdBlocker")
                                .body(okhttp3.ResponseBody.create(null, ""))
                                .build()
                        }
                        
                        val response = chain.proceed(request)

                        // Đồng bộ ngược Set-Cookie từ response Cronet về CookieManager và SharedPreferences
                        val setCookies = response.headers("Set-Cookie")
                        if (setCookies.isNotEmpty()) {
                            setCookies.forEach { cookieHeader ->
                                cookieManager.setCookie(baseDomainUrl, cookieHeader)
                            }
                            cookieManager.flush()

                            if (!extensionId.isNullOrBlank()) {
                                val updatedWebViewCookie = cookieManager.getCookie(baseDomainUrl)
                                val updatedSavedCookie = prefs.getString("ext_cookies_$extensionId", null)
                                val newMerged = mergeCookies(updatedWebViewCookie, updatedSavedCookie)
                                if (newMerged.isNotBlank()) {
                                    prefs.edit().putString("ext_cookies_$extensionId", newMerged).apply()
                                }
                            }
                        }

                        response
                    }

                    // Thêm CronetInterceptor làm Application Interceptor cuối cùng
                    val cronetInterceptor = com.google.net.cronet.okhttptransport.CronetInterceptor.newBuilder(engine).build()
                    cronetClientBuilder.addInterceptor(cronetInterceptor)
                    cronetInterceptorAdded = true
                    android.util.Log.d("AppModule", "CronetInterceptor added successfully to OkHttpClient")
                    return cronetClientBuilder.build()
                } catch (e: Exception) {
                    android.util.Log.e("AppModule", "Failed to add CronetInterceptor: ${e.message}", e)
                }
            }
        }

        // Cấu hình fallback OkHttp thông thường (chạy khi useCronet = false hoặc Cronet init fail)
        val fallbackClientBuilder = pureClient.newBuilder()
        fallbackClientBuilder.addInterceptor { chain ->
            val original = chain.request()
            var requestBuilder = original.newBuilder()
            
            val extensionId = original.header("X-Extension-Id")

            // Đồng bộ Cookie của extension vào CookieManager trước khi OkHttp BridgeInterceptor chạy
            if (!extensionId.isNullOrBlank()) {
                val customCookie = prefs.getString("ext_cookies_$extensionId", null)
                if (!customCookie.isNullOrBlank()) {
                    val cookieManager = android.webkit.CookieManager.getInstance()
                    val host = original.url.host
                    val baseDomainUrl = "https://$host"
                    
                    val domainAttr = if (host.isNotBlank()) {
                        val cleanHost = if (host.startsWith("www.")) host.substring(4) else host
                        "; Domain=.$cleanHost"
                    } else ""

                    customCookie.split(";").forEach { part ->
                        val trimmed = part.trim()
                        if (trimmed.isNotEmpty()) {
                            cookieManager.setCookie(baseDomainUrl, "$trimmed$domainAttr; Path=/")
                            if (host.isNotBlank()) {
                                cookieManager.setCookie(baseDomainUrl, "$trimmed; Domain=$host; Path=/")
                            }
                        }
                    }
                    cookieManager.flush()
                }
            }

            val customUserAgent = if (!extensionId.isNullOrBlank()) {
                prefs.getString("ext_user_agent_$extensionId", null)
            } else {
                prefs.getString("conn_user_agent", null)
            }
            
            val defaultUserAgent = if (!customUserAgent.isNullOrBlank()) {
                customUserAgent
            } else {
                com.nam.novelreader.util.UserAgentUtils.getCleanUserAgent(context)
            }

            if (original.header("User-Agent") == null) {
                requestBuilder = requestBuilder.header("User-Agent", defaultUserAgent)
            } else if (!extensionId.isNullOrBlank() && !customUserAgent.isNullOrBlank()) {
                requestBuilder = requestBuilder.header("User-Agent", customUserAgent)
            }
            
            val finalUserAgent = requestBuilder.build().header("User-Agent") ?: defaultUserAgent
            val isBrowserUa = finalUserAgent.contains("Mozilla", ignoreCase = true) || 
                              finalUserAgent.contains("Chrome", ignoreCase = true)
            if (isBrowserUa) {
                requestBuilder = requestBuilder
                    .header("Accept-Language", "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7")
                    .header("Sec-Ch-Ua-Mobile", "?1")
                    .header("Sec-Ch-Ua-Platform", "\"Android\"")
            }
            val request = requestBuilder.build()
            
            if (com.nam.novelreader.data.network.AdBlocker.isAd(request.url.toString(), checkStaticResources = false)) {
                android.util.Log.d("AdBlocker", "Blocked ad in OkHttp: ${request.url}")
                return@addInterceptor okhttp3.Response.Builder()
                    .request(request)
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(204)
                    .message("Blocked by AdBlocker")
                    .body(okhttp3.ResponseBody.create(null, ""))
                    .build()
            }
            
            com.nam.novelreader.data.network.CookieBypassState.skipWebViewCookie.set(!isBrowserUa)
            try {
                chain.proceed(request)
            } finally {
                com.nam.novelreader.data.network.CookieBypassState.skipWebViewCookie.remove()
            }
        }
        .addNetworkInterceptor { chain ->
            val original = chain.request()
            val extensionId = original.header("X-Extension-Id")
            if (extensionId.isNullOrBlank()) {
                return@addNetworkInterceptor chain.proceed(original)
            }

            var requestBuilder = original.newBuilder()
            requestBuilder.removeHeader("X-Extension-Id")

            val host = original.url.host
            val baseDomainUrl = "https://$host"
            val cookieManager = android.webkit.CookieManager.getInstance()
            val webViewCookie = cookieManager.getCookie(baseDomainUrl)

            val customCookie = prefs.getString("ext_cookies_$extensionId", null)
            val existingCookie = original.header("Cookie")
            var finalCookie = mergeCookies(webViewCookie, customCookie)
            finalCookie = mergeCookies(finalCookie, existingCookie)

            var processedCookie = finalCookie
            if (original.url.host.contains("nae.vn", ignoreCase = true) && !processedCookie.isNullOrBlank()) {
                processedCookie = processedCookie.split(";")
                    .map { it.trim() }
                    .filter { !it.startsWith("laravel_session=", ignoreCase = true) }
                    .joinToString("; ")
            }

            if (!processedCookie.isNullOrBlank()) {
                requestBuilder = requestBuilder.header("Cookie", processedCookie)
            } else {
                requestBuilder.removeHeader("Cookie")
            }

            val customUserAgent = prefs.getString("ext_user_agent_$extensionId", null)
            if (!customUserAgent.isNullOrBlank()) {
                requestBuilder = requestBuilder.header("User-Agent", customUserAgent)
            }

            val finalRequest = requestBuilder.build()
            android.util.Log.d("OkHttpDebug", "OkHttp Network Request URL: ${finalRequest.url} | UA: ${finalRequest.header("User-Agent")} | Cookie: ${finalRequest.header("Cookie")} | X-Ext-Id: $extensionId")
            
            val response = chain.proceed(finalRequest)

            // Đồng bộ Set-Cookie từ response ngược về CookieManager và SharedPreferences (luồng fallback)
            val setCookies = response.headers("Set-Cookie")
            if (setCookies.isNotEmpty()) {
                setCookies.forEach { cookieHeader ->
                    cookieManager.setCookie(baseDomainUrl, cookieHeader)
                }
                cookieManager.flush()

                val updatedWebViewCookie = cookieManager.getCookie(baseDomainUrl)
                val updatedSavedCookie = prefs.getString("ext_cookies_$extensionId", null)
                val newMerged = mergeCookies(updatedWebViewCookie, updatedSavedCookie)
                if (newMerged.isNotBlank()) {
                    prefs.edit().putString("ext_cookies_$extensionId", newMerged).apply()
                }
            }

            response
        }

        return fallbackClientBuilder.build()
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "novel_reader.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideNovelDao(db: AppDatabase): NovelDao = db.novelDao()

    @Provides
    fun provideChapterDao(db: AppDatabase): ChapterDao = db.chapterDao()

    @Provides
    fun provideExtensionDao(db: AppDatabase): ExtensionDao = db.extensionDao()

    @Provides
    fun provideRepositoryDao(db: AppDatabase): RepositoryDao = db.repositoryDao()

    @Provides
    fun provideReadingHistoryDao(db: AppDatabase): ReadingHistoryDao = db.readingHistoryDao()

    @Provides
    fun provideDownloadTaskDao(db: AppDatabase): DownloadTaskDao = db.downloadTaskDao()
}

/**
 * Custom DNS resolver — dùng DNS server chỉ định thay vì mặc định hệ thống.
 * Hỗ trợ Google (8.8.8.8) và Cloudflare (1.1.1.1).
 */
private class CustomDns(private val provider: String) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val systemAddresses = try {
            Dns.SYSTEM.lookup(hostname)
        } catch (e: Exception) {
            emptyList()
        }

        try {
            val urlString = when (provider) {
                "google" -> "https://8.8.8.8/resolve?name=$hostname&type=A"
                "cloudflare" -> "https://1.1.1.1/dns-query?name=$hostname&type=A"
                else -> return systemAddresses
            }

            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/dns-json")
            conn.connectTimeout = 3000
            conn.readTimeout = 3000

            if (conn is javax.net.ssl.HttpsURLConnection) {
                conn.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
            }

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()

                val json = org.json.JSONObject(response.toString())
                val answers = json.optJSONArray("Answer")
                if (answers != null && answers.length() > 0) {
                    val list = mutableListOf<InetAddress>()
                    for (i in 0 until answers.length()) {
                        val answer = answers.getJSONObject(i)
                        val type = answer.optInt("type")
                        if (type == 1) { // Type A (IPv4)
                            val data = answer.getString("data")
                            list.add(InetAddress.getByName(data))
                        }
                    }
                    if (list.isNotEmpty()) {
                        return list
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (systemAddresses.isNotEmpty()) {
            return systemAddresses
        }
        throw java.net.UnknownHostException("Could not resolve host $hostname")
    }
}

private fun mergeCookies(cookie1: String?, cookie2: String?): String {
    val cookieMap = mutableMapOf<String, String>()
    if (!cookie1.isNullOrBlank()) {
        cookie1.split(";").forEach { part ->
            val pair = part.trim().split("=", limit = 2)
            if (pair.size == 2) {
                cookieMap[pair[0].trim()] = pair[1].trim()
            }
        }
    }
    if (!cookie2.isNullOrBlank()) {
        cookie2.split(";").forEach { part ->
            val pair = part.trim().split("=", limit = 2)
            if (pair.size == 2) {
                cookieMap[pair[0].trim()] = pair[1].trim()
            }
        }
    }
    return cookieMap.map { "${it.key}=${it.value}" }.joinToString("; ")
}

