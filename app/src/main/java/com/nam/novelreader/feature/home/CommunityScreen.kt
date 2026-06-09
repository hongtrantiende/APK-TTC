package com.nam.novelreader.feature.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.nam.novelreader.feature.components.VBookTheme
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CommunityDatabaseEntryPoint {
    fun supabaseAuthManager(): com.nam.novelreader.data.network.SupabaseAuthManager
    fun appPreferences(): com.nam.novelreader.data.preferences.AppPreferences
}

data class ChatGroup(
    val id: String,
    val name: String,
    val avatarUrl: String, // Có thể là Base64 hoặc URL
    val isPublic: Boolean,
    val createdBy: String,
    val createdAt: String,
    val isPinned: Boolean
) {
    companion object {
        fun fromJson(json: JSONObject): ChatGroup {
            return ChatGroup(
                id = json.optString("id"),
                name = json.optString("name"),
                avatarUrl = json.optString("avatar_url"),
                isPublic = json.optBoolean("is_public", true),
                createdBy = json.optString("created_by"),
                createdAt = json.optString("created_at"),
                isPinned = json.optBoolean("is_pinned", false)
            )
        }
    }
}

data class ChatMessage(
    val id: Long,
    val groupId: String,
    val senderId: String,
    val senderName: String,
    val senderEmail: String,
    val senderAvatar: String,
    val content: String,
    val createdAt: String
) {
    companion object {
        fun fromJson(json: JSONObject): ChatMessage {
            return ChatMessage(
                id = json.optLong("id"),
                groupId = json.optString("group_id"),
                senderId = json.optString("sender_id"),
                senderName = json.optString("sender_name"),
                senderEmail = json.optString("sender_email"),
                senderAvatar = json.optString("sender_avatar", ""),
                content = json.optString("content"),
                createdAt = json.optString("created_at")
            )
        }
    }
}

// Helper nén ảnh cục bộ từ path sang Base64 siêu nhẹ
fun getAvatarBase64FromPath(path: String): String {
    if (path.isBlank()) return ""
    return try {
        val file = java.io.File(path)
        if (!file.exists()) return ""
        val originalBitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return ""
        // Nén ảnh nhỏ 80x80 cho avatar cực kỳ nhẹ để tối ưu REST body
        val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, 80, 80, true)
        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        val bytes = outputStream.toByteArray()
        "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    } catch (_: Exception) {
        ""
    }
}

