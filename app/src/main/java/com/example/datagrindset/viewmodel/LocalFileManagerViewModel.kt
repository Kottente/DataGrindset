// In viewmodel/LocalFileManagerViewModel.kt
package com.example.datagrindset.viewmodel

import android.app.Application
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract // For DocumentFile operations
import androidx.documentfile.provider.DocumentFile // Key class for OpenDocumentTree
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.datagrindset.ProcessingStatus
import com.example.datagrindset.ui.SortOption // Keep if sorting is still desired for current dir
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

// New sealed class to represent entries in the directory browser
sealed class DirectoryEntry {
    abstract val id: String
    abstract val name: String
    abstract val uri: Uri
    abstract val isDirectory: Boolean

    data class FileEntry(
        override val id: String,
        override val name: String,
        override val uri: Uri,
        val size: Long,
        val dateModified: Long,
        val mimeType: String?,
        var processingStatus: ProcessingStatus = ProcessingStatus.NONE,
        var processingResultSummary: String? = null
    ) : DirectoryEntry() {
        override val isDirectory: Boolean = false
    }

    data class FolderEntry(
        override val id: String,
        override val name: String,
        override val uri: Uri,
        val childCount: Int // Optional: good to know if it has children
    ) : DirectoryEntry() {
        override val isDirectory: Boolean = true
    }
}


class LocalFileManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val contentResolver: ContentResolver = application.contentResolver

    // Holds the URI of the root directory selected by the user
    private val _rootTreeUri = MutableStateFlow<Uri?>(null)
    val rootTreeUri: StateFlow<Uri?> = _rootTreeUri.asStateFlow()

    // Represents the current directory being browsed (can be root or a subfolder)
    // We'll store the path as a list of DocumentFile URIs to reconstruct the path for navigation
    private val _currentPathSegments = MutableStateFlow<List<Uri>>(emptyList())
    val currentPathDisplay: StateFlow<String> = combine(_rootTreeUri, _currentPathSegments) { root, segments ->
        if (root == null) "No folder selected"
        else {
            val rootName = DocumentFile.fromTreeUri(getApplication(), root)?.name ?: "Root"
            if (segments.isEmpty() || segments.last() == root) {
                rootName
            } else {
                // This needs more robust name extraction if segments are not just direct children
                // For now, let's assume segments help build a relative path.
                // A better way is to store DocumentFile instances or their names directly.
                // For simplicity now, just show the last segment's name if available.
                val currentFolderName = DocumentFile.fromSingleUri(getApplication(), segments.last())?.name ?: "..."
                "$rootName/.../$currentFolderName" // Simplified path display
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "No folder selected")


    private val _searchText = MutableStateFlow("") // Can still be used to filter current directory
    val searchText: StateFlow<String> = _searchText.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.BY_NAME_ASC) // Default sort
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    // This will hold the files and folders for the current path
    val directoryEntries: StateFlow<List<DirectoryEntry>> =
        combine(_currentPathSegments, _searchText, _sortOption) { currentSegments, text, sort ->
            val currentFolderUri = currentSegments.lastOrNull() ?: return@combine emptyList()
            val currentDirectory = DocumentFile.fromTreeUri(getApplication(), currentFolderUri)
                ?: DocumentFile.fromSingleUri(getApplication(), currentFolderUri)


            if (currentDirectory == null || !currentDirectory.isDirectory) {
                return@combine emptyList()
            }

            val entries = currentDirectory.listFiles().mapNotNull { docFile ->
                if (docFile.isDirectory) {
                    DirectoryEntry.FolderEntry(
                        id = docFile.uri.toString(), // URI is a good unique ID
                        name = docFile.name ?: "Unnamed Folder",
                        uri = docFile.uri,
                        childCount = docFile.listFiles().size // Can be expensive, consider alternatives
                    )
                } else {
                    DirectoryEntry.FileEntry(
                        id = docFile.uri.toString(),
                        name = docFile.name ?: "Unnamed File",
                        uri = docFile.uri,
                        size = docFile.length(),
                        dateModified = docFile.lastModified(),
                        mimeType = docFile.type
                        // processingStatus will need to be managed separately, perhaps in a map
                    )
                }
            }

            val filteredList = if (text.isBlank()) {
                entries
            } else {
                entries.filter { it.name.contains(text, ignoreCase = true) }
            }

            // Apply sorting
            // Ensure folders are typically listed before files or grouped
            val sortedList = filteredList.sortedWith(
                // Primary sort: folders first, then files
                compareBy<DirectoryEntry> { !it.isDirectory } // Folders (false) before files (true)
                    .thenApplySortOption(sort) // Apply the selected sort option as secondary criteria
            )
            sortedList

        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())


    init {
        // TODO: Load persisted rootTreeUri if available (e.g., from SharedPreferences)
        // For now, we start with null, meaning user needs to select a folder.
        // Example:
        // val persistedUriString = application.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).getString("root_tree_uri", null)
        // persistedUriString?.let { _rootTreeUri.value = Uri.parse(it); _currentPathSegments.value = listOf(Uri.parse(it)) }
    }

    fun setRootTreeUri(uri: Uri) {
        // Persist read permissions
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            _rootTreeUri.value = uri
            _currentPathSegments.value = listOf(uri) // Start browsing at the root

            // TODO: Persist this URI string (e.g., in SharedPreferences) so it can be loaded next time
            // getApplication<Application>().getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit().putString("root_tree_uri", uri.toString()).apply()

        } catch (e: SecurityException) {
            // Handle error - user might not have selected a valid tree or permission failed
            // You might want to show a message to the user
            _rootTreeUri.value = null // Clear if failed
            _currentPathSegments.value = emptyList()
        }
    }

    fun navigateTo(folderEntry: DirectoryEntry.FolderEntry) {
        // We need to ensure the folderEntry.uri is usable for DocumentFile.fromTreeUri or fromSingleUri
        // If folderEntry.uri is a child document URI, we can just add it.
        // For simplicity, let's assume it's a direct child and its URI is self-contained for listing.
        // This might need refinement based on how DocumentFile URIs are structured for children.
        _currentPathSegments.value = _currentPathSegments.value + folderEntry.uri

    }

    fun navigateUp() {
        if (_currentPathSegments.value.size > 1) { // Can only go up if not at the root of the selected tree
            _currentPathSegments.value = _currentPathSegments.value.dropLast(1)
        }
    }


    fun onSearchTextChanged(text: String) {
        _searchText.value = text
    }

    fun onSortOptionSelected(sort: SortOption) {
        _sortOption.value = sort
    }

    // Process file logic will now take a FileEntry
    fun processFile(fileEntry: DirectoryEntry.FileEntry) {
        // Update status (need to manage status for FileEntries, maybe in a map by URI)
        // viewModelScope.launch { ... }
        // Similar to before, but using fileEntry.uri
        println("Processing file: ${fileEntry.name} at ${fileEntry.uri}")
    }

    // Delete file logic will also take a DirectoryEntry
    // Deleting via DocumentFile API: DocumentFile.fromSingleUri(context, entry.uri)?.delete()
    fun deleteEntry(entry: DirectoryEntry) {
        viewModelScope.launch {
            val documentFile = DocumentFile.fromSingleUri(getApplication(), entry.uri)
            val success = documentFile?.delete() ?: false
            if (success) {
                // Refresh the list by re-triggering the flow (e.g., by re-setting current path or a refresh signal)
                // A simple way: slightly change the current path to force re-collection, then revert if needed
                // Or, have a dedicated refresh trigger.
                // For now, the combine should re-evaluate if _currentPathSegments is updated.
                // We might need to explicitly re-fetch.
                val currentSegments = _currentPathSegments.value
                _currentPathSegments.value = emptyList() // Force a temporary change to trigger recomposition
                _currentPathSegments.value = currentSegments
            } else {
                // Handle deletion failure
                println("Failed to delete ${entry.name}")
            }
        }
    }

    // TODO: Manage processing status for FileEntry objects (e.g., using a Map<Uri, ProcessingStatus>)
    // TODO: Implement persistence for _rootTreeUri using SharedPreferences.
    // TODO: More robust current path display and navigation logic for _currentPathSegments.
}

