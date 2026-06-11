package com.nam.novelreader.feature.reader

import android.app.Activity
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import kotlinx.coroutines.delay

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.platform.LocalConfiguration
import android.content.pm.ActivityInfo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.view.ViewGroup
import android.view.GestureDetector
import android.view.MotionEvent
import android.webkit.WebView
import android.webkit.WebViewClient

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.paint
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.draw.shadow
import com.nam.novelreader.navigation.Routes

// Media3 Player
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.nam.novelreader.data.local.preferences.ReaderPreferences
import com.nam.novelreader.data.repository.NovelRepository
import com.nam.novelreader.domain.model.Chapter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import javax.inject.Inject

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val repository: NovelRepository,
    val readerPreferences: ReaderPreferences,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {
    private val _chapter = MutableStateFlow<Chapter?>(null)
    val chapter: StateFlow<Chapter?> = _chapter

    private val _novel = MutableStateFlow<com.nam.novelreader.domain.model.Novel?>(null)
    val novel: StateFlow<com.nam.novelreader.domain.model.Novel?> = _novel

    fun loadNovel(novelUrl: String) {
        viewModelScope.launch {
            try {
                _novel.value = repository.getNovelFromDb(novelUrl)
            } catch (e: Exception) {
                android.util.Log.e("ReaderViewModel", "loadNovel failed: ${e.message}", e)
            }
        }
    }

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _chapters = MutableStateFlow<List<Chapter>>(emptyList())
    val chapters: StateFlow<List<Chapter>> = _chapters

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex

    fun loadChapter(extensionId: String, chapterUrl: String, isOffline: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            if (isOffline) {
                try {
                    val result = repository.getChapterContentOffline(chapterUrl)
                    if (result != null) {
                        _chapter.value = result
                    } else {
                        _error.value = "Chương chưa được tải xuống! Vui lòng tải chương trước khi đọc offline."
                    }
                } catch (e: Exception) {
                    _error.value = e.message ?: "Lỗi tải chương offline."
                }
                _isLoading.value = false
            } else {
                var success = false
                while (isActive && !success) {
                    try {
                        val result = repository.getChapterContent(extensionId, chapterUrl)
                        _chapter.value = result
                        _error.value = null
                        success = true
                        
                        // Tiền tải chương tiếp theo chạy ngầm (Prefetching)
                        getNextUrl()?.let { nextUrl ->
                            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    repository.getChapterContent(extensionId, nextUrl)
                                } catch (e: Exception) {
                                    // Bỏ qua lỗi prefetch
                                }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("ReaderViewModel", "loadChapter failed: ${e.message}, retrying in 3s...", e)
                        if (_chapter.value == null) {
                            _error.value = e.message ?: "Đã xảy ra lỗi khi tải chương. Đang thử lại..."
                        }
                        kotlinx.coroutines.delay(3000)
                    }
                }
                _isLoading.value = false
            }
        }
    }

    fun loadToc(extensionId: String, novelUrl: String, forceRefresh: Boolean = false, isOffline: Boolean = false) {
        viewModelScope.launch {
            if (isOffline) {
                repository.getCachedChapters(novelUrl).collect { cachedEntities ->
                    _chapters.value = cachedEntities.map { 
                        Chapter(url = it.url, title = it.title, index = it.index, isDownloaded = it.isDownloaded)
                    }.filter { it.isDownloaded }.sortedBy { it.index }
                }
            } else {
                if (forceRefresh) {
                    _isLoading.value = true
                    try {
                        val toc = repository.getTableOfContents(extensionId, novelUrl)
                        if (toc.isNotEmpty()) {
                            repository.cacheChapters(novelUrl, toc)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("ReaderViewModel", "force loadToc failed: ${e.message}", e)
                    } finally {
                        _isLoading.value = false
                    }
                } else {
                    repository.getCachedChapters(novelUrl).collect { cachedEntities ->
                        if (cachedEntities.isNotEmpty()) {
                            _chapters.value = cachedEntities.map { 
                                Chapter(url = it.url, title = it.title, index = it.index, isDownloaded = it.isDownloaded)
                            }.sortedBy { it.index }
                        } else {
                            try {
                                val toc = repository.getTableOfContents(extensionId, novelUrl)
                                _chapters.value = toc
                                repository.cacheChapters(novelUrl, toc)
                            } catch (e: Exception) {
                                android.util.Log.e("ReaderViewModel", "loadToc failed: ${e.message}", e)
                            }
                        }
                    }
                }
            }
        }
    }

    fun findCurrentIndex(chapterUrl: String) {
        val idx = _chapters.value.indexOfFirst { it.url == chapterUrl }
        if (idx >= 0) _currentIndex.value = idx
    }

    private fun countWords(content: String?): Int {
        if (content.isNullOrBlank()) return 0
        val cleanText = content.replace(Regex("<[^>]*>"), " ")
        val words = cleanText.trim().split(Regex("\\s+"))
        return words.size
    }

    fun updateProgress(novelUrl: String, extensionId: String, chapterUrl: String, chapterTitle: String) {
        viewModelScope.launch {
            try {
                repository.updateReadProgress(novelUrl, extensionId, chapterUrl, chapterTitle, 0)
                
                // Tích lũy số chữ đã đọc (hệ thống tu luyện)
                _chapter.value?.let { ch ->
                    if (ch.url == chapterUrl && !ch.content.isNullOrBlank()) {
                        val wordCount = countWords(ch.content)
                        if (wordCount > 0) {
                            val prefs = context.getSharedPreferences("novel_reader_prefs", android.content.Context.MODE_PRIVATE)
                            val readChapterUrls = prefs.getStringSet("words_counted_chapters", emptySet())?.toMutableSet() ?: mutableSetOf()
                            if (!readChapterUrls.contains(chapterUrl)) {
                                readChapterUrls.add(chapterUrl)
                                val currentTotal = prefs.getLong("total_words_read", 0L)
                                prefs.edit()
                                    .putLong("total_words_read", currentTotal + wordCount)
                                    .putStringSet("words_counted_chapters", readChapterUrls)
                                    .apply()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ReaderViewModel", "updateProgress failed: ${e.message}", e)
            }
        }
    }

    val hasPrevious: Boolean get() = _currentIndex.value > 0
    val hasNext: Boolean get() = _currentIndex.value < _chapters.value.size - 1

    fun getPreviousUrl(): String? {
        val idx = _currentIndex.value - 1
        return if (idx >= 0) _chapters.value[idx].url else null
    }

    fun getNextUrl(): String? {
        val idx = _currentIndex.value + 1
        return if (idx < _chapters.value.size) _chapters.value[idx].url else null
    }

    suspend fun getChapterContent(extensionId: String, chapterUrl: String): Chapter? {
        return repository.getChapterContent(extensionId, chapterUrl)
    }

    // ========== Nâng cấp Video Player & Track Resolver ==========
    val resolvedVideoUrl = MutableStateFlow<String?>(null)
    val isResolvingTrack = MutableStateFlow(false)

    fun resolveVideoUrl(extensionId: String, serverUrl: String) {
        viewModelScope.launch {
            isResolvingTrack.value = true
            try {
                val videoExts = listOf(".mp4", ".m3u8", ".mkv", ".webm", ".ts", ".avi", ".mov", ".flv", ".dash")
                val lowerUrl = serverUrl.lowercase()
                val isDirect = serverUrl.startsWith("http") 
                    && !serverUrl.contains("<")
                    && videoExts.any { lowerUrl.contains(it) }

                if (isDirect) {
                    resolvedVideoUrl.value = serverUrl
                } else {
                    val extension = repository.getExtension(extensionId)
                    if (extension?.pluginJson?.script?.containsKey("track") == true) {
                        val trackResult = repository.executeExtension(extensionId, com.nam.novelreader.extension.model.ScriptType.TRACK, serverUrl)
                        if (trackResult is com.nam.novelreader.extension.model.ExtensionResult.Success) {
                            val videoUrl = parseTrackUrl(trackResult.data)
                            if (!videoUrl.isNullOrBlank()) {
                                resolvedVideoUrl.value = videoUrl
                            } else {
                                resolvedVideoUrl.value = serverUrl
                            }
                        } else {
                            resolvedVideoUrl.value = serverUrl
                        }
                    } else {
                        resolvedVideoUrl.value = serverUrl
                    }
                }
            } catch (e: java.lang.Exception) {
                resolvedVideoUrl.value = serverUrl
            } finally {
                isResolvingTrack.value = false
            }
        }
    }

    private fun parseTrackUrl(jsonStr: String): String? {
        return try {
            val element = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.parseToJsonElement(jsonStr)
            if (element is kotlinx.serialization.json.JsonObject) {
                val dataElement = element["data"]
                if (dataElement is kotlinx.serialization.json.JsonObject) {
                    val d = dataElement["data"]
                    val u = dataElement["url"]
                    if (d is kotlinx.serialization.json.JsonPrimitive) d.content
                    else if (u is kotlinx.serialization.json.JsonPrimitive) u.content
                    else null
                } else if (dataElement is kotlinx.serialization.json.JsonPrimitive) {
                    dataElement.content
                } else {
                    val d = element["data"]
                    if (d is kotlinx.serialization.json.JsonPrimitive) d.content else null
                }
            } else {
                null
            }
        } catch (e: java.lang.Exception) {
            null
        }
    }
}

@Composable
fun BouncingDotsIndicator(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "BouncingDots")
    val dot1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(400, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "dot1"
    )
    val dot2 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(400, delayMillis = 150, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "dot2"
    )
    val dot3 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(400, delayMillis = 300, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "dot3"
    )

    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).graphicsLayer(translationY = -dot1 * 15f).background(color, CircleShape))
        Spacer(modifier = Modifier.width(8.dp))
        Box(modifier = Modifier.size(10.dp).graphicsLayer(translationY = -dot2 * 15f).background(color, CircleShape))
        Spacer(modifier = Modifier.width(8.dp))
        Box(modifier = Modifier.size(10.dp).graphicsLayer(translationY = -dot3 * 15f).background(color, CircleShape))
    }
}

@Composable
fun ScrollPercentText(
    scrollPercentProvider: () -> Float,
    color: Color,
    modifier: Modifier = Modifier,
    suffix: String = ""
) {
    Text(
        text = if (suffix.isEmpty()) {
            String.format("1/1  %.1f%%", scrollPercentProvider().coerceIn(0f, 100f))
        } else {
            String.format("%.1f%%$suffix", scrollPercentProvider().coerceIn(0f, 100f))
        },
        fontSize = 11.sp,
        color = color,
        modifier = modifier
    )
}


