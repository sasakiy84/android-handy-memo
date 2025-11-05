package net.sasakiy85.handymemo.data

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 年月を表すデータクラス
 */
data class YearMonth(val year: Int, val month: Int) {
    init {
        require(year > 0) { "Year must be positive" }
        require(month in 1..12) { "Month must be between 1 and 12" }
    }

    /**
     * 翌月を取得
     */
    fun toNextMonth(): YearMonth {
        return if (month == 12) {
            YearMonth(year + 1, 1)
        } else {
            YearMonth(year, month + 1)
        }
    }

    /**
     * 先月を取得
     */
    fun toPreviousMonth(): YearMonth {
        return if (month == 1) {
            YearMonth(year - 1, 12)
        } else {
            YearMonth(year, month - 1)
        }
    }

    /**
     * その月の開始時刻（ミリ秒）を取得
     * @param zoneId タイムゾーン
     */
    fun getStartTimestamp(zoneId: ZoneId): Long {
        return ZonedDateTime.of(year, month, 1, 0, 0, 0, 0, zoneId)
            .toInstant()
            .toEpochMilli()
    }

    /**
     * その月の終了時刻（ミリ秒）を取得
     * @param zoneId タイムゾーン
     */
    fun getEndTimestamp(zoneId: ZoneId): Long {
        return toNextMonth().getStartTimestamp(zoneId)
    }

    /**
     * この月が指定された月より未来かどうかを判定
     * @param other 比較対象の月
     * @return この月が未来の場合はtrue
     */
    fun isAfter(other: YearMonth): Boolean {
        return year > other.year || (year == other.year && month > other.month)
    }

    /**
     * この月が指定された月より過去かどうかを判定
     * @param other 比較対象の月
     * @return この月が過去の場合はtrue
     */
    fun isBefore(other: YearMonth): Boolean {
        return year < other.year || (year == other.year && month < other.month)
    }

    /**
     * この月が指定された月と同じかどうかを判定
     * @param other 比較対象の月
     * @return 同じ月の場合はtrue
     */
    fun isSame(other: YearMonth): Boolean {
        return year == other.year && month == other.month
    }

    companion object {
        /**
         * 現在の年月を取得
         * @param zoneId タイムゾーン
         */
        fun fromCurrent(zoneId: ZoneId): YearMonth {
            val now = ZonedDateTime.now(zoneId)
            return YearMonth(now.year, now.monthValue)
        }

        /**
         * タイムスタンプ（ミリ秒）から年月を取得
         * @param timestamp ミリ秒のタイムスタンプ
         * @param zoneId タイムゾーン
         */
        fun fromTimestamp(timestamp: Long, zoneId: ZoneId): YearMonth {
            val zonedDateTime = ZonedDateTime.ofInstant(
                Instant.ofEpochMilli(timestamp),
                zoneId
            )
            return YearMonth(zonedDateTime.year, zonedDateTime.monthValue)
        }
    }
}

