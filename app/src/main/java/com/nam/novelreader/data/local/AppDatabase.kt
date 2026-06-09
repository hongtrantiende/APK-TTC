package com.nam.novelreader.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.nam.novelreader.data.local.dao.*
import com.nam.novelreader.data.local.entity.*

@Database(
    entities = [
        NovelEntity::class,
        ChapterEntity::class,
        ExtensionEntity::class,
        RepositoryEntity::class,
        ReadingHistoryEntity::class,
        DownloadTaskEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun novelDao(): NovelDao
    abstract fun chapterDao(): ChapterDao
    abstract fun extensionDao(): ExtensionDao
    abstract fun repositoryDao(): RepositoryDao
    abstract fun readingHistoryDao(): ReadingHistoryDao
    abstract fun downloadTaskDao(): DownloadTaskDao
}
