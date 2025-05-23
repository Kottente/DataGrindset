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
    private val initialFileUri: Uri, // Corrected: Matches factory
    private val initialFileName: String // Corrected: Matches factory
) : AndroidViewModel(application) {

    // Use initialFileName for the StateFlow
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
        private const val PREVIEW_ROW_LIMIT = 10
    }

    init {
        Log.d(TAG, "Initializing for URI: $initialFileUri, FileName: $initialFileName")
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
                    fields.add(buffer.toString())
                    buffer.clear()
                }
                else -> {
                    buffer.append(char)
                }
            }
            i++
        }
        fields.add(buffer.toString())
        return fields
    }

    fun loadCsvFile() {
        _isLoading.value = true
        _error.value = null
        viewModelScope.launch {
            // Use initialFileUri and initialFileName from constructor
            Log.d(TAG, "Attempting to load CSV content for URI: $initialFileUri, Name: $initialFileName")
            try {
                val context = getApplication<Application>()

                withContext(Dispatchers.IO) {
                    val parcelFileDescriptor: ParcelFileDescriptor? = try {
                        context.contentResolver.openFileDescriptor(initialFileUri, "r")
                    } catch (e: SecurityException) {
                        Log.e(TAG, "SecurityException opening PFD for CSV $initialFileUri", e)
                        _error.value = "Permission denied to read CSV file: $initialFileName. Error: ${e.message}"
                        withContext(Dispatchers.Main) { _isLoading.value = false }
                        return@withContext
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception opening PFD for CSV $initialFileUri", e)
                        _error.value = "Failed to open CSV file: $initialFileName (${e.localizedMessage})"
                        withContext(Dispatchers.Main) { _isLoading.value = false }
                        return@withContext
                    }

                    if (parcelFileDescriptor == null) {
                        _error.value = "Failed to open file descriptor for CSV: $initialFileName."
                    } else {
                        FileInputStream(parcelFileDescriptor.fileDescriptor).use { fis ->
                            BufferedReader(InputStreamReader(fis)).use { reader ->
                                val allRows = mutableListOf<List<String>>()
                                var line: String?
                                var firstLine = true

                                while (reader.readLine().also { line = it } != null) {
                                    if (line.isNullOrBlank()) continue
                                    val parsedFields = parseCsvLine(line!!)
                                    if (firstLine) {
                                        _headers.value = parsedFields
                                        firstLine = false
                                    }
                                    allRows.add(parsedFields)
                                }

                                if (_headers.value.isNotEmpty()) {
                                    _previewData.value = allRows.drop(1).take(PREVIEW_ROW_LIMIT)
                                    _rowCount.value = allRows.size
                                } else if (allRows.isNotEmpty()) {
                                    _previewData.value = allRows.take(PREVIEW_ROW_LIMIT)
                                    _rowCount.value = allRows.size
                                    if (allRows.first().isNotEmpty()) {
                                        _headers.value = List(allRows.first().size) { index -> "Column ${index + 1}" }
                                    }
                                } else {
                                    _rowCount.value = 0
                                }
                                _columnCount.value = _headers.value.size
                                Log.i(TAG, "CSV content loaded successfully for $initialFileUri")
                            }
                        }
                        parcelFileDescriptor.close()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading CSV file $initialFileUri", e)
                _error.value = "Error parsing CSV: ${e.localizedMessage}"
            } finally {
                if (_isLoading.value) {
                    withContext(Dispatchers.Main) {
                        _isLoading.value = false
                    }
                }
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}

class CsvFileViewModelFactory(
    private val application: Application,
    private val initialFileUri: Uri, // Stays initialFileUri
    private val initialFileName: String // Stays initialFileName
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CsvFileViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CsvFileViewModel(application, initialFileUri, initialFileName) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}