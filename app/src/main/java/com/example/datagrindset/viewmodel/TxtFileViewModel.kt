package com.example.datagrindset.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class TxtFileViewModel(application: Application, private var currentFileUri: Uri) : AndroidViewModel(application) {

    private val _fileName = MutableStateFlow("Loading...")
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

    private val _currentResultIndex = MutableStateFlow(-1)
    val currentResultIndex: StateFlow<Int> = _currentResultIndex.asStateFlow()

    val totalResults: StateFlow<Int> = _searchResults.combine(_searchQuery) { results, query ->
        if (query.isBlank()) 0 else results.size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private var searchJob: Job? = null

    private val _replaceQuery = MutableStateFlow("")
    val replaceQuery: StateFlow<String> = _replaceQuery.asStateFlow()

    private val _isEditMode = MutableStateFlow(false)
    val isEditMode: StateFlow<Boolean> = _isEditMode.asStateFlow()

    private val _editableContent = MutableStateFlow("")
    val editableContent: StateFlow<String> = _editableContent.asStateFlow()

    private val _originalContentBeforeEdit = MutableStateFlow<String?>(null)

    val hasUnsavedChanges: StateFlow<Boolean> = combine(
        _isEditMode, _editableContent, _originalContentBeforeEdit
    ) { isEditing, currentEditable, originalContent ->
        isEditing && currentEditable != originalContent
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _showDiscardConfirmDialog = MutableStateFlow(false)
    val showDiscardConfirmDialog: StateFlow<Boolean> = _showDiscardConfirmDialog.asStateFlow()

    private val _initiateSaveAsChannel = Channel<String?>()
    val initiateSaveAsEvent: Flow<String?> = _initiateSaveAsChannel.receiveAsFlow()

    companion object {
        private const val TAG = "TxtFileViewModel"
    }

    init {
        Log.d(TAG, "Initializing for URI: $currentFileUri")
        loadFileNameAndContent(currentFileUri)
    }

    private fun loadFileNameAndContent(uri: Uri, isNewFileAfterSaveAs: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val context = getApplication<Application>()
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val displayNameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        _fileName.value = if (displayNameIndex != -1) it.getString(displayNameIndex) else uri.lastPathSegment ?: "Unknown File"
                    } else {
                        _fileName.value = uri.lastPathSegment ?: "Unknown File"
                    }
                } ?: run { _fileName.value = uri.lastPathSegment ?: "Unknown File" }
                Log.d(TAG, "File name set to: ${_fileName.value}")

                if (!isNewFileAfterSaveAs) {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        BufferedReader(InputStreamReader(inputStream)).use { reader ->
                            val loadedText = reader.readText()
                            _fileContent.value = loadedText
                            _originalContentBeforeEdit.value = loadedText
                            _editableContent.value = loadedText
                            Log.i(TAG, "File content loaded for $uri")
                        }
                    } ?: run {
                        _error.value = "Failed to open input stream for $uri"
                        _fileContent.value = null; _originalContentBeforeEdit.value = null; _editableContent.value = ""
                    }
                } else {
                    _fileContent.value = _editableContent.value
                    _originalContentBeforeEdit.value = _editableContent.value
                }
            } catch (e: SecurityException) {
                _error.value = "Permission denied loading $uri"; Log.e(TAG, "SecurityException loading $uri", e)
            } catch (e: Exception) {
                _error.value = "Error loading $uri: ${e.localizedMessage}"; Log.e(TAG, "Exception loading $uri", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _currentResultIndex.value = -1
            return
        }
        searchJob = viewModelScope.launch {
            delay(300) // Debounce
            val contentToSearch = if (_isEditMode.value) _editableContent.value else _fileContent.value
            performSearch(query, contentToSearch)
        }
    }

    private fun performSearch(query: String, textToSearch: String?) {
        if (textToSearch == null || query.isBlank()) {
            _searchResults.value = emptyList()
            _currentResultIndex.value = -1
            return
        }
        val results = mutableListOf<IntRange>()
        var lastIndex = 0
        while (lastIndex < textToSearch.length) {
            val foundIndex = textToSearch.indexOf(query, lastIndex, ignoreCase = true)
            if (foundIndex == -1) break
            results.add(IntRange(foundIndex, foundIndex + query.length - 1))
            lastIndex = foundIndex + query.length
        }
        _searchResults.value = results
        _currentResultIndex.value = if (results.isNotEmpty()) 0 else -1
        Log.d(TAG, "Search for '$query' found ${results.size} matches.")
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _replaceQuery.value = ""
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

    fun onReplaceQueryChanged(query: String) {
        _replaceQuery.value = query
    }

    fun replaceCurrentMatch() {
        if (!_isEditMode.value || _searchResults.value.isEmpty() || _currentResultIndex.value == -1) return
        val currentMatchRange = _searchResults.value[_currentResultIndex.value]
        val currentText = _editableContent.value
        val textBefore = currentText.substring(0, currentMatchRange.first)
        val textAfter = currentText.substring(currentMatchRange.last + 1)
        val newEditableContent = textBefore + _replaceQuery.value + textAfter
        _editableContent.value = newEditableContent
        performSearch(_searchQuery.value, newEditableContent)
    }

    fun replaceAllMatches() {
        if (!_isEditMode.value || _searchResults.value.isEmpty() || _searchQuery.value.isBlank()) return
        val currentText = _editableContent.value
        val query = _searchQuery.value
        val replacement = _replaceQuery.value
        val results = _searchResults.value.sortedBy { it.first }
        val newContentBuilder = StringBuilder()
        var lastProcessedIndex = 0
        results.forEach { matchRange ->
            newContentBuilder.append(currentText.substring(lastProcessedIndex, matchRange.first))
            newContentBuilder.append(replacement)
            lastProcessedIndex = matchRange.last + 1
        }
        if (lastProcessedIndex < currentText.length) {
            newContentBuilder.append(currentText.substring(lastProcessedIndex))
        }
        _editableContent.value = newContentBuilder.toString()
        clearSearch()
    }

    fun enterEditMode() {
        if (!_isEditMode.value) {
            val content = _fileContent.value ?: ""
            _originalContentBeforeEdit.value = content
            _editableContent.value = content
            _isEditMode.value = true
            _saveError.value = null
            clearSearch()
        }
    }

    fun exitEditMode() {
        _isEditMode.value = false
        _showDiscardConfirmDialog.value = false
        _editableContent.value = _fileContent.value ?: "" // Reset to last saved
    }

    fun attemptExitEditMode() {
        if (hasUnsavedChanges.value) {
            _showDiscardConfirmDialog.value = true
        } else {
            exitEditMode()
        }
    }

    fun onEditableContentChanged(newText: String) {
        if (_isEditMode.value) {
            _editableContent.value = newText
        }
    }

    fun saveChanges(andThen: (() -> Unit)? = null) {
        if (!_isEditMode.value) return
        _isSaving.value = true
        _saveError.value = null
        viewModelScope.launch {
            try {
                getApplication<Application>().contentResolver.openOutputStream(currentFileUri, "wt")?.use { outputStream ->
                    OutputStreamWriter(outputStream).use { writer -> writer.write(_editableContent.value) }
                    _fileContent.value = _editableContent.value
                    _originalContentBeforeEdit.value = _editableContent.value
                    Log.i(TAG, "File saved successfully: $currentFileUri")
                    andThen?.invoke()
                } ?: run { _saveError.value = "Failed to open output stream for writing." }
            } catch (e: SecurityException) {
                _saveError.value = "Permission denied. Cannot save file."; Log.e(TAG, "SecEx saving $currentFileUri",e)
            } catch (e: Exception) {
                _saveError.value = "Error saving file: ${e.localizedMessage}"; Log.e(TAG, "Ex saving $currentFileUri",e)
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun initiateSaveAs() {
        if (!_isEditMode.value && _fileContent.value.isNullOrEmpty() && _editableContent.value.isEmpty()) {
            _saveError.value = "Nothing to save."
            return
        }
        viewModelScope.launch {
            val suggestedName = if (_fileName.value != "Loading..." && _fileName.value != "Unknown File") _fileName.value else "Untitled.txt"
            _initiateSaveAsChannel.send(suggestedName)
        }
    }

    fun onSaveAsUriReceived(newUri: Uri?) {
        if (newUri == null) { _saveError.value = "Save As was cancelled."; return }
        _isSaving.value = true; _saveError.value = null
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                context.contentResolver.openOutputStream(newUri, "wt")?.use { outputStream ->
                    OutputStreamWriter(outputStream).use { writer -> writer.write(_editableContent.value) }
                    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(newUri, takeFlags)
                    currentFileUri = newUri
                    loadFileNameAndContent(newUri, isNewFileAfterSaveAs = true)
                    if (_isEditMode.value) exitEditMode()
                    Log.i(TAG, "'Save As' successful to: $newUri")
                } ?: run { _saveError.value = "Failed to open output stream for 'Save As'." }
            } catch (e: SecurityException) {
                _saveError.value = "Permission denied during 'Save As'."; Log.e(TAG, "SecEx SaveAs $newUri",e)
            } catch (e: Exception) {
                _saveError.value = "Error during 'Save As': ${e.localizedMessage}"; Log.e(TAG, "Ex SaveAs $newUri",e)
            } finally { _isSaving.value = false }
        }
    }

    fun confirmDiscardChanges() {
        exitEditMode()
    }

    fun saveAndExitEditMode() {
        saveChanges { exitEditMode() }
    }

    fun cancelDiscardDialog() {
        _showDiscardConfirmDialog.value = false
    }

    fun clearSaveError() {
        _saveError.value = null
    }

    fun generateSummary() { /* TODO: Implement summary generation */ }
}

class TxtFileViewModelFactory(
    private val application: Application,
    private val initialFileUri: Uri
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TxtFileViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TxtFileViewModel(application, initialFileUri) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}