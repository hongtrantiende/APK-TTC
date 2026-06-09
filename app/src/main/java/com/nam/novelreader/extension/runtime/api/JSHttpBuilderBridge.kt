package com.nam.novelreader.extension.runtime.api

import android.content.Context
import android.util.Base64
import android.util.Log
import android.webkit.CookieManager
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.mozilla.javascript.*
import org.mozilla.javascript.Context as RhinoContext
import java.util.concurrent.TimeUnit

/**
 * JSHttpBuilderBridge — implement `Http.get(url)` / `Http.post(url)` builder pattern.
 *
 * Tương ứng: core.js → Http object, _HttpGet(), _HttpPost()
 *
 * Cú pháp JS:
 * ```js
 * let html = Http.get(url).headers({...}).params({...}).html("utf-8");
 * let text = Http.post(url).headers({...}).body("key=val").string();
 * let blob = Http.get(url).blob();
 * ```
 */
object JSHttpBuilderBridge {

    private const val TAG = "JSHttp"

    fun inject(
        ctx: RhinoContext,
        scope: ScriptableObject,
        httpClient: OkHttpClient,
        extensionId: String,
        appContext: Context
    ) {
        val httpObj = ctx.newObject(scope)

        // Http.get(url) → returns builder object
        val getFn = object : BaseFunction() {
            override fun call(cx: RhinoContext, s: Scriptable, t: Scriptable, args: Array<out Any?>): Any {
                val url = RhinoContext.toString(args.getOrNull(0) ?: "")
                return createGetBuilder(cx, s, httpClient, url, extensionId, appContext)
            }
        }
        ScriptableObject.putProperty(httpObj, "get", getFn)

        // Http.post(url) → returns builder object
        val postFn = object : BaseFunction() {
            override fun call(cx: RhinoContext, s: Scriptable, t: Scriptable, args: Array<out Any?>): Any {
                val url = RhinoContext.toString(args.getOrNull(0) ?: "")
                return createPostBuilder(cx, s, httpClient, url, extensionId, appContext)
            }
        }
        ScriptableObject.putProperty(httpObj, "post", postFn)

        ScriptableObject.putProperty(scope, "Http", httpObj)
    }

    /**
     * Tạo GET builder object với chain methods: headers, params, queries, timeout, string, blob, html, url
     */
    private fun createGetBuilder(
        ctx: RhinoContext,
        scope: Scriptable,
        httpClient: OkHttpClient,
        url: String,
        extensionId: String,
        appContext: Context
    ): Scriptable {
        val builder = ctx.newObject(scope)
        val headersMap = mutableMapOf<String, String>()
        val queryParams = mutableMapOf<String, String>()
        var timeoutMs = 30000L

        // .headers({...}) → this
        val headersFn = object : BaseFunction() {
            override fun call(cx: RhinoContext, s: Scriptable, t: Scriptable, args: Array<out Any?>): Any {
                (args.getOrNull(0) as? ScriptableObject)?.let { obj ->
                    for (id in obj.ids) {
                        val key = id.toString()
                        headersMap[key] = RhinoContext.toString(obj.get(key, obj))
                    }
                }
                return builder
            }
        }
        ScriptableObject.putProperty(builder, "headers", headersFn)

        // .params({...}) → this (alias for queries)
        val paramsFn = object : BaseFunction() {
            override fun call(cx: RhinoContext, s: Scriptable, t: Scriptable, args: Array<out Any?>): Any {
                (args.getOrNull(0) as? ScriptableObject)?.let { obj ->
                    for (id in obj.ids) {
                        val key = id.toString()
                        queryParams[key] = RhinoContext.toString(obj.get(key, obj))
                    }
                }
                return builder
            }
        }
        ScriptableObject.putProperty(builder, "params", paramsFn)
        ScriptableObject.putProperty(builder, "queries", paramsFn)

        // .timeout(ms) → this
        val timeoutFn = object : BaseFunction() {
            override fun call(cx: RhinoContext, s: Scriptable, t: Scriptable, args: Array<out Any?>): Any {
                timeoutMs = RhinoContext.toNumber(args.getOrNull(0) ?: 30000).toLong()
                return builder
            }
        }
        ScriptableObject.putProperty(builder, "timeout", timeoutFn)

        // .string(charset?) → raw text
        val stringFn = object : BaseFunction() {
            override fun call(cx: RhinoContext, s: Scriptable, t: Scriptable, args: Array<out Any?>): Any {
                val arg0 = args.getOrNull(0)
                val charsetStr = if (arg0 != null && arg0 != Undefined.instance) RhinoContext.toString(arg0) else "UTF-8"
                val (bodyBytes, _) = executeRequest(httpClient, "GET", url, headersMap, queryParams, null, timeoutMs, extensionId, appContext)
                return if (bodyBytes != null) {
                    try {
                        String(bodyBytes, charset(charsetStr))
                    } catch (e: Exception) {
                        String(bodyBytes, charset("UTF-8"))
                    }
                } else ""
            }
        }
        ScriptableObject.putProperty(builder, "string", stringFn)

        // .html(charset?) → HtmlElement (Jsoup parsed)
        val htmlFn = object : BaseFunction() {
            override fun call(cx: RhinoContext, s: Scriptable, t: Scriptable, args: Array<out Any?>): Any {
                val arg0 = args.getOrNull(0)
                val charsetStr = if (arg0 != null && arg0 != Undefined.instance) RhinoContext.toString(arg0) else "UTF-8"
                val (bodyBytes, _) = executeRequest(httpClient, "GET", url, headersMap, queryParams, null, timeoutMs, extensionId, appContext)
                val text = if (bodyBytes != null) {
                    try {
                        String(bodyBytes, charset(charsetStr))
                    } catch (e: Exception) {
                        String(bodyBytes, charset("UTF-8"))
                    }
                } else ""
                val doc = Jsoup.parse(text)
                return RhinoContext.javaToJS(JSDocument(doc), s) as Scriptable
            }
        }
        ScriptableObject.putProperty(builder, "html", htmlFn)

        // .blob() → Blob object
        val blobFn = object : BaseFunction() {
            override fun call(cx: RhinoContext, s: Scriptable, t: Scriptable, args: Array<out Any?>): Any {
                val (bodyBytes, contentType) = executeRequest(httpClient, "GET", url, headersMap, queryParams, null, timeoutMs, extensionId, appContext)
                val base64 = if (bodyBytes != null) Base64.encodeToString(bodyBytes, Base64.NO_WRAP) else ""
                return createBlobObject(cx, s, base64, contentType ?: "")
            }
        }
        ScriptableObject.putProperty(builder, "blob", blobFn)

        // .url() → url string
        val urlFn = object : BaseFunction() {
            override fun call(cx: RhinoContext, s: Scriptable, t: Scriptable, args: Array<out Any?>): Any = url
        }
        ScriptableObject.putProperty(builder, "url", urlFn)

        return builder
    }

