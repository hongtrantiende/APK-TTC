package com.nam.novelreader.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.nam.novelreader.data.preferences.AppPreferences
import com.nam.novelreader.feature.components.*
import com.nam.novelreader.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.ViewModel

@HiltViewModel
class DisplaySettingsViewModel @Inject constructor(
    val appPrefs: AppPreferences
) : ViewModel()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDisplayScreen(
    navController: NavHostController,
    viewModel: DisplaySettingsViewModel = hiltViewModel()
) {
    val prefs = viewModel.appPrefs
    val accentGold = Color(0xFFD4A574)

    var darkMode by remember { mutableStateOf(prefs.darkMode) }
    var amoledMode by remember { mutableStateOf(prefs.amoledMode) }
    var dynamicColor by remember { mutableStateOf(prefs.dynamicColor) }
    var einkMode by remember { mutableStateOf(prefs.einkMode) }
    var themeColor by remember { mutableStateOf(prefs.themeColorHex) }
    var accentColor by remember { mutableStateOf(prefs.accentColorHex) }
    var settingsItemBgColor by remember { mutableStateOf(prefs.settingsItemBgColorHex) }
    var settingsItemTextColor by remember { mutableStateOf(prefs.settingsItemTextColorHex) }
    var settingsGroupTitleColor by remember { mutableStateOf(prefs.settingsGroupTitleColorHex) }
    var settingsIconColor by remember { mutableStateOf(prefs.settingsIconColorHex) }
    var displayBackground by remember { mutableStateOf(prefs.displayBackground) }
    var liquidGlass by remember { mutableStateOf(prefs.liquidGlass) }

    var browseGridColumns by remember { mutableStateOf(prefs.browseGridColumns.toFloat()) }
    var browseTitleFontSize by remember { mutableStateOf(prefs.browseTitleFontSize) }
    var browseTitleMaxLines by remember { mutableStateOf(prefs.browseTitleMaxLines.toFloat()) }
    var browseTitleAlign by remember { mutableStateOf(prefs.browseTitleAlign) }
    var browseCoverCornerRadius by remember { mutableStateOf(prefs.browseCoverCornerRadius) }

    var showColorPalette by remember { mutableStateOf(false) }
    var showBackgroundDialog by remember { mutableStateOf(false) }

    // Colors from VBook's theme + many more diverse colors
    val themeColors = listOf(
        // Gold / Browns
        "#D4A574", "#FFC107", "#FF9800", "#FF5722", "#795548", "#8D6E63",
        // Reds / Pinks
        "#F44336", "#E53935", "#D32F2F", "#B71C1C", "#E91E63", "#C2185B", "#880E4F", "#F06292",
        // Purples
        "#9C27B0", "#7B1FA2", "#4A148C", "#673AB7", "#512DA8", "#311B92", "#B39DDB",
        // Blues / Cyan
        "#3F51B5", "#303F9F", "#1A237E", "#2196F3", "#1976D2", "#0D47A1", "#03A9F4", "#0288D1", "#01579B", "#00BCD4", "#0097A7", "#006064",
        // Greens / Teal
        "#009688", "#00796B", "#004D40", "#4CAF50", "#388E3C", "#1B5E20", "#8BC34A", "#689F38", "#33691E", "#CDDC39", "#AFB42B", "#827717",
        // Grays / Black
        "#9E9E9E", "#616161", "#212121", "#607D8B", "#455A64", "#263238"
    )

    Scaffold(
        containerColor = VBookTheme.backgroundColor(),
        topBar = {
            TopAppBar(
                title = { Text("Cá nhân hoá", fontWeight = FontWeight.Bold, color = VBookTheme.textColor()) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = VBookTheme.textColor())
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VBookTheme.backgroundColor()
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ========== GIAO DIỆN ==========
            VBookSettingsGroup("Giao diện") {
                // Màu sắc
                VBookSettingsItem(
                    title = "Màu sắc",
                    subtitle = "TonalSpot",
                    onClick = { showColorPalette = !showColorPalette }
                ) {
                    val currentColor = try { Color(android.graphics.Color.parseColor(themeColor)) } catch (_: Exception) { accentGold }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(currentColor)
                            .border(1.dp, Color.Gray.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Expanded color palette
                AnimatedVisibility(
                    visible = showColorPalette,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        themeColors.forEach { hex ->
                            val color = try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { accentGold }
                            val isSelected = themeColor.equals(hex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .then(
                                        if (isSelected) Modifier.border(3.dp, VBookTheme.textColor(), CircleShape)
                                        else Modifier.border(1.dp, Color.Gray.copy(alpha = 0.3f), CircleShape)
                                    )
                                    .bounceClick {
                                        themeColor = hex
                                        prefs.themeColorHex = hex
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = VBookTheme.backgroundColor(),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                
                VBookSettingsDivider()

                // === MÀU NHẤN KHI CHỌN (Accent Color for tabs/buttons) ===
                var showAccentColorPalette by remember { mutableStateOf(false) }
                VBookSettingsItem(
                    title = "Màu nhấn khi chọn",
                    subtitle = if (accentColor.isEmpty()) "Theo màu chủ đề" else "Tùy chỉnh",
                    onClick = { showAccentColorPalette = !showAccentColorPalette }
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (accentColor.isEmpty()) VBookTheme.primaryColor() else try { Color(android.graphics.Color.parseColor(accentColor)) } catch(_: Exception) { VBookTheme.primaryColor() })
                            .border(1.dp, Color.Gray.copy(alpha=0.3f), CircleShape)
                    )
                }

                AnimatedVisibility(
                    visible = showAccentColorPalette,
                    enter = androidx.compose.animation.expandVertically(),
                    exit = androidx.compose.animation.shrinkVertically()
                ) {
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val accentOptions = listOf("") + themeColors
                        accentOptions.forEach { hex ->
                            val color = if (hex.isEmpty()) VBookTheme.primaryColor() else try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Color.Gray }
                            val isSelected = accentColor.equals(hex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .then(
                                        if (isSelected) Modifier.border(3.dp, VBookTheme.textColor(), CircleShape)
                                        else Modifier.border(1.dp, Color.Gray.copy(alpha = 0.3f), CircleShape)
                                    )
                                    .bounceClick {
                                        accentColor = hex
                                        prefs.accentColorHex = hex
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = if (hex.isEmpty()) VBookTheme.backgroundColor() else VBookTheme.backgroundColor(), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                VBookSettingsDivider()

                var showSettingsGroupTitleColorPalette by remember { mutableStateOf(false) }
                VBookSettingsItem(
                    title = "Màu tiêu đề nhóm cài đặt",
                    subtitle = if (settingsGroupTitleColor.isEmpty()) "Mặc định" else "Tùy chỉnh",
                    onClick = { showSettingsGroupTitleColorPalette = !showSettingsGroupTitleColorPalette }
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (settingsGroupTitleColor.isEmpty()) VBookTheme.primaryColor() else try { Color(android.graphics.Color.parseColor(settingsGroupTitleColor)) } catch(_: Exception) { VBookTheme.primaryColor() })
                            .border(1.dp, Color.Gray.copy(alpha=0.3f), CircleShape)
                    )
                }

                AnimatedVisibility(
                    visible = showSettingsGroupTitleColorPalette,
                    enter = androidx.compose.animation.expandVertically(),
                    exit = androidx.compose.animation.shrinkVertically()
                ) {
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val titleOptions = listOf("") + themeColors
                        titleOptions.forEach { hex ->
                            val color = if (hex.isEmpty()) VBookTheme.primaryColor() else try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Color.Gray }
                            val isSelected = settingsGroupTitleColor.equals(hex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .then(
                                        if (isSelected) Modifier.border(3.dp, VBookTheme.textColor(), CircleShape)
                                        else Modifier.border(1.dp, Color.Gray.copy(alpha = 0.3f), CircleShape)
                                    )
                                    .bounceClick {
                                        settingsGroupTitleColor = hex
                                        prefs.settingsGroupTitleColorHex = hex
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = if (hex.isEmpty()) VBookTheme.backgroundColor() else VBookTheme.backgroundColor(), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                VBookSettingsDivider()

                var showSettingsIconColorPalette by remember { mutableStateOf(false) }
                VBookSettingsItem(
                    title = "Màu Icon nhóm cài đặt",
                    subtitle = if (settingsIconColor.isEmpty()) "Mặc định" else "Tùy chỉnh",
                    onClick = { showSettingsIconColorPalette = !showSettingsIconColorPalette }
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (settingsIconColor.isEmpty()) VBookTheme.primaryColor() else try { Color(android.graphics.Color.parseColor(settingsIconColor)) } catch(_: Exception) { VBookTheme.primaryColor() })
                            .border(1.dp, Color.Gray.copy(alpha=0.3f), CircleShape)
                    )
                }

                AnimatedVisibility(
                    visible = showSettingsIconColorPalette,
                    enter = androidx.compose.animation.expandVertically(),
                    exit = androidx.compose.animation.shrinkVertically()
                ) {
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val iconOptions = listOf("") + themeColors
                        iconOptions.forEach { hex ->
                            val color = if (hex.isEmpty()) VBookTheme.primaryColor() else try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Color.Gray }
                            val isSelected = settingsIconColor.equals(hex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .then(
                                        if (isSelected) Modifier.border(3.dp, VBookTheme.textColor(), CircleShape)
                                        else Modifier.border(1.dp, Color.Gray.copy(alpha = 0.3f), CircleShape)
                                    )
                                    .bounceClick {
                                        settingsIconColor = hex
                                        prefs.settingsIconColorHex = hex
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = if (hex.isEmpty()) VBookTheme.backgroundColor() else VBookTheme.backgroundColor(), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                VBookSettingsDivider()

                // Chủ đề
                VBookSettingsItem(
                    title = "Chủ đề",
                    subtitle = null
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val activeColor = VBookTheme.primaryColor()
                        val inactiveBg = VBookTheme.backgroundColor()
                        val activeText = if (VBookTheme.isDarkTheme()) Color.Black else Color.White

                        // Auto / System
                        val isAutoSelected = darkMode == "system"
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (isAutoSelected) activeColor else inactiveBg)
                                .border(1.dp, if (isAutoSelected) Color.Transparent else VBookTheme.subTextColor().copy(alpha = 0.3f), CircleShape)
                                .bounceClick {
                                    darkMode = "system"
                                    prefs.darkMode = "system"
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("A", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (isAutoSelected) activeText else VBookTheme.textColor())
                        }

                        // Light Theme (Sun)
                        val isLightSelected = darkMode == "light"
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (isLightSelected) activeColor else inactiveBg)
                                .border(1.dp, if (isLightSelected) Color.Transparent else VBookTheme.subTextColor().copy(alpha = 0.3f), CircleShape)
                                .bounceClick {
                                    darkMode = "light"
                                    prefs.darkMode = "light"
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.WbSunny, contentDescription = "Sáng", modifier = Modifier.size(16.dp), tint = if (isLightSelected) activeText else VBookTheme.textColor())
                        }

                        // Dark Theme (Moon)
                        val isDarkSelected = darkMode == "dark"
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (isDarkSelected) activeColor else inactiveBg)
                                .border(1.dp, if (isDarkSelected) Color.Transparent else VBookTheme.subTextColor().copy(alpha = 0.3f), CircleShape)
                                .bounceClick {
                                    darkMode = "dark"
                                    prefs.darkMode = "dark"
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.NightsStay, contentDescription = "Tối", modifier = Modifier.size(16.dp), tint = if (isDarkSelected) activeText else VBookTheme.textColor())
                        }
                    }
                }
                
                VBookSettingsDivider()

                // Nền
                val bgLabel = when (displayBackground) {
                    "sepia" -> "Cát vàng"
                    "green" -> "Xanh ngọc"
                    "gray" -> "Xám nhẹ"
                    else -> "Không nền"
                }
                VBookSettingsItem(
                    title = "Nền",
                    subtitle = bgLabel,
                    showChevron = true,
                    onClick = { showBackgroundDialog = true }
                )
                
                VBookSettingsDivider()

                // Màu sắc thích ứng
                VBookSettingsItem(
                    title = "Màu sắc thích ứng",
                    subtitle = "Đồng bộ màu sắc ứng dụng với hệ thống",
                    content = {
                        VBookSwitch(
                            checked = dynamicColor,
                            onCheckedChange = {
                                dynamicColor = it
                                prefs.dynamicColor = it
                            }
                        )
                    }
                )
            }

            // ========== MÀN HÌNH ==========
            VBookSettingsGroup("Màn hình") {
                VBookSettingsItem(
                    title = "Chế độ màu AMOLED",
                    subtitle = "Nền ứng dụng chuyển sang đen tuyệt đối khi bật chế độ ban đêm",
                    content = {
                        VBookSwitch(
                            checked = amoledMode,
                            onCheckedChange = {
                                amoledMode = it
                                prefs.amoledMode = it
                            }
                        )
                    }
                )
                
                VBookSettingsDivider()

                VBookSettingsItem(
                    title = "Chế độ màn hình E-Ink",
                    subtitle = "Tắt hiệu ứng và màu sắc để phù hợp với màn hình E-Ink",
                    content = {
                        VBookSwitch(
                            checked = einkMode,
                            onCheckedChange = {
                                einkMode = it
                                prefs.einkMode = it
                            }
                        )
                    }
                )
                
                VBookSettingsDivider()

                VBookSettingsItem(
                    title = "Hiệu ứng Liquid Glass",
                    subtitle = "Bật hiệu ứng kính mờ cho thanh điều hướng và công tác trên tất cả nền tảng",
                    content = {
                        VBookSwitch(
                            checked = liquidGlass,
                            onCheckedChange = {
                                liquidGlass = it
                                prefs.liquidGlass = it
                            }
                        )
                    }
                )
            }

            // ========== VĂN BẢN ==========
            VBookSettingsGroup("Văn bản") {
                VBookSettingsItem(
                    title = "Phông chữ và kích thước",
                    showChevron = true,
                    onClick = { navController.navigate(Routes.SETTINGS_FONT_SIZE) }
                )
            }

            // ========== GIAO DIỆN KHÁM PHÁ ==========
            VBookSettingsGroup("Danh sách truyện (Khám phá)") {
                VBookSettingsItem(
                    title = "Số cột lưới",
                    subtitle = "${browseGridColumns.toInt()} cột"
                )
                Slider(
                    value = browseGridColumns,
                    onValueChange = { browseGridColumns = it },
                    onValueChangeFinished = { prefs.browseGridColumns = browseGridColumns.toInt() },
                    valueRange = 2f..5f,
                    steps = 2,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = VBookTheme.primaryColor(),
                        activeTrackColor = VBookTheme.primaryColor()
                    )
                )

                VBookSettingsDivider()

                VBookSettingsItem(
                    title = "Cỡ chữ tên truyện",
                    subtitle = "${browseTitleFontSize.toInt()} sp"
                )
                Slider(
                    value = browseTitleFontSize,
                    onValueChange = { browseTitleFontSize = it },
                    onValueChangeFinished = { prefs.browseTitleFontSize = browseTitleFontSize },
                    valueRange = 10f..18f,
                    steps = 7,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = VBookTheme.primaryColor(),
                        activeTrackColor = VBookTheme.primaryColor()
                    )
                )

                VBookSettingsDivider()

                VBookSettingsItem(
                    title = "Số dòng hiển thị tên truyện",
                    subtitle = "Tối đa ${browseTitleMaxLines.toInt()} dòng"
                )
                Slider(
                    value = browseTitleMaxLines,
                    onValueChange = { browseTitleMaxLines = it },
                    onValueChangeFinished = { prefs.browseTitleMaxLines = browseTitleMaxLines.toInt() },
                    valueRange = 1f..4f,
                    steps = 2,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = VBookTheme.primaryColor(),
                        activeTrackColor = VBookTheme.primaryColor()
                    )
                )

                VBookSettingsDivider()

                VBookSettingsItem(
                    title = "Canh lề tên truyện",
                    subtitle = if (browseTitleAlign == "start") "Trái" else "Giữa",
                    content = {
                        Row {
                            TextButton(onClick = {
                                browseTitleAlign = "start"
                                prefs.browseTitleAlign = "start"
                            }) {
                                Text("Trái", color = if (browseTitleAlign == "start") VBookTheme.primaryColor() else VBookTheme.subTextColor())
                            }
                            TextButton(onClick = {
                                browseTitleAlign = "center"
                                prefs.browseTitleAlign = "center"
                            }) {
                                Text("Giữa", color = if (browseTitleAlign == "center") VBookTheme.primaryColor() else VBookTheme.subTextColor())
                            }
                        }
                    }
                )

                VBookSettingsDivider()

                VBookSettingsItem(
                    title = "Độ bo góc ảnh bìa",
                    subtitle = "${browseCoverCornerRadius.toInt()} dp"
                )
                Slider(
                    value = browseCoverCornerRadius,
                    onValueChange = { browseCoverCornerRadius = it },
                    onValueChangeFinished = { prefs.browseCoverCornerRadius = browseCoverCornerRadius },
                    valueRange = 0f..16f,
                    steps = 15,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = VBookTheme.primaryColor(),
                        activeTrackColor = VBookTheme.primaryColor()
                    )
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Background selection dialog
    if (showBackgroundDialog) {
        RadioDialog(
            title = "Chọn nền giao diện",
            options = listOf(
                "default" to "Không nền",
                "sepia" to "Cát vàng",
                "green" to "Xanh ngọc",
                "gray" to "Xám nhẹ"
            ),
            selected = displayBackground,
            onSelect = {
                displayBackground = it
                prefs.displayBackground = it
            },
            onDismiss = { showBackgroundDialog = false }
        )
    }
}
