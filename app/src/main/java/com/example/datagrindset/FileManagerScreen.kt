package com.example.datagrindset

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.runtime.collectAsState // Ensure this is imported
import androidx.compose.material.CircularProgressIndicator // For loading indicator
import androidx.compose.ui.Alignment // For centering loader
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
    val isLoadingFile by viewModel.isLoadingFile.collectAsState() // Collect loading state for file open
    val snackbarHostState = remember { SnackbarHostState() }

    // Collect snackbar messages (if not handled in MainActivity)
    // This is a more common pattern if FileManagerScreen owns the SnackbarHost
    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }


    Box(modifier = Modifier.fillMaxSize()) { // Use Box to overlay loader
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("DataGrindSet — файловый менеджер", style = MaterialTheme.typography.h5)
            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onPickFile,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isUploading && !isLoadingFile // Disable if uploading or opening file
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
                        onOpen = { if (!isLoadingFile) viewModel.openFile(file) }, // Prevent multiple clicks
                        onDelete = { viewModel.deleteFile(file) },
                        isEnabled = !isLoadingFile // Disable item interaction while opening another file
                    )
                }
            }
            SnackbarHost(hostState = snackbarHostState) // Ensure this is inside the Column or Box correctly
        }

        if (isLoadingFile) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
fun FileItem(
    file: CloudFile,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    isEnabled: Boolean = true // Add isEnabled parameter
) {
    Card(
        elevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(enabled = isEnabled, onClick = onOpen) // Apply isEnabled to clickable
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                file.name,
                modifier = Modifier.weight(1f)
                // Clickable is now on the Card
            )
            Spacer(modifier = Modifier.width(10.dp))
            IconButton(onClick = onDelete, enabled = isEnabled) { // Apply isEnabled
                Icon(Icons.Default.Delete, contentDescription = "Удалить")
            }
        }
    }
}