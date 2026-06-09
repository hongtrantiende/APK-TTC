package com.nam.novelreader.util

import android.content.Context
import android.webkit.WebSettings

/**
 * Tiện ích xử lý User-Agent để vượt qua các bộ chặn bot (như Cloudflare, Sangtacviet).
 */
object UserAgentUtils {
    private var cleanUserAgent: String? = null

    /**
     * Lấy User-Agent tiêu chuẩn của trình duyệt Chrome di động sạch sẽ (không chứa tag WebView).
     */
    fun getCleanUserAgent(context: Context): String {
        cleanUserAgent?.let { return it }
        val ua = try {
            WebSettings.getDefaultUserAgent(context)
        } catch (e: Exception) {
            "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"
        }
        
        // Loại bỏ "; wv" và "Version/4.0" là các dấu hiệu nhận biết WebView của Android
        // giúp giả lập trình duyệt Chrome thật trên thiết bị Android
        val clean = ua.replace("; wv", "")
                      .replace(Regex("Version/\\d+\\.\\d+\\s?"), "")
        cleanUserAgent = clean
        return clean
    }
}