    /**
     * Tạo POST builder object với chain methods
     */
    private fun createPostBuilder(
        ctx: RhinoContext,
        scope: Scriptable,
        httpClient: OkHttpClient,
        url: String,
        extensionId: String,
        appContext: Context
    ): Scriptable {
        val builder = ctx.newObject(scope)
        val headersMap = mutableMapOf<String, String>()
        val queryParams = mutableMapOf<String, String>()
        var bodyStr: String? = null
        var isBinary = false
        var timeoutMs = 30000L

        // .headers({...}) → this
        val headersFn = object : BaseFunction() {
            override fun call(cx: RhinoContext, s: Scriptable, t: Scriptable, args: Array<out Any?>): Any {
                (args.getOrNull(0) as? ScriptableObject)?.let { obj ->
                    for (id in obj.ids) {
                        val key = id.toString()
                        headersMap[key] = RhinoContext.toString(obj.get(key, obj))
                    }
                }
                return builder
            }
        }
        ScriptableObject.putProperty(builder, "headers", headersFn)

        // .queries({...}) → this
        val queriesFn = object : BaseFunction() {
            override fun call(cx: RhinoContext, s: Scriptable, t: Scriptable, args: Array<out Any?>): Any {
                (args.getOrNull(0) as? ScriptableObject)?.let { obj ->
                    for (id in obj.ids) {
                        val key = id.toString()
                        queryParams[key] = RhinoContext.toString(obj.get(key, obj))
                    }
                }
                return builder
            }
        }
        ScriptableObject.putProperty(builder, "queries", queriesFn)

        // .body(data) → this
        val bodyFn = object : BaseFunction() {
            override fun call(cx: RhinoContext, s: Scriptable, t: Scriptable, args: Array<out Any?>): Any {
                bodyStr = RhinoContext.toString(args.getOrNull(0) ?: "")
                isBinary = false
                return builder
            }
        }
        ScriptableObject.putProperty(builder, "body", bodyFn)

        // .binary(base64, type) → this
        val binaryFn = object : BaseFunction() {
            override fun call(cx: RhinoContext, s: Scriptable, t: Scriptable, args: Array<out Any?>): Any {
                val base64 = RhinoContext.toString(args.getOrNull(0) ?: "")
                bodyStr = base64 // Will be decoded in executeRequest
                isBinary = true
                return builder
            }
        }
        ScriptableObject.putProperty(builder, "binary", binaryFn)

        // .timeout(ms) → this
        val timeoutFn = object : BaseFunction() {
            override fun call(cx: RhinoContext, s: Scriptable, t: Scriptable, args: Array<out Any?>): Any {
                timeoutMs = RhinoContext.toNumber(args.getOrNull(0) ?: 30000).toLong()
                return builder
            }
        }
        ScriptableObject.putProperty(builder, "timeout", timeoutFn)

        // .string(charset?) → raw text
        val stringFn = object : BaseFunction() {
            override fun call(cx: RhinoContext, s: Scriptable, t: Scriptable, args: Array<out Any?>): Any {
                val arg0 = args.getOrNull(0)
                val charsetStr = if (arg0 != null && arg0 != Undefined.instance) RhinoContext.toString(arg0) else "UTF-8"
                val (bytes, _) = executeRequest(httpClient, "POST", url, headersMap, queryParams, bodyStr, timeoutMs, extensionId, appContext, isBinary)
                return if (bytes != null) {
                    try {
                        String(bytes, charset(charsetStr))
                    } catch (e: Exception) {
                        String(bytes, charset("UTF-8"))
                    }
                } else ""
            }
        }
        ScriptableObject.putProperty(builder, "string", stringFn)

        // .html(charset?) → HtmlElement
        val htmlFn = object : BaseFunction() {
            override fun call(cx: RhinoContext, s: Scriptable, t: Scriptable, args: Array<out Any?>): Any {
                val arg0 = args.getOrNull(0)
                val charsetStr = if (arg0 != null && arg0 != Undefined.instance) RhinoContext.toString(arg0) else "UTF-8"
                val (bytes, _) = executeRequest(httpClient, "POST", url, headersMap, queryParams, bodyStr, timeoutMs, extensionId, appContext, isBinary)
                val text = if (bytes != null) {
                    try {
                        String(bytes, charset(charsetStr))
                    } catch (e: Exception) {
                        String(bytes, charset("UTF-8"))
                    }
                } else ""
                val doc = Jsoup.parse(text)
                return RhinoContext.javaToJS(JSDocument(doc), s) as Scriptable
            }
        }
        ScriptableObject.putProperty(builder, "html", htmlFn)

        // .blob() → Blob object
        val blobFn = object : BaseFunction() {
            override fun call(cx: RhinoContext, s: Scriptable, t: Scriptable, args: Array<out Any?>): Any {
                val (bytes, contentType) = executeRequest(httpClient, "POST", url, headersMap, queryParams, bodyStr, timeoutMs, extensionId, appContext, isBinary)
                val base64 = if (bytes != null) Base64.encodeToString(bytes, Base64.NO_WRAP) else ""
                return createBlobObject(cx, s, base64, contentType ?: "")
            }
        }
        ScriptableObject.putProperty(builder, "blob", blobFn)

        // .url() → url string
        val urlFn = object : BaseFunction() {
            override fun call(cx: RhinoContext, s: Scriptable, t: Scriptable, args: Array<out Any?>): Any = url
        }
        ScriptableObject.putProperty(builder, "url", urlFn)

        return builder
    }

