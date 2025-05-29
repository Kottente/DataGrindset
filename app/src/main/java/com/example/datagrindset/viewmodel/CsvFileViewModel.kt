package com.example.datagrindset.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
//import com.example.datagrindset.R // Not used in this file currently
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
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

    private val _fileSize = MutableStateFlow<Long>(0L)
    val fileSize: StateFlow<Long> = _fileSize.asStateFlow()


    companion object {
        private const val TAG = "CsvFileViewModel"
        private const val PREVIEW_ROW_LIMIT = 50
    }

    init {
        Log.i(TAG, "--- CsvFileViewModel INIT ---")
        Log.i(TAG, "Received URI from picker: $initialFileUri")
        Log.i(TAG, "Received FileName: $initialFileName")
        loadCsvFile()
    }

    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val buffer = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val char = line[i]
            when {
                char == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        buffer.append('"')
                        i++ // Skip next quote
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                char == ',' && !inQuotes -> {
                    fields.add(buffer.toString().trim())
                    buffer.clear()
                }
                else -> {
                    buffer.append(char)
                }
            }
            i++
        }
        fields.add(buffer.toString().trim())
        return fields
    }

    fun loadCsvFile() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _fileName.value = initialFileName
            Log.i(TAG, "--- loadCsvFile (using ContentResolver) ---")
            Log.i(TAG, "URI: $initialFileUri, FileName: $initialFileName")

            val context = getApplication<Application>()
            try {
                try {
                    val docFile = DocumentFile.fromSingleUri(context, initialFileUri)
                    if (docFile != null && docFile.exists()) {
                        _fileSize.value = docFile.length()
                        Log.i(TAG, "Got file size: ${_fileSize.value} for ${docFile.name}")
                    } else {
                        Log.w(TAG, "Could not get DocumentFile for size for URI: $initialFileUri.")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error getting DocumentFile for size: ${e.message}.")
                }

                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(initialFileUri)?.use { fis ->
                        BufferedReader(InputStreamReader(fis)).use { reader ->
                            val allRows = mutableListOf<List<String>>()
                            var line: String?
                            var firstLineRead = false // To ensure headers are from the first line
                            var tempHeaders = emptyList<String>()
                            var maxCols = 0

                            while (reader.readLine().also { line = it } != null) {
                                if (line.isNullOrBlank() && !reader.ready()) continue
                                val parsedFields = parseCsvLine(line!!)
                                if (parsedFields.isNotEmpty()) {
                                    if (!firstLineRead) {
                                        tempHeaders = parsedFields
                                        firstLineRead = true
                                    }
                                    allRows.add(parsedFields)
                                    if (parsedFields.size > maxCols) {
                                        maxCols = parsedFields.size
                                    }
                                }
                            }

                            withContext(Dispatchers.Main) {
                                if (tempHeaders.isNotEmpty()) {
                                    _headers.value = tempHeaders
                                    _previewData.value = allRows.drop(1).take(PREVIEW_ROW_LIMIT)
                                    _rowCount.value = allRows.size // Includes header row
                                    _columnCount.value = tempHeaders.size
                                } else if (allRows.isNotEmpty()) { // No header detected, but has rows
                                    _previewData.value = allRows.take(PREVIEW_ROW_LIMIT)
                                    _rowCount.value = allRows.size
                                    _columnCount.value = maxCols
                                    _headers.value = List(maxCols) { index -> "Col ${index + 1}" }
                                } else { // Empty file
                                    _rowCount.value = 0
                                    _columnCount.value = 0
                                    _headers.value = emptyList()
                                    _previewData.value = emptyList()
                                }
                            }
                            Log.i(TAG, "CSV content loaded. Total Rows (incl. header if detected): ${allRows.size}, MaxCols: $maxCols")
                        }
                    } ?: run {
                        Log.e(TAG, "ContentResolver.openInputStream returned null for URI: $initialFileUri")
                        withContext(Dispatchers.Main) {
                            _error.value = "Failed to open file stream for $initialFileName."
                        }
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException in loadCsvFile for $initialFileUri: ${e.message}", e)
                _error.value = "Permission Denied: $initialFileName. (Details: ${e.message})"
            } catch (e: Exception) {
                Log.e(TAG, "Generic Exception in loadCsvFile for $initialFileUri: ${e.message}", e)
                _error.value = "Error loading $initialFileName: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
                Log.i(TAG, "--- loadCsvFile finished --- Error: ${_error.value}")
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}

