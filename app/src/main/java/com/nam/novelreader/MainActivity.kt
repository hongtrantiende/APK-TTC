package com.nam.novelreader

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.nam.novelreader.data.preferences.AppPreferences
import com.nam.novelreader.feature.components.VBookTheme
import com.nam.novelreader.feature.components.VBookThemeState
import com.nam.novelreader.feature.components.LocalVBookThemeState
import com.nam.novelreader.navigation.NovelReaderNavHost
import com.nam.novelreader.ui.theme.NovelReaderTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * MainActivity — single Activity, Compose-based navigation.
 * Centralizes theme and display settings updates to prevent WeakReference garbage collection issues.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var appPrefs: AppPreferences

    @Inject
    lateinit var supabaseAuthManager: com.nam.novelreader.data.network.SupabaseAuthManager

    // Strong reference to SharedPreferences listener to prevent GC
    private var onPrefsChanged: (() -> Unit)? = null
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        onPrefsChanged?.invoke()
    }

    // URI + mimeType từ intent mở file (EPUB/TXT/CBZ)
    private val pendingUriStr = mutableStateOf<String>("")
    private val pendingMimeType = mutableStateOf<String?>(null)

    private fun handleFileIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        // OAuth callback: novelreader://auth/callback
        if (uri.scheme == "novelreader" && uri.host == "auth") {
            lifecycleScope.launch {
                val result = supabaseAuthManager.handleOAuthCallback(uri)
                if (result.isSuccess) {
                    android.widget.Toast.makeText(this@MainActivity, "Đăng nhập Google thành công!", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    android.widget.Toast.makeText(this@MainActivity, "Lỗi đăng nhập: ${result.exceptionOrNull()?.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
            return
        }
        // File open intent
        if (intent.action != Intent.ACTION_VIEW) return
        pendingUriStr.value = uri.toString()
        pendingMimeType.value = intent.type
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleFileIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Force highest refresh rate for 90Hz/120Hz screens
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val display = windowManager.defaultDisplay
            val modes = display.supportedModes
            val highestRefreshRateMode = modes.maxByOrNull { it.refreshRate }
            if (highestRefreshRateMode != null) {
                window.attributes = window.attributes.apply {
                    preferredDisplayModeId = highestRefreshRateMode.modeId
                }
            }
        }
        
        enableEdgeToEdge()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = android.Manifest.permission.POST_NOTIFICATIONS
            if (checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(permission), 101)
            }
        }

        // Kiểm tra intent mở file ngay khi khởi động
        handleFileIntent(intent)

        // Register the shared preference listener
        appPrefs.prefs.registerOnSharedPreferenceChangeListener(prefsListener)

        // Tự động gia hạn phiên đăng nhập Supabase nếu có
        lifecycleScope.launch {
            if (appPrefs.supabaseIsLoggedIn && appPrefs.supabaseRefreshToken.isNotBlank()) {
                supabaseAuthManager.refreshSession()
            }
        }

        setContent {
            val systemDark = isSystemInDarkTheme()

            // Function to generate the current theme state
            fun computeThemeState(): VBookThemeState {
                val isDark = when (appPrefs.darkMode) {
                    "light" -> false
                    "dark" -> true
                    else -> systemDark
                }
                return VBookThemeState(
                    isDark = isDark,
                    amoledMode = appPrefs.amoledMode,
                    dynamicColor = appPrefs.dynamicColor,
                    einkMode = appPrefs.einkMode,
                    themeColorHex = appPrefs.themeColorHex,
                    accentColorHex = appPrefs.accentColorHex,
                    settingsItemBgColorHex = appPrefs.settingsItemBgColorHex,
                    settingsItemTextColorHex = appPrefs.settingsItemTextColorHex,
                    settingsGroupTitleColorHex = appPrefs.settingsGroupTitleColorHex,
                    settingsIconColorHex = appPrefs.settingsIconColorHex,
                    fontScale = appPrefs.fontScale,
                    densityScale = appPrefs.densityScale,
                    fontFamily = appPrefs.fontFamily,
                    displayBackground = appPrefs.displayBackground,
                    liquidGlass = appPrefs.liquidGlass,
                    browseGridColumns = appPrefs.browseGridColumns,
                    browseTitleFontSize = appPrefs.browseTitleFontSize,
                    browseTitleMaxLines = appPrefs.browseTitleMaxLines,
                    browseTitleAlign = appPrefs.browseTitleAlign,
                    browseCoverCornerRadius = appPrefs.browseCoverCornerRadius
                )
            }

            var themeState by remember { mutableStateOf(computeThemeState()) }

            // Connect preference change callback to state update
            onPrefsChanged = {
                themeState = computeThemeState()
            }

            // Apply global font scale and display density scale
            val baseDensity = LocalDensity.current
            val scaledDensity = remember(baseDensity, themeState.fontScale, themeState.densityScale) {
                Density(
                    density = baseDensity.density * themeState.densityScale,
                    fontScale = baseDensity.fontScale * themeState.fontScale
                )
            }

            CompositionLocalProvider(
                LocalVBookThemeState provides themeState,
                LocalDensity provides scaledDensity
            ) {
                NovelReaderTheme(
                    darkTheme = themeState.isDark,
                    dynamicColor = themeState.dynamicColor,
                    appFontFamily = VBookTheme.getAppFontFamily()
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = VBookTheme.backgroundColor()
                    ) {
                        NovelReaderNavHost(
                            initialUriStr = pendingUriStr.value,
                            initialMimeType = pendingMimeType.value,
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        appPrefs.prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
    }
}
