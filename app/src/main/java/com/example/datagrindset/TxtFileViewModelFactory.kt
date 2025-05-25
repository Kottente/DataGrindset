package com.example.datagrindset

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.datagrindset.viewmodel.TxtFileViewModel

//class TxtFileViewModelFactory(
//    private val application: Application,
//    private val initialFileUri: Uri,
//    private val initialFileName: String
//) : ViewModelProvider.Factory {
//    override fun <T : ViewModel> create(modelClass: Class<T>): T {
//        if (modelClass.isAssignableFrom(TxtFileViewModel::class.java)) {
//            @Suppress("UNCHECKED_CAST")
//            return TxtFileViewModel(application, initialFileUri, initialFileName) as T
//        }
//        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
//    }
//}
class TxtFileViewModelFactory(
    private val application: Application,
    private val initialFileUri: Uri
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TxtFileViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TxtFileViewModel(application, initialFileUri) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}