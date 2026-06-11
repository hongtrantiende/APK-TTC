package com.nam.novelreader.feature.components

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import android.os.Build
import androidx.compose.runtime.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.composed
import androidx.compose.foundation.LocalIndication
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nam.novelreader.R
import com.nam.novelreader.data.preferences.AppPreferences

/**
 * State representing all app-level customization settings.
 */
data class VBookThemeState(
    val isDark: Boolean = false,
    val amoledMode: Boolean = false,
    val dynamicColor: Boolean = false,
    val einkMode: Boolean = false,
    val themeColorHex: String = "#D4A574",
    val accentColorHex: String = "",
    val settingsItemBgColorHex: String = "",
    val settingsItemTextColorHex: String = "",
    val settingsGroupTitleColorHex: String = "",
    val settingsIconColorHex: String = "",
    val fontScale: Float = 1.0f,
    val densityScale: Float = 1.0f,
    val fontFamily: String = "system",
    val displayBackground: String = "default",
    val liquidGlass: Boolean = false,
    val browseGridColumns: Int = 3,
    val browseTitleFontSize: Float = 12f,
    val browseTitleMaxLines: Int = 2,
    val browseTitleAlign: String = "start",
    val browseCoverCornerRadius: Float = 6f
)

/**
 * Static CompositionLocal holding the global customization state.
 */
val LocalVBookThemeState = staticCompositionLocalOf { VBookThemeState() }

object VBookTheme {
    @Composable
    fun themeState(): VBookThemeState {
        return LocalVBookThemeState.current
    }

    @Composable
    fun isDarkTheme(): Boolean {
        return themeState().isDark
    }

    @Composable
    fun backgroundColor(): Color {
        val state = themeState()
        if (state.einkMode) {
            return if (state.isDark) Color(0xFF000000) else Color(0xFFFFFFFF)
        }
        if (state.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return if (state.isDark) {
                if (state.amoledMode) Color(0xFF000000) else MaterialTheme.colorScheme.background
            } else {
                MaterialTheme.colorScheme.background
            }
        }
        return if (state.isDark) {
            if (state.amoledMode) Color(0xFF000000) else MaterialTheme.colorScheme.background
        } else {
            when (state.displayBackground) {
                "sepia" -> Color(0xFFF4ECD8)
                "green" -> Color(0xFFE8F1E5)
                "gray" -> Color(0xFFEAEAEA)
                else -> MaterialTheme.colorScheme.background
            }
        }
    }

