package com.nam.novelreader.extension.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Cấu trúc plugin.json của VBook extension.
 * Mỗi extension chứa metadata + mapping script type → file name.
 *
 * Ví dụ:
 * ```json
 * {
 *   "metadata": { "name": "Truyện Full", "type": "novel", ... },
 *   "script": { "home": "home.js", "search": "search.js", ... }
 * }
 * ```
 */
@Serializable
data class ExtensionSetting(
    val key: String,
    val title: String,
    val type: String, // "text" | "password" | "boolean" | "select"
    val default: String = "",
    val desc: String? = null,
    val choices: List<String>? = null,
)

@Serializable
data class PluginJson(
    val metadata: PluginMetadata,
    val script: Map<String, String> = emptyMap(),
    val settings: List<ExtensionSetting> = emptyList(),
)

@Serializable
data class PluginMetadata(
    val name: String,
    val author: String = "Unknown",
    val version: Int = 1,
    val source: String = "",
    val regexp: String? = null,
    val description: String = "",
    val locale: String = "vi_VN",
    val type: String = "novel",
    val language: String = "javascript",
    val encrypt: Boolean = false,
)

/**
 * Thông tin extension trong repository index (plugin.json ở root).
 * App dùng để hiển thị danh sách extension có thể cài đặt.
 */
@Serializable
data class ExtensionInfo(
    val name: String,
    val author: String = "Unknown",
    val version: Int = 1,
    val source: String = "",
    val path: String = "",           // URL download plugin.zip
    val icon: String = "",           // URL icon
    val description: String = "",
    val type: String = "novel",      // "novel" | "comic"
    val locale: String = "vi_VN",
    val public: Boolean = false,     // Chỉ hiển thị nếu Admin mở công khai
)

/**
 * Repository index — chứa danh sách extensions có thể cài.
 */
@Serializable
data class RepositoryIndex(
    val metadata: RepositoryMetadata? = null,
    val data: List<ExtensionInfo> = emptyList(),
)

@Serializable
data class RepositoryMetadata(
    val author: String = "",
    val description: String = "",
)

/**
 * Extension đã load vào memory, sẵn sàng thực thi.
 */
