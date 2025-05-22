// In viewmodel/LocalFileManagerViewModel.kt
package com.example.datagrindset.viewmodel

import android.app.Application
import android.content.ContentResolver
import android.content.Context // For SharedPreferences
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.datagrindset.ui.SortOption
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID // Keep for potential future use, though DocumentFile URIs are main IDs now
import androidx.core.net.toUri
import com.example.datagrindset.ProcessingStatus
import androidx.core.content.edit

// (DirectoryEntry sealed class remains the same as before)
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
        // We will get status from a separate map in the ViewModel
    ) : DirectoryEntry() {
        override val isDirectory: Boolean = false
    }

    data class FolderEntry(
        override val id: String,
        override val name: String,
        override val uri: Uri,
        val childCount: Int
    ) : DirectoryEntry() {
        override val isDirectory: Boolean = true
    }
}


class LocalFileManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val contentResolver: ContentResolver = application.contentResolver
    private val sharedPreferences = application.getSharedPreferences("app_prefs_datagrindset", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ROOT_TREE_URI = "root_tree_uri"
    }

    private val _rootTreeUri = MutableStateFlow<Uri?>(null)
    val rootTreeUri: StateFlow<Uri?> = _rootTreeUri.asStateFlow()

    // Store URI and display name for path segments
    private data class PathSegment(val uri: Uri, val name: String)
    private val _currentPathSegmentsList = MutableStateFlow<List<PathSegment>>(emptyList())

    val canNavigateUp: StateFlow<Boolean> =
        combine(_rootTreeUri, _currentPathSegmentsList) { root, segments ->
            root != null && segments.size > 1 // Can navigate up if not at the root of the selected tree
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)


    val currentPathDisplay: StateFlow<String> =
        _currentPathSegmentsList.map { segments ->
            if (segments.isEmpty()) "No folder selected"
            else segments.joinToString(" > ") { it.name }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "No folder selected")


    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.BY_NAME_ASC)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    // For managing processing status of files
    private val _fileProcessingStatusMap = MutableStateFlow<Map<Uri, Pair<ProcessingStatus, String?>>>(emptyMap())
    val fileProcessingStatusMap: StateFlow<Map<Uri, Pair<ProcessingStatus, String?>>> = _fileProcessingStatusMap.asStateFlow()
    // New StateFlow to signal navigation to an analysis screen
    private val _navigateToAnalysisTarget = MutableStateFlow<DirectoryEntry.FileEntry?>(null)
    val navigateToAnalysisTarget: StateFlow<DirectoryEntry.FileEntry?> = _navigateToAnalysisTarget.asStateFlow()


    val directoryEntries: StateFlow<List<DirectoryEntry>> =
        combine(
            _currentPathSegmentsList,
            _searchText,
            _sortOption,
            _fileProcessingStatusMap // Include status map to trigger recomposition on status change
        ) { currentSegments, text, sort, statusMap ->
            val currentFolderUri = currentSegments.lastOrNull()?.uri ?: return@combine emptyList()

            // Ensure we have permissions if this is a persisted URI being re-accessed
            try {
                val persistedPermissions = contentResolver.persistedUriPermissions
                if (persistedPermissions.none { it.uri == currentFolderUri && it.isReadPermission }) {
                    // Attempt to re-check root if current is not directly permitted (might be child of permitted root)
                    _rootTreeUri.value?.let { rootUri ->
                        if (persistedPermissions.none { it.uri == rootUri && it.isReadPermission }) {
                            // Permissions might have been revoked or not fully established.
                            // Consider prompting user to reselect the root folder.
                            // For now, we'll let it try and fail below if access is denied.
                            println("Warning: Read permission for $rootUri might be missing.")
                        }
                    }
                }
            } catch (e: Exception) {
                println("Error checking persisted permissions: $e")
            }


            val currentDirectory = DocumentFile.fromTreeUri(getApplication(), currentFolderUri)
            // We should primarily use fromTreeUri for the root,
            // and for children, their URIs are typically usable with fromSingleUri
            // or by finding them via parentDocumentFile.findFile(name)
            // For simplicity, if currentFolderUri IS the tree URI from DocumentFile.fromTreeUri
            // it will work. If it's a child URI, we might need to be more careful.
            // Let's assume currentFolderUri is always a valid URI for a directory.

            if (currentDirectory == null || !currentDirectory.isDirectory) {
                println("Failed to access directory: $currentFolderUri. Is it valid or permissions granted?")
                return@combine emptyList()
            }

            val entries = currentDirectory.listFiles().mapNotNull { docFile ->
                if (docFile.isDirectory) {
                    DirectoryEntry.FolderEntry(
                        id = docFile.uri.toString(),
                        name = docFile.name ?: "Unnamed Folder",
                        uri = docFile.uri,
                        childCount = docFile.listFiles().count { it.name != null } // Count non-null names
                    )
                } else {
                    DirectoryEntry.FileEntry(
                        id = docFile.uri.toString(),
                        name = docFile.name ?: "Unnamed File",
                        uri = docFile.uri,
                        size = docFile.length(),
                        dateModified = docFile.lastModified(),
                        mimeType = docFile.type
                        // Status will be looked up from _fileProcessingStatusMap by the UI
                    )
                }
            }

            val filteredList = if (text.isBlank()) {
                entries
            } else {
                entries.filter { it.name.contains(text, ignoreCase = true) }
            }

            // Apply sorting (using the corrected helper from before)
            filteredList.sortedWith(
                compareBy<DirectoryEntry> { !it.isDirectory }
                    .thenApplySortOption(sort)
            )

        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    init {
        val persistedUriString = sharedPreferences.getString(KEY_ROOT_TREE_URI, null)
        persistedUriString?.let { uriString ->
            val uri = uriString.toUri()
            // Check if we still have permission for this URI
            val persistedPermissions = contentResolver.persistedUriPermissions
            if (persistedPermissions.any { it.uri == uri && it.isReadPermission }) {
                _rootTreeUri.value = uri
                val rootDocFile = DocumentFile.fromTreeUri(getApplication(), uri)
                if (rootDocFile != null && rootDocFile.isDirectory) {
                    _currentPathSegmentsList.value = listOf(PathSegment(uri, rootDocFile.name ?: "Root"))
                } else {
                    // Root URI is invalid or no longer a directory, clear it
                    clearRootTreeUriPersistence()
                }
            } else {
                // No persisted permission, clear it
                clearRootTreeUriPersistence()
            }
        }
        // Load any persisted file statuses if needed (e.g., from a database or JSON in SharedPreferences)
    }

    private fun clearRootTreeUriPersistence() {
        sharedPreferences.edit { remove(KEY_ROOT_TREE_URI) }
        _rootTreeUri.value = null
        _currentPathSegmentsList.value = emptyList()
    }

    fun setRootTreeUri(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val rootDocFile = DocumentFile.fromTreeUri(getApplication(), uri)
            if (rootDocFile != null && rootDocFile.isDirectory) {
                _rootTreeUri.value = uri
                _currentPathSegmentsList.value = listOf(PathSegment(uri, rootDocFile.name ?: "Selected Folder"))
                sharedPreferences.edit { putString(KEY_ROOT_TREE_URI, uri.toString()) }
            } else {
                // Not a valid directory tree
                _rootTreeUri.value = null
                _currentPathSegmentsList.value = emptyList()
                // Potentially show an error to the user
            }
        } catch (e: SecurityException) {
            println("SecurityException setting root tree URI: $e")
            _rootTreeUri.value = null
            _currentPathSegmentsList.value = emptyList()
            // Potentially show an error to the user
        }
    }

    fun navigateTo(folderEntry: DirectoryEntry.FolderEntry) {
        // Ensure the folder URI is valid and we can access it.
        val folderDocFile = DocumentFile.fromTreeUri(getApplication(), folderEntry.uri) // or fromSingleUri if appropriate
            ?: DocumentFile.fromSingleUri(getApplication(), folderEntry.uri)

        if (folderDocFile != null && folderDocFile.isDirectory) {
            _currentPathSegmentsList.value = _currentPathSegmentsList.value + PathSegment(folderEntry.uri, folderEntry.name)
        } else {
            println("Cannot navigate to folder: ${folderEntry.name}, URI: ${folderEntry.uri} is not a valid directory or not accessible.")
            // Optionally, refresh current directory if navigation fails due to stale data
            // _currentPathSegmentsList.value = _currentPathSegmentsList.value // to trigger re-fetch of current
        }
    }

    fun navigateUp() {
        if (_currentPathSegmentsList.value.size > 1) {
            _currentPathSegmentsList.value = _currentPathSegmentsList.value.dropLast(1)
        }
    }

    fun onSearchTextChanged(text: String) {
        _searchText.value = text
    }

    fun onSortOptionSelected(sort: SortOption) {
        _sortOption.value = sort
    }

    private fun updateFileProcessingStatus(fileUri: Uri, status: ProcessingStatus, summary: String?) {
        _fileProcessingStatusMap.value = _fileProcessingStatusMap.value.toMutableMap().apply {
            this[fileUri] = Pair(status, summary)
        }
        // TODO: Persist this map if needed (e.g., if statuses should survive app restart)
    }

    fun prepareFileForAnalysis(fileEntry: DirectoryEntry.FileEntry) {
        val mimeType = fileEntry.mimeType?.lowercase()

        when (mimeType) {
            "text/plain", "text/markdown" -> { // Assuming markdown is also text
                updateFileProcessingStatus(fileEntry.uri, ProcessingStatus.SUCCESS, "Ready to open text file")
                _navigateToAnalysisTarget.value = fileEntry // Signal navigation
            }
            "text/csv" -> {
                updateFileProcessingStatus(fileEntry.uri, ProcessingStatus.SUCCESS, "Ready to open CSV file")
                _navigateToAnalysisTarget.value = fileEntry // Signal navigation
            }
            // Add more supported types here later (e.g., "application/json")
            else -> {
                updateFileProcessingStatus(fileEntry.uri, ProcessingStatus.UNSUPPORTED, "File type not supported for analysis.")
                // _navigateToAnalysisTarget remains null or is set to null if you want to clear previous signals
            }
        }
    }
    fun didNavigateToAnalysisScreen() {
        _navigateToAnalysisTarget.value = null
    }

    fun deleteEntry(entry: DirectoryEntry) {
        viewModelScope.launch {
            // For children, their URIs are often 'single' URIs.
            // The root tree URI itself cannot be deleted this way directly.
            val documentFile = DocumentFile.fromSingleUri(getApplication(), entry.uri)
                ?: DocumentFile.fromTreeUri(getApplication(), entry.uri) // Fallback for root/special cases

            if (documentFile != null && documentFile.uri == _rootTreeUri.value && _currentPathSegmentsList.value.size == 1) {
                println("Cannot delete the root of the selected tree directly. User should unselect/change root.")
                // Or, you could clear the root URI here: clearRootTreeUriPersistence()
                return@launch
            }

            val success = try { documentFile?.delete() } catch (e: Exception) { false }

            if (success == true) { // Explicitly check for true, as delete() returns Boolean?
                // Remove from status map if it was a file
                if (entry is DirectoryEntry.FileEntry) {
                    _fileProcessingStatusMap.value = _fileProcessingStatusMap.value.toMutableMap().apply {
                        remove(entry.uri)
                    }
                }
                // Force a refresh of the current directory's contents
                // Re-assigning _currentPathSegmentsList.value to itself will trigger the combine operator
                _currentPathSegmentsList.value = _currentPathSegmentsList.value.toList() // Create new list instance
            } else {
                println("Failed to delete ${entry.name} at ${entry.uri}")
                // Show error to user
            }
        }
    }
    // (The thenApplySortOption helper function remains the same)
}

// Helper extension function to apply the specific sort logic (ensure this is in the file or accessible)
private fun <T> Comparator<T>.thenApplySortOption(sortOption: SortOption): Comparator<T> where T : DirectoryEntry {
    val comparator = when (sortOption) {
        SortOption.BY_NAME_ASC -> compareBy<T> { it.name.lowercase() }
        SortOption.BY_NAME_DESC -> compareByDescending<T> { it.name.lowercase() }
        SortOption.BY_DATE_ASC -> compareBy<T> {
            if (it is DirectoryEntry.FileEntry) it.dateModified else Long.MAX_VALUE
        }
        SortOption.BY_DATE_DESC -> compareByDescending<T> {
            if (it is DirectoryEntry.FileEntry) it.dateModified else Long.MIN_VALUE
        }
        SortOption.BY_SIZE_ASC -> compareBy<T> {
            if (it is DirectoryEntry.FileEntry) it.size else Long.MAX_VALUE
        }
        SortOption.BY_SIZE_DESC -> compareByDescending<T> {
            if (it is DirectoryEntry.FileEntry) it.size else Long.MIN_VALUE
        }
    }
    return this.thenComparing(comparator)
}