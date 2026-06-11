package com.nam.novelreader.feature.settings

import android.widget.Toast
import android.os.Build
import kotlinx.coroutines.delay
import com.nam.novelreader.service.AndroidTestServerService
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.activity.compose.rememberLauncherForActivityResult
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import coil.compose.AsyncImage
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.nam.novelreader.feature.components.*
import com.nam.novelreader.navigation.Routes

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SettingsDatabaseEntryPoint {
    fun appDatabase(): com.nam.novelreader.data.local.AppDatabase
    fun novelDao(): com.nam.novelreader.data.local.dao.NovelDao
    fun extensionDao(): com.nam.novelreader.data.local.dao.ExtensionDao
    fun supabaseAuthManager(): com.nam.novelreader.data.network.SupabaseAuthManager
    fun appPreferences(): com.nam.novelreader.data.preferences.AppPreferences
    fun googleDriveBackupManager(): com.nam.novelreader.data.network.GoogleDriveBackupManager
}

private fun String.escapeJson(): String {
    return this.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
}

private fun String.unescapeJson(): String {
    return this.replace("\\\\", "\\").replace("\\\"", "\"").replace("\\n", "\n").replace("\\r", "\r")
}

private fun exportToJson(novels: List<com.nam.novelreader.data.local.entity.NovelEntity>): String {
    val sb = StringBuilder()
    sb.append("[\n")
    novels.forEachIndexed { idx, novel ->
        sb.append("  {\n")
        sb.append("    \"url\": \"${novel.url.escapeJson()}\",\n")
        sb.append("    \"title\": \"${novel.title.escapeJson()}\",\n")
        sb.append("    \"author\": \"${novel.author.escapeJson()}\",\n")
        sb.append("    \"cover\": \"${novel.cover.escapeJson()}\",\n")
        sb.append("    \"description\": \"${novel.description.escapeJson()}\",\n")
        sb.append("    \"genres\": \"${novel.genres.escapeJson()}\",\n")
        sb.append("    \"status\": \"${novel.status.escapeJson()}\",\n")
        sb.append("    \"type\": \"${novel.type.escapeJson()}\",\n")
        sb.append("    \"extensionId\": \"${novel.extensionId.escapeJson()}\",\n")
        sb.append("    \"totalChapters\": ${novel.totalChapters}\n")
        sb.append("  }")
        if (idx < novels.size - 1) sb.append(",")
        sb.append("\n")
    }
    sb.append("]")
    return sb.toString()
}

private fun parseBackupJson(json: String): List<com.nam.novelreader.data.local.entity.NovelEntity> {
    val list = mutableListOf<com.nam.novelreader.data.local.entity.NovelEntity>()
    val objRegex = Regex("\\{\\s*\"url\":\\s*\"(.*?)\",\\s*\"title\":\\s*\"(.*?)\",\\s*\"author\":\\s*\"(.*?)\",\\s*\"cover\":\\s*\"(.*?)\",\\s*\"description\":\\s*\"(.*?)\",\\s*\"genres\":\\s*\"(.*?)\",\\s*\"status\":\\s*\"(.*?)\",\\s*\"type\":\\s*\"(.*?)\",\\s*\"extensionId\":\\s*\"(.*?)\",\\s*\"totalChapters\":\\s*(\\d+)\\s*\\}")
    
    val cleanedJson = json.replace("\n", " ").replace("\r", " ")
    val matches = objRegex.findAll(cleanedJson)
    for (match in matches) {
        val groups = match.groupValues
        list.add(
            com.nam.novelreader.data.local.entity.NovelEntity(
                url = groups[1].unescapeJson(),
                title = groups[2].unescapeJson(),
                author = groups[3].unescapeJson(),
                cover = groups[4].unescapeJson(),
                description = groups[5].unescapeJson(),
                genres = groups[6].unescapeJson(),
                status = groups[7].unescapeJson(),
                type = groups[8].unescapeJson(),
                extensionId = groups[9].unescapeJson(),
                totalChapters = groups[10].toInt(),
                isInLibrary = true,
                addedToLibraryAt = System.currentTimeMillis()
            )
        )
    }
    return list
}

private fun getLocalIpAddress(): String {
    try {
        val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            val addresses = networkInterface.inetAddresses
            while (addresses.hasMoreElements()) {
                val address = addresses.nextElement()
                if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                    return address.hostAddress ?: "127.0.0.1"
                }
            }
        }
    } catch (e: Exception) {
        // ignore
    }
    return "127.0.0.1"
}

