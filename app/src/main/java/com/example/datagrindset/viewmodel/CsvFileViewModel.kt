package com.example.datagrindset.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class CsvFileViewModel(application: Application, private val fileUri: Uri) : AndroidViewModel(application) {

    private val _fileName = MutableStateFlow("Loading...")
    val fileName: StateFlow<String> = _fileName.asStateFlow()

    private val _headers = MutableStateFlow<List<String>>(emptyList())
    val headers: StateFlow<List<String>> = _headers.asStateFlow()

    private val _rows = MutableStateFlow<List<List<String>>>(emptyList())
    val rows: StateFlow<List<List<String>>> = _rows.asStateFlow()

    private val _rowCount = MutableStateFlow(0)
    val rowCount: StateFlow<Int> = _rowCount.asStateFlow()

    private val _columnCount = MutableStateFlow(0)
    val columnCount: StateFlow<Int> = _columnCount.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    companion object {
        private const val TAG = "CsvFileViewModel"
    }

    init {
        loadFileAndParseCsv()
    }

    private fun loadFileAndParseCsv() {
        _isLoading.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                // Load file name
                getApplication<Application>().contentResolver.query(fileUri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        _fileName.value = if (nameIndex != -1) cursor.getString(nameIndex) else fileUri.lastPathSegment ?: "Unknown CSV"
                    } else {
                        _fileName.value = fileUri.lastPathSegment ?: "Unknown CSV"
                    }
                } ?: run { _fileName.value = fileUri.lastPathSegment ?: "Unknown CSV" }

                // Read and parse CSV content
                val fileContentString = readFileContent(fileUri)
                if (fileContentString == null) {
                    _error.value = "Failed to read file content."
                    _isLoading.value = false
                    return@launch
                }
                parseCsvContent(fileContentString)

            } catch (e: SecurityException) {
                Log.e(TAG, "Permission denied loading CSV: $fileUri", e)
                _error.value = "Permission denied. Cannot load file."
            } catch (e: Exception) {
                Log.e(TAG, "Error loading or parsing CSV: $fileUri", e)
                _error.value = "Error processing CSV file: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun readFileContent(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    return@withContext reader.readText()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading file content from URI: $uri", e)
            return@withContext null
        }
    }

    private suspend fun parseCsvContent(csvString: String, delimiter: Char = ',') {
        withContext(Dispatchers.Default) { // Perform parsing on a background thread
            val parsedRows = mutableListOf<List<String>>()
            val lines = csvString.lines()

            if (lines.isEmpty()) {
                _rowCount.value = 0
                _columnCount.value = 0
                _headers.value = emptyList()
                _rows.value = emptyList()
                return@withContext
            }

            // Assume first line is header by default
            // Basic CSV parsing: splits by delimiter, handles simple quotes.
            // This is a naive parser and won't handle all CSV complexities (e.g., escaped quotes within quotes, newlines in fields).
            val headerLine = lines.firstOrNull()
            if (headerLine != null) {
                _headers.value = parseCsvLine(headerLine, delimiter)
                _columnCount.value = _headers.value.size

                val dataLines = if (lines.size > 1) lines.subList(1, lines.size) else emptyList()
                dataLines.forEach { line ->
                    if (line.isNotBlank()) { // Ignore empty lines
                        parsedRows.add(parseCsvLine(line, delimiter))
                    }
                }
                _rows.value = parsedRows
                _rowCount.value = parsedRows.size
            } else {
                _headers.value = emptyList()
                _rows.value = emptyList()
                _rowCount.value = 0
                _columnCount.value = 0
            }
        }
    }

    // Naive CSV line parser
    private fun parseCsvLine(line: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        val builder = StringBuilder()
        var inQuotes = false
        line.forEach { char ->
            when {
                char == '"' -> inQuotes = !inQuotes // Toggle quote state (doesn't handle escaped quotes)
                char == delimiter && !inQuotes -> {
                    result.add(builder.toString()); builder.clear()
                }
                else -> builder.append(char)
            }
        }
        result.add(builder.toString()) // Add the last field
        return result.map { it.trim() } // Trim whitespace from fields
    }

    fun refreshData() {
        loadFileAndParseCsv()
    }
}

class CsvFileViewModelFactory(
    private val application: Application,
    private val fileUri: Uri
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CsvFileViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CsvFileViewModel(application, fileUri) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}