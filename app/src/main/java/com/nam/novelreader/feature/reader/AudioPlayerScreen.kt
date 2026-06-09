package com.nam.novelreader.feature.reader

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.nam.novelreader.domain.model.Novel

@Composable
fun AudioPlayerScreen(
    novel: Novel?,
    chapterTitle: String,
    isPlaying: Boolean,
    currentSentenceIndex: Int,
    totalSentences: Int,
    timerRemainingSeconds: Int,
    speed: Float,
    themeIndex: Int,
    bgMusic: String,
    onPlayPause: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onSetTimer: (Long) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSetBgMusic: (String) -> Unit,
    onDismiss: () -> Unit,
    onShowToc: () -> Unit,
    onNavigateToExtensionSettings: (String) -> Unit,
) {
    // Xác định màu sắc theo themeIndex của Reader để tạo sự đồng bộ
    val (bgColor, textColor, primaryColor, cardColor) = when (themeIndex) {
        0 -> Quadruple(Color(0xFFD3C3A3), Color(0xFF3A3129), Color(0xFF8B5A2B), Color(0xFFC5B595)) // Kraft hoa văn (bg7.jpg)
        1 -> Quadruple(Color(0xFFFFFFFF), Color(0xFF3A342B), Color(0xFF1976D2), Color(0xFFF0F0F0)) // Trắng trơn
        2 -> Quadruple(Color(0xFFF1F7ED), Color(0xFF1B310E), Color(0xFF2E7D32), Color(0xFFE2EADF)) // Xanh lá hoa văn (bg6.png)
        3 -> Quadruple(Color(0xFFA2C0E5), Color(0xFF1B310E), Color(0xFF1976D2), Color(0xFF90B0D5)) // Xanh dương hoa văn (bg4.jpg)
        4 -> Quadruple(Color(0xFF1C1C1C), Color(0xFFCCCCCC), Color(0xFFD4A574), Color(0xFF282828)) // Đêm đen
        5 -> Quadruple(Color(0xFFF3C9D7), Color(0xFF1B310E), Color(0xFFC2185B), Color(0xFFE5B9C7)) // Hồng hoa văn (bg5.jpg)
        6 -> Quadruple(Color(0xFFECE1CA), Color(0xFF645032), Color(0xFF8B5A2B), Color(0xFFDDD2BA)) // Vàng giấy trơn
        7 -> Quadruple(Color(0xFFC2E0CD), Color(0xFF334B39), Color(0xFF2E7D32), Color(0xFFB2D0BD)) // Lục nhạt trơn
        else -> Quadruple(Color(0xFFD3C3A3), Color(0xFF3A3129), Color(0xFF8B5A2B), Color(0xFFC5B595))
    }

    var showTimerMenu by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showBgMusicMenu by remember { mutableStateOf(false) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var showTtsSettingsSheet by remember { mutableStateOf(false) }
    var selectedBgMusic by remember(bgMusic) { mutableStateOf(bgMusic) }

    androidx.activity.compose.BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
            // === Top Toolbar ===
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Thu nhỏ",
                        tint = textColor,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Text(
                    text = "Nghe đọc truyện",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Đóng",
                        tint = textColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // === Main Content (Scrollable Column if screen is small) ===
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 64.dp, bottom = 120.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 1. Cover Image (To, đẹp, bo tròn, đổ bóng)
                val coverModel = remember(novel?.url) { novel?.cover ?: "" }
                Box(
                    modifier = Modifier
                        .padding(horizontal = 48.dp)
                        .weight(1f, fill = false)
                        .aspectRatio(0.68f)
                        .shadow(16.dp, RoundedCornerShape(24.dp))
                        .background(cardColor, RoundedCornerShape(24.dp))
                ) {
                    AsyncImage(
                        model = coverModel,
                        contentDescription = novel?.title,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(24.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 2. Info: Title & Author
                Text(
                    text = novel?.title ?: "Đang tải truyện...",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = novel?.author ?: "Chưa rõ tác giả",
                    fontSize = 14.sp,
                    color = textColor.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // 3. Progress Slider (Tiến trình câu)
                val safeTotal = if (totalSentences > 0) totalSentences else 100
                val safeIndex = currentSentenceIndex.coerceIn(0, safeTotal)
                
                Slider(
                    value = safeIndex.toFloat(),
                    onValueChange = {},
                    valueRange = 0f..safeTotal.toFloat(),
                    enabled = false, // Chỉ hiển thị tiến trình của TTS
                    colors = SliderDefaults.colors(
                        disabledThumbColor = primaryColor,
                        disabledActiveTrackColor = primaryColor,
                        disabledInactiveTrackColor = textColor.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                )
                
                // Chương hiện tại
                Text(
                    text = chapterTitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // 4. Control Buttons (Tua lùi, chương trước, PLAY/PAUSE, chương sau, tua tiến)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tua lùi câu
                    IconButton(
                        onClick = onRewind,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FastRewind,
                            contentDescription = "Tua lùi",
                            tint = textColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Chương trước
                    IconButton(
                        onClick = onPrevChapter,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipPrevious,
                            contentDescription = "Chương trước",
                            tint = textColor,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Play / Pause (Cực to, tròn, nền màu nâu/vàng ấm)
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .shadow(6.dp, CircleShape)
                            .background(primaryColor, CircleShape)
                            .clickable { onPlayPause() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = bgColor,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    // Chương sau
                    IconButton(
                        onClick = onNextChapter,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = "Chương sau",
                            tint = textColor,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Tua tiến câu
                    IconButton(
                        onClick = onForward,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FastForward,
                            contentDescription = "Tua tiến",
                            tint = textColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            // === Bottom Functional Menu Row ===
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .shadow(4.dp, RoundedCornerShape(20.dp))
                    .background(cardColor, RoundedCornerShape(20.dp))
                    .padding(vertical = 12.dp, horizontal = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Mục lục
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable { onShowToc() }
                            .padding(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.List,
                            contentDescription = "Mục lục",
                            tint = textColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Mục lục", fontSize = 11.sp, color = textColor)
                    }

                    // 2. Hẹn giờ
                    Box {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { showTimerMenu = true }
                                .padding(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AccessTime,
                                contentDescription = "Hẹn giờ",
                                tint = if (timerRemainingSeconds > 0) primaryColor else textColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val timerText = if (timerRemainingSeconds > 0) {
                                val mins = timerRemainingSeconds / 60
                                val secs = timerRemainingSeconds % 60
                                String.format("%02d:%02d", mins, secs)
                            } else {
                                "Hẹn giờ"
                            }
                            Text(
                                text = timerText,
                                fontSize = 11.sp,
                                color = if (timerRemainingSeconds > 0) primaryColor else textColor
                            )
                        }
                        DropdownMenu(
                            expanded = showTimerMenu,
                            onDismissRequest = { showTimerMenu = false }
                        ) {
                            listOf(
                                "Tắt hẹn giờ" to 0L,
                                "15 phút" to 15L,
                                "30 phút" to 30L,
                                "45 phút" to 45L,
                                "60 phút" to 60L
                            ).forEach { (label, duration) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        showTimerMenu = false
                                        onSetTimer(duration)
                                    }
                                )
                            }
                        }
                    }

                    // 3. Tốc độ
                    Box {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { showSpeedMenu = true }
                                .padding(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Speed,
                                contentDescription = "Tốc độ",
                                tint = textColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = String.format("%.1fx", speed),
                                fontSize = 11.sp,
                                color = textColor
                            )
                        }
                        DropdownMenu(
                            expanded = showSpeedMenu,
                            onDismissRequest = { showSpeedMenu = false }
                        ) {
                            listOf(0.8f, 1.0f, 1.2f, 1.5f, 1.8f, 2.0f, 2.5f).forEach { rate ->
                                DropdownMenuItem(
                                    text = { Text("${rate}x") },
                                    onClick = {
                                        showSpeedMenu = false
                                        onSetSpeed(rate)
                                    }
                                )
                            }
                        }
                    }

                    // 4. Nhạc nền
                    Box {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { showBgMusicMenu = true }
                                .padding(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MusicNote,
                                contentDescription = "Nhạc nền",
                                tint = if (selectedBgMusic != "Không có") primaryColor else textColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Nhạc nền", fontSize = 11.sp, color = textColor)
                        }
                        DropdownMenu(
                            expanded = showBgMusicMenu,
                            onDismissRequest = { showBgMusicMenu = false }
                        ) {
                            listOf("Không có", "Mưa rơi nhẹ", "Nhạc thiền thư giãn", "Tiếng sóng biển").forEach { music ->
                                DropdownMenuItem(
                                    text = { Text(music) },
                                    onClick = {
                                        showBgMusicMenu = false
                                        selectedBgMusic = music
                                        onSetBgMusic(music)
                                    }
                                )
                            }
                        }
                    }

                    // 5. Cài đặt
                    val context = LocalContext.current
                    val prefs = remember { context.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE) }
                    var ttsAutoNext by remember { mutableStateOf(prefs.getBoolean("tts_auto_next_chapter", true)) }

                    val currentEngine = remember { prefs.getString("reader_tts_engine", "system") ?: "system" }
                    val sysEnginePkg = remember { prefs.getString("reader_tts_system_engine", "com.google.android.tts") ?: "com.google.android.tts" }
                    val engineLabel = when (currentEngine) {
                        "system" -> {
                            if (sysEnginePkg.contains("google")) "Google TTS" else "Hệ thống"
                        }
                        "ai" -> "AI TTS"
                        else -> {
                            currentEngine.split("-").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                        }
                    }

                    Box {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { showSettingsMenu = true }
                                .padding(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Cài đặt",
                                tint = textColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Cài đặt", fontSize = 11.sp, color = textColor)
                        }
                        DropdownMenu(
                            expanded = showSettingsMenu,
                            onDismissRequest = { showSettingsMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Đổi giọng đọc ($engineLabel)") },
                                onClick = {
                                    showSettingsMenu = false
                                    showTtsSettingsSheet = true
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Tự động chuyển chương")
                                        Checkbox(
                                            checked = ttsAutoNext,
                                            onCheckedChange = { checked ->
                                                ttsAutoNext = checked
                                                prefs.edit().putBoolean("tts_auto_next_chapter", checked).apply()
                                            },
                                            colors = CheckboxDefaults.colors(checkedColor = primaryColor)
                                        )
                                    }
                                },
                                onClick = {
                                    ttsAutoNext = !ttsAutoNext
                                    prefs.edit().putBoolean("tts_auto_next_chapter", ttsAutoNext).apply()
                                }
                            )
                        }
                    }
                }
            }

            if (showTtsSettingsSheet) {
                TtsPlayerSettingsBottomSheet(
                    themeIndex = themeIndex,
                    onNavigateToExtensionSettings = onNavigateToExtensionSettings,
                    onDismiss = { showTtsSettingsSheet = false }
                )
            }
        }
}

data class Quadruple<out A, out B, out C, out D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
