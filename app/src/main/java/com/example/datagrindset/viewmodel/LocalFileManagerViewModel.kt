package com.example.datagrindset.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.datagrindset.ProcessingStatus
import com.example.datagrindset.R
import com.example.datagrindset.ui.SortOption
import com.example.datagrindset.ViewType
import com.google.firebase.auth.FirebaseUser
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
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.text.Normalizer
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

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
        val childCount: Int,
        val isSecuredUserDataRoot: Boolean = false // Flag for user's own .userData_UID folder
    ) : DirectoryEntry(id, name, uri)
}
data class BatchRenameDialogState(
    val baseName: String = "",
    val startNumber: String = "1",
    val numDigits: String = "0", // 0 or empty means no padding
    val keepExtension: Boolean = true,
    val itemsToRenameCount: Int = 0 // To show in dialog title
)

/**
 * Options passed to the actual renaming function after dialog confirmation.
 */
data class PerformBatchRenameOptions(
    val baseName: String,
    val startNumber: Int,
    val numDigits: Int,
    val keepOriginalExtension: Boolean
)


data class ItemDetails(
    val name: String,
    val path: String,
    val type: String, // "Folder" or "File"
    val size: Long?, // Null for folders
    val dateModified: Long?,
    val mimeType: String?, // Null for folders
    val childrenCount: Int?, // Null for files
    val isReadable: Boolean,
    val isWritable: Boolean,
    val isHidden: Boolean,
    val isSecured: Boolean = false
)

object MimeTypeMapHelper {
    fun getMimeTypeFromExtension(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (extension) {
            "txt" -> "text/plain"
            "csv" -> "text/csv"
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }
    }
}

