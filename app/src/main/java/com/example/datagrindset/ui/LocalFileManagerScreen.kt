package com.example.datagrindset.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock // Icon for Secured Space
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MoveDown
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.datagrindset.ProcessingStatus
import com.example.datagrindset.R
import com.example.datagrindset.ui.SortOption
import com.example.datagrindset.ViewType
import com.example.datagrindset.ui.theme.DataGrindsetTheme
import com.example.datagrindset.viewmodel.DirectoryEntry
import com.example.datagrindset.viewmodel.ItemDetails
import com.example.datagrindset.viewmodel.LocalizedSummary
import com.example.datagrindset.LocaleHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.datagrindset.viewmodel.BatchRenameDialogState
import com.example.datagrindset.viewmodel.LocalFileManagerViewModel
import com.example.datagrindset.viewmodel.PerformBatchRenameOptions
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LocalFileManagerScreen(
    navController: NavController,
    rootUriIsSelected: Boolean,
    viewModel: LocalFileManagerViewModel,
    currentDirectoryUri: Uri?,
    canNavigateUp: Boolean,
    currentPath: String,
    entries: List<DirectoryEntry>,
    fileProcessingStatusMap: Map<Uri, Pair<ProcessingStatus, LocalizedSummary?>>,
    searchText: String,
    currentSortOption: SortOption,
    navigateToAnalysisTarget: DirectoryEntry.FileEntry?,
    suggestExternalAppForFile: DirectoryEntry.FileEntry?,
    isSelectionModeActive: Boolean,
    selectedItems: Set<DirectoryEntry>,
    selectedItemsUris: Set<Uri>,
    selectedItemsCount: Int,
    itemDetailsToShow: ItemDetails?,
    showCreateFolderDialog: Boolean,
    clipboardHasItems: Boolean,
    viewType: ViewType,
    showArchiveNameDialog: List<DirectoryEntry>?,
    showExtractOptionsDialog: DirectoryEntry.FileEntry?,
    onSearchTextChanged: (String) -> Unit,
    onSortOptionSelected: (SortOption) -> Unit,
    onSelectRootDirectoryClicked: () -> Unit,
    onNavigateToFolder: (DirectoryEntry.FolderEntry) -> Unit,
    onNavigateUp: () -> Unit,
    onDidNavigateToAnalysisScreen: (String) -> Unit,
    onDidAttemptToOpenWithExternalApp: () -> Unit,
    onPrepareFileForAnalysis: (DirectoryEntry.FileEntry) -> Unit,
    onToggleItemSelected: (DirectoryEntry) -> Unit,
    onEnterSelectionMode: (DirectoryEntry) -> Unit,
    onExitSelectionMode: () -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onShareSelected: () -> Unit,
    onCutSelected: () -> Unit,
    onCopySelected: () -> Unit,
    onRequestArchiveSelectedItems: () -> Unit,
    onRequestExtractArchive: (DirectoryEntry.FileEntry) -> Unit,
    onOpenSelectedWithAnotherApp: () -> Unit,
    onShowItemDetails: () -> Unit,
    onDeleteSelectedItems: () -> Unit,
    onDismissItemDetails: () -> Unit,
    onRequestShowCreateFolderDialog: () -> Unit,
    onDismissCreateFolderDialog: () -> Unit,
    onCreateFolder: (String) -> Unit,
    onPaste: () -> Unit,
    onInitiateMoveExternal: () -> Unit,
    onToggleViewType: () -> Unit,
    onDismissArchiveNameDialog: () -> Unit,
    onConfirmArchiveCreation: (String) -> Unit,
    onDismissExtractOptionsDialog: () -> Unit,
    onExtractToCurrentFolder: () -> Unit,
    onInitiateExtractToAnotherFolder: () -> Unit,
    onNavigateToMySecuredSpace: () -> Unit,
    isUserLoggedIn: Boolean,
    onRequestBatchRename: () -> Unit,
    onDismissBatchRenameDialog: () -> Unit,
    batchRenameDialogState: BatchRenameDialogState?,
    onConfirmBatchRename: (PerformBatchRenameOptions) -> Unit

) {
    var showSearchFieldState by remember { mutableStateOf(false) }
    var showSortAndOptionsKebabMenu by remember { mutableStateOf(false) }
    var showBottomBarMoreOptionsMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var newFolderName by remember { mutableStateOf("") }
    var archiveNameTextState by remember { mutableStateOf("") }

    var batchRenameBaseName by remember { mutableStateOf("") }
    var batchRenameStartNum by remember { mutableStateOf("1") }
    var batchRenameNumDigits by remember { mutableStateOf("0") }
    var batchRenameKeepExt by remember { mutableStateOf(true) }

    val itemDetailsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val openWithButtonTextResolved = stringResource(R.string.lfm_open_with_button)
    val couldNotOpenFileToastMsg = stringResource(id = R.string.lfm_could_not_open_file_toast)
    LaunchedEffect(batchRenameDialogState) {
        batchRenameDialogState?.let {
            batchRenameBaseName = it.baseName
            batchRenameStartNum = it.startNumber
            batchRenameNumDigits = it.numDigits
            batchRenameKeepExt = it.keepExtension
        }
    }
    LaunchedEffect(navigateToAnalysisTarget) {
        navigateToAnalysisTarget?.let { fileEntry ->
            Log.d("LFM_Screen", "navigateToAnalysisTarget changed: ${fileEntry.name}. Current value: $navigateToAnalysisTarget")
            val mimeType = fileEntry.mimeType?.lowercase(Locale.ROOT)
            val fileName = fileEntry.name
            val uriString = fileEntry.uri.toString()
            val isCsv = mimeType in listOf("text/csv", "application/csv", "text/comma-separated-values") || fileName.lowercase(Locale.ROOT).endsWith(".csv")
            val isTxt = mimeType in listOf("text/plain", "text/markdown") || fileName.lowercase(Locale.ROOT).endsWith(".txt") || fileName.lowercase(Locale.ROOT).endsWith(".md")

            val route = when {
                isTxt -> "txtAnalysisScreen/${Uri.encode(uriString)}"
                isCsv -> "csvAnalysisScreen/${Uri.encode(uriString)}/${Uri.encode(fileName)}"
                else -> null
            }
            route?.let {
                Log.d("LFM_Screen", "Constructed route: $it. Calling onDidNavigateToAnalysisScreen.")
                onDidNavigateToAnalysisScreen(it)
            }
        }
    }

    suggestExternalAppForFile?.let { fileEntry ->
        AlertDialog(
            onDismissRequest = { onDidAttemptToOpenWithExternalApp() },
            title = { Text(stringResource(R.string.lfm_unsupported_file_title)) },
            text = { Text(stringResource(R.string.lfm_unsupported_file_message, fileEntry.mimeType ?: "unknown")) },
            confirmButton = {
                TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(fileEntry.uri, fileEntry.mimeType ?: "*/*")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    try {
                        context.startActivity(Intent.createChooser(intent, openWithButtonTextResolved).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                    } catch (e: Exception) {
                        Log.e("LFM_Screen", "Failed ACTION_VIEW for external app: ${e.message}")
                        Toast.makeText(context, String.format(couldNotOpenFileToastMsg, e.localizedMessage ?: "Unknown error"), Toast.LENGTH_LONG).show()
                    }
                    onDidAttemptToOpenWithExternalApp()
                }) { Text(openWithButtonTextResolved) }
            },
            dismissButton = { TextButton(onClick = { onDidAttemptToOpenWithExternalApp() }) { Text(stringResource(R.string.lfm_cancel_button)) } },
            icon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = openWithButtonTextResolved) }
        )
    }

    if (itemDetailsToShow != null) {
        ModalBottomSheet(onDismissRequest = onDismissItemDetails, sheetState = itemDetailsSheetState) {
            ItemDetailsSheetContent(details = itemDetailsToShow, onDismiss = onDismissItemDetails)
        }
    }
    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = onDismissCreateFolderDialog,
            title = { Text(stringResource(R.string.lfm_create_folder_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text(stringResource(R.string.lfm_folder_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newFolderName.isNotBlank()) {
                        onCreateFolder(newFolderName); newFolderName = ""
                    } else { Toast.makeText(context, context.getString(R.string.lfm_empty_folder_name_toast), Toast.LENGTH_SHORT).show() }
                }) { Text(stringResource(R.string.lfm_create_button)) }
            },
            dismissButton = { TextButton(onClick = onDismissCreateFolderDialog) { Text(stringResource(R.string.lfm_cancel_button)) } }
        )
    }

    if (showArchiveNameDialog != null && showArchiveNameDialog.isNotEmpty()) {
        val defaultArchiveNameBase = if (showArchiveNameDialog.size == 1) {
            showArchiveNameDialog.first().name.substringBeforeLast('.')
        } else {
            currentPath.substringAfterLast(" > ", "Archive") // Use current path part as default
        }
        LaunchedEffect(showArchiveNameDialog) { // Re-init if dialog re-opens with different items
            archiveNameTextState = defaultArchiveNameBase
        }

        AlertDialog(
            onDismissRequest = onDismissArchiveNameDialog,
            title = { Text(stringResource(R.string.lfm_archive_name_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = archiveNameTextState,
                    onValueChange = { archiveNameTextState = it },
                    label = { Text(stringResource(R.string.lfm_archive_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (archiveNameTextState.isNotBlank()) {
                        onConfirmArchiveCreation(archiveNameTextState)
                    } else {
                        Toast.makeText(context, context.getString(R.string.lfm_invalid_archive_name), Toast.LENGTH_SHORT).show()
                    }
                }) { Text(stringResource(R.string.lfm_create_button)) }
            },
            dismissButton = {
                TextButton(onClick = onDismissArchiveNameDialog) { Text(stringResource(R.string.lfm_cancel_button)) }
            }
        )
    }

    if (showExtractOptionsDialog != null) {
        AlertDialog(
            onDismissRequest = onDismissExtractOptionsDialog,
            title = {
                Text(stringResource(R.string.lfm_extract_options_dialog_title) + " '${showExtractOptionsDialog.name}'")
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = {
                            onExtractToCurrentFolder()
                            onDismissExtractOptionsDialog()
                        },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) { Text(stringResource(R.string.lfm_extract_to_current_folder_button)) }
                    OutlinedButton(
                        onClick = {
                            onInitiateExtractToAnotherFolder()
                            // onDismissExtractOptionsDialog() // Dismissal handled by picker flow
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.lfm_extract_to_another_folder_button)) }
                }
            },
            confirmButton = {}, // Actions are in the text block
            dismissButton = {
                TextButton(onClick = onDismissExtractOptionsDialog) {
                    Text(stringResource(R.string.lfm_cancel_button))
                }
            }
        )
    }
    if (batchRenameDialogState != null) {
        AlertDialog(
            onDismissRequest = onDismissBatchRenameDialog,
            icon = { Icon(Icons.Filled.DriveFileRenameOutline, null) },
            title = { Text(stringResource(R.string.lfm_batch_rename_dialog_title, batchRenameDialogState.itemsToRenameCount)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = batchRenameBaseName,
                        onValueChange = { batchRenameBaseName = it },
                        label = { Text(stringResource(R.string.lfm_batch_rename_base_name_label)) },
                        placeholder = { Text(stringResource(R.string.lfm_batch_rename_base_name_placeholder))},
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = batchRenameStartNum,
                            onValueChange = { batchRenameStartNum = it.filter { char -> char.isDigit() } },
                            label = { Text(stringResource(R.string.lfm_batch_rename_start_number_label)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = batchRenameNumDigits,
                            onValueChange = { batchRenameNumDigits = it.filter { char -> char.isDigit() } },
                            label = { Text(stringResource(R.string.lfm_batch_rename_num_digits_label)) },
                            placeholder = { Text(stringResource(R.string.lfm_batch_rename_num_digits_placeholder)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                            .clickable { batchRenameKeepExt = !batchRenameKeepExt }
                            .padding(vertical = 8.dp)
                    ) {
                        Checkbox(
                            checked = batchRenameKeepExt,
                            onCheckedChange = { batchRenameKeepExt = it }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.lfm_batch_rename_keep_extension_label))
                    }
                    Text(
                        stringResource(
                            R.string.lfm_batch_rename_example_label,
                                constructBatchRenameExample(
                                batchRenameBaseName,
                                batchRenameStartNum.toIntOrNull() ?: 1,
                                batchRenameNumDigits.toIntOrNull() ?: 0,
                                batchRenameKeepExt
                            )
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val startNum = batchRenameStartNum.toIntOrNull() ?: 1
                    val numDigits = batchRenameNumDigits.toIntOrNull() ?: 0
                    if (numDigits < 0) {
                        Toast.makeText(context, context.getString(R.string.lfm_batch_rename_error_invalid_digits), Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    onConfirmBatchRename(
                        PerformBatchRenameOptions(
                            baseName = batchRenameBaseName,
                            startNumber = startNum,
                            numDigits = numDigits,
                            keepOriginalExtension = batchRenameKeepExt
                        )
                    )
                }) { Text(stringResource(R.string.lfm_rename_button)) }
            },
            dismissButton = {
                TextButton(onClick = onDismissBatchRenameDialog) { Text(stringResource(R.string.lfm_cancel_button)) }
            }
        )
    }

    BackHandler(enabled = isSelectionModeActive) { onExitSelectionMode() }

    Scaffold(
        topBar = {
            if (isSelectionModeActive) {
                TopAppBar(
                    title = { Text(stringResource(R.string.lfm_selection_mode_selected_count, selectedItemsCount)) },
                    navigationIcon = { IconButton(onClick = onExitSelectionMode) { Icon(Icons.Filled.Close, stringResource(R.string.lfm_selection_mode_cancel_desc)) } },
                    actions = {
                        val allSelected = entries.isNotEmpty() && selectedItemsCount == entries.size
                        IconButton(onClick = { if (allSelected) onDeselectAll() else onSelectAll() }) {
                            Icon(if (allSelected) Icons.Filled.DoneAll else Icons.Filled.SelectAll, stringResource(if (allSelected) R.string.lfm_selection_mode_deselect_all_desc else R.string.lfm_selection_mode_select_all_desc))
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { if (showSearchFieldState) OutlinedTextField(value = searchText, onValueChange = onSearchTextChanged, modifier = Modifier.fillMaxWidth().padding(end = 8.dp), placeholder = { Text(stringResource(R.string.lfm_search_placeholder)) }, singleLine = true) else Text(stringResource(R.string.lfm_title_default)) },
                    navigationIcon = { if (rootUriIsSelected && canNavigateUp) IconButton(onClick = onNavigateUp) { Icon(Icons.Filled.ArrowUpward, stringResource(R.string.lfm_navigate_up_icon_desc)) } },
                    actions = {
                        if (rootUriIsSelected) {
                            if (clipboardHasItems && !showSearchFieldState) { IconButton(onClick = onPaste) { Icon(Icons.Outlined.ContentPaste, stringResource(R.string.lfm_action_paste_desc)) } }

                            if (!showSearchFieldState) IconButton(onClick = { showSearchFieldState = true }) { Icon(Icons.Filled.Search, stringResource(R.string.lfm_search_files_icon_desc)) }
                            else IconButton(onClick = { showSearchFieldState = false; onSearchTextChanged("") }) { Icon(Icons.Filled.Close, stringResource(R.string.lfm_close_search_icon_desc)) }

                            IconButton(onClick = onToggleViewType) {
                                Icon(
                                    imageVector = if (viewType == ViewType.LIST) Icons.Filled.GridView else Icons.AutoMirrored.Filled.ViewList,
                                    contentDescription = stringResource(if (viewType == ViewType.LIST) R.string.lfm_view_toggle_grid_desc else R.string.lfm_view_toggle_list_desc)
                                )
                            }

                            IconButton(onClick = { navController.navigate("settings") }) { Icon(Icons.Filled.Settings, stringResource(R.string.lfm_settings_icon_desc)) }
                            Box {
                                IconButton(onClick = { showSortAndOptionsKebabMenu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.lfm_more_options_icon_desc)) }
                                DropdownMenu(expanded = showSortAndOptionsKebabMenu, onDismissRequest = { showSortAndOptionsKebabMenu = false }) {
                                    // My Secured Space Menu Item
                                    if (isUserLoggedIn) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.lfm_my_secured_space_menu_item)) },
                                            onClick = {
                                                onNavigateToMySecuredSpace()
                                                showSortAndOptionsKebabMenu = false
                                            },
                                            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.lfm_my_secured_space_menu_item_desc)) }
                                        )
                                        HorizontalDivider()
                                    }

                                    DropdownMenuItem(text = { Text(stringResource(R.string.lfm_create_folder_menu)) }, onClick = { onRequestShowCreateFolderDialog(); showSortAndOptionsKebabMenu = false }, leadingIcon = { Icon(Icons.Filled.CreateNewFolder, null) })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.lfm_select_items_to_move_here_menu)) }, onClick = { onInitiateMoveExternal(); showSortAndOptionsKebabMenu = false }, leadingIcon = { Icon(Icons.Filled.MoveDown, null) })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.lfm_change_root_folder_menu)) }, onClick = { onSelectRootDirectoryClicked(); showSortAndOptionsKebabMenu = false }, leadingIcon = { Icon(Icons.Filled.FolderOpen, null) })
                                    HorizontalDivider()
                                    Text(stringResource(R.string.lfm_sort_by_label), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelSmall)
                                    SortOption.entries.forEach { option -> DropdownMenuItem(text = { Text(stringResource(option.toDisplayStringRes())) }, onClick = { onSortOptionSelected(option); showSortAndOptionsKebabMenu = false }) }
                                }
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (isSelectionModeActive && selectedItemsCount > 0) {
                BottomAppBar {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onCopySelected) { Icon(Icons.Filled.FileCopy, stringResource(R.string.lfm_bottom_bar_copy_desc)) }
                        IconButton(onClick = onCutSelected) { Icon(Icons.AutoMirrored.Filled.DriveFileMove, stringResource(R.string.lfm_bottom_bar_move_desc)) }
                        IconButton(onClick = onShareSelected) { Icon(Icons.Filled.Share, stringResource(R.string.lfm_bottom_bar_share_desc)) }
                        Box {
                            IconButton(onClick = { showBottomBarMoreOptionsMenu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.lfm_bottom_bar_more_options_desc)) }
                            DropdownMenu(expanded = showBottomBarMoreOptionsMenu, onDismissRequest = { showBottomBarMoreOptionsMenu = false }) {
                                val firstSelectedItem = selectedItems.firstOrNull()
                                if (selectedItemsCount == 1 && firstSelectedItem is DirectoryEntry.FileEntry) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.lfm_action_open_with_another_app)) }, onClick = { onOpenSelectedWithAnotherApp(); showBottomBarMoreOptionsMenu = false })
                                }
                                DropdownMenuItem(text = { Text(stringResource(R.string.lfm_action_details)) }, onClick = { onShowItemDetails(); showBottomBarMoreOptionsMenu = false })
                                // Batch Rename Menu Item
                                val selectedFilesCount = selectedItems.count { it is DirectoryEntry.FileEntry }
                                if (selectedFilesCount > 0) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.lfm_batch_rename_menu_item)) },
                                        onClick = {
                                            onRequestBatchRename()
                                            showBottomBarMoreOptionsMenu = false
                                        },
                                        leadingIcon = { Icon(Icons.Filled.DriveFileRenameOutline, null) }
                                    )
                                }
                                if (selectedItemsCount == 1 && firstSelectedItem is DirectoryEntry.FileEntry && (firstSelectedItem.name.endsWith(".zip", ignoreCase = true) || firstSelectedItem.mimeType == "application/zip")) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.lfm_action_extract)) }, onClick = { onRequestExtractArchive(firstSelectedItem); showBottomBarMoreOptionsMenu = false }, leadingIcon = { Icon(Icons.Filled.Unarchive, null)})
                                } else if (selectedItems.isNotEmpty()){
                                    DropdownMenuItem(text = { Text(stringResource(R.string.lfm_action_archive)) }, onClick = { onRequestArchiveSelectedItems(); showBottomBarMoreOptionsMenu = false }, leadingIcon = { Icon(Icons.Outlined.Archive, null)})
                                }
                                DropdownMenuItem(text = { Text(stringResource(R.string.lfm_delete_icon_desc)) }, onClick = { onDeleteSelectedItems(); showBottomBarMoreOptionsMenu = false })
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (rootUriIsSelected) {
                Text(stringResource(R.string.lfm_current_path_label, currentPath), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (entries.isEmpty() && searchText.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) { Text(stringResource(R.string.lfm_empty_folder), style = MaterialTheme.typography.bodyLarge) }
                } else if (entries.isEmpty() && searchText.isNotEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) { Text(stringResource(R.string.lfm_no_search_results, searchText), style = MaterialTheme.typography.bodyLarge) }
                }
                else {
                    if (viewType == ViewType.LIST) {
                        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                            items(entries, key = { entry -> entry.id }) { entry ->
                                val isSelected = selectedItems.any { it.uri == entry.uri }
                                when (entry) {
                                    is DirectoryEntry.FileEntry -> FileListItem(fileEntry = entry, processingStatus = fileProcessingStatusMap[entry.uri]?.first ?: ProcessingStatus.NONE, localizedSummary = fileProcessingStatusMap[entry.uri]?.second, isSelected = isSelected, isSelectionModeActive = isSelectionModeActive, onItemClick = { if (isSelectionModeActive) onToggleItemSelected(entry) else onPrepareFileForAnalysis(entry) }, onItemLongClick = { onEnterSelectionMode(entry) })
                                    is DirectoryEntry.FolderEntry -> FolderListItem(folderEntry = entry, isSelected = isSelected, isSelectionModeActive = isSelectionModeActive, onItemClick = { if (isSelectionModeActive) onToggleItemSelected(entry) else onNavigateToFolder(entry) }, onItemLongClick = { onEnterSelectionMode(entry) })
                                }
                            }
                        }
                    } else { // GRID View
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 120.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(entries, key = { entry -> "grid_${entry.id}" }) { entry ->
                                val isSelected = selectedItems.any { it.uri == entry.uri }
                                when (entry) {
                                    is DirectoryEntry.FileEntry -> GridFileItem(fileEntry = entry, isSelected = isSelected, isSelectionModeActive = isSelectionModeActive, onItemClick = { if (isSelectionModeActive) onToggleItemSelected(entry) else onPrepareFileForAnalysis(entry) }, onItemLongClick = { onEnterSelectionMode(entry) })
                                    is DirectoryEntry.FolderEntry -> GridFolderItem(folderEntry = entry, isSelected = isSelected, isSelectionModeActive = isSelectionModeActive, onItemClick = { if (isSelectionModeActive) onToggleItemSelected(entry) else onNavigateToFolder(entry) }, onItemLongClick = { onEnterSelectionMode(entry) })
                                }
                            }
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Filled.FolderOpen, stringResource(R.string.lfm_select_root_button), modifier = Modifier.size(64.dp).padding(bottom = 16.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.lfm_select_root_prompt), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
                        Button(onClick = onSelectRootDirectoryClicked) { Text(stringResource(R.string.lfm_select_root_button)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GridFolderItem(
    folderEntry: DirectoryEntry.FolderEntry,
    isSelected: Boolean,
    isSelectionModeActive: Boolean,
    onItemClick: () -> Unit,
    onItemLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .aspectRatio(1f)
            .combinedClickable(onClick = onItemClick, onLongClick = onItemLongClick),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(modifier = Modifier.height(24.dp).fillMaxWidth()) {
                if (isSelectionModeActive) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onItemClick() }, // Simplified for grid
                        modifier = Modifier.align(Alignment.TopStart).size(24.dp)
                    )
                }
            }
            Icon(
                imageVector = if (folderEntry.isSecuredUserDataRoot) Icons.Filled.Lock else Icons.Filled.Folder, // Secured icon
                contentDescription = stringResource(R.string.lfm_folder_icon_desc),
                modifier = Modifier.size(48.dp).weight(1f, fill = false),
                tint = MaterialTheme.colorScheme.secondary
            )
            Text(
                folderEntry.name, // "My Secured Space" for the root
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GridFileItem(
    fileEntry: DirectoryEntry.FileEntry,
    isSelected: Boolean,
    isSelectionModeActive: Boolean,
    onItemClick: () -> Unit,
    onItemLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .aspectRatio(1f)
            .combinedClickable(onClick = onItemClick, onLongClick = onItemLongClick),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(modifier = Modifier.height(24.dp).fillMaxWidth()) {
                if (isSelectionModeActive) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onItemClick() },
                        modifier = Modifier.align(Alignment.TopStart).size(24.dp)
                    )
                }
            }
            Icon(
                getIconForMimeType(fileEntry.mimeType),
                contentDescription = stringResource(R.string.lfm_file_icon_desc),
                modifier = Modifier.size(48.dp).weight(1f, fill = false),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                fileEntry.name,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderListItem(
    folderEntry: DirectoryEntry.FolderEntry,
    isSelected: Boolean,
    isSelectionModeActive: Boolean,
    onItemClick: () -> Unit,
    onItemLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp).combinedClickable(
            onClick = onItemClick,
            onLongClick = onItemLongClick
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (isSelectionModeActive) { Checkbox(checked = isSelected, onCheckedChange = { onItemClick() } , modifier = Modifier.padding(end = 8.dp)) }
            Icon(
                imageVector = if (folderEntry.isSecuredUserDataRoot) Icons.Filled.Lock else Icons.Filled.Folder, // Secured icon
                contentDescription = stringResource(R.string.lfm_folder_icon_desc),
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(folderEntry.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.lfm_item_details_items_count, folderEntry.childCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!isSelectionModeActive) { Icon(Icons.Filled.ChevronRight, stringResource(R.string.lfm_open_folder_icon_desc), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListItem(
    fileEntry: DirectoryEntry.FileEntry,
    processingStatus: ProcessingStatus,
    localizedSummary: LocalizedSummary?,
    isSelected: Boolean,
    isSelectionModeActive: Boolean,
    onItemClick: () -> Unit,
    onItemLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp).combinedClickable(
            onClick = onItemClick,
            onLongClick = onItemLongClick
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (isSelectionModeActive) { Checkbox(checked = isSelected, onCheckedChange = { onItemClick() }, modifier = Modifier.padding(end = 8.dp)) }
            Icon(getIconForMimeType(fileEntry.mimeType), stringResource(R.string.lfm_file_icon_desc), modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(fileEntry.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)) {
                    Text(stringResource(R.string.lfm_item_details_size) + " ${formatFileSize(fileEntry.size)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.lfm_item_details_modified) + " ${formatDate(fileEntry.dateModified)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (processingStatus != ProcessingStatus.NONE || localizedSummary != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProcessingStatusIndicator(processingStatus)
                        localizedSummary?.let { summary -> Text(text = stringResource(summary.stringResId, *summary.formatArgs.toTypedArray()), style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, modifier = Modifier.padding(start = 6.dp), color = if (processingStatus == ProcessingStatus.UNSUPPORTED || processingStatus == ProcessingStatus.FAILURE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
        }
    }
}
private fun constructBatchRenameExample(baseName: String, startNum: Int, numDigits: Int, keepExt: Boolean): String {
    val exampleNum = if (numDigits > 0) startNum.toString().padStart(numDigits, '0') else startNum.toString()
    val namePart = if (baseName.contains("{#}") || baseName.contains("{orig}")) {
        baseName.replace("{#}", exampleNum).replace("{orig}", "OriginalName")
    } else {
        baseName + exampleNum
    }
    return if (keepExt) "$namePart.ext" else namePart
}
@Composable
fun ItemDetailsSheetContent(details: ItemDetails?, onDismiss: () -> Unit) {
    if (details == null) { Box(modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.lfm_item_details_no_details)) }; return }
    Column(modifier = Modifier.padding(16.dp).fillMaxWidth().navigationBarsPadding()) {
        Text(stringResource(R.string.lfm_item_details_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 16.dp))
        if (details.name == stringResource(R.string.lfm_item_details_select_one_item) || details.name == stringResource(R.string.lfm_item_details_no_details)) { Text(details.name) }
        else {
            DetailRow(label = stringResource(R.string.lfm_item_details_name), value = details.name)
            DetailRow(label = stringResource(R.string.lfm_item_details_path), value = details.path)
            DetailRow(label = stringResource(R.string.lfm_item_details_type), value = details.type)
            details.size?.let { DetailRow(label = stringResource(R.string.lfm_item_details_size), value = formatFileSize(it)) }
            details.dateModified?.let { DetailRow(label = stringResource(R.string.lfm_item_details_modified), value = formatDate(it)) }
            details.mimeType?.let { DetailRow(label = stringResource(R.string.lfm_item_details_mime_type), value = it) }
            details.childrenCount?.let { DetailRow(label = stringResource(R.string.lfm_item_details_children), value = stringResource(R.string.lfm_item_details_items_count, it)) }
            DetailRow(label = stringResource(R.string.lfm_item_details_readable), value = if (details.isReadable) stringResource(R.string.lfm_item_details_yes) else stringResource(R.string.lfm_item_details_no))
            DetailRow(label = stringResource(R.string.lfm_item_details_writable), value = if (details.isWritable) stringResource(R.string.lfm_item_details_yes) else stringResource(R.string.lfm_item_details_no))
            DetailRow(label = stringResource(R.string.lfm_item_details_hidden), value = if (details.isHidden) stringResource(R.string.lfm_item_details_yes) else stringResource(R.string.lfm_item_details_no))
            if(details.isSecured) { // Show secured status
                DetailRow(label = stringResource(R.string.lfm_item_details_secured_status), value = stringResource(R.string.lfm_item_details_secured))
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.txt_analysis_summary_close_button)) }
    }
}

@Composable
fun DetailRow(label: String, value: String) { Row(modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth()) { Text(label, fontWeight = FontWeight.Bold, modifier = Modifier.width(130.dp)); Text(value, maxLines = 3, overflow = TextOverflow.Ellipsis) } }


fun SortOption.toDisplayStringRes(): Int {
    return when (this) {
        SortOption.BY_NAME_ASC -> R.string.lfm_sort_name_asc
        SortOption.BY_NAME_DESC -> R.string.lfm_sort_name_desc
        SortOption.BY_DATE_ASC -> R.string.lfm_sort_date_asc
        SortOption.BY_DATE_DESC -> R.string.lfm_sort_date_desc
        SortOption.BY_SIZE_ASC -> R.string.lfm_sort_size_asc
        SortOption.BY_SIZE_DESC -> R.string.lfm_sort_size_desc
    }
}



// --- Preview Setup ---
class PreviewLocalFileManagerViewModel(application: Application, initialUser: FirebaseUser?) : LocalFileManagerViewModel(application, MutableStateFlow(initialUser)) {

}

class PreviewLocalFileManagerViewModelFactory(
    private val application: Application,
    private val initialUser: FirebaseUser? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LocalFileManagerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PreviewLocalFileManagerViewModel(application, initialUser) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class for Preview")
    }
}
