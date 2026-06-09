package com.nam.novelreader.feature.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.nam.novelreader.data.local.dao.NovelDao
import com.nam.novelreader.data.local.dao.ReadingHistoryDao
import com.nam.novelreader.data.local.entity.NovelEntity
import com.nam.novelreader.data.local.entity.ReadingHistoryEntity
import com.nam.novelreader.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

data class HistoryItem(
    val novelUrl: String,
    val novelTitle: String,
    val novelCover: String,
    val chapterTitle: String,
    val chapterUrl: String,
    val extensionId: String,
    val readAt: Long,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val novelDao: NovelDao,
    private val historyDao: ReadingHistoryDao,
) : ViewModel() {

    val historyNovels: Flow<List<NovelEntity>> = novelDao.getLibraryNovels().map { novels ->
        novels.filter { it.lastReadAt != null }
            .sortedByDescending { it.lastReadAt }
    }

    val recentHistory: Flow<List<ReadingHistoryEntity>> = historyDao.getRecentHistory()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    navController: NavHostController,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val novels by viewModel.historyNovels.collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lịch sử", fontWeight = FontWeight.Bold) },
            )
        }
    ) { padding ->
        if (novels.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📖", style = MaterialTheme.typography.displayLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Chưa có lịch sử đọc",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Truyện bạn đã đọc sẽ hiển thị tại đây",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = novels,
                    key = { it.url }
                ) { novel ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                navController.navigate(Routes.detail(novel.extensionId, novel.url))
                            },
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                        ) {
                            AsyncImage(
                                model = novel.cover,
                                contentDescription = novel.title,
                                modifier = Modifier
                                    .width(60.dp)
                                    .aspectRatio(0.67f)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    novel.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                novel.lastReadChapterUrl?.let { chUrl ->
                                    Text(
                                        "Đang đọc",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                novel.lastReadAt?.let { time ->
                                    Text(
                                        formatRelativeTime(time),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            // Continue reading button
                            novel.lastReadChapterUrl?.let { chUrl ->
                                IconButton(onClick = {
                                    navController.navigate(
                                        Routes.textReader(novel.extensionId, novel.url, chUrl)
                                    )
                                }) {
                                    Icon(
                                        Icons.Filled.PlayArrow,
                                        contentDescription = "Tiếp tục đọc",
                                        tint = MaterialTheme.colorScheme.primary,
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
 * Format thời gian tương đối.
 */
private fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        minutes < 1 -> "Vừa xong"
        minutes < 60 -> "${minutes} phút trước"
        hours < 24 -> "${hours} giờ trước"
        days < 7 -> "${days} ngày trước"
        days < 30 -> "${days / 7} tuần trước"
        else -> "${days / 30} tháng trước"
    }
}
