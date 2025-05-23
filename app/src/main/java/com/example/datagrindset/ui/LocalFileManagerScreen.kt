package com.example.datagrindset.ui

import android.app.Application
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CheckCircle // For success
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description // Default for TXT
import androidx.compose.material.icons.filled.Error // For error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile // Default file icon
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd // For unsupported
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh // For pending
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.TableView // For CSV
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.datagrindset.ProcessingStatus
import com.example.datagrindset.ui.theme.DataGrindsetTheme
import com.example.datagrindset.viewmodel.DirectoryEntry
import com.example.datagrindset.viewmodel.LocalFileManagerViewModel
// import com.example.datagrindset.ui.SortOption // No longer needed here if SortOption is in its own file in this package
import java.text.SimpleDateFormat
import java.util.*

// SortOption is now in its own file: ui/SortOption.kt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LocalFileManagerScreen(
    navController: NavController,
    viewModel: LocalFileManagerViewModel,
    onSelectRootDirectoryClicked: () -> Unit
) {
    val rootTreeUriFromVM by viewModel.rootTreeUri.collectAsStateWithLifecycle()
    val rootUriSelected = rootTreeUriFromVM != null
    val canNavigateUp by viewModel.canNavigateUp.collectAsStateWithLifecycle()
    val currentPath by viewModel.currentPathDisplay.collectAsStateWithLifecycle()
    val entries by viewModel.directoryEntries.collectAsStateWithLifecycle()
    val fileProcessingStatusMap by viewModel.fileProcessingStatusMap.collectAsStateWithLifecycle()
    val searchText by viewModel.searchText.collectAsStateWithLifecycle()
    val currentSortOption by viewModel.sortOption.collectAsStateWithLifecycle() // Uses SortOption

    var showSortMenu by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf<DirectoryEntry?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (rootUriSelected) {
                        Text(currentPath, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } else {
                        Text("Select Root Folder")
                    }
                },
                navigationIcon = {
                    if (canNavigateUp) {
                        IconButton(onClick = { viewModel.navigateUp() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Navigate Up")
                        }
                    }
                },
                actions = {
                    if (rootUriSelected) {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort Options")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            SortOption.entries.forEach { sortOption -> // Uses SortOption
                                DropdownMenuItem(
                                    text = { Text(sortOption.displayName) },
                                    onClick = {
                                        viewModel.onSortOptionSelected(sortOption)
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            if (!rootUriSelected) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Button(onClick = onSelectRootDirectoryClicked) {
                        Text("Select Root Storage Folder")
                    }
                }
            } else {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { viewModel.onSearchTextChanged(it) },
                    label = { Text("Search files/folders") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                )
                if (entries.isEmpty() && searchText.isBlank()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("This folder is empty.")
                    }
                } else if (entries.isEmpty() && searchText.isNotBlank()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No results found for \"$searchText\".")
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(entries, key = { it.id }) { entry ->
                            DirectoryRow(
                                entry = entry,
                                statusPair = if (entry is DirectoryEntry.FileEntry) fileProcessingStatusMap[entry.uri] else null,
                                onClick = {
                                    if (entry.isDirectory) {
                                        viewModel.navigateTo(entry as DirectoryEntry.FolderEntry)
                                    } else {
                                        viewModel.prepareFileForAnalysis(entry as DirectoryEntry.FileEntry)
                                    }
                                },
                                onLongClick = {
                                    showDeleteConfirmDialog = entry
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirmDialog != null) {
        val entryToDelete = showDeleteConfirmDialog!!
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text("Delete ${if (entryToDelete.isDirectory) "Folder" else "File"}") },
            text = { Text("Are you sure you want to delete \"${entryToDelete.name}\"? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteEntry(entryToDelete)
                        showDeleteConfirmDialog = null
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DirectoryRow(
    entry: DirectoryEntry,
    statusPair: Pair<ProcessingStatus, String?>?,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val icon = if (entry.isDirectory) {
        Icons.Filled.Folder
    } else if (entry is DirectoryEntry.FileEntry) {
        entry.mimeTypeToIcon() // This should now work correctly
    } else {
        Icons.AutoMirrored.Filled.InsertDriveFile
    }
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }

    ListItem(
        headlineContent = { Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                val description = when (entry) {
                    is DirectoryEntry.FileEntry -> "Size: ${entry.size.formatFileSizes()}, Modified: ${dateFormat.format(Date(entry.dateModified))}"
                    is DirectoryEntry.FolderEntry -> "${entry.childCount} items"
                }
                Text(description, style = MaterialTheme.typography.bodySmall)
                statusPair?.let { (status, message) ->
                    val (statusIcon, statusColor, statusText) = when (status) {
                        ProcessingStatus.PENDING -> Triple(Icons.Filled.Refresh, MaterialTheme.colorScheme.outline, "Pending: ${message ?: ""}")
                        ProcessingStatus.SUCCESS -> Triple(Icons.Filled.CheckCircle, MaterialTheme.colorScheme.primary, "Ready: ${message ?: ""}")
                        ProcessingStatus.ERROR -> Triple(Icons.Filled.Error, MaterialTheme.colorScheme.error, "Error: ${message ?: ""}")
                        ProcessingStatus.UNSUPPORTED -> Triple(Icons.AutoMirrored.Filled.NoteAdd, MaterialTheme.colorScheme.secondary, "Unsupported: ${message ?: ""}")
                        ProcessingStatus.NONE -> TODO()
                        ProcessingStatus.PROCESSING -> TODO()
                        ProcessingStatus.FAILED -> TODO()
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Icon(statusIcon, contentDescription = "Status", tint = statusColor, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(statusText, style = MaterialTheme.typography.labelSmall, color = statusColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        leadingContent = { Icon(icon, contentDescription = entry.name) },
        trailingContent = {
            IconButton(onClick = onLongClick) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete")
            }
        },
        modifier = Modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

fun DirectoryEntry.FileEntry.mimeTypeToIcon(): ImageVector {
    return when (mimeType?.lowercase()) {
        "text/plain", "text/markdown" -> Icons.Filled.Description
        "text/csv", "application/csv", "text/comma-separated-values" -> Icons.Filled.TableView
        "application/pdf" -> Icons.Filled.PictureAsPdf
        "image/jpeg", "image/png", "image/gif", "image/webp" -> Icons.Filled.Image
        "video/mp4", "video/webm" -> Icons.Filled.Videocam
        "audio/mpeg", "audio/ogg", "audio/wav" -> Icons.Filled.Audiotrack
        "application/zip", "application/rar" -> Icons.Filled.Archive
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
}



@Preview(showBackground = true)
@Composable
fun LocalFileManagerScreenPreview() {
    DataGrindsetTheme {
        val context = LocalContext.current
        val previewViewModel = remember {
            LocalFileManagerViewModel(context.applicationContext as Application)
        }
        LocalFileManagerScreen(
            navController = NavController(context),
            viewModel = previewViewModel,
            onSelectRootDirectoryClicked = {}
        )
    }
}