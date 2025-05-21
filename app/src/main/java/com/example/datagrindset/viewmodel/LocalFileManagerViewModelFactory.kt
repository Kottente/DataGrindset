package com.example.datagrindset.viewmodel
import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class LocalFileManagerViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LocalFileManagerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LocalFileManagerViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}