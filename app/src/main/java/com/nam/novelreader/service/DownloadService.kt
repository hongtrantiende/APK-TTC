package com.nam.novelreader.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.nam.novelreader.MainActivity
import com.nam.novelreader.data.repository.NovelRepository
import com.nam.novelreader.domain.model.Chapter
import com.nam.novelreader.domain.model.Novel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import com.nam.novelreader.data.local.dao.DownloadTaskDao
import com.nam.novelreader.data.local.entity.DownloadTaskEntity

@OptIn(ExperimentalCoroutinesApi::class)
@AndroidEntryPoint
class DownloadService : Service() {

    companion object {
        const val CHANNEL_ID = "download_service_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_DOWNLOAD = "com.nam.novelreader.action.DOWNLOAD"
        const val ACTION_RESUME = "com.nam.novelreader.action.RESUME"
        const val EXTRA_EXTENSION_ID = "extra_extension_id"
        const val EXTRA_CHAPTER_URLS = "extra_chapter_urls"
        const val EXTRA_NOVEL_URL = "extra_novel_url"
        const val EXTRA_NOVEL_TITLE = "extra_novel_title"
        const val EXTRA_COVER_URL = "extra_cover_url"
        const val EXTRA_START_INDEX = "extra_start_index"
        const val EXTRA_DOWNLOAD_SIZE = "extra_download_size"
        const val EXTRA_FROM_LAST_READ = "extra_from_last_read"
    }

    @Inject
    lateinit var repository: NovelRepository

    @Inject
    lateinit var downloadTaskDao: DownloadTaskDao

