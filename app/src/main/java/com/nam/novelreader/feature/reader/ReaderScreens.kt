package com.nam.novelreader.feature.reader

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

    fun loadChapter(extensionId: String, chapterUrl: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
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

    fun loadToc(extensionId: String, novelUrl: String, forceRefresh: Boolean = false) {
        viewModelScope.launch {
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
    val autoTranslateQt = prefs.getBoolean("reader_auto_translate_qt", false)
    val isDictLoaded by com.nam.novelreader.util.QuickTranslateEngine.isDictLoadedFlow.collectAsStateWithLifecycle(
        initialValue = com.nam.novelreader.util.QuickTranslateEngine.isDictLoaded()
    )

    var displayTitle by remember { mutableStateOf("") }
    var displayContent by remember { mutableStateOf<String?>(null) }
    var isTranslating by remember { mutableStateOf(false) }

    // Đồng bộ hóa trạng thái tức thời ngay trong Composition để WebView nạp ngay
    var lastChapterUrl by remember { mutableStateOf<String?>(null) }
    val currentUrlFromFlow = chapter?.url
    if (currentUrlFromFlow != lastChapterUrl) {
        lastChapterUrl = currentUrlFromFlow
        displayTitle = chapter?.title ?: ""
        displayContent = chapter?.content
    }

    LaunchedEffect(chapter?.url, autoTranslateQt, isDictLoaded) {
        val ch = chapter
        if (ch != null && autoTranslateQt && ch.content != null) {
            isTranslating = true
            val (tTitle, tContent) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                val title = com.nam.novelreader.util.QuickTranslateEngine.translate(context, ch.title)
                val content = com.nam.novelreader.util.QuickTranslateEngine.translate(context, ch.content)
                Pair(title, content)
            }
            displayTitle = tTitle
            displayContent = tContent
            isTranslating = false
        } else {
            displayTitle = ch?.title ?: ""
            displayContent = ch?.content
            isTranslating = false
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
                    
                    // Nếu đang nghe chương khác, chuyển chapter trên UI cho đồng bộ
                    if (ttsChapterUrl.isNotEmpty() && ttsChapterUrl != currentChapterUrl) {
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

    LaunchedEffect(extensionId, novelUrl) {
        kotlinx.coroutines.delay(250)
        viewModel.loadToc(extensionId, novelUrl)
        viewModel.loadNovel(novelUrl)
    }

    LaunchedEffect(currentChapterUrl, chapters) {
        viewModel.findCurrentIndex(currentChapterUrl)
    }

    LaunchedEffect(currentChapterUrl) {
        if (!loadedChaptersInWebView.contains(currentChapterUrl)) {
            webViewAlpha = 0.4f
            loadedChaptersInWebView.clear()
            loadedChaptersInWebView.add(currentChapterUrl)
            if (chapter == null) {
                kotlinx.coroutines.delay(250)
            }
            viewModel.loadChapter(extensionId, currentChapterUrl)
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
    val (uiBgColor, uiTextColor, uiPrimaryColor) = when (themeIndex) {
        0 -> Triple(Color(0xFFD3C3A3), Color(0xFF3A3129), Color(0xFF8B5A2B)) // Kraft hoa văn (bg7.jpg)
        1 -> Triple(Color(0xFFFFFFFF), Color(0xFF3A342B), Color(0xFF1976D2)) // Trắng trơn
        2 -> Triple(Color(0xFFF1F7ED), Color(0xFF1B310E), Color(0xFF2E7D32)) // Xanh lá hoa văn (bg6.png)
        3 -> Triple(Color(0xFFA2C0E5), Color(0xFF1B310E), Color(0xFF1976D2)) // Xanh dương hoa văn (bg4.jpg)
        4 -> Triple(Color(0xFF1C1C1C), Color(0xFFCCCCCC), Color(0xFFD4A574)) // Đêm đen
        5 -> Triple(Color(0xFFF3C9D7), Color(0xFF1B310E), Color(0xFFC2185B)) // Hồng hoa văn (bg5.jpg)
        6 -> Triple(Color(0xFFECE1CA), Color(0xFF645032), Color(0xFF8B5A2B)) // Vàng giấy trơn
        7 -> Triple(Color(0xFFC2E0CD), Color(0xFF334B39), Color(0xFF2E7D32)) // Lục nhạt trơn
        else -> Triple(Color(0xFFD3C3A3), Color(0xFF3A3129), Color(0xFF8B5A2B))
    }

    val customColorScheme = if (themeIndex == 4) {
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
                                                                val displayNextContent = if (autoTranslateQt && isDictLoaded) {
                                                                    com.nam.novelreader.util.QuickTranslateEngine.translate(context, nextContent)
                                                                } else {
                                                                    nextContent
                                                                }
                                                                val displayNextTitle = if (autoTranslateQt && isDictLoaded) {
                                                                    com.nam.novelreader.util.QuickTranslateEngine.translate(context, nextTitle)
                                                                } else {
                                                                    nextTitle
                                                                }
                                                                val formatted = formatHtmlContent(displayNextContent)
                                                                val escapedTitle = displayNextTitle.replace("'", "\\'").replace("\"", "\\\"")
                                                                val escapedContent = formatted.replace("'", "\\'").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")
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
                var showTranslationDialogByMenu by remember { mutableStateOf(false) }

                if (showTranslationDialogByMenu) {
                    AlertDialog(
                        onDismissRequest = { showTranslationDialogByMenu = false },
                        title = { Text("Chế độ dịch") },
                        text = { Text("Bạn có muốn bật/tắt chế độ dịch nhanh (Quick Translate) của trình đọc?") },
                        confirmButton = {
                            TextButton(onClick = {
                                showTranslationDialogByMenu = false
                                prefs.edit().putBoolean("reader_auto_translate_qt", !autoTranslateQt).apply()
                                viewModel.loadChapter(extensionId, currentChapterUrl)
                            }) {
                                Text("Xác nhận")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showTranslationDialogByMenu = false }) {
                                Text("Hủy")
                            }
                        }
                    )
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

                        // Nút Gốc/Dịch nằm trên Top Bar được chuyển sang bên phải và đồng bộ màu
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = uiTextColor.copy(alpha = 0.1f),
                            modifier = Modifier
                                .clickable { showTranslationDialogByMenu = true }
                                .padding(horizontal = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Language, null, tint = uiTextColor, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (autoTranslateQt) "Dịch" else "Gốc",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = uiTextColor
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(Icons.Filled.KeyboardArrowDown, null, tint = uiTextColor.copy(alpha = 0.7f), modifier = Modifier.size(10.dp))
                            }
                        }

                        IconButton(onClick = { viewModel.loadChapter(extensionId, currentChapterUrl) }) {
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
                        viewModel.loadToc(extensionId, novelUrl, forceRefresh = true)
                    },
                    onDismiss = { showTocBottomSheet = false }
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

            if ((isLoading || isTranslating || webViewAlpha < 0.9f) && error == null) {
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
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val allThemes = listOf(0, 1, 2, 3, 4, 5, 6, 7)
                    allThemes.forEach { index ->
                        val isSelected = themeIndex == index
                        val themeColor = when (index) {
                            1 -> Color(0xFFFFFFFF)
                            4 -> Color(0xFF000000)
                            6 -> Color(0xFFECE1CA)
                            7 -> Color(0xFFC2E0CD)
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
    // 8 themes từ APK gốc
    val (textColor, bgColor) = when (themeIndex) {
        0 -> Pair("#3A3129", "#D3C3A3") // Kraft hoa văn (bg7.jpg)
        1 -> Pair("#3A342B", "#FFFFFF") // Trắng trơn
        2 -> Pair("#1B310E", "#F1F7ED") // Xanh lá hoa văn (bg6.png)
        3 -> Pair("#1B310E", "#A2C0E5") // Xanh dương hoa văn (bg4.jpg)
        4 -> Pair("#CCCCCC", "#000000") // Đêm đen
        5 -> Pair("#1B310E", "#F3C9D7") // Hồng hoa văn (bg5.jpg)
        6 -> Pair("#645032", "#ECE1CA") // Vàng giấy trơn
        7 -> Pair("#334B39", "#C2E0CD") // Lục nhạt trơn
        else -> Pair("#3A3129", "#D3C3A3")
    }

    val bgImageName = when (themeIndex) {
        0 -> "bg7.jpg"
        2 -> "bg6.png"
        3 -> "bg4.jpg"
        5 -> "bg5.jpg"
        else -> null
    }
    val bgImageBase64 = bgImageName?.let { getAssetAsBase64(context, it) }

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
                ${if (!bgImageBase64.isNullOrEmpty()) "background-image: url('data:image/${if (bgImageName!!.endsWith(".png")) "png" else "jpeg"};base64,$bgImageBase64'); background-repeat: repeat; background-size: auto;" else ""}
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
            p {
                text-align: $textAlignStyle;
                text-indent: ${textIndentValue}em;
                letter-spacing: ${charSpacing}em;
                margin-top: 0;
                margin-bottom: ${paragraphSpacing}em;
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
    navController: NavHostController,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val chapter by viewModel.chapter.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val currentIndex by viewModel.currentIndex.collectAsStateWithLifecycle()
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

    val listState = androidx.compose.runtime.saveable.rememberSaveable(currentChapterUrl, saver = LazyListState.Saver) { LazyListState() }

    androidx.activity.compose.BackHandler {
        navController.popBackStack()
    }

    val activity = LocalContext.current as? android.app.Activity
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(Unit) {
        onDispose {
            activity?.window?.let { window ->
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, view)
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
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

    LaunchedEffect(extensionId, novelUrl) {
        viewModel.loadToc(extensionId, novelUrl)
    }

    LaunchedEffect(currentChapterUrl, chapters) {
        viewModel.findCurrentIndex(currentChapterUrl)
    }

    LaunchedEffect(currentChapterUrl) {
        viewModel.loadChapter(extensionId, currentChapterUrl)
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

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (error != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "❌ Lỗi Tải Trang", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = error ?: "", color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { viewModel.loadChapter(extensionId, currentChapterUrl) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                    ) {
                        Text("Thử lại")
                    }
                }
            }
        } else {
            chapter?.let { ch ->
                val images = ch.images ?: emptyList()
                if (images.isNotEmpty()) {
                    // Zoom box
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
                        val context = LocalContext.current
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(images, key = { index, imgUrl -> "$index-$imgUrl" }) { index, imgUrl ->
                                val imageRequest = remember(imgUrl) {
                                    val prefs = context.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE)
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
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .wrapContentHeight(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.FillWidth
                                    )
                                }
                            }

                            // Chuyển chương
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp, horizontal = 16.dp),
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
                    }
                } else {
                    // Fallback về text reader nếu không có images
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = ch.content ?: "Không có nội dung hình ảnh.",
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Top bar
        AnimatedVisibility(
            visible = showUI,
            enter = slideInVertically(initialOffsetY = { -it }),
            exit = slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Surface(color = Color.Black.copy(alpha = 0.85f)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
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

        // Chỉ số trang / Page indicator native ở góc dưới bên phải
        if (chapter != null && !chapter!!.images.isNullOrEmpty()) {
            val total = chapter!!.images!!.size
            val current = remember {
                derivedStateOf {
                    val index = listState.firstVisibleItemIndex
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoReaderScreen(
    extensionId: String,
    novelUrl: String,
    chapterUrl: String,
    navController: NavHostController,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val chapter by viewModel.chapter.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    androidx.activity.compose.BackHandler {
        navController.popBackStack()
    }

    DisposableEffect(chapterUrl) {
        viewModel.loadChapter(extensionId, chapterUrl)
        onDispose { }
    }

    LaunchedEffect(chapter) {
        chapter?.let { ch ->
            viewModel.updateProgress(novelUrl, extensionId, ch.url, ch.title)
            val content = ch.content?.trim() ?: ""
            // Nếu là direct link, set vào exoplayer
            if (content.startsWith("http") && !content.contains("<")) {
                val mediaItem = MediaItem.fromUri(content)
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(chapter?.title ?: "Video Reader", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues).background(Color.Black)) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
            } else if (error != null) {
                Text(text = error ?: "Lỗi kết nối", color = Color.Red, modifier = Modifier.align(Alignment.Center))
            } else if (chapter != null) {
                val content = chapter!!.content ?: ""
                val isDirectLink = content.trim().startsWith("http") && !content.trim().contains("<")

                if (isDirectLink) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = true
                                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Embed video / Iframe player -> Fallback WebView với Fullscreen support
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.mediaPlaybackRequiresUserGesture = false
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                setBackgroundColor(android.graphics.Color.BLACK)

                                webChromeClient = object : android.webkit.WebChromeClient() {
                                    private var customView: android.view.View? = null
                                    private var customViewCallback: CustomViewCallback? = null

                                    override fun onShowCustomView(view: android.view.View?, callback: CustomViewCallback?) {
                                        super.onShowCustomView(view, callback)
                                        if (customView != null) {
                                            callback?.onCustomViewHidden()
                                            return
                                        }
                                        customView = view
                                        customViewCallback = callback

                                        val activity = context as? android.app.Activity
                                        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                        val decorView = activity?.window?.decorView as? ViewGroup
                                        decorView?.addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                                    }

                                    override fun onHideCustomView() {
                                        super.onHideCustomView()
                                        if (customView == null) return

                                        val activity = context as? android.app.Activity
                                        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                        val decorView = activity?.window?.decorView as? ViewGroup
                                        decorView?.removeView(customView)
                                        customView = null
                                        customViewCallback?.onCustomViewHidden()
                                        customViewCallback = null
                                    }
                                }
                                webViewClient = WebViewClient()
                            }
                        },
                        update = { webView ->
                            val html = if (content.trim().startsWith("<iframe") || content.trim().startsWith("<video")) {
                                """<html><body style="margin:0;padding:0;background:black;display:flex;justify-content:center;align-items:center;height:100vh;">${content}</body></html>"""
                            } else {
                                content
                            }
                            webView.loadDataWithBaseURL(novelUrl, html, "text/html", "UTF-8", null)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
