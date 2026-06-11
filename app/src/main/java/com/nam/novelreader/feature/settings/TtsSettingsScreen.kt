package com.nam.novelreader.feature.settings

import android.content.Context
import android.util.Log
import android.speech.tts.TextToSpeech
import java.util.Locale
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.nam.novelreader.data.local.dao.ExtensionDao
import com.nam.novelreader.data.local.entity.ExtensionEntity
import com.nam.novelreader.data.preferences.AppPreferences
import com.nam.novelreader.extension.loader.ExtensionLoader
import com.nam.novelreader.extension.model.ExtensionResult
import com.nam.novelreader.extension.model.ScriptType
import com.nam.novelreader.extension.runtime.VBookJsExtensionRunner
import com.nam.novelreader.feature.components.*
import com.nam.novelreader.navigation.Routes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.resume
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import javax.inject.Inject

@Serializable
data class TtsVoice(
    val id: String,
    val name: String,
    val language: String = "vi-VN"
)

@Serializable
data class WordReplacementRule(
    val pattern: String,
    val replacement: String
)

@HiltViewModel
class TtsSettingsViewModel @Inject constructor(
    val appPrefs: AppPreferences,
    private val extensionDao: ExtensionDao,
    private val extensionLoader: ExtensionLoader,
    private val jsExtensionRunner: VBookJsExtensionRunner,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) : ViewModel() {

    private val _installedTtsExtensions = MutableStateFlow<List<ExtensionEntity>>(emptyList())
    val installedTtsExtensions: StateFlow<List<ExtensionEntity>> = _installedTtsExtensions.asStateFlow()

    private val _voices = MutableStateFlow<List<TtsVoice>>(emptyList())
    val voices: StateFlow<List<TtsVoice>> = _voices.asStateFlow()

    private val _isLoadingVoices = MutableStateFlow(false)
    val isLoadingVoices: StateFlow<Boolean> = _isLoadingVoices.asStateFlow()

    private val _isLoadingExtensions = MutableStateFlow(false)
    val isLoadingExtensions: StateFlow<Boolean> = _isLoadingExtensions.asStateFlow()

    private val _allExtensions = MutableStateFlow<List<com.nam.novelreader.extension.model.ExtensionInfo>>(emptyList())
    val allExtensions: StateFlow<List<com.nam.novelreader.extension.model.ExtensionInfo>> = _allExtensions.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    init {
        loadInstalledTtsExtensions()
        loadVoicesForSelectedEngine()
        fetchTtsExtensionsFromRegistry()
    }

    fun loadInstalledTtsExtensions() {
        viewModelScope.launch {
            extensionDao.getInstalledExtensions().collect { list ->
                _installedTtsExtensions.value = list.filter { it.type == "tts" && it.id == "chillaudio-tts" }
            }
        }
    }

    fun fetchTtsExtensionsFromRegistry() {
        viewModelScope.launch {
            _isLoadingExtensions.value = true
            try {
                extensionLoader.downloadAndInstallTtsExtensions()
                val exts = extensionLoader.fetchRepository("https://www.vbookext.me/api/registry/vbook-a427a9f1.json")
                _allExtensions.value = exts
            } catch (e: Exception) {
                Log.e("TtsSettingsVM", "Failed to fetch registry", e)
            } finally {
                _isLoadingExtensions.value = false
            }
        }
    }

    fun installTtsExtension(info: com.nam.novelreader.extension.model.ExtensionInfo) {
        viewModelScope.launch {
            _isLoadingExtensions.value = true
            extensionLoader.installExtension(info)
            loadInstalledTtsExtensions()
            _isLoadingExtensions.value = false
        }
    }

    private suspend fun initTtsAsync(context: Context, enginePkg: String?): TextToSpeech? = 
        suspendCancellableCoroutine { continuation ->
            var tts: TextToSpeech? = null
            var hasResumed = false
            val listener = TextToSpeech.OnInitListener { status ->
                if (!hasResumed) {
                    hasResumed = true
                    if (status == TextToSpeech.SUCCESS) {
                        continuation.resume(tts)
                    } else {
                        tts?.shutdown()
                        continuation.resume(null)
                    }
                }
            }
            try {
                tts = if (!enginePkg.isNullOrEmpty()) {
                    TextToSpeech(context, listener, enginePkg)
                } else {
                    TextToSpeech(context, listener)
                }
            } catch (e: Exception) {
                if (!hasResumed) {
                    hasResumed = true
                    continuation.resume(null)
                }
            }
            continuation.invokeOnCancellation {
                if (!hasResumed) {
                    tts?.shutdown()
                }
            }
        }

    private fun loadSystemVoices() {
        viewModelScope.launch {
            _isLoadingVoices.value = true
            val list = mutableListOf<TtsVoice>()
            try {
                val enginePkg = appPrefs.ttsSystemEngine
                val tempTts = withContext(Dispatchers.IO) {
                    initTtsAsync(context, enginePkg)
                }
                if (tempTts != null) {
                    val langParts = appPrefs.ttsSystemLanguage.split("-")
                    val locale = if (langParts.size >= 2) Locale(langParts[0], langParts[1]) else Locale(appPrefs.ttsSystemLanguage)
                    
                    tempTts.setLanguage(locale)
                    
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        val voices = tempTts.voices
                        if (voices != null) {
                            var index = 1
                            for (v in voices) {
                                if (v.locale.language == locale.language) {
                                    val label = if (v.isNetworkConnectionRequired) "Online" else "Local"
                                    list.add(TtsVoice(
                                        id = v.name,
                                        name = "Giọng đọc $index (${label})",
                                        language = v.locale.displayName
                                    ))
                                    index++
                                }
                            }
                        }
                    }
                    tempTts.shutdown()
                }
            } catch (e: Exception) {
                Log.e("TtsSettingsVM", "Error getting system voices", e)
            }
            
            val defaultVoices = listOf(
                TtsVoice("vi-vn-x-vif-local", "Giọng đọc 1 (Miền Nam - Nữ)", "Tiếng Việt (Việt Nam)"),
                TtsVoice("vi-vn-x-vic-local", "Giọng đọc 2 (Miền Bắc - Nam)", "Tiếng Việt (Việt Nam)"),
                TtsVoice("vi-vn-x-vid-local", "Giọng đọc 3 (Miền Bắc - Nữ)", "Tiếng Việt (Việt Nam)"),
                TtsVoice("vi-vn-x-vie-local", "Giọng đọc 4 (Miền Nam - Nam)", "Tiếng Việt (Việt Nam)"),
                TtsVoice("vi-vn-x-vfg-local", "Giọng đọc 5 (Địa phương - Nữ)", "Tiếng Việt (Việt Nam)"),
                TtsVoice("vi-vn-x-vfa-local", "Giọng đọc 6 (Địa phương - Nam trầm)", "Tiếng Việt (Việt Nam)")
            )
            
            val combinedList = mutableListOf<TtsVoice>()
            combinedList.addAll(list)
            
            val langParts = appPrefs.ttsSystemLanguage.split("-")
            val isVietnamese = langParts.firstOrNull()?.lowercase() == "vi"
            
            if (isVietnamese) {
                for (defVoice in defaultVoices) {
                    if (combinedList.none { it.id == defVoice.id }) {
                        combinedList.add(defVoice)
                    }
                }
            }
            
            if (combinedList.isEmpty()) {
                combinedList.addAll(defaultVoices)
            }
            
            _voices.value = combinedList
            _isLoadingVoices.value = false
            
            val currentVoice = appPrefs.ttsSelectedVoice
            if (currentVoice.isBlank() || combinedList.none { it.id == currentVoice }) {
                combinedList.firstOrNull()?.let {
                    appPrefs.ttsSelectedVoice = it.id
                }
            }
        }
    }

    fun loadVoicesForSelectedEngine() {
        val engine = appPrefs.ttsSelectedEngine
        if (engine == "system" || engine.isBlank()) {
            loadSystemVoices()
            return
        }
        if (engine == "azure_edge") {
            val list = listOf(
                TtsVoice("vi-VN-NamMinhNeural", "Giọng nam trầm (NamMinh)", "vi-VN"),
                TtsVoice("vi-VN-HoaiMyNeural", "Giọng nữ miền Nam (HoaiMy)", "vi-VN")
            )
            _voices.value = list
            val currentVoice = appPrefs.ttsSelectedVoice
            if (currentVoice.isBlank() || list.none { it.id == currentVoice }) {
                appPrefs.ttsSelectedVoice = "vi-VN-NamMinhNeural"
            }
            return
        }
        viewModelScope.launch {
            _isLoadingVoices.value = true
            _voices.value = emptyList()
            try {
                val loaded = extensionLoader.loadExtension(engine)
                if (loaded != null) {
                    val result = jsExtensionRunner.execute(loaded, ScriptType.VOICE)
                    if (result is ExtensionResult.Success) {
                        val jsonObject = json.parseToJsonElement(result.data).jsonObject
                        val dataArray = jsonObject["data"]
                        val parsed = if (dataArray != null) {
                            json.decodeFromJsonElement<List<TtsVoice>>(dataArray)
                        } else {
                            emptyList()
                        }
                        _voices.value = parsed
                        
                        val currentVoice = appPrefs.ttsSelectedVoice
                        if (currentVoice.isBlank() || parsed.none { it.id == currentVoice }) {
                            parsed.firstOrNull()?.let {
                                appPrefs.ttsSelectedVoice = it.id
                            }
                        }
                    } else if (result is ExtensionResult.Error) {
                        Log.e("TtsSettingsVM", "Failed to load voices from engine $engine: ${result.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e("TtsSettingsVM", "Error running voice.js script for $engine", e)
            } finally {
                _isLoadingVoices.value = false
            }
        }
    }

    fun selectEngine(engineId: String) {
        appPrefs.ttsSelectedEngine = engineId
        appPrefs.ttsSelectedVoice = ""
        loadVoicesForSelectedEngine()
    }

    fun selectVoice(voiceId: String) {
        appPrefs.ttsSelectedVoice = voiceId
    }

    fun getWordReplacementsCount(): Int {
        val jsonStr = appPrefs.ttsWordReplacements
        if (jsonStr.isBlank()) return 0
        return try {
            json.decodeFromString<List<WordReplacementRule>>(jsonStr).size
        } catch (e: Exception) {
            0
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsSettingsScreen(
    navController: NavHostController,
    viewModel: TtsSettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val prefs = viewModel.appPrefs

    val installedExtensions by viewModel.installedTtsExtensions.collectAsState()
    val availableVoices by viewModel.voices.collectAsState()
    val isLoadingVoices by viewModel.isLoadingVoices.collectAsState()
    val isLoadingExtensions by viewModel.isLoadingExtensions.collectAsState()
    val allExtensionsRegistry by viewModel.allExtensions.collectAsState()

    // Preferences states
    var ttsEngine by remember { mutableStateOf(prefs.ttsSelectedEngine) }
    var ttsVoice by remember { mutableStateOf(prefs.ttsSelectedVoice) }
    var autoExpand by remember { mutableStateOf(prefs.ttsAutoExpand) }
    var audioFocus by remember { mutableStateOf(prefs.ttsAudioFocus) }
    var stopOnExit by remember { mutableStateOf(prefs.ttsStopOnExit) }
    var headphoneControl by remember { mutableStateOf(prefs.ttsHeadphoneControl) }

    var wordReplacementsCount by remember { mutableIntStateOf(viewModel.getWordReplacementsCount()) }

    // Dialog & Dropdown visibility
    var showEngineDialog by remember { mutableStateOf(false) }
    var showVoiceDialog by remember { mutableStateOf(false) }
    var showStoreDialog by remember { mutableStateOf(false) }
    var showBackgroundMusicDialog by remember { mutableStateOf(false) }

    // Background music simple configuration
    val rawPrefs = remember { context.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE) }
    var bgMusicEnabled by remember { mutableStateOf(rawPrefs.getBoolean("reader_tts_bg_music_enabled", false)) }
    var bgMusicVolume by remember { mutableFloatStateOf(rawPrefs.getFloat("reader_tts_bg_music_volume", 0.3f)) }

    // Monitor engine selection and reload voices
    LaunchedEffect(ttsEngine) {
        viewModel.selectEngine(ttsEngine)
    }

    // Refresh voices state when loaded
    LaunchedEffect(availableVoices) {
        ttsVoice = prefs.ttsSelectedVoice
    }

    // Dynamic replacement count update when coming back
    LaunchedEffect(Unit) {
        wordReplacementsCount = viewModel.getWordReplacementsCount()
    }

    Scaffold(
        containerColor = VBookTheme.backgroundColor(),
        topBar = {
            TopAppBar(
                title = { Text("Đọc văn bản (TTS)", fontWeight = FontWeight.Bold, color = VBookTheme.textColor()) },
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
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 1. Công cụ đọc
            VBookSettingsGroup("Công cụ đọc") {
                val selectedEngineName = when (ttsEngine) {
                    "system" -> "Hệ thống"
                    "azure_edge" -> "Microsoft Azure Edge (Trudio)"
                    else -> installedExtensions.find { it.id == ttsEngine }?.name ?: ttsEngine
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showEngineDialog = true }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedEngineName,
                            color = VBookTheme.textColor(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        val activeVoiceName = availableVoices.find { it.id == ttsVoice }?.name ?: "Mặc định"
                        if (ttsEngine != "system" || (ttsEngine == "system" && availableVoices.isNotEmpty() && ttsVoice.isNotEmpty())) {
                            Text(
                                text = "Giọng đọc: $activeVoiceName",
                                color = VBookTheme.subTextColor(),
                                fontSize = 13.sp
                            )
                        } else {
                            Text(
                                text = "Sử dụng giọng đọc có sẵn trên thiết bị",
                                color = VBookTheme.subTextColor(),
                                fontSize = 13.sp
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (ttsEngine != "system" && ttsEngine != "azure_edge") {
                            IconButton(
                                onClick = {
                                    navController.navigate(Routes.extensionSettings(ttsEngine))
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Settings,
                                    contentDescription = "Settings",
                                    tint = VBookTheme.primaryColor()
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = VBookTheme.subTextColor()
                        )
                    }
                }

                // Chọn giọng đọc (khi có giọng đọc khả dụng)
                if (availableVoices.isNotEmpty()) {
                    VBookSettingsDivider()
                    val activeVoiceName = availableVoices.find { it.id == ttsVoice }?.name ?: "Chọn giọng đọc"
                    VBookSettingsItem(
                        title = "Giọng đọc",
                        subtitle = activeVoiceName,
                        showChevron = true,
                        onClick = { showVoiceDialog = true }
                    )
                }
            }

            // Nút tìm thêm công cụ khác
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = { showStoreDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VBookTheme.cardColor(),
                        contentColor = VBookTheme.textColor()
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    elevation = null
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = VBookTheme.primaryColor(),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Tìm thêm các công cụ khác",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = VBookTheme.primaryColor()
                        )
                    }
                }
            }

            // 2. Hành vi khi đọc
            VBookSettingsGroup("Hành vi khi đọc") {
                VBookSettingsItem(
                    title = "Tự động mở rộng khi phát",
                    subtitle = "Tự động mở rộng giao diện khi bắt đầu phát giọng đọc.",
                    content = {
                        VBookSwitch(
                            checked = autoExpand,
                            onCheckedChange = {
                                autoExpand = it
                                prefs.ttsAutoExpand = it
                            }
                        )
                    }
                )
                VBookSettingsDivider()
                VBookSettingsItem(
                    title = "Ưu tiên âm thanh",
                    subtitle = "Tạm dừng âm thanh của các ứng dụng khác khi đọc văn bản.",
                    content = {
                        VBookSwitch(
                            checked = audioFocus,
                            onCheckedChange = {
                                audioFocus = it
                                prefs.ttsAudioFocus = it
                            }
                        )
                    }
                )
            }

            // 3. Khi thoát trình đọc
            VBookSettingsGroup("Khi thoát trình đọc") {
                VBookSettingsItem(
                    title = "Dừng tự động khi thoát",
                    subtitle = "Tự động dừng đọc văn bản khi bạn thoát khỏi trình đọc truyện.",
                    content = {
                        VBookSwitch(
                            checked = stopOnExit,
                            onCheckedChange = {
                                stopOnExit = it
                                prefs.ttsStopOnExit = it
                            }
                        )
                    }
                )
            }

            // 4. Điều khiển phát
            VBookSettingsGroup("Điều khiển phát") {
                VBookSettingsItem(
                    title = "Hỗ trợ điều khiển bằng tai nghe",
                    subtitle = "Sử dụng các nút trên tai nghe để điều khiển chức năng đọc văn bản.",
                    content = {
                        VBookSwitch(
                            checked = headphoneControl,
                            onCheckedChange = {
                                headphoneControl = it
                                prefs.ttsHeadphoneControl = it
                            }
                        )
                    }
                )
            }

            // 5. Nội dung & trải nghiệm
            VBookSettingsGroup("Nội dung & trải nghiệm") {
                VBookSettingsItem(
                    title = "Thay thế từ",
                    subtitle = "$wordReplacementsCount từ",
                    showChevron = true,
                    onClick = {
                        navController.navigate(Routes.TTS_WORD_REPLACEMENT)
                    }
                )
                VBookSettingsDivider()
                val musicStateText = if (bgMusicEnabled) "Bật - Âm lượng ${(bgMusicVolume * 100).toInt()}%" else "Tắt - 0 bài nhạc"
                VBookSettingsItem(
                    title = "Nhạc nền",
                    subtitle = musicStateText,
                    showChevron = true,
                    onClick = { showBackgroundMusicDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // === DIALOGS ===

    // Engine Selection Dialog
    if (showEngineDialog) {
        AlertDialog(
            onDismissRequest = { showEngineDialog = false },
            containerColor = VBookTheme.cardColor(),
            title = { Text("Chọn công cụ đọc", color = VBookTheme.textColor(), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // System engine option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                ttsEngine = "system"
                                showEngineDialog = false
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = ttsEngine == "system", onClick = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Hệ thống (Android TTS)", color = VBookTheme.textColor())
                    }

                    // Azure Edge engine option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                ttsEngine = "azure_edge"
                                showEngineDialog = false
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = ttsEngine == "azure_edge", onClick = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Microsoft Azure Edge (Trudio)", color = VBookTheme.textColor())
                    }

                    // Installed AI TTS engines options
                    installedExtensions.forEach { ext ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    ttsEngine = ext.id
                                    showEngineDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = ttsEngine == ext.id, onClick = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(ext.name, color = VBookTheme.textColor())
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showEngineDialog = false }) {
                    Text("Đóng", color = Color.Gray)
                }
            }
        )
    }

    // Voice Selection Dialog
    if (showVoiceDialog && availableVoices.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showVoiceDialog = false },
            containerColor = VBookTheme.cardColor(),
            title = { Text("Chọn giọng đọc", color = VBookTheme.textColor(), fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isLoadingVoices) {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = VBookTheme.primaryColor())
                        }
                    } else {
                        availableVoices.forEach { voice ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        ttsVoice = voice.id
                                        prefs.ttsSelectedVoice = voice.id
                                        showVoiceDialog = false
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = ttsVoice == voice.id, onClick = null)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(voice.name, color = VBookTheme.textColor())
                                    Text(voice.language, color = VBookTheme.subTextColor(), fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showVoiceDialog = false }) {
                    Text("Đóng", color = Color.Gray)
                }
            }
        )
    }

    // Store Dialog (Download TTS Extensions)
    if (showStoreDialog) {
        AlertDialog(
            onDismissRequest = { showStoreDialog = false },
            containerColor = VBookTheme.cardColor(),
            title = { Text("Kho công cụ đọc TTS", color = VBookTheme.textColor(), fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isLoadingExtensions) {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = VBookTheme.primaryColor())
                        }
                    } else {
                        val ttsRegistryExts = allExtensionsRegistry.filter { it.type == "tts" && it.name.lowercase().replace(Regex("[^a-z0-9]"), "-") == "chillaudio-tts" }
                        if (ttsRegistryExts.isEmpty()) {
                            Text("Không tìm thấy tiện ích TTS nào.", color = VBookTheme.subTextColor())
                        } else {
                            ttsRegistryExts.forEach { info ->
                                val extId = info.name.lowercase().replace(Regex("[^a-z0-9]"), "-")
                                val isInstalled = installedExtensions.any { it.id == extId || it.name == info.name }
                                
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(info.name, color = VBookTheme.textColor(), fontWeight = FontWeight.Bold)
                                        Text(info.description, color = VBookTheme.subTextColor(), fontSize = 12.sp)
                                        Text("Tác giả: ${info.author} • v${info.version}", color = VBookTheme.subTextColor(), fontSize = 11.sp)
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    if (isInstalled) {
                                        Text(
                                            text = "Đã cài",
                                            color = VBookTheme.primaryColor(),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    } else {
                                        IconButton(
                                            onClick = {
                                                viewModel.installTtsExtension(info)
                                                Toast.makeText(context, "Đang tải và cài đặt ${info.name}", Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Download,
                                                contentDescription = "Download",
                                                tint = VBookTheme.primaryColor()
                                            )
                                        }
                                    }
                                }
                                Divider(color = Color.Gray.copy(alpha = 0.2f))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showStoreDialog = false }) {
                    Text("Đóng", color = Color.Gray)
                }
            }
        )
    }

    // Background Music Dialog
    if (showBackgroundMusicDialog) {
        AlertDialog(
            onDismissRequest = { showBackgroundMusicDialog = false },
            containerColor = VBookTheme.cardColor(),
            title = { Text("Cấu hình nhạc nền", color = VBookTheme.textColor(), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Phát nhạc nền khi đọc", color = VBookTheme.textColor())
                        VBookSwitch(
                            checked = bgMusicEnabled,
                            onCheckedChange = {
                                bgMusicEnabled = it
                                rawPrefs.edit().putBoolean("reader_tts_bg_music_enabled", it).apply()
                            }
                        )
                    }

                    if (bgMusicEnabled) {
                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Âm lượng nhạc nền:", fontSize = 14.sp, color = VBookTheme.textColor())
                                Text("${(bgMusicVolume * 100).toInt()}%", fontSize = 14.sp, color = VBookTheme.primaryColor(), fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = bgMusicVolume,
                                onValueChange = {
                                    bgMusicVolume = it
                                    rawPrefs.edit().putFloat("reader_tts_bg_music_volume", it).apply()
                                },
                                valueRange = 0.05f..1.0f,
                                steps = 19
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showBackgroundMusicDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = VBookTheme.primaryColor())
                ) {
                    Text("Đồng ý", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackgroundMusicDialog = false }) {
                    Text("Hủy", color = Color.Gray)
                }
            }
        )
    }
}
