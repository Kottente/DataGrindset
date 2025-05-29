package com.example.datagrindset

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.datagrindset.ui.CsvFileAnalysisScreen
import com.example.datagrindset.ui.LocalFileManagerScreen
import com.example.datagrindset.ui.SettingsScreen
import com.example.datagrindset.ui.TxtFileAnalysisScreen
import com.example.datagrindset.ui.theme.DataGrindsetTheme
import com.example.datagrindset.viewmodel.CsvFileViewModel
import com.example.datagrindset.viewmodel.CsvFileViewModelFactory
import com.example.datagrindset.viewmodel.DirectoryEntry
import com.example.datagrindset.viewmodel.LocalFileManagerViewModel
import com.example.datagrindset.viewmodel.LocalFileManagerViewModelFactory
import java.net.URLDecoder
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.example.datagrindset.ui.LoginScreen
import com.example.datagrindset.ui.SignUpScreen
import com.example.datagrindset.viewmodel.AuthViewModel
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import kotlinx.coroutines.flow.collectLatest


class MainActivity : ComponentActivity() {

    private val localFileManagerViewModel: LocalFileManagerViewModel by viewModels {
        LocalFileManagerViewModelFactory(application)
    }
    private val authViewModel: AuthViewModel by viewModels()

    private val openDirectoryLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { localFileManagerViewModel.setRootTreeUri(it) }
    }

    private val openMultipleDocumentsLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            localFileManagerViewModel.moveUrisToCurrentDirectory(uris)
        }
    }

    private var pendingZipToExtract: DirectoryEntry.FileEntry? = null
    private val openDirectoryForExtractionLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { targetDirUri ->
            pendingZipToExtract?.let { zipFileEntry ->
                Log.d("MainActivity", "Target directory for extraction selected: $targetDirUri for ${zipFileEntry.name}")
                localFileManagerViewModel.extractArchiveToSelectedDirectory(zipFileEntry, targetDirUri)
                pendingZipToExtract = null
            }
        }
    }


    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this) // Ensure Firebase is initialized
        val firebaseAppCheck = FirebaseAppCheck.getInstance()
        firebaseAppCheck.installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance()
        )
        setContent {
            DataGrindsetTheme {
                val navController = rememberNavController()
                val currentUser by authViewModel.currentUser.collectAsState()

                // Determine start destination based on auth state
                val startDestination = if (currentUser == null && !intent.getBooleanExtra("NAVIGATE_TO_FILE_MANAGER_GUEST", false) ) "login" else "fileManager"

                LaunchedEffect(Unit) {
                    localFileManagerViewModel.launchDirectoryPickerEvent.collectLatest {
                        openDirectoryLauncher.launch(null)
                    }
                }
                LaunchedEffect(Unit) {
                    localFileManagerViewModel.launchSystemFilePickerForMoveEvent.collectLatest {
                        openMultipleDocumentsLauncher.launch(arrayOf("*/*"))
                    }
                }
                LaunchedEffect(Unit) {
                    localFileManagerViewModel.toastMessageEvent.collectLatest { message ->
                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                    }
                }
                LaunchedEffect(Unit) {
                    localFileManagerViewModel.launchDirectoryPickerForExtractionEvent.collectLatest { zipFileEntry ->
                        pendingZipToExtract = zipFileEntry
                        Log.d("MainActivity", "Launching directory picker for extraction of ${zipFileEntry.name}")
                        openDirectoryForExtractionLauncher.launch(null)
                    }
                }


                NavHost(navController = navController, startDestination = "fileManager") {
                    composable("login") {
                        LoginScreen(navController = navController, authViewModel = authViewModel)
                    }
                    composable("signup") {
                        SignUpScreen(navController = navController, authViewModel = authViewModel)
                    }
                    composable("fileManager") {
                        val rootTreeUri by localFileManagerViewModel.rootTreeUri.collectAsStateWithLifecycle()
                        val currentFolderUri by localFileManagerViewModel.currentFolderUri.collectAsStateWithLifecycle()
                        val canNavigateUp by localFileManagerViewModel.canNavigateUp.collectAsStateWithLifecycle()
                        val currentPath by localFileManagerViewModel.currentPathDisplay.collectAsStateWithLifecycle()
                        val entries by localFileManagerViewModel.directoryEntries.collectAsStateWithLifecycle()
                        val fileProcessingStatusMap by localFileManagerViewModel.fileProcessingStatusMap.collectAsStateWithLifecycle()
                        val searchText by localFileManagerViewModel.searchText.collectAsStateWithLifecycle()
                        val currentSortOption by localFileManagerViewModel.sortOption.collectAsStateWithLifecycle()
                        val navigateToAnalysisTarget by localFileManagerViewModel.navigateToAnalysisTarget.collectAsStateWithLifecycle()
                        val suggestExternalAppForFile by localFileManagerViewModel.suggestExternalAppForFile.collectAsStateWithLifecycle()
                        val isSelectionModeActive by localFileManagerViewModel.isSelectionModeActive.collectAsStateWithLifecycle()
                        val selectedItems by localFileManagerViewModel.selectedItems.collectAsStateWithLifecycle()
                        val selectedItemsUris by localFileManagerViewModel.selectedItemsUris.collectAsStateWithLifecycle()
                        val selectedItemsCount by localFileManagerViewModel.selectedItemsCount.collectAsStateWithLifecycle()
                        val itemDetailsToShow by localFileManagerViewModel.showItemDetailsDialog.collectAsStateWithLifecycle()
                        val showCreateFolderDialog by localFileManagerViewModel.showCreateFolderDialog.collectAsStateWithLifecycle()
                        val clipboardUris by localFileManagerViewModel.clipboardUris.collectAsStateWithLifecycle()
                        val viewType by localFileManagerViewModel.viewType.collectAsStateWithLifecycle()
                        val showArchiveNameDialog by localFileManagerViewModel.showArchiveNameDialog.collectAsStateWithLifecycle()
                        val showExtractOptionsDialog by localFileManagerViewModel.showExtractOptionsDialog.collectAsStateWithLifecycle()


                        LocalFileManagerScreen(
                            navController = navController,
                            rootUriIsSelected = rootTreeUri != null,
                            currentDirectoryUri = currentFolderUri ?: rootTreeUri,
                            canNavigateUp = canNavigateUp,
                            currentPath = currentPath,
                            entries = entries,
                            fileProcessingStatusMap = fileProcessingStatusMap,
                            searchText = searchText,
                            onSearchTextChanged = localFileManagerViewModel::onSearchTextChanged,
                            currentSortOption = currentSortOption,
                            onSortOptionSelected = localFileManagerViewModel::onSortOptionSelected,
                            onSelectRootDirectoryClicked = localFileManagerViewModel::requestSelectRootDirectory,
                            onNavigateToFolder = localFileManagerViewModel::navigateTo,
                            onNavigateUp = localFileManagerViewModel::navigateUp,
                            navigateToAnalysisTarget = navigateToAnalysisTarget,
                            onDidNavigateToAnalysisScreen = { route ->
                                Log.d("MainActivity", "Navigating to: $route")
                                navController.navigate(route)
                                localFileManagerViewModel.didNavigateToAnalysisScreen()
                            },
                            suggestExternalAppForFile = suggestExternalAppForFile,
                            onDidAttemptToOpenWithExternalApp = localFileManagerViewModel::didAttemptToOpenWithExternalApp,
                            onPrepareFileForAnalysis = localFileManagerViewModel::prepareFileForAnalysis,
                            isSelectionModeActive = isSelectionModeActive,
                            selectedItems = selectedItems,
                            selectedItemsUris = selectedItemsUris,
                            selectedItemsCount = selectedItemsCount,
                            onToggleItemSelected = localFileManagerViewModel::toggleItemSelected,
                            onEnterSelectionMode = localFileManagerViewModel::enterSelectionMode,
                            onExitSelectionMode = localFileManagerViewModel::exitSelectionMode,
                            onSelectAll = localFileManagerViewModel::selectAllInCurrentDirectory,
                            onDeselectAll = localFileManagerViewModel::deselectAll,
                            onShareSelected = localFileManagerViewModel::shareSelectedItems,
                            onCutSelected = localFileManagerViewModel::cutSelectedToClipboard,
                            onCopySelected = localFileManagerViewModel::copySelectedToClipboard,
                            onRequestArchiveSelectedItems = localFileManagerViewModel::requestArchiveSelectedItems,
                            onRequestExtractArchive = localFileManagerViewModel::requestExtractArchive,
                            onOpenSelectedWithAnotherApp = localFileManagerViewModel::openSelectedFileWithAnotherApp,
                            onShowItemDetails = localFileManagerViewModel::getItemDetailsForSelected,
                            onDeleteSelectedItems = localFileManagerViewModel::deleteSelectedItems,
                            itemDetailsToShow = itemDetailsToShow,
                            onDismissItemDetails = localFileManagerViewModel::dismissItemDetailsDialog,
                            showCreateFolderDialog = showCreateFolderDialog,
                            onRequestShowCreateFolderDialog = localFileManagerViewModel::requestShowCreateFolderDialog,
                            onDismissCreateFolderDialog = localFileManagerViewModel::dismissCreateFolderDialog,
                            onCreateFolder = localFileManagerViewModel::createFolderInCurrentDirectory,
                            clipboardHasItems = clipboardUris.isNotEmpty(),
                            onPaste = localFileManagerViewModel::pasteFromClipboard,
                            onInitiateMoveExternal = localFileManagerViewModel::initiateSelectExternalItemsToMove,
                            viewType = viewType,
                            onToggleViewType = localFileManagerViewModel::toggleViewType,
                            showArchiveNameDialog = showArchiveNameDialog,
                            onDismissArchiveNameDialog = localFileManagerViewModel::dismissArchiveNameDialog,
                            onConfirmArchiveCreation = localFileManagerViewModel::confirmArchiveCreation,
                            showExtractOptionsDialog = showExtractOptionsDialog,
                            onDismissExtractOptionsDialog = localFileManagerViewModel::dismissExtractOptionsDialog,
                            onExtractToCurrentFolder = localFileManagerViewModel::extractArchiveToCurrentFolder,
                            onInitiateExtractToAnotherFolder = localFileManagerViewModel::initiateExtractArchiveToAnotherFolder
                        )
                    }

                    composable("txtAnalysisScreen/{fileUri}") { backStackEntry ->
                        val encodedFileUriString = backStackEntry.arguments?.getString("fileUri")
                        if (encodedFileUriString != null) {
                            val fileUri = Uri.decode(encodedFileUriString).toUri()
                            TxtFileAnalysisScreen(navController = navController, fileUri = fileUri)
                        } else { Text("Error: TXT file URI not provided.") }
                    }
                    composable(
                        "csvAnalysisScreen/{fileUri}/{fileName}",
                        arguments = listOf(
                            navArgument("fileUri") { type = NavType.StringType },
                            navArgument("fileName") { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val fileUriString = backStackEntry.arguments?.getString("fileUri") ?: return@composable
                        val encodedFileName = backStackEntry.arguments?.getString("fileName") ?: return@composable
                        val fileUri = Uri.decode(fileUriString).toUri()
                        val fileName = URLDecoder.decode(encodedFileName, "UTF-8")
                        val csvViewModel: CsvFileViewModel = viewModel(factory = CsvFileViewModelFactory(application, fileUri, fileName))
                        CsvFileAnalysisScreen(navController, fileUri, csvViewModel)
                    }
                    composable("settings") {
                        SettingsScreen(
                            navController = navController,
                            authViewModel = authViewModel,
                            onLanguageSelected = { langCode ->
                                LocaleHelper.persistUserChoice(this@MainActivity, langCode)
                                //val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply { addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK) }
                                val intent =
                                    packageManager.getLaunchIntentForPackage(packageName)?.apply {
                                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                                        // Preserve guest navigation state if any
                                        if (currentUser == null && startDestination == "fileManager") {
                                            putExtra("NAVIGATE_TO_FILE_MANAGER_GUEST", true)
                                        }
                                    }

                                if (intent != null) {
                                    finishAffinity(); startActivity(intent)
                                } else {
                                    recreate()
                                }
                            },
                            currentLanguageCode = LocaleHelper.getLanguage(this@MainActivity)
                        )
                    }
                }
            }
        }

    }
    override fun onNewIntent(intent: Intent) { // Intent should be non-nullable
        super.onNewIntent(intent)
        setIntent(intent) // Update the activity's intent

        // Logic for handling NAVIGATE_TO_FILE_MANAGER_GUEST after activity recreation (e.g., language change)
        // This will be re-evaluated in onCreate with the new intent.
        // If you need more immediate navigation changes here, it gets more complex
        // as NavController might not be immediately available or in the right state.
        // For now, relying on onCreate's startDestination logic with the updated intent is simpler.
        if (intent.getBooleanExtra("NAVIGATE_TO_FILE_MANAGER_GUEST", false) && authViewModel.currentUser.value == null) {
            Log.d("MainActivity", "onNewIntent: Guest navigation flag received. Activity will restart and check in onCreate.")
            // Potentially recreate or re-evaluate navigation if needed immediately,
            // but often just letting onCreate handle it with the new intent is fine.
            // For instance, if the NavHost is already set up, you might navigate:
            // findNavController(R.id.your_nav_host_fragment_id_if_using_fragments).navigate(R.id.fileManagerScreen)
            // However, with Jetpack Compose Navigation, this is typically handled by recomposition
            // based on state that NavHost observes, or by restarting the activity cleanly.
        }
    }
}