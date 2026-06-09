package com.nam.novelreader

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

import coil.ImageLoader
import coil.ImageLoaderFactory
import javax.inject.Inject
import okhttp3.OkHttpClient
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import java.util.concurrent.TimeUnit
import com.nam.novelreader.service.UpdateCheckWorker
import com.nam.novelreader.data.preferences.AppPreferences

/**
 * Application class khởi tạo Hilt DI container.
 * Entry point cho toàn bộ ứng dụng NovelReader.
 */
@HiltAndroidApp
class NovelReaderApp : Application(), ImageLoaderFactory {
    @Inject lateinit var okHttpClient: OkHttpClient
    @Inject lateinit var appPrefs: AppPreferences

    override fun onCreate() {
        super.onCreate()
        // Khởi tạo bộ chặn quảng cáo từ assets
        com.nam.novelreader.data.network.AdBlocker.init(this)
        // Khởi tạo và nạp từ điển Quick Translate ngầm
        com.nam.novelreader.util.QuickTranslateEngine.init(this)
        
        // Cài đặt Proxy cho WebView (nếu có bật proxy)
        com.nam.novelreader.util.WebViewProxyHelper.applyProxyFromPrefs(this)
        
        // Thiết lập kiểm tra cập nhật chương mới chạy ngầm định kỳ
        setupUpdateCheckWorker()
    }

    private fun setupUpdateCheckWorker() {
        val intervalMinutes = appPrefs.notifCheckInterval.coerceAtLeast(15)
        val workRequest = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
            intervalMinutes.toLong(),
            TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "update_check_work",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .build()
    }
}