open class LocalFileManagerViewModel(
    application: Application,
    private val currentUserState: StateFlow<FirebaseUser?>
) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()
    private val sharedPrefsName = "LocalFileManagerPrefs"
    private val viewTypePrefKey = "lfm_view_type"
    private val rootUriPrefKey = "root_uri"
    private val TAG = "LFMViewModel"

    companion object {
        const val USER_DATA_ROOT_PREFIX = ".userData_"
        const val MY_SECURED_SPACE_DISPLAY_NAME = "My Secured Space"
    }

    private val currentUserId: StateFlow<String?> = currentUserState
        .map { it?.uid }
        .stateIn(viewModelScope, SharingStarted.Eagerly, currentUserState.value?.uid)


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

    private val _selectedItems = MutableStateFlow<Set<DirectoryEntry>>(emptySet())
    val selectedItems: StateFlow<Set<DirectoryEntry>> = _selectedItems.asStateFlow()

    val selectedItemsUris: StateFlow<Set<Uri>> = _selectedItems.map { items -> items.map { it.uri }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptySet())

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

    private val _viewType = MutableStateFlow(ViewType.LIST)
    val viewType: StateFlow<ViewType> = _viewType.asStateFlow()

    private val _showArchiveNameDialog = MutableStateFlow<List<DirectoryEntry>?>(null)
    val showArchiveNameDialog: StateFlow<List<DirectoryEntry>?> = _showArchiveNameDialog.asStateFlow()

    private val _showExtractOptionsDialog = MutableStateFlow<DirectoryEntry.FileEntry?>(null)
    val showExtractOptionsDialog: StateFlow<DirectoryEntry.FileEntry?> = _showExtractOptionsDialog.asStateFlow()

    private val _launchDirectoryPickerForExtractionEvent = MutableSharedFlow<DirectoryEntry.FileEntry>(extraBufferCapacity = 1)
    val launchDirectoryPickerForExtractionEvent = _launchDirectoryPickerForExtractionEvent.asSharedFlow()

    private val _batchRenameDialogState = MutableStateFlow<BatchRenameDialogState?>(null)
    val batchRenameDialogState: StateFlow<BatchRenameDialogState?> = _batchRenameDialogState.asStateFlow()


    val directoryEntries: StateFlow<List<DirectoryEntry>> = combine(
        _rawDirectoryEntries, _searchText, _sortOption, currentUserId
    ) { entries, text, sortOpt, _ ->
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
        _rootTreeUri, _directoryStack, currentUserId
    ) { rootUri, stack, uid ->
        if (rootUri == null) return@combine context.getString(R.string.lfm_select_root_prompt)
        val rootDocFile = DocumentFile.fromTreeUri(context, rootUri)
        val rootName = rootDocFile?.name ?: "Root"

        val pathSegments = mutableListOf<String>()
        var isInsideSecuredSpace = false

        for (uriSegment in stack) {
            // For stack URIs representing directories, fromTreeUri is appropriate
            val segmentDocFile = DocumentFile.fromTreeUri(context, uriSegment)
            val segmentName = segmentDocFile?.name
            if (segmentName != null) {
                if (uid != null && segmentName == "$USER_DATA_ROOT_PREFIX$uid") {
                    pathSegments.add(MY_SECURED_SPACE_DISPLAY_NAME)
                    isInsideSecuredSpace = true
                } else if (segmentName.startsWith(USER_DATA_ROOT_PREFIX) && !isInsideSecuredSpace) {
                    pathSegments.add("Secured Space (Other)")
                    isInsideSecuredSpace = true
                }
                else {
                    pathSegments.add(segmentName)
                }
            }
        }
        (listOf(rootName) + pathSegments).joinToString(" > ")
    }.stateIn(viewModelScope, SharingStarted.Lazily, context.getString(R.string.lfm_select_root_prompt))


    val canNavigateUp: StateFlow<Boolean> = _directoryStack.combine(_rootTreeUri) { stack, root ->
        stack.isNotEmpty() && root != null
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    init {
        val prefs = application.getSharedPreferences(sharedPrefsName, Context.MODE_PRIVATE)
        prefs.getString(rootUriPrefKey, null)?.let { uriString ->
            val uri = Uri.parse(uriString)
            if (checkAndRequestPersistedPermissions(uri)) {
                _rootTreeUri.value = uri
                _currentFolderUri.value = uri
            } else {
                prefs.edit().remove(rootUriPrefKey).apply()
            }
        }
        val savedViewTypeName = prefs.getString(viewTypePrefKey, ViewType.LIST.name)
        _viewType.value = try { ViewType.valueOf(savedViewTypeName ?: ViewType.LIST.name) } catch (e: IllegalArgumentException) { ViewType.LIST }

        viewModelScope.launch {
            currentUserId.collect { _ ->
                _currentFolderUri.value?.let { currentUri ->
                    Log.d(TAG, "User ID changed or initial load with user, refetching for $currentUri")
                    fetchDirectoryEntries(currentUri)
                } ?: _rootTreeUri.value?.let { rootUri ->
                    Log.d(TAG, "User ID changed or initial load with user, refetching for root $rootUri")
                    _currentFolderUri.value = rootUri
                    fetchDirectoryEntries(rootUri)
                }
            }
        }
    }

    private suspend fun ensureAndGetUserDataRoot(): DocumentFile? = withContext(Dispatchers.IO) {
        val uid = currentUserId.value ?: run {
            _toastMessageEvent.tryEmit(context.getString(R.string.lfm_login_required_for_secured_folder))
            return@withContext null
        }
        val appRootUri = _rootTreeUri.value ?: run {
            _toastMessageEvent.tryEmit(context.getString(R.string.lfm_select_root_directory_first))
            return@withContext null
        }
        val appRootDoc = DocumentFile.fromTreeUri(context, appRootUri) ?: run {
            _toastMessageEvent.tryEmit(context.getString(R.string.lfm_invalid_root_directory))
            return@withContext null
        }

        val userSecuredFolderName = "$USER_DATA_ROOT_PREFIX$uid"
        var userDataRoot = appRootDoc.findFile(userSecuredFolderName)
        if (userDataRoot == null) {
            Log.d(TAG, "User data root '$userSecuredFolderName' not found, attempting to create.")
            userDataRoot = appRootDoc.createDirectory(userSecuredFolderName)
            if (userDataRoot == null) {
                Log.e(TAG, "Failed to create user data root '$userSecuredFolderName'")
                _toastMessageEvent.tryEmit(context.getString(R.string.lfm_error_creating_secured_space))
                return@withContext null
            }
            Log.d(TAG, "Successfully created user data root: ${userDataRoot.uri}")
        } else if (!userDataRoot.isDirectory) {
            Log.e(TAG, "User data root path '$userSecuredFolderName' exists but is not a directory.")
            _toastMessageEvent.tryEmit(context.getString(R.string.lfm_error_secured_space_not_folder))
            return@withContext null
        }
        userDataRoot
    }

    fun navigateToMySecuredSpace() {
        viewModelScope.launch {
            val uid = currentUserId.value
            if (uid == null) {
                _toastMessageEvent.tryEmit(context.getString(R.string.lfm_login_required_for_secured_folder))
                return@launch
            }
            if (_rootTreeUri.value == null) {
                _toastMessageEvent.tryEmit(context.getString(R.string.lfm_select_root_directory_first))
                requestSelectRootDirectory()
                return@launch
            }

            val userDataRootDocFile = ensureAndGetUserDataRoot()
            userDataRootDocFile?.let {
                val userSecuredRootEntry = DirectoryEntry.FolderEntry(
                    id = it.uri.toString() + "_folder_secured_root",
                    name = MY_SECURED_SPACE_DISPLAY_NAME,
                    uri = it.uri,
                    childCount = it.listFiles().count { f -> f.name != null },
                    isSecuredUserDataRoot = true
                )
                _directoryStack.value = emptyList()
                _currentFolderUri.value = _rootTreeUri.value

                _directoryStack.value = listOf(userSecuredRootEntry.uri)
                _currentFolderUri.value = userSecuredRootEntry.uri
                fetchDirectoryEntries(userSecuredRootEntry.uri)
                exitSelectionMode()
                Log.d(TAG, "Navigated to user's secured space: ${userSecuredRootEntry.uri}")
            }
        }
    }


    fun toggleViewType() {
        _viewType.value = if (_viewType.value == ViewType.LIST) ViewType.GRID else ViewType.LIST
        getApplication<Application>().getSharedPreferences(sharedPrefsName, Context.MODE_PRIVATE).edit {
            putString(viewTypePrefKey, _viewType.value.name)
        }
    }

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
            } catch (e: SecurityException) { Log.e(TAG, "SecurityException re-taking permission for $uri", e) }
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
                putString(rootUriPrefKey, uri.toString())
            }
        } catch (e: SecurityException) { Log.e(TAG, "SecurityException setting root URI: $uri", e) }
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
                if (documentFile == null || !documentFile.isDirectory) {
                    Log.w(TAG, "Cannot fetch entries: $folderUri is not a valid directory.")
                    _rawDirectoryEntries.value = emptyList(); return@launch
                }

                val entries = mutableListOf<DirectoryEntry>()
                val currentUid = currentUserId.value
                val isAppRootFolder = folderUri == _rootTreeUri.value

                documentFile.listFiles().forEach { file ->
                    val fileName = file.name ?: return@forEach

                    if (isAppRootFolder && fileName.startsWith(USER_DATA_ROOT_PREFIX)) {
                        if (currentUid != null && fileName == "$USER_DATA_ROOT_PREFIX$currentUid") {
                            entries.add(DirectoryEntry.FolderEntry(
                                id = file.uri.toString() + "_folder_secured_root",
                                name = MY_SECURED_SPACE_DISPLAY_NAME,
                                uri = file.uri,
                                childCount = file.listFiles().count { it.name != null },
                                isSecuredUserDataRoot = true
                            ))
                        } else {
                            Log.d(TAG, "Hiding user data folder: $fileName (current UID: $currentUid)")
                        }
                    } else if (file.isDirectory) {
                        entries.add(DirectoryEntry.FolderEntry(
                            id = file.uri.toString() + "_folder",
                            name = fileName,
                            uri = file.uri,
                            childCount = file.listFiles().count { it.name != null }
                        ))
                    } else {
                        entries.add(DirectoryEntry.FileEntry(
                            id = file.uri.toString() + "_file",
                            name = fileName,
                            uri = file.uri,
                            size = file.length(),
                            dateModified = file.lastModified(),
                            mimeType = file.type ?: context.contentResolver.getType(file.uri)
                        ))
                    }
                }
                withContext(Dispatchers.Main) { _rawDirectoryEntries.value = entries }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching directory entries for $folderUri", e)
                withContext(Dispatchers.Main) { _rawDirectoryEntries.value = emptyList() }
            }
        }
    }


    fun prepareFileForAnalysis(fileEntry: DirectoryEntry.FileEntry) {
        Log.d(TAG, "Preparing file for analysis: ${fileEntry.name}, URI: ${fileEntry.uri}")
        val mimeType = fileEntry.mimeType?.lowercase(Locale.ROOT)
        val fileName = fileEntry.name
        val isTxt = mimeType == "text/plain" || mimeType == "text/markdown" || fileName.lowercase(Locale.ROOT).endsWith(".txt") || fileName.lowercase(Locale.ROOT).endsWith(".md")
        val isCsv = mimeType == "text/csv" || mimeType == "application/csv" || fileName.lowercase(Locale.ROOT).endsWith(".csv")

        when {
            isTxt -> {
                Log.d(TAG, "TXT file identified. Setting navigateToAnalysisTarget.")
                _fileProcessingStatusMap.value += (fileEntry.uri to (ProcessingStatus.SUCCESS to LocalizedSummary(R.string.processing_summary_ready_to_open_txt)))
                _navigateToAnalysisTarget.value = fileEntry
            }
            isCsv -> {
                Log.d(TAG, "CSV file identified. Setting navigateToAnalysisTarget.")
                _fileProcessingStatusMap.value += (fileEntry.uri to (ProcessingStatus.SUCCESS to LocalizedSummary(R.string.processing_summary_ready_to_open_csv)))
                _navigateToAnalysisTarget.value = fileEntry
            }
            else -> {
                Log.d(TAG, "Unsupported file type. Suggesting external app.")
                _fileProcessingStatusMap.value += (fileEntry.uri to (ProcessingStatus.UNSUPPORTED to LocalizedSummary(R.string.processing_summary_unsupported_type_detailed, listOf(mimeType ?: "unknown"))))
                _suggestExternalAppForFile.value = fileEntry
            }
        }
    }

    fun deleteSelectedItems() {
        viewModelScope.launch(Dispatchers.IO) {
            _selectedItems.value.forEach { entry ->
                try {
                    val docFile = if (entry is DirectoryEntry.FolderEntry && entry.isSecuredUserDataRoot) {
                        // For "My Secured Space", we need to get the actual DocumentFile using its real name
                        val uid = currentUserId.value
                        if (uid != null) {
                            val appRootUri = _rootTreeUri.value
                            val appRootDoc = appRootUri?.let { DocumentFile.fromTreeUri(context, it) }
                            appRootDoc?.findFile("$USER_DATA_ROOT_PREFIX$uid")
                        } else null
                    } else {
                        DocumentFile.fromTreeUri(context, entry.uri)
                    }
                    docFile?.delete()
                    _fileProcessingStatusMap.value -= entry.uri
                } catch (e: Exception) { Log.e(TAG, "Error deleting ${entry.uri}", e) }
            }
            _currentFolderUri.value?.let { fetchDirectoryEntries(it) }
            withContext(Dispatchers.Main) { exitSelectionMode() }
        }
    }


    fun didNavigateToAnalysisScreen() {
        Log.d(TAG, "didNavigateToAnalysisScreen called. Resetting navigateToAnalysisTarget.")
        _navigateToAnalysisTarget.value = null
    }
    fun didAttemptToOpenWithExternalApp() { _suggestExternalAppForFile.value = null }

    fun enterSelectionMode(item: DirectoryEntry) {
        Log.d(TAG, "Entering selection mode for item: ${item.name}")
        _isSelectionModeActive.value = true
        _selectedItems.value = setOf(item)
    }
    fun exitSelectionMode() {
        Log.d(TAG, "Exiting selection mode.")
        _isSelectionModeActive.value = false
        _selectedItems.value = emptySet()
    }
    fun toggleItemSelected(item: DirectoryEntry) {
        _selectedItems.value = if (_selectedItems.value.any { it.uri == item.uri }) {
            _selectedItems.value.filterNot { it.uri == item.uri }.toSet()
        } else {
            _selectedItems.value + item
        }
        Log.d(TAG, "Toggled item ${item.name}. Selected items: ${_selectedItems.value.size}")
    }
    fun selectAllInCurrentDirectory() { _selectedItems.value = _rawDirectoryEntries.value.toSet() }
    fun deselectAll() { _selectedItems.value = emptySet() }

    fun shareSelectedItems() {
        val selectedUrisToShare = _selectedItems.value.map {
            if (it is DirectoryEntry.FolderEntry && it.isSecuredUserDataRoot) {
                // Need to get the actual URI if "My Secured Space" is selected
                val uid = currentUserId.value
                if (uid != null) {
                    val appRootUri = _rootTreeUri.value
                    val appRootDoc = appRootUri?.let { DocumentFile.fromTreeUri(context, it) }
                    appRootDoc?.findFile("$USER_DATA_ROOT_PREFIX$uid")?.uri
                } else null
            } else {
                it.uri
            }
        }.filterNotNull().toList()


        if (selectedUrisToShare.isEmpty()) { _toastMessageEvent.tryEmit(context.getString(R.string.lfm_action_share_no_items)); return }
        val shareIntent = Intent().apply {
            action = if (selectedUrisToShare.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE
            type = "*/*"
            val urisToShareList = ArrayList(selectedUrisToShare)
            if (selectedUrisToShare.size == 1) {
                putExtra(Intent.EXTRA_STREAM, urisToShareList.first())
                DocumentFile.fromSingleUri(context, urisToShareList.first())?.type?.let { type = it }
            } else {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, urisToShareList)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try { context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.lfm_bottom_bar_share_desc)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) }
        catch (e: Exception) { _toastMessageEvent.tryEmit(context.getString(R.string.lfm_action_share_error)) }
        exitSelectionMode()
    }


    fun openSelectedFileWithAnotherApp() {
        if (_selectedItems.value.size == 1 && _selectedItems.value.first() is DirectoryEntry.FileEntry) {
            val fileEntry = _selectedItems.value.first() as DirectoryEntry.FileEntry
            val mimeType = fileEntry.mimeType ?: context.contentResolver.getType(fileEntry.uri) ?: "*/*"
            val intent = Intent(Intent.ACTION_VIEW).apply { setDataAndType(fileEntry.uri, mimeType); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            try { context.startActivity(Intent.createChooser(intent, context.getString(R.string.lfm_action_open_with_another_app)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) }
            catch (e: Exception) { _toastMessageEvent.tryEmit(context.getString(R.string.lfm_could_not_open_file_toast, e.localizedMessage ?: "Unknown")) }
        } else { _toastMessageEvent.tryEmit(context.getString(R.string.lfm_action_open_with_select_one_file)) }
        exitSelectionMode()
    }


    fun getItemDetailsForSelected() {
        if (_selectedItems.value.size == 1) {
            val entry = _selectedItems.value.first()
            val docFile = DocumentFile.fromTreeUri(context, entry.uri) // This might be an issue for "My Secured Space" as its URI is the actual one

            if (docFile != null) {
                val isUserSecuredRoot = entry is DirectoryEntry.FolderEntry && entry.isSecuredUserDataRoot
                val actualName = if(isUserSecuredRoot) "$USER_DATA_ROOT_PREFIX${currentUserId.value}" else entry.name

                _showItemDetailsDialog.value = ItemDetails(
                    name = entry.name,
                    path = docFile.uri.path ?: docFile.uri.toString(),
                    type = if (entry is DirectoryEntry.FolderEntry) context.getString(R.string.lfm_item_details_folder) else context.getString(R.string.lfm_item_details_file),
                    size = if (entry is DirectoryEntry.FileEntry && docFile.isFile) docFile.length() else null,
                    dateModified = if(docFile.lastModified() > 0) docFile.lastModified() else null,
                    mimeType = if (entry is DirectoryEntry.FileEntry && docFile.isFile) docFile.type else null,
                    childrenCount = if (entry is DirectoryEntry.FolderEntry && docFile.isDirectory) entry.childCount else null,
                    isReadable = docFile.canRead(),
                    isWritable = docFile.canWrite(),
                    isHidden = actualName.startsWith("."),
                    isSecured = isUserSecuredRoot || (currentUserId.value != null && docFile.uri.toString().contains("$USER_DATA_ROOT_PREFIX${currentUserId.value}"))
                )
            } else {
                Log.w(TAG, "Could not get DocumentFile for details: ${entry.uri}")
                _showItemDetailsDialog.value = ItemDetails(context.getString(R.string.lfm_item_details_no_details), "", "", null,null,null,null,false,false,false, false)
            }
        } else {
            _showItemDetailsDialog.value = ItemDetails(context.getString(R.string.lfm_item_details_select_one_item), "", "", null,null,null,null,false,false,false, false)
        }
    }


    fun dismissItemDetailsDialog() { _showItemDetailsDialog.value = null }
    fun requestShowCreateFolderDialog() { _showCreateFolderDialog.value = true }
    fun dismissCreateFolderDialog() { _showCreateFolderDialog.value = false }

    fun createFolderInCurrentDirectory(folderNameInput: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val folderName = folderNameInput.trim()
            val parentUri = _currentFolderUri.value ?: _rootTreeUri.value
            if (parentUri == null) { _toastMessageEvent.tryEmit(context.getString(R.string.lfm_error_creating_folder_toast, "No current directory selected")); return@launch }
            if (folderName.isBlank()) { _toastMessageEvent.tryEmit(context.getString(R.string.lfm_empty_folder_name_toast)); return@launch }

            val parentDocFile = DocumentFile.fromTreeUri(context, parentUri)
            try {
                if (folderName.startsWith(USER_DATA_ROOT_PREFIX)) {
                    val currentUid = currentUserId.value
                    // Check if trying to create a secured folder manually at the app root
                    if (parentUri == _rootTreeUri.value && folderName != "$USER_DATA_ROOT_PREFIX$currentUid") {
                        _toastMessageEvent.tryEmit(context.getString(R.string.lfm_error_manual_secured_folder_creation))
                        return@launch
                    }
                    // Allow creation if inside user's own secured space (parentUri is already the secured space)
                    // Or if it's the auto-creation of the user's root, which is handled by ensureAndGetUserDataRoot
                }

                val newFolder = parentDocFile?.createDirectory(folderName)
                if (newFolder != null && newFolder.exists()) {
                    _toastMessageEvent.tryEmit(context.getString(R.string.lfm_folder_created_toast, folderName))
                    fetchDirectoryEntries(parentUri)
                } else {
                    _toastMessageEvent.tryEmit(context.getString(R.string.lfm_error_creating_folder_toast, "Failed to create folder"))
                }
            } catch (e: Exception) {
                _toastMessageEvent.tryEmit(context.getString(R.string.lfm_error_creating_folder_toast, e.localizedMessage ?: "Unknown error"))
            }
            withContext(Dispatchers.Main) { dismissCreateFolderDialog() }
        }
    }


    fun copySelectedToClipboard() {
        if (_selectedItems.value.isEmpty()) return
        _clipboardUris.value = _selectedItems.value.map { it.uri }.toSet()
        _isCutOperation.value = false
        _toastMessageEvent.tryEmit(context.getString(R.string.lfm_action_copy_to_clipboard_toast, _clipboardUris.value.size))
        exitSelectionMode()
    }

    fun cutSelectedToClipboard() {
        if (_selectedItems.value.isEmpty()) return
        _clipboardUris.value = _selectedItems.value.map { it.uri }.toSet()
        _isCutOperation.value = true
        _toastMessageEvent.tryEmit(context.getString(R.string.lfm_action_cut_to_clipboard_toast, _clipboardUris.value.size))
        exitSelectionMode()
    }

    fun pasteFromClipboard() {
        val urisToProcess = _clipboardUris.value
        val targetDirUri = _currentFolderUri.value ?: _rootTreeUri.value
        if (urisToProcess.isEmpty()) { _toastMessageEvent.tryEmit(context.getString(R.string.lfm_no_items_to_paste_toast)); return }
        if (targetDirUri == null) { _toastMessageEvent.tryEmit(context.getString(R.string.lfm_paste_error_toast, "No destination directory")); return }

        viewModelScope.launch(Dispatchers.IO) {
            var successCount = 0; var errorOccurred = false
            try {
                val targetDirDoc = DocumentFile.fromTreeUri(context, targetDirUri) ?: throw IOException("Invalid destination directory")
                for (sourceUri in urisToProcess) {
                    if (sourceUri == targetDirUri || isUriDescendant(sourceUri, targetDirUri)) {
                        Log.w(TAG, "Skipping paste: source $sourceUri is same or parent of target $targetDirUri")
                        errorOccurred = true; continue
                    }
                    // For source URIs from clipboard, they might be single or tree.
                    // If it's a directory, fromTreeUri is fine. If a file, fromSingleUri.
                    // DocumentFile.fromTreeUri can sometimes work for single files if permissions are broad.
                    // However, to be safe, if we know it's a file, fromSingleUri is better.
                    // Since clipboardUris are just URIs, we don't know their type yet.
                    // The copyDocument function will handle this.
                    val sourceDoc = DocumentFile.fromTreeUri(context, sourceUri) // Or fromSingleUri if known to be file
                    if (sourceDoc == null) {
                        Log.w(TAG, "Skipping paste: Could not access sourceDoc for $sourceUri")
                        errorOccurred = true; continue
                    }
                    val sourceName = sourceDoc.name
                    if (sourceName != null && sourceName.startsWith(USER_DATA_ROOT_PREFIX)) {
                        val currentUid = currentUserId.value
                        if (currentUid == null || sourceName != "$USER_DATA_ROOT_PREFIX$currentUid") {
                            Log.w(TAG, "Skipping paste of other user's secured folder: $sourceName")
                            errorOccurred = true; continue
                        }
                    }

                    if (copyDocument(sourceDoc, targetDirDoc)) {
                        successCount++; if (_isCutOperation.value) sourceDoc.delete()
                    } else errorOccurred = true
                }

                if (successCount > 0) _toastMessageEvent.tryEmit(context.getString(R.string.lfm_paste_success_toast, successCount))
                if (errorOccurred && successCount < urisToProcess.size) _toastMessageEvent.tryEmit(context.getString(R.string.lfm_paste_error_toast, "Some items failed"))

            } catch (e: Exception) {
                Log.e(TAG, "Error during paste operation", e)
                _toastMessageEvent.tryEmit(context.getString(R.string.lfm_paste_error_toast, e.localizedMessage ?: "IO Error"))
            }
            finally {
                if (_isCutOperation.value) _clipboardUris.value = emptySet()
                _isCutOperation.value = false
                fetchDirectoryEntries(targetDirUri)
            }
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
                    val newFile = targetParentDoc.createFile(sourceDoc.type ?: MimeTypeMapHelper.getMimeTypeFromExtension(sourceDoc.name ?: ""), sourceDoc.name ?: "File_${System.currentTimeMillis()}") ?: return@withContext false
                    context.contentResolver.openInputStream(sourceDoc.uri)?.use { input -> context.contentResolver.openOutputStream(newFile.uri)?.use { output -> input.copyTo(output) } ?: return@withContext false } ?: return@withContext false
                    true
                }
            } catch (e: IOException) { Log.e(TAG, "Error copying ${sourceDoc.uri} to ${targetParentDoc.uri}", e); false }
        }
    }

    fun initiateSelectExternalItemsToMove() { _launchSystemFilePickerForMoveEvent.tryEmit(Unit) }

    fun moveUrisToCurrentDirectory(sourceUris: List<Uri>) {
        val targetDirUri = _currentFolderUri.value ?: _rootTreeUri.value
        if (targetDirUri == null) { _toastMessageEvent.tryEmit(context.getString(R.string.lfm_move_items_here_error_toast, "No destination directory")); return }
        if (sourceUris.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            var successCount = 0; var errorOccurred = false
            val targetDirDoc = DocumentFile.fromTreeUri(context, targetDirUri) ?: run { _toastMessageEvent.tryEmit(context.getString(R.string.lfm_move_items_here_error_toast, "Invalid destination directory")); return@launch }

            for (sourceUri in sourceUris) {
                // Use fromSingleUri for URIs obtained from OpenMultipleDocuments
                val sourceDoc = DocumentFile.fromSingleUri(context, sourceUri)
                if (sourceDoc == null) {
                    Log.w(TAG, "Skipping move: Could not access sourceDoc for $sourceUri (using fromSingleUri)")
                    errorOccurred = true; continue
                }

                if (sourceUri == targetDirUri || (sourceDoc.isDirectory && isUriDescendant(targetDirUri, sourceUri))) {
                    Log.w(TAG, "Cannot move folder into itself or its child for $sourceUri")
                    errorOccurred = true; continue
                }
                val sourceName = sourceDoc.name
                if (sourceName != null && sourceName.startsWith(USER_DATA_ROOT_PREFIX)) {
                    val currentUid = currentUserId.value
                    if (currentUid == null || sourceName != "$USER_DATA_ROOT_PREFIX$currentUid") {
                        Log.w(TAG, "Skipping move of other user's secured folder: $sourceName")
                        errorOccurred = true; continue
                    }
                }

                try {
                    if (copyDocument(sourceDoc, targetDirDoc)) { if (sourceDoc.delete()) successCount++ else { Log.w(TAG, "Copied ${sourceDoc.name} but failed to delete original."); errorOccurred = true } }
                    else { Log.e(TAG, "Failed to copy ${sourceDoc.name} for move operation."); errorOccurred = true }
                } catch (e: Exception) { Log.e(TAG, "Error moving ${sourceDoc.name}: ${e.message}", e); errorOccurred = true }
            }
            if (successCount > 0) _toastMessageEvent.tryEmit(context.getString(R.string.lfm_move_items_here_success_toast, successCount))
            if (errorOccurred && successCount > 0 && successCount < sourceUris.size) { // Show partial failure only if some succeeded
                _toastMessageEvent.tryEmit(context.getString(R.string.lfm_move_items_here_error_toast, "Some items failed to move"))
            } else if (errorOccurred && successCount == 0) {
                _toastMessageEvent.tryEmit(context.getString(R.string.lfm_move_items_here_error_toast, "All items failed to move"))
            }
            fetchDirectoryEntries(targetDirUri)
        }
    }


    fun requestArchiveSelectedItems() {
        if (_selectedItems.value.isNotEmpty()) {
            _showArchiveNameDialog.value = _selectedItems.value.toList()
        }
    }
    fun dismissArchiveNameDialog() { _showArchiveNameDialog.value = null }

    fun confirmArchiveCreation(archiveNameInput: String) {
        val itemsToArchive = _showArchiveNameDialog.value ?: return
        val parentDirUri = _currentFolderUri.value ?: _rootTreeUri.value ?: return
        dismissArchiveNameDialog()

        var archiveName = archiveNameInput.trim()
        if (!archiveName.endsWith(".zip", ignoreCase = true)) {
            archiveName += ".zip"
        }
        if (archiveName == ".zip" || archiveName.any { it in "/\\?%*:|\"<>" }) {
            _toastMessageEvent.tryEmit(context.getString(R.string.lfm_invalid_archive_name))
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val parentDocFile = DocumentFile.fromTreeUri(context, parentDirUri)
                if (parentDocFile == null || !parentDocFile.canWrite()) {
                    _toastMessageEvent.tryEmit(context.getString(R.string.lfm_archive_error_toast, "Cannot write to target directory")); return@launch
                }
                if (parentDocFile.findFile(archiveName) != null) {
                    _toastMessageEvent.tryEmit(context.getString(R.string.lfm_archive_error_toast, "File '$archiveName' already exists")); return@launch
                }

                val archiveDocFile = parentDocFile.createFile("application/zip", archiveName)
                if (archiveDocFile == null) {
                    _toastMessageEvent.tryEmit(context.getString(R.string.lfm_archive_error_toast, "Could not create archive file")); return@launch
                }

                context.contentResolver.openOutputStream(archiveDocFile.uri)?.use { fos ->
                    ZipOutputStream(BufferedOutputStream(fos)).use { zos ->
                        itemsToArchive.forEach { entry ->
                            val docFile = DocumentFile.fromTreeUri(context, entry.uri) // Actual URI
                            docFile?.let {
                                val actualNameForZip = if (entry is DirectoryEntry.FolderEntry && entry.isSecuredUserDataRoot) {
                                    "$USER_DATA_ROOT_PREFIX${currentUserId.value}"
                                } else {
                                    entry.name
                                }
                                addDocFileToZip(it, actualNameForZip, zos, "")
                            }
                        }
                    }
                } ?: throw IOException("Could not open output stream for archive file.")
                _toastMessageEvent.tryEmit(context.getString(R.string.lfm_archive_success_toast, archiveName))
                fetchDirectoryEntries(parentDirUri)
            } catch (e: Exception) {
                Log.e(TAG, "Error creating archive", e)
                _toastMessageEvent.tryEmit(context.getString(R.string.lfm_archive_error_toast, e.localizedMessage ?: "Unknown error"))
            } finally {
                exitSelectionMode()
            }
        }
    }


    private fun addDocFileToZip(docFile: DocumentFile, entryName: String, zos: ZipOutputStream, basePathInZip: String) {
        val fullPathInZip = if (basePathInZip.isEmpty()) entryName else "$basePathInZip/$entryName"
        Log.d(TAG, "Adding to ZIP: ${docFile.uri}, as: $fullPathInZip, isDir: ${docFile.isDirectory}")

        if (docFile.isDirectory) {
            val dirEntryPath = if (fullPathInZip.endsWith("/")) fullPathInZip else "$fullPathInZip/"
            zos.putNextEntry(ZipEntry(dirEntryPath))
            zos.closeEntry()
            docFile.listFiles().forEach { child ->
                child.name?.let { childName ->
                    addDocFileToZip(child, childName, zos, dirEntryPath.removeSuffix("/"))
                }
            }
        } else {
            context.contentResolver.openInputStream(docFile.uri)?.use { fis ->
                BufferedInputStream(fis).use { bis ->
                    val entry = ZipEntry(fullPathInZip)
                    zos.putNextEntry(entry)
                    bis.copyTo(zos)
                    zos.closeEntry()
                }
            } ?: Log.w(TAG, "Could not open input stream for ${docFile.uri}")
        }
    }

    fun requestExtractArchive(fileEntry: DirectoryEntry.FileEntry) {
        if (fileEntry.name.endsWith(".zip", ignoreCase = true) || fileEntry.mimeType == "application/zip") {
            _showExtractOptionsDialog.value = fileEntry
        } else {
            _toastMessageEvent.tryEmit("Not a valid ZIP file.")
        }
    }
    fun dismissExtractOptionsDialog() { _showExtractOptionsDialog.value = null }

    fun extractArchiveToCurrentFolder() {
        val zipFileEntry = _showExtractOptionsDialog.value ?: return
        val targetDirUri = _currentFolderUri.value ?: _rootTreeUri.value ?: return
        dismissExtractOptionsDialog() // Dismiss before starting long operation
        performExtraction(zipFileEntry, targetDirUri)
    }

    fun initiateExtractArchiveToAnotherFolder() {
        val zipFileEntry = _showExtractOptionsDialog.value ?: return
        dismissExtractOptionsDialog() // Dismiss before launching picker
        _launchDirectoryPickerForExtractionEvent.tryEmit(zipFileEntry)
    }

    fun extractArchiveToSelectedDirectory(zipFileEntry: DirectoryEntry.FileEntry, targetParentDirUri: Uri) {
        performExtraction(zipFileEntry, targetParentDirUri)
    }

    private fun performExtraction(zipFileEntry: DirectoryEntry.FileEntry, targetParentDirUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val targetParentDocFile = DocumentFile.fromTreeUri(context, targetParentDirUri)
                if (targetParentDocFile == null || !targetParentDocFile.isDirectory || !targetParentDocFile.canWrite()) {
                    _toastMessageEvent.tryEmit(context.getString(R.string.lfm_extract_error_toast, "Invalid or non-writable target directory")); return@launch
                }

                val currentUid = currentUserId.value
                val targetPathString = targetParentDirUri.toString()
                if (targetPathString.contains(USER_DATA_ROOT_PREFIX)) {
                    if (currentUid == null || !targetPathString.contains("$USER_DATA_ROOT_PREFIX$currentUid")) {
                        _toastMessageEvent.tryEmit(context.getString(R.string.lfm_extract_error_toast, "Cannot extract into this secured location.")); return@launch
                    }
                }

                context.contentResolver.openInputStream(zipFileEntry.uri)?.use { fis ->
                    ZipInputStream(BufferedInputStream(fis)).use { zis ->
                        var currentZipEntry: ZipEntry? = zis.nextEntry
                        while (currentZipEntry != null) {
                            val entryName = currentZipEntry.name.trimEnd('/')
                            Log.d(TAG, "Extracting entry: $entryName, isDir: ${currentZipEntry.isDirectory}")

                            if (entryName.startsWith("/") || entryName.contains("..") || entryName.isBlank()) {
                                Log.w(TAG, "Skipping potentially malicious or empty zip entry: '$entryName'")
                                currentZipEntry = zis.nextEntry
                                continue
                            }

                            val pathParts = entryName.split('/').filter { it.isNotEmpty() }
                            var currentTargetDirDoc = targetParentDocFile

                            for (i in 0 until pathParts.size - 1) {
                                val dirName = pathParts[i]
                                var nextDirDoc = currentTargetDirDoc?.findFile(dirName)
                                if (nextDirDoc == null) {
                                    nextDirDoc = currentTargetDirDoc?.createDirectory(dirName)
                                    Log.d(TAG, "Created directory for extraction: ${nextDirDoc?.uri}")
                                } else if (!nextDirDoc.isDirectory) {
                                    throw IOException("Cannot create directory, a file with the same name exists: $dirName")
                                }
                                currentTargetDirDoc = nextDirDoc ?: throw IOException("Failed to create/find directory: $dirName")
                            }

                            val finalEntryName = pathParts.last()
                            if (currentZipEntry.isDirectory) {
                                if (currentTargetDirDoc.findFile(finalEntryName) == null) {
                                    currentTargetDirDoc.createDirectory(finalEntryName)
                                    Log.d(TAG, "Created directory (from entry.isDirectory): $finalEntryName in ${currentTargetDirDoc.uri}")
                                }
                            } else {
                                val existingFile = currentTargetDirDoc.findFile(finalEntryName)
                                if (existingFile != null && existingFile.isFile) {
                                    Log.w(TAG, "File $finalEntryName already exists in ${currentTargetDirDoc.uri}, overwriting.")
                                    existingFile.delete() // Delete to overwrite
                                }
                                val newFileDoc = currentTargetDirDoc.createFile(
                                    MimeTypeMapHelper.getMimeTypeFromExtension(finalEntryName),
                                    finalEntryName
                                )
                                if (newFileDoc == null) {
                                    throw IOException("Could not create file: $finalEntryName in ${currentTargetDirDoc.uri}")
                                }
                                context.contentResolver.openOutputStream(newFileDoc.uri)?.use { fos ->
                                    zis.copyTo(fos)
                                } ?: throw IOException("Could not open output stream for ${newFileDoc.uri}")
                                Log.d(TAG, "Extracted file: ${newFileDoc.uri}")
                            }
                            zis.closeEntry()
                            currentZipEntry = zis.nextEntry
                        }
                    }
                } ?: Log.e(TAG, "Could not open input stream for zip file: ${zipFileEntry.uri}")

                _toastMessageEvent.tryEmit(context.getString(R.string.lfm_extract_success_toast, zipFileEntry.name))
                fetchDirectoryEntries(targetParentDirUri) // Refresh the directory where extraction happened
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting archive '${zipFileEntry.name}' to '$targetParentDirUri'", e)
                _toastMessageEvent.tryEmit(context.getString(R.string.lfm_extract_error_toast, e.localizedMessage ?: "Unknown error"))
            } finally {
                exitSelectionMode() // Ensure selection mode is exited
            }
        }
    }
    fun requestBatchRename() {
        val filesToRename = _selectedItems.value.filterIsInstance<DirectoryEntry.FileEntry>()
        if (filesToRename.isEmpty()) {
            _toastMessageEvent.tryEmit(context.getString(R.string.lfm_batch_rename_no_files_selected))
            return
        }
        _batchRenameDialogState.value = BatchRenameDialogState(itemsToRenameCount = filesToRename.size)
    }

    fun dismissBatchRenameDialog() {
        _batchRenameDialogState.value = null
    }

    fun performBatchRename(options: PerformBatchRenameOptions) {
        val itemsToRename = _selectedItems.value.filterIsInstance<DirectoryEntry.FileEntry>()
            .sortedBy { it.name } // Sort for consistent numbering

        if (itemsToRename.isEmpty()) {
            _toastMessageEvent.tryEmit(context.getString(R.string.lfm_batch_rename_no_files_selected))
            dismissBatchRenameDialog()
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            var successCount = 0
            var failureCount = 0
            val parentUri = _currentFolderUri.value ?: _rootTreeUri.value ?: run {
                _toastMessageEvent.tryEmit(context.getString(R.string.lfm_batch_rename_error_no_parent_dir))
                return@launch
            }
            val parentDocFile = DocumentFile.fromTreeUri(context, parentUri) ?: run {
                _toastMessageEvent.tryEmit(context.getString(R.string.lfm_batch_rename_error_parent_inaccessible))
                return@launch
            }

            itemsToRename.forEachIndexed { index, fileEntry ->
                val originalDocFile = DocumentFile.fromTreeUri(context, fileEntry.uri)
                if (originalDocFile == null || !originalDocFile.isFile) {
                    Log.w(TAG, "Batch rename: Skipping non-existent or non-file entry ${fileEntry.name}")
                    failureCount++
                    return@forEachIndexed
                }

                val sequenceNumber = options.startNumber + index
                val numberString = if (options.numDigits > 0) {
                    sequenceNumber.toString().padStart(options.numDigits, '0')
                } else {
                    sequenceNumber.toString()
                }

                val originalNameWithoutExtension = fileEntry.name.substringBeforeLast('.', fileEntry.name)
                val originalExtension = if (fileEntry.name.contains('.')) "." + fileEntry.name.substringAfterLast('.') else ""

                val newNameWithoutExt = if (options.baseName.contains("{#}") || options.baseName.contains("{orig}")) {
                    options.baseName.replace("{#}", numberString)
                        .replace("{orig}", originalNameWithoutExtension)
                } else {
                    options.baseName + numberString
                }


                val finalNewName = if (options.keepOriginalExtension) {
                    newNameWithoutExt + originalExtension
                } else {
                    newNameWithoutExt // No extension or user needs to add it in baseName
                }

                if (finalNewName == fileEntry.name) { // No actual change
                    Log.i(TAG, "Batch rename: Skipping ${fileEntry.name} as new name is identical.")
                    // Consider it a success if no change needed, or a skip. For now, let's count as success.
                    successCount++
                    return@forEachIndexed
                }

                if (parentDocFile.findFile(finalNewName) != null) {
                    Log.w(TAG, "Batch rename: File with new name '$finalNewName' already exists. Skipping ${fileEntry.name}.")
                    _toastMessageEvent.tryEmit(context.getString(R.string.lfm_batch_rename_error_name_exists, finalNewName, fileEntry.name))
                    failureCount++
                } else {
                    try {
                        if (originalDocFile.renameTo(finalNewName)) {
                            Log.i(TAG, "Batch rename: Renamed ${fileEntry.name} to $finalNewName")
                            successCount++
                        } else {
                            Log.e(TAG, "Batch rename: Failed to rename ${fileEntry.name} to $finalNewName (renameTo returned false)")
                            _toastMessageEvent.tryEmit(context.getString(R.string.lfm_batch_rename_error_single, fileEntry.name))
                            failureCount++
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Batch rename: Error renaming ${fileEntry.name} to $finalNewName", e)
                        _toastMessageEvent.tryEmit(context.getString(R.string.lfm_batch_rename_error_single_exception, fileEntry.name, e.localizedMessage))
                        failureCount++
                    }
                }
            }

            val message: String = when {
                successCount > 0 && failureCount == 0 -> context.getString(R.string.lfm_batch_rename_success_all, successCount)
                successCount > 0 && failureCount > 0 -> context.getString(R.string.lfm_batch_rename_success_partial, successCount, failureCount)
                successCount == 0 && failureCount > 0 -> context.getString(R.string.lfm_batch_rename_failure_all, failureCount)
                else -> context.getString(R.string.lfm_batch_rename_no_files_processed) // Should not happen if initial check passes
            }
            _toastMessageEvent.tryEmit(message)

            fetchDirectoryEntries(parentUri)
            withContext(Dispatchers.Main) {
                exitSelectionMode()
                dismissBatchRenameDialog()
                    }
                }
            }

        }
