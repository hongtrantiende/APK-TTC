package com.nam.novelreader.data.network

import android.content.Context
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class ProxyRotationInterceptor(private val context: Context) : Interceptor {
    private val prefs = context.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE)

    companion object {
        private val lock = ReentrantLock()
        private var lastRotateTime = 0L
        private const val ROTATE_COOLDOWN_MS = 60000L // 60 seconds cooldown
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        
        // If it's the rotate API itself, don't intercept to prevent loops
        if (request.url.toString().contains("rotate")) {
            return chain.proceed(request)
        }

        var response: Response? = null
        var exception: Exception? = null

        try {
            response = chain.proceed(request)
        } catch (e: Exception) {
            exception = e
        }

        val isNetworkError = exception is SocketTimeoutException || exception is java.net.ConnectException
        val isBlockedHttpCode = response?.code == 403 || response?.code == 429 || response?.code == 503

        if (isNetworkError || isBlockedHttpCode) {
            val rotateApi = prefs.getString("proxy_rotate_api", "") ?: ""
            if (rotateApi.isNotBlank() && prefs.getBoolean("proxy_enabled", false)) {
                val rotated = rotateProxy(rotateApi)
                if (rotated) {
                    response?.close() // Close the failed response
                    // Retry request after rotation
                    try {
                        return chain.proceed(request)
                    } catch (e: Exception) {
                        throw e
                    }
                }
            }
        }

        if (exception != null) {
            throw exception
        }

        return response!!
    }

    private fun rotateProxy(apiUrl: String): Boolean {
        var rotated = false
        lock.withLock {
            val now = System.currentTimeMillis()
            if (now - lastRotateTime < ROTATE_COOLDOWN_MS) {
                // Already rotated recently by another thread. 
                rotated = true
            } else {
                android.util.Log.d("ProxyRotator", "Triggering proxy rotation via API: $apiUrl")
                try {
                    // Call API using a new client WITHOUT proxy
                    val directClient = OkHttpClient.Builder()
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(15, TimeUnit.SECONDS)
                        .build()

                    val request = Request.Builder().url(apiUrl).build()
                    val response = directClient.newCall(request).execute()

                    if (response.isSuccessful) {
                        android.util.Log.d("ProxyRotator", "Proxy rotated successfully.")
                        lastRotateTime = System.currentTimeMillis()
                        rotated = true
                    } else {
                        android.util.Log.e("ProxyRotator", "Failed to rotate proxy: HTTP ${response.code}")
                    }
                    response.close()
                } catch (e: Exception) {
                    android.util.Log.e("ProxyRotator", "Exception while rotating proxy: ${e.message}")
                }
            }
        }
        
        // Wait outside the lock so other threads aren't blocked from checking the time
        if (rotated) {
            Thread.sleep(2000)
        }
        
        return rotated


    }
}
