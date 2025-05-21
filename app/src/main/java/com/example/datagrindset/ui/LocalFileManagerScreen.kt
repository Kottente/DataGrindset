// In your UI package, e.g., ui/LocalFileManagerScreen.kt

package com.example.datagrindset.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items // Correct import for items in LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.datagrindset.LocalAnalyzableFile // Import your data class
import com.example.datagrindset.ProcessingStatus // Import your enum
import com.example.datagrindset.ui.theme.DataGrindsetTheme // Your app theme
import android.net.Uri // For preview data
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.TextFieldDefaults.outlinedTextFieldColors

// Enum for Sort Order
enum class SortOption {
    BY_NAME_ASC, BY_NAME_DESC,
    BY_DATE_ASC, BY_DATE_DESC,
    BY_SIZE_ASC, BY_SIZE_DESC
}

@OptIn(ExperimentalMaterial3Api::class) // For TopAppBar and other Material 3 components
@Composable
fun LocalFileManagerScreen(
    // ViewModel will be passed here eventually
    // For now, let's use placeholder data and callbacks
    files: List<LocalAnalyzableFile>,
    searchText: String,
    onSearchTextChanged: (String) -> Unit,
    currentSortOption: SortOption,
    onSortOptionSelected: (SortOption) -> Unit,
    onAddFileClicked: () -> Unit,
    onProcessFile: (LocalAnalyzableFile) -> Unit,
    onDeleteFile: (LocalAnalyzableFile) -> Unit
) {
    var showSearchField by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (showSearchField) {
                        OutlinedTextField( // Or BasicTextField for more control
                            value = searchText,
                            onValueChange = onSearchTextChanged,
                            modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                            placeholder = { Text("Search files...") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors( // <<< CORRECTED LINE
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        )
                    } else {
                        Text("DataGrindset")
                    }
                },
                actions = {
                    if (!showSearchField) {
                        IconButton(onClick = { showSearchField = true }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search Files")
                        }
                    } else {
                        // Optionally, a clear button for search or a back button to hide search
                        IconButton(onClick = {
                            showSearchField = false
                            onSearchTextChanged("") // Clear search when hiding
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "Close Search")
                        }
                    }
                    IconButton(onClick = onAddFileClicked) {
                        Icon(Icons.Filled.Add, contentDescription = "Add File")
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer, // Example color
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        if (files.isEmpty() && searchText.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No files added yet. Click the '+' button to add your first file.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (files.isEmpty() && searchText.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No files found matching '$searchText'.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        else {
            LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp) // Add some padding around the list itself
            ) {
                items(files, key = { file -> file.id }) { file -> // Use a stable key
                    LocalFileItem(
                        file = file,
                        onProcess = onProcessFile,
                        onDelete = onDeleteFile
                    )
                }
            }
        }
    }
}




// At the end of LocalFileManagerScreen.kt

@Preview(showBackground = true)
@Composable
fun LocalFileManagerScreenPreview() {
    DataGrindsetTheme { // Apply your app's theme
        val sampleFiles = listOf(
            LocalAnalyzableFile("1", Uri.EMPTY, "Sample Report.csv", 1024 * 200, System.currentTimeMillis() - 100000, "text/csv", ProcessingStatus.NONE),
            LocalAnalyzableFile("2", Uri.EMPTY, "Analysis Data.json", 1024 * 50, System.currentTimeMillis() - 200000, "application/json", ProcessingStatus.SUCCESS, "Completed"),
            LocalAnalyzableFile("3", Uri.EMPTY, "Image File.png", 1024 * 1024 * 2, System.currentTimeMillis() - 300000, "image/png", ProcessingStatus.FAILED, "Unsupported format"),
            LocalAnalyzableFile("4", Uri.EMPTY, "Old Document.pdf", 1024 * 750, System.currentTimeMillis() - 100000000, "application/pdf", ProcessingStatus.PROCESSING)
        )
        var searchText by remember { mutableStateOf("") }
        var sortOption by remember { mutableStateOf(SortOption.BY_DATE_DESC) }

        LocalFileManagerScreen(
            files = sampleFiles
                .filter { it.name.contains(searchText, ignoreCase = true) }
                .sortedWith( // Replace the previous sortedWith block with this:
                    Comparator { fileA: LocalAnalyzableFile, fileB: LocalAnalyzableFile ->
                        val comparisonResult = when (sortOption) {
                            SortOption.BY_NAME_ASC, SortOption.BY_NAME_DESC ->
                                fileA.name.compareTo(fileB.name, ignoreCase = true)
                            SortOption.BY_DATE_ASC, SortOption.BY_DATE_DESC ->
                                fileA.dateModified.compareTo(fileB.dateModified) // Compare as Long
                            SortOption.BY_SIZE_ASC, SortOption.BY_SIZE_DESC ->
                                fileA.size.compareTo(fileB.size) // Compare as Long
                        }

                        // Adjust for descending order
                        if (sortOption.name.endsWith("_DESC")) {
                            -comparisonResult // Invert the result for descending
                        } else {
                            comparisonResult
                        }
                    }
                )
            ,
            searchText = searchText,
            onSearchTextChanged = { searchText = it },
            currentSortOption = sortOption,
            onSortOptionSelected = { sortOption = it },
            onAddFileClicked = { /* TODO */ },
            onProcessFile = { /* TODO */ },
            onDeleteFile = { /* TODO */ }
        )
    }
}

@Preview(showBackground = true, name = "Empty Screen Preview")
@Composable
fun LocalFileManagerScreenEmptyPreview() {
    DataGrindsetTheme {
        var searchText by remember { mutableStateOf("") }
        var sortOption by remember { mutableStateOf(SortOption.BY_DATE_DESC) }
        LocalFileManagerScreen(
            files = emptyList(),
            searchText = searchText,
            onSearchTextChanged = { searchText = it },
            currentSortOption = sortOption,
            onSortOptionSelected = { sortOption = it },
            onAddFileClicked = { /* TODO */ },
            onProcessFile = { /* TODO */ },
            onDeleteFile = { /* TODO */ }
        )
    }
}
