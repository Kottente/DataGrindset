package com.example.datagrindset.viewmodel
import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.StateFlow

class LocalFileManagerViewModelFactory(
    private val application: Application,
    private val currentUserState: StateFlow<FirebaseUser?>
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LocalFileManagerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LocalFileManagerViewModel(application, currentUserState) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}