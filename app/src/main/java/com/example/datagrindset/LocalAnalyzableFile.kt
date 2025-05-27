// Potentially in a new file, e.g., data/models.kt or similar

package com.example.datagrindset // Or your appropriate package

import android.net.Uri // For storing the file's location

// Enum to represent the processing status of a file


// Data class to represent a file selected by the user from local storage
data class LocalAnalyzableFile(
    val id: String, // A unique ID for app's internal tracking (e.g., UUID, or derived from URI)
    val uri: Uri,   // The content URI or file URI pointing to the local file
    val name: String,
    val size: Long, // Size in bytes
    val dateModified: Long, // Timestamp (e.g., from File.lastModified())
    val mimeType: String?, //  e.g., "text/csv", "application/json", etc.
    var processingStatus: ProcessingStatus = ProcessingStatus.NONE,
    var processingResultSummary: String? = null // Optional: a brief summary or error message after processing
)