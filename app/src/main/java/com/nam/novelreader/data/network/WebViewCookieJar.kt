package com.nam.novelreader.data.network

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * WebViewCookieJar — Đồng bộ Cookie giữa Android WebView và OkHttpClient.
 * Điều này rất quan trọng để bypass Cloudflare: khi WebView vượt qua CAPTCHA, 
 * nó sẽ lưu `cf_clearance` vào CookieManager. OkHttp cần đọc từ đây để gửi request hợp lệ.
 */
object CookieBypassState {
    val skipWebViewCookie = ThreadLocal<Boolean>()
}

class WebViewCookieJar : CookieJar {
    private val cookieManager = CookieManager.getInstance()
    private val memoryCookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        val baseDomainUrl = "https://$host"
        
        // 1. Lưu vào Memory (để response nhanh)
        val existing = memoryCookieStore[host] ?: mutableListOf()
        cookies.forEach { cookie ->
            existing.removeAll { it.name == cookie.name }
            existing.add(cookie)
        }
        memoryCookieStore[host] = existing

        // 2. Đồng bộ xuống Android CookieManager qua base domain
        cookies.forEach { cookie ->
            val cookieString = "${cookie.name}=${cookie.value}; domain=${cookie.domain}; path=${cookie.path}"
            cookieManager.setCookie(baseDomainUrl, cookieString)
        }
        cookieManager.flush()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (CookieBypassState.skipWebViewCookie.get() == true) {
            return emptyList()
        }
        val host = url.host
        val baseDomainUrl = "https://$host"
        val cookies = mutableListOf<Cookie>()

        // 1. Lấy từ Android CookieManager qua base domain (Bao gồm cookie sinh ra từ WebView như cf_clearance)
        val cookieHeader = cookieManager.getCookie(baseDomainUrl)
        if (cookieHeader != null) {
            val cookieStrings = cookieHeader.split("; ")
            for (cookieStr in cookieStrings) {
                val parts = cookieStr.split("=", limit = 2)
                if (parts.size == 2) {
                    val name = parts[0]
                    val value = parts[1]
                    val baseDomain = getBaseDomain(host)
                    val builder = Cookie.Builder()
                        .name(name)
                        .value(value)
                        .domain(baseDomain) // Sử dụng baseDomain để có phạm vi rộng nhất (wildcard)
                        .expiresAt(253402300799999L) // Gán max expiration để không bị filter lọc mất
                    cookies.add(builder.build())
                }
            }
        }

        // 2. Lấy thêm từ Memory Store (tránh sót các session memory-only)
        val now = System.currentTimeMillis()
        memoryCookieStore.forEach { (domain, list) ->
            if (host.endsWith(domain) || domain.endsWith(host)) {
                // Merge, ưu tiên cookie từ CookieManager nếu trùng tên
                val existingNames = cookies.map { it.name }.toSet()
                list.forEach { memCookie ->
                    if (memCookie.name !in existingNames) {
                        // Chỉ thêm nếu cookie trong memory store chưa hết hạn
                        if (memCookie.expiresAt > now || memCookie.expiresAt == 253402300799999L) {
                            cookies.add(memCookie)
                        }
                    }
                }
            }
        }

        return cookies
    }

    private fun getBaseDomain(host: String): String {
        val parts = host.split(".")
        return if (parts.size >= 2) {
            val last = parts.last()
            val secondLast = parts[parts.size - 2]
            // Nếu là dạng domain.com.vn, co.uk, net.vn...
            if (last.length <= 3 && secondLast.length <= 3 && parts.size >= 3) {
                parts.takeLast(3).joinToString(".")
            } else {
                parts.takeLast(2).joinToString(".")
            }
        } else {
            host
        }
    }
}
