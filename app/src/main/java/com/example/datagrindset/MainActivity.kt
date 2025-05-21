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
import androidx.lifecycle.viewmodel.compose.viewModel // For viewModel() delegate
import com.example.datagrindset.ui.theme.DataGrindsetTheme
import android.net.Uri // Ensure this is imported
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.lifecycle.ViewModelProvider
import com.example.datagrindset.viewmodel.FileManagerViewModel




import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.datagrindset.ui.LocalFileManagerScreen // Your new screen
import com.example.datagrindset.ui.theme.DataGrindsetTheme
import com.example.datagrindset.viewmodel.LocalFileManagerViewModel
import com.example.datagrindset.viewmodel.LocalFileManagerViewModelFactory // If you use the factory



import androidx.activity.compose.setContent

import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.datagrindset.ui.LocalFileManagerScreen
import com.example.datagrindset.ui.theme.DataGrindsetTheme


class MainActivity : ComponentActivity() {

    private val viewModel: LocalFileManagerViewModel by viewModels {
        LocalFileManagerViewModelFactory(application)
    }

    // Launcher for selecting a directory tree
    private val openDirectoryLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            // Persist read permissions. This is crucial for long-term access.
            // The ViewModel will also call this, but doing it here immediately is also fine.
            try {
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                viewModel.setRootTreeUri(it)
            } catch (e: SecurityException) {
                // Handle the case where permissions could not be taken.
                // Maybe show a toast or log an error.
                // For now, the ViewModel will also handle setting its state to null if this fails.
                viewModel.setRootTreeUri(it) // Let ViewModel try and handle error display if needed
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            setContent {
                DataGrindsetTheme {
                    val rootTreeUriState by viewModel.rootTreeUri.collectAsStateWithLifecycle() // Renamed for clarity
                    val directoryEntries by viewModel.directoryEntries.collectAsStateWithLifecycle()
                    val searchText by viewModel.searchText.collectAsStateWithLifecycle()
                    val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()
                    val currentPathDisplay by viewModel.currentPathDisplay.collectAsStateWithLifecycle()
                    // Optional: Add a state for canNavigateUp if you implement it in ViewModel
                    // val canNavigateUp by viewModel.canNavigateUp.collectAsStateWithLifecycle()


                    // CORRECTED CALL TO LocalFileManagerScreen:
                    LocalFileManagerScreen(
                        rootUriSelected = rootTreeUriState != null, // Correct: using the collected state
                        currentPath = currentPathDisplay,
                        entries = directoryEntries,
                        searchText = searchText,
                        onSearchTextChanged = viewModel::onSearchTextChanged,
                        currentSortOption = sortOption,
                        onSortOptionSelected = viewModel::onSortOptionSelected,
                        onSelectRootDirectoryClicked = { // This is the new "add file/folder" equivalent
                            openDirectoryLauncher.launch(null)
                        },
                        onNavigateToFolder = viewModel::navigateTo,
                        onNavigateUp = viewModel::navigateUp,
                        // onCanNavigateUp = canNavigateUp, // Pass this if you add it
                        onProcessFile = viewModel::processFile,
                        onDeleteEntry = viewModel::deleteEntry // Correct: using the new parameter name
                    )

                }
            }
        }
    }
}


//
//class MainActivity : ComponentActivity() {
//
//    // Use the viewModels delegate with a factory
//    private val viewModel: LocalFileManagerViewModel by viewModels {
//        LocalFileManagerViewModelFactory(application)
//    }
//
//    // ActivityResultLauncher for picking files
//    private val filePickerLauncher = registerForActivityResult(
//        ActivityResultContracts.OpenDocument() // Or .GetContent() if you don't need persistent access post-reboot for the original URI
//    ) { uri ->
//        uri?.let {
//            // Grant persistent read permission if needed, especially for OpenDocument
//            // contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
//            viewModel.addFile(it)
//        }
//    }
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContent {
//            DataGrindsetTheme {
//                val files by viewModel.files.collectAsStateWithLifecycle()
//                val searchText by viewModel.searchText.collectAsStateWithLifecycle()
//                val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()
//
//                LocalFileManagerScreen(
//                    files = files,
//                    searchText = searchText,
//                    onSearchTextChanged = viewModel::onSearchTextChanged,
//                    currentSortOption = sortOption,
//                    onSortOptionSelected = viewModel::onSortOptionSelected,
//                    onAddFileClicked = {
//                        // Launch the file picker
//                        // You can specify MIME types here, e.g., arrayOf("text/csv", "application/json")
//                        filePickerLauncher.launch(arrayOf("*/*"))
//                    },
//                    onProcessFile = viewModel::processFile,
//                    onDeleteFile = viewModel::deleteFile
//                )
//            }
//        }
//    }
//}






//
//class MainActivity : ComponentActivity() {
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContent {
//            DataGrindsetTheme {
//                // Use the viewModel() delegate for easier ViewModel instantiation
//                val fileManagerViewModel: FileManagerViewModel = viewModel(
//                    factory = FileManagerViewModelFactory(applicationContext) // Create a simple factory
//                )
//
//                val openDocumentLauncher = rememberLauncherForActivityResult(
//                    contract = ActivityResultContracts.OpenDocument()
//                ) { uri: Uri? ->
//                    uri?.let { fileManagerViewModel.uploadFile(it) }
//                }
//
//                // Collect the openFileEvent
//                LaunchedEffect(Unit) { // Use Unit or a key that changes when you want to re-launch
//                    fileManagerViewModel.openFileEvent.collect { details ->
//                        val intent = Intent(Intent.ACTION_VIEW).apply {
//                            setDataAndType(details.uri, details.mimeType)
//                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
//                        }
//                        try {
//                            startActivity(intent)
//                        } catch (e: ActivityNotFoundException) {
//                            Toast.makeText(this@MainActivity, "Нет приложения для открытия файла", Toast.LENGTH_LONG).show()
//                        }
//                    }
//                }
//
//                // Collect snackbar messages from ViewModel
//                LaunchedEffect(Unit) {
//                    fileManagerViewModel.snackbarMessage.collect { message ->
//                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
//                        // If you are using Compose SnackbarHost, you'd show it here
//                    }
//                }
//
//
//                FileManagerScreen(
//                    viewModel = fileManagerViewModel,
//                    onPickFile = {
//                        openDocumentLauncher.launch(arrayOf("*/*")) // Launch with a general MIME type
//                    }
//                )
//            }
//        }
//    }
//}
//
//// Simple ViewModelFactory for FileManagerViewModel
//// You can place this in the same file or a separate file
//
//
//class FileManagerViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
//    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
//        if (modelClass.isAssignableFrom(FileManagerViewModel::class.java)) {
//            @Suppress("UNCHECKED_CAST")
//            return FileManagerViewModel(context.applicationContext) as T
//        }
//        throw IllegalArgumentException("Unknown ViewModel class")
//    }
//}