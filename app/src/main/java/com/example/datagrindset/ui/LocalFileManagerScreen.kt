// In ui/LocalFileManagerScreen.kt (and potentially new files for item composables)
package com.example.datagrindset.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.* // Keep all existing icon imports
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.datagrindset.ProcessingStatus
import com.example.datagrindset.ui.theme.DataGrindsetTheme
import com.example.datagrindset.viewmodel.DirectoryEntry // Import the new sealed class
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.net.Uri // For Preview
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.ui.platform.LocalContext
import androidx.documentfile.provider.DocumentFile

// (SortOption enum should already be here or imported)
enum class SortOption {
    BY_NAME_ASC, BY_NAME_DESC,
    BY_DATE_ASC, BY_DATE_DESC,
    BY_SIZE_ASC, BY_SIZE_DESC
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalFileManagerScreen(
    rootUriSelected: Boolean,
    currentPath: String,
    entries: List<DirectoryEntry>,
    searchText: String,
    onSearchTextChanged: (String) -> Unit,
    currentSortOption: SortOption,
    onSortOptionSelected: (SortOption) -> Unit,
    onSelectRootDirectoryClicked: () -> Unit,
    onNavigateToFolder: (DirectoryEntry.FolderEntry) -> Unit,
    onNavigateUp: () -> Unit,
    onProcessFile: (DirectoryEntry.FileEntry) -> Unit,
    onDeleteEntry: (DirectoryEntry) -> Unit
) {
    var showSearchField by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (showSearchField) {
                        OutlinedTextField(
                            value = searchText,
                            onValueChange = onSearchTextChanged,
                            modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                            placeholder = { Text("Search in current folder...") },
                            singleLine = true,
                            colors = TextFieldDefaults.outlinedTextFieldColors()
                        )
                    } else {
                        Text("DataGrindset Browser")
                    }
                },
                navigationIcon = {
                    // Show Up button only if root is selected and not at the very root of the selected tree
                    // The ViewModel's navigateUp() should handle the logic of whether it can go up.
                    // We can enable/disable based on currentPath or a dedicated canNavigateUp boolean from VM.
                    // For now, let's assume if rootUriSelected, we might be able to go up.
                    // A more robust way: viewModel.canNavigateUp.collectAsStateWithLifecycle()
                    if (rootUriSelected && currentPath != DocumentFile.fromTreeUri(LocalContext.current, Uri.parse(currentPath.split("/.../").firstOrNull() ?: currentPath))?.name) { // Simplified check
                        IconButton(onClick = onNavigateUp) {
                            Icon(Icons.Filled.ArrowUpward, contentDescription = "Navigate Up")
                        }
                    }
                },
                actions = {
                    if (rootUriSelected) { // Only show search/sort if a folder is selected
                        if (!showSearchField) {
                            IconButton(onClick = { showSearchField = true }) {
                                Icon(Icons.Filled.Search, contentDescription = "Search Files")
                            }
                        } else {
                            IconButton(onClick = {
                                showSearchField = false
                                onSearchTextChanged("")
                            }) {
                                Icon(Icons.Filled.Close, contentDescription = "Close Search")
                            }
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Sort Options")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                // Sort options remain the same
                                DropdownMenuItem(text = { Text("Sort by Name (A-Z)") }, onClick = { onSortOptionSelected(SortOption.BY_NAME_ASC); showSortMenu = false })
                                // ... other sort options
                                DropdownMenuItem(text = { Text("Sort by Name (Z-A)") }, onClick = { onSortOptionSelected(SortOption.BY_NAME_DESC); showSortMenu = false })
                                DropdownMenuItem(text = { Text("Sort by Date (Newest)") }, onClick = { onSortOptionSelected(SortOption.BY_DATE_DESC); showSortMenu = false })
                                DropdownMenuItem(text = { Text("Sort by Date (Oldest)") }, onClick = { onSortOptionSelected(SortOption.BY_DATE_ASC); showSortMenu = false })
                                DropdownMenuItem(text = { Text("Sort by Size (Largest)") }, onClick = { onSortOptionSelected(SortOption.BY_SIZE_DESC); showSortMenu = false })
                                DropdownMenuItem(text = { Text("Sort by Size (Smallest)") }, onClick = { onSortOptionSelected(SortOption.BY_SIZE_ASC); showSortMenu = false })
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (rootUriSelected) {
                // Display current path
                Text(
                    text = "Current: $currentPath",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (entries.isEmpty() && searchText.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("This folder is empty.", style = MaterialTheme.typography.bodyLarge)
                    }
                } else if (entries.isEmpty() && searchText.isNotEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("No items found matching '$searchText'.", style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(entries, key = { entry -> entry.id }) { entry ->
                            when (entry) {
                                is DirectoryEntry.FileEntry -> FileListItem(
                                    fileEntry = entry,
                                    onProcess = { onProcessFile(entry) },
                                    onDelete = { onDeleteEntry(entry) }
                                )
                                is DirectoryEntry.FolderEntry -> FolderListItem(
                                    folderEntry = entry,
                                    onClick = { onNavigateToFolder(entry) },
                                    onDelete = { onDeleteEntry(entry) }
                                )
                            }
                        }
                    }
                }
            } else {
                // Prompt to select a root directory
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Please select a root folder to browse.",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(16.dp)
                        )
                        Button(onClick = onSelectRootDirectoryClicked) {
                            Text("Select Root Folder")
                        }
                    }
                }
            }
        }
    }
}

