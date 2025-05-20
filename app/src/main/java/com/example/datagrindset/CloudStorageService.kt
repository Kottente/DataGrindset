package com.example.datagrindset

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider // Required for FileProvider
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.File // Required for File operations

class CloudStorageService(private val context: Context) { // Make context a property
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
            // Log error e.g., Log.e("CloudStorageService", "Error deleting file", e)
            false
        }
    }

    // New method to download file to cache and get a content URI
    suspend fun downloadFileToCache(fileName: String): Uri? {
        return try {
            val fileRef = storage.child(fileName)
            // Create a local file in the app's cache directory
            val localFile = File(context.cacheDir, fileName)

            // Download the file from Firebase Storage to the local file
            fileRef.getFile(localFile).await()

            // Get a content URI using FileProvider
            // IMPORTANT: Replace "com.example.datagrindset.fileprovider" with your actual application ID + ".fileprovider"
            // This authority string MUST match what you define in your AndroidManifest.xml
            val authority = "${context.packageName}.fileprovider"
            FileProvider.getUriForFile(context, authority, localFile)
        } catch (e: Exception) {
            // Log error e.g., Log.e("CloudStorageService", "Error downloading file to cache", e)
            null
        }
    }
}