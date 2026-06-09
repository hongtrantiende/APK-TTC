package com.nam.novelreader

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = android.Manifest.permission.POST_NOTIFICATIONS
            if (checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(permission), 101)
            }
        }

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
                    settingsItemBgColorHex = appPrefs.settingsItemBgColorHex,
                    settingsItemTextColorHex = appPrefs.settingsItemTextColorHex,
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
                        NovelReaderNavHost()
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
