package com.nam.novelreader.feature.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.nam.novelreader.data.preferences.AppPreferences
import com.nam.novelreader.feature.components.*
import com.nam.novelreader.navigation.Routes
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class ReaderSettingsViewModel @Inject constructor(
    val appPrefs: AppPreferences
) : ViewModel()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsReaderScreen(
    navController: NavHostController,
    viewModel: ReaderSettingsViewModel = hiltViewModel()
) {
    val prefs = viewModel.appPrefs
    val context = LocalContext.current
    val rawPrefs = remember { context.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE) }

    // local binding states
    var autoOpenLastRead by remember { mutableStateOf(prefs.readerAutoOpenLastRead) }
    var saveHistory by remember { mutableStateOf(prefs.readerSaveHistory) }
    var screenOn by remember { mutableStateOf(prefs.readerScreenOn) }
    var showProgress by remember { mutableStateOf(prefs.readerShowProgress) }

    var scrollMode by remember { mutableStateOf(prefs.readerScrollMode) }
    var turnPageAnim by remember { mutableStateOf(prefs.readerTurnPageAnim) }
    var volumeTurnPage by remember { mutableStateOf(prefs.readerVolumeTurnPage) }
    
    var contextMenu by remember { mutableStateOf(prefs.readerContextMenu) }
    var contextOptions by remember {
        mutableStateOf(rawPrefs.getString("reader_context_menu_options", "Dịch nhanh,Tra từ điển,Sao chép,Chia sẻ") ?: "Dịch nhanh,Tra từ điển,Sao chép,Chia sẻ")
    }

    var dictionaryTool by remember {
        mutableStateOf(rawPrefs.getString("reader_dictionary_tool", "Vietphrase") ?: "Vietphrase")
    }
    var dictionaryCustomUrl by remember {
        mutableStateOf(rawPrefs.getString("reader_dictionary_custom_url", "") ?: "")
    }

    var ttsEngine by remember {
        mutableStateOf(rawPrefs.getString("reader_tts_engine", "system") ?: "system")
    }
    var ttsSpeed by remember {
        mutableFloatStateOf(rawPrefs.getFloat("reader_tts_speed", 1.0f))
    }
    var ttsPitch by remember {
        mutableFloatStateOf(rawPrefs.getFloat("reader_tts_pitch", 1.0f))
    }

    DisposableEffect(Unit) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == "reader_tts_engine") {
                ttsEngine = sharedPreferences.getString("reader_tts_engine", "system") ?: "system"
            } else if (key == "reader_tts_speed") {
                ttsSpeed = sharedPreferences.getFloat("reader_tts_speed", 1.0f)
            } else if (key == "reader_tts_pitch") {
                ttsPitch = sharedPreferences.getFloat("reader_tts_pitch", 1.0f)
            }
        }
        rawPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            rawPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    var readingReminder by remember {
        mutableIntStateOf(rawPrefs.getInt("reader_reading_reminder", 0))
    }

    var downloadFormat by remember {
        mutableStateOf(rawPrefs.getString("reader_download_format", "EPUB") ?: "EPUB")
    }

    var autoTranslateQt by remember { mutableStateOf(prefs.readerAutoTranslateQt) }

    // Dialog state controllers
    var showPageTurnDialog by remember { mutableStateOf(false) }
    var showContextMenuDialog by remember { mutableStateOf(false) }
    var showDictionaryDialog by remember { mutableStateOf(false) }
    var showTtsDialog by remember { mutableStateOf(false) }
    var showReminderDialog by remember { mutableStateOf(false) }
    var showDownloadFormatDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = VBookTheme.backgroundColor(),
        topBar = {
            TopAppBar(
                title = { Text("Đọc truyện", fontWeight = FontWeight.Bold, color = VBookTheme.textColor()) },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        colors = IconButtonDefaults.iconButtonColors(contentColor = VBookTheme.textColor())
                    ) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
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
            Spacer(modifier = Modifier.height(8.dp))

            // Hành vi khi đọc
            VBookSettingsGroup("Hành vi khi đọc") {
                VBookSettingsItem(
                    title = "Tự động mở truyện vừa đọc",
                    subtitle = "Mở lại truyện đang đọc dở từ lần tắt ứng dụng gần nhất",
                    content = {
                        VBookSwitch(
                            checked = autoOpenLastRead,
                            onCheckedChange = { autoOpenLastRead = it; prefs.readerAutoOpenLastRead = it }
                        )
                    }
                )
                VBookSettingsDivider()
                VBookSettingsItem(
                    title = "Lưu lịch sử đọc",
                    subtitle = "Lưu và hiển thị lịch sử xem của bạn.",
                    content = {
                        VBookSwitch(
                            checked = saveHistory,
                            onCheckedChange = { saveHistory = it; prefs.readerSaveHistory = it }
                        )
                    }
                )
                VBookSettingsDivider()
                VBookSettingsItem(
                    title = "Luôn bật màn hình khi đọc",
                    subtitle = "Giữ màn hình luôn bật lâu hơn so với cài đặt mặc định của hệ thống",
                    content = {
                        VBookSwitch(
                            checked = screenOn,
                            onCheckedChange = { screenOn = it; prefs.readerScreenOn = it }
                        )
                    }
                )
                VBookSettingsDivider()
                VBookSettingsItem(
                    title = "Hiển thị điều khiển khi bắt đầu đọc",
                    subtitle = "Bật cài đặt này để hiển thị thanh điều khiển khi bắt đầu đọc",
                    content = {
                        VBookSwitch(
                            checked = showProgress,
                            onCheckedChange = { showProgress = it; prefs.readerShowProgress = it }
                        )
                    }
                )
            }

            // Điều khiển & thao tác
            VBookSettingsGroup("Điều khiển & thao tác") {
                val modeText = if (scrollMode) "Cuộn liên tục" else "Lật trang (${if (turnPageAnim == "none") "Không hiệu ứng" else if (turnPageAnim == "slide") "Trượt ngang" else "Lật sách"})"
                val volumeText = if (volumeTurnPage) "Phím âm lượng: Bật" else "Phím âm lượng: Tắt"
                VBookSettingsItem(
                    title = "Điều khiển chuyển trang",
                    subtitle = "$modeText • $volumeText",
                    showChevron = true,
                    onClick = { showPageTurnDialog = true }
                )
                VBookSettingsDivider()
                VBookSettingsItem(
                    title = "Menu ngữ cảnh",
                    subtitle = if (contextMenu) "Đang bật (${contextOptions})" else "Đang tắt",
                    showChevron = true,
                    onClick = { showContextMenuDialog = true }
                )
                VBookSettingsDivider()
                VBookSettingsItem(
                    title = "Công cụ tra cứu từ",
                    subtitle = "Đang dùng: $dictionaryTool",
                    showChevron = true,
                    onClick = { showDictionaryDialog = true }
                )
            }

            // Hỗ trợ đọc
            VBookSettingsGroup("Hỗ trợ đọc") {
                val displayEngine = if (ttsEngine == "system") "Hệ thống" else ttsEngine
                VBookSettingsItem(
                    title = "Đọc văn bản (TTS)",
                    subtitle = "Tốc độ: ${ttsSpeed}x • Tông: ${ttsPitch}x • Nguồn: $displayEngine",
                    showChevron = true,
                    onClick = { navController.navigate(Routes.TTS_SETTINGS) }
                )
                VBookSettingsDivider()
                VBookSettingsItem(
                    title = "Tự động dịch truyện",
                    subtitle = "Tự động dịch ngôn ngữ bằng các công cụ dịch có sẵn",
                    showChevron = true,
                    onClick = { navController.navigate(Routes.TRANSLATION_SETTINGS) }
                )
                VBookSettingsDivider()
                VBookSettingsItem(
                    title = "Tự động dịch Quick Translate",
                    subtitle = "Tự động dịch nội dung chương truyện sang tiếng Việt bằng từ điển offline",
                    content = {
                        VBookSwitch(
                            checked = autoTranslateQt,
                            onCheckedChange = { 
                                autoTranslateQt = it
                                prefs.readerAutoTranslateQt = it 
                            }
                        )
                    }
                )
                VBookSettingsDivider()
                val reminderText = when (readingReminder) {
                    0 -> "Không nhắc"
                    30 -> "Sau 30 phút"
                    60 -> "Sau 60 phút"
                    120 -> "Sau 120 phút"
                    180 -> "Sau 180 phút"
                    999 -> "Tự động khoá ứng dụng"
                    else -> "Không nhắc"
                }
                VBookSettingsItem(
                    title = "Nhắc nhở thời gian đọc",
                    subtitle = reminderText,
                    showChevron = true,
                    onClick = { showReminderDialog = true }
                )
            }

            // Nội dung & tải
            VBookSettingsGroup("Nội dung & tải") {
                VBookSettingsItem(
                    title = "Định dạng tải về",
                    subtitle = downloadFormat,
                    showChevron = true,
                    onClick = { showDownloadFormatDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // === DIALOGS IMPLEMENTATIONS ===

    // 1. Page Turn Dialog
    if (showPageTurnDialog) {
        var tempScrollMode by remember { mutableStateOf(scrollMode) }
        var tempTurnPageAnim by remember { mutableStateOf(turnPageAnim) }
        var tempVolumeTurnPage by remember { mutableStateOf(volumeTurnPage) }

        AlertDialog(
            onDismissRequest = { showPageTurnDialog = false },
            containerColor = VBookTheme.cardColor(),
            title = { Text("Điều khiển chuyển trang", color = VBookTheme.textColor(), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Scroll Mode Selector
                    Column {
                        Text("Chế độ đọc:", fontSize = 12.sp, color = VBookTheme.subTextColor(), fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { tempScrollMode = true }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = tempScrollMode, onClick = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Cuộn liên tục (Dọc)", color = VBookTheme.textColor())
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { tempScrollMode = false }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = !tempScrollMode, onClick = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Lật trang (Ngang)", color = VBookTheme.textColor())
                        }
                    }

                    // Turn Page Anim (only if Page mode)
                    if (!tempScrollMode) {
                        Column {
                            Text("Hiệu ứng lật trang:", fontSize = 12.sp, color = VBookTheme.subTextColor(), fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(6.dp))
                            listOf("none" to "Không hiệu ứng", "slide" to "Trượt ngang", "curl" to "Lật sách cuộn góc").forEach { (animVal, animLabel) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable { tempTurnPageAnim = animVal }.padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = tempTurnPageAnim == animVal, onClick = null)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(animLabel, color = VBookTheme.textColor())
                                }
                            }
                        }
                    }

                    // Volume keys toggle
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { tempVolumeTurnPage = !tempVolumeTurnPage }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Phím âm lượng", color = VBookTheme.textColor(), fontWeight = FontWeight.Medium)
                            Text("Nhấn phím tăng/giảm âm lượng để lật trang", color = VBookTheme.subTextColor(), fontSize = 12.sp)
                        }
                        Switch(checked = tempVolumeTurnPage, onCheckedChange = { tempVolumeTurnPage = it })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scrollMode = tempScrollMode
                        prefs.readerScrollMode = tempScrollMode
                        turnPageAnim = tempTurnPageAnim
                        prefs.readerTurnPageAnim = tempTurnPageAnim
                        volumeTurnPage = tempVolumeTurnPage
                        prefs.readerVolumeTurnPage = tempVolumeTurnPage
                        showPageTurnDialog = false
                        Toast.makeText(context, "Đã lưu cài đặt chuyển trang!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VBookTheme.primaryColor())
                ) {
                    Text("Lưu", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPageTurnDialog = false }) {
                    Text("Hủy", color = Color.Gray)
                }
            }
        )
    }

    // 2. Context Menu Dialog
    if (showContextMenuDialog) {
        var tempContextMenu by remember { mutableStateOf(contextMenu) }
        val allOptions = listOf("Dịch nhanh", "Tra từ điển", "Sao chép", "Tìm kiếm", "Chia sẻ")
        val activeOptions = remember {
            mutableStateListOf<String>().apply {
                addAll(contextOptions.split(",").filter { it.isNotBlank() })
            }
        }

        AlertDialog(
            onDismissRequest = { showContextMenuDialog = false },
            containerColor = VBookTheme.cardColor(),
            title = { Text("Menu ngữ cảnh", color = VBookTheme.textColor(), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Bật menu khi bôi đen text", color = VBookTheme.textColor(), fontWeight = FontWeight.Medium)
                        Switch(checked = tempContextMenu, onCheckedChange = { tempContextMenu = it })
                    }

                    if (tempContextMenu) {
                        Text("Chọn các chức năng hiển thị:", fontSize = 12.sp, color = VBookTheme.subTextColor(), fontWeight = FontWeight.Medium)
                        allOptions.forEach { opt ->
                            val isChecked = activeOptions.contains(opt)
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    if (isChecked) activeOptions.remove(opt) else activeOptions.add(opt)
                                }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        if (checked == true) activeOptions.add(opt) else activeOptions.remove(opt)
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(opt, color = VBookTheme.textColor())
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        contextMenu = tempContextMenu
                        prefs.readerContextMenu = tempContextMenu
                        val optionsStr = activeOptions.joinToString(",")
                        contextOptions = optionsStr
                        rawPrefs.edit().putString("reader_context_menu_options", optionsStr).apply()
                        showContextMenuDialog = false
                        Toast.makeText(context, "Cấu hình menu ngữ cảnh đã lưu!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VBookTheme.primaryColor())
                ) {
                    Text("Lưu", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showContextMenuDialog = false }) {
                    Text("Hủy", color = Color.Gray)
                }
            }
        )
    }

    // 3. Dictionary Tool Dialog
    if (showDictionaryDialog) {
        val options = listOf("Vietphrase", "Hán Việt", "Google Translate", "Soha Dict", "Tự định nghĩa URL")
        var tempDict by remember { mutableStateOf(dictionaryTool) }
        var tempUrl by remember { mutableStateOf(dictionaryCustomUrl) }

        AlertDialog(
            onDismissRequest = { showDictionaryDialog = false },
            containerColor = VBookTheme.cardColor(),
            title = { Text("Công cụ tra cứu từ", color = VBookTheme.textColor(), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { opt ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { tempDict = opt }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = tempDict == opt, onClick = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(opt, color = VBookTheme.textColor())
                        }
                    }

                    if (tempDict == "Tự định nghĩa URL") {
                        OutlinedTextField(
                            value = tempUrl,
                            onValueChange = { tempUrl = it },
                            placeholder = { Text("https://example.com/dict?word={word}") },
                            label = { Text("Địa chỉ URL tra từ") },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        dictionaryTool = tempDict
                        dictionaryCustomUrl = tempUrl
                        rawPrefs.edit()
                            .putString("reader_dictionary_tool", tempDict)
                            .putString("reader_dictionary_custom_url", tempUrl)
                            .apply()
                        showDictionaryDialog = false
                        Toast.makeText(context, "Đã cập nhật từ điển tra từ!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VBookTheme.primaryColor())
                ) {
                    Text("Đồng ý", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDictionaryDialog = false }) {
                    Text("Hủy", color = Color.Gray)
                }
            }
        )
    }

    // 4. TTS Settings Dialog
    if (showTtsDialog) {
        val engines = listOf("Google TTS", "TTS mặc định hệ thống", "Cloud TTS (Premium)")
        var tempEngine by remember { mutableStateOf(ttsEngine) }
        var tempSpeed by remember { mutableFloatStateOf(ttsSpeed) }
        var tempPitch by remember { mutableFloatStateOf(ttsPitch) }

        AlertDialog(
            onDismissRequest = { showTtsDialog = false },
            containerColor = VBookTheme.cardColor(),
            title = { Text("Cấu hình đọc văn bản (TTS)", color = VBookTheme.textColor(), fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    // TTS Engine Selection
                    Column {
                        Text("Công cụ giọng đọc:", fontSize = 12.sp, color = VBookTheme.subTextColor(), fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(4.dp))
                        engines.forEach { eng ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { tempEngine = eng }.padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = tempEngine == eng, onClick = null)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(eng, color = VBookTheme.textColor(), fontSize = 14.sp)
                            }
                        }
                    }

                    // Speed Slider
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Tốc độ giọng đọc:", fontSize = 14.sp, color = VBookTheme.textColor())
                            Text("${(tempSpeed * 10).roundToInt() / 10.0}x", fontSize = 14.sp, color = VBookTheme.primaryColor(), fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = tempSpeed,
                            onValueChange = { tempSpeed = it },
                            valueRange = 0.5f..3.0f,
                            steps = 25
                        )
                    }

                    // Pitch Slider
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Cao độ tông giọng:", fontSize = 14.sp, color = VBookTheme.textColor())
                            Text("${(tempPitch * 10).roundToInt() / 10.0}x", fontSize = 14.sp, color = VBookTheme.primaryColor(), fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = tempPitch,
                            onValueChange = { tempPitch = it },
                            valueRange = 0.5f..2.0f,
                            steps = 15
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        ttsEngine = tempEngine
                        ttsSpeed = tempSpeed
                        ttsPitch = tempPitch
                        rawPrefs.edit()
                            .putString("reader_tts_engine", tempEngine)
                            .putFloat("reader_tts_speed", tempSpeed)
                            .putFloat("reader_tts_pitch", tempPitch)
                            .apply()
                        showTtsDialog = false
                        Toast.makeText(context, "Cấu hình giọng đọc TTS thành công!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VBookTheme.primaryColor())
                ) {
                    Text("Đồng ý", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTtsDialog = false }) {
                    Text("Hủy", color = Color.Gray)
                }
            }
        )
    }

    // 5. Reading Reminder Dialog
    if (showReminderDialog) {
        val options = listOf(0 to "Không nhắc nhở", 30 to "Sau 30 phút đọc sách", 60 to "Sau 1 tiếng (60 phút)", 120 to "Sau 2 tiếng (120 phút)", 180 to "Sau 3 tiếng (180 phút)", 999 to "Tự động khoá ứng dụng khi quá giờ")
        var tempReminder by remember { mutableStateOf(readingReminder) }

        AlertDialog(
            onDismissRequest = { showReminderDialog = false },
            containerColor = VBookTheme.cardColor(),
            title = { Text("Nhắc nhở thời gian đọc", color = VBookTheme.textColor(), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    options.forEach { (mins, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { tempReminder = mins }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = tempReminder == mins, onClick = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(label, color = VBookTheme.textColor())
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        readingReminder = tempReminder
                        rawPrefs.edit().putInt("reader_reading_reminder", tempReminder).apply()
                        showReminderDialog = false
                        Toast.makeText(context, "Đã lưu cài đặt nhắc nhở!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VBookTheme.primaryColor())
                ) {
                    Text("Đồng ý", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showReminderDialog = false }) {
                    Text("Hủy", color = Color.Gray)
                }
            }
        )
    }

    // 6. Download Format Dialog
    if (showDownloadFormatDialog) {
        val options = listOf("EPUB", "TXT (Chương gộp)", "ZIP (Chương lẻ/Hình ảnh)")
        var tempFormat by remember { mutableStateOf(downloadFormat) }

        AlertDialog(
            onDismissRequest = { showDownloadFormatDialog = false },
            containerColor = VBookTheme.cardColor(),
            title = { Text("Định dạng tải về ngoại tuyến", color = VBookTheme.textColor(), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    options.forEach { format ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { tempFormat = format }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = tempFormat == format, onClick = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(format, color = VBookTheme.textColor())
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        downloadFormat = tempFormat
                        rawPrefs.edit().putString("reader_download_format", tempFormat).apply()
                        showDownloadFormatDialog = false
                        Toast.makeText(context, "Đã chọn định dạng tải về: $tempFormat", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VBookTheme.primaryColor())
                ) {
                    Text("Lưu", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDownloadFormatDialog = false }) {
                    Text("Hủy", color = Color.Gray)
                }
            }
        )
    }
}
