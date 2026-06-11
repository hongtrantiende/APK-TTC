package com.nam.novelreader.feature.detail

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.nam.novelreader.feature.components.bounceClick
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.nam.novelreader.data.repository.NovelRepository
import com.nam.novelreader.domain.model.Chapter
import com.nam.novelreader.domain.model.ContentType
import com.nam.novelreader.domain.model.Novel
import com.nam.novelreader.domain.model.Comment
import com.nam.novelreader.extension.model.ScriptType
import com.nam.novelreader.navigation.Routes
import com.nam.novelreader.service.DownloadService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import javax.inject.Inject

@HiltViewModel
class NovelDetailViewModel @Inject constructor(
    private val repository: NovelRepository,
    val appPrefs: com.nam.novelreader.data.preferences.AppPreferences,
) : ViewModel() {
    private val _novel = MutableStateFlow<Novel?>(null)
    val novel: StateFlow<Novel?> = _novel

    private val _extensionIconFile = MutableStateFlow<java.io.File?>(null)
    val extensionIconFile: StateFlow<java.io.File?> = _extensionIconFile

    private val _extensionLocalPath = MutableStateFlow("")
    /** localPath của extension hiện tại — dùng để đọc plugin.json defaults */
    val extensionLocalPath: StateFlow<String> = _extensionLocalPath

    private val _chapters = MutableStateFlow<List<Chapter>>(emptyList())
    val chapters: StateFlow<List<Chapter>> = _chapters

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isInLibrary = MutableStateFlow(false)
    val isInLibrary: StateFlow<Boolean> = _isInLibrary

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _authorNovels = MutableStateFlow<List<Novel>>(emptyList())
    val authorNovels: StateFlow<List<Novel>> = _authorNovels

    private val _isAuthorNovelsLoading = MutableStateFlow(false)
    val isAuthorNovelsLoading: StateFlow<Boolean> = _isAuthorNovelsLoading

    private val _comments = MutableStateFlow<List<Comment>>(emptyList())
    val comments: StateFlow<List<Comment>> = _comments

    private val _nextCommentPage = MutableStateFlow("")
    val nextCommentPage: StateFlow<String> = _nextCommentPage

    private val _isCommentsLoading = MutableStateFlow(false)
    val isCommentsLoading: StateFlow<Boolean> = _isCommentsLoading

    private val _hasCommentsScript = MutableStateFlow(false)
    val hasCommentsScript: StateFlow<Boolean> = _hasCommentsScript

    private val _isFollowed = MutableStateFlow(false)
    val isFollowed: StateFlow<Boolean> = _isFollowed

    private var translationJob: kotlinx.coroutines.Job? = null
    private var loadDetailJob: kotlinx.coroutines.Job? = null

    fun checkFollowState(novelUrl: String) {
        _isFollowed.value = appPrefs.isNovelFollowed(novelUrl)
    }

    fun toggleFollow(extensionId: String, novelUrl: String, novelTitle: String) {
        if (_isFollowed.value) {
            appPrefs.unfollowNovel(novelUrl)
            _isFollowed.value = false
        } else {
            appPrefs.followNovel(extensionId, novelUrl, novelTitle)
            _isFollowed.value = true
        }
    }

    private fun loadExtensionIcon(extensionId: String, novelUrl: String) {
        viewModelScope.launch {
            val extId = if (extensionId.isBlank()) {
                repository.getNovelFromDb(novelUrl)?.extensionId ?: ""
            } else {
                extensionId
            }
            if (extId.isNotBlank()) {
                try {
                    val ext = repository.getExtension(extId)
                    _extensionIconFile.value = ext?.getIconFile()
                    // Lưu localPath để đọc plugin.json defaults
                    _extensionLocalPath.value = ext?.directory?.absolutePath ?: ""
                } catch (e: Exception) {
                    android.util.Log.e("NovelDetailVM", "Failed to load extension icon: ${e.message}")
                }
            }
        }
    }

    fun loadDetail(extensionId: String, novelUrl: String, forceRefresh: Boolean = false, isOffline: Boolean = false) {
        checkFollowState(novelUrl)
        loadExtensionIcon(extensionId, novelUrl)
        
        // Nếu không yêu cầu load lại và đang hiển thị đúng truyện, bỏ qua
        if (!forceRefresh && _novel.value?.url == novelUrl && _chapters.value.isNotEmpty()) {
            val resolvedNovel = _novel.value
            val resolvedExtId = if (extensionId.isBlank()) resolvedNovel?.extensionId ?: "" else extensionId
            safeLoadAuthorNovels(resolvedExtId, resolvedNovel, novelUrl)
            checkCommentsSupport(resolvedExtId)
            loadComments(resolvedExtId, novelUrl)
            return
        }
        
        // Hủy job cũ nếu đang chạy — tránh duplicate calls
        loadDetailJob?.cancel()
        loadDetailJob = viewModelScope.launch {
            _error.value = null
            android.util.Log.d("NovelDetailVM", "loadDetail START: ext=$extensionId, url=$novelUrl, forceRefresh=$forceRefresh")
            
            // 1. Tải nhanh từ Database nếu có (chạy ngay lập tức để tránh delay 350ms)
            val cachedNovel = repository.getNovelFromDb(novelUrl)
            var resolvedExtId = extensionId
            var hasCache = false
            
            if (cachedNovel != null) {
                _novel.value = cachedNovel
                if (resolvedExtId.isBlank() && cachedNovel.extensionId.isNotBlank()) {
                    resolvedExtId = cachedNovel.extensionId
                }
                val cachedChapters = repository.getChapterList(novelUrl).map { entity ->
                    com.nam.novelreader.domain.model.Chapter(
                        url = entity.url,
                        title = entity.title,
                        index = entity.index,
                        content = entity.content,
                        images = entity.images?.let {
                            try {
                                kotlinx.serialization.json.Json.decodeFromString(it)
                            } catch (e: Exception) {
                                null
                            }
                        },
                        isDownloaded = entity.isDownloaded
                    )
                }
                if (cachedChapters.isNotEmpty()) {
                    _chapters.value = cachedChapters
                    hasCache = true
                    _isLoading.value = false // Tắt loading chính ngay vì đã có cache hiển thị
                    android.util.Log.d("NovelDetailVM", "Cache loaded: novel='${cachedNovel.title}', chapters=${cachedChapters.size}")
                    startTranslatingChapters(resolvedExtId)
                }
            }
            
            // Nếu hoàn toàn chưa có cache, hiển thị màn hình loading xoay tròn
            if (!hasCache) {
                _isLoading.value = true
            }
            
            _isInLibrary.value = repository.isInLibrary(novelUrl)
            val inLibrary = _isInLibrary.value
            
            // Chỉ gọi mạng (fetch mới) khi: (forceRefresh = true, HOẶC chưa có cache, HOẶC truyện CHƯA có trong thư viện (xem online)) và không ở chế độ offline
            val shouldFetchNetwork = (forceRefresh || !hasCache || !inLibrary) && !isOffline
            
            if (shouldFetchNetwork) {
                // Trì hoãn 350ms trước khi gọi mạng để tránh nghẽn luồng transition UI khi vừa mở màn hình
                if (!forceRefresh && hasCache) {
                    kotlinx.coroutines.delay(350)
                }
                
                // FIX: Tách riêng try-catch cho detail và TOC — nếu detail throw thì TOC vẫn được gọi
                try {
                    val remoteNovel = repository.getNovelDetail(resolvedExtId, novelUrl)
                    if (remoteNovel != null) {
                        _novel.value = remoteNovel
                        repository.updateNovelMetadata(remoteNovel)
                        android.util.Log.d("NovelDetailVM", "Remote novel loaded: '${remoteNovel.title}'")
                    }
                } catch (e: Exception) {
                    android.util.Log.w("NovelDetailVM", "Failed to fetch novel detail (non-fatal if cached): ${e.message}")
                }
                
                try {
                    val toc = repository.getTableOfContents(resolvedExtId, novelUrl)
                    if (toc.isNotEmpty()) {
                        _chapters.value = toc
                        repository.cacheChapters(novelUrl, toc)
                        android.util.Log.d("NovelDetailVM", "Remote TOC loaded: ${toc.size} chapters")
                        startTranslatingChapters(resolvedExtId)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("NovelDetailVM", "Failed to fetch TOC (non-fatal if cached): ${e.message}")
                    if (_chapters.value.isEmpty()) {
                        _error.value = "Lỗi: ${e.localizedMessage ?: e.message ?: "Không thể tải dữ liệu truyện. Vui lòng thử lại!"}"
                    }
                }
            }
            
            if (_chapters.value.isEmpty() && _error.value == null && _novel.value == null) {
                _error.value = "Danh sách chương trống hoặc lỗi phân tích dữ liệu nguồn!"
            }

            val finalNovel = _novel.value
            if (finalNovel != null) {
                safeLoadAuthorNovels(resolvedExtId, finalNovel, novelUrl)
                checkCommentsSupport(resolvedExtId)
                loadComments(resolvedExtId, novelUrl)
            }
            
            _isLoading.value = false
            android.util.Log.d("NovelDetailVM", "loadDetail END: novel=${_novel.value?.title}, chapters=${_chapters.value.size}, error=${_error.value}")
        }
    }

    /**
     * Load truyện cùng tác giả — chỉ khi extension có search script.
     * VBook gốc dùng suggests từ detail response, nhưng chúng ta gọi search API riêng.
     * Phải kiểm tra script tồn tại để tránh lỗi vô nghĩa cho extension không hỗ trợ.
     */
    private fun safeLoadAuthorNovels(extensionId: String, novel: Novel?, novelUrl: String) {
        if (novel == null || novel.author.isBlank()) return
        viewModelScope.launch {
            try {
                val ext = repository.getExtension(extensionId)
                val hasSearchScript = ext?.pluginJson?.script?.containsKey(ScriptType.SEARCH.key) == true
                if (hasSearchScript) {
                    loadAuthorNovels(extensionId, novel.author, novelUrl)
                }
            } catch (e: Exception) {
                android.util.Log.w("NovelDetailVM", "Failed to check search script support: ${e.message}")
            }
        }
    }

    fun loadAuthorNovels(extensionId: String, author: String, currentNovelUrl: String) {
        viewModelScope.launch {
            _isAuthorNovelsLoading.value = true
            try {
                val result = repository.searchNovels(extensionId, author)
                // Lấy tối đa 10 truyện cùng tác giả (loại trừ truyện hiện tại)
                val filtered = result.novels.filter { it.url != currentNovelUrl }.take(10)
                _authorNovels.value = filtered
            } catch (e: Exception) {
                android.util.Log.e("NovelDetailVM", "Failed to load author novels: ${e.message}")
            } finally {
                _isAuthorNovelsLoading.value = false
            }
        }
    }

    fun saveNovelMetadata(novel: Novel) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            repository.updateNovelMetadata(novel)
        }
    }

    fun checkCommentsSupport(extensionId: String) {
        viewModelScope.launch {
            val extension = repository.getExtension(extensionId)
            _hasCommentsScript.value = extension?.pluginJson?.script?.containsKey(ScriptType.TRACK.key) == true
        }
    }

    fun loadComments(extensionId: String, novelUrl: String, isLoadMore: Boolean = false) {
        if (_isCommentsLoading.value) return
        val page = if (isLoadMore) _nextCommentPage.value else ""
        if (isLoadMore && page.isBlank()) return

        viewModelScope.launch {
            _isCommentsLoading.value = true
            try {
                val commentPage = repository.getComments(extensionId, novelUrl, page)
                if (commentPage != null) {
                    if (isLoadMore) {
                        _comments.value = _comments.value + commentPage.comments
                    } else {
                        _comments.value = commentPage.comments
                    }
                    _nextCommentPage.value = commentPage.nextPage
                }
            } catch (e: Exception) {
                android.util.Log.e("NovelDetailVM", "Failed to load comments: ${e.message}")
            } finally {
                _isCommentsLoading.value = false
            }
        }
    }

    fun toggleLibrary(novel: Novel) {
        viewModelScope.launch {
            if (_isInLibrary.value) {
                repository.removeFromLibrary(novel.url)
                _isInLibrary.value = false
            } else {
                repository.addToLibrary(novel)
                _isInLibrary.value = true
            }
        }
    }

    private fun startTranslatingChapters(extensionId: String) {
        val prefs = repository.context.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE)
        val globalAutoTranslate = prefs.getBoolean("translation_auto_translate", true)
        val translationMode = prefs.getString("ext_translation_mode_$extensionId", "Gốc") ?: "Gốc"
        
        if (!globalAutoTranslate || translationMode == "Gốc") {
            return
        }
        
        translationJob?.cancel()
        translationJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            kotlinx.coroutines.delay(1000)
            
            val currentList = _chapters.value.toMutableList()
            val updates = mutableListOf<Chapter>()
            val dbUpdates = mutableListOf<Pair<String, String>>()
            
            var i = 0
            var hasChanges = false
            
            while (isActive && i < currentList.size) {
                val chapter = currentList[i]
                val rawTitle = chapter.title
                
                if (com.nam.novelreader.util.QuickTranslateEngine.hasChinese(rawTitle)) {
                    val translated = com.nam.novelreader.util.QuickTranslateEngine.translate(repository.context, rawTitle)
                    if (translated != rawTitle) {
                        currentList[i] = chapter.copy(title = translated)
                        updates.add(currentList[i])
                        dbUpdates.add(Pair(chapter.url, translated))
                        hasChanges = true
                    }
                }
                
                // Gom lô cập nhật UI và DB mỗi 30 chương hoặc khi đến chương cuối cùng
                if (updates.size >= 30 || (i == currentList.size - 1 && hasChanges)) {
                    val updatedList = currentList.toList()
                    _chapters.value = updatedList
                    
                    val dbUpdatesCopy = dbUpdates.toList()
                    dbUpdates.clear()
                    updates.clear()
                    
                    // Ghi vào DB chạy ngầm trên IO thread để tránh block UI
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        for (update in dbUpdatesCopy) {
                            repository.updateChapterTitle(update.first, update.second)
                        }
                    }
                }
                
                i++
                kotlinx.coroutines.delay(15) // delay ngắn để dịch mượt mà
            }
        }
    }

    override fun onCleared() {
        translationJob?.cancel()
        loadDetailJob?.cancel()
        super.onCleared()
    }
}

