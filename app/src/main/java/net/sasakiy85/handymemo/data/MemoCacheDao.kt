package net.sasakiy85.handymemo.data

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoCacheDao {
    // 一覧用の軽量投影（fullText を取得しない）
    @Query("SELECT filePath, displayName, memoCreatedAt FROM memo_cache ORDER BY memoCreatedAt DESC")
    fun getAllMemoListItems(): Flow<List<MemoListItem>>

    // Paging 3 用のクエリ（軽量投影）
    @Query("SELECT filePath, displayName, memoCreatedAt FROM memo_cache ORDER BY memoCreatedAt DESC")
    fun getPagedMemoListItems(): PagingSource<Int, MemoListItem>

    // 月ごとのフィルタリング用PagingSource
    @Query("""
        SELECT filePath, displayName, memoCreatedAt 
        FROM memo_cache 
        WHERE memoCreatedAt >= :startTimestamp 
          AND memoCreatedAt < :endTimestamp
        ORDER BY memoCreatedAt DESC
    """)
    fun getPagedMemoListItemsByMonth(
        startTimestamp: Long,
        endTimestamp: Long
    ): PagingSource<Int, MemoListItem>

    // 検索用の Paging 3 クエリ（軽量投影、空白区切りでAND検索）
    @RawQuery(observedEntities = [MemoCache::class])
    fun getPagedSearchMemoListItems(query: SupportSQLiteQuery): PagingSource<Int, MemoListItem>

    // 詳細用の全文取得
    @Query("SELECT * FROM memo_cache WHERE filePath = :filePath")
    suspend fun getMemoByFilePath(filePath: String): MemoCache?

    // 全削除
    @Query("DELETE FROM memo_cache")
    suspend fun deleteAll()

    // 一括挿入
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(memos: List<MemoCache>)

    // トランザクションで全削除→全件挿入
    @Transaction
    suspend fun replaceAll(memos: List<MemoCache>) {
        deleteAll()
        insertAll(memos)
    }

    // 検索用（オプション）
    @Query("""
        SELECT filePath, displayName, memoCreatedAt 
        FROM memo_cache 
        WHERE displayName LIKE '%' || :query || '%' OR fullText LIKE '%' || :query || '%'
        ORDER BY memoCreatedAt DESC
    """)
    fun searchMemoListItems(query: String): List<MemoListItem>

    // 最も古いメモの日付を取得（ミリ秒のタイムスタンプ）
    @Query("SELECT MIN(memoCreatedAt) FROM memo_cache")
    suspend fun getOldestMemoDate(): Long?
}

