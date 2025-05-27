package com.example.datagrindset.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.datagrindset.ProcessingStatus
import com.example.datagrindset.R
import com.example.datagrindset.ui.SortOption
import com.example.datagrindset.ViewType // Import new Enum
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
import java.io.IOException
import java.text.Normalizer
import java.util.Locale

// DirectoryEntry and ItemDetails data classes remain the same
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
    private val sharedPrefsName = "LocalFileManagerPrefs"
    private val viewTypePrefKey = "lfm_view_type"

    private val _rootTreeUri = MutableStateFlow<Uri?>(null)
    val rootTreeUri: StateFlow<Uri?> = _rootTreeUri.asStateFlow()

    private val _currentFolderUri = MutableStateFlow<Uri?>(null)
    val currentFolderUri: StateFlow<Uri?> = _currentFolderUri.asStateFlow()

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

    private val _launchDirectoryPickerEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val launchDirectoryPickerEvent = _launchDirectoryPickerEvent.asSharedFlow()

    private val _toastMessageEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastMessageEvent = _toastMessageEvent.asSharedFlow()

    private val _showCreateFolderDialog = MutableStateFlow(false)
    val showCreateFolderDialog: StateFlow<Boolean> = _showCreateFolderDialog.asStateFlow()

    private val _clipboardUris = MutableStateFlow<Set<Uri>>(emptySet())
    val clipboardUris: StateFlow<Set<Uri>> = _clipboardUris.asStateFlow()

    private val _isCutOperation = MutableStateFlow(false)

    private val _launchSystemFilePickerForMoveEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val launchSystemFilePickerForMoveEvent = _launchSystemFilePickerForMoveEvent.asSharedFlow()

    // --- NEW for ViewType ---
    private val _viewType = MutableStateFlow(ViewType.LIST)
    val viewType: StateFlow<ViewType> = _viewType.asStateFlow()
    // --- END NEW ---

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
        val prefs = application.getSharedPreferences(sharedPrefsName, Context.MODE_PRIVATE)
        prefs.getString("root_uri", null)?.let { uriString ->
            val uri = Uri.parse(uriString)
            if (checkAndRequestPersistedPermissions(uri)) {
                _rootTreeUri.value = uri
                _currentFolderUri.value = uri
                fetchDirectoryEntries(uri)
            } else {
                prefs.edit().remove("root_uri").apply()
            }
        }
        // Load ViewType preference
        val savedViewTypeName = prefs.getString(viewTypePrefKey, ViewType.LIST.name)
        _viewType.value = try { ViewType.valueOf(savedViewTypeName ?: ViewType.LIST.name) } catch (e: IllegalArgumentException) { ViewType.LIST }
    }

    // --- NEW for ViewType ---
    fun toggleViewType() {
        _viewType.value = if (_viewType.value == ViewType.LIST) ViewType.GRID else ViewType.LIST
        // Save preference
        getApplication<Application>().getSharedPreferences(sharedPrefsName, Context.MODE_PRIVATE).edit {
            putString(viewTypePrefKey, _viewType.value.name)
        }
    }
    // --- END NEW ---

    private fun SharedPreferences.edit(commit: Boolean = false, action: SharedPreferences.Editor.() -> Unit) {
        val editor = edit()
        action(editor)
        if (commit) editor.commit() else editor.apply()
    }


    private fun checkAndRequestPersistedPermissions(uri: Uri): Boolean {
        val persistedUriPermissions = context.contentResolver.persistedUriPermissions
        val hasPersistedPermission = persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
        if (hasPersistedPermission) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                return true
            } catch (e: SecurityException) { Log.e("LFMViewModel", "SecurityException re-taking permission for $uri", e) }
        }
        return false
    }

    fun setRootTreeUri(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            _rootTreeUri.value = uri
            _currentFolderUri.value = uri
            _directoryStack.value = emptyList()
            fetchDirectoryEntries(uri)
            exitSelectionMode()
            _clipboardUris.value = emptySet()
            getApplication<Application>().getSharedPreferences(sharedPrefsName, Context.MODE_PRIVATE).edit {
                putString("root_uri", uri.toString())
            }
        } catch (e: SecurityException) { Log.e("LFMViewModel", "SecurityException setting root URI: $uri", e) }
    }

    fun requestSelectRootDirectory() { _launchDirectoryPickerEvent.tryEmit(Unit) }

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
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val documentFile = DocumentFile.fromTreeUri(context, folderUri)
                if (documentFile == null || !documentFile.isDirectory) { _rawDirectoryEntries.value = emptyList(); return@launch }
                val entries = mutableListOf<DirectoryEntry>()
                documentFile.listFiles().forEach { file ->
                    if (file.name == null) return@forEach
                    if (file.isDirectory) { entries.add(DirectoryEntry.FolderEntry(id = file.uri.toString() + "_folder", name = file.name!!, uri = file.uri, childCount = file.listFiles().count { it.name != null })) }
                    else { entries.add(DirectoryEntry.FileEntry(id = file.uri.toString() + "_file", name = file.name!!, uri = file.uri, size = file.length(), dateModified = file.lastModified(), mimeType = file.type ?: context.contentResolver.getType(file.uri))) }
                }
                withContext(Dispatchers.Main) { _rawDirectoryEntries.value = entries }
            } catch (e: Exception) { withContext(Dispatchers.Main) { _rawDirectoryEntries.value = emptyList() } }
        }
    }

    fun prepareFileForAnalysis(fileEntry: DirectoryEntry.FileEntry) {
        val mimeType = fileEntry.mimeType?.lowercase(Locale.ROOT)
        val fileName = fileEntry.name.lowercase(Locale.ROOT)
        val isTxt = mimeType == "text/plain" || mimeType == "text/markdown" || fileName.endsWith(".txt", ignoreCase = true) || fileName.endsWith(".md", ignoreCase = true)
        val isCsv = mimeType == "text/csv" || mimeType == "application/csv" || fileName.endsWith(".csv", ignoreCase = true)

        when {
            isTxt -> { _fileProcessingStatusMap.value += (fileEntry.uri to (ProcessingStatus.SUCCESS to LocalizedSummary(R.string.processing_summary_ready_to_open_txt))); _navigateToAnalysisTarget.value = fileEntry }
            isCsv -> { _fileProcessingStatusMap.value += (fileEntry.uri to (ProcessingStatus.SUCCESS to LocalizedSummary(R.string.processing_summary_ready_to_open_csv))); _navigateToAnalysisTarget.value = fileEntry }
            else -> { _fileProcessingStatusMap.value += (fileEntry.uri to (ProcessingStatus.UNSUPPORTED to LocalizedSummary(R.string.processing_summary_unsupported_type_detailed, listOf(mimeType ?: "unknown")))); _suggestExternalAppForFile.value = fileEntry }
        }
    }

    fun deleteSelectedItems() {
        viewModelScope.launch(Dispatchers.IO) {
            _selectedItems.value.forEach { uri ->
                try { DocumentFile.fromSingleUri(context, uri)?.delete(); _fileProcessingStatusMap.value -= uri }
                catch (e: Exception) { Log.e("LFMViewModel", "Error deleting $uri", e) }
            }
            _currentFolderUri.value?.let { fetchDirectoryEntries(it) }
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
        if (selectedUris.isEmpty()) { _toastMessageEvent.tryEmit(context.getString(R.string.lfm_action_share_no_items)); return }
        val shareIntent = Intent().apply {
            action = if (selectedUris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE
            type = "*/*"; val urisToShare = ArrayList(selectedUris)
            if (selectedUris.size == 1) { putExtra(Intent.EXTRA_STREAM, urisToShare.first()); DocumentFile.fromSingleUri(context, urisToShare.first())?.type?.let { type = it } }
            else { putParcelableArrayListExtra(Intent.EXTRA_STREAM, urisToShare) }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try { context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.lfm_bottom_bar_share_desc)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) }
        catch (e: Exception) { _toastMessageEvent.tryEmit(context.getString(R.string.lfm_action_share_error)) }
        exitSelectionMode()
    }

    fun openSelectedFileWithAnotherApp() {
        if (_selectedItems.value.size == 1) {
            val fileUri = _selectedItems.value.first()
            val mimeType = DocumentFile.fromSingleUri(context, fileUri)?.type ?: context.contentResolver.getType(fileUri) ?: "*/*"
            val intent = Intent(Intent.ACTION_VIEW).apply { setDataAndType(fileUri, mimeType); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            try { context.startActivity(Intent.createChooser(intent, context.getString(R.string.lfm_action_open_with_another_app)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) }
            catch (e: Exception) { _toastMessageEvent.tryEmit(context.getString(R.string.lfm_could_not_open_file_toast, e.localizedMessage ?: "Unknown")) }
        } else { _toastMessageEvent.tryEmit(context.getString(R.string.lfm_action_open_with_select_one_file)) }
        exitSelectionMode()
    }

    fun getItemDetailsForSelected() {
        if (_selectedItems.value.size == 1) {
            val uri = _selectedItems.value.first()
            val docFile = DocumentFile.fromSingleUri(context, uri)
            if (docFile != null) {
                _showItemDetailsDialog.value = ItemDetails(name = docFile.name ?: "Unknown", path = docFile.uri.toString(), type = if (docFile.isDirectory) context.getString(R.string.lfm_item_details_folder) else context.getString(R.string.lfm_item_details_file), size = if (docFile.isFile) docFile.length() else null, dateModified = docFile.lastModified(), mimeType = if (docFile.isFile) docFile.type ?: context.contentResolver.getType(uri) else null, childrenCount = if (docFile.isDirectory) docFile.listFiles().size else null, isReadable = docFile.canRead(), isWritable = docFile.canWrite(), isHidden = docFile.name?.startsWith(".") ?: false)
            } else { _showItemDetailsDialog.value = ItemDetails(context.getString(R.string.lfm_item_details_no_details), "", "", null,null,null,null,false,false,false) }
        } else { _showItemDetailsDialog.value = ItemDetails(context.getString(R.string.lfm_item_details_select_one_item), "", "", null,null,null,null,false,false,false) }
    }
    fun dismissItemDetailsDialog() { _showItemDetailsDialog.value = null }

    fun requestShowCreateFolderDialog() { _showCreateFolderDialog.value = true }
    fun dismissCreateFolderDialog() { _showCreateFolderDialog.value = false }

    fun createFolderInCurrentDirectory(folderName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val parentUri = _currentFolderUri.value ?: _rootTreeUri.value
            if (parentUri == null) { _toastMessageEvent.tryEmit(context.getString(R.string.lfm_error_creating_folder_toast, "No current dir")); return@launch }
            if (folderName.isBlank()) { _toastMessageEvent.tryEmit(context.getString(R.string.lfm_empty_folder_name_toast)); return@launch }
            val parentDocFile = DocumentFile.fromTreeUri(context, parentUri)
            try {
                val newFolder = parentDocFile?.createDirectory(folderName)
                if (newFolder != null && newFolder.exists()) { _toastMessageEvent.tryEmit(context.getString(R.string.lfm_folder_created_toast, folderName)); fetchDirectoryEntries(parentUri) }
                else { _toastMessageEvent.tryEmit(context.getString(R.string.lfm_error_creating_folder_toast, "Failed")) }
            } catch (e: Exception) { _toastMessageEvent.tryEmit(context.getString(R.string.lfm_error_creating_folder_toast, e.localizedMessage ?: "Unknown")) }
            withContext(Dispatchers.Main) { dismissCreateFolderDialog() }
        }
    }

    fun copySelectedToClipboard() {
        if (_selectedItems.value.isEmpty()) return
        _clipboardUris.value = _selectedItems.value.toSet(); _isCutOperation.value = false
        _toastMessageEvent.tryEmit(context.getString(R.string.lfm_action_copy_to_clipboard_toast, _clipboardUris.value.size))
        exitSelectionMode()
    }

    fun cutSelectedToClipboard() {
        if (_selectedItems.value.isEmpty()) return
        _clipboardUris.value = _selectedItems.value.toSet(); _isCutOperation.value = true
        _toastMessageEvent.tryEmit(context.getString(R.string.lfm_action_cut_to_clipboard_toast, _clipboardUris.value.size))
        exitSelectionMode()
    }

    fun pasteFromClipboard() {
        val urisToProcess = _clipboardUris.value
        val targetDirUri = _currentFolderUri.value ?: _rootTreeUri.value
        if (urisToProcess.isEmpty()) { _toastMessageEvent.tryEmit(context.getString(R.string.lfm_no_items_to_paste_toast)); return }
        if (targetDirUri == null) { _toastMessageEvent.tryEmit(context.getString(R.string.lfm_paste_error_toast, "No destination")); return }

        viewModelScope.launch(Dispatchers.IO) {
            var successCount = 0; var errorOccurred = false
            try {
                val targetDirDoc = DocumentFile.fromTreeUri(context, targetDirUri) ?: throw IOException("Invalid dest dir")
                for (sourceUri in urisToProcess) {
                    if (sourceUri == targetDirUri || isUriDescendant(sourceUri, targetDirUri)) {
                        Log.w("LFMViewModel", "Skipping paste: source $sourceUri is same or parent of target $targetDirUri")
                        errorOccurred = true; continue
                    }
                    val sourceDoc = DocumentFile.fromSingleUri(context, sourceUri)
                    if (sourceDoc == null) {
                        Log.w("LFMViewModel", "Skipping paste: Could not access sourceDoc for $sourceUri")
                        errorOccurred = true; continue
                    }

                    if (copyDocument(sourceDoc, targetDirDoc)) {
                        successCount++; if (_isCutOperation.value) sourceDoc.delete()
                    } else errorOccurred = true
                }
                _toastMessageEvent.tryEmit(context.getString(R.string.lfm_paste_success_toast, successCount))
                if (errorOccurred && successCount < urisToProcess.size) _toastMessageEvent.tryEmit(context.getString(R.string.lfm_paste_error_toast, "Some items failed"))
            } catch (e: Exception) { _toastMessageEvent.tryEmit(context.getString(R.string.lfm_paste_error_toast, e.localizedMessage ?: "IO Error")) }
            finally { _clipboardUris.value = emptySet(); _isCutOperation.value = false; fetchDirectoryEntries(targetDirUri) }
        }
    }

    private fun isUriDescendant(childUri: Uri, parentUri: Uri): Boolean = childUri.toString().startsWith(parentUri.toString()) && childUri != parentUri

    private suspend fun copyDocument(sourceDoc: DocumentFile, targetParentDoc: DocumentFile): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (sourceDoc.isDirectory) {
                    val newDir = targetParentDoc.createDirectory(sourceDoc.name ?: "Dir_${System.currentTimeMillis()}") ?: return@withContext false
                    var success = true; sourceDoc.listFiles().forEach { if (!copyDocument(it, newDir)) success = false }; success
                } else {
                    val newFile = targetParentDoc.createFile(sourceDoc.type ?: "application/octet-stream", sourceDoc.name ?: "File_${System.currentTimeMillis()}") ?: return@withContext false
                    context.contentResolver.openInputStream(sourceDoc.uri)?.use { input -> context.contentResolver.openOutputStream(newFile.uri)?.use { output -> input.copyTo(output) } ?: return@withContext false } ?: return@withContext false
                    true
                }
            } catch (e: IOException) { Log.e("LFMViewModel", "Error copying ${sourceDoc.uri} to ${targetParentDoc.uri}", e); false }
        }
    }

    fun initiateSelectExternalItemsToMove() { _launchSystemFilePickerForMoveEvent.tryEmit(Unit) }

    fun moveUrisToCurrentDirectory(sourceUris: List<Uri>) {
        val targetDirUri = _currentFolderUri.value ?: _rootTreeUri.value
        if (targetDirUri == null) { _toastMessageEvent.tryEmit(context.getString(R.string.lfm_move_items_here_error_toast, "No dest")); return }
        if (sourceUris.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            var successCount = 0; var errorOccurred = false
            val targetDirDoc = DocumentFile.fromTreeUri(context, targetDirUri) ?: run { _toastMessageEvent.tryEmit(context.getString(R.string.lfm_move_items_here_error_toast, "Invalid dest")); return@launch }

            for (sourceUri in sourceUris) {
                val sourceDoc = DocumentFile.fromSingleUri(context, sourceUri)
                if (sourceDoc == null) {
                    Log.w("LFMViewModel", "Skipping move: Could not access sourceDoc for $sourceUri")
                    errorOccurred = true; continue
                }

                if (sourceUri == targetDirUri || sourceDoc.isDirectory && isUriDescendant(targetDirUri, sourceUri)) {
                    Log.w("LFMViewModel", "Cannot move folder into itself or its child for $sourceUri")
                    errorOccurred = true; continue
                }
                try {
                    if (copyDocument(sourceDoc, targetDirDoc)) { if (sourceDoc.delete()) successCount++ else { Log.w("LFMViewModel", "Copied ${sourceDoc.name} but failed to delete original."); errorOccurred = true } }
                    else { Log.e("LFMViewModel", "Failed to copy ${sourceDoc.name} for move operation."); errorOccurred = true }
                } catch (e: Exception) { Log.e("LFMViewModel", "Error moving ${sourceDoc.name}: ${e.message}", e); errorOccurred = true }
            }
            if (successCount > 0) _toastMessageEvent.tryEmit(context.getString(R.string.lfm_move_items_here_success_toast, successCount))
            if (errorOccurred) _toastMessageEvent.tryEmit(context.getString(R.string.lfm_move_items_here_error_toast, "Some failed"))
            fetchDirectoryEntries(targetDirUri)
        }
    }

    fun onArchiveSelected() { _toastMessageEvent.tryEmit(context.getString(R.string.lfm_action_archive_not_implemented)); exitSelectionMode() }
}
