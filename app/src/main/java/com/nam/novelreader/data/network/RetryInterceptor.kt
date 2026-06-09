package com.nam.novelreader.data.network

import android.content.Context
import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * RetryInterceptor — tự động retry khi request fail.
 * Số lần retry đọc từ SharedPreferences (conn_retry).
 * Dùng exponential backoff: 500ms → 1s → 2s → ...
 */
class RetryInterceptor(
    private val context: Context
) : Interceptor {

    companion object {
        private const val TAG = "RetryInterceptor"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val prefs = context.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE)
        val maxRetries = prefs.getInt("conn_retry", 2)
        val request = chain.request()

        var lastException: IOException? = null
        for (attempt in 0..maxRetries) {
            try {
                val response = chain.proceed(request)
                // Nếu server trả lỗi 5xx và còn retry, thử lại
                if (response.code in 500..599 && attempt < maxRetries) {
                    response.close()
                    val delayMs = 500L * (1 shl attempt) // exponential backoff
                    Log.w(TAG, "Server error ${response.code}, retry $attempt after ${delayMs}ms")
                    Thread.sleep(delayMs)
                    continue
                }
                return response
            } catch (e: IOException) {
                lastException = e
                if (attempt < maxRetries) {
                    val delayMs = 500L * (1 shl attempt)
                    Log.w(TAG, "IO error, retry $attempt after ${delayMs}ms: ${e.message}")
                    try {
                        Thread.sleep(delayMs)
                    } catch (_: InterruptedException) {
                        throw e
                    }
                }
            }
        }
        throw lastException ?: IOException("Request failed after $maxRetries retries")
    }
}
