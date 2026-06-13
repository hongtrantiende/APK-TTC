package com.nam.novelreader.extension.runtime.api

import android.util.Log
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.mozilla.javascript.*
import org.mozilla.javascript.Context as RhinoContext
import android.content.Context
import android.webkit.CookieManager
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * JSFetchFunction — bridge cho fetch() global function.
 *
 * Tương ứng: com.vbook.app.extensions.js.module.http.JSFetchFunction
 *
 * Cú pháp trong extension JS:
 * ```js
 * let res = fetch(url, { method: "GET", headers: {...}, queries: {...}, body: "..." });
 * let html = res.html();    // → JSDocument (Jsoup)
 * let text = res.text();    // → raw string
 * let json = res.json();    // → parsed JSON object
 * let ok   = res.ok;        // → boolean
 * ```
 */
object JSFetchFunction {

    private const val TAG = "JSFetch"
    private val semaphores = ConcurrentHashMap<String, Semaphore>()

    fun inject(
        ctx: RhinoContext,
        scope: ScriptableObject,
        httpClient: OkHttpClient,
        extensionId: String,
        context: Context
    ) {
        val fetchFn = object : BaseFunction() {
            override fun call(
                cx: RhinoContext,
                parentScope: Scriptable,
                thisObj: Scriptable,
                args: Array<out Any?>
            ): Any {
                if (args.isEmpty()) throw EvaluatorException("fetch() requires at least 1 argument (url)")

                val url = RhinoContext.toString(args[0])
                val options = if (args.size > 1 && args[1] is Scriptable) args[1] as Scriptable else null

                return executeFetch(cx, parentScope, httpClient, url, options, extensionId, context)
            }
        }

        ScriptableObject.putProperty(scope, "fetch", fetchFn)
    }

