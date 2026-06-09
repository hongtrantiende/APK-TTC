package com.nam.novelreader.feature.extensions

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.nam.novelreader.feature.components.*
import com.nam.novelreader.navigation.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationSettingsScreen(
    navController: NavHostController
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE) }
    var refreshTrigger by remember { mutableStateOf(0) }

    // States
    var autoTranslate by remember(refreshTrigger) {
        mutableStateOf(prefs.getBoolean("translation_auto_translate", true))
    }
    var langDetect by remember(refreshTrigger) {
        mutableStateOf(prefs.getInt("translation_lang_detect", 0))
    }

    var showLangDetectDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dịch", fontWeight = FontWeight.Bold, color = VBookTheme.textColor()) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = VBookTheme.textColor())
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VBookTheme.backgroundColor()
                )
            )
        },
        containerColor = VBookTheme.backgroundColor()
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            // General Settings Group
            item {
                VBookSettingsGroup("Cài đặt chung") {
                    // Tự động dịch truyện
                    VBookSettingsItem(
                        title = "Tự động dịch truyện",
                        subtitle = "Tự động dịch ngôn ngữ bằng các công cụ dịch có sẵn"
                    ) {
                        VBookSwitch(
                            checked = autoTranslate,
                            onCheckedChange = { checked ->
                                autoTranslate = checked
                                prefs.edit().putBoolean("translation_auto_translate", checked).apply()
                                refreshTrigger++
                            }
                        )
                    }

                    VBookSettingsDivider()

                    // Nhận dạng ngôn ngữ
                    val langDetectText = when (langDetect) {
                        0 -> "Tự động nhận diện"
                        1 -> "Ưu tiên cài đặt phần mở rộng"
                        else -> "Thiết lập thủ công"
                    }
                    VBookSettingsItem(
                        title = "Nhận dạng ngôn ngữ",
                        subtitle = langDetectText,
                        showChevron = true,
                        onClick = { showLangDetectDialog = true }
                    )
                }
            }

            // Engines Group
            item {
                VBookSettingsGroup("Công cụ dịch") {
                    VBookSettingsItem(
                        title = "Quick Translate",
                        subtitle = "Tự động dịch truyện Trung bằng công cụ Quick Translate",
                        showChevron = true,
                        onClick = { navController.navigate(Routes.QUICK_TRANSLATE_SETTINGS) }
                    )
                }
            }
        }
    }

    // Language Detection Selection Dialog
    if (showLangDetectDialog) {
        val options = listOf(
            0 to "Tự động nhận diện",
            1 to "Ưu tiên cài đặt phần mở rộng",
            2 to "Thiết lập thủ công"
        )
        AlertDialog(
            onDismissRequest = { showLangDetectDialog = false },
            title = { Text("Nhận dạng ngôn ngữ", fontWeight = FontWeight.Bold, color = VBookTheme.textColor()) },
            containerColor = VBookTheme.cardColor(),
            text = {
                Column {
                    options.forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    prefs.edit().putInt("translation_lang_detect", value).apply()
                                    langDetect = value
                                    showLangDetectDialog = false
                                    refreshTrigger++
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = langDetect == value,
                                onClick = {
                                    prefs.edit().putInt("translation_lang_detect", value).apply()
                                    langDetect = value
                                    showLangDetectDialog = false
                                    refreshTrigger++
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = VBookTheme.primaryColor(),
                                    unselectedColor = VBookTheme.subTextColor()
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = label, color = VBookTheme.textColor(), fontSize = 15.sp)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}