/**
 * Text Reader — clone 100% VBook:
 * - Slide Drawer mục lục chương từ cạnh trái
 * - Settings Panel tùy chỉnh Font, Size, Margins, Themes (giấy, đêm, xanh bảo vệ mắt)
 * - Slider kéo chọn chương cập nhật nhãn tên chương & % tiến độ tức thời
 * - Auto Keep Screen On dựa trên Preferences
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextReaderScreen(
    extensionId: String,
    novelUrl: String,
    chapterUrl: String,
    isOffline: Boolean = false,
    navController: NavHostController,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val chapter by viewModel.chapter.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val currentIndex by viewModel.currentIndex.collectAsStateWithLifecycle()
    val novel by viewModel.novel.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE) }
    var translationMode by remember(extensionId) {
        mutableStateOf(prefs.getString("ext_translation_mode_$extensionId", "Gốc") ?: "Gốc")
    }
    val isDictLoaded by com.nam.novelreader.util.QuickTranslateEngine.isDictLoadedFlow.collectAsStateWithLifecycle(
        initialValue = com.nam.novelreader.util.QuickTranslateEngine.isDictLoaded()
    )

    var displayTitle by remember { mutableStateOf("") }
    var displayContent by remember { mutableStateOf<String?>(null) }
    var isTranslating by remember { mutableStateOf(false) }

    // Đồng bộ hóa trạng thái tức thời ngay trong Composition để WebView nạp ngay
    var lastChapterUrl by remember { mutableStateOf<String?>(null) }
    val currentUrlFromFlow = chapter?.url
    if (currentUrlFromFlow != null && currentUrlFromFlow != lastChapterUrl) {
        lastChapterUrl = currentUrlFromFlow
        displayTitle = chapter?.title ?: ""
        displayContent = chapter?.content
    }

    LaunchedEffect(chapter?.url, translationMode, isDictLoaded) {
        val ch = chapter
        if (ch != null) {
            if (translationMode != "Gốc" && ch.content != null) {
                isTranslating = true
                val targetMode = if (translationMode.contains("Hán Việt")) "hanviet" else "vi"
                val (tTitle, tContent) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    val title = com.nam.novelreader.util.QuickTranslateEngine.translate(context, ch.title, targetMode)
                    val content = com.nam.novelreader.util.QuickTranslateEngine.translate(context, ch.content, targetMode)
                    Pair(title, content)
                }
                displayTitle = tTitle
                displayContent = tContent
                isTranslating = false
            } else {
                displayTitle = ch.title
                displayContent = ch.content
                isTranslating = false
            }
        }
    }

    androidx.activity.compose.BackHandler {
        navController.popBackStack()
    }

    // Cấu hình đọc trích xuất từ DataStore Preferences
    val fontSize by viewModel.readerPreferences.fontSize.collectAsStateWithLifecycle(initialValue = 18)
    val lineHeight by viewModel.readerPreferences.lineHeight.collectAsStateWithLifecycle(initialValue = 1.6f)
    val paragraphSpacing by viewModel.readerPreferences.paragraphSpacing.collectAsStateWithLifecycle(initialValue = 1.0f)
    val themeIndex by viewModel.readerPreferences.themeIndex.collectAsStateWithLifecycle(initialValue = 0)
    val fontFamily by viewModel.readerPreferences.fontFamily.collectAsStateWithLifecycle(initialValue = "serif")
    val textJustify by viewModel.readerPreferences.textJustify.collectAsStateWithLifecycle(initialValue = true)
    val textIndent by viewModel.readerPreferences.textIndent.collectAsStateWithLifecycle(initialValue = true)
    val keepScreenOn by viewModel.readerPreferences.keepScreenOn.collectAsStateWithLifecycle(initialValue = false)
    val readingMode by viewModel.readerPreferences.readingMode.collectAsStateWithLifecycle(initialValue = 0)

    val marginLeft by viewModel.readerPreferences.marginLeft.collectAsStateWithLifecycle(initialValue = 20)
    val marginRight by viewModel.readerPreferences.marginRight.collectAsStateWithLifecycle(initialValue = 20)
    val marginTop by viewModel.readerPreferences.marginTop.collectAsStateWithLifecycle(initialValue = 24)
    val marginBottom by viewModel.readerPreferences.marginBottom.collectAsStateWithLifecycle(initialValue = 24)
    val brightness by viewModel.readerPreferences.brightness.collectAsStateWithLifecycle(initialValue = -1f)

    val charSpacing by viewModel.readerPreferences.charSpacing.collectAsStateWithLifecycle(initialValue = 0.0f)
    val textIndentValue by viewModel.readerPreferences.textIndentValue.collectAsStateWithLifecycle(initialValue = 1.5f)
    val textAlign by viewModel.readerPreferences.textAlign.collectAsStateWithLifecycle(initialValue = 3)
    val eyeComfort by viewModel.readerPreferences.eyeComfort.collectAsStateWithLifecycle(initialValue = false)
    val doublePage by viewModel.readerPreferences.doublePage.collectAsStateWithLifecycle(initialValue = false)

    var showUI by remember { mutableStateOf(false) }
    var showMenuDropdown by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var currentChapterUrl by remember { mutableStateOf(chapterUrl) }
    val loadedChaptersInWebView = remember { mutableStateListOf<String>() }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    var isAutoScrolling by remember { mutableStateOf(false) }
    var autoScrollSpeed by remember { mutableIntStateOf(prefs.getInt("reader_auto_scroll_speed", 2)) }
    var resumeScrollJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    LaunchedEffect(isAutoScrolling, autoScrollSpeed, webViewRef) {
        val wv = webViewRef
        if (wv != null && readingMode == 0) {
            if (isAutoScrolling) {
                wv.evaluateJavascript("startAutoScroll($autoScrollSpeed);", null)
            } else {
                wv.evaluateJavascript("stopAutoScroll();", null)
            }
        }
    }

    var showAudioPlayer by remember { mutableStateOf(false) }
    var showTocBottomSheet by remember { mutableStateOf(false) }
    val scrollPercentState = remember { mutableFloatStateOf(0f) }
    var webViewAlpha by remember { mutableStateOf(0f) }
    val animatedAlpha by animateFloatAsState(
        targetValue = webViewAlpha,
        animationSpec = tween(400),
        label = "WebViewAlpha"
    )

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.let { wv ->
                wv.post {
                    try {
                        wv.stopLoading()
                        wv.clearHistory()
                        wv.removeAllViews()
                        wv.destroy()
                    } catch (e: Exception) {
                        android.util.Log.e("Reader", "Destroy webview error", e)
                    }
                }
            }
        }
    }


    // Trạng thái đồng bộ TTS
    var isTtsActive by remember { mutableStateOf(false) }
    var isTtsPlaying by remember { mutableStateOf(false) }
    var ttsSentence by remember { mutableStateOf("") }
    var ttsTimerRemaining by remember { mutableIntStateOf(0) }
    var ttsChapterUrl by remember { mutableStateOf("") }
    var ttsSpeed by remember { mutableFloatStateOf(1.0f) }
    var ttsSentenceIndex by remember { mutableIntStateOf(0) }
    var ttsTotalSentences by remember { mutableIntStateOf(0) }
    var ttsBgMusic by remember { mutableStateOf("Không có") }
    var ttsElapsedSeconds by remember { mutableIntStateOf(0) }
    var ttsTotalSeconds by remember { mutableIntStateOf(0) }
    var shouldAutoStartTtsOnNextChapter by remember { mutableStateOf(false) }
    var shouldStartTtsOnChapterLoaded by remember { mutableStateOf(false) }
    
    var showTocDrawer by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val activity = context as? android.app.Activity
    val view = androidx.compose.ui.platform.LocalView.current

    // Keep screen on side-effect
    LaunchedEffect(keepScreenOn) {
        if (keepScreenOn) {
            activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Screen brightness side-effect
    LaunchedEffect(brightness) {
        val layoutParams = activity?.window?.attributes
        if (brightness >= 0f) {
            layoutParams?.screenBrightness = brightness
        } else {
            layoutParams?.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        activity?.window?.attributes = layoutParams
    }

    DisposableEffect(Unit) {
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            // Restore default brightness on dispose
            activity?.let { act ->
                val lp = act.window?.attributes
                lp?.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                act.window?.attributes = lp
            }
            // Restore system bars on exit
            activity?.window?.let { window ->
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, view)
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
            // Auto stop TTS on exit if enabled
            val stopOnExitVal = prefs.getBoolean("reader_tts_stop_on_exit", false)
            if (stopOnExitVal) {
                val stopIntent = Intent(context, com.nam.novelreader.service.TextToSpeechService::class.java).apply {
                    action = com.nam.novelreader.service.TextToSpeechService.ACTION_STOP
                }
                context.startService(stopIntent)
            }
        }
    }

    LaunchedEffect(showUI) {
        activity?.window?.let { window ->
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, view)
            if (showUI) {
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            } else {
                insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == com.nam.novelreader.service.TextToSpeechService.BROADCAST_TTS_STATUS) {
                    isTtsActive = intent.getBooleanExtra(com.nam.novelreader.service.TextToSpeechService.EXTRA_STATUS_ACTIVE, false)
                    isTtsPlaying = intent.getBooleanExtra(com.nam.novelreader.service.TextToSpeechService.EXTRA_STATUS_PLAYING, false)
                    ttsSentence = intent.getStringExtra(com.nam.novelreader.service.TextToSpeechService.EXTRA_STATUS_TEXT) ?: ""
                    ttsTimerRemaining = intent.getIntExtra(com.nam.novelreader.service.TextToSpeechService.EXTRA_STATUS_TIMER_REMAINING, 0)
                    ttsChapterUrl = intent.getStringExtra(com.nam.novelreader.service.TextToSpeechService.EXTRA_STATUS_CHAPTER_URL) ?: ""
                    ttsSpeed = intent.getFloatExtra(com.nam.novelreader.service.TextToSpeechService.EXTRA_STATUS_SPEED, 1.0f)
                    ttsSentenceIndex = intent.getIntExtra(com.nam.novelreader.service.TextToSpeechService.EXTRA_STATUS_INDEX, 0)
                    ttsTotalSentences = intent.getIntExtra("extra_status_total", 0)
                    ttsBgMusic = intent.getStringExtra("extra_status_bg_music") ?: "Không có"
                    
                    ttsElapsedSeconds = intent.getIntExtra("extra_status_elapsed_seconds", 0)
                    ttsTotalSeconds = intent.getIntExtra("extra_status_total_seconds", 0)
                    
                    // Nếu đang nghe chương khác, chuyển chapter trên UI cho đồng bộ
                    if (ttsChapterUrl.isNotEmpty() && ttsChapterUrl != "sample_tts_test" && ttsChapterUrl != currentChapterUrl) {
                        currentChapterUrl = ttsChapterUrl
                    }
                } else if (intent?.action == com.nam.novelreader.service.TextToSpeechService.ACTION_TTS_CHAPTER_COMPLETED) {
                    val completedUrl = intent.getStringExtra(com.nam.novelreader.service.TextToSpeechService.EXTRA_COMPLETED_CHAPTER_URL)
                    val isPrev = intent.getBooleanExtra("is_prev", false)
                    if (completedUrl == currentChapterUrl) {
                        if (isPrev) {
                            viewModel.getPreviousUrl()?.let { prevUrl ->
                                shouldAutoStartTtsOnNextChapter = true
                                webViewAlpha = 0.4f
                                currentChapterUrl = prevUrl
                            }
                        } else {
                            val ttsAutoNext = prefs.getBoolean("tts_auto_next_chapter", true)
                            if (ttsAutoNext) {
                                viewModel.getNextUrl()?.let { nextUrl ->
                                    shouldAutoStartTtsOnNextChapter = true
                                    webViewAlpha = 0.4f
                                    currentChapterUrl = nextUrl
                                }
                            }
                        }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(com.nam.novelreader.service.TextToSpeechService.BROADCAST_TTS_STATUS)
            addAction(com.nam.novelreader.service.TextToSpeechService.ACTION_TTS_CHAPTER_COMPLETED)
        }
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED
        )
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    LaunchedEffect(extensionId, novelUrl, isOffline) {
        kotlinx.coroutines.delay(250)
        viewModel.loadToc(extensionId, novelUrl, isOffline = isOffline)
        viewModel.loadNovel(novelUrl)
    }

    LaunchedEffect(currentChapterUrl, chapters) {
        viewModel.findCurrentIndex(currentChapterUrl)
    }

    LaunchedEffect(currentChapterUrl, isOffline) {
        if (!loadedChaptersInWebView.contains(currentChapterUrl)) {
            webViewAlpha = 0.4f
            loadedChaptersInWebView.clear()
            loadedChaptersInWebView.add(currentChapterUrl)
            if (chapter == null) {
                kotlinx.coroutines.delay(250)
            }
            viewModel.loadChapter(extensionId, currentChapterUrl, isOffline = isOffline)
        } else {
            viewModel.findCurrentIndex(currentChapterUrl)
        }
    }

    LaunchedEffect(chapter) {
        chapter?.let { ch ->
            viewModel.updateProgress(novelUrl, extensionId, ch.url, ch.title)
            if (shouldAutoStartTtsOnNextChapter || shouldStartTtsOnChapterLoaded) {
                shouldAutoStartTtsOnNextChapter = false
                shouldStartTtsOnChapterLoaded = false
                val intent = Intent(context, com.nam.novelreader.service.TextToSpeechService::class.java).apply {
                    action = com.nam.novelreader.service.TextToSpeechService.ACTION_START
                    putExtra(com.nam.novelreader.service.TextToSpeechService.EXTRA_CHAPTER_URL, ch.url)
                    putExtra(com.nam.novelreader.service.TextToSpeechService.EXTRA_NOVEL_URL, novelUrl)
                    putExtra(com.nam.novelreader.service.TextToSpeechService.EXTRA_EXTENSION_ID, extensionId)
                    putExtra(com.nam.novelreader.service.TextToSpeechService.EXTRA_TITLE, displayTitle)
                    putExtra(com.nam.novelreader.service.TextToSpeechService.EXTRA_CONTENT, "")
                }
                context.startService(intent)
            }
        }
    }

    // Xác định màu nền của Toolbar và Panel dựa trên theme Index đọc để tiệp màu
    // Theme 0-7: sáng (khớp bg1-bg7 VBook) | Theme 8-17: tối (bg8-bg17 VBook)
    val (uiBgColor, uiTextColor, uiPrimaryColor) = when (themeIndex) {
        0  -> Triple(Color(0xFFD3C3A3), Color(0xFF3A3129), Color(0xFF8B5A2B)) // bg1: Kraft hoa văn
        1  -> Triple(Color(0xFFFFFFFF), Color(0xFF3A342B), Color(0xFF1976D2)) // bg6: Trắng trơn
        2  -> Triple(Color(0xFFF1F7ED), Color(0xFF1B310E), Color(0xFF2E7D32)) // bg4: Xanh lá hoa văn
        3  -> Triple(Color(0xFFA2C0E5), Color(0xFF1B310E), Color(0xFF1976D2)) // bg2: Xanh dương hoa văn
        4  -> Triple(Color(0xFF000000), Color(0xFFCCCCCC), Color(0xFFD4A574)) // bg_dark: Đêm đen thuần
        5  -> Triple(Color(0xFFF3C9D7), Color(0xFF1B310E), Color(0xFFC2185B)) // bg3: Hồng hoa văn
        6  -> Triple(Color(0xFFECE1CA), Color(0xFF645032), Color(0xFF8B5A2B)) // bg5: Vàng giấy trơn
        7  -> Triple(Color(0xFFC2E0CD), Color(0xFF334B39), Color(0xFF2E7D32)) // bg7: Lục nhạt trơn
        // Dark themes (bg8-bg17 VBook)
        8  -> Triple(Color(0xFF393030), Color(0xFF95938F), Color(0xFFD4A574)) // bg8: Nâu tối
        9  -> Triple(Color(0xFF333333), Color(0xFFCCE8CF), Color(0xFF66BB6A)) // bg9: Xám tối xanh
        10 -> Triple(Color(0xFF051C2C), Color(0xFF637079), Color(0xFF4FC3F7)) // bg10: Navy đêm
        11 -> Triple(Color(0xFF152B06), Color(0xFF607057), Color(0xFF66BB6A)) // bg11: Xanh rừng tối
        12 -> Triple(Color(0xFF151C1F), Color(0xFF4D5052), Color(0xFF78909C)) // bg12: Xanh đen
        13 -> Triple(Color(0xFF000000), Color(0xFF5F5F5F), Color(0xFF9E9E9E)) // bg13: Đen 1
        14 -> Triple(Color(0xFF000000), Color(0xFF494949), Color(0xFF757575)) // bg14: Đen 2
        15 -> Triple(Color(0xFF001622), Color(0xFF204353), Color(0xFF4FC3F7)) // bg15: Xanh đậm
        16 -> Triple(Color(0xFF171F27), Color(0xFF445053), Color(0xFF78909C)) // bg16: Slate tối
        17 -> Triple(Color(0xFF251C05), Color(0xFF574F3C), Color(0xFFD4A574)) // bg17: Đồng tối
        else -> Triple(Color(0xFFD3C3A3), Color(0xFF3A3129), Color(0xFF8B5A2B))
    }

    val customColorScheme = if (themeIndex >= 4) {
        darkColorScheme(
            background = uiBgColor,
            surface = uiBgColor,
            onBackground = uiTextColor,
            onSurface = uiTextColor,
            primary = uiPrimaryColor,
            onPrimary = Color.Black
        )
    } else {
        lightColorScheme(
            background = uiBgColor,
            surface = uiBgColor,
            onBackground = uiTextColor,
            onSurface = uiTextColor,
            primary = uiPrimaryColor,
            onPrimary = Color.White
        )
    }

    // Remove ModalNavigationDrawer and use Box to contain everything
    MaterialTheme(colorScheme = customColorScheme) {
        Box(modifier = Modifier.fillMaxSize().background(uiBgColor)) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            if (error != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "❌ Lỗi Tải Trang", color = uiTextColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = error ?: "", color = uiTextColor, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { viewModel.loadChapter(extensionId, currentChapterUrl) },
                            colors = ButtonDefaults.buttonColors(containerColor = uiPrimaryColor, contentColor = uiBgColor)
                        ) {
                            Text("Thử lại")
                        }
                    }
                }
            } else {
                chapter?.let { ch ->
                    val htmlContent = remember(
                        ch.url, displayContent, displayTitle, fontSize, lineHeight, paragraphSpacing, themeIndex,
                        fontFamily, textAlign, textIndentValue, charSpacing, marginLeft, marginRight, marginTop, marginBottom, readingMode, doublePage
                    ) {
                        buildReaderHtml(
                            context = context,
                            content = displayContent ?: "",
                            title = displayTitle,
                            chapterNum = if (chapters.isNotEmpty()) "${currentIndex + 1}/${chapters.size}" else "",
                            chapterUrl = ch.url,
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            paragraphSpacing = paragraphSpacing,
                            themeIndex = themeIndex,
                            fontFamily = fontFamily,
                            textAlign = textAlign,
                            textIndentValue = textIndentValue,
                            charSpacing = charSpacing,
                            marginLeft = marginLeft,
                            marginRight = marginRight,
                            marginTop = marginTop,
                            marginBottom = marginBottom,
                            readingMode = readingMode,
                            doublePage = doublePage
                        )
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        AndroidView(
                        factory = { context ->
                            val gd = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                                    val width = webViewRef?.width ?: 1
                                    val x = e.x
                                    if (readingMode != 0) {
                                        if (x < width / 3) {
                                            webViewRef?.evaluateJavascript("scrollPrev();", null)
                                        } else if (x > width * 2 / 3) {
                                            webViewRef?.evaluateJavascript("scrollNext();", null)
                                        } else {
                                            showUI = !showUI
                                        }
                                    } else {
                                        showUI = !showUI
                                    }
                                    return true
                                }
                            })
                            WebView(context).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    setOnScrollChangeListener { _, scrollX, scrollY, _, _ ->
                                        if (readingMode != 0) {
                                            val totalWidth = try {
                                                val method = android.view.View::class.java.getDeclaredMethod("computeHorizontalScrollRange")
                                                method.isAccessible = true
                                                method.invoke(this@apply) as Int
                                            } catch (e: Exception) {
                                                width
                                            }
                                            val webViewWidth = width
                                            val scrollRange = totalWidth - webViewWidth
                                            scrollPercentState.floatValue = if (scrollRange > 0) {
                                                (scrollX.toFloat() / scrollRange) * 100f
                                            } else {
                                                0f
                                            }
                                        } else {
                                            val totalHeight = contentHeight * resources.displayMetrics.density
                                            val webViewHeight = height
                                            val scrollRange = totalHeight - webViewHeight
                                            scrollPercentState.floatValue = if (scrollRange > 0) {
                                                (scrollY.toFloat() / scrollRange) * 100f
                                            } else {
                                                0f
                                            }
                                        }
                                    }
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                                        val url = request?.url?.toString() ?: ""
                                        if (url == "vbook://prev") {
                                            viewModel.getPreviousUrl()?.let { 
                                                webViewAlpha = 0.4f
                                                currentChapterUrl = it 
                                            }
                                            return true
                                        }
                                        if (url == "vbook://next") {
                                            viewModel.getNextUrl()?.let { 
                                                webViewAlpha = 0.4f
                                                currentChapterUrl = it 
                                            }
                                            return true
                                        }
                                        if (url == "vbook://near-end") {
                                            val nextUrl = viewModel.getNextUrl()
                                            if (nextUrl != null && !loadedChaptersInWebView.contains(nextUrl)) {
                                                loadedChaptersInWebView.add(nextUrl)
                                                scope.launch {
                                                    try {
                                                         val nextChap = viewModel.getChapterContent(extensionId, nextUrl)
                                                         if (nextChap != null) {
                                                             val nextContent = nextChap.content
                                                             val nextTitle = nextChap.title
                                                             if (nextContent != null) {
                                                                 val (displayNextTitle, displayNextContent) = if (translationMode != "Gốc") {
                                                                     val targetMode = if (translationMode.contains("Hán Việt")) "hanviet" else "vi"
                                                                     val title = com.nam.novelreader.util.QuickTranslateEngine.translate(context, nextTitle, targetMode)
                                                                     val content = com.nam.novelreader.util.QuickTranslateEngine.translate(context, nextContent, targetMode)
                                                                     Pair(title, content)
                                                                 } else {
                                                                     Pair(nextTitle, nextContent)
                                                                 }
                                                                 val formatted = formatHtmlContent(displayNextContent)
                                                                 val escapedTitle = displayNextTitle.replace("'", "\\'").replace("\"", "\\\"")
                                                                 val escapedContent = formatted.replace("'", "\\'").replace("\"", "\\\"").replace("\n", "\\\n").replace("\r", "")
                                                                 val js = "appendChapter('$nextUrl', '$escapedTitle', '$escapedContent');"
                                                                 webViewRef?.evaluateJavascript(js, null)
                                                             } else {
                                                                 webViewRef?.evaluateJavascript("resetLoadFlag();", null)
                                                             }
                                                         } else {
                                                             webViewRef?.evaluateJavascript("resetLoadFlag();", null)
                                                             loadedChaptersInWebView.remove(nextUrl)
                                                         }
                                                     } catch (e: Exception) {
                                                         android.util.Log.e("ReaderScreens", "Failed to append next chapter", e)
                                                         webViewRef?.evaluateJavascript("resetLoadFlag();", null)
                                                         loadedChaptersInWebView.remove(nextUrl)
                                                     }
                                                }
                                            } else {
                                                webViewRef?.evaluateJavascript("resetLoadFlag();", null)
                                            }
                                            return true
                                        }
                                        if (url.startsWith("vbook://current-chapter?url=")) {
                                            val targetUrl = android.net.Uri.parse(url).getQueryParameter("url") ?: ""
                                            if (targetUrl.isNotEmpty() && targetUrl != currentChapterUrl) {
                                                currentChapterUrl = targetUrl
                                            }
                                            return true
                                        }
                                        return super.shouldOverrideUrlLoading(view, request)
                                    }
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        webViewAlpha = 1f
                                    }
                                }
                                settings.apply {
                                    javaScriptEnabled = true
                                    defaultTextEncodingName = "UTF-8"
                                    loadWithOverviewMode = true
                                    useWideViewPort = false
                                    setSupportZoom(false)
                                    builtInZoomControls = false
                                    displayZoomControls = false
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    userAgentString = userAgentString?.replace("; wv", "")
                                }
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                setOnTouchListener { _, event ->
                                    gd.onTouchEvent(event)
                                    if (readingMode == 0 && isAutoScrolling) {
                                        when (event.action) {
                                            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                                                resumeScrollJob?.cancel()
                                                webViewRef?.evaluateJavascript("stopAutoScroll();", null)
                                            }
                                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                                resumeScrollJob?.cancel()
                                                resumeScrollJob = scope.launch {
                                                    kotlinx.coroutines.delay(2000)
                                                    if (isAutoScrolling) {
                                                        webViewRef?.evaluateJavascript("startAutoScroll($autoScrollSpeed);", null)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    false
                                }
                                webViewRef = this
                            }
                        },
                        update = { webView ->
                            if (webView.tag != htmlContent) {
                                webView.tag = htmlContent
                                webView.loadDataWithBaseURL(novelUrl, htmlContent, "text/html", "UTF-8", null)
                            }
                        },
                        modifier = Modifier.fillMaxSize().graphicsLayer(alpha = animatedAlpha),
                    )

                    if (eyeComfort) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFFE89A3C).copy(alpha = 0.15f))
                        )
                    }
                }
            // Hai nút chuyển chương nổi ở hai biên (Overlay Buttons)
            AnimatedVisibility(
                visible = showUI,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Surface(
                    onClick = {
                        if (viewModel.hasPrevious) {
                            viewModel.getPreviousUrl()?.let {
                                webViewAlpha = 0.4f
                                currentChapterUrl = it
                            }
                        }
                    },
                    enabled = viewModel.hasPrevious,
                    shape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                    color = uiBgColor.copy(alpha = 0.9f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, uiTextColor.copy(alpha = 0.1f)),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .width(42.dp)
                        .height(76.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.ChevronLeft,
                            contentDescription = "Chương trước",
                            tint = if (viewModel.hasPrevious) uiTextColor else uiTextColor.copy(alpha = 0.3f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = showUI,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Surface(
                    onClick = {
                        if (viewModel.hasNext) {
                            viewModel.getNextUrl()?.let {
                                webViewAlpha = 0.4f
                                currentChapterUrl = it
                            }
                        }
                    },
                    enabled = viewModel.hasNext,
                    shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp),
                    color = uiBgColor.copy(alpha = 0.9f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, uiTextColor.copy(alpha = 0.1f)),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .width(42.dp)
                        .height(76.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = "Chương sau",
                            tint = if (viewModel.hasNext) uiTextColor else uiTextColor.copy(alpha = 0.3f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // === Footer cố định ở đáy ===
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (chapters.isNotEmpty()) "${currentIndex + 1}/${chapters.size}" else "",
                        fontSize = 11.sp,
                        color = uiTextColor.copy(alpha = 0.5f)
                    )
                    ScrollPercentText(
                        scrollPercentProvider = { scrollPercentState.floatValue },
                        color = uiTextColor.copy(alpha = 0.5f)
                    )
                }
            }

            // === Top Bar ===
            AnimatedVisibility(
                visible = showUI,
                enter = slideInVertically(initialOffsetY = { -it }),
                exit = slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                var showTranslationDialog by remember { mutableStateOf(false) }

                if (showTranslationDialog) {
                    var tempSource by remember(extensionId, showTranslationDialog) {
                        mutableStateOf(prefs.getString("ext_translate_source_$extensionId", "Tiếng Trung") ?: "Tiếng Trung")
                    }
                    var tempTarget by remember(extensionId, showTranslationDialog) {
                        mutableStateOf(prefs.getString("ext_translate_target_$extensionId", "Việt (VP)") ?: "Việt (VP)")
                    }
                    var tempEngine by remember(extensionId, showTranslationDialog) {
                        mutableStateOf(prefs.getString("ext_translate_engine_$extensionId", "QT") ?: "QT")
                    }
                    var tempScope by remember(extensionId, showTranslationDialog) {
                        mutableStateOf(prefs.getString("ext_translate_scope_$extensionId", "Tất cả") ?: "Tất cả")
                    }
                    var tempEnabled by remember(extensionId, showTranslationDialog) {
                        mutableStateOf(translationMode != "Gốc")
                    }

                    var sourceMenuExpanded by remember { mutableStateOf(false) }
                    var targetMenuExpanded by remember { mutableStateOf(false) }
                    var engineMenuExpanded by remember { mutableStateOf(false) }
                    var scopeMenuExpanded by remember { mutableStateOf(false) }

                    val themeSwitchThumbChecked = com.nam.novelreader.feature.components.VBookTheme.switchThumbCheckedColor()
                    val themeSwitchTrackChecked = com.nam.novelreader.feature.components.VBookTheme.switchTrackCheckedColor()
                    val themeSwitchThumbUnchecked = com.nam.novelreader.feature.components.VBookTheme.switchThumbUncheckedColor()
                    val themeSwitchTrackUnchecked = com.nam.novelreader.feature.components.VBookTheme.switchTrackUncheckedColor()

                    Dialog(onDismissRequest = { showTranslationDialog = false }) {
                        Box(
                            modifier = Modifier
                                .width(360.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .background(uiBgColor)
                                .border(1.dp, uiTextColor.copy(alpha = 0.12f), RoundedCornerShape(28.dp))
                                .padding(20.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = { showTranslationDialog = false }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = uiTextColor)
                                    }
                                    Text(
                                        text = "Dịch",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = uiTextColor
                                    )
                                    IconButton(onClick = {
                                        showTranslationDialog = false
                                        navController.navigate(Routes.TRANSLATION_SETTINGS)
                                    }) {
                                        Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = uiTextColor)
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(uiTextColor.copy(alpha = 0.08f))
                                                .clickable { sourceMenuExpanded = true }
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(text = tempSource, color = uiTextColor, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(Icons.Filled.KeyboardArrowDown, null, tint = uiTextColor, modifier = Modifier.size(16.dp))
                                        }
                                        DropdownMenu(
                                            expanded = sourceMenuExpanded,
                                            onDismissRequest = { sourceMenuExpanded = false }
                                        ) {
                                            listOf("Tiếng Trung", "Tự động nhận diện", "Tiếng Anh", "Tiếng Nhật", "Tiếng Hàn").forEach { lang ->
                                                DropdownMenuItem(
                                                    text = { Text(lang) },
                                                    onClick = {
                                                        tempSource = lang
                                                        sourceMenuExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Switch(
                                        checked = tempEnabled,
                                        onCheckedChange = { tempEnabled = it },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = themeSwitchThumbChecked,
                                            checkedTrackColor = themeSwitchTrackChecked,
                                            uncheckedThumbColor = themeSwitchThumbUnchecked,
                                            uncheckedTrackColor = themeSwitchTrackUnchecked
                                        )
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Box(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(uiTextColor.copy(alpha = 0.08f))
                                                .clickable { targetMenuExpanded = true }
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(text = tempTarget, color = uiTextColor, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(Icons.Filled.KeyboardArrowDown, null, tint = uiTextColor, modifier = Modifier.size(16.dp))
                                        }
                                        DropdownMenu(
                                            expanded = targetMenuExpanded,
                                            onDismissRequest = { targetMenuExpanded = false }
                                        ) {
                                            listOf("Việt (VP)", "Hán Việt", "Việt (Dịch)").forEach { lang ->
                                                DropdownMenuItem(
                                                    text = { Text(lang) },
                                                    onClick = {
                                                        tempTarget = lang
                                                        targetMenuExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.weight(1.1f)) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(uiTextColor.copy(alpha = 0.08f))
                                                .clickable { engineMenuExpanded = true }
                                                .padding(horizontal = 10.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .background(uiPrimaryColor, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.MenuBook,
                                                    contentDescription = null,
                                                    tint = uiBgColor,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(text = tempEngine, color = uiTextColor, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                            Icon(Icons.Filled.KeyboardArrowDown, null, tint = uiTextColor, modifier = Modifier.size(16.dp))
                                        }
                                        DropdownMenu(
                                            expanded = engineMenuExpanded,
                                            onDismissRequest = { engineMenuExpanded = false }
                                        ) {
                                            listOf("QT", "Google", "Bắc Cực Tinh", "GGChan").forEach { eng ->
                                                DropdownMenuItem(
                                                    text = { Text(eng) },
                                                    onClick = {
                                                        tempEngine = eng
                                                        engineMenuExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Row(
                                        modifier = Modifier.weight(1.3f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(uiTextColor.copy(alpha = 0.08f))
                                                .clickable { scopeMenuExpanded = true }
                                                .padding(horizontal = 8.dp, vertical = 10.dp),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(text = tempScope, color = uiTextColor, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Icon(Icons.Filled.KeyboardArrowDown, null, tint = uiTextColor, modifier = Modifier.size(14.dp))
                                            }
                                            DropdownMenu(
                                                expanded = scopeMenuExpanded,
                                                onDismissRequest = { scopeMenuExpanded = false }
                                            ) {
                                                listOf("Tất cả", "Tên truyện", "Nội dung").forEach { scp ->
                                                    DropdownMenuItem(
                                                        text = { Text(scp) },
                                                        onClick = {
                                                            tempScope = scp
                                                            scopeMenuExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Button(
                                            onClick = {
                                                val finalMode = if (tempEnabled) tempTarget else "Gốc"
                                                prefs.edit().apply {
                                                    putString("ext_translation_mode_$extensionId", finalMode)
                                                    putString("ext_translate_source_$extensionId", tempSource)
                                                    putString("ext_translate_target_$extensionId", tempTarget)
                                                    putString("ext_translate_engine_$extensionId", tempEngine)
                                                    putString("ext_translate_scope_$extensionId", tempScope)
                                                }.apply()
                                                translationMode = finalMode
                                                showTranslationDialog = false
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = uiPrimaryColor,
                                                contentColor = uiBgColor
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                                        ) {
                                            Text("Lưu", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Surface(
                    color = uiBgColor.copy(alpha = 0.98f),
                    shadowElevation = 0.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Filled.Close, "Đóng", tint = uiTextColor)
                        }

                        // Chip dịch VBook gốc cạnh nút Close
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = uiTextColor.copy(alpha = 0.08f),
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .clickable { showTranslationDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .background(Color(0xFF3B5998), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.MenuBook,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                                Text(
                                    text = translationMode,
                                    color = uiTextColor,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = uiTextColor.copy(alpha = 0.7f),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                        
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showTocBottomSheet = true }
                                .padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = novel?.title ?: "Đang tải truyện...",
                                style = MaterialTheme.typography.labelSmall,
                                color = uiTextColor.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = displayTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = uiTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowDown,
                                    contentDescription = "Mục lục",
                                    tint = uiTextColor.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Xóa nút dịch cũ ở đây và thay thế bằng nút refresh
                        IconButton(onClick = { viewModel.loadChapter(extensionId, currentChapterUrl, isOffline = isOffline) }) {
                            Icon(Icons.Filled.Refresh, "Tải lại", tint = uiTextColor)
                        }
                        IconButton(onClick = {}) {
                            Icon(Icons.Filled.Bookmark, "Bookmark", tint = uiTextColor)
                        }
                        IconButton(onClick = { navController.navigate(Routes.search(extensionId, novel?.title ?: "")) }) {
                            Icon(Icons.Filled.Search, "Tìm kiếm", tint = uiTextColor)
                        }
                        Box {
                            IconButton(onClick = { showMenuDropdown = true }) {
                                Icon(Icons.Filled.MoreVert, "Menu", tint = uiTextColor)
                            }
                            DropdownMenu(
                                expanded = showMenuDropdown,
                                onDismissRequest = { showMenuDropdown = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Quản lý sửa từ") },
                                    onClick = { showMenuDropdown = false },
                                    trailingIcon = { Icon(Icons.Filled.ChevronRight, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Quản lý lọc rác") },
                                    onClick = { showMenuDropdown = false },
                                    trailingIcon = { Icon(Icons.Filled.ChevronRight, null) }
                                )
                            }
                        }
                    }
                }
            }

            // === Bottom Panel 2 tầng & FAB Night mode ===
            AnimatedVisibility(
                visible = showUI && !isTtsActive,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Box {
                    Surface(
                        color = uiBgColor.copy(alpha = 0.98f),
                        shadowElevation = 0.dp,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {

                            // Nhãn tiến độ chương & Nút Copy thực tế
                            Row(
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .padding(bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ScrollPercentText(
                                    scrollPercentProvider = { scrollPercentState.floatValue },
                                    color = uiTextColor.copy(alpha = 0.6f),
                                    suffix = "  •  "
                                )
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = "Sao chép chương",
                                    tint = uiTextColor.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                            val clip = android.content.ClipData.newPlainText("Chapter Content", displayContent ?: "")
                                            clipboard.setPrimaryClip(clip)
                                            android.widget.Toast.makeText(context, "Đã sao chép nội dung chương vào Clipboard", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                )
                            }

                            // Tầng 2: Các nút chức năng ở đáy (vBook chỉ có icon, không text)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = {
                                    showTocBottomSheet = true
                                    showUI = false
                                }) {
                                    Icon(Icons.Filled.Menu, "Mục lục", tint = uiTextColor)
                                }
                                IconButton(onClick = {
                                    android.widget.Toast.makeText(context, "Chế độ đọc: Cuộn dọc mặc định", android.widget.Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Filled.SwapVert, "Chế độ đọc", tint = uiTextColor)
                                }
                                IconButton(onClick = {
                                    showAudioPlayer = true
                                    val intent = Intent(context, com.nam.novelreader.service.TextToSpeechService::class.java).apply {
                                        action = com.nam.novelreader.service.TextToSpeechService.ACTION_START
                                        putExtra(com.nam.novelreader.service.TextToSpeechService.EXTRA_CHAPTER_URL, currentChapterUrl)
                                        putExtra(com.nam.novelreader.service.TextToSpeechService.EXTRA_NOVEL_URL, novelUrl)
                                        putExtra(com.nam.novelreader.service.TextToSpeechService.EXTRA_EXTENSION_ID, extensionId)
                                        putExtra(com.nam.novelreader.service.TextToSpeechService.EXTRA_TITLE, displayTitle)
                                        putExtra(com.nam.novelreader.service.TextToSpeechService.EXTRA_CONTENT, "")
                                    }
                                    context.startService(intent)
                                    showUI = false
                                }) {
                                    Icon(Icons.Filled.Headphones, "Nghe audio", tint = uiTextColor)
                                }
                                IconButton(onClick = {
                                    showSettingsSheet = true
                                    showUI = false
                                }) {
                                    Icon(Icons.Filled.Settings, "Cài đặt", tint = uiTextColor)
                                }
                            }
                        }
                    }

                    // Floating Night Mode Button (Nút mặt trăng)
                    FloatingActionButton(
                        onClick = {
                            val newTheme = if (themeIndex == 4) 0 else 4 // Chuyển đổi qua lại giữa Đêm(4) và Giấy vàng(0)
                            scope.launch { viewModel.readerPreferences.setThemeIndex(newTheme) }
                        },
                        containerColor = Color(0xFF8B7355),
                        contentColor = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 16.dp)
                            .offset(y = (-24).dp)
                            .size(48.dp),
                        shape = CircleShape
                    ) {
                        Icon(
                            imageVector = if (themeIndex == 4) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = "Chuyển chế độ ngày/đêm",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // === Quick Settings Dialog/BottomSheet ===
            if (showSettingsSheet) {
                ReaderSettingsDialog(
                    viewModel = viewModel,
                    readingMode = readingMode,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    paragraphSpacing = paragraphSpacing,
                    themeIndex = themeIndex,
                    fontFamily = fontFamily,
                    textJustify = textJustify,
                    textIndent = textIndent,
                    keepScreenOn = keepScreenOn,
                    marginLeft = marginLeft,
                    marginRight = marginRight,
                    marginTop = marginTop,
                    marginBottom = marginBottom,
                    brightness = brightness,
                    uiBgColor = uiBgColor,
                    uiTextColor = uiTextColor,
                    uiPrimaryColor = uiPrimaryColor,
                    isAutoScrolling = isAutoScrolling,
                    onAutoScrollChange = { isAutoScrolling = it },
                    autoScrollSpeed = autoScrollSpeed,
                    onAutoScrollSpeedChange = { 
                        autoScrollSpeed = it
                        prefs.edit().putInt("reader_auto_scroll_speed", it).apply()
                    },
                    charSpacing = charSpacing,
                    textIndentValue = textIndentValue,
                    textAlign = textAlign,
                    eyeComfort = eyeComfort,
                    doublePage = doublePage,
                    onDismiss = { showSettingsSheet = false }
                )
            }

            // === TOC Bottom Sheet ===
            AnimatedVisibility(
                visible = showTocBottomSheet,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                ReaderTocBottomSheet(
                    novel = novel,
                    chapters = chapters,
                    currentChapterUrl = currentChapterUrl,
                    themeIndex = themeIndex,
                    onChapterClick = { ch ->
                        webViewAlpha = 0.4f
                        currentChapterUrl = ch.url
                        showTocBottomSheet = false
                    },
                    extensionId = extensionId,
                    novelUrl = novelUrl,
                    onRefreshToc = {
                        viewModel.loadToc(extensionId, novelUrl, forceRefresh = true, isOffline = isOffline)
                    },
                    onDismiss = { showTocBottomSheet = false },
                    isOffline = isOffline
                )
            }

            // === Audio Player Screen ===
            AnimatedVisibility(
                visible = showAudioPlayer,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                AudioPlayerScreen(
                    novel = novel,
                    chapterTitle = displayTitle,
                    isPlaying = isTtsPlaying,
                    currentSentenceIndex = ttsSentenceIndex,
                    totalSentences = ttsTotalSentences,
                    timerRemainingSeconds = ttsTimerRemaining,
                    speed = ttsSpeed,
                    themeIndex = themeIndex,
                    bgMusic = ttsBgMusic,
                    elapsedSeconds = ttsElapsedSeconds,
                    totalSeconds = ttsTotalSeconds,
                    onPlayPause = {
                        val intent = Intent(context, com.nam.novelreader.service.TextToSpeechService::class.java).apply {
                            action = com.nam.novelreader.service.TextToSpeechService.ACTION_PLAY_PAUSE
                        }
                        context.startService(intent)
                    },
                    onRewind = {
                        val intent = Intent(context, com.nam.novelreader.service.TextToSpeechService::class.java).apply {
                            action = com.nam.novelreader.service.TextToSpeechService.ACTION_REWIND_SENTENCE
                        }
                        context.startService(intent)
                    },
                    onForward = {
                        val intent = Intent(context, com.nam.novelreader.service.TextToSpeechService::class.java).apply {
                            action = com.nam.novelreader.service.TextToSpeechService.ACTION_FORWARD_SENTENCE
                        }
                        context.startService(intent)
                    },
                    onPrevChapter = {
                        val intent = Intent(context, com.nam.novelreader.service.TextToSpeechService::class.java).apply {
                            action = com.nam.novelreader.service.TextToSpeechService.ACTION_PREV_CHAPTER
                        }
                        context.startService(intent)
                    },
                    onNextChapter = {
                        val intent = Intent(context, com.nam.novelreader.service.TextToSpeechService::class.java).apply {
                            action = com.nam.novelreader.service.TextToSpeechService.ACTION_NEXT_CHAPTER
                        }
                        context.startService(intent)
                    },
                    onSeekToSentence = { sentenceIndex ->
                        val intent = Intent(context, com.nam.novelreader.service.TextToSpeechService::class.java).apply {
                            action = com.nam.novelreader.service.TextToSpeechService.ACTION_SEEK_TO_SENTENCE
                            putExtra(com.nam.novelreader.service.TextToSpeechService.EXTRA_SENTENCE_INDEX, sentenceIndex)
                        }
                        context.startService(intent)
                    },
                    onSetTimer = { duration ->
                        val intent = Intent(context, com.nam.novelreader.service.TextToSpeechService::class.java).apply {
                            action = com.nam.novelreader.service.TextToSpeechService.ACTION_SET_TIMER
                            putExtra(com.nam.novelreader.service.TextToSpeechService.EXTRA_TIMER_DURATION, duration)
                        }
                        context.startService(intent)
                    },
                    onSetSpeed = { rate ->
                        val intent = Intent(context, com.nam.novelreader.service.TextToSpeechService::class.java).apply {
                            action = com.nam.novelreader.service.TextToSpeechService.ACTION_SET_SPEED
                            putExtra(com.nam.novelreader.service.TextToSpeechService.EXTRA_SPEED, rate)
                        }
                        context.startService(intent)
                    },
                    onSetBgMusic = { music ->
                        val intent = Intent(context, com.nam.novelreader.service.TextToSpeechService::class.java).apply {
                            action = com.nam.novelreader.service.TextToSpeechService.ACTION_SET_BG_MUSIC
                            putExtra(com.nam.novelreader.service.TextToSpeechService.EXTRA_BG_MUSIC, music)
                        }
                        context.startService(intent)
                    },
                    onDismiss = { showAudioPlayer = false },
                    onShowToc = {
                        showAudioPlayer = false
                        showTocBottomSheet = true
                    },
                    onNavigateToExtensionSettings = { extId ->
                        navController.navigate(Routes.extensionSettings(extId))
                    }
                )
            }

            if ((isLoading || isTranslating || webViewAlpha < 0.9f) && error == null && !showAudioPlayer) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(uiBgColor)
                        .clickable(enabled = true, onClick = {}),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = uiPrimaryColor,
                            strokeWidth = 3.5.dp,
                            modifier = Modifier.size(42.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Đang tải chương...",
                            color = uiTextColor.copy(alpha = 0.85f),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (isTtsActive && !showAudioPlayer) {
                FloatingAudioBubble(
                    novelCover = novel?.cover,
                    isPlaying = isTtsPlaying,
                    themeIndex = themeIndex,
                    onClick = {
                        showAudioPlayer = true
                    }
                )
            }
        }
    }
    }
}
}
}

/**
 * Bảng BottomSheet Quick Settings clone VBook:
 * - Chọn theme màu sắc (Vintage, Sáng, Bảo vệ mắt, Đêm, Xám, Gỗ)
 * - Tăng/giảm cỡ chữ
 * - Thay đổi line space, paragraph space
 * - Checkbox thụt đầu dòng, căn đều 2 bên, keep screen on
 * - Độ sáng màn hình (tự động/thủ công)
 * - Cài đặt lề trang riêng biệt
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsDialog(
    viewModel: ReaderViewModel,
    readingMode: Int,
    fontSize: Int,
    lineHeight: Float,
    paragraphSpacing: Float,
    themeIndex: Int,
    fontFamily: String,
    textJustify: Boolean,
    textIndent: Boolean,
    keepScreenOn: Boolean,
    marginLeft: Int,
    marginRight: Int,
    marginTop: Int,
    marginBottom: Int,
    brightness: Float,
    uiBgColor: Color,
    uiTextColor: Color,
    uiPrimaryColor: Color,
    isAutoScrolling: Boolean = false,
    onAutoScrollChange: (Boolean) -> Unit = {},
    autoScrollSpeed: Int = 2,
    onAutoScrollSpeedChange: (Int) -> Unit = {},
    charSpacing: Float,
    textIndentValue: Float,
    textAlign: Int,
    eyeComfort: Boolean,
    doublePage: Boolean,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var showFontPicker by remember { mutableStateOf(false) }

    // Helper data class cho lề
    val margins = listOf(
        Triple("Lề trái", marginLeft, 0f..60f),
        Triple("Lề phải", marginRight, 0f..60f),
        Triple("Lề trên", marginTop, 0f..100f),
        Triple("Lề dưới", marginBottom, 0f..100f)
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = uiBgColor,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Cấu hình đọc",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = uiTextColor,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            // Hàng 1: Brightness Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isAuto = brightness < 0f
                IconButton(
                    onClick = {
                        scope.launch {
                            if (isAuto) {
                                viewModel.readerPreferences.setBrightness(0.5f)
                            } else {
                                viewModel.readerPreferences.setBrightness(-1f)
                            }
                        }
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = if (isAuto) uiPrimaryColor else uiTextColor.copy(alpha = 0.1f),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Auto Brightness",
                        tint = if (isAuto) uiBgColor else uiTextColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                
                Box(modifier = Modifier.weight(1f)) {
                    val sliderValue = if (brightness < 0f) 0.5f else brightness
                    Slider(
                        value = sliderValue,
                        onValueChange = {
                            scope.launch { viewModel.readerPreferences.setBrightness(it) }
                        },
                        valueRange = 0.05f..1.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = uiPrimaryColor,
                            activeTrackColor = uiPrimaryColor,
                            inactiveTrackColor = uiTextColor.copy(alpha = 0.2f)
                        ),
                        enabled = !isAuto
                    )
                    Text(
                        text = if (isAuto) "Tự động" else String.format("%.0f%%", sliderValue * 100),
                        color = uiTextColor.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.align(Alignment.TopCenter).offset(y = (-16).dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                
                IconButton(
                    onClick = {
                        scope.launch { viewModel.readerPreferences.setEyeComfort(!eyeComfort) }
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = if (eyeComfort) uiPrimaryColor else uiTextColor.copy(alpha = 0.1f),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = "Eye Comfort",
                        tint = if (eyeComfort) uiBgColor else uiTextColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Hàng 2: Màu nền (Background Themes)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = "Theme Palette",
                    tint = uiTextColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val allThemes = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17)
                    items(allThemes) { index ->
                        val isSelected = themeIndex == index
                        // Màu dot preview khớp reader/theme.json của VBook
                        val themeColor = when (index) {
                            0  -> Color(0xFFD3C3A3)  // bg1: Kraft
                            1  -> Color(0xFFFFFFFF)  // bg6: Trắng
                            2  -> Color(0xFFF1F7ED)  // bg4: Xanh lá
                            3  -> Color(0xFFA2C0E5)  // bg2: Xanh dương
                            4  -> Color(0xFF000000)  // bg_dark: Đen thuần
                            5  -> Color(0xFFF3C9D7)  // bg3: Hồng
                            6  -> Color(0xFFECE1CA)  // bg5: Vàng giấy
                            7  -> Color(0xFFC2E0CD)  // bg7: Lục nhạt
                            8  -> Color(0xFF393030)  // bg8: Nâu tối
                            9  -> Color(0xFF333333)  // bg9: Xám tối
                            10 -> Color(0xFF051C2C)  // bg10: Navy
                            11 -> Color(0xFF152B06)  // bg11: Xanh rừng
                            12 -> Color(0xFF151C1F)  // bg12: Xanh đen
                            13 -> Color(0xFF1A1A1A)  // bg13: Đen 1
                            14 -> Color(0xFF0D0D0D)  // bg14: Đen 2
                            15 -> Color(0xFF001622)  // bg15: Xanh đậm
                            16 -> Color(0xFF171F27)  // bg16: Slate
                            17 -> Color(0xFF251C05)  // bg17: Đồng tối
                            else -> Color.Transparent
                        }
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .border(
                                    width = if (isSelected) 1.5.dp else 0.dp,
                                    color = if (isSelected) uiPrimaryColor else Color.Transparent,
                                    shape = CircleShape
                                )
                                .padding(if (isSelected) 3.dp else 0.dp)
                                .clip(CircleShape)
                                .background(themeColor)
                                .border(
                                    width = 1.dp,
                                    color = uiTextColor.copy(alpha = 0.15f),
                                    shape = CircleShape
                                )
                                .clickable {
                                    scope.launch { viewModel.readerPreferences.setThemeIndex(index) }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            val imgRes = when (index) {
                                0 -> "bg7.jpg"
                                2 -> "bg6.png"
                                3 -> "bg4.jpg"
                                5 -> "bg5.jpg"
                                else -> null
                            }
                            if (imgRes != null) {
                                AsyncImage(
                                    model = "file:///android_asset/img/$imgRes",
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = if (index == 1 || index == 7 || index == 6) uiPrimaryColor else Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Hàng 3: Font Size & Font Family
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "tT",
                    color = uiTextColor,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(36.dp)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(uiTextColor.copy(alpha = 0.08f), shape = RoundedCornerShape(20.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (fontSize > 12) scope.launch { viewModel.readerPreferences.setFontSize(fontSize - 1) }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("A-", color = uiTextColor, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = String.format("%.1f", fontSize.toFloat()),
                        color = uiTextColor,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (fontSize < 40) scope.launch { viewModel.readerPreferences.setFontSize(fontSize + 1) }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("A+", color = uiTextColor, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                
                val fontDisplayName = when (fontFamily) {
                    "serif" -> "Serif"
                    "sans-serif" -> "Sans-serif"
                    "'Noto Serif', Georgia, serif" -> "Noto Serif"
                    "'Nunito', sans-serif" -> "Nunito"
                    "'Literata', serif" -> "Literata"
                    "Georgia, serif" -> "Bookerly"
                    "'Courier New', monospace" -> "Architecture"
                    "'Comic Sans MS', cursive, sans-serif" -> "Comic Sans"
                    "'Constantia', Georgia, serif" -> "Constantia"
                    else -> fontFamily.replace("'", "")
                }
                OutlinedButton(
                    onClick = { showFontPicker = true },
                    modifier = Modifier.height(36.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = uiTextColor),
                    border = BorderStroke(1.dp, uiTextColor.copy(alpha = 0.3f)),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                ) {
                    Text(text = fontDisplayName, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = uiTextColor.copy(alpha = 0.6f)
                    )
                }
            }

            // Hàng 4: Reading Mode (Hiệu ứng lật trang)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Reading Mode Gesture",
                    tint = uiTextColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val modes = listOf("Cuộn dọc", "Vuốt ngang", "Trượt", "Mô phỏng", "Đơn giản")
                    itemsIndexed(modes) { index, mode ->
                        val isSelected = readingMode == index
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isSelected) uiPrimaryColor 
                                    else uiTextColor.copy(alpha = 0.03f)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) uiPrimaryColor else uiTextColor.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable {
                                    scope.launch { viewModel.readerPreferences.setReadingMode(index) }
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .background(uiBgColor, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = mode,
                                    color = if (isSelected) uiBgColor else uiTextColor,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = uiTextColor.copy(alpha = 0.1f))

            // Hàng 5: Thụt lề
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Thụt lề",
                    color = uiTextColor,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(90.dp)
                )
                Text(
                    text = String.format("%.1f", textIndentValue),
                    color = uiTextColor.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(50.dp),
                    textAlign = TextAlign.End
                )
                Spacer(modifier = Modifier.width(12.dp))
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Slider(
                        value = textIndentValue,
                        onValueChange = {
                            scope.launch { viewModel.readerPreferences.setTextIndentValue(it) }
                        },
                        valueRange = 0.0f..3.0f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = uiPrimaryColor,
                            activeTrackColor = uiPrimaryColor,
                            inactiveTrackColor = uiTextColor.copy(alpha = 0.2f)
                        )
                    )
                    IconButton(
                        onClick = {
                            scope.launch { viewModel.readerPreferences.setTextIndentValue((textIndentValue - 0.1f).coerceAtLeast(0.0f)) }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("-", color = uiTextColor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    IconButton(
                        onClick = {
                            scope.launch { viewModel.readerPreferences.setTextIndentValue((textIndentValue + 0.1f).coerceAtMost(3.0f)) }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("+", color = uiTextColor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Hàng 6: Căn lề
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Căn lề",
                        color = uiTextColor,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(90.dp)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Row(
                        modifier = Modifier
                            .background(uiTextColor.copy(alpha = 0.08f), shape = RoundedCornerShape(8.dp))
                            .padding(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val alignModes = listOf(0, 1, 2, 3)
                        alignModes.forEach { mode ->
                            val isSelected = textAlign == mode
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        color = if (isSelected) uiPrimaryColor else Color.Transparent,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable {
                                        scope.launch { viewModel.readerPreferences.setTextAlign(mode) }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                val tint = if (isSelected) uiBgColor else uiTextColor
                                when (mode) {
                                    0 -> AlignLeftIcon(tint = tint)
                                    1 -> AlignCenterIcon(tint = tint)
                                    2 -> AlignRightIcon(tint = tint)
                                    3 -> AlignJustifyIcon(tint = tint)
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            scope.launch { viewModel.readerPreferences.setTextAlign(3) }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Đặt lại", color = uiTextColor.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset Align",
                            tint = uiTextColor.copy(alpha = 0.6f),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = uiTextColor.copy(alpha = 0.1f))

            // Hàng 7: Cách chữ
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cách chữ",
                    color = uiTextColor,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(90.dp)
                )
                Text(
                    text = String.format("%.0f%%", charSpacing * 100),
                    color = uiTextColor.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(50.dp),
                    textAlign = TextAlign.End
                )
                Spacer(modifier = Modifier.width(12.dp))
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Slider(
                        value = charSpacing,
                        onValueChange = {
                            scope.launch { viewModel.readerPreferences.setCharSpacing(it) }
                        },
                        valueRange = -0.1f..0.5f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = uiPrimaryColor,
                            activeTrackColor = uiPrimaryColor,
                            inactiveTrackColor = uiTextColor.copy(alpha = 0.2f)
                        )
                    )
                    IconButton(
                        onClick = {
                            scope.launch { viewModel.readerPreferences.setCharSpacing((charSpacing - 0.01f).coerceAtLeast(-0.1f)) }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("-", color = uiTextColor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    IconButton(
                        onClick = {
                            scope.launch { viewModel.readerPreferences.setCharSpacing((charSpacing + 0.01f).coerceAtMost(0.5f)) }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("+", color = uiTextColor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Hàng 8: Cách dòng
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cách dòng",
                    color = uiTextColor,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(90.dp)
                )
                Text(
                    text = String.format("%.0f%%", lineHeight * 100),
                    color = uiTextColor.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(50.dp),
                    textAlign = TextAlign.End
                )
                Spacer(modifier = Modifier.width(12.dp))
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Slider(
                        value = lineHeight,
                        onValueChange = {
                            scope.launch { viewModel.readerPreferences.setLineHeight(it) }
                        },
                        valueRange = 1.0f..2.5f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = uiPrimaryColor,
                            activeTrackColor = uiPrimaryColor,
                            inactiveTrackColor = uiTextColor.copy(alpha = 0.2f)
                        )
                    )
                    IconButton(
                        onClick = {
                            scope.launch { viewModel.readerPreferences.setLineHeight((lineHeight - 0.01f).coerceAtLeast(1.0f)) }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("-", color = uiTextColor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    IconButton(
                        onClick = {
                            scope.launch { viewModel.readerPreferences.setLineHeight((lineHeight + 0.01f).coerceAtMost(2.5f)) }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("+", color = uiTextColor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Hàng 9: Cách đoạn
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cách đoạn",
                    color = uiTextColor,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(90.dp)
                )
                Text(
                    text = String.format("%.0f%%", paragraphSpacing * 100),
                    color = uiTextColor.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(50.dp),
                    textAlign = TextAlign.End
                )
                Spacer(modifier = Modifier.width(12.dp))
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Slider(
                        value = paragraphSpacing,
                        onValueChange = {
                            scope.launch { viewModel.readerPreferences.setParagraphSpacing(it) }
                        },
                        valueRange = 0.0f..3.0f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = uiPrimaryColor,
                            activeTrackColor = uiPrimaryColor,
                            inactiveTrackColor = uiTextColor.copy(alpha = 0.2f)
                        )
                    )
                    IconButton(
                        onClick = {
                            scope.launch { viewModel.readerPreferences.setParagraphSpacing((paragraphSpacing - 0.01f).coerceAtLeast(0.0f)) }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("-", color = uiTextColor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    IconButton(
                        onClick = {
                            scope.launch { viewModel.readerPreferences.setParagraphSpacing((paragraphSpacing + 0.01f).coerceAtMost(3.0f)) }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("+", color = uiTextColor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
            // Reset Spacing
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = {
                        scope.launch {
                            viewModel.readerPreferences.setCharSpacing(0.0f)
                            viewModel.readerPreferences.setLineHeight(1.6f)
                            viewModel.readerPreferences.setParagraphSpacing(1.0f)
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("Đặt lại", color = uiTextColor.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset Spacing",
                        tint = uiTextColor.copy(alpha = 0.6f),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            HorizontalDivider(color = uiTextColor.copy(alpha = 0.1f))

            // Hàng 10: 4 Lề trang độc lập (Lề trái, Lề phải, Lề trên, Lề dưới)
            margins.forEach { (label, value, range) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        color = uiTextColor,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(90.dp)
                    )
                    Text(
                        text = "$value",
                        color = uiTextColor.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(50.dp),
                        textAlign = TextAlign.End
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Slider(
                            value = value.toFloat(),
                            onValueChange = { newVal ->
                                scope.launch {
                                    when (label) {
                                        "Lề trái" -> viewModel.readerPreferences.setMargins(newVal.toInt(), marginRight, marginTop, marginBottom)
                                        "Lề phải" -> viewModel.readerPreferences.setMargins(marginLeft, newVal.toInt(), marginTop, marginBottom)
                                        "Lề trên" -> viewModel.readerPreferences.setMargins(marginLeft, marginRight, newVal.toInt(), marginBottom)
                                        "Lề dưới" -> viewModel.readerPreferences.setMargins(marginLeft, marginRight, marginTop, newVal.toInt())
                                    }
                                }
                            },
                            valueRange = range,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = uiPrimaryColor,
                                activeTrackColor = uiPrimaryColor,
                                inactiveTrackColor = uiTextColor.copy(alpha = 0.2f)
                            )
                        )
                        IconButton(
                            onClick = {
                                if (value > range.start.toInt()) {
                                    val newVal = value - 1
                                    scope.launch {
                                        when (label) {
                                            "Lề trái" -> viewModel.readerPreferences.setMargins(newVal, marginRight, marginTop, marginBottom)
                                            "Lề phải" -> viewModel.readerPreferences.setMargins(marginLeft, newVal, marginTop, marginBottom)
                                            "Lề trên" -> viewModel.readerPreferences.setMargins(marginLeft, marginRight, newVal, marginBottom)
                                            "Lề dưới" -> viewModel.readerPreferences.setMargins(marginLeft, marginRight, marginTop, newVal)
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Text("-", color = uiTextColor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        IconButton(
                            onClick = {
                                if (value < range.endInclusive.toInt()) {
                                    val newVal = value + 1
                                    scope.launch {
                                        when (label) {
                                            "Lề trái" -> viewModel.readerPreferences.setMargins(newVal, marginRight, marginTop, marginBottom)
                                            "Lề phải" -> viewModel.readerPreferences.setMargins(marginLeft, newVal, marginTop, marginBottom)
                                            "Lề trên" -> viewModel.readerPreferences.setMargins(marginLeft, marginRight, newVal, marginBottom)
                                            "Lề dưới" -> viewModel.readerPreferences.setMargins(marginLeft, marginRight, marginTop, newVal)
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Text("+", color = uiTextColor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            // Reset Margins
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = {
                        scope.launch { viewModel.readerPreferences.setMargins(20, 20, 24, 24) }
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("Đặt lại", color = uiTextColor.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset Margins",
                        tint = uiTextColor.copy(alpha = 0.6f),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            HorizontalDivider(color = uiTextColor.copy(alpha = 0.1f))

            // Hàng 11: Chế độ trang đôi
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        scope.launch { viewModel.readerPreferences.setDoublePage(!doublePage) }
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Chế độ trang đôi", color = uiTextColor, style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (doublePage) "Bật" else "Tắt",
                        color = uiTextColor.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = uiTextColor.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Hàng 12: Giữ màn hình luôn sáng
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { scope.launch { viewModel.readerPreferences.setKeepScreenOn(!keepScreenOn) } }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Giữ màn hình luôn sáng", color = uiTextColor, style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = keepScreenOn,
                    onCheckedChange = { scope.launch { viewModel.readerPreferences.setKeepScreenOn(it) } },
                    colors = SwitchDefaults.colors(checkedThumbColor = uiPrimaryColor)
                )
            }

            // Tự động cuộn (Auto-scroll) - Chỉ hiển thị khi đọc cuộn dọc
            if (readingMode == 0) {
                HorizontalDivider(color = uiTextColor.copy(alpha = 0.1f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Tự động cuộn", color = uiTextColor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text("Chạm màn hình để tạm dừng cuộn", color = uiTextColor.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = isAutoScrolling,
                        onCheckedChange = onAutoScrollChange,
                        colors = SwitchDefaults.colors(checkedThumbColor = uiPrimaryColor)
                    )
                }

                if (isAutoScrolling) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tốc độ: $autoScrollSpeed",
                            color = uiTextColor,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.width(90.dp)
                        )
                        Slider(
                            value = autoScrollSpeed.toFloat(),
                            onValueChange = { onAutoScrollSpeedChange(it.toInt()) },
                            valueRange = 1f..15f,
                            steps = 14,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(thumbColor = uiPrimaryColor, activeTrackColor = uiPrimaryColor)
                        )
                    }
                }
            }
        }
    }

    // Font Picker BottomSheet
    if (showFontPicker) {
        ModalBottomSheet(
            onDismissRequest = { showFontPicker = false },
            containerColor = uiBgColor,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "Kiểu chữ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = uiTextColor,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                val fonts = listOf(
                    "Inter" to "system-ui, -apple-system, sans-serif",
                    "Nunito" to "'Nunito', sans-serif",
                    "Literata" to "'Literata', serif",
                    "Architecture" to "'Courier New', monospace",
                    "Bookerly" to "Georgia, serif",
                    "Comic Sans" to "'Comic Sans MS', cursive, sans-serif",
                    "Constantia" to "'Constantia', Georgia, serif",
                    "GMV_Tenez" to "serif"
                )
                
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(fonts) { (name, family) ->
                        val isSelected = fontFamily == family
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        viewModel.readerPreferences.setFontFamily(family)
                                        showFontPicker = false
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = uiTextColor
                                )
                                Text(
                                    text = "Aa Bb Cc Dd Ee Ff Gg Hh Ii Jj 0123456789 !?",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = uiTextColor.copy(alpha = 0.6f)
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = uiPrimaryColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                if (name in listOf("Architecture", "Bookerly", "Comic Sans", "Constantia")) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "Download Font",
                                        tint = uiTextColor.copy(alpha = 0.5f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Build HTML page cho reader — VBook style với các cấu hình preferences tùy biến.
 */
