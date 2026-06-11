package com.nam.novelreader.extension.runtime.api

import android.content.Context
import android.util.Base64
import android.util.Log
import com.nam.novelreader.extension.model.LoadedExtension
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.mozilla.javascript.Context as RhinoContext
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeJSON
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.Wrapper
import java.util.concurrent.TimeUnit

/**
 * JSBridge — Cầu nối trung tâm đón đầu cuộc gọi từ core.js __(data)
 * và phân phối tới các logic native tương ứng của VBook.
 */
class JSBridge(
    private val ctx: RhinoContext,
    private val scope: Scriptable,
    private val httpClient: OkHttpClient,
    private val extensionId: String,
    private val context: Context,
    private val extension: LoadedExtension,
    private val logCollector: MutableList<String>? = null
) {
    private val activeWebSockets = mutableListOf<JSWebSocketWrapper>()
    private val activeBrowsers = mutableListOf<JSBrowser>()

    fun execFunction(data: Any?): Any? {
        if (data is NativeArray) {
            val size = data.length.toInt()
            if (size == 0) return null
            val cmd = data.get(0)?.toString() ?: ""
            
            val args = Array(size - 1) { i ->
                val jsVal = data.get(i + 1)
                val unwrapped = if (jsVal is Wrapper) {
                    jsVal.unwrap()
                } else {
                    RhinoContext.jsToJava(jsVal, Any::class.java)
                }
                Log.d("JSBridgeArgs", "Command '$cmd' Arg $i: jsVal class = ${jsVal?.javaClass?.name}, unwrapped class = ${unwrapped?.javaClass?.name}")
                unwrapped
            }
            
            return try {
                dispatch(cmd, args)
            } catch (e: Throwable) {
                Log.e("JSBridge", "Error executing command '$cmd': ${e.message}", e)
                null
            }
        }
        return null
    }

    private fun dispatch(cmd: String, args: Array<Any?>): Any? {
        return when (cmd) {
            "Http_fetch" -> {
                val url = args.getOrNull(0)?.toString() ?: ""
                val optionsJson = args.getOrNull(1)?.toString() ?: ""
                executeHttpFetch(url, optionsJson)
            }
            "HttpResponse_headers" -> {
                val resp = args.getOrNull(0) as? JSHttpResponse
                if (resp != null && resp.headers is Scriptable) {
                    NativeJSON.stringify(ctx, scope, resp.headers, null, null)?.toString() ?: "{}"
                } else "{}"
            }
            "HttpResponse_header" -> {
                val resp = args.getOrNull(0) as? JSHttpResponse
                val key = args.getOrNull(1)?.toString()
                resp?.header(key)
            }
            "HttpResponse_status" -> {
                val resp = args.getOrNull(0) as? JSHttpResponse
                resp?.status
            }
            "HttpResponse_statusText" -> {
                val resp = args.getOrNull(0) as? JSHttpResponse
                resp?.statusText
            }
            "HttpResponse_ok" -> {
                val resp = args.getOrNull(0) as? JSHttpResponse
                resp?.ok
            }
            "HttpResponse_url" -> {
                val resp = args.getOrNull(0) as? JSHttpResponse
                resp?.url
            }
            "HttpResponse_text" -> {
                val resp = args.getOrNull(0) as? JSHttpResponse
                val charset = args.getOrNull(1)
                resp?.text(charset)
            }
            "HttpResponse_base64" -> {
                val resp = args.getOrNull(0) as? JSHttpResponse
                resp?.base64()
            }
            "HttpResponse_blob" -> {
                val resp = args.getOrNull(0) as? JSHttpResponse
                resp?.blob()
            }
            "HttpRequest_headers" -> {
                val resp = args.getOrNull(0) as? JSHttpResponse
                if (resp != null && resp.request.headers is Scriptable) {
                    NativeJSON.stringify(ctx, scope, resp.request.headers, null, null)?.toString() ?: "{}"
                } else "{}"
            }
            "HttpRequest_url" -> {
                val resp = args.getOrNull(0) as? JSHttpResponse
                resp?.request?.url
            }

            // HTML parser
            "HtmlDocument_parse" -> {
                val htmlText = args.getOrNull(0)?.toString() ?: ""
                JSHtml().parse(htmlText)
            }
            "HtmlElement_select" -> {
                val element = args.getOrNull(0)
                val query = args.getOrNull(1)?.toString() ?: ""
                when (element) {
                    is JSDocument -> element.select(query)
                    is JSElement -> element.select(query)
                    is JSElements -> element.select(query)
                    else -> JSElements(org.jsoup.select.Elements())
                }
            }
            "HtmlElement_attr" -> {
                val element = args.getOrNull(0)
                val attr = args.getOrNull(1)?.toString() ?: ""
                when (element) {
                    is JSDocument -> element.attr(attr)
                    is JSElement -> element.attr(attr)
                    is JSElements -> element.attr(attr)
                    else -> ""
                }
            }
            "HtmlElement_text" -> {
                val element = args.getOrNull(0)
                when (element) {
                    is JSDocument -> element.text()
                    is JSElement -> element.text()
                    is JSElements -> element.text()
                    else -> ""
                }
            }
            "HtmlElement_html" -> {
                val element = args.getOrNull(0)
                when (element) {
                    is JSDocument -> element.html()
                    is JSElement -> element.html()
                    is JSElements -> element.html()
                    else -> ""
                }
            }
            "HtmlElement_remove" -> {
                val element = args.getOrNull(0)
                when (element) {
                    is JSDocument -> element.remove()
                    is JSElement -> element.remove()
                    is JSElements -> element.remove()
                }
                Undefined.instance
            }
            "HtmlElement_attributes" -> {
                val element = args.getOrNull(0)
                val jsoupEl = when (element) {
                    is JSElement -> element.getJsoupElement()
                    is JSDocument -> element.getJsoupDocument()
                    else -> null
                }
                val json = org.json.JSONObject()
                jsoupEl?.attributes()?.forEach { attr ->
                    json.put(attr.key, attr.value)
                }
                json.toString()
            }

            // HtmlElements
            "HtmlElements_size" -> {
                val elements = args.getOrNull(0) as? JSElements
                elements?.length ?: 0
            }
            "HtmlElements_get" -> {
                val elements = args.getOrNull(0) as? JSElements
                val index = (args.getOrNull(1) as? Number)?.toInt() ?: 0
                elements?.get(index)
            }
            "HtmlElements_first" -> {
                val elements = args.getOrNull(0) as? JSElements
                elements?.first()
            }
            "HtmlElements_last" -> {
                val elements = args.getOrNull(0) as? JSElements
                elements?.last()
            }
            "HtmlElements_select" -> {
                val elements = args.getOrNull(0) as? JSElements
                val query = args.getOrNull(1)?.toString() ?: ""
                elements?.select(query) ?: JSElements(org.jsoup.select.Elements())
            }
            "HtmlElements_attr" -> {
                val elements = args.getOrNull(0) as? JSElements
                val attr = args.getOrNull(1)?.toString() ?: ""
                elements?.attr(attr) ?: ""
            }
            "HtmlElements_text" -> {
                val elements = args.getOrNull(0) as? JSElements
                elements?.text() ?: ""
            }
            "HtmlElements_html" -> {
                val elements = args.getOrNull(0) as? JSElements
                elements?.html() ?: ""
            }
            "HtmlElements_remove" -> {
                val elements = args.getOrNull(0) as? JSElements
                elements?.remove()
                Undefined.instance
            }

            // WebView Browser
            "Engine_newBrowser" -> {
                val browser = JSBrowser(context, extensionId)
                activeBrowsers.add(browser)
                browser
            }
            "Browser_launch" -> {
                val browser = args.getOrNull(0) as? JSBrowser
                val url = args.getOrNull(1)
                val timeout = args.getOrNull(2)
                browser?.launch(url, timeout)
            }
            "Browser_launchAsync" -> {
                val browser = args.getOrNull(0) as? JSBrowser
                val url = args.getOrNull(1)
                browser?.launchAsync(url)
                Undefined.instance
            }
            "Browser_loadHtml" -> {
                val browser = args.getOrNull(0) as? JSBrowser
                val baseUrl = args.getOrNull(1)
                val html = args.getOrNull(2)
                browser?.loadHtml(html, baseUrl)
                Undefined.instance
            }
            "Browser_getVariable" -> {
                val browser = args.getOrNull(0) as? JSBrowser
                val name = args.getOrNull(1)?.toString() ?: ""
                browser?.getVariable(name)
            }
            "Browser_callJs" -> {
                val browser = args.getOrNull(0) as? JSBrowser
                val script = args.getOrNull(1)
                val timeout = args.getOrNull(2)
                browser?.callJs(script, timeout)
            }
            "Browser_block" -> {
                val browser = args.getOrNull(0) as? JSBrowser
                val urlsJson = args.getOrNull(1)?.toString() ?: "[]"
                browser?.blockJson(urlsJson)
                Undefined.instance
            }
            "Browser_urls" -> {
                val browser = args.getOrNull(0) as? JSBrowser
                browser?.getUrlsJson() ?: "[]"
            }
            "Browser_waitUrl" -> {
                val browser = args.getOrNull(0) as? JSBrowser
                val pattern = args.getOrNull(1)
                val timeout = args.getOrNull(2)
                browser?.waitUrl(pattern, timeout)
                Undefined.instance
            }
            "Browser_setUserAgent" -> {
                val browser = args.getOrNull(0) as? JSBrowser
                val ua = args.getOrNull(1)?.toString() ?: ""
                browser?.setUserAgent(ua)
                Undefined.instance
            }
            "Browser_html" -> {
                val browser = args.getOrNull(0) as? JSBrowser
                val timeout = args.getOrNull(1)
                browser?.html(timeout)
            }
            "Browser_close" -> {
                val browser = args.getOrNull(0) as? JSBrowser
                browser?.close()
                if (browser != null) activeBrowsers.remove(browser)
                Undefined.instance
            }

            // LocalStorage & Config
            "LocalStorage_setItem" -> {
                val prefs = context.getSharedPreferences("ext_storage_$extensionId", Context.MODE_PRIVATE)
                val key = args.getOrNull(0)?.toString() ?: ""
                val value = args.getOrNull(1)?.toString() ?: ""
                prefs.edit().putString(key, value).apply()
                Undefined.instance
            }
            "LocalStorage_getItem" -> {
                val prefs = context.getSharedPreferences("ext_storage_$extensionId", Context.MODE_PRIVATE)
                val key = args.getOrNull(0)?.toString() ?: ""
                prefs.getString(key, null)
            }
            "LocalStorage_removeItem" -> {
                val prefs = context.getSharedPreferences("ext_storage_$extensionId", Context.MODE_PRIVATE)
                val key = args.getOrNull(0)?.toString() ?: ""
                prefs.edit().remove(key).apply()
                Undefined.instance
            }
            "LocalStorage_clear" -> {
                val prefs = context.getSharedPreferences("ext_storage_$extensionId", Context.MODE_PRIVATE)
                prefs.edit().clear().apply()
                Undefined.instance
            }
            "CacheStorage_setItem" -> {
                val prefs = context.getSharedPreferences("ext_cache_$extensionId", Context.MODE_PRIVATE)
                val key = args.getOrNull(0)?.toString() ?: ""
                val value = args.getOrNull(1)?.toString() ?: ""
                prefs.edit().putString(key, value).apply()
                Undefined.instance
            }
            "CacheStorage_getItem" -> {
                val prefs = context.getSharedPreferences("ext_cache_$extensionId", Context.MODE_PRIVATE)
                val key = args.getOrNull(0)?.toString() ?: ""
                prefs.getString(key, null)
            }
            "CacheStorage_removeItem" -> {
                val prefs = context.getSharedPreferences("ext_cache_$extensionId", Context.MODE_PRIVATE)
                val key = args.getOrNull(0)?.toString() ?: ""
                prefs.edit().remove(key).apply()
                Undefined.instance
            }
            "CacheStorage_clear" -> {
                val prefs = context.getSharedPreferences("ext_cache_$extensionId", Context.MODE_PRIVATE)
                prefs.edit().clear().apply()
                Undefined.instance
            }
            "Cookie_set" -> {
                val prefs = context.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE)
                val value = args.getOrNull(0)?.toString() ?: ""
                prefs.edit().putString("ext_cookies_$extensionId", value).apply()
                Undefined.instance
            }
            "Cookie_get" -> {
                val prefs = context.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE)
                prefs.getString("ext_cookies_$extensionId", "") ?: ""
            }
            "LocalConfig_getItem" -> {
                val prefs = context.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE)
                val key = args.getOrNull(0)?.toString() ?: ""
                prefs.getString("ext_config_${extensionId}_$key", null)
            }

            // Common sleep & logs
            "Log_log" -> {
                val msg = args.getOrNull(0)?.toString() ?: ""
                Log.d("ExtJS", msg)
                logCollector?.add(msg)
                Undefined.instance
            }
            "Common_sleep" -> {
                val ms = (args.getOrNull(0) as? Number)?.toLong() ?: 0L
                if (ms > 0) Thread.sleep(ms)
                Undefined.instance
            }

            // UserAgent
            "UserAgent_system" -> {
                com.nam.novelreader.util.UserAgentUtils.getCleanUserAgent(context)
            }
            "UserAgent_chrome" -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            "UserAgent_android" -> "Mozilla/5.0 (Linux; Android ${android.os.Build.VERSION.RELEASE}; ${android.os.Build.MODEL}) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            "UserAgent_ios" -> "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"

            // Graphics
            "Graphics2D_createCanvas" -> {
                val w = (args.getOrNull(0) as? Number)?.toInt() ?: 100
                val h = (args.getOrNull(1) as? Number)?.toInt() ?: 100
                val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                JSCanvasWrapper(bitmap, canvas)
            }
            "Graphics2D_createImage" -> {
                val b64 = args.getOrNull(0)?.toString() ?: ""
                val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                JSImageWrapper(bitmap, b64)
            }
            "Graphics2D_drawImage" -> {
                val canvasWrap = args.getOrNull(0) as? JSCanvasWrapper
                val imgWrap = args.getOrNull(1) as? JSImageWrapper
                val x = (args.getOrNull(2) as? Number)?.toFloat() ?: 0f
                val y = (args.getOrNull(3) as? Number)?.toFloat() ?: 0f
                if (canvasWrap != null && imgWrap != null) {
                    canvasWrap.canvas.drawBitmap(imgWrap.bitmap, x, y, null)
                }
                Undefined.instance
            }
            "Graphics2D_capture" -> {
                val canvasWrap = args.getOrNull(0) as? JSCanvasWrapper
                canvasWrap?.capture() ?: ""
            }
            "Graphics2D_imageWidth" -> {
                val imgWrap = args.getOrNull(0) as? JSImageWrapper
                imgWrap?.width ?: 0
            }
            "Graphics2D_imageHeight" -> {
                val imgWrap = args.getOrNull(0) as? JSImageWrapper
                imgWrap?.height ?: 0
            }

            // WebSocket
            "WebSocket_create" -> {
                val url = args.getOrNull(0)?.toString() ?: ""
                val headersJson = args.getOrNull(1)?.toString()
                val ws = JSWebSocketWrapper(httpClient, url, headersJson)
                activeWebSockets.add(ws)
                ws
            }
            "WebSocket_connect" -> {
                val ws = args.getOrNull(0) as? JSWebSocketWrapper
                ws?.connect()
                Undefined.instance
            }
            "WebSocket_message" -> {
                val ws = args.getOrNull(0) as? JSWebSocketWrapper
                ws?.takeMessage()
            }
            "WebSocket_send_text" -> {
                val ws = args.getOrNull(0) as? JSWebSocketWrapper
                val text = args.getOrNull(1)?.toString() ?: ""
                ws?.sendText(text) ?: false
            }
            "WebSocket_send_buffer" -> {
                val ws = args.getOrNull(0) as? JSWebSocketWrapper
                val b64 = args.getOrNull(1)?.toString() ?: ""
                ws?.sendBuffer(b64) ?: false
            }
            "WebSocket_close" -> {
                val ws = args.getOrNull(0) as? JSWebSocketWrapper
                ws?.close()
                if (ws != null) activeWebSockets.remove(ws)
                Undefined.instance
            }
            "WebSocket_frame_type" -> {
                val frame = args.getOrNull(0) as? JSWebSocketWrapper.Frame
                frame?.type ?: 0
            }
            "WebSocket_frame_data" -> {
                val frame = args.getOrNull(0) as? JSWebSocketWrapper.Frame
                frame?.data ?: ""
            }

            // Qt
            "Qt_translate" -> {
                val text = args.getOrNull(0)?.toString() ?: ""
                val to = args.getOrNull(1)?.toString() ?: "vi"
                val translatedText = com.nam.novelreader.util.QuickTranslateEngine.translate(context, text, to)
                val result = org.json.JSONObject()
                result.put("text", translatedText)
                result.put("from", "auto")
                result.put("to", to)
                result.toString()
            }

            // Script execute (chạy script con)
            "Script_execute" -> {
                val scriptContent = args.getOrNull(0)?.toString() ?: ""
                val scriptName = args.getOrNull(1)?.toString() ?: "inline"
                val inputVal = args.getOrNull(2)
                executeScriptSubContext(scriptContent, scriptName, inputVal)
            }

            else -> {
                Log.w("JSBridge", "Unhandled command: $cmd")
                null
            }
        }
    }

    private fun executeHttpFetch(url: String, optionsJson: String): JSHttpResponse {
        return try {
            val jsonOpts = if (optionsJson.isNotEmpty()) org.json.JSONObject(optionsJson) else null
            var method = "GET"
            val headersBuilder = Headers.Builder()
            
            val httpUrl = try {
                url.toHttpUrl()
            } catch (e: Exception) {
                val fixedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    "http://$url"
                } else url
                fixedUrl.toHttpUrl()
            }
            var urlBuilder = httpUrl.newBuilder()
            var body: RequestBody? = null

            jsonOpts?.let { opts ->
                if (opts.has("method")) method = opts.getString("method").uppercase()
                if (opts.has("headers")) {
                    val headers = opts.getJSONObject("headers")
                    headers.keys().forEach { key ->
                        headersBuilder.add(key, headers.get(key).toString())
                    }
                }
                
                val queriesVal = if (opts.has("queries")) opts.get("queries") 
                                 else if (opts.has("params")) opts.get("params") 
                                 else null
                if (queriesVal is org.json.JSONObject) {
                    queriesVal.keys().forEach { key ->
                        urlBuilder.addQueryParameter(key, queriesVal.get(key).toString())
                    }
                }
                
                val contentType = headersBuilder["Content-Type"] ?: headersBuilder["content-type"]
                if (opts.has("body")) {
                    val bodyVal = opts.get("body")
                    val finalContentType = contentType ?: "application/x-www-form-urlencoded"
                    if (finalContentType.contains("application/x-www-form-urlencoded")) {
                        // Form-encoded: nếu body là JSONObject thì convert sang form fields
                        if (bodyVal is org.json.JSONObject) {
                            val formBuilder = FormBody.Builder()
                            bodyVal.keys().forEach { key ->
                                formBuilder.add(key, bodyVal.get(key).toString())
                            }
                            body = formBuilder.build()
                        } else {
                            body = bodyVal.toString().toRequestBody(finalContentType.toMediaType())
                        }
                    } else {
                        // JSON hoặc content-type khác: gửi nguyên body string
                        // Nếu org.json đã auto-parse body thành JSONObject, phải stringify lại
                        val bodyStr = when (bodyVal) {
                            is org.json.JSONObject -> bodyVal.toString()
                            is org.json.JSONArray -> bodyVal.toString()
                            else -> bodyVal.toString()
                        }
                        body = bodyStr.toRequestBody(finalContentType.toMediaType())
                    }
                }
                if (opts.has("json")) {
                    val jsonStr = opts.get("json").toString()
                    val finalContentType = contentType ?: "application/json; charset=utf-8"
                    body = jsonStr.toRequestBody(finalContentType.toMediaType())
                }
            }

            val requiresRequestBody = method == "POST" || method == "PUT" || method == "PATCH"
            if (body == null && requiresRequestBody) {
                val contentType = headersBuilder["Content-Type"] ?: headersBuilder["content-type"] ?: "application/x-www-form-urlencoded"
                body = "".toRequestBody(contentType.toMediaType())
            }

            // Không tự thêm Content-Length — để OkHttp tự quản lý
            // VBook gốc cũng không thêm thủ công, tránh duplicate/sai giá trị


            val prefs = context.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE)
            val customCookie = prefs.getString("ext_cookies_$extensionId", null)
            if (!customCookie.isNullOrBlank()) {
                val existingCookie = headersBuilder["Cookie"]
                if (existingCookie != null) {
                    headersBuilder.set("Cookie", "$existingCookie; $customCookie")
                } else {
                    headersBuilder.add("Cookie", customCookie)
                }
            }

            val customUa = prefs.getString("ext_user_agent_$extensionId", null)
            if (!customUa.isNullOrBlank()) {
                headersBuilder.set("User-Agent", customUa)
            } else if (headersBuilder["User-Agent"] == null) {
                val defaultUa = com.nam.novelreader.util.UserAgentUtils.getCleanUserAgent(context)
                headersBuilder.add("User-Agent", defaultUa)
            }

            // Không thêm X-Extension-Id — VBook gốc không thêm header này
            // Một số API (như SiTruyenCV) reject request có header lạ → 422

            val request = Request.Builder()
                .url(urlBuilder.build())
                .headers(headersBuilder.build())
                .method(method, body)
                .build()

            Log.d("JSBridgeFetch", "Fetching: ${request.url}\nHeaders:\n${request.headers}")

            val response = httpClient.newCall(request).execute()
            val bodyBytes = response.body?.use { it.bytes() }
            
            val respHeadersMap = mutableMapOf<String, String>()
            val jsonRespHeaders = org.json.JSONObject()
            response.headers.forEach { (name, value) ->
                respHeadersMap[name.lowercase()] = value
                jsonRespHeaders.put(name, value)
            }
            val jsRespHeaders = NativeJSON.parse(ctx, scope, jsonRespHeaders.toString(), org.mozilla.javascript.Callable { _, _, _, a -> a?.getOrNull(1) })

            val reqHeadersMap = mutableMapOf<String, String>()
            val jsonReqHeaders = org.json.JSONObject()
            request.headers.forEach { (name, value) ->
                reqHeadersMap[name.lowercase()] = value
                jsonReqHeaders.put(name, value)
            }
            val jsReqHeaders = NativeJSON.parse(ctx, scope, jsonReqHeaders.toString(), org.mozilla.javascript.Callable { _, _, _, a -> a?.getOrNull(1) })

            val jsRequest = JSHttpRequest(
                url = request.url.toString(),
                headers = jsReqHeaders ?: ctx.newObject(scope),
                rawHeadersMap = reqHeadersMap
            )
            
            JSHttpResponse(
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
        } catch (e: Exception) {
            Log.e("JSBridgeFetch", "Fetch error: ${e.message}", e)
            val jsRequest = JSHttpRequest(
                url = url,
                headers = ctx.newObject(scope),
                rawHeadersMap = emptyMap()
            )
            JSHttpResponse(
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
        }
    }

    private fun executeScriptSubContext(scriptContent: String, scriptName: String, inputVal: Any?): Any? {
        val subCtx = RhinoContext.enter()
        try {
            subCtx.optimizationLevel = -1
            subCtx.languageVersion = RhinoContext.VERSION_ES6
            subCtx.wrapFactory.isJavaPrimitiveWrap = false
            
            val subScope = subCtx.initStandardObjects()
            
            // Gán biến toàn cục input
            val jsInput = RhinoContext.javaToJS(inputVal, subScope)
            ScriptableObject.putProperty(subScope, "input", jsInput)
            
            val subBridge = JSBridge(subCtx, subScope, httpClient, extensionId, context, extension, logCollector)
            val subBridgeJs = RhinoContext.javaToJS(subBridge, subScope)
            ScriptableObject.putProperty(subScope, "JSBridge", subBridgeJs)
            
            val coreJs = context.assets.open("core.js").bufferedReader().use { it.readText() }
            subCtx.evaluateString(subScope, coreJs, "core.js", 1, null)
            
            // Nạp mã nguồn script để định nghĩa các hàm
            val evalResult = subCtx.evaluateString(subScope, scriptContent, "script_source", 1, null)
            
            // Tìm và thực thi hàm có tên scriptName
            val func = subScope.get(scriptName, subScope)
            return if (func is org.mozilla.javascript.Function) {
                func.call(subCtx, subScope, subScope, arrayOf(jsInput))
            } else {
                Log.w("JSBridge", "Function '$scriptName' not found in sub-context, returning eval result")
                evalResult
            }
        } catch (e: Throwable) {
            Log.e("JSBridgeSubContext", "Error in sub-context execution: ${e.message}", e)
            return null
        } finally {
            RhinoContext.exit()
        }
    }

    fun release() {
        activeWebSockets.forEach { it.close() }
        activeWebSockets.clear()
        activeBrowsers.forEach { it.close() }
        activeBrowsers.clear()
    }
}

