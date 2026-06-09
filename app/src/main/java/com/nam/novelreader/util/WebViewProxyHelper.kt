package com.nam.novelreader.util

import android.content.Context
import android.util.Log
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature

object WebViewProxyHelper {
    private const val TAG = "WebViewProxyHelper"

    fun applyProxyFromPrefs(context: Context) {
        try {
            val prefs = context.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE)
            val proxyEnabled = prefs.getBoolean("proxy_enabled", false)
            val proxyHost = prefs.getString("proxy_host", "") ?: ""
            val proxyPort = prefs.getString("proxy_port", "80") ?: "80"

            if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                if (proxyEnabled && proxyHost.isNotBlank()) {
                    val proxyUrl = "$proxyHost:$proxyPort"
                    val proxyConfig = ProxyConfig.Builder()
                        .addProxyRule(proxyUrl)
                        .addDirect()
                        .build()

                    ProxyController.getInstance().setProxyOverride(proxyConfig, { it.run() }) {
                        Log.d(TAG, "WebView proxy applied: $proxyUrl")
                    }
                } else {
                    ProxyController.getInstance().clearProxyOverride({ it.run() }) {
                        Log.d(TAG, "WebView proxy cleared")
                    }
                }
            } else {
                Log.w(TAG, "PROXY_OVERRIDE feature is not supported on this device")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying WebView proxy: ${e.message}")
        }
    }
}
