package net.sasakiy85.handymemo.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.sasakiy85.handymemo.data.AppDatabase
import net.sasakiy85.handymemo.data.Memo
import net.sasakiy85.handymemo.data.MemoCache
import net.sasakiy85.handymemo.data.MemoListItem
import net.sasakiy85.handymemo.data.SettingsRepository
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import net.sasakiy85.handymemo.data.MediaAttachment
import java.io.File

class MemoViewModel(
    private val application: Application,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val database = AppDatabase.getDatabase(application)
    private val dao = database.memoCacheDao()

    // Paging 3 を使用したメモ一覧
    val memoListItems: Flow<PagingData<MemoListItem>> = Pager(
        config = PagingConfig(
            pageSize = 20,
            enablePlaceholders = false,
            prefetchDistance = 5
        )
    ) {
        dao.getPagedMemoListItems()
    }.flow.cachedIn(viewModelScope)

    val rootUri: StateFlow<Uri?> = settingsRepository.rootUriFlow
        .map { uriString -> uriString?.toUri() }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val lastUsedTemplate: StateFlow<String> = settingsRepository.lastUsedTemplateFlow.stateIn(
        viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = ""
    )

    private val _navigateToEditScreen = MutableStateFlow<String?>(null)
    val navigateToEditScreen: StateFlow<String?> = _navigateToEditScreen

    private val _insertTextEvent = MutableSharedFlow<String>()
    val insertTextEvent = _insertTextEvent.asSharedFlow()

    private val fileNameFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

    // 詳細表示用：MemoListItem から Memo を構築（DB から fullText と attachments を取得）
    suspend fun getMemoDetail(memoListItem: MemoListItem): Memo? = withContext(Dispatchers.IO) {
        try {
            val memoCache = dao.getMemoByFilePath(memoListItem.filePath)
                ?: return@withContext null

            val rootUriString = settingsRepository.rootUriFlow.first()
            val rootUri = rootUriString?.toUri() ?: return@withContext null
            val rootDir = DocumentFile.fromTreeUri(application, rootUri)
                ?: return@withContext null

            // fullTextから attachments を取得
            val markdownLinkRegex = "!\\[(.*?)]\\((.*?)\\)".toRegex()
            val attachments = markdownLinkRegex.findAll(memoCache.fullText).mapNotNull { matchResult ->
                val relativePath = matchResult.groupValues[2]
                findMediaFile(rootDir, relativePath)?.let { mediaFile ->
                    val mimeType = mediaFile.type
                    val isVideo = mimeType?.startsWith("video/") == true
                    var thumbnailUri: Uri? = null

                    if (isVideo) {
                        thumbnailUri = generateAndCacheThumbnail(mediaFile.uri)
                    }
                    MediaAttachment(mediaFile.uri, isVideo, thumbnailUri)
                }
            }.toList()

            val cleanBodyText = memoCache.fullText.replace(markdownLinkRegex, "[Media Inserted]").trim()

            val localDateTime = LocalDateTime.parse(memoListItem.displayName, fileNameFormatter)
            val time = ZonedDateTime.of(localDateTime, ZoneId.systemDefault())

            Memo(
                id = memoListItem.displayName,
                time = time,
                tags = emptyList(),
                bodyText = cleanBodyText,
                attachments = attachments
            )
        } catch (e: Exception) {
            Log.e("MemoViewModel", "Failed to get memo detail: ${memoListItem.filePath}", e)
            null
        }
    }

    private fun findMediaFile(rootDir: DocumentFile, relativePath: String): DocumentFile? {
        val pathSegments = relativePath.split('/').filter { it.isNotBlank() && it != ".." }
        var currentFile = rootDir
        pathSegments.forEach { segment ->
            currentFile = currentFile.findFile(segment) ?: return null
        }
        return if (currentFile.uri != rootDir.uri && currentFile.isFile) currentFile else null
    }

    fun saveRootUri(uri: Uri) {
        viewModelScope.launch {
            settingsRepository.saveRootUri(uri.toString())
        }
    }

    fun createMemo(content: String) {
        val currentRootUri = rootUri.value
        if (currentRootUri == null) {
            Log.e("MemoApp", "Tried to create new memo, but rootUri is not set")
            return
        }
        viewModelScope.launch {
            val now = ZonedDateTime.now()
            val id = now.format(fileNameFormatter)
            val year = now.year.toString()
            val month = "%02d".format(now.monthValue)

            val rootDir = DocumentFile.fromTreeUri(application, currentRootUri)
            if (rootDir == null) {
                Log.d("MemoApp", "Failed to create memo, rootDir is null")
                return@launch
            }
            withContext(Dispatchers.IO) {
                try {
                    val memosDir = rootDir.findFile("memos") ?: rootDir.createDirectory("memos") ?: run {
                        Log.d("MemoApp", "Failed to create memo, memosDir is null")
                        return@withContext
                    }
                    val yearDir = memosDir.findFile(year) ?: memosDir.createDirectory(year) ?: run {
                        Log.d("MemoApp", "Failed to create memo, yearDir is null")
                        return@withContext
                    }
                    val monthDir = yearDir.findFile(month) ?: yearDir.createDirectory(month) ?: run {
                        Log.d("MemoApp", "Failed to create memo, monthDir is null")
                        return@withContext
                    }
                    val newFile = monthDir.createFile("text/markdown", "$id.md") ?: run {
                        Log.d("MemoApp", "Failed to create memo, file is null")
                        return@withContext
                    }

                    // ファイルに書き込み
                    application.contentResolver.openOutputStream(newFile.uri)?.bufferedWriter()?.use { writer ->
                        writer.write(content)
                    }

                    // DBにも挿入
                    val filePath = newFile.uri.toString()
                    val memoCreatedAt = now.toInstant().toEpochMilli()
                    val memoCache = MemoCache(
                        filePath = filePath,
                        displayName = id,
                        memoCreatedAt = memoCreatedAt,
                        fullText = content
                    )
                    dao.insertAll(listOf(memoCache))
                    
                    Log.d("MemoApp", "Successfully created memo and inserted into DB: $filePath")
                } catch (e: Exception) {
                    Log.e("MemoApp", "Failed to create memo file", e)
                    return@withContext
                }
            }
        }
    }

    fun clearLastUsedTemplate() {
        viewModelScope.launch {
            settingsRepository.saveLastUsedTemplate("")
        }
    }

    fun onWidgetTapped(templateText: String?) {
        viewModelScope.launch {
            settingsRepository.saveLastUsedTemplate(templateText ?: "")
        }
        _navigateToEditScreen.value = templateText
    }

    fun onNavigationCompleted() {
        _navigateToEditScreen.value = null
    }

    fun attachImages(uris: List<Uri>) {
        val currentRootUri = rootUri.value ?: run {
            Log.e("MemoApp", "Tried to attach images, but rootUri is not set")
            return
        }
        viewModelScope.launch {
            val markdownLinks = mutableListOf<String>()
            withContext(Dispatchers.IO) {
                val rootDir = DocumentFile.fromTreeUri(application, currentRootUri) ?: run {
                    Log.e("MemoApp", "Failed to attach images, rootDir is null")
                    return@withContext
                }
                val now = ZonedDateTime.now()
                val id = now.format(fileNameFormatter)
                val year = now.year.toString()
                val month = "%02d".format(now.monthValue)

                uris.forEachIndexed { index, uri ->
                    Log.d("MemoApp", "Attaching image: $uri")
                    try {
                        val mimeType = application.contentResolver.getType(uri)
                        val isVideo = mimeType?.startsWith("video") == true

                        val parentDirName = if (isVideo) "videos" else "images"
                        val extension =
                            MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: run {
                                Log.e("MemoApp", "Failed to attach images, extension is null")
                                _insertTextEvent.emit("Failed to getExtensionFromMimeType() and attach images due to unknown file type $mimeType")
                                return@withContext
                            }
                        val parentDir = rootDir.findFile(parentDirName) ?: rootDir.createDirectory(
                            parentDirName
                        ) ?: run {
                            Log.e("MemoApp", "Failed to attach images, parentDir is null")
                            return@withContext
                        }
                        val yearDir =
                            parentDir.findFile(year) ?: parentDir.createDirectory(year) ?: run {
                                Log.e("MemoApp", "Failed to attach images, yearDir is null")
                                return@withContext
                            }
                        val monthDir =
                            yearDir.findFile(month) ?: yearDir.createDirectory(month) ?: run {
                                Log.e("MemoApp", "Failed to attach images, monthDir is null")
                                return@withContext
                            }
                        val fileName = "$id-$index.$extension"
                        val newFile = monthDir.createFile(mimeType ?: "*/*", fileName) ?: run {
                            Log.e("MemoApp", "Failed to attach images, newFile is null")
                            return@withContext
                        }
                        application.contentResolver.openInputStream(uri)?.use { inputStream ->
                            application.contentResolver.openOutputStream(newFile.uri)
                                ?.use { outputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                        }
                        val altText = if (isVideo) "Video" else "Image"
                        val relativePath = "../../../$parentDirName/$year/$month/$fileName"
                        markdownLinks.add("![$altText]($relativePath)")
                    } catch (e: Exception) {
                        Log.e("MemoApp", "Failed to copy media from uri: $uri", e)
                    }
                }
            }
            if (markdownLinks.isNotEmpty()) {
                _insertTextEvent.emit("\n${markdownLinks.joinToString("\n\n")}\n")
            }
        }
    }

    private fun generateAndCacheThumbnail(videoUri: Uri): Uri? {
        val context = application.applicationContext
        val cacheFileName = "thumb_${videoUri.toString().hashCode()}.jpg"
        val cacheDir = File(context.cacheDir, "thumbnails")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        val thumbnailFile = File(cacheDir, cacheFileName)

        if (thumbnailFile.exists()) {
            return Uri.fromFile(thumbnailFile)
        }

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val bitmap = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)

            if (bitmap != null) {
                thumbnailFile.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                }
                return Uri.fromFile(thumbnailFile)
            }
        } catch (e: Exception) {
            Log.e("MemoApp", "Failed to generate thumbnail for $videoUri", e)
        } finally {
            retriever.release()
        }
        return null
    }
}
