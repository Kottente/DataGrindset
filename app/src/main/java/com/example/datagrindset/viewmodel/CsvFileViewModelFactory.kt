package com.example.datagrindset.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

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