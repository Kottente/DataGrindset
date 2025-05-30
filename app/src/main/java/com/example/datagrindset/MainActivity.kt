package com.example.datagrindset

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material.Text
import androidx.compose.runtime.collectAsState
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
import com.example.datagrindset.ui.CsvFileAnalysisScreen // Import new screen
import com.example.datagrindset.viewmodel.CsvFileViewModelFactory // Import new factory

import com.example.datagrindset.ui.TxtFileAnalysisScreen
import androidx.core.net.toUri
import androidx.media3.common.util.Log

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.example.datagrindset.ui.SettingsScreen
import com.example.datagrindset.viewmodel.CsvFileViewModel

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
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
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

                val suggestExternalAppForFile by viewModel.suggestExternalAppForFile.collectAsState()
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
                            suggestExternalAppForFile = suggestExternalAppForFile, // Pass the new state
                            onDidAttemptToOpenWithExternalApp = viewModel::didAttemptToOpenWithExternalApp,
                            onDidNavigateToAnalysisScreen = viewModel::didNavigateToAnalysisScreen,
                            navController = navController // Pass NavController for navigation actions

                        )
                    }

                    composable("txtAnalysisScreen/{fileUri}") { backStackEntry ->
                        val encodedFileUriString = backStackEntry.arguments?.getString("fileUri")
                        if (encodedFileUriString != null) {
                            // We encoded it once, so we might need to decode it once if the nav component didn't fully.
                            // However, Uri.parse() can often handle already-decoded or partially encoded strings.
                            // Let's try parsing directly first. If issues persist, explicitly decode.
                            val fileUri = encodedFileUriString.toUri() // Try direct parse
                            // If the above still has issues with encoding, you might try:
                            // val decodedUriString = Uri.decode(encodedFileUriString)
                            // val fileUri = Uri.parse(decodedUriString)
                            TxtFileAnalysisScreen(navController = navController, fileUri = fileUri)
                        } else {
                            // Handle error: fileUri not provided. Maybe navigate back or show error.
                            Text("Error: TXT file URI not provided in navigation arguments.")
                        }
                    }
                    composable(
                        "csvAnalysisScreen/{fileUri}/{fileName}",
                        arguments = listOf(
                            navArgument("fileUri") { type = NavType.StringType },
                            navArgument("fileName") { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val fileUriString = backStackEntry.arguments?.getString("fileUri") ?: return@composable
                        val fileName = backStackEntry.arguments?.getString("fileName") ?: return@composable
                        val fileUri = URLDecoder.decode(fileUriString, "UTF-8").toUri()
                        val csvViewModel: CsvFileViewModel = viewModel(
                            factory = CsvFileViewModelFactory(application, fileUri, fileName)
                        )
                        CsvFileAnalysisScreen(navController, fileUri, csvViewModel)
                    }
                    composable("settings") {
                        SettingsScreen(
                            navController = navController,
                            onLanguageSelected = { langCode ->
                                LocaleHelper.persistUserChoice(this@MainActivity, langCode)
                                // Restart the app
                                val intent = packageManager.getLaunchIntentForPackage(packageName)
                                intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                                if (intent != null) {
                                    finishAffinity() // Finishes all activities in this task
                                    startActivity(intent)
                                } else {
                                    // Fallback or error handling if launch intent is null
                                    recreate() // Try recreate as a fallback
                                }
                            },
                            currentLanguageCode = LocaleHelper.getLanguage(this@MainActivity)
                        )
                    }
                    /*
                    composable("csvAnalysisScreen/{fileUri}") { backStackEntry ->

                        val encodedFileUriString = backStackEntry.arguments?.getString("fileUri")
                        val encodedFileNameString = backStackEntry.arguments?.getString("fileName")
                        val fileUriString = backStackEntry.arguments?.getString("fileUri")
                        if (encodedFileUriString != null && encodedFileNameString != null) {
                            val decodedUriString = URLDecoder.decode(encodedFileUriString, StandardCharsets.UTF_8.toString())
                            val decodedFileNameString = URLDecoder.decode(encodedFileNameString, StandardCharsets.UTF_8.toString())
                            val fileUri = decodedUriString.toUri()

                            //Log.d("MainActivity/Nav", "Navigating to CSV: URI: $fileUri, Name: $decodedFileNameString")

                            val csvViewModel: com.example.datagrindset.viewmodel.CsvFileViewModel = viewModel(
                                factory = CsvFileViewModelFactory(application, fileUri, decodedFileNameString)
                            )
                            CsvFileAnalysisScreen(navController = navController, fileUri = fileUri, viewModel = csvViewModel)
                        } else {
                            Text("Error: CSV file URI or FileName not provided.")
                        }
                    }
                     */
                    // Add more destinations for other analysis screens as needed
                    // composable("csvAnalysisScreen/{fileUri}") { /* ... */ }
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