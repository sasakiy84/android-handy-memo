package net.sasakiy85.handymemo.work

import android.content.Context
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import net.sasakiy85.handymemo.HandyMemoApplication
import net.sasakiy85.handymemo.data.AppDatabase
import net.sasakiy85.handymemo.data.MemoCache
import net.sasakiy85.handymemo.data.SettingsRepository
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class MemoIndexerWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val database = AppDatabase.getDatabase(context)
    private val dao = database.memoCacheDao()
    private val settingsRepository = SettingsRepository(context)
    private val fileNameFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // 手動実行の場合はフォアグラウンドチェックをスキップ
        val isManual = inputData.getBoolean("isManual", false)
        
        // アプリがフォアグラウンドの場合は実行をスキップ（ただし手動実行は除く）
        if (!isManual && HandyMemoApplication.isAppInForeground) {
            Log.d(TAG, "App is in foreground, skipping indexing")
            return@withContext Result.success()
        }
        
        if (isManual) {
            Log.d(TAG, "Manual indexing started")
        }

        try {
            val rootUriString = settingsRepository.rootUriFlow.first()
            if (rootUriString == null) {
                Log.w(TAG, "Root URI is not set, skipping indexing")
                return@withContext Result.success()
            }

            val rootUri = android.net.Uri.parse(rootUriString)
            val rootDir = DocumentFile.fromTreeUri(applicationContext, rootUri)
            if (rootDir == null || !rootDir.exists()) {
                Log.e(TAG, "Root directory not found: $rootUri")
                return@withContext Result.failure(
                    workDataOf("error" to "Root directory not found")
                )
            }

            val memosDir = rootDir.findFile("memos")
            if (memosDir == null || !memosDir.exists()) {
                Log.w(TAG, "Memos directory not found, skipping indexing")
                return@withContext Result.success()
            }

            Log.d(TAG, "Starting memo indexing from: ${memosDir.uri}")

            val memoCacheList = mutableListOf<MemoCache>()
            val errors = mutableListOf<String>()

            // 再帰的にファイルを走査
            findMemosRecursive(memosDir, rootDir, memoCacheList, errors)

            Log.d(TAG, "Found ${memoCacheList.size} memos, ${errors.size} errors")

            // トランザクションで全削除→全件挿入
            try {
                dao.replaceAll(memoCacheList)
                Log.d(TAG, "Successfully indexed ${memoCacheList.size} memos")
                
                Result.success(
                    workDataOf(
                        "indexed_count" to memoCacheList.size,
                        "error_count" to errors.size
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to replace memo cache in database", e)
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to index memos", e)
            Result.failure(
                workDataOf("error" to (e.message ?: "Unknown error"))
            )
        }
    }

    private suspend fun findMemosRecursive(
        directory: DocumentFile,
        rootDir: DocumentFile,
        memoCacheList: MutableList<MemoCache>,
        errors: MutableList<String>
    ) {
        val files = directory.listFiles()
        if (files == null) return
        
        val memoFiles = files.filter { file ->
            file.isFile && file.name?.endsWith(".md") == true
        }

        // 並列処理（最大4並列）
        val chunks = memoFiles.chunked(4)
        for (chunk in chunks) {
            val results = coroutineScope {
                chunk.map { file: DocumentFile ->
                    async<MemoCache?> {
                        try {
                            parseMemoFile(file, rootDir)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse file: ${file.name}", e)
                            errors.add("${file.name}: ${e.message}")
                            null
                        }
                    }
                }.awaitAll()
            }

            memoCacheList.addAll(results.filterNotNull())
        }

        // ディレクトリを再帰的に処理
        files.filter { it.isDirectory }.forEach { subDir ->
            findMemosRecursive(subDir, rootDir, memoCacheList, errors)
        }
    }

    private suspend fun parseMemoFile(
        file: DocumentFile,
        rootDir: DocumentFile
    ): MemoCache? = withContext(Dispatchers.IO) {
        val fileName = file.name ?: return@withContext null
        val filePath = file.uri.toString()

        try {
            // ファイル名から日時を抽出
            val id = fileName.removeSuffix(".md")
            val localDateTime = LocalDateTime.parse(id, fileNameFormatter)
            val time = ZonedDateTime.of(localDateTime, ZoneId.systemDefault())
            val memoCreatedAt = time.toInstant().toEpochMilli()

            // 全文を読み込み
            val fullText = applicationContext.contentResolver.openInputStream(file.uri)
                ?.bufferedReader()?.use { it.readText() }
                ?: return@withContext null

            // displayName はファイル名（拡張子なし）
            val displayName = id

            MemoCache(
                filePath = filePath,
                displayName = displayName,
                memoCreatedAt = memoCreatedAt,
                fullText = fullText
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse memo file: $fileName", e)
            null
        }
    }

    companion object {
        private const val TAG = "MemoIndexerWorker"
        const val WORK_NAME_ONETIME = "memo_indexer_onetime"
        const val WORK_NAME_PERIODIC = "memo_indexer_periodic"
    }
}

