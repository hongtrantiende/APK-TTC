package com.nam.novelreader.feature.home

import android.content.Context
import android.content.Intent
import com.nam.novelreader.service.AndroidTestServerService
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import com.nam.novelreader.feature.components.bounceClick
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope

import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.nam.novelreader.data.local.dao.NovelDao
import com.nam.novelreader.data.local.entity.cleanName
import com.nam.novelreader.data.local.dao.ReadingHistoryDao
import com.nam.novelreader.data.local.dao.RecentNovelWithInfo
import com.nam.novelreader.data.local.entity.NovelEntity
import com.nam.novelreader.data.local.entity.ReadingHistoryEntity
import com.nam.novelreader.data.local.entity.ChapterEntity
import com.nam.novelreader.data.local.dao.ChapterDao
import com.nam.novelreader.data.local.dao.ExtensionDao
import com.nam.novelreader.navigation.Routes
import com.nam.novelreader.data.repository.NovelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.activity.compose.rememberLauncherForActivityResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val novelDao: NovelDao,
    private val readingHistoryDao: ReadingHistoryDao,
    private val repository: NovelRepository,
    private val chapterDao: ChapterDao,
    private val extensionDao: ExtensionDao,
) : ViewModel() {
    init {
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = repository.context.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE)
            val isCleaned = prefs.getBoolean("download_cache_cleaned_v1", false)
            if (!isCleaned) {
                try {
                    chapterDao.cleanOnlineCacheDownloadedFlags()
                    prefs.edit().putBoolean("download_cache_cleaned_v1", true).apply()
                    android.util.Log.d("HomeViewModel", "Successfully cleaned legacy online cache downloaded flags.")
                } catch (e: Exception) {
                    android.util.Log.e("HomeViewModel", "Failed to clean legacy online cache flags", e)
                }
            }
        }
    }

    /** Truyện đã đọc gần đây kèm thông tin chi tiết */
    val recentlyRead: Flow<List<RecentNovelWithInfo>> = readingHistoryDao.getRecentHistoryWithInfo(10)

    /** Tất cả truyện trong thư viện */
    val allLibrary: Flow<List<NovelEntity>> = novelDao.getLibraryNovels()

    /** Truyện trong thư viện có chương đã tải */
    val downloadedLibrary: Flow<List<NovelEntity>> = novelDao.getDownloadedLibraryNovels()

    /** Tất cả truyện trong DB */
    val allNovels: Flow<List<NovelEntity>> = novelDao.getAllNovels()

    /** Lịch sử đọc không giới hạn (tối đa 500) */
    val allHistory: Flow<List<com.nam.novelreader.data.local.entity.ReadingHistoryEntity>> = readingHistoryDao.getRecentHistory(500)

    val downloadTasks: Flow<List<com.nam.novelreader.data.local.entity.DownloadTaskEntity>> = repository.getAllDownloadTasks()

    fun clearHistory() {
        viewModelScope.launch {
            readingHistoryDao.clearAll()
        }
    }

    fun deleteHistoryForNovel(novelUrl: String) {
        viewModelScope.launch {
            readingHistoryDao.deleteForNovel(novelUrl)
        }
    }

    fun removeFromLibrary(novelUrl: String) {
        viewModelScope.launch {
            repository.removeFromLibrary(novelUrl)
        }
    }

    fun exportNovelAsTxt(context: android.content.Context, novelUrl: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val novel = repository.getNovelFromDb(novelUrl) ?: return@launch
                val chapters = repository.getChapterList(novel.url)
                    .sortedBy { it.index }
                    .filter { it.isDownloaded && !it.content.isNullOrBlank() }
                
                if (chapters.isEmpty()) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Không có chương chữ nào được tải!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val fileName = "${novel.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")}.txt"
                
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/VBook")
                    }
                }

                val uri = context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        java.io.OutputStreamWriter(os, Charsets.UTF_8).use { writer ->
                            writer.write("${novel.title}\n${novel.author}\n\n")
                            for (ch in chapters) {
                                val cleanedContent = com.nam.novelreader.util.EpubWriter.cleanHtmlContent(ch.content)
                                writer.write("${ch.title}\n\n${cleanedContent}\n\n")
                            }
                        }
                    }
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Đã xuất file TXT thành công!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Lỗi khi xuất: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun exportNovelAsEpub(context: android.content.Context, novelUrl: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val novel = repository.getNovelFromDb(novelUrl) ?: return@launch
                val chapters = repository.getChapterList(novel.url)
                    .sortedBy { it.index }
                    .filter { it.isDownloaded && !it.content.isNullOrBlank() }
                    .map { com.nam.novelreader.domain.model.Chapter(url = it.url, title = it.title, content = it.content, index = it.index) }
                
                if (chapters.isEmpty()) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Không có chương chữ nào được tải!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val fileName = "${novel.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")}.epub"
                
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/epub+zip")
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/VBook")
                    }
                }

                val uri = context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        com.nam.novelreader.util.EpubWriter.writeEpub(os, novel, chapters)
                    }
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Đã xuất file EPUB thành công!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Lỗi khi xuất EPUB: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun backupNovel(context: android.content.Context, novelUrl: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val novel = repository.getNovelFromDb(novelUrl) ?: return@launch
                val fileName = "${novel.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")}_backup.json"
                val jsonStr = Json.encodeToString(novel)
                
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/VBook/Backup")
                    }
                }

                val uri = context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        java.io.OutputStreamWriter(os, Charsets.UTF_8).use { writer ->
                            writer.write(jsonStr)
                        }
                    }
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Đã sao lưu thành công!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Lỗi sao lưu: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun updateNovelMetadata(novel: NovelEntity, newTitle: String, newAuthor: String, newCover: String) {
        viewModelScope.launch {
            val updatedNovel = novel.copy(
                title = newTitle,
                author = newAuthor,
                cover = newCover
            )
            novelDao.update(updatedNovel)
        }
    }

    private fun parseLocalEpub(inputStream: java.io.InputStream): Pair<String, List<Pair<String, String>>> {
        val chapters = mutableListOf<Pair<String, String>>()
        var title = "Sách EPUB"
        
        val zipContents = mutableMapOf<String, ByteArray>()
        java.util.zip.ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    zipContents[entry.name] = zis.readBytes()
                }
                entry = zis.nextEntry
            }
        }
        
        val containerXml = zipContents.keys.firstOrNull { it.endsWith("container.xml") }
        var opfPath: String? = null
        if (containerXml != null) {
            val doc = org.jsoup.Jsoup.parse(String(zipContents[containerXml]!!, Charsets.UTF_8))
            val rootfile = doc.select("rootfile").firstOrNull()
            opfPath = rootfile?.attr("full-path")
        }
        
        if (opfPath == null) {
            opfPath = zipContents.keys.firstOrNull { it.endsWith(".opf") }
        }
        
        if (opfPath != null) {
            val opfDir = if (opfPath.contains("/")) opfPath.substringBeforeLast("/") + "/" else ""
            val opfBytes = zipContents[opfPath]
            if (opfBytes != null) {
                val doc = org.jsoup.Jsoup.parse(String(opfBytes, Charsets.UTF_8))
                val titleText = doc.select("dc|title").text().ifEmpty { doc.select("metadata > title").text() }
                if (titleText.isNotEmpty()) {
                    title = titleText
                }
                
                val manifest = doc.select("manifest > item")
                val itemMap = manifest.associate { it.attr("id") to it.attr("href") }
                
                val spine = doc.select("spine > itemref")
                var idx = 1
                for (itemref in spine) {
                    val idref = itemref.attr("idref")
                    val href = itemMap[idref] ?: continue
                    val htmlPath = java.net.URLDecoder.decode(opfDir + href, "UTF-8")
                    val normalizedPath = htmlPath.replace("../", "")
                    val htmlEntry = zipContents.keys.firstOrNull { 
                        it.endsWith(normalizedPath) || it.endsWith(href)
                    }
                    val htmlBytes = htmlEntry?.let { zipContents[it] } ?: continue
                    val htmlDoc = org.jsoup.Jsoup.parse(String(htmlBytes, Charsets.UTF_8))
                    
                    val chTitle = htmlDoc.select("h1, h2, h3").firstOrNull()?.text() 
                        ?: htmlDoc.title() 
                        ?: "Chương $idx"
                    
                    val body = htmlDoc.body()
                    val paragraphs = body.select("p")
                    val contentText = if (paragraphs.isNotEmpty()) {
                        paragraphs.joinToString("\n\n") { it.text() }
                    } else {
                        body.text()
                    }
                    
                    chapters.add(chTitle to contentText)
                    idx++
                }
            }
        }
        
        if (chapters.isEmpty()) {
            val htmlEntries = zipContents.filter { it.key.endsWith(".html") || it.key.endsWith(".xhtml") }.keys.sorted()
            var idx = 1
            for (entry in htmlEntries) {
                val htmlBytes = zipContents[entry] ?: continue
                val htmlDoc = org.jsoup.Jsoup.parse(String(htmlBytes, Charsets.UTF_8))
                val chTitle = htmlDoc.select("h1, h2, h3").firstOrNull()?.text() ?: htmlDoc.title() ?: "Chương $idx"
                val body = htmlDoc.body()
                val paragraphs = body.select("p")
                val contentText = if (paragraphs.isNotEmpty()) {
                    paragraphs.joinToString("\n\n") { it.text() }
                } else {
                    body.text()
                }
                chapters.add(chTitle to contentText)
                idx++
            }
        }
        
        return Pair(title, chapters)
    }

    private fun parseLocalTxt(inputStream: java.io.InputStream): Pair<String, List<Pair<String, String>>> {
        val reader = java.io.BufferedReader(java.io.InputStreamReader(inputStream, Charsets.UTF_8))
        val text = reader.readText()
        
        val chapterPattern = Regex("(?m)^(chương|chapter|quyển|bài|tập|phần|tiết|hồi|\\d+)\\s+(\\d+|[ivxldm]+)([:\\s\\n-].*)?$")
        
        val matches = chapterPattern.findAll(text).toList()
        val chapters = mutableListOf<Pair<String, String>>()
        
        if (matches.isEmpty()) {
            val chunkSize = 5000
            var i = 0
            var chapterIdx = 1
            while (i < text.length) {
                val end = minOf(i + chunkSize, text.length)
                val chunk = text.substring(i, end)
                chapters.add("Phần $chapterIdx" to chunk)
                i += chunkSize
                chapterIdx++
            }
        } else {
            var prevIndex = 0
            var prevTitle = "Lời giới thiệu"
            
            for (match in matches) {
                val currentIndex = match.range.first
                val content = text.substring(prevIndex, currentIndex).trim()
                if (content.isNotEmpty() || prevTitle != "Lời giới thiệu") {
                    chapters.add(prevTitle to content)
                }
                prevTitle = match.value.trim()
                prevIndex = match.range.last + 1
            }
            
            val finalContent = text.substring(prevIndex).trim()
            if (finalContent.isNotEmpty()) {
                chapters.add(prevTitle to finalContent)
            }
        }
        
        return Pair("Truyện Txt", chapters)
    }

    fun importLocalNovel(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val contentResolver = context.contentResolver
                var displayName = "Truyện ngoại tuyến"
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIdx != -1) {
                            displayName = cursor.getString(nameIdx)
                        }
                    }
                }
                
                val inputStream = contentResolver.openInputStream(uri) ?: throw Exception("Không thể mở tệp")
                val isEpub = displayName.lowercase().endsWith(".epub")
                
                val (novelTitle, chapters) = if (isEpub) {
                    parseLocalEpub(inputStream)
                } else {
                    parseLocalTxt(inputStream)
                }
                
                val finalTitle = if (novelTitle == "Truyện Txt" || novelTitle == "Sách EPUB") {
                    displayName.substringBeforeLast(".")
                } else {
                    novelTitle
                }
                
                val novelUrl = "local://" + System.currentTimeMillis() + "/" + finalTitle.hashCode()
                
                val novelEntity = NovelEntity(
                    url = novelUrl,
                    title = finalTitle,
                    author = "Ngoại tuyến",
                    cover = "",
                    description = "Sách nhập từ thiết bị: $displayName",
                    genres = "[\"Ngoại tuyến\"]",
                    status = "Hoàn thành",
                    extensionId = "local",
                    totalChapters = chapters.size,
                    isInLibrary = true,
                    addedToLibraryAt = System.currentTimeMillis()
                )
                
                val chapterEntities = chapters.mapIndexed { index, (chTitle, chContent) ->
                    ChapterEntity(
                        url = "$novelUrl/chapter_$index",
                        novelUrl = novelUrl,
                        title = chTitle,
                        index = index,
                        content = chContent,
                        images = null,
                        isDownloaded = true,
                        downloadedAt = System.currentTimeMillis()
                    )
                }
                
                novelDao.insert(novelEntity)
                chapterDao.insertAll(chapterEntities)
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Đã nhập thành công truyện '$finalTitle' với ${chapters.size} chương!", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Lỗi nhập truyện: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun importFromUrl(context: android.content.Context, url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (url.isBlank()) throw Exception("URL không hợp lệ")
                
                val extensions = extensionDao.getEnabledExtensions()
                val matchedExt = extensions.firstOrNull { ext ->
                    val domain = ext.source.replace("https://", "").replace("http://", "").replace("www.", "").substringBefore("/")
                    url.contains(domain, ignoreCase = true)
                } ?: throw Exception("Không tìm thấy nguồn truyện (Extension) phù hợp cho URL này.")
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Đang nạp thông tin truyện từ URL...", Toast.LENGTH_SHORT).show()
                }
                
                val details = repository.getNovelDetail(matchedExt.id, url)
                    ?: throw Exception("Không thể tải thông tin chi tiết truyện.")
                val toc = repository.getTableOfContents(matchedExt.id, url)
                
                val novelEntity = NovelEntity(
                    url = details.url,
                    title = details.title,
                    author = details.author,
                    cover = details.cover,
                    description = details.description,
                    genres = Json.encodeToString<List<com.nam.novelreader.domain.model.Genre>>(details.genres),
                    status = details.status,
                    extensionId = matchedExt.id,
                    totalChapters = toc.size,
                    isInLibrary = true,
                    addedToLibraryAt = System.currentTimeMillis()
                )
                
                val chapterEntities = toc.mapIndexed { index, ch ->
                    ChapterEntity(
                        url = ch.url,
                        novelUrl = details.url,
                        title = ch.title,
                        index = index,
                        contentType = "novel",
                        isDownloaded = false
                    )
                }
                
                novelDao.insert(novelEntity)
                chapterDao.insertAll(chapterEntities)
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Đã nhập thành công truyện '${details.title}' từ URL!", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Lỗi nhập URL: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val enabledExtensions = androidx.compose.runtime.mutableStateListOf<com.nam.novelreader.data.local.entity.ExtensionEntity>()

    fun loadEnabledExtensions() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = extensionDao.getEnabledExtensions()
            withContext(Dispatchers.Main) {
                enabledExtensions.clear()
                enabledExtensions.addAll(list)
            }
        }
    }
}

