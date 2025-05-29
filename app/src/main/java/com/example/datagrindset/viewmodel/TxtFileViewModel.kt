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
import com.example.datagrindset.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.LinkedList
import java.util.Locale

data class SummaryData(
    val lineCount: Int,
    val wordCount: Int,
    val charCount: Int,
    val charCountWithoutSpaces: Int
)

class TxtFileViewModel(
    application: Application,
    private var currentFileUri: Uri,
    private val initialFileNameHint: String // Used as a fallback or initial display
) : AndroidViewModel(application) {

    private val _fileName = MutableStateFlow(initialFileNameHint)
    val fileName: StateFlow<String> = _fileName.asStateFlow()

    private val _fileContent = MutableStateFlow<String?>(null)
    val fileContent: StateFlow<String?> = _fileContent.asStateFlow()

    private val _isLoading = MutableStateFlow(true) // Start with loading true
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

    private val undoStack = LinkedList<String>()
    private val redoStack = LinkedList<String>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val _summaryResult = MutableStateFlow<SummaryData?>(null)
    val summaryResult: StateFlow<SummaryData?> = _summaryResult.asStateFlow()

    private val _isGeneratingSummary = MutableStateFlow(false)
    val isGeneratingSummary: StateFlow<Boolean> = _isGeneratingSummary.asStateFlow()

    private val _summaryError = MutableStateFlow<String?>(null)
    val summaryError: StateFlow<String?> = _summaryError.asStateFlow()

    private val _detailedSummaryData = MutableStateFlow<List<Pair<String, Int>>?>(null)
    val detailedSummaryData: StateFlow<List<Pair<String, Int>>?> = _detailedSummaryData.asStateFlow()

    private val _isGeneratingDetailedSummary = MutableStateFlow(false)
    val isGeneratingDetailedSummary: StateFlow<Boolean> = _isGeneratingDetailedSummary.asStateFlow()

    private val _detailedSummaryError = MutableStateFlow<String?>(null)
    val detailedSummaryError: StateFlow<String?> = _detailedSummaryError.asStateFlow()

    companion object {
        private const val TAG = "TxtFileViewModel"
        private const val MAX_HISTORY_SIZE = 100
        private val ENGLISH_STOP_WORDS = setOf("a", "an", "the", "is", "in", "it", "of", "for", "on", "with", "to", "and", "or", "but", "at", "by", "from", "as", "this", "that", "these", "those", "i", "you", "he", "she", "we", "they", "my", "your", "his", "her", "its", "our", "their", "am", "are", "was", "were", "be", "been", "being", "have", "has", "had", "do", "does", "did", "will", "would", "should", "can", "could", "may", "might", "must")
        private val RUSSIAN_STOP_WORDS = setOf("и", "в", "с", "на", "о", "не", "но", "по", "из", "за", "к", "от", "до", "под", "над", "перед", "при", "через", "без", "для", "у", "об", "про", "а", "если", "бы", "то", "что", "как", "так", "же", "уже", "еще", "вот", "там", "тут", "здесь", "мой", "твой", "его", "ее", "их", "наш", "ваш", "это", "тот", "эта", "те", "эти", "я", "ты", "он", "она", "оно", "мы", "вы", "они", "быть", "есть", "был", "была", "были")
    }

    init {
        Log.d(TAG, "Initializing for URI: $currentFileUri, InitialHint: $initialFileNameHint")
        loadFileNameAndContent(currentFileUri, isNewFileAfterSaveAs = false)
    }

    private fun loadFileNameAndContent(uri: Uri, isNewFileAfterSaveAs: Boolean) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val context = getApplication<Application>()

            // Attempt to get the definitive file name
            var retrievedDisplayName = initialFileNameHint
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (displayNameIndex != -1) {
                            retrievedDisplayName = cursor.getString(displayNameIndex) ?: initialFileNameHint
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not query display name for $uri, using hint: $initialFileNameHint. Error: ${e.message}")
                // Fallback to URI path if hint is generic like "Loading..." or if query fails badly
                if (initialFileNameHint == "Loading..." || initialFileNameHint.isBlank()) {
                    retrievedDisplayName = uri.lastPathSegment?.substringAfterLast('/') ?: "Untitled.txt"
                }
            }
            _fileName.value = retrievedDisplayName

            try {
                if (!isNewFileAfterSaveAs) {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        BufferedReader(InputStreamReader(inputStream)).use { reader ->
                            val loadedText = reader.readText()
                            _fileContent.value = loadedText
                            _originalContentBeforeEdit.value = loadedText
                            _editableContent.value = loadedText
                            Log.d(TAG, "Successfully loaded content for ${_fileName.value} (URI: $uri)")
                        }
                    } ?: run {
                        _error.value = context.getString(R.string.txt_analysis_error_loading, "Failed to open input stream for $uri")
                        Log.e(TAG, "ContentResolver.openInputStream returned null for $uri")
                        _fileContent.value = null; _originalContentBeforeEdit.value = null; _editableContent.value = ""
                    }
                } else {
                    _fileContent.value = _editableContent.value
                    _originalContentBeforeEdit.value = _editableContent.value
                    Log.d(TAG, "Content set from editableContent after Save As for $uri (${_fileName.value})")
                }
            } catch (e: SecurityException) {
                _error.value = context.getString(R.string.txt_analysis_error_loading, "Permission denied for $uri: ${e.localizedMessage}")
                Log.e(TAG, "SecurityException loading $uri (${_fileName.value})", e)
                _fileContent.value = null; _originalContentBeforeEdit.value = null; _editableContent.value = ""
            } catch (e: Exception) {
                _error.value = context.getString(R.string.txt_analysis_error_loading, "Error loading $uri: ${e.localizedMessage}")
                Log.e(TAG, "Exception loading $uri (${_fileName.value})", e)
                _fileContent.value = null; _originalContentBeforeEdit.value = null; _editableContent.value = ""
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() { _error.value = null }

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
            performSearch(query, if (isEditMode.value) _editableContent.value else _fileContent.value)
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
            val found = textToSearch.indexOf(query, lastIndex, ignoreCase = true)
            if (found == -1) break
            results.add(IntRange(found, found + query.length - 1))
            lastIndex = found + query.length
        }
        _searchResults.value = results
        _currentResultIndex.value = if (results.isNotEmpty()) 0 else -1
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _replaceQuery.value = ""
        _searchResults.value = emptyList()
        _currentResultIndex.value = -1
        searchJob?.cancel()
    }

    fun goToNextMatch() {
        if (searchResults.value.isEmpty()) return
        _currentResultIndex.value = (currentResultIndex.value + 1) % searchResults.value.size
    }

    fun goToPreviousMatch() {
        if (searchResults.value.isEmpty()) return
        _currentResultIndex.value = (currentResultIndex.value - 1 + searchResults.value.size) % searchResults.value.size
    }

    fun onReplaceQueryChanged(query: String) { _replaceQuery.value = query }

    fun replaceCurrentMatch() {
        if (!_isEditMode.value || _searchResults.value.isEmpty() || _currentResultIndex.value == -1) return
        val currentMatchRange = _searchResults.value[_currentResultIndex.value]
        val oldEditableText = _editableContent.value
        val textBefore = oldEditableText.substring(0, currentMatchRange.first)
        val textAfter = oldEditableText.substring(currentMatchRange.last + 1)
        val newEditableText = textBefore + _replaceQuery.value + textAfter
        onEditableContentChanged(newEditableText) // This will handle undo stack
        // Re-run search on the new content to update highlights and current index
        performSearch(_searchQuery.value, newEditableText)
    }

    fun replaceAllMatches() {
        if (!_isEditMode.value || _searchResults.value.isEmpty() || _searchQuery.value.isBlank()) return

        val currentText = _editableContent.value
        val query = _searchQuery.value
        val replacement = _replaceQuery.value

        // To correctly handle overlapping matches or matches changing indices,
        // it's often best to replace from the end of the document to the beginning.
        val originalResults = _searchResults.value.sortedByDescending { it.first }
        var newEditableText = currentText

        originalResults.forEach { range ->
            newEditableText = newEditableText.substring(0, range.first) + replacement + newEditableText.substring(range.last + 1)
        }
        onEditableContentChanged(newEditableText) // This will handle undo stack
        clearSearch() // Clear search as matches are no longer valid
    }

    fun enterEditMode() {
        if (!_isEditMode.value) {
            val content = _fileContent.value ?: ""
            _originalContentBeforeEdit.value = content
            _editableContent.value = content // Set editable content
            _isEditMode.value = true
            _saveError.value = null
            clearSearch() // Clear search when entering edit mode
            // Clear undo/redo history for the new edit session
            undoStack.clear()
            redoStack.clear()
            _canUndo.value = false
            _canRedo.value = false
        }
    }

    fun exitEditMode(force: Boolean = false) {
        if (force || !hasUnsavedChanges.value) {
            _isEditMode.value = false
            _showDiscardConfirmDialog.value = false // Ensure dialog is hidden
            _editableContent.value = _fileContent.value ?: "" // Reset editable content to original
            // Clear undo/redo history as editing session ends
            undoStack.clear()
            redoStack.clear()
            _canUndo.value = false
            _canRedo.value = false
        } else {
            attemptExitEditMode() // This will show the discard dialog
        }
    }

    fun attemptExitEditMode() {
        if (hasUnsavedChanges.value) {
            _showDiscardConfirmDialog.value = true
        } else {
            exitEditMode(force = true)
        }
    }

    fun onEditableContentChanged(newText: String) {
        if (_isEditMode.value) {
            val oldText = _editableContent.value
            if (newText != oldText) {
                // Add to undo stack only if it's a new change
                if (undoStack.isEmpty() || undoStack.lastOrNull() != oldText) {
                    undoStack.addLast(oldText)
                    if (undoStack.size > MAX_HISTORY_SIZE) {
                        undoStack.removeFirst()
                    }
                }
                redoStack.clear() // Clear redo stack on new change
                _editableContent.value = newText
                _canUndo.value = undoStack.isNotEmpty()
                _canRedo.value = false // New edit invalidates redo
            }
        }
    }

    fun saveChanges(andThen: (() -> Unit)? = null) {
        if (!_isEditMode.value) return
        _isSaving.value = true
        _saveError.value = null
        viewModelScope.launch {
            try {
                getApplication<Application>().contentResolver.openOutputStream(currentFileUri, "wt")?.use { outputStream ->
                    OutputStreamWriter(outputStream).use { writer ->
                        writer.write(_editableContent.value)
                    }
                    // After successful save, update the base content
                    _fileContent.value = _editableContent.value
                    _originalContentBeforeEdit.value = _editableContent.value // Reset original content to current
                    // Clear undo/redo history as changes are now persisted
                    undoStack.clear()
                    redoStack.clear()
                    _canUndo.value = false
                    _canRedo.value = false

                    // Attempt to persist permissions, especially useful after "Save As"
                    try {
                        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        getApplication<Application>().contentResolver.takePersistableUriPermission(currentFileUri, takeFlags)
                        Log.d(TAG, "Persisted permissions for $currentFileUri on save.")
                    } catch (e: SecurityException) {
                        Log.w(TAG, "Could not persist permission on save for $currentFileUri (might be okay if already persisted): ${e.message}")
                    }

                    andThen?.invoke() // Execute callback if provided (e.g., exit edit mode)
                } ?: run {
                    _saveError.value = getApplication<Application>().getString(R.string.txt_analysis_save_error_snackbar, "Failed to open output stream")
                }
            } catch (e: Exception) {
                _saveError.value = getApplication<Application>().getString(R.string.txt_analysis_save_error_snackbar, e.localizedMessage ?: "Unknown error")
                Log.e(TAG, "Exception saving $currentFileUri", e)
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun initiateSaveAs() {
        // Allow Save As even if not in edit mode, to save the currently loaded content
        if (_fileContent.value.isNullOrEmpty() && _editableContent.value.isEmpty() && !_isEditMode.value) {
            _saveError.value = getApplication<Application>().getString(R.string.nothing_to_save_toast)
            return
        }
        viewModelScope.launch {
            _initiateSaveAsChannel.send(_fileName.value) // Suggest current file name
        }
    }

    fun onSaveAsUriReceived(newUri: Uri?) {
        if (newUri == null) {
            // User cancelled Save As
            _saveError.value = getApplication<Application>().getString(R.string.save_as_cancelled_toast)
            return
        }
        _isSaving.value = true
        _saveError.value = null
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                // Content to save: if in edit mode, use editableContent, else use fileContent
                val contentToSave = if (_isEditMode.value) _editableContent.value else _fileContent.value ?: ""

                context.contentResolver.openOutputStream(newUri, "wt")?.use { outputStream ->
                    OutputStreamWriter(outputStream).use { writer ->
                        writer.write(contentToSave)
                    }
                    // Persist permissions for the new URI
                    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(newUri, takeFlags)
                    Log.d(TAG, "Persisted permissions for new URI $newUri after Save As.")

                    // Update currentFileUri to the new URI
                    currentFileUri = newUri

                    // Update file name and content based on the new URI
                    // isNewFileAfterSaveAs = true to ensure content isn't re-read from old URI
                    // The name will be re-queried from the new URI.
                    val newFileNameHint = DocumentFile.fromSingleUri(context, newUri)?.name ?: newUri.lastPathSegment ?: "Untitled.txt"
                    _fileName.value = newFileNameHint // Temporarily set, loadFileNameAndContent will get definitive

                    loadFileNameAndContent(newUri, isNewFileAfterSaveAs = true)

                    // If was in edit mode, original content is now this saved content
                    if (_isEditMode.value) {
                        _originalContentBeforeEdit.value = contentToSave
                    }
                    // Clear undo/redo for the new file context
                    undoStack.clear()
                    redoStack.clear()
                    _canUndo.value = false
                    _canRedo.value = false

                } ?: run {
                    _saveError.value = context.getString(R.string.txt_analysis_save_error_snackbar, "Failed to open output stream for Save As")
                }
            } catch (e: Exception) {
                _saveError.value = getApplication<Application>().getString(R.string.txt_analysis_save_error_snackbar, e.localizedMessage ?: "Unknown error for Save As")
                Log.e(TAG, "Exception during Save As for $newUri", e)
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun confirmDiscardChanges() {
        exitEditMode(force = true) // Force exit, discarding changes
    }

    fun saveAndExitEditMode() {
        saveChanges {
            exitEditMode(force = true) // Exit after save is complete
        }
    }

    fun cancelDiscardDialog() {
        _showDiscardConfirmDialog.value = false
    }

    fun clearSaveError() {
        _saveError.value = null
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val currentState = _editableContent.value
            val previousState = undoStack.removeLast()
            redoStack.addLast(currentState)
            if (redoStack.size > MAX_HISTORY_SIZE) {
                redoStack.removeFirst()
            }
            _editableContent.value = previousState
            _canUndo.value = undoStack.isNotEmpty()
            _canRedo.value = true
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val currentState = _editableContent.value
            val nextState = redoStack.removeLast()
            undoStack.addLast(currentState)
            if (undoStack.size > MAX_HISTORY_SIZE) {
                undoStack.removeFirst()
            }
            _editableContent.value = nextState
            _canRedo.value = redoStack.isNotEmpty()
            _canUndo.value = true
        }
    }

    fun generateSummary() {
        _isGeneratingSummary.value = true
        _summaryError.value = null
        _summaryResult.value = null
        viewModelScope.launch {
            delay(500) // Simulate work
            try {
                val contentToSummarize = if (isEditMode.value) _editableContent.value else _fileContent.value
                if (contentToSummarize.isNullOrEmpty()) {
                    _summaryError.value = getApplication<Application>().getString(R.string.txt_analysis_summary_not_available)
                    _isGeneratingSummary.value = false
                    return@launch
                }
                val lines = contentToSummarize.lines()
                val lineCount = lines.size
                val wordCount = contentToSummarize.split(Regex("\\s+")).filter { it.isNotBlank() }.size
                val charCount = contentToSummarize.length
                val charCountWithoutSpaces = contentToSummarize.replace(Regex("\\s"), "").length
                _summaryResult.value = SummaryData(lineCount, wordCount, charCount, charCountWithoutSpaces)
            } catch (e: Exception) {
                Log.e(TAG, "Error generating summary", e)
                _summaryError.value = getApplication<Application>().getString(R.string.txt_analysis_summary_error, e.localizedMessage ?: "Unknown error")
            } finally {
                _isGeneratingSummary.value = false
            }
        }
    }

    fun clearSummary() {
        _summaryResult.value = null
        _summaryError.value = null
        _isGeneratingSummary.value = false // Ensure this is reset
    }

    private fun isLikelyRussian(text: String): Boolean {
        if (text.isBlank()) return false
        // A simple heuristic: if more than 20% of alphabetic characters are Cyrillic.
        val alphabeticChars = text.filter { it.isLetter() }
        if (alphabeticChars.isEmpty()) return false
        val cyrillicCharCount = alphabeticChars.count { it in '\u0400'..'\u04FF' }
        return (cyrillicCharCount.toDouble() / alphabeticChars.length) > 0.2
    }

    fun generateDetailedSummary() {
        _isGeneratingDetailedSummary.value = true
        _detailedSummaryError.value = null
        _detailedSummaryData.value = null

        viewModelScope.launch {
            delay(1000) // Simulate work
            try {
                val contentToAnalyze = if (isEditMode.value) _editableContent.value else _fileContent.value
                if (contentToAnalyze.isNullOrEmpty()) {
                    _detailedSummaryError.value = getApplication<Application>().getString(R.string.txt_analysis_summary_not_available)
                    _isGeneratingDetailedSummary.value = false
                    return@launch
                }

                val likelyRussian = isLikelyRussian(contentToAnalyze)
                val currentStopWords = if (likelyRussian) RUSSIAN_STOP_WORDS else ENGLISH_STOP_WORDS
                // Regex to keep only letters (English or Cyrillic based on detection), numbers, and hyphens (as part of words).
                // It removes punctuation more broadly.
                val unwantedCharsRegex = if (likelyRussian) Regex("[^a-zа-яё0-9\\s-]") else Regex("[^a-z0-9\\s-]")


                val keywords = withContext(Dispatchers.Default) { // Intensive processing on Default dispatcher
                    val cleanedText = contentToAnalyze.lowercase(Locale.getDefault()) // Use Locale for lowercase
                        .replace(unwantedCharsRegex, " ") // Replace unwanted chars with space
                        .trim()

                    val words = cleanedText.split(Regex("\\s+")) // Split by one or more spaces
                        .filter {
                            it.isNotBlank() &&
                                    it.length > (if (likelyRussian) 1 else 2) && // Min word length
                                    it !in currentStopWords &&
                                    it.any { char -> char.isLetter() } // Ensure it contains at least one letter
                        }

                    val wordFrequencies = words.groupingBy { it }.eachCount()

                    wordFrequencies.entries
                        .sortedByDescending { it.value }
                        .take(10) // Top 10 keywords
                        .map { Pair(it.key, it.value) }
                }

                if (keywords.isEmpty()) {
                    _detailedSummaryData.value = listOf(Pair(getApplication<Application>().getString(R.string.txt_analysis_detailed_summary_none_found), 0))
                } else {
                    _detailedSummaryData.value = keywords
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error generating detailed summary", e)
                _detailedSummaryError.value = getApplication<Application>().getString(R.string.txt_analysis_summary_error, e.localizedMessage ?: "Unknown error")
            } finally {
                _isGeneratingDetailedSummary.value = false
            }
        }
    }

    fun clearDetailedSummary() {
        _detailedSummaryData.value = null
        _detailedSummaryError.value = null
        _isGeneratingDetailedSummary.value = false // Ensure this is reset
    }
}