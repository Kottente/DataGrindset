package com.example.datagrindset.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.datagrindset.ProcessingStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- File Icon Mapper ---
fun getIconForMimeType(mimeType: String?): ImageVector {
    return when (mimeType?.lowercase()) {
        "text/plain", "text/markdown" -> Icons.Filled.Description
        "text/csv", "application/csv", "text/comma-separated-values" -> Icons.Filled.TableView
        "application/pdf" -> Icons.Filled.PictureAsPdf
        "image/jpeg", "image/png", "image/gif", "image/webp" -> Icons.Filled.Image
        "video/mp4", "video/webm", "video/avi" -> Icons.Filled.Videocam
        "audio/mpeg", "audio/ogg", "audio/wav" -> Icons.Filled.Audiotrack
        "application/zip", "application/rar" -> Icons.Filled.Archive
        "application/json" -> Icons.Filled.DataObject
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
}

// Helper function to format file size
fun formatFileSize(sizeBytes: Long): String {
    if (sizeBytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(sizeBytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", sizeBytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

// Helper function to format date
fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@Composable
fun ProcessingStatusIndicator(status: ProcessingStatus) {
    val icon = when (status) {
        ProcessingStatus.PENDING -> Icons.Filled.HourglassEmpty
        ProcessingStatus.PROCESSING -> Icons.Filled.Sync
        ProcessingStatus.SUCCESS -> Icons.Filled.CheckCircle
        ProcessingStatus.FAILED, ProcessingStatus.UNSUPPORTED -> Icons.Filled.Error
        ProcessingStatus.NONE -> return // Don't show an icon if no status
        ProcessingStatus.ERROR -> return
        ProcessingStatus.FAILURE -> TODO()
    }
    val tint = when (status) {
        ProcessingStatus.SUCCESS -> MaterialTheme.colorScheme.primary
        ProcessingStatus.FAILED, ProcessingStatus.UNSUPPORTED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Icon(
        imageVector = icon,
        contentDescription = "Status: $status",
        tint = tint,
        modifier = Modifier.size(18.dp)
    )
}

@Composable
fun ProcessingStatusIndicator(statusPair: Pair<ProcessingStatus, String?>?) {
    statusPair?.let { (status, message) ->
        val (statusIcon, statusColor, statusText) = when (status) {
            ProcessingStatus.PENDING -> Triple(Icons.Filled.Refresh, MaterialTheme.colorScheme.outline, "Pending: ${message ?: ""}")
            ProcessingStatus.SUCCESS -> Triple(Icons.Filled.CheckCircle, MaterialTheme.colorScheme.primary, "Ready: ${message ?: ""}")
            ProcessingStatus.ERROR -> Triple(Icons.Filled.Error, MaterialTheme.colorScheme.error, "Error: ${message ?: ""}")
            ProcessingStatus.UNSUPPORTED -> Triple(Icons.AutoMirrored.Filled.NoteAdd, MaterialTheme.colorScheme.secondary, "Unsupported: ${message ?: ""}")
            ProcessingStatus.NONE -> return
            ProcessingStatus.PROCESSING -> Triple(Icons.Filled.Sync, MaterialTheme.colorScheme.outline, "Processing: ${message ?: ""}")
            ProcessingStatus.FAILED -> Triple(Icons.Filled.Error, MaterialTheme.colorScheme.error, "Failed: ${message ?: ""}")
            ProcessingStatus.FAILURE -> TODO()
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Icon(statusIcon, contentDescription = "Status", tint = statusColor, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                statusText,
                style = MaterialTheme.typography.labelSmall,
                color = statusColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}