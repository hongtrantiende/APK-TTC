package com.nam.novelreader.feature.listnovel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nam.novelreader.data.repository.NovelRepository
import com.nam.novelreader.domain.model.Novel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ListNovelViewModel @Inject constructor(
    private val repository: NovelRepository,
) : ViewModel() {

    private val _novels = MutableStateFlow<List<Novel>>(emptyList())
    val novels: StateFlow<List<Novel>> = _novels

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isPagingLoading = MutableStateFlow(false)
    val isPagingLoading: StateFlow<Boolean> = _isPagingLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var currentExtensionIdStr: String = ""
    private var currentScript: String = ""
    private var currentInput: String = ""
    private var nextPageKey: String? = null

    fun loadData(extensionId: String, script: String, input: String) {
        if (currentExtensionIdStr == extensionId && currentScript == script && currentInput == input) return
        
        currentExtensionIdStr = extensionId
        currentScript = script
        currentInput = input
        nextPageKey = null
        
        _novels.value = emptyList()
        _error.value = null

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val pageResult = repository.getHomeTabNovels(
                    extensionId = currentExtensionIdStr,
                    tabScript = currentScript,
                    tabInput = currentInput,
                    pageKey = null
                )
                _novels.value = pageResult.novels
                nextPageKey = pageResult.nextPageKey
            } catch (e: Exception) {
                _error.value = e.message
                nextPageKey = null
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadNextPage() {
        if (_isPagingLoading.value || _isLoading.value) return
        val currentKey = nextPageKey
        if (currentKey == null && _novels.value.isNotEmpty()) return
        if (currentKey != null && currentKey.isBlank()) return

        _isPagingLoading.value = true
        viewModelScope.launch {
            try {
                val pageResult = repository.getHomeTabNovels(
                    extensionId = currentExtensionIdStr,
                    tabScript = currentScript,
                    tabInput = currentInput,
                    pageKey = currentKey
                )
                if (pageResult.novels.isNotEmpty()) {
                    _novels.value = _novels.value + pageResult.novels
                }
                nextPageKey = pageResult.nextPageKey
            } catch (e: Exception) {
                android.util.Log.e("ListNovelVM", "loadNextPage failed: ${e.message}")
            } finally {
                _isPagingLoading.value = false
            }
        }
    }
}
