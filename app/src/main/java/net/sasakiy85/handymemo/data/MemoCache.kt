package net.sasakiy85.handymemo.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "memo_cache",
    indices = [Index(value = ["memoCreatedAt"], name = "idx_memo_created_at")]
)
data class MemoCache(
    @PrimaryKey
    val filePath: String,
    val displayName: String,
    val memoCreatedAt: Long, // ミリ秒
    val fullText: String // 全文
)

