package com.nam.novelreader.feature.extensions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nam.novelreader.data.local.dao.ExtensionDao
import com.nam.novelreader.data.local.dao.RepositoryDao
import com.nam.novelreader.data.local.entity.ExtensionEntity
import com.nam.novelreader.data.local.entity.RepositoryEntity
import com.nam.novelreader.extension.loader.ExtensionLoader
import com.nam.novelreader.extension.model.ExtensionInfo
import com.nam.novelreader.data.preferences.AppPreferences
import com.nam.novelreader.data.network.GoogleDriveBackupManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject
import com.nam.novelreader.extension.runtime.VBookJsExtensionRunner
import com.nam.novelreader.extension.model.ExtensionResult
import com.nam.novelreader.extension.model.ScriptType
import com.nam.novelreader.extension.model.LoadedExtension
import android.content.Intent
import kotlinx.coroutines.withContext

enum class ChainStepStatus { PENDING, RUNNING, SUCCESS, ERROR, SKIPPED }

data class ChainTestStep(
    val name: String,
    val status: ChainStepStatus,
    val duration: Long = 0,
    val logs: List<String> = emptyList(),
    val output: String? = null
)

@HiltViewModel
class ExtensionViewModel @Inject constructor(
    val extensionLoader: ExtensionLoader,
    private val extensionDao: ExtensionDao,
    private val repositoryDao: RepositoryDao,
    val appPrefs: AppPreferences,
    private val googleDriveBackupManager: GoogleDriveBackupManager,
    private val httpClient: OkHttpClient,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val jsExtensionRunner: VBookJsExtensionRunner,
) : ViewModel() {

    // === Test Extension States ===
    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    private val _testResultStatus = MutableStateFlow<String?>(null)
    val testResultStatus: StateFlow<String?> = _testResultStatus.asStateFlow()

    private val _testResultData = MutableStateFlow<String?>(null)
    val testResultData: StateFlow<String?> = _testResultData.asStateFlow()

    private val _testLogs = MutableStateFlow<List<String>>(emptyList())
    val testLogs: StateFlow<List<String>> = _testLogs.asStateFlow()

    // === E2E Chain Test States ===
    private val _isChainTesting = MutableStateFlow(false)
    val isChainTesting: StateFlow<Boolean> = _isChainTesting.asStateFlow()

    private val _chainTestSteps = MutableStateFlow<List<ChainTestStep>>(emptyList())
    val chainTestSteps: StateFlow<List<ChainTestStep>> = _chainTestSteps.asStateFlow()

    // === Installed Extensions ===
    val installedExtensions: Flow<List<ExtensionEntity>> = extensionDao.getInstalledExtensions()

    // === Repositories ===
    val repositories: Flow<List<RepositoryEntity>> = repositoryDao.getRepositories()

    // === Available Extensions (from all repos) ===
    private val _availableExtensions = MutableStateFlow<List<ExtensionInfo>>(emptyList())
    val availableExtensions: StateFlow<List<ExtensionInfo>> = _availableExtensions

    // === Public Extensions ===
    private val _publicExtensionsState = MutableStateFlow<Set<String>>(appPrefs.publicExtensions)
    val publicExtensionsState: StateFlow<Set<String>> = _publicExtensionsState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _installingIds = MutableStateFlow<Set<String>>(emptySet())
    val installingIds: StateFlow<Set<String>> = _installingIds

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        viewModelScope.launch {
            extensionLoader.ensureDefaultRepository()
            extensionLoader.autoFixExtensionTypes()
            loadPublicExtensions()
            fetchAllExtensions()
        }
    }

    /**
     * Fetch extensions từ built-in assets và tất cả các repository online đang hoạt động.
     */
    fun fetchAllExtensions() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val allExts = mutableListOf<ExtensionInfo>()
                
                // 1. Tải từ assets cục bộ
                val builtIn = extensionLoader.fetchBuiltInExtensions()
                allExts.addAll(builtIn)
                
                // 2. Tải từ các repository online đang hoạt động
                val repos = repositoryDao.getEnabledRepositories()
                for (repo in repos) {
                    if (repo.url != "file:///android_asset/plugin.json") {
                        try {
                            val repoExts = extensionLoader.fetchRepository(repo.url)
                            allExts.addAll(repoExts)
                        } catch (e: Exception) {
                            android.util.Log.e("ExtVM", "Failed to fetch from repo: ${repo.url}", e)
                        }
                    }
                }
                
                // 3. Loại bỏ trùng lặp (giữ bản ghi có version cao nhất)
                val distinctExts = allExts.groupBy { it.name.toSlug() }.map { (_, list) ->
                    list.maxByOrNull { it.version } ?: list.first()
                }
                
                _availableExtensions.value = distinctExts
                prefetchIcons(distinctExts)
            } catch (e: Exception) {
                _error.value = e.message
            }
            _isLoading.value = false
        }
    }

    /**
     * Fetch extensions từ 1 repository cụ thể.
     */
    fun fetchFromRepository(repoUrl: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val exts = if (repoUrl.startsWith("file:///android_asset/")) {
                    extensionLoader.fetchBuiltInExtensions()
                } else {
                    extensionLoader.fetchRepository(repoUrl)
                }
                _availableExtensions.value = exts
                prefetchIcons(exts)
            } catch (e: Exception) {
                _error.value = e.message
            }
            _isLoading.value = false
        }
    }

    fun installExtension(info: ExtensionInfo) {
        viewModelScope.launch {
            _installingIds.update { it + info.name }
            extensionLoader.installExtension(info)
            _installingIds.update { it - info.name }
        }
    }

    fun uninstallExtension(extensionId: String) {
        viewModelScope.launch {
            extensionLoader.uninstallExtension(extensionId)
        }
    }

    // === Repository Management ===

    fun addRepository(url: String, name: String = "") {
        viewModelScope.launch {
            // Auto-map vbookext.me to JSON registry
            val finalUrl = if (url.trim().removeSuffix("/") == "https://www.vbookext.me") {
                com.nam.novelreader.extension.loader.ExtensionLoader.DEFAULT_REPO_URL
            } else {
                url.trim()
            }

            val repoName = name.ifBlank {
                if (finalUrl == com.nam.novelreader.extension.loader.ExtensionLoader.DEFAULT_REPO_URL) {
                    "VBook Community Extensions"
                } else {
                    finalUrl.substringAfterLast("/").substringBefore(".")
                        .ifBlank { "Repository" }
                }
            }
            repositoryDao.insert(
                RepositoryEntity(
                    url = finalUrl,
                    name = repoName,
                )
            )
            // Auto-fetch extensions from new repo
            fetchAllExtensions()
        }
    }

    fun removeRepository(repo: RepositoryEntity) {
        viewModelScope.launch {
            repositoryDao.delete(repo)
        }
    }

    // === Public Extensions Management ===

    fun toggleExtensionPublic(extId: String, isPublic: Boolean) {
        // No-op: tất cả tiện ích mặc định là công khai
    }

    fun loadPublicExtensions() {
        // No-op: tất cả tiện ích mặc định là công khai, bỏ qua tải cấu hình từ Drive để tránh lag app lúc khởi động
    }


    private fun String.toSlug(): String {
        val normalized = java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD)
            .replace(Regex("[\\p{InCombiningDiacriticalMarks}]"), "")
        return normalized
            .lowercase()
            .replace("đ", "d").replace("Đ", "d")
            .replace(Regex("[^a-z0-9]"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
    }

    private fun prefetchIcons(exts: List<ExtensionInfo>) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val cacheFolder = java.io.File(context.cacheDir, "extensions_icons")
            cacheFolder.mkdirs()

            exts.forEach { info ->
                val iconUrl = info.icon
                if (iconUrl.isBlank()) return@forEach

                val slug = info.name
                    .let { java.text.Normalizer.normalize(it, java.text.Normalizer.Form.NFD) }
                    .replace(Regex("[\\p{InCombiningDiacriticalMarks}]"), "")
                    .lowercase()
                    .replace("đ", "d")
                    .replace(Regex("[^a-z0-9]"), "-")
                    .replace(Regex("-+"), "-")
                    .trim('-')
                val cacheFile = java.io.File(cacheFolder, "${slug}_icon.png")

                if (cacheFile.exists()) {
                    updateAvailableExtensionIcon(info.name, cacheFile.absolutePath)
                    return@forEach
                }

                try {
                    val bytes: ByteArray? = when {
                        iconUrl.startsWith("gdrive://") -> {
                            val filename = iconUrl.substringAfter("gdrive://")
                            googleDriveBackupManager.downloadFileFromExtensionFolder(filename).getOrNull()
                        }
                        iconUrl.startsWith("file:///android_asset/") -> {
                            val assetPath = iconUrl.substringAfter("file:///android_asset/")
                            context.assets.open(assetPath).use { it.readBytes() }
                        }
                        iconUrl.startsWith("http://") || iconUrl.startsWith("https://") -> {
                            val req = okhttp3.Request.Builder().url(iconUrl).build()
                            httpClient.newCall(req).execute().body?.bytes()
                        }
                        else -> null
                    }
                    if (bytes != null) {
                        cacheFile.writeBytes(bytes)
                        updateAvailableExtensionIcon(info.name, cacheFile.absolutePath)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("ExtVM", "Failed to prefetch icon for ${info.name}: ${e.message}")
                }
            }
        }
    }

    private fun updateAvailableExtensionIcon(name: String, localPath: String) {
        _availableExtensions.update { currentList ->
            currentList.map { info ->
                if (info.name == name) {
                    info.copy(icon = localPath)
                } else {
                    info
                }
            }
        }
    }

    fun runExtensionTest(extensionId: String, scriptKey: String, args: List<String>) {
        viewModelScope.launch {
            _isTesting.value = true
            _testResultStatus.value = null
            _testResultData.value = null
            _testLogs.value = emptyList()

            val logCollector = mutableListOf<String>()
            try {
                logCollector.add(">>> Bắt đầu kiểm thử tiện ích: $extensionId")
                logCollector.add(">>> Loại script: $scriptKey")
                logCollector.add(">>> Tham số truyền vào: $args")

                val loaded = extensionLoader.loadExtension(extensionId)
                if (loaded == null) {
                    _testResultStatus.value = "ERROR"
                    _testResultData.value = "Không thể load extension '$extensionId'. Hãy chắc chắn bạn đã cài đặt tiện ích này."
                    logCollector.add(">>> Lỗi: Không tìm thấy thư mục tiện ích hoặc plugin.json không hợp lệ.")
                    _testLogs.value = logCollector
                    _isTesting.value = false
                    return@launch
                }

                val scriptType = ScriptType.fromKey(scriptKey)
                if (scriptType == null) {
                    _testResultStatus.value = "ERROR"
                    _testResultData.value = "Loại script '$scriptKey' không hợp lệ."
                    logCollector.add(">>> Lỗi: ScriptType không hợp lệ.")
                    _testLogs.value = logCollector
                    _isTesting.value = false
                    return@launch
                }

                // Xóa cache script trước khi test để Rhino compile lại từ đầu
                jsExtensionRunner.clearCache(extensionId)
                
                val startTime = System.currentTimeMillis()
                logCollector.add(">>> Đang chạy script...")
                
                val result = jsExtensionRunner.execute(
                    loaded,
                    scriptType,
                    *args.toTypedArray(),
                    logCollector = logCollector
                )
                
                val duration = System.currentTimeMillis() - startTime
                logCollector.add(">>> Hoàn thành chạy trong ${duration}ms")

                when (result) {
                    is ExtensionResult.Success -> {
                        _testResultStatus.value = "SUCCESS (${duration}ms)"
                        _testResultData.value = result.data
                        logCollector.add(">>> Chạy thành công!")
                    }
                    is ExtensionResult.Error -> {
                        _testResultStatus.value = "ERROR (${duration}ms)"
                        _testResultData.value = result.message
                        logCollector.add(">>> Chạy thất bại: ${result.message}")
                    }
                }
            } catch (e: Exception) {
                _testResultStatus.value = "CRASH"
                _testResultData.value = e.stackTraceToString()
                logCollector.add(">>> Xảy ra lỗi crash hệ thống: ${e.message}")
            } finally {
                _testLogs.value = logCollector
                _isTesting.value = false
            }
        }
    }

    fun saveAndShareTestLog(extName: String, scriptKey: String, args: List<String>) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val status = _testResultStatus.value ?: "CHƯA TEST"
            val output = _testResultData.value ?: ""
            val logs = _testLogs.value.joinToString("\n")

            val logText = buildString {
                appendLine("==================================================")
                appendLine("BÁO CÁO KIỂM THỬ TIỆN ÍCH")
                appendLine("Tên tiện ích: $extName")
                appendLine("Script test: $scriptKey")
                appendLine("Tham số: ${args.joinToString(", ")}")
                appendLine("Trạng thái: $status")
                appendLine("Thời gian xuất log: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
                appendLine("==================================================")
                appendLine()
                appendLine("--- CONSOLE LOGS ---")
                appendLine(logs)
                appendLine()
                appendLine("--- DỮ LIỆU ĐẦU RA (JSON/LỖI) ---")
                appendLine(output)
                appendLine("==================================================")
            }

            // Ghi file cục bộ tại external storage (nếu có thể) để ADB pull
            try {
                val externalDir = context.getExternalFilesDir(null)
                if (externalDir != null) {
                    val logFile = java.io.File(externalDir, "extension_test_log.txt")
                    logFile.writeText(logText)
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Đã lưu file log tại: ${logFile.absolutePath}", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ExtVM", "Failed to write log file to external storage", e)
            }

            // Mở Share Sheet bằng text để người dùng gửi log đi
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                try {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Log Test Tiện Ích: $extName")
                        putExtra(Intent.EXTRA_TEXT, logText)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Gửi báo cáo log kiểm thử").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "Không thể mở ứng dụng chia sẻ: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun runChainTest(extensionId: String) {
        viewModelScope.launch {
            val steps = mutableListOf(
                ChainTestStep("1. Nạp danh sách truyện (home/search)", ChainStepStatus.PENDING),
                ChainTestStep("2. Xem chi tiết truyện (detail)", ChainStepStatus.PENDING),
                ChainTestStep("3. Lấy mục lục chương (toc)", ChainStepStatus.PENDING),
                ChainTestStep("4. Tải nội dung chương (chap/page/track)", ChainStepStatus.PENDING)
            )
            _chainTestSteps.value = steps
            _isChainTesting.value = true

            // === Bước 1 ===
            val step1Logs = mutableListOf<String>()
            step1Logs.add(">>> Bắt đầu bước 1: Nạp danh sách truyện...")
            steps[0] = steps[0].copy(status = ChainStepStatus.RUNNING, logs = step1Logs)
            _chainTestSteps.value = steps.toList()

            val loaded = extensionLoader.loadExtension(extensionId)
            if (loaded == null) {
                step1Logs.add(">>> Thất bại: Không thể load extension '$extensionId'")
                steps[0] = steps[0].copy(status = ChainStepStatus.ERROR, logs = step1Logs, output = "Extension not found")
                _chainTestSteps.value = steps.toList()
                
                for (i in 1..3) {
                    steps[i] = steps[i].copy(status = ChainStepStatus.SKIPPED)
                }
                _chainTestSteps.value = steps.toList()
                _isChainTesting.value = false
                return@launch
            }

            val step1StartTime = System.currentTimeMillis()
            var firstNovelUrl = ""
            var step1Status = ChainStepStatus.ERROR
            var step1Output: String? = null

            try {
                jsExtensionRunner.clearCache(extensionId)
                val hasHome = loaded.pluginJson.script.containsKey("home")
                val hasSearch = loaded.pluginJson.script.containsKey("search")

                val result = when {
                    hasHome -> {
                        step1Logs.add(">>> Thực thi script home.js...")
                        jsExtensionRunner.execute(loaded, ScriptType.HOME, "1", logCollector = step1Logs)
                    }
                    hasSearch -> {
                        step1Logs.add(">>> Fallback: Thực thi script search.js với từ khóa 'thần'...")
                        jsExtensionRunner.execute(loaded, ScriptType.SEARCH, "thần", "1", logCollector = step1Logs)
                    }
                    else -> {
                        throw Exception("Extension không hỗ trợ cả script 'home' và 'search'.")
                    }
                }

                when (result) {
                    is ExtensionResult.Success -> {
                        step1Output = result.data
                        firstNovelUrl = extractFirstNovelUrl(result.data, loaded, step1Logs)
                        if (firstNovelUrl.isNotBlank()) {
                            step1Logs.add(">>> Thành công! Trích xuất được link truyện đầu tiên: $firstNovelUrl")
                            step1Status = ChainStepStatus.SUCCESS
                        } else {
                            step1Logs.add(">>> Lỗi: Không thể trích xuất link truyện nào từ kết quả JSON.")
                            step1Status = ChainStepStatus.ERROR
                        }
                    }
                    is ExtensionResult.Error -> {
                        step1Logs.add(">>> Lỗi thực thi script: ${result.message}")
                        step1Status = ChainStepStatus.ERROR
                        step1Output = result.message
                    }
                }
            } catch (e: Exception) {
                step1Logs.add(">>> Crash: ${e.message}")
                step1Status = ChainStepStatus.ERROR
                step1Output = e.stackTraceToString()
            }

            steps[0] = steps[0].copy(
                status = step1Status,
                duration = System.currentTimeMillis() - step1StartTime,
                logs = step1Logs,
                output = step1Output
            )
            _chainTestSteps.value = steps.toList()

            if (step1Status != ChainStepStatus.SUCCESS) {
                for (i in 1..3) {
                    steps[i] = steps[i].copy(status = ChainStepStatus.SKIPPED)
                }
                _chainTestSteps.value = steps.toList()
                _isChainTesting.value = false
                return@launch
            }

            // === Bước 2 ===
            val step2Logs = mutableListOf<String>()
            step2Logs.add(">>> Bắt đầu bước 2: Xem chi tiết truyện...")
            steps[1] = steps[1].copy(status = ChainStepStatus.RUNNING, logs = step2Logs)
            _chainTestSteps.value = steps.toList()

            val step2StartTime = System.currentTimeMillis()
            var step2Status = ChainStepStatus.ERROR
            var step2Output: String? = null

            try {
                step2Logs.add(">>> Thực thi script detail.js với url: $firstNovelUrl")
                val result = jsExtensionRunner.execute(loaded, ScriptType.DETAIL, firstNovelUrl, logCollector = step2Logs)
                when (result) {
                    is ExtensionResult.Success -> {
                        step2Output = result.data
                        step2Logs.add(">>> Thành công! Lấy thông tin chi tiết truyện hoàn tất.")
                        step2Status = ChainStepStatus.SUCCESS
                    }
                    is ExtensionResult.Error -> {
                        step2Logs.add(">>> Lỗi thực thi script detail.js: ${result.message}")
                        step2Status = ChainStepStatus.ERROR
                        step2Output = result.message
                    }
                }
            } catch (e: Exception) {
                step2Logs.add(">>> Crash: ${e.message}")
                step2Status = ChainStepStatus.ERROR
                step2Output = e.stackTraceToString()
            }

            steps[1] = steps[1].copy(
                status = step2Status,
                duration = System.currentTimeMillis() - step2StartTime,
                logs = step2Logs,
                output = step2Output
            )
            _chainTestSteps.value = steps.toList()

            if (step2Status != ChainStepStatus.SUCCESS) {
                for (i in 2..3) {
                    steps[i] = steps[i].copy(status = ChainStepStatus.SKIPPED)
                }
                _chainTestSteps.value = steps.toList()
                _isChainTesting.value = false
                return@launch
            }

            // === Bước 3 ===
            val step3Logs = mutableListOf<String>()
            step3Logs.add(">>> Bắt đầu bước 3: Lấy mục lục chương...")
            steps[2] = steps[2].copy(status = ChainStepStatus.RUNNING, logs = step3Logs)
            _chainTestSteps.value = steps.toList()

            val step3StartTime = System.currentTimeMillis()
            var firstChapterUrl = ""
            var step3Status = ChainStepStatus.ERROR
            var step3Output: String? = null

            try {
                val hasPageScript = loaded.pluginJson.script.containsKey("page")
                val tocInputUrl = if (hasPageScript) {
                    step3Logs.add(">>> Phát hiện script page.js hỗ trợ phân trang mục lục. Đang chạy page.js...")
                    val pageResult = jsExtensionRunner.execute(loaded, ScriptType.PAGE, firstNovelUrl, logCollector = step3Logs)
                    when (pageResult) {
                        is ExtensionResult.Success -> {
                            val firstPage = extractFirstPageUrl(pageResult.data, step3Logs)
                            if (firstPage.isNotBlank()) {
                                val resolved = resolveUrl(firstPage, null, loaded.pluginJson.metadata.source)
                                step3Logs.add(">>> Trích xuất được trang mục lục đầu tiên: $resolved")
                                resolved
                            } else {
                                step3Logs.add(">>> Cảnh báo: page.js trả về rỗng. Fallback sử dụng link chi tiết gốc.")
                                firstNovelUrl
                            }
                        }
                        is ExtensionResult.Error -> {
                            step3Logs.add(">>> Lỗi chạy page.js: ${pageResult.message}. Fallback sử dụng link chi tiết gốc.")
                            firstNovelUrl
                        }
                    }
                } else {
                    firstNovelUrl
                }

                step3Logs.add(">>> Thực thi script toc.js với url: $tocInputUrl")
                val result = jsExtensionRunner.execute(loaded, ScriptType.TOC, tocInputUrl, "1", logCollector = step3Logs)
                when (result) {
                    is ExtensionResult.Success -> {
                        step3Output = result.data
                        firstChapterUrl = extractFirstChapterUrl(result.data, loaded.pluginJson.metadata.source, step3Logs)
                        if (firstChapterUrl.isNotBlank()) {
                            step3Logs.add(">>> Thành công! Trích xuất được link chương đầu tiên: $firstChapterUrl")
                            step3Status = ChainStepStatus.SUCCESS
                        } else {
                            step3Logs.add(">>> Lỗi: Không thể trích xuất link chương nào từ mục lục JSON.")
                            step3Status = ChainStepStatus.ERROR
                        }
                    }
                    is ExtensionResult.Error -> {
                        step3Logs.add(">>> Lỗi thực thi script toc.js: ${result.message}")
                        step3Status = ChainStepStatus.ERROR
                        step3Output = result.message
                    }
                }
            } catch (e: Exception) {
                step3Logs.add(">>> Crash: ${e.message}")
                step3Status = ChainStepStatus.ERROR
                step3Output = e.stackTraceToString()
            }

            steps[2] = steps[2].copy(
                status = step3Status,
                duration = System.currentTimeMillis() - step3StartTime,
                logs = step3Logs,
                output = step3Output
            )
            _chainTestSteps.value = steps.toList()

            if (step3Status != ChainStepStatus.SUCCESS) {
                steps[3] = steps[3].copy(status = ChainStepStatus.SKIPPED)
                _chainTestSteps.value = steps.toList()
                _isChainTesting.value = false
                return@launch
            }

            // === Bước 4 ===
            val step4Logs = mutableListOf<String>()
            step4Logs.add(">>> Bắt đầu bước 4: Tải nội dung chương...")
            steps[3] = steps[3].copy(status = ChainStepStatus.RUNNING, logs = step4Logs)
            _chainTestSteps.value = steps.toList()

            val step4StartTime = System.currentTimeMillis()
            var step4Status = ChainStepStatus.ERROR
            var step4Output: String? = null

            try {
                val extType = loaded.pluginJson.metadata.type.lowercase()
                step4Logs.add(">>> Thực thi script chap.js với url: $firstChapterUrl")

                val result = jsExtensionRunner.execute(loaded, ScriptType.CHAP, firstChapterUrl, logCollector = step4Logs)
                when (result) {
                    is ExtensionResult.Success -> {
                        if (extType == "video") {
                            step4Logs.add(">>> Phân tích danh sách server video từ kết quả chap.js...")
                            val serverUrl = extractFirstVideoServerUrl(result.data, loaded.pluginJson.script.containsKey("track"), step4Logs)
                            if (serverUrl.isNotBlank()) {
                                val resolvedServerUrl = resolveUrl(serverUrl, null, loaded.pluginJson.metadata.source)
                                step4Logs.add(">>> Trích xuất được link server phát: $resolvedServerUrl")
                                step4Logs.add(">>> Thực thi tiếp script track.js để lấy link stream video direct...")
                                
                                val trackResult = jsExtensionRunner.execute(loaded, ScriptType.TRACK, resolvedServerUrl, logCollector = step4Logs)
                                when (trackResult) {
                                    is ExtensionResult.Success -> {
                                        step4Output = trackResult.data
                                        step4Logs.add(">>> Thành công! Trích xuất được link stream video direct.")
                                        step4Status = ChainStepStatus.SUCCESS
                                    }
                                    is ExtensionResult.Error -> {
                                        step4Logs.add(">>> Lỗi thực thi script track.js: ${trackResult.message}")
                                        step4Status = ChainStepStatus.ERROR
                                        step4Output = trackResult.message
                                    }
                                }
                            } else {
                                step4Logs.add(">>> Lỗi: Không thể trích xuất link server nào từ JSON danh sách server.")
                                step4Status = ChainStepStatus.ERROR
                                step4Output = result.data
                            }
                        } else {
                            step4Output = result.data
                            step4Status = ChainStepStatus.SUCCESS
                            
                            if (extType == "comic") {
                                try {
                                    val element = org.json.JSONTokener(result.data).nextValue()
                                    val imagesCount = if (element is org.json.JSONArray) {
                                        element.length()
                                    } else if (element is org.json.JSONObject) {
                                        val dataElement = element.opt("data")
                                        if (dataElement is org.json.JSONArray) {
                                            dataElement.length()
                                        } else {
                                            (element.optJSONArray("images") ?: element.optJSONArray("urls") ?: element.optJSONArray("list") ?: element.optJSONArray("data")).let { it?.length() ?: 0 }
                                        }
                                    } else 0
                                    step4Logs.add(">>> Thành công! Trích xuất được $imagesCount ảnh truyện tranh.")
                                } catch (e: Exception) {
                                    step4Logs.add(">>> Thành công! Tải nội dung chương hoàn tất (Không parse được số ảnh).")
                                }
                            } else {
                                step4Logs.add(">>> Thành công! Tải nội dung chương truyện chữ hoàn tất (Độ dài: ${result.data.length} ký tự).")
                            }
                        }
                    }
                    is ExtensionResult.Error -> {
                        step4Logs.add(">>> Lỗi thực thi script chap.js: ${result.message}")
                        step4Status = ChainStepStatus.ERROR
                        step4Output = result.message
                    }
                }
            } catch (e: Exception) {
                step4Logs.add(">>> Crash: ${e.message}")
                step4Status = ChainStepStatus.ERROR
                step4Output = e.stackTraceToString()
            }

            steps[3] = steps[3].copy(
                status = step4Status,
                duration = System.currentTimeMillis() - step4StartTime,
                logs = step4Logs,
                output = step4Output
            )
            _chainTestSteps.value = steps.toList()
            _isChainTesting.value = false
        }
    }

    fun shareChainTestLog(extName: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val steps = _chainTestSteps.value
            val logText = buildString {
                appendLine("==================================================")
                appendLine("BÁO CÁO KIỂM THỬ LIÊN HOÀN E2E TIỆN ÍCH")
                appendLine("Tên tiện ích: $extName")
                appendLine("Thời gian xuất log: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
                appendLine("==================================================")
                appendLine()

                steps.forEach { step ->
                    appendLine("--------------------------------------------------")
                    appendLine("BƯỚC: ${step.name}")
                    appendLine("Trạng thái: ${step.status} (${step.duration}ms)")
                    appendLine("--------------------------------------------------")
                    appendLine("--- CONSOLE LOGS ---")
                    appendLine(step.logs.joinToString("\n"))
                    appendLine()
                    appendLine("--- DỮ LIỆU ĐẦU RA / LỖI ---")
                    appendLine(step.output ?: "(Không có dữ liệu)")
                    appendLine()
                }
                appendLine("==================================================")
            }

            // Ghi file cục bộ tại external storage để ADB pull
            try {
                val externalDir = context.getExternalFilesDir(null)
                if (externalDir != null) {
                    val logFile = java.io.File(externalDir, "extension_e2e_test_log.txt")
                    logFile.writeText(logText)
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Đã lưu file log E2E tại: ${logFile.absolutePath}", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ExtVM", "Failed to write E2E log file to external storage", e)
            }

            // Mở Share Sheet bằng text để người dùng gửi log đi
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                try {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Báo Cáo E2E Test Tiện Ích: $extName")
                        putExtra(Intent.EXTRA_TEXT, logText)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Gửi báo cáo log E2E").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "Không thể mở ứng dụng chia sẻ: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun extractFirstNovelUrl(jsonStr: String, loaded: LoadedExtension, logs: MutableList<String>): String {
        try {
            val jsonToken = org.json.JSONTokener(jsonStr).nextValue()

            // Trường hợp 1: JSON là một mảng
            if (jsonToken is org.json.JSONArray) {
                if (jsonToken.length() == 0) return ""
                val firstItem = jsonToken.optJSONObject(0) ?: return ""

                // Kiểm tra xem item đầu tiên có phải là Tab hay không
                if (firstItem.has("input") || firstItem.has("script")) {
                    val tabTitle = firstItem.optString("title").takeIf { it.isNotBlank() } ?: firstItem.optString("name", "")
                    val tabInput = firstItem.optString("input", "")
                    val tabScript = firstItem.optString("script", "home")
                    logs.add(">>> Phát hiện danh sách tab. Tự động nạp dữ liệu cho tab đầu tiên: '$tabTitle'...")

                    val normalizedScriptFile = if (tabScript.endsWith(".js")) tabScript else "$tabScript.js"
                    val tabResult = jsExtensionRunner.execute(
                        loaded,
                        normalizedScriptFile,
                        tabInput, "1",
                        logCollector = logs
                    )
                    return when (tabResult) {
                        is ExtensionResult.Success -> {
                            extractFirstNovelUrlFromList(tabResult.data, loaded.pluginJson.metadata.source, logs)
                        }
                        is ExtensionResult.Error -> {
                            logs.add(">>> Không thể tải dữ liệu tab '$tabTitle': ${tabResult.message}")
                            ""
                        }
                    }
                } else {
                    // Mảng các truyện trực tiếp
                    return extractFirstNovelUrlFromList(jsonStr, loaded.pluginJson.metadata.source, logs)
                }
            }

            // Trường hợp 2: JSON là một object
            if (jsonToken is org.json.JSONObject) {
                val code = jsonToken.optInt("code", 0)
                if (code != 0) {
                    logs.add(">>> Extension trả về mã lỗi: $code")
                    return ""
                }

                val dataElement = jsonToken.opt("data")
                if (dataElement is org.json.JSONArray) {
                    if (dataElement.length() > 0) {
                        val firstItem = dataElement.optJSONObject(0) ?: return ""
                        if (firstItem.has("input") || firstItem.has("script")) {
                            val tabTitle = firstItem.optString("title").takeIf { it.isNotBlank() } ?: firstItem.optString("name", "")
                            val tabInput = firstItem.optString("input", "")
                            val tabScript = firstItem.optString("script", "home")
                            logs.add(">>> Phát hiện danh sách tab trong 'data'. Tự động nạp dữ liệu cho tab đầu tiên: '$tabTitle'...")
                            val normalizedScriptFile = if (tabScript.endsWith(".js")) tabScript else "$tabScript.js"
                            val tabResult = jsExtensionRunner.execute(
                                loaded,
                                normalizedScriptFile,
                                tabInput, "1",
                                logCollector = logs
                            )
                            return when (tabResult) {
                                is ExtensionResult.Success -> {
                                    extractFirstNovelUrlFromList(tabResult.data, loaded.pluginJson.metadata.source, logs)
                                }
                                is ExtensionResult.Error -> {
                                    logs.add(">>> Không thể tải dữ liệu tab '$tabTitle': ${tabResult.message}")
                                    ""
                                }
                            }
                        } else {
                            return extractFirstNovelUrlFromList(dataElement.toString(), loaded.pluginJson.metadata.source, logs)
                        }
                    }
                } else if (dataElement is org.json.JSONObject) {
                    val itemsElement = dataElement.optJSONArray("items")
                    if (itemsElement != null) {
                        return extractFirstNovelUrlFromList(itemsElement.toString(), loaded.pluginJson.metadata.source, logs)
                    }
                }

                val itemsElement = jsonToken.optJSONArray("items")
                    ?: jsonToken.optJSONArray("list")
                    ?: jsonToken.optJSONArray("books")
                if (itemsElement != null) {
                    return extractFirstNovelUrlFromList(itemsElement.toString(), loaded.pluginJson.metadata.source, logs)
                }
            }
        } catch (e: Exception) {
            logs.add(">>> Lỗi parse JSON trích xuất truyện: ${e.message}")
        }
        return ""
    }

    private fun extractFirstNovelUrlFromList(jsonStr: String, sourceUrl: String, logs: MutableList<String>): String {
        try {
            val element = org.json.JSONTokener(jsonStr).nextValue()
            val array = when (element) {
                is org.json.JSONArray -> element
                is org.json.JSONObject -> element.optJSONArray("data") ?: element.optJSONArray("items") ?: element.optJSONArray("list")
                else -> null
            }
            if (array != null && array.length() > 0) {
                val firstNovel = array.optJSONObject(0) ?: return ""
                val rawUrl = firstNovel.optString("link").takeIf { it.isNotBlank() }
                    ?: firstNovel.optString("url").takeIf { it.isNotBlank() }
                    ?: firstNovel.optString("path").takeIf { it.isNotBlank() }
                    ?: ""
                val host = firstNovel.optString("host", "")
                return if (rawUrl.isBlank()) "" else resolveUrl(rawUrl, host, sourceUrl)
            }
        } catch (e: Exception) {
            logs.add(">>> Lỗi khi parse danh sách truyện: ${e.message}")
        }
        return ""
    }

    private fun extractFirstChapterUrl(jsonStr: String, sourceUrl: String, logs: MutableList<String>): String {
        try {
            val element = org.json.JSONTokener(jsonStr).nextValue()
            val items = if (element is org.json.JSONArray) {
                element
            } else if (element is org.json.JSONObject) {
                element.optJSONArray("items")
                    ?: element.optJSONArray("data")
                    ?: element.optJSONArray("chapters")
                    ?: element.optJSONArray("list")
            } else {
                null
            }

            if (items != null && items.length() > 0) {
                for (i in 0 until items.length()) {
                    val obj = items.optJSONObject(i) ?: continue
                    val isSection = obj.optString("type") == "section"
                    if (!isSection) {
                        val rawUrl = obj.optString("url").takeIf { it.isNotBlank() }
                            ?: obj.optString("link").takeIf { it.isNotBlank() }
                            ?: obj.optString("path").takeIf { it.isNotBlank() }
                            ?: ""
                        val host = obj.optString("host", "")
                        if (rawUrl.isNotBlank()) {
                            return resolveUrl(rawUrl, host, sourceUrl)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logs.add(">>> Lỗi parse mục lục chương: ${e.message}")
        }
        return ""
    }

    private fun extractFirstPageUrl(jsonStr: String, logs: MutableList<String>): String {
        try {
            val jsonToken = org.json.JSONTokener(jsonStr).nextValue()
            if (jsonToken is org.json.JSONArray) {
                if (jsonToken.length() > 0) {
                    return jsonToken.optString(0)
                }
            } else if (jsonToken is org.json.JSONObject) {
                val dataArray = jsonToken.optJSONArray("data")
                if (dataArray != null && dataArray.length() > 0) {
                    return dataArray.optString(0)
                }
            }
        } catch (e: Exception) {
            logs.add(">>> Lỗi parse trang mục lục từ page.js: ${e.message}")
        }
        return ""
    }

    private fun extractFirstVideoServerUrl(jsonStr: String, hasTrackScript: Boolean, logs: MutableList<String>): String {
        try {
            val element = org.json.JSONTokener(jsonStr).nextValue()
            val items = if (element is org.json.JSONArray) {
                element
            } else if (element is org.json.JSONObject) {
                element.optJSONArray("items")
                    ?: element.optJSONArray("data")
                    ?: element.optJSONArray("list")
                    ?: element.optJSONArray("servers")
            } else {
                null
            }

            if (items != null && items.length() > 0) {
                for (i in 0 until items.length()) {
                    val obj = items.optJSONObject(i) ?: continue
                    val rawData = obj.optString("data").trim()
                    
                    if (hasTrackScript) {
                        if (rawData.isNotBlank()) {
                            return rawData
                        }
                        val rawUrl = obj.optString("url").takeIf { it.isNotBlank() }
                            ?: obj.optString("link").takeIf { it.isNotBlank() }
                            ?: ""
                        if (rawUrl.isNotBlank()) {
                            return rawUrl
                        }
                    } else {
                        var rawUrl = obj.optString("url").takeIf { it.isNotBlank() }
                            ?: obj.optString("link").takeIf { it.isNotBlank() }
                            ?: ""
                        if (rawUrl.isBlank()) {
                            if (rawData.startsWith("http")) {
                                rawUrl = rawData
                            } else if (rawData.startsWith("{")) {
                                try {
                                    val innerObj = org.json.JSONObject(rawData)
                                    rawUrl = innerObj.optString("embedUrl").takeIf { it.isNotBlank() }
                                        ?: innerObj.optString("url").takeIf { it.isNotBlank() }
                                        ?: innerObj.optString("link").takeIf { it.isNotBlank() }
                                        ?: ""
                                } catch (_: Exception) {}
                            }
                        }
                        if (rawUrl.isNotBlank()) {
                            return rawUrl
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logs.add(">>> Lỗi parse danh sách server video: ${e.message}")
        }
        return ""
    }

    private fun resolveUrl(urlStr: String?, hostStr: String?, sourceUrl: String): String {
        if (urlStr == null) return ""
        val trimUrl = urlStr.trim()
        if (trimUrl.isEmpty() || trimUrl.startsWith("http") || trimUrl.startsWith("{")) return trimUrl

        val base = hostStr?.trim()?.ifEmpty { null }
            ?: sourceUrl.trim().ifEmpty { null }
            ?: ""

        if (base.isEmpty()) return trimUrl

        val cleanUrl = if (trimUrl.startsWith("/")) trimUrl.substring(1) else trimUrl
        val cleanBase = if (base.endsWith("/")) base else "$base/"
        return cleanBase + cleanUrl
    }
}

