package com.nam.novelreader.feature.extensions

import androidx.compose.foundation.clickable
import com.nam.novelreader.feature.components.bounceClick
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import coil.compose.AsyncImage
import com.nam.novelreader.data.local.entity.ExtensionEntity
import com.nam.novelreader.data.local.entity.cleanName
import com.nam.novelreader.extension.model.cleanName
import com.nam.novelreader.data.local.entity.RepositoryEntity
import com.nam.novelreader.navigation.Routes
import android.content.Context
import java.io.File
import android.content.Intent
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import java.util.Locale

/**
 * Tab Khám phá — danh sách extensions đã cài.
 * Click vào extension → mở BrowseScreen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionListScreen(
    navController: NavHostController,
    viewModel: ExtensionViewModel = hiltViewModel(),
) {
    val installedExtensions by viewModel.installedExtensions.collectAsStateWithLifecycle(initialValue = emptyList())
    var selectedFilter by remember { mutableStateOf("Tất cả") }
    val filters = listOf("Tất cả", "Truyện chữ", "Truyện chữ Trung", "Truyện tranh", "Phim")
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val isResumed = navBackStackEntry?.lifecycle?.currentState?.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) == true

    val filteredExtensions = remember(installedExtensions, selectedFilter) {
        if (selectedFilter == "Tất cả") {
            installedExtensions
        } else {
            installedExtensions.filter { ext ->
                val type = ext.type.lowercase(Locale.ROOT)
                val locale = ext.locale.lowercase(Locale.ROOT)
                val isChinese = type.contains("chinese") || locale.startsWith("zh") || locale.startsWith("cn") || locale == "chi" || locale == "zho"
                val isNovel = type.contains("novel") || type.contains("text")
                val isComic = type.contains("comic") || type.contains("manga") || type.contains("image")
                val isVideo = type.contains("video") || type.contains("movie") || type.contains("anime")
                when (selectedFilter) {
                    "Truyện chữ" -> isNovel && !isChinese
                    "Truyện chữ Trung" -> isNovel && isChinese
                    "Truyện tranh" -> isComic
                    "Phim" -> isVideo
                    else -> true
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Khám phá", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = { navController.navigate(Routes.EXTENSION_STORE) }) {
                        Icon(Icons.Filled.Extension, contentDescription = "Quản lý Extension")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = com.nam.novelreader.feature.components.VBookTheme.backgroundColor(),
                    titleContentColor = com.nam.novelreader.feature.components.VBookTheme.textColor(),
                    actionIconContentColor = com.nam.novelreader.feature.components.VBookTheme.textColor()
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Hàng Filter Chips ngang giống VBook Explore
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filters.forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = com.nam.novelreader.feature.components.VBookTheme.primaryColor().copy(alpha = 0.2f),
                            selectedLabelColor = com.nam.novelreader.feature.components.VBookTheme.primaryColor()
                        )
                    )
                }
            }

            if (filteredExtensions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🔌", style = MaterialTheme.typography.displayLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (installedExtensions.isEmpty()) "Chưa có tiện ích mở rộng nào" else "Không có tiện ích thuộc loại này",
                            style = MaterialTheme.typography.titleMedium,
                            color = com.nam.novelreader.feature.components.VBookTheme.subTextColor(),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (installedExtensions.isEmpty()) "Thêm kho lưu trữ để cài đặt và cập nhật nội dung bạn quan tâm nhé!" else "Hãy chọn bộ lọc khác hoặc cài đặt thêm tiện ích mới.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = com.nam.novelreader.feature.components.VBookTheme.subTextColor(),
                            modifier = Modifier.padding(horizontal = 32.dp),
                            textAlign = TextAlign.Center
                        )
                        if (installedExtensions.isEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedButton(onClick = { navController.navigate(Routes.EXTENSION_STORE) }) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Quản lý Extension")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(
                        items = filteredExtensions,
                        key = { it.id }
                    ) { ext ->
                        InstalledExtensionItem(ext, isResumed) {
                            navController.navigate(Routes.browse(ext.id))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InstalledExtensionItem(ext: ExtensionEntity, isResumed: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE) }
    val isError = remember(ext.id, isResumed) { prefs.getBoolean("ext_error_${ext.id}", false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        } else {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(10.dp),
                color = com.nam.novelreader.feature.components.VBookTheme.primaryColor().copy(alpha = 0.15f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Extension,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = com.nam.novelreader.feature.components.VBookTheme.primaryColor(),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = ext.cleanName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = com.nam.novelreader.feature.components.VBookTheme.textColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isError) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("⚠️ Lỗi", color = Color(0xFFE57373), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExtensionBadges(type = ext.type, locale = ext.locale)
                Text(
                    text = ext.source.replace("https://", "").replace("http://", "").trimEnd('/'),
                    style = MaterialTheme.typography.bodySmall,
                    color = com.nam.novelreader.feature.components.VBookTheme.subTextColor().copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Extension Store — 2 tabs: "Phần mở rộng" (Extensions) + "Kho lưu trữ" (Repositories).
 * Giống hệt flow của VBook.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionStoreScreen(
    navController: NavHostController,
    viewModel: ExtensionViewModel = hiltViewModel(),
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Phần mở rộng", "Kho lưu trữ")
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE) }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val isResumed = navBackStackEntry?.lifecycle?.currentState?.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) == true
    var showAiGeneratorDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Phần mở rộng") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAiGeneratorDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = "Tạo bằng AI"
                        )
                    }
                    // Check if there are any marked errors
                    val hasErrors = remember(isResumed) {
                        prefs.all.any { it.key.startsWith("ext_error_") && it.value == true }
                    }
                    if (hasErrors) {
                        IconButton(onClick = {
                            val errorSlugs = prefs.all.filter { it.key.startsWith("ext_error_") && it.value == true }
                                .map { it.key.substringAfter("ext_error_") }
                            if (errorSlugs.isNotEmpty()) {
                                val jsonContent = "[" + errorSlugs.joinToString(",") { "\"$it\"" } + "]"
                                
                                // Copy to clipboard
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Error Extensions", jsonContent)
                                clipboard.setPrimaryClip(clip)
                                
                                // Share Intent
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Danh sách tiện ích lỗi")
                                    putExtra(Intent.EXTRA_TEXT, jsonContent)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Xuất danh sách lỗi"))
                                Toast.makeText(context, "Đã sao chép danh sách lỗi vào bộ nhớ tạm!", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = "Xuất file lỗi",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Tab bar
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) },
                    )
                }
            }

            when (selectedTab) {
                0 -> ExtensionsTab(viewModel = viewModel, navController = navController)
                1 -> RepositoriesTab(viewModel = viewModel)
            }
        }
    }

    if (showAiGeneratorDialog) {
        com.nam.novelreader.feature.extension.AiExtensionGeneratorDialog(
            onDismiss = { showAiGeneratorDialog = false },
            onExtensionCreated = { viewModel.fetchAllExtensions() }
        )
    }
}

@Composable
fun ExtensionsTab(viewModel: ExtensionViewModel, navController: NavHostController) {
    val available by viewModel.availableExtensions.collectAsStateWithLifecycle()
    val installed by viewModel.installedExtensions.collectAsStateWithLifecycle(initialValue = emptyList())
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val installingIds by viewModel.installingIds.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val publicExtensions by viewModel.publicExtensionsState.collectAsStateWithLifecycle()

    val installedIds = installed.map { it.name }.toSet()

    var searchQuery by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    
    var selectedFilter by remember { mutableStateOf("Tất cả") }
    val filters = listOf("Tất cả", "Truyện chữ", "Truyện chữ Trung", "Truyện tranh", "Phim")
    
    var displayedCount by remember(selectedFilter, searchQuery) { mutableIntStateOf(50) }
    
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE) }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val isResumed = navBackStackEntry?.lifecycle?.currentState?.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) == true

    val isAdmin = remember(viewModel.appPrefs.supabaseIsLoggedIn, viewModel.appPrefs.supabaseUserEmail) {
        viewModel.appPrefs.supabaseIsLoggedIn && viewModel.appPrefs.supabaseUserEmail == "nthanhnam@gmail.com"
    }

    val filteredInstalled = remember(installed, searchQuery) {
        if (searchQuery.isBlank()) {
            installed
        } else {
            installed.filter {
                it.cleanName.contains(searchQuery, ignoreCase = true) ||
                it.type.contains(searchQuery, ignoreCase = true) ||
                it.source.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val notInstalled = remember(available, installedIds, searchQuery) {
        val baseList = available.filter { info ->
            info.name !in installedIds
        }
        if (searchQuery.isBlank()) {
            baseList
        } else {
            baseList.filter {
                it.cleanName.contains(searchQuery, ignoreCase = true) ||
                it.type.contains(searchQuery, ignoreCase = true) ||
                it.source.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val typeFilteredInstalled = remember(filteredInstalled, selectedFilter) {
        if (selectedFilter == "Tất cả") {
            filteredInstalled
        } else {
            filteredInstalled.filter { ext ->
                val type = ext.type.lowercase(Locale.ROOT)
                val locale = ext.locale.lowercase(Locale.ROOT)
                val isChinese = type.contains("chinese") || locale.startsWith("zh") || locale.startsWith("cn") || locale == "chi" || locale == "zho"
                val isNovel = type.contains("novel") || type.contains("text")
                val isComic = type.contains("comic") || type.contains("manga") || type.contains("image")
                val isVideo = type.contains("video") || type.contains("movie") || type.contains("anime")
                when (selectedFilter) {
                    "Truyện chữ" -> isNovel && !isChinese
                    "Truyện chữ Trung" -> isNovel && isChinese
                    "Truyện tranh" -> isComic
                    "Phim" -> isVideo
                    else -> true
                }
            }
        }
    }

    val typeFilteredNotInstalled = remember(notInstalled, selectedFilter) {
        if (selectedFilter == "Tất cả") {
            notInstalled
        } else {
            notInstalled.filter { info ->
                val type = info.type.lowercase(Locale.ROOT)
                val locale = info.locale.lowercase(Locale.ROOT)
                val isChinese = type.contains("chinese") || locale.startsWith("zh") || locale.startsWith("cn") || locale == "chi" || locale == "zho"
                val isNovel = type.contains("novel") || type.contains("text")
                val isComic = type.contains("comic") || type.contains("manga") || type.contains("image")
                val isVideo = type.contains("video") || type.contains("movie") || type.contains("anime")
                when (selectedFilter) {
                    "Truyện chữ" -> isNovel && !isChinese
                    "Truyện chữ Trung" -> isNovel && isChinese
                    "Truyện tranh" -> isComic
                    "Phim" -> isVideo
                    else -> true
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.fetchAllExtensions()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Tìm kiếm extension...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = "Xóa")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() })
        )

        // Hàng Filter Chips ngang lọc tiện ích store
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filters.forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = { Text(filter) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (error != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("❌ Lỗi tải extension", style = MaterialTheme.typography.titleMedium)
                    Text(error ?: "", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(16.dp))
                    FilledTonalButton(onClick = { viewModel.fetchAllExtensions() }) {
                        Text("Thử lại")
                    }
                }
            }
        } else {
            val itemsToDisplay = remember(typeFilteredNotInstalled, displayedCount) {
                typeFilteredNotInstalled.take(displayedCount)
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                // === Installed Section ===
                if (typeFilteredInstalled.isNotEmpty()) {
                    item {
                        Text(
                            "Đã cài đặt (${typeFilteredInstalled.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    items(typeFilteredInstalled) { ext ->
                        val isError = remember(ext.id, isResumed) { prefs.getBoolean("ext_error_${ext.id}", false) }
                        ListItem(
                            modifier = Modifier.clickable {
                                navController.navigate(Routes.browse(ext.id))
                            },
                            headlineContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(ext.cleanName, fontWeight = FontWeight.SemiBold)
                                    if (isError) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("⚠️ Lỗi", color = Color(0xFFE57373), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            },
                            supportingContent = {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        ExtensionBadges(type = ext.type, locale = ext.locale)
                                        Text("v${ext.version}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                    }
                                    Text(
                                        text = ext.source.replace("https://", "").replace("http://", "").trimEnd('/'),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                            },
                            leadingContent = {
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
                                            .size(40.dp)
                                            .clip(CircleShape),
                                    )
                                } else {
                                    Surface(
                                        modifier = Modifier.size(40.dp),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Filled.Extension,
                                                null,
                                                modifier = Modifier.size(24.dp),
                                            )
                                        }
                                    }
                                }
                            },
                            trailingContent = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    TextButton(onClick = { viewModel.uninstallExtension(ext.id) }) {
                                        Text("Gỡ", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        )
                    }
                }

                // === Available Section ===
                if (typeFilteredNotInstalled.isNotEmpty()) {
                    item {
                        if (typeFilteredInstalled.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }
                        Text(
                            "Có sẵn (${typeFilteredNotInstalled.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    
                    items(itemsToDisplay) { info ->
                        val isInstalling = info.name in installingIds
                        val extId = remember(info.name) { info.name.toSlug() }
                        val isError = remember(extId, isResumed) { prefs.getBoolean("ext_error_$extId", false) }
                        ListItem(
                            headlineContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(info.cleanName, fontWeight = FontWeight.SemiBold)
                                    if (isError) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("⚠️ Lỗi", color = Color(0xFFE57373), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            },
                            supportingContent = {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        ExtensionBadges(type = info.type, locale = info.locale)
                                        Text("v${info.version}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                    }
                                    Text(
                                        text = info.source.replace("https://", "").replace("http://", "").trimEnd('/'),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                            },
                            leadingContent = {
                                val iconModel = remember(info.icon) {
                                    if (info.icon.startsWith("http://") || info.icon.startsWith("https://")) {
                                        info.icon
                                    } else if (info.icon.startsWith("file:///android_asset/")) {
                                        android.net.Uri.parse(info.icon)
                                    } else if (info.icon.isNotBlank() && !info.icon.startsWith("gdrive://")) {
                                        File(info.icon)
                                    } else {
                                        null
                                    }
                                }
                                if (iconModel != null && (iconModel is String || iconModel is android.net.Uri || (iconModel is File && iconModel.exists()))) {
                                    AsyncImage(
                                        model = iconModel,
                                        contentDescription = info.cleanName,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape),
                                    )
                                } else {
                                    Surface(
                                        modifier = Modifier.size(40.dp),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Filled.Extension,
                                                null,
                                                modifier = Modifier.size(24.dp),
                                            )
                                        }
                                    }
                                }
                            },
                            trailingContent = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (isInstalling) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    } else {
                                        FilledTonalButton(
                                            onClick = { 
                                                viewModel.installExtension(info) 
                                            },
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                        ) {
                                            Text("Cài")
                                        }
                                    }
                                }
                            }
                        )
                    }
                    
                    if (typeFilteredNotInstalled.size > displayedCount) {
                        item {
                            LaunchedEffect(Unit) {
                                displayedCount += 50
                            }
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

                // Empty state
                if (typeFilteredNotInstalled.isEmpty() && typeFilteredInstalled.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🔍", style = MaterialTheme.typography.displayMedium)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    if (searchQuery.isNotEmpty() || selectedFilter != "Tất cả") "Không tìm thấy kết quả" else "Chưa có tiện ích mở rộng nào",
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    if (searchQuery.isNotEmpty() || selectedFilter != "Tất cả") "Hãy thử tìm với từ khóa khác hoặc đổi bộ lọc." else "Thêm kho lưu trữ để cài đặt và cập nhật nội dung bạn quan tâm nhé!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

            }
        }
    }
}

/**
 * Tab "Kho lưu trữ" — quản lý repository URLs.
 * Giống hệt VBook: list repos + button "Thêm kho lưu trữ mới" + dialog nhập URL.
 */
