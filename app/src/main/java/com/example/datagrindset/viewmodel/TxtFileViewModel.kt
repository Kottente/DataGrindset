package com.example.datagrindset.viewmodel

import android.app.Application
import android.content.ContentValues.TAG
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import android.provider.OpenableColumns // For getting display name
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.OutputStreamWriter
import android.content.Intent // Needed for FLAG_GRANT_WRITE_URI_PERMISSION
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow


class TxtFileViewModel(application: Application, private var currentFileUri: Uri) : AndroidViewModel(application) {

    private val _fileName = MutableStateFlow<String>("Loading...")
    val fileName: StateFlow<String> = _fileName.asStateFlow()

    private val _fileContent = MutableStateFlow<String?>(null)
    val fileContent: StateFlow<String?> = _fileContent.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<IntRange>>(emptyList())
    val searchResults: StateFlow<List<IntRange>> = _searchResults.asStateFlow()

    private val _currentResultIndex = MutableStateFlow(-1) // -1 means no active highlight or no results
    val currentResultIndex: StateFlow<Int> = _currentResultIndex.asStateFlow()

    private val _isEditMode = MutableStateFlow(false)
    val isEditMode: StateFlow<Boolean> = _isEditMode.asStateFlow()

    private val _editableContent = MutableStateFlow("")
    val editableContent: StateFlow<String> = _editableContent.asStateFlow()

    private val _originalContentBeforeEdit = MutableStateFlow<String?>(null) // To compare for unsaved changes

    val hasUnsavedChanges: StateFlow<Boolean> = combine(
        _isEditMode, _editableContent, _originalContentBeforeEdit
    ) { isEditing, currentEditable, originalContent ->
        isEditing && currentEditable != originalContent
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    // --- Discard Confirmation Dialog State ---
    private val _showDiscardConfirmDialog = MutableStateFlow(false)
    val showDiscardConfirmDialog: StateFlow<Boolean> = _showDiscardConfirmDialog.asStateFlow()

    // To communicate save status/errors specifically for save operation
    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError.asStateFlow()
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()
    // --- "Save As" Event Channel ---
    // We'll use a Channel for one-time events like triggering the file chooser
    private val _initiateSaveAsChannel = Channel<String?>() // String for suggested file name
    val initiateSaveAsEvent: Flow<String?> = _initiateSaveAsChannel.receiveAsFlow()

    val totalResults: StateFlow<Int> = _searchResults.combine(_searchQuery) { results, query ->
        if (query.isBlank()) 0 else results.size
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000), // Keep active for 5s after last observer gone
        initialValue = 0
    )// Corrected combine usage

    private var searchJob: Job? = null
    // More states for text analysis features will be added here later
    // e.g., fontSize, editMode, searchText, summary, etc.
    companion object {
        private const val TAG = "TxtFileViewModel"
    }

