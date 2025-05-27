package com.example.datagrindset.ui

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.net.toUri

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LocalFileManagerScreen(
    navController: NavController,
    rootUriIsSelected: Boolean,
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
    selectedItems: Set<Uri>,
    selectedItemsCount: Int,
    itemDetailsToShow: ItemDetails?,
    showCreateFolderDialog: Boolean,
    clipboardHasItems: Boolean,
    viewType: ViewType,
    onSearchTextChanged: (String) -> Unit,
    onSortOptionSelected: (SortOption) -> Unit,
    onSelectRootDirectoryClicked: () -> Unit,
    onNavigateToFolder: (DirectoryEntry.FolderEntry) -> Unit,
    onNavigateUp: () -> Unit,
    onDidNavigateToAnalysisScreen: (String) -> Unit,
    onDidAttemptToOpenWithExternalApp: () -> Unit,
    onPrepareFileForAnalysis: (DirectoryEntry.FileEntry) -> Unit,
    onToggleItemSelected: (Uri) -> Unit,
    onEnterSelectionMode: (Uri) -> Unit,
    onExitSelectionMode: () -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onShareSelected: () -> Unit,
    onCutSelected: () -> Unit,
    onCopySelected: () -> Unit,
    onArchiveSelected: () -> Unit,
    onOpenSelectedWithAnotherApp: () -> Unit,
    onShowItemDetails: () -> Unit,
    onDeleteSelectedItems: () -> Unit,
    onDismissItemDetails: () -> Unit,
    onRequestShowCreateFolderDialog: () -> Unit,
    onDismissCreateFolderDialog: () -> Unit,
    onCreateFolder: (String) -> Unit,
    onPaste: () -> Unit,
    onInitiateMoveExternal: () -> Unit,
    onToggleViewType: () -> Unit
) {
    var showSearchFieldState by remember { mutableStateOf(false) }
    var showSortAndOptionsKebabMenu by remember { mutableStateOf(false) }
    var showBottomBarMoreOptionsMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var newFolderName by remember { mutableStateOf("") }

    val itemDetailsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val openWithButtonTextResolved = stringResource(R.string.lfm_open_with_button)
    val couldNotOpenFileToastMsg = stringResource(id = R.string.lfm_could_not_open_file_toast)

    // For File Opening
    LaunchedEffect(navigateToAnalysisTarget) {
        navigateToAnalysisTarget?.let { fileEntry ->
            Log.d("LFM_Screen", "navigateToAnalysisTarget changed: ${fileEntry.name}. Current value: $navigateToAnalysisTarget")
            val mimeType = fileEntry.mimeType?.lowercase(Locale.ROOT)
            val fileName = fileEntry.name
            val uriString = fileEntry.uri.toString()
            val isCsv = mimeType in listOf("text/csv", "application/csv", "text/comma-separated-values") || fileName.endsWith(".csv", ignoreCase = true)
            val isTxt = mimeType in listOf("text/plain", "text/markdown") || fileName.endsWith(".txt", ignoreCase = true) || fileName.endsWith(".md", ignoreCase = true)

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

    BackHandler(enabled = isSelectionModeActive) {
        Log.d("LFM_Screen", "Back pressed in selection mode. Exiting selection mode.")
        onExitSelectionMode()
    }

    Scaffold(
        topBar = {
            Log.d("LFM_Screen", "TopAppBar recomposing. isSelectionModeActive: $isSelectionModeActive, selectedItemsCount: $selectedItemsCount")
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
            Log.d("LFM_Screen", "BottomBar recomposing. isSelectionModeActive: $isSelectionModeActive, selectedItemsCount: $selectedItemsCount")
            if (isSelectionModeActive && selectedItemsCount > 0) {
                BottomAppBar {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onCopySelected) { Icon(Icons.Filled.FileCopy, stringResource(R.string.lfm_bottom_bar_copy_desc)) }
                        IconButton(onClick = onCutSelected) { Icon(Icons.AutoMirrored.Filled.DriveFileMove, stringResource(R.string.lfm_bottom_bar_move_desc)) }
                        IconButton(onClick = onShareSelected) { Icon(Icons.Filled.Share, stringResource(R.string.lfm_bottom_bar_share_desc)) }
                        Box {
                            IconButton(onClick = { showBottomBarMoreOptionsMenu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.lfm_bottom_bar_more_options_desc)) }
                            DropdownMenu(expanded = showBottomBarMoreOptionsMenu, onDismissRequest = { showBottomBarMoreOptionsMenu = false }) {
                                if (selectedItemsCount == 1 && selectedItems.firstOrNull()?.let { uri -> entries.find { it.uri == uri } is DirectoryEntry.FileEntry } == true) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.lfm_action_open_with_another_app)) }, onClick = { onOpenSelectedWithAnotherApp(); showBottomBarMoreOptionsMenu = false })
                                }
                                DropdownMenuItem(text = { Text(stringResource(R.string.lfm_action_details)) }, onClick = { onShowItemDetails(); showBottomBarMoreOptionsMenu = false })
                                DropdownMenuItem(text = { Text(stringResource(R.string.lfm_action_archive)) }, onClick = { onArchiveSelected(); showBottomBarMoreOptionsMenu = false })
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
                                val isSelected = selectedItems.contains(entry.uri)
                                when (entry) {
                                    is DirectoryEntry.FileEntry -> FileListItem(fileEntry = entry, processingStatus = fileProcessingStatusMap[entry.uri]?.first ?: ProcessingStatus.NONE, localizedSummary = fileProcessingStatusMap[entry.uri]?.second, isSelected = isSelected, isSelectionModeActive = isSelectionModeActive, onItemClick = { if (isSelectionModeActive) onToggleItemSelected(entry.uri) else onPrepareFileForAnalysis(entry) }, onItemLongClick = { onEnterSelectionMode(entry.uri) })
                                    is DirectoryEntry.FolderEntry -> FolderListItem(folderEntry = entry, isSelected = isSelected, isSelectionModeActive = isSelectionModeActive, onItemClick = { if (isSelectionModeActive) onToggleItemSelected(entry.uri) else onNavigateToFolder(entry) }, onItemLongClick = { onEnterSelectionMode(entry.uri) })
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
                                val isSelected = selectedItems.contains(entry.uri)
                                when (entry) {
                                    is DirectoryEntry.FileEntry -> GridFileItem(fileEntry = entry, isSelected = isSelected, isSelectionModeActive = isSelectionModeActive, onItemClick = { if (isSelectionModeActive) onToggleItemSelected(entry.uri) else onPrepareFileForAnalysis(entry) }, onItemLongClick = { onEnterSelectionMode(entry.uri) })
                                    is DirectoryEntry.FolderEntry -> GridFolderItem(folderEntry = entry, isSelected = isSelected, isSelectionModeActive = isSelectionModeActive, onItemClick = { if (isSelectionModeActive) onToggleItemSelected(entry.uri) else onNavigateToFolder(entry) }, onItemLongClick = { onEnterSelectionMode(entry.uri) })
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
            verticalArrangement = Arrangement.Center
        ) {
            Box(modifier = Modifier.height(24.dp).fillMaxWidth()) { // Consistent height for checkbox area
                if (isSelectionModeActive) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onItemClick() },
                        modifier = Modifier.align(Alignment.TopStart).size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.weight(0.1f)) // Small spacer
            Icon(
                Icons.Filled.Folder,
                contentDescription = stringResource(R.string.lfm_folder_icon_desc),
                modifier = Modifier.size(48.dp), // Fixed size
                tint = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.weight(0.1f)) // Small spacer
            Text(
                folderEntry.name,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp) // Padding for text
            )
            Spacer(modifier = Modifier.weight(0.1f))
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
            verticalArrangement = Arrangement.Center
        ) {
            Box(modifier = Modifier.height(24.dp).fillMaxWidth()) { // Consistent height for checkbox area
                if (isSelectionModeActive) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onItemClick() },
                        modifier = Modifier.align(Alignment.TopStart).size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.weight(0.1f))
            Icon(
                getIconForMimeType(fileEntry.mimeType),
                contentDescription = stringResource(R.string.lfm_file_icon_desc),
                modifier = Modifier.size(48.dp), // Fixed size
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.weight(0.1f))
            Text(
                fileEntry.name,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(modifier = Modifier.weight(0.1f))
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
            onClick = {
                Log.d("LFM_Screen_FolderItem", "Clicked. SelectionMode: $isSelectionModeActive")
                onItemClick()
            },
            onLongClick = {
                Log.d("LFM_Screen_FolderItem", "LongClicked. Entering selection mode.")
                onItemLongClick()
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (isSelectionModeActive) { Checkbox(checked = isSelected, onCheckedChange = null, modifier = Modifier.padding(end = 8.dp)) } // Pass null for onCheckedChange if combinedClickable handles toggle
            Icon(Icons.Filled.Folder, stringResource(R.string.lfm_folder_icon_desc), modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.secondary)
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
            onClick = {
                Log.d("LFM_Screen_FileItem", "Clicked. SelectionMode: $isSelectionModeActive. File: ${fileEntry.name}")
                onItemClick()
            },
            onLongClick = {
                Log.d("LFM_Screen_FileItem", "LongClicked. Entering selection mode. File: ${fileEntry.name}")
                onItemLongClick()
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (isSelectionModeActive) { Checkbox(checked = isSelected, onCheckedChange = null, modifier = Modifier.padding(end = 8.dp)) }
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

@Composable
fun ItemDetailsSheetContent(details: ItemDetails?, onDismiss: () -> Unit) {
    if (details == null) { Box(modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.lfm_item_details_no_details)) }; return }
    Column(modifier = Modifier.padding(16.dp).fillMaxWidth().navigationBarsPadding()) {
        Text(stringResource(R.string.lfm_item_details_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))
        if (details.name == stringResource(R.string.lfm_item_details_select_one_item) || details.name == "Error" || details.name == stringResource(R.string.lfm_item_details_no_details)) { Text(details.name) }
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
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.txt_analysis_summary_close_button)) }
    }
}

