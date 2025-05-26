package com.example.datagrindset.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.datagrindset.ProcessingStatus
import com.example.datagrindset.R
import com.example.datagrindset.ui.SortOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale


sealed class DirectoryEntry(open val id: String, open val name: String, open val uri: Uri) {
    data class FileEntry(
        override val id: String,
        override val name: String,
        override val uri: Uri,
        val size: Long,
        val dateModified: Long,
        val mimeType: String?
    ) : DirectoryEntry(id, name, uri)

    data class FolderEntry(
        override val id: String,
        override val name: String,
        override val uri: Uri,
        val childCount: Int
    ) : DirectoryEntry(id, name, uri)
}


class LocalFileManagerViewModel(application: Application) : AndroidViewModel(application) {
    private val context: Context get() = getApplication()
    private val _rootTreeUri = MutableStateFlow<Uri?>(null)
    val rootTreeUri: StateFlow<Uri?> = _rootTreeUri.asStateFlow()

    private val _currentFolderUri = MutableStateFlow<Uri?>(null)
    private val _directoryStack = MutableStateFlow<List<Uri>>(emptyList())

    private val _rawDirectoryEntries = MutableStateFlow<List<DirectoryEntry>>(emptyList())

    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.BY_NAME_ASC)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _fileProcessingStatusMap = MutableStateFlow<Map<Uri, Pair<ProcessingStatus, LocalizedSummary?>>>(emptyMap())
    val fileProcessingStatusMap: StateFlow<Map<Uri, Pair<ProcessingStatus, LocalizedSummary?>>> = _fileProcessingStatusMap.asStateFlow()


    private val _navigateToAnalysisTarget = MutableStateFlow<DirectoryEntry.FileEntry?>(null)
    val navigateToAnalysisTarget: StateFlow<DirectoryEntry.FileEntry?> = _navigateToAnalysisTarget.asStateFlow()

    private val _suggestExternalAppForFile = MutableStateFlow<DirectoryEntry.FileEntry?>(null)
    val suggestExternalAppForFile: StateFlow<DirectoryEntry.FileEntry?> = _suggestExternalAppForFile.asStateFlow()


    val directoryEntries: StateFlow<List<DirectoryEntry>> = combine(
        _rawDirectoryEntries, _searchText, _sortOption
    ) { entries, text, sortOpt ->
        val filteredEntries = if (text.isBlank()) {
            entries
        } else {
            val normalizedSearchText = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
                .lowercase(Locale.getDefault())
            entries.filter { entry ->
                Normalizer.normalize(entry.name, Normalizer.Form.NFD)
                    .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
                    .lowercase(Locale.getDefault())
                    .contains(normalizedSearchText)
            }
        }
        sortEntries(filteredEntries, sortOpt)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())


    val currentPathDisplay: StateFlow<String> = combine(
        _rootTreeUri, _directoryStack
    ) { rootUri, stack ->
        if (rootUri == null) return@combine context.getString(R.string.lfm_select_root_prompt) // Localized default
        val rootDocFile = DocumentFile.fromTreeUri(context, rootUri)
        val rootName = rootDocFile?.name ?: "Root" // "Root" could be a string resource if needed
        val pathSegments = stack.mapNotNull { uri -> DocumentFile.fromTreeUri(context, uri)?.name }
        (listOf(rootName) + pathSegments).joinToString(" > ")
    }.stateIn(viewModelScope, SharingStarted.Lazily, context.getString(R.string.lfm_select_root_prompt))


    val canNavigateUp: StateFlow<Boolean> = _directoryStack.combine(_rootTreeUri) { stack, root ->
        stack.isNotEmpty() && root != null
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)


    init {
        val sharedPrefs = application.getSharedPreferences("LocalFileManagerPrefs", Context.MODE_PRIVATE)
        sharedPrefs.getString("root_uri", null)?.let { uriString ->
            val uri = Uri.parse(uriString)
            Log.d("LFMViewModel", "Found persisted root URI: $uriString")
            if (checkAndRequestPersistedPermissions(uri)) {
                _rootTreeUri.value = uri
                _currentFolderUri.value = uri
                fetchDirectoryEntries(uri)
            } else {
                Log.w("LFMViewModel", "Failed to re-acquire permission for $uriString")
                sharedPrefs.edit().remove("root_uri").apply()
            }
        }
    }

    private fun checkAndRequestPersistedPermissions(uri: Uri): Boolean {
        val persistedUriPermissions = context.contentResolver.persistedUriPermissions
        val hasPersistedPermission = persistedUriPermissions.any { it.uri == uri && it.isReadPermission }

        if (hasPersistedPermission) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                Log.i("LFMViewModel", "Successfully re-acquired persisted permission for root URI: $uri")
                return true
            } catch (e: SecurityException) {
                Log.e("LFMViewModel", "SecurityException trying to re-take permission for $uri", e)
                return false
            }
        }
        return false
    }


    fun setRootTreeUri(uri: Uri) {
        try {
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)

            _rootTreeUri.value = uri
            _currentFolderUri.value = uri
            _directoryStack.value = emptyList()
            fetchDirectoryEntries(uri)

            val sharedPrefs = getApplication<Application>().getSharedPreferences("LocalFileManagerPrefs", Context.MODE_PRIVATE)
            sharedPrefs.edit().putString("root_uri", uri.toString()).apply()
            Log.i("LFMViewModel", "New root URI set and persisted: $uri")

        } catch (e: SecurityException) {
            Log.e("LFMViewModel", "SecurityException setting root URI: $uri", e)
        }
    }


    fun navigateTo(folderEntry: DirectoryEntry.FolderEntry) {
        _currentFolderUri.value?.let {
            _directoryStack.value = _directoryStack.value + listOf(folderEntry.uri)
            _currentFolderUri.value = folderEntry.uri
            fetchDirectoryEntries(folderEntry.uri)
        }
    }

    fun navigateUp() {
        if (_directoryStack.value.isNotEmpty()) {
            val newStack = _directoryStack.value.dropLast(1)
            _directoryStack.value = newStack
            val parentUri = newStack.lastOrNull() ?: _rootTreeUri.value
            parentUri?.let {
                _currentFolderUri.value = it
                fetchDirectoryEntries(it)
            }
        }
    }

    fun onSearchTextChanged(text: String) {
        _searchText.value = text
    }

    fun onSortOptionSelected(sortOption: SortOption) {
        _sortOption.value = sortOption
    }

    private fun sortEntries(entries: List<DirectoryEntry>, sortOption: SortOption): List<DirectoryEntry> {
        return when (sortOption) {
            SortOption.BY_NAME_ASC -> entries.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            SortOption.BY_NAME_DESC -> entries.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name })
            SortOption.BY_DATE_ASC -> entries.sortedBy { if (it is DirectoryEntry.FileEntry) it.dateModified else Long.MAX_VALUE }
            SortOption.BY_DATE_DESC -> entries.sortedByDescending { if (it is DirectoryEntry.FileEntry) it.dateModified else Long.MIN_VALUE }
            SortOption.BY_SIZE_ASC -> entries.sortedBy { if (it is DirectoryEntry.FileEntry) it.size else Long.MAX_VALUE }
            SortOption.BY_SIZE_DESC -> entries.sortedByDescending { if (it is DirectoryEntry.FileEntry) it.size else Long.MIN_VALUE }
        }
    }


    private fun fetchDirectoryEntries(folderUri: Uri) {
        Log.d("LFMViewModel", "Fetching entries for folder URI: $folderUri")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val documentFile = DocumentFile.fromTreeUri(context, folderUri)
                if (documentFile == null || !documentFile.isDirectory) {
                    Log.e("LFMViewModel", "Not a directory or URI is invalid: $folderUri")
                    _rawDirectoryEntries.value = emptyList()
                    return@launch
                }

                if (!documentFile.canRead()) {
                    Log.e("LFMViewModel", "No read permission for folder: ${documentFile.uri}")
                    val rootDocFile = _rootTreeUri.value?.let { DocumentFile.fromTreeUri(context, it) }
                    if (rootDocFile == null || !rootDocFile.canRead()) {
                        Log.e("LFMViewModel", "Root URI also lacks read permission or is null.")
                        _rawDirectoryEntries.value = emptyList()
                        return@launch
                    }
                    Log.w("LFMViewModel", "Folder ${documentFile.uri} reported no read permission, but root URI has it. Proceeding cautiously.")
                } else {
                    Log.i("LFMViewModel", "Confirmed read permission for the root of ${documentFile.uri} (root: ${_rootTreeUri.value})")
                }

                val entries = mutableListOf<DirectoryEntry>()
                documentFile.listFiles().forEach { file ->
                    if (file.name == null) return@forEach

                    if (file.isDirectory) {
                        val childCount = file.listFiles().size
                        entries.add(
                            DirectoryEntry.FolderEntry(
                                id = file.uri.toString() + "_folder",
                                name = file.name ?: "Unknown Folder",
                                uri = file.uri,
                                childCount = childCount
                            )
                        )
                    } else {
                        entries.add(
                            DirectoryEntry.FileEntry(
                                id = file.uri.toString() + "_file",
                                name = file.name ?: "Unknown File",
                                uri = file.uri,
                                size = file.length(),
                                dateModified = file.lastModified(),
                                mimeType = file.type ?: context.contentResolver.getType(file.uri)
                            )
                        )
                    }
                }
                Log.d("LFMViewModel", "Found ${entries.count { it.name != "Unknown Folder" && it.name != "Unknown File"}} readable entries in $folderUri")
                withContext(Dispatchers.Main) {
                    _rawDirectoryEntries.value = entries
                }
            } catch (e: Exception) {
                Log.e("LFMViewModel", "Error fetching directory entries for $folderUri", e)
                withContext(Dispatchers.Main) {
                    _rawDirectoryEntries.value = emptyList()
                }
            }
        }
    }

    fun prepareFileForAnalysis(fileEntry: DirectoryEntry.FileEntry) {
        val mimeType = fileEntry.mimeType?.lowercase(Locale.ROOT)
        val fileName = fileEntry.name.lowercase(Locale.ROOT)

        val isTxt = mimeType == "text/plain" || mimeType == "text/markdown" || fileName.endsWith(".txt") || fileName.endsWith(".md")
        val isCsv = mimeType == "text/csv" || mimeType == "application/csv" || fileName.endsWith(".csv")

        when {
            isTxt -> {
                _fileProcessingStatusMap.value = _fileProcessingStatusMap.value +
                        (fileEntry.uri to (ProcessingStatus.SUCCESS to LocalizedSummary(R.string.processing_summary_ready_to_open_txt)))
                _navigateToAnalysisTarget.value = fileEntry
            }
            isCsv -> {
                _fileProcessingStatusMap.value = _fileProcessingStatusMap.value +
                        (fileEntry.uri to (ProcessingStatus.SUCCESS to LocalizedSummary(R.string.processing_summary_ready_to_open_csv)))
                _navigateToAnalysisTarget.value = fileEntry
            }
            else -> {
                _fileProcessingStatusMap.value = _fileProcessingStatusMap.value +
                        (fileEntry.uri to (ProcessingStatus.UNSUPPORTED to LocalizedSummary(R.string.processing_summary_unsupported_type_detailed, listOf(mimeType ?: "unknown"))))
                _suggestExternalAppForFile.value = fileEntry
            }
        }
    }

    fun deleteEntry(entry: DirectoryEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val documentFile = if (entry is DirectoryEntry.FolderEntry) {
                    DocumentFile.fromTreeUri(context, entry.uri)
                } else {
                    // For single files, DocumentFile.fromSingleUri might not have delete capability if it's part of a tree.
                    // If the URI is a child of a tree URI, we might need to build its path relative to the tree root.
                    // However, SAF delete usually works best with the specific document URI if permissions are granted broadly.
                    DocumentFile.fromSingleUri(context, entry.uri)
                }

                if (documentFile?.exists() == true) {
                    if (documentFile.delete()) {
                        Log.i("LFMViewModel", "Successfully deleted: ${entry.uri}")
                        _currentFolderUri.value?.let { fetchDirectoryEntries(it) }
                        _fileProcessingStatusMap.value = _fileProcessingStatusMap.value - entry.uri
                    } else {
                        Log.e("LFMViewModel", "Failed to delete: ${entry.uri}")
                    }
                } else {
                    Log.w("LFMViewModel", "Attempted to delete non-existent file: ${entry.uri}")
                }
            } catch (e: Exception) {
                Log.e("LFMViewModel", "Error deleting entry ${entry.uri}", e)
            }
        }
    }

    fun didNavigateToAnalysisScreen() {
        _navigateToAnalysisTarget.value = null
    }
    fun didAttemptToOpenWithExternalApp() {
        _suggestExternalAppForFile.value = null
    }
}