    init {
        Log.d(TAG, "Initializing for URI: $currentFileUri")
        // Check persisted permissions (useful for debugging)
        val context = getApplication<Application>()
        Log.d(TAG, "Current persisted URI permissions:")
        context.contentResolver.persistedUriPermissions.forEach {
            Log.d(TAG, " - URI: ${it.uri}, Read: ${it.isReadPermission}, Write: ${it.isWritePermission}")
            // Check if the currentFileUri's parent tree is among these
            if (currentFileUri.toString().startsWith(it.uri.toString()) && it.isReadPermission) {
                Log.i(TAG, "File URI $currentFileUri appears to be a child of a permitted tree: ${it.uri}")
            }
        }
        loadFileNameDirectly()
        loadFileContentDirectly()
        //loadFileName()
        //loadFileContent()
    }
    private fun loadFileNameAndContent(uri: Uri, isNewFileAfterSaveAs: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val context = getApplication<Application>()
                // Load File Name
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val displayNameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (displayNameIndex != -1) {
                            _fileName.value = it.getString(displayNameIndex)
                        } else {
                            _fileName.value = uri.lastPathSegment ?: "Unknown File"
                        }
                    } else {
                        _fileName.value = uri.lastPathSegment ?: "Unknown File"
                    }
                } ?: run { _fileName.value = uri.lastPathSegment ?: "Unknown File" }
                Log.d(TAG, "File name set to: ${_fileName.value}")

                // Load File Content (only if not a new file from Save As, where content is already known)
                if (!isNewFileAfterSaveAs) {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        BufferedReader(InputStreamReader(inputStream)).use { reader ->
                            val loadedText = reader.readText()
                            _fileContent.value = loadedText
                            _originalContentBeforeEdit.value = loadedText
                            _editableContent.value = loadedText // Ensure editable content is also updated
                            Log.i(TAG, "File content loaded for $uri")
                        }
                    } ?: run {
                        _error.value = "Failed to open input stream for $uri"
                        _fileContent.value = null // Clear content if loading fails
                        _originalContentBeforeEdit.value = null
                        _editableContent.value = ""
                    }
                } else {
                    // For Save As, editableContent is the source of truth
                    _fileContent.value = _editableContent.value
                    _originalContentBeforeEdit.value = _editableContent.value
                }

            } catch (e: SecurityException) {
                _error.value = "Permission denied loading $uri"
                Log.e(TAG, "SecurityException loading $uri", e)
            } catch (e: Exception) {
                _error.value = "Error loading $uri: ${e.localizedMessage}"
                Log.e(TAG, "Exception loading $uri", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
    private fun loadFileName() {
        viewModelScope.launch {
            try {
                val documentFile = DocumentFile.fromSingleUri(getApplication(), currentFileUri)
                _fileName.value = documentFile?.name ?: "Unknown File"
                Log.d(TAG, "File name loaded: ${_fileName.value}")
            } catch (e: Exception) {
                _fileName.value = "Error getting name"
                Log.e(TAG, "Error loading file name for $currentFileUri", e)
            }
        }
    }

    private fun loadFileNameDirectly() {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val cursor = context.contentResolver.query(currentFileUri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val displayNameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (displayNameIndex != -1) {
                            _fileName.value = it.getString(displayNameIndex)
                            Log.d(TAG, "File name loaded directly: ${_fileName.value}")
                            return@launch
                        }
                    }
                }
                // Fallback if query fails or no display name
                _fileName.value = currentFileUri.lastPathSegment ?: "Unknown File"
                Log.w(TAG, "Could not get display name from ContentResolver, used lastPathSegment: ${_fileName.value}")

            } catch (e: SecurityException) {
                _fileName.value = "Error (Perm)" // Short indicator for UI
                Log.e(TAG, "SecurityException getting file name for $currentFileUri", e)
                // Attempt to load content anyway, as sometimes metadata query fails but open works
            } catch (e: Exception) {
                _fileName.value = "Error (Name)"
                Log.e(TAG, "Error loading file name for $currentFileUri", e)
            }
        }
    }

    private fun loadFileContentDirectly() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            Log.d(TAG, "Attempting to load content directly for URI: $currentFileUri")

            try {
                val context = getApplication<Application>()
                // Directly try to open the input stream.
                // This is the most direct way and often works if tree permissions are active.
                context.contentResolver.openInputStream(currentFileUri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        val loadedText = reader.readText()
                        _fileContent.value = loadedText // Update main content
                        _originalContentBeforeEdit.value = loadedText // Also set original for comparison
                        Log.i(TAG, "File content loaded successfully for URI: $currentFileUri")
                    }
                } ?: run {
                    _error.value = "Failed to open file stream (ContentResolver returned null)."
                    Log.w(TAG, "contentResolver.openInputStream returned null for URI: $currentFileUri")
                }

            } catch (e: SecurityException) {
                _fileContent.value = null
                _error.value = "Permission Denied. Ensure app has access to the folder."
                Log.e(TAG, "SecurityException opening file stream for $currentFileUri. This indicates the persisted permission for the root tree is not being applied, or the URI is problematic.", e)
            } catch (e: Exception) {
                _fileContent.value = null
                _error.value = "Error loading file: ${e.localizedMessage}"
                Log.e(TAG, "Generic exception opening file stream for $currentFileUri", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadFileContent() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            Log.d(TAG, "Attempting to load content for URI: $currentFileUri")

            try {
                val context = getApplication<Application>()

                // Log currently persisted URI permissions for debugging
                Log.d(TAG, "Current persisted URI permissions:")
                val persistedUris = context.contentResolver.persistedUriPermissions
                if (persistedUris.isEmpty()) {
                    Log.w(TAG, "No persisted URI permissions found.")
                }
                persistedUris.forEach {
                    Log.d(TAG, " - URI: ${it.uri}, Read: ${it.isReadPermission}, Write: ${it.isWritePermission}, Persisted Time: ${it.persistedTime}")
                }

                // Try to create a DocumentFile from the URI to check its properties
                val documentFile = DocumentFile.fromSingleUri(context, currentFileUri)

                if (documentFile == null) {
                    _error.value = "File URI is invalid or DocumentFile cannot be created."
                    _fileContent.value = null
                    Log.w(TAG, "DocumentFile.fromSingleUri returned null for URI: $currentFileUri")
                    _isLoading.value = false
                    return@launch
                }

                if (!documentFile.exists()) {
                    _error.value = "File does not exist at URI: $currentFileUri"
                    _fileContent.value = null
                    Log.w(TAG, "documentFile.exists() is false for URI: $currentFileUri")
                    _isLoading.value = false
                    return@launch
                }

                Log.d(TAG, "DocumentFile details: Name: ${documentFile.name}, CanRead: ${documentFile.canRead()}, CanWrite: ${documentFile.canWrite()}, Type: ${documentFile.type}")

                if (!documentFile.canRead()) {
                    _error.value = "Cannot read file (documentFile.canRead() is false). This indicates a permission issue with the URI itself or its tree."
                    _fileContent.value = null
                    Log.w(TAG, "documentFile.canRead() is false for URI: $currentFileUri. This is the primary indicator of the permission problem.")
                    _isLoading.value = false
                    return@launch
                }

                // If documentFile.canRead() is true, then openInputStream should work.
                // The SecurityException happens here if the ContentResolver doesn't honor the permission.
                context.contentResolver.openInputStream(documentFile.uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        _fileContent.value = reader.readText()
                        Log.d(TAG, "File content loaded successfully for URI: $currentFileUri")
                    }
                } ?: run {
                    _error.value = "Failed to open file stream (contentResolver.openInputStream returned null)."
                    Log.w(TAG, "contentResolver.openInputStream returned null for URI: ${documentFile.uri}")
                }

            } catch (e: SecurityException) {
                _fileContent.value = null
                // This is the error you are seeing.
                _error.value = "SecurityException: ${e.localizedMessage}. Check logs for persisted permissions."
                Log.e(TAG, "SecurityException opening file: $currentFileUri. This usually means the persisted permission for the root tree is not being applied to this child URI.", e)
            } catch (e: Exception) {
                _fileContent.value = null
                _error.value = "Error loading file: ${e.localizedMessage}"
                Log.e(TAG, "Generic exception opening file: $currentFileUri", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        searchJob?.cancel() // Cancel previous search if any
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _currentResultIndex.value = -1
            return
        }
        // Debounce search slightly to avoid searching on every keystroke instantly
        searchJob = viewModelScope.launch {
            delay(300) // Debounce for 300ms
            performSearch(query)
        }
    }

    private fun performSearch(query: String) {
        val content = _fileContent.value
        if (content == null || query.isBlank()) {
            _searchResults.value = emptyList()
            _currentResultIndex.value = -1
            return
        }

        val results = mutableListOf<IntRange>()
        var lastIndex = 0
        while (lastIndex < content.length) {
            val foundIndex = content.indexOf(query, lastIndex, ignoreCase = true) // ignoreCase = true for user-friendly search
            if (foundIndex == -1) break
            results.add(IntRange(foundIndex, foundIndex + query.length - 1))
            lastIndex = foundIndex + query.length
        }
        _searchResults.value = results
        _currentResultIndex.value = if (results.isNotEmpty()) 0 else -1 // Highlight first result or none
        Log.d(TAG, "Search for '$query' found ${results.size} matches.")
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _currentResultIndex.value = -1
        searchJob?.cancel()
    }

    fun goToNextMatch() {
        if (_searchResults.value.isEmpty()) return
        val nextIndex = (_currentResultIndex.value + 1) % _searchResults.value.size
        _currentResultIndex.value = nextIndex
    }

    fun goToPreviousMatch() {
        if (_searchResults.value.isEmpty()) return
        val prevIndex = if (_currentResultIndex.value - 1 < 0) {
            _searchResults.value.size - 1
        } else {
            _currentResultIndex.value - 1
        }
        _currentResultIndex.value = prevIndex
    }

    fun enterEditMode() {
        if (!_isEditMode.value) {
            val currentSavedContent = _fileContent.value ?: ""
            _originalContentBeforeEdit.value = currentSavedContent // Store content at the start of edit
            _editableContent.value = currentSavedContent
            _isEditMode.value = true
            _saveError.value = null
            clearSearch()
        }
    }

    fun exitEditMode() { // This will be called after confirmation or if no unsaved changes
        _isEditMode.value = false
        _showDiscardConfirmDialog.value = false
        // Reset editable content to last saved state to avoid showing stale data if re-entering edit mode
        _editableContent.value = _fileContent.value ?: ""
    }

    fun attemptExitEditMode() { // Called by UI (back button, close icon)
        if (hasUnsavedChanges.value) {
            _showDiscardConfirmDialog.value = true
        } else {
            exitEditMode() // No unsaved changes, exit directly
        }
    }

    fun onEditableContentChanged(newText: String) {
        if (_isEditMode.value) {
            _editableContent.value = newText
        }
    }

    fun saveChanges(andThen: (() -> Unit)? = null) { // Optional callback for after save
        if (!_isEditMode.value) return
        _isSaving.value = true
        _saveError.value = null
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                context.contentResolver.openOutputStream(currentFileUri, "wt")?.use { outputStream ->
                    OutputStreamWriter(outputStream).use { writer ->
                        writer.write(_editableContent.value)
                    }
                    _fileContent.value = _editableContent.value // Update main content
                    _originalContentBeforeEdit.value = _editableContent.value // Update original to current saved
                    Log.i(TAG, "File saved successfully: $currentFileUri")
                    andThen?.invoke() // Execute callback (e.g., exitEditMode)
                } ?: run {
                    _saveError.value = "Failed to open output stream for writing."
                }
            } catch (e: SecurityException) {
                _saveError.value = "Permission denied. Cannot save file."
                Log.e(TAG, "SecurityException saving file $currentFileUri: ${e.message}", e)
            } catch (e: Exception) {
                _saveError.value = "Error saving file: ${e.localizedMessage}"
                Log.e(TAG, "Exception saving file $currentFileUri: ${e.message}", e)
            } finally {
                _isSaving.value = false
            }
        }
    }

    // --- Discard Confirmation Dialog Actions ---
    fun confirmDiscardChanges() {
        exitEditMode() // Exits edit mode and closes dialog
    }

    fun saveAndExitEditMode() {
        saveChanges { // Pass a lambda to execute after successful save
            exitEditMode()
        }
        // Dialog will be closed by exitEditMode if save is successful
        // If save fails, dialog remains, error shown via snackbar
    }

    fun cancelDiscardDialog() {
        _showDiscardConfirmDialog.value = false
    }

    fun clearSaveError() { // For UI to call after snackbar shown
        _saveError.value = null
    }
    fun initiateSaveAs() {
        if (!_isEditMode.value && _fileContent.value.isNullOrEmpty()) {
            // Or if editableContent is empty and not in edit mode
            _saveError.value = "Nothing to save." // Or handle as you see fit
            return
        }
        viewModelScope.launch {
            // Suggest current file name, or a default if none
            val suggestedName = if (_fileName.value != "Loading..." && _fileName.value != "Unknown File") {
                _fileName.value
            } else {
                "Untitled.txt"
            }
            _initiateSaveAsChannel.send(suggestedName)
        }
    }

    fun onSaveAsUriReceived(newUri: Uri?) {
        if (newUri == null) {
            _saveError.value = "Save As was cancelled."
            return
        }

        _isSaving.value = true
        _saveError.value = null
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                // Write the current editableContent to the new URI
                context.contentResolver.openOutputStream(newUri, "wt")?.use { outputStream ->
                    OutputStreamWriter(outputStream).use { writer ->
                        writer.write(_editableContent.value) // Use _editableContent as source
                    }

                    // Persist permission for the new URI
                    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(newUri, takeFlags)
                    Log.i(TAG, "Persisted permission for new URI: $newUri")

                    // Update internal state to point to the new file
                    currentFileUri = newUri
                    // Reload file name and content (content will be from _editableContent)
                    loadFileNameAndContent(newUri, isNewFileAfterSaveAs = true)

                    // If in edit mode, we've essentially saved, so exit edit mode.
                    if (_isEditMode.value) {
                        exitEditMode()
                    }
                    Log.i(TAG, "'Save As' successful to: $newUri")

                } ?: run {
                    _saveError.value = "Failed to open output stream for 'Save As'."
                }
            } catch (e: SecurityException) {
                _saveError.value = "Permission denied during 'Save As'."
                Log.e(TAG, "SecurityException during 'Save As' to $newUri", e)
            } catch (e: Exception) {
                _saveError.value = "Error during 'Save As': ${e.localizedMessage}"
                Log.e(TAG, "Exception during 'Save As' to $newUri", e)
            } finally {
                _isSaving.value = false
            }
        }
    }

    // Placeholder for future actions
    fun generateSummary() { /* TODO */ }
    fun increaseFontSize() { /* TODO */ }
    fun decreaseFontSize() { /* TODO */ }
    fun searchInText(query: String) { /* TODO */ }
    fun applyEdits(newContent: String) { /* TODO - this will also need write permissions */ }

}

// Factory for TxtFileViewModel
class TxtFileViewModelFactory(
    private val application: Application,
    private val currentFileUri: Uri
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TxtFileViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TxtFileViewModel(application, currentFileUri) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}