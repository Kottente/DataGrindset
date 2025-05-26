package com.example.datagrindset.ui

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.example.datagrindset.viewmodel.LocalizedSummary // Import LocalizedSummary
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.net.toUri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalFileManagerScreen(
    rootUriSelected: Boolean,
    canNavigateUp: Boolean,
    currentPath: String,
    entries: List<DirectoryEntry>,
    // Updated map type
    fileProcessingStatusMap: Map<Uri, Pair<ProcessingStatus, LocalizedSummary?>>,
    searchText: String,
    onSearchTextChanged: (String) -> Unit,
    currentSortOption: SortOption,
    onSortOptionSelected: (SortOption) -> Unit,
    onSelectRootDirectoryClicked: () -> Unit,
    onNavigateToFolder: (DirectoryEntry.FolderEntry) -> Unit,
    onNavigateUp: () -> Unit,
    navigateToAnalysisTarget: DirectoryEntry.FileEntry?,
    onDidNavigateToAnalysisScreen: () -> Unit,
    suggestExternalAppForFile: DirectoryEntry.FileEntry?,
    onDidAttemptToOpenWithExternalApp: () -> Unit,
    onPrepareFileForAnalysis: (DirectoryEntry.FileEntry) -> Unit,
    onDeleteEntry: (DirectoryEntry) -> Unit,
    navController: NavController
) {
    var showSearchField by remember { mutableStateOf(false) }
    var showSortAndOptionsKebabMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(navigateToAnalysisTarget) {
        navigateToAnalysisTarget?.let { fileEntry ->
            val mimeType = fileEntry.mimeType?.lowercase()
            val fileName = fileEntry.name.lowercase()
            val uriString = fileEntry.uri.toString()
            val isCsv = mimeType in listOf("text/csv", "application/csv", "text/comma-separated-values") ||
                    fileName.endsWith(".csv")
            val isTxt = mimeType in listOf("text/plain", "text/markdown") ||
                    fileName.endsWith(".txt") || fileName.endsWith(".md")
            val route = when {
                isTxt -> "txtAnalysisScreen/${Uri.encode(uriString)}"
                isCsv -> "csvAnalysisScreen/${URLEncoder.encode(uriString, "UTF-8")}/${URLEncoder.encode(fileName, "UTF-8")}"
                else -> null
            }
            route?.let {
                Log.d("LFM_Screen", "Navigating to internal analysis route: $it")
                navController.navigate(it)
            } ?: Log.w("LFM_Screen", "No internal analysis route determined for MIME type: ${fileEntry.mimeType}")
            onDidNavigateToAnalysisScreen()
        }
    }

    val unsupportedFileTitle = stringResource(R.string.lfm_unsupported_file_title)
    val openWithButtonText = stringResource(R.string.lfm_open_with_button)
    val cancelButtonText = stringResource(R.string.lfm_cancel_button)

    suggestExternalAppForFile?.let { fileEntry ->
        val unsupportedFileMessage = stringResource(R.string.lfm_unsupported_file_message, fileEntry.mimeType ?: "unknown")
        AlertDialog(
            onDismissRequest = { onDidAttemptToOpenWithExternalApp() },
            title = { Text(unsupportedFileTitle) },
            text = { Text(unsupportedFileMessage) },
            confirmButton = {
                TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(fileEntry.uri, fileEntry.mimeType ?: "*/*")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    try {
                        val chooser = Intent.createChooser(intent, openWithButtonText)
                        context.startActivity(chooser)
                    } catch (e: Exception) {
                        Log.e("LFM_Screen", "Failed to start activity for ACTION_VIEW: ${e.message}")
                    }
                    onDidAttemptToOpenWithExternalApp()
                }) { Text(openWithButtonText) }
            },
            dismissButton = {
                TextButton(onClick = { onDidAttemptToOpenWithExternalApp() }) { Text(cancelButtonText) }
            },
            icon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = openWithButtonText) }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (showSearchField) {
                        OutlinedTextField(
                            value = searchText,
                            onValueChange = onSearchTextChanged,
                            modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                            placeholder = { Text(stringResource(R.string.lfm_search_placeholder)) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        )
                    } else { Text(stringResource(R.string.lfm_title_default)) }
                },
                navigationIcon = {
                    if (rootUriSelected && canNavigateUp) {
                        IconButton(onClick = onNavigateUp) {
                            Icon(Icons.Filled.ArrowUpward, contentDescription = stringResource(R.string.lfm_navigate_up_icon_desc))
                        }
                    }
                },
                actions = {
                    if (rootUriSelected) {
                        if (!showSearchField) {
                            IconButton(onClick = { showSearchField = true }) {
                                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.lfm_search_files_icon_desc))
                            }
                        } else {
                            IconButton(onClick = {
                                showSearchField = false
                                onSearchTextChanged("")
                            }) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.lfm_close_search_icon_desc)) }
                        }
                        IconButton(onClick = { navController.navigate("settings") }) {
                            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.lfm_settings_icon_desc))
                        }
                        Box {
                            IconButton(onClick = { showSortAndOptionsKebabMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.lfm_more_options_icon_desc))
                            }
                            DropdownMenu(
                                expanded = showSortAndOptionsKebabMenu,
                                onDismissRequest = { showSortAndOptionsKebabMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.lfm_change_root_folder_menu)) },
                                    onClick = { onSelectRootDirectoryClicked(); showSortAndOptionsKebabMenu = false },
                                    leadingIcon = { Icon(Icons.Filled.FolderOpen, contentDescription = stringResource(R.string.lfm_change_root_folder_menu)) }
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                Text(stringResource(R.string.lfm_sort_by_label), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                DropdownMenuItem(text = { Text(stringResource(R.string.lfm_sort_name_asc)) }, onClick = { onSortOptionSelected(SortOption.BY_NAME_ASC); showSortAndOptionsKebabMenu = false })
                                DropdownMenuItem(text = { Text(stringResource(R.string.lfm_sort_name_desc)) }, onClick = { onSortOptionSelected(SortOption.BY_NAME_DESC); showSortAndOptionsKebabMenu = false })
                                DropdownMenuItem(text = { Text(stringResource(R.string.lfm_sort_date_desc)) }, onClick = { onSortOptionSelected(SortOption.BY_DATE_DESC); showSortAndOptionsKebabMenu = false })
                                DropdownMenuItem(text = { Text(stringResource(R.string.lfm_sort_date_asc)) }, onClick = { onSortOptionSelected(SortOption.BY_DATE_ASC); showSortAndOptionsKebabMenu = false })
                                DropdownMenuItem(text = { Text(stringResource(R.string.lfm_sort_size_desc)) }, onClick = { onSortOptionSelected(SortOption.BY_SIZE_DESC); showSortAndOptionsKebabMenu = false })
                                DropdownMenuItem(text = { Text(stringResource(R.string.lfm_sort_size_asc)) }, onClick = { onSortOptionSelected(SortOption.BY_SIZE_ASC); showSortAndOptionsKebabMenu = false })
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (rootUriSelected) {
                Text(stringResource(R.string.lfm_current_path_label, currentPath), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (entries.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(if (searchText.isEmpty()) stringResource(R.string.lfm_empty_folder) else stringResource(R.string.lfm_no_search_results, searchText), style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                        items(entries, key = { entry -> entry.id }) { entry ->
                            when (entry) {
                                is DirectoryEntry.FileEntry -> {
                                    val statusPair = fileProcessingStatusMap[entry.uri]
                                    FileListItem(
                                        fileEntry = entry,
                                        processingStatus = statusPair?.first ?: ProcessingStatus.NONE,
                                        localizedSummary = statusPair?.second, // Pass LocalizedSummary
                                        onProcess = { onPrepareFileForAnalysis(entry) },
                                        onDelete = { onDeleteEntry(entry) }
                                    )
                                }
                                is DirectoryEntry.FolderEntry -> FolderListItem(folderEntry = entry, onClick = { onNavigateToFolder(entry) }, onDelete = { onDeleteEntry(entry) })
                            }
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = stringResource(R.string.lfm_select_root_button), modifier = Modifier.size(64.dp).padding(bottom = 16.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.lfm_select_root_prompt), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
                        Button(onClick = onSelectRootDirectoryClicked) { Text(stringResource(R.string.lfm_select_root_button)) }
                    }
                }
            }
        }
    }
}