@Composable
fun DetailRow(label: String, value: String) { Row(modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth()) { Text(label, fontWeight = FontWeight.Bold, modifier = Modifier.width(130.dp)); Text(value, maxLines = 3, overflow = TextOverflow.Ellipsis) } }
fun SortOption.toDisplayStringRes(): Int { return when (this) { SortOption.BY_NAME_ASC -> R.string.lfm_sort_name_asc; SortOption.BY_NAME_DESC -> R.string.lfm_sort_name_desc; SortOption.BY_DATE_ASC -> R.string.lfm_sort_date_asc; SortOption.BY_DATE_DESC -> R.string.lfm_sort_date_desc; SortOption.BY_SIZE_ASC -> R.string.lfm_sort_size_asc; SortOption.BY_SIZE_DESC -> R.string.lfm_sort_size_desc } }

@Preview(showBackground = true, name = "File Manager - List View")
@Composable
fun LocalFileManagerScreenListPreview() {
    DataGrindsetTheme {
        LocalFileManagerScreen(navController = rememberNavController(), rootUriIsSelected = true, currentDirectoryUri = Uri.parse("content://preview/current"), canNavigateUp = true, currentPath = "Preview > Current Folder", entries = listOf(DirectoryEntry.FolderEntry("folder1_id", "My Folder", Uri.parse("content://preview/folder1"), 2), DirectoryEntry.FileEntry("file1_id", "MyFile.txt", Uri.parse("content://preview/file1"), 1024, System.currentTimeMillis(), "text/plain")), fileProcessingStatusMap = emptyMap(), searchText = "", onSearchTextChanged = {}, currentSortOption = SortOption.BY_NAME_ASC, onSortOptionSelected = {}, onSelectRootDirectoryClicked = {}, onNavigateToFolder = {}, onNavigateUp = {}, navigateToAnalysisTarget = null, onDidNavigateToAnalysisScreen = {}, suggestExternalAppForFile = null, onDidAttemptToOpenWithExternalApp = {}, onPrepareFileForAnalysis = {}, isSelectionModeActive = false, selectedItems = emptySet(), selectedItemsCount = 0, onToggleItemSelected = {}, onEnterSelectionMode = {}, onExitSelectionMode = {}, onSelectAll = {}, onDeselectAll = {}, onShareSelected = {}, onCutSelected = {}, onCopySelected = {}, onArchiveSelected = {}, onOpenSelectedWithAnotherApp = {}, onShowItemDetails = {}, onDeleteSelectedItems = {}, itemDetailsToShow = null, onDismissItemDetails = {}, showCreateFolderDialog = false, onRequestShowCreateFolderDialog = {}, onDismissCreateFolderDialog = {}, onCreateFolder = {}, clipboardHasItems = false, onPaste = {}, onInitiateMoveExternal = {}, viewType = ViewType.LIST, onToggleViewType = {})
    }
}

