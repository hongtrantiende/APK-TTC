package com.nam.novelreader.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.nam.novelreader.domain.model.Novel
import com.nam.novelreader.navigation.Routes
import com.nam.novelreader.feature.components.VBookTheme
import androidx.compose.ui.graphics.Color
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LibraryDatabaseEntryPoint {
    fun appPreferences(): com.nam.novelreader.data.preferences.AppPreferences
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    navController: NavHostController,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val novels by viewModel.libraryNovels.collectAsStateWithLifecycle(initialValue = emptyList())
    val downloadTasks by viewModel.downloadTasks.collectAsStateWithLifecycle(initialValue = emptyList())
    val downloadedNovelUrls = remember(downloadTasks) {
        downloadTasks.filter { it.status == "completed" }.map { it.novelUrl }.toSet()
    }
    var selectedNovelForAction by remember { mutableStateOf<Novel?>(null) }
    val context = LocalContext.current
    
    val entryPoint = remember {
        dagger.hilt.EntryPoints.get(context.applicationContext, LibraryDatabaseEntryPoint::class.java)
    }
    val appPrefs = entryPoint.appPreferences()
    var isVip by remember { mutableStateOf(appPrefs.isVip()) }
    
    DisposableEffect(appPrefs.prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "vip_expiry_timestamp") {
                isVip = appPrefs.isVip()
            }
        }
        appPrefs.prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            appPrefs.prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    Scaffold(
        containerColor = VBookTheme.backgroundColor(),
        topBar = {
            TopAppBar(
                title = { Text("Thư viện", fontWeight = FontWeight.Bold, color = VBookTheme.textColor()) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VBookTheme.backgroundColor(),
                    titleContentColor = VBookTheme.textColor()
                )
            )
        }
    ) { padding ->
        if (novels.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "📚",
                        style = MaterialTheme.typography.displayLarge,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "📚",
                        style = MaterialTheme.typography.displayLarge,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Thư viện trống",
                        style = MaterialTheme.typography.titleMedium,
                        color = VBookTheme.subTextColor(),
                    )
                    Text(
                        "Khám phá và thêm truyện vào thư viện",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VBookTheme.subTextColor().copy(alpha = 0.6f),
                    )
                }
            }
        } else {
            val themeState = com.nam.novelreader.feature.components.VBookTheme.themeState()
            LazyVerticalGrid(
                columns = GridCells.Fixed(themeState.browseGridColumns),
                contentPadding = PaddingValues(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = novels,
                    key = { it.url }
                ) { novel ->
                    NovelGridItem(
                        novel = novel,
                        isDownloaded = downloadedNovelUrls.contains(novel.url),
                        onClick = {
                            navController.navigate(Routes.detail(novel.extensionId, novel.url))
                        },
                        onLongClick = {
                            selectedNovelForAction = novel
                        }
                    )
                }
            }
        }
        
        if (selectedNovelForAction != null) {
            val novel = selectedNovelForAction!!
            AlertDialog(
                onDismissRequest = { selectedNovelForAction = null },
                containerColor = VBookTheme.cardColor(),
                title = {
                    Text(
                        text = novel.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = VBookTheme.textColor()
                    )
                },
                text = {
                    Column {
                        HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp), color = VBookTheme.primaryColor().copy(alpha = 0.3f))
                        
                        ListItem(
                            headlineContent = { Text("Xóa khỏi thư viện", color = MaterialTheme.colorScheme.error) },
                            leadingContent = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                viewModel.removeFromLibrary(novel)
                                selectedNovelForAction = null
                            }
                        )
                        if (isVip) {
                            val isDownloaded = downloadedNovelUrls.contains(novel.url)
                            ListItem(
                                headlineContent = { 
                                    Text(
                                        "Tải về máy (TXT)", 
                                        color = if (isDownloaded) VBookTheme.textColor() else VBookTheme.subTextColor().copy(alpha = 0.5f)
                                    ) 
                                },
                                leadingContent = { 
                                    Icon(
                                        Icons.Filled.Download, 
                                        contentDescription = null, 
                                        tint = if (isDownloaded) VBookTheme.textColor() else VBookTheme.subTextColor().copy(alpha = 0.5f)
                                    ) 
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable {
                                    if (isDownloaded) {
                                        viewModel.exportNovelAsTxt(context, novel)
                                        selectedNovelForAction = null
                                    } else {
                                        android.widget.Toast.makeText(context, "Chỉ truyện đã tải offline xong mới có thể xuất file!", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            ListItem(
                                headlineContent = { 
                                    Text(
                                        "Tải về máy (EPUB)", 
                                        color = if (isDownloaded) VBookTheme.textColor() else VBookTheme.subTextColor().copy(alpha = 0.5f)
                                    ) 
                                },
                                leadingContent = { 
                                    Icon(
                                        Icons.Filled.Download, 
                                        contentDescription = null, 
                                        tint = if (isDownloaded) VBookTheme.textColor() else VBookTheme.subTextColor().copy(alpha = 0.5f)
                                    ) 
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable {
                                    if (isDownloaded) {
                                        viewModel.exportNovelAsEpub(context, novel)
                                        selectedNovelForAction = null
                                    } else {
                                        android.widget.Toast.makeText(context, "Chỉ truyện đã tải offline xong mới có thể xuất file!", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            ListItem(
                                headlineContent = { 
                                    Text(
                                        "Lưu bản sao lưu (JSON)", 
                                        color = if (isDownloaded) VBookTheme.textColor() else VBookTheme.subTextColor().copy(alpha = 0.5f)
                                    ) 
                                },
                                leadingContent = { 
                                    Icon(
                                        Icons.Filled.Save, 
                                        contentDescription = null, 
                                        tint = if (isDownloaded) VBookTheme.textColor() else VBookTheme.subTextColor().copy(alpha = 0.5f)
                                    ) 
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable {
                                    if (isDownloaded) {
                                        viewModel.backupNovel(context, novel)
                                        selectedNovelForAction = null
                                    } else {
                                        android.widget.Toast.makeText(context, "Chỉ truyện đã tải offline xong mới có thể xuất file!", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { selectedNovelForAction = null }) {
                        Text("Đóng", color = VBookTheme.primaryColor())
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NovelGridItem(
    novel: Novel, 
    isDownloaded: Boolean = false,
    onClick: () -> Unit, 
    onLongClick: () -> Unit
) {
    val themeState = com.nam.novelreader.feature.components.VBookTheme.themeState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
    ) {
        // Cover image
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(themeState.browseCoverCornerRadius.dp))
                .border(
                    width = 1.dp,
                    color = VBookTheme.subTextColor().copy(alpha = 0.2f),
                    shape = RoundedCornerShape(themeState.browseCoverCornerRadius.dp)
                ),
            shape = RoundedCornerShape(themeState.browseCoverCornerRadius.dp),
        ) {
            Box {
                AsyncImage(
                    model = novel.cover,
                    contentDescription = novel.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                
                // More options button
                IconButton(
                    onClick = onLongClick,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "Options",
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f), shape = androidx.compose.foundation.shape.CircleShape).padding(4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Title
        Text(
            text = novel.title,
            fontSize = themeState.browseTitleFontSize.sp,
            lineHeight = (themeState.browseTitleFontSize + 2f).sp,
            color = VBookTheme.textColor(),
            maxLines = themeState.browseTitleMaxLines,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (themeState.browseTitleAlign == "center") androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
            fontWeight = FontWeight.Bold
        )

        if (isDownloaded) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Đã tải",
                style = MaterialTheme.typography.labelSmall,
                color = VBookTheme.primaryColor(),
                fontWeight = FontWeight.Bold
            )
        }
    }
}
