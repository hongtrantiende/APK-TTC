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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.BorderStroke
import coil.request.ImageRequest
import android.view.ViewGroup

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
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
    val tabs = listOf("Phần mở rộng", "Kho lưu trữ", "Kiểm thử")
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
                2 -> ExtensionTestTab(viewModel = viewModel)
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
                                val updateInfo = remember(available, ext.name, ext.version) {
                                    available.find { 
                                        it.name == ext.name && it.version > ext.version.toDouble()
                                    }
                                }
                                val isInstalling = remember(installingIds, ext.name) {
                                    ext.name in installingIds
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (updateInfo != null) {
                                        if (isInstalling) {
                                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                        } else {
                                            Button(
                                                onClick = { viewModel.installExtension(updateInfo) },
                                                contentPadding = PaddingValues(horizontal = 12.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                                ),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Text("Cập nhật", fontSize = 12.sp)
                                            }
                                        }
                                    }
                                    TextButton(
                                        onClick = { viewModel.uninstallExtension(ext.id) },
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionTestTab(viewModel: ExtensionViewModel) {
    val context = LocalContext.current
    val installedExtensions by viewModel.installedExtensions.collectAsStateWithLifecycle(initialValue = emptyList())
    val isTesting by viewModel.isTesting.collectAsStateWithLifecycle()
    val testResultStatus by viewModel.testResultStatus.collectAsStateWithLifecycle()
    val testResultData by viewModel.testResultData.collectAsStateWithLifecycle()
    val testLogs by viewModel.testLogs.collectAsStateWithLifecycle()

    val isChainTesting by viewModel.isChainTesting.collectAsStateWithLifecycle()
    val chainTestSteps by viewModel.chainTestSteps.collectAsStateWithLifecycle()

    var selectedExtension by remember { mutableStateOf<ExtensionEntity?>(null) }
    var loadedExtension by remember { mutableStateOf<com.nam.novelreader.extension.model.LoadedExtension?>(null) }
    
    // Dropdown States
    var extDropdownExpanded by remember { mutableStateOf(false) }
    var scriptDropdownExpanded by remember { mutableStateOf(false) }
    
    // Script & Arguments State
    var selectedScriptKey by remember { mutableStateOf("") }
    val arguments = remember { mutableStateListOf<String>() }

    // Chế độ kiểm thử: 0 = Test đơn lẻ, 1 = Test liên hoàn E2E
    var testMode by remember { mutableStateOf(0) }

    // Tự chọn extension đầu tiên
    LaunchedEffect(installedExtensions) {
        if (selectedExtension == null && installedExtensions.isNotEmpty()) {
            selectedExtension = installedExtensions.first()
        }
    }

    // Load LoadedExtension khi chọn ExtensionEntity thay đổi
    LaunchedEffect(selectedExtension) {
        selectedExtension?.id?.let { id ->
            loadedExtension = viewModel.extensionLoader.loadExtension(id)
        } ?: run {
            loadedExtension = null
        }
    }

    // Cập nhật Script dropdown khi LoadedExtension thay đổi
    LaunchedEffect(loadedExtension) {
        val scripts = loadedExtension?.pluginJson?.script?.keys?.toList() ?: emptyList()
        if (scripts.isNotEmpty()) {
            // Ưu tiên chọn "detail" hoặc "home", nếu không thì lấy phần tử đầu tiên
            selectedScriptKey = when {
                scripts.contains("detail") -> "detail"
                scripts.contains("home") -> "home"
                else -> scripts.first()
            }
        } else {
            selectedScriptKey = ""
        }
    }

    // Reset Arguments gợi ý khi loại script thay đổi
    LaunchedEffect(selectedScriptKey, loadedExtension) {
        arguments.clear()
        val defaultSource = loadedExtension?.pluginJson?.metadata?.source ?: ""
        when (selectedScriptKey) {
            "home" -> {
                arguments.add("1") // Trang mặc định
            }
            "search" -> {
                arguments.add("Đấu Phá Thương Khung") // Từ khóa mặc định
                arguments.add("1") // Trang mặc định
            }
            "detail" -> {
                // Gợi ý URL truyện cụ thể của nguồn đó
                val host = if (defaultSource.startsWith("http")) defaultSource else "https://$defaultSource"
                val sampleUrl = when {
                    host.contains("truyenfull") -> "$host/dau-pha-thuong-khung/"
                    host.contains("metruyencv") -> "$host/truyen/dau-pha-thuong-khung"
                    host.contains("sangtacviet") -> "$host/truyen/uukanshu/1/1/"
                    else -> "$host/sample-novel-slug"
                }
                arguments.add(sampleUrl)
            }
            "toc" -> {
                val host = if (defaultSource.startsWith("http")) defaultSource else "https://$defaultSource"
                val sampleUrl = when {
                    host.contains("truyenfull") -> "$host/dau-pha-thuong-khung/"
                    host.contains("metruyencv") -> "$host/truyen/dau-pha-thuong-khung"
                    else -> "$host/sample-novel-slug"
                }
                arguments.add(sampleUrl)
            }
            "chap" -> {
                val host = if (defaultSource.startsWith("http")) defaultSource else "https://$defaultSource"
                val sampleUrl = when {
                    host.contains("truyenfull") -> "$host/dau-pha-thuong-khung/chuong-1/"
                    host.contains("metruyencv") -> "$host/truyen/dau-pha-thuong-khung/chuong-1"
                    else -> "$host/sample-novel-slug/chapter-1"
                }
                arguments.add(sampleUrl)
            }
            else -> {
                // Các loại script khác, mặc định chừa 1 ô trống
                arguments.add("")
            }
        }
    }

    if (installedExtensions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🔌", style = MaterialTheme.typography.displayLarge)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Không tìm thấy tiện ích đã cài đặt",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Bạn cần cài đặt ít nhất một tiện ích mở rộng từ kho lưu trữ trước khi thực hiện kiểm thử.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Mode Switcher (Segmented Control)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val selectedColor = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                val unselectedColor = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                
                Button(
                    onClick = { testMode = 0 },
                    colors = if (testMode == 0) selectedColor else unselectedColor,
                    modifier = Modifier.weight(1f),
                    elevation = null,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Test Đơn Lẻ", fontWeight = FontWeight.SemiBold)
                }
                
                Button(
                    onClick = { testMode = 1 },
                    colors = if (testMode == 1) selectedColor else unselectedColor,
                    modifier = Modifier.weight(1f),
                    elevation = null,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Test Liên Hoàn E2E", fontWeight = FontWeight.SemiBold)
                }
            }

            if (testMode == 0) {
                //Card cấu hình tiện ích
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Cấu hình kiểm thử", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

                        // 1. Dropdown chọn Extension
                        ExposedDropdownMenuBox(
                            expanded = extDropdownExpanded,
                            onExpandedChange = { extDropdownExpanded = !extDropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedExtension?.cleanName ?: "Chọn tiện ích",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Chọn tiện ích") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = extDropdownExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = extDropdownExpanded,
                                onDismissRequest = { extDropdownExpanded = false }
                            ) {
                                installedExtensions.forEach { ext ->
                                    DropdownMenuItem(
                                        text = { Text(ext.cleanName) },
                                        onClick = {
                                            selectedExtension = ext
                                            extDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // 2. Dropdown chọn Script Type
                        val scripts = loadedExtension?.pluginJson?.script?.keys?.toList() ?: emptyList()
                        ExposedDropdownMenuBox(
                            expanded = scriptDropdownExpanded,
                            onExpandedChange = { if (scripts.isNotEmpty()) scriptDropdownExpanded = !scriptDropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = if (selectedScriptKey.isEmpty()) "Không có script" else "$selectedScriptKey.js",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Loại script test") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = scriptDropdownExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                enabled = scripts.isNotEmpty()
                            )
                            if (scripts.isNotEmpty()) {
                                ExposedDropdownMenu(
                                    expanded = scriptDropdownExpanded,
                                    onDismissRequest = { scriptDropdownExpanded = false }
                                ) {
                                    scripts.forEach { key ->
                                        DropdownMenuItem(
                                            text = { Text("$key.js") },
                                            onClick = {
                                                selectedScriptKey = key
                                                scriptDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Card nhập tham số Arguments
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Tham số truyền vào (Arguments)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                // Nút thêm tham số
                                IconButton(
                                    onClick = { arguments.add("") },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = "Thêm tham số", modifier = Modifier.size(20.dp))
                                }
                                // Nút xóa bớt tham số cuối
                                IconButton(
                                    onClick = { if (arguments.isNotEmpty()) arguments.removeAt(arguments.size - 1) },
                                    modifier = Modifier.size(32.dp),
                                    enabled = arguments.isNotEmpty()
                                ) {
                                    Icon(Icons.Filled.Remove, contentDescription = "Xóa tham số", modifier = Modifier.size(20.dp))
                                }
                            }
                        }

                        if (arguments.isEmpty()) {
                            Text(
                                "Không truyền tham số (Không đối số)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            arguments.forEachIndexed { index, arg ->
                                OutlinedTextField(
                                    value = arg,
                                    onValueChange = { newValue -> arguments[index] = newValue },
                                    label = { Text("Tham số ${index + 1}") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }
                }

                // Bảng điều khiển nút bấm
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            selectedExtension?.let { ext ->
                                viewModel.runExtensionTest(ext.id, selectedScriptKey, arguments.toList())
                            }
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !isTesting && selectedExtension != null && selectedScriptKey.isNotEmpty()
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Bắt đầu test")
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            selectedExtension?.let { ext ->
                                viewModel.saveAndShareTestLog(ext.cleanName, selectedScriptKey, arguments.toList())
                            }
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        enabled = testLogs.isNotEmpty() || testResultStatus != null
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Chia sẻ Log")
                    }
                }

                // Hiển thị trạng thái kết quả
                testResultStatus?.let { status ->
                    val statusColor = when {
                        status.startsWith("SUCCESS") -> Color(0xFF4CAF50)
                        status.startsWith("ERROR") -> Color(0xFFF44336)
                        else -> Color(0xFFFF9800) // CRASH/Warning
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.12f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = statusColor,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (status.startsWith("SUCCESS")) Icons.Filled.Check else Icons.Filled.Close,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Column {
                                Text("Trạng thái kiểm thử", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(status, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = statusColor)
                            }
                        }
                    }
                }

                // Console Logs Terminal View
                if (testLogs.isNotEmpty()) {
                    Text("Nhật ký Console (Console Logs)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(testLogs) { logLine ->
                                val textColor = when {
                                    logLine.contains("Lỗi") || logLine.contains("Error") || logLine.contains("fail") -> Color(0xFFF44336)
                                    logLine.contains("Cảnh báo") || logLine.contains("Warning") -> Color(0xFFFFEB3B)
                                    logLine.startsWith(">>>") -> Color(0xFF00E676)
                                    else -> Color(0xFFE0E0E0)
                                }
                                Text(
                                    text = logLine,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = textColor
                                )
                            }
                        }
                    }
                }

                // Dữ liệu đầu ra (JSON Output)
                testResultData?.let { data ->
                    val prettyJson = remember(data) {
                        try {
                            val parsed = kotlinx.serialization.json.Json.parseToJsonElement(data)
                            val prettyFormat = kotlinx.serialization.json.Json { prettyPrint = true }
                            prettyFormat.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), parsed)
                        } catch (e: Exception) {
                            data
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Dữ liệu đầu ra (JSON Output)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        // Nút sao chép JSON nhanh
                        TextButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Test JSON Output", prettyJson)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Đã sao chép kết quả vào bộ nhớ tạm!", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sao chép", fontSize = 12.sp)
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 350.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2D2D))
                    ) {
                        Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
                            Text(
                                text = prettyJson,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color(0xFF81D4FA) // Xanh dương nhạt phong cách code editor
                            )
                        }
                    }
                }
            } else {
                // Card cấu hình tiện ích cho E2E
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Cấu hình kiểm thử liên hoàn E2E", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Hệ thống sẽ chạy chuỗi liên hoàn 4 bước: Nạp danh sách truyện -> Xem chi tiết truyện -> Lấy mục lục chương -> Tải nội dung chương để kiểm chứng vòng đời hoạt động của tiện ích.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        // Dropdown chọn Extension
                        ExposedDropdownMenuBox(
                            expanded = extDropdownExpanded,
                            onExpandedChange = { extDropdownExpanded = !extDropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedExtension?.cleanName ?: "Chọn tiện ích",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Chọn tiện ích") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = extDropdownExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = extDropdownExpanded,
                                onDismissRequest = { extDropdownExpanded = false }
                            ) {
                                installedExtensions.forEach { ext ->
                                    DropdownMenuItem(
                                        text = { Text(ext.cleanName) },
                                        onClick = {
                                            selectedExtension = ext
                                            extDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Bảng điều khiển nút bấm E2E
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            selectedExtension?.let { ext ->
                                viewModel.runChainTest(ext.id)
                            }
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !isChainTesting && selectedExtension != null
                    ) {
                        if (isChainTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Chạy E2E")
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            selectedExtension?.let { ext ->
                                viewModel.shareChainTestLog(ext.cleanName)
                            }
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        enabled = chainTestSteps.isNotEmpty()
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Chia sẻ Báo cáo")
                    }
                }

                // Hiển thị danh sách các bước E2E
                if (chainTestSteps.isNotEmpty()) {
                    Text("Tiến trình kiểm thử E2E", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    
                    var expandedStepIndex by remember { mutableStateOf<Int?>(null) }
                    
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        chainTestSteps.forEachIndexed { index, step ->
                            val statusColor = when (step.status) {
                                ChainStepStatus.SUCCESS -> Color(0xFF4CAF50)
                                ChainStepStatus.ERROR -> Color(0xFFF44336)
                                ChainStepStatus.RUNNING -> MaterialTheme.colorScheme.primary
                                ChainStepStatus.SKIPPED -> Color.Gray
                                ChainStepStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            }
                            
                            val isExpanded = expandedStepIndex == index
                            
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isExpanded) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                                ),
                                onClick = {
                                    expandedStepIndex = if (isExpanded) null else index
                                }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier.size(24.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                when (step.status) {
                                                    ChainStepStatus.RUNNING -> {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(16.dp),
                                                            strokeWidth = 2.dp,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                    ChainStepStatus.SUCCESS -> {
                                                        Surface(
                                                            shape = CircleShape,
                                                            color = Color(0xFF4CAF50),
                                                            modifier = Modifier.size(20.dp)
                                                        ) {
                                                            Box(contentAlignment = Alignment.Center) {
                                                                Icon(
                                                                    imageVector = Icons.Filled.Check,
                                                                    contentDescription = null,
                                                                    tint = Color.White,
                                                                    modifier = Modifier.size(12.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                    ChainStepStatus.ERROR -> {
                                                        Surface(
                                                            shape = CircleShape,
                                                            color = Color(0xFFF44336),
                                                            modifier = Modifier.size(20.dp)
                                                        ) {
                                                            Box(contentAlignment = Alignment.Center) {
                                                                Icon(
                                                                    imageVector = Icons.Filled.Close,
                                                                    contentDescription = null,
                                                                    tint = Color.White,
                                                                    modifier = Modifier.size(12.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                    ChainStepStatus.SKIPPED -> {
                                                        Surface(
                                                            shape = CircleShape,
                                                            color = Color.Gray.copy(alpha = 0.5f),
                                                            modifier = Modifier.size(16.dp)
                                                        ) {
                                                            Box(contentAlignment = Alignment.Center) {
                                                                Icon(
                                                                    imageVector = Icons.Filled.Remove,
                                                                    contentDescription = null,
                                                                    tint = Color.White,
                                                                    modifier = Modifier.size(10.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                    ChainStepStatus.PENDING -> {
                                                        Surface(
                                                            shape = CircleShape,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                                            modifier = Modifier.size(12.dp)
                                                        ) {}
                                                    }
                                                }
                                            }
                                            Text(
                                                text = step.name,
                                                fontWeight = FontWeight.SemiBold,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (step.status == ChainStepStatus.SKIPPED) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        if (step.duration > 0) {
                                            Text("${step.duration}ms", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    
                                    if (isExpanded) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        
                                        // Console logs
                                        if (step.logs.isNotEmpty()) {
                                            Text("Console logs:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Card(
                                                modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp),
                                                shape = RoundedCornerShape(6.dp),
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
                                            ) {
                                                LazyColumn(modifier = Modifier.fillMaxSize().padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    items(step.logs) { logLine ->
                                                        val lineTextColor = when {
                                                            logLine.contains("Lỗi") || logLine.contains("Error") || logLine.contains("fail") -> Color(0xFFF44336)
                                                            logLine.contains("Cảnh báo") || logLine.contains("Warning") -> Color(0xFFFFEB3B)
                                                            logLine.startsWith(">>>") -> Color(0xFF00E676)
                                                            else -> Color(0xFFE0E0E0)
                                                        }
                                                        Text(
                                                            text = logLine,
                                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                            fontSize = 10.sp,
                                                            color = lineTextColor
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                        
                                        // Output JSON
                                        step.output?.let { outputData ->
                                            val prettyStepJson = remember(outputData) {
                                                try {
                                                    val parsed = kotlinx.serialization.json.Json.parseToJsonElement(outputData)
                                                    val prettyFormat = kotlinx.serialization.json.Json { prettyPrint = true }
                                                    prettyFormat.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), parsed)
                                                } catch (e: Exception) {
                                                    outputData
                                                }
                                            }
                                            
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("Output JSON:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                TextButton(
                                                    onClick = {
                                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                        val clip = android.content.ClipData.newPlainText("Step Output", prettyStepJson)
                                                        clipboard.setPrimaryClip(clip)
                                                        Toast.makeText(context, "Đã sao chép!", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.height(28.dp)
                                                ) {
                                                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Sao chép", fontSize = 10.sp)
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Card(
                                                modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                                                shape = RoundedCornerShape(6.dp),
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2D2D))
                                            ) {
                                                Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp)) {
                                                    Text(
                                                        text = prettyStepJson,
                                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                        fontSize = 10.sp,
                                                        color = Color(0xFF81D4FA)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bản xem trước kết quả E2E trực quan
                    val step4 = chainTestSteps.getOrNull(3)
                    if (step4 != null && step4.status == ChainStepStatus.SUCCESS && step4.output != null && selectedExtension != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Bản xem trước kết quả E2E", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        
                        val extType = selectedExtension!!.type.lowercase(Locale.ROOT)
                        val outputText = step4.output!!
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                when {
                                    extType.contains("comic") || extType.contains("manga") || extType.contains("image") -> {
                                        val imageUrls = remember(outputText) { extractComicImages(outputText) }
                                        if (imageUrls.isEmpty()) {
                                            Text("Không tìm thấy danh sách ảnh trong kết quả.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                                        } else {
                                            Text("Xem thử truyện tranh (${imageUrls.size} ảnh)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            Card(
                                                modifier = Modifier.fillMaxWidth().height(400.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                colors = CardDefaults.cardColors(containerColor = Color.Black)
                                            ) {
                                                LazyColumn(
                                                    modifier = Modifier.fillMaxSize(),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    items(imageUrls) { imgUrl ->
                                                        AsyncImage(
                                                            model = ImageRequest.Builder(LocalContext.current)
                                                                .data(imgUrl)
                                                                .crossfade(true)
                                                                .setHeader("Referer", selectedExtension!!.source)
                                                                .build(),
                                                            contentDescription = null,
                                                            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                                                            contentScale = androidx.compose.ui.layout.ContentScale.FillWidth
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    extType.contains("video") || extType.contains("movie") || extType.contains("anime") -> {
                                        val videoUrl = remember(outputText) { extractVideoUrl(outputText) }
                                        if (videoUrl.isBlank()) {
                                            Text("Không trích xuất được link video từ kết quả.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                                        } else {
                                            Text("Xem thử video / phim", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            
                                            val context = LocalContext.current
                                            val isDirectLink = remember(videoUrl) {
                                                val lower = videoUrl.lowercase()
                                                videoUrl.startsWith("http") && !videoUrl.contains("<") &&
                                                        listOf(".mp4", ".m3u8", ".mkv", ".webm", ".ts", ".avi", ".mov", ".flv", ".dash", ".m4s").any { lower.contains(it) }
                                            }
                                            
                                            if (isDirectLink) {
                                                var exoPlayerInstance by remember { mutableStateOf<androidx.media3.exoplayer.ExoPlayer?>(null) }
                                                
                                                DisposableEffect(videoUrl) {
                                                    val lowerUrl = videoUrl.lowercase()
                                                    val headers = mutableMapOf<String, String>()
                                                    var referer = selectedExtension?.source ?: "https://animehay.fm/"
                                                    var userAgent = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                                                    
                                                    if (lowerUrl.contains("bilibili") || 
                                                        lowerUrl.contains("bstar") || 
                                                        lowerUrl.contains("akamaized.net") || 
                                                        lowerUrl.contains("upos-") ||
                                                        lowerUrl.contains("bilibilivideo")) {
                                                        referer = "https://www.bilibili.tv/"
                                                        userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                                                        headers["Origin"] = "https://www.bilibili.tv"
                                                    }
                                                    if (lowerUrl.contains("streamfree")) {
                                                        referer = videoUrl
                                                    }
                                                    val cookieManager = android.webkit.CookieManager.getInstance()
                                                    val cookie = cookieManager.getCookie(videoUrl)
                                                    if (!cookie.isNullOrBlank()) {
                                                        headers["Cookie"] = cookie
                                                    }
                                                    headers["Referer"] = referer
                                                    headers["User-Agent"] = userAgent

                                                    val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
                                                        .setDefaultRequestProperties(headers)
                                                    
                                                    val mediaItemBuilder = androidx.media3.common.MediaItem.Builder().setUri(videoUrl)
                                                    if (lowerUrl.contains(".m3u8")) {
                                                        mediaItemBuilder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
                                                    } else if (lowerUrl.contains(".m4s") || lowerUrl.contains(".mp4")) {
                                                        mediaItemBuilder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_MP4)
                                                    }
                                                    val mediaItem = mediaItemBuilder.build()
                                                    
                                                    val mediaSource = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context)
                                                        .setDataSourceFactory(httpDataSourceFactory)
                                                        .createMediaSource(mediaItem)

                                                    val player = androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
                                                        setMediaSource(mediaSource)
                                                        prepare()
                                                        playWhenReady = false
                                                    }
                                                    exoPlayerInstance = player
                                                    onDispose {
                                                        player.release()
                                                        exoPlayerInstance = null
                                                    }
                                                }
                                                
                                                Card(
                                                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                                                    shape = RoundedCornerShape(8.dp),
                                                    colors = CardDefaults.cardColors(containerColor = Color.Black)
                                                ) {
                                                    AndroidView(
                                                        factory = { ctx ->
                                                            androidx.media3.ui.PlayerView(ctx).apply {
                                                                player = exoPlayerInstance
                                                                useController = true
                                                                layoutParams = ViewGroup.LayoutParams(
                                                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                                                    ViewGroup.LayoutParams.MATCH_PARENT
                                                                )
                                                            }
                                                        },
                                                        update = { playerView ->
                                                            playerView.player = exoPlayerInstance
                                                        },
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                }
                                            } else {
                                                var previewWebView by remember { mutableStateOf<android.webkit.WebView?>(null) }
                                                DisposableEffect(videoUrl) {
                                                    onDispose {
                                                        previewWebView?.let { webView ->
                                                            try {
                                                                webView.stopLoading()
                                                                webView.loadUrl("about:blank")
                                                                webView.clearHistory()
                                                                webView.removeAllViews()
                                                                webView.destroy()
                                                            } catch (e: Exception) {
                                                                android.util.Log.e("ExtensionScreens", "Error destroying preview WebView", e)
                                                            }
                                                        }
                                                        previewWebView = null
                                                    }
                                                }

                                                Card(
                                                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                                                    shape = RoundedCornerShape(8.dp),
                                                    colors = CardDefaults.cardColors(containerColor = Color.Black)
                                                ) {
                                                    AndroidView(
                                                        factory = { ctx ->
                                                            android.webkit.WebView(ctx).apply {
                                                                previewWebView = this
                                                                settings.apply {
                                                                    javaScriptEnabled = true
                                                                    domStorageEnabled = true
                                                                    mediaPlaybackRequiresUserGesture = false
                                                                }
                                                                webViewClient = object : android.webkit.WebViewClient() {
                                                                    override fun shouldOverrideUrlLoading(view: android.webkit.WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                                                                        return false
                                                                    }
                                                                }
                                                            }
                                                        },
                                                        update = { webView ->
                                                            if (webView.tag != videoUrl) {
                                                                webView.tag = videoUrl
                                                                val isRawHtml = videoUrl.trimStart().startsWith("<") || videoUrl.contains("<html") || videoUrl.contains("<iframe")
                                                                if (isRawHtml) {
                                                                    webView.loadDataWithBaseURL(selectedExtension!!.source, videoUrl, "text/html", "utf-8", null)
                                                                } else {
                                                                    webView.loadUrl(videoUrl, mapOf("Referer" to selectedExtension!!.source))
                                                                }
                                                            }
                                                        },
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    else -> {
                                        val contentText = remember(outputText) { extractNovelContent(outputText) }
                                        Text("Nội dung chương truyện chữ", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        Card(
                                            modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        ) {
                                            Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
                                                Text(
                                                    text = contentText,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    lineHeight = 22.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
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
        }
    }
}

private fun extractNovelContent(jsonStr: String): String {
    return try {
        val jsonToken = org.json.JSONTokener(jsonStr).nextValue()
        if (jsonToken is org.json.JSONObject) {
            val content = jsonToken.optString("content").takeIf { it.isNotBlank() }
                ?: jsonToken.optString("data").takeIf { it.isNotBlank() }
            content ?: jsonStr
        } else {
            jsonStr
        }
    } catch (e: Exception) {
        jsonStr
    }
}

private fun extractComicImages(jsonStr: String): List<String> {
    val urls = mutableListOf<String>()
    try {
        val jsonToken = org.json.JSONTokener(jsonStr).nextValue()
        val array = if (jsonToken is org.json.JSONArray) {
            jsonToken
        } else if (jsonToken is org.json.JSONObject) {
            val dataVal = jsonToken.opt("data")
            if (dataVal is org.json.JSONArray) {
                dataVal
            } else {
                jsonToken.optJSONArray("images")
                    ?: jsonToken.optJSONArray("urls")
                    ?: jsonToken.optJSONArray("list")
                    ?: jsonToken.optJSONArray("data")
            }
        } else {
            null
        }
        if (array != null) {
            for (i in 0 until array.length()) {
                val u = array.optString(i)
                if (u.isNotBlank()) {
                    urls.add(u)
                }
            }
        }
    } catch (e: Exception) {}
    return urls
}

private fun extractVideoUrl(jsonStr: String): String {
    try {
        val jsonToken = org.json.JSONTokener(jsonStr).nextValue()
        if (jsonToken is org.json.JSONObject) {
            return jsonToken.optString("url").takeIf { it.isNotBlank() }
                ?: jsonToken.optString("link").takeIf { it.isNotBlank() }
                ?: jsonToken.optString("data").takeIf { it.isNotBlank() }
                ?: jsonStr
        } else if (jsonToken is org.json.JSONArray && jsonToken.length() > 0) {
            val first = jsonToken.optJSONObject(0)
            if (first != null) {
                return first.optString("url").takeIf { it.isNotBlank() }
                    ?: first.optString("link").takeIf { it.isNotBlank() }
                    ?: ""
            }
        }
    } catch (e: Exception) {}
    return jsonStr.trim().removeSurrounding("\"")
}