/**
 * NovelDetailScreen — giống VBook:
 * - Blurred cover background
 * - Cover image centered
 * - Title, Author, Source badge
 * - Genre chips
 * - Giới thiệu (expandable)
 * - Mục lục (chapter count + search + list)
 * - FABs: bookmark, history, play
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NovelDetailScreen(
    extensionId: String,
    novelUrl: String,
    isOffline: Boolean = false,
    navController: NavHostController,
    viewModel: NovelDetailViewModel = hiltViewModel(),
) {
    val novel by viewModel.novel.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isInLibrary by viewModel.isInLibrary.collectAsStateWithLifecycle()
    val isFollowed by viewModel.isFollowed.collectAsStateWithLifecycle()
    val errorMsg by viewModel.error.collectAsStateWithLifecycle()
    val extensionIconFile by viewModel.extensionIconFile.collectAsStateWithLifecycle()
    val extensionLocalPath by viewModel.extensionLocalPath.collectAsStateWithLifecycle()
    var descExpanded by remember { mutableStateOf(false) }
    var chapterSearch by remember { mutableStateOf("") }
    val context = LocalContext.current
    var showDownloadDialog by remember { mutableStateOf(false) }
    var showFollowDialog by remember { mutableStateOf(false) }
    var isChaptersExpanded by remember { mutableStateOf(false) }

    val comments by viewModel.comments.collectAsStateWithLifecycle()
    val isCommentsLoading by viewModel.isCommentsLoading.collectAsStateWithLifecycle()
    val nextCommentPage by viewModel.nextCommentPage.collectAsStateWithLifecycle()
    val hasCommentsScript by viewModel.hasCommentsScript.collectAsStateWithLifecycle()
    val authorNovels by viewModel.authorNovels.collectAsStateWithLifecycle()
    val uniqueAuthorNovels = remember(authorNovels) { authorNovels.distinctBy { it.url } }
    val isAuthorNovelsLoading by viewModel.isAuthorNovelsLoading.collectAsStateWithLifecycle()

    var isAscending by remember { mutableStateOf(true) }
    var showMenuDropdown by remember { mutableStateOf(false) }

    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val themeSwitchThumbChecked = com.nam.novelreader.feature.components.VBookTheme.switchThumbCheckedColor()
    val themeSwitchTrackChecked = com.nam.novelreader.feature.components.VBookTheme.switchTrackCheckedColor()
    val themeSwitchThumbUnchecked = com.nam.novelreader.feature.components.VBookTheme.switchThumbUncheckedColor()
    val themeSwitchTrackUnchecked = com.nam.novelreader.feature.components.VBookTheme.switchTrackUncheckedColor()

    val tocCardIndex = remember(novel, uniqueAuthorNovels) {
        var index = 2 // Hero Section (0) + Novel Info (1)
        val currentNovel = novel
        if (currentNovel != null) {
            if (currentNovel.genres.isNotEmpty()) {
                index++
            }
            index++ // Card Thông tin
            if (currentNovel.description.isNotBlank()) {
                index++
            }
            if (uniqueAuthorNovels.isNotEmpty()) {
                index++
            }
            if (currentNovel.suggests.isNotEmpty()) {
                index++
            }
        }
        index
    }

    val filteredChapters = remember(chapters, chapterSearch, isAscending, isOffline) {
        val baseList = if (isOffline) chapters.filter { it.isDownloaded } else chapters
        val list = if (chapterSearch.isBlank()) baseList
        else baseList.filter { it.title.contains(chapterSearch, ignoreCase = true) }
        val sorted = if (isAscending) list else list.reversed()
        sorted.distinctBy { it.url }
    }

    val displayChapters = remember(filteredChapters, isChaptersExpanded, chapterSearch) {
        if (isChaptersExpanded || filteredChapters.size <= 50 || chapterSearch.isNotBlank()) {
            filteredChapters
        } else {
            filteredChapters.take(50)
        }
    }

    val captchaPassedFlow = navController.currentBackStackEntry?.savedStateHandle?.getStateFlow("captcha_passed", false)
    val captchaPassed by captchaPassedFlow?.collectAsState() ?: remember { mutableStateOf(false) }

    LaunchedEffect(captchaPassed) {
        if (captchaPassed) {
            viewModel.loadDetail(extensionId, novelUrl, forceRefresh = true, isOffline = isOffline)
            navController.currentBackStackEntry?.savedStateHandle?.set("captcha_passed", false)
        }
    }

    LaunchedEffect(extensionId, novelUrl, isOffline) {
        // Trì hoãn 250ms để nhường CPU cho slide transition chạy mượt mà trước khi load dữ liệu nặng
        kotlinx.coroutines.delay(250)
        viewModel.loadDetail(extensionId, novelUrl, isOffline = isOffline)
    }

    val currentNovel = novel

    // FIX: Chỉ hiện loading spinner khi CHƯA có data nào — tránh "bụp xoay" khi đã có cache
    val hasAnyData = currentNovel != null || chapters.isNotEmpty()
    if (!hasAnyData && errorMsg == null) {
        // Trạng thái initial hoặc đang loading lần đầu — chưa có bất kỳ data nào
        Box(modifier = Modifier.fillMaxSize().background(com.nam.novelreader.feature.components.VBookTheme.backgroundColor()), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    if (currentNovel == null) {
        Box(modifier = Modifier.fillMaxSize().background(com.nam.novelreader.feature.components.VBookTheme.backgroundColor()), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                Icon(Icons.Filled.Warning, contentDescription = "Lỗi", modifier = Modifier.size(48.dp), tint = com.nam.novelreader.feature.components.VBookTheme.subTextColor())
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = errorMsg ?: "Không thể tải thông tin truyện.",
                    color = com.nam.novelreader.feature.components.VBookTheme.subTextColor(),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { viewModel.loadDetail(extensionId, novelUrl) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Thử lại", color = com.nam.novelreader.feature.components.VBookTheme.backgroundColor())
                    }
                    Button(
                        onClick = { navController.navigate(Routes.webview(novelUrl, extensionId)) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Trang nguồn", color = com.nam.novelreader.feature.components.VBookTheme.backgroundColor())
                    }
                    Button(
                        onClick = { navController.popBackStack() },
                        colors = ButtonDefaults.buttonColors(containerColor = com.nam.novelreader.feature.components.VBookTheme.cardColor()),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Quay lại", color = com.nam.novelreader.feature.components.VBookTheme.textColor())
                    }
                }
            }
        }
        return
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = com.nam.novelreader.feature.components.VBookTheme.backgroundColor(),
        contentColor = com.nam.novelreader.feature.components.VBookTheme.textColor()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(state = lazyListState, modifier = Modifier.fillMaxSize()) {
            // === Hero Section: blurred background + cover ===
            item(key = "hero") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp),
                ) {
                    val context = LocalContext.current
                    val imageRequest = com.nam.novelreader.feature.components.buildNovelImageRequest(currentNovel)

                    // Blurred background image
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer()
                            .blur(10.dp),
                        contentScale = ContentScale.Crop,
                    )
                    // Gradient overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.15f),
                                        Color.Transparent,
                                        com.nam.novelreader.feature.components.VBookTheme.backgroundColor().copy(alpha = 0.85f),
                                        com.nam.novelreader.feature.components.VBookTheme.backgroundColor(),
                                    ),
                                    startY = 0f,
                                    endY = Float.POSITIVE_INFINITY,
                                )
                            )
                    )

                    // Top bar overlay
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.4f),
                            ) {
                                Icon(
                                    Icons.Filled.ArrowBack,
                                    "Back",
                                    tint = Color.White,
                                    modifier = Modifier.padding(8.dp),
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // VBook style Translation button & Dialog
                            var showTranslationDialog by remember { mutableStateOf(false) }
                            val prefs = remember { context.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE) }
                            var translationMode by remember(extensionId) {
                                mutableStateOf(prefs.getString("ext_translation_mode_$extensionId", "Gốc") ?: "Gốc")
                            }

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color.Black.copy(alpha = 0.4f),
                                modifier = Modifier.clickable { showTranslationDialog = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = if (translationMode == "Gốc") "Gốc" else "Dịch",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Icon(
                                        imageVector = Icons.Filled.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

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

                                Dialog(onDismissRequest = { showTranslationDialog = false }) {
                                    Box(
                                        modifier = Modifier
                                            .width(360.dp)
                                            .clip(RoundedCornerShape(28.dp))
                                            .background(com.nam.novelreader.feature.components.VBookTheme.cardColor())
                                            .padding(20.dp)
                                    ) {
                                        Column {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                IconButton(onClick = { showTranslationDialog = false }) {
                                                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = com.nam.novelreader.feature.components.VBookTheme.textColor())
                                                }
                                                Text(
                                                    text = "Dịch",
                                                    fontSize = 20.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = com.nam.novelreader.feature.components.VBookTheme.textColor()
                                                )
                                                IconButton(onClick = {
                                                    showTranslationDialog = false
                                                    navController.navigate(Routes.TRANSLATION_SETTINGS)
                                                }) {
                                                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = com.nam.novelreader.feature.components.VBookTheme.textColor())
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
                                                            .background(com.nam.novelreader.feature.components.VBookTheme.backgroundColor())
                                                            .clickable { sourceMenuExpanded = true }
                                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                                        horizontalArrangement = Arrangement.Center,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(text = tempSource, color = com.nam.novelreader.feature.components.VBookTheme.textColor(), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Icon(Icons.Filled.KeyboardArrowDown, null, tint = com.nam.novelreader.feature.components.VBookTheme.textColor(), modifier = Modifier.size(16.dp))
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
                                                            .background(com.nam.novelreader.feature.components.VBookTheme.backgroundColor())
                                                            .clickable { targetMenuExpanded = true }
                                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                                        horizontalArrangement = Arrangement.Center,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(text = tempTarget, color = com.nam.novelreader.feature.components.VBookTheme.textColor(), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Icon(Icons.Filled.KeyboardArrowDown, null, tint = com.nam.novelreader.feature.components.VBookTheme.textColor(), modifier = Modifier.size(16.dp))
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
                                                            .background(com.nam.novelreader.feature.components.VBookTheme.backgroundColor())
                                                            .clickable { engineMenuExpanded = true }
                                                            .padding(horizontal = 10.dp, vertical = 10.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(20.dp)
                                                                .background(com.nam.novelreader.feature.components.VBookTheme.primaryColor(), CircleShape),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Filled.MenuBook,
                                                                contentDescription = null,
                                                                tint = com.nam.novelreader.feature.components.VBookTheme.backgroundColor(),
                                                                modifier = Modifier.size(12.dp)
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(text = tempEngine, color = com.nam.novelreader.feature.components.VBookTheme.textColor(), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                                        Icon(Icons.Filled.KeyboardArrowDown, null, tint = com.nam.novelreader.feature.components.VBookTheme.textColor(), modifier = Modifier.size(16.dp))
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
                                                                .background(com.nam.novelreader.feature.components.VBookTheme.backgroundColor())
                                                            .clickable { scopeMenuExpanded = true }
                                                            .padding(horizontal = 8.dp, vertical = 10.dp),
                                                            horizontalArrangement = Arrangement.Center,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(text = tempScope, color = com.nam.novelreader.feature.components.VBookTheme.textColor(), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                            Spacer(modifier = Modifier.width(2.dp))
                                                            Icon(Icons.Filled.KeyboardArrowDown, null, tint = com.nam.novelreader.feature.components.VBookTheme.textColor(), modifier = Modifier.size(14.dp))
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
                                                            viewModel.loadDetail(extensionId, novelUrl, forceRefresh = true)
                                                        },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = com.nam.novelreader.feature.components.VBookTheme.primaryColor(),
                                                            contentColor = com.nam.novelreader.feature.components.VBookTheme.backgroundColor()
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

                            // Search button - scrolls to TOC
                            IconButton(onClick = {
                                coroutineScope.launch {
                                    lazyListState.animateScrollToItem(tocCardIndex)
                                }
                            }) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.4f),
                                ) {
                                    Icon(
                                        Icons.Filled.Search,
                                        "Tìm kiếm chương",
                                        tint = Color.White,
                                        modifier = Modifier.padding(8.dp),
                                    )
                                }
                            }

                            Box {
                                IconButton(onClick = { showMenuDropdown = true }) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.Black.copy(alpha = 0.4f),
                                    ) {
                                        Icon(
                                            Icons.Filled.MoreVert,
                                            "Menu",
                                            tint = Color.White,
                                            modifier = Modifier.padding(8.dp),
                                        )
                                    }
                                }
                                DropdownMenu(
                                    expanded = showMenuDropdown,
                                    onDismissRequest = { showMenuDropdown = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(if (isInLibrary) "Xóa khỏi kệ sách" else "Thêm vào kệ sách") },
                                        onClick = {
                                            showMenuDropdown = false
                                            viewModel.toggleLibrary(currentNovel)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (isFollowed) "Hủy theo dõi" else "Theo dõi") },
                                        onClick = {
                                            showMenuDropdown = false
                                            viewModel.toggleFollow(extensionId.ifBlank { currentNovel.extensionId }, currentNovel.url, currentNovel.title)
                                        }
                                    )
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("Mở trong trình duyệt") },
                                        onClick = {
                                            showMenuDropdown = false
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(currentNovel.url))
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                android.widget.Toast.makeText(context, "Không thể mở liên kết", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Chia sẻ liên kết") },
                                        onClick = {
                                            showMenuDropdown = false
                                            try {
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_TEXT, "Đọc truyện ${currentNovel.title} tại: ${currentNovel.url}")
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, "Chia sẻ truyện"))
                                            } catch (e: Exception) {
                                                android.widget.Toast.makeText(context, "Không thể chia sẻ", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Sao chép liên kết") },
                                        onClick = {
                                            showMenuDropdown = false
                                            try {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                val clip = android.content.ClipData.newPlainText("Novel Link", currentNovel.url)
                                                clipboard.setPrimaryClip(clip)
                                                android.widget.Toast.makeText(context, "Đã sao chép liên kết truyện", android.widget.Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) {
                                                android.widget.Toast.makeText(context, "Không thể sao chép", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Centered cover image
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = currentNovel.title,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = 40.dp)
                            .width(140.dp)
                            .aspectRatio(0.67f)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
            }

            // === Novel Info ===
            item(key = "info") {
                Spacer(modifier = Modifier.height(48.dp))
                // Title
                Text(
                    text = currentNovel.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = com.nam.novelreader.feature.components.VBookTheme.textColor(),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                )

                // Author
                if (currentNovel.author.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = com.nam.novelreader.feature.components.VBookTheme.cardColor(),
                            modifier = Modifier.bounceClick {
                                navController.navigate(Routes.search(extensionId, currentNovel.author))
                            }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Person,
                                    contentDescription = null,
                                    tint = com.nam.novelreader.feature.components.VBookTheme.primaryColor(),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = currentNovel.author,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = com.nam.novelreader.feature.components.VBookTheme.textColor(),
                                )
                            }
                        }
                    }
                }

                // Source badge & Status (horizontal layout)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(com.nam.novelreader.feature.components.VBookTheme.cardColor())
                            .bounceClick { navController.navigate(Routes.webview(novelUrl, extensionId)) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isSupportedExtIcon = extensionIconFile != null &&
                            extensionIconFile!!.extension.lowercase() in listOf("png", "jpg", "jpeg", "webp", "bmp")
                        if (isSupportedExtIcon) {
                            AsyncImage(
                                model = extensionIconFile,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp).clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = extensionId.replace("-", " ").replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            color = com.nam.novelreader.feature.components.VBookTheme.textColor().copy(alpha = 0.8f)
                        )
                    }
                    
                    val statusText = currentNovel.status.ifBlank { "ĐANG RA" }
                    if (statusText.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        SuggestionChip(
                            onClick = {},
                            label = {
                                Text(
                                    statusText.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = Color(0xFFFFB74D).copy(alpha = 0.2f),
                                labelColor = Color(0xFFFF9800)
                            ),
                            border = null,
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // === Genres ===
            if (currentNovel.genres.isNotEmpty()) {
                item(key = "genres") {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        currentNovel.genres.forEach { genre ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(com.nam.novelreader.feature.components.VBookTheme.cardColor())
                                    .bounceClick { 
                                        if (genre.script.isNotBlank()) {
                                            navController.navigate(Routes.listNovel(extensionId, genre.name, genre.script, genre.input))
                                        } else {
                                            navController.navigate(Routes.search(extensionId, genre.name))
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = genre.name,
                                    fontSize = 12.sp,
                                    color = com.nam.novelreader.feature.components.VBookTheme.subTextColor()
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // === Card Thông tin (giống VBook gốc) ===
            item(key = "info_card") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = com.nam.novelreader.feature.components.VBookTheme.cardColor(),
                        contentColor = com.nam.novelreader.feature.components.VBookTheme.textColor()
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Thông tin",
                            style = MaterialTheme.typography.titleMedium,
                            color = com.nam.novelreader.feature.components.VBookTheme.textColor(),
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        val context = LocalContext.current
                        var hanVietTitle by remember(currentNovel.originalTitle) {
                            mutableStateOf(
                                if (currentNovel.originalTitle.isNotBlank() && !com.nam.novelreader.util.QuickTranslateEngine.hasChinese(currentNovel.originalTitle)) {
                                    currentNovel.originalTitle
                                } else {
                                    ""
                                }
                            )
                        }

                        LaunchedEffect(currentNovel.originalTitle) {
                            if (currentNovel.originalTitle.isNotBlank() && com.nam.novelreader.util.QuickTranslateEngine.hasChinese(currentNovel.originalTitle)) {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                                    val translated = com.nam.novelreader.util.QuickTranslateEngine.translate(context, currentNovel.originalTitle, "hanviet")
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        hanVietTitle = translated
                                    }
                                }
                            }
                        }

                        InfoRow(label = "Hán Việt", value = hanVietTitle.ifBlank { "Chưa rõ" })
                        InfoRow(label = "Tác giả", value = currentNovel.author.ifBlank { "Chưa rõ" })
                        InfoRow(label = "Trạng thái", value = currentNovel.status.ifBlank { "Còn tiếp" })
                        
                        val latestChap = currentNovel.latestChapter ?: ""
                        if (latestChap.isNotBlank()) {
                            InfoRow(label = "Mới nhất", value = latestChap)
                        }

                        // Nhãn: hiển thị genres nối bằng dấu /
                        if (currentNovel.genres.isNotEmpty()) {
                            InfoRow(label = "Nhãn", value = currentNovel.genres.joinToString(" / ") { it.name })
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // === Giới thiệu ===
            if (currentNovel.description.isNotBlank()) {
                item(key = "description") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = com.nam.novelreader.feature.components.VBookTheme.cardColor(),
                            contentColor = com.nam.novelreader.feature.components.VBookTheme.textColor()
                        ),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Giới thiệu",
                                style = MaterialTheme.typography.titleMedium,
                                color = com.nam.novelreader.feature.components.VBookTheme.textColor(),
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                currentNovel.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = if (descExpanded) Int.MAX_VALUE else 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                            TextButton(
                                onClick = { descExpanded = !descExpanded },
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            ) {
                                Text(if (descExpanded) "Thu gọn" else "Xem thêm")
                                Icon(
                                    if (descExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // === Cùng tác giả ===
            if (uniqueAuthorNovels.isNotEmpty()) {
                item(key = "author_novels") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Cùng tác giả",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            IconButton(onClick = {
                                navController.navigate(Routes.search(extensionId, currentNovel.author))
                            }) {
                                Icon(
                                    imageVector = Icons.Filled.ArrowForward,
                                    contentDescription = "Xem tất cả",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(uniqueAuthorNovels, key = { it.url }) { novel ->
                                AuthorNovelItem(novel) {
                                    viewModel.saveNovelMetadata(novel)
                                    navController.navigate(Routes.detail(extensionId, novel.url))
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // === Suggests ===
            if (currentNovel.suggests.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Text(
                            "Khám phá thêm",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            currentNovel.suggests.forEach { suggest ->
                                OutlinedButton(
                                    onClick = { 
                                        if (suggest.script.isNotBlank()) {
                                            navController.navigate(Routes.listNovel(extensionId, suggest.name, suggest.script, suggest.input))
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Text(suggest.name)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // === Mục lục ===
            item(key = "toc_header") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = com.nam.novelreader.feature.components.VBookTheme.cardColor(),
                        contentColor = com.nam.novelreader.feature.components.VBookTheme.textColor()
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Mục lục",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = com.nam.novelreader.feature.components.VBookTheme.textColor(),
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "(${chapters.size} chương)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { showDownloadDialog = true }) {
                                    Icon(Icons.Filled.CloudDownload, "Tải offline", tint = com.nam.novelreader.feature.components.VBookTheme.primaryColor())
                                }
                                IconButton(onClick = { viewModel.loadDetail(extensionId, novelUrl, forceRefresh = true, isOffline = isOffline) }) {
                                    Icon(Icons.Filled.Refresh, "Làm mới mục lục", tint = com.nam.novelreader.feature.components.VBookTheme.textColor())
                                }
                                IconButton(onClick = { isAscending = !isAscending }) {
                                    Icon(
                                        Icons.Filled.SwapVert, 
                                        "Sắp xếp", 
                                        tint = if (!isAscending) MaterialTheme.colorScheme.primary else com.nam.novelreader.feature.components.VBookTheme.textColor()
                                    )
                                }
                            }
                        }

                        // Search chapters
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(com.nam.novelreader.feature.components.VBookTheme.cardColor())
                                .border(1.dp, com.nam.novelreader.feature.components.VBookTheme.subTextColor().copy(alpha = 0.2f), RoundedCornerShape(24.dp)),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            androidx.compose.foundation.text.BasicTextField(
                                value = chapterSearch,
                                onValueChange = { chapterSearch = it },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = com.nam.novelreader.feature.components.VBookTheme.textColor()
                                ),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(start = 48.dp, end = 16.dp),
                                decorationBox = { innerTextField ->
                                    if (chapterSearch.isEmpty()) {
                                        Text(
                                            text = "Tìm kiếm ${chapters.size} chương",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = com.nam.novelreader.feature.components.VBookTheme.subTextColor()
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                                tint = com.nam.novelreader.feature.components.VBookTheme.subTextColor(),
                                modifier = Modifier
                                    .padding(start = 16.dp)
                                    .size(20.dp)
                            )
                            if (chapterSearch.isNotEmpty()) {
                                IconButton(
                                    onClick = { chapterSearch = "" },
                                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Clear search",
                                        tint = com.nam.novelreader.feature.components.VBookTheme.subTextColor(),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Chapter list
            if (filteredChapters.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = com.nam.novelreader.feature.components.VBookTheme.primaryColor(),
                                modifier = Modifier.size(36.dp),
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Đang tải danh sách chương...",
                                color = com.nam.novelreader.feature.components.VBookTheme.subTextColor(),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Text(
                                text = errorMsg ?: "Danh sách chương trống.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.loadDetail(extensionId, novelUrl) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Tải lại danh sách chương", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                itemsIndexed(
                    items = displayChapters,
                    key = { _, chapter -> chapter.url }
                ) { index, chapter ->
                    val isLastRead = chapter.url == currentNovel.lastReadChapterUrl
                    ListItem(
                        headlineContent = {
                            Text(
                                chapter.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isLastRead) com.nam.novelreader.feature.components.VBookTheme.primaryColor() else com.nam.novelreader.feature.components.VBookTheme.textColor(),
                                fontWeight = if (isLastRead) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isLastRead) {
                                    Text(
                                        "Đang đọc",
                                        color = com.nam.novelreader.feature.components.VBookTheme.primaryColor(),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                // Nút tải từng chương
                                IconButton(
                                    onClick = {
                                        val intent = Intent(context, DownloadService::class.java).apply {
                                            action = DownloadService.ACTION_DOWNLOAD
                                            putExtra(DownloadService.EXTRA_EXTENSION_ID, extensionId)
                                            putExtra(DownloadService.EXTRA_NOVEL_URL, novelUrl)
                                            putExtra(DownloadService.EXTRA_NOVEL_TITLE, novel?.title ?: "")
                                            putExtra(DownloadService.EXTRA_COVER_URL, novel?.cover ?: "")
                                            putStringArrayListExtra(DownloadService.EXTRA_CHAPTER_URLS, arrayListOf(chapter.url))
                                        }
                                        context.startService(intent)
                                        android.widget.Toast.makeText(context, "Đang tải: ${chapter.title}", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (chapter.isDownloaded) Icons.Filled.OfflinePin else Icons.Filled.CloudDownload,
                                        contentDescription = if (chapter.isDownloaded) "Đã tải" else "Tải chương",
                                        tint = if (chapter.isDownloaded) com.nam.novelreader.feature.components.VBookTheme.primaryColor() else com.nam.novelreader.feature.components.VBookTheme.subTextColor(),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                            headlineColor = com.nam.novelreader.feature.components.VBookTheme.textColor(),
                            supportingColor = com.nam.novelreader.feature.components.VBookTheme.subTextColor()
                        ),
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(com.nam.novelreader.feature.components.VBookTheme.cardColor().copy(alpha = 0.5f))
                            .bounceClick {
                                val route = when (currentNovel.type) {
                                    ContentType.COMIC -> Routes.comicReader(extensionId, currentNovel.url, chapter.url, isOffline = isOffline)
                                    ContentType.VIDEO -> Routes.videoReader(extensionId, currentNovel.url, chapter.url, isOffline = isOffline)
                                    else -> Routes.textReader(extensionId, currentNovel.url, chapter.url, isOffline = isOffline)
                                }
                                navController.navigate(route)
                            },
                    )
                }

                if (!isChaptersExpanded && filteredChapters.size > 50 && chapterSearch.isBlank()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            TextButton(onClick = { isChaptersExpanded = true }) {
                                Text("Xem tất cả (${filteredChapters.size} chương)", color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Filled.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            // === Bình luận ===
            if (hasCommentsScript) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = com.nam.novelreader.feature.components.VBookTheme.cardColor(),
                            contentColor = com.nam.novelreader.feature.components.VBookTheme.textColor()
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Bình luận",
                                style = MaterialTheme.typography.titleMedium,
                                color = com.nam.novelreader.feature.components.VBookTheme.textColor(),
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            if (comments.isEmpty() && isCommentsLoading) {
                                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            } else if (comments.isEmpty()) {
                                Text(
                                    text = "Chưa có bình luận nào.",
                                    color = com.nam.novelreader.feature.components.VBookTheme.subTextColor(),
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(vertical = 16.dp)
                                )
                            } else {
                                comments.forEachIndexed { index, comment ->
                                    CommentItem(comment)
                                    if (index < comments.lastIndex) {
                                        HorizontalDivider(
                                            color = if (com.nam.novelreader.feature.components.VBookTheme.isDarkTheme()) Color(0xFF332A22) else Color(0xFFE8E2DA),
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }
                                }
                                
                                if (nextCommentPage.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    if (isCommentsLoading) {
                                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                        }
                                    } else {
                                        TextButton(
                                            onClick = { viewModel.loadComments(extensionId, novelUrl, isLoadMore = true) },
                                            modifier = Modifier.align(Alignment.CenterHorizontally)
                                        ) {
                                            Text("Tải thêm bình luận")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }

        // === FABs ===
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // TOC FAB
            SmallFloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        lazyListState.animateScrollToItem(tocCardIndex)
                    }
                },
                containerColor = com.nam.novelreader.feature.components.VBookTheme.cardColor(),
                contentColor = com.nam.novelreader.feature.components.VBookTheme.textColor(),
            ) {
                Icon(
                    Icons.Filled.FormatListBulleted,
                    "Mục lục",
                    tint = com.nam.novelreader.feature.components.VBookTheme.primaryColor()
                )
            }
            // Follow/Notification FAB
            SmallFloatingActionButton(
                onClick = { showFollowDialog = true },
                containerColor = com.nam.novelreader.feature.components.VBookTheme.cardColor(),
                contentColor = if (isFollowed) com.nam.novelreader.feature.components.VBookTheme.primaryColor() else com.nam.novelreader.feature.components.VBookTheme.textColor(),
            ) {
                Icon(
                    Icons.Filled.PowerSettingsNew,
                    if (isFollowed) "Đang theo dõi" else "Theo dõi",
                    tint = if (isFollowed) com.nam.novelreader.feature.components.VBookTheme.primaryColor() else com.nam.novelreader.feature.components.VBookTheme.textColor()
                )
            }
            val hasRead = !currentNovel.lastReadChapterUrl.isNullOrBlank()
            // Play/Read FAB
            FloatingActionButton(
                onClick = {
                    val availableChapters = if (isOffline) chapters.filter { it.isDownloaded } else chapters
                    if (availableChapters.isNotEmpty()) {
                        val startUrl = currentNovel.lastReadChapterUrl?.takeIf { url ->
                            url.isNotBlank() && availableChapters.any { it.url == url }
                        } ?: availableChapters.first().url
                        val route = when (currentNovel.type) {
                            ContentType.COMIC -> Routes.comicReader(extensionId, currentNovel.url, startUrl, isOffline = isOffline)
                            ContentType.VIDEO -> Routes.videoReader(extensionId, currentNovel.url, startUrl, isOffline = isOffline)
                            else -> Routes.textReader(extensionId, currentNovel.url, startUrl, isOffline = isOffline)
                        }
                        navController.navigate(route)
                    }
                },
                containerColor = com.nam.novelreader.feature.components.VBookTheme.primaryColor(),
                contentColor = com.nam.novelreader.feature.components.VBookTheme.backgroundColor(),
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = if (hasRead) "Đọc tiếp" else "Bắt đầu đọc",
                    tint = com.nam.novelreader.feature.components.VBookTheme.backgroundColor()
                )
            }
        }

        if (showDownloadDialog) {
            var downloadStartChapter by remember { mutableStateOf("1") }
            var downloadEndChapter by remember { mutableStateOf(chapters.size.toString()) }
            val lastReadUrl = novel?.lastReadChapterUrl
            val lastReadChapterIndex = if (!lastReadUrl.isNullOrBlank()) {
                chapters.indexOfFirst { it.url == lastReadUrl }
            } else -1

            // Dùng plugin.json defaults nếu chưa có giá trị user-saved
            var downloadThreads by remember { mutableIntStateOf(viewModel.appPrefs.getExtParallelConnections(extensionId, extensionLocalPath)) }
            var downloadDelay by remember { mutableIntStateOf(viewModel.appPrefs.getExtConnectionInterval(extensionId, extensionLocalPath)) }
            var downloadTimeout by remember { mutableIntStateOf(viewModel.appPrefs.getExtConnectionTimeout(extensionId)) }

            val startDownloadAction = { intent: Intent ->
                // Lưu cài đặt của extension trước khi tải
                viewModel.appPrefs.setExtParallelConnections(extensionId, downloadThreads)
                viewModel.appPrefs.setExtConnectionInterval(extensionId, downloadDelay)
                viewModel.appPrefs.setExtConnectionTimeout(extensionId, downloadTimeout)
                
                context.startService(intent)
                android.widget.Toast.makeText(context, "Bắt đầu tải offline: ${novel?.title}...", android.widget.Toast.LENGTH_SHORT).show()
                showDownloadDialog = false
            }

            AlertDialog(
                onDismissRequest = { showDownloadDialog = false },
                containerColor = com.nam.novelreader.feature.components.VBookTheme.cardColor(),
                title = { Text("Tải chương offline", color = com.nam.novelreader.feature.components.VBookTheme.textColor(), fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Tổng số chương: ${chapters.size}", style = MaterialTheme.typography.bodyMedium, color = com.nam.novelreader.feature.components.VBookTheme.textColor())
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { viewModel.toggleLibrary(currentNovel) }
                        ) {
                            Checkbox(
                                checked = isInLibrary,
                                onCheckedChange = { viewModel.toggleLibrary(currentNovel) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = com.nam.novelreader.feature.components.VBookTheme.primaryColor(),
                                    uncheckedColor = com.nam.novelreader.feature.components.VBookTheme.subTextColor()
                                )
                            )
                            Text("Thêm vào kệ sách", color = com.nam.novelreader.feature.components.VBookTheme.textColor(), style = MaterialTheme.typography.bodyMedium)
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val count = 50.coerceAtMost(chapters.size)
                                    val urls = chapters.take(count).map { it.url }
                                    val intent = Intent(context, DownloadService::class.java).apply {
                                        action = DownloadService.ACTION_DOWNLOAD
                                        putExtra(DownloadService.EXTRA_EXTENSION_ID, extensionId)
                                        putExtra(DownloadService.EXTRA_NOVEL_URL, novelUrl)
                                        putExtra(DownloadService.EXTRA_NOVEL_TITLE, novel?.title ?: "")
                                        putExtra(DownloadService.EXTRA_COVER_URL, novel?.cover ?: "")
                                        putStringArrayListExtra(DownloadService.EXTRA_CHAPTER_URLS, ArrayList(urls))
                                    }
                                    startDownloadAction(intent)
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = com.nam.novelreader.feature.components.VBookTheme.primaryColor().copy(alpha = 0.8f),
                                    contentColor = com.nam.novelreader.feature.components.VBookTheme.backgroundColor()
                                )
                            ) {
                                Text("50 chương đầu")
                            }

                            Button(
                                onClick = {
                                    val count = 100.coerceAtMost(chapters.size)
                                    val urls = chapters.take(count).map { it.url }
                                    val intent = Intent(context, DownloadService::class.java).apply {
                                        action = DownloadService.ACTION_DOWNLOAD
                                        putExtra(DownloadService.EXTRA_EXTENSION_ID, extensionId)
                                        putExtra(DownloadService.EXTRA_NOVEL_URL, novelUrl)
                                        putExtra(DownloadService.EXTRA_NOVEL_TITLE, novel?.title ?: "")
                                        putExtra(DownloadService.EXTRA_COVER_URL, novel?.cover ?: "")
                                        putStringArrayListExtra(DownloadService.EXTRA_CHAPTER_URLS, ArrayList(urls))
                                    }
                                    startDownloadAction(intent)
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = com.nam.novelreader.feature.components.VBookTheme.primaryColor().copy(alpha = 0.8f),
                                    contentColor = com.nam.novelreader.feature.components.VBookTheme.backgroundColor()
                                )
                            ) {
                                Text("100 chương đầu")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val urls = chapters.map { it.url }
                                    val intent = Intent(context, DownloadService::class.java).apply {
                                        action = DownloadService.ACTION_DOWNLOAD
                                        putExtra(DownloadService.EXTRA_EXTENSION_ID, extensionId)
                                        putExtra(DownloadService.EXTRA_NOVEL_URL, novelUrl)
                                        putExtra(DownloadService.EXTRA_NOVEL_TITLE, novel?.title ?: "")
                                        putExtra(DownloadService.EXTRA_COVER_URL, novel?.cover ?: "")
                                        putStringArrayListExtra(DownloadService.EXTRA_CHAPTER_URLS, ArrayList(urls))
                                    }
                                    startDownloadAction(intent)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = com.nam.novelreader.feature.components.VBookTheme.primaryColor(),
                                    contentColor = com.nam.novelreader.feature.components.VBookTheme.backgroundColor()
                                )
                            ) {
                                Text("Tải toàn bộ")
                            }
                        }

                        if (lastReadChapterIndex >= 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val intent = Intent(context, DownloadService::class.java).apply {
                                            action = DownloadService.ACTION_DOWNLOAD
                                            putExtra(DownloadService.EXTRA_EXTENSION_ID, extensionId)
                                            putExtra(DownloadService.EXTRA_NOVEL_URL, novelUrl)
                                            putExtra(DownloadService.EXTRA_NOVEL_TITLE, novel?.title ?: "")
                                            putExtra(DownloadService.EXTRA_COVER_URL, novel?.cover ?: "")
                                            putExtra(DownloadService.EXTRA_FROM_LAST_READ, true)
                                        }
                                        startDownloadAction(intent)
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = com.nam.novelreader.feature.components.VBookTheme.primaryColor().copy(alpha = 0.8f),
                                        contentColor = com.nam.novelreader.feature.components.VBookTheme.backgroundColor()
                                    )
                                ) {
                                    val currentChapNum = lastReadChapterIndex + 1
                                    Text("Từ chương hiện tại ($currentChapNum)")
                                }

                                Button(
                                    onClick = {
                                        val intent = Intent(context, DownloadService::class.java).apply {
                                            action = DownloadService.ACTION_DOWNLOAD
                                            putExtra(DownloadService.EXTRA_EXTENSION_ID, extensionId)
                                            putExtra(DownloadService.EXTRA_NOVEL_URL, novelUrl)
                                            putExtra(DownloadService.EXTRA_NOVEL_TITLE, novel?.title ?: "")
                                            putExtra(DownloadService.EXTRA_COVER_URL, novel?.cover ?: "")
                                        }
                                        startDownloadAction(intent)
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = com.nam.novelreader.feature.components.VBookTheme.primaryColor().copy(alpha = 0.8f),
                                        contentColor = com.nam.novelreader.feature.components.VBookTheme.backgroundColor()
                                    )
                                ) {
                                    Text("Chương chưa tải")
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val intent = Intent(context, DownloadService::class.java).apply {
                                            action = DownloadService.ACTION_DOWNLOAD
                                            putExtra(DownloadService.EXTRA_EXTENSION_ID, extensionId)
                                            putExtra(DownloadService.EXTRA_NOVEL_URL, novelUrl)
                                            putExtra(DownloadService.EXTRA_NOVEL_TITLE, novel?.title ?: "")
                                            putExtra(DownloadService.EXTRA_COVER_URL, novel?.cover ?: "")
                                        }
                                        startDownloadAction(intent)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = com.nam.novelreader.feature.components.VBookTheme.primaryColor().copy(alpha = 0.8f),
                                        contentColor = com.nam.novelreader.feature.components.VBookTheme.backgroundColor()
                                    )
                                ) {
                                    Text("Tải các chương chưa ngoại tuyến")
                                }
                            }
                        }

                        HorizontalDivider(
                            color = if (com.nam.novelreader.feature.components.VBookTheme.isDarkTheme()) Color(0xFF332A22) else Color(0xFFE8E2DA),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        Text("Tùy chọn khoảng tải chương:", style = MaterialTheme.typography.bodySmall, color = com.nam.novelreader.feature.components.VBookTheme.subTextColor())

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = downloadStartChapter,
                                onValueChange = { downloadStartChapter = it },
                                label = { Text("Từ") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = com.nam.novelreader.feature.components.VBookTheme.primaryColor(),
                                    unfocusedBorderColor = com.nam.novelreader.feature.components.VBookTheme.subTextColor().copy(alpha = 0.5f),
                                    focusedLabelColor = com.nam.novelreader.feature.components.VBookTheme.primaryColor(),
                                    unfocusedLabelColor = com.nam.novelreader.feature.components.VBookTheme.subTextColor(),
                                    focusedTextColor = com.nam.novelreader.feature.components.VBookTheme.textColor(),
                                    unfocusedTextColor = com.nam.novelreader.feature.components.VBookTheme.textColor()
                                )
                            )
                            OutlinedTextField(
                                value = downloadEndChapter,
                                onValueChange = { downloadEndChapter = it },
                                label = { Text("Đến") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = com.nam.novelreader.feature.components.VBookTheme.primaryColor(),
                                    unfocusedBorderColor = com.nam.novelreader.feature.components.VBookTheme.subTextColor().copy(alpha = 0.5f),
                                    focusedLabelColor = com.nam.novelreader.feature.components.VBookTheme.primaryColor(),
                                    unfocusedLabelColor = com.nam.novelreader.feature.components.VBookTheme.subTextColor(),
                                    focusedTextColor = com.nam.novelreader.feature.components.VBookTheme.textColor(),
                                    unfocusedTextColor = com.nam.novelreader.feature.components.VBookTheme.textColor()
                                )
                            )
                        }

                        HorizontalDivider(
                            color = if (com.nam.novelreader.feature.components.VBookTheme.isDarkTheme()) Color(0xFF332A22) else Color(0xFFE8E2DA),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        Text("Cấu hình tốc độ tải tiện ích:", style = MaterialTheme.typography.bodySmall, color = com.nam.novelreader.feature.components.VBookTheme.subTextColor())

                        // Row for Parallel Connections (Threads)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Số luồng song song:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = com.nam.novelreader.feature.components.VBookTheme.textColor()
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val buttonColor = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = com.nam.novelreader.feature.components.VBookTheme.cardColor(),
                                    contentColor = com.nam.novelreader.feature.components.VBookTheme.textColor()
                                )
                                FilledTonalButton(
                                    onClick = { if (downloadThreads > 1) downloadThreads-- },
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.size(36.dp),
                                    colors = buttonColor,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Filled.Remove, "Giảm luồng", modifier = Modifier.size(18.dp))
                                }
                                Text(
                                    text = "$downloadThreads",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = com.nam.novelreader.feature.components.VBookTheme.textColor(),
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                                FilledTonalButton(
                                    onClick = { if (downloadThreads < 20) downloadThreads++ },
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.size(36.dp),
                                    colors = buttonColor,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Filled.Add, "Tăng luồng", modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        // Row for Connection Interval (Delay)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Giãn cách kết nối:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = com.nam.novelreader.feature.components.VBookTheme.textColor()
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val buttonColor = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = com.nam.novelreader.feature.components.VBookTheme.cardColor(),
                                    contentColor = com.nam.novelreader.feature.components.VBookTheme.textColor()
                                )
                                FilledTonalButton(
                                    onClick = { if (downloadDelay >= 100) downloadDelay -= 100 },
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.size(36.dp),
                                    colors = buttonColor,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Filled.Remove, "Giảm trễ", modifier = Modifier.size(18.dp))
                                }
                                val delayLabel = if (downloadDelay >= 1000) "${downloadDelay / 1000.0}s" else "${downloadDelay}ms"
                                Text(
                                    text = delayLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = com.nam.novelreader.feature.components.VBookTheme.textColor(),
                                    modifier = Modifier.width(60.dp),
                                    textAlign = TextAlign.Center
                                )
                                FilledTonalButton(
                                    onClick = { if (downloadDelay < 5000) downloadDelay += 100 },
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.size(36.dp),
                                    colors = buttonColor,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Filled.Add, "Tăng trễ", modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        // Row for Connection Timeout (Seconds)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Thời gian chờ (Timeout):",
                                style = MaterialTheme.typography.bodyMedium,
                                color = com.nam.novelreader.feature.components.VBookTheme.textColor()
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val buttonColor = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = com.nam.novelreader.feature.components.VBookTheme.cardColor(),
                                    contentColor = com.nam.novelreader.feature.components.VBookTheme.textColor()
                                )
                                FilledTonalButton(
                                    onClick = { if (downloadTimeout > 3) downloadTimeout-- },
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.size(36.dp),
                                    colors = buttonColor,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Filled.Remove, "Giảm thời gian chờ", modifier = Modifier.size(18.dp))
                                }
                                Text(
                                    text = "$downloadTimeout giây",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = com.nam.novelreader.feature.components.VBookTheme.textColor(),
                                    modifier = Modifier.width(60.dp),
                                    textAlign = TextAlign.Center
                                )
                                FilledTonalButton(
                                    onClick = { if (downloadTimeout < 60) downloadTimeout++ },
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.size(36.dp),
                                    colors = buttonColor,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Filled.Add, "Tăng thời gian chờ", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val startIdx = (downloadStartChapter.toIntOrNull() ?: 1).coerceIn(1, chapters.size) - 1
                        val endIdx = (downloadEndChapter.toIntOrNull() ?: chapters.size).coerceIn(1, chapters.size) - 1
                        if (startIdx <= endIdx) {
                            val urls = chapters.subList(startIdx, endIdx + 1).map { it.url }
                            val intent = Intent(context, DownloadService::class.java).apply {
                                action = DownloadService.ACTION_DOWNLOAD
                                putExtra(DownloadService.EXTRA_EXTENSION_ID, extensionId)
                                putExtra(DownloadService.EXTRA_NOVEL_URL, novelUrl)
                                putExtra(DownloadService.EXTRA_NOVEL_TITLE, novel?.title ?: "")
                                putExtra(DownloadService.EXTRA_COVER_URL, novel?.cover ?: "")
                                putStringArrayListExtra(DownloadService.EXTRA_CHAPTER_URLS, ArrayList(urls))
                            }
                            startDownloadAction(intent)
                        }
                    }) {
                        Text("Bắt đầu tải", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDownloadDialog = false }) {
                        Text("Hủy", color = com.nam.novelreader.feature.components.VBookTheme.subTextColor())
                    }
                }
            )
        }

        // === Dialog "Thông báo chương mới" ===
        if (showFollowDialog) {
            Dialog(onDismissRequest = { showFollowDialog = false }) {
                Box(
                    modifier = Modifier
                        .width(320.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(com.nam.novelreader.feature.components.VBookTheme.cardColor())
                        .padding(24.dp)
                ) {
                    Column {
                        Text(
                            "Thông báo chương mới",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = com.nam.novelreader.feature.components.VBookTheme.textColor()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            currentNovel.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = com.nam.novelreader.feature.components.VBookTheme.subTextColor(),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.toggleFollow(
                                        extensionId.ifBlank { currentNovel.extensionId },
                                        currentNovel.url,
                                        currentNovel.title
                                    )
                                }
                        ) {
                            Checkbox(
                                checked = isFollowed,
                                onCheckedChange = {
                                    viewModel.toggleFollow(
                                        extensionId.ifBlank { currentNovel.extensionId },
                                        currentNovel.url,
                                        currentNovel.title
                                    )
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = com.nam.novelreader.feature.components.VBookTheme.primaryColor(),
                                    uncheckedColor = com.nam.novelreader.feature.components.VBookTheme.subTextColor()
                                )
                            )
                            Text(
                                "Theo dõi cập nhật chương mới",
                                style = MaterialTheme.typography.bodyMedium,
                                color = com.nam.novelreader.feature.components.VBookTheme.textColor()
                            )
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { showFollowDialog = false }) {
                                Text("Hủy", color = com.nam.novelreader.feature.components.VBookTheme.subTextColor())
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { showFollowDialog = false },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = com.nam.novelreader.feature.components.VBookTheme.primaryColor(),
                                    contentColor = com.nam.novelreader.feature.components.VBookTheme.backgroundColor()
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Lưu", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
fun AuthorNovelItem(
    novel: Novel,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(90.dp)
            .bounceClick(onClick = onClick)
            .padding(bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = novel.cover,
            contentDescription = novel.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.67f)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = novel.title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = com.nam.novelreader.feature.components.VBookTheme.textColor()
        )
    }
}

@Composable
fun CommentItem(comment: Comment) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        AsyncImage(
            model = comment.avatar.ifBlank { Icons.Filled.Person },
            contentDescription = comment.name,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.DarkGray),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = comment.name,
                    fontWeight = FontWeight.Bold,
                    color = com.nam.novelreader.feature.components.VBookTheme.textColor(),
                    fontSize = 14.sp
                )
                 if (comment.description.isNotBlank()) {
                     Text(
                         text = comment.description,
                         color = com.nam.novelreader.feature.components.VBookTheme.subTextColor(),
                         fontSize = 11.sp
                     )
                 }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = comment.content,
                color = com.nam.novelreader.feature.components.VBookTheme.textColor(),
                fontSize = 13.sp
            )
            
            if (comment.subComments.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(com.nam.novelreader.feature.components.VBookTheme.backgroundColor(), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    comment.subComments.forEach { sub ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            AsyncImage(
                                model = sub.avatar.ifBlank { Icons.Filled.Person },
                                contentDescription = sub.name,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color.DarkGray),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(sub.name, fontWeight = FontWeight.Bold, color = com.nam.novelreader.feature.components.VBookTheme.textColor(), fontSize = 12.sp)
                                    if (sub.description.isNotBlank()) {
                                        Text(sub.description, color = Color.Gray, fontSize = 10.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(sub.content, color = com.nam.novelreader.feature.components.VBookTheme.subTextColor(), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = com.nam.novelreader.feature.components.VBookTheme.subTextColor()
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = com.nam.novelreader.feature.components.VBookTheme.textColor(),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).padding(start = 16.dp)
        )
    }
}
