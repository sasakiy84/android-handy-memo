package net.sasakiy85.handymemo.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import net.sasakiy85.handymemo.data.Memo
import net.sasakiy85.handymemo.data.MemoListItem
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Composable
fun MemoListItemCard(
    memoListItem: MemoListItem,
    onClick: () -> Unit,
    getMemoDetail: suspend (MemoListItem) -> Memo?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val formatter = remember { DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm") }
    val time = ZonedDateTime.ofInstant(
        Instant.ofEpochMilli(memoListItem.memoCreatedAt),
        ZoneId.systemDefault()
    )
    
    var memo: Memo? by remember(memoListItem.filePath) { mutableStateOf(null) }
    var isLoading by remember(memoListItem.filePath) { mutableStateOf(true) }

    LaunchedEffect(memoListItem.filePath) {
        isLoading = true
        memo = getMemoDetail(memoListItem)
        isLoading = false
    }

    Card(
        modifier = modifier
            .padding(8.dp)
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = time.format(formatter),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isLoading) {
                // ローディング中
                Text(
                    text = "Loading...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (memo != null) {
                // 本文を表示
                if (memo!!.bodyText.isNotEmpty()) {
                    Text(
                        text = memo!!.bodyText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                // 画像・動画を表示
                if (memo!!.attachments.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    memo!!.attachments.forEach { attachment ->
                        if (attachment.isVideo) {
                            // --- 動画の場合 ---
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16 / 9f)
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable {
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(attachment.uri, "video/*")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(intent)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                // 背景にサムネイル画像を表示
                                AsyncImage(
                                    model = attachment.thumbnailUri,
                                    contentDescription = "Video Thumbnail",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                // 中央に再生アイコンを重ねて表示
                                Icon(
                                    imageVector = Icons.Default.PlayCircleOutline,
                                    contentDescription = "Play Video",
                                    modifier = Modifier.size(64.dp),
                                    tint = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        } else {
                            // --- 画像の場合 ---
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16 / 9f)
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable {
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(attachment.uri, "image/*")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(intent)
                                    }
                            ) {
                                AsyncImage(
                                    model = attachment.uri,
                                    contentDescription = "Attached Image",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            } else {
                // エラーまたはデータなし
                Text(
                    text = memoListItem.displayName,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
