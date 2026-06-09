package com.nam.novelreader.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "novels")
data class NovelEntity(
    @PrimaryKey val url: String,
    val title: String,
    val author: String = "",
    val cover: String = "",
    val description: String = "",
    val genres: String = "[]",           // JSON array
    val status: String = "",
    val type: String = "novel",
    val extensionId: String = "",
    val latestChapter: String? = null,
    val totalChapters: Int = 0,
    val lastReadChapterUrl: String? = null,
    val lastReadAt: Long? = null,
    val addedToLibraryAt: Long? = null,
    val isInLibrary: Boolean = false,
    val scrollPosition: Int = 0,
)

@Entity(tableName = "chapters")
data class ChapterEntity(
    @PrimaryKey val url: String,
    val novelUrl: String,
    val title: String,
    val index: Int = 0,
    val content: String? = null,
    val contentType: String = "novel",
    val images: String? = null,          // JSON array of image URLs
    val isDownloaded: Boolean = false,
    val downloadedAt: Long? = null,
)

@Entity(tableName = "extensions")
data class ExtensionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val author: String = "Unknown",
    val version: Int = 1,
    val source: String = "",
    val type: String = "novel",
    val locale: String = "vi_VN",
    val description: String = "",
    val localPath: String = "",
    val iconPath: String? = null,
    val isInstalled: Boolean = false,
    val isEnabled: Boolean = true,
    val repositoryUrl: String? = null,
)

@Entity(tableName = "repositories")
data class RepositoryEntity(
    @PrimaryKey val url: String,
    val name: String = "",
    val addedAt: Long = System.currentTimeMillis(),
    val isEnabled: Boolean = true,
)

@Entity(tableName = "reading_history")
data class ReadingHistoryEntity(
    @PrimaryKey val novelUrl: String,
    val chapterUrl: String,
    val chapterTitle: String = "",
    val scrollPosition: Int = 0,
    val readAt: Long = System.currentTimeMillis(),
    val extensionId: String = "",
)

@Entity(tableName = "download_tasks")
data class DownloadTaskEntity(
    @PrimaryKey val novelUrl: String,
    val extensionId: String,
    val novelTitle: String = "",
    val coverUrl: String = "",
    val status: String = "preparing",  // preparing | downloading | paused | completed | failed | canceled
    val totalChapters: Int = 0,
    val downloadedChapters: Int = 0,
    val startIndex: Int = 0,           // Chương bắt đầu tải
    val endIndex: Int = 0,             // Chương kết thúc
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val errorMessage: String? = null,
)

val ExtensionEntity.cleanName: String
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