@Composable
fun RepositoriesTab(viewModel: ExtensionViewModel) {
    val repos by viewModel.repositories.collectAsStateWithLifecycle(initialValue = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<RepositoryEntity?>(null) }

    val isAdmin = remember(viewModel.appPrefs.supabaseIsLoggedIn, viewModel.appPrefs.supabaseUserEmail) {
        viewModel.appPrefs.supabaseIsLoggedIn && viewModel.appPrefs.supabaseUserEmail == "nthanhnam@gmail.com"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (repos.isEmpty()) {
            // Empty state — giống VBook
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("🔍", style = MaterialTheme.typography.displayLarge)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Chưa có tiện ích mở rộng nào",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Thêm kho lưu trữ để cài đặt và cập nhật nội dung bạn quan tâm nhé!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                if (isAdmin) {
                    Spacer(modifier = Modifier.height(24.dp))
                    OutlinedButton(onClick = { showAddDialog = true }) {
                        Text("Thêm kho lưu trữ mới")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(repos) { repo ->
                    ListItem(
                        headlineContent = {
                            Text(repo.name.ifBlank { "Repository" }, fontWeight = FontWeight.Medium)
                        },
                        supportingContent = {
                            Text(
                                repo.url,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        leadingContent = {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Filled.Inventory2,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                }
                            }
                        },
                        trailingContent = {
                            if (isAdmin) {
                                IconButton(onClick = { showDeleteConfirm = repo }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Xóa",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        },
                    )
                }

                // Button thêm mới
                if (isAdmin) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            OutlinedButton(onClick = { showAddDialog = true }) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Thêm kho lưu trữ mới")
                            }
                        }
                    }
                }
            }
        }
    }

    // === Dialog thêm kho lưu trữ ===
    if (showAddDialog && isAdmin) {
        AddRepositoryDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { url ->
                viewModel.addRepository(url)
                showAddDialog = false
            }
        )
    }

    // === Dialog xác nhận xóa ===
    if (isAdmin) {
        showDeleteConfirm?.let { repo ->
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = null },
                title = { Text("Xóa kho lưu trữ") },
                text = { Text("Bạn có chắc muốn xóa \"${repo.name.ifBlank { repo.url }}\"?") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.removeRepository(repo)
                        showDeleteConfirm = null
                    }) {
                        Text("Xóa", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = null }) {
                        Text("Hủy")
                    }
                }
            )
        }
    }
}

