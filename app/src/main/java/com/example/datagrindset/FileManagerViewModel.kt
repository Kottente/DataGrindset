package com.example.datagrindset


import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class FileManagerViewModel(context: Context) : ViewModel() {
    private val cloudStorage = CloudStorageService(context)
    private val _files = MutableStateFlow<List<CloudFile>>(emptyList())
    val files: StateFlow<List<CloudFile>> = _files

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading

    var snackbarMessage: String? = null
        private set

    init {
        refreshFiles()
    }

    fun uploadFile(uri: Uri) {
        viewModelScope.launch {
            _isUploading.value = true
            try {
                val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "file_${System.currentTimeMillis()}"
                cloudStorage.uploadFile(uri, fileName)
                snackbarMessage = "Файл загружен: $fileName"
            } catch (e: Exception) {
                snackbarMessage = "Ошибка загрузки"
            }
            refreshFiles()
            _isUploading.value = false
        }
    }

    fun deleteFile(file: CloudFile) {
        viewModelScope.launch {
            val success = cloudStorage.deleteFile(file.name)
            snackbarMessage = if (success) "Удалено: ${file.name}" else "Ошибка удаления"
            refreshFiles()
        }
    }

    fun refreshFiles() {
        viewModelScope.launch {
            try {
                _files.value = cloudStorage.listFiles()
            } catch (e: Exception) {
                _files.value = emptyList()
            }
        }
    }

    fun openFile(file: CloudFile) {
        snackbarMessage = "Открытие файла: ${file.name}"
    }

    fun clearSnackbar() {
        snackbarMessage = null
    }
}