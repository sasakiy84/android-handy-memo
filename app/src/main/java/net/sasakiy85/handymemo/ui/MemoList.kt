package net.sasakiy85.handymemo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.flow.Flow
import androidx.paging.PagingData
import net.sasakiy85.handymemo.data.Memo
import net.sasakiy85.handymemo.data.MemoListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoList(
    memoListItems: Flow<PagingData<MemoListItem>>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onAddMemo: () -> Unit,
    onSettingsClick: () -> Unit,
    onMemoClick: (MemoListItem) -> Unit,
    getMemoDetail: suspend (MemoListItem) -> Memo?
) {
    val pagingItems: LazyPagingItems<MemoListItem> = memoListItems.collectAsLazyPagingItems()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        placeholder = { Text("Search with Regex...") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "Search Icon")
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear Search")
                                }
                            }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        singleLine = true
                    )
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddMemo) {
                Icon(Icons.Default.Add, contentDescription = "New Memo")
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when {
                pagingItems.loadState.refresh is LoadState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                pagingItems.loadState.refresh is LoadState.Error -> {
                    val error = (pagingItems.loadState.refresh as LoadState.Error).error
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Error: ${error.message}",
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { pagingItems.retry() }
                        ) {
                            Text("Retry")
                        }
                    }
                }
                pagingItems.itemCount == 0 -> {
                    Text(
                        text = "No memos found",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            count = pagingItems.itemCount,
                            key = pagingItems.itemKey { it.filePath }
                        ) { index ->
                            val item = pagingItems[index]
                            if (item != null) {
                                MemoListItemCard(
                                    memoListItem = item,
                                    onClick = { onMemoClick(item) },
                                    getMemoDetail = getMemoDetail
                                )
                            } else {
                                Card(
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(100.dp)
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }
                            }
                        }

                        // 追加読み込み中の表示
                        if (pagingItems.loadState.append is LoadState.Loading) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        }

                        // 追加読み込みエラー
                        if (pagingItems.loadState.append is LoadState.Error) {
                            item {
                                val error = (pagingItems.loadState.append as LoadState.Error).error
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Error: ${error.message}",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = { pagingItems.retry() }
                                    ) {
                                        Text("Retry")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