@Preview(showBackground = true, name = "File Manager - Grid View")
@Composable
fun LocalFileManagerScreenGridPreview() {
    DataGrindsetTheme {
        LocalFileManagerScreen(navController = rememberNavController(), rootUriIsSelected = true, currentDirectoryUri = Uri.parse("content://preview/current"), canNavigateUp = true, currentPath = "Preview > Current Folder", entries = listOf(DirectoryEntry.FolderEntry("folder1_id", "My Folder with a very long name that should wrap or ellipsis", Uri.parse("content://preview/folder1"), 2), DirectoryEntry.FileEntry("file1_id", "MyFileWithALongName.txt", Uri.parse("content://preview/file1"), 1024, System.currentTimeMillis(), "text/plain"), DirectoryEntry.FileEntry("file2_id", "Image.jpg", Uri.parse("content://preview/file2"), 204800, System.currentTimeMillis() - 100000, "image/jpeg")), fileProcessingStatusMap = emptyMap(), searchText = "", onSearchTextChanged = {}, currentSortOption = SortOption.BY_NAME_ASC, onSortOptionSelected = {}, onSelectRootDirectoryClicked = {}, onNavigateToFolder = {}, onNavigateUp = {}, navigateToAnalysisTarget = null, onDidNavigateToAnalysisScreen = {}, suggestExternalAppForFile = null, onDidAttemptToOpenWithExternalApp = {}, onPrepareFileForAnalysis = {}, isSelectionModeActive = false, selectedItems = emptySet(), selectedItemsCount = 0, onToggleItemSelected = {}, onEnterSelectionMode = {}, onExitSelectionMode = {}, onSelectAll = {}, onDeselectAll = {}, onShareSelected = {}, onCutSelected = {}, onCopySelected = {}, onArchiveSelected = {}, onOpenSelectedWithAnotherApp = {}, onShowItemDetails = {}, onDeleteSelectedItems = {}, itemDetailsToShow = null, onDismissItemDetails = {}, showCreateFolderDialog = false, onRequestShowCreateFolderDialog = {}, onDismissCreateFolderDialog = {}, onCreateFolder = {}, clipboardHasItems = false, onPaste = {}, onInitiateMoveExternal = {}, viewType = ViewType.GRID, onToggleViewType = {})
    }
}