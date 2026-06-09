package com.nam.novelreader.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nam.novelreader.MainActivity
import com.nam.novelreader.data.preferences.AppPreferences
import com.nam.novelreader.data.repository.NovelRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * UpdateCheckWorker — Tác vụ chạy ngầm định kỳ quét cập nhật chương mới
 * của các truyện trong danh sách theo dõi (followed novels).
 */
class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun repository(): NovelRepository
        fun appPrefs(): AppPreferences
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val appContext = applicationContext
        val entryPoint = EntryPointAccessors.fromApplication(
            appContext,
            WorkerEntryPoint::class.java
        )
        val repository = entryPoint.repository()
        val appPrefs = entryPoint.appPrefs()

        val followedSet = appPrefs.followedNovels
        if (followedSet.isEmpty()) {
            return@withContext Result.success()
        }

        createNotificationChannel(appContext)

        for (followedStr in followedSet) {
            val parts = followedStr.split("|")
            if (parts.size < 3) continue
            val extensionId = parts[0]
            val novelUrl = parts[1]
            val novelTitle = parts[2]

            try {
                // 1. Lấy danh sách chương online
                val onlineChapters = repository.getTableOfContents(extensionId, novelUrl)
                if (onlineChapters.isEmpty()) continue

                // 2. Lấy danh sách chương cache cục bộ
                val dbChapters = repository.getChapterList(novelUrl)

                // 3. So sánh
                if (onlineChapters.size > dbChapters.size) {
                    // Cập nhật lại cache chương mới trong DB
                    repository.cacheChapters(novelUrl, onlineChapters)

                    // Lấy tên chương mới nhất
                    val newestChapter = onlineChapters.last()
                    
                    // Gửi thông báo chương mới
                    sendNewChapterNotification(appContext, novelUrl, novelTitle, newestChapter.title)
                }
            } catch (e: Exception) {
                android.util.Log.e("UpdateCheckWorker", "Lỗi quét chương mới cho truyện $novelTitle: ${e.message}")
            }
        }

        Result.success()
    }

    private fun sendNewChapterNotification(
        context: Context,
        novelUrl: String,
        novelTitle: String,
        chapterTitle: String
    ) {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val piOpenApp = PendingIntent.getActivity(
            context,
            novelUrl.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
            .setContentTitle("Chương mới: $novelTitle")
            .setContentText(chapterTitle)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentIntent(piOpenApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(novelUrl.hashCode(), notification)
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                UPDATE_CHANNEL_ID,
                "Cập nhật chương mới (Followed)",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Thông báo khi truyện bạn theo dõi có chương mới"
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val UPDATE_CHANNEL_ID = "chapter_update_channel"
    }
}
