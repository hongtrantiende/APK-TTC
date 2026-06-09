package com.nam.novelreader.extension.runtime.api

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.SslErrorHandler
import android.net.http.SslError
import android.util.Log
import org.jsoup.Jsoup
import org.mozilla.javascript.*
import org.mozilla.javascript.Context as RhinoContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import com.nam.novelreader.util.BackgroundWebView

/**
 * JSLocalStorage — extension-scoped persistent storage.
 * Tương ứng: com.vbook.app.extensions.js.module.storage.JSLocalStorage
 */
object JSLocalStorage {

    fun inject(ctx: RhinoContext, scope: ScriptableObject, appContext: Context, extensionId: String) {
        val prefs = appContext.getSharedPreferences("ext_storage_$extensionId", Context.MODE_PRIVATE)
        val storageObj = ctx.newObject(scope)

        // setItem(key, value)
        val setItemFn = object : BaseFunction() {
            override fun call(cx: RhinoContext, s: Scriptable, t: Scriptable, args: Array<out Any?>): Any {
                val key = RhinoContext.toString(args.getOrNull(0) ?: "")
                val value = RhinoContext.toString(args.getOrNull(1) ?: "")
                prefs.edit().putString(key, value).apply()
                return Undefined.instance
            }
        }
        ScriptableObject.putProperty(storageObj, "setItem", setItemFn)

        // getItem(key) → string | null
        val getItemFn = object : BaseFunction() {
            override fun call(cx: RhinoContext, s: Scriptable, t: Scriptable, args: Array<out Any?>): Any {
                val key = RhinoContext.toString(args.getOrNull(0) ?: "")
                return prefs.getString(key, null) ?: Undefined.instance
            }
        }
        ScriptableObject.putProperty(storageObj, "getItem", getItemFn)

        // removeItem(key)
        val removeItemFn = object : BaseFunction() {
            override fun call(cx: RhinoContext, s: Scriptable, t: Scriptable, args: Array<out Any?>): Any {
                val key = RhinoContext.toString(args.getOrNull(0) ?: "")
                prefs.edit().remove(key).apply()
                return Undefined.instance
            }
        }
        ScriptableObject.putProperty(storageObj, "removeItem", removeItemFn)

        // clear()
        val clearFn = object : BaseFunction() {
            override fun call(cx: RhinoContext, s: Scriptable, t: Scriptable, args: Array<out Any?>): Any {
                prefs.edit().clear().apply()
                return Undefined.instance
            }
        }
        ScriptableObject.putProperty(storageObj, "clear", clearFn)

        ScriptableObject.putProperty(scope, "LocalStorage", storageObj)
    }
}

/**
 * JSBrowser — WebView-based headless browser bridge cho Engine.newBrowser()
 * Tương ứng: com.vbook.app.extensions.js.module.browser.JSBrowser
 *
 * Chạy WebView trên Main Thread, block background thread chạy JS Rhino bằng CountDownLatch
 * để thực thi các tác vụ đồng bộ của extensions VBook.
 */

class JSBrowser(private val appContext: Context, private val extensionId: String) {
    private var webView: BackgroundWebView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val browserHandler = Handler(Looper.getMainLooper())
    private val lock = Object()
    @Volatile
    private var htmlReceived = false
    private val blockUrls = mutableSetOf<String>()
    private val injectCookies = mutableMapOf<String, String>()
    private var userAgent: String? = null
    private val variables = mutableMapOf<String, String>()
    private val urls = mutableListOf<String>()

    private var currentHtml: String = ""
    private val bridgeName = "Cache"

