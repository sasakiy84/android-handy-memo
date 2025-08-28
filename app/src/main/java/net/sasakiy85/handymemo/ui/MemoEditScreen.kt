package net.sasakiy85.handymemo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.SubdirectoryArrowLeft
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoEditScreen(
    onSave: (content: String) -> Unit,
    onSaveAndClose: (content: String) -> Unit,
    onToList: () -> Unit,
    onClear: () -> Unit,
    initialText: String?,
) {
    var text by remember {
        mutableStateOf(initialText ?: "")
    }
    val focusRequester = remember { FocusRequester() }

    Scaffold(
        floatingActionButton = {
            Column(
                modifier = Modifier.imePadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    FloatingActionButton(
                        onClick = { onSave(text) },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Icon(Icons.Default.SubdirectoryArrowLeft, contentDescription = "Save")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    FloatingActionButton(
                        onClick = onToList,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Cancel")
                    }
                    Spacer(Modifier.width(16.dp))
                    FloatingActionButton(
                        onClick = {
                            text = ""
                            onClear()
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Icon(Icons.Default.Autorenew, contentDescription = "Clear")
                    }
                    Spacer(Modifier.width(16.dp))
                    FloatingActionButton(onClick = { onSaveAndClose(text) }) {
                        Icon(Icons.Default.Done, contentDescription = "Save and Close")
                    }
                }
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Text("新しいメモ")
                },
                navigationIcon = {
                    IconButton(onClick = onToList) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        onSave(text)
                    }) {
                        Icon(Icons.Default.Done, contentDescription = "Save")
                    }
                }
            )
        }
    ) {
        paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.imePadding().fillMaxSize().focusRequester(focusRequester),
                label = {
                    Text("Content")
                }
            )
        }
    }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}