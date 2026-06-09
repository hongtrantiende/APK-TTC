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
import javax.inject.Inject

@HiltViewModel
class ExtensionViewModel @Inject constructor(
    private val extensionLoader: ExtensionLoader,
    private val extensionDao: ExtensionDao,
    private val repositoryDao: RepositoryDao,
    val appPrefs: AppPreferences,
    private val googleDriveBackupManager: GoogleDriveBackupManager,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) : ViewModel() {

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
     * Fetch extensions từ built-in assets.
     */
    fun fetchAllExtensions() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                // Tải trực tiếp từ assets
                val allExts = extensionLoader.fetchBuiltInExtensions()
                _availableExtensions.value = allExts
                prefetchIcons(allExts)
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
                // Tải trực tiếp từ assets (bỏ qua url)
                val exts = extensionLoader.fetchBuiltInExtensions()
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
                if (info.icon.startsWith("gdrive://")) {
                    val filename = info.icon.substringAfter("gdrive://")
                    val cacheFile = java.io.File(cacheFolder, filename)
                    
                    if (!cacheFile.exists()) {
                        googleDriveBackupManager.downloadFileFromExtensionFolder(filename).onSuccess { bytes ->
                            try {
                                cacheFile.writeBytes(bytes)
                                updateAvailableExtensionIcon(info.name, cacheFile.absolutePath)
                            } catch (e: Exception) {
                                android.util.Log.e("ExtVM", "Failed to write cached icon for ${info.name}", e)
                            }
                        }.onFailure { e ->
                            android.util.Log.e("ExtVM", "Failed to download icon for ${info.name} from Drive", e)
                        }
                    } else {
                        updateAvailableExtensionIcon(info.name, cacheFile.absolutePath)
                    }
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
}
