package net.sasakiy85.handymemo

import android.app.Activity
import android.app.ComponentCaller
import android.content.Intent
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import net.sasakiy85.handymemo.data.SettingsRepository
import net.sasakiy85.handymemo.ui.MemoEditScreen
import net.sasakiy85.handymemo.ui.theme.HandyMemoTheme
import net.sasakiy85.handymemo.ui.MemoList
import net.sasakiy85.handymemo.ui.SettingsScreen
import net.sasakiy85.handymemo.ui.viewmodel.MemoViewModel
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

class MainActivity : ComponentActivity() {

    private lateinit var memoViewModel: MemoViewModel
    private val pickMultiplePhotoLauncher = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            Log.d("PhotoPicker", "Selected URIs: $uris")
            memoViewModel.attachImages(uris)
        }
    }
    private val openDirectoryLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            memoViewModel.saveRootUri(uri)
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val settingsRepository = SettingsRepository(applicationContext)
        val factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(MemoViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return MemoViewModel(application, settingsRepository) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
        memoViewModel = ViewModelProvider(this, factory)[MemoViewModel::class.java]

        // WorkManager の初期化（OneTime + Periodic）
        net.sasakiy85.handymemo.work.WorkManagerInitializer.initializeWorkManager(applicationContext)

        handleIntent(intent)
        enableEdgeToEdge()
        setContent {
            HandyMemoTheme {
                val activity = LocalActivity.current
                val rootUri by memoViewModel.rootUri.collectAsState()
                val initialUri = DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents",
                    "primary"
                )
                if (rootUri == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(onClick = { openDirectoryLauncher.launch(initialUri) }) {
                            Text("Select Memo Folder")
                        }
                    }
                } else {
                    val lastUsedTemplate by memoViewModel.lastUsedTemplate.collectAsState()
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "memoEdit?template=${URLEncoder.encode(lastUsedTemplate, "UTF-8")}") {
                        composable("memoList") {
                            val searchQuery by memoViewModel.searchQuery.collectAsState()
                            val currentDisplayMonth by memoViewModel.currentDisplayMonth.collectAsState()
                            MemoList(
                                memoListItems = memoViewModel.memoListItems,
                                searchQuery = searchQuery,
                                onSearchQueryChange = { query ->
                                    memoViewModel.onSearchQueryChange(query)
                                },
                                onAddMemo = {
                                    val encodedTemplate = URLEncoder.encode(lastUsedTemplate, "UTF-8")
                                    navController.navigate("memoEdit?template=$encodedTemplate")
                                },
                                onSettingsClick = {
                                    navController.navigate("settings")
                                },
                                onMemoClick = { memoListItem ->
                                    // TODO: 詳細画面への遷移（後で追加）
                                    // 現在は一覧のみ表示
                                },
                                getMemoDetail = { memoListItem ->
                                    memoViewModel.getMemoDetail(memoListItem)
                                },
                                currentDisplayMonth = currentDisplayMonth,
                                onMoveToNextMonth = {
                                    memoViewModel.moveToNextMonth()
                                },
                                onMoveToPreviousMonth = {
                                    memoViewModel.moveToPreviousMonth()
                                }
                            )
                        }
                        composable("memoEdit?template={templateText}",
                            arguments = listOf(navArgument("templateText") {
                                type = NavType.StringType
                                defaultValue = ""
                            })) { backStackEntry ->
                            val encodedTemplate = backStackEntry.arguments?.getString("templateText") ?: ""
                            val template = URLDecoder.decode(encodedTemplate, "UTF-8")
                            var textFieldValue by remember { mutableStateOf(TextFieldValue(template)) }

                            LaunchedEffect(Unit) {
                                memoViewModel.insertTextEvent.collect { textToInsert ->
                                    val currentText = textFieldValue.text
                                    val cursorPosition = textFieldValue.selection.start
                                    val newText = currentText.replaceRange(cursorPosition, cursorPosition, textToInsert)
                                    val newCursorPosition = cursorPosition + textToInsert.length

                                    textFieldValue = TextFieldValue(
                                        text = newText,
                                        selection = TextRange(newCursorPosition)
                                    )
                                }
                            }

                            MemoEditScreen(
                                value = textFieldValue,
                                onValueChange = { textFieldValue = it },
                                onSave = { content ->
                                    memoViewModel.createMemo(content)
                                    navController.popBackStack()
                                },
                                onSaveAndClose = { content ->
                                    memoViewModel.createMemo(content)
                                    activity?.finish()
                                },
                                onAddImages = {
                                    pickMultiplePhotoLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                    )
                                },
                                onToList = { navController.navigate("memoList") },
                                onClear = {
                                    textFieldValue = TextFieldValue("")
                                    memoViewModel.clearLastUsedTemplate()
                                },
                            )
                        }
                        composable("settings") {
                            val indexingStatus by memoViewModel.indexingStatus.collectAsState()
                            val shareIntentTemplate by memoViewModel.shareIntentTemplate.collectAsState()
                            SettingsScreen(
                                currentFolderUri = rootUri,
                                onSelectFolder = {
                                    // Launch the same directory picker
                                    openDirectoryLauncher.launch(initialUri)
                                },
                                onBack = {
                                    navController.popBackStack()
                                },
                                onReloadIndex = {
                                    memoViewModel.triggerManualIndexing()
                                },
                                indexingStatus = indexingStatus,
                                shareIntentTemplate = shareIntentTemplate,
                                onShareIntentTemplateChange = { template ->
                                    memoViewModel.saveShareIntentTemplate(template)
                                }
                            )
                        }
                    }

                    val templateToOpen by memoViewModel.navigateToEditScreen.collectAsState()
                    LaunchedEffect(templateToOpen) {
                        if (templateToOpen != null) {
                            // URLエンコードして安全に引数を渡す
                            val encodedTemplate = URLEncoder.encode(templateToOpen, "UTF-8")
                            navController.navigate("memoEdit?template=$encodedTemplate")
                            memoViewModel.onNavigationCompleted() // 処理完了をViewModelに通知
                        }
                    }

                }
            }
        }


