package com.nam.novelreader.extension.runtime.api

import org.mozilla.javascript.*
import org.mozilla.javascript.Context as RhinoContext

/**
 * JSResponse — bridge cho Response global object.
 * Tương ứng: com.vbook.app.extensions.js.module.response.JSResponse
 *
 * Extensions gọi Response.success(data) để trả kết quả về cho app.
 * Kết quả được serialize thành JSON string và lưu vào __result__ global.
 *
 * Cú pháp:
 * ```js
 * Response.success({ items: [...] });
 * Response.error("Something went wrong");
 * ```
 */
object JSResponse {

    /**
     * Cần inject cùng globalScope để Response.success() lưu __result__
     * vào đúng global scope (không phải activation scope bên trong function).
     */
    fun inject(ctx: RhinoContext, scope: ScriptableObject) {
        val responseObj = ctx.newObject(scope)

        // Response.success(data) — serialize data to JSON, store in __result__ on GLOBAL scope
        val successFn = object : BaseFunction() {
            override fun call(cx: RhinoContext, s: Scriptable, t: Scriptable, args: Array<out Any?>): Any {
                val data = args.getOrNull(0) ?: Undefined.instance
                // Serialize JS object → JSON string
                val json = NativeJSON.stringify(cx, s, data, null, null)
                // Lưu vào global scope (scope), KHÔNG phải s (activation scope)
                ScriptableObject.putProperty(scope, "__result__", json)
                return Undefined.instance
            }
        }
        ScriptableObject.putProperty(responseObj, "success", successFn)

        // Response.error(message) — throw exception to signal error
        val errorFn = object : BaseFunction() {
            override fun call(cx: RhinoContext, s: Scriptable, t: Scriptable, args: Array<out Any?>): Any {
                val message = RhinoContext.toString(args.getOrNull(0) ?: "Unknown error")
                throw EvaluatorException("Extension error: $message")
            }
        }
        ScriptableObject.putProperty(responseObj, "error", errorFn)

        ScriptableObject.putProperty(scope, "Response", responseObj)
    }
}