// FolderListItem remains the same

@Composable
fun FileListItem(
    fileEntry: DirectoryEntry.FileEntry,
    processingStatus: ProcessingStatus,
    localizedSummary: LocalizedSummary?, // Changed from processingSummary: String?
    onProcess: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onProcess),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(getIconForMimeType(fileEntry.mimeType), contentDescription = stringResource(R.string.lfm_file_icon_desc), modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(fileEntry.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)) {
                    Text("Size: ${formatFileSize(fileEntry.size)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Modified: ${formatDate(fileEntry.dateModified)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (processingStatus != ProcessingStatus.NONE || localizedSummary != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProcessingStatusIndicator(processingStatus)
                        localizedSummary?.let { summary ->
                            Text(
                                text = stringResource(summary.stringResId, *summary.formatArgs.toTypedArray()), // Use stringResource
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = FontStyle.Italic,
                                modifier = Modifier.padding(start = 6.dp),
                                color = if (processingStatus == ProcessingStatus.UNSUPPORTED || processingStatus == ProcessingStatus.FAILURE || processingStatus == ProcessingStatus.FAILED || processingStatus == ProcessingStatus.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.lfm_delete_icon_desc), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// getIconForMimeType, formatFileSize, formatDate, ProcessingStatusIndicator remain the same as previous step
// ... (ensure these helper functions are present as defined before)
@Composable
fun FolderListItem(
    folderEntry: DirectoryEntry.FolderEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = stringResource(R.string.lfm_folder_icon_desc),
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(folderEntry.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${folderEntry.childCount} items", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = stringResource(R.string.lfm_delete_icon_desc), tint = MaterialTheme.colorScheme.error)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = stringResource(R.string.lfm_open_folder_icon_desc), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// Previews remain the same
@Preview(showBackground = true, name = "File Manager - No Root Selected")
@Composable
fun LocalFileManagerScreenNoRootPreview() {
    DataGrindsetTheme {
        LocalFileManagerScreen( // ... same preview params ...
            rootUriSelected = false, canNavigateUp = false, currentPath = "No folder selected", entries = emptyList(), fileProcessingStatusMap = emptyMap(), searchText = "", onSearchTextChanged = {}, currentSortOption = SortOption.BY_NAME_ASC, onSortOptionSelected = {}, onSelectRootDirectoryClicked = {}, onNavigateToFolder = {}, onNavigateUp = {}, navigateToAnalysisTarget = null, onDidNavigateToAnalysisScreen = {}, suggestExternalAppForFile = null, onDidAttemptToOpenWithExternalApp = {}, onPrepareFileForAnalysis = {}, onDeleteEntry = {}, navController = rememberNavController()
        )
    }
}
// ... other previews ...