private fun formatHtmlContent(rawContent: String): String {
    if (rawContent.isBlank()) return ""
    if (rawContent.contains("<p>", ignoreCase = true) || rawContent.contains("<p ", ignoreCase = true)) {
        return rawContent
    }
    val lines = if (rawContent.contains("<br", ignoreCase = true)) {
        rawContent.split(Regex("(?i)<br\\s*/?>"))
    } else {
        rawContent.split("\n")
    }
    return lines.map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("") { "<p>$it</p>" }
}

private fun buildReaderHtml(
    context: Context,
    content: String,
    title: String,
    chapterNum: String = "",
    chapterUrl: String = "",
    fontSize: Int,
    lineHeight: Float,
    paragraphSpacing: Float,
    themeIndex: Int,
    fontFamily: String,
    textAlign: Int,
    textIndentValue: Float,
    charSpacing: Float,
    marginLeft: Int,
    marginRight: Int,
    marginTop: Int,
    marginBottom: Int,
    readingMode: Int,
    doublePage: Boolean
): String {
    val formattedContent = formatHtmlContent(content)
    // 18 themes khớp VBook reader/theme.json
    val (textColor, bgColor) = when (themeIndex) {
        0  -> Pair("#3A3129", "#D3C3A3") // bg1: Kraft
        1  -> Pair("#3A342B", "#FFFFFF") // bg6: Trắng trơn
        2  -> Pair("#1B310E", "#F1F7ED") // bg4: Xanh lá nhạt
        3  -> Pair("#1B310E", "#A2C0E5") // bg2: Xanh dương
        4  -> Pair("#CCCCCC", "#000000") // bg_dark: Đêm đen
        5  -> Pair("#1B310E", "#F3C9D7") // bg3: Hồng nhạt
        6  -> Pair("#645032", "#ECE1CA") // bg5: Vàng giấy
        7  -> Pair("#334B39", "#C2E0CD") // bg7: Lục nhạt
        // Dark themes (bg8-bg17)
        8  -> Pair("#95938F", "#393030") // bg8: Nâu tối
        9  -> Pair("#CCE8CF", "#333333") // bg9: Xám tối xanh
        10 -> Pair("#637079", "#051C2C") // bg10: Navy đêm
        11 -> Pair("#607057", "#152B06") // bg11: Xanh rừng tối
        12 -> Pair("#4D5052", "#151C1F") // bg12: Xanh đen
        13 -> Pair("#5F5F5F", "#000000") // bg13: Đen 1
        14 -> Pair("#494949", "#000000") // bg14: Đen 2
        15 -> Pair("#204353", "#001622") // bg15: Xanh đậm
        16 -> Pair("#445053", "#171F27") // bg16: Slate tối
        17 -> Pair("#574F3C", "#251C05") // bg17: Đồng tối
        else -> Pair("#3A3129", "#D3C3A3")
    }

    // CSS gradient texture thay thế ảnh nền — không bao giờ có vết hằn
    val bgTextureCss = when (themeIndex) {
        0 -> "background: linear-gradient(135deg, #D3C3A3 0%, #CBBB99 25%, #D5C5A5 50%, #CDBD9D 75%, #D3C3A3 100%);"
        2 -> "background: linear-gradient(135deg, #F1F7ED 0%, #E8F0E4 25%, #F3F9EF 50%, #EAF2E6 75%, #F1F7ED 100%);"
        3 -> "background: linear-gradient(135deg, #A2C0E5 0%, #9AB8DD 25%, #A6C4E9 50%, #9EBCE1 75%, #A2C0E5 100%);"
        5 -> "background: linear-gradient(135deg, #F3C9D7 0%, #EBBFCD 25%, #F5CDD9 50%, #EDC3CF 75%, #F3C9D7 100%);"
        else -> "" // Themes 1,4,6,7 đã là màu trơn
    }

    val textAlignStyle = when (textAlign) {
        0 -> "left"
        1 -> "center"
        2 -> "right"
        else -> "justify"
    }

    return """
    <!DOCTYPE html>
    <html lang="vi">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
        <style>
            @import url('https://fonts.googleapis.com/css2?family=Inter:wght@300;400;600;700&family=Nunito:wght@300;400;600;700&family=Literata:opsz,wght@7..72,300;7..72,400;7..72,600;7..72,700&display=swap');
            * { margin: 0; padding: 0; box-sizing: border-box; }
            body {
                font-family: $fontFamily, system-ui, -apple-system, sans-serif;
                font-size: ${fontSize}px;
                line-height: $lineHeight;
                color: $textColor;
                background-color: $bgColor;
                $bgTextureCss
                -webkit-text-size-adjust: 100%;
            }
            ${if (readingMode == 0) """
            body {
                padding: ${marginTop}px ${marginRight}px ${marginBottom}px ${marginLeft}px;
                word-wrap: break-word;
            }
            #reader-container {
                width: 100%;
            }
            .chapter-block {
                margin-bottom: 40px;
            }
            .chapter-separator {
                border: 0;
                height: 1px;
                background-color: rgba(128, 128, 128, 0.15);
                margin: 40px 0;
            }
            """ else """
            html {
                height: 100%;
                overflow-x: auto;
                overflow-y: hidden;
            }
            body {
                height: 100vh;
                width: 100vw;
                overflow: visible;
            }
            #reader-container {
                position: absolute;
                left: ${marginLeft}px;
                top: ${marginTop}px;
                width: calc(100vw - ${marginLeft + marginRight}px);
                height: calc(100vh - ${marginTop + marginBottom}px);
                column-width: calc(${if (doublePage) "(100vw - " + (marginLeft + marginRight + 40) + "px) / 2" else "100vw - " + (marginLeft + marginRight) + "px"});
                column-gap: calc(${if (doublePage) "40px" else (marginLeft + marginRight).toString() + "px"});
                column-fill: auto;
                overflow: visible;
            }
            .chapter-block {
                display: inline;
            }
            .chapter-separator {
                column-break-before: always;
                break-before: column;
                margin: 0;
                border: 0;
                height: 0;
            }
            """}
            img {
                max-width: 100%;
                height: auto;
                display: block;
                margin: 16px auto;
                border-radius: 8px;
            }
            .chapter-label {
                text-align: left;
                color: $textColor;
                opacity: 0.6;
                font-size: 0.7em;
                margin-bottom: 12px;
            }
            h1.chapter-title {
                text-align: center;
                color: $textColor;
                font-size: 1.35em;
                font-weight: bold;
                margin: 24px 0 36px 0;
                letter-spacing: 1px;
            }
            hr:not(.chapter-separator) {
                border: 0;
                height: 0;
                margin: 20px 0;
            }
            p, div {
                text-align: $textAlignStyle !important;
                text-indent: ${textIndentValue}em !important;
                letter-spacing: ${charSpacing}em !important;
                margin-top: 0 !important;
                margin-bottom: ${paragraphSpacing}em !important;
                word-wrap: break-word;
            }
            .nav-buttons {
                display: flex;
                justify-content: space-between;
                margin-top: 48px;
                padding-top: 24px;
                border-top: 1px solid rgba(0,0,0,0.1);
            }
            .nav-btn {
                padding: 12px 24px;
                border: 1px solid rgba(0,0,0,0.2);
                border-radius: 20px;
                background: transparent;
                color: $textColor;
                font-size: 1em;
                font-family: inherit;
                text-decoration: none;
                display: block;
                text-align: center;
                flex: 1;
                margin: 0 8px;
            }
            .footer {
                text-align: center;
                color: $textColor;
                opacity: 0.6;
                font-size: 0.7em;
                margin-top: 24px;
                padding-bottom: 24px;
            }
        </style>
        <script>
            var readingMode = $readingMode;
            var behavior = (readingMode === 4) ? 'auto' : 'smooth';
 
            function scrollNext() {
                if (readingMode === 0) return;
                var pageW = window.innerWidth;
                var currentScroll = window.pageXOffset || document.documentElement.scrollLeft;
                var currentPage = Math.round(currentScroll / pageW);
                var maxScroll = document.documentElement.scrollWidth - pageW;
                if (currentScroll >= maxScroll - 5) {
                    window.location.href = "vbook://next";
                } else {
                    var targetScroll = (currentPage + 1) * pageW;
                    window.scrollTo({ left: targetScroll, behavior: behavior });
                }
            }
 
            function scrollPrev() {
                if (readingMode === 0) return;
                var pageW = window.innerWidth;
                var currentScroll = window.pageXOffset || document.documentElement.scrollLeft;
                var currentPage = Math.round(currentScroll / pageW);
                if (currentScroll <= 5) {
                    window.location.href = "vbook://prev";
                } else {
                    var targetScroll = (currentPage - 1) * pageW;
                    window.scrollTo({ left: targetScroll, behavior: behavior });
                }
            }
 
            var isLoadingNext = false;
            if (readingMode === 0) {
                window.addEventListener('scroll', function() {
                    if (isLoadingNext) return;
                    var threshold = 1200;
                    var scrollHeight = document.documentElement.scrollHeight;
                    var clientHeight = window.innerHeight;
                    var scrollTop = window.pageYOffset || document.documentElement.scrollTop;
                    if (scrollHeight - clientHeight - scrollTop < threshold) {
                        isLoadingNext = true;
                        window.location.href = "vbook://near-end";
                    }
                });
            }
 
            var observer;
            window.addEventListener('DOMContentLoaded', function() {
                if (typeof IntersectionObserver !== 'undefined') {
                    var options = {
                        root: null,
                        rootMargin: '-20% 0px -60% 0px',
                        threshold: 0
                    };
                    observer = new IntersectionObserver(function(entries) {
                        entries.forEach(function(entry) {
                            if (entry.isIntersecting) {
                                var url = entry.target.getAttribute('data-url');
                                if (url) {
                                    window.location.href = "vbook://current-chapter?url=" + encodeURIComponent(url);
                                }
                            }
                        });
                    }, options);
                    
                    var firstBlock = document.querySelector('.chapter-block');
                    if (firstBlock) observer.observe(firstBlock);
                }
            });
 
            function appendChapter(url, title, content) {
                var container = document.getElementById('reader-container') || document.body;
                var block = document.createElement('div');
                block.className = 'chapter-block';
                block.setAttribute('data-url', url);
                block.innerHTML = '<hr class="chapter-separator" /><h1 class="chapter-title">' + title + '</h1><div class="chapter-content">' + content + '</div>';
                container.appendChild(block);
                if (observer) {
                    observer.observe(block);
                }
                isLoadingNext = false;
            }
 
            function resetLoadFlag() {
                isLoadingNext = false;
            }
 
            // Auto-scroll JS
            var autoScrollActive = false;
            var autoScrollSpeed = 1;
            function stepScroll() {
                if (!autoScrollActive) return;
                window.scrollBy(0, autoScrollSpeed);
                requestAnimationFrame(stepScroll);
            }
            function startAutoScroll(speed) {
                autoScrollSpeed = speed * 0.4;
                if (!autoScrollActive) {
                    autoScrollActive = true;
                    requestAnimationFrame(stepScroll);
                }
            }
            function stopAutoScroll() {
                autoScrollActive = false;
            }
        </script>
    </head>
    <body>
        <div id="reader-container">
            <div class="chapter-block" data-url="$chapterUrl">
                <div class="chapter-label">$chapterNum</div>
                <h1 class="chapter-title">$title</h1>
                <div class="chapter-content">
                    $formattedContent
                </div>
            </div>
        </div>
    </body>
    </html>
    """.trimIndent()
}

