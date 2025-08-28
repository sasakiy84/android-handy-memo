package net.sasakiy85.handymemo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.sasakiy85.handymemo.data.Memo
import java.time.format.DateTimeFormatter

@Composable
fun MemoCard(memo: Memo) {
    Card(modifier = Modifier.padding(8.dp).fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (memo.tags.isNotEmpty()) {
                    Text(
                        text = memo.tags.joinToString(", "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (memo.tags.isEmpty()) {
                    Spacer(modifier = Modifier.weight(1f))
                }

                val formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
                // 2. ZonedDateTime オブジェクトの format メソッドで書式を適用
                val formattedTime = memo.time.format(formatter)

                Text(
                    text = formattedTime,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Text(
                modifier = Modifier.padding(top = 8.dp),
                text = memo.content,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}