    /**
     * Thực hiện HTTP request.
     * @return Pair<ByteArray?, String?> — (body bytes, content-type header)
     */
    private fun executeRequest(
        httpClient: OkHttpClient,
        method: String,
        url: String,
        headers: Map<String, String>,
        queries: Map<String, String>,
        body: String?,
        timeoutMs: Long,
        extensionId: String,
        appContext: Context,
        isBinary: Boolean = false
    ): Pair<ByteArray?, String?> {
        try {
            val urlBuilder = url.toHttpUrl().newBuilder()
            queries.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }

            val headersBuilder = Headers.Builder()
            headers.forEach { (k, v) -> headersBuilder.add(k, v) }

            // Default User-Agent is now handled by OkHttpClient Interceptor in AppModule

            // Inject & merge extension cookies with real-time CookieManager cookies
            val prefs = appContext.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE)
            val customCookie = prefs.getString("ext_cookies_$extensionId", null)
            val cookieManager = CookieManager.getInstance()
            val webViewCookie = cookieManager.getCookie(url)
            val existingCookie = headersBuilder["Cookie"]

            val finalCookie = mergeCookies(webViewCookie, customCookie, existingCookie)
            if (finalCookie.isNotBlank()) {
                headersBuilder.set("Cookie", finalCookie)
            }

            val customUa = prefs.getString("ext_user_agent_$extensionId", null)
            if (!customUa.isNullOrBlank()) {
                headersBuilder.set("User-Agent", customUa)
            }

            val requestBody = if (method == "POST" || method == "PUT" || method == "PATCH") {
                val contentType = headers["Content-Type"] ?: headers["content-type"] ?: "application/x-www-form-urlencoded"
                val rBody = if (isBinary && body != null) {
                    val bytes = try {
                        Base64.decode(body, Base64.DEFAULT)
                    } catch (e: Exception) {
                        body.toByteArray()
                    }
                    bytes.toRequestBody(contentType.toMediaType())
                } else {
                    (body ?: "").toRequestBody(contentType.toMediaType())
                }
                
                val contentLength = rBody.contentLength()
                if (contentLength >= 0 && headersBuilder["Content-Length"] == null && headersBuilder["content-length"] == null) {
                    headersBuilder.add("Content-Length", contentLength.toString())
                }
                rBody
            } else {
                null
            }

