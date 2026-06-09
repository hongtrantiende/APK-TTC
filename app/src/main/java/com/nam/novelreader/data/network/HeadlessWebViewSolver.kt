package com.nam.novelreader.data.network

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

import com.nam.novelreader.util.BackgroundWebView

class HeadlessWebViewSolver(private val context: Context) {

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun solveChallenge(url: String): Boolean = withContext(Dispatchers.Main) {
        val result = withTimeoutOrNull(20_000L) { // 20 seconds timeout for solving
            suspendCoroutine<Boolean> { continuation ->
                var isFinished = false
                val webView = BackgroundWebView(context)
                
                webView.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    val defaultUa = com.nam.novelreader.util.UserAgentUtils.getCleanUserAgent(context)
                    userAgentString = defaultUa
                }

                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

                webView.webViewClient = object : WebViewClient() {
                    override fun onReceivedSslError(
                        view: WebView?,
                        handler: android.webkit.SslErrorHandler?,
                        error: android.net.http.SslError?
                    ) {
                        handler?.proceed()
                    }

                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val url = request?.url?.toString() ?: return null
                        if (AdBlocker.isAd(url, checkStaticResources = false)) {
                            Log.d("AdBlocker", "Blocked ad in Headless: $url")
                            return AdBlocker.createEmptyResource()
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // Check if we passed the challenge by checking cookies
                        val cookies = CookieManager.getInstance().getCookie(url)
                        if (cookies != null && (cookies.contains("cf_clearance") || cookies.contains("VBNOM"))) {
                            if (!isFinished) {
                                isFinished = true
                                CookieManager.getInstance().flush()
                                webView.destroy()
                                continuation.resume(true)
                            }
                        }
                    }

                    override fun onReceivedHttpError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        errorResponse: WebResourceResponse?
                    ) {
                        super.onReceivedHttpError(view, request, errorResponse)
                        if (request?.isForMainFrame == true && errorResponse?.statusCode != 403 && errorResponse?.statusCode != 503) {
                            if (!isFinished) {
                                isFinished = true
                                webView.destroy()
                                continuation.resume(false)
                            }
                        }
                    }
                }

                Log.d("HeadlessSolver", "Loading invisible WebView for: $url")
                webView.loadUrl(url)

                // Safety fallback polling for cookies in case onPageFinished doesn't fire when JS sets cookie
                GlobalScope.launch(Dispatchers.Main) {
                    var attempts = 0
                    while (!isFinished && attempts < 20) {
                        delay(1000)
                        val cookies = CookieManager.getInstance().getCookie(url)
                        if (cookies != null && cookies.contains("cf_clearance")) {
                            if (!isFinished) {
                                isFinished = true
                                CookieManager.getInstance().flush()
                                webView.destroy()
                                continuation.resume(true)
                            }
                            break
                        }
                        attempts++
                    }
                }
            }
        }
        
        return@withContext result ?: false
    }
}
