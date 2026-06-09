package com.nam.novelreader.data.network

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.WebResourceResponse
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * AdBlocker — Bộ chặn quảng cáo WebView & HTTP Requests nâng cấp.
 * Vận hành giống hệt VBook gốc (chặn phần mở rộng tĩnh, video và domain hosts).
 */
object AdBlocker {
    private const val TAG = "AdBlocker"

    // Bộ chặn đuôi mở rộng file tĩnh (giảm 90% băng thông)
    private val BLOCKED_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "gif", "webp", "svg", "ico",
        "css", "woff", "woff2", "ttf", "otf", "eot"
    )

    // Bộ chặn định dạng video/media để tránh tải ngầm tốn băng thông
    private val VIDEO_EXTENSIONS = setOf(
        "m3u8", "m3u", "mpd", "mp4", "m4v", "webm", "mkv", "mov", "ts"
    )

    // Từ khóa đường dẫn quảng cáo phổ biến
    private val AD_KEYWORD_IN_PATH = listOf(
        "/ads/", "/ad/", "/advert", "/banner", "/popunder", "/popup", "?ad=", "&ad="
    )

    // Hosts mặc định (Hardcoded Fallback)
    private val DEFAULT_AD_HOSTS = setOf(
        "googleads.g.doubleclick.net",
        "pagead2.googlesyndication.com",
        "adservice.google.com",
        "adservice.google.com.vn",
        "securepubads.g.doubleclick.net",
        "www.google-analytics.com",
        "ssl.google-analytics.com",
        "analytics.tiktok.com",
        "connect.facebook.net",
        "www.facebook.com",
        "ads.twitter.com",
        "syndication.twitter.com",
        "static.criteo.net",
        "bidder.criteo.com",
        "cdn.taboola.com",
        "trc.taboola.com",
        "popads.net",
        "serve.popads.net",
        "popcash.net",
        "propellerads.com",
        "onclickads.net",
        "exoclick.com",
        "realsrv.com",
        "mgid.com",
        "jsc.mgid.com",
        "outbrain.com",
        "widgets.outbrain.com",
        "ad.directrev.com",
        "a.medyanetads.com",
        "ads.pubmatic.com",
        "c.amazon-adsystem.com",
        "s.amazon-adsystem.com",
        "aax.amazon-adsystem.com",
        "admicro.vn",
        "cpx.admicro.vn",
        "ssp.admicro.vn",
        "ants.vn",
        "e.ants.vn"
    )

    // Tập hợp hosts động nạp từ adblock.txt
    private val adHosts = ConcurrentHashMap.newKeySet<String>().apply {
        addAll(DEFAULT_AD_HOSTS)
    }

    private var isInitialized = false

    /**
     * Khởi tạo AdBlocker, nạp danh sách domain từ assets/adblock.txt
     */
    fun init(context: Context) {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return
            try {
                context.assets.open("adblock.txt").use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        var line: String?
                        var count = 0
                        while (reader.readLine().also { line = it } != null) {
                            val cleanLine = line!!.trim()
                            if (cleanLine.isEmpty() || cleanLine.startsWith("#")) continue
                            
                            // Hỗ trợ định dạng hosts file (127.0.0.1 domain.com) hoặc domain thô
                            val parts = cleanLine.split(Regex("\\s+"))
                            val domain = if (parts.size > 1) parts[1] else parts[0]
                            val cleanDomain = domain.lowercase(Locale.ROOT).trim()
                            if (cleanDomain.isNotEmpty()) {
                                adHosts.add(cleanDomain)
                                count++
                            }
                        }
                        Log.d(TAG, "Loaded $count domain rules from adblock.txt")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load adblock.txt from assets, using fallback rules", e)
            }
            isInitialized = true
        }
    }

    /**
     * Trích xuất phần mở rộng (file extension) từ URL
     */
    private fun getFileExtension(url: String): String {
        try {
            // Loại bỏ query parameter (?) và anchor (#)
            var cleanUrl = url.substringBefore('?').substringBefore('#')
            // Lấy phần sau dấu chấm cuối cùng
            val lastDotIndex = cleanUrl.lastIndexOf('.')
            if (lastDotIndex != -1 && lastDotIndex < cleanUrl.length - 1) {
                val slashIndex = cleanUrl.lastIndexOf('/')
                if (slashIndex < lastDotIndex) {
                    return cleanUrl.substring(lastDotIndex + 1).lowercase(Locale.ROOT)
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return ""
    }

    /**
     * Kiểm tra xem URL có phải là quảng cáo hoặc tài nguyên bị chặn (image, css, video...) hay không.
     *
     * @param checkStaticResources Nếu true (dùng cho WebView), chặn cả các file tĩnh như ảnh, css, video.
     *                            Nếu false (dùng cho OkHttpClient), cho phép tải ảnh/video bình thường, chỉ chặn ad hosts.
     */
    fun isAd(url: String, checkStaticResources: Boolean = true): Boolean {
        try {
            val lowerUrl = url.lowercase(Locale.ROOT)
            
            // 1. Chặn theo phần mở rộng của file tĩnh (ảnh, css, font...) hoặc video (Chỉ áp dụng cho WebView)
            if (checkStaticResources) {
                val ext = getFileExtension(lowerUrl)
                if (ext.isNotEmpty()) {
                    if (BLOCKED_EXTENSIONS.contains(ext)) {
                        return true
                    }
                    if (VIDEO_EXTENSIONS.contains(ext)) {
                        return true
                    }
                }
            }

            // 2. Chặn theo Host/Domain
            val uri = Uri.parse(url)
            val host = uri.host?.lowercase(Locale.ROOT)
            if (host != null) {
                if (adHosts.contains(host)) return true
                if (adHosts.any { host.endsWith(".$it") }) return true
            }

            // 3. Chặn theo từ khóa đường dẫn
            if (AD_KEYWORD_IN_PATH.any { lowerUrl.contains(it) }) {
                return true
            }

        } catch (e: Exception) {
            // Ignore parse errors
        }
        return false
    }

    /**
     * Tạo phản hồi rỗng để chặn tải tài nguyên WebView
     */
    fun createEmptyResource(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            ByteArrayInputStream(ByteArray(0))
        )
    }
}
