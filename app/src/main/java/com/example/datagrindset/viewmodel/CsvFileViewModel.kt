package com.example.datagrindset.viewmodel

import android.app.Application
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.documentfile.provider.DocumentFile
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
        private const val PREVIEW_ROW_LIMIT = 20
    }

    init {
        Log.i(TAG, "--- CsvFileViewModel INIT ---")
        Log.i(TAG, "Received URI: $initialFileUri (toString: ${initialFileUri.toString()})")
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
                        i++
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
        fields.add(buffer.toString().trim()) // Add the last field
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
                withContext(Dispatchers.IO) {
                    // SAF Fix: Use DocumentFile to resolve and open the file URI
                    val docFile = DocumentFile.fromSingleUri(context, initialFileUri)
                        ?: DocumentFile.fromTreeUri(context, initialFileUri)
                    if (docFile == null || !docFile.canRead()) {
                        Log.e(TAG, "DocumentFile is null or not readable for URI: $initialFileUri")
                        withContext(Dispatchers.Main) {
                            _error.value = "Cannot access file: $initialFileName (DocumentFile null or not readable)"
                        }
                        return@withContext
                    }
                    pfd = context.contentResolver.openFileDescriptor(docFile.uri, "r")
                    if (pfd == null) {
                        Log.e(TAG, "ParcelFileDescriptor is NULL for URI: ${docFile.uri}")
                        withContext(Dispatchers.Main) {
                            _error.value = "Failed to open file descriptor for $initialFileName (PFD was null)."
                        }
                    } else {
                        val safePfd = pfd
                        Log.i(TAG, "SUCCESSFULLY opened ParcelFileDescriptor for URI: ${docFile.uri}. Size: ${safePfd!!.statSize}")
                        FileInputStream(safePfd.fileDescriptor).use { fis ->
                            BufferedReader(InputStreamReader(fis)).use { reader ->
                                val allRows = mutableListOf<List<String>>()
                                var line: String?
                                var firstLine = true
                                var maxCols = 0

                                while (reader.readLine().also { line = it } != null) {
                                    if (line.isNullOrBlank() && reader.ready().not()) continue // Skip blank lines unless it's the only line
                                    val parsedFields = parseCsvLine(line!!)
                                    if (parsedFields.isNotEmpty()) {
                                        if (firstLine) {
                                            withContext(Dispatchers.Main) { _headers.value = parsedFields }
                                            firstLine = false
                                        }
                                        allRows.add(parsedFields)
                                        if (parsedFields.size > maxCols) {
                                            maxCols = parsedFields.size
                                        }
                                    }
                                }

                                withContext(Dispatchers.Main) {
                                    if (_headers.value.isNotEmpty()) {
                                        _previewData.value = allRows.drop(1).take(PREVIEW_ROW_LIMIT)
                                        _rowCount.value = allRows.size
                                        _columnCount.value = _headers.value.size
                                    } else if (allRows.isNotEmpty()) {
                                        _previewData.value = allRows.take(PREVIEW_ROW_LIMIT)
                                        _rowCount.value = allRows.size
                                        _columnCount.value = maxCols
                                        _headers.value = List(maxCols) { index -> "Col ${index + 1}" }
                                    } else {
                                        _rowCount.value = 0
                                        _columnCount.value = 0
                                    }
                                }
                                Log.i(TAG, "CSV content loaded. Rows: ${allRows.size}, MaxCols: $maxCols")
                            }
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
                try {
                    pfd?.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing PFD: ${e.message}", e)
                }
                _isLoading.value = false
                Log.i(TAG, "--- loadCsvFile finished --- Error: ${_error.value}")
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}

