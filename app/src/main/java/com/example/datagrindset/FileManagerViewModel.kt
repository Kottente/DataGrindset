package com.example.datagrindset

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap // Required for MimeTypeMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow // Required for asSharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow // Required for MutableSharedFlow
import kotlinx.coroutines.launch

// Data class to hold intent details
data class FileIntentDetails(val uri: Uri, val mimeType: String?)

class FileManagerViewModel(private val context: Context) : ViewModel() { // Make context a property
    private val cloudStorage = CloudStorageService(context) // Pass context
    private val _files = MutableStateFlow<List<CloudFile>>(emptyList())
    val files: StateFlow<List<CloudFile>> = _files

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading

    // For general snackbar messages
    private val _snackbarMessage = MutableSharedFlow<String>() // Use SharedFlow for one-time events
    val snackbarMessage = _snackbarMessage.asSharedFlow()

    // For triggering file open intent
    private val _openFileEvent = MutableSharedFlow<FileIntentDetails>()
    val openFileEvent = _openFileEvent.asSharedFlow()

    private val _isLoadingFile = MutableStateFlow(false) // To show loading for file open
    val isLoadingFile: StateFlow<Boolean> = _isLoadingFile

    init {
        refreshFiles()
    }

    fun uploadFile(uri: Uri) {
        viewModelScope.launch {
            _isUploading.value = true
            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "file_${System.currentTimeMillis()}"
            val result = cloudStorage.uploadFile(uri, fileName)
            if (result != null) {
                _snackbarMessage.emit("Файл загружен: ${result.name}")
                refreshFiles()
            } else {
                _snackbarMessage.emit("Ошибка загрузки")
            }
            _isUploading.value = false
        }
    }

    fun deleteFile(file: CloudFile) {
        viewModelScope.launch {
            val success = cloudStorage.deleteFile(file.name)
            _snackbarMessage.emit(if (success) "Удалено: ${file.name}" else "Ошибка удаления")
            if (success) {
                refreshFiles()
            }
        }
    }

    fun refreshFiles() {
        viewModelScope.launch {
            try {
                _files.value = cloudStorage.listFiles()
            } catch (e: Exception) {
                _files.value = emptyList()
                _snackbarMessage.emit("Ошибка обновления списка файлов")
            }
        }
    }

    fun openFile(file: CloudFile) {
        viewModelScope.launch {
            _isLoadingFile.value = true
            val localFileUri = cloudStorage.downloadFileToCache(file.name)
            if (localFileUri != null) {
                val mimeType = getMimeType(localFileUri, file.name) // Use localFileUri or file.name for extension
                _openFileEvent.emit(FileIntentDetails(localFileUri, mimeType))
            } else {
                _snackbarMessage.emit("Не удалось открыть файл: ${file.name}")
            }
            _isLoadingFile.value = false
        }
    }

    private fun getMimeType(uri: Uri, fileName: String): String? {
        return if (uri.scheme == "content") {
            context.contentResolver.getType(uri)
        } else {
            val fileExtension = MimeTypeMap.getFileExtensionFromUrl(fileName) // Or parse from URI if it's a file URI
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension?.lowercase())
        }
    }

    // Removed clearSnackbar as snackbarMessage is now a SharedFlow and collected as a one-time event
}