package com.nam.novelreader.extension.runtime

import android.content.Context
import android.util.Log
import com.nam.novelreader.extension.model.ExtensionException
import com.nam.novelreader.extension.model.ExtensionResult
import com.nam.novelreader.extension.model.LoadedExtension
import com.nam.novelreader.extension.model.ScriptType
import com.nam.novelreader.extension.runtime.api.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.mozilla.javascript.Context as RhinoContext
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Script
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * VBookJsExtensionRunner — trung tâm thực thi JS extension.
 *
 * Tái tạo chính xác runtime API của VBook:
 * - fetch() → OkHttp
 * - Html.parse() / Html.clean() → Jsoup
 * - Response.success() / Response.error()
 * - Engine.newBrowser() → headless WebView
 * - Console.log()
 * - load() → load other JS files
 * - sleep()
 * - LocalStorage → extension-scoped SharedPreferences
 *
 * Dùng Mozilla Rhino (giống VBook gốc) để đảm bảo tương thích 100%.
 */
@Singleton
class VBookJsExtensionRunner @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val httpClient: OkHttpClient,
) {
    companion object {
        private const val TAG = "ExtRunner"
    }

    private val compiledScripts = ConcurrentHashMap<String, Script>()

    fun clearCache(extensionId: String) {
        val keysToRemove = compiledScripts.keys().asSequence().filter { it.startsWith("${extensionId}_") }.toList()
        keysToRemove.forEach { compiledScripts.remove(it) }
        Log.d(TAG, "Cleared compiled scripts cache for extension: $extensionId")
    }

    /**
     * Thực thi script của extension và trả kết quả JSON string.
     *
     * @param extension Extension đã load (chứa plugin.json + JS files)
     * @param scriptType Loại script cần chạy (HOME, SEARCH, DETAIL, TOC, CHAP...)
     * @param args Tham số truyền vào hàm execute() trong JS
     * @return ExtensionResult.Success(jsonString) hoặc ExtensionResult.Error(message)
     */
    suspend fun execute(
        extension: LoadedExtension,
        scriptType: ScriptType,
        vararg args: String,
        logCollector: MutableList<String>? = null
    ): ExtensionResult {
        val scriptFileName = extension.pluginJson.script[scriptType.key]
            ?: return ExtensionResult.Error(
                "Script type '${scriptType.key}' not found in plugin.json"
            )
        return execute(extension, scriptFileName, *args, logCollector = logCollector)
    }

    suspend fun execute(
        extension: LoadedExtension,
        scriptFileName: String,
        vararg args: String,
        logCollector: MutableList<String>? = null
    ): ExtensionResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "execute scriptFileName: $scriptFileName with args: ${args.toList()}")
        // Rhino Context — mỗi lần execute tạo context mới (thread-safe)
        val rhinoCtx = RhinoContext.enter()
        var bridge: JSBridge? = null
        try {
            // Optimization level -1 = interpreter mode (required for Android — no JIT)
            rhinoCtx.optimizationLevel = -1
            rhinoCtx.languageVersion = RhinoContext.VERSION_ES6
            rhinoCtx.wrapFactory.isJavaPrimitiveWrap = false

            // Tạo global scope
            val scope = rhinoCtx.initStandardObjects()

            // 1. Nạp core.js từ assets chứa các polyfill (sử dụng cache compiled Script)
            val coreScript = compiledScripts.getOrPut("core.js") {
                val coreJs = appContext.assets.open("core.js").bufferedReader().use { it.readText() }
                rhinoCtx.compileString(coreJs, "core.js", 1, null)
            }
            coreScript.exec(rhinoCtx, scope)

            // Phân giải CONFIG_URL theo thứ tự ưu tiên 3 tầng để đồng bộ hóa tên miền AJAX và Cookies
            var resolvedConfigUrl: String? = null

            // Tầng 1: Cấu hình tùy chỉnh của người dùng trong Preferences
            val prefs = appContext.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE)
            val customConfigUrl = prefs.getString("ext_config_${extension.id}_CONFIG_URL", null)
            if (!customConfigUrl.isNullOrBlank()) {
                resolvedConfigUrl = customConfigUrl.trim().trimEnd('/')
            }

            // Tầng 2: Trích xuất từ tham số URL truyện (nếu đối số là URL)
            if (resolvedConfigUrl == null) {
                val firstArg = args.firstOrNull()
                if (firstArg != null && (firstArg.startsWith("http://") || firstArg.startsWith("https://"))) {
                    try {
                        val uri = java.net.URI(firstArg)
                        resolvedConfigUrl = "${uri.scheme}://${uri.host}"
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }

            // Tầng 3: Trích xuất từ trường source mặc định trong plugin.json của extension
            if (resolvedConfigUrl == null) {
                val defaultSource = extension.pluginJson.metadata.source
                if (!defaultSource.isNullOrBlank()) {
                    try {
                        val uri = java.net.URI(defaultSource)
                        resolvedConfigUrl = "${uri.scheme}://${uri.host}"
                    } catch (e: Exception) {
                        resolvedConfigUrl = defaultSource.trim().trimEnd('/')
                    }
                }
            }

            // Inject CONFIG_URL vào scope của Rhino
            if (resolvedConfigUrl != null) {
                var configUrlToInject = resolvedConfigUrl
                if (extension.id.contains("khotruyenchu") || extension.id.contains("kho-truyen-chu")) {
                    configUrlToInject = configUrlToInject.replace(Regex("^https?://"), "").trimEnd('/')
                }
                ScriptableObject.putProperty(scope, "CONFIG_URL", configUrlToInject)
                Log.d(TAG, "Injected CONFIG_URL: $configUrlToInject for extension ${extension.id}")
            }

            // 2. Inject JSBridge hợp nhất
            bridge = JSBridge(rhinoCtx, scope, httpClient, extension.id, appContext, extension, logCollector)
            val bridgeJs = RhinoContext.javaToJS(bridge, scope)
            ScriptableObject.putProperty(scope, "JSBridge", bridgeJs)

            // Inject thêm hàm load() để nạp file JS phụ trong cùng extension (sử dụng cache compiled Script)
            val loadFn = object : org.mozilla.javascript.BaseFunction() {
                override fun call(cx: RhinoContext, s: org.mozilla.javascript.Scriptable, t: org.mozilla.javascript.Scriptable, loadArgs: Array<out Any?>): Any {
                    val fileName = RhinoContext.toString(loadArgs.getOrNull(0) ?: "")
                    if (fileName.isBlank()) return org.mozilla.javascript.Undefined.instance
                    
                    // Sao lưu hàm execute chính để tránh bị tệp phụ ghi đè do hoisting/runtime replacement
                    val backupExecute = s.get("execute", s)
                    
                    val cacheKey = "${extension.id}_$fileName"
                    val subScript = compiledScripts.getOrPut(cacheKey) {
                        val content = extension.getScriptContent(fileName)
                        cx.compileString(content, fileName, 1, null)
                    }
                    subScript.exec(cx, s)
                    
                    // Khôi phục lại hàm execute chính nếu bị ghi đè
                    if (backupExecute != org.mozilla.javascript.Scriptable.NOT_FOUND && backupExecute != null) {
                        s.put("execute", s, backupExecute)
                    }
                    
                    return org.mozilla.javascript.Undefined.instance
                }
            }
            ScriptableObject.putProperty(scope, "load", loadFn)

            // Inject JSFetchFunction Native để override fetch() của core.js,
            // Đảm bảo request gửi đi giống hệt VBook gốc 100% (tránh lỗi 422 do khác biệt JSON serialize)
            JSFetchFunction.inject(rhinoCtx, scope, httpClient, extension.id, appContext)

            // 3. Load và execute script của extension (sử dụng cache compiled Script)
            val extCacheKey = "${extension.id}_$scriptFileName"
            val extScript = compiledScripts.getOrPut(extCacheKey) {
                val scriptContent = extension.getScriptContent(scriptFileName)
                rhinoCtx.compileString(scriptContent, scriptFileName, 1, null)
            }
            extScript.exec(rhinoCtx, scope)

            // 4. Gọi hàm execute() với arguments — bọc try-catch tầng JS để bắt lỗi
            //    giống cách VBook gốc xử lý: nếu script return null hoặc throw,
            //    tự động fallback sang Response.error() thay vì crash.
            val executeCall = buildExecuteCall(args)
            val wrappedCall = """
                (function() {
                    try {
                        var __result = $executeCall;
                        if (__result === null || __result === undefined) {
                            return Response.error("Extension trả về kết quả rỗng");
                        }
                        return __result;
                    } catch(__e) {
                        return Response.error("Lỗi JS: " + __e.message);
                    }
                })()
            """.trimIndent()
            val evalResult = rhinoCtx.evaluateString(scope, wrappedCall, "invoke", 1, null)

            if (evalResult == null || evalResult == org.mozilla.javascript.Undefined.instance) {
                Log.w(TAG, "execute scriptFileName: $scriptFileName returned undefined or null")
                // Fallback: trả về empty list thay vì crash — tương thích VBook gốc
                ExtensionResult.Success("{\"code\":1,\"data\":\"Extension không trả kết quả\"}")
            } else {
                val resStr = RhinoContext.toString(evalResult)
                Log.d(TAG, "execute scriptFileName: $scriptFileName success, data length: ${resStr.length}")
                ExtensionResult.Success(resStr)
            }

        } catch (e: Throwable) {
            Log.e(TAG, "Extension execution failed: ${e.message}", e)
            // Trả về error response JSON thay vì raw error string — tương thích VBook gốc
            ExtensionResult.Error(e.message ?: "Unknown JS execution error")
        } finally {
            bridge?.release()
            RhinoContext.exit()
        }
    }

    /**
     * Build JS call: execute("arg1", "arg2", ...)
     * Escape đặc biệt cho chuỗi JS.
     */
    private fun buildExecuteCall(args: Array<out String>): String {
        if (args.isEmpty()) return "execute()"
        val escapedArgs = args.joinToString(",") { "\"${escapeJs(it)}\"" }
        return "execute($escapedArgs)"
    }

    /** Escape chuỗi cho JS string literal */
    private fun escapeJs(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}
