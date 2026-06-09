package com.nam.novelreader.extension.runtime.api

import android.util.Log
import com.nam.novelreader.extension.model.LoadedExtension
import org.mozilla.javascript.*
import org.mozilla.javascript.Context as RhinoContext

/**
 * JSLog — Console.log() bridge.
 * Tương ứng: com.vbook.app.extensions.js.module.log.JSLog
 */
object JSLog {
    private const val TAG = "ExtJS"

    fun inject(ctx: RhinoContext, scope: ScriptableObject) {
        val consoleObj = ctx.newObject(scope)

        val logFn = object : BaseFunction() {
            override fun call(cx: RhinoContext, s: Scriptable, t: Scriptable, args: Array<out Any?>): Any {
                val msg = args.joinToString(" ") { RhinoContext.toString(it) }
                Log.d(TAG, msg)
                return Undefined.instance
            }
        }

        ScriptableObject.putProperty(consoleObj, "log", logFn)
        ScriptableObject.putProperty(consoleObj, "warn", logFn)
        ScriptableObject.putProperty(consoleObj, "error", logFn)
        ScriptableObject.putProperty(consoleObj, "info", logFn)

        ScriptableObject.putProperty(scope, "Console", consoleObj)
        // Cũng inject console (lowercase) cho compatibility
        ScriptableObject.putProperty(scope, "console", consoleObj)
    }
}

/**
 * JSScript — load() function bridge.
 * Tương ứng: com.vbook.app.extensions.js.module.script.JSScript
 *
 * Cho phép extension load file JS khác trong cùng thư mục.
 * Cú pháp: load("config.js")
 */
object JSScript {

    fun inject(ctx: RhinoContext, scope: ScriptableObject, extension: LoadedExtension) {
        val loadFn = object : BaseFunction() {
            override fun call(cx: RhinoContext, s: Scriptable, t: Scriptable, args: Array<out Any?>): Any {
                val fileName = RhinoContext.toString(args.getOrNull(0) ?: "")
                if (fileName.isBlank()) return Undefined.instance

                val content = extension.getScriptContent(fileName)
                cx.evaluateString(s, content, fileName, 1, null)
                return Undefined.instance
            }
        }

        ScriptableObject.putProperty(scope, "load", loadFn)
    }
}

/**
 * JSSleepFunction — sleep(ms) bridge.
 * Tương ứng: com.vbook.app.extensions.js.module.http.JSSleepFunction
 */
object JSSleepFunction {

    fun inject(ctx: RhinoContext, scope: ScriptableObject) {
        val sleepFn = object : BaseFunction() {
            override fun call(cx: RhinoContext, s: Scriptable, t: Scriptable, args: Array<out Any?>): Any {
                val ms = when (val arg = args.getOrNull(0)) {
                    is Number -> arg.toLong()
                    else -> RhinoContext.toString(arg).toLongOrNull() ?: 0L
                }
                if (ms > 0) Thread.sleep(ms)
                return Undefined.instance
            }
        }

        ScriptableObject.putProperty(scope, "sleep", sleepFn)
    }
}
