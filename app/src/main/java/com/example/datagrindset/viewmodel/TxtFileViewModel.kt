package com.example.datagrindset.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.LinkedList

data class SummaryData(
    val lineCount: Int,
    val wordCount: Int,
    val charCount: Int,
    val charCountWithoutSpaces: Int
)

class TxtFileViewModel(
    application: Application,
    initialFileUri: Uri,
    initialFileName: String
) : AndroidViewModel(application) {

    private var currentFileUri: Uri = initialFileUri
    private var currentFileName: String = initialFileName

    private val _fileName = MutableStateFlow(this.currentFileName)
    val fileName: StateFlow<String> = _fileName.asStateFlow()

    private val _fileContent = MutableStateFlow<String?>(null)
    val fileContent: StateFlow<String?> = _fileContent.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // ... (other state flows remain the same)
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
        private const val TAG = "TxtFileViewModel" // Ensure TAG is correct
        private const val MAX_HISTORY_SIZE = 100
        private val ENGLISH_STOP_WORDS = setOf("a", "an", "and", "are", "as", "at", "be", "but", "by", "for", "if", "in", "into", "is", "it", "no", "not", "of", "on", "or", "such", "that", "the", "their", "then", "there", "these", "they", "this", "to", "was", "will", "with", "i", "me", "my", "myself", "we", "our", "ours", "ourselves", "you", "your", "yours", "yourself", "yourselves", "he", "him", "his", "himself", "she", "her", "hers", "herself", "its", "itself", "them", "theirs", "themselves", "what", "which", "who", "whom", "those", "am", "were", "been", "being", "have", "has", "had", "having", "do", "does", "did", "doing", "because", "until", "while", "about", "against", "between", "through", "during", "before", "after", "above", "below", "from", "up", "down", "out", "off", "over", "under", "again", "further", "once", "here", "when", "where", "why", "how", "all", "any", "both", "each", "few", "more", "most", "other", "some", "only", "own", "same", "so", "than", "too", "very", "s", "t", "can", "just", "don", "should", "now", "d", "ll", "m", "o", "re", "ve", "y")
        private val RUSSIAN_STOP_WORDS = setOf("и", "в", "во", "не", "что", "он", "на", "я", "с", "со", "как", "а", "то", "все", "она", "так", "его", "но", "да", "ты", "к", "у", "же", "вы", "за", "бы", "по", "только", "ее", "мне", "было", "вот", "от", "меня", "еще", "нет", "о", "из", "ему", "теперь", "когда", "даже", "ну", "вдруг", "ли", "если", "уже", "или", "ни", "быть", "был", "него", "до", "вас", "нибудь", "опять", "уж", "вам", "ведь", "там", "потом", "себя", "ничего", "ей", "может", "они", "тут", "где", "есть", "надо", "ней", "для", "мы", "тебя", "их", "чем", "была", "сам", "чтоб", "без", "будто", "чего", "раз", "тоже", "себе", "под", "будет", "ж", "тогда", "кто", "этот", "того", "потому", "этого", "какой", "совсем", "ним", "здесь", "эти", "куда", "весь", "вся", "всё", "мой", "моя", "моё", "мои", "твой", "твоя", "твоё", "твои", "наш", "наша", "наше", "наши", "ваш", "ваша", "ваше", "ваши", "который", "которая", "которое", "которые", "сейчас")
    }

    init {
        Log.i(TAG, "--- TxtFileViewModel INIT ---")
        Log.i(TAG, "Received URI: $currentFileUri (toString: ${currentFileUri.toString()})")
        Log.i(TAG, "Received FileName: $currentFileName")
        loadContent(isNewFileAfterSaveAs = false)
    }

    private fun loadContent(isNewFileAfterSaveAs: Boolean) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            Log.i(TAG, "--- loadContent ---")
            Log.i(TAG, "Using URI: $currentFileUri for loading. isNewFileAfterSaveAs: $isNewFileAfterSaveAs")
            Log.i(TAG, "Current Filename for error messages: $currentFileName")

            val context = getApplication<Application>()
            var pfd: ParcelFileDescriptor? = null
            try {
                Log.d(TAG, "Attempting context.contentResolver.openFileDescriptor for URI: $currentFileUri")
                pfd = context.contentResolver.openFileDescriptor(currentFileUri, "r")

                if (pfd == null) {
                    Log.e(TAG, "ParcelFileDescriptor is NULL for URI: $currentFileUri")
                    _error.value = "Failed to open file descriptor for $currentFileName (PFD was null)."
                } else {
                    Log.i(TAG, "SUCCESSFULLY opened ParcelFileDescriptor for URI: $currentFileUri. Size: ${pfd.statSize}")
                    // Now, if this works, proceed to read the content
                    if (!isNewFileAfterSaveAs) {
                        FileInputStream(pfd.fileDescriptor).use { fileInputStream ->
                            BufferedReader(InputStreamReader(fileInputStream)).use { reader ->
                                val loadedText = reader.readText()
                                _fileContent.value = loadedText
                                _originalContentBeforeEdit.value = loadedText
                                _editableContent.value = loadedText
                                Log.i(TAG, "File content loaded successfully.")
                            }
                        }
                    } else {
                        _fileContent.value = _editableContent.value
                        _originalContentBeforeEdit.value = _editableContent.value
                        Log.i(TAG, "Content set from editableContent after Save As.")
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException in loadContent for $currentFileUri: ${e.message}", e)
                _error.value = "Permission Denied: $currentFileName. Please re-select the folder. (Details: ${e.message})"
            } catch (e: Exception) {
                Log.e(TAG, "Generic Exception in loadContent for $currentFileUri: ${e.message}", e)
                _error.value = "Error loading $currentFileName: ${e.localizedMessage}"
            } finally {
                try {
                    pfd?.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing PFD: ${e.message}", e)
                }
                _isLoading.value = false
                Log.i(TAG, "--- loadContent finished --- Error: ${_error.value}")
            }
        }
    }

    // ... (onSearchQueryChanged and other methods remain the same)
    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query; searchJob?.cancel()
        if (query.isBlank()) { _searchResults.value = emptyList(); _currentResultIndex.value = -1; return }
        searchJob = viewModelScope.launch { delay(300); performSearch(query, if(isEditMode.value) _editableContent.value else _fileContent.value) }
    }

    private fun performSearch(query: String, textToSearch: String?) {
        if (textToSearch == null || query.isBlank()) { _searchResults.value = emptyList(); _currentResultIndex.value = -1; return }
        val results = mutableListOf<IntRange>()
        var lastIndex = 0
        while(lastIndex < textToSearch.length) {
            val found = textToSearch.indexOf(query, lastIndex, ignoreCase = true)
            if (found == -1) break
            results.add(IntRange(found, found + query.length -1)); lastIndex = found + query.length
        }
        _searchResults.value = results; _currentResultIndex.value = if (results.isNotEmpty()) 0 else -1
    }

    fun clearSearch() {
        _searchQuery.value = ""; _replaceQuery.value = ""; _searchResults.value = emptyList(); _currentResultIndex.value = -1; searchJob?.cancel()
    }

    fun goToNextMatch() {
        if(searchResults.value.isEmpty()) return; _currentResultIndex.value = (currentResultIndex.value + 1) % searchResults.value.size
    }

    fun goToPreviousMatch() {
        if(searchResults.value.isEmpty()) return; _currentResultIndex.value = (currentResultIndex.value - 1 + searchResults.value.size) % searchResults.value.size
    }

    fun onReplaceQueryChanged(query: String) { _replaceQuery.value = query }

    fun replaceCurrentMatch() {
        if (!_isEditMode.value || _searchResults.value.isEmpty() || _currentResultIndex.value == -1) return
        val currentMatchRange = _searchResults.value[_currentResultIndex.value]; val oldEditableText = _editableContent.value
        val textBefore = oldEditableText.substring(0, currentMatchRange.first)
        val textAfter = oldEditableText.substring(currentMatchRange.last + 1)
        val newEditableText = textBefore + _replaceQuery.value + textAfter
        onEditableContentChanged(newEditableText)
        performSearch(_searchQuery.value, newEditableText)
    }

    fun replaceAllMatches() {
        if (!_isEditMode.value || _searchResults.value.isEmpty() || _searchQuery.value.isBlank()) return
        val currentText = _editableContent.value; val query = _searchQuery.value; val replacement = _replaceQuery.value
        val originalResults = _searchResults.value.sortedByDescending { it.first }
        var newEditableText = currentText
        originalResults.forEach { range -> newEditableText = newEditableText.substring(0, range.first) + replacement + newEditableText.substring(range.last + 1) }
        onEditableContentChanged(newEditableText)
        clearSearch()
    }

    fun enterEditMode() {
        if (!_isEditMode.value) {
            val content = _fileContent.value ?: ""; _originalContentBeforeEdit.value = content; _editableContent.value = content
            _isEditMode.value = true; _saveError.value = null; clearSearch()
            undoStack.clear(); redoStack.clear(); _canUndo.value = false; _canRedo.value = false
        }
    }

    fun exitEditMode() {
        _isEditMode.value = false; _showDiscardConfirmDialog.value = false; _editableContent.value = _fileContent.value ?: ""
        undoStack.clear(); redoStack.clear(); _canUndo.value = false; _canRedo.value = false
    }

    fun attemptExitEditMode() { if (hasUnsavedChanges.value) _showDiscardConfirmDialog.value = true else exitEditMode() }

    fun onEditableContentChanged(newText: String) {
        if (_isEditMode.value) {
            val oldText = _editableContent.value
            if (newText != oldText) {
                if (undoStack.isEmpty() || undoStack.lastOrNull() != oldText) {
                    undoStack.addLast(oldText); if (undoStack.size > MAX_HISTORY_SIZE) undoStack.removeFirst()
                }
                redoStack.clear(); _editableContent.value = newText
                _canUndo.value = undoStack.isNotEmpty(); _canRedo.value = false
            }
        }
    }

    fun saveChanges(andThen: (() -> Unit)? = null) {
        if (!_isEditMode.value) return
        _isSaving.value = true; _saveError.value = null
        viewModelScope.launch {
            try {
                getApplication<Application>().contentResolver.openOutputStream(currentFileUri, "wt")?.use { outputStream ->
                    OutputStreamWriter(outputStream).use { writer -> writer.write(_editableContent.value) }
                    _fileContent.value = _editableContent.value; _originalContentBeforeEdit.value = _editableContent.value
                    undoStack.clear(); redoStack.clear(); _canUndo.value = false; _canRedo.value = false
                    andThen?.invoke()
                } ?: run { _saveError.value = "Failed to open output stream for writing." }
            } catch (e: Exception) { _saveError.value = "Error saving file: ${e.localizedMessage}"; Log.e(TAG, "Ex saving $currentFileUri",e) }
            finally { _isSaving.value = false }
        }
    }

    fun initiateSaveAs() {
        if (!_isEditMode.value && _fileContent.value.isNullOrEmpty() && _editableContent.value.isEmpty()) { _saveError.value = "Nothing to save."; return }
        viewModelScope.launch {
            val suggestedName = currentFileName
            _initiateSaveAsChannel.send(suggestedName)
        }
    }

    fun onSaveAsUriReceived(newUriFromPicker: Uri?) {
        if (newUriFromPicker == null) { _saveError.value = "Save As was cancelled."; return }
        _isSaving.value = true; _saveError.value = null
        viewModelScope.launch {
            var retrievedNewFileName = currentFileName
            try {
                val context = getApplication<Application>()
                context.contentResolver.openOutputStream(newUriFromPicker, "wt")?.use { outputStream ->
                    OutputStreamWriter(outputStream).use { writer -> writer.write(_editableContent.value) }
                } ?: run {
                    _saveError.value = "Failed to open output stream for 'Save As'."
                    _isSaving.value = false
                    return@launch
                }

                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(newUriFromPicker, takeFlags)

                context.contentResolver.query(newUriFromPicker, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (displayNameIndex != -1) {
                            retrievedNewFileName = cursor.getString(displayNameIndex)
                        }
                    }
                }
                Log.d(TAG, "New file name after Save As: $retrievedNewFileName (URI: $newUriFromPicker)")

                currentFileUri = newUriFromPicker
                currentFileName = retrievedNewFileName
                _fileName.value = retrievedNewFileName

                loadContent(isNewFileAfterSaveAs = true)

                undoStack.clear(); redoStack.clear(); _canUndo.value = false; _canRedo.value = false
                if (_isEditMode.value) _isEditMode.value = false
            } catch (e: Exception) {
                _saveError.value = "Error during 'Save As': ${e.localizedMessage}"; Log.e(TAG, "Ex SaveAs $newUriFromPicker",e)
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun confirmDiscardChanges() { exitEditMode() }
    fun saveAndExitEditMode() { saveChanges { exitEditMode() } }
    fun cancelDiscardDialog() { _showDiscardConfirmDialog.value = false }
    fun clearSaveError() { _saveError.value = null }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val currentState = _editableContent.value; val previousState = undoStack.removeLast()
            redoStack.addLast(currentState); if (redoStack.size > MAX_HISTORY_SIZE) redoStack.removeFirst()
            _editableContent.value = previousState
            _canUndo.value = undoStack.isNotEmpty(); _canRedo.value = true
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val currentState = _editableContent.value; val nextState = redoStack.removeLast()
            undoStack.addLast(currentState); if (undoStack.size > MAX_HISTORY_SIZE) undoStack.removeFirst()
            _editableContent.value = nextState
            _canRedo.value = redoStack.isNotEmpty(); _canUndo.value = true
        }
    }

    fun generateSummary() {
        _isGeneratingSummary.value = true; _summaryError.value = null; _summaryResult.value = null
        viewModelScope.launch {
            delay(500)
            try {
                val contentToSummarize = if (isEditMode.value) _editableContent.value else _fileContent.value
                if (contentToSummarize.isNullOrEmpty()) { _summaryError.value = "No content to summarize."; _isGeneratingSummary.value = false; return@launch }
                val lines = contentToSummarize.lines(); val lineCount = lines.size
                val wordCount = contentToSummarize.split(Regex("\\s+")).filter { it.isNotBlank() }.size
                val charCount = contentToSummarize.length
                val charCountWithoutSpaces = contentToSummarize.replace(Regex("\\s"), "").length
                _summaryResult.value = SummaryData(lineCount, wordCount, charCount, charCountWithoutSpaces)
            } catch (e: Exception) { Log.e(TAG, "Error generating summary", e); _summaryError.value = "Failed to generate summary: ${e.localizedMessage}" }
            finally { _isGeneratingSummary.value = false }
        }
    }

    fun clearSummary() {
        _summaryResult.value = null; _summaryError.value = null; _isGeneratingSummary.value = false
    }

    private fun isLikelyRussian(text: String): Boolean {
        if (text.isBlank()) return false
        val cyrillicCharCount = text.count { it in '\u0400'..'\u04FF' }
        return if (text.isNotEmpty()) (cyrillicCharCount.toDouble() / text.length) > 0.2 else false
    }

    fun generateDetailedSummary() {
        _isGeneratingDetailedSummary.value = true
        _detailedSummaryError.value = null
        _detailedSummaryData.value = null
        viewModelScope.launch {
            delay(1000)
            try {
                val contentToAnalyze = if (isEditMode.value) _editableContent.value else _fileContent.value
                if (contentToAnalyze.isNullOrEmpty()) {
                    _detailedSummaryError.value = "No content to analyze."
                    _isGeneratingDetailedSummary.value = false
                    return@launch
                }
                val likelyRussian = isLikelyRussian(contentToAnalyze)
                val currentStopWords = if (likelyRussian) RUSSIAN_STOP_WORDS else ENGLISH_STOP_WORDS
                val punctuationRegex = if (likelyRussian) Regex("[^a-zа-яё0-9\\s-]") else Regex("[^a-z0-9\\s-]")

                val keywords = withContext(Dispatchers.Default) {
                    val cleanedText = contentToAnalyze.lowercase()
                        .replace(punctuationRegex, " ").trim()
                    val words = cleanedText.split(Regex("\\s+"))
                        .filter { it.isNotBlank() && it.length > (if (likelyRussian) 1 else 2) && it !in currentStopWords && it.any { char -> char.isLetter() } }
                    val wordFrequencies = words.groupingBy { it }.eachCount()
                    wordFrequencies.entries.sortedByDescending { it.value }.take(10).map { Pair(it.key, it.value) }
                }
                if (keywords.isEmpty()){ _detailedSummaryData.value = listOf(Pair(if (likelyRussian) "Ключевые слова не найдены." else "No significant keywords found.", 0)) }
                else { _detailedSummaryData.value = keywords }
            } catch (e: Exception) { Log.e(TAG, "Error generating detailed summary", e); _detailedSummaryError.value = "Failed to generate detailed summary: ${e.localizedMessage}" }
            finally { _isGeneratingDetailedSummary.value = false }
        }
    }
    fun clearDetailedSummary() {
        _detailedSummaryData.value = null; _detailedSummaryError.value = null; _isGeneratingDetailedSummary.value = false
    }
}

