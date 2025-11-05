package net.sasakiy85.handymemo.data

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery

object MemoSearchHelper {
    /**
     * 検索クエリを空白で分割し、AND条件で検索するSQLクエリを生成
     * @param searchQuery 検索クエリ（空白区切りで複数キーワード）
     * @return SupportSQLiteQuery
     */
    fun buildSearchQuery(searchQuery: String): SupportSQLiteQuery {
        val keywords = searchQuery.trim()
            .split("\\s+".toRegex())
            .filter { it.isNotBlank() }
            .map { it.trim() }

        if (keywords.isEmpty()) {
            // 空の場合は全件取得
            return SimpleSQLiteQuery(
                "SELECT filePath, displayName, memoCreatedAt FROM memo_cache ORDER BY memoCreatedAt DESC"
            )
        }

        // 各キーワードに対してAND条件を構築
        val conditions = keywords.mapIndexed { index, keyword ->
            val escapedKeyword = keyword.replace("'", "''") // SQLインジェクション対策
            "(displayName LIKE ? OR fullText LIKE ?)"
        }.joinToString(" AND ")

        val sql = """
            SELECT filePath, displayName, memoCreatedAt 
            FROM memo_cache 
            WHERE $conditions
            ORDER BY memoCreatedAt DESC
        """.trimIndent()

        // 各キーワードに対して2つのプレースホルダー（displayName用とfullText用）を追加
        val bindArgs: Array<Any?> = keywords.flatMap { keyword ->
            listOf("%$keyword%", "%$keyword%")
        }.toTypedArray()

        return SimpleSQLiteQuery(sql, bindArgs)
    }
}

