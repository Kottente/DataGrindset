package com.example.datagrindset.viewmodel

import android.app.Application
import android.content.ContentValues.TAG
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import android.provider.OpenableColumns // For getting display name

class TxtFileViewModel(application: Application, private val fileUri: Uri) : AndroidViewModel(application) {

    private val _fileName = MutableStateFlow<String>("Loading...")
    val fileName: StateFlow<String> = _fileName.asStateFlow()

    private val _fileContent = MutableStateFlow<String?>(null)
    val fileContent: StateFlow<String?> = _fileContent.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // More states for text analysis features will be added here later
    // e.g., fontSize, editMode, searchText, summary, etc.
    companion object {
        private const val TAG = "TxtFileViewModel"
    }

    init {
        Log.d(TAG, "Initializing for URI: $fileUri")
        // Check persisted permissions (useful for debugging)
        val context = getApplication<Application>()
        Log.d(TAG, "Current persisted URI permissions:")
        context.contentResolver.persistedUriPermissions.forEach {
            Log.d(TAG, " - URI: ${it.uri}, Read: ${it.isReadPermission}, Write: ${it.isWritePermission}")
            // Check if the fileUri's parent tree is among these
            if (fileUri.toString().startsWith(it.uri.toString()) && it.isReadPermission) {
                Log.i(TAG, "File URI $fileUri appears to be a child of a permitted tree: ${it.uri}")
            }
        }
        loadFileNameDirectly()
        loadFileContentDirectly()
        //loadFileName()
        //loadFileContent()
    }

    private fun loadFileName() {
        viewModelScope.launch {
            try {
                val documentFile = DocumentFile.fromSingleUri(getApplication(), fileUri)
                _fileName.value = documentFile?.name ?: "Unknown File"
                Log.d(TAG, "File name loaded: ${_fileName.value}")
            } catch (e: Exception) {
                _fileName.value = "Error getting name"
                Log.e(TAG, "Error loading file name for $fileUri", e)
            }
        }
    }

    private fun loadFileNameDirectly() {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val cursor = context.contentResolver.query(fileUri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val displayNameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (displayNameIndex != -1) {
                            _fileName.value = it.getString(displayNameIndex)
                            Log.d(TAG, "File name loaded directly: ${_fileName.value}")
                            return@launch
                        }
                    }
                }
                // Fallback if query fails or no display name
                _fileName.value = fileUri.lastPathSegment ?: "Unknown File"
                Log.w(TAG, "Could not get display name from ContentResolver, used lastPathSegment: ${_fileName.value}")

            } catch (e: SecurityException) {
                _fileName.value = "Error (Perm)" // Short indicator for UI
                Log.e(TAG, "SecurityException getting file name for $fileUri", e)
                // Attempt to load content anyway, as sometimes metadata query fails but open works
            } catch (e: Exception) {
                _fileName.value = "Error (Name)"
                Log.e(TAG, "Error loading file name for $fileUri", e)
            }
        }
    }

    private fun loadFileContentDirectly() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            Log.d(TAG, "Attempting to load content directly for URI: $fileUri")

            try {
                val context = getApplication<Application>()
                // Directly try to open the input stream.
                // This is the most direct way and often works if tree permissions are active.
                context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        _fileContent.value = reader.readText()
                        Log.i(TAG, "File content loaded successfully for URI: $fileUri")
                    }
                } ?: run {
                    _error.value = "Failed to open file stream (ContentResolver returned null)."
                    Log.w(TAG, "contentResolver.openInputStream returned null for URI: $fileUri")
                }

            } catch (e: SecurityException) {
                _fileContent.value = null
                _error.value = "Permission Denied. Ensure app has access to the folder."
                Log.e(TAG, "SecurityException opening file stream for $fileUri. This indicates the persisted permission for the root tree is not being applied, or the URI is problematic.", e)
            } catch (e: Exception) {
                _fileContent.value = null
                _error.value = "Error loading file: ${e.localizedMessage}"
                Log.e(TAG, "Generic exception opening file stream for $fileUri", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadFileContent() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            Log.d(TAG, "Attempting to load content for URI: $fileUri")

            try {
                val context = getApplication<Application>()

                // Log currently persisted URI permissions for debugging
                Log.d(TAG, "Current persisted URI permissions:")
                val persistedUris = context.contentResolver.persistedUriPermissions
                if (persistedUris.isEmpty()) {
                    Log.w(TAG, "No persisted URI permissions found.")
                }
                persistedUris.forEach {
                    Log.d(TAG, " - URI: ${it.uri}, Read: ${it.isReadPermission}, Write: ${it.isWritePermission}, Persisted Time: ${it.persistedTime}")
                }

                // Try to create a DocumentFile from the URI to check its properties
                val documentFile = DocumentFile.fromSingleUri(context, fileUri)

                if (documentFile == null) {
                    _error.value = "File URI is invalid or DocumentFile cannot be created."
                    _fileContent.value = null
                    Log.w(TAG, "DocumentFile.fromSingleUri returned null for URI: $fileUri")
                    _isLoading.value = false
                    return@launch
                }

                if (!documentFile.exists()) {
                    _error.value = "File does not exist at URI: $fileUri"
                    _fileContent.value = null
                    Log.w(TAG, "documentFile.exists() is false for URI: $fileUri")
                    _isLoading.value = false
                    return@launch
                }

                Log.d(TAG, "DocumentFile details: Name: ${documentFile.name}, CanRead: ${documentFile.canRead()}, CanWrite: ${documentFile.canWrite()}, Type: ${documentFile.type}")

                if (!documentFile.canRead()) {
                    _error.value = "Cannot read file (documentFile.canRead() is false). This indicates a permission issue with the URI itself or its tree."
                    _fileContent.value = null
                    Log.w(TAG, "documentFile.canRead() is false for URI: $fileUri. This is the primary indicator of the permission problem.")
                    _isLoading.value = false
                    return@launch
                }

                // If documentFile.canRead() is true, then openInputStream should work.
                // The SecurityException happens here if the ContentResolver doesn't honor the permission.
                context.contentResolver.openInputStream(documentFile.uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        _fileContent.value = reader.readText()
                        Log.d(TAG, "File content loaded successfully for URI: $fileUri")
                    }
                } ?: run {
                    _error.value = "Failed to open file stream (contentResolver.openInputStream returned null)."
                    Log.w(TAG, "contentResolver.openInputStream returned null for URI: ${documentFile.uri}")
                }

            } catch (e: SecurityException) {
                _fileContent.value = null
                // This is the error you are seeing.
                _error.value = "SecurityException: ${e.localizedMessage}. Check logs for persisted permissions."
                Log.e(TAG, "SecurityException opening file: $fileUri. This usually means the persisted permission for the root tree is not being applied to this child URI.", e)
            } catch (e: Exception) {
                _fileContent.value = null
                _error.value = "Error loading file: ${e.localizedMessage}"
                Log.e(TAG, "Generic exception opening file: $fileUri", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Placeholder for future actions
    fun generateSummary() { /* TODO */ }
    fun increaseFontSize() { /* TODO */ }
    fun decreaseFontSize() { /* TODO */ }
    fun toggleEditMode() { /* TODO */ }
    fun searchInText(query: String) { /* TODO */ }
    fun applyEdits(newContent: String) { /* TODO - this will also need write permissions */ }

}

// Factory for TxtFileViewModel
class TxtFileViewModelFactory(
    private val application: Application,
    private val fileUri: Uri
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TxtFileViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TxtFileViewModel(application, fileUri) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}