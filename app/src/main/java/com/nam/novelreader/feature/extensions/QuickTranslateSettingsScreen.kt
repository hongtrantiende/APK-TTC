package com.nam.novelreader.feature.extensions

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import com.nam.novelreader.feature.components.VBookTheme
import com.nam.novelreader.feature.components.VBookSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import android.util.Log

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickTranslateSettingsScreen(
    navController: NavHostController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE) }
    var refreshTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            com.nam.novelreader.util.QuickTranslateEngine.copyDictFromAssetsIfNeed(context)
        }
        refreshTrigger++
    }

    // Dictionary word count states
    var vietPhraseCount by remember { mutableStateOf("...") }
    var nameCount by remember { mutableStateOf("...") }
    var phienAmCount by remember { mutableStateOf("...") }
    var pronounsCount by remember { mutableStateOf("...") }
    var luatNhanCount by remember { mutableStateOf("...") }

    LaunchedEffect(refreshTrigger) {
        withContext(Dispatchers.IO) {
            val vp = com.nam.novelreader.util.QuickTranslateEngine.getWordCount(context, "VietPhrase.txt")
            val nm = com.nam.novelreader.util.QuickTranslateEngine.getWordCount(context, "Name.txt")
            val pa = com.nam.novelreader.util.QuickTranslateEngine.getWordCount(context, "PhienAm.txt")
            val pr = com.nam.novelreader.util.QuickTranslateEngine.getWordCount(context, "Pronouns.txt")
            val ln = com.nam.novelreader.util.QuickTranslateEngine.getWordCount(context, "LuatNhan.txt")
            withContext(Dispatchers.Main) {
                vietPhraseCount = vp
                nameCount = nm
                phienAmCount = pa
                pronounsCount = pr
                luatNhanCount = ln
            }
        }
    }

    // Download States
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgressText by remember { mutableStateOf("") }

    // SharedPreferences states
    var nameVpPriority by remember(refreshTrigger) {
        mutableStateOf(prefs.getString("qt_dict_priority_name_vp", "Name > VP") ?: "Name > VP")
    }
    var privatePublicPriority by remember(refreshTrigger) {
        mutableStateOf(prefs.getString("qt_dict_priority_private_public", "Riêng > Chung") ?: "Riêng > Chung")
    }
    var maxPhraseLength by remember(refreshTrigger) {
        mutableStateOf(prefs.getInt("qt_max_phrase_length", 12))
    }
    var vpLengthPriority by remember(refreshTrigger) {
        mutableStateOf(prefs.getString("qt_vp_length_priority", "Dài > Ngắn") ?: "Dài > Ngắn")
    }
    var luatNhan by remember(refreshTrigger) {
        mutableStateOf(prefs.getString("qt_luat_nhan", "Không nhân") ?: "Không nhân")
    }
    var segmentMode by remember(refreshTrigger) {
        mutableStateOf(prefs.getString("qt_segment_mode", "Theo đoạn văn") ?: "Theo đoạn văn")
    }
    var wordLookupTool by remember(refreshTrigger) {
        mutableStateOf(prefs.getString("qt_word_lookup_tool", "Cài đặt công cụ tra cứu bản dịch từ trên web") ?: "Cài đặt công cụ tra cứu bản dịch từ trên web")
    }
    var convertTraditionalSimplified by remember(refreshTrigger) {
        mutableStateOf(prefs.getBoolean("qt_convert_traditional_simplified", true))
    }
    var italicizeDialogue by remember(refreshTrigger) {
        mutableStateOf(prefs.getBoolean("qt_italicize_dialogue", true))
    }

    // Dialog states
    var showNameVpDialog by remember { mutableStateOf(false) }
    var showPrivatePublicDialog by remember { mutableStateOf(false) }
    var showMaxLengthDialog by remember { mutableStateOf(false) }
    var showVpLengthDialog by remember { mutableStateOf(false) }
    var showLuatNhanDialog by remember { mutableStateOf(false) }
    var showSegmentModeDialog by remember { mutableStateOf(false) }
    var showWordLookupDialog by remember { mutableStateOf(false) }

    // Theme Colors
    val cardBg = VBookTheme.cardColor()
    val dividerColor = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF332A22) else Color(0xFFE8E2DA)
    val accentGold = VBookTheme.primaryColor()
    val textColor = VBookTheme.textColor()
    val subTextColor = VBookTheme.subTextColor()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quick Translate", fontWeight = FontWeight.Bold, color = VBookTheme.textColor()) },
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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ================= SECTION 1: TỪ ĐIỂN CHUNG =================
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Từ điển chung",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentGold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )

                    Text(
                        text = "Tải từ điển",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentGold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(dividerColor)
                            .clickable {
                                isDownloading = true
                                downloadProgressText = "Đang giải nén từ điển..."
                                scope.launch(Dispatchers.IO) {
                                    val success = com.nam.novelreader.util.QuickTranslateEngine.unzipDictFromAssets(context)
                                    withContext(Dispatchers.Main) {
                                        isDownloading = false
                                        refreshTrigger++
                                        if (success) {
                                            Toast.makeText(context, "Tải từ điển hoàn thành!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Lỗi giải nén từ điển từ assets!", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val dicts = listOf(
                        Triple("VietPhrase.txt", vietPhraseCount, 0xFF2D2621),
                        Triple("Name.txt", nameCount, 0xFF2D2621),
                        Triple("PhienAm.txt", phienAmCount, 0xFF2D2621),
                        Triple("Pronouns.txt", pronounsCount, 0xFF2D2621),
                        Triple("LuatNhan.txt", luatNhanCount, 0xFF2D2621)
                    )

                    // Grid-like structure with 3 items first row, 2 items second row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (i in 0..2) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(cardBg)
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Text(
                                        text = dicts[i].first,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = textColor
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = dicts[i].second,
                                        fontSize = 11.sp,
                                        color = accentGold
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (i in 3..4) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(cardBg)
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Text(
                                        text = dicts[i].first,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = textColor
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = dicts[i].second,
                                        fontSize = 11.sp,
                                        color = accentGold
                                    )
                                }
                            }
                        }
                        Box(modifier = Modifier.weight(1f))
                    }
                }
            }

            // ================= SECTION 2: CÀI ĐẶT DỊCH =================
            item {
                Text(
                    text = "Cài đặt dịch",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentGold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardBg)
                ) {
                    // Ưu tiên từ điển name - VP
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showNameVpDialog = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Ưu tiên từ điển name - VP", fontSize = 15.sp, color = textColor, fontWeight = FontWeight.Medium)
                            Text(text = nameVpPriority, fontSize = 12.sp, color = Color(0xFF8E8276))
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = Color(0xFF8E8276), modifier = Modifier.size(20.dp))
                    }

                    HorizontalDivider(color = dividerColor, thickness = 1.dp)

                    // Ưu tiên từ điển riêng - chung
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showPrivatePublicDialog = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Ưu tiên từ điển riêng - chung", fontSize = 15.sp, color = textColor, fontWeight = FontWeight.Medium)
                            Text(text = privatePublicPriority, fontSize = 12.sp, color = Color(0xFF8E8276))
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = Color(0xFF8E8276), modifier = Modifier.size(20.dp))
                    }

                    HorizontalDivider(color = dividerColor, thickness = 1.dp)

                    // Cụm từ dài nhất
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showMaxLengthDialog = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Cụm từ dài nhất", fontSize = 15.sp, color = textColor, fontWeight = FontWeight.Medium)
                            Text(text = maxPhraseLength.toString(), fontSize = 12.sp, color = Color(0xFF8E8276))
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = Color(0xFF8E8276), modifier = Modifier.size(20.dp))
                    }

                    HorizontalDivider(color = dividerColor, thickness = 1.dp)

                    // Ưu tiên độ dài VP
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showVpLengthDialog = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Ưu tiên độ dài VP", fontSize = 15.sp, color = textColor, fontWeight = FontWeight.Medium)
                            Text(text = vpLengthPriority, fontSize = 12.sp, color = Color(0xFF8E8276))
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = Color(0xFF8E8276), modifier = Modifier.size(20.dp))
                    }

                    HorizontalDivider(color = dividerColor, thickness = 1.dp)

                    // Luật nhân
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showLuatNhanDialog = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Luật nhân", fontSize = 15.sp, color = textColor, fontWeight = FontWeight.Medium)
                            Text(text = luatNhan, fontSize = 12.sp, color = Color(0xFF8E8276))
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = Color(0xFF8E8276), modifier = Modifier.size(20.dp))
                    }

                    HorizontalDivider(color = dividerColor, thickness = 1.dp)

                    // Phân đoạn dịch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showSegmentModeDialog = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Phân đoạn dịch", fontSize = 15.sp, color = textColor, fontWeight = FontWeight.Medium)
                            Text(text = segmentMode, fontSize = 12.sp, color = Color(0xFF8E8276))
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = Color(0xFF8E8276), modifier = Modifier.size(20.dp))
                    }

                    HorizontalDivider(color = dividerColor, thickness = 1.dp)

                    // Công cụ tra cứu từ
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showWordLookupDialog = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Công cụ tra cứu từ", fontSize = 15.sp, color = textColor, fontWeight = FontWeight.Medium)
                            Text(text = wordLookupTool, fontSize = 12.sp, color = Color(0xFF8E8276))
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = Color(0xFF8E8276), modifier = Modifier.size(20.dp))
                    }

                    HorizontalDivider(color = dividerColor, thickness = 1.dp)

                    // Chuyển phồn thể sang giản thể (Switch)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Chuyển phồn thể sang giản thể", fontSize = 15.sp, color = textColor, fontWeight = FontWeight.Medium)
                            Text(text = "Tự động chuyển đổi văn bản từ chữ phồn thể sang chữ giản thể.", fontSize = 12.sp, color = Color(0xFF8E8276))
                        }
                        VBookSwitch(
                            checked = convertTraditionalSimplified,
                            onCheckedChange = { checked ->
                                convertTraditionalSimplified = checked
                                prefs.edit().putBoolean("qt_convert_traditional_simplified", checked).apply()
                                refreshTrigger++
                            }
                        )
                    }

                    HorizontalDivider(color = dividerColor, thickness = 1.dp)

                    // In nghiêng câu thoại (Switch)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "In nghiêng câu thoại", fontSize = 15.sp, color = textColor, fontWeight = FontWeight.Medium)
                            Text(text = "Tự động in nghiêng câu thoại nằm giữa 2 dấu nháy kép.", fontSize = 12.sp, color = Color(0xFF8E8276))
                        }
                        VBookSwitch(
                            checked = italicizeDialogue,
                            onCheckedChange = { checked ->
                                italicizeDialogue = checked
                                prefs.edit().putBoolean("qt_italicize_dialogue", checked).apply()
                                refreshTrigger++
                            }
                        )
                    }
                }
            }
        }
    }

    // ================= DOWNLOAD PROGRESS DIALOG =================
    if (isDownloading) {
        Dialog(onDismissRequest = {}) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(cardBg)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = accentGold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = downloadProgressText, color = textColor, fontSize = 15.sp)
                }
            }
        }
    }

    // ================= DIALOGS =================

    // Name - VP Priority Dialog
    if (showNameVpDialog) {
        val options = listOf("Name > VP", "VP > Name")
        AlertDialog(
            onDismissRequest = { showNameVpDialog = false },
            title = { Text("Ưu tiên từ điển name - VP", color = textColor, fontWeight = FontWeight.Bold) },
            containerColor = cardBg,
            text = {
                Column {
                    options.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    prefs.edit().putString("qt_dict_priority_name_vp", option).apply()
                                    nameVpPriority = option
                                    showNameVpDialog = false
                                    refreshTrigger++
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = nameVpPriority == option,
                                onClick = {
                                    prefs.edit().putString("qt_dict_priority_name_vp", option).apply()
                                    nameVpPriority = option
                                    showNameVpDialog = false
                                    refreshTrigger++
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = accentGold, unselectedColor = Color.Gray)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = option, color = textColor)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // Private - Public Priority Dialog
    if (showPrivatePublicDialog) {
        val options = listOf("Riêng > Chung", "Chung > Riêng")
        AlertDialog(
            onDismissRequest = { showPrivatePublicDialog = false },
            title = { Text("Ưu tiên từ điển riêng - chung", color = textColor, fontWeight = FontWeight.Bold) },
            containerColor = cardBg,
            text = {
                Column {
                    options.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    prefs.edit().putString("qt_dict_priority_private_public", option).apply()
                                    privatePublicPriority = option
                                    showPrivatePublicDialog = false
                                    refreshTrigger++
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = privatePublicPriority == option,
                                onClick = {
                                    prefs.edit().putString("qt_dict_priority_private_public", option).apply()
                                    privatePublicPriority = option
                                    showPrivatePublicDialog = false
                                    refreshTrigger++
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = accentGold, unselectedColor = Color.Gray)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = option, color = textColor)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // Max Phrase Length Dialog
    if (showMaxLengthDialog) {
        val options = listOf(10, 11, 12, 13, 14, 15, 16)
        AlertDialog(
            onDismissRequest = { showMaxLengthDialog = false },
            title = { Text("Cụm từ dài nhất", color = textColor, fontWeight = FontWeight.Bold) },
            containerColor = cardBg,
            text = {
                Column {
                    options.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    prefs.edit().putInt("qt_max_phrase_length", option).apply()
                                    maxPhraseLength = option
                                    showMaxLengthDialog = false
                                    refreshTrigger++
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = maxPhraseLength == option,
                                onClick = {
                                    prefs.edit().putInt("qt_max_phrase_length", option).apply()
                                    maxPhraseLength = option
                                    showMaxLengthDialog = false
                                    refreshTrigger++
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = accentGold, unselectedColor = Color.Gray)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = option.toString(), color = textColor)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // VP Length Priority Dialog
    if (showVpLengthDialog) {
        val options = listOf("Dài > Ngắn", "Ngắn > Dài")
        AlertDialog(
            onDismissRequest = { showVpLengthDialog = false },
            title = { Text("Ưu tiên độ dài VP", color = textColor, fontWeight = FontWeight.Bold) },
            containerColor = cardBg,
            text = {
                Column {
                    options.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    prefs.edit().putString("qt_vp_length_priority", option).apply()
                                    vpLengthPriority = option
                                    showVpLengthDialog = false
                                    refreshTrigger++
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = vpLengthPriority == option,
                                onClick = {
                                    prefs.edit().putString("qt_vp_length_priority", option).apply()
                                    vpLengthPriority = option
                                    showVpLengthDialog = false
                                    refreshTrigger++
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = accentGold, unselectedColor = Color.Gray)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = option, color = textColor)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // Luật nhân Dialog
    if (showLuatNhanDialog) {
        val options = listOf("Không nhân", "Luật nhân 1", "Luật nhân 2")
        AlertDialog(
            onDismissRequest = { showLuatNhanDialog = false },
            title = { Text("Luật nhân", color = textColor, fontWeight = FontWeight.Bold) },
            containerColor = cardBg,
            text = {
                Column {
                    options.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    prefs.edit().putString("qt_luat_nhan", option).apply()
                                    luatNhan = option
                                    showLuatNhanDialog = false
                                    refreshTrigger++
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = luatNhan == option,
                                onClick = {
                                    prefs.edit().putString("qt_luat_nhan", option).apply()
                                    luatNhan = option
                                    showLuatNhanDialog = false
                                    refreshTrigger++
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = accentGold, unselectedColor = Color.Gray)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = option, color = textColor)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // Phân đoạn dịch Dialog
    if (showSegmentModeDialog) {
        val options = listOf("Theo đoạn văn", "Theo dòng", "Theo câu")
        AlertDialog(
            onDismissRequest = { showSegmentModeDialog = false },
            title = { Text("Phân đoạn dịch", color = textColor, fontWeight = FontWeight.Bold) },
            containerColor = cardBg,
            text = {
                Column {
                    options.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    prefs.edit().putString("qt_segment_mode", option).apply()
                                    segmentMode = option
                                    showSegmentModeDialog = false
                                    refreshTrigger++
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = segmentMode == option,
                                onClick = {
                                    prefs.edit().putString("qt_segment_mode", option).apply()
                                    segmentMode = option
                                    showSegmentModeDialog = false
                                    refreshTrigger++
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = accentGold, unselectedColor = Color.Gray)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = option, color = textColor)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // Công cụ tra cứu từ Dialog / Custom setting
    if (showWordLookupDialog) {
        var wordLookupInput by remember { mutableStateOf(wordLookupTool) }
        AlertDialog(
            onDismissRequest = { showWordLookupDialog = false },
            title = { Text("Công cụ tra cứu từ", color = textColor, fontWeight = FontWeight.Bold) },
            containerColor = cardBg,
            text = {
                OutlinedTextField(
                    value = wordLookupInput,
                    onValueChange = { wordLookupInput = it },
                    label = { Text("Cấu hình công cụ tra cứu", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentGold,
                        unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = accentGold,
                        unfocusedLabelColor = Color.Gray,
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    prefs.edit().putString("qt_word_lookup_tool", wordLookupInput.trim()).apply()
                    wordLookupTool = wordLookupInput.trim()
                    showWordLookupDialog = false
                    refreshTrigger++
                }) {
                    Text("Lưu", color = accentGold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWordLookupDialog = false }) {
                    Text("Hủy", color = Color.Gray)
                }
            }
        )
    }
}