/**
 * Dialog "Kho lưu trữ" — nhập URL repo (giống VBook).
 */
@Composable
fun AddRepositoryDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kho lưu trữ") },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Nhập URL kho lưu trữ") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (url.isNotBlank()) onConfirm(url) },
                enabled = url.isNotBlank(),
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    )
}

@Composable
fun ExtensionBadges(type: String, locale: String) {
    val flag = when (locale.lowercase(Locale.ROOT)) {
        "vi_vn", "vi" -> "🇻🇳"
        "zh_cn", "zh" -> "🇨🇳"
        "en_us", "en" -> "🇺🇸"
        else -> "🌐"
    }
    
    val typeText = when (type.lowercase(Locale.ROOT)) {
        "comic", "manga", "image" -> "Tranh"
        "video", "anime", "movie" -> "Phim"
        else -> "Chữ"
    }
    
    val typeColor = when (type.lowercase(Locale.ROOT)) {
        "comic", "manga", "image" -> Color(0xFF2E7D32) // Xanh lục
        "video", "anime", "movie" -> Color(0xFFC62828) // Đỏ
        else -> Color(0xFFD84315) // Cam/Nâu gỗ ấm
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Quốc gia Badge
        Box(
            modifier = Modifier
                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(text = "$flag ${locale.uppercase(Locale.ROOT).take(2)}", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
        }
        
        // Loại nội dung Badge
        Box(
            modifier = Modifier
                .background(typeColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                .border(0.5.dp, typeColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(text = typeText, fontSize = 10.sp, color = typeColor, fontWeight = FontWeight.Bold)
        }
    }
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