    @Composable
    fun cardColor(): Color {
        val state = themeState()
        if (state.einkMode) {
            return if (state.isDark) Color(0xFF000000) else Color(0xFFFFFFFF)
        }
        if (state.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return if (state.isDark) {
                if (state.amoledMode) Color(0xFF0A0A0A) else MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        }
        return if (state.isDark) {
            if (state.amoledMode) Color(0xFF0A0A0A) else MaterialTheme.colorScheme.surfaceVariant
        } else {
            when (state.displayBackground) {
                "sepia" -> Color(0xFFEADFCA)
                "green" -> Color(0xFFDCE6D9)
                "gray" -> Color(0xFFDFDFDF)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        }
    }

    @Composable
    fun textColor(): Color {
        val state = themeState()
        if (state.einkMode) {
            return if (state.isDark) Color(0xFFFFFFFF) else Color(0xFF000000)
        }
        if (state.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return MaterialTheme.colorScheme.onBackground
        }
        return MaterialTheme.colorScheme.onBackground
    }

    @Composable
    fun subTextColor(): Color {
        val state = themeState()
        if (state.einkMode) {
            return if (state.isDark) Color(0xFF888888) else Color(0xFF666666)
        }
        if (state.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return MaterialTheme.colorScheme.onSurfaceVariant
        }
        return MaterialTheme.colorScheme.onSurfaceVariant
    }

    @Composable
    fun settingsItemBgColor(): Color {
        val state = themeState()
        return if (state.settingsItemBgColorHex.isEmpty()) {
            cardColor()
        } else {
            try { Color(android.graphics.Color.parseColor(state.settingsItemBgColorHex)) } catch (_: Exception) { cardColor() }
        }
    }

    @Composable
    fun settingsItemTextColor(): Color {
        val state = themeState()
        return if (state.settingsItemTextColorHex.isEmpty()) {
            textColor()
        } else {
            try { Color(android.graphics.Color.parseColor(state.settingsItemTextColorHex)) } catch (_: Exception) { textColor() }
        }
    }

    @Composable
    fun settingsGroupTitleColor(): Color {
        val state = themeState()
        return if (state.settingsGroupTitleColorHex.isEmpty()) {
            primaryColor()
        } else {
            try { Color(android.graphics.Color.parseColor(state.settingsGroupTitleColorHex)) } catch (_: Exception) { primaryColor() }
        }
    }

    @Composable
    fun settingsIconColor(): Color {
        val state = themeState()
        return if (state.settingsIconColorHex.isEmpty()) {
            primaryColor()
        } else {
            try { Color(android.graphics.Color.parseColor(state.settingsIconColorHex)) } catch (_: Exception) { primaryColor() }
        }
    }

    @Composable
    fun primaryColor(): Color {
        val state = themeState()
        if (state.einkMode) {
            return if (state.isDark) Color(0xFFFFFFFF) else Color(0xFF000000)
        }
        if (state.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return MaterialTheme.colorScheme.primary
        }
        // Use user-chosen theme color when not using dynamic color
        return try {
            Color(android.graphics.Color.parseColor(state.themeColorHex))
        } catch (_: Exception) {
            MaterialTheme.colorScheme.primary
        }
    }

    /** Màu nhấn khi chọn tab/nút — fallback sang primaryColor nếu chưa tùy chỉnh */
    @Composable
    fun accentColor(): Color {
        val state = themeState()
        if (state.einkMode) {
            return if (state.isDark) Color(0xFFFFFFFF) else Color(0xFF000000)
        }
        if (state.accentColorHex.isNotEmpty()) {
            return try {
                Color(android.graphics.Color.parseColor(state.accentColorHex))
            } catch (_: Exception) {
                primaryColor()
            }
        }
        return primaryColor()
    }

    @Composable
    fun switchTrackCheckedColor(): Color {
        return primaryColor().copy(alpha = 0.5f)
    }

    @Composable
    fun switchThumbCheckedColor(): Color {
        return if (isDarkTheme()) Color(0xFFFFFFFF) else primaryColor()
    }

    @Composable
    fun switchTrackUncheckedColor(): Color {
        val state = themeState()
        if (state.einkMode) {
            return if (state.isDark) Color(0xFF333333) else Color(0xFFCCCCCC)
        }
        return if (state.isDark) Color(0xFF332A22) else Color(0xFFE0D8D0)
    }

    @Composable
    fun switchThumbUncheckedColor(): Color {
        val state = themeState()
        if (state.einkMode) {
            return if (state.isDark) Color(0xFF666666) else Color(0xFF888888)
        }
        return if (state.isDark) Color(0xFF777777) else Color(0xFFA09085)
    }

    @Composable
    fun getAppFontFamily(): FontFamily {
        val name = themeState().fontFamily
        return when (name.lowercase()) {
            "inter" -> FontFamily(Font(R.font.inter))
            "nunito" -> FontFamily(Font(R.font.nunito))
            "literata" -> FontFamily(Font(R.font.literata))
            else -> FontFamily.Default
        }
    }
}

@Composable
fun VBookSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val bias by animateFloatAsState(
        targetValue = if (checked) 1f else -1f,
        label = "switchBias"
    )
    
    val trackColor = if (checked) VBookTheme.switchTrackCheckedColor() else VBookTheme.switchTrackUncheckedColor()
    val thumbColor = if (checked) VBookTheme.switchThumbCheckedColor() else VBookTheme.switchThumbUncheckedColor()
    
    Box(
        modifier = modifier
            .width(52.dp)
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(trackColor)
            .clickable(
                enabled = onCheckedChange != null,
                onClick = { onCheckedChange?.invoke(!checked) }
            )
            .padding(3.dp),
        contentAlignment = BiasAlignment(bias, 0f)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(thumbColor),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = if (VBookTheme.isDarkTheme()) Color(0xFF1A1410) else Color(0xFFFFFFFF),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun VBookSettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp)
    ) {
        if (title.isNotBlank()) {
            Text(
                text = title,
                color = VBookTheme.settingsGroupTitleColor(),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp, start = 12.dp)
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(VBookTheme.settingsItemBgColor())
        ) {
            content()
        }
    }
}

