package com.nam.novelreader.feature.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.nam.novelreader.data.preferences.AppPreferences
import com.nam.novelreader.feature.components.VBookTheme
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

@HiltViewModel
class TtsWordReplacementViewModel @Inject constructor(
    val appPrefs: AppPreferences
) : ViewModel() {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    
    private val _rules = mutableStateListOf<WordReplacementRule>()
    val rules: List<WordReplacementRule> get() = _rules

    init {
        loadRules()
    }

    private fun loadRules() {
        val jsonStr = appPrefs.ttsWordReplacements
        _rules.clear()
        if (jsonStr.isBlank()) {
            // Khởi tạo các ký tự đặc biệt mặc định nếu trống
            val defaultSpecialChars = listOf(
                "'", "\"", "-", "=", "~", "!", "@", "#", "^", "&", "*", "(", ")", "_", "+"
            )
            val defaultRules = defaultSpecialChars.map { WordReplacementRule(it, "") }
            _rules.addAll(defaultRules)
            saveRules()
        } else {
            try {
                val parsed = json.decodeFromString<List<WordReplacementRule>>(jsonStr)
                _rules.addAll(parsed)
            } catch (e: Exception) {
                // reset if error
                val defaultSpecialChars = listOf(
                    "'", "\"", "-", "=", "~", "!", "@", "#", "^", "&", "*", "(", ")", "_", "+"
                )
                val defaultRules = defaultSpecialChars.map { WordReplacementRule(it, "") }
                _rules.addAll(defaultRules)
                saveRules()
            }
        }
    }

    fun addRule(pattern: String, replacement: String) {
        if (pattern.isBlank()) return
        // Tránh trùng lặp
        _rules.removeAll { it.pattern == pattern }
        _rules.add(WordReplacementRule(pattern, replacement))
        saveRules()
    }

    fun removeRule(rule: WordReplacementRule) {
        _rules.remove(rule)
        saveRules()
    }

    private fun saveRules() {
        val jsonStr = json.encodeToString(rules.toList())
        appPrefs.ttsWordReplacements = jsonStr
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsWordReplacementScreen(
    navController: NavHostController,
    viewModel: TtsWordReplacementViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = VBookTheme.backgroundColor(),
        topBar = {
            TopAppBar(
                title = { Text("Thay thế từ", fontWeight = FontWeight.Bold, color = VBookTheme.textColor()) },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        colors = IconButtonDefaults.iconButtonColors(contentColor = VBookTheme.textColor())
                    ) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VBookTheme.backgroundColor()
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = VBookTheme.primaryColor(),
                contentColor = Color.White
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Thêm từ thay thế")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val rules = viewModel.rules
            if (rules.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Chưa có quy tắc thay thế từ nào.",
                        color = VBookTheme.subTextColor(),
                        fontSize = 16.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    items(rules, key = { it.pattern }) { rule ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(VBookTheme.cardColor())
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = rule.pattern,
                                    color = VBookTheme.textColor(),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (rule.replacement.isEmpty()) "<Trống>" else rule.replacement,
                                    color = if (rule.replacement.isEmpty()) VBookTheme.subTextColor().copy(alpha = 0.6f) else VBookTheme.subTextColor(),
                                    fontSize = 13.sp
                                )
                            }
                            
                            IconButton(
                                onClick = {
                                    viewModel.removeRule(rule)
                                    Toast.makeText(context, "Đã xóa quy tắc cho '${rule.pattern}'", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Xóa",
                                    tint = Color.Red.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.height(80.dp)) // Tránh bị FAB đè lên
                    }
                }
            }
        }
    }

    // Add Replacement Rule Dialog
    if (showAddDialog) {
        var pattern by remember { mutableStateOf("") }
        var replacement by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = VBookTheme.cardColor(),
            title = { Text("Thêm từ thay thế", color = VBookTheme.textColor(), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = pattern,
                        onValueChange = { pattern = it },
                        label = { Text("Từ gốc / Ký tự cần thay thế") },
                        placeholder = { Text("Ví dụ: đ.") },
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = VBookTheme.textColor(),
                            unfocusedTextColor = VBookTheme.textColor(),
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = replacement,
                        onValueChange = { replacement = it },
                        label = { Text("Từ thay thế (để trống để lọc bỏ)") },
                        placeholder = { Text("Ví dụ: đi") },
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = VBookTheme.textColor(),
                            unfocusedTextColor = VBookTheme.textColor(),
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pattern.isBlank()) {
                            Toast.makeText(context, "Từ gốc không được để trống", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.addRule(pattern, replacement)
                            showAddDialog = false
                            Toast.makeText(context, "Đã thêm quy tắc thành công!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VBookTheme.primaryColor())
                ) {
                    Text("Lưu", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Hủy", color = Color.Gray)
                }
            }
        )
    }
}
