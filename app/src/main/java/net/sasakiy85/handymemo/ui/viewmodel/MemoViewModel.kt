package net.sasakiy85.handymemo.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
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

class MemoViewModel(private val application: Application,
    private val settingsRepository: SettingsRepository
    ): ViewModel() {

    private val _memos = MutableStateFlow<List<Memo>>(emptyList())
    val memos: StateFlow<List<Memo>> = _memos
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
                            parseMemoFile(file)?.let { memo ->
                                memoList.add(memo)
                            }
                        }
                    }
                }

                findMemosRecursive(memosDir)
                memoList.sortedByDescending { it.time }
            }
            _memos.value = loadedMemos
        }

    }

    private val fileNameFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

    private fun parseMemoFile(file: DocumentFile): Memo? {
        val fileName = file.name ?: return  null
        try {
            val lines = application.contentResolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() } ?: return null
            val id = fileName.removeSuffix(".md")
            val localDatetime = LocalDateTime.parse(id, fileNameFormatter)
            val createdTime = ZonedDateTime.of(localDatetime, ZoneId.systemDefault())

            return Memo(
                time = createdTime,
                content = lines,
                id = id,
                tags = emptyList()
            )
        } catch (e: Exception) {
            Log.e("MemoApp", "Failed to parse file: ${file.name}", e)
            return null
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
}