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
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

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
    }

    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false
    private var isPlaying = false
    private var speed = 1.0f

    private var audioPlayer: MediaPlayer? = null
    private var bgMediaPlayer: MediaPlayer? = null
    private var selectedBgMusic = "Không có"

    private var chapterTitle = ""
    private var chapterUrl = ""
    private var rawContentText = ""
    private var sentences = listOf<String>()
    private var currentSentenceIndex = 0

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
        createNotificationChannel()
        if (appPrefs.ttsSelectedEngine == "system") {
            initSystemTtsIfNeeded()
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
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(applicationContext, "Không thể khởi tạo động cơ TTS hệ thống!", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setupUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                sendUpdateBroadcast()
            }

            override fun onDone(utteranceId: String?) {
                currentSentenceIndex++
                if (currentSentenceIndex < sentences.size) {
                    if (isPlaying) speakCurrent()
                } else {
                    onPlaybackFinished()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
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
        val filteredText = applyWordReplacements(cleanText)
        sentences = splitIntoSentences(filteredText)
        currentSentenceIndex = 0
        isPlaying = true
        speed = appPrefs.ttsSpeed

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
        if (engine == "system") {
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
        if (novelUrl.isEmpty() || extensionId.isEmpty() || chapterUrl.isEmpty()) return
        nextChapterUrl = ""
        nextChapterTitle = ""
        nextChapterContent = ""
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

    private fun splitIntoSentences(text: String): List<String> {
        val splitType = appPrefs.ttsSplitType
        val maxLength = appPrefs.ttsMaxLength.coerceAtLeast(100)
        
        // Split text by lines first
        val paragraphs = text.split(Regex("\n+")).map { it.trim() }.filter { it.isNotEmpty() }
        
        return when (splitType) {
            "Theo đoạn", "Theo độ dài" -> {
                val result = mutableListOf<String>()
                for (para in paragraphs) {
                    splitParagraphIfNeeded(para, maxLength, result)
                }
                result
            }
            else -> { // "Theo câu"
                val sentencesList = mutableListOf<String>()
                for (para in paragraphs) {
                    val rawSentences = para.split(Regex("(?<=[.!?])\\s+"))
                    sentencesList.addAll(rawSentences.map { it.trim() }.filter { it.isNotEmpty() })
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
                    onSentenceCompleted()
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
                onSentenceCompleted()
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

        val limit = (currentIndex + 3).coerceAtMost(sentences.size - 1)
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
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    audioPlayer?.playbackParams = audioPlayer?.playbackParams?.setSpeed(speed) 
                        ?: android.media.PlaybackParams().setSpeed(speed)
                } catch (e: Exception) {
                    Log.e("TTS", "Failed to set playback speed", e)
                }
            }
            
            audioPlayer?.start()
            updateNotification()
            sendUpdateBroadcast()
        } catch (e: Exception) {
            Log.e("TTS", "Error playing audio file: $path", e)
            onSentenceCompleted()
        }
    }

    private fun onSentenceCompleted() {
        currentSentenceIndex++
        if (currentSentenceIndex < sentences.size) {
            if (isPlaying) {
                val engine = appPrefs.ttsSelectedEngine
                if (engine == "system") {
                    speakCurrent()
                } else {
                    speakCurrentAi()
                }
            }
        } else {
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
        }
    }

    private fun onPlaybackFinished() {
        isPlaying = false
        bgMediaPlayer?.pause()
        sendUpdateBroadcast()
        
        val completedIntent = Intent(ACTION_TTS_CHAPTER_COMPLETED).apply {
            putExtra(EXTRA_COMPLETED_CHAPTER_URL, chapterUrl)
        }
        sendBroadcast(completedIntent)
        
        stopForeground(STOP_FOREGROUND_DETACH)
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
        if (isPlaying) {
            isPlaying = false
            if (engine == "system") {
                tts?.stop()
            } else {
                audioPlayer?.pause()
            }
            bgMediaPlayer?.pause()
        } else {
            isPlaying = true
            requestAudioFocus()
            if (engine == "system") {
                speakCurrent()
            } else {
                if (audioPlayer != null && !audioPlayer!!.isPlaying && downloadedFiles.containsKey(currentSentenceIndex)) {
                    audioPlayer?.start()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        try {
                            audioPlayer?.playbackParams = audioPlayer?.playbackParams?.setSpeed(speed) 
                                ?: android.media.PlaybackParams().setSpeed(speed)
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
            if (engine == "system") {
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
                if (engine == "system") {
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
        val engine = appPrefs.ttsSelectedEngine
        if (engine == "system") {
            if (isTtsInitialized) {
                tts?.setSpeechRate(speed)
                if (isPlaying) {
                    speakCurrent()
                }
            }
        } else {
            if (isPlaying && audioPlayer?.isPlaying == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        audioPlayer?.playbackParams = audioPlayer?.playbackParams?.setSpeed(speed) 
                            ?: android.media.PlaybackParams().setSpeed(speed)
                    } catch (e: Exception) {
                        Log.e("TTS", "Failed to set playback speed", e)
                    }
                }
            }
        }
    }

    private fun setTtsPitch(newPitch: Float) {
        val engine = appPrefs.ttsSelectedEngine
        if (engine == "system") {
            if (isTtsInitialized) {
                tts?.setPitch(newPitch)
                if (isPlaying) {
                    speakCurrent()
                }
            }
        }
    }

    private fun reloadTtsSettings() {
        val newEngine = appPrefs.ttsSelectedEngine
        speed = appPrefs.ttsSpeed
        val newPitch = appPrefs.ttsPitch
        
        if (newEngine == "system") {
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
            val filteredText = applyWordReplacements(rawContentText)
            val newSentences = splitIntoSentences(filteredText)
            sentences = newSentences
            if (currentSentenceIndex >= sentences.size) {
                currentSentenceIndex = (sentences.size - 1).coerceAtLeast(0)
            }
        }
        
        if (isPlaying) {
            if (newEngine == "system") {
                speakCurrent()
            } else {
                speakCurrentAi()
            }
        } else {
            updateNotification()
            sendUpdateBroadcast()
        }
    }

    private fun sendUpdateBroadcast(isActive: Boolean = true) {
        val intent = Intent(BROADCAST_TTS_STATUS).apply {
            putExtra(EXTRA_STATUS_ACTIVE, isActive)
            putExtra(EXTRA_STATUS_PLAYING, isPlaying)
            putExtra(EXTRA_STATUS_INDEX, currentSentenceIndex)
            putExtra(EXTRA_STATUS_TEXT, if (currentSentenceIndex < sentences.size) sentences[currentSentenceIndex] else "")
            putExtra(EXTRA_STATUS_TIMER_REMAINING, timerRemainingSeconds)
            putExtra(EXTRA_STATUS_CHAPTER_URL, chapterUrl)
            putExtra(EXTRA_STATUS_SPEED, speed)
            putExtra("extra_status_total", sentences.size)
            putExtra("extra_status_bg_music", selectedBgMusic)
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
}
