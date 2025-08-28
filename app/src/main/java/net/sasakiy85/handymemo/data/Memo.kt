package net.sasakiy85.handymemo.data

import android.net.Uri
import java.time.ZonedDateTime

data class MediaAttachment(
    val uri: Uri,
    val isVideo: Boolean,
    val thumbnailUri: Uri? = null
)
data class Memo(
    val id: String,
    val time: ZonedDateTime,
    val tags: List<String>,
    val bodyText: String, // Markdownリンクが取り除かれた本文
    val attachments: List<MediaAttachment>
)