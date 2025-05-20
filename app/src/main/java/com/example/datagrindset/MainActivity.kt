package com.example.datagrindset

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast // For simple error messages
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect // Required for LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel // For viewModel() delegate
import com.example.datagrindset.ui.theme.DataGrindsetTheme
import android.net.Uri // Ensure this is imported
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.lifecycle.ViewModelProvider

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DataGrindsetTheme {
                // Use the viewModel() delegate for easier ViewModel instantiation
                val fileManagerViewModel: FileManagerViewModel = viewModel(
                    factory = FileManagerViewModelFactory(applicationContext) // Create a simple factory
                )

                val openDocumentLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri: Uri? ->
                    uri?.let { fileManagerViewModel.uploadFile(it) }
                }

                // Collect the openFileEvent
                LaunchedEffect(Unit) { // Use Unit or a key that changes when you want to re-launch
                    fileManagerViewModel.openFileEvent.collect { details ->
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(details.uri, details.mimeType)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        try {
                            startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(this@MainActivity, "Нет приложения для открытия файла", Toast.LENGTH_LONG).show()
                        }
                    }
                }

                // Collect snackbar messages from ViewModel
                LaunchedEffect(Unit) {
                    fileManagerViewModel.snackbarMessage.collect { message ->
                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                        // If you are using Compose SnackbarHost, you'd show it here
                    }
                }


                FileManagerScreen(
                    viewModel = fileManagerViewModel,
                    onPickFile = {
                        openDocumentLauncher.launch(arrayOf("*/*")) // Launch with a general MIME type
                    }
                )
            }
        }
    }
}

// Simple ViewModelFactory for FileManagerViewModel
// You can place this in the same file or a separate file


class FileManagerViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FileManagerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FileManagerViewModel(context.applicationContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}