//        setContent {
//            HandyMemoTheme {
//                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
//                    Greeting(
//                        name = "Android",
//                        modifier = Modifier.padding(innerPadding)
//                    )
//                }
//            }
//        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        Log.d("ShareIntent", "handleIntent called with action: ${intent?.action}")
        when (intent?.action) {
            "CREATE_MEMO_FROM_WIDGET" -> {
                val templateText = intent.getStringExtra("EXTRA_TEMPLATE_TEXT")
                Log.d("ShareIntent", "Widget template: $templateText")
                memoViewModel.onWidgetTapped(templateText)
            }
            Intent.ACTION_SEND -> {
                Log.d("ShareIntent", "Intent type: ${intent.type}")
                if (intent.type == "text/plain") {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                    val sharedSubject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
                    // EXTRA_TITLEは標準の定数がないため、文字列で取得（Pocket Castなどが使用）
                    val sharedTitle = intent.getStringExtra("android.intent.extra.TITLE")
                    
                    Log.d("ShareIntent", "EXTRA_TEXT: $sharedText")
                    Log.d("ShareIntent", "EXTRA_SUBJECT: $sharedSubject")
                    Log.d("ShareIntent", "EXTRA_TITLE: $sharedTitle")
                    
                    // すべてのIntentのextraを確認
                    intent.extras?.keySet()?.forEach { key ->
                        val value = intent.extras?.get(key)
                        Log.d("ShareIntent", "Intent extra - $key: $value")
                    }
                    
                    // タイトルとURLを組み合わせてテキストを作成
                    val combinedText = buildString {
                        // タイトルの優先順位: EXTRA_SUBJECT > EXTRA_TITLE
                        val title = when {
                            !sharedSubject.isNullOrBlank() -> {
                                Log.d("ShareIntent", "Using EXTRA_SUBJECT as title: $sharedSubject")
                                sharedSubject
                            }
                            !sharedTitle.isNullOrBlank() -> {
                                Log.d("ShareIntent", "Using EXTRA_TITLE as title: $sharedTitle")
                                sharedTitle
                            }
                            else -> null
                        }
                        
                        if (!title.isNullOrBlank()) {
                            append(title)
                            if (!sharedText.isNullOrBlank()) {
                                append("\n")
                            }
                        }
                        if (!sharedText.isNullOrBlank()) {
                            append(sharedText)
                        }
                    }
                    
                    Log.d("ShareIntent", "Final combined text: $combinedText")
                    if (combinedText.isNotBlank()) {
                        memoViewModel.onShareIntentReceived(combinedText)
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    HandyMemoTheme {
        Greeting("Android")
    }
}