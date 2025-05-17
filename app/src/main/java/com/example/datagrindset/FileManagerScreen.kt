package com.example.datagrindset

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete

@Composable
fun FileManagerScreen(
    viewModel: FileManagerViewModel,
    onPickFile: () -> Unit
) {
    val files by viewModel.files.collectAsState()
    val isUploading by viewModel.isUploading.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("DataGrindSet — файловый менеджер", style = MaterialTheme.typography.h5)
        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onPickFile,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isUploading
        ) {
            Text("Загрузить файл в облако")
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (isUploading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(files.size) { i ->
                val file = files[i]
                FileItem(
                    file,
                    onOpen = { viewModel.openFile(file) },
                    onDelete = { viewModel.deleteFile(file) }
                )
            }
        }
        SnackbarHost(hostState = snackbarHostState)
    }

    LaunchedEffect(viewModel.snackbarMessage) {
        viewModel.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }
}

@Composable
fun FileItem(file: CloudFile, onOpen: () -> Unit, onDelete: () -> Unit) {
    Card(
        elevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                file.name,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpen)
            )
            Spacer(modifier = Modifier.width(10.dp))
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Удалить")
            }
        }
    }
}