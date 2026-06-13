package com.nam.novelreader.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.nam.novelreader.MainActivity

import com.nam.novelreader.data.preferences.AppPreferences
import com.nam.novelreader.extension.loader.ExtensionLoader
import com.nam.novelreader.extension.model.ExtensionResult
import com.nam.novelreader.extension.model.ScriptType
import com.nam.novelreader.extension.runtime.VBookJsExtensionRunner
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okio.ByteString
import kotlin.coroutines.resume

@Serializable
private data class ServiceWordReplacementRule(
    val pattern: String,
    val replacement: String
)

@AndroidEntryPoint
class TextToSpeechService : Service(), TextToSpeech.OnInitListener {

    @Inject
    lateinit var appPrefs: AppPreferences

    @Inject
    lateinit var extensionLoader: ExtensionLoader

    @Inject
    lateinit var jsExtensionRunner: VBookJsExtensionRunner

    @Inject
    lateinit var repository: com.nam.novelreader.data.repository.NovelRepository

    companion object {
        const val CHANNEL_ID = "tts_service_channel"
        const val NOTIFICATION_ID = 1002

        const val ACTION_START = "com.nam.novelreader.action.TTS_START"
        const val ACTION_PLAY_PAUSE = "com.nam.novelreader.action.TTS_PLAY_PAUSE"
        const val ACTION_STOP = "com.nam.novelreader.action.TTS_STOP"
        const val ACTION_SET_SPEED = "com.nam.novelreader.action.TTS_SET_SPEED"
        const val ACTION_SET_PITCH = "com.nam.novelreader.action.TTS_SET_PITCH"
        const val EXTRA_PITCH = "extra_pitch"
        const val ACTION_TEST_VOICE = "com.nam.novelreader.action.TTS_TEST_VOICE"
        const val ACTION_SET_VOLUME_GAIN = "com.nam.novelreader.action.TTS_SET_VOLUME_GAIN"
        const val EXTRA_VOLUME_GAIN = "extra_volume_gain"
        const val ACTION_SEEK_TO_SENTENCE = "com.nam.novelreader.action.TTS_SEEK_TO_SENTENCE"
        const val EXTRA_SENTENCE_INDEX = "extra_sentence_index"
        const val ACTION_RELOAD_TTS = "com.nam.novelreader.action.TTS_RELOAD"
        const val ACTION_REWIND_SENTENCE = "com.nam.novelreader.action.TTS_REWIND_SENTENCE"
        const val ACTION_FORWARD_SENTENCE = "com.nam.novelreader.action.TTS_FORWARD_SENTENCE"
        const val ACTION_NEXT_CHAPTER = "com.nam.novelreader.action.TTS_NEXT_CHAPTER"
        const val ACTION_PREV_CHAPTER = "com.nam.novelreader.action.TTS_PREV_CHAPTER"
        const val ACTION_SET_TIMER = "com.nam.novelreader.action.TTS_SET_TIMER"
        const val ACTION_SET_BG_MUSIC = "com.nam.novelreader.action.TTS_SET_BG_MUSIC"
        const val EXTRA_BG_MUSIC = "extra_bg_music"

        const val ACTION_TTS_CHAPTER_COMPLETED = "com.nam.novelreader.action.TTS_CHAPTER_COMPLETED"
        const val EXTRA_COMPLETED_CHAPTER_URL = "extra_completed_chapter_url"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_CONTENT = "extra_content"
        const val EXTRA_SPEED = "extra_speed"
        const val EXTRA_TIMER_DURATION = "extra_timer_duration" // minutes (Long)
        const val EXTRA_CHAPTER_URL = "extra_chapter_url"
        const val EXTRA_NOVEL_URL = "extra_novel_url"
        const val EXTRA_EXTENSION_ID = "extra_extension_id"

        const val BROADCAST_TTS_STATUS = "com.nam.novelreader.broadcast.TTS_STATUS"
        const val EXTRA_STATUS_ACTIVE = "extra_status_active"
        const val EXTRA_STATUS_PLAYING = "extra_status_playing"
        const val EXTRA_STATUS_TEXT = "extra_status_text"
        const val EXTRA_STATUS_INDEX = "extra_status_index"
        const val EXTRA_STATUS_TIMER_REMAINING = "extra_status_timer_remaining" // seconds (Int)
        const val EXTRA_STATUS_CHAPTER_URL = "extra_status_chapter_url"
        const val EXTRA_STATUS_SPEED = "extra_status_speed"
        
        const val ENGINE_AZURE_EDGE = "azure_edge"
        const val VOICE_NAM_MINH = "vi-VN-NamMinhNeural"
        const val VOICE_HOAI_MY = "vi-VN-HoaiMyNeural"
        // Giọng nam trầm: dùng NamMinhNeural với pitch thấp hơn qua SSML
        const val VOICE_NAM_TRAM = "vi-VN-NamMinhNeural-Deep"
        const val VOICE_NAM_TRAM_PITCH = "-20%"

        val googleCloudVoiceMap = mapOf(
            "vi-vn-x-vif-local" to "via",
            "vi-vn-x-vic-local" to "vib",
            "vi-vn-x-vid-local" to "vic",
            "vi-vn-x-vie-local" to "vid",
            "vi-vn-x-vfg-local" to "vie",
            "vi-vn-x-vfa-local" to "vif"
        )
    }

    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false
    private var isPlaying = false
    private var speed = 1.0f
    private var pitch = 1.0f
    private var volumeGain = 0.0f
    private var errorCount = 0

    private var audioPlayer: MediaPlayer? = null
    private var bgMediaPlayer: MediaPlayer? = null
    private var testAudioPlayer: MediaPlayer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var testLoudnessEnhancer: LoudnessEnhancer? = null
    private var isTestingVoice = false
    private var selectedBgMusic = "Không có"

    private var chapterTitle = ""
    private var chapterUrl = ""
    private var rawContentText = ""
    private var sentences = listOf<String>()
    private var currentSentenceIndex = 0
    private var sentenceWordCounts = listOf<Int>()

    private var novelUrl = ""
    private var extensionId = ""
    private var nextChapterUrl = ""
    private var nextChapterTitle = ""
    private var nextChapterContent = ""

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var timerJob: Job? = null
    private var timerRemainingSeconds = 0

    private val downloadJobs = ConcurrentHashMap<Int, Job>()
    private val downloadedFiles = ConcurrentHashMap<Int, String>()
    private var playJob: Job? = null

    private var mediaSession: android.media.session.MediaSession? = null