data class LoadedExtension(
    val pluginJson: PluginJson,
    val directory: java.io.File,
) {
    /** Đọc nội dung file JS trong thư mục src/ */
    fun getScriptContent(fileName: String): String {
        // Thử src/ trước, rồi root
        val srcFile = java.io.File(directory, "src/$fileName")
        val content = if (srcFile.exists()) {
            srcFile.readText()
        } else {
            val rootFile = java.io.File(directory, fileName)
            if (rootFile.exists()) {
                rootFile.readText()
            } else {
                throw ExtensionException("Script file not found: $fileName in ${directory.absolutePath}")
            }
        }

        var finalContent = content
        if (pluginJson.metadata.encrypt) {
            val decrypted = com.nam.novelreader.extension.runtime.VBookExtensionDecryptor.decrypt(
                content,
                pluginJson.metadata.source,
                pluginJson.metadata.author
            )
            if (decrypted != null) {
                finalContent = decrypted
            }
        }

        // Vá lỗi truyencv.io Regex match[0] null crash
        if (id == "truyencv-io" && fileName == "detail.js") {
            val oldRegex = """html.select("div.uk-text-truncate").text().match(/\d+(?=\s*Chương)/)[0]"""
            val safeRegex = """(function(){ var m = html.select("div.uk-text-truncate").text().match(/\d+(?=\s*Chương)/); return m ? m[0] : "Chưa rõ"; })()"""
            finalContent = finalContent.replace(oldRegex, safeRegex)
        }

        // Tự động fallback tên miền Sáng Tác Việt nếu bị chặn ở Việt Nam
        if (id == "sang-tac-viet" || id.startsWith("stv")) {
            finalContent = finalContent
                .replace("https://sangtacviet.vip", "https://sangtacviet.com")
                .replace("http://sangtacviet.vip", "https://sangtacviet.com")
                .replace("https://sangtacviet.pro", "https://sangtacviet.com")
                .replace("http://sangtacviet.pro", "https://sangtacviet.com")
                .replace("https://dns1.stv-appdomain-00000001.org", "https://sangtacviet.com")
                .replace("http://dns1.stv-appdomain-00000001.org", "https://sangtacviet.com")
                .replace("http://14.225.254.182", "https://sangtacviet.com")
                .replace("https://14.225.254.182", "https://sangtacviet.com")
        }

        // Runtime patches cho tiện ích Wikidich
        if (id == "wiki-dich" || id == "wikidich") {
            if (fileName == "config.js") {
                finalContent = finalContent
                    .replace("https://wikicv.net", "https://wikidichvn.com")
                    .replace("http://wikicv.net", "https://wikidichvn.com")
                    .replace("https://wikidich.com.vn", "https://wikidichvn.com")
                    .replace("http://wikidich.com.vn", "https://wikidichvn.com")
                    .replace("https://truyenwikidich.com", "https://wikidichvn.com")
                    .replace("http://truyenwikidich.com", "https://wikidichvn.com")
                    .replace("https://truyenwikidich.net", "https://wikidichvn.com")
                    .replace("http://truyenwikidich.net", "https://wikidichvn.com")
                
                finalContent = finalContent + "\n" + """
                    if (typeof BASE_URL !== 'undefined') {
                        if (BASE_URL.indexOf("wikicv.net") >= 0 || 
                            BASE_URL.indexOf("wikidich.com.vn") >= 0 || 
                            BASE_URL.indexOf("truyenwikidich.com") >= 0 || 
                            BASE_URL.indexOf("truyenwikidich.net") >= 0) {
                            BASE_URL = "https://wikidichvn.com";
                        }
                    }
                """.trimIndent()
            } else if (fileName == "toc.js") {
                finalContent = """
                    load('config.js');

                    function execute(url) {
                        var targetUrl = url;
                        if (typeof storyUrlFromAny === 'function') {
                            targetUrl = storyUrlFromAny(url);
                        }
                        
                        targetUrl = targetUrl.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);
                        
                        var doc = Http.get(targetUrl).html();
                        var html = doc.html();
                        
                        var bookId = "";
                        var totalPages = 1;
                        var re = /page\((\d+)\s*,\s*(\d+)\)/g;
                        var m;
                        while ((m = re.exec(html)) !== null) {
                            bookId = bookId || m[1];
                            var p = parseInt(m[2], 10);
                            if (p > totalPages) totalPages = p;
                        }
                        
                        if (!bookId) {
                            var bidInput = html.match(/name="bid"\s+value="(\d+)"/) || html.match(/value="(\d+)"\s+name="bid"/);
                            if (bidInput) bookId = bidInput[1];
                        }
                        
                        var data = [];
                        var seen = {};
                        
                        function parseHtmlChapters(contentHtml) {
                            var linksRe = /<a\s+href=['"]([^'"]+)['"][^>]*>([\s\S]*?)<\/a>/gi;
                            var lm;
                            while ((lm = linksRe.exec(contentHtml)) !== null) {
                                var href = lm[1];
                                if (href.indexOf('/chuong-') >= 0) {
                                    var name = lm[2].replace(/<[^>]*>/g, '').trim();
                                    var fullUrl = href;
                                    if (fullUrl.indexOf('http') !== 0) {
                                        if (fullUrl.indexOf('/') !== 0) {
                                            fullUrl = '/' + fullUrl;
                                        }
                                        fullUrl = BASE_URL + fullUrl;
                                    }
                                    if (!seen[fullUrl]) {
                                        seen[fullUrl] = true;
                                        data.push({
                                            name: name,
                                            url: fullUrl,
                                            host: BASE_URL
                                        });
                                    }
                                }
                            }
                        }
                        
                        if (bookId) {
                            for (var pageNum = 1; pageNum <= totalPages; pageNum++) {
                                var listUrl = BASE_URL + "/get/listchap/" + bookId + "?page=" + pageNum;
                                var response = Http.get(listUrl).string();
                                if (response) {
                                    try {
                                        var json = JSON.parse(response);
                                        if (json && json.data) {
                                            parseHtmlChapters(json.data);
                                        }
                                    } catch(e) {
                                        parseHtmlChapters(response);
                                    }
                                }
                            }
                        } else {
                            parseHtmlChapters(html);
                        }
                        
                        return Response.success(data);
                    }
                """.trimIndent()
            }
        }

        return finalContent
    }

    fun getIconFile(): java.io.File? {
        val file = java.io.File(directory, "icon.png")
        return if (file.exists()) file else null
    }

    val id: String get() {
        val normalized = java.text.Normalizer.normalize(pluginJson.metadata.name, java.text.Normalizer.Form.NFD)
            .replace(Regex("[\\p{InCombiningDiacriticalMarks}]"), "")
        return normalized
            .lowercase()
            .replace("đ", "d").replace("Đ", "d")
            .replace(Regex("[^a-z0-9]"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
    }
}

class ExtensionException(message: String) : Exception(message)

/**
 * Kết quả trả về từ extension script execution.
 */
sealed class ExtensionResult {
    data class Success(val data: String) : ExtensionResult()
    data class Error(val message: String) : ExtensionResult()
}

/**
 * Loại script mà extension hỗ trợ.
 * Tương ứng với key trong plugin.json "script" object.
 */
enum class ScriptType(val key: String) {
    HOME("home"),
    GENRE("genre"),
    DETAIL("detail"),
    SEARCH("search"),
    PAGE("page"),
    TOC("toc"),
    CHAP("chap"),
    TRACK("track"),
    VOICE("voice"),
    TTS("tts"),
    ;

    companion object {
        fun fromKey(key: String): ScriptType? = entries.find { it.key == key }
    }
}

val ExtensionInfo.cleanName: String
    get() {
        var clean = name
        // 1. Loại bỏ các phần trong ngoặc tròn/vuông chứa "Đăng Nhập", "Login", "VPN", "🔑", "🌐"
        val bracketKeywordsRegex = Regex("""[(\[][^()\n\[\]]*(đăng nhập|login|vpn|🔑|🌐)[^()\n\[\]]*[)\]]""", RegexOption.IGNORE_CASE)
        clean = bracketKeywordsRegex.replace(clean, "")

        // 2. Loại bỏ các từ khóa đăng nhập, vpn riêng lẻ nếu có
        val standaloneKeywordsRegex = Regex("\\b(đăng nhập|login|vpn)\\b", RegexOption.IGNORE_CASE)
        clean = standaloneKeywordsRegex.replace(clean, "")
        clean = clean.replace("🔑", "").replace("🌐", "")

        // 3. Loại bỏ hoàn toàn ký tự đóng mở ngoặc nhưng giữ chữ bên trong
        clean = clean.replace("(", "").replace(")", "").replace("[", "").replace("]", "")

        // 4. Loại bỏ khoảng trắng thừa
        clean = clean.replace(Regex("\\s+"), " ").trim()

        return clean
    }