// --- New Item Composable for Folders ---
@Composable
fun FolderListItem(
    folderEntry: DirectoryEntry.FolderEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit, // Optional: allow deleting folders
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = "Folder",
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(folderEntry.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${folderEntry.childCount} items", style = MaterialTheme.typography.bodySmall) // Optional child count
            }
            // Optional: Delete button for folders
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete Folder", tint = MaterialTheme.colorScheme.error)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = "Open folder")
        }
    }
}

// --- Modified Item Composable for Files (was LocalFileItem) ---
@Composable
fun FileListItem(
    fileEntry: DirectoryEntry.FileEntry,
    onProcess: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = getIconForMimeType(fileEntry.mimeType), // Assuming getIconForMimeType is defined
                contentDescription = "File type",
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(fileEntry.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Size: ${formatFileSize(fileEntry.size)}", style = MaterialTheme.typography.bodySmall)
                    Text("Modified: ${formatDate(fileEntry.dateModified)}", style = MaterialTheme.typography.bodySmall)
                }
                // Processing status (if you re-add it to FileEntry or manage it via ViewModel map)
                if (fileEntry.processingStatus != ProcessingStatus.NONE || fileEntry.processingResultSummary != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProcessingStatusIndicator(fileEntry.processingStatus) // Assuming this is defined
                        fileEntry.processingResultSummary?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = FontStyle.Italic,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }
            }
            IconButton(onClick = onProcess) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Process File", tint = MaterialTheme.colorScheme.secondary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete File", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// Helper functions (getIconForMimeType, formatFileSize, formatDate, ProcessingStatusIndicator)
// should be kept or moved to a common place if not already.
// Make sure they are accessible here. For brevity, I'm not repeating them.
// Example stubs if they are missing:
fun getIconForMimeType(mimeType: String?): ImageVector {
    return when (mimeType?.lowercase()) {
        "text/csv" -> Icons.Filled.Description
        "application/json" -> Icons.Filled.DataObject
        "application/pdf" -> Icons.Filled.PictureAsPdf
        "image/jpeg", "image/png" -> Icons.Filled.Image
        else -> Icons.Filled.InsertDriveFile
    }
}
fun formatFileSize(sizeInBytes: Long): String {
    if (sizeInBytes < 1024) return "$sizeInBytes B"
    val kb = sizeInBytes / 1024; if (kb < 1024) return "$kb KB"
    val mb = kb / 1024; if (mb < 1024) return "$mb MB"
    return "${mb / 1024} GB"
}
fun formatDate(timestamp: Long): String = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(timestamp))

@Composable
fun ProcessingStatusIndicator(status: ProcessingStatus) {
    val (icon, color) = when (status) {
        ProcessingStatus.PENDING -> Icons.Filled.HourglassEmpty to MaterialTheme.colorScheme.onSurfaceVariant
        ProcessingStatus.PROCESSING -> Icons.Filled.Sync to MaterialTheme.colorScheme.secondary
        ProcessingStatus.SUCCESS -> Icons.Filled.CheckCircle to Color(0xFF2E7D32) // Example Green
        ProcessingStatus.FAILED -> Icons.Filled.Error to MaterialTheme.colorScheme.error
        ProcessingStatus.NONE -> return
    }
    Icon(imageVector = icon, contentDescription = "Status: $status", tint = color, modifier = Modifier.size(18.dp))
}

// --- Preview ---
@Preview(showBackground = true, name = "File Manager Screen - No Root Selected")
@Composable
fun LocalFileManagerScreenNoRootPreview() {
    DataGrindsetTheme {
        LocalFileManagerScreen(
            rootUriSelected = false,
            currentPath = "No folder selected",
            entries = emptyList(),
            searchText = "",
            onSearchTextChanged = {},
            currentSortOption = SortOption.BY_NAME_ASC,
            onSortOptionSelected = {},
            onSelectRootDirectoryClicked = {},
            onNavigateToFolder = {},
            onNavigateUp = {},
            onProcessFile = {},
            onDeleteEntry = {}
        )
    }
}

@Preview(showBackground = true, name = "File Manager Screen - With Root")
@Composable
fun LocalFileManagerScreenWithRootPreview() {
    DataGrindsetTheme {
        val sampleEntries = listOf(
            DirectoryEntry.FolderEntry("folder1", "Work Documents", Uri.EMPTY, 3),
            DirectoryEntry.FileEntry("file1", "Report Q1.csv", Uri.EMPTY, 12345, System.currentTimeMillis() - 100000, "text/csv", ProcessingStatus.SUCCESS, "Done"),
            DirectoryEntry.FolderEntry("folder2", "Images", Uri.EMPTY, 1),
            DirectoryEntry.FileEntry("file2", "Data Analysis.json", Uri.EMPTY, 67890, System.currentTimeMillis() - 200000, "application/json")
        )
        LocalFileManagerScreen(
            rootUriSelected = true,
            currentPath = "My Phone/.../Selected Folder",
            entries = sampleEntries,
            searchText = "",
            onSearchTextChanged = {},
            currentSortOption = SortOption.BY_NAME_ASC,
            onSortOptionSelected = {},
            onSelectRootDirectoryClicked = {},
            onNavigateToFolder = {},
            onNavigateUp = {},
            onProcessFile = {},
            onDeleteEntry = {}
        )
    }
}
