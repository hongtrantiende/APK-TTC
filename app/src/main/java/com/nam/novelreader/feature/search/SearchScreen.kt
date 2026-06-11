package com.nam.novelreader.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import com.nam.novelreader.domain.model.Novel
import com.nam.novelreader.domain.model.SearchResult
import com.nam.novelreader.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: NovelRepository,
) : ViewModel() {
    private val _results = MutableStateFlow<List<Novel>>(emptyList())
    val results: StateFlow<List<Novel>> = _results

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _sourceUrl = MutableStateFlow("")
    val sourceUrl: StateFlow<String> = _sourceUrl

    private val _hasSearched = MutableStateFlow(false)
    val hasSearched: StateFlow<Boolean> = _hasSearched

    fun loadExtensionInfo(extensionId: String) {
        android.util.Log.d("SearchVM", "loadExtensionInfo: extensionId=$extensionId")
        viewModelScope.launch {
            val extension = repository.getExtension(extensionId)
            _sourceUrl.value = extension?.pluginJson?.metadata?.source ?: ""
        }
    }

    fun search(extensionId: String, query: String) {
        android.util.Log.d("SearchVM", "search: extensionId=$extensionId, query=$query")
        viewModelScope.launch {
            _isLoading.value = true
            _hasSearched.value = true
            try {
                val result = repository.searchNovels(extensionId, query)
                android.util.Log.d("SearchVM", "search results size: ${result.novels.size}")
                _results.value = result.novels
            } catch (e: Exception) {
                android.util.Log.e("SearchVM", "search failed: ${e.message}", e)
                _results.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    extensionId: String,
    initialQuery: String? = null,
    navController: NavHostController,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    var query by remember { mutableStateOf(initialQuery ?: "") }
    val results by viewModel.results.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val sourceUrl by viewModel.sourceUrl.collectAsStateWithLifecycle()
    val hasSearched by viewModel.hasSearched.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(extensionId, initialQuery) {
        viewModel.loadExtensionInfo(extensionId)
        if (!initialQuery.isNullOrBlank()) {
            viewModel.search(extensionId, initialQuery)
        }
    }

    Scaffold(
        containerColor = com.nam.novelreader.feature.components.VBookTheme.backgroundColor(),
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Tìm kiếm truyện...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = {
                                if (query.isNotBlank()) {
                                    viewModel.search(extensionId, query)
                                    keyboardController?.hide()
                                }
                            }) {
                                Icon(Icons.Filled.Search, contentDescription = "Search")
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                if (query.isNotBlank()) {
                                    viewModel.search(extensionId, query)
                                    keyboardController?.hide()
                                }
                            }
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = com.nam.novelreader.feature.components.VBookTheme.textColor())
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = com.nam.novelreader.feature.components.VBookTheme.backgroundColor()
                )
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (hasSearched && results.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 32.dp)
                ) {
                    Text("🔍", style = MaterialTheme.typography.displayMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Không tìm thấy truyện nào!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = com.nam.novelreader.feature.components.VBookTheme.textColor()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Vui lòng kiểm tra lại từ khóa hoặc mở trang nguồn để kiểm tra kết nối (và vượt Cloudflare/Captcha nếu có).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    if (sourceUrl.isNotBlank()) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { navController.navigate(Routes.webview(sourceUrl, extensionId)) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Mở trang nguồn", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            val uniqueResults = remember(results) { results.distinctBy { it.url } }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = uniqueResults,
                    key = { it.url }
                ) { novel ->
                    NovelListItem(novel) {
                        navController.navigate(Routes.detail(extensionId, novel.url))
                    }
                }
            }
        }
    }
}

@Composable
fun NovelListItem(novel: Novel, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = com.nam.novelreader.feature.components.VBookTheme.cardColor()),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            AsyncImage(
                model = com.nam.novelreader.feature.components.buildNovelImageRequest(novel),
                contentDescription = novel.title,
                modifier = Modifier
                    .width(80.dp)
                    .aspectRatio(0.67f)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = novel.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (novel.author.isNotBlank()) {
                    Text(
                        text = novel.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (novel.status.isNotBlank()) {
                    Text(
                        text = novel.status,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (novel.latestChapter != null) {
                    Text(
                        text = novel.latestChapter,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

