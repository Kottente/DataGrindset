package com.example.datagrindset.viewmodel

import android.app.Application
import android.net.Uri
import android.os.ParcelFileDescriptor
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
import java.io.FileInputStream
import java.io.InputStreamReader

class CsvFileViewModel(
    application: Application,
    private val initialFileUri: Uri,
    private val initialFileName: String
) : AndroidViewModel(application) {

    private val _fileName = MutableStateFlow(initialFileName)
    val fileName: StateFlow<String> = _fileName.asStateFlow()

    private val _rowCount = MutableStateFlow(0)
    val rowCount: StateFlow<Int> = _rowCount.asStateFlow()

    private val _columnCount = MutableStateFlow(0)
    val columnCount: StateFlow<Int> = _columnCount.asStateFlow()

    private val _headers = MutableStateFlow<List<String>>(emptyList())
    val headers: StateFlow<List<String>> = _headers.asStateFlow()

    private val _previewData = MutableStateFlow<List<List<String>>>(emptyList())
    val previewData: StateFlow<List<List<String>>> = _previewData.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    companion object {
        private const val TAG = "CsvFileViewModel"
        private const val PREVIEW_ROW_LIMIT = 10 // Number of data rows to preview (excluding header)
    }

    init {
        Log.i(TAG, "--- CsvFileViewModel INIT ---")
        Log.i(TAG, "Received URI: $initialFileUri (toString: ${initialFileUri.toString()})")
        Log.i(TAG, "Received FileName: $initialFileName")
        loadCsvFile()
    }

    // Basic CSV parser (can be improved for edge cases like quotes within quotes)
    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val buffer = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val char = line[i]
            when {
                char == '"' -> {
                    // Handle escaped quotes ("")
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        buffer.append('"')
                        i++ // Skip the second quote of the pair
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                char == ',' && !inQuotes -> {
                    fields.add(buffer.toString())
                    buffer.clear()
                }
                else -> {
                    buffer.append(char)
                }
            }
            i++
        }
        fields.add(buffer.toString()) // Add the last field
        return fields
    }


    fun loadCsvFile() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            Log.i(TAG, "--- loadCsvFile ---")
            Log.i(TAG, "Using URI: $initialFileUri for loading.")
            Log.i(TAG, "Current Filename for error messages: $initialFileName")

            val context = getApplication<Application>()
            var pfd: ParcelFileDescriptor? = null
            try {
                // Reading file content should be on a background thread
                withContext(Dispatchers.IO) {
                    Log.d(TAG, "Attempting context.contentResolver.openFileDescriptor for URI: $initialFileUri")
                    pfd = context.contentResolver.openFileDescriptor(initialFileUri, "r")

                    if (pfd == null) {
                        Log.e(TAG, "ParcelFileDescriptor is NULL for URI: $initialFileUri")
                        withContext(Dispatchers.Main) {
                            _error.value = "Failed to open file descriptor for $initialFileName (PFD was null)."
                        }
                    } else {
                        Log.i(TAG, "SUCCESSFULLY opened ParcelFileDescriptor for URI: $initialFileUri. Size: ${pfd.statSize}")
                        FileInputStream(pfd.fileDescriptor).use { fis ->
                            BufferedReader(InputStreamReader(fis)).use { reader ->
                                val allRows = mutableListOf<List<String>>()
                                var line: String?
                                var firstLine = true

                                while (reader.readLine().also { line = it } != null) {
                                    if (line.isNullOrBlank() && allRows.isEmpty()) continue // Skip initial blank lines
                                    val parsedFields = parseCsvLine(line!!)
                                    if (firstLine) {
                                        withContext(Dispatchers.Main) { _headers.value = parsedFields }
                                        firstLine = false
                                        // Don't add header to allRows if you handle it separately for rowCount
                                    } else {
                                        allRows.add(parsedFields)
                                    }
                                }

                                withContext(Dispatchers.Main) {
                                    _previewData.value = allRows.take(PREVIEW_ROW_LIMIT)
                                    _rowCount.value = allRows.size + if(_headers.value.isNotEmpty()) 1 else 0 // Add 1 for header if present
                                    _columnCount.value = _headers.value.size
                                    if (_headers.value.isEmpty() && allRows.isNotEmpty() && allRows.first().isNotEmpty()) {
                                        // Attempt to generate generic headers if none found but data exists
                                        _headers.value = List(allRows.first().size) { index -> "Column ${index + 1}" }
                                        _columnCount.value = _headers.value.size
                                        // Recalculate row count if we just generated headers
                                        _rowCount.value = allRows.size // Data rows only, as header was just made up
                                    }
                                }
                                Log.i(TAG, "CSV content loaded successfully. Headers: ${_headers.value.size}, Rows (data): ${allRows.size}")
                            }
                        }
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException in loadCsvFile for $initialFileUri: ${e.message}", e)
                _error.value = "Permission Denied: $initialFileName. Please re-select the folder. (Details: ${e.message})"
            } catch (e: Exception) {
                Log.e(TAG, "Generic Exception in loadCsvFile for $initialFileUri: ${e.message}", e)
                _error.value = "Error loading $initialFileName: ${e.localizedMessage}"
            } finally {
                try {
                    pfd?.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing PFD: ${e.message}", e)
                }
                // Ensure isLoading is set on the Main thread
                if (_isLoading.value) { // Avoid unnecessary switch if already handled by error path
                    withContext(Dispatchers.Main) {
                        _isLoading.value = false
                    }
                }
                Log.i(TAG, "--- loadCsvFile finished --- Error: ${_error.value}")
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}

class CsvFileViewModelFactory(
    private val application: Application,
    private val initialFileUri: Uri,
    private val initialFileName: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CsvFileViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CsvFileViewModel(application, initialFileUri, initialFileName) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}