@Composable
fun VBookSettingsItem(
    title: String,
    subtitle: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = false,
    content: @Composable (RowScope.() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(VBookTheme.settingsIconColor().copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = VBookTheme.settingsIconColor(),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
        }
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = VBookTheme.settingsItemTextColor(),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = VBookTheme.settingsItemTextColor().copy(alpha = 0.7f),
                    fontSize = 13.sp
                )
            }
        }
        
        if (content != null) {
            Spacer(modifier = Modifier.width(16.dp))
            content()
        }
        if (showChevron) {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = VBookTheme.settingsItemTextColor().copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun VBookSettingsDivider() {
    Divider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color = VBookTheme.subTextColor().copy(alpha = 0.15f)
    )
}



@androidx.compose.runtime.Composable
fun buildNovelImageRequest(novel: com.nam.novelreader.domain.model.Novel): coil.request.ImageRequest {
    val context = androidx.compose.ui.platform.LocalContext.current
    return androidx.compose.runtime.remember(novel.cover, novel.extensionId, novel.url) {
        val builder = coil.request.ImageRequest.Builder(context)
            .data(novel.cover)
            .crossfade(true)
            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
        if (novel.extensionId.isNotBlank()) {
            builder.addHeader("X-Extension-Id", novel.extensionId)
        }
        if (novel.url.isNotBlank()) {
            builder.addHeader("Referer", novel.url)
        }
        builder.build()
    }
}

@androidx.compose.runtime.Composable
fun buildNovelImageRequest(novel: com.nam.novelreader.data.local.entity.NovelEntity): coil.request.ImageRequest {
    val context = androidx.compose.ui.platform.LocalContext.current
    return androidx.compose.runtime.remember(novel.cover, novel.extensionId, novel.url) {
        val builder = coil.request.ImageRequest.Builder(context)
            .data(novel.cover)
            .crossfade(true)
            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
        if (novel.extensionId.isNotBlank()) {
            builder.addHeader("X-Extension-Id", novel.extensionId)
        }
        if (novel.url.isNotBlank()) {
            builder.addHeader("Referer", novel.url)
        }
        builder.build()
    }
}

@androidx.compose.runtime.Composable
fun buildNovelImageRequest(recentNovel: com.nam.novelreader.data.local.dao.RecentNovelWithInfo): coil.request.ImageRequest {
    val context = androidx.compose.ui.platform.LocalContext.current
    return androidx.compose.runtime.remember(recentNovel.cover, recentNovel.extensionId, recentNovel.novelUrl) {
        val builder = coil.request.ImageRequest.Builder(context)
            .data(recentNovel.cover)
            .crossfade(true)
            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
        if (!recentNovel.extensionId.isNullOrBlank()) {
            builder.addHeader("X-Extension-Id", recentNovel.extensionId)
        }
        if (!recentNovel.novelUrl.isNullOrBlank()) {
            builder.addHeader("Referer", recentNovel.novelUrl)
        }
        builder.build()
    }
}
