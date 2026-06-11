package com.nam.novelreader.feature.reader

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nam.novelreader.domain.model.Chapter
import com.nam.novelreader.domain.model.Novel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderTocBottomSheet(
    novel: Novel?,
    chapters: List<Chapter>,
    currentChapterUrl: String,
    themeIndex: Int,
    onChapterClick: (Chapter) -> Unit,
    extensionId: String,
    novelUrl: String,
    onRefreshToc: () -> Unit,
    onDismiss: () -> Unit,
    isOffline: Boolean = false
) {
    // Đồng bộ màu sắc dựa trên theme đọc sách
    val (bgColor, textColor, primaryColor, cardColor) = when (themeIndex) {
        0 -> Quadruple(Color(0xFFD3C3A3), Color(0xFF3A3129), Color(0xFF8B5A2B), Color(0xFFC5B595)) // Kraft hoa văn (bg7.jpg)
        1 -> Quadruple(Color(0xFFFFFFFF), Color(0xFF3A342B), Color(0xFF1976D2), Color(0xFFF0F0F0)) // Trắng trơn
        2 -> Quadruple(Color(0xFFF1F7ED), Color(0xFF1B310E), Color(0xFF2E7D32), Color(0xFFE2EADF)) // Xanh lá hoa văn (bg6.png)
        3 -> Quadruple(Color(0xFFA2C0E5), Color(0xFF1B310E), Color(0xFF1976D2), Color(0xFF90B0D5)) // Xanh dương hoa văn (bg4.jpg)
        4 -> Quadruple(Color(0xFF1C1C1C), Color(0xFFCCCCCC), Color(0xFFD4A574), Color(0xFF282828)) // Đêm đen
        5 -> Quadruple(Color(0xFFF3C9D7), Color(0xFF1B310E), Color(0xFFC2185B), Color(0xFFE5B9C7)) // Hồng hoa văn (bg5.jpg)
        6 -> Quadruple(Color(0xFFECE1CA), Color(0xFF645032), Color(0xFF8B5A2B), Color(0xFFDDD2BA)) // Vàng giấy trơn
        7 -> Quadruple(Color(0xFFC2E0CD), Color(0xFF334B39), Color(0xFF2E7D32), Color(0xFFB2D0BD)) // Lục nhạt trơn
        else -> Quadruple(Color(0xFFD3C3A3), Color(0xFF3A3129), Color(0xFF8B5A2B), Color(0xFFC5B595))
    }

    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Mục lục, 1: Đánh dấu
    var searchQuery by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val filteredChapters = remember(chapters, searchQuery) {
        val list = if (searchQuery.isBlank()) {
            chapters
        } else {
            chapters.filter { it.title.contains(searchQuery, ignoreCase = true) }
        }
        list.distinctBy { it.url }
    }

    // Cuộn ngay đến vị trí chương hiện tại khi mở
    LaunchedEffect(chapters, currentChapterUrl) {
        val currentIndex = chapters.indexOfFirst { it.url == currentChapterUrl }
        if (currentIndex >= 0) {
            // Cuộn mượt hoặc cuộn tức thời về chương hiện tại
            listState.scrollToItem((currentIndex - 3).coerceAtLeast(0))
        }
    }

    androidx.activity.compose.BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f)) // Scrim
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) {
                onDismiss()
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(bgColor)
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) {
                    // Ngăn chạm lan truyền làm ẩn sheet
                }
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                // Drag handle bar giả lập bottom sheet
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(vertical = 12.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .background(textColor.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
                )
                // === Header Info ===
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Bìa truyện nhỏ ở góc trái
                    AsyncImage(
                        model = novel?.cover ?: "",
                        contentDescription = novel?.title,
                        modifier = Modifier
                            .size(width = 50.dp, height = 75.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(cardColor),
                        contentScale = ContentScale.Crop
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = novel?.title ?: "Đang tải truyện...",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = novel?.author ?: "Tác giả: Chưa rõ",
                            fontSize = 12.sp,
                            color = textColor.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Nhóm icon góc phải: Refresh, Comment, TTS-mute, Download
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isOffline) {
                            IconButton(onClick = onRefreshToc) {
                                Icon(Icons.Filled.Refresh, "Làm mới", tint = textColor, modifier = Modifier.size(20.dp))
                            }
                        }
                        IconButton(onClick = {}) {
                            Icon(Icons.Filled.RateReview, "Đánh giá", tint = textColor, modifier = Modifier.size(20.dp))
                        }
                        if (!isOffline) {
                            IconButton(onClick = {
                                val intent = Intent(context, com.nam.novelreader.service.DownloadService::class.java).apply {
                                    action = com.nam.novelreader.service.DownloadService.ACTION_DOWNLOAD
                                    putExtra(com.nam.novelreader.service.DownloadService.EXTRA_EXTENSION_ID, extensionId)
                                    putExtra(com.nam.novelreader.service.DownloadService.EXTRA_NOVEL_URL, novelUrl)
                                    putExtra(com.nam.novelreader.service.DownloadService.EXTRA_NOVEL_TITLE, novel?.title ?: "")
                                    putExtra(com.nam.novelreader.service.DownloadService.EXTRA_COVER_URL, novel?.cover ?: "")
                                }
                                context.startService(intent)
                                android.widget.Toast.makeText(context, "Bắt đầu tải toàn bộ chương ngoại tuyến...", android.widget.Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Filled.CloudDownload, "Tải xuống", tint = textColor, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }

                // === Tabs: Mục lục (X) | Đánh dấu (Y) ===
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { selectedTab = 0 },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = if (selectedTab == 0) primaryColor else textColor.copy(alpha = 0.6f)
                        )
                    ) {
                        Text(
                            text = "Mục lục ${chapters.size}",
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    TextButton(
                        onClick = { selectedTab = 1 },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = if (selectedTab == 1) primaryColor else textColor.copy(alpha = 0.6f)
                        )
                    ) {
                        Text(
                            text = "Đánh dấu 0",
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 16.sp
                        )
                    }
                }

                Divider(color = textColor.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))

                if (selectedTab == 0) {
                    // === Ô tìm kiếm chương ===
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { 
                            Text(
                                "Tìm kiếm ${chapters.size} chương", 
                                color = textColor.copy(alpha = 0.4f),
                                fontSize = 14.sp
                            ) 
                        },
                        leadingIcon = { 
                            Icon(
                                imageVector = Icons.Filled.Search, 
                                contentDescription = "Tìm kiếm", 
                                tint = textColor.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp)
                            ) 
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Filled.Clear,
                                        contentDescription = "Xóa",
                                        tint = textColor.copy(alpha = 0.5f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryColor.copy(alpha = 0.5f),
                            unfocusedBorderColor = textColor.copy(alpha = 0.12f),
                            focusedTextColor = textColor,
                            unfocusedTextColor = textColor,
                            focusedContainerColor = cardColor,
                            unfocusedContainerColor = cardColor
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .padding(bottom = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // === Danh sách chương ===
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        itemsIndexed(filteredChapters, key = { _, ch -> ch.url }) { index, ch ->
                            val isCurrent = ch.url == currentChapterUrl
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onChapterClick(ch) }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = ch.title,
                                        color = if (isCurrent) primaryColor else textColor,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        fontStyle = if (isCurrent) FontStyle.Italic else FontStyle.Normal,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    // Mockup số từ và thời gian cập nhật để y hệt APK gốc
                                    Text(
                                        text = "${1500 + (index * 7) % 800} từ  •  Cập nhật: 06/06/2026",
                                        fontSize = 11.sp,
                                        color = textColor.copy(alpha = 0.4f)
                                    )
                                }
                                
                                Icon(
                                    imageVector = if (ch.isDownloaded) Icons.Filled.CheckCircle else Icons.Filled.CloudDownload,
                                    contentDescription = if (ch.isDownloaded) "Đã tải" else "Chưa tải",
                                    tint = if (isCurrent) primaryColor else if (ch.isDownloaded) primaryColor.copy(alpha = 0.7f) else textColor.copy(alpha = 0.4f),
                                    modifier = Modifier
                                        .size(20.dp)
                                        .let { modifier ->
                                            if (isOffline || ch.isDownloaded) {
                                                modifier
                                            } else {
                                                modifier.clickable {
                                                    val intent = Intent(context, com.nam.novelreader.service.DownloadService::class.java).apply {
                                                        action = com.nam.novelreader.service.DownloadService.ACTION_DOWNLOAD
                                                        putExtra(com.nam.novelreader.service.DownloadService.EXTRA_EXTENSION_ID, extensionId)
                                                        putExtra(com.nam.novelreader.service.DownloadService.EXTRA_NOVEL_URL, novelUrl)
                                                        putExtra(com.nam.novelreader.service.DownloadService.EXTRA_NOVEL_TITLE, novel?.title ?: "")
                                                        putExtra(com.nam.novelreader.service.DownloadService.EXTRA_COVER_URL, novel?.cover ?: "")
                                                        putStringArrayListExtra(
                                                            com.nam.novelreader.service.DownloadService.EXTRA_CHAPTER_URLS,
                                                            arrayListOf(ch.url)
                                                        )
                                                    }
                                                    context.startService(intent)
                                                    android.widget.Toast.makeText(context, "Đang tải chương: ${ch.title}...", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                )
                            }
                            Divider(color = textColor.copy(alpha = 0.05f))
                        }
                    }
                } else {
                    // === Tab Đánh dấu rỗng ===
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.BookmarkBorder,
                                contentDescription = null,
                                tint = textColor.copy(alpha = 0.3f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Chưa có đánh dấu nào",
                                fontSize = 14.sp,
                                color = textColor.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }

            // === Nút FAB màu vàng trượt nhanh xuống đáy (Ảnh 4) ===
            if (selectedTab == 0 && filteredChapters.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 24.dp, bottom = 24.dp)
                        .size(48.dp)
                        .shadow(4.dp, CircleShape)
                        .background(primaryColor, CircleShape)
                        .clickable {
                            coroutineScope.launch {
                                // Cuộn nhanh xuống chương cuối
                                listState.animateScrollToItem(filteredChapters.size - 1)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowDownward,
                        contentDescription = "Cuộn xuống đáy",
                        tint = bgColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

