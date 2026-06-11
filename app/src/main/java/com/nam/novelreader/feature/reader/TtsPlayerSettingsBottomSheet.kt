package com.nam.novelreader.feature.reader

import android.content.Context
import android.content.Intent
import android.util.Log
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nam.novelreader.data.local.dao.ExtensionDao
import com.nam.novelreader.data.local.entity.ExtensionEntity
import com.nam.novelreader.data.preferences.AppPreferences
import com.nam.novelreader.extension.loader.ExtensionLoader
import com.nam.novelreader.extension.model.ExtensionResult
import com.nam.novelreader.extension.model.ScriptType
import com.nam.novelreader.extension.runtime.VBookJsExtensionRunner
import com.nam.novelreader.feature.settings.TtsVoice
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.resume
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class TtsPlayerSettingsViewModel @Inject constructor(
    val appPrefs: AppPreferences,
    private val extensionDao: ExtensionDao,
    private val extensionLoader: ExtensionLoader,
    private val jsExtensionRunner: VBookJsExtensionRunner,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _installedTtsExtensions = MutableStateFlow<List<ExtensionEntity>>(emptyList())
    val installedTtsExtensions: StateFlow<List<ExtensionEntity>> = _installedTtsExtensions

    private val _voices = MutableStateFlow<List<TtsVoice>>(emptyList())
    val voices: StateFlow<List<TtsVoice>> = _voices

    private val _isLoadingVoices = MutableStateFlow(false)
    val isLoadingVoices: StateFlow<Boolean> = _isLoadingVoices

    private val _systemEngines = MutableStateFlow<List<EngineInfo>>(emptyList())
    val systemEngines: StateFlow<List<EngineInfo>> = _systemEngines

    private val _systemLanguages = MutableStateFlow<List<LanguageInfo>>(emptyList())
    val systemLanguages: StateFlow<List<LanguageInfo>> = _systemLanguages

    private val json = Json { ignoreUnknownKeys = true }

    data class EngineInfo(val packageName: String, val label: String)
    data class LanguageInfo(val code: String, val displayName: String)

    init {
        loadInstalledTtsExtensions()
        loadSystemEngines()
        loadSystemLanguages()
        loadVoicesForSelectedEngine()
    }

    fun loadInstalledTtsExtensions() {
        viewModelScope.launch {
            extensionDao.getInstalledExtensions().collect { list ->
                _installedTtsExtensions.value = list.filter { it.type == "tts" && it.id == "chillaudio-tts" }
            }
        }
    }

    private fun loadSystemEngines() {
        val list = mutableListOf<EngineInfo>()
        var tempTts: TextToSpeech? = null
        try {
            tempTts = TextToSpeech(context, null)
            val engines = tempTts.engines
            for (eng in engines) {
                list.add(EngineInfo(eng.name, eng.label))
            }
        } catch (e: Exception) {
            list.add(EngineInfo("com.google.android.tts", "Nhận dạng và tổng hợp giọng nói của Google"))
        } finally {
            tempTts?.shutdown()
        }
        _systemEngines.value = list
    }

    private fun loadSystemLanguages() {
        val list = listOf(
            LanguageInfo("vi-VN", "Tiếng Việt (Việt Nam)"),
            LanguageInfo("en-US", "Tiếng Anh (United States)"),
            LanguageInfo("en-GB", "Tiếng Anh (United Kingdom)"),
            LanguageInfo("zh-CN", "Tiếng Trung (Trung Quốc)"),
            LanguageInfo("ja-JP", "Tiếng Nhật (Nhật Bản)"),
            LanguageInfo("ko-KR", "Tiếng Hàn (Hàn Quốc)"),
            LanguageInfo("fr-FR", "Tiếng Pháp (Pháp)"),
            LanguageInfo("de-DE", "Tiếng Đức (Đức)"),
            LanguageInfo("ru-RU", "Tiếng Nga (Nga)")
        )
        _systemLanguages.value = list
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
            val matchedVoice = list.find { it.id == currentVoice }
            if (matchedVoice != null) {
                appPrefs.ttsCustomLanguage = matchedVoice.language
            } else {
                list.firstOrNull()?.let {
                    appPrefs.ttsSelectedVoice = it.id
                    appPrefs.ttsCustomLanguage = it.language
                }
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
                        val matchedVoice = parsed.find { it.id == currentVoice }
                        if (matchedVoice != null) {
                            appPrefs.ttsCustomLanguage = matchedVoice.language
                        } else {
                            parsed.firstOrNull()?.let {
                                appPrefs.ttsSelectedVoice = it.id
                                appPrefs.ttsCustomLanguage = it.language
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore
            } finally {
                _isLoadingVoices.value = false
            }
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
                Log.e("TTS", "Error getting system voices", e)
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

    fun selectEngine(engineId: String) {
        appPrefs.ttsSelectedEngine = engineId
        appPrefs.ttsSelectedVoice = ""
        loadVoicesForSelectedEngine()
    }

    fun selectSystemEngine(pkgName: String) {
        appPrefs.ttsSystemEngine = pkgName
        appPrefs.ttsSelectedVoice = ""
        loadSystemVoices()
    }

    fun selectSystemLanguage(langCode: String) {
        appPrefs.ttsSystemLanguage = langCode
        appPrefs.ttsSelectedVoice = ""
        loadSystemVoices()
    }
}

@Composable
fun TtsPlayerSettingsBottomSheet(
    themeIndex: Int,
    onNavigateToExtensionSettings: (String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: TtsPlayerSettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val appPrefs = viewModel.appPrefs

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

    val installedExtensions by viewModel.installedTtsExtensions.collectAsState()
    val availableVoices by viewModel.voices.collectAsState()
    val systemEngines by viewModel.systemEngines.collectAsState()
    val systemLanguages by viewModel.systemLanguages.collectAsState()

    var showTtsMenu by remember { mutableStateOf(false) }
    var showToolMenu by remember { mutableStateOf(false) }
    var showLanguageMenu by remember { mutableStateOf(false) }
    var showVoiceMenu by remember { mutableStateOf(false) }
    var showSplitMenu by remember { mutableStateOf(false) }
    var showMaxLengthMenu by remember { mutableStateOf(false) }

    // TTS speed/pitch states synced to preferences
    val rawPrefs = remember { context.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE) }
    var speedVal by remember { mutableFloatStateOf(rawPrefs.getFloat("reader_tts_speed", 1.0f)) }
    var pitchVal by remember { mutableFloatStateOf(rawPrefs.getFloat("reader_tts_pitch", 1.0f)) }
    var volumeGainVal by remember { mutableFloatStateOf(rawPrefs.getFloat("reader_tts_volume_gain", 0.0f)) }
    var ttsAutoNext by remember { mutableStateOf(rawPrefs.getBoolean("tts_auto_next_chapter", true)) }

    // Google API Key states
    var showGoogleApiKeyDialog by remember { mutableStateOf(false) }
    var currentGoogleApiKey by remember { mutableStateOf(appPrefs.ttsGoogleApiKey) }

    val customLanguages = remember(availableVoices) {
        availableVoices.map { it.language }.distinct()
    }

    LaunchedEffect(customLanguages) {
        if (customLanguages.isNotEmpty() && !customLanguages.contains(appPrefs.ttsCustomLanguage)) {
            appPrefs.ttsCustomLanguage = customLanguages.first()
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
                .fillMaxHeight(0.85f)
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(bgColor)
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) {}
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                // Drag handle
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(vertical = 12.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .background(textColor.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
                )

                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.KeyboardArrowDown, "Thu nhỏ", tint = textColor, modifier = Modifier.size(28.dp))
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, "Đóng", tint = textColor, modifier = Modifier.size(24.dp))
                    }
                }

                // Scrollable List of Settings
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. TTS selection
                    val currentEngine = appPrefs.ttsSelectedEngine
                    val engineName = when (currentEngine) {
                        "system" -> "Hệ thống"
                        "azure_edge" -> "Microsoft Azure Edge (Trudio)"
                        else -> {
                            installedExtensions.find { it.id == currentEngine }?.name ?: currentEngine
                        }
                    }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        TtsSettingsItemCard(
                            label = "TTS",
                            value = engineName,
                            textColor = textColor,
                            cardColor = cardColor,
                            onSettingsClick = if (currentEngine != "system" && currentEngine != "azure_edge" && currentEngine.isNotBlank()) {
                                {
                                    onDismiss()
                                    onNavigateToExtensionSettings(currentEngine)
                                }
                            } else null,
                            onClick = { showTtsMenu = true }
                        )
                        MaterialTheme(
                            colorScheme = MaterialTheme.colorScheme.copy(
                                surface = cardColor,
                                onSurface = textColor
                            )
                        ) {
                            DropdownMenu(
                                expanded = showTtsMenu,
                                onDismissRequest = { showTtsMenu = false },
                                modifier = Modifier.background(cardColor)
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Hệ thống", color = textColor)
                                            if (currentEngine == "system") Icon(Icons.Filled.Check, null, tint = primaryColor, modifier = Modifier.size(16.dp))
                                        }
                                    },
                                    onClick = {
                                        showTtsMenu = false
                                        viewModel.selectEngine("system")
                                        notifyServiceEngineChanged(context)
                                    }
                                )

                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Microsoft Azure Edge (Trudio)", color = textColor)
                                            if (currentEngine == "azure_edge") Icon(Icons.Filled.Check, null, tint = primaryColor, modifier = Modifier.size(16.dp))
                                        }
                                    },
                                    onClick = {
                                        showTtsMenu = false
                                        viewModel.selectEngine("azure_edge")
                                        notifyServiceEngineChanged(context)
                                    }
                                )

                                installedExtensions.forEach { ext ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(ext.name, color = textColor)
                                                if (currentEngine == ext.id) Icon(Icons.Filled.Check, null, tint = primaryColor, modifier = Modifier.size(16.dp))
                                            }
                                        },
                                        onClick = {
                                            showTtsMenu = false
                                            viewModel.selectEngine(ext.id)
                                            notifyServiceEngineChanged(context)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 2. Công cụ (Only shown if engine is system)
                    if (currentEngine == "system") {
                        val currentSysEnginePkg = appPrefs.ttsSystemEngine
                        val sysEngineLabel = systemEngines.find { it.packageName == currentSysEnginePkg }?.label
                            ?: "Nhận dạng và tổng hợp giọng nói của Google"
                        Box(modifier = Modifier.fillMaxWidth()) {
                            TtsSettingsItemCard(
                                label = "Công cụ",
                                value = sysEngineLabel,
                                textColor = textColor,
                                cardColor = cardColor,
                                onClick = { showToolMenu = true }
                            )
                            MaterialTheme(
                                colorScheme = MaterialTheme.colorScheme.copy(
                                    surface = cardColor,
                                    onSurface = textColor
                                )
                            ) {
                                DropdownMenu(
                                    expanded = showToolMenu,
                                    onDismissRequest = { showToolMenu = false },
                                    modifier = Modifier.background(cardColor)
                                ) {
                                    systemEngines.forEach { eng ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(eng.label, color = textColor)
                                                    if (currentSysEnginePkg == eng.packageName) Icon(Icons.Filled.Check, null, tint = primaryColor, modifier = Modifier.size(16.dp))
                                                }
                                            },
                                            onClick = {
                                                showToolMenu = false
                                                viewModel.selectSystemEngine(eng.packageName)
                                                notifyServiceEngineChanged(context)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. Ngôn ngữ
                    val showLanguage = currentEngine == "system" || (currentEngine != "system" && currentEngine != "azure_edge" && customLanguages.isNotEmpty())
                    if (showLanguage) {
                        val currentLang = if (currentEngine == "system") appPrefs.ttsSystemLanguage else appPrefs.ttsCustomLanguage
                        val langLabel = if (currentEngine == "system") {
                            systemLanguages.find { it.code == currentLang }?.displayName ?: currentLang
                        } else {
                            currentLang
                        }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            TtsSettingsItemCard(
                                label = "Ngôn ngữ",
                                value = langLabel,
                                textColor = textColor,
                                cardColor = cardColor,
                                onClick = { showLanguageMenu = true }
                            )
                            MaterialTheme(
                                colorScheme = MaterialTheme.colorScheme.copy(
                                    surface = cardColor,
                                    onSurface = textColor
                                )
                            ) {
                                DropdownMenu(
                                    expanded = showLanguageMenu,
                                    onDismissRequest = { showLanguageMenu = false },
                                    modifier = Modifier.background(cardColor)
                                ) {
                                    if (currentEngine == "system") {
                                        systemLanguages.forEach { lang ->
                                            DropdownMenuItem(
                                                text = {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(lang.displayName, color = textColor)
                                                        if (currentLang == lang.code) Icon(Icons.Filled.Check, null, tint = primaryColor, modifier = Modifier.size(16.dp))
                                                    }
                                                },
                                                onClick = {
                                                    showLanguageMenu = false
                                                    viewModel.selectSystemLanguage(lang.code)
                                                    notifyServiceEngineChanged(context)
                                                }
                                            )
                                        }
                                    } else {
                                        customLanguages.forEach { lang ->
                                            DropdownMenuItem(
                                                text = {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(lang, color = textColor)
                                                        if (currentLang == lang) Icon(Icons.Filled.Check, null, tint = primaryColor, modifier = Modifier.size(16.dp))
                                                    }
                                                },
                                                onClick = {
                                                    showLanguageMenu = false
                                                    appPrefs.ttsCustomLanguage = lang
                                                    val firstVoiceInLang = availableVoices.firstOrNull { it.language == lang }
                                                    if (firstVoiceInLang != null) {
                                                        appPrefs.ttsSelectedVoice = firstVoiceInLang.id
                                                    }
                                                    notifyServiceEngineChanged(context)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 4. Giọng đọc
                    val currentVoiceId = appPrefs.ttsSelectedVoice
                    val voiceLabel = availableVoices.find { it.id == currentVoiceId }?.name ?: "Giọng đọc 1"
                    Box(modifier = Modifier.fillMaxWidth()) {
                        TtsSettingsItemCard(
                            label = "Giọng đọc",
                            value = voiceLabel,
                            textColor = textColor,
                            cardColor = cardColor,
                            onClick = { showVoiceMenu = true }
                        )
                        MaterialTheme(
                            colorScheme = MaterialTheme.colorScheme.copy(
                                surface = cardColor,
                                onSurface = textColor
                            )
                        ) {
                            DropdownMenu(
                                expanded = showVoiceMenu,
                                onDismissRequest = { showVoiceMenu = false },
                                modifier = Modifier
                                    .background(cardColor)
                                    .fillMaxWidth(0.9f)
                            ) {
                                val displayVoices = if (currentEngine == "system") {
                                    availableVoices
                                } else {
                                    availableVoices.filter { it.language == appPrefs.ttsCustomLanguage }
                                }
                                if (displayVoices.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("Không tìm thấy giọng đọc nào", color = textColor.copy(alpha = 0.5f)) },
                                        onClick = { showVoiceMenu = false }
                                    )
                                } else {
                                    displayVoices.forEach { voice ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(voice.name, color = textColor, fontWeight = FontWeight.Bold)
                                                        Text(voice.language, color = textColor.copy(alpha = 0.6f), fontSize = 11.sp)
                                                    }
                                                    if (currentVoiceId == voice.id) Icon(Icons.Filled.Check, null, tint = primaryColor, modifier = Modifier.size(16.dp))
                                                }
                                            },
                                            onClick = {
                                                showVoiceMenu = false
                                                appPrefs.ttsSelectedVoice = voice.id
                                                if (voice.id == "vi-vn-x-vfa-local") {
                                                    pitchVal = 0.67f
                                                    rawPrefs.edit().putFloat("reader_tts_pitch", 0.67f).apply()
                                                    notifyServicePitchChanged(context, 0.67f)
                                                }
                                                notifyServiceEngineChanged(context)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 4.5. Google API Key (chỉ hiển thị nếu dùng giọng Google Online)
                    val isGoogleOnline = currentEngine == "system" && currentVoiceId in listOf(
                        "vi-vn-x-vif-local",
                        "vi-vn-x-vic-local",
                        "vi-vn-x-vid-local",
                        "vi-vn-x-vie-local",
                        "vi-vn-x-vfg-local",
                        "vi-vn-x-vfa-local"
                    )
                    if (isGoogleOnline) {
                        val keyLabel = when (currentGoogleApiKey) {
                            "AIzaSyA33f9cSqKdR-V4XNkZNZ_rh_dbT1VQJFo" -> "Key mặc định 1"
                            "AIzaSyA6mOHhF5xLAjOqCHepfQprTYPjmKVFmKA" -> "Key mặc định 2 (VBook)"
                            "AIzaSyCRZVR4LpsA2hIxn8wkbnaSxxduHheAvhc" -> "Key mặc định 3 (Premium)"
                            else -> if (currentGoogleApiKey.isBlank()) "Chưa thiết lập" else "Key tự nhập (${currentGoogleApiKey.take(6)}...)"
                        }
                        TtsSettingsItemCard(
                            label = "Google API Key",
                            value = keyLabel,
                            textColor = textColor,
                            cardColor = cardColor,
                            onClick = { showGoogleApiKeyDialog = true }
                        )
                    }

                    // 5. Chia nội dung
                    val currentSplitType = appPrefs.ttsSplitType
                    Box(modifier = Modifier.fillMaxWidth()) {
                        TtsSettingsItemCard(
                            label = "Chia nội dung",
                            value = currentSplitType,
                            textColor = textColor,
                            cardColor = cardColor,
                            onClick = { showSplitMenu = true }
                        )
                        MaterialTheme(
                            colorScheme = MaterialTheme.colorScheme.copy(
                                surface = cardColor,
                                onSurface = textColor
                            )
                        ) {
                            DropdownMenu(
                                expanded = showSplitMenu,
                                onDismissRequest = { showSplitMenu = false },
                                modifier = Modifier.background(cardColor)
                            ) {
                                listOf("Theo câu", "Theo đoạn", "Theo độ dài").forEach { split ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(split, color = textColor)
                                                if (currentSplitType == split) Icon(Icons.Filled.Check, null, tint = primaryColor, modifier = Modifier.size(16.dp))
                                            }
                                        },
                                        onClick = {
                                            showSplitMenu = false
                                            appPrefs.ttsSplitType = split
                                            notifyServiceEngineChanged(context)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 6. Độ dài tối đa (Shown if split is by length or paragraph)
                    if (currentSplitType == "Theo độ dài" || currentSplitType == "Theo đoạn") {
                        val currentMaxLength = appPrefs.ttsMaxLength
                        Box(modifier = Modifier.fillMaxWidth()) {
                            TtsSettingsItemCard(
                                label = "Độ dài tối đa",
                                value = currentMaxLength.toString(),
                                textColor = textColor,
                                cardColor = cardColor,
                                onClick = { showMaxLengthMenu = true }
                            )
                            MaterialTheme(
                                colorScheme = MaterialTheme.colorScheme.copy(
                                    surface = cardColor,
                                    onSurface = textColor
                                )
                            ) {
                                DropdownMenu(
                                    expanded = showMaxLengthMenu,
                                    onDismissRequest = { showMaxLengthMenu = false },
                                    modifier = Modifier.background(cardColor)
                                ) {
                                    listOf(100, 200, 260, 300, 500, 1000, 2000, 3000, 4000, 5000).forEach { len ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(len.toString(), color = textColor)
                                                    if (currentMaxLength == len) Icon(Icons.Filled.Check, null, tint = primaryColor, modifier = Modifier.size(16.dp))
                                                }
                                            },
                                            onClick = {
                                                showMaxLengthMenu = false
                                                appPrefs.ttsMaxLength = len
                                                notifyServiceEngineChanged(context)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 7.5. Tốc độ Slider Card
                    TtsSliderCard(
                        label = "Tốc độ đọc",
                        value = speedVal,
                        valueRange = 0.5f..3.0f,
                        resetValue = 1.0f,
                        textColor = textColor,
                        cardColor = cardColor,
                        activeTrackColor = primaryColor,
                        onValueChange = { newVal ->
                            speedVal = newVal
                            rawPrefs.edit().putFloat("reader_tts_speed", newVal).apply()
                        },
                        onValueChangeFinished = {
                            notifyServiceSpeedChanged(context, speedVal)
                        }
                    )

                    // 8. Độ cao Slider Card
                    TtsSliderCard(
                        label = "Độ cao",
                        value = pitchVal,
                        valueRange = 0.5f..3.0f,
                        resetValue = if (appPrefs.ttsSelectedVoice == "vi-vn-x-vfa-local") 0.67f else 1.0f,
                        textColor = textColor,
                        cardColor = cardColor,
                        activeTrackColor = primaryColor,
                        onValueChange = { newVal ->
                            pitchVal = newVal
                            rawPrefs.edit().putFloat("reader_tts_pitch", newVal).apply()
                        },
                        onValueChangeFinished = {
                            notifyServicePitchChanged(context, pitchVal)
                        }
                    )

                    // 8.5. Độ lợi âm (dB) Slider Card
                    TtsSliderCard(
                        label = "Độ lợi âm (dB)",
                        value = volumeGainVal,
                        valueRange = -10.0f..10.0f,
                        resetValue = 0.0f,
                        textColor = textColor,
                        cardColor = cardColor,
                        activeTrackColor = primaryColor,
                        onValueChange = { newVal ->
                            volumeGainVal = newVal
                            rawPrefs.edit().putFloat("reader_tts_volume_gain", newVal).apply()
                        },
                        onValueChangeFinished = {
                            notifyServiceVolumeGainChanged(context, volumeGainVal)
                        }
                    )

                    // 9. Tự động chuyển chương Checkbox Card
                    TtsCheckboxCard(
                        label = "Tự động chuyển chương",
                        checked = ttsAutoNext,
                        textColor = textColor,
                        cardColor = cardColor,
                        activeColor = primaryColor,
                        onCheckedChange = { checked ->
                            ttsAutoNext = checked
                            rawPrefs.edit().putBoolean("tts_auto_next_chapter", checked).apply()
                        }
                    )
                }

                // Bottom button "Nghe thử"
                Button(
                    onClick = {
                        val sampleText = "Đây là giọng đọc thử nghiệm của ứng dụng Novel Studio."
                        val intent = Intent(context, com.nam.novelreader.service.TextToSpeechService::class.java).apply {
                            action = com.nam.novelreader.service.TextToSpeechService.ACTION_TEST_VOICE
                            putExtra(com.nam.novelreader.service.TextToSpeechService.EXTRA_CONTENT, sampleText)
                        }
                        context.startService(intent)
                        Toast.makeText(context, "Đang phát thử nghiệm...", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                        .height(48.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Filled.Headphones, null, tint = bgColor)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Nghe thử", color = bgColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }

    if (showGoogleApiKeyDialog) {
        GoogleApiKeyDialog(
            currentKey = currentGoogleApiKey,
            textColor = textColor,
            bgColor = bgColor,
            primaryColor = primaryColor,
            cardColor = cardColor,
            onDismiss = { showGoogleApiKeyDialog = false },
            onSave = { newKey ->
                currentGoogleApiKey = newKey
                appPrefs.ttsGoogleApiKey = newKey
                showGoogleApiKeyDialog = false
                notifyServiceEngineChanged(context)
            }
        )
    }
}

@Composable
private fun TtsSettingsItemCard(
    label: String,
    value: String,
    textColor: Color,
    cardColor: Color,
    onSettingsClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(14.dp))
            .background(cardColor, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 11.sp, color = textColor.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, fontSize = 15.sp, color = textColor, fontWeight = FontWeight.Medium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onSettingsClick != null) {
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Cấu hình tiện ích",
                        tint = textColor.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }
            Icon(Icons.Filled.ChevronRight, null, tint = textColor.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun TtsSliderCard(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    resetValue: Float,
    textColor: Color,
    cardColor: Color,
    activeTrackColor: Color,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(14.dp))
            .background(cardColor, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 14.sp, color = textColor, fontWeight = FontWeight.Bold)

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Minus button
                IconButton(
                    onClick = {
                        val newVal = (value - 0.05f).coerceIn(valueRange.start, valueRange.endInclusive)
                        onValueChange(newVal)
                        onValueChangeFinished?.invoke()
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Filled.Remove, null, tint = textColor, modifier = Modifier.size(16.dp))
                }

                // Value text
                Text(
                    text = String.format(Locale.US, "%.2f", value),
                    fontSize = 14.sp,
                    color = textColor,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                // Plus button
                IconButton(
                    onClick = {
                        val newVal = (value + 0.05f).coerceIn(valueRange.start, valueRange.endInclusive)
                        onValueChange(newVal)
                        onValueChangeFinished?.invoke()
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Filled.Add, null, tint = textColor, modifier = Modifier.size(16.dp))
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Reset button
                IconButton(
                    onClick = { 
                        onValueChange(resetValue)
                        onValueChangeFinished?.invoke()
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Filled.Refresh, null, tint = textColor, modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = activeTrackColor,
                activeTrackColor = activeTrackColor,
                inactiveTrackColor = textColor.copy(alpha = 0.12f)
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(String.format(Locale.US, "%.1f", valueRange.start), fontSize = 10.sp, color = textColor.copy(alpha = 0.5f))
            Text(String.format(Locale.US, "%.1f", valueRange.endInclusive), fontSize = 10.sp, color = textColor.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun TtsCheckboxCard(
    label: String,
    checked: Boolean,
    textColor: Color,
    cardColor: Color,
    activeColor: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(14.dp))
            .background(cardColor, RoundedCornerShape(14.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, color = textColor, fontWeight = FontWeight.Bold)
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = activeColor)
        )
    }
}

private fun notifyServiceEngineChanged(context: Context) {
    // Stop and restart the current sentence in the service with the new engine
    val intent = Intent(context, com.nam.novelreader.service.TextToSpeechService::class.java).apply {
        // Stop current playback. The user can start again or we can let the service reload settings
        action = com.nam.novelreader.service.TextToSpeechService.ACTION_STOP
    }
    context.startService(intent)
}

private fun notifyServiceSpeedChanged(context: Context, speed: Float) {
    val intent = Intent(context, com.nam.novelreader.service.TextToSpeechService::class.java).apply {
        action = com.nam.novelreader.service.TextToSpeechService.ACTION_SET_SPEED
        putExtra(com.nam.novelreader.service.TextToSpeechService.EXTRA_SPEED, speed)
    }
    context.startService(intent)
}

private fun notifyServicePitchChanged(context: Context, pitch: Float) {
    val intent = Intent(context, com.nam.novelreader.service.TextToSpeechService::class.java).apply {
        action = com.nam.novelreader.service.TextToSpeechService.ACTION_SET_PITCH
        putExtra(com.nam.novelreader.service.TextToSpeechService.EXTRA_PITCH, pitch)
    }
    context.startService(intent)
}

private fun notifyServiceVolumeGainChanged(context: Context, volumeGain: Float) {
    val intent = Intent(context, com.nam.novelreader.service.TextToSpeechService::class.java).apply {
        action = com.nam.novelreader.service.TextToSpeechService.ACTION_SET_VOLUME_GAIN
        putExtra(com.nam.novelreader.service.TextToSpeechService.EXTRA_VOLUME_GAIN, volumeGain)
    }
    context.startService(intent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleApiKeyDialog(
    currentKey: String,
    textColor: Color,
    bgColor: Color,
    primaryColor: Color,
    cardColor: Color,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val defaultKeys = listOf(
        "AIzaSyA33f9cSqKdR-V4XNkZNZ_rh_dbT1VQJFo",
        "AIzaSyA6mOHhF5xLAjOqCHepfQprTYPjmKVFmKA",
        "AIzaSyCRZVR4LpsA2hIxn8wkbnaSxxduHheAvhc"
    )

    var selectedKeyOption by remember {
        mutableStateOf(
            if (currentKey in defaultKeys) currentKey else "custom"
        )
    }

    var customKeyValue by remember {
        mutableStateOf(
            if (currentKey !in defaultKeys) currentKey else ""
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = bgColor,
        title = {
            Text(
                text = "Cấu hình Google API Key",
                color = textColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Chọn API Key sử dụng cho giọng đọc Google Online. Nếu key mặc định bị giới hạn lượt đọc, bạn có thể chuyển đổi sang key khác hoặc dán key của riêng bạn.",
                    color = textColor.copy(alpha = 0.7f),
                    fontSize = 13.sp
                )

                // Option 1
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selectedKeyOption == defaultKeys[0]) cardColor else Color.Transparent)
                        .clickable { selectedKeyOption = defaultKeys[0] }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (selectedKeyOption == defaultKeys[0]),
                        onClick = { selectedKeyOption = defaultKeys[0] },
                        colors = RadioButtonDefaults.colors(selectedColor = primaryColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Key mặc định 1", color = textColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(defaultKeys[0].take(10) + "..." + defaultKeys[0].takeLast(6), color = textColor.copy(alpha = 0.5f), fontSize = 11.sp)
                    }
                }

                // Option 2
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selectedKeyOption == defaultKeys[1]) cardColor else Color.Transparent)
                        .clickable { selectedKeyOption = defaultKeys[1] }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (selectedKeyOption == defaultKeys[1]),
                        onClick = { selectedKeyOption = defaultKeys[1] },
                        colors = RadioButtonDefaults.colors(selectedColor = primaryColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Key mặc định 2 (VBook)", color = textColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(defaultKeys[1].take(10) + "..." + defaultKeys[1].takeLast(6), color = textColor.copy(alpha = 0.5f), fontSize = 11.sp)
                    }
                }

                // Option 3
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selectedKeyOption == defaultKeys[2]) cardColor else Color.Transparent)
                        .clickable { selectedKeyOption = defaultKeys[2] }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (selectedKeyOption == defaultKeys[2]),
                        onClick = { selectedKeyOption = defaultKeys[2] },
                        colors = RadioButtonDefaults.colors(selectedColor = primaryColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Key mặc định 3 (Premium)", color = textColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(defaultKeys[2].take(10) + "..." + defaultKeys[2].takeLast(6), color = textColor.copy(alpha = 0.5f), fontSize = 11.sp)
                    }
                }

                // Option Custom
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selectedKeyOption == "custom") cardColor else Color.Transparent)
                        .clickable { selectedKeyOption = "custom" }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (selectedKeyOption == "custom"),
                        onClick = { selectedKeyOption = "custom" },
                        colors = RadioButtonDefaults.colors(selectedColor = primaryColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tự nhập Google API Key...", color = textColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                if (selectedKeyOption == "custom") {
                    OutlinedTextField(
                        value = customKeyValue,
                        onValueChange = { customKeyValue = it },
                        placeholder = { Text("Nhập Google API Key của bạn", color = textColor.copy(alpha = 0.4f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textColor,
                            unfocusedTextColor = textColor,
                            cursorColor = primaryColor,
                            focusedBorderColor = primaryColor,
                            unfocusedBorderColor = textColor.copy(alpha = 0.3f),
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val keyToSave = if (selectedKeyOption == "custom") {
                        customKeyValue.trim()
                    } else {
                        selectedKeyOption
                    }
                    onSave(keyToSave)
                },
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
            ) {
                Text("Lưu", color = bgColor)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = textColor)
            ) {
                Text("Hủy")
            }
        }
    )
}