// Helper convert asset to Base64
private fun getAssetAsBase64(context: Context, fileName: String): String {
    return try {
        context.assets.open("img/$fileName").use { inputStream ->
            val bytes = inputStream.readBytes()
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        }
    } catch (e: Exception) {
        ""
    }
}

// Icons vẽ tay bằng Canvas cho Căn lề
@Composable
fun AlignLeftIcon(tint: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()
        drawLine(tint, start = androidx.compose.ui.geometry.Offset(0f, h * 0.2f), end = androidx.compose.ui.geometry.Offset(w, h * 0.2f), strokeWidth = stroke)
        drawLine(tint, start = androidx.compose.ui.geometry.Offset(0f, h * 0.4f), end = androidx.compose.ui.geometry.Offset(w * 0.7f, h * 0.4f), strokeWidth = stroke)
        drawLine(tint, start = androidx.compose.ui.geometry.Offset(0f, h * 0.6f), end = androidx.compose.ui.geometry.Offset(w, h * 0.6f), strokeWidth = stroke)
        drawLine(tint, start = androidx.compose.ui.geometry.Offset(0f, h * 0.8f), end = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.8f), strokeWidth = stroke)
    }
}

@Composable
fun AlignCenterIcon(tint: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()
        drawLine(tint, start = androidx.compose.ui.geometry.Offset(0f, h * 0.2f), end = androidx.compose.ui.geometry.Offset(w, h * 0.2f), strokeWidth = stroke)
        drawLine(tint, start = androidx.compose.ui.geometry.Offset(w * 0.15f, h * 0.4f), end = androidx.compose.ui.geometry.Offset(w * 0.85f, h * 0.4f), strokeWidth = stroke)
        drawLine(tint, start = androidx.compose.ui.geometry.Offset(0f, h * 0.6f), end = androidx.compose.ui.geometry.Offset(w, h * 0.6f), strokeWidth = stroke)
        drawLine(tint, start = androidx.compose.ui.geometry.Offset(w * 0.25f, h * 0.8f), end = androidx.compose.ui.geometry.Offset(w * 0.75f, h * 0.8f), strokeWidth = stroke)
    }
}

