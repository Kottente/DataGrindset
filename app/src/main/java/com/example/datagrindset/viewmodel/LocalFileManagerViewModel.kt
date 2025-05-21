package com.example.datagrindset.viewmodel

// In a new file, e.g., viewmodel/LocalFileManagerViewModel.kt

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.datagrindset.LocalAnalyzableFile
import com.example.datagrindset.ProcessingStatus
import com.example.datagrindset.ui.SortOption // Assuming SortOption is in ui package
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class LocalFileManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val _allFiles = MutableStateFlow<List<LocalAnalyzableFile>>(emptyList())

    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.BY_DATE_DESC)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    // This will hold the filtered and sorted list for the UI
    val files: StateFlow<List<LocalAnalyzableFile>> =
        combine(_allFiles, _searchText, _sortOption) { files, text, sort ->
            val filteredList = if (text.isBlank()) {
                files
            } else {
                files.filter { it.name.contains(text, ignoreCase = true) }
            }
            // Apply sorting (simplified example)
            when (sort) {
                SortOption.BY_NAME_ASC -> filteredList.sortedBy { it.name.lowercase() }
                SortOption.BY_NAME_DESC -> filteredList.sortedByDescending { it.name.lowercase() }
                SortOption.BY_DATE_ASC -> filteredList.sortedBy { it.dateModified }
                SortOption.BY_DATE_DESC -> filteredList.sortedByDescending { it.dateModified }
                SortOption.BY_SIZE_ASC -> filteredList.sortedBy { it.size }
                SortOption.BY_SIZE_DESC -> filteredList.sortedByDescending { it.size }
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // --- Public Methods for UI to call ---

    fun onSearchTextChanged(text: String) {
        _searchText.value = text
    }

    fun onSortOptionSelected(sort: SortOption) {
        _sortOption.value = sort
    }

    fun addFile(uri: Uri) {
        viewModelScope.launch {
            // Extract metadata from URI
            val contentResolver = getApplication<Application>().contentResolver
            val name = getFileName(contentResolver, uri) ?: "Unknown File"
            val size = getFileSize(contentResolver, uri) ?: 0L
            val lastModified = System.currentTimeMillis() // Placeholder, ideally from URI if possible or when app "adds" it
            val mimeType = contentResolver.getType(uri)

            val newFile = LocalAnalyzableFile(
                id = UUID.randomUUID().toString(),
                uri = uri,
                name = name,
                size = size,
                dateModified = lastModified, // Or use actual file modified date if accessible
                mimeType = mimeType
            )
            _allFiles.value = _allFiles.value + newFile
            // TODO: Persist this file list (e.g., in Room database or SharedPreferences for URIs)
        }
    }

    fun deleteFile(fileToDelete: LocalAnalyzableFile) {
        _allFiles.value = _allFiles.value.filterNot { it.id == fileToDelete.id }
        // TODO: Handle actual file deletion if the app copied the file, or just remove reference
        // TODO: Revoke URI permissions if necessary
    }

    fun processFile(fileToProcess: LocalAnalyzableFile) {
        // 1. Update status to PENDING/PROCESSING
        updateFileStatus(fileToProcess.id, ProcessingStatus.PROCESSING, "Starting analysis...")
        viewModelScope.launch {
            // TODO: Implement actual file processing logic (this will be a longer task)
            // For now, simulate processing
            kotlinx.coroutines.delay(3000) // Simulate work
            val success = true // Simulate outcome
            if (success) {
                updateFileStatus(fileToProcess.id, ProcessingStatus.SUCCESS, "Analysis complete.")
            } else {
                updateFileStatus(fileToProcess.id, ProcessingStatus.FAILED, "Analysis failed: Error details.")
            }
        }
    }

    private fun updateFileStatus(fileId: String, status: ProcessingStatus, summary: String? = null) {
        _allFiles.value = _allFiles.value.map {
            if (it.id == fileId) it.copy(processingStatus = status, processingResultSummary = summary) else it
        }
    }

    // --- Helper methods to get file metadata ---
    private fun getFileName(contentResolver: ContentResolver, uri: Uri): String? {
        var name: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    name = cursor.getString(nameIndex)
                }
            }
        }
        return name
    }

    private fun getFileSize(contentResolver: ContentResolver, uri: Uri): Long? {
        var size: Long? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        }
        return size
    }

    // TODO: Load initial files from persistent storage on init
}