    private fun executeFetch(
        ctx: RhinoContext,
        scope: Scriptable,
        httpClient: OkHttpClient,
        url: String,
        options: Scriptable?,
        extensionId: String,
        context: Context
    ): Scriptable {
        try {
            // Parse options
            var method = "GET"
            val headersBuilder = Headers.Builder()
            var urlBuilder = url.toHttpUrl().newBuilder()
            var body: RequestBody? = null

            options?.let { opts ->
                // Method
                getProperty(opts, "method")?.let { method = it.uppercase() }

                // Headers
                getScriptableProperty(opts, "headers")?.let { headers ->
                    for (id in (headers as ScriptableObject).ids) {
                        val key = id.toString()
                        val value = RhinoContext.toString(headers.get(key, headers))
                        headersBuilder.add(key, value)
                    }
                }

                // Query parameters (hỗ trợ cả queries và params cho cào API)
                val queriesProp = getScriptableProperty(opts, "queries") ?: getScriptableProperty(opts, "params")
                queriesProp?.let { queries ->
                    for (id in (queries as ScriptableObject).ids) {
                        val key = id.toString()
                        val value = RhinoContext.toString(queries.get(key, queries))
                        urlBuilder = urlBuilder.addQueryParameter(key, value)
                    }
                }

                val contentType = headersBuilder.get("Content-Type") ?: headersBuilder.get("content-type")

                // Body (for POST) - Hỗ trợ cả dữ liệu nhị phân Blob và String thường
                val rawBody = opts.get("body", opts)
                if (rawBody != null && rawBody != Scriptable.NOT_FOUND) {
                    val finalContentType = contentType ?: "application/x-www-form-urlencoded"
                    if (rawBody is Scriptable && RhinoContext.toString(rawBody.get("_isBlob", rawBody)) == "true") {
                        val base64 = RhinoContext.toString(rawBody.get("_base64", rawBody))
                        val bytes = try {
                            android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                        } catch (e: Exception) {
                            base64.toByteArray()
                        }
                        body = bytes.toRequestBody(finalContentType.toMediaType())
                    } else {
                        val bodyStr = RhinoContext.toString(rawBody)
                        body = bodyStr.toRequestBody(finalContentType.toMediaType())
                    }
                }

                // JSON body
                getProperty(opts, "json")?.let {
                    val finalContentType = contentType ?: "application/json; charset=utf-8"
                    body = it.toRequestBody(finalContentType.toMediaType())
                }
            }

            val requiresRequestBody = method == "POST" || method == "PUT" || method == "PATCH"
            if (body == null && requiresRequestBody) {
                val contentType = headersBuilder.get("Content-Type") ?: headersBuilder.get("content-type") ?: "application/x-www-form-urlencoded"
                body = "".toRequestBody(contentType.toMediaType())
            }

            // Không tự thêm Content-Length — để OkHttp tự quản lý (VBook gốc)


            // Inject & merge extension cookies with real-time CookieManager cookies
            val prefs = context.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE)
            val customCookie = prefs.getString("ext_cookies_$extensionId", null)
            val cookieManager = CookieManager.getInstance()
            val webViewCookie = cookieManager.getCookie(url)
            val existingCookie = headersBuilder.get("Cookie")

            val finalCookie = mergeCookies(webViewCookie, customCookie, existingCookie)
            if (finalCookie.isNotBlank()) {
                headersBuilder.set("Cookie", finalCookie)
            }

            // Default User-Agent (Sử dụng WebView UA để khớp với cookie Cloudflare)
            val customUa = prefs.getString("ext_user_agent_$extensionId", null)
            if (!customUa.isNullOrBlank()) {
                headersBuilder.set("User-Agent", customUa)
            } else if (headersBuilder.get("User-Agent") == null) {
                val defaultUa = com.nam.novelreader.util.UserAgentUtils.getCleanUserAgent(context)
                headersBuilder.add("User-Agent", defaultUa)
            }

            // Thêm X-Extension-Id để AppModule nhận diện và định tuyến qua OkHttp bypass Cronet
            headersBuilder.set("X-Extension-Id", extensionId)
            
            val request = Request.Builder()
                .url(urlBuilder.build())
                .headers(headersBuilder.build())
                .method(method, body)
                .build()

            Log.d(TAG, "Fetching: ${request.url}")

            val extTimeout = prefs.getInt("ext_connection_timeout_$extensionId", prefs.getInt("conn_timeout", 30))
            val activeClient = if (extTimeout > 0) {
                httpClient.newBuilder()
                    .connectTimeout(extTimeout.toLong(), TimeUnit.SECONDS)
                    .readTimeout(extTimeout.toLong(), TimeUnit.SECONDS)
                    .writeTimeout(extTimeout.toLong(), TimeUnit.SECONDS)
                    .build()
            } else {
                httpClient
            }

            val response = activeClient.newCall(request).execute()

            // Sync Set-Cookie back to WebView CookieManager
            val setCookies = response.headers("Set-Cookie")
            if (setCookies.isNotEmpty()) {
                setCookies.forEach { cookieHeader ->
                    cookieManager.setCookie(url, cookieHeader)
                }
                cookieManager.flush()
            }

            // Save updated cookies to preferences for persistence
            val updatedCookie = cookieManager.getCookie(url)
            if (!updatedCookie.isNullOrBlank()) {
                prefs.edit().putString("ext_cookies_$extensionId", updatedCookie).apply()
            }
            
            // Build response headers JS object & raw maps
            val respHeadersMap = mutableMapOf<String, String>()
            val jsonRespHeaders = org.json.JSONObject()
            response.headers.forEach { (name, value) ->
                respHeadersMap[name.lowercase()] = value
                jsonRespHeaders.put(name, value)
            }
            val jsRespHeaders = NativeJSON.parse(ctx, scope, jsonRespHeaders.toString(), org.mozilla.javascript.Callable { _, _, _, args ->
                args?.getOrNull(1)
            })

            // Build request headers JS object & raw maps
            val reqHeadersMap = mutableMapOf<String, String>()
            val jsonReqHeaders = org.json.JSONObject()
            request.headers.forEach { (name, value) ->
                reqHeadersMap[name.lowercase()] = value
                jsonReqHeaders.put(name, value)
            }
            val jsReqHeaders = NativeJSON.parse(ctx, scope, jsonReqHeaders.toString(), org.mozilla.javascript.Callable { _, _, _, args ->
                args?.getOrNull(1)
            })


            val bodyBytes = response.body?.use { it.bytes() }
            val jsRequest = JSHttpRequest(
                url = request.url.toString(),
                headers = jsReqHeaders ?: ctx.newObject(scope),
                rawHeadersMap = reqHeadersMap
            )
            
            val jsResponse = JSHttpResponse(
                ctx = ctx,
                scope = scope,
                bodyBytes = bodyBytes,
                ok = response.isSuccessful,
                status = response.code,
                statusText = response.message,
                url = response.request.url.toString(),
                headers = jsRespHeaders ?: ctx.newObject(scope),
                request = jsRequest,
                rawHeadersMap = respHeadersMap
            )
            
            return RhinoContext.javaToJS(jsResponse, scope) as Scriptable

        } catch (e: Exception) {
            Log.e(TAG, "Fetch error: ${e.message}")
            val jsRequest = JSHttpRequest(
                url = url,
                headers = ctx.newObject(scope),
                rawHeadersMap = emptyMap()
            )
            val jsResponse = JSHttpResponse(
                ctx = ctx,
                scope = scope,
                bodyBytes = null,
                ok = false,
                status = 0,
                statusText = e.message ?: "Unknown",
                url = url,
                headers = ctx.newObject(scope),
                request = jsRequest,
                rawHeadersMap = emptyMap()
            )
            return RhinoContext.javaToJS(jsResponse, scope) as Scriptable
        }
    }


    private fun getProperty(obj: Scriptable, name: String): String? {
        val value = obj.get(name, obj)
        if (value == Scriptable.NOT_FOUND || value == null) return null
        return RhinoContext.toString(value)
    }

    private fun getScriptableProperty(obj: Scriptable, name: String): Scriptable? {
        val value = obj.get(name, obj)
        if (value == Scriptable.NOT_FOUND || value !is Scriptable) return null
        return value
    }

    private fun mergeCookies(cookie1: String?, cookie2: String?, cookie3: String? = null): String {
        val cookieMap = mutableMapOf<String, String>()
        listOf(cookie1, cookie2, cookie3).forEach { cookieStr ->
            if (!cookieStr.isNullOrBlank()) {
                cookieStr.split(";").forEach { part ->
                    val pair = part.trim().split("=", limit = 2)
                    if (pair.size == 2) {
                        cookieMap[pair[0].trim()] = pair[1].trim()
                    }
                }
            }
        }
        return cookieMap.map { "${it.key}=${it.value}" }.joinToString("; ")
    }
}
