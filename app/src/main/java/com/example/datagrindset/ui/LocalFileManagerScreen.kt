// In ui/LocalFileManagerScreen.kt
package com.example.datagrindset.ui

import android.net.Uri
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
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
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
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.datagrindset.ProcessingStatus
import com.example.datagrindset.ui.theme.DataGrindsetTheme
import com.example.datagrindset.viewmodel.DirectoryEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Assuming SortOption enum is defined elsewhere and imported, e.g.:
// package com.example.datagrindset.ui
enum class SortOption {
    BY_NAME_ASC, BY_NAME_DESC,
    BY_DATE_ASC, BY_DATE_DESC,
    BY_SIZE_ASC, BY_SIZE_DESC
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalFileManagerScreen(
    rootUriSelected: Boolean,
    canNavigateUp: Boolean,
    currentPath: String,
    entries: List<DirectoryEntry>,
    fileProcessingStatusMap: Map<Uri, Pair<ProcessingStatus, String?>>,
    searchText: String,
    onSearchTextChanged: (String) -> Unit,
    currentSortOption: SortOption,
    onSortOptionSelected: (SortOption) -> Unit,
    onSelectRootDirectoryClicked: () -> Unit, // Used for initial selection AND changing root
    onNavigateToFolder: (DirectoryEntry.FolderEntry) -> Unit,
    onNavigateUp: () -> Unit,
    navigateToAnalysisTarget: DirectoryEntry.FileEntry?, // From ViewModel to trigger navigation
    onDidNavigateToAnalysisScreen: () -> Unit,          // Callback to ViewModel after navigation
    onPrepareFileForAnalysis: (DirectoryEntry.FileEntry) -> Unit, // Renamed from onProcessFile
    onDeleteEntry: (DirectoryEntry) -> Unit,
    navController: NavController
    // navController: NavController, // Example: Pass NavController for actual navigation
) {
    var showSearchField by remember { mutableStateOf(false) }
    var showSortAndOptionsKebabMenu by remember { mutableStateOf(false) }

    // Effect to handle navigation when navigateToAnalysisTarget changes
    LaunchedEffect(navigateToAnalysisTarget) {
        navigateToAnalysisTarget?.let { fileEntry ->
            val mimeType = fileEntry.mimeType?.lowercase()
            // This is where you would typically use your NavController

            val uriString = fileEntry.uri.toString()
            val route = when (mimeType) {
                "text/plain", "text/markdown" -> "txtAnalysisScreen/${Uri.encode(uriString)}" // Encode the whole URI string once
                "text/csv" -> "csvAnalysisScreen/${Uri.encode(uriString)}"
                else -> null
            }
            route?.let {
                navController.navigate(it) // Use the passed NavController
            }
            onDidNavigateToAnalysisScreen()

            if (route != null) {
                println("UI: Would navigate to $route for file: ${fileEntry.name}")
                // Example: navController.navigate(route)
                // Ensure your NavHost is set up in MainActivity or a higher-level composable
            }
            // Always call this to reset the signal, even if no route is defined here,
            // as the ViewModel might have set a status like UNSUPPORTED.
            onDidNavigateToAnalysisScreen()
        }
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
                            placeholder = { Text("Search in current folder...") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        )
                    } else {
                        Text("DataGrindset Browser")
                    }
                },
                navigationIcon = {
                    if (rootUriSelected && canNavigateUp) {
                        IconButton(onClick = onNavigateUp) {
                            Icon(Icons.Filled.ArrowUpward, contentDescription = "Navigate Up")
                        }
                    }
                },
                actions = {
                    if (rootUriSelected) {
                        // Search Icon
                        if (!showSearchField) {
                            IconButton(onClick = { showSearchField = true }) {
                                Icon(Icons.Filled.Search, contentDescription = "Search Files")
                            }
                        } else {
                            IconButton(onClick = {
                                showSearchField = false
                                onSearchTextChanged("") // Clear search text when closing
                            }) {
                                Icon(Icons.Filled.Close, contentDescription = "Close Search")
                            }
                        }
                        // Kebab menu for Sort and other options
                        Box {
                            IconButton(onClick = { showSortAndOptionsKebabMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More Options")
                            }
                            DropdownMenu(
                                expanded = showSortAndOptionsKebabMenu,
                                onDismissRequest = { showSortAndOptionsKebabMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Change Root Folder") },
                                    onClick = {
                                        onSelectRootDirectoryClicked() // Re-use the same callback
                                        showSortAndOptionsKebabMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.Filled.FolderOpen, contentDescription = "Change Root Folder") }
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                Text(
                                    "Sort by",
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                DropdownMenuItem(text = { Text("Name (A-Z)") }, onClick = { onSortOptionSelected(SortOption.BY_NAME_ASC); showSortAndOptionsKebabMenu = false })
                                DropdownMenuItem(text = { Text("Name (Z-A)") }, onClick = { onSortOptionSelected(SortOption.BY_NAME_DESC); showSortAndOptionsKebabMenu = false })
                                DropdownMenuItem(text = { Text("Date (Newest)") }, onClick = { onSortOptionSelected(SortOption.BY_DATE_DESC); showSortAndOptionsKebabMenu = false })
                                DropdownMenuItem(text = { Text("Date (Oldest)") }, onClick = { onSortOptionSelected(SortOption.BY_DATE_ASC); showSortAndOptionsKebabMenu = false })
                                DropdownMenuItem(text = { Text("Size (Largest)") }, onClick = { onSortOptionSelected(SortOption.BY_SIZE_DESC); showSortAndOptionsKebabMenu = false })
                                DropdownMenuItem(text = { Text("Size (Smallest)") }, onClick = { onSortOptionSelected(SortOption.BY_SIZE_ASC); showSortAndOptionsKebabMenu = false })
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

                if (entries.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (searchText.isEmpty()) "This folder is empty." else "No items found matching '$searchText'.",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        items(entries, key = { entry -> entry.id }) { entry ->
                            when (entry) {
                                is DirectoryEntry.FileEntry -> {
                                    val statusPair = fileProcessingStatusMap[entry.uri]
                                    FileListItem(
                                        fileEntry = entry,
                                        processingStatus = statusPair?.first ?: ProcessingStatus.NONE,
                                        processingSummary = statusPair?.second,
                                        onProcess = { onPrepareFileForAnalysis(entry) },
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
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(
                            imageVector = Icons.Filled.FolderOpen,
                            contentDescription = "Select Folder Icon",
                            modifier = Modifier.size(64.dp).padding(bottom = 16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
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
            .padding(vertical = 4.dp) // Removed horizontal padding from here, add to LazyColumn
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
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(folderEntry.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${folderEntry.childCount} items", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete Folder", tint = MaterialTheme.colorScheme.error)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = "Open folder", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun FileListItem(
    fileEntry: DirectoryEntry.FileEntry,
    processingStatus: ProcessingStatus,
    processingSummary: String?,
    onProcess: () -> Unit, // This is actually onPrepareFileForAnalysis
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp), // Removed horizontal padding
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
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(fileEntry.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)) {
                    Text("Size: ${formatFileSize(fileEntry.size)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Modified: ${formatDate(fileEntry.dateModified)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (processingStatus != ProcessingStatus.NONE || processingSummary != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProcessingStatusIndicator(processingStatus)
                        processingSummary?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = FontStyle.Italic,
                                modifier = Modifier.padding(start = 6.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onProcess, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Analyze File", tint = MaterialTheme.colorScheme.tertiary)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete File", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

fun getIconForMimeType(mimeType: String?): ImageVector {
    return when (mimeType?.lowercase()?.substringBefore('/')) { // Check primary type
        "text" -> when(mimeType.lowercase()){
            "text/csv" -> Icons.Filled.Description // More specific for CSV
            else -> Icons.Filled.Description // General text
        }
        "application" -> when (mimeType.lowercase()) {
            "application/json" -> Icons.Filled.DataObject
            "application/pdf" -> Icons.Filled.PictureAsPdf
            else -> Icons.AutoMirrored.Filled.InsertDriveFile // General application files
        }
        "image" -> Icons.Filled.Image
        // Add more primary types like "audio", "video" if needed
        else -> Icons.AutoMirrored.Filled.InsertDriveFile // Default for unknown or other types
    }
}

fun formatFileSize(sizeInBytes: Long): String {
    if (sizeInBytes < 0) return "N/A" // Folders might pass 0 or -1 from DocumentFile.length()
    if (sizeInBytes < 1024) return "$sizeInBytes B"
    val kb = sizeInBytes / 1024.0
    if (kb < 1024) return String.format(Locale.getDefault(), "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.getDefault(), "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.getDefault(), "%.1f GB", gb)
}

fun formatDate(timestamp: Long): String {
    if (timestamp <= 0) return "Unknown date"
    return try {
        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(timestamp))
    } catch (e: Exception) {
        "Invalid date"
    }
}

@Composable
fun ProcessingStatusIndicator(status: ProcessingStatus) {
    val (icon: ImageVector, color: Color, description: String) = when (status) {
        ProcessingStatus.PENDING -> Triple(Icons.Filled.HourglassEmpty, MaterialTheme.colorScheme.onSurfaceVariant, "Pending")
        ProcessingStatus.PROCESSING -> Triple(Icons.Filled.Sync, MaterialTheme.colorScheme.secondary, "Processing")
        ProcessingStatus.SUCCESS -> Triple(Icons.Filled.CheckCircle, Color(0xFF2E7D32), "Success") // Consider theme color
        ProcessingStatus.FAILED -> Triple(Icons.Filled.Error, MaterialTheme.colorScheme.error, "Failed")
        ProcessingStatus.UNSUPPORTED -> Triple(Icons.Filled.Error, MaterialTheme.colorScheme.onSurfaceVariant, "Unsupported")
        ProcessingStatus.NONE -> return // Don't show anything for NONE
    }
    Icon(imageVector = icon, contentDescription = "Status: $description", tint = color, modifier = Modifier.size(18.dp))
}

// --- Previews ---
@Preview(showBackground = true, name = "File Manager - No Root Selected")
@Composable
fun LocalFileManagerScreenNoRootPreview() {
    DataGrindsetTheme {
        LocalFileManagerScreen(
            rootUriSelected = false,
            canNavigateUp = false,
            currentPath = "No folder selected",
            entries = emptyList(),
            fileProcessingStatusMap = emptyMap(),
            searchText = "",
            onSearchTextChanged = {},
            currentSortOption = SortOption.BY_NAME_ASC,
            onSortOptionSelected = {},
            onSelectRootDirectoryClicked = {},
            onNavigateToFolder = {},
            onNavigateUp = {},
            navigateToAnalysisTarget = null,
            onDidNavigateToAnalysisScreen = {},
            onPrepareFileForAnalysis = {},
            onDeleteEntry = {},
            navController = rememberNavController()
        )
    }
}

@Preview(showBackground = true, name = "File Manager - With Root & Items")
@Composable
fun LocalFileManagerScreenWithRootPreview() {
    DataGrindsetTheme {
        val context = LocalContext.current
        val dummyFileUri1 = Uri.parse("content://com.example.dummyprovider/document/file1.csv")
        val dummyFileUri2 = Uri.parse("content://com.example.dummyprovider/document/data.json")
        val dummyFolderUri1 = Uri.parse("content://com.example.dummyprovider/tree/folder1/document/folder1")


        val sampleEntries = listOf(
            DirectoryEntry.FolderEntry("folder_id_1", "Work Documents", dummyFolderUri1, 3),
            DirectoryEntry.FileEntry("file_id_1", "Report Q1 2025.csv", dummyFileUri1, 12345, System.currentTimeMillis() - 100000000, "text/csv"),
            DirectoryEntry.FileEntry("file_id_2", "User Data Backup Long Name For Testing Ellipsis.json", dummyFileUri2, 6789000, System.currentTimeMillis() - 20000000, "application/json")
        )
        val sampleStatusMap = mapOf(
            dummyFileUri1 to (ProcessingStatus.SUCCESS to "Ready"),
            dummyFileUri2 to (ProcessingStatus.PENDING to "Queued for analysis")
        )
        LocalFileManagerScreen(
            rootUriSelected = true,
            canNavigateUp = true,
            currentPath = "My Phone Storage > Documents > Reports",
            entries = sampleEntries,
            fileProcessingStatusMap = sampleStatusMap,
            searchText = "",
            onSearchTextChanged = {},
            currentSortOption = SortOption.BY_DATE_DESC,
            onSortOptionSelected = {},
            onSelectRootDirectoryClicked = {},
            onNavigateToFolder = {},
            onNavigateUp = {},
            navigateToAnalysisTarget = null,
            onDidNavigateToAnalysisScreen = {},
            onPrepareFileForAnalysis = {},
            onDeleteEntry = {},
            navController = rememberNavController()
        )
    }
}

@Preview(showBackground = true, name = "File Manager - Empty Folder")
@Composable
fun LocalFileManagerScreenEmptyFolderPreview() {
    DataGrindsetTheme {
        LocalFileManagerScreen(
            rootUriSelected = true,
            canNavigateUp = true,
            currentPath = "My Phone Storage > Empty Folder",
            entries = emptyList(),
            fileProcessingStatusMap = emptyMap(),
            searchText = "",
            onSearchTextChanged = {},
            currentSortOption = SortOption.BY_NAME_ASC,
            onSortOptionSelected = {},
            onSelectRootDirectoryClicked = {},
            onNavigateToFolder = {},
            onNavigateUp = {},
            navigateToAnalysisTarget = null,
            onDidNavigateToAnalysisScreen = {},
            onPrepareFileForAnalysis = {},
            onDeleteEntry = {},
            navController = rememberNavController()
        )
    }
}