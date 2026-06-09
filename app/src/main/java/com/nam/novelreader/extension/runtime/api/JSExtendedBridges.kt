package com.nam.novelreader.extension.runtime.api

import android.content.Context
import android.os.Build
import android.webkit.WebSettings
import org.mozilla.javascript.*
import org.mozilla.javascript.Context as RhinoContext

/**
 * JSCacheStorage — bộ nhớ tạm cho extension (khác với localStorage — persistent).
 * Dùng SharedPreferences riêng biệt với prefix "ext_cache_".
 * Tương ứng: core.js → cacheStorage object
 */
object JSCacheStorage {

    fun inject(ctx: RhinoContext, scope: ScriptableObject, appContext: Context, extensionId: String) {
        val prefs = appContext.getSharedPreferences("ext_cache_$extensionId", Context.MODE_PRIVATE)
        val cacheObj = ctx.newObject(scope)

        val setItemFn = object : BaseFunction() {
            override fun call(cx: RhinoContext, s: Scriptable, t: Scriptable, args: Array<out Any?>): Any {
                val key = RhinoContext.toString(args.getOrNull(0) ?: "")
                val value = RhinoContext.toString(args.getOrNull(1) ?: "")
                prefs.edit().putString(key, value).apply()
                return Undefined.instance
            }
        }
        ScriptableObject.putProperty(cacheObj, "setItem", setItemFn)

        val getItemFn = object : BaseFunction() {
            override fun call(cx: RhinoContext, s: Scriptable, t: Scriptable, args: Array<out Any?>): Any {
                val key = RhinoContext.toString(args.getOrNull(0) ?: "")
                return prefs.getString(key, null) ?: Undefined.instance
            }
        }
        ScriptableObject.putProperty(cacheObj, "getItem", getItemFn)

        val removeItemFn = object : BaseFunction() {
            override fun call(cx: RhinoContext, s: Scriptable, t: Scriptable, args: Array<out Any?>): Any {
                val key = RhinoContext.toString(args.getOrNull(0) ?: "")
                prefs.edit().remove(key).apply()
                return Undefined.instance
            }
        }
        ScriptableObject.putProperty(cacheObj, "removeItem", removeItemFn)

        val clearFn = object : BaseFunction() {
            override fun call(cx: RhinoContext, s: Scriptable, t: Scriptable, args: Array<out Any?>): Any {
                prefs.edit().clear().apply()
                return Undefined.instance
            }
        }
        ScriptableObject.putProperty(cacheObj, "clear", clearFn)

        ScriptableObject.putProperty(scope, "cacheStorage", cacheObj)
    }
}

/**
 * JSLocalCookie — quản lý cookie riêng cho extension.
 * Lưu vào SharedPreferences key "ext_cookies_{extensionId}".
 * Tương ứng: core.js → localCookie object
 */
object JSLocalCookie {

    fun inject(ctx: RhinoContext, scope: ScriptableObject, appContext: Context, extensionId: String) {
        val prefs = appContext.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE)
        val cookieObj = ctx.newObject(scope)

        val setCookieFn = object : BaseFunction() {
            override fun call(cx: RhinoContext, s: Scriptable, t: Scriptable, args: Array<out Any?>): Any {
                val value = RhinoContext.toString(args.getOrNull(0) ?: "")
                prefs.edit().putString("ext_cookies_$extensionId", value).apply()
                return Undefined.instance
            }
        }
        ScriptableObject.putProperty(cookieObj, "setCookie", setCookieFn)

        val getCookieFn = object : BaseFunction() {
            override fun call(cx: RhinoContext, s: Scriptable, t: Scriptable, args: Array<out Any?>): Any {
                return prefs.getString("ext_cookies_$extensionId", "") ?: ""
            }
        }
        ScriptableObject.putProperty(cookieObj, "getCookie", getCookieFn)

        ScriptableObject.putProperty(scope, "localCookie", cookieObj)
    }
}

/**
 * JSLocalConfig — đọc cấu hình extension (read-only).
 * Đọc từ extension's SharedPreferences.
 * Tương ứng: core.js → localConfig object
 */
object JSLocalConfig {

    fun inject(ctx: RhinoContext, scope: ScriptableObject, appContext: Context, extensionId: String) {
        val prefs = appContext.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE)
        val configObj = ctx.newObject(scope)

        val getItemFn = object : BaseFunction() {
            override fun call(cx: RhinoContext, s: Scriptable, t: Scriptable, args: Array<out Any?>): Any {
                val key = RhinoContext.toString(args.getOrNull(0) ?: "")
                // Đọc cấu hình extension-scoped
                return prefs.getString("ext_config_${extensionId}_$key", null) ?: Undefined.instance
            }
        }
        ScriptableObject.putProperty(configObj, "getItem", getItemFn)

        ScriptableObject.putProperty(scope, "localConfig", configObj)
    }
}

/**
 * JSUserAgent — cung cấp các User-Agent string chuẩn.
 * Tương ứng: core.js → UserAgent object
 */
object JSUserAgent {

    private val CHROME_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private val ANDROID_UA = "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}; ${Build.MODEL}) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    private val IOS_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"

