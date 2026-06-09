package com.nam.novelreader.data.local.dao

import androidx.room.*
import com.nam.novelreader.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NovelDao {
    @Query("SELECT * FROM novels WHERE isInLibrary = 1 ORDER BY lastReadAt DESC")
    fun getLibraryNovels(): Flow<List<NovelEntity>>

    @Query("""
        SELECT DISTINCT n.* FROM novels n
        INNER JOIN chapters c ON n.url = c.novelUrl
        WHERE c.isDownloaded = 1
        ORDER BY n.lastReadAt DESC
    """)
    fun getDownloadedLibraryNovels(): Flow<List<NovelEntity>>

    @Query("SELECT * FROM novels")
    fun getAllNovels(): Flow<List<NovelEntity>>

    @Query("SELECT * FROM novels WHERE url = :url")
    suspend fun getNovelByUrl(url: String): NovelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(novel: NovelEntity)

    @Update
    suspend fun update(novel: NovelEntity)

    @Delete
    suspend fun delete(novel: NovelEntity)

    @Query("DELETE FROM novels WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    @Query("UPDATE novels SET isInLibrary = :inLibrary, addedToLibraryAt = :time WHERE url = :url")
    suspend fun updateLibraryStatus(url: String, inLibrary: Boolean, time: Long?)

    @Query("UPDATE novels SET lastReadChapterUrl = :chapterUrl, lastReadAt = :time, scrollPosition = :scroll WHERE url = :url")
    suspend fun updateReadProgress(url: String, chapterUrl: String, time: Long, scroll: Int)
}

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE novelUrl = :novelUrl ORDER BY `index` ASC")
    fun getChaptersForNovel(novelUrl: String): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE novelUrl = :novelUrl ORDER BY `index` ASC")
    suspend fun getChaptersForNovelSync(novelUrl: String): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE url = :url")
    suspend fun getChapterByUrl(url: String): ChapterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chapters: List<ChapterEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(chapter: ChapterEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(chapter: ChapterEntity): Long

    @Query("UPDATE chapters SET title = :title, `index` = :index WHERE url = :url")
    suspend fun updateChapterMetadata(url: String, title: String, index: Int)

    @Query("UPDATE chapters SET title = :title WHERE url = :url")
    suspend fun updateChapterTitle(url: String, title: String)

    @Transaction
    suspend fun insertOrUpdateChapters(chapters: List<ChapterEntity>) {
        for (chapter in chapters) {
            val id = insertIgnore(chapter)
            if (id == -1L) {
                updateChapterMetadata(chapter.url, chapter.title, chapter.index)
            }
        }
    }

    @Query("UPDATE chapters SET content = :content, images = :images, isDownloaded = :isDownloaded, downloadedAt = :time WHERE url = :url")
    suspend fun saveContent(url: String, content: String?, images: String?, isDownloaded: Boolean, time: Long)

    @Query("SELECT COUNT(*) FROM chapters WHERE novelUrl = :novelUrl AND isDownloaded = 1")
    suspend fun getDownloadedCount(novelUrl: String): Int

    @Query("SELECT url FROM chapters WHERE novelUrl = :novelUrl AND isDownloaded = 1")
    suspend fun getDownloadedChapterUrls(novelUrl: String): List<String>

    @Query("DELETE FROM chapters WHERE novelUrl = :novelUrl")
    suspend fun deleteChaptersForNovel(novelUrl: String)

    @Query("UPDATE chapters SET isDownloaded = 0 WHERE novelUrl NOT IN (SELECT novelUrl FROM download_tasks)")
    suspend fun cleanOnlineCacheDownloadedFlags()
}

@Dao
interface ExtensionDao {
    @Query("SELECT * FROM extensions WHERE isInstalled = 1 ORDER BY name ASC")
    fun getInstalledExtensions(): Flow<List<ExtensionEntity>>

    @Query("SELECT * FROM extensions WHERE isInstalled = 1 AND isEnabled = 1")
    suspend fun getEnabledExtensions(): List<ExtensionEntity>

    @Query("SELECT * FROM extensions WHERE id = :id")
    suspend fun getExtensionById(id: String): ExtensionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(extension: ExtensionEntity)

    @Delete
    suspend fun delete(extension: ExtensionEntity)

    @Query("UPDATE extensions SET isEnabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("UPDATE extensions SET version = :version WHERE id = :id")
    suspend fun updateVersion(id: String, version: Int)
}

@Dao
interface RepositoryDao {
    @Query("SELECT * FROM repositories ORDER BY addedAt ASC")
    fun getRepositories(): Flow<List<RepositoryEntity>>

    @Query("SELECT * FROM repositories WHERE isEnabled = 1")
    suspend fun getEnabledRepositories(): List<RepositoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(repository: RepositoryEntity)

    @Delete
    suspend fun delete(repository: RepositoryEntity)
}

@Dao
interface ReadingHistoryDao {
    @Query("SELECT * FROM reading_history ORDER BY readAt DESC LIMIT :limit")
    fun getRecentHistory(limit: Int = 50): Flow<List<ReadingHistoryEntity>>

    @Query("""
        SELECT 
            h.novelUrl as novelUrl,
            n.title as title,
            n.cover as cover,
            h.chapterUrl as chapterUrl,
            h.chapterTitle as chapterTitle,
            h.scrollPosition as scrollPosition,
            h.readAt as readAt,
            h.extensionId as extensionId,
            n.totalChapters as totalChapters
        FROM reading_history h
        INNER JOIN novels n ON h.novelUrl = n.url
        ORDER BY h.readAt DESC
        LIMIT :limit
    """)
    fun getRecentHistoryWithInfo(limit: Int): Flow<List<RecentNovelWithInfo>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: ReadingHistoryEntity)

    @Query("DELETE FROM reading_history WHERE novelUrl = :novelUrl")
    suspend fun deleteForNovel(novelUrl: String)

    @Query("DELETE FROM reading_history")
    suspend fun clearAll()
}

data class RecentNovelWithInfo(
    val novelUrl: String,
    val title: String,
    val cover: String,
    val chapterUrl: String,
    val chapterTitle: String,
    val scrollPosition: Int,
    val readAt: Long,
    val extensionId: String,
    val totalChapters: Int
)

@Dao
interface DownloadTaskDao {
    @Query("SELECT * FROM download_tasks ORDER BY createdAt DESC")
    fun getAllTasks(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks")
    suspend fun getAllTasksSync(): List<DownloadTaskEntity>

    @Query("SELECT * FROM download_tasks WHERE status IN ('preparing', 'downloading') ORDER BY createdAt ASC")
    fun getActiveTasks(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE novelUrl = :novelUrl")
    suspend fun getTask(novelUrl: String): DownloadTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: DownloadTaskEntity)

    @Query("UPDATE download_tasks SET status = :status, updatedAt = :time WHERE novelUrl = :novelUrl")
    suspend fun updateStatus(novelUrl: String, status: String, time: Long = System.currentTimeMillis())

    @Query("UPDATE download_tasks SET downloadedChapters = :count, updatedAt = :time WHERE novelUrl = :novelUrl")
    suspend fun updateProgress(novelUrl: String, count: Int, time: Long = System.currentTimeMillis())

    @Query("UPDATE download_tasks SET status = 'failed', errorMessage = :error, updatedAt = :time WHERE novelUrl = :novelUrl")
    suspend fun markFailed(novelUrl: String, error: String, time: Long = System.currentTimeMillis())

    @Query("DELETE FROM download_tasks WHERE novelUrl = :novelUrl")
    suspend fun delete(novelUrl: String)

    @Query("DELETE FROM download_tasks WHERE status = 'completed'")
    suspend fun clearCompleted()
}

