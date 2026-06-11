package com.nam.novelreader.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.nam.novelreader.data.local.dao.*
import com.nam.novelreader.data.local.entity.*
import com.nam.novelreader.domain.model.*
import com.nam.novelreader.extension.loader.ExtensionLoader
import com.nam.novelreader.extension.model.*
import com.nam.novelreader.extension.runtime.VBookJsExtensionRunner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.serializer
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NovelRepository – trung tâm data access.
 * Kết nối Extension Engine — Room Database — UI.
 */
@Singleton
class NovelRepository @Inject constructor(
    @ApplicationContext val context: Context,
    private val novelDao: NovelDao,
    private val chapterDao: ChapterDao,
    private val extensionDao: ExtensionDao,
    private val readingHistoryDao: ReadingHistoryDao,
    private val downloadTaskDao: DownloadTaskDao,
    private val extensionLoader: ExtensionLoader,
    private val extensionRunner: VBookJsExtensionRunner,
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private suspend fun translateTextIfNeeded(extId: String, text: String?, fieldType: String): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        if (text.isNullOrBlank()) return@withContext text
        val prefs = context.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE)
        val globalAutoTranslate = prefs.getBoolean("translation_auto_translate", true)
        if (!globalAutoTranslate) return@withContext text

        val translationMode = prefs.getString("ext_translation_mode_$extId", "Gốc") ?: "Gốc"
        if (translationMode == "Gốc") return@withContext text

        val engine = prefs.getString("ext_translate_engine_$extId", "QT") ?: "QT"
        if (engine != "QT") return@withContext text

        val isNovelMetadataField = fieldType in listOf("title", "author", "introduce", "genre")
        val shouldTranslate = if (isNovelMetadataField) {
            true
        } else {
            val scope = prefs.getString("ext_translate_scope_$extId", "Tất cả") ?: "Tất cả"
            scope == "Tất cả" || scope == "Nội dung"
        }

        if (!shouldTranslate) return@withContext text
        val targetMode = if (translationMode.contains("Hán Việt")) "hanviet" else "vi"
        return@withContext com.nam.novelreader.util.QuickTranslateEngine.translate(context, text, targetMode)
    }

    // ========== Library ==========

    fun getLibraryNovels(): Flow<List<Novel>> {
        return novelDao.getLibraryNovels().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun addToLibrary(novel: Novel) {
        if (novel.url.isBlank()) {
            android.util.Log.e("NovelRepo", "Cannot add novel to library: URL is empty!")
            return
        }
        val existing = novelDao.getNovelByUrl(novel.url)
        if (existing != null) {
            if (existing.extensionId.isBlank() && novel.extensionId.isNotBlank()) {
                novelDao.insert(existing.copy(isInLibrary = true, addedToLibraryAt = System.currentTimeMillis(), extensionId = novel.extensionId))
            } else {
                novelDao.updateLibraryStatus(novel.url, true, System.currentTimeMillis())
            }
        } else {
            novelDao.insert(novel.toEntity().copy(isInLibrary = true, addedToLibraryAt = System.currentTimeMillis()))
        }
    }

    suspend fun removeFromLibrary(novelUrl: String) {
        novelDao.deleteByUrl(novelUrl)
        chapterDao.deleteChaptersForNovel(novelUrl)
        readingHistoryDao.deleteForNovel(novelUrl)
        downloadTaskDao.delete(novelUrl)
    }

    suspend fun isInLibrary(novelUrl: String): Boolean {
        return novelDao.getNovelByUrl(novelUrl)?.isInLibrary == true
    }

    suspend fun getNovelFromDb(novelUrl: String): Novel? {
        return novelDao.getNovelByUrl(novelUrl)?.toDomain()
    }

    // ========== Extension Execution ==========

    suspend fun getExtension(extensionId: String): LoadedExtension? {
        return extensionLoader.loadExtension(extensionId)
    }

    fun getInstalledExtensions(): Flow<List<ExtensionEntity>> {
        return extensionDao.getInstalledExtensions()
    }

    /**
     * Thực thi extension script và parse kết quả thành domain models.
     */
    suspend fun executeExtension(
        extensionId: String,
        scriptType: ScriptType,
        vararg args: String,
    ): ExtensionResult {
        android.util.Log.d("NovelRepo", "executeExtension: id=$extensionId, type=${scriptType.key}, args=${args.toList()}")
        val extension = extensionLoader.loadExtension(extensionId)
        if (extension == null) {
            android.util.Log.e("NovelRepo", "Extension not found: $extensionId")
            return ExtensionResult.Error("Extension not found: $extensionId")
        }
        android.util.Log.d("NovelRepo", "Extension loaded: ${extension.pluginJson.metadata.name}, scripts: ${extension.pluginJson.script.keys}")
        return extensionRunner.execute(extension, scriptType, *args)
    }

    /**
     * Tìm kiếm truyện qua extension.
     */
    suspend fun searchNovels(extensionId: String, query: String, pageKey: String? = null): SearchResult {
        val extension = extensionLoader.loadExtension(extensionId)
        val sourceUrl = extension?.pluginJson?.metadata?.source ?: ""
        val result = executeExtension(extensionId, ScriptType.SEARCH, query, pageKey ?: "1")
        return when (result) {
            is ExtensionResult.Success -> parseSearchResult(result.data, extensionId, sourceUrl)
            is ExtensionResult.Error -> throw Exception(result.message)
        }
    }

    /**
     * Lấy trang chủ extension.
     */
    suspend fun getHome(extensionId: String, page: Int = 1): List<HomeTab> {
        val extension = extensionLoader.loadExtension(extensionId)
        val sourceUrl = extension?.pluginJson?.metadata?.source ?: ""
        val result = executeExtension(extensionId, ScriptType.HOME, page.toString())
        return when (result) {
            is ExtensionResult.Success -> {
                android.util.Log.d("NovelRepo", "getHome success, data length: ${result.data.length}")
                android.util.Log.d("NovelRepo", "getHome data preview: ${result.data.take(500)}")
                parseHomeTabs(result.data, extensionId, sourceUrl)
            }
            is ExtensionResult.Error -> {
                android.util.Log.e("NovelRepo", "getHome error: ${result.message}")
                throw Exception(result.message)
            }
        }
    }

    /**
     * Lấy danh sách thể loại từ extension.
     */
    suspend fun getGenres(extensionId: String): List<HomeTab> {
        val extension = extensionLoader.loadExtension(extensionId) ?: return emptyList()
        val sourceUrl = extension.pluginJson.metadata.source ?: ""
        val hasGenreScript = extension.pluginJson.script.containsKey(ScriptType.GENRE.key)
        if (!hasGenreScript) {
            android.util.Log.d("NovelRepo", "Extension $extensionId does not support genre script, skipping.")
            return emptyList()
        }
        val result = executeExtension(extensionId, ScriptType.GENRE)
        return when (result) {
            is ExtensionResult.Success -> {
                android.util.Log.d("NovelRepo", "getGenres success, data length: ${result.data.length}")
                parseHomeTabs(result.data, extensionId, sourceUrl)
            }
            is ExtensionResult.Error -> {
                android.util.Log.e("NovelRepo", "getGenres error: ${result.message}")
                throw Exception(result.message)
            }
        }
    }

    /**
     * Lấy chi tiết truyện.
     */
    suspend fun getNovelDetail(extensionId: String, novelUrl: String): Novel? {
        val extension = extensionLoader.loadExtension(extensionId)
        val sourceUrl = extension?.pluginJson?.metadata?.source ?: ""
        val result = executeExtension(extensionId, ScriptType.DETAIL, novelUrl)
        return when (result) {
            is ExtensionResult.Success -> {
                val parsed = parseNovelDetail(result.data, extensionId, sourceUrl)
                if (parsed != null) {
                    if (parsed.url.isBlank()) {
                        parsed.copy(url = novelUrl)
                    } else {
                        parsed
                    }
                } else null
            }
            is ExtensionResult.Error -> throw Exception(result.message)
        }
    }

    /**
     * Lấy danh sách chương.
     */
    suspend fun getTableOfContents(extensionId: String, novelUrl: String, page: Int = 1): List<Chapter> {
        val extension = extensionLoader.loadExtension(extensionId)
        val sourceUrl = extension?.pluginJson?.metadata?.source ?: ""

        if (extension != null && extension.pluginJson.script.containsKey("page")) {
            // Trường hợp 1: Tiện ích có phân trang mục lục (sử dụng page.js)
            android.util.Log.d("NovelRepo", "Fetching Table of Contents using page.js for extension $extensionId")
            val pageResult = executeExtension(extensionId, ScriptType.PAGE, novelUrl)
            return when (pageResult) {
                is ExtensionResult.Success -> {
                    try {
                        val jsonObject = org.json.JSONObject(pageResult.data)
                        val jsonArray = jsonObject.optJSONArray("data") ?: org.json.JSONArray()
                        val urls = mutableListOf<String>()
                        for (i in 0 until jsonArray.length()) {
                            urls.add(jsonArray.getString(i))
                        }
                        
                        android.util.Log.d("NovelRepo", "page.js returned ${urls.size} page URLs")
                        
                        val allChapters = mutableListOf<Chapter>()
                        // Lần lượt tải danh sách chương của từng trang
                        for (ajaxUrl in urls) {
                            android.util.Log.d("NovelRepo", "Fetching TOC page: $ajaxUrl")
                            val tocResult = executeExtension(extensionId, ScriptType.TOC, ajaxUrl)
                            if (tocResult is ExtensionResult.Success) {
                                val chapters = parseChapters(tocResult.data, extensionId, sourceUrl)
                                allChapters.addAll(chapters)
                            } else if (tocResult is ExtensionResult.Error) {
                                android.util.Log.e("NovelRepo", "Failed to fetch TOC page: $ajaxUrl. Error: ${tocResult.message}")
                            }
                        }
                        
                        // Đánh số lại chỉ số index cho các chương để đảm bảo trật tự tăng dần liên tục
                        allChapters.mapIndexed { index, chapter ->
                            chapter.copy(index = index)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("NovelRepo", "Failed to parse page.js output: ${e.message}", e)
                        emptyList()
                    }
                }
                is ExtensionResult.Error -> throw Exception(pageResult.message)
            }
        } else {
            // Trường hợp 2: Tiện ích không sử dụng phân trang
            val result = executeExtension(extensionId, ScriptType.TOC, novelUrl, page.toString())
            return when (result) {
                is ExtensionResult.Success -> parseChapters(result.data, extensionId, sourceUrl)
                is ExtensionResult.Error -> throw Exception(result.message)
            }
        }
    }

    /**
     * Lấy nội dung 1 chương.
     */
    suspend fun getChapterContent(extensionId: String, chapterUrl: String, isOfflineDownload: Boolean = false): Chapter? {
        // Check cache trước
        val cached = chapterDao.getChapterByUrl(chapterUrl)
        var chapter = if ((cached?.isDownloaded == true || !cached?.content.isNullOrBlank()) && cached.content != null) {
            Chapter(
                url = cached.url,
                title = cached.title,
                index = cached.index,
                content = cached.content,
                images = cached.images?.let { Json.decodeFromString(it) },
                isDownloaded = cached.isDownloaded,
            )
        } else {
            val result = executeExtension(extensionId, ScriptType.CHAP, chapterUrl)
            when (result) {
                is ExtensionResult.Success -> {
                    val extension = extensionLoader.loadExtension(extensionId)
                    val isVideo = extension?.pluginJson?.metadata?.type == "video"
                    
                    var parsedChapter = if (isVideo) {
                        // Nếu là video, tạo Chapter trực tiếp với content là JSON string của các server (result.data)
                        Chapter(
                            url = chapterUrl,
                            title = cached?.title ?: "Tập phim",
                            content = result.data,
                            isDownloaded = isOfflineDownload
                        )
                    } else {
                        parseChapterContent(result.data, extensionId, chapterUrl)
                    }
                    
                    if (parsedChapter != null && parsedChapter.title.isBlank() && cached != null) {
                        parsedChapter = parsedChapter.copy(title = cached.title)
                    }

                    if (parsedChapter != null && (!parsedChapter.content.isNullOrBlank() || !parsedChapter.images.isNullOrEmpty())) {
                        val imagesJson = parsedChapter.images?.let { json.encodeToString(it) }
                        val shouldMarkDownloaded = (cached?.isDownloaded == true) || isOfflineDownload
                        chapterDao.saveContent(chapterUrl, parsedChapter.content, imagesJson, shouldMarkDownloaded, System.currentTimeMillis())
                    }
                    parsedChapter
                }
                is ExtensionResult.Error -> throw Exception(result.message)
            }
        }

        // Thực hiện dịch động (on-demand) khi trả về cho UI
        if (chapter != null) {
            val translatedTitle = translateTextIfNeeded(extensionId, chapter.title, "chapter_title") ?: chapter.title
            val translatedContent = translateTextIfNeeded(extensionId, chapter.content, "chapter_content")
            chapter = chapter.copy(title = translatedTitle, content = translatedContent)
        }
        return chapter
    }

    suspend fun getChapterContentOffline(chapterUrl: String): Chapter? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val cached = chapterDao.getChapterByUrl(chapterUrl) ?: return@withContext null
        if (cached.content.isNullOrBlank() && cached.images.isNullOrBlank()) {
            return@withContext null
        }
        var chapter = Chapter(
            url = cached.url,
            title = cached.title,
            index = cached.index,
            content = cached.content,
            images = cached.images?.let {
                try {
                    Json.decodeFromString(it)
                } catch (e: Exception) {
                    null
                }
            },
            isDownloaded = cached.isDownloaded
        )
        val novel = novelDao.getNovelByUrl(cached.novelUrl)
        val extId = novel?.extensionId ?: ""
        if (extId.isNotBlank()) {
            val translatedTitle = translateTextIfNeeded(extId, chapter.title, "chapter_title") ?: chapter.title
            val translatedContent = translateTextIfNeeded(extId, chapter.content, "chapter_content")
            chapter = chapter.copy(title = translatedTitle, content = translatedContent)
        }
        chapter
    }

    // ========== Reading Progress ==========

    suspend fun updateNovelMetadata(novel: Novel) {
        if (novel.url.isBlank()) {
            android.util.Log.e("NovelRepo", "Cannot update novel metadata: URL is empty!")
            return
        }
        val existing = novelDao.getNovelByUrl(novel.url)
        if (existing != null) {
            val extId = if (existing.extensionId.isBlank()) novel.extensionId else existing.extensionId
            novelDao.insert(
                novel.toEntity().copy(
                    extensionId = extId,
                    isInLibrary = existing.isInLibrary,
                    addedToLibraryAt = existing.addedToLibraryAt,
                    lastReadChapterUrl = existing.lastReadChapterUrl,
                    lastReadAt = existing.lastReadAt,
                    scrollPosition = existing.scrollPosition
                )
            )
        } else {
            novelDao.insert(novel.toEntity().copy(isInLibrary = false))
        }
    }

    suspend fun updateReadProgress(novelUrl: String, extensionId: String, chapterUrl: String, chapterTitle: String, scroll: Int) {
        if (novelUrl.isBlank()) return
        var novel = novelDao.getNovelByUrl(novelUrl)
        if (novel == null) {
            val skeleton = NovelEntity(
                url = novelUrl,
                title = "Truyện chưa rõ tên",
                extensionId = extensionId,
                isInLibrary = false
            )
            novelDao.insert(skeleton)
            novel = skeleton
        }
        val extId = extensionId.ifBlank { novel.extensionId }
        novelDao.updateReadProgress(novelUrl, chapterUrl, System.currentTimeMillis(), scroll)
        
        val prefs = context.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE)
        val saveHistory = prefs.getBoolean("home_save_history", true)
        val isIncognito = prefs.getBoolean("ext_incognito_mode_$extId", false)
        if (saveHistory && !isIncognito) {
            readingHistoryDao.insert(
                ReadingHistoryEntity(
                    novelUrl = novelUrl,
                    chapterUrl = chapterUrl,
                    chapterTitle = chapterTitle,
                    scrollPosition = scroll,
                    readAt = System.currentTimeMillis(),
                    extensionId = extId
                )
            )
        }
    }

    fun getReadingHistory(): Flow<List<ReadingHistoryEntity>> {
        return readingHistoryDao.getRecentHistory()
    }

    fun getRecentHistoryWithInfo(limit: Int = 10): Flow<List<RecentNovelWithInfo>> {
        return readingHistoryDao.getRecentHistoryWithInfo(limit)
    }

    // ========== Chapters Cache ==========

    suspend fun cacheChapters(novelUrl: String, chapters: List<Chapter>) {
        chapterDao.insertOrUpdateChapters(chapters.mapIndexed { index, ch ->
            ChapterEntity(
                url = ch.url,
                novelUrl = novelUrl,
                title = ch.title,
                index = index,
            )
        })
    }

    suspend fun updateChapterTitle(chapterUrl: String, title: String) {
        chapterDao.updateChapterTitle(chapterUrl, title)
    }

    fun getCachedChapters(novelUrl: String): Flow<List<ChapterEntity>> {
        return chapterDao.getChaptersForNovel(novelUrl)
    }

    suspend fun getChapterList(novelUrl: String): List<ChapterEntity> {
        return chapterDao.getChaptersForNovelSync(novelUrl)
    }

    suspend fun getDownloadedChapterUrls(novelUrl: String): List<String> {
        return chapterDao.getDownloadedChapterUrls(novelUrl)
    }

    // ========== JSON Parsing Helpers ==========

    private fun checkExtensionErrorCode(element: JsonElement) {
        if (element is JsonObject) {
            val code = element["code"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            if (code != 0) {
                val errorMsg = element["data"]?.jsonPrimitive?.content 
                    ?: element["message"]?.jsonPrimitive?.content 
                    ?: "Lỗi tải dữ liệu từ extension"
                throw Exception("Lỗi từ extension: $errorMsg")
            }
        }
    }

    private suspend fun parseSearchResult(jsonStr: String, extensionId: String, sourceUrl: String): SearchResult = withContext(Dispatchers.Default) {
        try {
            val element = json.parseToJsonElement(jsonStr)
            checkExtensionErrorCode(element)
            val items = if (element is JsonArray) {
                element
            } else {
                element.jsonObject["items"]?.jsonArray 
                    ?: element.jsonObject["data"]?.jsonArray 
                    ?: JsonArray(emptyList())
            }
            val novels = items.map { parseNovelFromJson(it.jsonObject, extensionId, sourceUrl) }
            val hasNext = if (element is JsonObject) {
                element["hasNextPage"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() 
                    ?: element["hasNext"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
                    ?: (novels.isNotEmpty())
            } else {
                novels.isNotEmpty()
            }
            val nextPageKey = if (element is JsonObject) {
                element["data2"]?.jsonPrimitive?.content?.takeIf { it != "null" }
            } else {
                null
            }
            SearchResult(novels, hasNext, nextPageKey = nextPageKey)
        } catch (e: Exception) {
            android.util.Log.e("NovelRepo", "parseSearchResult failed: ${e.message}", e)
            throw e
        }
    }

    private suspend fun parseHomeTabs(jsonStr: String, extensionId: String, sourceUrl: String): List<HomeTab> = withContext(Dispatchers.Default) {
        try {
            val element = json.parseToJsonElement(jsonStr)
            checkExtensionErrorCode(element)
            
            if (element is JsonArray) {
                parseTabsJsonArray(element, extensionId)
            } else {
                val obj = element.jsonObject
                val dataElement = obj["data"]
                
                if (dataElement is JsonArray) {
                    // Kiểm tra xem đây là mảng các tab hay mảng các truyện
                    val firstItem = dataElement.firstOrNull()?.jsonObject
                    val isTabArray = firstItem != null && (firstItem.containsKey("input") || firstItem.containsKey("script"))
                    
                    if (isTabArray) {
                        parseTabsJsonArray(dataElement, extensionId)
                    } else {
                        // Đây là mảng các truyện (novel list)
                        listOf(
                            HomeTab(
                                name = "Truyện",
                                novels = dataElement.map { parseNovelFromJson(it.jsonObject, extensionId, sourceUrl) }
                            )
                        )
                    }
                } else {
                    // Cấu trúc cũ hoặc single list
                    val items = obj["items"]?.jsonArray 
                        ?: obj["data"]?.jsonObject?.get("items")?.jsonArray
                        ?: return@withContext emptyList()
                    listOf(
                        HomeTab(
                            name = "Truyện",
                            novels = items.map { parseNovelFromJson(it.jsonObject, extensionId, sourceUrl) }
                        )
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("NovelRepo", "parseHomeTabs failed: ${e.message}", e)
            throw e
        }
    }

    private suspend fun parseTabsJsonArray(array: JsonArray, extensionId: String): List<HomeTab> {
        return array.map { tabObj ->
            val obj = tabObj.jsonObject
            val rawName = obj["title"]?.jsonPrimitive?.content 
                ?: obj["name"]?.jsonPrimitive?.content 
                ?: ""
            val name = translateTextIfNeeded(extensionId, rawName, "genre") ?: rawName
            HomeTab(
                name = name,
                input = obj["input"]?.jsonPrimitive?.content ?: "",
                script = obj["script"]?.jsonPrimitive?.content ?: "",
            )
        }
    }

    private suspend fun parseNovelsPage(jsonStr: String, extensionId: String, sourceUrl: String): NovelPage = withContext(Dispatchers.Default) {
        try {
            val element = json.parseToJsonElement(jsonStr)
            checkExtensionErrorCode(element)
            if (element is JsonArray) {
                val novels = element.map { parseNovelFromJson(it.jsonObject, extensionId, sourceUrl) }
                NovelPage(novels, null)
            } else {
                val obj = element.jsonObject
                val itemsElement = obj["data"]
                val novels = when {
                    itemsElement is JsonArray -> {
                        itemsElement.map { parseNovelFromJson(it.jsonObject, extensionId, sourceUrl) }
                    }
                    itemsElement is JsonObject && itemsElement.containsKey("items") -> {
                        itemsElement["items"]?.jsonArray?.map { parseNovelFromJson(it.jsonObject, extensionId, sourceUrl) } ?: emptyList()
                    }
                    else -> {
                        val fallbackItems = obj["items"]?.jsonArray 
                            ?: obj["list"]?.jsonArray 
                            ?: emptyList()
                        fallbackItems.map { parseNovelFromJson(it.jsonObject, extensionId, sourceUrl) }
                    }
                }
                val nextPageKey = obj["data2"]?.jsonPrimitive?.content?.takeIf { it != "null" }
                NovelPage(novels, nextPageKey)
            }
        } catch (e: Exception) {
            android.util.Log.e("NovelRepo", "parseNovelsPage failed: ${e.message}", e)
            throw e
        }
    }

    suspend fun getHomeTabNovels(
        extensionId: String,
        tabScript: String,
        tabInput: String,
        pageKey: String? = null
    ): NovelPage {
        android.util.Log.d("NovelRepo", "getHomeTabNovels called: id=$extensionId, tabScript=$tabScript, tabInput=$tabInput, pageKey=$pageKey")
        val extension = extensionLoader.loadExtension(extensionId) ?: run {
            android.util.Log.w("NovelRepo", "getHomeTabNovels: extension not found!")
            return NovelPage(emptyList(), null)
        }
        val sourceUrl = extension.pluginJson.metadata.source
        val pageParam = pageKey ?: "1"
        val result = extensionRunner.execute(extension, tabScript, tabInput, pageParam)
        return when (result) {
            is ExtensionResult.Success -> {
                val parsed = parseNovelsPage(result.data, extensionId, sourceUrl)
                android.util.Log.d("NovelRepo", "getHomeTabNovels success: parsed ${parsed.novels.size} novels")
                parsed
            }
            is ExtensionResult.Error -> {
                android.util.Log.e("NovelRepo", "getHomeTabNovels error: ${result.message}")
                throw Exception(result.message)
            }
        }
    }

    private suspend fun parseNovelsList(jsonStr: String, extensionId: String, sourceUrl: String): List<Novel> = withContext(Dispatchers.Default) {
        try {
            val element = json.parseToJsonElement(jsonStr)
            checkExtensionErrorCode(element)
            val items = if (element is JsonArray) {
                element
            } else {
                element.jsonObject["items"]?.jsonArray ?: element.jsonObject["data"]?.jsonArray ?: return@withContext emptyList()
            }
            items.map { parseNovelFromJson(it.jsonObject, extensionId, sourceUrl) }
        } catch (e: Exception) {
            android.util.Log.e("NovelRepo", "parseNovelsList failed: ${e.message}", e)
            throw e
        }
    }

    private suspend fun parseNovelDetail(jsonStr: String, extensionId: String, sourceUrl: String): Novel? = withContext(Dispatchers.Default) {
        try {
            val element = json.parseToJsonElement(jsonStr)
            checkExtensionErrorCode(element)
            val rootObj = element.jsonObject
            val dataObj = rootObj["data"]
            val targetObj = if (dataObj is JsonObject) {
                dataObj
            } else {
                rootObj
            }
            parseNovelFromJson(targetObj, extensionId, sourceUrl)
        } catch (e: Exception) {
            android.util.Log.e("NovelRepo", "parseNovelDetail failed: ${e.message}", e)
            throw e
        }
    }

    private suspend fun parseChapters(jsonStr: String, extensionId: String, sourceUrl: String): List<Chapter> = withContext(Dispatchers.Default) {
        try {
            val element = json.parseToJsonElement(jsonStr)
            checkExtensionErrorCode(element)
            val items = if (element is JsonArray) {
                element
            } else {
                element.jsonObject["items"]?.jsonArray 
                    ?: element.jsonObject["data"]?.jsonArray 
                    ?: element.jsonObject["chapters"]?.jsonArray 
                    ?: element.jsonObject["list"]?.jsonArray 
                    ?: return@withContext emptyList()
            }
            items.mapIndexed { index, item ->
                val obj = item.jsonObject
                val isSection = obj["type"]?.jsonPrimitive?.content == "section"
                val rawTitle = obj["title"]?.jsonPrimitive?.content 
                    ?: obj["name"]?.jsonPrimitive?.content 
                    ?: "Chương ${index + 1}"
                val title = cleanHtmlText(rawTitle)
                val rawUrl = if (isSection) {
                    "section://$title"
                } else {
                    obj["url"]?.jsonPrimitive?.content 
                        ?: obj["link"]?.jsonPrimitive?.content
                        ?: obj["path"]?.jsonPrimitive?.content
                        ?: ""
                }
                val host = if (isSection) "" else (obj["host"]?.jsonPrimitive?.content ?: "")
                val resolvedUrl = if (isSection) rawUrl else resolveUrl(rawUrl, host, sourceUrl)
                Chapter(
                    url = resolvedUrl,
                    title = title,
                    index = index,
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("NovelRepo", "parseChapters failed: ${e.message}", e)
            throw e
        }
    }

    private suspend fun parseChapterContent(jsonStr: String, extensionId: String, chapterUrl: String): Chapter? = withContext(Dispatchers.Default) {
        try {
            val element = json.parseToJsonElement(jsonStr)
            checkExtensionErrorCode(element)
            if (element is JsonObject) {
                val dataElement = element["data"]
                val targetObj = if (dataElement is JsonObject) dataElement else element
                
                val rawContent = targetObj["content"]?.jsonPrimitive?.content
                    ?: targetObj["text"]?.jsonPrimitive?.content
                    ?: (if (dataElement is JsonPrimitive) dataElement.content else null)
                val content = rawContent
                
                var images = if (dataElement is JsonArray) {
                    dataElement.mapNotNull { 
                        if (it is JsonPrimitive) {
                            it.content
                        } else if (it is JsonObject) {
                            val fallbackUrl = it["fallback"]?.jsonArray?.firstOrNull { f -> f is JsonPrimitive }?.jsonPrimitive?.content
                            val mainUrl = it["link"]?.jsonPrimitive?.content 
                                ?: it["url"]?.jsonPrimitive?.content 
                                ?: it["src"]?.jsonPrimitive?.content
                            if (!fallbackUrl.isNullOrBlank()) fallbackUrl else mainUrl
                        } else {
                            null
                        }
                    }
                } else {
                    targetObj["images"]?.jsonArray?.mapNotNull { 
                        if (it is JsonPrimitive) {
                            it.content
                        } else if (it is JsonObject) {
                            val fallbackUrl = it["fallback"]?.jsonArray?.firstOrNull { f -> f is JsonPrimitive }?.jsonPrimitive?.content
                            val mainUrl = it["link"]?.jsonPrimitive?.content 
                                ?: it["url"]?.jsonPrimitive?.content 
                                ?: it["src"]?.jsonPrimitive?.content
                            if (!fallbackUrl.isNullOrBlank()) fallbackUrl else mainUrl
                        } else {
                            null
                        }
                    }
                }
                
                if (images.isNullOrEmpty() && !content.isNullOrBlank() && content.contains("<img", ignoreCase = true)) {
                    try {
                        val doc = Jsoup.parse(content)
                        val imgElements = doc.select("img")
                        val urls = imgElements.mapNotNull { img ->
                            img.attr("data-original").takeIf { it.isNotBlank() }
                                ?: img.attr("data-src").takeIf { it.isNotBlank() }
                                ?: img.attr("data-lazy-src").takeIf { it.isNotBlank() }
                                ?: img.attr("src").takeIf { it.isNotBlank() && !it.contains("placeholder") && !it.contains("trans.gif") && !it.contains("blank.gif") }
                        }
                        if (urls.isNotEmpty()) {
                            images = urls
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("NovelRepo", "Failed to extract images from HTML content", e)
                    }
                }
                
                val rawTitle = cleanHtmlText(targetObj["title"]?.jsonPrimitive?.content ?: "")
                val title = rawTitle

                Chapter(
                    url = chapterUrl,
                    title = title,
                    content = content,
                    images = images,
                )
            } else if (element is JsonPrimitive) {
                val content = element.content
                Chapter(
                    url = chapterUrl,
                    title = "",
                    content = content,
                    images = null,
                )
            } else if (element is JsonArray) {
                val images = element.mapNotNull { 
                    if (it is JsonPrimitive) {
                        it.content
                    } else if (it is JsonObject) {
                        val fallbackUrl = it["fallback"]?.jsonArray?.firstOrNull { f -> f is JsonPrimitive }?.jsonPrimitive?.content
                        val mainUrl = it["link"]?.jsonPrimitive?.content 
                            ?: it["url"]?.jsonPrimitive?.content 
                            ?: it["src"]?.jsonPrimitive?.content
                        if (!fallbackUrl.isNullOrBlank()) fallbackUrl else mainUrl
                    } else {
                        null
                    }
                }
                Chapter(
                    url = chapterUrl,
                    title = "",
                    content = null,
                    images = images
                )
            } else {
                null
            }
        } catch (e: Exception) {
            if (e.message?.startsWith("Lỗi từ extension:") == true) {
                throw e
            }
            android.util.Log.e("NovelRepo", "parseChapterContent failed, falling back to raw body", e)
            val content = jsonStr
            Chapter(
                url = chapterUrl,
                title = "",
                content = content,
                images = null,
            )
        }
    }

    private suspend fun parseNovelFromJson(obj: JsonObject, extensionId: String, sourceUrl: String): Novel {
        val rawUrl = obj["url"]?.jsonPrimitive?.content
            ?: obj["link"]?.jsonPrimitive?.content
            ?: obj["path"]?.jsonPrimitive?.content
            ?: ""
        
        val host = obj["host"]?.jsonPrimitive?.content ?: ""
        val resolvedUrl = resolveUrl(rawUrl, host, sourceUrl)

        val rawCover = obj["cover"]?.jsonPrimitive?.content
            ?: obj["image"]?.jsonPrimitive?.content
            ?: ""
        val resolvedCover = resolveUrl(rawCover, host, sourceUrl)

        val rawTitle = obj["title"]?.jsonPrimitive?.content 
            ?: obj["name"]?.jsonPrimitive?.content 
            ?: ""
        val title = translateTextIfNeeded(extensionId, cleanHtmlText(rawTitle), "title") ?: ""

        val rawAuthor = obj["author"]?.jsonPrimitive?.content ?: ""
        var author = translateTextIfNeeded(extensionId, cleanHtmlText(rawAuthor), "author") ?: ""
        
        // Trích xuất tác giả thông minh từ description nếu bị trống (phổ biến với các VBook extension cẩu thả ở trang home)
        if (author.isBlank()) {
            val rawDesc = obj["description"]?.jsonPrimitive?.content 
                ?: obj["desc"]?.jsonPrimitive?.content 
                ?: ""
            val descClean = cleanHtmlText(rawDesc).trim()
            if (descClean.isNotBlank()) {
                val patterns = listOf(
                    Regex("""(?i)Tác\s*giả\s*[:\-‐‑‒–—―]\s*([^\n\r|/]+)"""),
                    Regex("""(?i)Author\s*[:\-‐‑‒–—―]\s*([^\n\r|/]+)"""),
                    Regex("""^([^\n\r|/]{2,25})\s*\|\s*"""),
                    Regex("""^([^\n\r|/]{2,25})\s*/\s*"""),
                    Regex("""^([^\n\r|/]{2,25})\s+[\u2014-]\s+""")
                )
                for (pattern in patterns) {
                    val match = pattern.find(descClean)
                    if (match != null) {
                        val candidate = match.groupValues[1].trim()
                        if (candidate.length in 2..25 && 
                            !candidate.contains("tác giả", ignoreCase = true) && 
                            !candidate.contains("giới thiệu", ignoreCase = true) &&
                            !candidate.contains("chương", ignoreCase = true)
                        ) {
                            author = translateTextIfNeeded(extensionId, candidate, "author") ?: candidate
                            break
                        }
                    }
                }
                // Fallback nếu description chỉ chứa 1 dòng ngắn có thể chính là tên tác giả gộp
                if (author.isBlank() && descClean.length in 2..20 && !descClean.contains(" ") && !descClean.contains("chương", ignoreCase = true)) {
                    author = translateTextIfNeeded(extensionId, descClean, "author") ?: descClean
                }
            }
        }

        val rawDescription = obj["description"]?.jsonPrimitive?.content 
            ?: obj["desc"]?.jsonPrimitive?.content 
            ?: ""
        val description = translateTextIfNeeded(extensionId, cleanHtmlText(rawDescription), "introduce") ?: ""

        val genres = mutableListOf<com.nam.novelreader.domain.model.Genre>()
        obj["genres"]?.let { genElement ->
            try {
                if (genElement is JsonArray) {
                    genElement.forEach { item ->
                        if (item is JsonObject) {
                            val genreTitle = item["title"]?.jsonPrimitive?.content
                                ?: item["name"]?.jsonPrimitive?.content
                            val genreInput = item["input"]?.jsonPrimitive?.content ?: ""
                            val genreScript = item["script"]?.jsonPrimitive?.content ?: ""
                            
                            if (!genreTitle.isNullOrBlank()) {
                                genres.add(com.nam.novelreader.domain.model.Genre(
                                    name = translateTextIfNeeded(extensionId, cleanHtmlText(genreTitle), "genre") ?: "",
                                    input = genreInput,
                                    script = genreScript
                                ))
                            }
                        } else if (item is JsonPrimitive) {
                            genres.add(com.nam.novelreader.domain.model.Genre(name = translateTextIfNeeded(extensionId, cleanHtmlText(item.content), "genre") ?: ""))
                        }
                    }
                } else if (genElement is JsonPrimitive) {
                    genres.add(com.nam.novelreader.domain.model.Genre(name = translateTextIfNeeded(extensionId, cleanHtmlText(genElement.content), "genre") ?: ""))
                }
            } catch (e: Exception) {
                android.util.Log.e("NovelRepo", "Failed to parse genres", e)
            }
        }
        
        val suggests = mutableListOf<HomeTab>()
        obj["suggests"]?.let { sugElement ->
            try {
                if (sugElement is JsonArray) {
                    sugElement.forEach { item ->
                        if (item is JsonObject) {
                            val sugTitle = item["title"]?.jsonPrimitive?.content ?: ""
                            val sugInput = item["input"]?.jsonPrimitive?.content ?: ""
                            val sugScript = item["script"]?.jsonPrimitive?.content ?: ""
                            if (sugTitle.isNotBlank()) {
                                suggests.add(HomeTab(name = sugTitle, input = sugInput, script = sugScript))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("NovelRepo", "Failed to parse suggests", e)
            }
        }

        val status = cleanHtmlText(obj["status"]?.jsonPrimitive?.content ?: "")
        
        val parsedType = obj["type"]?.jsonPrimitive?.content ?: obj["is_comic"]?.jsonPrimitive?.content?.let { if (it == "true" || it == "1" || it == "comic") "comic" else "novel" }
        val fallbackType = getExtension(extensionId)?.pluginJson?.metadata?.type ?: "novel"
        val type = ContentType.fromString(parsedType ?: fallbackType)
        
        val latestChapter = obj["latestChapter"]?.jsonPrimitive?.content?.let { cleanHtmlText(it) }

        return Novel(
            url = resolvedUrl,
            title = title,
            author = author,
            cover = resolvedCover,
            description = description,
            genres = genres,
            status = status,
            type = type,
            extensionId = extensionId,
            latestChapter = latestChapter,
            suggests = suggests,
            originalTitle = cleanHtmlText(rawTitle)
        )
    }

    private fun cleanHtmlText(text: String): String {
        if (text.isBlank()) return ""
        return try {
            android.text.Html.fromHtml(text, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim()
        } catch (e: Exception) {
            text.trim()
        }
    }

    private fun resolveUrl(urlStr: String?, hostStr: String?, sourceUrl: String): String {
        if (urlStr == null) return ""
        val trimUrl = urlStr.trim()
        if (trimUrl.isEmpty() || trimUrl.startsWith("http")) return trimUrl
        
        val base = hostStr?.trim()?.ifEmpty { null } 
            ?: sourceUrl.trim().ifEmpty { null }
            ?: ""
        
        if (base.isEmpty()) return trimUrl
        
        val cleanUrl = if (trimUrl.startsWith("/")) trimUrl.substring(1) else trimUrl
        val cleanBase = if (base.endsWith("/")) base else "$base/"
        return cleanBase + cleanUrl
    }

    private fun parseGenreList(jsonStr: String): List<com.nam.novelreader.domain.model.Genre> {
        return try {
            val arr = json.parseToJsonElement(jsonStr).jsonArray
            arr.map { 
                if (it is JsonObject) {
                    com.nam.novelreader.domain.model.Genre(
                        name = it["name"]?.jsonPrimitive?.content ?: "",
                        input = it["input"]?.jsonPrimitive?.content ?: "",
                        script = it["script"]?.jsonPrimitive?.content ?: ""
                    )
                } else {
                    com.nam.novelreader.domain.model.Genre(name = it.jsonPrimitive.content)
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ========== Entity <-> Domain Mapping ==========

    private fun NovelEntity.toDomain() = Novel(
        url = url,
        title = title,
        author = author,
        cover = cover,
        description = description,
        genres = parseGenreList(genres),
        status = status,
        type = ContentType.fromString(type),
        extensionId = extensionId,
        latestChapter = latestChapter,
        totalChapters = totalChapters,
        lastReadChapterUrl = lastReadChapterUrl,
        lastReadAt = lastReadAt,
        scrollPosition = scrollPosition,
        originalTitle = ""
    )


    private fun Novel.toEntity() = NovelEntity(
        url = url,
        title = title,
        author = author,
        cover = cover,
        description = description,
        genres = json.encodeToString(genres),
        status = status,
        type = type.name.lowercase(),
        extensionId = extensionId,
        latestChapter = latestChapter,
        totalChapters = totalChapters,
        lastReadChapterUrl = lastReadChapterUrl,
        lastReadAt = lastReadAt,
        scrollPosition = scrollPosition,
    )

    fun getLastUsedExtensionId(): String? {
        return context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE).getString("last_extension_id", null)
    }

    fun setLastUsedExtensionId(extensionId: String) {
        context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE).edit().putString("last_extension_id", extensionId).apply()
    }

    suspend fun getComments(extensionId: String, novelUrl: String, page: String = ""): CommentPage? {
        val extension = extensionLoader.loadExtension(extensionId) ?: return null
        if (!extension.pluginJson.script.containsKey(ScriptType.TRACK.key)) {
            return null
        }
        val result = executeExtension(extensionId, ScriptType.TRACK, novelUrl, page)
        return when (result) {
            is ExtensionResult.Success -> {
                parseComments(result.data)
            }
            is ExtensionResult.Error -> {
                android.util.Log.e("NovelRepo", "getComments error: ${result.message}")
                null
            }
        }
    }

    private suspend fun parseComments(jsonStr: String): CommentPage = withContext(Dispatchers.Default) {
        try {
            val root = json.parseToJsonElement(jsonStr).jsonObject
            val arr = root["data"]?.jsonArray ?: root["items"]?.jsonArray ?: return@withContext CommentPage(emptyList())
            val nextPage = root["data2"]?.jsonPrimitive?.content ?: root["nextPage"]?.jsonPrimitive?.content ?: ""
            
            val comments = arr.map { item ->
                val obj = item.jsonObject
                val name = obj["name"]?.jsonPrimitive?.content ?: obj["userName"]?.jsonPrimitive?.content ?: "Ẩn danh"
                val avatar = obj["avatar"]?.jsonPrimitive?.content ?: obj["userAvatar"]?.jsonPrimitive?.content ?: ""
                val content = obj["content"]?.jsonPrimitive?.content ?: ""
                val description = obj["description"]?.jsonPrimitive?.content ?: ""
                
                val subCommentsArr = obj["subComments"]?.jsonArray ?: obj["subSourceComments"]?.jsonArray
                val subComments = subCommentsArr?.map { subItem ->
                    val subObj = subItem.jsonObject
                    Comment(
                        name = subObj["name"]?.jsonPrimitive?.content ?: subObj["userName"]?.jsonPrimitive?.content ?: "Ẩn danh",
                        avatar = subObj["avatar"]?.jsonPrimitive?.content ?: subObj["userAvatar"]?.jsonPrimitive?.content ?: "",
                        content = subObj["content"]?.jsonPrimitive?.content ?: "",
                        description = subObj["description"]?.jsonPrimitive?.content ?: "",
                    )
                } ?: emptyList()

                Comment(
                    name = name,
                    avatar = avatar,
                    content = content,
                    description = description,
                    subComments = subComments,
                )
            }
            CommentPage(comments, nextPage)
        } catch (e: Exception) {
            android.util.Log.e("NovelRepo", "parseComments failed: ${e.message}", e)
            CommentPage(emptyList())
        }
    }

    suspend fun getDownloadTask(novelUrl: String): DownloadTaskEntity? {
        return downloadTaskDao.getTask(novelUrl)
    }

    fun getAllDownloadTasks(): Flow<List<DownloadTaskEntity>> {
        return downloadTaskDao.getAllTasks()
    }
}
