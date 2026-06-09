package com.nam.novelreader.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.nam.novelreader.R
import com.nam.novelreader.data.preferences.AppPreferences
import com.nam.novelreader.feature.components.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsFontSizeScreen(
    navController: NavHostController,
    viewModel: DisplaySettingsViewModel = hiltViewModel()
) {
    val prefs = viewModel.appPrefs

    var fontFamily by remember { mutableStateOf(prefs.fontFamily) }
    var fontScale by remember { mutableFloatStateOf(prefs.fontScale) }
    var densityScale by remember { mutableFloatStateOf(prefs.densityScale) }

    // Predefined fonts
    val fonts = listOf(
        Triple("system", "Hệ thống", FontFamily.Default),
        Triple("inter", "Inter", FontFamily(Font(R.font.inter))),
        Triple("nunito", "Nunito", FontFamily(Font(R.font.nunito))),
        Triple("literata", "Literata", FontFamily(Font(R.font.literata)))
    )

    // Discrete steps for scale: 0.8 to 1.5, step = 0.1
    // Total steps: 8 (0.8, 0.9, 1.0, 1.1, 1.2, 1.3, 1.4, 1.5)
    val fontScaleSteps = 7
    val densityScaleSteps = 7

    Scaffold(
        containerColor = VBookTheme.backgroundColor(),
        topBar = {
            TopAppBar(
                title = { Text("Phông chữ và kích thước", fontWeight = FontWeight.Bold, color = VBookTheme.textColor()) },
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
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // ========== PHÔNG CHỮ ==========
            Text(
                text = "Phông chữ",
                color = VBookTheme.primaryColor(),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                fonts.forEach { (id, name, composeFont) ->
                    val isSelected = fontFamily.lowercase() == id.lowercase()
                    val cardBg = if (isSelected) VBookTheme.primaryColor().copy(alpha = 0.3f) else VBookTheme.cardColor()
                    val borderColor = if (isSelected) VBookTheme.primaryColor() else Color.Transparent

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(74.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(cardBg)
                                .border(2.dp, borderColor, RoundedCornerShape(16.dp))
                                .clickable {
                                    fontFamily = id
                                    prefs.fontFamily = id
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Aa",
                                fontSize = 22.sp,
                                fontFamily = composeFont,
                                fontWeight = FontWeight.Bold,
                                color = VBookTheme.textColor()
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = name,
                            fontSize = 12.sp,
                            color = if (isSelected) VBookTheme.primaryColor() else VBookTheme.subTextColor(),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Add Font button placeholder
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(74.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(VBookTheme.cardColor())
                            .clickable { /* Add custom font functionality if needed */ },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Thêm font",
                            tint = VBookTheme.textColor(),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Thêm font",
                        fontSize = 12.sp,
                        color = VBookTheme.subTextColor(),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ========== KÍCH THƯỚC HIỂN THỊ ==========
            Text(
                text = "Kích thước hiển thị",
                color = VBookTheme.primaryColor(),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )

            // Kích thước phông chữ
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = VBookTheme.cardColor())
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Kích thước phông chữ", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = VBookTheme.textColor())
                            Text("Phóng to hoặc thu nhỏ văn bản", fontSize = 12.sp, color = VBookTheme.subTextColor())
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${(fontScale * 100).roundToInt()}%", fontSize = 14.sp, color = VBookTheme.textColor(), modifier = Modifier.padding(end = 8.dp))
                            IconButton(
                                onClick = {
                                    fontScale = 1.0f
                                    prefs.fontScale = 1.0f
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Reset", tint = VBookTheme.primaryColor(), modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Minus button
                        IconButton(
                            onClick = {
                                if (fontScale > 0.85f) {
                                    fontScale = ((fontScale - 0.1f) * 10f).roundToInt() / 10f
                                    prefs.fontScale = fontScale
                                }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Filled.Remove, contentDescription = "Giảm", tint = VBookTheme.textColor())
                        }

                        // Slider with discrete ticks
                        Slider(
                            value = fontScale,
                            onValueChange = {
                                fontScale = (it * 10f).roundToInt() / 10f
                            },
                            onValueChangeFinished = {
                                prefs.fontScale = fontScale
                            },
                            valueRange = 0.8f..1.5f,
                            steps = fontScaleSteps,
                            colors = SliderDefaults.colors(
                                thumbColor = VBookTheme.primaryColor(),
                                activeTrackColor = VBookTheme.primaryColor(),
                                inactiveTrackColor = VBookTheme.subTextColor().copy(alpha = 0.3f),
                                activeTickColor = VBookTheme.primaryColor(),
                                inactiveTickColor = VBookTheme.subTextColor().copy(alpha = 0.6f)
                            ),
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                        )

                        // Plus button
                        IconButton(
                            onClick = {
                                if (fontScale < 1.45f) {
                                    fontScale = ((fontScale + 0.1f) * 10f).roundToInt() / 10f
                                    prefs.fontScale = fontScale
                                }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Tăng", tint = VBookTheme.textColor())
                        }
                    }
                }
            }

            // Kích thước giao diện
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = VBookTheme.cardColor())
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Kích thước giao diện", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = VBookTheme.textColor())
                            Text("Phóng to hoặc thu nhỏ toàn bộ giao diện", fontSize = 12.sp, color = VBookTheme.subTextColor())
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${(densityScale * 100).roundToInt()}%", fontSize = 14.sp, color = VBookTheme.textColor(), modifier = Modifier.padding(end = 8.dp))
                            IconButton(
                                onClick = {
                                    densityScale = 1.0f
                                    prefs.densityScale = 1.0f
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Reset", tint = VBookTheme.primaryColor(), modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Minus button
                        IconButton(
                            onClick = {
                                if (densityScale > 0.85f) {
                                    densityScale = ((densityScale - 0.1f) * 10f).roundToInt() / 10f
                                    prefs.densityScale = densityScale
                                }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Filled.Remove, contentDescription = "Giảm", tint = VBookTheme.textColor())
                        }

                        // Slider with discrete ticks
                        Slider(
                            value = densityScale,
                            onValueChange = {
                                densityScale = (it * 10f).roundToInt() / 10f
                            },
                            onValueChangeFinished = {
                                prefs.densityScale = densityScale
                            },
                            valueRange = 0.8f..1.5f,
                            steps = densityScaleSteps,
                            colors = SliderDefaults.colors(
                                thumbColor = VBookTheme.primaryColor(),
                                activeTrackColor = VBookTheme.primaryColor(),
                                inactiveTrackColor = VBookTheme.subTextColor().copy(alpha = 0.3f),
                                activeTickColor = VBookTheme.primaryColor(),
                                inactiveTickColor = VBookTheme.subTextColor().copy(alpha = 0.6f)
                            ),
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                        )

                        // Plus button
                        IconButton(
                            onClick = {
                                if (densityScale < 1.45f) {
                                    densityScale = ((densityScale + 0.1f) * 10f).roundToInt() / 10f
                                    prefs.densityScale = densityScale
                                }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Tăng", tint = VBookTheme.textColor())
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