private fun <T> Comparator<T>.thenApplySortOption(sortOption: SortOption): Comparator<T> where T : DirectoryEntry {
    val comparator = when (sortOption) {
        SortOption.BY_NAME_ASC -> compareBy<T> { it.name.lowercase() }
        SortOption.BY_NAME_DESC -> compareByDescending<T> { it.name.lowercase() }
        SortOption.BY_DATE_ASC -> compareBy<T> {
            if (it is DirectoryEntry.FileEntry) it.dateModified else Long.MAX_VALUE // Files by date, folders last
        }
        SortOption.BY_DATE_DESC -> compareByDescending<T> {
            if (it is DirectoryEntry.FileEntry) it.dateModified else Long.MIN_VALUE // Files by date (reversed), folders first (reversed)
        }
        SortOption.BY_SIZE_ASC -> compareBy<T> {
            if (it is DirectoryEntry.FileEntry) it.size else Long.MAX_VALUE // Files by size, folders last
        }
        SortOption.BY_SIZE_DESC -> compareByDescending<T> {
            if (it is DirectoryEntry.FileEntry) it.size else Long.MIN_VALUE // Files by size (reversed), folders first (reversed)
        }
    }
    return this.thenComparing(comparator)
}

//package com.example.datagrindset.viewmodel
//
//// In a new file, e.g., viewmodel/LocalFileManagerViewModel.kt
//
//import android.app.Application
//import android.content.ContentResolver
//import android.net.Uri
//import android.provider.OpenableColumns
//import androidx.lifecycle.AndroidViewModel
//import androidx.lifecycle.viewModelScope
//import com.example.datagrindset.LocalAnalyzableFile
//import com.example.datagrindset.ProcessingStatus
//import com.example.datagrindset.ui.SortOption // Assuming SortOption is in ui package
//import kotlinx.coroutines.flow.MutableStateFlow
//import kotlinx.coroutines.flow.SharingStarted
//import kotlinx.coroutines.flow.StateFlow
//import kotlinx.coroutines.flow.asStateFlow
//import kotlinx.coroutines.flow.combine
//import kotlinx.coroutines.flow.stateIn
//import kotlinx.coroutines.launch
//import java.util.UUID
//
//class LocalFileManagerViewModel(application: Application) : AndroidViewModel(application) {
//
//    private val _allFiles = MutableStateFlow<List<LocalAnalyzableFile>>(emptyList())
//
//    private val _searchText = MutableStateFlow("")
//    val searchText: StateFlow<String> = _searchText.asStateFlow()
//
//    private val _sortOption = MutableStateFlow(SortOption.BY_DATE_DESC)
//    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()
//
//    // This will hold the filtered and sorted list for the UI
//    val files: StateFlow<List<LocalAnalyzableFile>> =
//        combine(_allFiles, _searchText, _sortOption) { files, text, sort ->
//            val filteredList = if (text.isBlank()) {
//                files
//            } else {
//                files.filter { it.name.contains(text, ignoreCase = true) }
//            }
//            // Apply sorting (simplified example)
//            when (sort) {
//                SortOption.BY_NAME_ASC -> filteredList.sortedBy { it.name.lowercase() }
//                SortOption.BY_NAME_DESC -> filteredList.sortedByDescending { it.name.lowercase() }
//                SortOption.BY_DATE_ASC -> filteredList.sortedBy { it.dateModified }
//                SortOption.BY_DATE_DESC -> filteredList.sortedByDescending { it.dateModified }
//                SortOption.BY_SIZE_ASC -> filteredList.sortedBy { it.size }
//                SortOption.BY_SIZE_DESC -> filteredList.sortedByDescending { it.size }
//            }
//        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
//
//    // --- Public Methods for UI to call ---
//
//    fun onSearchTextChanged(text: String) {
//        _searchText.value = text
//    }
//
//    fun onSortOptionSelected(sort: SortOption) {
//        _sortOption.value = sort
//    }
//
//    fun addFile(uri: Uri) {
//        viewModelScope.launch {
//            // Extract metadata from URI
//            val contentResolver = getApplication<Application>().contentResolver
//            val name = getFileName(contentResolver, uri) ?: "Unknown File"
//            val size = getFileSize(contentResolver, uri) ?: 0L
//            val lastModified = System.currentTimeMillis() // Placeholder, ideally from URI if possible or when app "adds" it
//            val mimeType = contentResolver.getType(uri)
//
//            val newFile = LocalAnalyzableFile(
//                id = UUID.randomUUID().toString(),
//                uri = uri,
//                name = name,
//                size = size,
//                dateModified = lastModified, // Or use actual file modified date if accessible
//                mimeType = mimeType
//            )
//            _allFiles.value = _allFiles.value + newFile
//            // TODO: Persist this file list (e.g., in Room database or SharedPreferences for URIs)
//        }
//    }
//
//    fun deleteFile(fileToDelete: LocalAnalyzableFile) {
//        _allFiles.value = _allFiles.value.filterNot { it.id == fileToDelete.id }
//        // TODO: Handle actual file deletion if the app copied the file, or just remove reference
//        // TODO: Revoke URI permissions if necessary
//    }
//
//    fun processFile(fileToProcess: LocalAnalyzableFile) {
//        // 1. Update status to PENDING/PROCESSING
//        updateFileStatus(fileToProcess.id, ProcessingStatus.PROCESSING, "Starting analysis...")
//        viewModelScope.launch {
//            // TODO: Implement actual file processing logic (this will be a longer task)
//            // For now, simulate processing
//            kotlinx.coroutines.delay(3000) // Simulate work
//            val success = true // Simulate outcome
//            if (success) {
//                updateFileStatus(fileToProcess.id, ProcessingStatus.SUCCESS, "Analysis complete.")
//            } else {
//                updateFileStatus(fileToProcess.id, ProcessingStatus.FAILED, "Analysis failed: Error details.")
//            }
//        }
//    }
//
//    private fun updateFileStatus(fileId: String, status: ProcessingStatus, summary: String? = null) {
//        _allFiles.value = _allFiles.value.map {
//            if (it.id == fileId) it.copy(processingStatus = status, processingResultSummary = summary) else it
//        }
//    }
//
//    // --- Helper methods to get file metadata ---
//    private fun getFileName(contentResolver: ContentResolver, uri: Uri): String? {
//        var name: String? = null
//        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
//            if (cursor.moveToFirst()) {
//                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
//                if (nameIndex != -1) {
//                    name = cursor.getString(nameIndex)
//                }
//            }
//        }
//        return name
//    }
//
//    private fun getFileSize(contentResolver: ContentResolver, uri: Uri): Long? {
//        var size: Long? = null
//        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
//            if (cursor.moveToFirst()) {
//                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
//                if (sizeIndex != -1) {
//                    size = cursor.getLong(sizeIndex)
//                }
//            }
//        }
//        return size
//    }
//
//    // TODO: Load initial files from persistent storage on init
//}