    @Inject
    lateinit var appPrefs: com.nam.novelreader.data.preferences.AppPreferences

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val activeJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()
    private var wakeLock: PowerManager.WakeLock? = null

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "NovelReader:DownloadWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(15 * 60 * 1000L) // 15 minutes timeout max
            }
            android.util.Log.d("DownloadService", "WakeLock acquired")
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
            android.util.Log.d("DownloadService", "WakeLock released")
        }
        wakeLock = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val extensionId = intent?.getStringExtra(EXTRA_EXTENSION_ID) ?: ""
        val novelUrl = intent?.getStringExtra(EXTRA_NOVEL_URL) ?: ""
        
        when (intent?.action) {
            ACTION_DOWNLOAD -> {
                val urls = intent.getStringArrayListExtra(EXTRA_CHAPTER_URLS)
                val startIndex = intent.getIntExtra(EXTRA_START_INDEX, -1)
                val downloadSize = intent.getIntExtra(EXTRA_DOWNLOAD_SIZE, -1)
                val fromLastRead = intent.getBooleanExtra(EXTRA_FROM_LAST_READ, false)
                val novelTitle = intent.getStringExtra(EXTRA_NOVEL_TITLE) ?: ""
                val coverUrl = intent.getStringExtra(EXTRA_COVER_URL) ?: ""
                
                startDownload(extensionId, novelUrl, novelTitle, coverUrl, urls, startIndex, downloadSize, fromLastRead)
            }
            ACTION_RESUME -> {
                resumeDownload(extensionId, novelUrl)
            }
        }
        return START_NOT_STICKY
    }

    private fun resumeDownload(extensionId: String, novelUrl: String) {
        if (novelUrl.isBlank() || extensionId.isBlank()) return
        if (activeJobs.containsKey(novelUrl)) return // Đang tải rồi, không tải trùng
        
        acquireWakeLock()
        ensureForeground()
        updateForegroundNotification()

        val job = serviceScope.launch {
            val task = downloadTaskDao.getTask(novelUrl) ?: run {
                handleTaskFinished(novelUrl)
                return@launch
            }
            downloadTaskDao.updateStatus(novelUrl, "preparing")
            downloadTaskDao.insert(task.copy(status = "preparing", errorMessage = null))
            
            try {
                var allChapters = repository.getChapterList(novelUrl).map {
                    Chapter(url = it.url, title = it.title, index = it.index)
                }
                if (allChapters.isEmpty()) {
                    allChapters = repository.getTableOfContents(extensionId, novelUrl)
                    if (allChapters.isNotEmpty()) {
                        repository.cacheChapters(novelUrl, allChapters)
                    }
                }
                
                val start = task.startIndex.coerceIn(0, allChapters.size - 1)
                val end = task.endIndex.coerceIn(start, allChapters.size - 1)
                val taskUrls = allChapters.subList(start, end + 1).map { it.url }
                
                val downloadedUrls = repository.getDownloadedChapterUrls(novelUrl)
                val remainingUrls = taskUrls.filter { it !in downloadedUrls }
                
                if (remainingUrls.isEmpty()) {
                    downloadTaskDao.updateStatus(novelUrl, "completed")
                    downloadTaskDao.updateProgress(novelUrl, task.totalChapters)
                    updateDetailNotification(novelUrl, task.novelTitle, task.totalChapters, task.totalChapters)
                    handleTaskFinished(novelUrl)
                    return@launch
                }
                
                val alreadyDownloaded = taskUrls.size - remainingUrls.size
                
                startDownloadInternal(
                    extensionId = extensionId,
                    novelUrl = novelUrl,
                    novelTitle = task.novelTitle,
                    coverUrl = task.coverUrl,
                    urls = remainingUrls,
                    totalChaptersCount = taskUrls.size,
                    initialDownloadedCount = alreadyDownloaded,
                    finalStart = start,
                    finalEnd = end
                )
            } catch (e: Exception) {
                e.printStackTrace()
                downloadTaskDao.insert(task.copy(status = "failed", errorMessage = "Lỗi mạng hoặc tải danh sách chương thất bại"))
                handleTaskFinished(novelUrl)
            }
        }
        activeJobs[novelUrl] = job
    }

    private fun startDownload(
        extensionId: String, 
        novelUrl: String, 
        novelTitle: String, 
        coverUrl: String, 
        urls: List<String>?,
        startIndex: Int,
        downloadSize: Int,
        fromLastRead: Boolean
    ) {
        if (novelUrl.isBlank()) return
        if (activeJobs.containsKey(novelUrl)) return // Đang tải rồi, không tải trùng
        
        acquireWakeLock()
        ensureForeground()
        updateForegroundNotification()

        val job = serviceScope.launch {
            try {
                var allChapters = repository.getChapterList(novelUrl).map {
                    Chapter(url = it.url, title = it.title, index = it.index, isDownloaded = it.isDownloaded)
                }
                
                if (allChapters.isEmpty() && extensionId.isNotBlank()) {
                    allChapters = repository.getTableOfContents(extensionId, novelUrl)
                    if (allChapters.isNotEmpty()) {
                        repository.cacheChapters(novelUrl, allChapters)
                    }
                }
                
                if (allChapters.isEmpty()) {
                    downloadTaskDao.insert(
                        DownloadTaskEntity(
                            novelUrl = novelUrl,
                            extensionId = extensionId,
                            novelTitle = novelTitle,
                            coverUrl = coverUrl,
                            status = "failed",
                            errorMessage = "Không tìm thấy danh sách chương"
                        )
                    )
                    handleTaskFinished(novelUrl)
                    return@launch
                }
                
                var finalStart = 0
                if (fromLastRead) {
                    val novel = repository.getNovelFromDb(novelUrl)
                    val lastReadUrl = novel?.lastReadChapterUrl
                    if (!lastReadUrl.isNullOrBlank()) {
                        val idx = allChapters.indexOfFirst { it.url == lastReadUrl }
                        if (idx >= 0) {
                            finalStart = idx
                        }
                    }
                } else if (startIndex >= 0) {
                    finalStart = startIndex.coerceIn(0, allChapters.size - 1)
                }
                
                var finalEnd = allChapters.size - 1
                val taskUrls = if (urls != null && urls.isNotEmpty()) {
                    val firstIdx = allChapters.indexOfFirst { it.url == urls.first() }
                    val lastIdx = allChapters.indexOfFirst { it.url == urls.last() }
                    if (firstIdx >= 0) finalStart = firstIdx
                    if (lastIdx >= 0) finalEnd = lastIdx
                    urls
                } else {
                    if (downloadSize > 0) {
                        finalEnd = (finalStart + downloadSize - 1).coerceAtMost(allChapters.size - 1)
                    }
                    if (finalStart <= finalEnd) {
                        allChapters.subList(finalStart, finalEnd + 1).map { it.url }
                    } else {
                        emptyList()
                    }
                }
                
                val downloadedUrls = repository.getDownloadedChapterUrls(novelUrl)
                val remainingUrls = taskUrls.filter { it !in downloadedUrls }
                
                val totalChaptersCount = taskUrls.size
                val alreadyDownloaded = totalChaptersCount - remainingUrls.size
                
                if (remainingUrls.isEmpty()) {
                    val task = downloadTaskDao.getTask(novelUrl)
                    if (task == null) {
                        downloadTaskDao.insert(
                            DownloadTaskEntity(
                                novelUrl = novelUrl,
                                extensionId = extensionId,
                                novelTitle = novelTitle,
                                coverUrl = coverUrl,
                                status = "completed",
                                totalChapters = totalChaptersCount,
                                downloadedChapters = totalChaptersCount,
                                startIndex = finalStart,
                                endIndex = finalEnd
                            )
                        )
                    } else {
                        downloadTaskDao.insert(
                            task.copy(
                                status = "completed",
                                totalChapters = totalChaptersCount,
                                downloadedChapters = totalChaptersCount,
                                startIndex = finalStart,
                                endIndex = finalEnd
                            )
                        )
                    }
                    updateDetailNotification(novelUrl, novelTitle, totalChaptersCount, totalChaptersCount)
                    handleTaskFinished(novelUrl)
                    return@launch
                }
                
                startDownloadInternal(
                    extensionId = extensionId,
                    novelUrl = novelUrl,
                    novelTitle = novelTitle,
                    coverUrl = coverUrl,
                    urls = remainingUrls,
                    totalChaptersCount = totalChaptersCount,
                    initialDownloadedCount = alreadyDownloaded,
                    finalStart = finalStart,
                    finalEnd = finalEnd
                )
            } catch (e: Exception) {
                e.printStackTrace()
                downloadTaskDao.insert(
                    DownloadTaskEntity(
                        novelUrl = novelUrl,
                        extensionId = extensionId,
                        novelTitle = novelTitle,
                        coverUrl = coverUrl,
                        status = "failed",
                        errorMessage = "Lỗi chuẩn bị tải: ${e.message}"
                    )
                )
                handleTaskFinished(novelUrl)
            }
        }
        activeJobs[novelUrl] = job
    }

    private suspend fun startDownloadInternal(
        extensionId: String, 
        novelUrl: String, 
        novelTitle: String, 
        coverUrl: String, 
        urls: List<String>,
        totalChaptersCount: Int,
        initialDownloadedCount: Int,
        finalStart: Int,
        finalEnd: Int
    ) {
        val total = urls.size
        var downloadedCount = 0
        val mutex = Mutex()
        var hasError = false
        
        val existingTask = downloadTaskDao.getTask(novelUrl)
        if (existingTask == null) {
            downloadTaskDao.insert(
                DownloadTaskEntity(
                    novelUrl = novelUrl,
                    extensionId = extensionId,
                    novelTitle = novelTitle,
                    coverUrl = coverUrl,
                    status = "downloading",
                    totalChapters = totalChaptersCount,
                    downloadedChapters = initialDownloadedCount,
                    startIndex = finalStart,
                    endIndex = finalEnd
                )
            )
        } else {
            downloadTaskDao.insert(
                existingTask.copy(
                    status = "downloading", 
                    errorMessage = null,
                    totalChapters = totalChaptersCount,
                    downloadedChapters = initialDownloadedCount,
                    startIndex = finalStart,
                    endIndex = finalEnd
                )
            )
        }

        updateDetailNotification(novelUrl, novelTitle, initialDownloadedCount, totalChaptersCount)
 
        var lastErrorMessage: String? = null
        
        try {
            withContext(Dispatchers.IO) {
                val parallelConnections = appPrefs.getEffectiveParallelConnections(extensionId)
                val connectionInterval = appPrefs.getEffectiveConnectionInterval(extensionId)
                val semaphore = Semaphore(parallelConnections)
                
                coroutineScope {
                    val jobs = urls.map { url ->
                        launch {
                            semaphore.withPermit {
                                val currentStatus = downloadTaskDao.getTask(novelUrl)?.status
                                if (currentStatus != "downloading" || hasError) return@withPermit
                                
                                // Áp dụng giãn cách kết nối tải truyện để tránh bị chặn IP
                                if (connectionInterval > 0) {
                                    try {
                                        delay(connectionInterval.toLong())
                                    } catch (_: Exception) {}
                                }
                                
                                var retryCount = 0
                                var success = false
                                var chapter: Chapter? = null
                                
                                while (isActive && !success && retryCount < 5) {
                                    val statusCheck = downloadTaskDao.getTask(novelUrl)?.status
                                    if (statusCheck != "downloading" || hasError) break
                                    
                                    if (retryCount > 0) {
                                        try {
                                            delay(3000)
                                        } catch (_: Exception) {}
                                    }
                                    
                                    // Kiểm tra lại trạng thái sau khi delay
                                    val statusCheckAfterDelay = downloadTaskDao.getTask(novelUrl)?.status
                                    if (statusCheckAfterDelay != "downloading" || hasError) break
                                    
                                    try {
                                        chapter = repository.getChapterContent(extensionId, url, isOfflineDownload = true)
                                        if (chapter == null || (chapter.content.isNullOrBlank() && chapter.images.isNullOrEmpty())) {
                                            throw Exception("Nội dung tải về rỗng hoặc không tải được")
                                        }
                                        success = true
                                    } catch (e: Exception) {
                                        retryCount++
                                        mutex.withLock {
                                            lastErrorMessage = e.message
                                        }
                                        
                                        // Nếu đã thử quá nhiều lần mà vẫn lỗi, đánh dấu task lỗi để dừng các luồng khác
                                        if (retryCount >= 5) {
                                            hasError = true
                                        }
                                    }
                                }
                                
                                if (success && chapter != null) {
                                    val newProgress = mutex.withLock {
                                        downloadedCount++
                                        initialDownloadedCount + downloadedCount
                                    }
                                    downloadTaskDao.updateProgress(novelUrl, newProgress)
                                    if (downloadedCount % 5 == 0 || downloadedCount == total) {
                                        updateDetailNotification(novelUrl, novelTitle, newProgress, totalChaptersCount)
                                    }
                                } else {
                                    // Bị hủy hoặc tạm dừng bởi người dùng (status != "downloading")
                                    this@coroutineScope.cancel()
                                }
                            }
                        }
                    }
                    jobs.joinAll()
                }
            }
        } catch (e: CancellationException) {
            // Expected
        }
        
        val finalStatus = downloadTaskDao.getTask(novelUrl)?.status
        if (finalStatus == "downloading" && !hasError) {
            downloadTaskDao.updateStatus(novelUrl, "completed")
            downloadTaskDao.updateProgress(novelUrl, totalChaptersCount)
            updateDetailNotification(novelUrl, novelTitle, totalChaptersCount, totalChaptersCount)
            handleTaskFinished(novelUrl)
        } else {
            // Trường hợp lỗi (hasError == true hoặc finalStatus là failed) hoặc người dùng chủ động bấm Tạm dừng/Hủy
            if (hasError || finalStatus == "failed") {
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val openAppIntent = Intent(this, MainActivity::class.java)
                val piOpenApp = PendingIntent.getActivity(this, 0, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val errorNotification = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("Tải truyện lỗi: $novelTitle")
                    .setContentText("Tải chương thất bại sau nhiều lần thử lại (Lỗi mạng/Nguồn chặn)")
                    .setSmallIcon(android.R.drawable.stat_notify_error)
                    .setContentIntent(piOpenApp)
                    .setAutoCancel(true)
                    .build()
                manager.notify(novelUrl.hashCode(), errorNotification)
            }
            handleTaskFinished(novelUrl)
        }
    }

    // startDownloadInternal logic replaced the original startDownload body

    private fun buildNotification(progress: Int, total: Int): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val piOpenApp = PendingIntent.getActivity(this, 0, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Đang tải chương offline")
            .setContentText("Đã tải $progress/$total chương")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(total, progress, false)
            .setContentIntent(piOpenApp)
            .build()
    }

    private fun ensureForeground() {
        createNotificationChannel()
        val openAppIntent = Intent(this, MainActivity::class.java)
        val piOpenApp = PendingIntent.getActivity(this, 0, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Đang tải truyện offline")
            .setContentText("Chuẩn bị tải...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(piOpenApp)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateForegroundNotification() {
        val count = activeJobs.size
        if (count == 0) return
        
        val openAppIntent = Intent(this, MainActivity::class.java)
        val piOpenApp = PendingIntent.getActivity(this, 0, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Đang tải truyện offline")
            .setContentText("Đang tải song song $count truyện...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(piOpenApp)
            .setOngoing(true)
            .build()
            
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun updateDetailNotification(novelUrl: String, novelTitle: String, progress: Int, total: Int) {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val piOpenApp = PendingIntent.getActivity(this, 0, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Tải: $novelTitle")
            .setContentText("Đã tải $progress/$total chương")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(total, progress, false)
            .setContentIntent(piOpenApp)
            .build()
            
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(novelUrl.hashCode(), notification)
    }

    private fun handleTaskFinished(novelUrl: String) {
        activeJobs.remove(novelUrl)
        
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(novelUrl.hashCode())
        
        if (activeJobs.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            releaseWakeLock()
            stopSelf()
        } else {
            updateForegroundNotification()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Tải truyện offline (Download)",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        serviceJob.cancel()
        releaseWakeLock()
        super.onDestroy()
    }
}
