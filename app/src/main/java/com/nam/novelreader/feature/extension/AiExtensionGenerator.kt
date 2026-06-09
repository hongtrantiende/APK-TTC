package com.nam.novelreader.feature.extension

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nam.novelreader.feature.components.VBookTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.style.TextAlign
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiExtensionGeneratorDialog(
    onDismiss: () -> Unit,
    onExtensionCreated: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE) }

    var webUrl by remember { mutableStateOf("") }
    var selectedProvider by remember { mutableStateOf(prefs.getString("ai_gen_provider", "Gemini") ?: "Gemini") }
    var apiKey by remember { mutableStateOf(prefs.getString("ai_gen_apikey", "") ?: "") }
    var aiModel by remember { mutableStateOf(prefs.getString("ai_gen_model", "gemini-2.5-flash") ?: "gemini-2.5-flash") }

    var isProcessing by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var progressVal by remember { mutableStateOf(0f) }

    val providers = listOf("Gemini", "OpenAI")
    val defaultModels = mapOf(
        "Gemini" to "gemini-2.5-flash",
        "OpenAI" to "gpt-4o-mini"
    )

    val textColor = VBookTheme.textColor()
    val bgColor = VBookTheme.backgroundColor()
    val primaryColor = VBookTheme.primaryColor()

    Dialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor),
            color = bgColor
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Tự Tạo Tiện Ích Bằng AI", fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            if (!isProcessing) {
                                IconButton(onClick = onDismiss) {
                                    Icon(Icons.Filled.Close, contentDescription = "Đóng")
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = bgColor,
                            titleContentColor = textColor
                        )
                    )
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!isProcessing) {
                        Text(
                            text = "Nhập liên kết trang truyện hoặc trang chủ của một website, cấu hình API Key AI của bạn để hệ thống tự động phân tích và tạo VBook Extension.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = webUrl,
                            onValueChange = { webUrl = it },
                            label = { Text("Địa chỉ Website truyện (ví dụ: https://truyenfull.vn)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // AI Provider
                        Text("Nhà cung cấp AI", color = textColor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            providers.forEach { provider ->
                                val selected = selectedProvider == provider
                                FilterChip(
                                    selected = selected,
                                    onClick = { 
                                        selectedProvider = provider
                                        aiModel = defaultModels[provider] ?: ""
                                    },
                                    label = { Text(provider) }
                                )
                            }
                        }

                        // API Key
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text("AI API Key") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Model
                        OutlinedTextField(
                            value = aiModel,
                            onValueChange = { aiModel = it },
                            label = { Text("Mã Model AI") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                if (webUrl.isBlank() || apiKey.isBlank() || aiModel.isBlank()) {
                                    Toast.makeText(context, "Vui lòng nhập đầy đủ thông tin", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                // Lưu cấu hình
                                prefs.edit()
                                    .putString("ai_gen_provider", selectedProvider)
                                    .putString("ai_gen_apikey", apiKey)
                                    .putString("ai_gen_model", aiModel)
                                    .apply()

                                isProcessing = true
                                scope.launch {
                                    val success = runAiGenerator(
                                        context = context,
                                        targetUrl = webUrl,
                                        provider = selectedProvider,
                                        key = apiKey,
                                        modelName = aiModel,
                                        onStatusChange = { statusText = it },
                                        onProgressChange = { progressVal = it }
                                    )
                                    isProcessing = false
                                    if (success) {
                                        onExtensionCreated()
                                        onDismiss()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                        ) {
                            Text("Bắt đầu phân tích & Tạo", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        // Trạng thái đang xử lý
                        Box(
                            modifier = Modifier.fillMaxSize().weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    progress = { progressVal },
                                    modifier = Modifier.size(100.dp),
                                    color = primaryColor
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun cleanHtml(html: String): String {
    var cleaned = html.replace(Regex("(?s)<script.*?>.*?</script>"), "")
        .replace(Regex("(?s)<style.*?>.*?</style>"), "")
        .replace(Regex("(?s)<svg.*?>.*?</svg>"), "")
        .replace(Regex("(?s)<head.*?>.*?</head>"), "")
    if (cleaned.length > 15000) {
        cleaned = cleaned.substring(0, 15000) + "... [HTML TRUNCATED]"
    }
    return cleaned
}

private suspend fun runAiGenerator(
    context: Context,
    targetUrl: String,
    provider: String,
    key: String,
    modelName: String,
    onStatusChange: (String) -> Unit,
    onProgressChange: (Float) -> Unit
): Boolean = withContext(Dispatchers.IO) {
    try {
        onStatusChange("Đang kết nối tải trang web mẫu...")
        onProgressChange(0.2f)

        val client = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        val request = Request.Builder()
            .url(targetUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .build()

        val response = client.newCall(request).execute()
        val htmlRaw = response.body?.string() ?: throw Exception("Không thể tải HTML của trang web")
        val cleanedHtml = cleanHtml(htmlRaw)

        onStatusChange("Đang gửi phân tích cấu trúc HTML lên AI...")
        onProgressChange(0.4f)

        val host = try {
            val u = URL(targetUrl)
            "${u.protocol}://${u.host}"
        } catch (e: Exception) {
            targetUrl
        }

        val systemPrompt = """
Bạn là một AI chuyên gia viết VBook Extensions (tiện ích cào truyện bằng Javascript cho ứng dụng VBook).
Dựa trên mã nguồn HTML của trang web truyện được cung cấp dưới đây, hãy phân tích cấu trúc DOM và viết ra bộ mã nguồn VBook Extension hoàn chỉnh.

Yêu cầu output duy nhất dưới dạng JSON Object thô (KHÔNG bọc trong markdown code block, KHÔNG chứa ký tự lạ ngoài JSON hợp lệ). JSON phải chứa chính xác các keys tương ứng với các tệp tin cấu hình và mã nguồn sau:

1. "plugin.json": Tệp cấu hình chứa metadata. encrypt phải đặt là false. type đặt là "novel". locales đặt là "vi_VN". source đặt là tên miền gốc (ví dụ "https://truyenfull.vn"). script phải khai báo chính xác các file tương ứng:
   {
     "metadata": {
       "name": "Tiện ích tự sinh",
       "author": "Novel Studio AI",
       "version": 1,
       "source": "$host",
       "description": "Tiện ích tự sinh bằng AI",
       "locale": "vi_VN",
       "type": "novel",
       "language": "javascript",
       "encrypt": false
     },
     "script": {
       "home": "home.js",
       "detail": "detail.js",
       "toc": "toc.js",
       "chap": "chap.js",
       "search": "search.js"
     }
   }

2. "config.js": Khai báo BASE_URL của trang web. Cú pháp:
   let BASE_URL = '$host';

3. "home.js": Trả về các mục hiển thị trang chủ của tiện ích. Cú pháp:
   load('config.js');
   function execute() {
       return Response.success([
           {title: "Trang chủ", input: BASE_URL, script: "gen.js"}
       ]);
   }

4. "detail.js": Hàm execute(url) trả về chi tiết truyện. Cú pháp:
   load('config.js');
   function execute(url) {
       let response = fetch(url);
       if (response.ok) {
           let doc = response.html();
           return Response.success({
               name: doc.select("h1, .title, .book-title").first().text(),
               cover: doc.select("img").first().attr('src') || "",
               author: doc.select(".author, a[href*='tac-gia']").first().text() || "Chưa rõ",
               description: doc.select(".desc, .description, .intro").text(),
               detail: "Tác giả: " + (doc.select(".author").first().text() || "Chưa rõ"),
               host: BASE_URL
           });
       }
       return null;
   }

5. "toc.js": Hàm execute(url) trả về danh mục toàn bộ chương. Cú pháp:
   load('config.js');
   function execute(url) {
       let response = fetch(url);
       if (response.ok) {
           let doc = response.html();
           let list = [];
           doc.select("a[href*='chuong'], a[href*='chapter']").forEach(e => {
               list.push({
                   name: e.text(),
                   url: e.attr('href'),
                   host: BASE_URL
               });
           });
           return Response.success(list);
       }
       return null;
   }

6. "chap.js": Hàm execute(url) trả về nội dung chữ của chương. Cú pháp:
   load('config.js');
   function execute(url) {
       let response = fetch(url);
       if (response.ok) {
           let doc = response.html();
           let content = doc.select(".chapter-content, .content, #chapter-c").html();
           content = content.replace(/\n/g, '<br>');
           return Response.success(content);
       }
       return null;
   }

7. "search.js": Hàm execute(keyword, page) tìm kiếm truyện. Cú pháp:
   load('config.js');
   function execute(keyword, page) {
       if (!page) page = '1';
       let response = fetch(BASE_URL + "/search?q=" + keyword);
       if (response.ok) {
           let doc = response.html();
           let data = [];
           doc.select("a").forEach(e => {
               var name = e.text();
               if (name.length > 3) {
                   data.push({
                       name: name,
                       link: e.attr("href"),
                       cover: "",
                       description: "",
                       host: BASE_URL
                   });
               }
           });
           return Response.success(data);
       }
       return null;
   }

Hãy phân tích kỹ mã nguồn HTML bên dưới và điền đúng các CSS Selector thích hợp thay thế cho các selector mẫu trên. Trả về đúng 1 JSON Object duy nhất:
"""

        val userPrompt = """
Mã nguồn HTML trang web:
$cleanedHtml
"""

        val aiResponse = callAiApi(provider, key, modelName, systemPrompt + userPrompt)
        
        onStatusChange("Đang tạo và cấu hình tệp tin tiện ích...")
        onProgressChange(0.7f)

        // Loại bỏ markdown code block format nếu AI tự ý chèn
        val cleanedJson = aiResponse.trim()
            .replace(Regex("^```json"), "")
            .replace(Regex("^```"), "")
            .replace(Regex("```$"), "")
            .trim()

        val jsonObj = Json.decodeFromString<JsonObject>(cleanedJson)
        val pluginJsonStr = jsonObj["plugin.json"]?.jsonPrimitive?.content ?: throw Exception("Thiếu plugin.json")
        val configJs = jsonObj["config.js"]?.jsonPrimitive?.content ?: ""
        val homeJs = jsonObj["home.js"]?.jsonPrimitive?.content ?: ""
        val detailJs = jsonObj["detail.js"]?.jsonPrimitive?.content ?: ""
        val tocJs = jsonObj["toc.js"]?.jsonPrimitive?.content ?: ""
        val chapJs = jsonObj["chap.js"]?.jsonPrimitive?.content ?: ""
        val searchJs = jsonObj["search.js"]?.jsonPrimitive?.content ?: ""

        val pluginMeta = Json.decodeFromString<JsonObject>(pluginJsonStr)
        val metaObj = pluginMeta["metadata"]?.toString() ?: throw Exception("Thiếu metadata trong plugin.json")
        val metaMap = Json.decodeFromString<JsonObject>(metaObj)
        val extName = metaMap["name"]?.jsonPrimitive?.content ?: "AI Extension"
        val extSource = metaMap["source"]?.jsonPrimitive?.content ?: host

        val extSlug = extName.toSlug()
        val extDir = File(context.filesDir, "extensions/$extSlug")
        extDir.mkdirs()
        val srcDir = File(extDir, "src")
        srcDir.mkdirs()

        // Ghi các file
        File(extDir, "plugin.json").writeText(pluginJsonStr)
        File(srcDir, "config.js").writeText(configJs)
        File(srcDir, "home.js").writeText(homeJs)
        File(srcDir, "detail.js").writeText(detailJs)
        File(srcDir, "toc.js").writeText(tocJs)
        File(srcDir, "chap.js").writeText(chapJs)
        File(srcDir, "search.js").writeText(searchJs)

        // Giả lập file gen.js tối giản để hỗ trợ home list
        val genJs = """
            load('config.js');
            function execute(url) {
                return Response.success([]);
            }
        """.trimIndent()
        File(srcDir, "gen.js").writeText(genJs)

        onStatusChange("Đang ghi vào cơ sở dữ liệu...")
        onProgressChange(0.9f)

        val entryPoint = dagger.hilt.EntryPoints.get(context.applicationContext, AiExtensionEntryPoint::class.java)
        val extensionDao = entryPoint.extensionDao()
        extensionDao.insert(
            com.nam.novelreader.data.local.entity.ExtensionEntity(
                id = extSlug,
                name = extName,
                author = "Novel Studio AI",
                version = 1,
                source = extSource,
                type = "novel",
                locale = "vi_VN",
                description = "Tiện ích tự sinh tự động bằng AI",
                localPath = extDir.absolutePath,
                iconPath = null,
                isInstalled = true,
                isEnabled = true
            )
        )

        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Cài đặt thành công extension: $extName", Toast.LENGTH_LONG).show()
        }
        true
    } catch (e: Exception) {
        android.util.Log.e("AiExtensionGenerator", "Error generating extension", e)
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Lỗi sinh Extension: ${e.message}", Toast.LENGTH_LONG).show()
        }
        false
    }
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface AiExtensionEntryPoint {
    fun extensionDao(): com.nam.novelreader.data.local.dao.ExtensionDao
}

private suspend fun callAiApi(
    provider: String,
    key: String,
    modelName: String,
    prompt: String
): String = withContext(Dispatchers.IO) {
    val client = OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    if (provider == "Gemini") {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$key"
        val requestBody = """
            {
              "contents": [
                {
                  "parts": [
                    {
                      "text": ${Json.encodeToString(prompt)}
                    }
                  ]
                }
              ],
              "generationConfig": {
                "responseMimeType": "application/json"
              }
            }
        """.trimIndent()

        val request = Request.Builder()
            .url(url)
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw Exception("API Gemini trả về trống")
        if (!response.isSuccessful) throw Exception("Lỗi API Gemini (${response.code}): $body")

        val resObj = Json.decodeFromString<JsonObject>(body)
        val candidates = resObj["candidates"]?.toString() ?: throw Exception("Gemini phản hồi thiếu candidates")
        val candidatesList = Json.decodeFromString<List<JsonObject>>(candidates)
        val firstCandidate = candidatesList.firstOrNull() ?: throw Exception("Gemini phản hồi candidates trống")
        val content = firstCandidate["content"]?.toString() ?: throw Exception("Thiếu content")
        val contentObj = Json.decodeFromString<JsonObject>(content)
        val parts = contentObj["parts"]?.toString() ?: throw Exception("Thiếu parts")
        val partsList = Json.decodeFromString<List<JsonObject>>(parts)
        val text = partsList.firstOrNull()?.get("text")?.jsonPrimitive?.content ?: throw Exception("Thiếu phản hồi text của AI")
        text
    } else {
        val url = "https://api.openai.com/v1/chat/completions"
        val requestBody = """
            {
              "model": "$modelName",
              "messages": [
                {
                  "role": "user",
                  "content": ${Json.encodeToString(prompt)}
                }
              ],
              "response_format": { "type": "json_object" }
            }
        """.trimIndent()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $key")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw Exception("API OpenAI trả về trống")
        if (!response.isSuccessful) throw Exception("Lỗi API OpenAI (${response.code}): $body")

        val resObj = Json.decodeFromString<JsonObject>(body)
        val choices = resObj["choices"]?.toString() ?: throw Exception("OpenAI phản hồi thiếu choices")
        val choicesList = Json.decodeFromString<List<JsonObject>>(choices)
        val firstChoice = choicesList.firstOrNull() ?: throw Exception("OpenAI phản hồi choices trống")
        val message = firstChoice["message"]?.toString() ?: throw Exception("Thiếu message")
        val messageObj = Json.decodeFromString<JsonObject>(message)
        val text = messageObj["content"]?.jsonPrimitive?.content ?: throw Exception("Thiếu phản hồi content của AI")
        text
    }
}

private fun String.toSlug(): String {
    val normalized = java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD)
        .replace(Regex("[\\p{InCombiningDiacriticalMarks}]"), "")
    return normalized
        .lowercase()
        .replace("đ", "d").replace("Đ", "d")
        .replace(Regex("[^a-z0-9]"), "-")
        .replace(Regex("-+"), "-")
        .trim('-')
}