@Composable
fun AlignRightIcon(tint: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()
        drawLine(tint, start = androidx.compose.ui.geometry.Offset(0f, h * 0.2f), end = androidx.compose.ui.geometry.Offset(w, h * 0.2f), strokeWidth = stroke)
        drawLine(tint, start = androidx.compose.ui.geometry.Offset(w * 0.3f, h * 0.4f), end = androidx.compose.ui.geometry.Offset(w, h * 0.4f), strokeWidth = stroke)
        drawLine(tint, start = androidx.compose.ui.geometry.Offset(0f, h * 0.6f), end = androidx.compose.ui.geometry.Offset(w, h * 0.6f), strokeWidth = stroke)
        drawLine(tint, start = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.8f), end = androidx.compose.ui.geometry.Offset(w, h * 0.8f), strokeWidth = stroke)
    }
}

@Composable
fun AlignJustifyIcon(tint: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()
        drawLine(tint, start = androidx.compose.ui.geometry.Offset(0f, h * 0.2f), end = androidx.compose.ui.geometry.Offset(w, h * 0.2f), strokeWidth = stroke)
        drawLine(tint, start = androidx.compose.ui.geometry.Offset(0f, h * 0.4f), end = androidx.compose.ui.geometry.Offset(w, h * 0.4f), strokeWidth = stroke)
        drawLine(tint, start = androidx.compose.ui.geometry.Offset(0f, h * 0.6f), end = androidx.compose.ui.geometry.Offset(w, h * 0.6f), strokeWidth = stroke)
        drawLine(tint, start = androidx.compose.ui.geometry.Offset(0f, h * 0.8f), end = androidx.compose.ui.geometry.Offset(w, h * 0.8f), strokeWidth = stroke)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComicReaderScreen(
    extensionId: String,
    novelUrl: String,
    chapterUrl: String,
    isOffline: Boolean = false,
    navController: NavHostController,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val chapter by viewModel.chapter.collectAsStateWithLifecycle()
    val novel by viewModel.novel.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val currentIndex by viewModel.currentIndex.collectAsStateWithLifecycle()
    
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE) }
    
    // Đọc chế độ đọc: 0: Cuộn dọc, 1: Lật ngang
    var readingMode by remember { 
        mutableIntStateOf(prefs.getInt("comic_reading_mode", 0)) 
    }
    
    var showUI by remember { mutableStateOf(false) }
    var currentChapterUrl by remember { mutableStateOf(chapterUrl) }

    // Trạng thái zoom cử chỉ
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformableState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 4f)
        if (scale > 1f) {
            offset += offsetChange
        } else {
            offset = Offset.Zero
        }
    }

    val listState = rememberLazyListState()
    
    val images = chapter?.images ?: emptyList()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { images.size })

    androidx.activity.compose.BackHandler {
        navController.popBackStack()
    }

    val activity = context as? android.app.Activity
    val view = androidx.compose.ui.platform.LocalView.current
    var isLandscape by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            activity?.window?.let { window ->
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, view)
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(showUI) {
        activity?.window?.let { window ->
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, view)
            if (showUI) {
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            } else {
                insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    LaunchedEffect(extensionId, novelUrl, isOffline) {
        viewModel.loadToc(extensionId, novelUrl, isOffline = isOffline)
        viewModel.loadNovel(novelUrl)
    }

    LaunchedEffect(currentChapterUrl, chapters) {
        viewModel.findCurrentIndex(currentChapterUrl)
    }

    LaunchedEffect(currentChapterUrl, isOffline) {
        viewModel.loadChapter(extensionId, currentChapterUrl, isOffline = isOffline)
        try {
            listState.scrollToItem(0)
        } catch (_: Exception) {}
        scale = 1f
        offset = Offset.Zero
    }

    LaunchedEffect(chapter?.url) {
        chapter?.let { ch ->
            try {
                listState.scrollToItem(0)
            } catch (_: Exception) {}
            scale = 1f
            offset = Offset.Zero
        }
    }

    LaunchedEffect(chapter) {
        chapter?.let { ch ->
            viewModel.updateProgress(novelUrl, extensionId, ch.url, ch.title)
        }
    }

    var showTocBottomSheet by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (error != null) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "❌ Lỗi Tải Trang", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = error ?: "", color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { viewModel.loadChapter(extensionId, currentChapterUrl, isOffline = isOffline) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                    ) {
                        Text("Thử lại")
                    }
                }
            }
        } else {
            chapter?.let { ch ->
                if (images.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            )
                            .transformable(state = transformableState)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        if (scale > 1f) {
                                            scale = 1f
                                            offset = Offset.Zero
                                        } else {
                                            scale = 2.5f
                                        }
                                    },
                                    onTap = {
                                        showUI = !showUI
                                    }
                                )
                            }
                    ) {
                        if (readingMode == 0) {
                            // Chế độ 1: Cuộn dọc liên tục
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                itemsIndexed(images, key = { index, imgUrl -> "$index-$imgUrl" }) { index, imgUrl ->
                                    val imageRequest = remember(imgUrl) {
                                        val cookie = extensionId.takeIf { it.isNotBlank() }?.let { prefs.getString("ext_cookies_$it", null) }
                                        val defaultUa = com.nam.novelreader.util.UserAgentUtils.getCleanUserAgent(context)

                                        val builder = coil.request.ImageRequest.Builder(context)
                                            .data(imgUrl)
                                            .crossfade(true)
                                            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                        
                                        if (!cookie.isNullOrBlank()) {
                                            builder.addHeader("Cookie", cookie)
                                        }
                                        if (defaultUa.isNotBlank()) {
                                            builder.addHeader("User-Agent", defaultUa)
                                        }
                                        builder.addHeader("Referer", novelUrl)
                                        builder.build()
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = 400.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = imageRequest,
                                            contentDescription = "Trang ${index + 1}",
                                            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                                            contentScale = androidx.compose.ui.layout.ContentScale.FillWidth
                                        )
                                    }
                                }

                                // Nút nhảy chương nhanh ở cuối
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Button(
                                            onClick = { viewModel.getPreviousUrl()?.let { currentChapterUrl = it } },
                                            enabled = viewModel.hasPrevious,
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26211D)),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Chương trước", color = Color.White)
                                        }
                                        Button(
                                            onClick = { viewModel.getNextUrl()?.let { currentChapterUrl = it } },
                                            enabled = viewModel.hasNext,
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26211D)),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Chương sau", color = Color.White)
                                        }
                                    }
                                }
                            }
                        } else {
                            // Chế độ 2: Lật trang ngang (Horizontal Pager)
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize()
                            ) { page ->
                                val imgUrl = images[page]
                                val imageRequest = remember(imgUrl) {
                                    val cookie = extensionId.takeIf { it.isNotBlank() }?.let { prefs.getString("ext_cookies_$it", null) }
                                    val defaultUa = com.nam.novelreader.util.UserAgentUtils.getCleanUserAgent(context)

                                    val builder = coil.request.ImageRequest.Builder(context)
                                        .data(imgUrl)
                                        .crossfade(true)
                                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                    
                                    if (!cookie.isNullOrBlank()) {
                                        builder.addHeader("Cookie", cookie)
                                    }
                                    if (defaultUa.isNotBlank()) {
                                        builder.addHeader("User-Agent", defaultUa)
                                    }
                                    builder.addHeader("Referer", novelUrl)
                                    builder.build()
                                }
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = imageRequest,
                                        contentDescription = "Trang ${page + 1}",
                                        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black).padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = ch.content ?: "Không có nội dung hình ảnh.", color = Color.White, textAlign = TextAlign.Center)
                    }
                }
            }
        }

        // Top Bar điều khiển
        AnimatedVisibility(
            visible = showUI,
            enter = slideInVertically(initialOffsetY = { -it }),
            exit = slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Surface(color = Color.Black.copy(alpha = 0.8f)) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                    Text(
                        text = chapter?.title ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Bottom Bar & Slider trang điều khiển Comic
        AnimatedVisibility(
            visible = showUI && chapter != null && images.isNotEmpty(),
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val total = images.size
            val currentPageIndex = if (readingMode == 0) {
                remember {
                    derivedStateOf {
                        val index = listState.firstVisibleItemIndex
                        if (index < total) index else total - 1
                    }
                }.value
            } else {
                pagerState.currentPage
            }

            val coroutineScope = rememberCoroutineScope()

            Surface(
                color = Color.Black.copy(alpha = 0.85f),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 1. Slider trang ảnh trong chương
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Trang ${currentPageIndex + 1} / $total",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Slider(
                            value = currentPageIndex.toFloat(),
                            onValueChange = { value ->
                                coroutineScope.launch {
                                    val targetPage = value.toInt().coerceIn(0, total - 1)
                                    if (readingMode == 0) {
                                        listState.scrollToItem(targetPage)
                                    } else {
                                        pagerState.scrollToPage(targetPage)
                                    }
                                }
                            },
                            valueRange = 0f..(total - 1).toFloat().coerceAtLeast(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFD4A574),
                                activeTrackColor = Color(0xFFD4A574),
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 2. Các nút công cụ bổ sung
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Nút xoay màn hình nhanh
                        IconButton(onClick = {
                            isLandscape = !isLandscape
                            activity?.requestedOrientation = if (isLandscape) {
                                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            } else {
                                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            }
                        }) {
                            Icon(
                                imageVector = if (isLandscape) Icons.Filled.ScreenLockPortrait else Icons.Filled.ScreenRotation,
                                contentDescription = "Xoay màn hình",
                                tint = Color.White
                            )
                        }

                        // Chọn chế độ đọc (Cuộn dọc <-> Lật ngang)
                        Button(
                            onClick = {
                                val nextMode = if (readingMode == 0) 1 else 0
                                readingMode = nextMode
                                prefs.edit().putInt("comic_reading_mode", nextMode).apply()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26211D)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = if (readingMode == 0) Icons.Filled.ViewColumn else Icons.Filled.ViewCarousel,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (readingMode == 0) "Cuộn dọc" else "Lật ngang",
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }

                        // Danh sách chương Bottom Sheet
                        IconButton(onClick = { showTocBottomSheet = true }) {
                            Icon(Icons.Filled.FormatListBulleted, "Mục lục", tint = Color.White)
                        }
                    }
                }
            }
        }

        // Chỉ số trang nổi ở góc dưới khi ẩn UI điều khiển
        if (!showUI && chapter != null && images.isNotEmpty()) {
            val total = images.size
            val current = remember {
                derivedStateOf {
                    val index = if (readingMode == 0) listState.firstVisibleItemIndex else pagerState.currentPage
                    if (index < total) index + 1 else total
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "${current.value} / $total",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (showTocBottomSheet) {
            ReaderTocBottomSheet(
                novel = novel,
                chapters = chapters,
                currentChapterUrl = currentChapterUrl,
                themeIndex = 4, // Dark theme as default for comic
                onChapterClick = { targetChapter ->
                    currentChapterUrl = targetChapter.url
                    showTocBottomSheet = false
                },
                extensionId = extensionId,
                novelUrl = novelUrl,
                onRefreshToc = {
                    viewModel.loadToc(extensionId, novelUrl, forceRefresh = true, isOffline = isOffline)
                },
                onDismiss = { showTocBottomSheet = false },
                isOffline = isOffline
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoReaderScreen(
    extensionId: String,
    novelUrl: String,
    chapterUrl: String,
    isOffline: Boolean = false,
    navController: NavHostController,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val chapter by viewModel.chapter.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val currentIndex by viewModel.currentIndex.collectAsStateWithLifecycle()

    val resolvedVideoUrl by viewModel.resolvedVideoUrl.collectAsStateWithLifecycle()
    val isResolvingTrack by viewModel.isResolvingTrack.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val prefs = remember { context.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE) }

    // 1. Phân tách danh sách Server (Tracks) của tập phim từ JSON content
    val servers = remember(chapter?.content) {
        try {
            val content = chapter?.content ?: ""
            if (content.startsWith("[")) {
                val jsonArray = org.json.JSONArray(content)
                val list = mutableListOf<Pair<String, String>>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val title = obj.optString("title", obj.optString("name", "Server " + (i + 1)))
                    val data = obj.optString("data", obj.optString("url", obj.optString("link", "")))
                    list.add(Pair(title, data))
                }
                list
            } else {
                if (content.isNotBlank()) {
                    listOf(Pair("Mặc định", content))
                } else {
                    emptyList()
                }
            }
        } catch (e: Exception) {
            if (chapter?.content?.isNotBlank() == true) {
                listOf(Pair("Mặc định", chapter!!.content!!))
            } else {
                emptyList()
            }
        }
    }

    var selectedServerIndex by remember(chapter) { mutableIntStateOf(0) }
    var currentChapterUrl by remember { mutableStateOf(chapterUrl) }

    // Tự động phân giải Link Video khi chọn server hoặc đổi tập
    LaunchedEffect(servers, selectedServerIndex) {
        if (servers.isNotEmpty() && selectedServerIndex < servers.size) {
            val serverUrl = servers[selectedServerIndex].second
            viewModel.resolveVideoUrl(extensionId, serverUrl)
        }
    }

    // Tải thông tin tập phim
    LaunchedEffect(extensionId, novelUrl, isOffline) {
        viewModel.loadToc(extensionId, novelUrl, isOffline = isOffline)
    }

    LaunchedEffect(currentChapterUrl, chapters) {
        viewModel.findCurrentIndex(currentChapterUrl)
    }

    LaunchedEffect(currentChapterUrl, isOffline) {
        viewModel.loadChapter(extensionId, currentChapterUrl, isOffline = isOffline)
        selectedServerIndex = 0
    }

    LaunchedEffect(chapter) {
        chapter?.let { ch ->
            viewModel.updateProgress(novelUrl, extensionId, ch.url, ch.title)
        }
    }

    // Trình phát ExoPlayer
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = prefs.getBoolean("video_auto_play", true)
        }
    }

    // Đồng bộ tốc độ phát từ Preferences
    var playSpeed by remember { mutableFloatStateOf(prefs.getFloat("video_play_speed", 1.0f)) }
    LaunchedEffect(playSpeed) {
        exoPlayer.setPlaybackSpeed(playSpeed)
    }

    // Xoay màn hình và Immersive Mode
    var isFullscreen by remember { mutableStateOf(false) }
    val view = androidx.compose.ui.platform.LocalView.current

    fun enterImmersive() {
        activity?.window?.let { window ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                window.insetsController?.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                window.insetsController?.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                    or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
            }
        }
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        isFullscreen = true
    }

    fun exitImmersive() {
        activity?.window?.let { window ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                window.insetsController?.show(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        isFullscreen = false
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
            activity?.window?.let { window ->
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    window.insetsController?.show(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                }
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    androidx.activity.compose.BackHandler {
        if (isFullscreen) {
            exitImmersive()
        } else {
            navController.popBackStack()
        }
    }

    // Cài đặt giữ màn hình sáng
    val keepScreenOn = prefs.getBoolean("video_keep_screen_on", true)
    DisposableEffect(keepScreenOn) {
        if (keepScreenOn) {
            activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        VideoContent(
            chapter = chapter,
            isLoading = isLoading || isResolvingTrack,
            error = error,
            exoPlayer = exoPlayer,
            novelUrl = novelUrl,
            chromeUA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
            isFullscreen = isFullscreen,
            onFullscreenToggle = {
                if (isFullscreen) exitImmersive() else enterImmersive()
            },
            resolvedVideoUrl = resolvedVideoUrl,
            servers = servers,
            selectedServerIndex = selectedServerIndex,
            onServerChange = { selectedServerIndex = it },
            playSpeed = playSpeed,
            onPlaySpeedChange = { speed ->
                playSpeed = speed
                prefs.edit().putFloat("video_play_speed", speed).apply()
            },
            chapters = chapters,
            currentIndex = currentIndex,
            onChapterSelect = { targetUrl ->
                currentChapterUrl = targetUrl
            },
            navController = navController,
            isOffline = isOffline
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoContent(
    chapter: com.nam.novelreader.domain.model.Chapter?,
    isLoading: Boolean,
    error: String?,
    exoPlayer: ExoPlayer,
    novelUrl: String,
    chromeUA: String,
    isFullscreen: Boolean,
    onFullscreenToggle: () -> Unit,
    resolvedVideoUrl: String?,
    servers: List<Pair<String, String>>,
    selectedServerIndex: Int,
    onServerChange: (Int) -> Unit,
    playSpeed: Float,
    onPlaySpeedChange: (Float) -> Unit,
    chapters: List<Chapter>,
    currentIndex: Int,
    onChapterSelect: (String) -> Unit,
    navController: NavHostController,
    isOffline: Boolean
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val prefs = remember { context.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE) }

    // Trạng thái dò link video ngầm (WebView Sniffing)
    var sniffedVideoUrl by remember(resolvedVideoUrl) { mutableStateOf<String?>(null) }
    var isSniffing by remember(resolvedVideoUrl) { mutableStateOf(false) }
    var sniffingTimeout by remember(resolvedVideoUrl) { mutableStateOf(false) }

    // Nhận diện link video stream trực tiếp
    val videoExts = remember { listOf(".mp4", ".m3u8", ".mkv", ".webm", ".ts", ".avi", ".mov", ".flv", ".dash") }
    val isDirectLink = remember(resolvedVideoUrl) {
        val lower = (resolvedVideoUrl ?: "").lowercase()
        resolvedVideoUrl != null 
            && resolvedVideoUrl.startsWith("http") 
            && !resolvedVideoUrl.contains("<")
            && videoExts.any { lower.contains(it) }
    }

    LaunchedEffect(resolvedVideoUrl, isDirectLink) {
        if (resolvedVideoUrl != null) {
            if (isDirectLink) {
                sniffedVideoUrl = resolvedVideoUrl
                isSniffing = false
                sniffingTimeout = false
            } else {
                sniffedVideoUrl = null
                isSniffing = true
                sniffingTimeout = false
                // Hẹn giờ 10 giây nếu không sniff được thì fallback hiển thị WebView
                kotlinx.coroutines.delay(10000)
                if (sniffedVideoUrl == null) {
                    sniffingTimeout = true
                    isSniffing = false
                }
            }
        }
    }

    // Nạp link video vào ExoPlayer
    LaunchedEffect(sniffedVideoUrl) {
        if (!sniffedVideoUrl.isNullOrBlank()) {
            val mediaItem = MediaItem.fromUri(sniffedVideoUrl!!)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            
            // Tự động seek tới vị trí trước đó nếu có lưu cấu hình
            val autoContinue = prefs.getBoolean("video_auto_continue", false)
            if (autoContinue && chapter != null) {
                val savedPos = prefs.getLong("video_pos_" + chapter.url, 0L)
                if (savedPos > 0) {
                    exoPlayer.seekTo(savedPos)
                }
            }
            
            exoPlayer.play()
        }
    }

    // Save vị trí phát lại khi dừng/thoát
    DisposableEffect(chapter) {
        onDispose {
            if (chapter != null && exoPlayer.duration > 0) {
                prefs.edit().putLong("video_pos_" + chapter.url, exoPlayer.currentPosition).apply()
            }
        }
    }

    // Tự động chuyển tập khi phát xong
    var playFinishedTriggered by remember(chapter) { mutableStateOf(false) }
    val autoNext = prefs.getBoolean("video_auto_next", true)
    LaunchedEffect(exoPlayer) {
        exoPlayer.addListener(object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == androidx.media3.common.Player.STATE_ENDED && autoNext && !playFinishedTriggered) {
                    playFinishedTriggered = true
                    // Nhảy sang tập tiếp theo của Server hiện tại
                    val serverChapters = chapters.filterIndexed { idx, ch ->
                        // Bóc tách tập của Server hiện tại
                        var sIdx = 0
                        for (i in 0..idx) {
                            if (chapters[i].url.startsWith("section://")) sIdx++
                        }
                        // so khớp chỉ số Server
                        val currentServerIndex = selectedServerIndex
                        sIdx == currentServerIndex + 1 && !ch.url.startsWith("section://")
                    }
                    val currentChapInServerIdx = serverChapters.indexOfFirst { it.url == chapter?.url }
                    if (currentChapInServerIdx != -1 && currentChapInServerIdx < serverChapters.size - 1) {
                        onChapterSelect(serverChapters[currentChapInServerIdx + 1].url)
                    }
                }
            }
        })
    }

    // Trạng thái thanh công cụ điều khiển
    var showUI by remember { mutableStateOf(true) }
    LaunchedEffect(showUI) {
        if (showUI) {
            kotlinx.coroutines.delay(5000)
            showUI = false
        }
    }

    var showPlaylistBottomSheet by remember { mutableStateOf(false) }
    var showSettingsBottomSheet by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "Đang giải mã link phim...", color = Color.White, fontSize = 12.sp)
                }
            }
        } else if (error != null) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Error, contentDescription = null, tint = Color.Red, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Lỗi tải video: " + error, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            // Khi đã bắt được direct link hoặc đang tải video bằng ExoPlayer
            if (sniffedVideoUrl != null) {
                Box(
                    modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                        detectTapGestures(onTap = { showUI = !showUI })
                    }
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = false // Tắt controller mặc định, tự vẽ controls
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // 1. Vẽ các thanh điều khiển Custom Controls của chúng ta
                    AnimatedVisibility(
                        visible = showUI,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                        exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                        modifier = Modifier.align(Alignment.TopCenter)
                    ) {
                        VideoTopBar(
                            title = chapter?.title ?: "Xem phim",
                            servers = servers,
                            selectedServerIndex = selectedServerIndex,
                            onServerChange = onServerChange,
                            playSpeed = playSpeed,
                            onPlaySpeedChange = onPlaySpeedChange,
                            onSettingsClick = { showSettingsBottomSheet = true },
                            onBackClick = {
                                if (isFullscreen) onFullscreenToggle() else navController.popBackStack()
                            }
                        )
                    }

                    // Nút Play/Pause và tua nhanh ở giữa
                    AnimatedVisibility(
                        visible = showUI,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(40.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val seekStep = prefs.getInt("video_seek_step", 10) // mặc định tua 10s
                            // Tua lùi
                            IconButton(onClick = {
                                val target = (exoPlayer.currentPosition - (seekStep * 1000)).coerceAtLeast(0L)
                                exoPlayer.seekTo(target)
                            }) {
                                Icon(Icons.Filled.Replay10, "Tua lùi ${seekStep}s", tint = Color.White, modifier = Modifier.size(36.dp))
                            }

                            // Play/Pause
                            var isPlaying by remember { mutableStateOf(exoPlayer.isPlaying) }
                            LaunchedEffect(exoPlayer.isPlaying) {
                                isPlaying = exoPlayer.isPlaying
                            }
                            IconButton(
                                onClick = {
                                    if (exoPlayer.isPlaying) {
                                        exoPlayer.pause()
                                    } else {
                                        exoPlayer.play()
                                    }
                                },
                                modifier = Modifier.size(56.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            // Tua tới
                            IconButton(onClick = {
                                val target = (exoPlayer.currentPosition + (seekStep * 1000)).coerceAtMost(exoPlayer.duration)
                                exoPlayer.seekTo(target)
                            }) {
                                Icon(Icons.Filled.Forward10, "Tua tới ${seekStep}s", tint = Color.White, modifier = Modifier.size(36.dp))
                            }
                        }
                    }

                    // Bottom Bar điều khiển
                    AnimatedVisibility(
                        visible = showUI,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                        exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        VideoBottomBar(
                            exoPlayer = exoPlayer,
                            chapters = chapters,
                            selectedServerIndex = selectedServerIndex,
                            currentChapterUrl = chapter?.url ?: "",
                            onChapterSelect = onChapterSelect,
                            onPlaylistClick = { showPlaylistBottomSheet = true },
                            onFullscreenToggle = onFullscreenToggle,
                            isFullscreen = isFullscreen
                        )
                    }
                }
            } else if (isSniffing) {
                // Đang dò link ngầm (hiển thị spinner)
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "Đang dò link video chất lượng cao (sniffing)...", color = Color.White, fontSize = 12.sp)
                    }
                    
                    // Webview ẩn ngầm
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(1, 1) // 1x1 dp ẩn
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    mediaPlaybackRequiresUserGesture = false
                                    userAgentString = chromeUA
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun shouldInterceptRequest(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): WebResourceResponse? {
                                        val reqUrl = request?.url?.toString() ?: ""
                                        val lowerUrl = reqUrl.lowercase()
                                        if (lowerUrl.contains(".m3u8") || lowerUrl.contains(".mp4") || lowerUrl.contains(".mkv") || lowerUrl.contains(".webm") || lowerUrl.contains("googlevideo.com")) {
                                            (view?.context as? Activity)?.runOnUiThread {
                                                if (isSniffing && sniffedVideoUrl == null) {
                                                    sniffedVideoUrl = reqUrl
                                                    isSniffing = false
                                                    view.stopLoading()
                                                }
                                            }
                                        }
                                        return super.shouldInterceptRequest(view, request)
                                    }
                                }
                            }
                        },
                        update = { webView ->
                            resolvedVideoUrl?.let { webView.loadUrl(it, mapOf("Referer" to novelUrl)) }
                        }
                    )
                }
            } else if (sniffingTimeout) {
                // Quá 10 giây không dò được link video direct -> Hiển thị WebView trực tiếp làm Fallback
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    mediaPlaybackRequiresUserGesture = false
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    userAgentString = chromeUA
                                }
                                webChromeClient = object : WebChromeClient() {
                                    private var customView: View? = null
                                    private var customViewCallback: CustomViewCallback? = null

                                    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                        super.onShowCustomView(view, callback)
                                        if (customView != null) { callback?.onCustomViewHidden(); return }
                                        customView = view
                                        customViewCallback = callback
                                        onFullscreenToggle()
                                        val decorView = (ctx as? Activity)?.window?.decorView as? ViewGroup
                                        decorView?.addView(view, ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        ))
                                    }

                                    override fun onHideCustomView() {
                                        super.onHideCustomView()
                                        if (customView == null) return
                                        val decorView = (ctx as? Activity)?.window?.decorView as? ViewGroup
                                        decorView?.removeView(customView)
                                        customView = null
                                        customViewCallback?.onCustomViewHidden()
                                        customViewCallback = null
                                        onFullscreenToggle()
                                    }
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        return false
                                    }
                                }
                            }
                        },
                        update = { webView ->
                            resolvedVideoUrl?.let { webView.loadUrl(it, mapOf("Referer" to novelUrl)) }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Nút Back trên góc để thoát WebView fallback
                    IconButton(
                        onClick = {
                            if (isFullscreen) onFullscreenToggle() else navController.popBackStack()
                        },
                        modifier = Modifier.statusBarsPadding().padding(8.dp).align(Alignment.TopStart).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                }
            }
        }

        // Playlist bottom sheet
        if (showPlaylistBottomSheet) {
            VideoPlaylistBottomSheet(
                chapters = chapters,
                currentChapterUrl = chapter?.url ?: "",
                selectedServerIndex = selectedServerIndex,
                onChapterClick = { targetUrl ->
                    onChapterSelect(targetUrl)
                    showPlaylistBottomSheet = false
                },
                onDismiss = { showPlaylistBottomSheet = false }
            )
        }

        // Settings bottom sheet
        if (showSettingsBottomSheet) {
            VideoSettingsBottomSheet(
                onDismiss = { showSettingsBottomSheet = false }
            )
        }
    }
}

