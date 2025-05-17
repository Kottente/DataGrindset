package com.example.datagrindset

import android.content.Context
import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

class CloudStorageService(context: Context) {
    private val storage = FirebaseStorage.getInstance().reference.child("userfiles")

    suspend fun uploadFile(uri: Uri, fileName: String): CloudFile? {
        val ref = storage.child(fileName)
        ref.putFile(uri).await()
        val url = ref.downloadUrl.await().toString()
        return CloudFile(fileName, url)
    }

    suspend fun listFiles(): List<CloudFile> {
        val result = storage.listAll().await()
        return result.items.map {
            val url = it.downloadUrl.await().toString()
            CloudFile(it.name, url)
        }
    }

    suspend fun deleteFile(fileName: String): Boolean {
        return try {
            storage.child(fileName).delete().await()
            true
        } catch (e: Exception) {
            false
        }
    }
}