package net.sasakiy85.handymemo.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.sasakiy85.handymemo.data.Memo
import net.sasakiy85.handymemo.data.SettingsRepository
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import net.sasakiy85.handymemo.data.MediaAttachment
import java.io.File

class MemoViewModel(private val application: Application,
    private val settingsRepository: SettingsRepository
    ): ViewModel() {

    private val _allMemos = MutableStateFlow<List<Memo>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery
    @OptIn(FlowPreview::class)
    val memos: StateFlow<List<Memo>> = _searchQuery.debounce(300L)
        .combine(_allMemos) { query, allMemos ->
            if (query.isBlank()) {
                allMemos
            } else {
                withContext(Dispatchers.Default) {
                    try {
                        val regex = query.toRegex(RegexOption.IGNORE_CASE)
                        allMemos.filter { memo ->
                            regex.containsMatchIn(memo.bodyText)
                        }
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val rootUri: StateFlow<Uri?> = settingsRepository.rootUriFlow
        .map { uriString -> uriString?.toUri() }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
    val lastUsedTemplate: StateFlow<String> = settingsRepository.lastUsedTemplateFlow.stateIn(viewModelScope, started = SharingStarted.Lazily, initialValue = "")

    private val _navigateToEditScreen = MutableStateFlow<String?>(null)
    val navigateToEditScreen: StateFlow<String?> = _navigateToEditScreen

    init {
        viewModelScope.launch {
            rootUri.collect { uri ->
                if (uri != null) {
                    loadMemos()
                } else {
                    Log.d("MemoApp", "rootUri is not set")
                }
            }
        }
    }

    fun saveRootUri(uri: Uri) {
        viewModelScope.launch {
            settingsRepository.saveRootUri(uri.toString())
        }
    }

    private fun loadMemos() {
        val currentRootUri = rootUri.value ?: return
        viewModelScope.launch {
            val loadedMemos = withContext(Dispatchers.IO) {
                val rootDir = DocumentFile.fromTreeUri(application, currentRootUri) ?: return@withContext emptyList<Memo>()
                val memosDir = rootDir.findFile("memos") ?: return@withContext emptyList<Memo>()

                Log.d("MemoApp", "Try to walking directory: ${memosDir.uri}")
                if (!memosDir.exists()) {
                    Log.e("MemoApp", "Memos directory not found: ${memosDir.uri}")
                    return@withContext emptyList<Memo>()
                }

                val memoList = mutableListOf<Memo>()
                fun findMemosRecursive(directory: DocumentFile) {
                    directory.listFiles().forEach { file ->
                        if (file.isDirectory) {
                            findMemosRecursive(file)
                        } else if (file.isFile && file.name?.endsWith(".md") == true) {
                            parseMemoFile(file, rootDir)?.let { memo ->
                                memoList.add(memo)
                            }
                        }
                    }
                }

                findMemosRecursive(memosDir)
                memoList.sortedByDescending { it.time }
            }
            _allMemos.value = loadedMemos
        }

    }

    private val fileNameFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

    private fun parseMemoFile(file: DocumentFile, rootDir: DocumentFile): Memo? {
        val fileName = file.name ?: return null
        try {
            val id = fileName.removeSuffix(".md")
            val localDateTime = LocalDateTime.parse(id, fileNameFormatter)
            val time = ZonedDateTime.of(localDateTime, ZoneId.systemDefault())

            val bodyWithLinks = application.contentResolver.openInputStream(file.uri)
                ?.bufferedReader()?.use { it.readText() } ?: return null


            val markdownLinkRegex = "!\\[(.*?)]\\((.*?)\\)".toRegex()
            val attachments = markdownLinkRegex.findAll(bodyWithLinks).mapNotNull { matchResult ->
                val relativePath = matchResult.groupValues[2]
                // ルートから相対パスをたどってメディアファイルを見つける
                findMediaFile(rootDir, relativePath)?.let { mediaFile ->
                    val mimeType = mediaFile.type
                    val isVideo = mimeType?.startsWith("video/") == true
                    var thumbnailUri: Uri? = null

                    if (isVideo) {
                        // Generate and cache the thumbnail for videos
                        thumbnailUri = generateAndCacheThumbnail(mediaFile.uri)
                    }
                    MediaAttachment(mediaFile.uri, isVideo, thumbnailUri)
                }
            }.toList()

            val cleanBodyText = bodyWithLinks.replace(markdownLinkRegex, "[Media Inserted]").trim()

            return Memo(id, time, tags = emptyList(), cleanBodyText, attachments)
        } catch (e: Exception) {
            Log.e("MemoApp", "Failed to parse file: $fileName", e)
            return null
        }
    }
    private fun findMediaFile(rootDir: DocumentFile, relativePath: String): DocumentFile? {
        // "../" を無視し、意味のあるパス部分だけを抽出
        val pathSegments = relativePath.split('/').filter { it.isNotBlank() && it != ".." }
        var currentFile = rootDir
        pathSegments.forEach { segment ->
            currentFile = currentFile.findFile(segment) ?: return null
        }
        return if (currentFile.uri != rootDir.uri && currentFile.isFile) currentFile else null
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
            val fileContent = """
                $content
            """.trimIndent()

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

                    application.contentResolver.openOutputStream(newFile.uri)?.bufferedWriter()?.use { writer ->
                        writer.write(content)
                    }
                } catch (e: Exception) {
                    Log.e("MemoApp", "Failed to create memo file", e)
                    return@withContext
                }
            }
        }
        loadMemos()
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


    private val _insertTextEvent = MutableSharedFlow<String>()
    val insertTextEvent = _insertTextEvent.asSharedFlow()
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
        // Create a unique name for the cache file based on the original URI
        val cacheFileName = "thumb_${videoUri.toString().hashCode()}.jpg"
        val cacheDir = File(context.cacheDir, "thumbnails")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        val thumbnailFile = File(cacheDir, cacheFileName)

        // If the thumbnail already exists in the cache, return its URI
        if (thumbnailFile.exists()) {
            return Uri.fromFile(thumbnailFile)
        }

        // If not cached, extract the thumbnail
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            // Get a frame at the 1-second mark
            val bitmap = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)

            if (bitmap != null) {
                // Save the bitmap to the cache file
                thumbnailFile.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                }
                // Return the URI of the newly created cache file
                return Uri.fromFile(thumbnailFile)
            }
        } catch (e: Exception) {
            Log.e("MemoApp", "Failed to generate thumbnail for $videoUri", e)
        } finally {
            retriever.release()
        }
        return null
    }


    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }
}