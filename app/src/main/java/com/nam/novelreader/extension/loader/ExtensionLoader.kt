package com.nam.novelreader.extension.loader

import android.content.Context
import android.util.Log
import com.nam.novelreader.data.local.dao.ExtensionDao
import com.nam.novelreader.data.local.dao.RepositoryDao
import com.nam.novelreader.data.local.entity.ExtensionEntity
import com.nam.novelreader.data.local.entity.RepositoryEntity
import com.nam.novelreader.data.network.GoogleDriveBackupManager
import kotlinx.coroutines.flow.first
import com.nam.novelreader.extension.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ExtensionLoader — download, cài đặt, và quản lý VBook extensions.
 *
 * Workflow:
 * 1. fetchRepository(url) → danh sách ExtensionInfo từ repo
 * 2. installExtension(info) → download ZIP, giải nén, parse plugin.json
 * 3. loadExtension(id) → load extension đã cài vào memory
 * 4. uninstallExtension(id) → xóa extension
 */
@Singleton
class ExtensionLoader @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val httpClient: OkHttpClient,
    private val extensionDao: ExtensionDao,
    private val repositoryDao: RepositoryDao,
    private val googleDriveBackupManager: GoogleDriveBackupManager,
) {
    companion object {
        private const val TAG = "ExtLoader"
        /** Default VBook community extension repository */
        const val DEFAULT_REPO_URL = "https://www.vbookext.me/api/plugin.json"
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val extensionsDir = File(appContext.filesDir, "extensions")
    private val cache = mutableMapOf<String, LoadedExtension>()

    fun clearCache(extensionId: String? = null) {
        if (extensionId != null) {
            cache.remove(extensionId)
        } else {
            cache.clear()
        }
    }

    init {
        extensionsDir.mkdirs()
    }

    /**
     * Fetch danh sách extensions từ assets.
     */
    suspend fun fetchBuiltInExtensions(): List<ExtensionInfo> {
        return fetchRepository("file:///android_asset/plugin.json")
    }

    /**
     * Fetch danh sách extensions từ 1 repository URL.
     * Repository trả về JSON: { metadata: {...}, data: [...] }
     */
    suspend fun fetchRepository(repoUrl: String): List<ExtensionInfo> = withContext(Dispatchers.IO) {
        try {
            val body = if (repoUrl.startsWith("file:///android_asset/")) {
                val assetPath = repoUrl.substringAfter("file:///android_asset/")
                appContext.assets.open(assetPath).bufferedReader().use { it.readText() }
            } else {
                val request = Request.Builder().url(repoUrl).build()
                val response = httpClient.newCall(request).execute()
                response.body?.string() ?: return@withContext emptyList()
            }

            val repo = json.decodeFromString<RepositoryIndex>(body)
            repo.data
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to fetch repo: $repoUrl", e)
            emptyList()
        }
    }

    /**
     * Download và cài đặt 1 extension từ repository.
     */
    suspend fun installExtension(info: ExtensionInfo): LoadedExtension? = withContext(Dispatchers.IO) {
        try {
            val extId = info.name.toSlug()
            val extDir = File(extensionsDir, extId)
            extDir.deleteRecursively() // Clear old/corrupted files
            extDir.mkdirs()

            val pluginJson: PluginJson
            var iconPath: String? = null

            if (info.path.startsWith("file:///android_asset/") && !info.path.endsWith(".zip")) {
                // A. Cài đặt trực tiếp từ thư mục asset (unzipped)
                val assetPath = info.path.substringAfter("file:///android_asset/")
                Log.d(TAG, "Installing unzipped extension folder from assets: $assetPath")
                copyAssetFolder(assetPath, extDir)

                val pluginJsonFile = File(extDir, "plugin.json")
                if (!pluginJsonFile.exists()) {
                    throw ExtensionException("plugin.json not found in asset extension folder: ${info.name}")
                }
                pluginJson = json.decodeFromString<PluginJson>(pluginJsonFile.readText())

                val iconFile = File(extDir, "icon.png")
                if (iconFile.exists()) {
                    iconPath = iconFile.absolutePath
                }
            } else {
                // B. Cài đặt từ file ZIP (cũ: online hoặc local zip)
                // 1. Get plugin.zip bytes
                Log.d(TAG, "Loading extension: ${info.name} from ${info.path}")
                val zipBytes = if (info.path.startsWith("file:///android_asset/")) {
                    val assetPath = info.path.substringAfter("file:///android_asset/")
                    appContext.assets.open(assetPath).use { it.readBytes() }
                } else if (info.path.startsWith("gdrive://")) {
                    val filename = info.path.substringAfter("gdrive://")
                    googleDriveBackupManager.downloadFileFromExtensionFolder(filename).getOrThrow()
                } else {
                    val zipRequest = Request.Builder().url(info.path).build()
                    val zipResponse = httpClient.newCall(zipRequest).execute()
                    zipResponse.body?.bytes() ?: throw ExtensionException("Empty download")
                }

                // 2. Giải nén ZIP
                ZipInputStream(zipBytes.inputStream()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val normalizedName = entry.name.replace('\\', '/')
                        val outFile = File(extDir, normalizedName)
                        if (entry.isDirectory || normalizedName.endsWith("/")) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { out ->
                                zip.copyTo(out)
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }

                // 3. Parse plugin.json
                val pluginJsonFile = File(extDir, "plugin.json")
                if (!pluginJsonFile.exists()) {
                    throw ExtensionException("plugin.json not found in extension: ${info.name}")
                }
                pluginJson = json.decodeFromString<PluginJson>(pluginJsonFile.readText())

                // 4. Load icon nếu có
                if (info.icon.isNotBlank()) {
                    try {
                        val iconFile = File(extDir, "icon.png")
                        val iconBytes = if (info.icon.startsWith("file:///android_asset/")) {
                            val assetPath = info.icon.substringAfter("file:///android_asset/")
                            appContext.assets.open(assetPath).use { it.readBytes() }
                        } else if (info.icon.startsWith("gdrive://")) {
                            val filename = info.icon.substringAfter("gdrive://")
                            googleDriveBackupManager.downloadFileFromExtensionFolder(filename).getOrNull()
                        } else if (info.icon.startsWith("/") && File(info.icon).exists()) {
                            File(info.icon).readBytes()
                        } else {
                            val iconRequest = Request.Builder().url(info.icon).build()
                            httpClient.newCall(iconRequest).execute().body?.bytes()
                        }
                        iconBytes?.let {
                            iconFile.writeBytes(it)
                            iconPath = iconFile.absolutePath
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to load icon for ${info.name}: ${e.message}")
                    }
                }

                // Fallback: Nếu không tải được icon từ info.icon hoặc iconPath vẫn null,
                // kiểm tra xem trong zip giải nén có icon.png sẵn không
                if (iconPath == null) {
                    val localIconFile = File(extDir, "icon.png")
                    if (localIconFile.exists()) {
                        iconPath = localIconFile.absolutePath
                    }
                }
            }

            // 5. Lưu vào database
            val normalizedType = getNormalizedType(info.name, info.source, info.type)
            extensionDao.insert(
                ExtensionEntity(
                    id = extId,
                    name = info.name,
                    author = info.author,
                    version = info.version,
                    source = info.source,
                    type = normalizedType,
                    locale = info.locale,
                    description = info.description,
                    localPath = extDir.absolutePath,
                    iconPath = iconPath,
                    isInstalled = true,
                    isEnabled = true,
                )
            )

            val loaded = LoadedExtension(pluginJson, extDir)
            cache[extId] = loaded
            Log.d(TAG, "✅ Installed extension: ${info.name}")
            loaded
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to install extension: ${info.name}", e)
            null
        }
    }

    /**
     * Load extension đã cài vào memory (từ local files).
     */
    suspend fun loadExtension(extensionId: String): LoadedExtension? {
        // Check cache
        cache[extensionId]?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                val entity = extensionDao.getExtensionById(extensionId) ?: return@withContext null
                val extDir = File(entity.localPath)
                if (!extDir.exists()) return@withContext null

                val pluginJsonFile = File(extDir, "plugin.json")
                if (!pluginJsonFile.exists()) return@withContext null

                val pluginJson = json.decodeFromString<PluginJson>(pluginJsonFile.readText())
                val loaded = LoadedExtension(pluginJson, extDir)
                cache[extensionId] = loaded
                loaded
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to load extension: $extensionId", e)
                null
            }
        }
    }

    /**
     * Gỡ cài đặt extension.
     */
    suspend fun uninstallExtension(extensionId: String) = withContext(Dispatchers.IO) {
        try {
            val entity = extensionDao.getExtensionById(extensionId)
            entity?.let {
                File(it.localPath).deleteRecursively()
                extensionDao.delete(it)
                cache.remove(extensionId)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to uninstall extension: $extensionId", e)
        }
    }
 
    suspend fun downloadAndInstallTtsExtensions(registryUrl: String = "https://www.vbookext.me/api/registry/vbook-a427a9f1.json") = withContext(Dispatchers.IO) {
        try {
            val installed = extensionDao.getInstalledExtensions().first()
            val otherTts = installed.filter { it.type == "tts" && it.id != "chillaudio-tts" }
            for (ext in otherTts) {
                Log.d(TAG, "Removing non-allowed TTS extension: ${ext.name}")
                uninstallExtension(ext.id)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to uninstall non-allowed TTS extensions", e)
        }

        try {
            Log.d(TAG, "Fetching TTS extensions registry from $registryUrl")
            val extensions = fetchRepository(registryUrl)
            Log.d(TAG, "Found ${extensions.size} TTS extensions in registry")
            for (info in extensions) {
                val extId = info.name.toSlug()
                if (extId == "chillaudio-tts") {
                    val existing = extensionDao.getExtensionById(extId)
                    if (existing == null || !existing.isInstalled) {
                        Log.d(TAG, "Auto-installing TTS extension: ${info.name}")
                        installExtension(info)
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to download and install TTS extensions", e)
        }
    }
 
    /**
     * Thêm repository mặc định nếu chưa có.
     * Chỉ nạp duy nhất repository nội bộ (offline assets) để đảm bảo tính an toàn và local-first.
     */
    suspend fun ensureDefaultRepository() {
        val repos = repositoryDao.getEnabledRepositories()
        val hasLocal = repos.any { it.url == "file:///android_asset/plugin.json" }

        if (!hasLocal) {
            repositoryDao.insert(
                RepositoryEntity(
                    url = "file:///android_asset/plugin.json",
                    name = "Thư viện Tiện ích (Cục bộ)",
                )
            )
        }
    }

    private fun copyAssetFolder(assetPath: String, targetDir: File) {
        val assets = appContext.assets.list(assetPath)
        if (assets.isNullOrEmpty()) {
            try {
                appContext.assets.open(assetPath).use { input ->
                    targetDir.parentFile?.mkdirs()
                    FileOutputStream(targetDir).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                targetDir.mkdirs()
            }
        } else {
            targetDir.mkdirs()
            for (asset in assets) {
                val childAssetPath = if (assetPath.isEmpty()) asset else "$assetPath/$asset"
                val childTargetFile = File(targetDir, asset)
                copyAssetFolder(childAssetPath, childTargetFile)
            }
        }
    }

    private fun String.toSlug(): String {
        // Normalize Unicode → strip diacritics → lowercase → slug
        val normalized = java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD)
            .replace(Regex("[\\p{InCombiningDiacriticalMarks}]"), "")
        return normalized
            .lowercase()
            .replace("đ", "d").replace("Đ", "d")
            .replace(Regex("[^a-z0-9]"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
    }

    suspend fun autoFixExtensionTypes() = withContext(Dispatchers.IO) {
        try {
            // Lấy tất cả các extension đã cài
            val installed = extensionDao.getEnabledExtensions()
            for (ext in installed) {
                val newType = getNormalizedType(ext.name, ext.source, ext.type)
                if (newType != ext.type) {
                    extensionDao.insert(ext.copy(type = newType))
                    Log.d(TAG, "Auto-fixed type for installed extension: ${ext.name} [${ext.type} -> $newType]")
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to auto-fix extension types: ${e.message}", e)
        }
    }

    private fun getNormalizedType(name: String, source: String, rawType: String): String {
        val cleanName = name.lowercase(java.util.Locale.ROOT)
        val cleanSource = source.lowercase(java.util.Locale.ROOT)
        return when {
            cleanSource.contains("sangtacviet") || cleanSource.contains("14.225.254.182") || cleanName.contains("stv") -> "novel"
            cleanSource.contains("tvtruyen") || cleanName.contains("tvtruyen") -> "novel"
            cleanSource.contains("truyentv") || cleanName.contains("truyện tv") -> "novel"
            cleanSource.contains("shinigamilnteam") || cleanName.contains("shinigami") -> "novel"
            cleanSource.contains("wnacg") || cleanName.contains("wnacg") -> "comic"
            cleanSource.contains("myreadingmanga") || cleanName.contains("myreadingmanga") -> "comic"
            cleanSource.contains("truyenqq") || cleanName.contains("truyện qq") || cleanName.contains("truyenqq") || cleanSource.contains("qqko") -> "comic"
            cleanSource.contains("ac.qq.com") || cleanName.contains("ac qq") -> "comic"
            cleanSource.contains("8muses") || cleanName.contains("8muses") -> "comic"
            cleanName.contains("scans") || cleanName.contains("raw") || cleanName.contains("sexy") || cleanSource.contains("scans") || cleanSource.contains("rawdevart") -> "comic"
            else -> rawType
        }
    }
}
