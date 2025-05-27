package com.example.datagrindset.ui

// import android.app.Application // Not needed if ViewModel is not created here
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.datagrindset.ProcessingStatus
import com.example.datagrindset.R
import com.example.datagrindset.ui.SortOption
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
    onSearchTextChanged: (String) -> Unit,
    onSortOptionSelected: (SortOption) -> Unit,
    onSelectRootDirectoryClicked: () -> Unit,
    onNavigateToFolder: (DirectoryEntry.FolderEntry) -> Unit,
    onNavigateUp: () -> Unit,
    onDidNavigateToAnalysisScreen: (String) -> Unit, // Takes route string
    onDidAttemptToOpenWithExternalApp: () -> Unit,
    onPrepareFileForAnalysis: (DirectoryEntry.FileEntry) -> Unit,
    onToggleItemSelected: (Uri) -> Unit,
    onEnterSelectionMode: (Uri) -> Unit,
    onExitSelectionMode: () -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onShareSelected: () -> Unit,
    onMoveSelected: () -> Unit,
    onArchiveSelected: () -> Unit,
    onOpenSelectedWithAnotherApp: () -> Unit,
    onShowItemDetails: () -> Unit,
    onDeleteSelectedItems: () -> Unit,
    onDismissItemDetails: () -> Unit
) {
    var showSearchField by remember { mutableStateOf(false) }
    var showSortAndOptionsKebabMenu by remember { mutableStateOf(false) }
    var showBottomBarMoreOptionsMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val itemDetailsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Resolve strings for lambdas here, in the Composable scope
    val openWithButtonTextResolved = stringResource(R.string.lfm_open_with_button)
    val couldNotOpenFileToastTextResolved = stringResource(R.string.lfm_could_not_open_file_toast, "") // Base for formatting

    LaunchedEffect(navigateToAnalysisTarget) {
        navigateToAnalysisTarget?.let { fileEntry ->
            val mimeType = fileEntry.mimeType?.lowercase()
            val fileName = fileEntry.name.lowercase()
            val uriString = fileEntry.uri.toString()
            val isCsv = mimeType in listOf("text/csv", "application/csv", "text/comma-separated-values") || fileName.endsWith(".csv")
            val isTxt = mimeType in listOf("text/plain", "text/markdown") || fileName.endsWith(".txt") || fileName.endsWith(".md")
            val route = when {
                isTxt -> "txtAnalysisScreen/${Uri.encode(uriString)}"
                isCsv -> "csvAnalysisScreen/${Uri.encode(uriString)}/${Uri.encode(fileName)}" // Uri.encode for fileName too
                else -> null
            }
            route?.let { onDidNavigateToAnalysisScreen(it) } // Pass route to lambda
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
                        Log.e("LFM_Screen", "Failed ACTION_VIEW: ${e.message}")
                        Toast.makeText(context, String.format(couldNotOpenFileToastTextResolved, e.localizedMessage ?: "Unknown error"), Toast.LENGTH_LONG).show()
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

    BackHandler(enabled = isSelectionModeActive) { onExitSelectionMode() }

    Scaffold(
        topBar = {
            if (isSelectionModeActive) {
                TopAppBar(
                    title = { Text(stringResource(R.string.lfm_selection_mode_selected_count, selectedItemsCount)) },
                    navigationIcon = { IconButton(onClick = onExitSelectionMode) { Icon(Icons.Filled.Close, stringResource(R.string.lfm_selection_mode_cancel_desc)) } },
                    actions = {
                        val allSelected = selectedItemsCount == entries.size && entries.isNotEmpty()
                        IconButton(onClick = { if (allSelected) onDeselectAll() else onSelectAll() }) {
                            Icon(if (allSelected) Icons.Filled.DoneAll else Icons.Filled.SelectAll, stringResource(if (allSelected) R.string.lfm_selection_mode_deselect_all_desc else R.string.lfm_selection_mode_select_all_desc))
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { if (showSearchField) OutlinedTextField(value = searchText, onValueChange = onSearchTextChanged, modifier = Modifier.fillMaxWidth().padding(end = 8.dp), placeholder = { Text(stringResource(R.string.lfm_search_placeholder)) }, singleLine = true) else Text(stringResource(R.string.lfm_title_default)) },
                    navigationIcon = { if (rootUriIsSelected && canNavigateUp) IconButton(onClick = onNavigateUp) { Icon(Icons.Filled.ArrowUpward, stringResource(R.string.lfm_navigate_up_icon_desc)) } },
                    actions = {
                        if (rootUriIsSelected) {
                            if (!showSearchField) IconButton(onClick = { showSearchField = true }) { Icon(Icons.Filled.Search, stringResource(R.string.lfm_search_files_icon_desc)) }
                            else IconButton(onClick = { showSearchField = false; onSearchTextChanged("") }) { Icon(Icons.Filled.Close, stringResource(R.string.lfm_close_search_icon_desc)) }
                            IconButton(onClick = { navController.navigate("settings") }) { Icon(Icons.Filled.Settings, stringResource(R.string.lfm_settings_icon_desc)) }
                            Box {
                                IconButton(onClick = { showSortAndOptionsKebabMenu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.lfm_more_options_icon_desc)) }
                                DropdownMenu(expanded = showSortAndOptionsKebabMenu, onDismissRequest = { showSortAndOptionsKebabMenu = false }) {
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
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceAround) {
                        IconButton(onClick = onShareSelected) { Icon(Icons.Filled.Share, stringResource(R.string.lfm_bottom_bar_share_desc)) }
                        IconButton(onClick = onMoveSelected) { Icon(Icons.AutoMirrored.Filled.DriveFileMove, stringResource(R.string.lfm_bottom_bar_move_desc)) }
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
                if (entries.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) { Text(if (searchText.isEmpty()) stringResource(R.string.lfm_empty_folder) else stringResource(R.string.lfm_no_search_results, searchText), style = MaterialTheme.typography.bodyLarge) }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                        items(entries, key = { entry -> entry.id }) { entry ->
                            val isSelected = selectedItems.contains(entry.uri)
                            when (entry) {
                                is DirectoryEntry.FileEntry -> FileListItem(fileEntry = entry, processingStatus = fileProcessingStatusMap[entry.uri]?.first ?: ProcessingStatus.NONE, localizedSummary = fileProcessingStatusMap[entry.uri]?.second, isSelected = isSelected, isSelectionModeActive = isSelectionModeActive, onItemClick = { if (isSelectionModeActive) onToggleItemSelected(entry.uri) else onPrepareFileForAnalysis(entry) }, onItemLongClick = { onEnterSelectionMode(entry.uri) })
                                is DirectoryEntry.FolderEntry -> FolderListItem(folderEntry = entry, isSelected = isSelected, isSelectionModeActive = isSelectionModeActive, onItemClick = { if (isSelectionModeActive) onToggleItemSelected(entry.uri) else onNavigateToFolder(entry) }, onItemLongClick = { onEnterSelectionMode(entry.uri) })
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
fun FolderListItem(
    folderEntry: DirectoryEntry.FolderEntry,
    isSelected: Boolean,
    isSelectionModeActive: Boolean,
    onItemClick: () -> Unit,
    onItemLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp).combinedClickable(onClick = onItemClick, onLongClick = onItemLongClick),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (isSelectionModeActive) { Checkbox(checked = isSelected, onCheckedChange = { onItemClick() }, modifier = Modifier.padding(end = 8.dp)) }
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
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp).combinedClickable(onClick = onItemClick, onLongClick = onItemLongClick),
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

@Composable
fun ItemDetailsSheetContent(details: ItemDetails?, onDismiss: () -> Unit) {
    if (details == null) {
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.lfm_item_details_no_details)) }
        return
    }
    Column(modifier = Modifier.padding(16.dp).fillMaxWidth().navigationBarsPadding()) {
        Text(stringResource(R.string.lfm_item_details_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))
        if (details.name == stringResource(R.string.lfm_item_details_select_one_item) || details.name == "Error") { Text(details.name) }
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
fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth()) {
        Text(label, fontWeight = FontWeight.Bold, modifier = Modifier.width(130.dp))
        Text(value, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}


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

@Preview(showBackground = true, name = "File Manager - No Root")
@Composable
fun LocalFileManagerScreenNoRootPreview() {
    DataGrindsetTheme {
        LocalFileManagerScreen(
            navController = rememberNavController(), rootUriIsSelected = false, canNavigateUp = false,
            currentPath = "Please select a root folder", entries = emptyList(), fileProcessingStatusMap = emptyMap(),
            searchText = "", onSearchTextChanged = {}, currentSortOption = SortOption.BY_NAME_ASC, onSortOptionSelected = {},
            onSelectRootDirectoryClicked = {}, onNavigateToFolder = {}, onNavigateUp = {}, navigateToAnalysisTarget = null,
            onDidNavigateToAnalysisScreen = {}, suggestExternalAppForFile = null, onDidAttemptToOpenWithExternalApp = {},
            onPrepareFileForAnalysis = {}, isSelectionModeActive = false, selectedItems = emptySet(), selectedItemsCount = 0,
            onToggleItemSelected = {}, onEnterSelectionMode = {}, onExitSelectionMode = {}, onSelectAll = {}, onDeselectAll = {},
            onShareSelected = {}, onMoveSelected = {}, onArchiveSelected = {}, onOpenSelectedWithAnotherApp = {},
            onShowItemDetails = {}, onDeleteSelectedItems = {}, itemDetailsToShow = null, onDismissItemDetails = {}
        )
    }
}

@Preview(showBackground = true, name = "File Manager - Selection Mode")
@Composable
fun LocalFileManagerScreenSelectionModePreview() {
    DataGrindsetTheme {
        val sampleFileUri1 = Uri.parse("content://preview/file1.txt")
        val sampleFolderUri1 = Uri.parse("content://preview/folder1")
        LocalFileManagerScreen(
            navController = rememberNavController(), rootUriIsSelected = true, canNavigateUp = true,
            currentPath = "Preview > Folder",
            entries = listOf(
                DirectoryEntry.FolderEntry("folder1_id", "My Folder", sampleFolderUri1, 2),
                DirectoryEntry.FileEntry("file1_id", "MyFile.txt", sampleFileUri1, 1024, System.currentTimeMillis(), "text/plain")
            ),
            fileProcessingStatusMap = emptyMap(), searchText = "", onSearchTextChanged = {},
            currentSortOption = SortOption.BY_NAME_ASC, onSortOptionSelected = {}, onSelectRootDirectoryClicked = {},
            onNavigateToFolder = {}, onNavigateUp = {}, navigateToAnalysisTarget = null, onDidNavigateToAnalysisScreen = {},
            suggestExternalAppForFile = null, onDidAttemptToOpenWithExternalApp = {}, onPrepareFileForAnalysis = {},
            isSelectionModeActive = true, selectedItems = setOf(sampleFileUri1), selectedItemsCount = 1,
            onToggleItemSelected = {}, onEnterSelectionMode = {}, onExitSelectionMode = {}, onSelectAll = {}, onDeselectAll = {},
            onShareSelected = {}, onMoveSelected = {}, onArchiveSelected = {}, onOpenSelectedWithAnotherApp = {},
            onShowItemDetails = {}, onDeleteSelectedItems = {}, itemDetailsToShow = null, onDismissItemDetails = {}
        )
    }
}