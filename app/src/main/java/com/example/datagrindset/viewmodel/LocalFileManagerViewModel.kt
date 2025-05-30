package com.example.datagrindset.viewmodel

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.datagrindset.ProcessingStatus
import com.example.datagrindset.ui.SortOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        private const val TAG = "LFMViewModel"
    }

    private val _rootTreeUri = MutableStateFlow<Uri?>(null)
    val rootTreeUri: StateFlow<Uri?> = _rootTreeUri.asStateFlow()

    private data class PathSegment(val uri: Uri, val name: String)
    private val _currentPathSegmentsList = MutableStateFlow<List<PathSegment>>(emptyList())

    val canNavigateUp: StateFlow<Boolean> =
        combine(_rootTreeUri, _currentPathSegmentsList) { root, segments ->
            root != null && segments.size > 1
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

    private val _fileProcessingStatusMap = MutableStateFlow<Map<Uri, Pair<ProcessingStatus, String?>>>(emptyMap())
    val fileProcessingStatusMap: StateFlow<Map<Uri, Pair<ProcessingStatus, String?>>> = _fileProcessingStatusMap.asStateFlow()

    private val _navigateToAnalysisTarget = MutableStateFlow<DirectoryEntry.FileEntry?>(null)
    val navigateToAnalysisTarget: StateFlow<DirectoryEntry.FileEntry?> = _navigateToAnalysisTarget.asStateFlow()

    private val _suggestExternalAppForFile = MutableStateFlow<DirectoryEntry.FileEntry?>(null)
    val suggestExternalAppForFile: StateFlow<DirectoryEntry.FileEntry?> = _suggestExternalAppForFile.asStateFlow()

    val directoryEntries: StateFlow<List<DirectoryEntry>> =
        combine(
            _currentPathSegmentsList,
            _searchText,
            _sortOption,
            _fileProcessingStatusMap
        ) { currentSegments, text, sort, statusMap ->
            val currentFolderUri = currentSegments.lastOrNull()?.uri
            if (currentFolderUri == null) {
                Log.d(TAG, "No current folder URI, returning empty list.")
                return@combine emptyList()
            }
            Log.d(TAG, "Fetching entries for folder URI: $currentFolderUri")

            val rootOfCurrent = DocumentsContract.getTreeDocumentId(currentFolderUri)?.let { treeDocId ->
                DocumentsContract.buildTreeDocumentUri(currentFolderUri.authority!!, treeDocId)
            } ?: currentFolderUri

            val hasPermissionForRootOfCurrent = contentResolver.persistedUriPermissions.any { persistedUri ->
                persistedUri.uri == rootOfCurrent && persistedUri.isReadPermission
            }

            if (!hasPermissionForRootOfCurrent) {
                Log.e(TAG, "No persisted read permission for the root of $currentFolderUri (root: $rootOfCurrent). Cannot list files.")
                _rootTreeUri.value?.let { originalRoot ->
                    Log.e(TAG, "Original selected root $originalRoot permission: ${contentResolver.persistedUriPermissions.any {it.uri == originalRoot && it.isReadPermission}}")
                }
                return@combine emptyList()
            }
            Log.i(TAG, "Confirmed read permission for the root of $currentFolderUri (root: $rootOfCurrent)")

            val currentDirectory = DocumentFile.fromTreeUri(getApplication(), currentFolderUri)
            if (currentDirectory == null || !currentDirectory.isDirectory) {
                Log.e(TAG, "Failed to access directory DocumentFile or not a directory: $currentFolderUri.")
                return@combine emptyList()
            }
            if (!currentDirectory.canRead()) {
                Log.e(TAG, "Cannot read directory DocumentFile: $currentFolderUri")
                return@combine emptyList()
            }

            val entries = currentDirectory.listFiles().mapNotNull { docFile ->
                if (!docFile.canRead()) {
                    Log.w(TAG, "Cannot read child DocumentFile: ${docFile.name} at ${docFile.uri}")
                    null
                } else if (docFile.isDirectory) {
                    DirectoryEntry.FolderEntry(
                        id = docFile.uri.toString(),
                        name = docFile.name ?: "Unnamed Folder",
                        uri = docFile.uri,
                        childCount = docFile.listFiles().count { it.name != null && it.canRead() }
                    )
                } else {
                    DirectoryEntry.FileEntry(
                        id = docFile.uri.toString(),
                        name = docFile.name ?: "Unnamed File",
                        uri = docFile.uri,
                        size = docFile.length(),
                        dateModified = docFile.lastModified(),
                        mimeType = docFile.type
                    )
                }
            }
            Log.d(TAG, "Found ${entries.size} readable entries in $currentFolderUri")

            val filteredList = if (text.isBlank()) {
                entries
            } else {
                entries.filter { it.name.contains(text, ignoreCase = true) }
            }

            filteredList.sortedWith(
                compareBy<DirectoryEntry> { !it.isDirectory }
                    .thenApplySortOption(sort)
            )

        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    init {
        val persistedUriString = sharedPreferences.getString(KEY_ROOT_TREE_URI, null)
        persistedUriString?.let { uriString ->
            val uri = uriString.toUri()
            Log.d(TAG, "Found persisted root URI: $uri")
            val persistedPermissions = contentResolver.persistedUriPermissions
            if (persistedPermissions.any { it.uri == uri && it.isReadPermission }) {
                Log.i(TAG, "Successfully re-acquired persisted permission for root URI: $uri")
                _rootTreeUri.value = uri
                val rootDocFile = DocumentFile.fromTreeUri(getApplication(), uri)
                if (rootDocFile != null && rootDocFile.isDirectory && rootDocFile.canRead()) {
                    _currentPathSegmentsList.value = listOf(PathSegment(uri, rootDocFile.name ?: "Root"))
                } else {
                    Log.w(TAG, "Persisted root URI $uri is invalid, not a directory, or not readable. Clearing.")
                    clearRootTreeUriPersistence()
                }
            } else {
                Log.w(TAG, "No persisted read permission for root URI: $uri. Clearing.")
                clearRootTreeUriPersistence()
            }
        } ?: Log.d(TAG, "No persisted root URI found.")
    }

    private fun clearRootTreeUriPersistence() {
        Log.i(TAG, "Clearing persisted root tree URI.")
        _rootTreeUri.value?.let {
            try {
                val takeFlagsClear: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                contentResolver.releasePersistableUriPermission(it, takeFlagsClear) // Corrected flags variable name
                Log.i(TAG, "Released persisted permissions for $it")
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException releasing permissions for $it", e)
            }
        }
        sharedPreferences.edit { remove(KEY_ROOT_TREE_URI) }
        _rootTreeUri.value = null
        _currentPathSegmentsList.value = emptyList()
    }

//    fun setRootTreeUri(uri: Uri?) {
//        if (uri == null) {
//            clearRootTreeUriPersistence()
//            return
//        }
//        try {
//            // Permissions should primarily be taken in MainActivity after user selection.
//            // This is a fallback or re-check.
//            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
//            // Check if already persisted. If not, this call might fail if not from the original picker.
//            // For safety, we rely on MainActivity having done this robustly.
//            // contentResolver.takePersistableUriPermission(uri, takeFlags)
//            // Log.i(TAG, "Attempted to ensure persistable permissions for $uri in setRootTreeUri")
//
//
//            val rootDocFile = DocumentFile.fromTreeUri(getApplication(), uri)
//            if (rootDocFile != null && rootDocFile.isDirectory && rootDocFile.canRead()) {
//                // Verify again if we truly hold permission
//                val persistedPermissions = contentResolver.persistedUriPermissions
//                if (persistedPermissions.none { it.uri == uri && it.isReadPermission }) {
//                    Log.e(TAG, "setRootTreeUri: Permission for $uri was NOT found in persisted list, even after trying to take. This is unexpected if MainActivity succeeded.")
//                    // Attempt to take it again, though this might be problematic outside original callback
//                    try {
//                        contentResolver.takePersistableUriPermission(uri, takeFlags)
//                        Log.i(TAG, "Re-attempted takePersistableUriPermission for $uri in setRootTreeUri.")
//                        if (contentResolver.persistedUriPermissions.none { it.uri == uri && it.isReadPermission }) {
//                            Log.e(TAG, "Still no permission after re-attempt. Aborting setRootTreeUri.")
//                            _rootTreeUri.value = null
//                            _currentPathSegmentsList.value = emptyList()
//                            return
//                        }
//                    } catch (se: SecurityException) {
//                        Log.e(TAG, "SecurityException on re-attempting takePersistableUriPermission for $uri. Aborting.", se)
//                        _rootTreeUri.value = null
//                        _currentPathSegmentsList.value = emptyList()
//                        return
//                    }
//                }
//
//                _rootTreeUri.value = uri
//                _currentPathSegmentsList.value = listOf(PathSegment(uri, rootDocFile.name ?: "Selected Folder"))
//                sharedPreferences.edit { putString(KEY_ROOT_TREE_URI, uri.toString()) }
//                Log.i(TAG, "Root tree URI set to: $uri, Name: ${rootDocFile.name}")
//            } else {
//                Log.e(TAG, "Provided URI $uri is not a valid directory tree or not readable.")
//                _rootTreeUri.value = null
//                _currentPathSegmentsList.value = emptyList()
//            }
//        } catch (e: Exception) { // Broader catch for unexpected issues during DocumentFile access
//            Log.e(TAG, "Exception in setRootTreeUri for: $uri", e)
//            _rootTreeUri.value = null
//            _currentPathSegmentsList.value = emptyList()
//        }
//    }
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
        Log.d(TAG, "Navigating to folder: ${folderEntry.name}, URI: ${folderEntry.uri}")
        // For children of a tree URI, fromTreeUri should work.
        val folderDocFile = DocumentFile.fromTreeUri(getApplication(), folderEntry.uri)
        if (folderDocFile != null && folderDocFile.isDirectory && folderDocFile.canRead()) {
            _currentPathSegmentsList.value = _currentPathSegmentsList.value + PathSegment(folderEntry.uri, folderEntry.name)
        } else {
            Log.e(TAG, "Cannot navigate to folder: ${folderEntry.name}, URI: ${folderEntry.uri}. Not a valid directory or not readable.")
        }
    }

    fun navigateUp() {
        if (_currentPathSegmentsList.value.size > 1) {
            _currentPathSegmentsList.value = _currentPathSegmentsList.value.dropLast(1)
            Log.d(TAG, "Navigated up. New path: ${currentPathDisplay.value}")
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
    }

    fun prepareFileForAnalysis(fileEntry: DirectoryEntry.FileEntry) {
        val targetUri = fileEntry.uri
        val targetName = fileEntry.name
        Log.i(TAG, "Preparing analysis for: $targetName, URI: $targetUri, Mime: ${fileEntry.mimeType}")

        // --- DEBUGGING STEP to ensure LFMViewModel itself can open it ---
        var canOpenFileInLFM = false
        try {
            val context = getApplication<Application>()
            Log.d(TAG, "LFM Read Test: Attempting to open PFD for $targetUri")
            context.contentResolver.openFileDescriptor(targetUri, "r")?.use { pfd ->
                Log.i(TAG, "LFM Read Test: Successfully opened PFD for $targetUri. Size: ${pfd.statSize}. File is readable here.")
                canOpenFileInLFM = true
            } ?: run {
                Log.e(TAG, "LFM Read Test: PFD was null for $targetUri.")
            }
        } catch (e: Exception) { // Catch generic Exception for broader logging
            Log.e(TAG, "LFM Read Test: Exception opening PFD for $targetUri: ${e.message}", e)
            updateFileProcessingStatus(targetUri, ProcessingStatus.ERROR, "LFM Permission/File Error: ${e.message?.take(50)}")
            return // Stop if LFM can't even open it
        }

        if (!canOpenFileInLFM) {
            Log.e(TAG, "LFM Read Test: Failed for $targetUri. Not proceeding to analysis.")
            if (fileProcessingStatusMap.value[targetUri]?.first != ProcessingStatus.ERROR) {
                updateFileProcessingStatus(targetUri, ProcessingStatus.ERROR, "LFM: Open failed (PFD null/other).")
            }
            return
        }
        // --- END DEBUGGING STEP ---

        val mimeType = fileEntry.mimeType?.lowercase()
        when (mimeType) {
            "text/plain", "text/markdown" -> {
                updateFileProcessingStatus(targetUri, ProcessingStatus.SUCCESS, "Ready for TXT analysis")
                _navigateToAnalysisTarget.value = fileEntry
            }
            "text/csv", "text/comma-separated-values", "application/csv" -> { // Added application/csv
                updateFileProcessingStatus(targetUri, ProcessingStatus.SUCCESS, "Ready for CSV analysis")
                _navigateToAnalysisTarget.value = fileEntry
            }
            else -> {
                updateFileProcessingStatus(fileEntry.uri, ProcessingStatus.UNSUPPORTED, "File type not supported by this app. Try opening with another app.")
                _suggestExternalAppForFile.value = fileEntry
            }
        }
    }

    fun didAttemptToOpenWithExternalApp() {
        _suggestExternalAppForFile.value = null
    }
    fun didNavigateToAnalysisScreen() {
        _navigateToAnalysisTarget.value = null
    }

    fun deleteEntry(entry: DirectoryEntry) {
        viewModelScope.launch {
            val documentFileToDelete = DocumentFile.fromTreeUri(getApplication(), entry.uri)
                ?: DocumentFile.fromSingleUri(getApplication(), entry.uri)

            if (documentFileToDelete == null || !documentFileToDelete.exists()) {
                Log.e(TAG, "DocumentFile not found or does not exist for deletion: ${entry.uri}")
                return@launch
            }
            if (documentFileToDelete.uri == _rootTreeUri.value && _currentPathSegmentsList.value.size == 1) {
                Log.w(TAG, "Attempt to delete the root of the selected tree. Aborting.")
                return@launch
            }

            val success = try { documentFileToDelete.delete() } catch (e: Exception) {
                Log.e(TAG, "Exception during delete for ${entry.name} at ${entry.uri}", e); false
            }

            if (success) {
                Log.i(TAG, "Successfully deleted ${entry.name} at ${entry.uri}")
                if (entry is DirectoryEntry.FileEntry) {
                    _fileProcessingStatusMap.value = _fileProcessingStatusMap.value.toMutableMap().apply { remove(entry.uri) }
                }
                _currentPathSegmentsList.value = _currentPathSegmentsList.value.toList()
            } else {
                Log.e(TAG, "Failed to delete ${entry.name} at ${entry.uri}")
            }
        }
    }
}

private fun <T> Comparator<T>.thenApplySortOption(sortOption: SortOption): Comparator<T> where T : DirectoryEntry {
    val comparator = when (sortOption) {
        SortOption.BY_NAME_ASC -> compareBy<T> { it.name.lowercase() }
        SortOption.BY_NAME_DESC -> compareByDescending<T> { it.name.lowercase() }
        SortOption.BY_DATE_ASC -> compareBy<T> { if (it is DirectoryEntry.FileEntry) it.dateModified else Long.MAX_VALUE }
        SortOption.BY_DATE_DESC -> compareByDescending<T> { if (it is DirectoryEntry.FileEntry) it.dateModified else Long.MIN_VALUE }
        SortOption.BY_SIZE_ASC -> compareBy<T> { if (it is DirectoryEntry.FileEntry) it.size else Long.MAX_VALUE }
        SortOption.BY_SIZE_DESC -> compareByDescending<T> { if (it is DirectoryEntry.FileEntry) it.size else Long.MIN_VALUE }
    }
    return this.thenComparing(comparator)
}