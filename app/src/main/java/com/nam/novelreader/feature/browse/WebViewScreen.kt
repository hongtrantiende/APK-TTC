package com.nam.novelreader.feature.browse

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Book
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.navigation.NavHostController
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.nam.novelreader.data.local.dao.ExtensionDao
import com.nam.novelreader.data.local.dao.NovelDao

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DatabaseEntryPoint {
    fun extensionDao(): ExtensionDao
    fun novelDao(): NovelDao
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewScreen(
    url: String,
    extensionId: String? = null,
    navController: NavHostController
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var pageTitle by remember { mutableStateOf("Trình duyệt") }
    var isLoading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var currentUrl by remember { mutableStateOf(url) }

    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    var extensions by remember { mutableStateOf<List<com.nam.novelreader.data.local.entity.ExtensionEntity>>(emptyList()) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.Dispatchers.IO.run {
            try {
                val entryPoint = dagger.hilt.EntryPoints.get(appContext, DatabaseEntryPoint::class.java)
                extensions = entryPoint.extensionDao().getEnabledExtensions()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val matchedExtension = remember(currentUrl, extensions) {
        if (currentUrl.isBlank()) null
        else {
            extensions.firstOrNull { ext ->
                val domain = ext.source.replace("https://", "").replace("http://", "").replace("www.", "").substringBefore("/")
                domain.isNotBlank() && currentUrl.contains(domain, ignoreCase = true)
            }
        }
    }

    val currentExtension = remember(extensionId, extensions) {
        if (extensionId == null) null
        else extensions.firstOrNull { it.id == extensionId }
    }

    val isNotHomepage = remember(currentUrl) {
        val cleanUrl = currentUrl.replace("https://", "").replace("http://", "").replace("www.", "").substringBefore("?")
        cleanUrl.contains("/") && cleanUrl.substringAfter("/").isNotBlank()
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("novel_reader_prefs", android.content.Context.MODE_PRIVATE) }

    var adblockEnabled by remember { mutableStateOf(prefs.getBoolean("browser_adblock_enabled", true)) }
    var desktopModeEnabled by remember { mutableStateOf(prefs.getBoolean("browser_desktop_mode_enabled", false)) }
    var showDropdownMenu by remember { mutableStateOf(false) }

    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    BackHandler(enabled = webView?.canGoBack() == true) {
        webView?.goBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(pageTitle, maxLines = 1, style = MaterialTheme.typography.titleMedium)
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (webView?.canGoBack() == true) {
                            webView?.goBack()
                        } else {
                            navController.popBackStack()
                        }
                    }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        android.webkit.CookieManager.getInstance().flush()
                        if (extensionId != null) {
                            val extBaseUrl = currentExtension?.source?.let { src ->
                                val secure = if (src.startsWith("http://")) src.replaceFirst("http://", "https://") else src
                                val host = try { java.net.URI(secure).host } catch (_: Exception) { null }
                                if (host != null) "https://$host" else secure
                            }
                            val cookieStr = getCookiesForExtension(
                                android.webkit.CookieManager.getInstance(),
                                currentUrl,
                                extBaseUrl
                            )
                            val oldCookie = prefs.getString("ext_cookies_$extensionId", null)
                            val mergedCookie = mergeCookies(oldCookie, cookieStr)
                            
                            if (mergedCookie.isNotBlank()) {
                                val userAgentStr = webView?.settings?.userAgentString
                                val editor = prefs.edit().putString("ext_cookies_$extensionId", mergedCookie)
                                if (!userAgentStr.isNullOrBlank()) {
                                    editor.putString("ext_user_agent_$extensionId", userAgentStr)
                                }
                                editor.apply()
                            }
                        }
                        val prevRoute = navController.previousBackStackEntry?.destination?.route ?: ""
                        if (prevRoute.startsWith("detail")) {
                            navController.previousBackStackEntry?.savedStateHandle?.set("captcha_passed", true)
                        }
                        navController.popBackStack()
                    }) {
                        Icon(Icons.Filled.Check, contentDescription = "Hoàn thành")
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar(
                modifier = Modifier.height(64.dp),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    IconButton(
                        onClick = { webView?.goBack() },
                        enabled = canGoBack
                    ) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                    IconButton(
                        onClick = { webView?.goForward() },
                        enabled = canGoForward
                    ) {
                        Icon(Icons.Filled.ArrowForward, contentDescription = "Tiến lên")
                    }
                    IconButton(onClick = { webView?.loadUrl(url) }) {
                        Icon(Icons.Filled.Home, contentDescription = "Trang chủ")
                    }
                    IconButton(onClick = { webView?.reload() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Tải lại")
                    }
                    Box {
                        IconButton(onClick = { showDropdownMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Thêm")
                        }
                        DropdownMenu(
                            expanded = showDropdownMenu,
                            onDismissRequest = { showDropdownMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Chặn quảng cáo")
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Switch(
                                            checked = adblockEnabled,
                                            onCheckedChange = { checked ->
                                                adblockEnabled = checked
                                                prefs.edit().putBoolean("browser_adblock_enabled", checked).apply()
                                                showDropdownMenu = false
                                                webView?.reload()
                                            },
                                            modifier = Modifier.scale(0.8f)
                                        )
                                    }
                                },
                                onClick = {}
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Chế độ máy tính")
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Switch(
                                            checked = desktopModeEnabled,
                                            onCheckedChange = { checked ->
                                                desktopModeEnabled = checked
                                                prefs.edit().putBoolean("browser_desktop_mode_enabled", checked).apply()
                                                showDropdownMenu = false
                                                
                                                webView?.let { wv ->
                                                    val desktopUa = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                                                    val mobileUa = com.nam.novelreader.util.UserAgentUtils.getCleanUserAgent(context)
                                                    wv.settings.userAgentString = if (checked) desktopUa else mobileUa
                                                    wv.settings.useWideViewPort = checked
                                                    wv.settings.loadWithOverviewMode = checked
                                                    wv.reload()
                                                }
                                            },
                                            modifier = Modifier.scale(0.8f)
                                        )
                                    }
                                },
                                onClick = {}
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Đóng trình duyệt", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Filled.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showDropdownMenu = false
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            androidx.compose.animation.AnimatedVisibility(
                visible = matchedExtension != null && isNotHomepage,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it },
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it }
            ) {
                ExtendedFloatingActionButton(
                    onClick = {
                        // 1. Lưu Cookie & User-Agent hiện tại trước
                        android.webkit.CookieManager.getInstance().flush()
                        if (extensionId != null) {
                            val extBaseUrl = currentExtension?.source?.let { src ->
                                val secure = if (src.startsWith("http://")) src.replaceFirst("http://", "https://") else src
                                val host = try { java.net.URI(secure).host } catch (_: Exception) { null }
                                if (host != null) "https://$host" else secure
                            }
                            val cookieStr = getCookiesForExtension(
                                android.webkit.CookieManager.getInstance(),
                                currentUrl,
                                extBaseUrl
                            )
                            val oldCookie = prefs.getString("ext_cookies_$extensionId", null)
                            val mergedCookie = mergeCookies(oldCookie, cookieStr)
                            
                            if (mergedCookie.isNotBlank()) {
                                val userAgentStr = webView?.settings?.userAgentString
                                val editor = prefs.edit().putString("ext_cookies_$extensionId", mergedCookie)
                                if (!userAgentStr.isNullOrBlank()) {
                                    editor.putString("ext_user_agent_$extensionId", userAgentStr)
                                }
                                editor.apply()
                            }
                        }
                        
                        // 2. Điều hướng quay lại màn hình chi tiết hoặc mở màn hình mới
                        val prevRoute = navController.previousBackStackEntry?.destination?.route ?: ""
                        if (prevRoute.startsWith("detail")) {
                            navController.previousBackStackEntry?.savedStateHandle?.set("captcha_passed", true)
                            navController.popBackStack()
                        } else {
                            val encodedUrl = java.net.URLEncoder.encode(currentUrl, "UTF-8")
                            navController.navigate("detail/${matchedExtension!!.id}?novelUrl=$encodedUrl")
                        }
                    },
                    icon = { Icon(Icons.Filled.Book, contentDescription = null, tint = Color.White) },
                    text = { Text("Đọc truyện", color = Color.White) },
                    containerColor = Color(0xFF4E3629),
                    modifier = Modifier.padding(bottom = 16.dp, end = 8.dp)
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isLoading) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true

                        // Apply desktop settings if enabled
                        val desktopUa = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                        val defaultUa = com.nam.novelreader.util.UserAgentUtils.getCleanUserAgent(ctx)
                        settings.userAgentString = if (desktopModeEnabled) desktopUa else defaultUa
                        settings.useWideViewPort = desktopModeEnabled
                        settings.loadWithOverviewMode = desktopModeEnabled

                        android.webkit.CookieManager.getInstance().setAcceptCookie(true)
                        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        webViewClient = object : WebViewClient() {
                            override fun onReceivedSslError(
                                view: WebView?,
                                handler: android.webkit.SslErrorHandler?,
                                error: android.net.http.SslError?
                            ) {
                                handler?.proceed()
                            }

                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: android.webkit.WebResourceRequest?
                            ): android.webkit.WebResourceResponse? {
                                val currentUrl = request?.url?.toString() ?: return null
                                if (adblockEnabled && com.nam.novelreader.data.network.AdBlocker.isAd(currentUrl, checkStaticResources = false)) {
                                    return com.nam.novelreader.data.network.AdBlocker.createEmptyResource()
                                }
                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoading = true
                                canGoBack = view?.canGoBack() ?: false
                                canGoForward = view?.canGoForward() ?: false
                                url?.let { currentUrl = it }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                canGoBack = view?.canGoBack() ?: false
                                canGoForward = view?.canGoForward() ?: false
                                url?.let { currentUrl = it }
                                android.webkit.CookieManager.getInstance().flush()
                                if (extensionId != null && url != null) {
                                    val extBaseUrl = currentExtension?.source?.let { src ->
                                        val secure = if (src.startsWith("http://")) src.replaceFirst("http://", "https://") else src
                                        val host = try { java.net.URI(secure).host } catch (_: Exception) { null }
                                        if (host != null) "https://$host" else secure
                                    }
                                    val cookieStr = getCookiesForExtension(
                                        android.webkit.CookieManager.getInstance(),
                                        url,
                                        extBaseUrl
                                    )
                                    val sharedPrefs = ctx.getSharedPreferences("novel_reader_prefs", android.content.Context.MODE_PRIVATE)
                                    val oldCookie = sharedPrefs.getString("ext_cookies_$extensionId", null)
                                    val mergedCookie = mergeCookies(oldCookie, cookieStr)
                                    
                                    if (mergedCookie.isNotBlank()) {
                                        val userAgentStr = view?.settings?.userAgentString
                                        val editor = sharedPrefs.edit().putString("ext_cookies_$extensionId", mergedCookie)
                                        if (!userAgentStr.isNullOrBlank()) {
                                            editor.putString("ext_user_agent_$extensionId", userAgentStr)
                                        }
                                        editor.apply()
                                    }
                                }
                            }
                        }


                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress / 100f
                                canGoBack = view?.canGoBack() ?: false
                                canGoForward = view?.canGoForward() ?: false
                                view?.url?.let { currentUrl = it }
                            }
                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                title?.let { pageTitle = it }
                            }
                        }
                        
                        val finalUrl = if (url.contains("sangtacviet.vip")) {
                            url.replace("sangtacviet.vip", "sangtacviet.com")
                        } else if (url.contains("sangtacviet.pro")) {
                            url.replace("sangtacviet.pro", "sangtacviet.com")
                        } else {
                            url
                        }
                        loadUrl(finalUrl)
                        webView = this
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// Add modifier helper to support scale if it's missing in imports
private fun Modifier.scale(scale: Float): Modifier = this.graphicsLayer(scaleX = scale, scaleY = scale)

private fun getCookiesForExtension(
    cookieManager: android.webkit.CookieManager,
    currentUrl: String,
    extBaseUrl: String?
): String {
    val cookies = mutableListOf<String>()

    // 1. Trích xuất host từ currentUrl và tạo HTTPS base URL
    val currentBaseUrl = try {
        val secure = if (currentUrl.startsWith("http://")) {
            currentUrl.replaceFirst("http://", "https://")
        } else {
            currentUrl
        }
        val host = java.net.URI(secure).host
        if (host != null) "https://$host" else secure
    } catch (e: Exception) {
        currentUrl
    }

    val currentCookies = cookieManager.getCookie(currentBaseUrl)
    if (!currentCookies.isNullOrBlank()) {
        cookies.add(currentCookies)
    }

    // 2. Lấy cookie từ extBaseUrl (domain gốc của extension)
    if (extBaseUrl != null && extBaseUrl != currentBaseUrl) {
        val extCookies = cookieManager.getCookie(extBaseUrl)
        if (!extCookies.isNullOrBlank()) {
            cookies.add(extCookies)
        }
    }

    // Gộp và trả về
    var merged = ""
    cookies.forEach { c ->
        merged = mergeCookies(merged, c)
    }
    return merged
}

private fun mergeCookies(oldCookie: String?, newCookie: String?): String {
    val cookieMap = mutableMapOf<String, String>()
    if (!oldCookie.isNullOrBlank()) {
        oldCookie.split(";").forEach { part ->
            val pair = part.trim().split("=", limit = 2)
            if (pair.size == 2) {
                cookieMap[pair[0].trim()] = pair[1].trim()
            }
        }
    }
    if (!newCookie.isNullOrBlank()) {
        newCookie.split(";").forEach { part ->
            val pair = part.trim().split("=", limit = 2)
            if (pair.size == 2) {
                cookieMap[pair[0].trim()] = pair[1].trim()
            }
        }
    }
    return cookieMap.map { "${it.key}=${it.value}" }.joinToString("; ")
}