/**
 * SettingsScreen — giống VBook "Thêm" tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavHostController
) {
    val context = LocalContext.current
    val entryPoint = remember {
        dagger.hilt.EntryPoints.get(context.applicationContext, SettingsDatabaseEntryPoint::class.java)
    }
    val authManager = entryPoint.supabaseAuthManager()
    val appPrefs = entryPoint.appPreferences()
    val prefs = appPrefs.prefs
    val scope = rememberCoroutineScope()

    // Login/Profile states
    var isLoggedIn by remember { mutableStateOf(appPrefs.supabaseIsLoggedIn) }
    var displayName by remember { mutableStateOf(prefs.getString("user_display_name", "Khách") ?: "Khách") }
    var userSubtitle by remember { mutableStateOf(if (appPrefs.supabaseIsLoggedIn) appPrefs.supabaseUserEmail else "Chưa đăng nhập") }
    var showProfileEditDialog by remember { mutableStateOf(false) }
    var avatarPath by remember { mutableStateOf(appPrefs.userAvatarPath) }
    
    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "user_avatar_path" || key == "supabase_is_logged_in" || key == "user_display_name" || key == "supabase_user_email") {
                isLoggedIn = appPrefs.supabaseIsLoggedIn
                displayName = prefs.getString("user_display_name", "Khách") ?: "Khách"
                userSubtitle = if (appPrefs.supabaseIsLoggedIn) appPrefs.supabaseUserEmail else "Chưa đăng nhập"
                avatarPath = appPrefs.userAvatarPath
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        val avatarFile = java.io.File(context.filesDir, "user_avatar.jpg")
                        avatarFile.outputStream().use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }

                        // Nén ảnh sang Base64 siêu nhẹ
                        val originalBitmap = BitmapFactory.decodeFile(avatarFile.absolutePath)
                        val base64Str = if (originalBitmap != null) {
                            val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, 120, 120, true)
                            val outputStream = ByteArrayOutputStream()
                            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
                            val bytes = outputStream.toByteArray()
                            "data:image/jpeg;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        } else ""

                        appPrefs.userAvatarPath = avatarFile.absolutePath
                        avatarPath = avatarFile.absolutePath

                        // Đồng bộ lên Supabase Auth
                        if (appPrefs.supabaseIsLoggedIn && base64Str.isNotBlank()) {
                            authManager.updateUserMetadata(displayName = null, avatarBase64 = base64Str)
                        }

                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Đã cập nhật ảnh đại diện!", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: java.lang.Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Lỗi lưu ảnh đại diện: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Dialog states for settings options
    var showLoginDialog by remember { mutableStateOf(false) }
    var showRegisterDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showNotifDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var showSupabaseConfigDialog by remember { mutableStateOf(false) }

    // Settings details
    var appLanguage by remember { mutableStateOf(prefs.getString("app_language", "vi") ?: "vi") }

    // === WARP VPN state ===
    var warpEnabled by remember { mutableStateOf(com.nam.novelreader.vpn.WarpVpnService.isRunning) }
    var warpAutoIntervalSeconds by remember {
        mutableIntStateOf(prefs.getInt("warp_auto_interval", 0))
    }
    var showWarpIntervalDialog by remember { mutableStateOf(false) }
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            // Permission granted — start WARP
            val intent = android.content.Intent(context, com.nam.novelreader.vpn.WarpVpnService::class.java).apply {
                action = com.nam.novelreader.vpn.WarpVpnService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            warpEnabled = true
        }
    }
    // Auto-reconnect timer job
    val warpAutoJob = remember { androidx.compose.runtime.mutableStateOf<kotlinx.coroutines.Job?>(null) }
    LaunchedEffect(warpEnabled, warpAutoIntervalSeconds) {
        warpAutoJob.value?.cancel()
        if (warpEnabled && warpAutoIntervalSeconds > 0) {
            warpAutoJob.value = scope.launch {
                while (true) {
                    delay(warpAutoIntervalSeconds * 1000L)
                    if (com.nam.novelreader.vpn.WarpVpnService.isRunning) {
                        val intent = android.content.Intent(context, com.nam.novelreader.vpn.WarpVpnService::class.java)
                            .setAction(com.nam.novelreader.vpn.WarpVpnService.ACTION_RECONNECT)
                        context.startService(intent)
                    }
                }
            }
        }
    }
    // Sync WARP state from broadcast
    DisposableEffect(Unit) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context?, intent: android.content.Intent?) {
                warpEnabled = intent?.getBooleanExtra(com.nam.novelreader.vpn.WarpVpnService.EXTRA_CONNECTED, false) ?: false
            }
        }
        val filter = android.content.IntentFilter(com.nam.novelreader.vpn.WarpVpnService.BROADCAST_STATE)
        // Android 13+ yêu cầu flag RECEIVER_NOT_EXPORTED cho local broadcasts
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose { context.unregisterReceiver(receiver) }
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri) ?: throw Exception("Không thể mở tệp")
                    val jsonStr = inputStream.bufferedReader().use { it.readText() }
                    
                    val parsedNovels = parseBackupJson(jsonStr)
                    if (parsedNovels.isEmpty()) throw Exception("Tệp sao lưu rỗng hoặc không đúng cấu trúc")
                    
                    val entryPoint = dagger.hilt.EntryPoints.get(context.applicationContext, SettingsDatabaseEntryPoint::class.java)
                    val db = entryPoint.appDatabase()
                    
                    parsedNovels.forEach { novel ->
                        db.novelDao().insert(novel)
                    }
                    
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Khôi phục thành công ${parsedNovels.size} truyện vào thư viện!", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Lỗi khôi phục: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
    
    Scaffold(
        containerColor = VBookTheme.backgroundColor(),
        topBar = {
            TopAppBar(
                title = { Text("Thêm", fontWeight = FontWeight.Bold, color = VBookTheme.textColor()) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VBookTheme.backgroundColor()
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // === Avatar Section ===
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .clickable { if (isLoggedIn) showProfileEditDialog = true else showLoginDialog = true },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    Surface(
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape,
                        color = VBookTheme.cardColor(),
                        border = androidx.compose.foundation.BorderStroke(1.dp, VBookTheme.primaryColor().copy(alpha = 0.3f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            val hasLocalAvatar = avatarPath.isNotBlank() && java.io.File(avatarPath).exists()
                            if (hasLocalAvatar) {
                                AsyncImage(
                                    model = java.io.File(avatarPath),
                                    contentDescription = "Avatar",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    Icons.Filled.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = VBookTheme.subTextColor(),
                                )
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(VBookTheme.backgroundColor())
                            .border(1.dp, VBookTheme.cardColor(), CircleShape)
                            .clickable { if (isLoggedIn) showProfileEditDialog = true else showLoginDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = VBookTheme.primaryColor(), modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        displayName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = VBookTheme.textColor()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        userSubtitle,
                        fontSize = 14.sp,
                        color = VBookTheme.subTextColor()
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Buttons: Đăng nhập / Đăng ký / Đăng xuất
            if (isLoggedIn) {
                Button(
                    onClick = {
                        scope.launch {
                            authManager.logout()
                            isLoggedIn = false
                            displayName = "Khách"
                            userSubtitle = "Chưa đăng nhập"
                            Toast.makeText(context, "Đã đăng xuất thành công!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("Đăng xuất", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = { showLoginDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VBookTheme.cardColor()),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("Đăng nhập", color = VBookTheme.textColor(), fontWeight = FontWeight.Medium, fontSize = 15.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Search Bar (mocked search click)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(48.dp)
                    .background(VBookTheme.cardColor(), RoundedCornerShape(24.dp))
                    .clickable { Toast.makeText(context, "Hãy dùng tính năng Tìm kiếm trên Trang chủ hoặc Web", Toast.LENGTH_SHORT).show() }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = VBookTheme.subTextColor(), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Nhập nội dung tìm kiếm", color = VBookTheme.subTextColor(), fontSize = 15.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Settings List Group 1
            VBookSettingsGroup("Ứng dụng") {
                VBookSettingsItem(
                    title = "Cá nhân hoá",
                    subtitle = "Chủ đề tối, màu sắc, phông chữ",
                    icon = Icons.Outlined.Palette,
                    showChevron = true,
                    onClick = { navController.navigate(Routes.SETTINGS_DISPLAY) }
                )
                VBookSettingsDivider()

                VBookSettingsItem(
                    title = "Ngôn ngữ",
                    subtitle = if (appLanguage == "vi") "Tiếng Việt" else "English",
                    icon = Icons.Outlined.Language,
                    showChevron = true,
                    onClick = { showLanguageDialog = true }
                )
                VBookSettingsDivider()
                VBookSettingsItem(
                    title = "Đọc truyện",
                    subtitle = "Hiển thị, dịch, ghi nhớ tiến độ",
                    icon = Icons.Outlined.MenuBook,
                    showChevron = true,
                    onClick = { navController.navigate(Routes.SETTINGS_READER) }
                )
                VBookSettingsDivider()
                VBookSettingsItem(
                    title = "Thông báo",
                    subtitle = "Cập nhật chương mới",
                    icon = Icons.Outlined.Notifications,
                    showChevron = true,
                    onClick = { showNotifDialog = true }
                )
                VBookSettingsDivider()
                VBookSettingsItem(
                    title = "Phần mở rộng",
                    subtitle = "Cài đặt thêm nguồn truyện",
                    icon = Icons.Outlined.Extension,
                    showChevron = true,
                    onClick = { navController.navigate(Routes.EXTENSION_STORE) }
                )
                VBookSettingsItem(
                    title = "Kết nối tải truyện",
                    subtitle = "Giao thức, DNS, giới hạn kết nối",
                    icon = Icons.Outlined.Public,
                    showChevron = true,
                    onClick = { navController.navigate(Routes.SETTINGS_CONNECTION) }
                )
            VBookSettingsDivider()

            // ===== WARP VPN Section =====
            var warpIntervalLabel by remember(warpAutoIntervalSeconds) {
                mutableStateOf(
                    when (warpAutoIntervalSeconds) {
                        0 -> "Tắt tự động"
                        10 -> "10 giây"
                        30 -> "30 giây"
                        60 -> "1 phút"
                        300 -> "5 phút"
                        else -> "${warpAutoIntervalSeconds}s"
                    }
                )
            }
            VBookSettingsItem(
                title = "Cloudflare WARP",
                subtitle = if (warpEnabled) "🟢 Đang bảo vệ — DNS: 1.1.1.1" else "🔴 Tắt — Bật để bypass chặn IP",
                icon = Icons.Outlined.VpnLock,
                content = {
                    VBookSwitch(
                        checked = warpEnabled,
                        onCheckedChange = { checked ->
                            if (checked) {
                                // Request VPN permission
                                val vpnIntent = android.net.VpnService.prepare(context)
                                if (vpnIntent != null) {
                                    vpnPermissionLauncher.launch(vpnIntent)
                                } else {
                                    // Already permitted
                                    val intent = android.content.Intent(context, com.nam.novelreader.vpn.WarpVpnService::class.java)
                                        .setAction(com.nam.novelreader.vpn.WarpVpnService.ACTION_START)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                                        context.startForegroundService(intent)
                                    else context.startService(intent)
                                    warpEnabled = true
                                }
                            } else {
                                val intent = android.content.Intent(context, com.nam.novelreader.vpn.WarpVpnService::class.java)
                                    .setAction(com.nam.novelreader.vpn.WarpVpnService.ACTION_STOP)
                                context.startService(intent)
                                warpEnabled = false
                            }
                        }
                    )
                }
            )
            if (warpEnabled) {
                VBookSettingsDivider()
                VBookSettingsItem(
                    title = "Tự động đổi IP",
                    subtitle = "Reconnect định kỳ để lấy IP mới: $warpIntervalLabel",
                    icon = Icons.Outlined.Autorenew,
                    showChevron = true,
                    onClick = { showWarpIntervalDialog = true }
                )
                VBookSettingsDivider()
                VBookSettingsItem(
                    title = "Đổi IP ngay",
                    subtitle = "Reconnect WARP để lấy IP Cloudflare mới",
                    icon = Icons.Outlined.Refresh,
                    onClick = {
                        val intent = android.content.Intent(context, com.nam.novelreader.vpn.WarpVpnService::class.java)
                            .setAction(com.nam.novelreader.vpn.WarpVpnService.ACTION_RECONNECT)
                        context.startService(intent)
                        Toast.makeText(context, "Đang đổi IP...", Toast.LENGTH_SHORT).show()
                    }
                )
            }
            VBookSettingsDivider()

            var isDevModeEnabled by remember {
                    mutableStateOf(AndroidTestServerService.isServiceRunning)
                }
                LaunchedEffect(Unit) {
                    while (true) {
                        isDevModeEnabled = AndroidTestServerService.isServiceRunning
                        delay(1000)
                    }
                }
                VBookSettingsItem(
                    title = "Chế độ nhà phát triển",
                    icon = Icons.Outlined.BugReport,
                    subtitle = if (isDevModeEnabled) {
                        "Đang chạy tại http://${getLocalIpAddress()}:8080"
                    } else {
                        "Nhận cài đặt & test extension từ máy tính"
                    },
                    content = {
                        VBookSwitch(
                            checked = isDevModeEnabled,
                            onCheckedChange = { checked ->
                                isDevModeEnabled = checked
                                val serviceIntent = Intent(context, AndroidTestServerService::class.java).apply {
                                    action = if (checked) {
                                        AndroidTestServerService.ACTION_START
                                    } else {
                                        AndroidTestServerService.ACTION_STOP
                                    }
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    context.startForegroundService(serviceIntent)
                                } else {
                                    context.startService(serviceIntent)
                                }
                            }
                        )
                    }
                )
                VBookSettingsDivider()
                VBookSettingsItem(
                    title = "Đồng bộ & sao lưu",
                    subtitle = "Xuất, nhập dữ liệu ứng dụng",
                    icon = Icons.Outlined.CloudUpload,
                    showChevron = true,
                    onClick = { showBackupDialog = true }
                )
                VBookSettingsDivider()
                val isAdmin = isLoggedIn && appPrefs.supabaseUserEmail.trim().lowercase(java.util.Locale.ROOT) == "nthanhnam@gmail.com"
                if (isAdmin) {
                    VBookSettingsItem(
                        title = "Cấu hình Supabase Sync",
                        subtitle = if (authManager.isConfigured()) "Đã cấu hình đám mây" else "Thiết lập Cloud Sync cá nhân",
                        icon = Icons.Outlined.CloudSync,
                        showChevron = true,
                        onClick = { showSupabaseConfigDialog = true }
                    )
                    VBookSettingsDivider()
                    VBookSettingsItem(
                        title = "Quản lý thành viên VIP",
                        subtitle = "Danh sách tài khoản, gmail, tên nhân vật và cấp VIP",
                        icon = Icons.Outlined.ManageAccounts,
                        showChevron = true,
                        onClick = { navController.navigate(Routes.ADMIN_VIP_MANAGEMENT) }
                    )
                    VBookSettingsDivider()
                }
                VBookSettingsItem(
                    title = "Trợ giúp",
                    subtitle = "Hướng dẫn sử dụng ứng dụng",
                    icon = Icons.Outlined.HelpOutline,
                    showChevron = true,
                    onClick = { showHelpDialog = true }
                )
                VBookSettingsDivider()
                VBookSettingsItem(
                    title = "Phản hồi",
                    subtitle = "Gửi phản hồi của bạn về ứng dụng",
                    icon = Icons.Outlined.Feedback,
                    showChevron = true,
                    onClick = { showFeedbackDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // === Dialogs Tree ===

    // WARP Auto-Reconnect Interval Dialog
    if (showWarpIntervalDialog) {
        AlertDialog(
            onDismissRequest = { showWarpIntervalDialog = false },
            containerColor = VBookTheme.cardColor(),
            title = { Text("Tự động đổi IP", fontWeight = FontWeight.Bold, color = VBookTheme.textColor()) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Chọn tần suất reconnect WARP để lấy IP mới:", color = VBookTheme.subTextColor(), fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    listOf(0 to "Tắt tự động", 10 to "10 giây", 30 to "30 giây", 60 to "1 phút", 300 to "5 phút").forEach { (seconds, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (warpAutoIntervalSeconds == seconds) VBookTheme.primaryColor().copy(alpha = 0.15f) else Color.Transparent)
                                .clickable {
                                    warpAutoIntervalSeconds = seconds
                                    prefs.edit().putInt("warp_auto_interval", seconds).apply()
                                    showWarpIntervalDialog = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(label, color = VBookTheme.textColor(), fontSize = 15.sp)
                            if (warpAutoIntervalSeconds == seconds) {
                                Icon(Icons.Filled.Check, null, tint = VBookTheme.primaryColor(), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showProfileEditDialog) {
        var tempName by remember { mutableStateOf(displayName) }
        var isUpdating by remember { mutableStateOf(false) }

        LaunchedEffect(showProfileEditDialog) {
            tempName = displayName
        }

        AlertDialog(
            onDismissRequest = { if (!isUpdating) showProfileEditDialog = false },
            containerColor = VBookTheme.cardColor(),
            title = { Text("Chỉnh sửa hồ sơ", color = VBookTheme.textColor(), fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Avatar Selection
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable(enabled = !isUpdating) { imagePickerLauncher.launch("image/*") }
                            .padding(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(96.dp),
                            shape = CircleShape,
                            color = VBookTheme.backgroundColor(),
                            border = androidx.compose.foundation.BorderStroke(1.dp, VBookTheme.primaryColor().copy(alpha = 0.3f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                val hasLocalAvatar = avatarPath.isNotBlank() && java.io.File(avatarPath).exists()
                                if (hasLocalAvatar) {
                                    AsyncImage(
                                        model = java.io.File(avatarPath),
                                        contentDescription = "Large Avatar",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        Icons.Filled.Person,
                                        contentDescription = null,
                                        modifier = Modifier.size(56.dp),
                                        tint = VBookTheme.subTextColor(),
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Nhấp để đổi ảnh đại diện",
                            fontSize = 12.sp,
                            color = VBookTheme.primaryColor(),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Text Field for Name
                    OutlinedTextField(
                        value = tempName,
                        onValueChange = { tempName = it },
                        label = { Text("Tên tài khoản") },
                        singleLine = true,
                        enabled = !isUpdating,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Read-only Account Info
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "Email: ${appPrefs.supabaseUserEmail}",
                            color = VBookTheme.subTextColor(),
                            fontSize = 13.sp
                        )
                        Text(
                            "ID: ${appPrefs.supabaseUserId}",
                            color = VBookTheme.subTextColor().copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showProfileEditDialog = false }, enabled = !isUpdating) {
                        Text("Hủy", color = VBookTheme.subTextColor())
                    }
                    Button(
                        onClick = {
                            if (tempName.isNotBlank()) {
                                isUpdating = true
                                scope.launch {
                                    val result = authManager.updateUserMetadata(tempName.trim(), null)
                                    isUpdating = false
                                    if (result.isSuccess) {
                                        displayName = tempName.trim()
                                        showProfileEditDialog = false
                                        Toast.makeText(context, "Cập nhật tên thành công!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Cập nhật thất bại: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        enabled = tempName.isNotBlank() && !isUpdating,
                        colors = ButtonDefaults.buttonColors(containerColor = VBookTheme.primaryColor())
                    ) {
                        if (isUpdating) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("Lưu", color = Color.White)
                        }
                    }
                }
            }
        )
    }

    if (showLoginDialog) {
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!isLoading) showLoginDialog = false },
            containerColor = VBookTheme.cardColor(),
            title = { Text("Đăng nhập", color = VBookTheme.textColor(), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Mật khẩu") },
                        enabled = !isLoading,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Google Sign-In separator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Divider(modifier = Modifier.weight(1f), color = VBookTheme.subTextColor().copy(alpha = 0.3f))
                        Text("  hoặc  ", color = VBookTheme.subTextColor(), fontSize = 12.sp)
                        Divider(modifier = Modifier.weight(1f), color = VBookTheme.subTextColor().copy(alpha = 0.3f))
                    }

                    // Nút Đăng nhập bằng Google
                    Button(
                        onClick = {
                            if (!authManager.isConfigured()) {
                                android.widget.Toast.makeText(context, "Chưa cấu hình Supabase URL!", android.widget.Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val url = authManager.getGoogleSignInUrl()
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                            context.startActivity(intent)
                            showLoginDialog = false
                        },
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF4285F4)),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(23.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AccountCircle,
                            contentDescription = null,
                            tint = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Đăng nhập bằng Google", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Medium)
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showLoginDialog = false }, enabled = !isLoading) {
                        Text("Hủy", color = VBookTheme.subTextColor())
                    }
                    Button(
                        onClick = {
                            if (!authManager.isConfigured()) {
                                Toast.makeText(context, "Vui lòng cấu hình Supabase URL và Anon Key trước!", Toast.LENGTH_LONG).show()
                                return@Button
                            }
                            if (email.isNotBlank() && password.isNotBlank()) {
                                isLoading = true
                                scope.launch {
                                    val result = authManager.login(email.trim(), password)
                                    isLoading = false
                                    if (result.isSuccess) {
                                        isLoggedIn = true
                                        displayName = prefs.getString("user_display_name", "Khách") ?: "Khách"
                                        userSubtitle = appPrefs.supabaseUserEmail
                                        showLoginDialog = false
                                        Toast.makeText(context, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Đăng nhập thất bại: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        enabled = email.isNotBlank() && password.isNotBlank() && !isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = VBookTheme.primaryColor())
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("Đăng nhập", color = Color.White)
                        }
                    }
                }
            }
        )
    }

    if (showRegisterDialog) {
        var name by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!isLoading) showRegisterDialog = false },
            containerColor = VBookTheme.cardColor(),
            title = { Text("Đăng ký tài khoản", color = VBookTheme.textColor(), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Tên hiển thị") },
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Mật khẩu") },
                        enabled = !isLoading,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showRegisterDialog = false }, enabled = !isLoading) {
                        Text("Hủy", color = VBookTheme.subTextColor())
                    }
                    Button(
                        onClick = {
                            if (!authManager.isConfigured()) {
                                Toast.makeText(context, "Vui lòng cấu hình Supabase URL và Anon Key trước!", Toast.LENGTH_LONG).show()
                                return@Button
                            }
                            if (name.isNotBlank() && email.isNotBlank() && password.isNotBlank()) {
                                isLoading = true
                                scope.launch {
                                    val result = authManager.signUp(email.trim(), password, name.trim())
                                    isLoading = false
                                    if (result.isSuccess) {
                                        if (appPrefs.supabaseIsLoggedIn) {
                                            isLoggedIn = true
                                            displayName = prefs.getString("user_display_name", "Khách") ?: "Khách"
                                            userSubtitle = appPrefs.supabaseUserEmail
                                            Toast.makeText(context, "Đăng ký và đăng nhập thành công!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Đăng ký thành công! Vui lòng xác nhận email (nếu có) và đăng nhập.", Toast.LENGTH_LONG).show()
                                        }
                                        showRegisterDialog = false
                                    } else {
                                        Toast.makeText(context, "Đăng ký thất bại: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        enabled = name.isNotBlank() && email.isNotBlank() && password.isNotBlank() && !isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = VBookTheme.primaryColor())
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("Đăng ký", color = Color.White)
                        }
                    }
                }
            }
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            containerColor = VBookTheme.cardColor(),
            title = { Text("Ngôn ngữ hiển thị", color = VBookTheme.textColor(), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                prefs.edit().putString("app_language", "vi").apply()
                                appLanguage = "vi"
                                showLanguageDialog = false
                                Toast.makeText(context, "Đã đổi sang Tiếng Việt", Toast.LENGTH_SHORT).show()
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = appLanguage == "vi", onClick = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Tiếng Việt", color = VBookTheme.textColor())
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                prefs.edit().putString("app_language", "en").apply()
                                appLanguage = "en"
                                showLanguageDialog = false
                                Toast.makeText(context, "Changed language to English", Toast.LENGTH_SHORT).show()
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = appLanguage == "en", onClick = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("English", color = VBookTheme.textColor())
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text("Đóng", color = VBookTheme.primaryColor())
                }
            }
        )
    }

    if (showNotifDialog) {
        var isNotifEnabled by remember { mutableStateOf(prefs.getBoolean("notif_new_chapter", true)) }
        var isBgScanEnabled by remember { mutableStateOf(prefs.getBoolean("notif_background_scan", true)) }
        var intervalMinutes by remember { mutableIntStateOf(prefs.getInt("notif_check_interval", 60)) }
        
        AlertDialog(
            onDismissRequest = { showNotifDialog = false },
            containerColor = VBookTheme.cardColor(),
            title = { Text("Cài đặt thông báo", color = VBookTheme.textColor(), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Thông báo chương mới", color = VBookTheme.textColor())
                        Switch(
                            checked = isNotifEnabled,
                            onCheckedChange = {
                                isNotifEnabled = it
                                prefs.edit().putBoolean("notif_new_chapter", it).apply()
                            }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Quét cập nhật ngầm", color = VBookTheme.textColor())
                        Switch(
                            checked = isBgScanEnabled,
                            onCheckedChange = {
                                isBgScanEnabled = it
                                prefs.edit().putBoolean("notif_background_scan", it).apply()
                            }
                        )
                    }
                    if (isBgScanEnabled) {
                        Column {
                            Text("Chu kỳ quét tự động: $intervalMinutes phút", color = VBookTheme.textColor())
                            Slider(
                                value = intervalMinutes.toFloat(),
                                onValueChange = {
                                    val mins = it.toInt()
                                    intervalMinutes = mins
                                    prefs.edit().putInt("notif_check_interval", mins).apply()
                                },
                                valueRange = 15f..240f,
                                steps = 15
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showNotifDialog = false }) {
                    Text("Đóng", color = VBookTheme.primaryColor())
                }
            }
        )
    }

    if (showBackupDialog) {
        var isCloudSyncing by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!isCloudSyncing) showBackupDialog = false },
            containerColor = VBookTheme.cardColor(),
            title = { Text("Đồng bộ & sao lưu", color = VBookTheme.textColor(), fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sao lưu cục bộ (Thiết bị)", fontWeight = FontWeight.Bold, color = VBookTheme.textColor(), fontSize = 14.sp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !isCloudSyncing) {
                                showBackupDialog = false
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val entryPoint = dagger.hilt.EntryPoints.get(context.applicationContext, SettingsDatabaseEntryPoint::class.java)
                                        val db = entryPoint.appDatabase()
                                        val cursor = db.openHelper.readableDatabase.query("SELECT * FROM novels WHERE isInLibrary = 1")
                                        val novelsList = mutableListOf<com.nam.novelreader.data.local.entity.NovelEntity>()
                                        
                                        val urlIdx = cursor.getColumnIndex("url")
                                        val titleIdx = cursor.getColumnIndex("title")
                                        val authorIdx = cursor.getColumnIndex("author")
                                        val coverIdx = cursor.getColumnIndex("cover")
                                        val descIdx = cursor.getColumnIndex("description")
                                        val genresIdx = cursor.getColumnIndex("genres")
                                        val statusIdx = cursor.getColumnIndex("status")
                                        val typeIdx = cursor.getColumnIndex("type")
                                        val extIdx = cursor.getColumnIndex("extensionId")
                                        val totalChaptersIdx = cursor.getColumnIndex("totalChapters")
                                        
                                        if (cursor.moveToFirst()) {
                                            do {
                                                novelsList.add(
                                                    com.nam.novelreader.data.local.entity.NovelEntity(
                                                        url = if (urlIdx != -1) cursor.getString(urlIdx) else "",
                                                        title = if (titleIdx != -1) cursor.getString(titleIdx) else "",
                                                        author = if (authorIdx != -1) cursor.getString(authorIdx) else "",
                                                        cover = if (coverIdx != -1) cursor.getString(coverIdx) else "",
                                                        description = if (descIdx != -1) cursor.getString(descIdx) else "",
                                                        genres = if (genresIdx != -1) cursor.getString(genresIdx) else "[]",
                                                        status = if (statusIdx != -1) cursor.getString(statusIdx) else "",
                                                        type = if (typeIdx != -1) cursor.getString(typeIdx) else "novel",
                                                        extensionId = if (extIdx != -1) cursor.getString(extIdx) else "",
                                                        totalChapters = if (totalChaptersIdx != -1) cursor.getInt(totalChaptersIdx) else 0,
                                                        isInLibrary = true
                                                    )
                                                )
                                            } while (cursor.moveToNext())
                                        }
                                        cursor.close()
                                        
                                        if (novelsList.isEmpty()) {
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "Thư viện rỗng, không có gì để sao lưu!", Toast.LENGTH_SHORT).show()
                                            }
                                            return@launch
                                        }
                                        
                                        val jsonStr = exportToJson(novelsList)
                                        val fileName = "vbook_backup_${System.currentTimeMillis()}.json"
                                        
                                        val values = android.content.ContentValues().apply {
                                            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                                            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/json")
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/VBook/Backup")
                                            }
                                        }
                                        
                                        val uri = context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                                        if (uri != null) {
                                            context.contentResolver.openOutputStream(uri)?.use { os ->
                                                java.io.OutputStreamWriter(os, Charsets.UTF_8).use { writer ->
                                                    writer.write(jsonStr)
                                                }
                                            }
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "Sao lưu thành công! Tệp được lưu tại Downloads/VBook/Backup", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Lỗi sao lưu: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.CloudUpload, contentDescription = null, tint = VBookTheme.primaryColor())
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Sao lưu thư viện (Xuất JSON)", color = VBookTheme.textColor(), fontWeight = FontWeight.SemiBold)
                            Text("Lưu danh sách truyện hiện tại thành tệp backup", color = VBookTheme.subTextColor(), fontSize = 12.sp)
                        }
                    }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !isCloudSyncing) {
                                showBackupDialog = false
                                restoreBackupLauncher.launch("*/*")
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = null, tint = VBookTheme.primaryColor())
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Khôi phục thư viện (Nhập JSON)", color = VBookTheme.textColor(), fontWeight = FontWeight.SemiBold)
                            Text("Khôi phục danh sách truyện từ tệp sao lưu", color = VBookTheme.subTextColor(), fontSize = 12.sp)
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp), color = if (VBookTheme.isDarkTheme()) Color(0xFF332A22) else Color(0xFFE8E2DA))
                    Text("Đồng bộ Kho chung (Google Drive)", fontWeight = FontWeight.Bold, color = VBookTheme.textColor(), fontSize = 14.sp)

                    if (isCloudSyncing) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = VBookTheme.primaryColor())
                        }
                    } else {
                        // Lưu lên Kho
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    isCloudSyncing = true
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val entryPoint = dagger.hilt.EntryPoints.get(context.applicationContext, SettingsDatabaseEntryPoint::class.java)
                                            val db = entryPoint.appDatabase()
                                            val cursor = db.openHelper.readableDatabase.query("SELECT * FROM novels WHERE isInLibrary = 1")
                                            val novelsList = mutableListOf<com.nam.novelreader.data.local.entity.NovelEntity>()
                                            
                                            val urlIdx = cursor.getColumnIndex("url")
                                            val titleIdx = cursor.getColumnIndex("title")
                                            val authorIdx = cursor.getColumnIndex("author")
                                            val coverIdx = cursor.getColumnIndex("cover")
                                            val descIdx = cursor.getColumnIndex("description")
                                            val genresIdx = cursor.getColumnIndex("genres")
                                            val statusIdx = cursor.getColumnIndex("status")
                                            val typeIdx = cursor.getColumnIndex("type")
                                            val extIdx = cursor.getColumnIndex("extensionId")
                                            val totalChaptersIdx = cursor.getColumnIndex("totalChapters")
                                            
                                            if (cursor.moveToFirst()) {
                                                do {
                                                    novelsList.add(
                                                        com.nam.novelreader.data.local.entity.NovelEntity(
                                                            url = if (urlIdx != -1) cursor.getString(urlIdx) else "",
                                                            title = if (titleIdx != -1) cursor.getString(titleIdx) else "",
                                                            author = if (authorIdx != -1) cursor.getString(authorIdx) else "",
                                                            cover = if (coverIdx != -1) cursor.getString(coverIdx) else "",
                                                            description = if (descIdx != -1) cursor.getString(descIdx) else "",
                                                            genres = if (genresIdx != -1) cursor.getString(genresIdx) else "[]",
                                                            status = if (statusIdx != -1) cursor.getString(statusIdx) else "",
                                                            type = if (typeIdx != -1) cursor.getString(typeIdx) else "novel",
                                                            extensionId = if (extIdx != -1) cursor.getString(extIdx) else "",
                                                            totalChapters = if (totalChaptersIdx != -1) cursor.getInt(totalChaptersIdx) else 0,
                                                            isInLibrary = true
                                                        )
                                                    )
                                                } while (cursor.moveToNext())
                                            }
                                            cursor.close()
                                            
                                            if (novelsList.isEmpty()) {
                                                withContext(Dispatchers.Main) {
                                                    isCloudSyncing = false
                                                    Toast.makeText(context, "Thư viện rỗng, không có gì để lưu!", Toast.LENGTH_SHORT).show()
                                                }
                                                return@launch
                                            }
                                            
                                            val jsonStr = exportToJson(novelsList)
                                            val googleDriveBackupManager = entryPoint.googleDriveBackupManager()
                                            val result = googleDriveBackupManager.backupLibrary(jsonStr)
                                            
                                            withContext(Dispatchers.Main) {
                                                isCloudSyncing = false
                                                if (result.isSuccess) {
                                                    Toast.makeText(context, "Đã lưu lên Kho thành công!", Toast.LENGTH_LONG).show()
                                                } else {
                                                    Toast.makeText(context, "Lưu lên Kho lỗi: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                isCloudSyncing = false
                                                Toast.makeText(context, "Lỗi lưu kho: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.CloudUpload, contentDescription = null, tint = VBookTheme.primaryColor())
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Lưu lên Kho", color = VBookTheme.textColor(), fontWeight = FontWeight.SemiBold)
                                Text("Lưu danh sách truyện hiện tại lên Kho chung Google Drive", color = VBookTheme.subTextColor(), fontSize = 12.sp)
                            }
                        }

                        // Nhập từ Kho
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    isCloudSyncing = true
                                    scope.launch(Dispatchers.IO) {
                                        val googleDriveBackupManager = entryPoint.googleDriveBackupManager()
                                        val result = googleDriveBackupManager.restoreLibrary()
                                        withContext(Dispatchers.Main) {
                                            if (result.isSuccess) {
                                                val jsonStr = result.getOrNull() ?: ""
                                                scope.launch(Dispatchers.IO) {
                                                    try {
                                                        val parsedNovels = parseBackupJson(jsonStr)
                                                        if (parsedNovels.isEmpty()) throw Exception("Tệp sao lưu rỗng hoặc không đúng cấu trúc")
                                                        
                                                        val db = entryPoint.appDatabase()
                                                        
                                                        parsedNovels.forEach { novel ->
                                                            db.novelDao().insert(novel)
                                                        }
                                                        
                                                        withContext(Dispatchers.Main) {
                                                            isCloudSyncing = false
                                                            showBackupDialog = false
                                                            Toast.makeText(context, "Đồng bộ về thành công ${parsedNovels.size} truyện từ Kho!", Toast.LENGTH_LONG).show()
                                                        }
                                                    } catch (e: Exception) {
                                                        withContext(Dispatchers.Main) {
                                                            isCloudSyncing = false
                                                            Toast.makeText(context, "Lỗi giải mã dữ liệu: ${e.message}", Toast.LENGTH_LONG).show()
                                                        }
                                                    }
                                                }
                                            } else {
                                                isCloudSyncing = false
                                                Toast.makeText(context, "Khôi phục từ Kho lỗi: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.CloudDownload, contentDescription = null, tint = VBookTheme.primaryColor())
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Nhập từ Kho", color = VBookTheme.textColor(), fontWeight = FontWeight.SemiBold)
                                Text("Tải danh sách truyện đã lưu từ Kho chung về thiết bị này", color = VBookTheme.subTextColor(), fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBackupDialog = false }, enabled = !isCloudSyncing) {
                    Text("Đóng", color = VBookTheme.primaryColor())
                }
            }
        )
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            containerColor = VBookTheme.cardColor(),
            title = { Text("Hướng dẫn sử dụng", color = VBookTheme.textColor(), fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("1. Nhập truyện mới:", fontWeight = FontWeight.Bold, color = VBookTheme.textColor())
                    Text("Nhấn nút dấu cộng (+) ở góc phải màn hình Trang chủ để chọn: nhập file chữ offline (.txt/.epub), dán URL truyện, hoặc duyệt nguồn qua trình duyệt tích hợp.", color = VBookTheme.subTextColor(), fontSize = 13.sp)
                    
                    Text("2. Đọc truyện:", fontWeight = FontWeight.Bold, color = VBookTheme.textColor())
                    Text("Khi lướt web truyện trên WebView, nếu gặp truyện phù hợp, nút nổi 'Đọc truyện' sẽ tự động xuất hiện để bạn nạp truyện trực tiếp.", color = VBookTheme.subTextColor(), fontSize = 13.sp)
                    
                    Text("3. Cử chỉ trong trình đọc:", fontWeight = FontWeight.Bold, color = VBookTheme.textColor())
                    Text("Chạm vào cạnh trái/phải để chuyển trang, chạm vào giữa màn hình để mở menu cài đặt đọc nhanh.", color = VBookTheme.subTextColor(), fontSize = 13.sp)
                    
                    Text("4. Đồng bộ & Sao lưu:", fontWeight = FontWeight.Bold, color = VBookTheme.textColor())
                    Text("Chọn mục Đồng bộ & sao lưu để xuất toàn bộ danh sách truyện sang tệp JSON và dễ dàng khôi phục trên thiết bị khác.", color = VBookTheme.subTextColor(), fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("Đóng", color = VBookTheme.primaryColor())
                }
            }
        )
    }

    if (showFeedbackDialog) {
        var feedbackText by remember { mutableStateOf("") }
        var isSending by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!isSending) showFeedbackDialog = false },
            containerColor = VBookTheme.cardColor(),
            title = { Text("Gửi phản hồi đóng góp", color = VBookTheme.textColor(), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Ý kiến đóng góp của bạn giúp ứng dụng ngày càng hoàn thiện hơn.", color = VBookTheme.subTextColor(), fontSize = 13.sp)
                    OutlinedTextField(
                        value = feedbackText,
                        onValueChange = { feedbackText = it },
                        placeholder = { Text("Nhập nội dung phản hồi của bạn ở đây...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        maxLines = 5,
                        enabled = !isSending
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showFeedbackDialog = false }, enabled = !isSending) {
                        Text("Hủy", color = VBookTheme.subTextColor())
                    }
                    Button(
                        onClick = {
                            if (feedbackText.isNotBlank()) {
                                isSending = true
                                scope.launch {
                                    kotlinx.coroutines.delay(1000)
                                    isSending = false
                                    showFeedbackDialog = false
                                    Toast.makeText(context, "Cảm ơn bạn đã đóng góp phản hồi!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = feedbackText.isNotBlank() && !isSending,
                        colors = ButtonDefaults.buttonColors(containerColor = VBookTheme.primaryColor())
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                           Text("Gửi", color = Color.White)
                        }
                    }
                }
            }
        )
    }

    if (showSupabaseConfigDialog) {
        var supabaseUrl by remember { mutableStateOf(appPrefs.supabaseUrl) }
        var supabaseAnonKey by remember { mutableStateOf(appPrefs.supabaseAnonKey) }
        var supabaseServiceRoleKey by remember { mutableStateOf(appPrefs.supabaseServiceRoleKey) }
        AlertDialog(
            onDismissRequest = { showSupabaseConfigDialog = false },
            containerColor = VBookTheme.cardColor(),
            title = { Text("Cấu hình Supabase Sync", color = VBookTheme.textColor(), fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Thiết lập kết nối với cơ sở dữ liệu Supabase cá nhân để đồng bộ tủ sách.", color = VBookTheme.subTextColor(), fontSize = 13.sp)
                    OutlinedTextField(
                        value = supabaseUrl,
                        onValueChange = { supabaseUrl = it },
                        label = { Text("Supabase URL") },
                        placeholder = { Text("https://xxxx.supabase.co") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = supabaseAnonKey,
                        onValueChange = { supabaseAnonKey = it },
                        label = { Text("Anon API Key") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = supabaseServiceRoleKey,
                        onValueChange = { supabaseServiceRoleKey = it },
                        label = { Text("Service Role Key (Dành cho Admin)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Hướng dung thiết lập bảng database:", fontWeight = FontWeight.Bold, color = VBookTheme.textColor(), fontSize = 13.sp)
                    Text("Sao chép đoạn mã SQL bên dưới và chạy trong SQL Editor của Supabase để tạo bảng user_backups và cấu hình bảo mật RLS.", color = VBookTheme.subTextColor(), fontSize = 12.sp)
                    
                    val sqlCode = """
                        -- 1. Tạo và cấu hình bảng user_backups
                        CREATE TABLE IF NOT EXISTS user_backups (
                          user_id uuid REFERENCES auth.users NOT NULL PRIMARY KEY,
                          backup_data text NOT NULL,
                          updated_at timestamp with time zone DEFAULT timezone('utc'::text, now()) NOT NULL
                        );
                        
                        -- Thêm cột nếu đã có bảng cũ nhưng thiếu
                        ALTER TABLE user_backups ADD COLUMN IF NOT EXISTS backup_data text;
                        ALTER TABLE user_backups ADD COLUMN IF NOT EXISTS updated_at timestamp with time zone DEFAULT timezone('utc'::text, now());
                        
                        ALTER TABLE user_backups ENABLE ROW LEVEL SECURITY;
                        
                        DROP POLICY IF EXISTS "Users can insert backup" ON user_backups;
                        CREATE POLICY "Users can insert backup" ON user_backups FOR INSERT WITH CHECK (auth.uid() = user_id);
                        
                        DROP POLICY IF EXISTS "Users can update backup" ON user_backups;
                        CREATE POLICY "Users can update backup" ON user_backups FOR UPDATE USING (auth.uid() = user_id);
                        
                        DROP POLICY IF EXISTS "Users can select backup" ON user_backups;
                        CREATE POLICY "Users can select backup" ON user_backups FOR SELECT USING (auth.uid() = user_id);

                        -- 2. Tạo và cấu hình bảng chat_groups
                        CREATE TABLE IF NOT EXISTS chat_groups (
                          id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                          name text NOT NULL,
                          avatar_url text,
                          is_public boolean NOT NULL DEFAULT true,
                          created_by uuid REFERENCES auth.users NOT NULL,
                          created_at timestamp with time zone DEFAULT timezone('utc'::text, now()) NOT NULL
                        );
                        
                        -- Thêm cột phòng trường hợp bảng cũ đã có nhưng thiếu
                        ALTER TABLE chat_groups ADD COLUMN IF NOT EXISTS name text;
                        ALTER TABLE chat_groups ADD COLUMN IF NOT EXISTS avatar_url text;
                        ALTER TABLE chat_groups ADD COLUMN IF NOT EXISTS is_public boolean DEFAULT true;
                        ALTER TABLE chat_groups ADD COLUMN IF NOT EXISTS created_by uuid REFERENCES auth.users;
                        ALTER TABLE chat_groups ADD COLUMN IF NOT EXISTS created_at timestamp with time zone DEFAULT timezone('utc'::text, now());
                        
                        ALTER TABLE chat_groups ENABLE ROW LEVEL SECURITY;
                        
                        DROP POLICY IF EXISTS "Anyone can view public groups" ON chat_groups;
                        CREATE POLICY "Anyone can view public groups" ON chat_groups FOR SELECT USING (is_public = true);
                        
                        DROP POLICY IF EXISTS "Creators can update groups" ON chat_groups;
                        CREATE POLICY "Creators can update groups" ON chat_groups FOR UPDATE USING (auth.uid() = created_by);
                        
                        DROP POLICY IF EXISTS "Creators can delete groups" ON chat_groups;
                        CREATE POLICY "Creators can delete groups" ON chat_groups FOR DELETE USING (auth.uid() = created_by);
                        
                        DROP POLICY IF EXISTS "Admins can insert groups" ON chat_groups;
                        CREATE POLICY "Admins can insert groups" ON chat_groups FOR INSERT WITH CHECK (
                          lower(auth.jwt() ->> 'email') = 'nthanhnam@gmail.com'
                        );

                        -- 3. Tạo và cấu hình bảng chat_messages
                        CREATE TABLE IF NOT EXISTS chat_messages (
                          id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                          group_id uuid REFERENCES chat_groups ON DELETE CASCADE NOT NULL,
                          sender_id uuid REFERENCES auth.users NOT NULL,
                          sender_name text NOT NULL,
                          sender_email text NOT NULL,
                          content text NOT NULL,
                          created_at timestamp with time zone DEFAULT timezone('utc'::text, now()) NOT NULL
                        );
                        
                        -- Thêm cột phòng trường hợp bảng cũ đã có nhưng thiếu
                        ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS group_id uuid REFERENCES chat_groups ON DELETE CASCADE;
                        ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS sender_id uuid REFERENCES auth.users;
                        ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS sender_name text;
                        ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS sender_email text;
                        ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS content text;
                        ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS created_at timestamp with time zone DEFAULT timezone('utc'::text, now());
                        
                        ALTER TABLE chat_messages ENABLE ROW LEVEL SECURITY;
                        
                        DROP POLICY IF EXISTS "Anyone can view public group messages" ON chat_messages;
                        CREATE POLICY "Anyone can view public group messages" ON chat_messages FOR SELECT USING (
                          EXISTS (SELECT 1 FROM chat_groups WHERE chat_groups.id = group_id AND chat_groups.is_public = true)
                        );
                        
                        DROP POLICY IF EXISTS "Authenticated users can insert messages" ON chat_messages;
                        CREATE POLICY "Authenticated users can insert messages" ON chat_messages FOR INSERT WITH CHECK (
                          auth.uid() = sender_id
                        );

                        -- 4. Tạo View đếm tổng số lượng thành viên (Cộng đồng)
                        CREATE OR REPLACE VIEW public.user_count AS
                        SELECT count(*) as count FROM auth.users;
                        
                        GRANT SELECT ON public.user_count TO anon, authenticated;

                        -- 5. Cấp quyền quản lý profiles cho admin
                        ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
                        
                        DROP POLICY IF EXISTS "Admins can select all profiles" ON public.profiles;
                        CREATE POLICY "Admins can select all profiles" ON public.profiles 
                        FOR SELECT USING (
                          lower(auth.jwt() ->> 'email') = 'nthanhnam@gmail.com'
                        );
                        
                        DROP POLICY IF EXISTS "Admins can update all profiles" ON public.profiles;
                        CREATE POLICY "Admins can update all profiles" ON public.profiles 
                        FOR UPDATE USING (
                          lower(auth.jwt() ->> 'email') = 'nthanhnam@gmail.com'
                        );
                    """.trimIndent()
                    
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Supabase SQL", sqlCode)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Đã sao chép SQL vào bộ nhớ tạm!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = VBookTheme.backgroundColor()),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = VBookTheme.textColor(), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sao chép SQL", color = VBookTheme.textColor(), fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showSupabaseConfigDialog = false }) {
                        Text("Đóng", color = VBookTheme.subTextColor())
                    }
                    Button(
                        onClick = {
                            appPrefs.supabaseUrl = supabaseUrl.trim()
                            appPrefs.supabaseAnonKey = supabaseAnonKey.trim()
                            appPrefs.supabaseServiceRoleKey = supabaseServiceRoleKey.trim()
                            showSupabaseConfigDialog = false
                            Toast.makeText(context, "Đã lưu cấu hình Supabase!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = VBookTheme.primaryColor())
                    ) {
                        Text("Lưu", color = Color.White)
                    }
                }
            }
        )
    }
}
