package com.nam.novelreader.feature.settings

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.nam.novelreader.data.network.SupabaseAuthManager
import com.nam.novelreader.data.network.SupabaseProfile
import com.nam.novelreader.feature.components.VBookTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class AdminVipManagementViewModel @Inject constructor(
    val authManager: SupabaseAuthManager
) : ViewModel() {

    private val _profiles = MutableStateFlow<List<SupabaseProfile>>(emptyList())
    val profiles: StateFlow<List<SupabaseProfile>> = _profiles.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadProfiles()
    }

    fun loadProfiles() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val result = authManager.getAllProfiles()
            result.onSuccess {
                _profiles.value = it
            }.onFailure {
                _error.value = it.message ?: "Lỗi tải danh sách người dùng"
            }
            _isLoading.value = false
        }
    }

    fun updateProfile(
        id: String,
        displayName: String,
        vipUntil: String?,
        adminModelQuota: Int,
        adminDailyQuotaLimit: Int,
        adminAssignedModel: String?,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = authManager.updateUserProfile(
                id = id,
                displayName = displayName,
                vipUntil = vipUntil,
                adminModelQuota = adminModelQuota,
                adminDailyQuotaLimit = adminDailyQuotaLimit,
                adminAssignedModel = adminAssignedModel
            )
            result.onSuccess {
                loadProfiles()
                onSuccess()
            }.onFailure {
                _error.value = it.message ?: "Cập nhật thất bại"
                _isLoading.value = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminVipManagementScreen(
    navController: NavHostController,
    viewModel: AdminVipManagementViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val profiles by viewModel.profiles.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.error.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var editingProfile by remember { mutableStateOf<SupabaseProfile?>(null) }

    val filteredProfiles = remember(profiles, searchQuery) {
        if (searchQuery.isBlank()) {
            profiles
        } else {
            profiles.filter {
                it.email.contains(searchQuery, ignoreCase = true) ||
                        it.displayName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // Display error Toast if any
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        containerColor = VBookTheme.backgroundColor(),
        topBar = {
            TopAppBar(
                title = { Text("Quản lý thành viên VIP", fontWeight = FontWeight.Bold, color = VBookTheme.textColor()) },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        colors = IconButtonDefaults.iconButtonColors(contentColor = VBookTheme.textColor())
                    ) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.loadProfiles() },
                        colors = IconButtonDefaults.iconButtonColors(contentColor = VBookTheme.textColor())
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Tải lại")
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
        ) {
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Tìm theo Gmail hoặc tên...", color = VBookTheme.subTextColor()) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = VBookTheme.subTextColor()) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear", tint = VBookTheme.subTextColor())
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = VBookTheme.textColor(),
                    unfocusedTextColor = VBookTheme.textColor(),
                    focusedContainerColor = VBookTheme.cardColor(),
                    unfocusedContainerColor = VBookTheme.cardColor(),
                    focusedBorderColor = VBookTheme.primaryColor().copy(alpha = 0.5f),
                    unfocusedBorderColor = VBookTheme.primaryColor().copy(alpha = 0.15f)
                ),
                shape = RoundedCornerShape(24.dp)
            )

            if (isLoading && profiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = VBookTheme.primaryColor())
                }
            } else if (filteredProfiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isBlank()) "Danh sách tài khoản trống" else "Không tìm thấy kết quả phù hợp",
                        color = VBookTheme.subTextColor(),
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredProfiles, key = { it.id }) { profile ->
                        ProfileItem(
                            profile = profile,
                            onEditClick = { editingProfile = profile }
                        )
                    }
                }
            }
        }
    }

    if (editingProfile != null) {
        val currentProfile = profiles.find { it.id == editingProfile!!.id } ?: editingProfile!!
        EditProfileDialog(
            profile = currentProfile,
            onDismiss = { editingProfile = null },
            onSave = { displayName, vipUntil ->
                viewModel.updateProfile(
                    id = currentProfile.id,
                    displayName = displayName,
                    vipUntil = vipUntil,
                    adminModelQuota = currentProfile.adminModelQuota,
                    adminDailyQuotaLimit = currentProfile.adminDailyQuotaLimit,
                    adminAssignedModel = currentProfile.adminAssignedModel,
                    onSuccess = {
                        Toast.makeText(context, "Cập nhật thành công!", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        )
    }
}

@Composable
fun ProfileItem(
    profile: SupabaseProfile,
    onEditClick: () -> Unit
) {
    val isVip = remember(profile.vipUntil) {
        if (profile.vipUntil.isNullOrBlank()) false else {
            try {
                val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val date = formatter.parse(profile.vipUntil.substringBefore("T"))
                date != null && date.after(Date())
            } catch (_: Exception) {
                false
            }
        }
    }

    val vipStatusText = remember(profile.vipUntil, isVip) {
        if (isVip) {
            try {
                val inputFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val date = inputFormatter.parse(profile.vipUntil!!.substringBefore("T"))
                if (date != null) {
                    val diff = date.time - System.currentTimeMillis()
                    val days = (diff / (1000 * 60 * 60 * 24)).coerceAtLeast(0) + 1
                    "VIP còn $days ngày"
                } else "VIP hoạt động"
            } catch (_: Exception) {
                "VIP hoạt động"
            }
        } else {
            "Thành viên thường"
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEditClick() },
        colors = CardDefaults.cardColors(containerColor = VBookTheme.cardColor()),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile Initials / Avatar
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(VBookTheme.primaryColor().copy(alpha = 0.1f))
                    .border(
                        width = 1.dp,
                        color = if (isVip) Color(0xFFFFD700) else VBookTheme.primaryColor().copy(alpha = 0.2f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                val initials = if (profile.displayName.isNotBlank()) {
                    profile.displayName.take(2).uppercase(Locale.getDefault())
                } else if (profile.email.isNotBlank()) {
                    profile.email.take(2).uppercase(Locale.getDefault())
                } else "U"
                
                Text(
                    text = initials,
                    color = VBookTheme.primaryColor(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (profile.displayName.isNotBlank()) profile.displayName else "Chưa đặt tên",
                        color = VBookTheme.textColor(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    if (isVip) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "VIP",
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    text = profile.email,
                    color = VBookTheme.subTextColor(),
                    fontSize = 12.sp
                )
                
                Spacer(modifier = Modifier.height(4.dp))
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = vipStatusText,
                    color = if (isVip) Color(0xFFE5A900) else VBookTheme.subTextColor(),
                    fontWeight = if (isVip) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 12.sp
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Chỉnh sửa",
                    tint = VBookTheme.subTextColor(),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun EditProfileDialog(
    profile: SupabaseProfile,
    onDismiss: () -> Unit,
    onSave: (displayName: String, vipUntil: String?) -> Unit
) {
    val context = LocalContext.current
    var displayName by remember(profile) { mutableStateOf(profile.displayName) }
    var isVipActive by remember(profile) {
        mutableStateOf(
            if (profile.vipUntil.isNullOrBlank()) false else {
                try {
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(profile.vipUntil.substringBefore("T"))
                    date != null && date.after(Date())
                } catch (_: Exception) {
                    false
                }
            }
        )
    }

    var vipUntilDate by remember(profile) {
        mutableStateOf(
            if (!profile.vipUntil.isNullOrBlank()) {
                profile.vipUntil.substringBefore("T")
            } else ""
        )
    }

    val handleGrantDays = { days: Int ->
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, days)
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val newDate = formatter.format(calendar.time)
        val vipUntilIso = "${newDate}T23:59:59Z"
        onSave(displayName, vipUntilIso)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VBookTheme.cardColor(),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = VBookTheme.primaryColor(), modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cấu hình thành viên", color = VBookTheme.textColor(), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 450.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Cấu hình quyền lợi cho tài khoản: ${profile.email}",
                    color = VBookTheme.subTextColor(),
                    fontSize = 12.sp
                )

                // Nickname
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Tên nhân vật (Nickname)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (displayName.trim() != profile.displayName) {
                                val vipUntilIso = if (isVipActive && vipUntilDate.isNotBlank()) {
                                    "${vipUntilDate}T23:59:59Z"
                                } else null
                                onSave(displayName.trim(), vipUntilIso)
                            }
                        })
                    )
                    if (displayName.trim() != profile.displayName) {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                val vipUntilIso = if (isVipActive && vipUntilDate.isNotBlank()) {
                                    "${vipUntilDate}T23:59:59Z"
                                } else null
                                onSave(displayName.trim(), vipUntilIso)
                            },
                            colors = IconButtonDefaults.iconButtonColors(contentColor = VBookTheme.primaryColor())
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = "Lưu tên")
                        }
                    }
                }

                HorizontalDivider(color = VBookTheme.backgroundColor().copy(alpha = 0.5f))

                // VIP Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Kích hoạt VIP", color = VBookTheme.textColor(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                    Switch(
                        checked = isVipActive,
                        onCheckedChange = { active ->
                            val vipUntilIso = if (active) {
                                val dateStr = if (vipUntilDate.isNotBlank()) vipUntilDate else {
                                    val calendar = Calendar.getInstance()
                                    calendar.add(Calendar.DAY_OF_YEAR, 30)
                                    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
                                }
                                "${dateStr}T23:59:59Z"
                            } else {
                                null
                            }
                            onSave(displayName, vipUntilIso)
                        }
                    )
                }

                if (isVipActive) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(VBookTheme.backgroundColor().copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .border(1.dp, VBookTheme.primaryColor().copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Ngày hết hạn VIP:", color = VBookTheme.textColor(), fontSize = 13.sp)
                            
                            val dateText = if (vipUntilDate.isNotBlank()) vipUntilDate else "Chọn ngày"
                            Text(
                                text = dateText,
                                color = VBookTheme.primaryColor(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier
                                    .clickable {
                                        val calendar = Calendar.getInstance()
                                        if (vipUntilDate.isNotBlank()) {
                                            try {
                                                val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(vipUntilDate)
                                                if (date != null) calendar.time = date
                                            } catch (_: Exception) {}
                                        }
                                        DatePickerDialog(
                                            context,
                                            { _, year, month, dayOfMonth ->
                                                val formattedMonth = String.format("%02d", month + 1)
                                                val formattedDay = String.format("%02d", dayOfMonth)
                                                val newDate = "$year-$formattedMonth-$formattedDay"
                                                val vipUntilIso = "${newDate}T23:59:59Z"
                                                onSave(displayName, vipUntilIso)
                                            },
                                            calendar.get(Calendar.YEAR),
                                            calendar.get(Calendar.MONTH),
                                            calendar.get(Calendar.DAY_OF_MONTH)
                                        ).show()
                                    }
                                    .padding(vertical = 4.dp, horizontal = 8.dp)
                            )
                        }

                        // Quick days grant
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("30 ngày" to 30, "90 ngày" to 90, "365 ngày" to 365).forEach { (label, days) ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(VBookTheme.backgroundColor(), RoundedCornerShape(6.dp))
                                        .clickable { handleGrantDays(days) }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(label, color = VBookTheme.textColor(), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng", color = VBookTheme.textColor(), fontWeight = FontWeight.Bold)
            }
        }
    )
}
