package com.nam.novelreader.feature.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nam.novelreader.data.repository.NovelRepository
import com.nam.novelreader.domain.model.HomeTab
import com.nam.novelreader.domain.model.Novel
import com.nam.novelreader.extension.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import com.nam.novelreader.data.local.entity.ExtensionEntity
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val repository: NovelRepository,
) : ViewModel() {

    private val _homeTabs = MutableStateFlow<List<HomeTab>>(emptyList())
    val homeTabs: StateFlow<List<HomeTab>> = _homeTabs

    private val _genreTabs = MutableStateFlow<List<HomeTab>>(emptyList())
    val genreTabs: StateFlow<List<HomeTab>> = _genreTabs

    private val _selectedTab = MutableStateFlow<HomeTab?>(null)
    val selectedTab: StateFlow<HomeTab?> = _selectedTab

    private val _novels = MutableStateFlow<List<Novel>>(emptyList())
    val novels: StateFlow<List<Novel>> = _novels

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _isPagingLoading = MutableStateFlow(false)
    val isPagingLoading: StateFlow<Boolean> = _isPagingLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _extensionName = MutableStateFlow("")
    val extensionName: StateFlow<String> = _extensionName

    private val _extensionSource = MutableStateFlow("")
    val extensionSource: StateFlow<String> = _extensionSource

    private val _extensionIconPath = MutableStateFlow<String?>(null)
    val extensionIconPath: StateFlow<String?> = _extensionIconPath

    private val _extensionLocale = MutableStateFlow("vi_VN")
    val extensionLocale: StateFlow<String> = _extensionLocale

    val installedExtensions: Flow<List<ExtensionEntity>> = repository.getInstalledExtensions()

    private val _currentExtensionId = MutableStateFlow<String?>(null)
    val currentExtensionId: StateFlow<String?> = _currentExtensionId

    private var currentExtensionIdStr: String = ""
    private var nextPageKey: String? = null

    private val _tabNovels = MutableStateFlow<Map<String, List<Novel>>>(emptyMap())
    val tabNovels: StateFlow<Map<String, List<Novel>>> = _tabNovels

    private val _tabNextPageKeys = MutableStateFlow<Map<String, String?>>(emptyMap())

    init {
        viewModelScope.launch {
            installedExtensions.collect { list ->
                if (_currentExtensionId.value == null && list.isNotEmpty()) {
                    val lastUsedId = repository.getLastUsedExtensionId()
                    val defaultId = if (lastUsedId != null && list.any { it.id == lastUsedId }) {
                        lastUsedId
                    } else {
                        list.first().id
                    }
                    _currentExtensionId.value = defaultId
                    loadHome(defaultId)
                }
            }
        }
    }

    fun selectExtension(id: String) {
        _currentExtensionId.value = id
        repository.setLastUsedExtensionId(id)
        loadHome(id)
    }


    fun loadHome(extensionId: String) {
        currentExtensionIdStr = extensionId
        _currentExtensionId.value = extensionId
        repository.setLastUsedExtensionId(extensionId)
        
        // Reset toàn bộ state cũ để tránh crash LazyGrid và gọi nhầm tab của extension cũ
        _homeTabs.value = emptyList()
        _genreTabs.value = emptyList()
        _selectedTab.value = null
        _novels.value = emptyList()
        _tabNovels.value = emptyMap()
        _tabNextPageKeys.value = emptyMap()
        nextPageKey = null
        
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                // Tải thông tin extension chi tiết
                val ext = repository.getExtension(extensionId)
                ext?.let {
                    var clean = ext.pluginJson.metadata.name
                    val bracketRegex = Regex("\\[[^\\]]*(đăng nhập|login|vpn|🔑|🌐)[^\\]]*\\]", RegexOption.IGNORE_CASE)
                    clean = bracketRegex.replace(clean, "")
                    clean = clean.replace("(", "").replace(")", "").replace("[", "").replace("]", "")
                    clean = clean.replace(Regex("\\s+"), " ").trim()
                    
                    _extensionName.value = clean
                    _extensionSource.value = ext.pluginJson.metadata.source
                    _extensionIconPath.value = ext.getIconFile()?.absolutePath
                    _extensionLocale.value = ext.pluginJson.metadata.locale
                }

                val tabs = repository.getHome(extensionId)
                val genres = repository.getGenres(extensionId)
                _homeTabs.value = tabs
                _genreTabs.value = genres
                if (tabs.isNotEmpty()) {
                    selectTab(tabs[0])
                } else {
                    _novels.value = emptyList()
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _error.value = e.message
                _isLoading.value = false
            }
        }
    }


    fun selectTab(tab: HomeTab) {
        _selectedTab.value = tab
        nextPageKey = _tabNextPageKeys.value[tab.name]
        
        // Nếu tab chưa từng tải dữ liệu, tiến hành tải mới
        if (!_tabNovels.value.containsKey(tab.name)) {
            loadTabNovels(tab)
        } else {
            // Đã tải rồi thì chỉ cần cập nhật _novels để tương thích ngược nếu cần
            _novels.value = _tabNovels.value[tab.name] ?: emptyList()
        }
    }

    fun loadNextPage() {
        val tab = _selectedTab.value ?: return
        if (_isPagingLoading.value || _isLoading.value) return
        val currentKey = _tabNextPageKeys.value[tab.name]
        val currentNovels = _tabNovels.value[tab.name] ?: emptyList()
        if (currentKey == null && currentNovels.isNotEmpty()) return
        if (currentKey != null && currentKey.isBlank()) return

        _isPagingLoading.value = true
        viewModelScope.launch {
            try {
                val pageResult = repository.getHomeTabNovels(
                    extensionId = currentExtensionIdStr,
                    tabScript = tab.script,
                    tabInput = tab.input,
                    pageKey = currentKey
                )
                if (pageResult.novels.isNotEmpty()) {
                    val updatedNovels = currentNovels + pageResult.novels
                    _tabNovels.value = _tabNovels.value + (tab.name to updatedNovels)
                    _novels.value = updatedNovels
                }
                _tabNextPageKeys.value = _tabNextPageKeys.value + (tab.name to pageResult.nextPageKey)
                nextPageKey = pageResult.nextPageKey
            } catch (e: Exception) {
                android.util.Log.e("BrowseViewModel", "loadNextPage failed: ${e.message}")
            } finally {
                _isPagingLoading.value = false
            }
        }
    }


    private fun loadTabNovels(tab: HomeTab) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                if (tab.novels.isNotEmpty()) {
                    // Fallback tab đã có sẵn novels
                    _tabNovels.value = _tabNovels.value + (tab.name to tab.novels)
                    _tabNextPageKeys.value = _tabNextPageKeys.value + (tab.name to null)
                    _novels.value = tab.novels
                    nextPageKey = null
                } else if (tab.script.isNotBlank()) {
                    val pageResult = repository.getHomeTabNovels(
                        extensionId = currentExtensionIdStr,
                        tabScript = tab.script,
                        tabInput = tab.input,
                        pageKey = null
                    )
                    _tabNovels.value = _tabNovels.value + (tab.name to pageResult.novels)
                    _tabNextPageKeys.value = _tabNextPageKeys.value + (tab.name to pageResult.nextPageKey)
                    _novels.value = pageResult.novels
                    nextPageKey = pageResult.nextPageKey
                } else {
                    _tabNovels.value = _tabNovels.value + (tab.name to emptyList())
                    _tabNextPageKeys.value = _tabNextPageKeys.value + (tab.name to null)
                    _novels.value = emptyList()
                    nextPageKey = null
                }
            } catch (e: Exception) {
                _error.value = e.message
                _tabNextPageKeys.value = _tabNextPageKeys.value + (tab.name to null)
                nextPageKey = null
            }
            _isLoading.value = false
        }
    }

    fun refresh() {
        val extId = currentExtensionId.value ?: return
        viewModelScope.launch {
            _isRefreshing.value = true
            _error.value = null
            try {
                val tabs = repository.getHome(extId)
                val genres = repository.getGenres(extId)
                _homeTabs.value = tabs
                _genreTabs.value = genres
                
                // Xóa cache cũ để làm mới hoàn toàn
                _tabNovels.value = emptyMap()
                _tabNextPageKeys.value = emptyMap()
                
                val currentTab = _selectedTab.value
                val matchingTab = tabs.find { it.name == currentTab?.name } ?: tabs.firstOrNull()
                
                if (matchingTab != null) {
                    _selectedTab.value = matchingTab
                    if (matchingTab.script.isNotBlank()) {
                        val pageResult = repository.getHomeTabNovels(
                            extensionId = extId,
                            tabScript = matchingTab.script,
                            tabInput = matchingTab.input,
                            pageKey = null
                        )
                        _tabNovels.value = mapOf(matchingTab.name to pageResult.novels)
                        _tabNextPageKeys.value = mapOf(matchingTab.name to pageResult.nextPageKey)
                        _novels.value = pageResult.novels
                        nextPageKey = pageResult.nextPageKey
                    } else {
                        _tabNovels.value = mapOf(matchingTab.name to matchingTab.novels)
                        _tabNextPageKeys.value = mapOf(matchingTab.name to null)
                        _novels.value = matchingTab.novels
                        nextPageKey = null
                    }
                } else {
                    _novels.value = emptyList()
                    nextPageKey = null
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
