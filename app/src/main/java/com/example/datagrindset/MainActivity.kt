package com.example.datagrindset

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHost
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.datagrindset.ui.LocalFileManagerScreen
import com.example.datagrindset.ui.theme.DataGrindsetTheme
import com.example.datagrindset.viewmodel.LocalFileManagerViewModel
import com.example.datagrindset.viewmodel.LocalFileManagerViewModelFactory
// Import your analysis screens here when they are created, e.g.:
// import com.example.datagrindset.ui.TxtFileAnalysisScreen
// import com.example.datagrindset.ui.CsvFileAnalysisScreen

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
            try {
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION // Optional: if you ever need to write
                contentResolver.takePersistableUriPermission(it, takeFlags)
                viewModel.setRootTreeUri(it)
            } catch (e: SecurityException) {
                // Handle the case where permissions could not be taken.
                // The ViewModel will also handle setting its state appropriately if this fails.
                viewModel.setRootTreeUri(it) // Let ViewModel try and handle error display if needed
                // You might want to show a toast to the user here indicating permission failure.
                // e.g., Toast.makeText(this, "Failed to get folder permissions", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            DataGrindsetTheme {
                val navController = rememberNavController() // For Jetpack Compose Navigation

                // Collecting all necessary states from the ViewModel
                val rootTreeUri by viewModel.rootTreeUri.collectAsStateWithLifecycle()
                val canNavigateUp by viewModel.canNavigateUp.collectAsStateWithLifecycle()
                val currentPathDisplay by viewModel.currentPathDisplay.collectAsStateWithLifecycle()
                val directoryEntries by viewModel.directoryEntries.collectAsStateWithLifecycle()
                val fileProcessingStatusMap by viewModel.fileProcessingStatusMap.collectAsStateWithLifecycle()
                val searchText by viewModel.searchText.collectAsStateWithLifecycle()
                val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()
                val navigateToAnalysisTarget by viewModel.navigateToAnalysisTarget.collectAsStateWithLifecycle()

                // Navigation Host for managing different screens
                NavHost(navController = navController, startDestination = "fileManager") {
                    composable("fileManager") {
                        LocalFileManagerScreen(
                            rootUriSelected = rootTreeUri != null,
                            canNavigateUp = canNavigateUp,
                            currentPath = currentPathDisplay,
                            entries = directoryEntries,
                            fileProcessingStatusMap = fileProcessingStatusMap,
                            searchText = searchText,
                            onSearchTextChanged = viewModel::onSearchTextChanged,
                            currentSortOption = sortOption,
                            onSortOptionSelected = viewModel::onSortOptionSelected,
                            onSelectRootDirectoryClicked = {
                                openDirectoryLauncher.launch(null) // Initial URI can be null
                            },
                            onNavigateToFolder = viewModel::navigateTo,
                            onNavigateUp = viewModel::navigateUp,
                            onPrepareFileForAnalysis = viewModel::prepareFileForAnalysis,
                            onDeleteEntry = viewModel::deleteEntry,
                            navigateToAnalysisTarget = navigateToAnalysisTarget,
                            onDidNavigateToAnalysisScreen = viewModel::didNavigateToAnalysisScreen,
                            navController = navController // Pass NavController for navigation actions
                        )
                    }

                    // Example destination for TXT file analysis
                    composable("txtAnalysisScreen/{fileUri}") { backStackEntry ->
                        val fileUriString = backStackEntry.arguments?.getString("fileUri")
                        if (fileUriString != null) {
                            val decodedUri = Uri.parse(Uri.decode(fileUriString))
                            // Replace with your actual TxtFileAnalysisScreen composable
                            // TxtFileAnalysisScreen(navController = navController, fileUri = decodedUri)
                            // For now, a placeholder:
                            // Text("Placeholder for TXT Analysis Screen. URI: $decodedUri")
                        } else {
                            // Handle error: fileUri not provided
                            // Text("Error: TXT file URI not provided.")
                        }
                    }

                    // Example destination for CSV file analysis
                    composable("csvAnalysisScreen/{fileUri}") { backStackEntry ->
                        val fileUriString = backStackEntry.arguments?.getString("fileUri")
                        if (fileUriString != null) {
                            val decodedUri = Uri.parse(Uri.decode(fileUriString))
                            // Replace with your actual CsvFileAnalysisScreen composable
                            // CsvFileAnalysisScreen(navController = navController, fileUri = decodedUri)
                            // For now, a placeholder:
                            // Text("Placeholder for CSV Analysis Screen. URI: $decodedUri")
                        } else {
                            // Handle error: fileUri not provided
                            // Text("Error: CSV file URI not provided.")
                        }
                    }
                    // Add more destinations for other analysis screens as needed
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