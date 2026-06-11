package com.nam.novelreader.feature.localreader

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import android.webkit.WebViewClient
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * LocalFileType — các loại file VBook hỗ trợ mở từ Intent
 */
enum class LocalFileType {
    TXT, EPUB, CBZ, UNKNOWN;

    companion object {
        fun fromUri(uri: Uri, mimeType: String?): LocalFileType {
            val path = uri.path?.lowercase() ?: ""
            val mime = mimeType?.lowercase() ?: ""
            return when {
                path.endsWith(".txt") || mime.contains("text/plain") -> TXT
                path.endsWith(".epub") || mime.contains("epub") -> EPUB
                path.endsWith(".cbz") || mime.contains("cbz") -> CBZ
                else -> UNKNOWN
            }
        }
    }
}

/**
 * LocalFileReaderScreen — hiển thị nội dung file local
 * Hỗ trợ: TXT, EPUB (HTML), CBZ (ảnh)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalFileReaderScreen(
    uri: Uri,
    mimeType: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val fileType = remember(uri) { LocalFileType.fromUri(uri, mimeType) }
    var title by remember { mutableStateOf(uri.lastPathSegment ?: "File") }
    var content by remember { mutableStateOf<LocalContent?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uri) {
        loading = true
        error = null
        try {
            content = withContext(Dispatchers.IO) {
                val stream = context.contentResolver.openInputStream(uri)
                    ?: throw Exception("Không thể mở file")
                when (fileType) {
                    LocalFileType.TXT -> readTxtFile(stream)
                    LocalFileType.EPUB -> readEpubFile(stream)
                    LocalFileType.CBZ -> readCbzFile(stream)
                    LocalFileType.UNKNOWN -> LocalContent.Text("Định dạng file không được hỗ trợ")
                }
            }
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Quay lại")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                error != null -> Text(
                    "Lỗi: $error",
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    color = MaterialTheme.colorScheme.error
                )
                content != null -> ContentView(content = content!!)
            }
        }
    }
}

// ─── Content types ────────────────────────────────────────────────────────────

sealed class LocalContent {
    data class Text(val text: String) : LocalContent()
    data class Html(val html: String) : LocalContent()
    data class Images(val byteArrays: List<ByteArray>) : LocalContent()
}

// ─── Viewers ─────────────────────────────────────────────────────────────────

@Composable
private fun ContentView(content: LocalContent) {
    when (content) {
        is LocalContent.Text -> {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Chunk thành paragraphs để tránh OOM
                val paragraphs = content.text.split("\n\n")
                items(paragraphs) { para ->
                    Text(para.trim(), lineHeight = MaterialTheme.typography.bodyLarge.lineHeight)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        is LocalContent.Html -> {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewClient = WebViewClient()
                        settings.javaScriptEnabled = false
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                    }
                },
                update = { webView ->
                    webView.loadDataWithBaseURL(
                        "file:///android_asset/",
                        content.html,
                        "text/html",
                        "UTF-8",
                        null
                    )
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        is LocalContent.Images -> {
            LazyColumn(
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(content.byteArrays.indices.toList()) { idx ->
                    AsyncImage(
                        model = content.byteArrays[idx],
                        contentDescription = "Trang ${idx + 1}",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

// ─── File parsers ─────────────────────────────────────────────────────────────

private fun readTxtFile(stream: InputStream): LocalContent.Text {
    return LocalContent.Text(stream.bufferedReader(Charsets.UTF_8).readText())
}

/**
 * Đọc EPUB: unzip → tìm OPF → đọc các HTML chapter đầu tiên → ghép HTML
 */
private fun readEpubFile(stream: InputStream): LocalContent.Html {
    val files = mutableMapOf<String, ByteArray>()
    ZipInputStream(stream).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
            if (!entry.isDirectory) {
                files[entry.name] = zip.readBytes()
            }
            entry = zip.nextEntry
        }
    }

    // Tìm OPF file
    val containerXml = files["META-INF/container.xml"]?.toString(Charsets.UTF_8) ?: ""
    val opfPath = Regex("full-path=\"([^\"]+\\.opf)\"").find(containerXml)?.groupValues?.get(1)
        ?: files.keys.firstOrNull { it.endsWith(".opf") }

    if (opfPath == null) {
        // Không tìm được OPF, thử đọc HTML đầu tiên
        val htmlContent = files.entries.firstOrNull { it.key.endsWith(".html") || it.key.endsWith(".xhtml") }
            ?.value?.toString(Charsets.UTF_8) ?: "<p>Không thể đọc nội dung EPUB</p>"
        return LocalContent.Html(wrapHtml(htmlContent))
    }

    // Parse OPF để lấy spine order
    val opfContent = files[opfPath]?.toString(Charsets.UTF_8) ?: ""
    val opfDir = opfPath.substringBeforeLast("/", "")

    // Lấy item hrefs từ manifest
    val itemMap = mutableMapOf<String, String>() // id → href
    Regex("""<item[^>]+id="([^"]+)"[^>]+href="([^"]+)"""").findAll(opfContent).forEach { m ->
        itemMap[m.groupValues[1]] = m.groupValues[2]
    }

    // Lấy spine order
    val spineIds = Regex("""<itemref[^>]+idref="([^"]+)"""").findAll(opfContent).map { it.groupValues[1] }.toList()

    // Đọc tối đa 5 chapter đầu để không OOM
    val htmlBuilder = StringBuilder()
    htmlBuilder.append("<html><body>")
    spineIds.take(5).forEach { id ->
        val href = itemMap[id] ?: return@forEach
        val fullPath = if (opfDir.isEmpty()) href else "$opfDir/$href"
        val chapterHtml = files[fullPath]?.toString(Charsets.UTF_8) ?: return@forEach
        // Strip outer html/body tags
        val body = Regex("<body[^>]*>(.*?)</body>", RegexOption.DOT_MATCHES_ALL).find(chapterHtml)?.groupValues?.get(1)
            ?: chapterHtml
        htmlBuilder.append(body)
        htmlBuilder.append("<hr/>")
    }
    htmlBuilder.append("</body></html>")

    return LocalContent.Html(htmlBuilder.toString())
}

/**
 * Đọc CBZ: unzip → lấy tất cả ảnh (PNG/JPG/WEBP) theo thứ tự tên file
 */
private fun readCbzFile(stream: InputStream): LocalContent.Images {
    val images = mutableListOf<Pair<String, ByteArray>>()
    ZipInputStream(stream).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
            val name = entry.name.lowercase()
            if (!entry.isDirectory && (name.endsWith(".jpg") || name.endsWith(".jpeg")
                        || name.endsWith(".png") || name.endsWith(".webp") || name.endsWith(".gif"))) {
                images.add(entry.name to zip.readBytes())
            }
            entry = zip.nextEntry
        }
    }
    // Sắp xếp theo tên (đảm bảo thứ tự trang đúng)
    images.sortBy { it.first }
    return LocalContent.Images(images.map { it.second })
}

private fun wrapHtml(content: String) =
    """<html><head><meta charset="UTF-8"/></head><body>$content</body></html>"""
