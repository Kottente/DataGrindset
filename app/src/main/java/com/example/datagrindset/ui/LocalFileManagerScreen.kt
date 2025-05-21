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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*

import androidx.compose.ui.platform.LocalContext // For preview, if needed for DocumentFile

import androidx.compose.ui.unit.dp
import com.example.datagrindset.ui.theme.DataGrindsetTheme


// (SortOption enum should already be here or imported)
enum class SortOption {
    BY_NAME_ASC, BY_NAME_DESC,
    BY_DATE_ASC, BY_DATE_DESC,
    BY_SIZE_ASC, BY_SIZE_DESC
}

// In ui/LocalFileManagerScreen.kt



// (SortOption enum should already be here or imported)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalFileManagerScreen(
    rootUriSelected: Boolean,
    canNavigateUp: Boolean, // New parameter
    currentPath: String,
    entries: List<DirectoryEntry>,
    fileProcessingStatusMap: Map<Uri, Pair<ProcessingStatus, String?>>, // New parameter
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
                            value = searchText, // Provide the searchText state
                            onValueChange = onSearchTextChanged, // Provide the callback
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 8.dp) // Keep padding if desired
                                .height(50.dp), // Adjust height to better fit TopAppBar
                            placeholder = { Text("Search in current folder...") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors( // Optional: customize colors
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            ),
                            keyboardActions = KeyboardActions(onSearch = {
                                // Optionally hide keyboard or trigger search action if needed
                            }),
                            textStyle = MaterialTheme.typography.bodyLarge // Ensure text size is appropriate
                        )
                    } else {
                        Text("DataGrindset Browser")
                    }
                },
                navigationIcon = {
                    if (rootUriSelected && canNavigateUp) { // Use canNavigateUp state
                        IconButton(onClick = onNavigateUp) {
                            Icon(Icons.Filled.ArrowUpward, contentDescription = "Navigate Up")
                        }
                    }
                },
                actions = {
                    if (rootUriSelected) {
                        // Search and Sort icons
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
                                DropdownMenuItem(text = { Text("Sort by Name (A-Z)") }, onClick = { onSortOptionSelected(SortOption.BY_NAME_ASC); showSortMenu = false })
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
                Text(
                    text = "Current: $currentPath",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (entries.isEmpty() && searchText.isEmpty()) { /* ... Empty folder message ... */ }
                else if (entries.isEmpty() && searchText.isNotEmpty()) { /* ... No search results message ... */ }
                else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(entries, key = { entry -> entry.id }) { entry ->
                            when (entry) {
                                is DirectoryEntry.FileEntry -> {
                                    val statusPair = fileProcessingStatusMap[entry.uri] // Get status from map
                                    FileListItem(
                                        fileEntry = entry,
                                        processingStatus = statusPair?.first ?: ProcessingStatus.NONE,
                                        processingSummary = statusPair?.second,
                                        onProcess = { onProcessFile(entry) },
                                        onDelete = { onDeleteEntry(entry) }
                                    )
                                }
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

// FolderListItem remains the same as before
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
                Text("${folderEntry.childCount} items", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDelete) { // Keep delete for folders for now
                Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete Folder", tint = MaterialTheme.colorScheme.error)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = "Open folder")
        }
    }
}


// --- Modified Item Composable for Files ---
@Composable
fun FileListItem(
    fileEntry: DirectoryEntry.FileEntry,
    processingStatus: ProcessingStatus, // New parameter
    processingSummary: String?,        // New parameter
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
                imageVector = getIconForMimeType(fileEntry.mimeType),
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
                if (processingStatus != ProcessingStatus.NONE || processingSummary != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProcessingStatusIndicator(processingStatus) // Use the passed status
                        processingSummary?.let {
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
// (Assuming they are already defined as in the previous step)
fun getIconForMimeType(mimeType: String?): ImageVector { /* ... */ return Icons.AutoMirrored.Filled.InsertDriveFile
}
fun formatFileSize(sizeInBytes: Long): String { /* ... */ return "$sizeInBytes B" }
fun formatDate(timestamp: Long): String = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(timestamp))

@Composable
fun ProcessingStatusIndicator(status: ProcessingStatus) { /* ... */ }


// --- Updated Previews ---
@Preview(showBackground = true, name = "File Manager Screen - No Root Selected")
@Composable
fun LocalFileManagerScreenNoRootPreview() {
    DataGrindsetTheme {
        LocalFileManagerScreen(
            rootUriSelected = false,
            canNavigateUp = false, // Added
            currentPath = "No folder selected",
            entries = emptyList(),
            fileProcessingStatusMap = emptyMap(), // Added
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
        val sampleFileUri1 = Uri.parse("file://dummy/Report_Q1.csv")
        val sampleFileUri2 = Uri.parse("file://dummy/Data_Analysis.json")

        val sampleEntries = listOf(
            DirectoryEntry.FolderEntry("folder1", "Work Documents", Uri.parse("folder://dummy/work"), 3),
            DirectoryEntry.FileEntry("file1", "Report Q1.csv", sampleFileUri1, 12345, System.currentTimeMillis() - 100000, "text/csv"),
            DirectoryEntry.FolderEntry("folder2", "Images", Uri.parse("folder://dummy/images"), 1),
            DirectoryEntry.FileEntry("file2", "Data Analysis.json", sampleFileUri2, 67890, System.currentTimeMillis() - 200000, "application/json")
        )
        val sampleStatusMap = mapOf(
            sampleFileUri1 to (ProcessingStatus.SUCCESS to "Done"),
            sampleFileUri2 to (ProcessingStatus.PENDING to "Queued")
        )
        LocalFileManagerScreen(
            rootUriSelected = true,
            canNavigateUp = true, // Added, assuming we are in a subfolder for preview
            currentPath = "Selected Folder > Work Documents",
            entries = sampleEntries,
            fileProcessingStatusMap = sampleStatusMap, // Added
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