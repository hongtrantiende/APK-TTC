package com.nam.novelreader.feature.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavHostController
import com.nam.novelreader.data.preferences.AppPreferences
import com.nam.novelreader.feature.components.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class ConnectionSettingsViewModel @Inject constructor(
    val appPrefs: AppPreferences
) : ViewModel()

/**
 * SettingsConnectionScreen — cài đặt kết nối mạng chi tiết.
 * Thiết kế giao diện dạng các thẻ bo tròn đẹp mắt, đồng nhất phong cách.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsConnectionScreen(
    navController: NavHostController,
    viewModel: ConnectionSettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val prefs = viewModel.appPrefs

    // State holders
    var protocol by remember { mutableStateOf(prefs.connectionProtocol) }
    var threads by remember { mutableIntStateOf(prefs.connectionThreads) }
    var delay by remember { mutableIntStateOf(prefs.connectionDelay) }
    var retry by remember { mutableIntStateOf(prefs.connectionRetry) }
    var timeout by remember { mutableIntStateOf(prefs.connectionTimeout) }
    var dns by remember { mutableStateOf(prefs.connectionDns) }
    var ignoreSsl by remember { mutableStateOf(prefs.connectionIgnoreSsl) }
    var userAgent by remember { mutableStateOf(prefs.connectionUserAgent) }
    var performance by remember { mutableStateOf(prefs.connectionPerformance) }

    // Proxy States
    var proxyEnabled by remember { mutableStateOf(prefs.proxyEnabled) }
    var proxyBypassThreadLimit by remember { mutableStateOf(prefs.proxyBypassThreadLimit) }
    var proxyHost by remember { mutableStateOf(prefs.proxyHost) }
    var proxyPort by remember { mutableStateOf(prefs.proxyPort.toString()) }
    var proxyRotateApi by remember { mutableStateOf(prefs.proxyRotateApi) }

    // Dialog states
    var showProtocolDialog by remember { mutableStateOf(false) }
    var showThreadsDialog by remember { mutableStateOf(false) }
    var showDelayDialog by remember { mutableStateOf(false) }
    var showRetryDialog by remember { mutableStateOf(false) }
    var showTimeoutDialog by remember { mutableStateOf(false) }
    var showDnsDialog by remember { mutableStateOf(false) }
    var showUserAgentDialog by remember { mutableStateOf(false) }
    var showPerformanceDialog by remember { mutableStateOf(false) }
    var showDomainDialog by remember { mutableStateOf(false) }
    var showProxyDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = VBookTheme.backgroundColor(),
        topBar = {
            TopAppBar(
                title = { Text("Kết nối tải truyện", fontWeight = FontWeight.Bold, color = VBookTheme.textColor()) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = VBookTheme.textColor())
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // === GROUP 1: KẾT NỐI & HIỆU NĂNG ===
            VBookSettingsGroup(title = "Kết nối & hiệu năng") {
                // Threads
                VBookSettingsItem(
                    title = "Kết nối song song",
                    subtitle = "$threads luồng",
                    onClick = { showThreadsDialog = true }
                )
                VBookSettingsDivider()

                // Delay
                val delayLabel = if (delay >= 1000) "${delay / 1000}s" else "${delay}ms"
                VBookSettingsItem(
                    title = "Giãn cách kết nối",
                    subtitle = delayLabel,
                    onClick = { showDelayDialog = true }
                )
                VBookSettingsDivider()

                // Retry
                VBookSettingsItem(
                    title = "Số lần thử lại",
                    subtitle = "$retry lần",
                    onClick = { showRetryDialog = true }
                )
                VBookSettingsDivider()

                // Timeout
                VBookSettingsItem(
                    title = "Thời gian chờ kết nối",
                    subtitle = "$timeout giây",
                    onClick = { showTimeoutDialog = true }
                )
                VBookSettingsDivider()

                // Performance mode
                val perfLabel = when (performance) {
                    "balanced" -> "Cân bằng — Mặc định"
                    "performance" -> "Hiệu suất cao — Tốn pin hơn"
                    "battery" -> "Tiết kiệm pin — Chậm hơn"
                    else -> performance
                }
                VBookSettingsItem(
                    title = "Chế độ hiệu suất",
                    subtitle = perfLabel,
                    onClick = { showPerformanceDialog = true }
                )
            }

            // === GROUP 2: GIAO THỨC & ĐỊNH TUYẾN ===
            VBookSettingsGroup(title = "Giao thức & định tuyến") {
                // DNS over HTTPS
                val dnsLabel = when (dns) {
                    "system" -> "Không"
                    "google" -> "Google DNS"
                    "cloudflare" -> "Cloudflare DNS"
                    "adguard" -> "AdGuard DNS"
                    "quad9" -> "Quad9"
                    "alidns" -> "AliDNS"
                    "dnspod" -> "DNSPod"
                    "360" -> "360"
                    "quad101" -> "Quad101"
                    "mullvad" -> "Mullvad"
                    "controld" -> "Control D"
                    "njalla" -> "Njalla"
                    "shecan" -> "Shecan"
                    else -> dns
                }
                VBookSettingsItem(
                    title = "DNS over HTTPS",
                    subtitle = dnsLabel,
                    onClick = { showDnsDialog = true }
                )
                VBookSettingsDivider()

                // Cronet toggle
                VBookSettingsItem(
                    title = "Kết nối bằng Cronet",
                    subtitle = "Chuyển qua dùng Cronet để hỗ trợ một số trang dùng giao thức QUIC và HTTP/3",
                    onClick = {
                        val newVal = if (protocol == "cronet") "http2" else "cronet"
                        protocol = newVal
                        prefs.connectionProtocol = newVal
                    }
                ) {
                    VBookSwitch(
                        checked = protocol == "cronet",
                        onCheckedChange = {
                            val newVal = if (it) "cronet" else "http2"
                            protocol = newVal
                            prefs.connectionProtocol = newVal
                        }
                    )
                }
                VBookSettingsDivider()

                // SSL ignore
                VBookSettingsItem(
                    title = "Bỏ qua chứng chỉ SSL",
                    subtitle = "Bỏ qua các lỗi chứng chỉ bảo mật của website truyện.",
                    onClick = {
                        ignoreSsl = !ignoreSsl
                        prefs.connectionIgnoreSsl = ignoreSsl
                    }
                ) {
                    VBookSwitch(
                        checked = ignoreSsl,
                        onCheckedChange = {
                            ignoreSsl = it
                            prefs.connectionIgnoreSsl = it
                        }
                    )
                }
                VBookSettingsDivider()

                // Custom User Agent
                VBookSettingsItem(
                    title = "User Agent tùy chỉnh",
                    subtitle = if (userAgent.isEmpty()) "Sử dụng User Agent mặc định hệ thống" else userAgent,
                    onClick = { showUserAgentDialog = true }
                )
                VBookSettingsDivider()

                // Domain mapping
                VBookSettingsItem(
                    title = "Chuyển tiếp tên miền",
                    subtitle = "Cấu hình thay đổi tên miền kết nối ban đầu để tránh bị nhà mạng chặn.",
                    onClick = { showDomainDialog = true }
                )
            }

            // === GROUP 3: PROXY & TẢI SIÊU TỐC ===
            VBookSettingsGroup(title = "Proxy cá nhân & Tải siêu tốc") {
                VBookSettingsItem(
                    title = "Sử dụng HTTP Proxy",
                    subtitle = "Vượt tường lửa bằng Proxy. Yêu cầu cấu hình thêm IP và Port.",
                    onClick = { showProxyDialog = true }
                ) {
                    VBookSwitch(
                        checked = proxyEnabled,
                        onCheckedChange = {
                            proxyEnabled = it
                            prefs.proxyEnabled = it
                            if (it && (proxyHost.isBlank() || proxyPort.isBlank() || proxyPort == "0")) {
                                showProxyDialog = true
                            }
                        }
                    )
                }

                if (proxyEnabled) {
                    VBookSettingsDivider()
                    VBookSettingsItem(
                        title = "Cấu hình IP và Port Proxy",
                        subtitle = if (proxyHost.isNotBlank() && proxyPort != "0") "$proxyHost:$proxyPort" else "Chưa thiết lập",
                        onClick = { showProxyDialog = true }
                    )
                    VBookSettingsDivider()
                    VBookSettingsItem(
                        title = "Mở khóa giới hạn luồng tải",
                        subtitle = "Bỏ qua các giới hạn an toàn của tiện ích. Kết hợp Proxy sẽ tải siêu tốc mà không lo bị ban.",
                        onClick = {
                            proxyBypassThreadLimit = !proxyBypassThreadLimit
                            prefs.proxyBypassThreadLimit = proxyBypassThreadLimit
                        }
                    ) {
                        VBookSwitch(
                            checked = proxyBypassThreadLimit,
                            onCheckedChange = {
                                proxyBypassThreadLimit = it
                                prefs.proxyBypassThreadLimit = it
                            }
                        )
                    }
                }
            }

            // === GROUP 4: DỌN DẸP & TỐI ƯU ===
            VBookSettingsGroup(title = "Dọn dẹp & Tối ưu") {
                VBookSettingsItem(
                    title = "Xóa Cookie & Cache kết nối",
                    subtitle = "Xóa cookies và bộ nhớ đệm WebView để vượt tường lửa hoặc fix lỗi tải chương.",
                    onClick = {
                        try {
                            android.webkit.CookieManager.getInstance().removeAllCookies(null)
                            android.webkit.WebStorage.getInstance().deleteAllData()
                            Toast.makeText(context, "Đã xóa toàn bộ cookie và bộ nhớ đệm thành công!", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Lỗi khi dọn dẹp: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // ========== DIALOGS ==========

    if (showThreadsDialog) {
        RadioDialog(
            title = "Kết nối song song",
            options = listOf(1, 2, 3, 5, 10, 15, 20).map { it.toString() to "$it luồng" },
            selected = threads.toString(),
            onSelect = { threads = it.toInt(); prefs.connectionThreads = it.toInt() },
            onDismiss = { showThreadsDialog = false }
        )
    }

    if (showDelayDialog) {
        RadioDialog(
            title = "Giãn cách kết nối",
            options = listOf(
                "0" to "0ms (Không giãn cách)",
                "10" to "10ms",
                "50" to "50ms",
                "100" to "100ms",
                "500" to "500ms",
                "1000" to "1s (1000ms)",
                "2000" to "2s (2000ms)"
            ),
            selected = delay.toString(),
            onSelect = { delay = it.toInt(); prefs.connectionDelay = it.toInt() },
            onDismiss = { showDelayDialog = false }
        )
    }

    if (showRetryDialog) {
        RadioDialog(
            title = "Số lần thử lại",
            options = (0..5).map { it.toString() to "$it lần" },
            selected = retry.toString(),
            onSelect = { retry = it.toInt(); prefs.connectionRetry = it.toInt() },
            onDismiss = { showRetryDialog = false }
        )
    }

    if (showTimeoutDialog) {
        RadioDialog(
            title = "Thời gian chờ kết nối",
            options = listOf(
                "10" to "10 giây",
                "15" to "15 giây",
                "30" to "30 giây (Mặc định)",
                "45" to "45 giây",
                "60" to "60 giây"
            ),
            selected = timeout.toString(),
            onSelect = { timeout = it.toInt(); prefs.connectionTimeout = it.toInt() },
            onDismiss = { showTimeoutDialog = false }
        )
    }

    if (showDnsDialog) {
        RadioDialog(
            title = "DNS over HTTPS",
            options = listOf(
                "system" to "Không",
                "google" to "Google DNS",
                "cloudflare" to "Cloudflare DNS",
                "adguard" to "AdGuard DNS",
                "quad9" to "Quad9",
                "alidns" to "AliDNS",
                "dnspod" to "DNSPod",
                "360" to "360",
                "quad101" to "Quad101",
                "mullvad" to "Mullvad",
                "controld" to "Control D",
                "njalla" to "Njalla",
                "shecan" to "Shecan"
            ),
            selected = dns,
            onSelect = { dns = it; prefs.connectionDns = it },
            onDismiss = { showDnsDialog = false }
        )
    }

    if (showPerformanceDialog) {
        RadioDialog(
            title = "Chế độ hiệu suất",
            options = listOf(
                "balanced" to "Cân bằng — Mặc định",
                "performance" to "Hiệu suất cao — Tốn pin hơn",
                "battery" to "Tiết kiệm pin — Chậm hơn"
            ),
            selected = performance,
            onSelect = { performance = it; prefs.connectionPerformance = it },
            onDismiss = { showPerformanceDialog = false }
        )
    }

    if (showUserAgentDialog) {
        var tempUserAgent by remember { mutableStateOf(userAgent) }
        AlertDialog(
            onDismissRequest = { showUserAgentDialog = false },
            containerColor = VBookTheme.cardColor(),
            title = { Text("User Agent tùy chỉnh", fontWeight = FontWeight.Bold, color = VBookTheme.textColor()) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Nhập User Agent mong muốn để giả lập trình duyệt và tránh bị Cloudflare chặn kết nối.", fontSize = 12.sp, color = VBookTheme.subTextColor())
                    OutlinedTextField(
                        value = tempUserAgent,
                        onValueChange = { tempUserAgent = it },
                        placeholder = { Text("Mozilla/5.0 ...") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VBookTheme.primaryColor(),
                            focusedLabelColor = VBookTheme.primaryColor(),
                            cursorColor = VBookTheme.primaryColor(),
                            focusedTextColor = VBookTheme.textColor(),
                            unfocusedTextColor = VBookTheme.textColor()
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = tempUserAgent.trim()
                        userAgent = trimmed
                        prefs.connectionUserAgent = trimmed
                        showUserAgentDialog = false
                        Toast.makeText(context, "Đã lưu User Agent tùy chỉnh!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VBookTheme.primaryColor())
                ) {
                    Text("Lưu", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUserAgentDialog = false }) {
                    Text("Hủy", color = Color.Gray)
                }
            }
        )
    }

    if (showDomainDialog) {
        // Parse from JSON string
        var mappingList by remember {
            val list = mutableStateListOf<Pair<String, String>>()
            val jsonStr = prefs.connectionUrlOverride
            if (jsonStr.isNotEmpty()) {
                try {
                    val json = org.json.JSONObject(jsonStr)
                    val keys = json.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        list.add(key to json.getString(key))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            mutableStateOf(list)
        }

        var newSourceDomain by remember { mutableStateOf("") }
        var newTargetDomain by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showDomainDialog = false },
            containerColor = VBookTheme.cardColor(),
            title = {
                Text(
                    "Chuyển tiếp tên miền",
                    fontWeight = FontWeight.Bold,
                    color = VBookTheme.textColor()
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Cấu hình thay thế tên miền gốc sang tên miền đích để tránh bị nhà mạng chặn.",
                        fontSize = 12.sp,
                        color = VBookTheme.subTextColor(),
                        lineHeight = 16.sp
                    )

                    // Current mappings list
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                    ) {
                        Text(
                            "Danh sách chuyển tiếp (${mappingList.size}):",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = VBookTheme.textColor()
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        if (mappingList.isEmpty()) {
                            Text(
                                "Chưa có cấu hình chuyển tiếp nào.",
                                fontSize = 13.sp,
                                color = VBookTheme.subTextColor(),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 160.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                mappingList.forEachIndexed { index, pair ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                pair.first,
                                                fontSize = 14.sp,
                                                color = VBookTheme.textColor(),
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                "↓ ${pair.second}",
                                                fontSize = 12.sp,
                                                color = VBookTheme.primaryColor()
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                mappingList.removeAt(index)
                                            }
                                        ) {
                                            Icon(
                                                Icons.Filled.Delete,
                                                contentDescription = "Xoá",
                                                tint = Color.Red.copy(alpha = 0.7f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    if (index < mappingList.lastIndex) {
                                        HorizontalDivider(
                                            color = if (VBookTheme.isDarkTheme()) Color(0xFF332A22) else Color(0xFFE8E2DA)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        color = if (VBookTheme.isDarkTheme()) Color(0xFF332A22) else Color(0xFFE8E2DA)
                    )

                    // Add new mapping form
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Thêm chuyển tiếp mới:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = VBookTheme.textColor()
                        )
                        OutlinedTextField(
                            value = newSourceDomain,
                            onValueChange = { newSourceDomain = it.trim() },
                            placeholder = { Text("truyen.com") },
                            label = { Text("Tên miền gốc (Original)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = VBookTheme.primaryColor(),
                                focusedLabelColor = VBookTheme.primaryColor(),
                                cursorColor = VBookTheme.primaryColor(),
                                focusedTextColor = VBookTheme.textColor(),
                                unfocusedTextColor = VBookTheme.textColor()
                            )
                        )
                        OutlinedTextField(
                            value = newTargetDomain,
                            onValueChange = { newTargetDomain = it.trim() },
                            placeholder = { Text("truyen-mirror.net") },
                            label = { Text("Tên miền mới (Mirror)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = VBookTheme.primaryColor(),
                                focusedLabelColor = VBookTheme.primaryColor(),
                                cursorColor = VBookTheme.primaryColor(),
                                focusedTextColor = VBookTheme.textColor(),
                                unfocusedTextColor = VBookTheme.textColor()
                            )
                        )

                        Button(
                            onClick = {
                                if (newSourceDomain.isNotEmpty() && newTargetDomain.isNotEmpty()) {
                                    val existsIdx = mappingList.indexOfFirst { it.first == newSourceDomain }
                                    if (existsIdx != -1) {
                                        mappingList[existsIdx] = newSourceDomain to newTargetDomain
                                    } else {
                                        mappingList.add(newSourceDomain to newTargetDomain)
                                    }
                                    newSourceDomain = ""
                                    newTargetDomain = ""
                                }
                            },
                            modifier = Modifier.align(Alignment.End),
                            colors = ButtonDefaults.buttonColors(containerColor = VBookTheme.primaryColor()),
                            enabled = newSourceDomain.isNotEmpty() && newTargetDomain.isNotEmpty()
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Thêm", color = Color.White)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val json = org.json.JSONObject()
                        try {
                            mappingList.forEach { (src, dest) ->
                                json.put(src, dest)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        prefs.connectionUrlOverride = json.toString()
                        showDomainDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VBookTheme.primaryColor())
                ) {
                    Text("Lưu cấu hình", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDomainDialog = false }) {
                    Text("Hủy", color = Color.Gray)
                }
            }
        )
    }

    if (showProxyDialog) {
        var tempHost by remember { mutableStateOf(proxyHost) }
        var tempPort by remember { mutableStateOf(if (proxyPort == "0") "" else proxyPort) }
        var tempRotateApi by remember { mutableStateOf(proxyRotateApi) }
        
        val coroutineScope = rememberCoroutineScope()
        var isTesting by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showProxyDialog = false },
            containerColor = VBookTheme.cardColor(),
            title = { Text("Cấu hình HTTP Proxy", fontWeight = FontWeight.Bold, color = VBookTheme.textColor()) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Lưu ý: Bạn phải whitelist IP của mạng hiện tại vào hệ thống Proxy để được phép kết nối.", fontSize = 12.sp, color = VBookTheme.subTextColor())
                    
                    OutlinedTextField(
                        value = tempHost,
                        onValueChange = { tempHost = it.trim() },
                        placeholder = { Text("Ví dụ: 160.250.166.32") },
                        label = { Text("IP Proxy (Host)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VBookTheme.primaryColor(),
                            focusedLabelColor = VBookTheme.primaryColor(),
                            cursorColor = VBookTheme.primaryColor(),
                            focusedTextColor = VBookTheme.textColor(),
                            unfocusedTextColor = VBookTheme.textColor()
                        )
                    )
                    OutlinedTextField(
                        value = tempPort,
                        onValueChange = { tempPort = it.trim().filter { c -> c.isDigit() } },
                        placeholder = { Text("Ví dụ: 10022") },
                        label = { Text("Cổng (Port)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VBookTheme.primaryColor(),
                            focusedLabelColor = VBookTheme.primaryColor(),
                            cursorColor = VBookTheme.primaryColor(),
                            focusedTextColor = VBookTheme.textColor(),
                            unfocusedTextColor = VBookTheme.textColor()
                        )
                    )
                    OutlinedTextField(
                        value = tempRotateApi,
                        onValueChange = { tempRotateApi = it.trim() },
                        placeholder = { Text("https://api.proxy.com/rotate?key=...") },
                        label = { Text("Link API xoay IP (nếu có)") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VBookTheme.primaryColor(),
                            focusedLabelColor = VBookTheme.primaryColor(),
                            cursorColor = VBookTheme.primaryColor(),
                            focusedTextColor = VBookTheme.textColor(),
                            unfocusedTextColor = VBookTheme.textColor()
                        )
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (tempHost.isBlank() || tempPort.isBlank()) {
                                Toast.makeText(context, "Vui lòng nhập Host và Port", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isTesting = true
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    val proxy = java.net.Proxy(java.net.Proxy.Type.HTTP, java.net.InetSocketAddress(tempHost, tempPort.toInt()))
                                    val client = okhttp3.OkHttpClient.Builder()
                                        .proxy(proxy)
                                        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                                        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                                        .build()
                                    
                                    val request = okhttp3.Request.Builder().url("https://api.ipify.org?format=json").build()
                                    val response = client.newCall(request).execute()
                                    
                                    if (response.isSuccessful) {
                                        val responseBody = response.body?.string() ?: ""
                                        val ip = org.json.JSONObject(responseBody).optString("ip")
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            Toast.makeText(context, "Proxy hoạt động tốt! IP của bạn: $ip", Toast.LENGTH_LONG).show()
                                        }
                                    } else {
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            Toast.makeText(context, "Proxy bị từ chối kết nối (Mã lỗi: ${response.code})", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                    response.close()
                                } catch (e: Exception) {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        Toast.makeText(context, "Lỗi kết nối Proxy: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                } finally {
                                    isTesting = false
                                }
                            }
                        },
                        enabled = !isTesting,
                        colors = ButtonDefaults.buttonColors(containerColor = VBookTheme.primaryColor())
                    ) {
                        Text(if (isTesting) "Đang test..." else "Test Proxy", color = Color.White)
                    }

                    Button(
                        onClick = {
                            proxyHost = tempHost
                            proxyPort = tempPort
                            proxyRotateApi = tempRotateApi
                            prefs.proxyHost = tempHost
                            prefs.proxyPort = tempPort.toIntOrNull() ?: 0
                            prefs.proxyRotateApi = tempRotateApi
                            showProxyDialog = false
                            Toast.makeText(context, "Đã lưu cấu hình Proxy. Vui lòng khởi động lại kết nối nếu cần.", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = VBookTheme.primaryColor())
                    ) {
                        Text("Lưu", color = Color.White)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showProxyDialog = false }) {
                    Text("Hủy", color = Color.Gray)
                }
            }
        )
    }
}

@Composable
fun RadioDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VBookTheme.cardColor(),
        title = { Text(title, fontWeight = FontWeight.Bold, color = VBookTheme.textColor()) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(value)
                                onDismiss()
                            }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected == value,
                            onClick = {
                                onSelect(value)
                                onDismiss()
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = VBookTheme.primaryColor(),
                                unselectedColor = VBookTheme.subTextColor()
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = label, fontSize = 15.sp, color = VBookTheme.textColor())
                    }
                }
            }
        },
        confirmButton = {}
    )
}
