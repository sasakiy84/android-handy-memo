package net.sasakiy85.handymemo.data

data class MemoListItem(
    val filePath: String,
    val displayName: String,
    val memoCreatedAt: Long // ミリ秒
)

