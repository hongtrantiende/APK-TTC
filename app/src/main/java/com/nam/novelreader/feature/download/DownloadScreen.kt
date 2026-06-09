package com.nam.novelreader.feature.download

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.nam.novelreader.data.local.dao.DownloadTaskDao
import com.nam.novelreader.data.local.dao.ChapterDao
import com.nam.novelreader.data.local.entity.DownloadTaskEntity
import com.nam.novelreader.navigation.Routes
import com.nam.novelreader.feature.components.VBookTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val downloadTaskDao: DownloadTaskDao,
    private val chapterDao: ChapterDao
) : ViewModel() {
    val tasks = downloadTaskDao.getAllTasks()

    init {
        syncTasksProgress()
    }

    private fun syncTasksProgress() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val allTasks = downloadTaskDao.getAllTasksSync()
                for (task in allTasks) {
                    val actualDownloaded = chapterDao.getDownloadedCount(task.novelUrl)
                    
                    // So sánh và cập nhật progress thực tế
                    if (actualDownloaded != task.downloadedChapters) {
                        downloadTaskDao.updateProgress(task.novelUrl, actualDownloaded)
                    }
                    
                    // Nếu số chương thực tế đã tải đủ, tự động hoàn tất task
                    if (actualDownloaded >= task.totalChapters && task.totalChapters > 0 && task.status != "completed") {
                        downloadTaskDao.updateStatus(task.novelUrl, "completed")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun pauseTask(novelUrl: String) {
        viewModelScope.launch {
            downloadTaskDao.updateStatus(novelUrl, "paused")
        }
    }

    fun resumeTask(novelUrl: String) {
        viewModelScope.launch {
            downloadTaskDao.updateStatus(novelUrl, "downloading")
        }
    }

    fun cancelTask(novelUrl: String) {
        viewModelScope.launch {
            downloadTaskDao.updateStatus(novelUrl, "canceled")
        }
    }

    fun deleteTask(novelUrl: String) {
        viewModelScope.launch {
            downloadTaskDao.delete(novelUrl)
        }
    }

    fun clearCompleted() {
        viewModelScope.launch {
            downloadTaskDao.clearCompleted()
        }
    }
}

/**
 * DownloadScreen — quản lý tải truyện offline.
 * Giống VBook: danh sách truyện đang tải/đã tải, progress bar, nút tạm dừng/hủy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    navController: NavHostController,
    viewModel: DownloadViewModel = hiltViewModel()
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle(initialValue = emptyList())
    val accentGold = Color(0xFFD4A574)

    Scaffold(
        containerColor = VBookTheme.backgroundColor(),
        topBar = {
            TopAppBar(
                title = { Text("Tải xuống", fontWeight = FontWeight.Bold, color = VBookTheme.textColor()) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = VBookTheme.textColor())
                    }
                },
                actions = {
                    if (tasks.any { it.status == "completed" }) {
                        IconButton(onClick = { viewModel.clearCompleted() }) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "Xóa hoàn thành", tint = VBookTheme.textColor())
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VBookTheme.backgroundColor(),
                    titleContentColor = VBookTheme.textColor(),
                    navigationIconContentColor = VBookTheme.textColor()
                )
            )
        }
    ) { padding ->
        if (tasks.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Filled.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = VBookTheme.subTextColor()
                    )
                    Text("Chưa có truyện nào được tải", color = VBookTheme.subTextColor(), fontSize = 16.sp)
                    Text(
                        "Vào chi tiết truyện để tải offline",
                        color = VBookTheme.subTextColor().copy(alpha = 0.6f),
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                // Active downloads
                val active = tasks.filter { it.status in listOf("preparing", "downloading") }
                if (active.isNotEmpty()) {
                    item {
                        Text(
                            "Đang tải",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = accentGold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(VBookTheme.cardColor())
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                    items(active) { task ->
                        DownloadTaskItem(
                            task = task,
                            onPause = { viewModel.pauseTask(task.novelUrl) },
                            onResume = { viewModel.resumeTask(task.novelUrl) },
                            onCancel = { viewModel.cancelTask(task.novelUrl) },
                            onDelete = { viewModel.deleteTask(task.novelUrl) },
                            onClick = {
                                navController.navigate(Routes.detail(task.extensionId, task.novelUrl))
                            },
                            onRescue = null
                        )
                    }
                }

                // Paused
                val paused = tasks.filter { it.status == "paused" }
                if (paused.isNotEmpty()) {
                    item {
                        Text(
                            "Tạm dừng",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFFFFA000),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(VBookTheme.cardColor())
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                    items(paused) { task ->
                        DownloadTaskItem(
                            task = task,
                            onPause = { viewModel.pauseTask(task.novelUrl) },
                            onResume = { viewModel.resumeTask(task.novelUrl) },
                            onCancel = { viewModel.cancelTask(task.novelUrl) },
                            onDelete = { viewModel.deleteTask(task.novelUrl) },
                            onClick = {
                                navController.navigate(Routes.detail(task.extensionId, task.novelUrl))
                            },
                            onRescue = null
                        )
                    }
                }

                // Completed
                val completed = tasks.filter { it.status == "completed" }
                if (completed.isNotEmpty()) {
                    item {
                        Text(
                            "Đã hoàn thành",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF4CAF50),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(VBookTheme.cardColor())
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                    items(completed) { task ->
                        DownloadTaskItem(
                            task = task,
                            onPause = { },
                            onResume = { },
                            onCancel = { },
                            onDelete = { viewModel.deleteTask(task.novelUrl) },
                            onClick = {
                                navController.navigate(Routes.detail(task.extensionId, task.novelUrl))
                            },
                            onRescue = null
                        )
                    }
                }

                // Failed
                val failed = tasks.filter { it.status in listOf("failed", "canceled") }
                if (failed.isNotEmpty()) {
                    item {
                        Text(
                            "Lỗi",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFFF44336),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(VBookTheme.cardColor())
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                    items(failed) { task ->
                        val context = androidx.compose.ui.platform.LocalContext.current
                        DownloadTaskItem(
                            task = task,
                            onPause = { },
                            onResume = { 
                                val intent = android.content.Intent(context, com.nam.novelreader.service.DownloadService::class.java).apply {
                                    action = com.nam.novelreader.service.DownloadService.ACTION_RESUME
                                    putExtra(com.nam.novelreader.service.DownloadService.EXTRA_EXTENSION_ID, task.extensionId)
                                    putExtra(com.nam.novelreader.service.DownloadService.EXTRA_NOVEL_URL, task.novelUrl)
                                }
                                context.startService(intent)
                            },
                            onCancel = { },
                            onDelete = { viewModel.deleteTask(task.novelUrl) },
                            onClick = {
                                navController.navigate(Routes.detail(task.extensionId, task.novelUrl))
                            },
                            onRescue = {
                                navController.navigate(Routes.webview(task.novelUrl, task.extensionId))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadTaskItem(
    task: DownloadTaskEntity,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    onRescue: (() -> Unit)?
) {
    val accentGold = Color(0xFFD4A574)
    val progress = if (task.totalChapters > 0) task.downloadedChapters.toFloat() / task.totalChapters else 0f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Cover
        AsyncImage(
            model = task.coverUrl,
            contentDescription = task.novelTitle,
            modifier = Modifier
                .width(50.dp)
                .height(70.dp)
                .clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                task.novelTitle,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = VBookTheme.textColor(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))

            // Status text
            val statusText = when (task.status) {
                "preparing" -> "Đang chuẩn bị..."
                "downloading" -> "${task.downloadedChapters}/${task.totalChapters} chương"
                "paused" -> "Tạm dừng — ${task.downloadedChapters}/${task.totalChapters}"
                "completed" -> "Hoàn thành — ${task.totalChapters} chương"
                "failed" -> "Lỗi: ${task.errorMessage ?: "Unknown"}"
                "canceled" -> "Đã hủy"
                else -> task.status
            }
            val statusColor = when (task.status) {
                "downloading" -> accentGold
                "completed" -> Color(0xFF4CAF50)
                "paused" -> Color(0xFFFFA000)
                "failed", "canceled" -> Color(0xFFF44336)
                else -> Color.Gray
            }
            Text(statusText, fontSize = 12.sp, color = statusColor)

            // Progress bar
            if (task.status in listOf("downloading", "paused")) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = accentGold,
                    trackColor = VBookTheme.subTextColor().copy(alpha = 0.3f),
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Actions
        when (task.status) {
            "downloading" -> {
                IconButton(onClick = onPause, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Pause, contentDescription = "Tạm dừng", tint = VBookTheme.textColor(), modifier = Modifier.size(20.dp))
                }
            }
            "paused" -> {
                IconButton(onClick = onResume, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Tiếp tục", tint = accentGold, modifier = Modifier.size(20.dp))
                }
            }
            "failed", "canceled" -> {
                if (onRescue != null && task.status == "failed") {
                    IconButton(onClick = onRescue, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Warning, contentDescription = "Cứu hộ", tint = Color.Cyan, modifier = Modifier.size(20.dp))
                    }
                }
                IconButton(onClick = onResume, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Thử lại", tint = accentGold, modifier = Modifier.size(20.dp))
                }
            }
            "completed" -> {
                Icon(Icons.Filled.CheckCircle, contentDescription = "Hoàn thành", tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
            }
        }

        // Delete button (always available except when downloading)
        if (task.status != "downloading") {
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Xóa", tint = Color.Gray, modifier = Modifier.size(18.dp))
            }
        }
    }
}
