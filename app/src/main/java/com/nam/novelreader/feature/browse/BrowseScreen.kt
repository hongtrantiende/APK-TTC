package com.nam.novelreader.feature.browse

import androidx.compose.foundation.pager.HorizontalPager
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.clickable
import com.nam.novelreader.feature.components.bounceClick
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.input.ImeAction
import android.content.Context
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import java.io.File
import coil.request.ImageRequest
import coil.request.CachePolicy
import com.nam.novelreader.domain.model.HomeTab
import com.nam.novelreader.domain.model.Novel
import com.nam.novelreader.data.local.entity.ExtensionEntity
import com.nam.novelreader.data.local.entity.cleanName
import com.nam.novelreader.navigation.Routes



/**
 * BrowseScreen ? gi?ng VBook:
 * - Top bar: t?n extension + source URL + Search
 * - Filter chips (tab names t? home.js)
 * - Grid 3 c?t hi?n th? truy?n
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    navController: NavHostController,
    extensionId: String? = null,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val homeTabs by viewModel.homeTabs.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val genreTabs by viewModel.genreTabs.collectAsStateWithLifecycle()
    val novels by viewModel.novels.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isPagingLoading by viewModel.isPagingLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    val extensionName by viewModel.extensionName.collectAsStateWithLifecycle()
    val extensionSource by viewModel.extensionSource.collectAsStateWithLifecycle()
    val extensionIconPath by viewModel.extensionIconPath.collectAsStateWithLifecycle()
    val extensionLocale by viewModel.extensionLocale.collectAsStateWithLifecycle()

    val currentExtensionId by viewModel.currentExtensionId.collectAsStateWithLifecycle()
    val installedExtensions by viewModel.installedExtensions.collectAsStateWithLifecycle(initialValue = emptyList())

    val isFromTab = extensionId == null
    var showExtensionSheet by remember { mutableStateOf(false) }
    val drawerScope = rememberCoroutineScope()

    val themeBgColor = com.nam.novelreader.feature.components.VBookTheme.backgroundColor()
    val themeTextColor = com.nam.novelreader.feature.components.VBookTheme.textColor()
    val themePrimaryColor = com.nam.novelreader.feature.components.VBookTheme.primaryColor()
    val themeAccentColor = com.nam.novelreader.feature.components.VBookTheme.accentColor()
    val themeCardColor = com.nam.novelreader.feature.components.VBookTheme.cardColor()
    val themeSubTextColor = com.nam.novelreader.feature.components.VBookTheme.subTextColor()
    val themeSwitchThumbChecked = com.nam.novelreader.feature.components.VBookTheme.switchThumbCheckedColor()
    val themeSwitchTrackChecked = com.nam.novelreader.feature.components.VBookTheme.switchTrackCheckedColor()
    val themeSwitchThumbUnchecked = com.nam.novelreader.feature.components.VBookTheme.switchThumbUncheckedColor()
    val themeSwitchTrackUnchecked = com.nam.novelreader.feature.components.VBookTheme.switchTrackUncheckedColor()

    LaunchedEffect(extensionId) {
        if (!extensionId.isNullOrBlank()) {
            viewModel.selectExtension(extensionId)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = com.nam.novelreader.feature.components.VBookTheme.backgroundColor(),
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(com.nam.novelreader.feature.components.VBookTheme.backgroundColor())
                        .statusBarsPadding()
                        .padding(top = 8.dp, bottom = 4.dp)
                ) {
                    // Top Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 0.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = extensionName.ifBlank { "Tàng Thư Viện [APP]" },
                                color = com.nam.novelreader.feature.components.VBookTheme.textColor(),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            if (extensionSource.isNotBlank()) {
                                Text(
                                    text = extensionSource.replace("https://", "").replace("http://", "").trimEnd('/'),
                                    color = com.nam.novelreader.feature.components.VBookTheme.subTextColor(),
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
                        }

                    // Actions
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // VBook style Translation button & Dialog
                        var showTranslationDialog by remember { mutableStateOf(false) }
                        val context = LocalContext.current
                        val themeState = com.nam.novelreader.feature.components.VBookTheme.themeState()
                        val prefs = remember { context.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE) }
                        val extId = currentExtensionId ?: ""
                        var translationMode by remember(extId) {
                            mutableStateOf(prefs.getString("ext_translation_mode_$extId", "Gốc") ?: "Gốc")
                        }

                        Box {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(com.nam.novelreader.feature.components.VBookTheme.cardColor())
                                    .clickable { showTranslationDialog = true }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val isSupportedBrowseIcon = !extensionIconPath.isNullOrBlank() &&
                                    java.io.File(extensionIconPath).extension.lowercase() in listOf("png", "jpg", "jpeg", "webp", "bmp")
                                if (isSupportedBrowseIcon) {
                                    AsyncImage(
                                        model = extensionIconPath,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .background(Color(0xFF64B5F6), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Language,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (translationMode == "Gốc") "Gốc" else "Dịch",
                                    color = com.nam.novelreader.feature.components.VBookTheme.textColor(),
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = com.nam.novelreader.feature.components.VBookTheme.textColor(),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        if (showTranslationDialog) {
                            var tempSource by remember(extId, showTranslationDialog) {
                                mutableStateOf(prefs.getString("ext_translate_source_$extId", "Tiếng Trung") ?: "Tiếng Trung")
                            }
                            var tempTarget by remember(extId, showTranslationDialog) {
                                mutableStateOf(prefs.getString("ext_translate_target_$extId", "Việt (VP)") ?: "Việt (VP)")
                            }
                            var tempEngine by remember(extId, showTranslationDialog) {
                                mutableStateOf(prefs.getString("ext_translate_engine_$extId", "QT") ?: "QT")
                            }
                            var tempScope by remember(extId, showTranslationDialog) {
                                mutableStateOf(prefs.getString("ext_translate_scope_$extId", "Tất cả") ?: "Tất cả")
                            }
                            var tempEnabled by remember(extId, showTranslationDialog) {
                                mutableStateOf(translationMode != "Gốc")
                            }

                            var sourceMenuExpanded by remember { mutableStateOf(false) }
                            var targetMenuExpanded by remember { mutableStateOf(false) }
                            var engineMenuExpanded by remember { mutableStateOf(false) }
                            var scopeMenuExpanded by remember { mutableStateOf(false) }

                            Dialog(onDismissRequest = { showTranslationDialog = false }) {
                                Box(
                                    modifier = Modifier
                                        .width(360.dp)
                                        .clip(RoundedCornerShape(28.dp))
                                        .background(com.nam.novelreader.feature.components.VBookTheme.cardColor())
                                        .padding(20.dp)
                                ) {
                                    Column {
                                        // Header Row
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(onClick = { showTranslationDialog = false }) {
                                                Icon(Icons.Filled.Close, contentDescription = "Close", tint = com.nam.novelreader.feature.components.VBookTheme.textColor())
                                            }
                                            Text(
                                                text = "Dịch",
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = com.nam.novelreader.feature.components.VBookTheme.textColor()
                                            )
                                            IconButton(onClick = {
                                                showTranslationDialog = false
                                                navController.navigate(Routes.TRANSLATION_SETTINGS)
                                            }) {
                                                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = com.nam.novelreader.feature.components.VBookTheme.textColor())
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))

                                        // Row 1: Source Language -> Switch -> Target Language
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Source Dropdown
                                            Box(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(com.nam.novelreader.feature.components.VBookTheme.backgroundColor())
                                                        .clickable { sourceMenuExpanded = true }
                                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                                    horizontalArrangement = Arrangement.Center,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(text = tempSource, color = com.nam.novelreader.feature.components.VBookTheme.textColor(), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Icon(Icons.Filled.KeyboardArrowDown, null, tint = com.nam.novelreader.feature.components.VBookTheme.textColor(), modifier = Modifier.size(16.dp))
                                                }
                                                DropdownMenu(
                                                    expanded = sourceMenuExpanded,
                                                    onDismissRequest = { sourceMenuExpanded = false }
                                                ) {
                                                    listOf("Tiếng Trung", "Tự động nhận diện", "Tiếng Anh", "Tiếng Nhật", "Tiếng Hàn").forEach { lang ->
                                                        DropdownMenuItem(
                                                            text = { Text(lang) },
                                                            onClick = {
                                                                tempSource = lang
                                                                sourceMenuExpanded = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(8.dp))

                                            // Toggle Switch
                                            Switch(
                                                checked = tempEnabled,
                                                onCheckedChange = { tempEnabled = it },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = themeSwitchThumbChecked,
                                                    checkedTrackColor = themeSwitchTrackChecked,
                                                    uncheckedThumbColor = themeSwitchThumbUnchecked,
                                                    uncheckedTrackColor = themeSwitchTrackUnchecked
                                                )
                                            )

                                            Spacer(modifier = Modifier.width(8.dp))

                                            // Target Dropdown
                                            Box(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(com.nam.novelreader.feature.components.VBookTheme.backgroundColor())
                                                        .clickable { targetMenuExpanded = true }
                                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                                    horizontalArrangement = Arrangement.Center,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(text = tempTarget, color = com.nam.novelreader.feature.components.VBookTheme.textColor(), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Icon(Icons.Filled.KeyboardArrowDown, null, tint = com.nam.novelreader.feature.components.VBookTheme.textColor(), modifier = Modifier.size(16.dp))
                                                }
                                                DropdownMenu(
                                                    expanded = targetMenuExpanded,
                                                    onDismissRequest = { targetMenuExpanded = false }
                                                ) {
                                                    listOf("Việt (VP)", "Hán Việt", "Việt (Dịch)").forEach { lang ->
                                                        DropdownMenuItem(
                                                            text = { Text(lang) },
                                                            onClick = {
                                                                tempTarget = lang
                                                                targetMenuExpanded = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(20.dp))

                                        // Row 2: Engine Selector -> Scope Selector + Save Button
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Engine Selector
                                            Box(modifier = Modifier.weight(1.1f)) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(com.nam.novelreader.feature.components.VBookTheme.backgroundColor())
                                                        .clickable { engineMenuExpanded = true }
                                                        .padding(horizontal = 10.dp, vertical = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(20.dp)
                                                            .background(com.nam.novelreader.feature.components.VBookTheme.primaryColor(), CircleShape),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Filled.MenuBook,
                                                            contentDescription = null,
                                                            tint = com.nam.novelreader.feature.components.VBookTheme.backgroundColor(),
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(text = tempEngine, color = com.nam.novelreader.feature.components.VBookTheme.textColor(), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                                    Icon(Icons.Filled.KeyboardArrowDown, null, tint = com.nam.novelreader.feature.components.VBookTheme.textColor(), modifier = Modifier.size(16.dp))
                                                }
                                                DropdownMenu(
                                                    expanded = engineMenuExpanded,
                                                    onDismissRequest = { engineMenuExpanded = false }
                                                ) {
                                                    listOf("QT", "Google", "Bắc Cực Tinh", "GGChan").forEach { eng ->
                                                        DropdownMenuItem(
                                                            text = { Text(eng) },
                                                            onClick = {
                                                                tempEngine = eng
                                                                engineMenuExpanded = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(10.dp))

                                            // Scope & Save
                                            Row(
                                                modifier = Modifier.weight(1.3f),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Scope Dropdown
                                                        Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(RoundedCornerShape(12.dp))
                                                            .background(com.nam.novelreader.feature.components.VBookTheme.backgroundColor())
                                                            .clickable { scopeMenuExpanded = true }
                                                            .padding(horizontal = 8.dp, vertical = 10.dp),
                                                        horizontalArrangement = Arrangement.Center,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(text = tempScope, color = com.nam.novelreader.feature.components.VBookTheme.textColor(), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        Spacer(modifier = Modifier.width(2.dp))
                                                        Icon(Icons.Filled.KeyboardArrowDown, null, tint = com.nam.novelreader.feature.components.VBookTheme.textColor(), modifier = Modifier.size(14.dp))
                                                    }
                                                    DropdownMenu(
                                                        expanded = scopeMenuExpanded,
                                                        onDismissRequest = { scopeMenuExpanded = false }
                                                    ) {
                                                        listOf("Tất cả", "Tên truyện", "Nội dung").forEach { scp ->
                                                            DropdownMenuItem(
                                                                text = { Text(scp) },
                                                                onClick = {
                                                                    tempScope = scp
                                                                    scopeMenuExpanded = false
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
 
                                                Spacer(modifier = Modifier.width(8.dp))
 
                                                // Save button
                                                Button(
                                                    onClick = {
                                                        val finalMode = if (tempEnabled) tempTarget else "Gốc"
                                                        prefs.edit().apply {
                                                            putString("ext_translation_mode_$extId", finalMode)
                                                            putString("ext_translate_source_$extId", tempSource)
                                                            putString("ext_translate_target_$extId", tempTarget)
                                                            putString("ext_translate_engine_$extId", tempEngine)
                                                            putString("ext_translate_scope_$extId", tempScope)
                                                        }.apply()
                                                        translationMode = finalMode
                                                        showTranslationDialog = false
                                                        viewModel.loadHome(extId)
                                                    },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = com.nam.novelreader.feature.components.VBookTheme.primaryColor(),
                                                        contentColor = com.nam.novelreader.feature.components.VBookTheme.backgroundColor()
                                                    ),
                                                    shape = RoundedCornerShape(12.dp),
                                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                                                ) {
                                                    Text("Luu", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                                }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Search
                        IconButton(
                            onClick = { navController.navigate(Routes.search(currentExtensionId ?: "")) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "Search",
                                tint = com.nam.novelreader.feature.components.VBookTheme.textColor(),
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Extension Icon (opens floating card) ? Gi?ng quy?n s?ch ? APK g?c
                        val toolbarIconFile = remember(extensionIconPath) { extensionIconPath?.let { java.io.File(it) } }
                        IconButton(
                            onClick = { showExtensionSheet = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            if (toolbarIconFile != null && toolbarIconFile.exists()) {
                                AsyncImage(
                                    model = toolbarIconFile,
                                    contentDescription = "Extensions",
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.MenuBook,
                                    contentDescription = "Extensions",
                                    tint = com.nam.novelreader.feature.components.VBookTheme.primaryColor(),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (currentExtensionId == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Chưa có tiện ích mở rộng nào hoạt động",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    FilledTonalButton(onClick = { navController.navigate(Routes.EXTENSION_STORE) }) {
                        Text("Kho tiện ích")
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                val pagerState = rememberPagerState(
                    initialPage = 0,
                    pageCount = { homeTabs.size }
                )
                val coroutineScope = rememberCoroutineScope()
                var categoriesExpanded by remember { mutableStateOf(false) }

                // ??ng b? t? pagerState.currentPage sang ViewModel selectTab
                LaunchedEffect(pagerState.currentPage, homeTabs) {
                    if (homeTabs.isNotEmpty() && pagerState.currentPage in homeTabs.indices) {
                        viewModel.selectTab(homeTabs[pagerState.currentPage])
                    }
                }

                // 1. Lu?n hi?n th? Tab Chips n?u danh s?ch homeTabs kh?ng r?ng
                if (homeTabs.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().background(com.nam.novelreader.feature.components.VBookTheme.backgroundColor())
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (categoriesExpanded) themePrimaryColor else Color.Transparent)
                                    .border(1.dp, if (categoriesExpanded) Color.Transparent else themePrimaryColor.copy(alpha = 0.3f), CircleShape)
                                    .clickable { categoriesExpanded = !categoriesExpanded },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Category,
                                    contentDescription = "Categories",
                                    tint = if (categoriesExpanded) themeBgColor else themeTextColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Row(
                                modifier = Modifier
                                    .horizontalScroll(rememberScrollState())
                                    .weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                homeTabs.forEachIndexed { index, tab ->
                                    CustomCategoryChip(
                                        name = tab.name,
                                        selected = pagerState.currentPage == index,
                                        onClick = { 
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(index)
                                            }
                                            categoriesExpanded = false 
                                        }
                                    )
                                }
                            }
                        }
                        
                        AnimatedVisibility(visible = categoriesExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val chunkedTabs = if (genreTabs.isNotEmpty()) genreTabs.chunked(4) else homeTabs.chunked(4)
                                chunkedTabs.forEach { rowTabs ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        rowTabs.forEach { tab ->
                                            val index = homeTabs.indexOf(tab)
                                            CustomCategoryChip(
                                                name = tab.name,
                                                selected = pagerState.currentPage == index,
                                                isGridItem = true,
                                                modifier = Modifier.weight(1f),
                                                onClick = { 
                                                    if (index >= 0) {
                                                        coroutineScope.launch {
                                                            pagerState.animateScrollToPage(index)
                                                        }
                                                    }
                                                    categoriesExpanded = false 
                                                }
                                            )
                                        }
                                        repeat(4 - rowTabs.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }

                // 2. Ph?n n?i dung c?n l?i
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = { viewModel.refresh() },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (homeTabs.isEmpty()) {
                            if (isLoading) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            } else if (error != null) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp)
                                ) {
                                    Text("❌ Lỗi", style = MaterialTheme.typography.titleMedium)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(error ?: "", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    FilledTonalButton(onClick = { currentExtensionId?.let { viewModel.loadHome(it) } }) {
                                        Text("Thử lại")
                                    }
                                }
                            }
                        } else {
                            val tabNovelsMap by viewModel.tabNovels.collectAsStateWithLifecycle()

                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                                beyondViewportPageCount = 3
                            ) { page ->
                                val tab = homeTabs.getOrNull(page)
                                if (tab != null) {
                                    val novelsForTab = tabNovelsMap[tab.name] ?: emptyList()
                                    val gridState = rememberLazyGridState()

                                    val shouldLoadMore = remember {
                                        derivedStateOf {
                                            val layoutInfo = gridState.layoutInfo
                                            val totalItemsCount = layoutInfo.totalItemsCount
                                            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                                            totalItemsCount > 0 && lastVisibleItemIndex >= totalItemsCount - 6
                                        }
                                    }

                                    LaunchedEffect(shouldLoadMore.value) {
                                        if (shouldLoadMore.value && pagerState.currentPage == page && !isLoading && !isRefreshing) {
                                            viewModel.loadNextPage()
                                        }
                                    }

                                    when {
                                        isLoading && novelsForTab.isEmpty() -> {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                CircularProgressIndicator()
                                            }
                                        }
                                        error != null && novelsForTab.isEmpty() -> {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center,
                                                modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp)
                                            ) {
                                                Text("❌ Lỗi", style = MaterialTheme.typography.titleMedium)
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(error ?: "", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                                                Spacer(modifier = Modifier.height(16.dp))
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    FilledTonalButton(onClick = { currentExtensionId?.let { viewModel.loadHome(it) } }) {
                                                        Text("Thử lại")
                                                    }
                                                    OutlinedButton(onClick = { navController.navigate(Routes.webview(extensionSource, currentExtensionId)) }) {
                                                        Text("Mở Web")
                                                    }
                                                }
                                            }
                                        }
                                        novelsForTab.isEmpty() -> {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Text("Không có dữ liệu hoặc đang tải...", style = MaterialTheme.typography.bodyMedium, color = com.nam.novelreader.feature.components.VBookTheme.subTextColor())
                                            }
                                        }
                                        else -> {
                                            val uniqueNovels = remember(novelsForTab) { novelsForTab.distinctBy { it.url } }
                                            val themeState = com.nam.novelreader.feature.components.VBookTheme.themeState()
                                            LazyVerticalGrid(
                                                state = gridState,
                                                columns = GridCells.Fixed(themeState.browseGridColumns),
                                                modifier = Modifier.fillMaxSize(),
                                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                items(
                                                    items = uniqueNovels,
                                                    key = { it.url }
                                                ) { novel ->
                                                    NovelGridItem(
                                                        novel = novel,
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        navController.navigate(Routes.detail(currentExtensionId ?: "", novel.url))
                                                    }
                                                }

                                                if (isPagingLoading && pagerState.currentPage == page) {
                                                    item(span = { GridItemSpan(themeState.browseGridColumns) }) {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(16.dp),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Dim background che ph? b?t click d? d?ng Drawer
        // Extension Popup Card
        AnimatedVisibility(
            visible = showExtensionSheet,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showExtensionSheet = false },
                contentAlignment = Alignment.TopEnd
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = padding.calculateTopPadding() + 8.dp, end = 12.dp, start = 12.dp)
                        .widthIn(max = 340.dp)
                        .fillMaxWidth(0.9f)
                        .fillMaxHeight(0.75f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(com.nam.novelreader.feature.components.VBookTheme.cardColor())
                        .clickable( // Prevent clicks from propagating to the background
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {}
                ) {
                    ExtensionDetailSheetContent(
                        name = extensionName.ifBlank { (currentExtensionId ?: "").replace("-", " ").replaceFirstChar { it.uppercase() } },
                        source = extensionSource,
                        iconPath = extensionIconPath,
                        locale = extensionLocale,
                        installedExtensions = installedExtensions,
                        activeExtensionId = currentExtensionId ?: "",
                        onSelectExtension = { id ->
                            viewModel.selectExtension(id)
                            showExtensionSheet = false
                        },
                        onConfigExtension = { id ->
                            showExtensionSheet = false
                            navController.navigate(Routes.extensionSettings(id))
                        },
                        onOpenSourceWebView = { url ->
                            showExtensionSheet = false
                            navController.navigate(Routes.webview(url, currentExtensionId))
                        },
                        onDismiss = { showExtensionSheet = false }
                    )
                }
            }
        }
}
}
}

@Composable
fun CustomCategoryChip(
    name: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    isGridItem: Boolean = false,
    onClick: () -> Unit
) {
    val accentColor = com.nam.novelreader.feature.components.VBookTheme.accentColor()
    val cardColor = com.nam.novelreader.feature.components.VBookTheme.cardColor()
    val textColor = com.nam.novelreader.feature.components.VBookTheme.textColor()

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(
                if (selected) accentColor.copy(alpha = 0.2f) else cardColor.copy(alpha = 0.4f)
            )
            .border(
                width = 1.dp,
                color = if (selected) accentColor else textColor.copy(alpha = 0.2f),
                shape = CircleShape
            )
            .bounceClick(onClick = onClick)
            .padding(horizontal = if (isGridItem) 10.dp else 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) accentColor else textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun NovelGridItem(
    novel: Novel,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val subTextColor = com.nam.novelreader.feature.components.VBookTheme.subTextColor()
    val themeState = com.nam.novelreader.feature.components.VBookTheme.themeState()

    Column(
        modifier = modifier
            .bounceClick(onClick = onClick)
            .padding(bottom = 2.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(themeState.browseCoverCornerRadius.dp))
        ) {
            AsyncImage(
                model = com.nam.novelreader.feature.components.buildNovelImageRequest(novel),
                contentDescription = novel.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = novel.title,
            fontSize = themeState.browseTitleFontSize.sp,
            lineHeight = (themeState.browseTitleFontSize + 2f).sp,
            fontWeight = FontWeight.Bold,
            maxLines = themeState.browseTitleMaxLines,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (themeState.browseTitleAlign == "center") TextAlign.Center else TextAlign.Start,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            color = com.nam.novelreader.feature.components.VBookTheme.textColor()
        )
        if (!novel.author.isNullOrBlank()) {
            val authorText = novel.author + if (!novel.status.isNullOrBlank()) " · ${novel.status}" else ""
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = authorText,
                fontSize = (themeState.browseTitleFontSize - 2.5f).coerceAtLeast(8f).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (themeState.browseTitleAlign == "center") TextAlign.Center else TextAlign.Start,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                color = subTextColor
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionDetailSheetContent(
    name: String,
    source: String,
    iconPath: String?,
    locale: String,
    installedExtensions: List<ExtensionEntity>,
    activeExtensionId: String,
    onSelectExtension: (String) -> Unit,
    onConfigExtension: (String) -> Unit,
    onOpenSourceWebView: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE) }
    var pinnedExts by remember {
        mutableStateOf(prefs.getStringSet("pinned_extensions", emptySet()) ?: emptySet())
    }

    var searchQuery by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    val filteredExtensions = remember(searchQuery, installedExtensions) {
        if (searchQuery.isBlank()) {
            installedExtensions
        } else {
            installedExtensions.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.source.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val sortedExtensions = remember(filteredExtensions, pinnedExts) {
        filteredExtensions.sortedWith(compareByDescending<ExtensionEntity> { pinnedExts.contains(it.id) }.thenBy { it.name })
    }

    val themeBg = com.nam.novelreader.feature.components.VBookTheme.backgroundColor()
    val themeText = com.nam.novelreader.feature.components.VBookTheme.textColor()
    val themeSubText = com.nam.novelreader.feature.components.VBookTheme.subTextColor()
    val themePrimary = com.nam.novelreader.feature.components.VBookTheme.primaryColor()
    val themeCard = com.nam.novelreader.feature.components.VBookTheme.cardColor()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(themeCard)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // List Section
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(
                items = sortedExtensions,
                key = { it.id }
            ) { ext ->
                val isActive = ext.id == activeExtensionId
                val isExtPinned = pinnedExts.contains(ext.id)
                val extLocale = ext.locale ?: ""
                val flag = when (extLocale.lowercase(java.util.Locale.ROOT)) {
                    "vi_vn", "vi" -> "🇻🇳"
                    "zh_cn", "zh", "cn", "chi", "zho" -> "🇨🇳"
                    "en_us", "en" -> "🇺🇸"
                    else -> "🌐"
                }
                val langLabel = when (extLocale.lowercase(java.util.Locale.ROOT)) {
                    "vi_vn", "vi" -> "VI"
                    "zh_cn", "zh", "cn", "chi", "zho" -> "ZH"
                    "en_us", "en" -> "EN"
                    "ja", "ja_jp" -> "JA"
                    "ko", "ko_kr" -> "KO"
                    else -> extLocale.take(2).uppercase().ifBlank { "??" }
                }
                val langBadgeColor = when (extLocale.lowercase(java.util.Locale.ROOT)) {
                    "vi_vn", "vi" -> Color(0xFF4CAF50)
                    "zh_cn", "zh", "cn", "chi", "zho" -> Color(0xFFE53935)
                    "en_us", "en" -> Color(0xFF1565C0)
                    "ja", "ja_jp" -> Color(0xFFE91E63)
                    "ko", "ko_kr" -> Color(0xFF9C27B0)
                    else -> Color(0xFF607D8B)
                }
                val cleanDomain = ext.source.replace("https://", "").replace("http://", "").trimEnd('/')

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isActive) themePrimary.copy(alpha = 0.1f) else themeCard.copy(alpha = 0.5f))
                        .bounceClick { onSelectExtension(ext.id) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val iconFile = remember(ext.iconPath, ext.localPath) {
                        val path = ext.iconPath ?: "${ext.localPath}/icon.png"
                        File(path)
                    }
                    val isSupportedIcon = remember(iconFile) {
                        iconFile.exists() && iconFile.extension.lowercase() in listOf("png", "jpg", "jpeg", "webp", "bmp")
                    }
                    if (isSupportedIcon) {
                        AsyncImage(
                            model = iconFile,
                            contentDescription = ext.cleanName,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(themePrimary, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MenuBook,
                                contentDescription = null,
                                tint = themeBg,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = ext.cleanName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            color = themeText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Badge ngôn ngữ: VI / ZH / EN ...
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = langBadgeColor.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = langLabel,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = langBadgeColor,
                                    letterSpacing = 0.5.sp
                                )
                            }
                            Text(
                                text = flag,
                                style = MaterialTheme.typography.bodySmall,
                                color = themeSubText
                            )
                            Text(
                                text = cleanDomain,
                                style = MaterialTheme.typography.bodySmall,
                                color = themeSubText.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { onConfigExtension(ext.id) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Cấu hình",
                                tint = if (isActive) themePrimary else themeSubText,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                val updated = pinnedExts.toMutableSet()
                                if (isExtPinned) {
                                    updated.remove(ext.id)
                                } else {
                                    updated.add(ext.id)
                                }
                                pinnedExts = updated
                                prefs.edit().putStringSet("pinned_extensions", updated).apply()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PushPin,
                                contentDescription = "Ghim",
                                tint = if (isExtPinned) themePrimary else themeSubText.copy(alpha = 0.4f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    text = "Tìm kiếm phần mở rộng",
                    style = MaterialTheme.typography.bodyMedium,
                    color = themeSubText.copy(alpha = 0.6f)
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = themeSubText.copy(alpha = 0.7f)
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            shape = RoundedCornerShape(24.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = themeBg.copy(alpha = 0.6f),
                unfocusedContainerColor = themeBg.copy(alpha = 0.6f)
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() })
        )
    }
}