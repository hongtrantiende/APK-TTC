package com.nam.novelreader.feature.reader

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import java.net.URLEncoder

/**
 * LookupProvider — một provider từ lockup.json
 */
data class LookupProvider(
    val title: String,
    val url: String   // URL với %s là placeholder cho từ cần tra
)

/**
 * DictionaryLookupBar — hiển thị khi user long-press 1 từ trong reader
 * Cho phép mở tra từ trong các dictionary/search engine bên ngoài
 *
 * @param selectedText  từ/cụm từ được chọn
 * @param providers     danh sách provider từ lockup.json
 * @param onDismiss     callback đóng bar
 */
@Composable
fun DictionaryLookupBar(
    selectedText: String,
    providers: List<LookupProvider>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    AnimatedVisibility(
        visible = selectedText.isNotEmpty(),
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it },
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(Modifier.padding(12.dp)) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = selectedText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, "Đóng", Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Provider chips
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(providers) { provider ->
                        ProviderChip(
                            label = provider.title,
                            onClick = {
                                val encoded = try {
                                    URLEncoder.encode(selectedText, "UTF-8")
                                } catch (e: Exception) {
                                    selectedText
                                }
                                val url = provider.url.replace("%s", encoded)
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderChip(label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

/**
 * loadLookupProviders — đọc lockup.json từ assets
 * Trả về danh sách default nếu file không tồn tại
 */
fun loadLookupProviders(jsonContent: String): List<LookupProvider> {
    return try {
        val arr = JSONArray(jsonContent)
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            LookupProvider(
                title = obj.getString("title"),
                url = obj.getString("url")
            )
        }
    } catch (e: Exception) {
        // Default providers nếu đọc file lỗi
        listOf(
            LookupProvider("G.Translate", "https://translate.google.com/?sl=auto&tl=vi&text=%s&op=translate"),
            LookupProvider("G.Search", "https://www.google.com/search?q=%s"),
            LookupProvider("Hanzii", "https://hanzii.net/search/word/%s?hl=vi-VN")
        )
    }
}
