package net.sasakiy85.handymemo.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.sasakiy85.handymemo.data.Memo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoList(memos: List<Memo>, onAddMemo: () -> Unit, onSettingsClick: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Memos") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddMemo) {
                Icon(Icons.Default.Add, contentDescription = "Create New Memo")
            }
        }
    ) {
        paddingValues ->
            LazyColumn(modifier = Modifier.padding(paddingValues)) {
                items(memos) { memo ->
                    MemoCard(memo = memo)
                }
            }
    }

}