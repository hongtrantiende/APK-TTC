package com.nam.novelreader.feature.library

import androidx.lifecycle.ViewModel
import com.nam.novelreader.data.repository.NovelRepository
import com.nam.novelreader.domain.model.Novel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.viewModelScope
import kotlinx.serialization.encodeToString
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: NovelRepository,
) : ViewModel() {

    val libraryNovels: Flow<List<Novel>> = repository.getLibraryNovels()
    val downloadTasks: Flow<List<com.nam.novelreader.data.local.entity.DownloadTaskEntity>> = repository.getAllDownloadTasks()

    fun removeFromLibrary(novel: Novel) {
        viewModelScope.launch {
            repository.removeFromLibrary(novel.url)
        }
    }

    fun exportNovelAsTxt(context: android.content.Context, novel: Novel) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val chapters = repository.getChapterList(novel.url)
                    .sortedBy { it.index }
                    .filter { it.isDownloaded && !it.content.isNullOrBlank() }
                
                if (chapters.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Không có chương chữ nào được tải!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val fileName = "${novel.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")}.txt"
                
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/VBook")
                    }
                }

                val uri = context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        java.io.OutputStreamWriter(os, Charsets.UTF_8).use { writer ->
                            writer.write("${novel.title}\n${novel.author}\n\n")
                            for (ch in chapters) {
                                val cleanedContent = com.nam.novelreader.util.EpubWriter.cleanHtmlContent(ch.content)
                                writer.write("${ch.title}\n\n${cleanedContent}\n\n")
                            }
                        }
                    }
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Đã xuất file TXT thành công!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Lỗi khi xuất TXT: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun exportNovelAsEpub(context: android.content.Context, novel: Novel) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val chapters = repository.getChapterList(novel.url)
                    .sortedBy { it.index }
                    .filter { it.isDownloaded && !it.content.isNullOrBlank() }
                    .map { com.nam.novelreader.domain.model.Chapter(url = it.url, title = it.title, content = it.content, index = it.index) }
                
                if (chapters.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Không có chương chữ nào được tải!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val fileName = "${novel.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")}.epub"
                
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/epub+zip")
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/VBook")
                    }
                }

                val uri = context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        com.nam.novelreader.util.EpubWriter.writeEpub(os, novel, chapters)
                    }
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Đã xuất file EPUB thành công!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Lỗi khi xuất EPUB: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun backupNovel(context: android.content.Context, novel: Novel) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fileName = "${novel.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")}_backup.json"
                val jsonStr = kotlinx.serialization.json.Json.encodeToString(novel)
                
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/VBook/Backup")
                    }
                }

                val uri = context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        java.io.OutputStreamWriter(os, Charsets.UTF_8).use { writer ->
                            writer.write(jsonStr)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Đã sao lưu thành công!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Lỗi sao lưu: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
