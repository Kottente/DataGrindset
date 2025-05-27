package com.example.datagrindset.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
// import android.provider.DocumentsContract // Not strictly needed if DocumentFile is used consistently
import android.util.Log
import android.widget.Toast
// import androidx.core.content.FileProvider // Needed if sharing app-private files, not for SAF content URIs directly
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.datagrindset.ProcessingStatus
import com.example.datagrindset.R
import com.example.datagrindset.ui.SortOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
// import java.io.File // Not used for SAF operations directly
import java.text.Normalizer
// import java.util.Date // Not directly used here, but by formatDate
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

data class ItemDetails(
    val name: String,
    val path: String,
    val type: String,
    val size: Long?,
    val dateModified: Long?,
    val mimeType: String?,
    val childrenCount: Int?,
    val isReadable: Boolean,
    val isWritable: Boolean,
    val isHidden: Boolean
)


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

    private val _isSelectionModeActive = MutableStateFlow(false)
    val isSelectionModeActive: StateFlow<Boolean> = _isSelectionModeActive.asStateFlow()

    private val _selectedItems = MutableStateFlow<Set<Uri>>(emptySet())
    val selectedItems: StateFlow<Set<Uri>> = _selectedItems.asStateFlow()

    val selectedItemsCount: StateFlow<Int> = _selectedItems.map { it.size }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    private val _showItemDetailsDialog = MutableStateFlow<ItemDetails?>(null)
    val showItemDetailsDialog: StateFlow<ItemDetails?> = _showItemDetailsDialog.asStateFlow()

    // Event for MainActivity to launch directory picker
    private val _launchDirectoryPickerEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val launchDirectoryPickerEvent = _launchDirectoryPickerEvent.asSharedFlow()

    private val _toastMessageEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastMessageEvent = _toastMessageEvent.asSharedFlow()

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
        if (rootUri == null) return@combine context.getString(R.string.lfm_select_root_prompt)
        val rootDocFile = DocumentFile.fromTreeUri(context, rootUri)
        val rootName = rootDocFile?.name ?: "Root"
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


    fun setRootTreeUri(uri: Uri) { // Called by MainActivity after picker
        try {
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)

            _rootTreeUri.value = uri
            _currentFolderUri.value = uri
            _directoryStack.value = emptyList()
            fetchDirectoryEntries(uri)
            exitSelectionMode()

            val sharedPrefs = getApplication<Application>().getSharedPreferences("LocalFileManagerPrefs", Context.MODE_PRIVATE)
            sharedPrefs.edit().putString("root_uri", uri.toString()).apply()
            Log.i("LFMViewModel", "New root URI set and persisted: $uri")

        } catch (e: SecurityException) {
            Log.e("LFMViewModel", "SecurityException setting root URI: $uri", e)
        }
    }

    // Called from UI to request directory selection
    fun requestSelectRootDirectory() {
        _launchDirectoryPickerEvent.tryEmit(Unit)
    }


    fun navigateTo(folderEntry: DirectoryEntry.FolderEntry) {
        _currentFolderUri.value?.let {
            _directoryStack.value = _directoryStack.value + listOf(folderEntry.uri)
            _currentFolderUri.value = folderEntry.uri
            fetchDirectoryEntries(folderEntry.uri)
            exitSelectionMode()
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
            exitSelectionMode()
        }
    }

    fun onSearchTextChanged(text: String) { _searchText.value = text }
    fun onSortOptionSelected(sortOption: SortOption) { _sortOption.value = sortOption }

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
                    Log.e("LFMViewModel", "Not a directory or URI is invalid: $folderUri"); _rawDirectoryEntries.value = emptyList(); return@launch
                }
                if (!documentFile.canRead()) {
                    Log.e("LFMViewModel", "No read permission for folder: ${documentFile.uri}")
                    val rootDocFile = _rootTreeUri.value?.let { DocumentFile.fromTreeUri(context, it) }
                    if (rootDocFile == null || !rootDocFile.canRead()) { Log.e("LFMViewModel", "Root URI also lacks read permission or is null."); _rawDirectoryEntries.value = emptyList(); return@launch }
                    Log.w("LFMViewModel", "Folder ${documentFile.uri} reported no read permission, but root URI has it. Proceeding cautiously.")
                }

                val entries = mutableListOf<DirectoryEntry>()
                documentFile.listFiles().forEach { file ->
                    if (file.name == null) return@forEach
                    if (file.isDirectory) {
                        entries.add(DirectoryEntry.FolderEntry(id = file.uri.toString() + "_folder", name = file.name!!, uri = file.uri, childCount = file.listFiles().count { it.name != null }))
                    } else {
                        entries.add(DirectoryEntry.FileEntry(id = file.uri.toString() + "_file", name = file.name!!, uri = file.uri, size = file.length(), dateModified = file.lastModified(), mimeType = file.type ?: context.contentResolver.getType(file.uri)))
                    }
                }
                Log.d("LFMViewModel", "Found ${entries.size} entries in $folderUri")
                withContext(Dispatchers.Main) { _rawDirectoryEntries.value = entries }
            } catch (e: Exception) {
                Log.e("LFMViewModel", "Error fetching directory entries for $folderUri", e)
                withContext(Dispatchers.Main) { _rawDirectoryEntries.value = emptyList() }
            }
        }
    }

    fun prepareFileForAnalysis(fileEntry: DirectoryEntry.FileEntry) {
        val mimeType = fileEntry.mimeType?.lowercase(Locale.ROOT)
        val fileName = fileEntry.name.lowercase(Locale.ROOT)
        val isTxt = mimeType == "text/plain" || mimeType == "text/markdown" || fileName.endsWith(".txt") || fileName.endsWith(".md")
        val isCsv = mimeType == "text/csv" || mimeType == "application/csv" || fileName.endsWith(".csv")

        when {
            isTxt -> { _fileProcessingStatusMap.value += (fileEntry.uri to (ProcessingStatus.SUCCESS to LocalizedSummary(R.string.processing_summary_ready_to_open_txt))); _navigateToAnalysisTarget.value = fileEntry }
            isCsv -> { _fileProcessingStatusMap.value += (fileEntry.uri to (ProcessingStatus.SUCCESS to LocalizedSummary(R.string.processing_summary_ready_to_open_csv))); _navigateToAnalysisTarget.value = fileEntry }
            else -> { _fileProcessingStatusMap.value += (fileEntry.uri to (ProcessingStatus.UNSUPPORTED to LocalizedSummary(R.string.processing_summary_unsupported_type_detailed, listOf(mimeType ?: "unknown")))); _suggestExternalAppForFile.value = fileEntry }
        }
    }

    fun deleteSelectedItems() {
        viewModelScope.launch(Dispatchers.IO) {
            val itemsToDelete = _selectedItems.value.toList()
            itemsToDelete.forEach { uri ->
                try {
                    DocumentFile.fromSingleUri(context, uri)?.delete()
                    Log.i("LFMViewModel", "Deleted selected item: $uri")
                    _fileProcessingStatusMap.value -= uri
                } catch (e: Exception) { Log.e("LFMViewModel", "Error deleting selected item $uri", e) }
            }
            _currentFolderUri.value?.let { fetchDirectoryEntries(it) } // Refresh
            withContext(Dispatchers.Main) { exitSelectionMode() }
        }
    }

    fun didNavigateToAnalysisScreen() { _navigateToAnalysisTarget.value = null }
    fun didAttemptToOpenWithExternalApp() { _suggestExternalAppForFile.value = null }

    fun enterSelectionMode(itemUri: Uri) { _isSelectionModeActive.value = true; _selectedItems.value = setOf(itemUri) }
    fun exitSelectionMode() { _isSelectionModeActive.value = false; _selectedItems.value = emptySet() }
    fun toggleItemSelected(itemUri: Uri) { _selectedItems.value = if (_selectedItems.value.contains(itemUri)) _selectedItems.value - itemUri else _selectedItems.value + itemUri }
    fun selectAllInCurrentDirectory() { _selectedItems.value = _rawDirectoryEntries.value.map { it.uri }.toSet() }
    fun deselectAll() { _selectedItems.value = emptySet() }

    fun shareSelectedItems() {
        val selectedUris = _selectedItems.value.toList()
        if (selectedUris.isEmpty()) { Toast.makeText(context, context.getString(R.string.lfm_action_share_no_items), Toast.LENGTH_SHORT).show(); return }

        val shareIntent = Intent().apply {
            action = if (selectedUris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE
            type = "*/*"
            val urisToShare = ArrayList(selectedUris)
            if (selectedUris.size == 1) {
                putExtra(Intent.EXTRA_STREAM, urisToShare.first())
                DocumentFile.fromSingleUri(context, urisToShare.first())?.type?.let { type = it }
            } else { putParcelableArrayListExtra(Intent.EXTRA_STREAM, urisToShare) }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.lfm_bottom_bar_share_desc)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        } catch (e: Exception) { Log.e("LFMViewModel", "Error sharing items", e); Toast.makeText(context, context.getString(R.string.lfm_action_share_error), Toast.LENGTH_SHORT).show() }
        exitSelectionMode()
    }

    fun openSelectedFileWithAnotherApp() {
        if (_selectedItems.value.size == 1) {
            val fileUri = _selectedItems.value.first()
            val mimeType = DocumentFile.fromSingleUri(context, fileUri)?.type ?: context.contentResolver.getType(fileUri) ?: "*/*"
            val intent = Intent(Intent.ACTION_VIEW).apply { setDataAndType(fileUri, mimeType); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            try { context.startActivity(Intent.createChooser(intent, context.getString(R.string.lfm_action_open_with_another_app)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) } catch (e: Exception) { Log.e("LFMViewModel", "Failed ACTION_VIEW: ${e.message}") }
        } else { Toast.makeText(context, context.getString(R.string.lfm_action_open_with_select_one_file), Toast.LENGTH_SHORT).show() }
        exitSelectionMode()
    }

    fun getItemDetailsForSelected() {
        if (_selectedItems.value.size == 1) {
            val uri = _selectedItems.value.first()
            val docFile = DocumentFile.fromSingleUri(context, uri)
            if (docFile != null) {
                _showItemDetailsDialog.value = ItemDetails(
                    name = docFile.name ?: "Unknown", path = docFile.uri.toString(), // Use URI string as path
                    type = if (docFile.isDirectory) context.getString(R.string.lfm_item_details_folder) else context.getString(R.string.lfm_item_details_file),
                    size = if (docFile.isFile) docFile.length() else null, dateModified = docFile.lastModified(), // lastModified works for both
                    mimeType = if (docFile.isFile) docFile.type ?: context.contentResolver.getType(uri) else null,
                    childrenCount = if (docFile.isDirectory) docFile.listFiles().size else null,
                    isReadable = docFile.canRead(), isWritable = docFile.canWrite(),
                    isHidden = docFile.name?.startsWith(".") ?: false
                )
            } else { _showItemDetailsDialog.value = ItemDetails("Error", "Could not access item", "Unknown", null, null, null, null, false, false, false) }
        } else { _showItemDetailsDialog.value = ItemDetails(context.getString(R.string.lfm_item_details_select_one_item), "", "", null, null, null, null, false, false, false) }
    }

    fun dismissItemDetailsDialog() { _showItemDetailsDialog.value = null }

    val triggerMoveNotImplementedToast = MutableStateFlow(false)
    val triggerArchiveNotImplementedToast = MutableStateFlow(false)
    fun onMoveSelected() { triggerMoveNotImplementedToast.value = true; exitSelectionMode() }
    fun onArchiveSelected() { triggerArchiveNotImplementedToast.value = true; exitSelectionMode() }
    fun resetMoveToastTrigger() { triggerMoveNotImplementedToast.value = false }
    fun resetArchiveToastTrigger() { triggerArchiveNotImplementedToast.value = false }
}