@Composable
private fun VideoTopBar(
    title: String,
    servers: List<Pair<String, String>>,
    selectedServerIndex: Int,
    onServerChange: (Int) -> Unit,
    playSpeed: Float,
    onPlaySpeedChange: (Float) -> Unit,
    onSettingsClick: () -> Unit,
    onBackClick: () -> Unit
) {
    var serverMenuExpanded by remember { mutableStateOf(false) }
    var speedMenuExpanded by remember { mutableStateOf(false) }

    Surface(color = Color.Black.copy(alpha = 0.6f)) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White)
            }
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // 1. Dropdown chọn Server phát
            if (servers.isNotEmpty()) {
                Box {
                    Button(
                        onClick = { serverMenuExpanded = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            text = servers.getOrNull(selectedServerIndex)?.first ?: "Mặc định",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(Icons.Filled.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(
                        expanded = serverMenuExpanded,
                        onDismissRequest = { serverMenuExpanded = false }
                    ) {
                        servers.forEachIndexed { index, pair ->
                            DropdownMenuItem(
                                text = { Text(pair.first) },
                                onClick = {
                                    onServerChange(index)
                                    serverMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 2. Dropdown chọn Tốc độ phát
            Box {
                Button(
                    onClick = { speedMenuExpanded = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = if (playSpeed == 1.0f) "1x" else "${playSpeed}x",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(Icons.Filled.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
                
                DropdownMenu(
                    expanded = speedMenuExpanded,
                    onDismissRequest = { speedMenuExpanded = false }
                ) {
                    // Custom dialog tốc độ dạng slide/chọn nhanh
                    listOf(0.25f, 0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f).forEach { speed ->
                        DropdownMenuItem(
                            text = { Text("${speed}x") },
                            onClick = {
                                onPlaySpeedChange(speed)
                                speedMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 3. Nút Cài đặt (răng cưa)
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Filled.Settings, "Cài đặt", tint = Color.White)
            }
        }
    }
}

@Composable
private fun VideoBottomBar(
    exoPlayer: ExoPlayer,
    chapters: List<Chapter>,
    selectedServerIndex: Int,
    currentChapterUrl: String,
    onChapterSelect: (String) -> Unit,
    onPlaylistClick: () -> Unit,
    onFullscreenToggle: () -> Unit,
    isFullscreen: Boolean
) {
    // Thu thập thời gian chạy hiện tại và tổng thời gian phát
    var currentPos by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }

    LaunchedEffect(exoPlayer) {
        while (true) {
            currentPos = exoPlayer.currentPosition
            duration = exoPlayer.duration.coerceAtLeast(0L)
            kotlinx.coroutines.delay(1000)
        }
    }

    // Định dạng thời gian (Long sang mm:ss)
    fun formatTime(ms: Long): String {
        if (ms <= 0) return "00:00"
        val totalSecs = ms / 1000
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        return String.format(java.util.Locale.US, "%02d:%02d", mins, secs)
    }

    // Lọc danh sách tập của Server hiện tại
    val serverChapters = remember(chapters, selectedServerIndex) {
        chapters.filterIndexed { idx, ch ->
            var sIdx = 0
            for (i in 0..idx) {
                if (chapters[i].url.startsWith("section://")) sIdx++
            }
            val currentServerIndex = selectedServerIndex
            sIdx == currentServerIndex + 1 && !ch.url.startsWith("section://")
        }
    }

    val currentChapInServerIdx = serverChapters.indexOfFirst { it.url == currentChapterUrl }
    val hasPrev = currentChapInServerIdx > 0
    val hasNext = currentChapInServerIdx != -1 && currentChapInServerIdx < serverChapters.size - 1

    Surface(color = Color.Black.copy(alpha = 0.6f)) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // 1. SeekBar tiến trình phát
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = formatTime(currentPos),
                    color = Color.White,
                    fontSize = 11.sp
                )
                Slider(
                    value = currentPos.toFloat(),
                    onValueChange = { value ->
                        exoPlayer.seekTo(value.toLong())
                    },
                    valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFD4A574),
                        activeTrackColor = Color(0xFFD4A574),
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatTime(duration),
                    color = Color.White,
                    fontSize = 11.sp
                )
            }

            // 2. Hàng nút điều khiển dưới đáy
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Góc bên trái: Tập trước / Tập sau
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    IconButton(
                        onClick = {
                            if (hasPrev) onChapterSelect(serverChapters[currentChapInServerIdx - 1].url)
                        },
                        enabled = hasPrev
                    ) {
                        Icon(Icons.Filled.SkipPrevious, "Tập trước", tint = if (hasPrev) Color.White else Color.White.copy(alpha = 0.3f))
                    }
                    IconButton(
                        onClick = {
                            if (hasNext) onChapterSelect(serverChapters[currentChapInServerIdx + 1].url)
                        },
                        enabled = hasNext
                    ) {
                        Icon(Icons.Filled.SkipNext, "Tập sau", tint = if (hasNext) Color.White else Color.White.copy(alpha = 0.3f))
                    }
                }

                // Góc bên phải: Playlist, Xoay ngang màn hình
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Nút Danh sách tập (Playlist)
                    IconButton(onClick = onPlaylistClick) {
                        Icon(Icons.Filled.QueuePlayNext, "Danh sách tập", tint = Color.White)
                    }
                    // Nút Xoay màn hình nhanh (Full/Normal screen)
                    IconButton(onClick = onFullscreenToggle) {
                        Icon(
                            imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                            contentDescription = "Toàn màn hình",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoPlaylistBottomSheet(
    chapters: List<Chapter>,
    currentChapterUrl: String,
    selectedServerIndex: Int,
    onChapterClick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // 1. Nhóm các tập theo Server dựa trên separator "section://"
    val serverTabs = remember(chapters) {
        val list = mutableListOf<String>()
        chapters.forEach { ch ->
            if (ch.url.startsWith("section://")) {
                list.add(ch.title)
            }
        }
        if (list.isEmpty()) list.add("Mặc định")
        list
    }

    var selectedTabIdx by remember(selectedServerIndex) { mutableIntStateOf(selectedServerIndex.coerceIn(0, serverTabs.size - 1)) }
    var searchQuery by remember { mutableStateOf("") }

    // Lọc các tập phim của server đang chọn
    val filteredChapters = remember(chapters, selectedTabIdx, searchQuery) {
        val serverName = serverTabs.getOrNull(selectedTabIdx) ?: "Mặc định"
        val list = mutableListOf<Chapter>()
        var currentServer = "Mặc định"
        
        chapters.forEach { ch ->
            if (ch.url.startsWith("section://")) {
                currentServer = ch.title
            } else {
                if (currentServer == serverName) {
                    if (searchQuery.isBlank() || ch.title.contains(searchQuery, ignoreCase = true)) {
                        list.add(ch)
                    }
                }
            }
        }
        list
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141210),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header Playlist
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Danh sách tập", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, "Đóng", tint = Color.White)
                }
            }

            // 2. Hàng Tab Server
            if (serverTabs.size > 1) {
                ScrollableTabRow(
                    selectedTabIndex = selectedTabIdx,
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                    edgePadding = 0.dp,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            color = Color(0xFFD4A574),
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIdx])
                        )
                    }
                ) {
                    serverTabs.forEachIndexed { index, name ->
                        Tab(
                            selected = selectedTabIdx == index,
                            onClick = { selectedTabIdx = index },
                            text = { Text(name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (selectedTabIdx == index) Color(0xFFD4A574) else Color.White.copy(alpha = 0.6f)) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Ô tìm kiếm tập phim
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Tìm kiếm tập phim...", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFD4A574),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Lưới hiển thị các tập phim
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false).defaultMinSize(minHeight = 200.dp)
            ) {
                items(filteredChapters) { ch ->
                    val isCurrent = ch.url == currentChapterUrl
                    val btnBg = if (isCurrent) Color(0xFFD4A574) else Color(0xFF26211D)
                    val textColor = if (isCurrent) Color(0xFF141210) else Color.White

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(btnBg)
                            .clickable { onChapterClick(ch.url) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = ch.title,
                            color = textColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoSettingsBottomSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE) }

    var autoPlay by remember { mutableStateOf(prefs.getBoolean("video_auto_play", true)) }
    var autoNext by remember { mutableStateOf(prefs.getBoolean("video_auto_next", true)) }
    var autoContinue by remember { mutableStateOf(prefs.getBoolean("video_auto_continue", false)) }
    var seekStep by remember { mutableIntStateOf(prefs.getInt("video_seek_step", 10)) }
    var keepScreenOn by remember { mutableStateOf(prefs.getBoolean("video_keep_screen_on", true)) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141210)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Cài đặt phát video", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, "Đóng", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Phát lại
            Text(text = "Phát lại", color = Color(0xFFD4A574), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Tự động phát", color = Color.White, fontSize = 14.sp)
                Switch(
                    checked = autoPlay,
                    onCheckedChange = {
                        autoPlay = it
                        prefs.edit().putBoolean("video_auto_play", it).apply()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFFD4A574),
                        checkedTrackColor = Color(0xFFD4A574).copy(alpha = 0.5f)
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Tự động chuyển tập", color = Color.White, fontSize = 14.sp)
                Switch(
                    checked = autoNext,
                    onCheckedChange = {
                        autoNext = it
                        prefs.edit().putBoolean("video_auto_next", it).apply()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFFD4A574),
                        checkedTrackColor = Color(0xFFD4A574).copy(alpha = 0.5f)
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Tự động tiếp tục từ vị trí trước", color = Color.White, fontSize = 14.sp)
                Switch(
                    checked = autoContinue,
                    onCheckedChange = {
                        autoContinue = it
                        prefs.edit().putBoolean("video_auto_continue", it).apply()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFFD4A574),
                        checkedTrackColor = Color(0xFFD4A574).copy(alpha = 0.5f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tua
            Text(text = "Tua tới/Tua lùi", color = Color(0xFFD4A574), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(5, 10, 15, 30).forEach { secs ->
                    val isSelected = seekStep == secs
                    val bg = if (isSelected) Color(0xFFD4A574) else Color(0xFF26211D)
                    val textCol = if (isSelected) Color(0xFF141210) else Color.White
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(bg)
                            .clickable {
                                seekStep = secs
                                prefs.edit().putInt("video_seek_step", secs).apply()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "${secs}s", color = textCol, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Hiển thị
            Text(text = "Hiển thị & Năng lượng", color = Color(0xFFD4A574), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Giữ màn hình luôn bật khi xem phim", color = Color.White, fontSize = 14.sp)
                Switch(
                    checked = keepScreenOn,
                    onCheckedChange = {
                        keepScreenOn = it
                        prefs.edit().putBoolean("video_keep_screen_on", it).apply()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFFD4A574),
                        checkedTrackColor = Color(0xFFD4A574).copy(alpha = 0.5f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