// Helper giải mã ảnh Base64 sang Bitmap
fun decodeBase64ToBitmap(base64Str: String): Bitmap? {
    return try {
        val cleanStr = if (base64Str.startsWith("data:image")) {
            base64Str.substringAfter(",")
        } else {
            base64Str
        }
        val decodedBytes = Base64.decode(cleanStr, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
    } catch (e: Exception) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(
    navController: NavHostController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val entryPoint = remember {
        dagger.hilt.EntryPoints.get(context.applicationContext, CommunityDatabaseEntryPoint::class.java)
    }
    val authManager = entryPoint.supabaseAuthManager()
    val appPrefs = entryPoint.appPreferences()

    // Trạng thái đăng nhập
    val isLoggedIn = appPrefs.supabaseIsLoggedIn
    val userEmail = appPrefs.supabaseUserEmail
    val isAdmin = isLoggedIn && userEmail.trim().lowercase(java.util.Locale.ROOT) == "nthanhnam@gmail.com"

    var chatGroups by remember { mutableStateOf<List<ChatGroup>>(emptyList()) }
    var isLoadingGroups by remember { mutableStateOf(false) }
    var activeChatGroup by remember { mutableStateOf<ChatGroup?>(null) }
    
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    
    // Biến lưu tổng số lượng user và số lượng user trong nhóm hiện tại
    var totalUserCount by remember { mutableStateOf(0) }
    var activeGroupMemberCount by remember { mutableStateOf(0) }

    var groupToDelete by remember { mutableStateOf<ChatGroup?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Tải danh sách nhóm chat công khai
    val loadGroups = suspend {
        isLoadingGroups = true
        val result = authManager.getChatGroups()
        isLoadingGroups = false
        if (result.isSuccess) {
            val arr = result.getOrNull() ?: JSONArray()
            val list = mutableListOf<ChatGroup>()
            for (i in 0 until arr.length()) {
                list.add(ChatGroup.fromJson(arr.getJSONObject(i)))
            }
            chatGroups = list
        } else {
            Toast.makeText(context, "Lỗi tải nhóm chat: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(activeChatGroup) {
        if (activeChatGroup == null) {
            loadGroups()
            activeGroupMemberCount = 0
            // Tải tổng số lượng user đã đăng ký
            scope.launch {
                val userCountRes = authManager.getTotalUserCount()
                if (userCountRes.isSuccess) {
                    totalUserCount = userCountRes.getOrDefault(0)
                }
            }
        }
    }

    Scaffold(
        containerColor = VBookTheme.backgroundColor(),
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(
                            text = activeChatGroup?.name ?: "Cộng đồng", 
                            color = VBookTheme.textColor(), 
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        if (activeChatGroup == null) {
                            Text(
                                text = if (totalUserCount > 0) "$totalUserCount thành viên đã tham gia" else "Kết nối & Giao lưu",
                                color = VBookTheme.subTextColor(),
                                fontSize = 12.sp
                            )
                        } else {
                            Text(
                                text = if (activeGroupMemberCount > 0) "$activeGroupMemberCount người đã tham gia" else "Đang kết nối...",
                                color = VBookTheme.subTextColor(),
                                fontSize = 12.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (activeChatGroup != null) {
                        IconButton(onClick = { activeChatGroup = null }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Quay lại", tint = VBookTheme.textColor())
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VBookTheme.backgroundColor()
                )
            )
        },
        floatingActionButton = {
            // Chỉ hiển thị nút thêm cho Admin và ở chế độ xem danh sách nhóm
            if (activeChatGroup == null && isAdmin) {
                FloatingActionButton(
                    onClick = { showCreateGroupDialog = true },
                    containerColor = VBookTheme.primaryColor(),
                    contentColor = Color.White
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Tạo nhóm chat")
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (activeChatGroup != null) {
                // Hiển thị phòng chat
                ChatRoomView(
                    chatGroup = activeChatGroup!!,
                    authManager = authManager,
                    appPrefs = appPrefs,
                    isLoggedIn = isLoggedIn,
                    onMemberCountChanged = { activeGroupMemberCount = it }
                )
            } else {
                // Hiển thị danh sách nhóm chat
                if (isLoadingGroups) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = VBookTheme.primaryColor())
                    }
                } else if (chatGroups.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Chưa có nhóm chat nào.\nHãy đợi Admin tạo nhóm chat công khai nhé!",
                            color = VBookTheme.subTextColor(),
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(chatGroups, key = { it.id }) { group ->
                            ChatGroupItem(
                                group = group,
                                isAdmin = isAdmin,
                                onClick = { activeChatGroup = group },
                                onPinClick = {
                                    scope.launch {
                                        val newPinned = !group.isPinned
                                        val result = authManager.pinChatGroup(group.id, newPinned)
                                        if (result.isSuccess) {
                                            Toast.makeText(context, if (newPinned) "Đã ghim nhóm!" else "Đã bỏ ghim nhóm!", Toast.LENGTH_SHORT).show()
                                            loadGroups()
                                        } else {
                                            Toast.makeText(context, "Lỗi: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onDeleteClick = {
                                    groupToDelete = group
                                    showDeleteConfirmDialog = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialog tạo nhóm chat dành cho Admin
    if (showCreateGroupDialog) {
        CreateGroupDialog(
            onDismiss = { showCreateGroupDialog = false },
            onCreate = { name, avatarBase64, isPublic ->
                scope.launch {
                    val result = authManager.createChatGroup(name, avatarBase64, isPublic)
                    if (result.isSuccess) {
                        Toast.makeText(context, "Tạo nhóm chat thành công!", Toast.LENGTH_SHORT).show()
                        showCreateGroupDialog = false
                        loadGroups()
                    } else {
                        Toast.makeText(context, "Lỗi tạo nhóm: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    // Dialog xác nhận xóa nhóm chat dành cho Admin
    if (showDeleteConfirmDialog && groupToDelete != null) {
        AlertDialog(
            onDismissRequest = { 
                showDeleteConfirmDialog = false
                groupToDelete = null
            },
            containerColor = VBookTheme.cardColor(),
            title = { 
                Text(
                    text = "Xóa nhóm chat", 
                    color = VBookTheme.textColor(), 
                    fontWeight = FontWeight.Bold
                ) 
            },
            text = {
                Text(
                    text = "Bạn có chắc chắn muốn xóa nhóm chat \"${groupToDelete?.name}\" không? Hành động này sẽ xóa vĩnh viễn nhóm và tất cả các tin nhắn trong nhóm.",
                    color = VBookTheme.textColor(),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            showDeleteConfirmDialog = false
                            groupToDelete = null
                        }
                    ) {
                        Text("Hủy", color = VBookTheme.subTextColor())
                    }
                    Button(
                        onClick = {
                            val targetGroup = groupToDelete
                            showDeleteConfirmDialog = false
                            groupToDelete = null
                            if (targetGroup != null) {
                                scope.launch {
                                    val result = authManager.deleteChatGroup(targetGroup.id)
                                    if (result.isSuccess) {
                                        Toast.makeText(context, "Đã xóa nhóm chat thành công", Toast.LENGTH_SHORT).show()
                                        loadGroups()
                                    } else {
                                        Toast.makeText(context, "Xóa nhóm thất bại: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE57373))
                    ) {
                        Text("Xóa", color = Color.White)
                    }
                }
            }
        )
    }
}

@Composable
fun ChatGroupItem(
    group: ChatGroup,
    isAdmin: Boolean,
    onClick: () -> Unit,
    onPinClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val cardBg = VBookTheme.cardColor()
    val textCol = VBookTheme.textColor()
    val subTextCol = VBookTheme.subTextColor()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Group Avatar
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(VBookTheme.backgroundColor())
                    .border(1.dp, VBookTheme.primaryColor().copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                val bitmap = remember(group.avatarUrl) {
                    if (group.avatarUrl.startsWith("data:image") || group.avatarUrl.length > 50) {
                        decodeBase64ToBitmap(group.avatarUrl)
                    } else {
                        null
                    }
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Group Avatar",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else if (group.avatarUrl.isNotBlank() && group.avatarUrl.startsWith("http")) {
                    AsyncImage(
                        model = group.avatarUrl,
                        contentDescription = "Group Avatar",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Group,
                        contentDescription = "Default Avatar",
                        tint = VBookTheme.primaryColor(),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = group.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = textCol,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (group.isPinned) {
                        Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = "Đã ghim",
                            tint = VBookTheme.primaryColor(),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Bấm để tham gia trò chuyện",
                    fontSize = 13.sp,
                    color = subTextCol
                )
            }

            if (isAdmin) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onPinClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = if (group.isPinned) "Bỏ ghim" else "Ghim",
                            tint = if (group.isPinned) VBookTheme.primaryColor() else subTextCol.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Xóa nhóm",
                            tint = Color(0xFFE57373),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = "Vào nhóm",
                    tint = subTextCol,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun CreateGroupDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, avatarBase64: String, isPublic: Boolean) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var avatarBase64 by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(true) }
    var selectedImageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isCompressing by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            isCompressing = true
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val originalBitmap = BitmapFactory.decodeStream(inputStream)
                    if (originalBitmap != null) {
                        // Nén ảnh về 120x120 để chuyển thành Base64 nhẹ
                        val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, 120, 120, true)
                        val outputStream = ByteArrayOutputStream()
                        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                        val bytes = outputStream.toByteArray()
                        avatarBase64 = "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
                        selectedImageBitmap = scaledBitmap
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Lỗi nén ảnh: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isCompressing = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VBookTheme.cardColor(),
        title = { Text("Tạo nhóm chat mới", color = VBookTheme.textColor(), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Chọn ảnh đại diện nhóm
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(VBookTheme.backgroundColor())
                            .border(1.dp, VBookTheme.primaryColor().copy(alpha = 0.3f), CircleShape)
                            .clickable { imagePickerLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedImageBitmap != null) {
                            Image(
                                bitmap = selectedImageBitmap!!.asImageBitmap(),
                                contentDescription = "Selected Group Image",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.PhotoCamera, null, tint = VBookTheme.subTextColor())
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Chọn ảnh", fontSize = 10.sp, color = VBookTheme.subTextColor())
                            }
                        }
                    }
                    if (isCompressing) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Đang xử lý ảnh...", fontSize = 11.sp, color = VBookTheme.primaryColor())
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Tên nhóm chat") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Nhóm công khai", color = VBookTheme.textColor())
                    Switch(
                        checked = isPublic,
                        onCheckedChange = { isPublic = it }
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) {
                    Text("Hủy", color = VBookTheme.subTextColor())
                }
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            onCreate(name.trim(), avatarBase64, isPublic)
                        }
                    },
                    enabled = name.isNotBlank() && !isCompressing,
                    colors = ButtonDefaults.buttonColors(containerColor = VBookTheme.primaryColor())
                ) {
                    Text("Tạo", color = Color.White)
                }
            }
        }
    )
}

@Composable
fun ChatRoomView(
    chatGroup: ChatGroup,
    authManager: com.nam.novelreader.data.network.SupabaseAuthManager,
    appPrefs: com.nam.novelreader.data.preferences.AppPreferences,
    isLoggedIn: Boolean,
    onMemberCountChanged: (Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var isLoadingMessages by remember { mutableStateOf(false) }
    var messageText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Tải tin nhắn từ API
    val loadMessages = suspend {
        val result = authManager.getChatMessages(chatGroup.id)
        if (result.isSuccess) {
            val arr = result.getOrNull() ?: JSONArray()
            val list = mutableListOf<ChatMessage>()
            for (i in 0 until arr.length()) {
                list.add(ChatMessage.fromJson(arr.getJSONObject(i)))
            }
            messages = list
            
            // Tính số lượng thành viên đã tham gia chat và thông báo cho cha
            val memberCount = list.map { it.senderId }.distinct().size
            onMemberCountChanged(memberCount)

            // Tự động cuộn xuống dưới cùng khi có tin nhắn mới
            if (list.isNotEmpty()) {
                scope.launch {
                    listState.animateScrollToItem(list.size - 1)
                }
            }
        }
    }

    // Polling tự động làm mới tin nhắn mỗi 3 giây
    LaunchedEffect(chatGroup.id) {
        isLoadingMessages = true
        loadMessages()
        isLoadingMessages = false
        
        while (true) {
            delay(3000)
            loadMessages()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VBookTheme.backgroundColor())
    ) {
        // Danh sách tin nhắn
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (isLoadingMessages && messages.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = VBookTheme.primaryColor())
                }
            } else if (messages.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Phòng chat trống. Hãy gửi tin nhắn chào hỏi mọi người!", color = VBookTheme.subTextColor(), fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        val isMine = msg.senderId == appPrefs.supabaseUserId
                        ChatMessageItem(msg = msg, isMine = isMine)
                    }
                }
            }
        }

        // Ô nhập tin nhắn
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 2.dp,
            color = VBookTheme.cardColor(),
            border = androidx.compose.foundation.BorderStroke(1.dp, VBookTheme.primaryColor().copy(alpha = 0.05f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isLoggedIn) {
                    Text(
                        text = "Vui lòng đăng nhập tài khoản để gửi tin nhắn",
                        color = VBookTheme.subTextColor(),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    )
                } else {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        placeholder = { Text("Nhập tin nhắn...") },
                        maxLines = 3,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VBookTheme.primaryColor(),
                            unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f)
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (messageText.isNotBlank() && !isSending) {
                                isSending = true
                                val content = messageText.trim()
                                messageText = ""
                                scope.launch {
                                    val myAvatarBase64 = getAvatarBase64FromPath(appPrefs.userAvatarPath)
                                    val result = authManager.sendChatMessage(chatGroup.id, content, myAvatarBase64)
                                    isSending = false
                                    if (result.isSuccess) {
                                        loadMessages()
                                    } else {
                                        Toast.makeText(context, "Lỗi gửi: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        enabled = messageText.isNotBlank() && !isSending,
                        colors = IconButtonDefaults.iconButtonColors(contentColor = VBookTheme.primaryColor())
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = VBookTheme.primaryColor(), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Send, contentDescription = "Gửi")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatMessageItem(
    msg: ChatMessage,
    isMine: Boolean
) {
    val themeColor = VBookTheme.primaryColor()
    val cardColor = VBookTheme.cardColor()
    val textCol = VBookTheme.textColor()
    
    val bubbleColor = if (isMine) {
        themeColor.copy(alpha = 0.15f)
    } else {
        cardColor
    }

    val bubbleShape = if (isMine) {
        RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
    }

    // Tính toán màu sắc avatar cố định dựa trên senderId để mỗi người có 1 màu riêng biệt
    val avatarBgColor = remember(msg.senderId) {
        val hash = msg.senderId.hashCode()
        val colors = listOf(
            Color(0xFFE57373), Color(0xFFF06292), Color(0xFFBA68C8), Color(0xFF9575CD),
            Color(0xFF7986CB), Color(0xFF64B5F6), Color(0xFF4FC3F7), Color(0xFF4DB6AC),
            Color(0xFF81C784), Color(0xFFAED581), Color(0xFFD4E157), Color(0xFFFFD54F),
            Color(0xFFFFB74D), Color(0xFFFF8A65), Color(0xFFA1887F), Color(0xFF90A4AE)
        )
        colors[Math.abs(hash) % colors.size]
    }
    
    val initial = remember(msg.senderName) {
        if (msg.senderName.isNotBlank()) {
            msg.senderName.take(1).uppercase(java.util.Locale.ROOT)
        } else {
            "?"
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isMine) {
            // Avatar hình tròn của người khác
            val bitmap = remember(msg.senderAvatar) {
                if (msg.senderAvatar.startsWith("data:image") || msg.senderAvatar.length > 50) {
                    decodeBase64ToBitmap(msg.senderAvatar)
                } else {
                    null
                }
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (bitmap == null) avatarBgColor else Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Sender Avatar",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = initial,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
        ) {
            // Tên người gửi
            Text(
                text = msg.senderName,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isMine) themeColor else VBookTheme.subTextColor(),
                modifier = Modifier.padding(
                    start = if (isMine) 0.dp else 4.dp,
                    end = if (isMine) 4.dp else 0.dp,
                    bottom = 2.dp
                )
            )

            // Bong bóng tin nhắn
            Surface(
                color = bubbleColor,
                shape = bubbleShape,
                border = if (isMine) androidx.compose.foundation.BorderStroke(1.dp, themeColor.copy(alpha = 0.3f)) else null,
                modifier = Modifier.widthIn(max = 250.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(
                        text = msg.content,
                        fontSize = 14.sp,
                        color = textCol,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        if (isMine) {
            Spacer(modifier = Modifier.width(8.dp))
            // Avatar hình tròn của chính mình
            val bitmap = remember(msg.senderAvatar) {
                if (msg.senderAvatar.startsWith("data:image") || msg.senderAvatar.length > 50) {
                    decodeBase64ToBitmap(msg.senderAvatar)
                } else {
                    null
                }
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (bitmap == null) avatarBgColor else Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "My Avatar",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = initial,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
