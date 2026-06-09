package com.nam.novelreader.extension.runtime.api

import android.util.Log
import okhttp3.*
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.Context as RhinoContext

/**
 * JSWebSocketBridge — implement `WebSocket(url, headers)`
 * Tương ứng: core.js → WebSocket
 *
 * Cú pháp:
 * ```js
 * let ws = WebSocket(url, { "header": "value" });
 * ws.connect();
 * ws.send("text");
 * ws.close();
 * // listen events: onMessage, onOpen, onClose, onError
 * ```
 */
object JSWebSocketBridge {
    private const val TAG = "JSWebSocket"

    fun inject(ctx: RhinoContext, scope: ScriptableObject, httpClient: OkHttpClient) {
        val wsFn = object : BaseFunction() {
            override fun call(cx: RhinoContext, s: Scriptable, t: Scriptable, args: Array<out Any?>): Any {
                val url = RhinoContext.toString(args.getOrNull(0) ?: "")
                val headersOpt = args.getOrNull(1) as? ScriptableObject
                return createWebSocketObject(cx, s, httpClient, url, headersOpt)
            }
        }
        ScriptableObject.putProperty(scope, "WebSocket", wsFn)
    }

    private fun createWebSocketObject(
        ctx: RhinoContext,
        scope: Scriptable,
        httpClient: OkHttpClient,
        url: String,
        headersOpt: ScriptableObject?
    ): Scriptable {
        val wsObj = ctx.newObject(scope)
        var webSocket: okhttp3.WebSocket? = null

        val headersBuilder = Headers.Builder()
        if (headersOpt != null) {
            for (id in headersOpt.ids) {
                val key = id.toString()
                val value = RhinoContext.toString(headersOpt.get(key, headersOpt))
                headersBuilder.add(key, value)
            }
        }

        // connect()
        val connectFn = object : BaseFunction() {
            override fun call(cx: RhinoContext, s: Scriptable, t: Scriptable, args: Array<out Any?>): Any {
                try {
                    val request = Request.Builder()
                        .url(url)
                        .headers(headersBuilder.build())
                        .build()

                    webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
                        override fun onOpen(webSocket: okhttp3.WebSocket, response: Response) {
                            Log.d(TAG, "WebSocket opened: $url")
                            invokeCallback(wsObj, "onOpen", response.message)
                        }

                        override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
                            invokeCallback(wsObj, "onMessage", text)
                        }

                        override fun onClosed(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
                            Log.d(TAG, "WebSocket closed: $code $reason")
                            invokeCallback(wsObj, "onClose", reason)
                        }

                        override fun onFailure(webSocket: okhttp3.WebSocket, t: Throwable, response: Response?) {
                            Log.e(TAG, "WebSocket error: ${t.message}")
                            invokeCallback(wsObj, "onError", t.message ?: "Unknown error")
                        }
                    })
                } catch (e: Exception) {
                    Log.e(TAG, "WebSocket connect error: ${e.message}")
                    invokeCallback(wsObj, "onError", e.message ?: "Unknown error")
                }
                return Undefined.instance
            }
        }
        ScriptableObject.putProperty(wsObj, "connect", connectFn)

        // send(text) / sendText(text)
        val sendFn = object : BaseFunction() {
            override fun call(cx: RhinoContext, s: Scriptable, t: Scriptable, args: Array<out Any?>): Any {
                val text = RhinoContext.toString(args.getOrNull(0) ?: "")
                webSocket?.send(text)
                return Undefined.instance
            }
        }
        ScriptableObject.putProperty(wsObj, "send", sendFn)
        ScriptableObject.putProperty(wsObj, "sendText", sendFn)

        // close()
        val closeFn = object : BaseFunction() {
            override fun call(cx: RhinoContext, s: Scriptable, t: Scriptable, args: Array<out Any?>): Any {
                webSocket?.close(1000, "Normal closure")
                return Undefined.instance
            }
        }
        ScriptableObject.putProperty(wsObj, "close", closeFn)

        return wsObj
    }

    private fun invokeCallback(obj: Scriptable, functionName: String, arg: String) {
        val func = obj.get(functionName, obj)
        if (func is org.mozilla.javascript.Function) {
            try {
                // Must enter a new context since this is called from an OkHttp background thread
                val ctx = RhinoContext.enter()
                ctx.optimizationLevel = -1
                func.call(ctx, obj.parentScope ?: obj, obj, arrayOf(arg))
            } catch (e: Exception) {
                Log.e(TAG, "Error invoking callback $functionName: ${e.message}")
            } finally {
                RhinoContext.exit()
            }
        }
    }
}
