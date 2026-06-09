package com.nam.novelreader.feature.extensions

import android.content.Context
import java.io.File
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.nam.novelreader.data.preferences.AppPreferences
import com.nam.novelreader.data.repository.NovelRepository
import com.nam.novelreader.extension.model.ScriptType
import com.nam.novelreader.extension.model.ExtensionResult
import coil.compose.AsyncImage
import com.nam.novelreader.navigation.Routes
import com.nam.novelreader.data.local.dao.ExtensionDao
import com.nam.novelreader.data.local.entity.ExtensionEntity
import com.nam.novelreader.data.local.entity.cleanName
import com.nam.novelreader.extension.loader.ExtensionLoader
import com.nam.novelreader.extension.model.LoadedExtension
import com.nam.novelreader.extension.model.ExtensionSetting
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource

@HiltViewModel
class ExtensionSettingsViewModel @Inject constructor(
    private val extensionDao: ExtensionDao,
    private val extensionLoader: ExtensionLoader,
    val appPreferences: AppPreferences,
    private val repository: NovelRepository,
) : ViewModel() {
    private val _extension = MutableStateFlow<ExtensionEntity?>(null)
    val extension: StateFlow<ExtensionEntity?> = _extension

    private val _loadedExtension = MutableStateFlow<LoadedExtension?>(null)
    val loadedExtension: StateFlow<LoadedExtension?> = _loadedExtension

    private val _connectionTestResult = MutableStateFlow<String?>(null)
    val connectionTestResult: StateFlow<String?> = _connectionTestResult

    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection

    fun loadExtension(id: String) {
        viewModelScope.launch {
            _extension.value = extensionDao.getExtensionById(id)
            _loadedExtension.value = extensionLoader.loadExtension(id)
        }
    }

    fun uninstallExtension(id: String, onCompleted: () -> Unit) {
        viewModelScope.launch {
            try {
                extensionLoader.uninstallExtension(id)
                onCompleted()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun testConnection(extensionId: String) {
        viewModelScope.launch {
            _isTestingConnection.value = true
            _connectionTestResult.value = "Đang kết nối..."
            try {
                val result = repository.executeExtension(extensionId, ScriptType.HOME, "1")
                when (result) {
                    is ExtensionResult.Success -> {
                        if (result.data.length > 50) {
                            _connectionTestResult.value = "SUCCESS: Kết nối tốt! (Tải được ${result.data.length} bytes dữ liệu)"
                        } else {
                            _connectionTestResult.value = "WARNING: Kết nối thành công nhưng dữ liệu trả về ngắn hoặc rỗng."
                        }
                    }
                    is ExtensionResult.Error -> {
                        _connectionTestResult.value = "ERROR: Lỗi từ tiện ích: ${result.message}"
                    }
                }
            } catch (e: Exception) {
                val msg = e.message ?: "Lỗi không xác định"
                if (msg.contains("403")) {
                    _connectionTestResult.value = "ERROR: Lỗi 403 (Bị chặn bởi Cloudflare hoặc cần cập nhật Cookie)."
                } else if (msg.contains("404")) {
                    _connectionTestResult.value = "ERROR: Lỗi 404 (Không tìm thấy trang - nguồn có thể đã đổi tên miền hoặc bị sập)."
                } else if (msg.contains("timeout") || msg.contains("time out")) {
                    _connectionTestResult.value = "ERROR: Kết nối quá hạn (Timeout). Kiểm tra mạng hoặc bật VPN."
                } else {
                    _connectionTestResult.value = "ERROR: Lỗi mạng: $msg"
                }
            } finally {
                _isTestingConnection.value = false
            }
        }
    }

    fun clearConnectionTestResult() {
        _connectionTestResult.value = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionSettingsScreen(
    extensionId: String,
    navController: NavHostController,
    viewModel: ExtensionSettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val extension by viewModel.extension.collectAsStateWithLifecycle()
    val loadedExtension by viewModel.loadedExtension.collectAsStateWithLifecycle()
    val connectionTestResult by viewModel.connectionTestResult.collectAsStateWithLifecycle()
    val isTestingConnection by viewModel.isTestingConnection.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // Preferences
    val prefs = remember { context.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE) }
    
    // Settings States
    var refreshTrigger by remember { mutableStateOf(0) }
    var showTextSettingDialog by remember { mutableStateOf(false) }
    var showSelectSettingDialog by remember { mutableStateOf(false) }
    var activeSettingForDialog by remember { mutableStateOf<ExtensionSetting?>(null) }
    var pinnedExts by remember(refreshTrigger) {
        mutableStateOf(prefs.getStringSet("pinned_extensions", emptySet()) ?: emptySet())
    }
    val isPinned = pinnedExts.contains(extensionId)

    val parallelConnections = remember(extensionId, refreshTrigger) {
        viewModel.appPreferences.getExtParallelConnections(extensionId)
    }
    val connectionInterval = remember(extensionId, refreshTrigger) {
        viewModel.appPreferences.getExtConnectionInterval(extensionId)
    }
    val connectionTimeout = remember(extensionId, refreshTrigger) {
        viewModel.appPreferences.getExtConnectionTimeout(extensionId)
    }
    var incognitoMode by remember(extensionId, refreshTrigger) {
        mutableStateOf(prefs.getBoolean("ext_incognito_mode_$extensionId", false))
    }
    val customCookie = remember(extensionId, refreshTrigger) {
        prefs.getString("ext_cookies_$extensionId", "") ?: ""
    }
    var isErrorChecked by remember(extensionId, refreshTrigger) {
        mutableStateOf(prefs.getBoolean("ext_error_$extensionId", false))
    }

    // Storage Map
    val storagePrefs = remember(extensionId) {
        context.getSharedPreferences("ext_storage_$extensionId", Context.MODE_PRIVATE)
    }
    val storageEntries = remember(extensionId, refreshTrigger) {
        storagePrefs.all.mapValues { it.value?.toString() ?: "" }
    }

    // Dialog States
    var showCookieDialog by remember { mutableStateOf(false) }
    var showStorageDialog by remember { mutableStateOf(false) }
    var showParallelDialog by remember { mutableStateOf(false) }
    var showIntervalDialog by remember { mutableStateOf(false) }
    var showTimeoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(extensionId) {
        if (extensionId.isNotBlank()) {
            viewModel.loadExtension(extensionId)
        }
    }

    // Theme Colors
    val cardBg = Color(0xFF1F1A17) // VBook dark card color
    val dividerColor = Color(0xFF332A22)
    val accentGold = Color(0xFFD4A574)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Thông tin chi tiết", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        val ext = extension
        if (ext == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = accentGold)
            }
        } else {
            val cleanDomain = ext.source.replace("https://", "").replace("http://", "").trimEnd('/')
            val flag = when (ext.locale.lowercase()) {
                "vi_vn", "vi" -> "🇻🇳"
                "zh_cn", "zh" -> "🇨🇳"
                "en_us", "en" -> "🇺🇸"
                else -> "🌐"
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // === Extension Card ===
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(cardBg)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Icon
                        val iconFile = remember(ext.iconPath, ext.localPath) {
                            val path = ext.iconPath ?: "${ext.localPath}/icon.png"
                            File(path)
                        }
                        if (iconFile.exists()) {
                            AsyncImage(
                                model = iconFile,
                                contentDescription = ext.cleanName,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(16.dp)),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(Color(0xFF3A302A), RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = ext.cleanName.firstOrNull()?.toString()?.uppercase() ?: "?",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentGold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Title
                        Text(
                            text = ext.cleanName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Badges Row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Flag badge
                            BadgeContainer(text = "$flag TRUYỆN CHỮ", containerColor = Color(0xFF382D24), contentColor = accentGold)
                            Image(
                                painter = painterResource(id = com.nam.novelreader.R.mipmap.ic_launcher),
                                contentDescription = "App Icon",
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            BadgeContainer(text = ext.version.toString(), containerColor = Color(0xFF2C2C2C), contentColor = Color.LightGray)
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // URL source link
                        Text(
                            text = ext.source,
                            style = MaterialTheme.typography.bodySmall,
                            color = accentGold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Toggle Báo lỗi / Tiện ích hỏng
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val newValue = !isErrorChecked
                                    isErrorChecked = newValue
                                    prefs.edit().putBoolean("ext_error_$extensionId", newValue).apply()
                                    refreshTrigger++
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Checkbox(
                                checked = isErrorChecked,
                                onCheckedChange = { newValue ->
                                    isErrorChecked = newValue
                                    prefs.edit().putBoolean("ext_error_$extensionId", newValue).apply()
                                    refreshTrigger++
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color(0xFFE57373),
                                    uncheckedColor = accentGold,
                                    checkmarkColor = Color.White
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Báo lỗi / Tiện ích hỏng (⚠️)",
                                color = if (isErrorChecked) Color(0xFFE57373) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Action Buttons Row (Trang nguồn, Gỡ, Ghim)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Trang nguồn (WebView in-app)
                            Button(
                                onClick = {
                                    navController.navigate(Routes.webview(ext.source, extensionId))
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E2721), contentColor = accentGold),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Trang nguồn", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }

                            // Gỡ (Uninstall)
                            Button(
                                onClick = {
                                    viewModel.uninstallExtension(ext.id) {
                                        Toast.makeText(context, "Đã gỡ tiện ích mở rộng", Toast.LENGTH_SHORT).show()
                                        navController.popBackStack()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E2721), contentColor = Color(0xFFE57373)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Gỡ", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }

                            // Ghim / Gỡ ghim
                            Button(
                                onClick = {
                                    val updated = pinnedExts.toMutableSet()
                                    if (isPinned) {
                                        updated.remove(extensionId)
                                    } else {
                                        updated.add(extensionId)
                                    }
                                    prefs.edit().putStringSet("pinned_extensions", updated).apply()
                                    refreshTrigger++
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E2721), contentColor = accentGold),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = if (isPinned) "Gỡ ghim" else "Ghim",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Nút Kiểm tra kết nối
                        Button(
                            onClick = { viewModel.testConnection(extensionId) },
                            enabled = !isTestingConnection,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isTestingConnection) Color.Gray else accentGold,
                                contentColor = Color(0xFF1F1A17)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isTestingConnection) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.DarkGray,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text("Kiểm tra kết nối", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        // Hiển thị kết quả chẩn đoán
                        connectionTestResult?.let { result ->
                            Spacer(modifier = Modifier.height(12.dp))
                            val isSuccess = result.startsWith("SUCCESS")
                            val isWarning = result.startsWith("WARNING")
                            val isError = result.startsWith("ERROR")
                            
                            val displayMsg = when {
                                isSuccess -> result.substringAfter("SUCCESS: ")
                                isWarning -> result.substringAfter("WARNING: ")
                                isError -> result.substringAfter("ERROR: ")
                                else -> result
                            }
                            
                            val textColor = when {
                                isSuccess -> Color(0xFF8BC34A)
                                isWarning -> Color(0xFFFFB74D)
                                isError -> Color(0xFFE57373)
                                else -> MaterialTheme.colorScheme.onBackground
                            }
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(textColor.copy(alpha = 0.1f))
                                    .border(1.dp, textColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = displayMsg,
                                    color = textColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // === Cookie Card ===
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(cardBg)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Cookie",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = accentGold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = customCookie.ifBlank { "Chưa có lịch sử cookie" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (customCookie.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onBackground,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { showCookieDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = accentGold),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = androidx.compose.ui.graphics.SolidColor(accentGold)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("+ Thêm")
                        }
                    }
                }

                // === Local Storage Card ===
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(cardBg)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Bộ nhớ cục bộ",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = accentGold
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        if (storageEntries.isEmpty()) {
                            Text(
                                text = "Không có dữ liệu cục bộ",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        } else {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                storageEntries.forEach { (key, value) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF29231E))
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = key, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = accentGold)
                                            Text(text = value, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground)
                                        }
                                        IconButton(
                                            onClick = {
                                                storagePrefs.edit().remove(key).apply()
                                                refreshTrigger++
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Delete,
                                                contentDescription = "Xóa key",
                                                tint = Color(0xFFE57373),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { showStorageDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = accentGold),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = androidx.compose.ui.graphics.SolidColor(accentGold)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("+ Thêm")
                        }
                    }
                }

                // === Dynamic Extension Settings Card ===
                val pluginJson = loadedExtension?.pluginJson
                val dynamicSettings = pluginJson?.settings ?: emptyList()
                if (dynamicSettings.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(cardBg)
                        ) {
                            PaddingWrapper {
                                Text(
                                    text = "Cài đặt",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = accentGold
                                )
                            }

                            dynamicSettings.forEachIndexed { index, setting ->
                                if (index > 0) {
                                    HorizontalDivider(color = dividerColor, thickness = 1.dp)
                                }

                                val key = setting.key
                                val title = setting.title
                                val type = setting.type.lowercase()
                                val defaultVal = setting.default

                                when (type) {
                                    "boolean", "switch" -> {
                                        var value by remember(extensionId, key, refreshTrigger) {
                                            mutableStateOf(prefs.getBoolean("ext_config_${extensionId}_$key", defaultVal.toBoolean()))
                                        }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = title,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onBackground
                                                )
                                                if (!setting.desc.isNullOrBlank()) {
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = setting.desc,
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                    )
                                                }
                                            }
                                            Switch(
                                                checked = value,
                                                onCheckedChange = { checked ->
                                                    value = checked
                                                    prefs.edit().putBoolean("ext_config_${extensionId}_$key", checked).apply()
                                                    refreshTrigger++
                                                },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = MaterialTheme.colorScheme.background,
                                                    checkedTrackColor = accentGold,
                                                    uncheckedThumbColor = Color.Gray,
                                                    uncheckedTrackColor = Color.DarkGray
                                                )
                                            )
                                        }
                                    }
                                    "select", "list" -> {
                                        val currentValue = remember(extensionId, key, refreshTrigger) {
                                            prefs.getString("ext_config_${extensionId}_$key", defaultVal) ?: defaultVal
                                        }
                                        SettingRow(
                                            title = title,
                                            subtitle = currentValue.ifBlank { "Chưa chọn" },
                                            onClick = {
                                                activeSettingForDialog = setting
                                                showSelectSettingDialog = true
                                            }
                                        )
                                    }
                                    else -> { // text, password, etc.
                                        val currentValue = remember(extensionId, key, refreshTrigger) {
                                            prefs.getString("ext_config_${extensionId}_$key", "") ?: ""
                                        }
                                        val isPassword = type == "password"
                                        val displayValue = if (currentValue.isBlank()) {
                                            "<Chưa thiết lập>"
                                        } else if (isPassword) {
                                            "•".repeat(currentValue.length.coerceAtMost(12))
                                        } else {
                                            currentValue
                                        }
                                        SettingRow(
                                            title = title,
                                            subtitle = displayValue,
                                            onClick = {
                                                activeSettingForDialog = setting
                                                showTextSettingDialog = true
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // === Connection Card ===
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(cardBg)
                    ) {
                        PaddingWrapper {
                            Text(
                                text = "Kết nối",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = accentGold
                            )
                        }

                        // Parallel Connections row
                        SettingRow(
                            title = "Kết nối song song",
                            subtitle = "$parallelConnections luồng",
                            onClick = { showParallelDialog = true }
                        )

                        HorizontalDivider(color = dividerColor, thickness = 1.dp)

                        // Connection Interval row
                        val intervalText = if (connectionInterval >= 1000) "${connectionInterval / 1000}s" else "${connectionInterval}ms"
                        SettingRow(
                            title = "Giãn cách kết nối",
                            subtitle = intervalText,
                            onClick = { showIntervalDialog = true }
                        )

                        HorizontalDivider(color = dividerColor, thickness = 1.dp)

                        // Connection Timeout row
                        SettingRow(
                            title = "Thời gian chờ (Timeout)",
                            subtitle = "$connectionTimeout giây",
                            onClick = { showTimeoutDialog = true }
                        )

                        HorizontalDivider(color = dividerColor, thickness = 1.dp)

                        // Incognito Mode row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Chế độ ẩn danh",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Không lưu và không hiển thị lịch sử xem của bạn.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                            Switch(
                                checked = incognitoMode,
                                onCheckedChange = { checked ->
                                    incognitoMode = checked
                                    prefs.edit().putBoolean("ext_incognito_mode_$extensionId", checked).apply()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.background,
                                    checkedTrackColor = accentGold,
                                    uncheckedThumbColor = Color.Gray,
                                    uncheckedTrackColor = Color.DarkGray
                                )
                            )
                        }

                        val hasCustomConfig = remember(extensionId, refreshTrigger) {
                            prefs.contains("ext_parallel_connections_$extensionId") || 
                            prefs.contains("ext_connection_interval_$extensionId") ||
                            prefs.contains("ext_connection_timeout_$extensionId")
                        }
                        if (hasCustomConfig) {
                            HorizontalDivider(color = dividerColor, thickness = 1.dp)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        prefs.edit()
                                            .remove("ext_parallel_connections_$extensionId")
                                            .remove("ext_connection_interval_$extensionId")
                                            .remove("ext_connection_timeout_$extensionId")
                                            .apply()
                                        refreshTrigger++
                                        Toast.makeText(context, "Đã khôi phục cài đặt mặc định tốt nhất", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Khôi phục cài đặt mặc định",
                                    color = accentGold,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // === Cookie Dialog ===
    if (showCookieDialog) {
        var cookieInput by remember { mutableStateOf(customCookie) }
        AlertDialog(
            onDismissRequest = { showCookieDialog = false },
            title = { Text("Thêm Cookie", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = cookieInput,
                    onValueChange = { cookieInput = it },
                    label = { Text("Nhập chuỗi cookie") },
                    placeholder = { Text("name=value; name2=value2") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    prefs.edit().putString("ext_cookies_$extensionId", cookieInput.trim()).apply()
                    showCookieDialog = false
                    refreshTrigger++
                }) {
                    Text("OK", color = accentGold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCookieDialog = false }) {
                    Text("Hủy", color = Color.Gray)
                }
            }
        )
    }

    // === Storage Dialog ===
    if (showStorageDialog) {
        var keyInput by remember { mutableStateOf("") }
        var valInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showStorageDialog = false },
            title = { Text("Thêm dữ liệu", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = { Text("Key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = valInput,
                        onValueChange = { valInput = it },
                        label = { Text("Value") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (keyInput.isNotBlank()) {
                            storagePrefs.edit().putString(keyInput.trim(), valInput.trim()).apply()
                            showStorageDialog = false
                            refreshTrigger++
                        }
                    },
                    enabled = keyInput.isNotBlank()
                ) {
                    Text("OK", color = accentGold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showStorageDialog = false }) {
                    Text("Hủy", color = Color.Gray)
                }
            }
        )
    }

    // === Parallel Threads Dialog ===
    if (showParallelDialog) {
        val options = listOf(1, 2, 3, 5, 10, 15, 20)
        AlertDialog(
            onDismissRequest = { showParallelDialog = false },
            title = { Text("Số luồng kết nối", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    options.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    prefs.edit().putInt("ext_parallel_connections_$extensionId", option).apply()
                                    showParallelDialog = false
                                    refreshTrigger++
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = parallelConnections == option,
                                onClick = {
                                    prefs.edit().putInt("ext_parallel_connections_$extensionId", option).apply()
                                    showParallelDialog = false
                                    refreshTrigger++
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = accentGold)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "$option luồng", fontSize = 16.sp)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // === Connection Interval Dialog ===
    if (showIntervalDialog) {
        val options = listOf(
            0 to "0ms",
            10 to "10ms",
            50 to "50ms",
            100 to "100ms",
            500 to "500ms",
            1000 to "1s (1000ms)",
            2000 to "2s (2000ms)"
        )
        AlertDialog(
            onDismissRequest = { showIntervalDialog = false },
            title = { Text("Giãn cách kết nối", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    options.forEach { (optionVal, optionLabel) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    prefs.edit().putInt("ext_connection_interval_$extensionId", optionVal).apply()
                                    showIntervalDialog = false
                                    refreshTrigger++
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = connectionInterval == optionVal,
                                onClick = {
                                    prefs.edit().putInt("ext_connection_interval_$extensionId", optionVal).apply()
                                    showIntervalDialog = false
                                    refreshTrigger++
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = accentGold)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = optionLabel, fontSize = 16.sp)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // === Connection Timeout Dialog ===
    if (showTimeoutDialog) {
        val options = listOf(3, 5, 10, 15, 30, 45, 60)
        AlertDialog(
            onDismissRequest = { showTimeoutDialog = false },
            title = { Text("Thời gian chờ (Timeout)", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    options.forEach { optionVal ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.appPreferences.setExtConnectionTimeout(extensionId, optionVal)
                                    showTimeoutDialog = false
                                    refreshTrigger++
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = connectionTimeout == optionVal,
                                onClick = {
                                    viewModel.appPreferences.setExtConnectionTimeout(extensionId, optionVal)
                                    showTimeoutDialog = false
                                    refreshTrigger++
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = accentGold)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "$optionVal giây", fontSize = 16.sp)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // === Text/Password Dynamic Setting Dialog ===
    if (showTextSettingDialog) {
        val setting = activeSettingForDialog
        if (setting != null) {
            val key = setting.key
            val title = setting.title
            val isPassword = setting.type.lowercase() == "password"
            val currentValue = remember(extensionId, key) {
                prefs.getString("ext_config_${extensionId}_$key", "") ?: ""
            }
            var textInput by remember { mutableStateOf(currentValue) }
            AlertDialog(
                onDismissRequest = { showTextSettingDialog = false },
                title = { Text(title, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!setting.desc.isNullOrBlank()) {
                            Text(text = setting.desc, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            label = { Text("Giá trị") },
                            singleLine = true,
                            visualTransformation = if (isPassword) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                            keyboardOptions = if (isPassword) KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password) else KeyboardOptions.Default,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        prefs.edit().putString("ext_config_${extensionId}_$key", textInput.trim()).apply()
                        showTextSettingDialog = false
                        refreshTrigger++
                    }) {
                        Text("OK", color = accentGold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTextSettingDialog = false }) {
                        Text("Hủy", color = Color.Gray)
                    }
                }
            )
        }
    }

    // === Select Dynamic Setting Dialog ===
    if (showSelectSettingDialog) {
        val setting = activeSettingForDialog
        if (setting != null) {
            val key = setting.key
            val title = setting.title
            val choices = setting.choices ?: emptyList()
            val defaultVal = setting.default
            val currentValue = remember(extensionId, key) {
                prefs.getString("ext_config_${extensionId}_$key", defaultVal) ?: defaultVal
            }
            AlertDialog(
                onDismissRequest = { showSelectSettingDialog = false },
                title = { Text(title, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        if (!setting.desc.isNullOrBlank()) {
                            Text(
                                text = setting.desc,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        choices.forEach { option ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        prefs.edit().putString("ext_config_${extensionId}_$key", option).apply()
                                        showSelectSettingDialog = false
                                        refreshTrigger++
                                    }
                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = currentValue == option,
                                    onClick = {
                                        prefs.edit().putString("ext_config_${extensionId}_$key", option).apply()
                                        showSelectSettingDialog = false
                                        refreshTrigger++
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = accentGold)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = option, fontSize = 16.sp)
                            }
                        }
                    }
                },
                confirmButton = {}
            )
        }
    }
}

@Composable
fun BadgeContainer(text: String, containerColor: Color, contentColor: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(containerColor)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text = text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = contentColor)
    }
}

@Composable
fun PaddingWrapper(content: @Composable () -> Unit) {
    Box(modifier = Modifier.padding(16.dp)) {
        content()
    }
}

@Composable
fun SettingRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = subtitle,
            fontSize = 14.sp,
            color = Color(0xFFD4A574) // accentGold
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(18.dp)
        )
    }
}
