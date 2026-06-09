package com.nam.novelreader.data.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * InMemoryCookieJar — Lưu trữ Cookie trong bộ nhớ để duy trì phiên làm việc (session).
 * Giúp các extension vượt qua các cơ chế kiểm tra cookie của web truyện.
 */
class InMemoryCookieJar : CookieJar {
    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        val existing = cookieStore[host] ?: mutableListOf()
        
        cookies.forEach { cookie ->
            // Loại bỏ cookie cũ trùng tên
            existing.removeAll { it.name == cookie.name }
            existing.add(cookie)
        }
        cookieStore[host] = existing
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val cookies = mutableListOf<Cookie>()
        
        // Lấy cookies của host hiện tại hoặc domain cha/con liên quan
        cookieStore.forEach { (domain, list) ->
            if (host.endsWith(domain) || domain.endsWith(host)) {
                cookies.addAll(list)
            }
        }
        
        // Lọc các cookie đã hết hạn
        val now = System.currentTimeMillis()
        return cookies.filter { it.expiresAt > now }
    }
}