    init {
        val latch = CountDownLatch(1)
        handler.post {
            try {
                val wv = BackgroundWebView(appContext)
                wv.layout(0, 0, 1080, 1920)
                wv.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    allowFileAccess = true
                    allowContentAccess = true
                    loadsImagesAutomatically = false // Tiết kiệm RAM theo lessons learned
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    val savedUa = appContext.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE).getString("ext_user_agent_$extensionId", null)
                    userAgentString = if (!savedUa.isNullOrBlank()) savedUa else com.nam.novelreader.util.UserAgentUtils.getCleanUserAgent(appContext)
                }

                wv.webViewClient = object : WebViewClient() {
                    override fun onReceivedSslError(
                        view: WebView,
                        handler: SslErrorHandler,
                        error: SslError
                    ) {
                        handler.proceed()
                    }

                    override fun shouldInterceptRequest(
                        view: WebView,
                        url: String
                    ): android.webkit.WebResourceResponse? {
                        urls.add(url)
                        if (com.nam.novelreader.data.network.AdBlocker.isAd(url, checkStaticResources = false)) {
                            return com.nam.novelreader.data.network.AdBlocker.createEmptyResource()
                        }
                        for (pattern in blockUrls) {
                            if (url.contains(pattern) || url.matches(pattern.toRegex())) {
                                return android.webkit.WebResourceResponse(
                                    "text/plain", "utf-8", 200, "OK", emptyMap(),
                                    java.io.ByteArrayInputStream("".toByteArray())
                                )
                            }
                        }
                        return null
                    }

                    @Deprecated("Deprecated in Java")
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: android.webkit.WebResourceRequest
                    ): android.webkit.WebResourceResponse? {
                        val url = request.url.toString()
                        urls.add(url)
                        if (com.nam.novelreader.data.network.AdBlocker.isAd(url, checkStaticResources = false)) {
                            return com.nam.novelreader.data.network.AdBlocker.createEmptyResource()
                        }
                        for (pattern in blockUrls) {
                            if (url.contains(pattern) || url.matches(pattern.toRegex())) {
                                return android.webkit.WebResourceResponse(
                                    "text/plain", "utf-8", 200, "OK", emptyMap(),
                                    java.io.ByteArrayInputStream("".toByteArray())
                                )
                            }
                        }
                        return null
                    }
                }

