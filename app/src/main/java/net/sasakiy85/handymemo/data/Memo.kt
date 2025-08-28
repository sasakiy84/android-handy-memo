package net.sasakiy85.handymemo.data

import java.time.ZonedDateTime

data class Memo(
    val id: String,
    val time: ZonedDateTime,
    val tags: List<String>,
    val content: String
)