    private val audioFocusRequest: AudioFocusRequest? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { focusChange ->
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
                        focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        if (isPlaying) {
                            togglePlayPause()
                        }
                    }
                }
                .build()
        } else {
            null
        }
    }

    private fun initSystemTtsIfNeeded() {
        if (tts == null) {
            val preferredEngine = appPrefs.ttsSystemEngine
            tts = if (preferredEngine.isNotEmpty()) {
                TextToSpeech(this, this, preferredEngine)
            } else {
                TextToSpeech(this, this)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        speed = appPrefs.ttsSpeed
        pitch = appPrefs.ttsPitch
        volumeGain = appPrefs.ttsVolumeGain
        createNotificationChannel()
        if (appPrefs.ttsSelectedEngine == "system") {
            val voice = appPrefs.ttsSelectedVoice
            val isGoogleOnline = voice in googleCloudVoiceMap.keys
            if (!isGoogleOnline) {
                initSystemTtsIfNeeded()
            }
        }
        setupMediaSession()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
                val content = intent.getStringExtra(EXTRA_CONTENT) ?: ""
                chapterUrl = intent.getStringExtra(EXTRA_CHAPTER_URL) ?: ""
                novelUrl = intent.getStringExtra(EXTRA_NOVEL_URL) ?: ""
                extensionId = intent.getStringExtra(EXTRA_EXTENSION_ID) ?: ""
                
                if (content.isEmpty() && chapterUrl.isNotEmpty() && extensionId.isNotEmpty()) {
                    serviceScope.launch {
                        val contentLoaded = withContext(Dispatchers.IO) {
                            try {
                                repository.getChapterContent(extensionId, chapterUrl)?.content ?: ""
                            } catch (e: Exception) {
                                Log.e("TTS", "Error fetching chapter content in TTS service: ${e.message}", e)
                                ""
                            }
                        }
                        startTts(title, contentLoaded)
                    }
                } else {
                    startTts(title, content)
                }
            }
            ACTION_PLAY_PAUSE -> {
                togglePlayPause()
            }
            ACTION_STOP -> {
                stopSelf()
            }
            ACTION_SET_SPEED -> {
                val newSpeed = intent.getFloatExtra(EXTRA_SPEED, 1.0f)
                setTtsSpeed(newSpeed)
            }
            ACTION_SET_PITCH -> {
                val newPitch = intent.getFloatExtra(EXTRA_PITCH, 1.0f)
                setTtsPitch(newPitch)
            }
            ACTION_SET_VOLUME_GAIN -> {
                val newVolumeGain = intent.getFloatExtra(EXTRA_VOLUME_GAIN, 0.0f)
                setTtsVolumeGain(newVolumeGain)
            }
            ACTION_SEEK_TO_SENTENCE -> {
                val index = intent.getIntExtra(EXTRA_SENTENCE_INDEX, 0)
                seekToSentence(index)
            }
            ACTION_TEST_VOICE -> {
                val content = intent.getStringExtra(EXTRA_CONTENT) ?: ""
                startTestVoice(content)
            }
            ACTION_RELOAD_TTS -> {
                reloadTtsSettings()
            }
            ACTION_REWIND_SENTENCE -> {
                rewindSentence()
            }
            ACTION_FORWARD_SENTENCE -> {
                forwardSentence()
            }
            ACTION_NEXT_CHAPTER -> {
                if (nextChapterUrl.isNotEmpty() && nextChapterContent.isNotEmpty()) {
                    serviceScope.launch(Dispatchers.Main) {
                        val nextUrl = nextChapterUrl
                        val nextTitle = nextChapterTitle
                        val nextContent = nextChapterContent
                        
                        chapterUrl = nextUrl
                        chapterTitle = nextTitle
                        
                        nextChapterUrl = ""
                        nextChapterTitle = ""
                        nextChapterContent = ""
                        
                        startTts(chapterTitle, nextContent)
                        sendUpdateBroadcast()
                    }
                } else {
                    val completedIntent = Intent(ACTION_TTS_CHAPTER_COMPLETED).apply {
                        putExtra(EXTRA_COMPLETED_CHAPTER_URL, chapterUrl)
                    }
                    sendBroadcast(completedIntent)
                }
            }
            ACTION_PREV_CHAPTER -> {
                val completedIntent = Intent(ACTION_TTS_CHAPTER_COMPLETED).apply {
                    putExtra(EXTRA_COMPLETED_CHAPTER_URL, chapterUrl)
                    putExtra("is_prev", true)
                }
                sendBroadcast(completedIntent)
            }
            ACTION_SET_TIMER -> {
                val durationMinutes = intent.getLongExtra(EXTRA_TIMER_DURATION, 0L)
                setTimer(durationMinutes)
            }
            ACTION_SET_BG_MUSIC -> {
                val musicName = intent.getStringExtra(EXTRA_BG_MUSIC) ?: "Không có"
                setBgMusic(musicName)
            }
        }
        return START_NOT_STICKY
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val langParts = appPrefs.ttsSystemLanguage.split("-")
            var locale = if (langParts.size >= 2) {
                Locale(langParts[0], langParts[1])
            } else {
                Locale(appPrefs.ttsSystemLanguage)
            }
            
            var result = tts?.setLanguage(locale)
            
            // Fallback 1: Try language code only (e.g. Locale("vi") instead of Locale("vi", "VN"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                locale = Locale(langParts[0])
                result = tts?.setLanguage(locale)
            }
            
            // Fallback 2: Try default locale of the system
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                locale = Locale.getDefault()
                result = tts?.setLanguage(locale)
            }
            
            // Fallback 3: Try US English as a last resort
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                locale = Locale.US
                result = tts?.setLanguage(locale)
            }

            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val voiceId = appPrefs.ttsSelectedVoice
                    if (voiceId.isNotEmpty() && appPrefs.ttsSelectedEngine == "system") {
                        tts?.voices?.find { it.name == voiceId }?.let {
                            tts?.voice = it
                        }
                    }
                }
                isTtsInitialized = true
                tts?.setSpeechRate(speed)
                tts?.setPitch(appPrefs.ttsPitch)
                setupUtteranceListener()
                
                // Check if media volume is muted
                try {
                    val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    if (currentVolume == 0) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            Toast.makeText(applicationContext, "Âm lượng phương tiện đang tắt. Vui lòng tăng âm lượng để nghe đọc!", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    // ignore
                }

                if (isPlaying && appPrefs.ttsSelectedEngine == "system" && sentences.isNotEmpty()) {
                    speakCurrent()
                }
            } else {
                Log.e("TTS", "No language supported by TTS engine")
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(applicationContext, "Lỗi: Động cơ TTS không hỗ trợ ngôn ngữ này!", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            // Fallback: If custom engine initialization failed, try system default engine
            val currentPreferred = appPrefs.ttsSystemEngine
            if (currentPreferred.isNotEmpty()) {
                Log.w("TTS", "Failed to initialize preferred engine $currentPreferred, falling back to system default engine")
                appPrefs.ttsSystemEngine = "" // clear preferred engine to avoid loops
                
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(applicationContext, "Lỗi động cơ ${currentPreferred}. Đang thử động cơ mặc định...", Toast.LENGTH_SHORT).show()
                }
                
                tts?.shutdown()
                tts = TextToSpeech(this, this)
            } else {
                Log.e("TTS", "Failed to initialize system default TTS engine")
                val isGoogleOnline = appPrefs.ttsSelectedEngine == "system" && appPrefs.ttsSelectedVoice in googleCloudVoiceMap.keys
                if (!isGoogleOnline) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Toast.makeText(applicationContext, "Không thể khởi tạo động cơ TTS hệ thống!", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun setupUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (utteranceId == "test_voice") return
                sendUpdateBroadcast()
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId == "test_voice") {
                    isTestingVoice = false
                    return
                }
                errorCount = 0
                currentSentenceIndex++
                if (currentSentenceIndex < sentences.size) {
                    if (isPlaying) speakCurrent()
                } else {
                    if (isPlaying) {
                        onPlaybackFinished()
                    } else {
                        currentSentenceIndex = sentences.size
                        sendUpdateBroadcast()
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId == "test_voice") {
                    isTestingVoice = false
                    return
                }
                Log.e("TTS", "System TTS utterance error: $utteranceId")
                isPlaying = false
                sendUpdateBroadcast()
            }
        })
    }

    private fun setupMediaSession() {
        if (!appPrefs.ttsHeadphoneControl) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mediaSession = android.media.session.MediaSession(this, "VBookTtsSession").apply {
                setCallback(object : android.media.session.MediaSession.Callback() {
                    override fun onPlay() {
                        if (!isPlaying) togglePlayPause()
                    }
                    override fun onPause() {
                        if (isPlaying) togglePlayPause()
                    }
                    override fun onSkipToNext() {
                        forwardSentence()
                    }
                    override fun onSkipToPrevious() {
                        rewindSentence()
                    }
                    override fun onStop() {
                        stopSelf()
                    }
                })
                isActive = true
            }
        }
    }

    private fun requestAudioFocus() {
        if (!appPrefs.ttsAudioFocus) return
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.requestAudioFocus(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                { focusChange ->
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
                        focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        if (isPlaying) {
                            togglePlayPause()
                        }
                    }
                },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
    }

    private fun applyWordReplacements(text: String): String {
        val rulesJson = appPrefs.ttsWordReplacements
        if (rulesJson.isBlank()) return text
        var result = text
        try {
            val rules = Json.decodeFromString<List<ServiceWordReplacementRule>>(rulesJson)
            for (rule in rules) {
                if (rule.pattern.isNotEmpty()) {
                    result = result.replace(rule.pattern, rule.replacement)
                }
            }
        } catch (e: Exception) {
            Log.e("TTS", "Failed to apply word replacements", e)
        }
        return result
    }

    private fun startTts(title: String, content: String) {
        chapterTitle = title
        val cleanText = cleanHtmlText(content)
        rawContentText = cleanText
        val preprocessedText = preprocessTextForTts(applyWordReplacements(cleanText))
        sentences = splitIntoSentences(preprocessedText)
        sentenceWordCounts = sentences.map { s -> s.split(Regex("\\s+")).filter { it.isNotEmpty() }.size }
        currentSentenceIndex = 0
        isPlaying = true
        speed = appPrefs.ttsSpeed
        pitch = appPrefs.ttsPitch
        volumeGain = appPrefs.ttsVolumeGain

        clearTtsCache()
        prefetchNextChapter()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }

        requestAudioFocus()

        val engine = appPrefs.ttsSelectedEngine
        val voice = appPrefs.ttsSelectedVoice
        val isGoogleOnline = engine == "system" && voice in googleCloudVoiceMap.keys

        if (engine == "system" && !isGoogleOnline) {
            audioPlayer?.stop()
            audioPlayer?.release()
            audioPlayer = null
            
            if (isTtsInitialized) {
                speakCurrent()
            }
        } else {
            tts?.stop()
            setupAudioPlayer()
            speakCurrentAi()
        }
    }

    private fun prefetchNextChapter() {
        nextChapterUrl = ""
        nextChapterTitle = ""
        nextChapterContent = ""
        if (novelUrl.isEmpty() || extensionId.isEmpty() || chapterUrl.isEmpty() || chapterUrl == "sample_tts_test") return
        serviceScope.launch(Dispatchers.IO) {
            try {
                val chapters = repository.getChapterList(novelUrl)
                val currentIndex = chapters.indexOfFirst { it.url == chapterUrl }
                if (currentIndex != -1 && currentIndex < chapters.size - 1) {
                    val nextChEntity = chapters[currentIndex + 1]
                    val nextUrl = nextChEntity.url
                    val nextTitle = nextChEntity.title
                    
                    val nextCh = repository.getChapterContent(extensionId, nextUrl)
                    if (nextCh != null && !nextCh.content.isNullOrBlank()) {
                        nextChapterUrl = nextUrl
                        nextChapterTitle = nextTitle
                        nextChapterContent = nextCh.content
                        Log.d("TTS", "Prefetched next chapter successfully: $nextTitle")
                    }
                }
            } catch (e: Exception) {
                Log.e("TTS", "Failed to prefetch next chapter: ${e.message}", e)
            }
        }
    }

    private fun cleanHtmlText(html: String): String {
        return html.replace(Regex("<[^>]*>"), "")
            .replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun splitParagraphIfNeeded(para: String, maxLength: Int, result: MutableList<String>) {
        if (para.length <= maxLength) {
            result.add(para)
        } else {
            var remaining = para
            while (remaining.isNotEmpty()) {
                if (remaining.length <= maxLength) {
                    result.add(remaining)
                    break
                }
                var splitIndex = remaining.lastIndexOf(' ', maxLength)
                if (splitIndex <= 0) {
                    splitIndex = maxLength
                }
                result.add(remaining.substring(0, splitIndex).trim())
                remaining = remaining.substring(splitIndex).trim()
            }
        }
    }

    private fun splitLongSentence(sentence: String, maxLength: Int = 120): List<String> {
        if (sentence.length <= maxLength) return listOf(sentence)
        val result = mutableListOf<String>()
        var remaining = sentence
        val punctuation = listOf(',', ';', ':', '，', '；', '：')
        while (remaining.length > maxLength) {
            var splitIdx = -1
            for (p in punctuation) {
                val idx = remaining.lastIndexOf(p, maxLength)
                if (idx > splitIdx) {
                    splitIdx = idx
                }
            }
            if (splitIdx > 15) {
                val chunk = remaining.substring(0, splitIdx + 1).trim()
                if (chunk.isNotEmpty()) result.add(chunk)
                remaining = remaining.substring(splitIdx + 1).trim()
            } else {
                val spaceIdx = remaining.lastIndexOf(' ', maxLength)
                if (spaceIdx > 0) {
                    val chunk = remaining.substring(0, spaceIdx).trim()
                    if (chunk.isNotEmpty()) result.add(chunk)
                    remaining = remaining.substring(spaceIdx).trim()
                } else {
                    val chunk = remaining.substring(0, maxLength).trim()
                    if (chunk.isNotEmpty()) result.add(chunk)
                    remaining = remaining.substring(maxLength).trim()
                }
            }
        }
        if (remaining.isNotEmpty()) {
            result.add(remaining)
        }
        return result
    }

    private fun splitIntoSentences(text: String): List<String> {
        val splitType = appPrefs.ttsSplitType
        val maxLength = appPrefs.ttsMaxLength.coerceAtLeast(100)
        
        // Split text by lines first
        val paragraphs = text.split(Regex("\n+")).map { it.trim() }.filter { it.isNotEmpty() }
        
        return when (splitType) {
            "Theo đoạn", "Theo độ dài" -> {
                val result = mutableListOf<String>()
                for (para in paragraphs) {
                    val tempChunks = mutableListOf<String>()
                    splitParagraphIfNeeded(para, maxLength, tempChunks)
                    for (chunk in tempChunks) {
                        result.addAll(splitLongSentence(chunk))
                    }
                }
                result
            }
            else -> { // "Theo câu"
                val sentencesList = mutableListOf<String>()
                for (para in paragraphs) {
                    val rawSentences = para.split(Regex("(?<=[.!?])\\s+"))
                    for (raw in rawSentences) {
                        val trimmed = raw.trim()
                        if (trimmed.isNotEmpty()) {
                            sentencesList.addAll(splitLongSentence(trimmed))
                        }
                    }
                }
                sentencesList
            }
        }
    }


    private fun speakCurrent() {
        initSystemTtsIfNeeded()
        if (currentSentenceIndex < sentences.size && isPlaying) {
            val text = sentences[currentSentenceIndex]
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, currentSentenceIndex.toString())
            }
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, currentSentenceIndex.toString())
            updateNotification()
        }
    }

    private fun setupAudioPlayer() {
        if (audioPlayer == null) {
            audioPlayer = MediaPlayer().apply {
                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                setAudioAttributes(attributes)
                setOnCompletionListener {
                    onSentenceCompleted()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("TTS", "MediaPlayer error: what=$what, extra=$extra")
                    handleTtsError()
                    true
                }
            }
        }
    }

    private fun speakCurrentAi() {
        playJob?.cancel()
        playJob = serviceScope.launch {
            if (currentSentenceIndex >= sentences.size || !isPlaying) return@launch

            startPrefetching(currentSentenceIndex)

            val sentenceFile = getOrWaitForSentenceFile(currentSentenceIndex)
            if (sentenceFile != null && isPlaying) {
                playAudioFile(sentenceFile)
            } else if (isPlaying) {
                handleTtsError()
            }
        }
    }

    private fun startPrefetching(currentIndex: Int) {
        val iterator = downloadJobs.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key < currentIndex) {
                entry.value.cancel()
                iterator.remove()
                downloadedFiles.remove(entry.key)?.let { path ->
                    try {
                        File(path).delete()
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }
        }

        val limit = (currentIndex + 6).coerceAtMost(sentences.size - 1)
        for (i in currentIndex..limit) {
            if (!downloadedFiles.containsKey(i) && !downloadJobs.containsKey(i)) {
                val job = serviceScope.launch(Dispatchers.IO) {
                    downloadSentence(i)
                }
                downloadJobs[i] = job
            }
        }
    }
    private suspend fun downloadSentence(index: Int) {
        val engine = appPrefs.ttsSelectedEngine
        val voice = appPrefs.ttsSelectedVoice
        val text = sentences[index]
        
        val isGoogleOnline = engine == "system" && voice in googleCloudVoiceMap.keys
        if (isGoogleOnline) {
            val googleVoiceCode = googleCloudVoiceMap[voice] ?: "via"
            try {
                downloadGoogleCloudSentence(index, text, googleVoiceCode)
            } catch (e: Exception) {
                Log.e("TTS", "Failed to download Google Cloud sentence $index", e)
                downloadedFiles[index] = "FAILED"
            } finally {
                downloadJobs.remove(index)
            }
            return
        }
        
        if (engine == ENGINE_AZURE_EDGE) {
            val voiceName = if (voice.isEmpty()) VOICE_NAM_MINH else voice
            try {
                downloadAzureEdgeSentence(index, text, voiceName)
            } catch (e: Exception) {
                Log.e("TTS", "Failed to download Azure Edge sentence $index", e)
                downloadedFiles[index] = "FAILED"
            } finally {
                downloadJobs.remove(index)
            }
            return
        }

        try {
            val extension = extensionLoader.loadExtension(engine)
            if (extension == null) {
                Log.e("TTS", "Extension not found: $engine")
                downloadedFiles[index] = "FAILED"
                appPrefs.ttsSelectedEngine = "system"
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(applicationContext, "Không tìm thấy tiện ích $engine, tự động chuyển về TTS hệ thống!", android.widget.Toast.LENGTH_LONG).show()
                    
                    audioPlayer?.stop()
                    audioPlayer?.release()
                    audioPlayer = null
                    
                    if (isTtsInitialized) {
                        speakCurrent()
                    } else {
                        val preferredEngine = appPrefs.ttsSystemEngine
                        tts?.shutdown()
                        tts = if (preferredEngine.isNotEmpty()) {
                            TextToSpeech(applicationContext, this@TextToSpeechService, preferredEngine)
                        } else {
                            TextToSpeech(applicationContext, this@TextToSpeechService)
                        }
                    }
                }
                return
            }

            val result = jsExtensionRunner.execute(extension, ScriptType.TTS, text, voice)
            if (result is ExtensionResult.Success) {
                val jsonObject = Json.parseToJsonElement(result.data).jsonObject
                val base64Data = jsonObject["data"]?.jsonPrimitive?.content ?: ""
                
                if (base64Data.isNotEmpty()) {
                    val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                    val cacheFile = File(cacheDir, "tts_sentence_$index.mp3")
                    cacheFile.writeBytes(bytes)
                    
                    downloadedFiles[index] = cacheFile.absolutePath
                    Log.d("TTS", "Successfully prefetched sentence $index")
                } else {
                    Log.e("TTS", "Empty data in TTS response for sentence $index")
                    downloadedFiles[index] = "FAILED"
                }
            } else if (result is ExtensionResult.Error) {
                Log.e("TTS", "TTS script error for sentence $index: ${result.message}")
                downloadedFiles[index] = "FAILED"
            }
        } catch (e: Exception) {
            Log.e("TTS", "Failed to download sentence $index", e)
            downloadedFiles[index] = "FAILED"
        } finally {
            downloadJobs.remove(index)
        }
    }

    private suspend fun getOrWaitForSentenceFile(index: Int): String? {
        val startTime = System.currentTimeMillis()
        val timeoutMs = 15000L
        var broadcastSent = false
        
        while (isPlaying) {
            val path = downloadedFiles[index]
            if (path == "FAILED") {
                return null
            }
            if (path != null && File(path).exists()) {
                return path
            }
            
            if (!downloadJobs.containsKey(index) && downloadedFiles[index] == null) {
                startPrefetching(index)
            }
            
            if (System.currentTimeMillis() - startTime > 300 && !broadcastSent) {
                broadcastSent = true
                sendUpdateBroadcast(isWaitingAudio = true)
            }
            
            if (System.currentTimeMillis() - startTime > timeoutMs) {
                Log.e("TTS", "Timeout waiting for sentence $index file")
                return null
            }
            
            delay(100)
        }
        return null
    }

    private fun playAudioFile(path: String) {
        try {
            audioPlayer?.reset()
            setupAudioPlayer()
            audioPlayer?.setDataSource(path)
            audioPlayer?.prepare()
            
            // Setup LoudnessEnhancer for positive volume gain
            try {
                loudnessEnhancer?.release()
                loudnessEnhancer = null
                val audioSessionId = audioPlayer?.audioSessionId
                if (audioSessionId != null && audioSessionId != 0 && volumeGain > 0f) {
                    val enhancer = LoudnessEnhancer(audioSessionId)
                    enhancer.setTargetGain((volumeGain * 100).toInt())
                    enhancer.enabled = true
                    loudnessEnhancer = enhancer
                }
            } catch (e: Exception) {
                Log.e("TTS", "Failed to setup LoudnessEnhancer for playAudioFile", e)
            }
            
            // Apply volume gain directly to MediaPlayer (scale down if negative, or keep 1.0f if positive)
            val vol = if (volumeGain < 0f) Math.pow(10.0, volumeGain / 20.0).toFloat().coerceIn(0.0f, 1.0f) else 1.0f
            audioPlayer?.setVolume(vol, vol)
            
            val isGoogleOnline = appPrefs.ttsSelectedEngine == "system" && appPrefs.ttsSelectedVoice in googleCloudVoiceMap.keys
            val isAzureEdge = appPrefs.ttsSelectedEngine == ENGINE_AZURE_EDGE
            val useServerSideSpeed = isGoogleOnline || isAzureEdge
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val targetSpeed = if (useServerSideSpeed) 1.0f else speed
                    val params = android.media.PlaybackParams().setSpeed(targetSpeed)
                    audioPlayer?.playbackParams = params
                } catch (e: Exception) {
                    Log.e("TTS", "Failed to set playback speed", e)
                }
            }
            
            audioPlayer?.start()
            errorCount = 0
            updateNotification()
            sendUpdateBroadcast()
        } catch (e: Exception) {
            Log.e("TTS", "Error playing audio file: $path", e)
            handleTtsError()
        }
    }

    private fun onSentenceCompleted() {
        currentSentenceIndex++
        if (currentSentenceIndex < sentences.size) {
            if (isPlaying) {
                val engine = appPrefs.ttsSelectedEngine
                val voice = appPrefs.ttsSelectedVoice
                val isGoogleOnline = engine == "system" && voice in googleCloudVoiceMap.keys
                if (engine == "system" && !isGoogleOnline) {
                    speakCurrent()
                } else {
                    speakCurrentAi()
                }
            }
        } else {
            if (isPlaying) {
                if (chapterUrl == "sample_tts_test") {
                    onPlaybackFinished()
                    return
                }
                val rawPrefs = getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE)
                val ttsAutoNext = rawPrefs.getBoolean("tts_auto_next_chapter", true)
                if (ttsAutoNext && nextChapterUrl.isNotEmpty() && nextChapterContent.isNotEmpty()) {
                    serviceScope.launch(Dispatchers.Main) {
                        val nextUrl = nextChapterUrl
                        val nextTitle = nextChapterTitle
                        val nextContent = nextChapterContent
                        
                        chapterUrl = nextUrl
                        chapterTitle = nextTitle
                        
                        nextChapterUrl = ""
                        nextChapterTitle = ""
                        nextChapterContent = ""
                        
                        startTts(chapterTitle, nextContent)
                        sendUpdateBroadcast()
                    }
                } else {
                    onPlaybackFinished()
                }
            } else {
                currentSentenceIndex = sentences.size
                sendUpdateBroadcast()
            }
        }
    }

    private fun onPlaybackFinished() {
        val wasPlaying = isPlaying
        isPlaying = false
        bgMediaPlayer?.pause()
        sendUpdateBroadcast()
        
        if (wasPlaying && chapterUrl != "sample_tts_test") {
            val completedIntent = Intent(ACTION_TTS_CHAPTER_COMPLETED).apply {
                putExtra(EXTRA_COMPLETED_CHAPTER_URL, chapterUrl)
            }
            sendBroadcast(completedIntent)
        }
        
        stopForeground(STOP_FOREGROUND_DETACH)
    }

    private fun handleTtsError() {
        errorCount++
        Log.w("TTS", "TTS error occurred, errorCount=$errorCount")
        if (errorCount >= 3) {
            isPlaying = false
            errorCount = 0
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(applicationContext, "Lỗi kết nối mạng hoặc lỗi giọng đọc AI liên tục. Đã tạm dừng đọc!", Toast.LENGTH_LONG).show()
            }
            sendUpdateBroadcast()
        } else {
            onSentenceCompleted()
        }
    }

    private fun clearTtsCache() {
        downloadJobs.values.forEach { it.cancel() }
        downloadJobs.clear()
        downloadedFiles.clear()
        
        try {
            cacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("tts_sentence_") && file.name.endsWith(".mp3")) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.e("TTS", "Error clearing tts cache files", e)
        }
    }

    private fun setBgMusic(musicName: String) {
        selectedBgMusic = musicName
        bgMediaPlayer?.stop()
        bgMediaPlayer?.release()
        bgMediaPlayer = null
        
        if (musicName == "Không có") return
        
        val url = when (musicName) {
            "Mưa rơi nhẹ" -> "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"
            "Nhạc thiền thư giãn" -> "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3"
            "Tiếng sóng biển" -> "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3"
            else -> null
        }
        
        if (url != null) {
            val rawPrefs = getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE)
            val volume = rawPrefs.getFloat("reader_tts_bg_music_volume", 0.3f)
            bgMediaPlayer = MediaPlayer().apply {
                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                setAudioAttributes(attributes)
                try {
                    setDataSource(url)
                    setVolume(volume, volume)
                    isLooping = true
                    prepareAsync()
                    setOnPreparedListener { 
                        if (isPlaying) start() 
                    }
                } catch (e: Exception) {
                    Log.e("TTS", "Lỗi phát nhạc nền: ${e.message}")
                }
            }
        }
    }

    private fun togglePlayPause() {
        val engine = appPrefs.ttsSelectedEngine
        val voice = appPrefs.ttsSelectedVoice
        val isGoogleOnline = engine == "system" && voice in googleCloudVoiceMap.keys
        if (isPlaying) {
            isPlaying = false
            if (engine == "system" && !isGoogleOnline) {
                tts?.stop()
            } else {
                audioPlayer?.pause()
            }
            bgMediaPlayer?.pause()
        } else {
            isPlaying = true
            requestAudioFocus()
            if (engine == "system" && !isGoogleOnline) {
                speakCurrent()
            } else {
                if (audioPlayer != null && !audioPlayer!!.isPlaying && downloadedFiles.containsKey(currentSentenceIndex)) {
                    // Áp dụng volume gain trực tiếp lên MediaPlayer trước khi phát tiếp
                    val vol = if (volumeGain < 0f) Math.pow(10.0, volumeGain / 20.0).toFloat().coerceIn(0.0f, 1.0f) else 1.0f
                    audioPlayer?.setVolume(vol, vol)
                    
                    // Setup LoudnessEnhancer for positive volume gain
                    try {
                        val audioSessionId = audioPlayer?.audioSessionId
                        if (audioSessionId != null && audioSessionId != 0 && volumeGain > 0f) {
                            if (loudnessEnhancer == null) {
                                loudnessEnhancer = LoudnessEnhancer(audioSessionId)
                            }
                            loudnessEnhancer?.setTargetGain((volumeGain * 100).toInt())
                            loudnessEnhancer?.enabled = true
                        }
                    } catch (e: Exception) {
                        // ignore
                    }
                    
                    audioPlayer?.start()
                    val isGoogleOnline = appPrefs.ttsSelectedEngine == "system" && appPrefs.ttsSelectedVoice in googleCloudVoiceMap.keys
                    val isAzureEdge = appPrefs.ttsSelectedEngine == ENGINE_AZURE_EDGE
                    val useServerSideSpeed = isGoogleOnline || isAzureEdge
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        try {
                            val targetSpeed = if (useServerSideSpeed) 1.0f else speed
                            val params = android.media.PlaybackParams().setSpeed(targetSpeed)
                            audioPlayer?.playbackParams = params
                        } catch (e: Exception) {
                            Log.e("TTS", "Failed to set playback speed", e)
                        }
                    }
                } else {
                    speakCurrentAi()
                }
            }
            bgMediaPlayer?.start()
        }
        updateNotification()
        sendUpdateBroadcast()
    }

    private fun rewindSentence() {
        if (sentences.isNotEmpty()) {
            currentSentenceIndex = (currentSentenceIndex - 1).coerceAtLeast(0)
            val engine = appPrefs.ttsSelectedEngine
            val voice = appPrefs.ttsSelectedVoice
            val isGoogleOnline = engine == "system" && voice in googleCloudVoiceMap.keys
            if (engine == "system" && !isGoogleOnline) {
                if (isTtsInitialized) speakCurrent()
            } else {
                speakCurrentAi()
            }
            sendUpdateBroadcast()
        }
    }

    private fun forwardSentence() {
        if (sentences.isNotEmpty()) {
            if (currentSentenceIndex < sentences.size - 1) {
                currentSentenceIndex++
                val engine = appPrefs.ttsSelectedEngine
                val voice = appPrefs.ttsSelectedVoice
                val isGoogleOnline = engine == "system" && voice in googleCloudVoiceMap.keys
                if (engine == "system" && !isGoogleOnline) {
                    if (isTtsInitialized) speakCurrent()
                } else {
                    speakCurrentAi()
                }
                sendUpdateBroadcast()
            } else {
                onPlaybackFinished()
            }
        }
    }

    private fun seekToSentence(index: Int) {
        if (sentences.isNotEmpty()) {
            currentSentenceIndex = index.coerceIn(0, sentences.size - 1)
            val engine = appPrefs.ttsSelectedEngine
            val voice = appPrefs.ttsSelectedVoice
            val isGoogleOnline = engine == "system" && voice in googleCloudVoiceMap.keys
            if (engine == "system" && !isGoogleOnline) {
                if (isTtsInitialized && isPlaying) speakCurrent()
            } else {
                if (isPlaying) speakCurrentAi()
            }
            sendUpdateBroadcast()
        }
    }

    private fun setTimer(durationMinutes: Long) {
        timerJob?.cancel()
        if (durationMinutes > 0) {
            timerRemainingSeconds = (durationMinutes * 60).toInt()
            timerJob = serviceScope.launch {
                while (timerRemainingSeconds > 0) {
                    delay(1000)
                    timerRemainingSeconds--
                    sendUpdateBroadcast()
                }
                stopSelf()
            }
        } else {
            timerRemainingSeconds = 0
            sendUpdateBroadcast()
        }
    }

    private fun setTtsSpeed(newSpeed: Float) {
        speed = newSpeed
        appPrefs.ttsSpeed = newSpeed
        val engine = appPrefs.ttsSelectedEngine
        val voice = appPrefs.ttsSelectedVoice
        val isGoogleOnline = engine == "system" && voice in googleCloudVoiceMap.keys
        val isAzureEdge = engine == ENGINE_AZURE_EDGE
        val useServerSideSpeed = isGoogleOnline || isAzureEdge
        
        if (engine == "system" && !isGoogleOnline) {
            if (isTtsInitialized) {
                tts?.setSpeechRate(speed)
                if (isPlaying) {
                    speakCurrent()
                }
            }
        } else if (useServerSideSpeed) {
            if (isPlaying) {
                clearTtsCache()
                speakCurrentAi()
            }
        } else {
            if (isPlaying && audioPlayer?.isPlaying == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        val params = android.media.PlaybackParams().setSpeed(speed)
                        audioPlayer?.playbackParams = params
                    } catch (e: Exception) {
                        Log.e("TTS", "Failed to set playback speed", e)
                    }
                }
            }
        }
    }

    private fun setTtsPitch(newPitch: Float) {
        pitch = newPitch
        appPrefs.ttsPitch = newPitch
        val engine = appPrefs.ttsSelectedEngine
        val voice = appPrefs.ttsSelectedVoice
        val isGoogleOnline = engine == "system" && voice in googleCloudVoiceMap.keys
        val isAzureEdge = engine == ENGINE_AZURE_EDGE
        if (engine == "system" && !isGoogleOnline) {
            if (isTtsInitialized) {
                tts?.setPitch(pitch)
                if (isPlaying) {
                    speakCurrent()
                }
            }
        } else if (isGoogleOnline || isAzureEdge) {
            if (isPlaying) {
                clearTtsCache()
                speakCurrentAi()
            }
        }
    }

    private fun setTtsVolumeGain(newVolumeGain: Float) {
        volumeGain = newVolumeGain
        appPrefs.ttsVolumeGain = newVolumeGain
        
        // Cập nhật âm lượng tức thì cho MediaPlayer đang phát
        try {
            val vol = if (volumeGain < 0f) Math.pow(10.0, volumeGain / 20.0).toFloat().coerceIn(0.0f, 1.0f) else 1.0f
            audioPlayer?.setVolume(vol, vol)
            testAudioPlayer?.setVolume(vol, vol)
            
            if (volumeGain > 0f) {
                audioPlayer?.audioSessionId?.let { id ->
                    if (id != 0) {
                        if (loudnessEnhancer == null) {
                            loudnessEnhancer = LoudnessEnhancer(id)
                        }
                        loudnessEnhancer?.setTargetGain((volumeGain * 100).toInt())
                        loudnessEnhancer?.enabled = true
                    }
                }
                testAudioPlayer?.audioSessionId?.let { id ->
                    if (id != 0) {
                        if (testLoudnessEnhancer == null) {
                            testLoudnessEnhancer = LoudnessEnhancer(id)
                        }
                        testLoudnessEnhancer?.setTargetGain((volumeGain * 100).toInt())
                        testLoudnessEnhancer?.enabled = true
                    }
                }
            } else {
                loudnessEnhancer?.enabled = false
                testLoudnessEnhancer?.enabled = false
            }
        } catch (e: Exception) {
            // ignore
        }

        val engine = appPrefs.ttsSelectedEngine
        val isAzureEdge = engine == ENGINE_AZURE_EDGE
        
        // Chỉ có Azure Edge mới cần tải lại vì volume được cấu hình cứng trong SSML gửi lên Server
        if (isAzureEdge) {
            if (isPlaying) {
                clearTtsCache()
                speakCurrentAi()
            }
        }
    }

    private fun reloadTtsSettings() {
        val newEngine = appPrefs.ttsSelectedEngine
        val newVoice = appPrefs.ttsSelectedVoice
        val isGoogleOnline = newEngine == "system" && newVoice in googleCloudVoiceMap.keys
        speed = appPrefs.ttsSpeed
        val newPitch = appPrefs.ttsPitch
        
        if (newEngine == "system" && !isGoogleOnline) {
            audioPlayer?.stop()
            audioPlayer?.release()
            audioPlayer = null
            
            if (tts == null) {
                initSystemTtsIfNeeded()
            } else if (isTtsInitialized) {
                tts?.setSpeechRate(speed)
                tts?.setPitch(newPitch)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val voiceId = appPrefs.ttsSelectedVoice
                    if (voiceId.isNotEmpty()) {
                        tts?.voices?.find { it.name == voiceId }?.let {
                            tts?.voice = it
                        }
                    }
                }
            }
        } else {
            tts?.stop()
            setupAudioPlayer()
        }
        
        if (rawContentText.isNotEmpty()) {
            val preprocessedText = preprocessTextForTts(applyWordReplacements(rawContentText))
            val newSentences = splitIntoSentences(preprocessedText)
            sentences = newSentences
            sentenceWordCounts = sentences.map { s -> s.split(Regex("\\s+")).filter { it.isNotEmpty() }.size }
            if (currentSentenceIndex >= sentences.size) {
                currentSentenceIndex = (sentences.size - 1).coerceAtLeast(0)
            }
        }
        
        if (isPlaying) {
            if (newEngine == "system" && !isGoogleOnline) {
                speakCurrent()
            } else {
                speakCurrentAi()
            }
        } else {
            updateNotification()
            sendUpdateBroadcast()
        }
    }

    private fun sendUpdateBroadcast(isActive: Boolean = true, isWaitingAudio: Boolean = false) {
        val intent = Intent(BROADCAST_TTS_STATUS).apply {
            putExtra(EXTRA_STATUS_ACTIVE, isActive)
            putExtra(EXTRA_STATUS_PLAYING, isPlaying)
            putExtra(EXTRA_STATUS_INDEX, currentSentenceIndex)
            val baseText = if (currentSentenceIndex < sentences.size) sentences[currentSentenceIndex] else ""
            val displayText = if (isWaitingAudio) "$baseText (Đang tải audio...)" else baseText
            putExtra(EXTRA_STATUS_TEXT, displayText)
            putExtra(EXTRA_STATUS_TIMER_REMAINING, timerRemainingSeconds)
            putExtra(EXTRA_STATUS_CHAPTER_URL, chapterUrl)
            putExtra(EXTRA_STATUS_SPEED, speed)
            putExtra("extra_status_total", sentences.size)
            putExtra("extra_status_bg_music", selectedBgMusic)
            
            // Tính toán và gửi thời lượng ước tính
            val elapsedWords = if (sentenceWordCounts.isNotEmpty()) sentenceWordCounts.take(currentSentenceIndex).sum() else 0
            val totalWords = sentenceWordCounts.sum()
            val elapsedSeconds = (elapsedWords * 0.4f / speed).toInt()
            val totalSeconds = (totalWords * 0.4f / speed).toInt()
            putExtra("extra_status_elapsed_seconds", elapsedSeconds)
            putExtra("extra_status_total_seconds", totalSeconds)
        }
        sendBroadcast(intent)
    }

    private fun buildNotification(): Notification {
        val intentPlayPause = Intent(this, TextToSpeechService::class.java).apply { action = ACTION_PLAY_PAUSE }
        val piPlayPause = PendingIntent.getService(this, 1, intentPlayPause, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val intentStop = Intent(this, TextToSpeechService::class.java).apply { action = ACTION_STOP }
        val piStop = PendingIntent.getService(this, 2, intentStop, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val openAppIntent = Intent(this, MainActivity::class.java)
        val piOpenApp = PendingIntent.getActivity(this, 0, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val actionIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val actionText = if (isPlaying) "Tạm dừng" else "Tiếp tục"

        val currentText = if (currentSentenceIndex < sentences.size) sentences[currentSentenceIndex] else "Đang đọc..."

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(chapterTitle)
            .setContentText(currentText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(piOpenApp)
            .addAction(actionIcon, actionText, piPlayPause)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dừng", piStop)
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Nghe đọc truyện (TTS)",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        timerJob?.cancel()
        serviceJob.cancel()
        
        tts?.stop()
        tts?.shutdown()
        
        audioPlayer?.stop()
        audioPlayer?.release()
        audioPlayer = null
        
        bgMediaPlayer?.stop()
        bgMediaPlayer?.release()
        bgMediaPlayer = null
        
        loudnessEnhancer?.release()
        loudnessEnhancer = null
        testLoudnessEnhancer?.release()
        testLoudnessEnhancer = null
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mediaSession?.isActive = false
            mediaSession?.release()
            mediaSession = null
        }
        
        clearTtsCache()
        
        isPlaying = false
        sendUpdateBroadcast(isActive = false)
        super.onDestroy()
    }

    private fun generateSecMsGecToken(): String {
        val randomOffset = Random().nextInt(61) + 30
        val currentTimeSec = (System.currentTimeMillis() / 1000.0) - randomOffset + 11644473600L
        val roundedTime = currentTimeSec - (currentTimeSec % 300.0)
        val ticks = (roundedTime * 1.0E7).toLong()
        val input = "${ticks}6A5AA1D4EAFF4E9FB37E23D68491D6F4"
        
        return try {
            val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            val sb = StringBuilder()
            for (b in digest) {
                val hex = Integer.toHexString(b.toInt() and 0xFF)
                if (hex.length == 1) {
                    sb.append('0')
                }
                sb.append(hex)
            }
            sb.toString().uppercase()
        } catch (e: Exception) {
            Log.e("TTS", "Error generating Sec-MS-GEC token", e)
            ""
        }
    }

    private fun findByteArrayIndex(parent: ByteArray, child: ByteArray): Int {
        if (child.isEmpty() || parent.size < child.size) return -1
        for (i in 0..parent.size - child.size) {
            var found = true
            for (j in child.indices) {
                if (parent[i + j] != child[j]) {
                    found = false
                    break
                }
            }
            if (found) return i
        }
        return -1
    }

    private fun extractAudioBytes(data: ByteArray): ByteArray? {
        if (data.size <= 2) return null
        val headerLen = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
        if (2 + headerLen <= data.size) {
            val headerString = String(data, 2, headerLen, Charsets.UTF_8)
            if (headerString.contains("Path:audio")) {
                return data.copyOfRange(2 + headerLen, data.size)
            }
        }
        val marker = "Path:audio\r\n".toByteArray(Charsets.UTF_8)
        val index = findByteArrayIndex(data, marker)
        if (index != -1) {
            return data.copyOfRange(index + marker.size, data.size)
        }
        return null
    }

    private suspend fun downloadAzureEdgeSentence(index: Int, text: String, voiceName: String): Boolean = suspendCancellableCoroutine { continuation ->
        val client = OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val connectionId = UUID.randomUUID().toString().replace("-", "")
        val secMsGec = generateSecMsGecToken()
        val url = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1" +
                "?TrustedClientToken=6A5AA1D4EAFF4E9FB37E23D68491D6F4" +
                "&Sec-MS-GEC=$secMsGec" +
                "&Sec-MS-GEC-Version=1-134.0.3124.66" +
                "&ConnectionId=$connectionId"

        val request = Request.Builder()
            .url(url)
            .addHeader("Origin", "chrome-extension://jdlujaimjcdjbhbacklagiffaodbboak")
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36 Edg/134.0.0.0")
            .build()

        val bos = ByteArrayOutputStream()
        var hasResumed = false

        val webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val configMsg = "Content-Type:application/json; charset=utf-8\r\n" +
                        "Path:speech.config\r\n\r\n" +
                        "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"},\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}"
                webSocket.send(configMsg)

                val escapedText = text.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&apos;")
                
                val requestId = UUID.randomUUID().toString().replace("-", "")
                // Giọng nam trầm: mánh khóa dùng NamMinhNeural + pitch thấp
                val isDeepMale = voiceName == VOICE_NAM_TRAM
                val resolvedVoiceName = if (isDeepMale) VOICE_NAM_MINH else voiceName
                
                // Cấu hình pitch động cho Edge TTS
                val basePercent = if (isDeepMale) -20 else 0
                val diffPercent = ((pitch - 1.0f) * 100).toInt()
                val totalPercent = basePercent + diffPercent
                val ssmlPitch = if (totalPercent >= 0) "+$totalPercent%" else "$totalPercent%"
                
                // Cấu hình volume gain động cho Edge TTS (%) thay vì dB để tránh lỗi Connection reset
                val vGain = volumeGain.coerceIn(-10f, 10f)
                val percent = (vGain * 10).toInt()
                val ssmlVolume = if (percent >= 0) "+$percent%" else "$percent%"
                
                // Cấu hình speed factor động gửi lên Server cho Edge TTS
                val ratePercent = ((speed - 1.0f) * 100).toInt()
                val ssmlRate = if (ratePercent >= 0) "+$ratePercent%" else "$ratePercent%"
                
                val ssmlMsg = "X-RequestId:$requestId\r\n" +
                        "Content-Type:application/ssml+xml\r\n" +
                        "Path:ssml\r\n\r\n" +
                        "<speak version=\"1.0\" xmlns=\"http://www.w3.org/2001/10/synthesis\" xmlns:mstts=\"https://www.w3.org/2001/mstts\" xml:lang=\"vi-VN\">" +
                        "<voice name=\"$resolvedVoiceName\">" +
                        "<prosody rate=\"$ssmlRate\" pitch=\"$ssmlPitch\" volume=\"$ssmlVolume\">" +
                        escapedText +
                        "</prosody></voice></speak>"
                webSocket.send(ssmlMsg)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val data = bytes.toByteArray()
                val audioData = extractAudioBytes(data)
                if (audioData != null) {
                    bos.write(audioData)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("Path:turn.end")) {
                    webSocket.close(1000, "Normal closure")
                    val audioBytes = bos.toByteArray()
                    if (audioBytes.isNotEmpty()) {
                        val cacheFile = File(cacheDir, "tts_sentence_$index.mp3")
                        try {
                            cacheFile.writeBytes(audioBytes)
                            downloadedFiles[index] = cacheFile.absolutePath
                            Log.d("TTS", "Successfully downloaded Azure Edge sentence $index: ${cacheFile.length()} bytes")
                            if (!hasResumed) {
                                hasResumed = true
                                continuation.resume(true)
                            }
                        } catch (e: Exception) {
                            Log.e("TTS", "Failed to write Azure Edge audio cache", e)
                            downloadedFiles[index] = "FAILED"
                            if (!hasResumed) {
                                hasResumed = true
                                continuation.resume(false)
                            }
                        }
                    } else {
                        Log.e("TTS", "Received empty audio from Azure Edge")
                        downloadedFiles[index] = "FAILED"
                        if (!hasResumed) {
                            hasResumed = true
                            continuation.resume(false)
                        }
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("TTS", "WebSocket failure: ${t.message}", t)
                downloadedFiles[index] = "FAILED"
                if (!hasResumed) {
                    hasResumed = true
                    continuation.resume(false)
                }
                webSocket.close(1001, "Failure")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!hasResumed) {
                    hasResumed = true
                    continuation.resume(false)
                }
            }
        })

        continuation.invokeOnCancellation {
            webSocket.close(1001, "Cancelled")
        }
    }

    private fun escapeJsonString(text: String): String {
        val builder = java.lang.StringBuilder()
        for (element in text) {
            when (element) {
                '\\' -> builder.append("\\\\")
                '\"' -> builder.append("\\\"")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                '\t' -> builder.append("\\t")
                else -> {
                    if (element.code < 0x20) {
                        builder.append(String.format("\\u%04x", element.code))
                    } else {
                        builder.append(element)
                    }
                }
            }
        }
        return builder.toString()
    }

    private fun tryDirectPremiumApi(
        client: OkHttpClient,
        index: Int,
        text: String,
        voiceCode: String,
        escapedText: String,
        speedFactor: Double,
        pitchFactor: Double,
        continuation: kotlinx.coroutines.CancellableContinuation<Boolean>
    ) {
        val premiumApiKey = "AIzaSyCRZVR4LpsA2hIxn8wkbnaSxxduHheAvhc"
        val googleUrl = "https://readaloud.googleapis.com/v1:generateAudioDocStream?key=$premiumApiKey"

        val payload = """
            {
              "text": {
                "textParts": ["$escapedText"]
              },
              "advanced_options": {
                "force_language": "vi",
                "audio_generation_options": {
                  "speed_factor": $speedFactor,
                  "pitch_factor": $pitchFactor
                }
              },
              "voice_settings": {
                "voice_criteria_and_selections": [
                  {
                    "criteria": {
                      "language": "vi"
                    },
                    "selection": {
                      "default_voice": "$voiceCode"
                    }
                  }
                ]
              }
            }
        """.trimIndent()

        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val requestBody = okhttp3.RequestBody.create(mediaType, payload)

        val request = Request.Builder()
            .url(googleUrl)
            .addHeader("Content-Type", "application/json")
            .addHeader("X-Goog-Api-Key", premiumApiKey)
            .post(requestBody)
            .build()

        var hasResumed = false

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                Log.w("TTS", "Premium Google Cloud API failure, falling back to keyless v2: ${e.message}")
                if (!hasResumed) {
                    hasResumed = true
                    fallbackToKeylessApi(index, text, voiceCode, continuation)
                }
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        Log.w("TTS", "Premium Google Cloud API response not success (${resp.code}), falling back to keyless v2")
                        if (!hasResumed) {
                            hasResumed = true
                            fallbackToKeylessApi(index, text, voiceCode, continuation)
                        }
                        return
                    }

                    try {
                        val responseStr = resp.body?.string() ?: ""
                        val jsonArray = org.json.JSONArray(responseStr)
                        if (jsonArray.length() > 2) {
                            val audioObj = jsonArray.optJSONObject(2)
                            val audioNode = audioObj?.optJSONObject("audio")
                            val base64Bytes = audioNode?.optString("bytes", "") ?: ""
                            if (base64Bytes.isNotEmpty()) {
                                val audioBytes = Base64.decode(base64Bytes, Base64.DEFAULT)
                                val cacheFile = File(cacheDir, "tts_sentence_$index.mp3")
                                cacheFile.writeBytes(audioBytes)
                                downloadedFiles[index] = cacheFile.absolutePath
                                Log.d("TTS", "Successfully downloaded Premium Google Cloud sentence $index: ${cacheFile.length()} bytes")
                                if (!hasResumed) {
                                    hasResumed = true
                                    continuation.resume(true)
                                }
                                return
                            }
                        }
                        Log.w("TTS", "Premium Google Cloud response missing audio bytes, falling back to keyless v2")
                        if (!hasResumed) {
                            hasResumed = true
                            fallbackToKeylessApi(index, text, voiceCode, continuation)
                        }
                    } catch (e: Exception) {
                        Log.w("TTS", "Error parsing Premium Google Cloud response, falling back to keyless v2: ${e.message}", e)
                        if (!hasResumed) {
                            hasResumed = true
                            fallbackToKeylessApi(index, text, voiceCode, continuation)
                        }
                    }
                }
            }
        })
    }

    private suspend fun downloadGoogleCloudSentence(index: Int, text: String, voiceCode: String): Boolean = suspendCancellableCoroutine { continuation ->
        val client = OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        // Round to 1 decimal place as required by Google Premium API
        val speedFactor = Math.round(speed * 10) / 10.0
        val pitchFactor = Math.round(pitch * 10) / 10.0

        val escapedText = escapeJsonString(text)

        // 1. Thử gọi Web Proxy của thienthu.vip trước — cùng logic premium API với web
        val proxyUrl = "https://thienthu.vip/api/tts/google-free?voice=$voiceCode&rate=$speed&pitch=$pitch"
        val proxyPayload = """{"text": "$escapedText"}"""
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val proxyRequestBody = okhttp3.RequestBody.create(mediaType, proxyPayload)

        val proxyRequest = Request.Builder()
            .url(proxyUrl)
            .addHeader("Content-Type", "application/json")
            .post(proxyRequestBody)
            .build()

        var hasResumed = false

        client.newCall(proxyRequest).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                Log.w("TTS", "Web Proxy API failure, trying direct Premium API: ${e.message}")
                if (!hasResumed) {
                    hasResumed = true
                    tryDirectPremiumApi(client, index, text, voiceCode, escapedText, speedFactor, pitchFactor, continuation)
                }
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        Log.w("TTS", "Web Proxy API response not success (${resp.code}), trying direct Premium API")
                        if (!hasResumed) {
                            hasResumed = true
                            tryDirectPremiumApi(client, index, text, voiceCode, escapedText, speedFactor, pitchFactor, continuation)
                        }
                        return
                    }
                    try {
                        val audioBytes = resp.body?.bytes()
                        if (audioBytes != null && audioBytes.isNotEmpty()) {
                            val cacheFile = File(cacheDir, "tts_sentence_$index.mp3")
                            cacheFile.writeBytes(audioBytes)
                            downloadedFiles[index] = cacheFile.absolutePath
                            Log.d("TTS", "Successfully downloaded Web Proxy sentence $index: ${cacheFile.length()} bytes")
                            if (!hasResumed) {
                                hasResumed = true
                                continuation.resume(true)
                            }
                        } else {
                            Log.w("TTS", "Web Proxy response empty body, trying direct Premium API")
                            if (!hasResumed) {
                                  hasResumed = true
                                tryDirectPremiumApi(client, index, text, voiceCode, escapedText, speedFactor, pitchFactor, continuation)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("TTS", "Error reading Web Proxy response, trying direct Premium API: ${e.message}")
                        if (!hasResumed) {
                            hasResumed = true
                            tryDirectPremiumApi(client, index, text, voiceCode, escapedText, speedFactor, pitchFactor, continuation)
                        }
                    }
                }
            }
        })

        continuation.invokeOnCancellation {
            // Cancel call if job cancelled
        }
    }

    private fun fallbackToKeylessApi(index: Int, text: String, voiceCode: String, continuation: kotlinx.coroutines.CancellableContinuation<Boolean>) {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val voiceName = when (voiceCode) {
            "via" -> "vi-VN-Wavenet-A"
            "vib" -> "vi-VN-Wavenet-B"
            "vic" -> "vi-VN-Wavenet-C"
            "vid" -> "vi-VN-Wavenet-D"
            "vie" -> "vi-VN-Standard-A"
            "vif" -> "vi-VN-Standard-D"
            else -> "vi-VN-Wavenet-A"
        }

        val keylessSpeed = (speed * 0.5f).coerceIn(0.1f, 1.0f)
        val keylessPitch = (pitch * 0.5f).coerceIn(0.1f, 1.0f)

        val speedStr = String.format(Locale.US, "%.2f", keylessSpeed)
        val pitchStr = String.format(Locale.US, "%.2f", keylessPitch)

        val encodedText = try {
            java.net.URLEncoder.encode(text, "UTF-8")
        } catch (e: Exception) {
            text
        }

        val apiKey = appPrefs.ttsGoogleApiKey.ifBlank { "AIzaSyA33f9cSqKdR-V4XNkZNZ_rh_dbT1VQJFo" }
        val url = "https://www.google.com/speech-api/v2/synthesize" +
                "?client=android-tts/com.apps.google:1.1.0" +
                "&lang=vi-VN" +
                "&key=$apiKey" +
                "&name=$voiceName" +
                "&rate=24000" +
                "&speed=$speedStr" +
                "&pitch=$pitchStr" +
                "&text=$encodedText" +
                "&enc=mpeg"

        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
            .addHeader("Accept", "*/*")
            .build()

        var hasResumed = false

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                Log.e("TTS", "Fallback keyless Google Cloud API failure: ${e.message}", e)
                downloadedFiles[index] = "FAILED"
                if (!hasResumed) {
                    hasResumed = true
                    continuation.resume(false)
                }
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        Log.e("TTS", "Fallback keyless Google Cloud API error response: code=${resp.code}")
                        downloadedFiles[index] = "FAILED"
                        if (!hasResumed) {
                            hasResumed = true
                            continuation.resume(false)
                        }
                        return
                    }

                    try {
                        val audioBytes = resp.body?.bytes()
                        if (audioBytes != null && audioBytes.isNotEmpty()) {
                            val cacheFile = File(cacheDir, "tts_sentence_$index.mp3")
                            cacheFile.writeBytes(audioBytes)
                            downloadedFiles[index] = cacheFile.absolutePath
                            Log.d("TTS", "Successfully downloaded keyless Google Cloud sentence $index: ${cacheFile.length()} bytes")
                            if (!hasResumed) {
                                hasResumed = true
                                continuation.resume(true)
                            }
                        } else {
                            Log.e("TTS", "Empty audio response from keyless Google Cloud API")
                            downloadedFiles[index] = "FAILED"
                            if (!hasResumed) {
                                hasResumed = true
                                continuation.resume(false)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("TTS", "Error processing keyless Google Cloud audio: ${e.message}", e)
                        downloadedFiles[index] = "FAILED"
                        if (!hasResumed) {
                            hasResumed = true
                            continuation.resume(false)
                        }
                    }
                }
            }
        })
    }


    private fun preprocessTextForTts(text: String): String {
        var result = text
        
        // 1. Remove dialogue dash at the start of paragraphs/lines or replace with space/comma
        result = result.replace(Regex("(?m)^\\s*[-—–]+\\s*"), " ")
        result = result.replace(Regex("\\s+[-—–]+\\s+"), ", ")

        // 2. Clear repeated exclamation/question marks
        result = result.replace(Regex("!+"), "!")
        result = result.replace(Regex("\\?+"), "?")
        result = result.replace(Regex("~+"), ", ")
        
        // 3. Replace ellipses and multiple dots with a comma to force a pause without pronouncing dots
        result = result.replace(Regex("\\.{2,}"), ", ")

        // 4. Normalize spacing after punctuation marks (ensure there is a space after comma/period/exclamation/question if not followed by a digit)
        result = result.replace(Regex("([,.;!?])(?=[^\\s0-9])"), "$1 ")

        // 5. dialogue tag colons: replace "nói:", "hỏi:" etc with "nói, ", "hỏi, "
        result = result.replace(Regex("(?i)(?<=\\b(nói|hỏi|rằng|kêu|thét|hét|lẩm bẩm|thì thào|hô|quát|đáp|nhắc|gầm|hừ|vặn|hất hàm))\\s*:\\s*"), ", ")

        // 6. Clean up quotation marks to avoid reading them or confusing the engine
        result = result.replace(Regex("[\"“”‘’']"), " ")

        // 7. Expand common abbreviations
        result = result.replaceWord("ko", "không")
        result = result.replaceWord("đc", "được")
        result = result.replaceWord("dc", "được")
        result = result.replaceWord("khg", "không")
        result = result.replaceWord("chx", "chưa")

        // 8. Insert commas before conjunctions in long runs of text to create natural breathing pauses
        val wordRegex = "(?:(?!(?i:nhưng|mà|thì|bởi\\s+vì|cho\\s+nên|tuy\\s+nhiên)\\b)[^,.;!?\\s()\\-\\[\\]\"'“‘’”]+)"
        val conjunctionRegex = Regex("($wordRegex(?:\\s+$wordRegex){5,})\\s+(nhưng|mà|thì|bởi\\s+vì|cho\\s+nên|tuy\\s+nhiên)\\b", RegexOption.IGNORE_CASE)
        result = result.replace(conjunctionRegex, "$1, $2")

        // 9. Clean up double spaces
        result = result.replace(Regex("\\s+"), " ").trim()

        return result
    }

    private fun startTestVoice(content: String) {
        // 1. Tạm dừng trình phát chính nếu đang chạy để tránh âm thanh đè nhau
        if (isPlaying) {
            isPlaying = false
            val engine = appPrefs.ttsSelectedEngine
            val voice = appPrefs.ttsSelectedVoice
            val isGoogleOnline = engine == "system" && voice in googleCloudVoiceMap.keys
            if (engine == "system" && !isGoogleOnline) {
                tts?.stop()
            } else {
                audioPlayer?.pause()
            }
            bgMediaPlayer?.pause()
            sendUpdateBroadcast()
        }

        // Dừng test voice cũ nếu đang chạy
        try {
            testAudioPlayer?.stop()
            testAudioPlayer?.reset()
        } catch (e: Exception) {
            // ignore
        }
        isTestingVoice = true

        val engine = appPrefs.ttsSelectedEngine
        val voice = appPrefs.ttsSelectedVoice
        val isGoogleOnline = engine == "system" && voice in googleCloudVoiceMap.keys

        if (engine == "system" && !isGoogleOnline) {
            initSystemTtsIfNeeded()
            if (isTtsInitialized) {
                val params = Bundle().apply {
                    putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "test_voice")
                }
                tts?.setSpeechRate(speed)
                tts?.setPitch(pitch)
                tts?.speak(content, TextToSpeech.QUEUE_FLUSH, params, "test_voice")
            } else {
                isTestingVoice = false
            }
        } else {
            // Tải bất đồng bộ câu test voice
            serviceScope.launch {
                val testIndex = -999
                val voiceCode = googleCloudVoiceMap[voice] ?: "via"
                
                // Xóa cache test cũ
                val cacheFile = File(cacheDir, "tts_sentence_$testIndex.mp3")
                if (cacheFile.exists()) {
                    try { cacheFile.delete() } catch(e: Exception) {}
                }
                downloadedFiles.remove(testIndex)

                if (isGoogleOnline) {
                    downloadGoogleCloudSentence(testIndex, content, voiceCode)
                } else if (engine == ENGINE_AZURE_EDGE) {
                    val voiceName = if (voice.isEmpty()) VOICE_NAM_MINH else voice
                    downloadAzureEdgeSentence(testIndex, content, voiceName)
                } else {
                    // JS Extension
                    try {
                        val extension = extensionLoader.loadExtension(engine)
                        if (extension != null) {
                            val result = jsExtensionRunner.execute(extension, ScriptType.TTS, content, voice)
                            if (result is ExtensionResult.Success) {
                                val jsonObject = Json.parseToJsonElement(result.data).jsonObject
                                val base64Data = jsonObject["data"]?.jsonPrimitive?.content ?: ""
                                if (base64Data.isNotEmpty()) {
                                    val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                                    cacheFile.writeBytes(bytes)
                                    downloadedFiles[testIndex] = cacheFile.absolutePath
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("TTS", "Error downloading test sentence via JS extension", e)
                    }
                }

                val path = downloadedFiles[testIndex]
                if (path != null && path != "FAILED" && File(path).exists()) {
                    playTestAudioFile(path)
                } else {
                    isTestingVoice = false
                }
            }
        }
    }

    private fun playTestAudioFile(path: String) {
        try {
            if (testAudioPlayer == null) {
                testAudioPlayer = MediaPlayer().apply {
                    val attributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                    setAudioAttributes(attributes)
                    setOnCompletionListener {
                        isTestingVoice = false
                        it.reset()
                    }
                    setOnErrorListener { _, what, extra ->
                        isTestingVoice = false
                        true
                    }
                }
            } else {
                testAudioPlayer?.reset()
            }
            testAudioPlayer?.setDataSource(path)
            testAudioPlayer?.prepare()
            
            // Setup LoudnessEnhancer for positive volume gain
            try {
                testLoudnessEnhancer?.release()
                testLoudnessEnhancer = null
                val audioSessionId = testAudioPlayer?.audioSessionId
                if (audioSessionId != null && audioSessionId != 0 && volumeGain > 0f) {
                    val enhancer = LoudnessEnhancer(audioSessionId)
                    enhancer.setTargetGain((volumeGain * 100).toInt())
                    enhancer.enabled = true
                    testLoudnessEnhancer = enhancer
                }
            } catch (e: Exception) {
                Log.e("TTS", "Failed to setup LoudnessEnhancer for testAudioPlayer", e)
            }

            // Áp dụng volume gain trực tiếp lên test audio player (scale down if negative, or keep 1.0f if positive)
            val vol = if (volumeGain < 0f) Math.pow(10.0, volumeGain / 20.0).toFloat().coerceIn(0.0f, 1.0f) else 1.0f
            testAudioPlayer?.setVolume(vol, vol)
            
            val isGoogleOnline = appPrefs.ttsSelectedEngine == "system" && appPrefs.ttsSelectedVoice in googleCloudVoiceMap.keys
            val isAzureEdge = appPrefs.ttsSelectedEngine == ENGINE_AZURE_EDGE
            val useServerSideSpeed = isGoogleOnline || isAzureEdge
            
            // Áp dụng speed cho test audio player
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val targetSpeed = if (useServerSideSpeed) 1.0f else speed
                    val params = android.media.PlaybackParams().setSpeed(targetSpeed)
                    testAudioPlayer?.playbackParams = params
                } catch (e: Exception) {
                    // ignore
                }
            }
            
            testAudioPlayer?.start()
        } catch (e: Exception) {
            Log.e("TTS", "Error playing test audio file: $path", e)
            isTestingVoice = false
        }
    }

    private fun String.replaceWord(target: String, replacement: String): String {
        val regex = Regex("(?<=^|\\s|[,.;!?\"'“()\\-\\[\\]])$target(?=$|\\s|[,.;!?\"'”()\\-\\[\\]])", RegexOption.IGNORE_CASE)
        return this.replace(regex, replacement)
    }
}
