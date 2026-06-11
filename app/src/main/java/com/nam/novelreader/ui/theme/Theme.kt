package com.nam.novelreader.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Modern reading-app optimized color scheme.
 * Dark: sophisticated slate-blue tones, easy on eyes.
 * Light: clean white-blue, comfortable for reading.
 */
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF7B9CFF),
    onPrimary = Color(0xFF1A2A5E),
    primaryContainer = Color(0xFF2D3F7A),
    onPrimaryContainer = Color(0xFFD6E2FF),
    secondary = Color(0xFFA8B8D4),
    onSecondary = Color(0xFF2A3142),
    secondaryContainer = Color(0xFF3D4559),
    onSecondaryContainer = Color(0xFFDDE4F0),
    tertiary = Color(0xFFB0C6E0),
    onTertiary = Color(0xFF1E3148),
    tertiaryContainer = Color(0xFF354760),
    onTertiaryContainer = Color(0xFFD6E8FA),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E4EA),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE2E4EA),
    surfaceVariant = Color(0xFF1E2028),
    onSurfaceVariant = Color(0xFF9EA2AE),
    outline = Color(0xFF6B6F7A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF4664E0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDBE1FF),
    onPrimaryContainer = Color(0xFF001552),
    secondary = Color(0xFF5A6070),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDEE2F0),
    onSecondaryContainer = Color(0xFF171C2A),
    tertiary = Color(0xFF4E6A84),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD1E5FA),
    onTertiaryContainer = Color(0xFF0A253E),
    background = Color(0xFFFAFBFD),
    onBackground = Color(0xFF1A1C22),
    surface = Color(0xFFFAFBFD),
    onSurface = Color(0xFF1A1C22),
    surfaceVariant = Color(0xFFECEFF4),
    onSurfaceVariant = Color(0xFF454750),
    outline = Color(0xFF757880),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

@Composable
fun NovelReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Disable dynamic color to keep VBook-style theme consistent
    dynamicColor: Boolean = false,
    appFontFamily: androidx.compose.ui.text.font.FontFamily = androidx.compose.ui.text.font.FontFamily.Default,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val window = (view.context as? android.app.Activity)?.window
            if (window != null) {
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    val defaultTypography = androidx.compose.material3.Typography()
    val customTypography = androidx.compose.runtime.remember(appFontFamily) {
        androidx.compose.material3.Typography(
            displayLarge = defaultTypography.displayLarge.copy(fontFamily = appFontFamily),
            displayMedium = defaultTypography.displayMedium.copy(fontFamily = appFontFamily),
            displaySmall = defaultTypography.displaySmall.copy(fontFamily = appFontFamily),
            headlineLarge = defaultTypography.headlineLarge.copy(fontFamily = appFontFamily),
            headlineMedium = defaultTypography.headlineMedium.copy(fontFamily = appFontFamily),
            headlineSmall = defaultTypography.headlineSmall.copy(fontFamily = appFontFamily),
            titleLarge = defaultTypography.titleLarge.copy(fontFamily = appFontFamily),
            titleMedium = defaultTypography.titleMedium.copy(fontFamily = appFontFamily),
            titleSmall = defaultTypography.titleSmall.copy(fontFamily = appFontFamily),
            bodyLarge = defaultTypography.bodyLarge.copy(fontFamily = appFontFamily),
            bodyMedium = defaultTypography.bodyMedium.copy(fontFamily = appFontFamily),
            bodySmall = defaultTypography.bodySmall.copy(fontFamily = appFontFamily),
            labelLarge = defaultTypography.labelLarge.copy(fontFamily = appFontFamily),
            labelMedium = defaultTypography.labelMedium.copy(fontFamily = appFontFamily),
            labelSmall = defaultTypography.labelSmall.copy(fontFamily = appFontFamily)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = customTypography,
        content = content
    )
}
