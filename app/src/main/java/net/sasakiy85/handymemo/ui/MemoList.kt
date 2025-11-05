package net.sasakiy85.handymemo.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.flow.Flow
import androidx.paging.PagingData
import net.sasakiy85.handymemo.data.Memo
import net.sasakiy85.handymemo.data.MemoListItem
import net.sasakiy85.handymemo.data.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoList(
    memoListItems: Flow<PagingData<MemoListItem>>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onAddMemo: () -> Unit,
    onSettingsClick: () -> Unit,
    onMemoClick: (MemoListItem) -> Unit,
    getMemoDetail: suspend (MemoListItem) -> Memo?,
    currentDisplayMonth: YearMonth,
    onMoveToNextMonth: () -> Unit,
    onMoveToPreviousMonth: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pagingItems: LazyPagingItems<MemoListItem> = memoListItems.collectAsLazyPagingItems()
    
    // 検索モードかどうか
    val isSearchMode = searchQuery.isNotBlank()
    
    val density = LocalDensity.current
    val swipeThreshold = with(density) { 100.dp.toPx() } // スワイプ判定の閾値（100dp）
    
    // スワイプアニメーション用の状態
    var dragOffset by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    
    // ドラッグ中のオフセットをアニメーション（制限付き、スムーズにする）
    val animatedDragOffset by animateFloatAsState(
        targetValue = dragOffset,
        animationSpec = tween(durationMillis = 150, easing = androidx.compose.animation.core.EaseOutCubic),
        label = "dragOffset"
    )
    
    // 月切り替え時のトランジション用キー
    val monthKey = remember(currentDisplayMonth) { 
        "${currentDisplayMonth.year}-${currentDisplayMonth.month}" 
    }

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
        Box(modifier = modifier.padding(paddingValues)) {
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
                    // 月切り替え時のスムーズなトランジション
                    AnimatedContent(
                        targetState = monthKey,
                        transitionSpec = {
                            if (targetState > initialState) {
                                // 左スワイプ（翌月へ）- より滑らかなアニメーション
                                fadeIn(
                                    animationSpec = tween(250, easing = androidx.compose.animation.core.EaseOutCubic)
                                ) + slideInHorizontally(
                                    initialOffsetX = { (it * 0.3).toInt() },
                                    animationSpec = tween(250, easing = androidx.compose.animation.core.EaseOutCubic)
                                ) togetherWith fadeOut(
                                    animationSpec = tween(250, easing = androidx.compose.animation.core.EaseInCubic)
                                ) + slideOutHorizontally(
                                    targetOffsetX = { (-it * 0.3).toInt() },
                                    animationSpec = tween(250, easing = androidx.compose.animation.core.EaseInCubic)
                                )
                            } else {
                                // 右スワイプ（先月へ）- より滑らかなアニメーション
                                fadeIn(
                                    animationSpec = tween(250, easing = androidx.compose.animation.core.EaseOutCubic)
                                ) + slideInHorizontally(
                                    initialOffsetX = { (-it * 0.3).toInt() },
                                    animationSpec = tween(250, easing = androidx.compose.animation.core.EaseOutCubic)
                                ) togetherWith fadeOut(
                                    animationSpec = tween(250, easing = androidx.compose.animation.core.EaseInCubic)
                                ) + slideOutHorizontally(
                                    targetOffsetX = { (it * 0.3).toInt() },
                                    animationSpec = tween(250, easing = androidx.compose.animation.core.EaseInCubic)
                                )
                            }
                        },
                        label = "monthTransition"
                    ) { _ ->
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (!isSearchMode && isDragging) {
                                        Modifier.offset(x = with(density) { animatedDragOffset.dp })
                                    } else {
                                        Modifier
                                    }
                                )
                                .pointerInput(isSearchMode, swipeThreshold) {
                                    if (isSearchMode) return@pointerInput // 検索モード時はスワイプ無効
                                    
                                    var swipeOffset = 0f
                                    
                                    detectHorizontalDragGestures(
                                        onDragStart = {
                                            isDragging = true
                                            dragOffset = 0f
                                        },
                                        onDragEnd = {
                                            isDragging = false
                                            // ドラッグ終了時にスワイプ判定
                                            if (swipeOffset > swipeThreshold) {
                                                // 左スワイプ（正のdx）→ 翌月
                                                onMoveToNextMonth()
                                            } else if (swipeOffset < -swipeThreshold) {
                                                // 右スワイプ（負のdx）→ 先月
                                                onMoveToPreviousMonth()
                                            }
                                            // 元の位置に戻す（アニメーション付き）
                                            dragOffset = 0f
                                            swipeOffset = 0f
                                        }
                                    ) { change, dragAmount ->
                                        // ドラッグ中の処理
                                        swipeOffset += dragAmount
                                        dragOffset += dragAmount
                                        // ドラッグ量を制限（画面幅の20%まで）- 視覚的フィードバックを抑える
                                        val maxOffset = with(density) { 100.dp.toPx() }
                                        dragOffset = dragOffset.coerceIn(-maxOffset, maxOffset)
                                    }
                                },
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
}
