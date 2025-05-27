package com.example.datagrindset

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import com.example.datagrindset.viewmodel.LocalFileManagerViewModel
import com.example.datagrindset.viewmodel.LocalFileManagerViewModelFactory
import java.net.URLDecoder
import androidx.core.net.toUri
import kotlinx.coroutines.flow.collectLatest


class MainActivity : ComponentActivity() {

    private val localFileManagerViewModel: LocalFileManagerViewModel by viewModels {
        LocalFileManagerViewModelFactory(application)
    }

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

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            DataGrindsetTheme {
                val navController = rememberNavController()

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

                NavHost(navController = navController, startDestination = "fileManager") {
                    composable("fileManager") {
                        val rootTreeUri by localFileManagerViewModel.rootTreeUri.collectAsStateWithLifecycle()
                        val currentFolderUri by localFileManagerViewModel.currentFolderUri.collectAsStateWithLifecycle() // For paste target
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
                        val selectedItemsCount by localFileManagerViewModel.selectedItemsCount.collectAsStateWithLifecycle()
                        val itemDetailsToShow by localFileManagerViewModel.showItemDetailsDialog.collectAsStateWithLifecycle()
                        val showCreateFolderDialog by localFileManagerViewModel.showCreateFolderDialog.collectAsStateWithLifecycle()
                        val clipboardUris by localFileManagerViewModel.clipboardUris.collectAsStateWithLifecycle()


                        LocalFileManagerScreen(
                            navController = navController,
                            rootUriIsSelected = rootTreeUri != null,
                            currentDirectoryUri = currentFolderUri ?: rootTreeUri,
                            canNavigateUp = canNavigateUp,
                            currentPath = currentPath,
                            entries = entries,
                            fileProcessingStatusMap = fileProcessingStatusMap,
                            searchText = searchText,
                            onSearchTextChanged = localFileManagerViewModel::onSearchTextChanged, // Correctly wired
                            currentSortOption = currentSortOption,
                            onSortOptionSelected = localFileManagerViewModel::onSortOptionSelected,
                            onSelectRootDirectoryClicked = localFileManagerViewModel::requestSelectRootDirectory,
                            onNavigateToFolder = localFileManagerViewModel::navigateTo,
                            onNavigateUp = localFileManagerViewModel::navigateUp,
                            navigateToAnalysisTarget = navigateToAnalysisTarget,
                            onDidNavigateToAnalysisScreen = { route -> // Corrected lambda
                                navController.navigate(route)
                                localFileManagerViewModel.didNavigateToAnalysisScreen() // Reset trigger
                            },
                            suggestExternalAppForFile = suggestExternalAppForFile,
                            onDidAttemptToOpenWithExternalApp = localFileManagerViewModel::didAttemptToOpenWithExternalApp,
                            onPrepareFileForAnalysis = localFileManagerViewModel::prepareFileForAnalysis, // Correctly wired
                            isSelectionModeActive = isSelectionModeActive,
                            selectedItems = selectedItems,
                            selectedItemsCount = selectedItemsCount,
                            onToggleItemSelected = localFileManagerViewModel::toggleItemSelected,
                            onEnterSelectionMode = localFileManagerViewModel::enterSelectionMode,
                            onExitSelectionMode = localFileManagerViewModel::exitSelectionMode,
                            onSelectAll = localFileManagerViewModel::selectAllInCurrentDirectory,
                            onDeselectAll = localFileManagerViewModel::deselectAll,
                            onShareSelected = localFileManagerViewModel::shareSelectedItems,
                            onCutSelected = localFileManagerViewModel::cutSelectedToClipboard,
                            onCopySelected = localFileManagerViewModel::copySelectedToClipboard,
                            onArchiveSelected = localFileManagerViewModel::onArchiveSelected,
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
                            onInitiateMoveExternal = localFileManagerViewModel::initiateSelectExternalItemsToMove
                        )
                    }

                    composable("txtAnalysisScreen/{fileUri}") { backStackEntry ->
                        val encodedFileUriString = backStackEntry.arguments?.getString("fileUri")
                        if (encodedFileUriString != null) {
                            val fileUri = encodedFileUriString.toUri() // No need to decode if already from Uri.encode
                            TxtFileAnalysisScreen(navController = navController, fileUri = fileUri)
                        } else { Text("Error: TXT file URI not provided.") }
                    }
                    composable(
                        "csvAnalysisScreen/{fileUri}/{fileName}",
                        arguments = listOf(navArgument("fileUri") { type = NavType.StringType }, navArgument("fileName") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val fileUriString = backStackEntry.arguments?.getString("fileUri") ?: return@composable
                        val fileName = backStackEntry.arguments?.getString("fileName") ?: return@composable
                        // URLDecoder.decode might not be needed if Uri.encode was used for both parts.
                        // However, if fileName can have special chars not handled by path segment encoding, it might be.
                        // For safety, let's assume Uri.encode was sufficient.
                        val fileUri = fileUriString.toUri() // Uri.decode might be needed if complex encoding was done
                        val decodedFileName = URLDecoder.decode(fileName, "UTF-8") // Filenames often need decoding
                        val csvViewModel: CsvFileViewModel = viewModel(factory = CsvFileViewModelFactory(application, fileUri, decodedFileName))
                        CsvFileAnalysisScreen(navController, fileUri, csvViewModel)
                    }
                    composable("settings") {
                        SettingsScreen(
                            navController = navController,
                            onLanguageSelected = { langCode ->
                                LocaleHelper.persistUserChoice(this@MainActivity, langCode)
                                val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply { addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK) }
                                if (intent != null) { finishAffinity(); startActivity(intent) } else { recreate() }
                            },
                            currentLanguageCode = LocaleHelper.getLanguage(this@MainActivity)
                        )
                    }
                }
            }
        }
    }
}