// Bảng màu VBook tối ấm áp
val PrimaryAccent: Color @Composable get() = com.nam.novelreader.feature.components.VBookTheme.primaryColor()
val SecondaryAccent: Color @Composable get() = com.nam.novelreader.feature.components.VBookTheme.primaryColor().copy(alpha = 0.7f)
val ScreenBg: Color @Composable get() = com.nam.novelreader.feature.components.VBookTheme.backgroundColor()
val CardBg: Color @Composable get() = com.nam.novelreader.feature.components.VBookTheme.cardColor()
val BottomSheetBg: Color @Composable get() = com.nam.novelreader.feature.components.VBookTheme.cardColor()
val SelectedChipBg: Color @Composable get() = com.nam.novelreader.feature.components.VBookTheme.primaryColor().copy(alpha = 0.2f)
val DividerColor: Color @Composable get() = if (com.nam.novelreader.feature.components.VBookTheme.isDarkTheme()) Color(0xFF3D352F) else Color(0xFFE8E2DA)
val GreyText: Color @Composable get() = com.nam.novelreader.feature.components.VBookTheme.subTextColor()

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface HomeEntryPoint {
    fun appPreferences(): com.nam.novelreader.data.preferences.AppPreferences
    fun supabaseAuthManager(): com.nam.novelreader.data.network.SupabaseAuthManager
    fun appDatabase(): com.nam.novelreader.data.local.AppDatabase
    fun novelDao(): com.nam.novelreader.data.local.dao.NovelDao
    fun googleDriveBackupManager(): com.nam.novelreader.data.network.GoogleDriveBackupManager
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavHostController,
    onNavigateToTab: (Int) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val entryPoint = remember {
        dagger.hilt.EntryPoints.get(context.applicationContext, HomeEntryPoint::class.java)
    }
    val appPrefs = entryPoint.appPreferences()
    val authManager = entryPoint.supabaseAuthManager()
    val googleDriveBackupManager = remember { entryPoint.googleDriveBackupManager() }
    
    var isVip by remember { mutableStateOf(appPrefs.isVip()) }
    DisposableEffect(appPrefs.prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "vip_expiry_timestamp") {
                isVip = appPrefs.isVip()
            }
        }
        appPrefs.prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            appPrefs.prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val prefs = remember { context.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

    var showImportDialog by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var showBrowserSourceDialog by remember { mutableStateOf(false) }
    var showDevModeDialog by remember { mutableStateOf(false) }
    var showCloudSyncDialog by remember { mutableStateOf(false) }
    var isCloudSyncing by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.importLocalNovel(context, it)
        }
    }

    // Đọc các cài đặt giao diện hiển thị từ SharedPreferences
    var layoutStyle by remember { mutableIntStateOf(prefs.getInt("home_layout_style", 1)) } // 0: Grid 2, 1: Grid 3, 2: List, 3: Compact List
    var coverSize by remember { mutableIntStateOf(prefs.getInt("home_cover_size", 120)) }
    var sortBy by remember { mutableIntStateOf(prefs.getInt("home_sort_by", 0)) } // 0: Đọc gần đây, 1: Thời gian thêm, 2: Mới cập nhật, 3: Chương mới, 4: Số chương
    var showProgress by remember { mutableStateOf(prefs.getBoolean("home_show_progress", true)) }
    var showTotalChapters by remember { mutableStateOf(prefs.getBoolean("home_show_total_chapters", true)) }
    var showNewChapters by remember { mutableStateOf(prefs.getBoolean("home_show_new_chapters", true)) }
    var saveHistory by remember { mutableStateOf(prefs.getBoolean("home_save_history", true)) }

    // Reactive profile/avatar state to update UI immediately upon login/logout/profile update
    var userAvatarPath by remember { mutableStateOf(appPrefs.userAvatarPath) }
    var isLoggedIn by remember { mutableStateOf(appPrefs.supabaseIsLoggedIn) }
    var displayName by remember { mutableStateOf(prefs.getString("user_display_name", "") ?: "") }
    var email by remember { mutableStateOf(appPrefs.supabaseUserEmail) }

    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "user_avatar_path" || key == "supabase_is_logged_in" || key == "user_display_name" || key == "supabase_user_email") {
                userAvatarPath = appPrefs.userAvatarPath
                isLoggedIn = appPrefs.supabaseIsLoggedIn
                displayName = prefs.getString("user_display_name", "") ?: ""
                email = appPrefs.supabaseUserEmail
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    // State của màn hình
    val recentlyRead by viewModel.recentlyRead.collectAsStateWithLifecycle(initialValue = emptyList())
    val allHistory by viewModel.allHistory.collectAsStateWithLifecycle(initialValue = emptyList())
    val readProgressMap = remember(allHistory) {
        allHistory.associate { it.novelUrl to (extractChapterNumber(it.chapterTitle) ?: 0) }
    }
    val allLibrary by viewModel.allLibrary.collectAsStateWithLifecycle(initialValue = emptyList())
    val downloadedLibrary by viewModel.downloadedLibrary.collectAsStateWithLifecycle(initialValue = emptyList())
    val allNovels by viewModel.allNovels.collectAsStateWithLifecycle(initialValue = emptyList())
    val downloadTasks by viewModel.downloadTasks.collectAsStateWithLifecycle(initialValue = emptyList())
    val downloadedNovelUrls = remember(downloadedLibrary) {
        downloadedLibrary.map { it.url }.toSet()
    }
    val downloadedNovelUrlsWithTasks = remember(downloadedLibrary, downloadTasks) {
        val urls = downloadedLibrary.map { it.url }.toMutableSet()
        urls.addAll(
            downloadTasks
                .filter { it.status == "preparing" || it.status == "downloading" }
                .map { it.novelUrl }
        )
        urls
    }
    
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 6 })
    val selectedFilter by remember { derivedStateOf { pagerState.currentPage } }
    var selectedSubFilter by remember { mutableIntStateOf(0) } // Theo dõi sub: 0 = Đang theo dõi, 1 = Chưa theo dõi
    var followedUrls by remember {
        mutableStateOf(prefs.getStringSet("followed_novel_urls", emptySet()) ?: emptySet())
    }
    var selectedNovelForAction by remember { mutableStateOf<NovelEntity?>(null) }

    var showBottomSheet by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Lưu trạng thái cuộn độc lập cho mỗi tab
    val stateTabAll = rememberLazyGridState()
    val stateTabDownloaded = rememberLazyGridState()
    val stateTabSaved = rememberLazyGridState()
    val stateTabHistory = rememberLazyGridState()
    val stateTabFollow = rememberLazyGridState()

    // Bộ lọc danh mục (Dropdown Tất cả)
    var showCategoryMenu by remember { mutableStateOf(false) }

    // Sắp xếp danh sách thư viện (Sắp xếp thông minh: Đọc gần đây ưu tiên hiển thị truyện mới đánh dấu)
    val sortedLibrary = remember(allLibrary, sortBy) {
        when (sortBy) {
            0 -> allLibrary.sortedByDescending { maxOf(it.lastReadAt ?: 0L, it.addedToLibraryAt ?: 0L) }
            1 -> allLibrary.sortedByDescending { it.addedToLibraryAt ?: 0L }
            2 -> allLibrary.sortedByDescending { maxOf(it.lastReadAt ?: 0L, it.addedToLibraryAt ?: 0L) } // fallback
            3 -> allLibrary.sortedByDescending { it.totalChapters } // fallback
            4 -> allLibrary.sortedByDescending { it.totalChapters }
            else -> allLibrary
        }
    }

    val combinedDownloadedLibrary = remember(allNovels, downloadedNovelUrlsWithTasks) {
        allNovels.filter { downloadedNovelUrlsWithTasks.contains(it.url) }
    }

    // Sắp xếp danh sách thư viện đã tải
    val sortedDownloadedLibrary = remember(combinedDownloadedLibrary, sortBy) {
        when (sortBy) {
            0 -> combinedDownloadedLibrary.sortedByDescending { maxOf(it.lastReadAt ?: 0L, it.addedToLibraryAt ?: 0L) }
            1 -> combinedDownloadedLibrary.sortedByDescending { it.addedToLibraryAt ?: 0L }
            2 -> combinedDownloadedLibrary.sortedByDescending { maxOf(it.lastReadAt ?: 0L, it.addedToLibraryAt ?: 0L) } // fallback
            3 -> combinedDownloadedLibrary.sortedByDescending { it.totalChapters } // fallback
            4 -> combinedDownloadedLibrary.sortedByDescending { it.totalChapters }
            else -> combinedDownloadedLibrary
        }
    }

    // Gộp tất cả truyện đã đọc, đã lưu, đã tải
    val combinedAllNovels = remember(allLibrary, recentlyRead, combinedDownloadedLibrary, allNovels) {
        val uniqueUrlNovels = mutableMapOf<String, NovelEntity>()
        
        allLibrary.forEach { uniqueUrlNovels[it.url] = it }
        combinedDownloadedLibrary.forEach { uniqueUrlNovels[it.url] = it }
        
        val allNovelsMap = allNovels.associateBy { it.url }
        recentlyRead.forEach { recent ->
            val novel = allNovelsMap[recent.novelUrl]
            if (novel != null) {
                uniqueUrlNovels[recent.novelUrl] = novel
            } else {
                uniqueUrlNovels[recent.novelUrl] = NovelEntity(
                    url = recent.novelUrl,
                    title = recent.title,
                    author = "",
                    cover = recent.cover,
                    extensionId = recent.extensionId,
                    isInLibrary = false
                )
            }
        }
        uniqueUrlNovels.values.toList()
    }

    // Sắp xếp danh sách tất cả truyện
    val sortedAllNovels = remember(combinedAllNovels, sortBy) {
        when (sortBy) {
            0 -> combinedAllNovels.sortedByDescending { maxOf(it.lastReadAt ?: 0L, it.addedToLibraryAt ?: 0L) }
            1 -> combinedAllNovels.sortedByDescending { it.addedToLibraryAt ?: 0L }
            2 -> combinedAllNovels.sortedByDescending { maxOf(it.lastReadAt ?: 0L, it.addedToLibraryAt ?: 0L) }
            3 -> combinedAllNovels.sortedByDescending { it.totalChapters }
            4 -> combinedAllNovels.sortedByDescending { it.totalChapters }
            else -> combinedAllNovels
        }
    }

    // Phân loại nhóm theo dõi
    val followedNovels = remember(sortedLibrary, followedUrls) {
        sortedLibrary.filter { followedUrls.contains(it.url) }
    }
    val unfollowedNovels = remember(sortedLibrary, followedUrls) {
        sortedLibrary.filter { !followedUrls.contains(it.url) }
    }

    val groupedHistory = remember(recentlyRead) {
        groupHistoryByDay(recentlyRead)
    }

    if (isSearching) {
        // Giao diện tìm kiếm trực quan (Image 9)
        LocalSearchScreen(
            recentlyRead = recentlyRead,
            allLibrary = allLibrary,
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            onBack = {
                isSearching = false
                searchQuery = ""
            },
            onNovelClick = { extId, url ->
                navController.navigate(Routes.detail(extId, url))
            }
        )
    } else {
        // Cấu hình Grid Layout dựa trên style đã chọn
        val columnsCount = when (layoutStyle) {
            0 -> 2
            1 -> 3
            else -> 1 // List & Compact List dạng hàng dọc
        }
        val maxLineSpan = columnsCount

        Scaffold(
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ScreenBg)
                        .statusBarsPadding()
                        .height(44.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tiêu đề
                    Text(
                        "Trang chủ",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = com.nam.novelreader.feature.components.VBookTheme.textColor(),
                        modifier = Modifier.weight(1f)
                    )

                    // Search
                    IconButton(onClick = { isSearching = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Search, contentDescription = "Tìm kiếm", tint = com.nam.novelreader.feature.components.VBookTheme.textColor(), modifier = Modifier.size(20.dp))
                    }
                    // Filter
                    IconButton(onClick = { showBottomSheet = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.FilterList, contentDescription = "Bộ lọc", tint = com.nam.novelreader.feature.components.VBookTheme.textColor(), modifier = Modifier.size(20.dp))
                    }
                    
                    // Avatar
                    val avatarFile = remember(userAvatarPath) {
                        if (userAvatarPath.isNotBlank()) java.io.File(userAvatarPath) else null
                    }
                    val hasAvatar = avatarFile != null && avatarFile.exists()

                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE5C09F))
                            .clickable { onNavigateToTab(3) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (hasAvatar) {
                            AsyncImage(
                                model = avatarFile,
                                contentDescription = "Avatar",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            if (isLoggedIn && (email.isNotBlank() || displayName.isNotBlank())) {
                                val displayChar = if (displayName.isNotBlank()) {
                                    displayName.take(1).uppercase(java.util.Locale.ROOT)
                                } else {
                                    email.take(1).uppercase(java.util.Locale.ROOT)
                                }
                                Text(
                                    text = displayChar,
                                    color = Color(0xFF4E3629),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Person,
                                    contentDescription = "Cá nhân",
                                    tint = Color(0xFF4E3629),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            },
            floatingActionButton = {
                // FAB dấu cộng nâu đất ở góc phải dưới
                FloatingActionButton(
                    onClick = { showImportDialog = true },
                    containerColor = Color(0xFF4E3629),
                    contentColor = SecondaryAccent,
                    shape = CircleShape,
                    modifier = Modifier.padding(bottom = 16.dp, end = 8.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Thêm", modifier = Modifier.size(28.dp))
                }
            },
            containerColor = ScreenBg
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // === Filter chips row — gọn gàng, capsule shape ===
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                        // Nút Dark/Light mode nhỏ gọn
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(CardBg)
                                .clickable {
                                    val isDark = !prefs.getBoolean("theme_is_dark", true)
                                    prefs.edit().putBoolean("theme_is_dark", isDark).apply()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (com.nam.novelreader.feature.components.VBookTheme.isDarkTheme()) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                                contentDescription = "Chế độ",
                                tint = com.nam.novelreader.feature.components.VBookTheme.subTextColor(),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Scrollable chips
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CustomHomeChip(
                                label = "Tất cả",
                                selected = selectedFilter == 0,
                                hasDropdown = true,
                                onClick = { scope.launch { pagerState.animateScrollToPage(0) } }
                            )
                            CustomHomeChip(
                                label = "Lịch sử",
                                selected = selectedFilter == 3,
                                onClick = { scope.launch { pagerState.animateScrollToPage(3) } }
                            )
                            CustomHomeChip(
                                label = "Theo dõi",
                                selected = selectedFilter == 4,
                                onClick = { scope.launch { pagerState.animateScrollToPage(4) } }
                            )
                            CustomHomeChip(
                                label = "Đã tải",
                                selected = selectedFilter == 5,
                                onClick = { scope.launch { pagerState.animateScrollToPage(5) } }
                            )
                        }

                        // Nút kho tải
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(CardBg)
                                .clickable { navController.navigate(Routes.DOWNLOAD) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.Inventory2, contentDescription = "Kho", tint = com.nam.novelreader.feature.components.VBookTheme.subTextColor(), modifier = Modifier.size(16.dp))
                        }
                    }

                    // === 2. Phân phối Layout nội dung chính theo Tab ===
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            userScrollEnabled = true,
                            beyondViewportPageCount = 5
                        ) { page ->
                            when (page) {
                    0 -> {
                        // === TAB TẤT CẢ ===
                        LazyVerticalGrid(
                            state = stateTabAll,
                            columns = GridCells.Fixed(columnsCount),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 90.dp)
                        ) {
                            if (sortedAllNovels.isEmpty() && followedNovels.isEmpty()) {
                            // Empty state (Image 6 -> Video VBook)
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 120.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Outlined.Description,
                                        contentDescription = null,
                                        tint = PrimaryAccent.copy(0.4f),
                                        modifier = Modifier.size(90.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        "Kệ đang trống",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        fontSize = 18.sp,
                                        color = com.nam.novelreader.feature.components.VBookTheme.textColor()
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "Hãy thêm những nội dung bạn yêu thích để kệ sống động hơn nhé!",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = GreyText,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 32.dp)
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Button(
                                        onClick = {
                                            scope.launch { pagerState.animateScrollToPage(1) } // Switch to Khám phá page
                                            onNavigateToTab(1)
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF4E3629),
                                            contentColor = SecondaryAccent
                                        ),
                                        shape = RoundedCornerShape(24.dp),
                                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
                                    ) {
                                        Text("Khám phá", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else {
                            // "Xem gần đây" Carousel hàng ngang
                            if (recentlyRead.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Text(
                                        "Xem gần đây",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = com.nam.novelreader.feature.components.VBookTheme.textColor(),
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                    )
                                }
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.padding(bottom = 16.dp)
                                    ) {
                                        items(
                                            items = recentlyRead.toList(),
                                            key = { it.novelUrl }
                                        ) { novel ->
                                            val isFirst = recentlyRead.firstOrNull()?.novelUrl == novel.novelUrl
                                            RecentCard(
                                                novel = novel,
                                                isLandscape = isFirst,
                                                onClick = {
                                                    navController.navigate(Routes.detail(novel.extensionId, novel.novelUrl))
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // "Đang theo dõi" Carousel hàng ngang
                            if (followedNovels.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Text(
                                        "Đang theo dõi",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = com.nam.novelreader.feature.components.VBookTheme.textColor(),
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                    )
                                }
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.padding(bottom = 16.dp)
                                    ) {
                                        items(
                                            items = followedNovels,
                                            key = { it.url }
                                        ) { novel ->
                                            RecentCard(
                                                novel = RecentNovelWithInfo(
                                                    novelUrl = novel.url,
                                                    title = novel.title,
                                                    cover = novel.cover,
                                                    extensionId = novel.extensionId,
                                                    chapterUrl = "",
                                                    chapterTitle = novel.extensionId.replaceFirstChar { it.uppercase() },
                                                    scrollPosition = 0,
                                                    readAt = 0L,
                                                    totalChapters = 0
                                                ),
                                                isLandscape = false,
                                                onClick = {
                                                    navController.navigate(Routes.detail(novel.extensionId, novel.url))
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // Tất cả truyện (đã lưu, đã tải, đã đọc)
                            if (sortedAllNovels.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Text(
                                        "Tất cả truyện (${sortedAllNovels.size})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = com.nam.novelreader.feature.components.VBookTheme.textColor(),
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                    )
                                }

                                // Grid items
                                if (layoutStyle == 0 || layoutStyle == 1) {
                                    items(
                                        items = sortedAllNovels,
                                        key = { it.url }
                                    ) { novel ->
                                        LibraryGridCard(
                                            novel = novel,
                                            coverSize = coverSize,
                                            showProgress = showProgress,
                                            showTotal = showTotalChapters,
                                            isDownloaded = downloadedNovelUrls.contains(novel.url),
                                            currentReadChapter = readProgressMap[novel.url] ?: 0,
                                            onClick = {
                                                navController.navigate(Routes.detail(novel.extensionId, novel.url))
                                            },
                                            onLongClick = {
                                                selectedNovelForAction = novel
                                            }
                                        )
                                    }
                                } else {
                                    // List hoặc Compact List
                                    items(
                                        items = sortedAllNovels,
                                        key = { it.url }
                                    ) { novel ->
                                        LibraryListCard(
                                            novel = novel,
                                            coverSize = coverSize,
                                            isCompact = layoutStyle == 3,
                                            showProgress = showProgress,
                                            showTotal = showTotalChapters,
                                            isDownloaded = downloadedNovelUrls.contains(novel.url),
                                            currentReadChapter = readProgressMap[novel.url] ?: 0,
                                            onClick = {
                                                navController.navigate(Routes.detail(novel.extensionId, novel.url))
                                            },
                                            onLongClick = {
                                                selectedNovelForAction = novel
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        }
                    }

                    1 -> {
                        // === TAB ĐÃ TẢI ===
                        LazyVerticalGrid(
                            state = stateTabDownloaded,
                            columns = GridCells.Fixed(columnsCount),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 90.dp)
                        ) {
                            if (sortedDownloadedLibrary.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 120.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Filled.Download,
                                        contentDescription = null,
                                        tint = PrimaryAccent.copy(0.4f),
                                        modifier = Modifier.size(90.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        "Chưa tải truyện nào",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = com.nam.novelreader.feature.components.VBookTheme.textColor()
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "Tải truyện offline để có thể đọc mọi lúc mọi nơi ngay cả khi mất mạng nhé!",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = GreyText,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 32.dp)
                                    )
                                }
                            }
                        } else {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Tủ sách đã tải (${sortedDownloadedLibrary.size})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = com.nam.novelreader.feature.components.VBookTheme.textColor()
                                    )
                                    IconButton(
                                        onClick = { navController.navigate(Routes.DOWNLOAD) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Settings,
                                            contentDescription = "Quản lý tiến trình tải ngầm",
                                            tint = SecondaryAccent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            // Grid items thư viện đã tải (2 hoặc 3 cột)
                            if (layoutStyle == 0 || layoutStyle == 1) {
                                items(
                                    items = sortedDownloadedLibrary,
                                    key = { it.url }
                                ) { novel ->
                                    LibraryGridCard(
                                        novel = novel,
                                        coverSize = coverSize,
                                        showProgress = showProgress,
                                        showTotal = showTotalChapters,
                                        isDownloaded = downloadedNovelUrls.contains(novel.url),
                                        currentReadChapter = readProgressMap[novel.url] ?: 0,
                                        onClick = {
                                            navController.navigate(Routes.detail(novel.extensionId, novel.url))
                                        },
                                        onLongClick = {
                                            selectedNovelForAction = novel
                                        }
                                    )
                                }
                            } else {
                                // List hoặc Compact List
                                items(
                                    items = sortedDownloadedLibrary,
                                    key = { it.url }
                                ) { novel ->
                                    LibraryListCard(
                                        novel = novel,
                                        coverSize = coverSize,
                                        isCompact = layoutStyle == 3,
                                        showProgress = showProgress,
                                        showTotal = showTotalChapters,
                                        isDownloaded = downloadedNovelUrls.contains(novel.url),
                                        currentReadChapter = readProgressMap[novel.url] ?: 0,
                                        onClick = {
                                            navController.navigate(Routes.detail(novel.extensionId, novel.url))
                                        },
                                        onLongClick = {
                                            selectedNovelForAction = novel
                                        }
                                    )
                                }
                            }
                        }
                    }
                    }

                    2 -> {
                        // === TAB ĐÃ LƯU ===
                        LazyVerticalGrid(
                            state = stateTabSaved,
                            columns = GridCells.Fixed(columnsCount),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 90.dp)
                        ) {
                            if (sortedLibrary.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 120.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Filled.Archive,
                                        contentDescription = null,
                                        tint = PrimaryAccent.copy(0.4f),
                                        modifier = Modifier.size(90.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        "Tủ sách trống",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = com.nam.novelreader.feature.components.VBookTheme.textColor()
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "Đánh dấu (Bookmark) các bộ truyện để lưu vào đây nhé!",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = GreyText,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 32.dp)
                                    )
                                }
                            }
                        } else {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    "Tủ sách đã lưu (${sortedLibrary.size})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = com.nam.novelreader.feature.components.VBookTheme.textColor(),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                )
                            }

                            // Grid items thư viện chính (2 hoặc 3 cột)
                            if (layoutStyle == 0 || layoutStyle == 1) {
                                items(
                                    items = sortedLibrary,
                                    key = { it.url }
                                ) { novel ->
                                    LibraryGridCard(
                                        novel = novel,
                                        coverSize = coverSize,
                                        showProgress = showProgress,
                                        showTotal = showTotalChapters,
                                        isDownloaded = downloadedNovelUrls.contains(novel.url),
                                        currentReadChapter = readProgressMap[novel.url] ?: 0,
                                        onClick = {
                                            navController.navigate(Routes.detail(novel.extensionId, novel.url))
                                        },
                                        onLongClick = {
                                            selectedNovelForAction = novel
                                        }
                                    )
                                }
                            } else {
                                // List hoặc Compact List
                                items(
                                    items = sortedLibrary,
                                    key = { it.url }
                                ) { novel ->
                                    LibraryListCard(
                                        novel = novel,
                                        coverSize = coverSize,
                                        isCompact = layoutStyle == 3,
                                        showProgress = showProgress,
                                        showTotal = showTotalChapters,
                                        isDownloaded = downloadedNovelUrls.contains(novel.url),
                                        currentReadChapter = readProgressMap[novel.url] ?: 0,
                                        onClick = {
                                            navController.navigate(Routes.detail(novel.extensionId, novel.url))
                                        },
                                        onLongClick = {
                                            selectedNovelForAction = novel
                                        }
                                    )
                                }
                            }
                        }
                        }
                    }

                    3 -> {
                        // === TAB LỊCH SỬ (Image 7) ===
                        LazyVerticalGrid(
                            state = stateTabHistory,
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 90.dp)
                        ) {
                            if (recentlyRead.isEmpty()) {
                            item(span = { GridItemSpan(3) }) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 120.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        Icons.Filled.Cached,
                                        contentDescription = null,
                                        tint = GreyText,
                                        modifier = Modifier.size(60.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("Chưa có lịch sử đọc", color = com.nam.novelreader.feature.components.VBookTheme.textColor(), fontSize = 16.sp)
                                }
                            }
                        } else {
                            groupedHistory.forEach { (day, items) ->
                                // Group header: e.g. "Hôm nay 2"
                                item(span = { GridItemSpan(3) }) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            day,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = com.nam.novelreader.feature.components.VBookTheme.textColor()
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(CardBg)
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                "${items.size}",
                                                fontSize = 11.sp,
                                                color = SecondaryAccent,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                items(
                                    items = items,
                                    key = { it.novelUrl }
                                ) { novel ->
                                    HistoryCardItem(
                                        novel = novel,
                                        onClick = {
                                            navController.navigate(Routes.detail(novel.extensionId, novel.novelUrl))
                                        }
                                    )
                                }
                            }

                            // Khối chú thích lịch sử
                            item(span = { GridItemSpan(3) }) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = CardBg),
                                    modifier = Modifier.padding(16.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            "Lịch sử truyện chỉ lưu các truyện đã đọc gần nhất trong 30 ngày gần nhất.",
                                            color = GreyText,
                                            fontSize = 13.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(bottom = 12.dp)
                                        )
                                        Button(
                                            onClick = { viewModel.clearHistory() },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF3E271D),
                                                contentColor = SecondaryAccent
                                            ),
                                            shape = CircleShape
                                        ) {
                                            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Xóa toàn bộ lịch sử", fontSize = 14.sp)
                                        }
                                    }
                                }
                            }

                            // Switch lưu lịch sử đọc
                            item(span = { GridItemSpan(3) }) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = CardBg),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "Lưu lịch sử đọc",
                                                fontWeight = FontWeight.Bold,
                                                color = com.nam.novelreader.feature.components.VBookTheme.textColor(),
                                                fontSize = 15.sp
                                            )
                                            Text(
                                                "Lưu và hiển thị lịch sử xem của bạn.",
                                                color = GreyText,
                                                fontSize = 12.sp
                                            )
                                        }
                                        Switch(
                                            checked = saveHistory,
                                            onCheckedChange = {
                                                saveHistory = it
                                                prefs.edit().putBoolean("home_save_history", it).apply()
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = SecondaryAccent,
                                                checkedTrackColor = PrimaryAccent,
                                                uncheckedThumbColor = Color.Gray,
                                                uncheckedTrackColor = Color.DarkGray
                                            )
                                        )
                                    }
                                }
                            }
                        }
                        }
                    }

                    4 -> {
                        // === TAB THEO DÕI (Image 4 & 5) ===
                        LazyVerticalGrid(
                            state = stateTabFollow,
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 90.dp)
                        ) {
                            // Hiển thị 2 sub-chips Đang theo dõi / Chưa theo dõi
                            item(span = { GridItemSpan(3) }) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CustomSubChip(
                                    label = "Đang theo dõi",
                                    count = followedNovels.size,
                                    selected = selectedSubFilter == 0,
                                    onClick = { selectedSubFilter = 0 }
                                )
                                CustomSubChip(
                                    label = "Chưa theo dõi",
                                    count = unfollowedNovels.size,
                                    selected = selectedSubFilter == 1,
                                    onClick = { selectedSubFilter = 1 }
                                )
                            }
                        }

                        if (selectedSubFilter == 0) {
                            // Đang theo dõi
                            if (followedNovels.isEmpty()) {
                                // Giao diện trống (Image 4 -> Video VBook)
                                item(span = { GridItemSpan(3) }) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 120.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            Icons.Outlined.Inventory2,
                                            contentDescription = null,
                                            tint = PrimaryAccent.copy(0.4f),
                                            modifier = Modifier.size(90.dp)
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            "Chưa theo dõi gì",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            fontSize = 18.sp,
                                            color = com.nam.novelreader.feature.components.VBookTheme.textColor()
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            "Theo dõi nội dung bạn quan tâm để xem tại đây nhé!",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = GreyText,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(horizontal = 32.dp)
                                        )
                                        Spacer(modifier = Modifier.height(24.dp))
                                        Button(
                                            onClick = {
                                                scope.launch { pagerState.animateScrollToPage(1) }
                                                onNavigateToTab(1)
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF4E3629),
                                                contentColor = SecondaryAccent
                                            ),
                                            shape = RoundedCornerShape(24.dp),
                                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
                                        ) {
                                            Text("Khám phá", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            } else {
                                items(
                                    items = followedNovels,
                                    key = { it.url },
                                    span = { GridItemSpan(3) }
                                ) { novel ->
                                    FollowCardItem(
                                        novel = novel,
                                        isFollowed = true,
                                        onToggleFollow = {
                                            val updated = followedUrls.toMutableSet()
                                            updated.remove(novel.url)
                                            followedUrls = updated
                                            prefs.edit().putStringSet("followed_novel_urls", updated).apply()
                                        },
                                        onClick = {
                                            navController.navigate(Routes.detail(novel.extensionId, novel.url))
                                        }
                                    )
                                }
                            }
                        } else {
                            // Chưa theo dõi list (Image 5)
                            if (unfollowedNovels.isEmpty()) {
                                item(span = { GridItemSpan(3) }) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 80.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("Không có truyện chưa theo dõi", color = GreyText)
                                    }
                                }
                            } else {
                                items(
                                    items = unfollowedNovels,
                                    key = { it.url },
                                    span = { GridItemSpan(3) }
                                ) { novel ->
                                    FollowCardItem(
                                        novel = novel,
                                        isFollowed = false,
                                        onToggleFollow = {
                                            val updated = followedUrls.toMutableSet()
                                            updated.add(novel.url)
                                            followedUrls = updated
                                            prefs.edit().putStringSet("followed_novel_urls", updated).apply()
                                        },
                                        onClick = {
                                            navController.navigate(Routes.detail(novel.extensionId, novel.url))
                                        }
                                    )
                                }
                            }
                        }
                        }
                    }

                    5 -> {
                        // === TAB TRUYỆN ĐÃ TẢI OFFLINE ===
                        LazyVerticalGrid(
                            state = stateTabDownloaded,
                            columns = GridCells.Fixed(columnsCount),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 90.dp)
                        ) {
                            if (sortedDownloadedLibrary.isEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 120.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            Icons.Filled.Download,
                                            contentDescription = null,
                                            tint = PrimaryAccent.copy(0.4f),
                                            modifier = Modifier.size(90.dp)
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            "Chưa tải truyện nào",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = com.nam.novelreader.feature.components.VBookTheme.textColor()
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            "Tải truyện offline để có thể đọc mọi lúc mọi nơi ngay cả khi mất mạng nhé!",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = GreyText,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(horizontal = 32.dp)
                                        )
                                    }
                                }
                            } else {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "Các truyện đã tải về (${sortedDownloadedLibrary.size})",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = com.nam.novelreader.feature.components.VBookTheme.textColor()
                                        )
                                        IconButton(
                                            onClick = { navController.navigate(Routes.DOWNLOAD) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.Settings,
                                                contentDescription = "Quản lý tiến trình tải ngầm",
                                                tint = SecondaryAccent,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }

                                // Grid items thư viện đã tải (2 hoặc 3 cột)
                                if (layoutStyle == 0 || layoutStyle == 1) {
                                    items(
                                        items = sortedDownloadedLibrary,
                                        key = { it.url }
                                    ) { novel ->
                                        LibraryGridCard(
                                            novel = novel,
                                            coverSize = coverSize,
                                            showProgress = showProgress,
                                            showTotal = showTotalChapters,
                                            isDownloaded = downloadedNovelUrls.contains(novel.url),
                                            currentReadChapter = readProgressMap[novel.url] ?: 0,
                                            onClick = {
                                                navController.navigate(Routes.detail(novel.extensionId, novel.url, isOffline = true))
                                            },
                                            onLongClick = {
                                                selectedNovelForAction = novel
                                            }
                                        )
                                    }
                                } else {
                                    // List hoặc Compact List
                                    items(
                                        items = sortedDownloadedLibrary,
                                        key = { it.url }
                                    ) { novel ->
                                        LibraryListCard(
                                            novel = novel,
                                            coverSize = coverSize,
                                            isCompact = layoutStyle == 3,
                                            showProgress = showProgress,
                                            showTotal = showTotalChapters,
                                            isDownloaded = downloadedNovelUrls.contains(novel.url),
                                            currentReadChapter = readProgressMap[novel.url] ?: 0,
                                            onClick = {
                                                navController.navigate(Routes.detail(novel.extensionId, novel.url, isOffline = true))
                                            },
                                            onLongClick = {
                                                selectedNovelForAction = novel
                                            }
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

        // Bottom Sheet tùy chỉnh (Image 8)
        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                containerColor = BottomSheetBg,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Kiểu
                    Text("Kiểu", fontWeight = FontWeight.Bold, color = PrimaryAccent, fontSize = 14.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        LayoutOptionBox(title = "Grid 2", selected = layoutStyle == 0, onClick = {
                            layoutStyle = 0
                            prefs.edit().putInt("home_layout_style", 0).apply()
                        }) {
                            MiniLayoutGrid(columns = 2)
                        }
                        LayoutOptionBox(title = "Grid 3", selected = layoutStyle == 1, onClick = {
                            layoutStyle = 1
                            prefs.edit().putInt("home_layout_style", 1).apply()
                        }) {
                            MiniLayoutGrid(columns = 3)
                        }
                        LayoutOptionBox(title = "List", selected = layoutStyle == 2, onClick = {
                            layoutStyle = 2
                            prefs.edit().putInt("home_layout_style", 2).apply()
                        }) {
                            MiniLayoutList(isCompact = false)
                        }
                        LayoutOptionBox(title = "Compact", selected = layoutStyle == 3, onClick = {
                            layoutStyle = 3
                            prefs.edit().putInt("home_layout_style", 3).apply()
                        }) {
                            MiniLayoutList(isCompact = true)
                        }
                    }

                    // Kích thước
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Kích thước", fontWeight = FontWeight.Bold, color = PrimaryAccent, fontSize = 14.sp)
                        Text("$coverSize", color = com.nam.novelreader.feature.components.VBookTheme.textColor(), fontSize = 14.sp)
                    }
                    Slider(
                        value = coverSize.toFloat(),
                        onValueChange = {
                            coverSize = it.toInt()
                            prefs.edit().putInt("home_cover_size", it.toInt()).apply()
                        },
                        valueRange = 80f..200f,
                        colors = SliderDefaults.colors(
                            thumbColor = SecondaryAccent,
                            activeTrackColor = PrimaryAccent,
                            inactiveTrackColor = DividerColor
                        )
                    )

                    // Sắp xếp
                    Text("Sắp xếp", fontWeight = FontWeight.Bold, color = PrimaryAccent, fontSize = 14.sp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val sortOptions = listOf("Đọc gần đây", "Thời gian thêm", "Mới cập nhật", "Chương mới", "Số chương")
                        sortOptions.forEachIndexed { idx, opt ->
                            FilterChip(
                                selected = sortBy == idx,
                                onClick = {
                                    sortBy = idx
                                    prefs.edit().putInt("home_sort_by", idx).apply()
                                },
                                label = { Text("↓ $opt") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimaryAccent,
                                    selectedLabelColor = com.nam.novelreader.feature.components.VBookTheme.backgroundColor(),
                                    containerColor = CardBg,
                                    labelColor = com.nam.novelreader.feature.components.VBookTheme.textColor()
                                )
                            )
                        }
                    }

                    // Hiển thị
                    Text("Hiển thị", fontWeight = FontWeight.Bold, color = PrimaryAccent, fontSize = 14.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = showProgress,
                            onClick = {
                                showProgress = !showProgress
                                prefs.edit().putBoolean("home_show_progress", showProgress).apply()
                            },
                            label = { Text("Tiến độ đọc") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryAccent,
                                selectedLabelColor = com.nam.novelreader.feature.components.VBookTheme.backgroundColor(),
                                containerColor = CardBg,
                                labelColor = com.nam.novelreader.feature.components.VBookTheme.textColor()
                            )
                        )
                        FilterChip(
                            selected = showTotalChapters,
                            onClick = {
                                showTotalChapters = !showTotalChapters
                                prefs.edit().putBoolean("home_show_total_chapters", showTotalChapters).apply()
                            },
                            label = { Text("Tổng số chương") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryAccent,
                                selectedLabelColor = com.nam.novelreader.feature.components.VBookTheme.backgroundColor(),
                                containerColor = CardBg,
                                labelColor = com.nam.novelreader.feature.components.VBookTheme.textColor()
                            )
                        )
                        FilterChip(
                            selected = showNewChapters,
                            onClick = {
                                showNewChapters = !showNewChapters
                                prefs.edit().putBoolean("home_show_new_chapters", showNewChapters).apply()
                            },
                            label = { Text("Số chương mới") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryAccent,
                                selectedLabelColor = com.nam.novelreader.feature.components.VBookTheme.backgroundColor(),
                                containerColor = CardBg,
                                labelColor = com.nam.novelreader.feature.components.VBookTheme.textColor()
                            )
                        )
                    }
                }
            }
        }

        if (selectedNovelForAction != null) {
            val novel = selectedNovelForAction!!
            var showEditDialog by remember { mutableStateOf(false) }
            var showExportMenu by remember { mutableStateOf(false) }

            ModalBottomSheet(
                onDismissRequest = { selectedNovelForAction = null },
                containerColor = BottomSheetBg,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .navigationBarsPadding()
                ) {
                    // Header: Cover | Title, Author, Source, Progress
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = com.nam.novelreader.feature.components.buildNovelImageRequest(novel),
                            contentDescription = novel.title,
                            modifier = Modifier
                                .width(64.dp)
                                .height(96.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = novel.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = com.nam.novelreader.feature.components.VBookTheme.textColor(),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = novel.author.ifBlank { "Khuyết Danh" },
                                fontSize = 13.sp,
                                color = GreyText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = novel.extensionId,
                                fontSize = 11.sp,
                                color = PrimaryAccent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tiến độ: " + (if (novel.lastReadChapterUrl.isNullOrBlank()) "Chưa đọc" else "Đang đọc"),
                                fontSize = 12.sp,
                                color = GreyText
                            )
                        }
                    }

                    HorizontalDivider(color = DividerColor)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Lưới các nút hành động (lọc theo VIP nếu cần ẩn nút xuất file)
                    val gridItems = remember(isVip, novel) {
                        val list = mutableListOf<Triple<String, androidx.compose.ui.graphics.vector.ImageVector, () -> Unit>>()
                        list.add(Triple("Chỉnh sửa", Icons.Default.Edit) {
                            showEditDialog = true
                        })
                        list.add(Triple("Lối tắt", Icons.Default.Shortcut) {
                            val appNovel = com.nam.novelreader.domain.model.Novel(
                                url = novel.url,
                                title = novel.title,
                                author = novel.author,
                                cover = novel.cover,
                                extensionId = novel.extensionId,
                                status = novel.status,
                                description = novel.description,
                                type = com.nam.novelreader.domain.model.ContentType.fromString(novel.type),
                                lastReadChapterUrl = novel.lastReadChapterUrl,
                                lastReadAt = novel.lastReadAt,
                                scrollPosition = novel.scrollPosition
                            )
                            com.nam.novelreader.util.ShortcutUtils.addNovelShortcut(context, scope, appNovel)
                            selectedNovelForAction = null
                        })
                        list.add(Triple("Tải xuống", Icons.Default.Download) {
                            val downloadIntent = android.content.Intent(context, com.nam.novelreader.service.DownloadService::class.java).apply {
                                action = com.nam.novelreader.service.DownloadService.ACTION_DOWNLOAD
                                putExtra(com.nam.novelreader.service.DownloadService.EXTRA_EXTENSION_ID, novel.extensionId)
                                putExtra(com.nam.novelreader.service.DownloadService.EXTRA_NOVEL_URL, novel.url)
                                putExtra(com.nam.novelreader.service.DownloadService.EXTRA_NOVEL_TITLE, novel.title)
                                putExtra(com.nam.novelreader.service.DownloadService.EXTRA_COVER_URL, novel.cover)
                            }
                            context.startService(downloadIntent)
                            Toast.makeText(context, "Bắt đầu tải xuống truyện chạy ngầm", Toast.LENGTH_SHORT).show()
                            selectedNovelForAction = null
                        })
                        if (isVip) {
                            list.add(Triple("Xuất file", Icons.Default.Archive) {
                                showExportMenu = true
                            })
                        }
                        list.add(Triple("Chia sẻ", Icons.Default.Share) {
                            val shareIntent = android.content.Intent().apply {
                                action = android.content.Intent.ACTION_SEND
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_SUBJECT, novel.title)
                                putExtra(android.content.Intent.EXTRA_TEXT, "Đọc truyện '${novel.title}' của tác giả '${novel.author}' tại Novel Reader!")
                            }
                            context.startActivity(android.content.Intent.createChooser(shareIntent, "Chia sẻ truyện"))
                            selectedNovelForAction = null
                        })
                        list.add(Triple("Xóa truyện", Icons.Default.Delete) {
                            viewModel.removeFromLibrary(novel.url)
                            selectedNovelForAction = null
                        })
                        list
                    }

                    val chunkedItems = gridItems.chunked(3)
                    chunkedItems.forEachIndexed { rowIndex, rowItems ->
                        if (rowIndex > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Row(modifier = Modifier.fillMaxWidth()) {
                            rowItems.forEach { item ->
                                val isDelete = item.first == "Xóa truyện"
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { item.third() }
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            item.second, 
                                            contentDescription = item.first, 
                                            tint = if (isDelete) Color.Red else SecondaryAccent, 
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            item.first, 
                                            fontSize = 12.sp, 
                                            color = if (isDelete) Color.Red else com.nam.novelreader.feature.components.VBookTheme.textColor()
                                        )
                                    }
                                }
                            }
                            if (rowItems.size < 3) {
                                for (k in 0 until (3 - rowItems.size)) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Hộp thoại chỉnh sửa thông tin truyện
            if (showEditDialog) {
                var editTitle by remember { mutableStateOf(novel.title) }
                var editAuthor by remember { mutableStateOf(novel.author) }
                var editCover by remember { mutableStateOf(novel.cover) }

                AlertDialog(
                    onDismissRequest = { showEditDialog = false },
                    title = { Text("Chỉnh sửa thông tin", color = com.nam.novelreader.feature.components.VBookTheme.textColor()) },
                    containerColor = BottomSheetBg,
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = editTitle,
                                onValueChange = { editTitle = it },
                                label = { Text("Tên truyện") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PrimaryAccent,
                                    focusedLabelColor = PrimaryAccent,
                                    unfocusedBorderColor = DividerColor
                                )
                            )
                            OutlinedTextField(
                                value = editAuthor,
                                onValueChange = { editAuthor = it },
                                label = { Text("Tác giả") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PrimaryAccent,
                                    focusedLabelColor = PrimaryAccent,
                                    unfocusedBorderColor = DividerColor
                                )
                            )
                            OutlinedTextField(
                                value = editCover,
                                onValueChange = { editCover = it },
                                label = { Text("Đường dẫn ảnh bìa") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PrimaryAccent,
                                    focusedLabelColor = PrimaryAccent,
                                    unfocusedBorderColor = DividerColor
                                )
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.updateNovelMetadata(novel, editTitle, editAuthor, editCover)
                                showEditDialog = false
                                selectedNovelForAction = null
                            }
                        ) {
                            Text("Lưu", color = PrimaryAccent)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showEditDialog = false }) {
                            Text("Hủy", color = GreyText)
                        }
                    }
                )
            }

            // Hộp thoại menu Xuất file
            if (showExportMenu) {
                val isDownloaded = downloadedNovelUrls.contains(novel.url)
                AlertDialog(
                    onDismissRequest = { showExportMenu = false },
                    title = { Text("Chọn định dạng xuất", color = com.nam.novelreader.feature.components.VBookTheme.textColor()) },
                    containerColor = BottomSheetBg,
                    text = {
                        Column {
                            ListItem(
                                headlineContent = { 
                                    Text(
                                        "Xuất file văn bản (TXT)", 
                                        color = if (isDownloaded) com.nam.novelreader.feature.components.VBookTheme.textColor() else com.nam.novelreader.feature.components.VBookTheme.textColor().copy(alpha = 0.5f)
                                    ) 
                                },
                                leadingContent = { 
                                    Icon(
                                        Icons.Default.Description, 
                                        contentDescription = null, 
                                        tint = if (isDownloaded) SecondaryAccent else SecondaryAccent.copy(alpha = 0.5f)
                                    ) 
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable {
                                    if (isDownloaded) {
                                        viewModel.exportNovelAsTxt(context, novel.url)
                                        showExportMenu = false
                                        selectedNovelForAction = null
                                    } else {
                                        Toast.makeText(context, "Chỉ truyện đã tải offline xong mới có thể xuất file!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            ListItem(
                                headlineContent = { 
                                    Text(
                                        "Xuất sách điện tử (EPUB)", 
                                        color = if (isDownloaded) com.nam.novelreader.feature.components.VBookTheme.textColor() else com.nam.novelreader.feature.components.VBookTheme.textColor().copy(alpha = 0.5f)
                                    ) 
                                },
                                leadingContent = { 
                                    Icon(
                                        Icons.Default.Book, 
                                        contentDescription = null, 
                                        tint = if (isDownloaded) SecondaryAccent else SecondaryAccent.copy(alpha = 0.5f)
                                    ) 
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable {
                                    if (isDownloaded) {
                                        viewModel.exportNovelAsEpub(context, novel.url)
                                        showExportMenu = false
                                        selectedNovelForAction = null
                                    } else {
                                        Toast.makeText(context, "Chỉ truyện đã tải offline xong mới có thể xuất file!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            ListItem(
                                headlineContent = { 
                                    Text(
                                        "Sao lưu dữ liệu cấu trúc (JSON)", 
                                        color = if (isDownloaded) com.nam.novelreader.feature.components.VBookTheme.textColor() else com.nam.novelreader.feature.components.VBookTheme.textColor().copy(alpha = 0.5f)
                                    ) 
                                },
                                leadingContent = { 
                                    Icon(
                                        Icons.Default.Save, 
                                        contentDescription = null, 
                                        tint = if (isDownloaded) SecondaryAccent else SecondaryAccent.copy(alpha = 0.5f)
                                    ) 
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable {
                                    if (isDownloaded) {
                                        viewModel.backupNovel(context, novel.url)
                                        showExportMenu = false
                                        selectedNovelForAction = null
                                    } else {
                                        Toast.makeText(context, "Chỉ truyện đã tải offline xong mới có thể xuất file!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showExportMenu = false }) {
                            Text("Hủy", color = GreyText)
                        }
                    }
                )
            }
        }
    }

    if (showCloudSyncDialog) {
        AlertDialog(
            onDismissRequest = { if (!isCloudSyncing) showCloudSyncDialog = false },
            containerColor = CardBg,
            title = {
                Text(
                    text = "Đồng bộ Kho chung",
                    color = com.nam.novelreader.feature.components.VBookTheme.textColor(),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isCloudSyncing) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = PrimaryAccent)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Đang đồng bộ dữ liệu...", color = GreyText, fontSize = 13.sp)
                            }
                        }
                    } else {
                        val messageText = if (isLoggedIn) {
                            "Tài khoản: $email\n\nDữ liệu sẽ được sao lưu/khôi phục từ thư mục cá nhân trên Kho chung Google Drive (Sử dụng cấu hình hệ thống)."
                        } else {
                            "Tài khoản: Khách (Chưa đăng nhập)\n\nDữ liệu sẽ được sao lưu/khôi phục từ thư mục thiết bị riêng biệt trên Kho chung Google Drive (Sử dụng cấu hình hệ thống)."
                        }
                        Text(
                            text = messageText,
                            color = com.nam.novelreader.feature.components.VBookTheme.textColor(),
                            fontSize = 14.sp
                        )
                    }
                }
            },
            confirmButton = {
                if (isCloudSyncing) {
                    // Trống
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    isCloudSyncing = true
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val db = entryPoint.appDatabase()
                                            val cursor = db.openHelper.readableDatabase.query("SELECT * FROM novels WHERE isInLibrary = 1")
                                            val novelsList = mutableListOf<com.nam.novelreader.data.local.entity.NovelEntity>()
                                            
                                            val urlIdx = cursor.getColumnIndex("url")
                                            val titleIdx = cursor.getColumnIndex("title")
                                            val authorIdx = cursor.getColumnIndex("author")
                                            val coverIdx = cursor.getColumnIndex("cover")
                                            val descIdx = cursor.getColumnIndex("description")
                                            val genresIdx = cursor.getColumnIndex("genres")
                                            val statusIdx = cursor.getColumnIndex("status")
                                            val typeIdx = cursor.getColumnIndex("type")
                                            val extIdx = cursor.getColumnIndex("extensionId")
                                            val totalChaptersIdx = cursor.getColumnIndex("totalChapters")
                                            
                                            if (cursor.moveToFirst()) {
                                                do {
                                                    novelsList.add(
                                                        com.nam.novelreader.data.local.entity.NovelEntity(
                                                            url = if (urlIdx != -1) cursor.getString(urlIdx) else "",
                                                            title = if (titleIdx != -1) cursor.getString(titleIdx) else "",
                                                            author = if (authorIdx != -1) cursor.getString(authorIdx) else "",
                                                            cover = if (coverIdx != -1) cursor.getString(coverIdx) else "",
                                                            description = if (descIdx != -1) cursor.getString(descIdx) else "",
                                                            genres = if (genresIdx != -1) cursor.getString(genresIdx) else "[]",
                                                            status = if (statusIdx != -1) cursor.getString(statusIdx) else "",
                                                            type = if (typeIdx != -1) cursor.getString(typeIdx) else "novel",
                                                            extensionId = if (extIdx != -1) cursor.getString(extIdx) else "",
                                                            totalChapters = if (totalChaptersIdx != -1) cursor.getInt(totalChaptersIdx) else 0,
                                                            isInLibrary = true
                                                        )
                                                    )
                                                } while (cursor.moveToNext())
                                            }
                                            cursor.close()
                                            
                                            if (novelsList.isEmpty()) {
                                                withContext(Dispatchers.Main) {
                                                    isCloudSyncing = false
                                                    Toast.makeText(context, "Thư viện rỗng, không có gì để sao lưu!", Toast.LENGTH_SHORT).show()
                                                }
                                                return@launch
                                            }
                                            
                                            val jsonStr = exportToJson(novelsList)
                                            val result = googleDriveBackupManager.backupLibrary(jsonStr)
                                            
                                            withContext(Dispatchers.Main) {
                                                isCloudSyncing = false
                                                if (result.isSuccess) {
                                                    Toast.makeText(context, "Đã sao lưu lên Kho chung thành công!", Toast.LENGTH_LONG).show()
                                                    showCloudSyncDialog = false
                                                } else {
                                                    Toast.makeText(context, "Sao lưu Kho chung lỗi: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                isCloudSyncing = false
                                                Toast.makeText(context, "Lỗi sao lưu: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
                            ) {
                                Icon(Icons.Filled.CloudUpload, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Lưu lên kho", color = Color.White, fontSize = 13.sp)
                            }
                            
                            Button(
                                onClick = {
                                    isCloudSyncing = true
                                    scope.launch(Dispatchers.IO) {
                                        val result = googleDriveBackupManager.restoreLibrary()
                                        withContext(Dispatchers.Main) {
                                            if (result.isSuccess) {
                                                val jsonStr = result.getOrNull() ?: ""
                                                scope.launch(Dispatchers.IO) {
                                                    try {
                                                        val parsedNovels = parseBackupJson(jsonStr)
                                                        if (parsedNovels.isEmpty()) throw Exception("Tệp sao lưu rỗng hoặc không đúng cấu trúc")
                                                        
                                                        val db = entryPoint.appDatabase()
                                                        parsedNovels.forEach { novel ->
                                                            db.novelDao().insert(novel)
                                                        }
                                                        
                                                        withContext(Dispatchers.Main) {
                                                            isCloudSyncing = false
                                                            showCloudSyncDialog = false
                                                            Toast.makeText(context, "Khôi phục thành công ${parsedNovels.size} truyện từ Kho chung!", Toast.LENGTH_LONG).show()
                                                        }
                                                    } catch (e: Exception) {
                                                        withContext(Dispatchers.Main) {
                                                            isCloudSyncing = false
                                                            Toast.makeText(context, "Lỗi giải mã sao lưu: ${e.message}", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                }
                                            } else {
                                                isCloudSyncing = false
                                                Toast.makeText(context, "Khôi phục từ Kho chung lỗi: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
                            ) {
                                Icon(Icons.Filled.CloudDownload, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Nhập về kho", color = Color.White, fontSize = 13.sp)
                            }
                        }
                        TextButton(
                            onClick = { showCloudSyncDialog = false },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Đóng", color = GreyText)
                        }
                    }
                }
            }
        )
    }

    // Dialog Chế độ nhà phát triển (CLI Test Server)
    if (showDevModeDialog) {
        var isRunning by remember {
            mutableStateOf(com.nam.novelreader.service.AndroidTestServerService.isServiceRunning)
        }
        AlertDialog(
            onDismissRequest = { showDevModeDialog = false },
            containerColor = CardBg,
            title = {
                Text(
                    text = "Chế độ nhà phát triển",
                    color = com.nam.novelreader.feature.components.VBookTheme.textColor(),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Dịch vụ Test Server cục bộ cho phép bạn cài đặt và kiểm thử các tiện ích mở rộng (extensions) trực tiếp từ máy tính thông qua VBook CLI.",
                        color = com.nam.novelreader.feature.components.VBookTheme.textColor(),
                        fontSize = 14.sp
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Trạng thái dịch vụ",
                                color = com.nam.novelreader.feature.components.VBookTheme.textColor(),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = if (isRunning) "Đang chạy" else "Đã tắt",
                                color = if (isRunning) PrimaryAccent else GreyText,
                                fontSize = 13.sp
                            )
                        }
                        Switch(
                            checked = isRunning,
                            onCheckedChange = { checked ->
                                isRunning = checked
                                val serviceIntent = Intent(context, AndroidTestServerService::class.java).apply {
                                    action = if (checked) {
                                        AndroidTestServerService.ACTION_START
                                    } else {
                                        AndroidTestServerService.ACTION_STOP
                                    }
                                }
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    context.startForegroundService(serviceIntent)
                                } else {
                                    context.startService(serviceIntent)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                  checkedThumbColor = Color.White,
                                  checkedTrackColor = PrimaryAccent
                            )
                        )
                    }
                    
                    if (isRunning) {
                        val serverUrl = "http://${getLocalIpAddress()}:8080"
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ScreenBg, CircleShape)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "Địa chỉ kết nối:",
                                color = GreyText,
                                fontSize = 12.sp
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = serverUrl,
                                    color = com.nam.novelreader.feature.components.VBookTheme.textColor(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("VBook Test Server", serverUrl)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Đã sao chép liên kết!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ContentCopy,
                                        contentDescription = "Copy",
                                        tint = PrimaryAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDevModeDialog = false }) {
                    Text("Đóng", color = PrimaryAccent)
                }
            }
        )
    }

    // Dialogs for VBook custom imports
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            containerColor = com.nam.novelreader.feature.components.VBookTheme.cardColor(),
            title = { Text("Nhập truyện", color = com.nam.novelreader.feature.components.VBookTheme.textColor(), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CircleShape)
                            .clickable {
                                showImportDialog = false
                                filePickerLauncher.launch("*/*")
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = com.nam.novelreader.feature.components.VBookTheme.primaryColor(), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Nhập từ thiết bị", color = com.nam.novelreader.feature.components.VBookTheme.textColor(), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Text("Hỗ trợ định dạng tệp .txt và .epub", color = com.nam.novelreader.feature.components.VBookTheme.subTextColor(), fontSize = 12.sp)
                        }
                    }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CircleShape)
                            .clickable {
                                showImportDialog = false
                                showUrlDialog = true
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Link, contentDescription = null, tint = com.nam.novelreader.feature.components.VBookTheme.primaryColor(), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Nhập từ URL", color = com.nam.novelreader.feature.components.VBookTheme.textColor(), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Text("Tự động nhận diện và nạp từ link nguồn", color = com.nam.novelreader.feature.components.VBookTheme.subTextColor(), fontSize = 12.sp)
                        }
                    }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CircleShape)
                            .clickable {
                                showImportDialog = false
                                viewModel.loadEnabledExtensions()
                                showBrowserSourceDialog = true
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Language, contentDescription = null, tint = com.nam.novelreader.feature.components.VBookTheme.primaryColor(), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Nhập từ trình duyệt", color = com.nam.novelreader.feature.components.VBookTheme.textColor(), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Text("Mở nguồn truyện trong trình duyệt tích hợp", color = com.nam.novelreader.feature.components.VBookTheme.subTextColor(), fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Đóng", color = com.nam.novelreader.feature.components.VBookTheme.primaryColor())
                }
            }
        )
    }

    if (showUrlDialog) {
        var urlInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            containerColor = com.nam.novelreader.feature.components.VBookTheme.cardColor(),
            title = { Text("Nhập từ URL", color = com.nam.novelreader.feature.components.VBookTheme.textColor(), fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    placeholder = { Text("Dán URL trang chi tiết truyện...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = com.nam.novelreader.feature.components.VBookTheme.primaryColor(),
                        unfocusedBorderColor = com.nam.novelreader.feature.components.VBookTheme.subTextColor().copy(alpha = 0.5f),
                        focusedLabelColor = com.nam.novelreader.feature.components.VBookTheme.primaryColor()
                    )
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showUrlDialog = false }) {
                        Text("Hủy", color = com.nam.novelreader.feature.components.VBookTheme.subTextColor())
                    }
                    Button(
                        onClick = {
                            if (urlInput.isNotBlank()) {
                                viewModel.importFromUrl(context, urlInput.trim())
                                showUrlDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = com.nam.novelreader.feature.components.VBookTheme.primaryColor())
                    ) {
                        Text("Nạp truyện", color = Color.White)
                    }
                }
            }
        )
    }

    if (showBrowserSourceDialog) {
        AlertDialog(
            onDismissRequest = { showBrowserSourceDialog = false },
            containerColor = com.nam.novelreader.feature.components.VBookTheme.cardColor(),
            title = { Text("Chọn nguồn mở trình duyệt", color = com.nam.novelreader.feature.components.VBookTheme.textColor(), fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (viewModel.enabledExtensions.isEmpty()) {
                        Text("Không có nguồn truyện nào được bật. Hãy cài đặt nguồn trước.", color = com.nam.novelreader.feature.components.VBookTheme.subTextColor(), modifier = Modifier.padding(8.dp))
                    } else {
                        for (ext in viewModel.enabledExtensions) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(CircleShape)
                                    .clickable {
                                        showBrowserSourceDialog = false
                                        val encodedUrl = java.net.URLEncoder.encode(ext.source, "UTF-8")
                                        navController.navigate("webview?url=$encodedUrl&extensionId=${ext.id}")
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Extension, contentDescription = null, tint = com.nam.novelreader.feature.components.VBookTheme.primaryColor(), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(ext.cleanName, color = com.nam.novelreader.feature.components.VBookTheme.textColor(), fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBrowserSourceDialog = false }) {
                    Text("Đóng", color = com.nam.novelreader.feature.components.VBookTheme.primaryColor())
                }
            }
        )
    }
}
}

// === Sub-components & UI Renderers ===

@Composable
fun CustomHomeChip(
    label: String,
    selected: Boolean,
    hasDropdown: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) SelectedChipBg else CardBg)
            .border(
                1.dp,
                if (selected) PrimaryAccent else DividerColor,
                CircleShape
            )
            .bounceClick(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                label,
                color = if (selected) SecondaryAccent else com.nam.novelreader.feature.components.VBookTheme.textColor(),
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
            if (hasDropdown) {
                Spacer(modifier = Modifier.width(3.dp))
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = if (selected) SecondaryAccent else com.nam.novelreader.feature.components.VBookTheme.textColor(),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun CustomSubChip(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) SelectedChipBg else Color.Transparent)
            .border(1.dp, if (selected) PrimaryAccent else Color.Transparent, CircleShape)
            .bounceClick(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                label,
                color = if (selected) SecondaryAccent else GreyText,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (selected) PrimaryAccent else CardBg)
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(
                    "$count",
                    fontSize = 10.sp,
                    color = if (selected) com.nam.novelreader.feature.components.VBookTheme.backgroundColor() else GreyText,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun RecentCard(
    novel: RecentNovelWithInfo,
    isLandscape: Boolean,
    onClick: () -> Unit
) {
    val cardWidth = if (isLandscape) 220.dp else 90.dp
    val cardHeight = 130.dp

    Box(
        modifier = Modifier
            .width(cardWidth)
            .height(cardHeight)
            .clip(RoundedCornerShape(12.dp))
            .bounceClick(onClick = onClick)
    ) {
        AsyncImage(
            model = com.nam.novelreader.feature.components.buildNovelImageRequest(novel),
            contentDescription = novel.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Overlay Gradient bóng tối
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(0.85f)),
                        startY = 150f
                    )
                )
        )

        // Văn bản đè
        if (isLandscape) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    novel.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        novel.chapterTitle,
                        fontSize = 10.sp,
                        color = Color.LightGray,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "0%", // Nhãn tiến độ góc dưới phải
                        fontSize = 10.sp,
                        color = SecondaryAccent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(Color.Black.copy(0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        } else {
            // Card portrait nhỏ trong row
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    novel.title,
                    fontSize = 11.sp,
                    color = Color.White,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryGridCard(
    novel: NovelEntity,
    coverSize: Int,
    showProgress: Boolean,
    showTotal: Boolean,
    isDownloaded: Boolean = false,
    currentReadChapter: Int = 0,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .shadow(2.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
        ) {
            AsyncImage(
                model = com.nam.novelreader.feature.components.buildNovelImageRequest(novel),
                contentDescription = novel.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Badge góc dưới phải (Tổng số chương hoặc Đã tải)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(Color.Black.copy(0.6f), RoundedCornerShape(topStart = 6.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                val badgeText = if (isDownloaded) {
                    "Đã tải"
                } else if (novel.totalChapters > 0) {
                    "${novel.totalChapters} Chương"
                } else {
                    "Đang cập nhật"
                }
                Text(badgeText, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        // Tựa truyện (1 dòng)
        Text(
            novel.title,
            color = com.nam.novelreader.feature.components.VBookTheme.textColor(),
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        // Tên tác giả / Tiến độ (1 dòng)
        val subText = if (currentReadChapter > 0) {
            "Đã đọc: $currentReadChapter"
        } else if (novel.author.isNotBlank()) {
            novel.author
        } else {
            novel.extensionId
        }
        Text(
            subText,
            color = GreyText,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryListCard(
    novel: NovelEntity,
    coverSize: Int,
    isCompact: Boolean,
    showProgress: Boolean,
    showTotal: Boolean,
    isDownloaded: Boolean = false,
    currentReadChapter: Int = 0,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val height = if (isCompact) (coverSize * 0.45f).dp else (coverSize * 0.6f).dp
    val imageWidth = if (isCompact) (coverSize * 0.3f).dp else (coverSize * 0.4f).dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = com.nam.novelreader.feature.components.buildNovelImageRequest(novel),
            contentDescription = novel.title,
            modifier = Modifier
                .width(imageWidth)
                .height(height)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                novel.title,
                color = com.nam.novelreader.feature.components.VBookTheme.textColor(),
                fontWeight = FontWeight.Medium,
                fontSize = if (isCompact) 14.sp else 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val isCompleted = novel.status.equals("Hoàn thành", true) || novel.status.equals("Hoàn tất", true) || novel.status.equals("Full", true)
                if (isCompleted) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color(0xFF67B787), // Màu xanh lá cây
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Hoàn tất",
                        color = Color(0xFF67B787),
                        fontSize = 12.sp
                    )
                } else {
                    Text(
                        novel.status.ifBlank { "Đang ra" },
                        color = PrimaryAccent,
                        fontSize = 12.sp
                    )
                }

                if (isDownloaded) {
                    Text(" • ", color = DividerColor)
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color(0xFF67B787),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        "Đã tải",
                        color = Color(0xFF67B787),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                if (showTotal && novel.totalChapters > 0) {
                    Text(" • ", color = DividerColor)
                    Text("$currentReadChapter/${novel.totalChapters}", color = GreyText, fontSize = 12.sp)
                }
            }
        }
        
        // Menu 3 dots bên phải cho List layout
        IconButton(onClick = onLongClick) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Tùy chọn", tint = GreyText)
        }
    }
}

@Composable
fun HistoryCardItem(
    novel: RecentNovelWithInfo,
    onClick: () -> Unit
) {
    val currentReadChapter = extractChapterNumber(novel.chapterTitle) ?: 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(6.dp))
        ) {
            AsyncImage(
                model = com.nam.novelreader.feature.components.buildNovelImageRequest(novel),
                contentDescription = novel.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Nhãn tiến độ góc dưới phải
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(Color.Black.copy(0.6f), RoundedCornerShape(topStart = 6.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                val badgeText = if (novel.totalChapters > 0) "${novel.totalChapters}" else "Đang ra"
                Text(badgeText, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        // Tựa truyện
        Text(
            novel.title,
            color = com.nam.novelreader.feature.components.VBookTheme.textColor(),
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        // Chương đang đọc
        Text(
            novel.chapterTitle.ifBlank { "Chương 1" },
            color = GreyText,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(1.dp))
        // Nguồn truyện
        Text(
            novel.extensionId.replaceFirstChar { it.uppercase() },
            color = GreyText,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun FollowCardItem(
    novel: NovelEntity,
    isFollowed: Boolean,
    onToggleFollow: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .bounceClick(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = com.nam.novelreader.feature.components.buildNovelImageRequest(novel),
                contentDescription = novel.title,
                modifier = Modifier
                    .width(64.dp)
                    .height(84.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    novel.title,
                    fontWeight = FontWeight.Medium,
                    color = com.nam.novelreader.feature.components.VBookTheme.textColor(),
                    fontSize = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    novel.extensionId.ifBlank { "Nguồn đọc" }.replaceFirstChar { it.uppercase() },
                    color = GreyText,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "Cập nhật 1 ngày",
                    color = GreyText,
                    fontSize = 12.sp
                )
            }

            // Nút chuông thông báo (Bell icon có viền vòng tròn)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .border(1.dp, DividerColor, CircleShape)
                    .clickable { onToggleFollow() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isFollowed) Icons.Filled.NotificationsActive else Icons.Filled.Notifications,
                    contentDescription = "Theo dõi",
                    tint = SecondaryAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun LayoutOptionBox(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.bounceClick(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp, 80.dp)
                .clip(CircleShape)
                .background(CardBg)
                .border(
                    if (selected) 2.dp else 1.dp,
                    if (selected) PrimaryAccent else DividerColor,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            title,
            color = if (selected) PrimaryAccent else GreyText,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun MiniLayoutGrid(columns: Int) {
    val color = PrimaryAccent.copy(0.6f)
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(columns) {
                    val w = if (columns == 2) 16.dp else 10.dp
                    val h = if (columns == 2) 22.dp else 15.dp
                    Box(
                        Modifier
                            .size(w, h)
                            .background(color, RoundedCornerShape(2.dp))
                    )
                }
            }
        }
    }
}

@Composable
fun MiniLayoutList(isCompact: Boolean) {
    val color = PrimaryAccent.copy(0.6f)
    val spacing = if (isCompact) 3.dp else 4.dp
    val count = if (isCompact) 4 else 3
    Column(
        verticalArrangement = Arrangement.spacedBy(spacing),
        modifier = Modifier.padding(4.dp)
    ) {
        repeat(count) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val size = if (isCompact) 8.dp else 12.dp
                Box(
                    Modifier
                        .size(size)
                        .background(color, RoundedCornerShape(1.dp))
                )
                val lineW = if (isCompact) 32.dp else 24.dp
                Box(
                    Modifier
                        .size(lineW, 3.dp)
                        .background(color)
                )
            }
        }
    }
}

@Composable
fun LocalSearchScreen(
    recentlyRead: List<RecentNovelWithInfo>,
    allLibrary: List<NovelEntity>,
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onNovelClick: (String, String) -> Unit
) {
    val filteredLibrary = remember(allLibrary, query) {
        if (query.isBlank()) emptyList()
        else allLibrary.filter {
            it.title.contains(query, ignoreCase = true) || it.author.contains(query, ignoreCase = true)
        }
    }
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
    ) {
        // Top Search Bar (Image 9)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Trở lại", tint = SecondaryAccent)
            }
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Nhập nội dung tìm kiếm", color = GreyText) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = com.nam.novelreader.feature.components.VBookTheme.textColor(),
                    unfocusedTextColor = com.nam.novelreader.feature.components.VBookTheme.textColor()
                ),
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() })
            )
            IconButton(onClick = {}) {
                Icon(Icons.Filled.Forum, contentDescription = "Thông báo", tint = SecondaryAccent)
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(SecondaryAccent)
            )
        }

        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(DividerColor)
        )

        if (query.isBlank()) {
            // "Đọc gần đây" (Image 9)
            Text(
                "Đọc gần đây",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = com.nam.novelreader.feature.components.VBookTheme.textColor(),
                modifier = Modifier.padding(16.dp)
            )
            if (recentlyRead.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Chưa có lịch sử đọc gần đây", color = GreyText)
                }
            } else {
                recentlyRead.forEach { novel ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNovelClick(novel.extensionId, novel.novelUrl) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = com.nam.novelreader.feature.components.buildNovelImageRequest(novel),
                            contentDescription = novel.title,
                            modifier = Modifier
                                .size(36.dp, 54.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(novel.title, fontWeight = FontWeight.Bold, color = com.nam.novelreader.feature.components.VBookTheme.textColor(), fontSize = 14.sp)
                            Text(novel.chapterTitle.ifBlank { "Chương 1" }, color = GreyText, fontSize = 12.sp)
                        }
                    }
                }
            }
        } else {
            // Kết quả tìm kiếm
            Text(
                "Kết quả tìm kiếm",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = com.nam.novelreader.feature.components.VBookTheme.textColor(),
                modifier = Modifier.padding(16.dp)
            )
            if (filteredLibrary.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Không tìm thấy kết quả nào trong thư viện", color = GreyText)
                }
            } else {
                filteredLibrary.forEach { novel ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNovelClick(novel.extensionId, novel.url) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = com.nam.novelreader.feature.components.buildNovelImageRequest(novel),
                            contentDescription = novel.title,
                            modifier = Modifier
                                .size(36.dp, 54.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(novel.title, fontWeight = FontWeight.Bold, color = com.nam.novelreader.feature.components.VBookTheme.textColor(), fontSize = 14.sp)
                            Text(novel.author, color = GreyText, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

// Group history logic
private fun groupHistoryByDay(historyList: List<RecentNovelWithInfo>): Map<String, List<RecentNovelWithInfo>> {
    val groups = LinkedHashMap<String, MutableList<RecentNovelWithInfo>>()
    val now = System.currentTimeMillis()
    val dayMs = 24 * 60 * 60 * 1000L

    for (item in historyList) {
        val diff = now - item.readAt
        val dayKey = when {
            diff < dayMs -> "Hôm nay"
            diff < 2 * dayMs -> "Hôm qua"
            else -> "Trước đây"
        }
        groups.getOrPut(dayKey) { mutableListOf() }.add(item)
    }
    return groups
}

fun extractChapterNumber(title: String?): Int? {
    if (title.isNullOrBlank()) return null
    val chapterRegex = Regex("""(?i)(?:chương|chapter|ch)\s*[:.-]?\s*(\d+)""")
    val match = chapterRegex.find(title)
    if (match != null) {
        return match.groupValues[1].toIntOrNull()
    }
    val numberRegex = Regex("""\d+""")
    val numMatch = numberRegex.find(title)
    return numMatch?.value?.toIntOrNull()
}

private fun getLocalIpAddress(): String {
    try {
        val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            val addresses = networkInterface.inetAddresses
            while (addresses.hasMoreElements()) {
                val address = addresses.nextElement()
                if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                    return address.hostAddress ?: "127.0.0.1"
                }
            }
        }
    } catch (_: Exception) {}
    return "127.0.0.1"
}

private fun String.escapeJson(): String {
    return this.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
}

private fun String.unescapeJson(): String {
    return this.replace("\\\\", "\\").replace("\\\"", "\"").replace("\\n", "\n").replace("\\r", "\r")
}

private fun exportToJson(novels: List<com.nam.novelreader.data.local.entity.NovelEntity>): String {
    val sb = StringBuilder()
    sb.append("[\n")
    novels.forEachIndexed { idx, novel ->
        sb.append("  {\n")
        sb.append("    \"url\": \"${novel.url.escapeJson()}\",\n")
        sb.append("    \"title\": \"${novel.title.escapeJson()}\",\n")
        sb.append("    \"author\": \"${novel.author.escapeJson()}\",\n")
        sb.append("    \"cover\": \"${novel.cover.escapeJson()}\",\n")
        sb.append("    \"description\": \"${novel.description.escapeJson()}\",\n")
        sb.append("    \"genres\": \"${novel.genres.escapeJson()}\",\n")
        sb.append("    \"status\": \"${novel.status.escapeJson()}\",\n")
        sb.append("    \"type\": \"${novel.type.escapeJson()}\",\n")
        sb.append("    \"extensionId\": \"${novel.extensionId.escapeJson()}\",\n")
        sb.append("    \"totalChapters\": ${novel.totalChapters}\n")
        sb.append("  }")
        if (idx < novels.size - 1) sb.append(",")
        sb.append("\n")
    }
    sb.append("]")
    return sb.toString()
}

private fun parseBackupJson(json: String): List<com.nam.novelreader.data.local.entity.NovelEntity> {
    val list = mutableListOf<com.nam.novelreader.data.local.entity.NovelEntity>()
    val objRegex = Regex("\\{\\s*\"url\":\\s*\"(.*?)\",\\s*\"title\":\\s*\"(.*?)\",\\s*\"author\":\\s*\"(.*?)\",\\s*\"cover\":\\s*\"(.*?)\",\\s*\"description\":\\s*\"(.*?)\",\\s*\"genres\":\\s*\"(.*?)\",\\s*\"status\":\\s*\"(.*?)\",\\s*\"type\":\\s*\"(.*?)\",\\s*\"extensionId\":\\s*\"(.*?)\",\\s*\"totalChapters\":\\s*(\\d+)\\s*\\}")
    
    val cleanedJson = json.replace("\n", " ").replace("\r", " ")
    val matches = objRegex.findAll(cleanedJson)
    for (match in matches) {
        val groups = match.groupValues
        list.add(
            com.nam.novelreader.data.local.entity.NovelEntity(
                url = groups[1].unescapeJson(),
                title = groups[2].unescapeJson(),
                author = groups[3].unescapeJson(),
                cover = groups[4].unescapeJson(),
                description = groups[5].unescapeJson(),
                genres = groups[6].unescapeJson(),
                status = groups[7].unescapeJson(),
                type = groups[8].unescapeJson(),
                extensionId = groups[9].unescapeJson(),
                totalChapters = groups[10].toInt(),
                isInLibrary = true,
                addedToLibraryAt = System.currentTimeMillis()
            )
        )
    }
    return list
}