                // Đăng ký đồng thời nhiều Javascript Interface để tăng tính tương thích cho WebView của VBook Extension
                wv.addJavascriptInterface(this, "Cache")
                wv.addJavascriptInterface(this, "Browser")
                wv.addJavascriptInterface(this, "AndroidBrowserBridge")
                webView = wv
            } catch (e: Exception) {
                Log.e("JSBrowser", "Init webview failed", e)
            } finally {
                latch.countDown()
            }
        }
        latch.await(5, TimeUnit.SECONDS)
    }

    @JavascriptInterface
    fun receiveHtml(html: String) {
        currentHtml = html
        synchronized(lock) {
            htmlReceived = true
            lock.notifyAll()
        }
    }

    @JavascriptInterface
    fun putVariable(key: String, value: String) {
        variables[key] = value
    }

    @JavascriptInterface
    fun log(msg: String) {
        Log.d("JSBrowserWebView", msg)
    }

    fun block(nativeArray: NativeArray) {
        blockUrls.clear()
        for (i in 0 until nativeArray.size.toInt()) {
            blockUrls.add(nativeArray.get(i).toString())
        }
    }

    fun blockJson(urlsJson: String) {
        blockUrls.clear()
        try {
            val arr = org.json.JSONArray(urlsJson)
            for (i in 0 until arr.length()) {
                blockUrls.add(arr.getString(i))
            }
        } catch (e: Exception) {
            Log.e("JSBrowser", "Failed to parse block URLs: ${e.message}")
        }
    }

    fun getUrlsJson(): String {
        val arr = org.json.JSONArray()
        urls.forEach { arr.put(it) }
        return arr.toString()
    }

    fun overrideCookie(obj: Any?) {
        if (obj is NativeObject) {
            for (key in obj.ids) {
                val value = obj.get(key)
                if (key != null && value != null) {
                    injectCookies[key.toString()] = value.toString()
                }
            }
        }
    }

    fun setUserAgent(ua: String) {
        val cleanUa = ua.replace("; wv", "").replace(Regex("Version/\\d+\\.\\d+\\s?"), "")
        userAgent = cleanUa
        browserHandler.post {
            webView?.settings?.userAgentString = cleanUa
        }
    }

    fun getVariable(key: String): String? {
        return variables[key]
    }

    fun urls(): JSList {
        val list = JSList()
        list.addAll(urls)
        return list
    }

    fun close() {
        browserHandler.removeCallbacksAndMessages(null)
        handler.post {
            webView?.destroy()
            webView = null
        }
    }

    private fun unescapeJsonString(jsonStr: String): String {
        if (jsonStr == "null" || jsonStr == "undefined") return ""
        if (jsonStr.length < 2 || !jsonStr.startsWith("\"") || !jsonStr.endsWith("\"")) return jsonStr
        
        val sb = java.lang.StringBuilder(jsonStr.length - 2)
        var i = 1
        val len = jsonStr.length - 1
        while (i < len) {
            val c = jsonStr[i]
            if (c == '\\') {
                i++
                if (i >= len) break
                val next = jsonStr[i]
                when (next) {
                    '\"' -> sb.append('\"')
                    '\\' -> sb.append('\\')
                    '/' -> sb.append('/')
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000C')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    'u' -> {
                        if (i + 4 < len) {
                            val hex = jsonStr.substring(i + 1, i + 5)
                            try {
                                sb.append(hex.toInt(16).toChar())
                            } catch (e: Exception) {
                                sb.append("\\u").append(hex)
                            }
                            i += 4
                        } else {
                            sb.append("\\u")
                        }
                    }
                    else -> sb.append('\\').append(next)
                }
            } else {
                sb.append(c)
            }
            i++
        }
        return sb.toString()
    }

    private fun syncBrowserCookies() {
        val latch = CountDownLatch(1)
        handler.post {
            try {
                val wv = webView
                val currentUrl = wv?.url
                if (wv != null && !currentUrl.isNullOrBlank()) {
                    val cookieManager = CookieManager.getInstance()
                    val currentCookies = cookieManager.getCookie(currentUrl)
                    if (!currentCookies.isNullOrBlank()) {
                        appContext.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE)
                            .edit()
                            .putString("ext_cookies_$extensionId", currentCookies)
                            .apply()
                    }
                }
            } catch (e: Exception) {
                Log.e("JSBrowser", "Failed to sync browser cookies: ${e.message}")
            } finally {
                latch.countDown()
            }
        }
        try {
            latch.await(1, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {}
    }

    fun launch(urlObj: Any?, timeoutObj: Any?): JSDocument {
        val url = urlObj?.toString() ?: ""
        val timeout = (timeoutObj?.toString()?.toDoubleOrNull() ?: 15000.0).toLong()
        var isFinished = false

        synchronized(lock) {
            currentHtml = ""
            htmlReceived = false
            browserHandler.removeCallbacksAndMessages(null)

            browserHandler.post {
                val wv = webView
                if (wv != null) {
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    // Set custom cookies if present in preferences
                    val prefs = appContext.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE)
                    val customCookie = prefs.getString("ext_cookies_$extensionId", null)
                    if (!customCookie.isNullOrBlank()) {
                        customCookie.split(";").forEach { cookie ->
                            if (cookie.isNotBlank()) {
                                cookieManager.setCookie(url, cookie.trim())
                            }
                        }
                    }

                    injectCookies.forEach { (k, v) ->
                        cookieManager.setCookie(url, "$k=$v")
                    }

                    val baseHost = try { java.net.URI(url).host } catch (e: Exception) { null }

                    val getHtmlRunnable = Runnable {
                        if (isFinished) return@Runnable
                        isFinished = true
                        wv.evaluateJavascript(
                            "window.$bridgeName.receiveHtml('<html>' + document.getElementsByTagName('html')[0].innerHTML + '</html>');",
                            null
                        )
                    }

                    wv.webViewClient = object : WebViewClient() {
                        override fun onReceivedSslError(
                            view: WebView,
                            handler: SslErrorHandler,
                            error: SslError
                        ) {
                            handler.proceed()
                        }

                        override fun onPageFinished(view: WebView, loadedUrl: String) {
                            browserHandler.removeCallbacks(getHtmlRunnable)
                            val delay = if (timeout >= 5000) 2500L else 500L
                            browserHandler.postDelayed(getHtmlRunnable, delay)
                        }

                        override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean {
                            return false
                        }

                        @Deprecated("Deprecated in Java")
                        override fun shouldOverrideUrlLoading(view: WebView, urlString: String): Boolean {
                            return false
                        }

                        override fun shouldInterceptRequest(
                            view: WebView,
                            interceptUrl: String
                        ): android.webkit.WebResourceResponse? {
                            urls.add(interceptUrl)
                            if (com.nam.novelreader.data.network.AdBlocker.isAd(interceptUrl, checkStaticResources = false)) {
                                return com.nam.novelreader.data.network.AdBlocker.createEmptyResource()
                            }
                            for (pattern in blockUrls) {
                                if (interceptUrl.contains(pattern) || interceptUrl.matches(pattern.toRegex())) {
                                    return android.webkit.WebResourceResponse(
                                        "text/plain", "utf-8", 200, "OK", emptyMap(),
                                        java.io.ByteArrayInputStream("".toByteArray())
                                    )
                                }
                            }
                            return null
                        }

                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: android.webkit.WebResourceRequest
                        ): android.webkit.WebResourceResponse? {
                            val url = request.url.toString()
                            urls.add(url)
                            if (com.nam.novelreader.data.network.AdBlocker.isAd(url, checkStaticResources = false)) {
                                return com.nam.novelreader.data.network.AdBlocker.createEmptyResource()
                            }
                            for (pattern in blockUrls) {
                                if (url.contains(pattern) || url.matches(pattern.toRegex())) {
                                    return android.webkit.WebResourceResponse(
                                        "text/plain", "utf-8", 200, "OK", emptyMap(),
                                        java.io.ByteArrayInputStream("".toByteArray())
                                    )
                                }
                            }
                            return null
                        }
                    }

                    userAgent?.let { wv.settings.userAgentString = it }
                    wv.loadUrl(url)

                    // Fallback safety timer
                    browserHandler.postDelayed({
                        if (!isFinished) {
                            isFinished = true
                            wv.evaluateJavascript(
                                "window.$bridgeName.receiveHtml('<html>' + document.getElementsByTagName('html')[0].innerHTML + '</html>');",
                                null
                            )
                        }
                    }, timeout)
                } else {
                    synchronized(lock) {
                        htmlReceived = true
                        lock.notifyAll()
                    }
                }
            }

            val startTime = SystemClock.uptimeMillis()
            while (!htmlReceived && SystemClock.uptimeMillis() - startTime < timeout) {
                val remaining = timeout - (SystemClock.uptimeMillis() - startTime)
                if (remaining <= 0) break
                try {
                    lock.wait(remaining)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }

        syncBrowserCookies()
        return JSDocument(Jsoup.parse(currentHtml))
    }

    fun launchAsync(urlObj: Any?) {
        val url = urlObj?.toString() ?: ""
        browserHandler.post {
            webView?.loadUrl(url)
        }
    }

    fun loadHtml(htmlObj: Any?, baseUrlObj: Any?) {
        val html = htmlObj?.toString() ?: ""
        val baseUrl = baseUrlObj?.toString() ?: ""
        browserHandler.post {
            webView?.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
        }
    }

    fun click(xObj: Any?, yObj: Any?): JSDocument {
        val x = xObj?.toString()?.toFloatOrNull() ?: 0f
        val y = yObj?.toString()?.toFloatOrNull() ?: 0f
        val timeout = 2500L

        synchronized(lock) {
            currentHtml = ""
            htmlReceived = false
            browserHandler.removeCallbacksAndMessages(null)

            browserHandler.post {
                val wv = webView
                if (wv != null) {
                    val density = wv.resources.displayMetrics.density
                    val px = x * density
                    val py = y * density
                    val uptime = SystemClock.uptimeMillis()
                    wv.dispatchTouchEvent(MotionEvent.obtain(uptime, uptime + 50, MotionEvent.ACTION_DOWN, px, py, 0))
                    wv.dispatchTouchEvent(MotionEvent.obtain(uptime, uptime + 100, MotionEvent.ACTION_UP, px, py, 0))

                    browserHandler.postDelayed({
                        wv.evaluateJavascript(
                            "window.$bridgeName.receiveHtml('<html>' + document.getElementsByTagName('html')[0].innerHTML + '</html>');",
                            null
                        )
                    }, 800)
                } else {
                    synchronized(lock) {
                        htmlReceived = true
                        lock.notifyAll()
                    }
                }
            }

            val startTime = SystemClock.uptimeMillis()
            while (!htmlReceived && SystemClock.uptimeMillis() - startTime < timeout) {
                val remaining = timeout - (SystemClock.uptimeMillis() - startTime)
                if (remaining <= 0) break
                try {
                    lock.wait(remaining)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }

        syncBrowserCookies()
        return JSDocument(Jsoup.parse(currentHtml))
    }

    fun callJs(jsObj: Any?, timeoutObj: Any?): JSDocument {
        val js = jsObj?.toString() ?: ""
        val timeout = (timeoutObj?.toString()?.toDoubleOrNull() ?: 5000.0).toLong().coerceAtLeast(100L)
        val latch = CountDownLatch(1)
        var evalResult = ""

        browserHandler.post {
            val wv = webView
            if (wv != null) {
                wv.evaluateJavascript(js) { result ->
                    evalResult = result ?: ""
                    latch.countDown()
                }
            } else {
                latch.countDown()
            }
        }

        try {
            latch.await(timeout, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            // ignore
        }

        syncBrowserCookies()
        val decodedResult = unescapeJsonString(evalResult)
        return JSDocument(Jsoup.parse("<html><head></head><body>$decodedResult</body></html>"))
    }

    fun waitUrl(regexObj: Any?, timeoutObj: Any?) {
        val regex = regexObj?.toString() ?: ""
        val timeout = (timeoutObj?.toString()?.toDoubleOrNull() ?: 10000.0).toLong()
        val startTime = SystemClock.uptimeMillis()
        while (SystemClock.uptimeMillis() - startTime < timeout) {
            val matched = urls.any { it.contains(regex) || it.matches(regex.toRegex()) }
            if (matched) break
            Thread.sleep(150)
        }
    }

    fun html(): JSDocument {
        return html(1000.0)
    }

    fun html(timeoutObj: Any?): JSDocument {
        val timeout = (timeoutObj?.toString()?.toDoubleOrNull() ?: 1000.0).toLong().coerceAtLeast(100L)

        synchronized(lock) {
            currentHtml = ""
            htmlReceived = false
            browserHandler.removeCallbacksAndMessages(null)

            browserHandler.post {
                val wv = webView
                if (wv != null) {
                    wv.evaluateJavascript(
                        "window.$bridgeName.receiveHtml('<html>' + document.getElementsByTagName('html')[0].innerHTML + '</html>');",
                        null
                    )
                } else {
                    synchronized(lock) {
                        htmlReceived = true
                        lock.notifyAll()
                    }
                }
            }

            val startTime = SystemClock.uptimeMillis()
            while (!htmlReceived && SystemClock.uptimeMillis() - startTime < timeout) {
                val remaining = timeout - (SystemClock.uptimeMillis() - startTime)
                if (remaining <= 0) break
                try {
                    lock.wait(remaining)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }

        syncBrowserCookies()
        return JSDocument(Jsoup.parse(currentHtml))
    }
}

/**
 * JSBrowserBridge — Đăng ký Engine global object và API cho JS extension.
 * Tương ứng: com.vbook.app.extensions.js.module.browser.JSBrowserEngine
 */
object JSBrowserBridge {

    fun inject(ctx: RhinoContext, scope: ScriptableObject, appContext: Context, extensionId: String) {
        val engineObj = ctx.newObject(scope)

        // Engine.newBrowser() hoặc Engine.create()
        val newBrowserFn = object : BaseFunction() {
            override fun call(cx: RhinoContext, s: Scriptable, t: Scriptable, args: Array<out Any?>): Any {
                val browser = JSBrowser(appContext, extensionId)
                return RhinoContext.javaToJS(browser, s)
            }
        }
        ScriptableObject.putProperty(engineObj, "newBrowser", newBrowserFn)
        ScriptableObject.putProperty(engineObj, "create", newBrowserFn)

        ScriptableObject.putProperty(scope, "Engine", engineObj)
    }
}