class JSCanvasWrapper(val bitmap: android.graphics.Bitmap, val canvas: android.graphics.Canvas) {
    fun capture(): String {
        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
        return android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.NO_WRAP)
    }
}

class JSImageWrapper(val bitmap: android.graphics.Bitmap, val base64: String) {
    val width: Int get() = bitmap.width
    val height: Int get() = bitmap.height
}

class JSWebSocketWrapper(
    private val httpClient: OkHttpClient,
    val url: String,
    private val headersJson: String?
) {
    private var webSocket: okhttp3.WebSocket? = null
    private val messageQueue = java.util.concurrent.LinkedBlockingQueue<Frame>()
    private val openLatch = java.util.concurrent.CountDownLatch(1)
    
    class Frame(val type: Int, val data: Any)
    
    fun connect() {
        val headersBuilder = Headers.Builder()
        if (!headersJson.isNullOrBlank()) {
            try {
                val json = org.json.JSONObject(headersJson)
                json.keys().forEach { key ->
                    headersBuilder.add(key, json.getString(key))
                }
            } catch (e: Exception) {
                Log.e("JSWS", "Failed to parse headers: ${e.message}")
            }
        }
        
        val request = Request.Builder()
            .url(url)
            .headers(headersBuilder.build())
            .build()
            
        webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: okhttp3.WebSocket, response: Response) {
                openLatch.countDown()
            }
            
            override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
                messageQueue.put(Frame(1, text))
            }
            
            override fun onMessage(webSocket: okhttp3.WebSocket, bytes: okio.ByteString) {
                messageQueue.put(Frame(2, bytes.base64()))
            }
            
            override fun onClosed(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
                openLatch.countDown()
                messageQueue.put(Frame(0, reason))
            }
            
            override fun onFailure(webSocket: okhttp3.WebSocket, t: Throwable, response: Response?) {
                openLatch.countDown()
                messageQueue.put(Frame(-1, t.message ?: "Unknown error"))
            }
        })
        
        openLatch.await(10, TimeUnit.SECONDS)
    }
    
    fun takeMessage(): Frame? {
        return try {
            messageQueue.poll(30, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            null
        }
    }
    
    fun sendText(text: String): Boolean {
        return webSocket?.send(text) ?: false
    }
    
    fun sendBuffer(base64Data: String): Boolean {
        val bytes = try {
            okio.ByteString.of(*Base64.decode(base64Data, Base64.DEFAULT))
        } catch (e: Exception) {
            return false
        }
        return webSocket?.send(bytes) ?: false
    }
    
    fun close() {
        webSocket?.close(1000, "Normal closure")
    }
}