            val extTimeout = prefs.getInt("ext_connection_timeout_$extensionId", prefs.getInt("conn_timeout", 30))
            val activeTimeoutMs = if (timeoutMs == 30000L) {
                extTimeout * 1000L
            } else {
                timeoutMs
            }

            // Build a client with custom timeout if needed
            val client = if (activeTimeoutMs > 0) {
                httpClient.newBuilder()
                    .connectTimeout(activeTimeoutMs, TimeUnit.MILLISECONDS)
                    .readTimeout(activeTimeoutMs, TimeUnit.MILLISECONDS)
                    .writeTimeout(activeTimeoutMs, TimeUnit.MILLISECONDS)
                    .build()
            } else {
                httpClient
            }

            headersBuilder.add("X-Extension-Id", extensionId)

            val request = Request.Builder()
                .url(urlBuilder.build())
                .headers(headersBuilder.build())
                .method(method, requestBody)
                .build()

            Log.d(TAG, "Http.$method: ${request.url}")

            val response = client.newCall(request).execute()
            
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

            val bodyBytes = response.body?.use { it.bytes() }
            val contentType = response.header("content-type")
            return Pair(bodyBytes, contentType)

        } catch (e: Exception) {
            Log.e(TAG, "Http request error: ${e.message}")
            return Pair(null, null)
        }
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

    /**
     * Tạo Blob JS object giống core.js _Blob()
     */
    fun createBlobObject(ctx: RhinoContext, scope: Scriptable, base64: String, contentType: String): Scriptable {
        val blob = ctx.newObject(scope)
        ScriptableObject.putProperty(blob, "_isBlob", true)
        ScriptableObject.putProperty(blob, "_base64", base64)
        ScriptableObject.putProperty(blob, "type", contentType)

        // Calculate size
        var padding = 0
        if (base64.endsWith("==")) padding = 2
        else if (base64.endsWith("=")) padding = 1
        val size = Math.max(0, (base64.length * 3 / 4) - padding)
        ScriptableObject.putProperty(blob, "size", size)

        // .base64() → string
        val base64Fn = object : BaseFunction() {
            override fun call(cx: RhinoContext, s: Scriptable, t: Scriptable, args: Array<out Any?>): Any = base64
        }
        ScriptableObject.putProperty(blob, "base64", base64Fn)

        // .toString() → string
        val toStringFn = object : BaseFunction() {
            override fun call(cx: RhinoContext, s: Scriptable, t: Scriptable, args: Array<out Any?>): Any = base64
        }
        ScriptableObject.putProperty(blob, "toString", toStringFn)

        return blob
    }
}

/**
 * JSBlobBridge — implement `Blob.fromBase64()` global object.
 * Tương ứng: core.js → Blob object
 */
object JSBlobBridge {
    fun inject(ctx: RhinoContext, scope: ScriptableObject) {
        val blobObj = ctx.newObject(scope)

        val fromBase64Fn = object : BaseFunction() {
            override fun call(cx: RhinoContext, s: Scriptable, t: Scriptable, args: Array<out Any?>): Any {
                val base64 = RhinoContext.toString(args.getOrNull(0) ?: "")
                val type = RhinoContext.toString(args.getOrNull(1) ?: "")
                return JSHttpBuilderBridge.createBlobObject(cx, s, base64, type)
            }
        }
        ScriptableObject.putProperty(blobObj, "fromBase64", fromBase64Fn)

        ScriptableObject.putProperty(scope, "Blob", blobObj)
    }
}

/**
 * JSQtBridge — implement `Qt.translate()` stub.
 * Tương ứng: core.js → Qt object
 */
object JSQtBridge {
    fun inject(ctx: RhinoContext, scope: ScriptableObject, appContext: Context) {
        val qtObj = ctx.newObject(scope)

        val translateFn = object : BaseFunction() {
            override fun call(cx: RhinoContext, s: Scriptable, t: Scriptable, args: Array<out Any?>): Any {
                val text = RhinoContext.toString(args.getOrNull(0) ?: "")
                val to = RhinoContext.toString(args.getOrNull(1) ?: "vi")
                val translatedText = com.nam.novelreader.util.QuickTranslateEngine.translate(appContext, text, to)
                val result = org.json.JSONObject()
                result.put("text", translatedText)
                result.put("from", "auto")
                result.put("to", to)
                return result.toString()
            }
        }
        ScriptableObject.putProperty(qtObj, "translate", translateFn)

        ScriptableObject.putProperty(scope, "Qt", qtObj)
    }
}
