package com.nam.novelreader.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import java.util.LinkedHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object QuickTranslateEngine {
    private const val TAG = "QuickTranslateEngine"

    @Volatile
    private var isLoaded = false
    @Volatile
    private var isLoading = false

    private val _isDictLoadedFlow = MutableStateFlow(false)
    val isDictLoadedFlow: StateFlow<Boolean> = _isDictLoadedFlow

    // HashMap chứa từ điển gộp: VietPhrase + Name + Pronouns
    @Volatile
    private var translationDict = HashMap<String, String>()
    // HashMap chứa phiên âm Hán Việt
    @Volatile
    private var phienAmDict = HashMap<String, String>()

    fun isDictLoaded(): Boolean = isLoaded
    fun isDictLoading(): Boolean = isLoading

    /**
     * Khởi tạo và nạp từ điển vào RAM ngầm.
     * @param force Nếu true, bỏ qua kiểm tra isLoaded để nạp lại từ điển mới.
     */
    fun init(context: Context, force: Boolean = false) {
        if (!force && (isLoaded || isLoading)) return
        isLoading = true

        Thread {
            try {
                // Tự động giải nén từ assets nếu chưa tồn tại
                copyDictFromAssetsIfNeed(context)

                val dictDir = File(context.filesDir, "dict")
                if (!dictDir.exists()) {
                    Log.w(TAG, "Directory dict does not exist at ${dictDir.absolutePath}")
                    isLoading = false
                    return@Thread
                }

                Log.d(TAG, "Starting to load dictionaries into memory (force=$force)...")
                val startTime = System.currentTimeMillis()

                // Sử dụng map tạm thời trong quá trình nạp để tránh ảnh hưởng đến luồng dịch đang chạy
                val tempTranslationDict = HashMap<String, String>(1500000)
                val tempPhienAmDict = HashMap<String, String>(25000)

                // String pool tạm thời để khử trùng lặp các chuỗi nghĩa dịch tiếng Việt trong RAM
                val stringPool = HashMap<String, String>(100000)
                fun dedup(str: String): String {
                    return stringPool.getOrPut(str) { str }
                }

                // 1. Nạp PhienAm.txt (Hán Việt)
                val phienAmFile = File(dictDir, "PhienAm.txt")
                if (phienAmFile.exists()) {
                    phienAmFile.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            val parts = line.split('=', limit = 2)
                            if (parts.size == 2) {
                                val key = parts[0].trim()
                                val valPart = parts[1].split('/', limit = 2)[0].trim()
                                if (key.isNotEmpty() && valPart.isNotEmpty()) {
                                    tempPhienAmDict[key] = dedup(valPart)
                                }
                            }
                        }
                    }
                }
                Log.d(TAG, "Loaded PhienAm: ${tempPhienAmDict.size} entries")

                // Quyết định thứ tự nạp để ưu tiên: VietPhrase -> Name -> Pronouns
                // 2. Nạp VietPhrase.txt
                val vpFile = File(dictDir, "VietPhrase.txt")
                if (vpFile.exists()) {
                    vpFile.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            val parts = line.split('=', limit = 2)
                            if (parts.size == 2) {
                                val key = parts[0].trim()
                                val valPart = parts[1].split('/', limit = 2)[0].trim()
                                if (key.isNotEmpty() && valPart.isNotEmpty()) {
                                    tempTranslationDict[key] = dedup(valPart)
                                }
                            }
                        }
                    }
                }
                Log.d(TAG, "Loaded VietPhrase. Current dict size: ${tempTranslationDict.size}")

                // 3. Nạp Name.txt (Ghi đè nghĩa chung của VietPhrase nếu trùng key)
                val nameFile = File(dictDir, "Name.txt")
                if (nameFile.exists()) {
                    nameFile.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            val parts = line.split('=', limit = 2)
                            if (parts.size == 2) {
                                val key = parts[0].trim()
                                val valPart = parts[1].split('/', limit = 2)[0].trim()
                                if (key.isNotEmpty() && valPart.isNotEmpty()) {
                                    tempTranslationDict[key] = dedup(valPart)
                                }
                            }
                        }
                    }
                }
                Log.d(TAG, "Loaded Name. Current dict size: ${tempTranslationDict.size}")

                // 4. Nạp Pronouns.txt (Đại từ nhân xưng có độ ưu tiên cao nhất)
                val pronounsFile = File(dictDir, "Pronouns.txt")
                if (pronounsFile.exists()) {
                    pronounsFile.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            val parts = line.split('=', limit = 2)
                            if (parts.size == 2) {
                                val key = parts[0].trim()
                                val valPart = parts[1].split('/', limit = 2)[0].trim()
                                if (key.isNotEmpty() && valPart.isNotEmpty()) {
                                    tempTranslationDict[key] = dedup(valPart)
                                }
                            }
                        }
                    }
                }
                Log.d(TAG, "Loaded Pronouns. Current dict size: ${tempTranslationDict.size}")

                // Giải phóng String pool tạm thời để thu hồi bộ nhớ RAM
                stringPool.clear()

                // Hoán đổi map tạm sang map chính một cách an toàn (Atomic Swap)
                translationDict = tempTranslationDict
                phienAmDict = tempPhienAmDict

                isLoaded = true
                _isDictLoadedFlow.value = true
                Log.d(TAG, "All dictionaries loaded successfully in ${System.currentTimeMillis() - startTime} ms. Final dict size: ${translationDict.size}")

            } catch (e: Exception) {
                Log.e(TAG, "Error loading dictionaries: ${e.message}", e)
            } finally {
                isLoading = false
            }
        }.start()
    }

    /**
     * Dịch một đoạn văn bản tiếng Trung sang tiếng Việt.
     */
    fun translate(context: Context, text: String, targetMode: String = "vi"): String {
        if (text.isBlank()) return text

        if (!isLoaded) {
            init(context)
            // Chờ tối đa 3 giây nếu đang trong quá trình nạp
            var waitCount = 0
            while (isLoading && !isLoaded && waitCount < 30) {
                try { Thread.sleep(100) } catch (_: Exception) {}
                waitCount++
            }
            if (!isLoaded) {
                Log.w(TAG, "Dictionaries not loaded yet, returning raw text.")
                return text
            }
        }

        val prefs = context.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE)
        val maxLen = prefs.getInt("qt_max_phrase_length", 12)
        val italicizeDialogue = prefs.getBoolean("qt_italicize_dialogue", true)

        val result = StringBuilder(text.length * 2)
        var i = 0
        val len = text.length

        // Phân biệt chế độ dịch: Hán Việt toàn bộ hay VietPhrase
        val isHanVietOnly = targetMode.contains("hv") || targetMode.contains("hanviet") || targetMode.contains("hán")

        // Sao lưu tham chiếu thread-safe của map chính
        val currentDict = translationDict
        val currentPhienAm = phienAmDict

        while (i < len) {
            val char = text[i]

            // Giữ nguyên các ký tự không phải tiếng Trung (khoảng trắng, dấu câu, số, ký tự latin)
            if (!isChineseChar(char)) {
                result.append(char)
                i++
                continue
            }

            if (isHanVietOnly) {
                // Dịch âm Hán Việt từng chữ
                val singleChar = text.substring(i, i + 1)
                val hanViet = currentPhienAm[singleChar] ?: singleChar
                
                if (result.isNotEmpty() && result.last() != ' ' && result.last() != '\n') {
                    result.append(' ')
                }
                result.append(hanViet)
                i++
            } else {
                // Dịch VietPhrase (Maximum Matching)
                var matchedLength = 0
                var matchedTranslation = ""
                val limit = minOf(len - i, maxLen)

                for (l in limit downTo 1) {
                    val phrase = text.substring(i, i + l)
                    val translation = currentDict[phrase]
                    if (translation != null) {
                        matchedLength = l
                        matchedTranslation = translation
                        break
                    }
                }

                if (matchedLength > 0) {
                    // Khớp được cụm từ
                    if (result.isNotEmpty() && result.last() != ' ' && result.last() != '\n') {
                        result.append(' ')
                    }
                    result.append(matchedTranslation)
                    i += matchedLength
                } else {
                    // Không khớp, fallback về Hán Việt từng từ
                    val singleChar = text.substring(i, i + 1)
                    val hanViet = currentPhienAm[singleChar] ?: singleChar
                    
                    if (result.isNotEmpty() && result.last() != ' ' && result.last() != '\n') {
                        result.append(' ')
                    }
                    result.append(hanViet)
                    i++
                }
            }
        }

        var output = result.toString()
        if (italicizeDialogue) {
            output = italicizeDialogues(output)
        }

        return output
    }

    private fun isChineseChar(c: Char): Boolean {
        val ub = Character.UnicodeBlock.of(c)
        return ub === Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                ub === Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
                ub === Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
                ub === Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
    }

    fun hasChinese(text: String): Boolean {
        if (text.isBlank()) return false
        for (i in text.indices) {
            if (isChineseChar(text[i])) return true
        }
        return false
    }

    /**
     * Tự động in nghiêng các câu thoại nằm giữa hai dấu nháy kép (ví dụ: "nói...")
     * Dùng định dạng HTML <i> để hiển thị.
     */
    private fun italicizeDialogues(text: String): String {
        val result = StringBuilder()
        var inQuote = false
        var lastIdx = 0
        
        for (idx in text.indices) {
            val c = text[idx]
            if (c == '"' || c == '“' || c == '”') {
                if (inQuote) {
                    // Đóng nháy
                    result.append(text.substring(lastIdx, idx))
                    result.append("</i>\"")
                    inQuote = false
                } else {
                    // Mở nháy
                    result.append(text.substring(lastIdx, idx))
                    result.append("\"<i>")
                    inQuote = true
                }
                lastIdx = idx + 1
            }
        }
        result.append(text.substring(lastIdx))
        return result.toString()
    }

    // ========== DICTIONARY FILE HELPERS ==========

    /**
     * Đọc động số từ trong file từ điển.
     */
    fun getWordCount(context: Context, fileName: String): String {
        val dictDir = File(context.filesDir, "dict")
        val file = File(dictDir, fileName)
        if (!file.exists() || file.length() == 0L) return "Chưa tải"
        return try {
            var lineCount = 0
            file.bufferedReader().useLines { lines ->
                lineCount = lines.count { it.isNotBlank() && it.contains("=") }
            }
            if (lineCount == 0) {
                // Fallback đếm dòng thường nếu không chứa dấu =
                file.bufferedReader().useLines { lines ->
                    lineCount = lines.count { it.isNotBlank() }
                }
            }
            "$lineCount từ"
        } catch (e: Exception) {
            "Lỗi đọc file"
        }
    }

    private fun mergeDictFiles(localFile: File, tempFile: File) {
        try {
            val dictMap = LinkedHashMap<String, String>(300000)
            
            // 1. Đọc file hệ thống mới trước
            if (tempFile.exists()) {
                tempFile.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        val parts = line.split('=', limit = 2)
                        if (parts.size == 2) {
                            val key = parts[0].trim()
                            val value = parts[1].trim()
                            if (key.isNotEmpty()) {
                                dictMap[key] = value
                            }
                        }
                    }
                }
            }
            
            // 2. Đọc file cục bộ của người dùng đè lên sau cùng để giữ nguyên các sửa đổi cá nhân
            if (localFile.exists()) {
                localFile.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        val parts = line.split('=', limit = 2)
                        if (parts.size == 2) {
                            val key = parts[0].trim()
                            val value = parts[1].trim()
                            if (key.isNotEmpty()) {
                                dictMap[key] = value
                            }
                        }
                    }
                }
            }
            
            // 3. Ghi lại vào file cục bộ
            localFile.bufferedWriter().use { writer ->
                dictMap.forEach { (key, value) ->
                    writer.write("$key=$value\n")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi gộp từ điển: ${e.message}", e)
        }
    }

    fun unzipDictFromAssets(context: Context): Boolean {
        val dictDir = File(context.filesDir, "dict")
        if (!dictDir.exists()) {
            dictDir.mkdirs()
        }
        
        return try {
            context.assets.open("dict.zip").use { inputStream ->
                ZipInputStream(inputStream).use { zipInput ->
                    var entry = zipInput.nextEntry
                    while (entry != null) {
                        val fileName = entry.name
                        val file = File(dictDir, fileName)
                        
                        if ((fileName == "Name.txt" || fileName == "Pronouns.txt") && file.exists() && file.length() > 0L) {
                            // Nếu file đã tồn tại và không rỗng, thực hiện gộp thông minh
                            val tempFile = File(dictDir, "$fileName.tmp")
                            FileOutputStream(tempFile).use { output ->
                                zipInput.copyTo(output)
                            }
                            mergeDictFiles(file, tempFile)
                            tempFile.delete()
                        } else {
                            // Giải nén ghi đè bình thường
                            FileOutputStream(file).use { output ->
                                zipInput.copyTo(output)
                            }
                        }
                        
                        zipInput.closeEntry()
                        entry = zipInput.nextEntry
                    }
                }
            }
            // Gọi force reload từ điển trong QuickTranslateEngine ngay lập tức để cập nhật RAM
            init(context, force = true)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi giải nén từ điển từ assets: ${e.message}", e)
            false
        }
    }

    fun copyDictFromAssetsIfNeed(context: Context) {
        val dictDir = File(context.filesDir, "dict")
        val vietPhraseFile = File(dictDir, "VietPhrase.txt")
        // Nếu chưa có từ điển hệ thống, hoặc từ điển quá nhỏ (bản mẫu cũ lỗi), tự động giải nén ngầm từ assets
        if (!dictDir.exists() || !vietPhraseFile.exists() || vietPhraseFile.length() < 10240L) {
            unzipDictFromAssets(context)
        }
    }
}