    fun inject(ctx: RhinoContext, scope: ScriptableObject, appContext: Context) {
        val uaObj = ctx.newObject(scope)

        // system() → WebView default UA
        val systemFn = object : BaseFunction() {
            override fun call(cx: RhinoContext, s: Scriptable, t: Scriptable, args: Array<out Any?>): Any {
                return try {
                    com.nam.novelreader.util.UserAgentUtils.getCleanUserAgent(appContext)
                } catch (_: Exception) {
                    ANDROID_UA
                }
            }
        }
        ScriptableObject.putProperty(uaObj, "system", systemFn)

        // chrome()
        val chromeFn = object : BaseFunction() {
            override fun call(cx: RhinoContext, s: Scriptable, t: Scriptable, args: Array<out Any?>): Any = CHROME_UA
        }
        ScriptableObject.putProperty(uaObj, "chrome", chromeFn)

        // android()
        val androidFn = object : BaseFunction() {
            override fun call(cx: RhinoContext, s: Scriptable, t: Scriptable, args: Array<out Any?>): Any = ANDROID_UA
        }
        ScriptableObject.putProperty(uaObj, "android", androidFn)

        // ios()
        val iosFn = object : BaseFunction() {
            override fun call(cx: RhinoContext, s: Scriptable, t: Scriptable, args: Array<out Any?>): Any = IOS_UA
        }
        ScriptableObject.putProperty(uaObj, "ios", iosFn)

        ScriptableObject.putProperty(scope, "UserAgent", uaObj)
    }
}

/**
 * JSGraphics — Canvas/Image API bridge cho xử lý captcha/image.
 * Tương ứng: core.js → Graphics object
 *
 * Đây là phiên bản tối giản — hỗ trợ createImage() để lấy kích thước ảnh
 * và createCanvas() để ghép ảnh (dùng Android Bitmap).
 */
object JSGraphics {

    fun inject(ctx: RhinoContext, scope: ScriptableObject) {
        val graphicsObj = ctx.newObject(scope)

        // Graphics.createCanvas(width, height) → canvas object
        val createCanvasFn = object : BaseFunction() {
            override fun call(cx: RhinoContext, s: Scriptable, t: Scriptable, args: Array<out Any?>): Any {
                val width = (args.getOrNull(0) as? Number)?.toInt() ?: 100
                val height = (args.getOrNull(1) as? Number)?.toInt() ?: 100

                val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)

                val canvasObj = cx.newObject(s)

                // drawImage(image, x, y, ...)
                val drawImageFn = object : BaseFunction() {
                    override fun call(cx2: RhinoContext, s2: Scriptable, t2: Scriptable, drawArgs: Array<out Any?>): Any {
                        // Simplified: decode base64 image and draw it
                        try {
                            val imgObj = drawArgs.getOrNull(0) as? Scriptable
                            val base64 = imgObj?.get("_base64", imgObj)?.toString() ?: return Undefined.instance
                            val x = (drawArgs.getOrNull(1) as? Number)?.toFloat() ?: 0f
                            val y = (drawArgs.getOrNull(2) as? Number)?.toFloat() ?: 0f
                            val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                            val srcBitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (srcBitmap != null) {
                                canvas.drawBitmap(srcBitmap, x, y, null)
                                srcBitmap.recycle()
                            }
                        } catch (_: Exception) { }
                        return Undefined.instance
                    }
                }
                ScriptableObject.putProperty(canvasObj, "drawImage", drawImageFn)

                // capture() → base64 string
                val captureFn = object : BaseFunction() {
                    override fun call(cx2: RhinoContext, s2: Scriptable, t2: Scriptable, capArgs: Array<out Any?>): Any {
                        val stream = java.io.ByteArrayOutputStream()
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                        return android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.NO_WRAP)
                    }
                }
                ScriptableObject.putProperty(canvasObj, "capture", captureFn)

                return canvasObj
            }
        }
        ScriptableObject.putProperty(graphicsObj, "createCanvas", createCanvasFn)

        // Graphics.createImage(base64) → image object with width/height
        val createImageFn = object : BaseFunction() {
            override fun call(cx: RhinoContext, s: Scriptable, t: Scriptable, args: Array<out Any?>): Any {
                val base64 = RhinoContext.toString(args.getOrNull(0) ?: "")
                val imgObj = cx.newObject(s)
                try {
                    val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                    val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ScriptableObject.putProperty(imgObj, "_base64", base64)
                    ScriptableObject.putProperty(imgObj, "width", bitmap?.width ?: 0)
                    ScriptableObject.putProperty(imgObj, "height", bitmap?.height ?: 0)
                    bitmap?.recycle()
                } catch (_: Exception) {
                    ScriptableObject.putProperty(imgObj, "_base64", base64)
                    ScriptableObject.putProperty(imgObj, "width", 0)
                    ScriptableObject.putProperty(imgObj, "height", 0)
                }
                return imgObj
            }
        }
        ScriptableObject.putProperty(graphicsObj, "createImage", createImageFn)

        ScriptableObject.putProperty(scope, "Graphics", graphicsObj)
    }
}
