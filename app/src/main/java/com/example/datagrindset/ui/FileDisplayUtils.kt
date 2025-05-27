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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.datagrindset.ProcessingStatus // Make sure this import is correct
import com.example.datagrindset.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- File Size Formatter (Extension Function) ---
//fun getIconForMimeType(mimeType: String?): ImageVector {
//    return when (mimeType?.lowercase()) {
//        "text/plain", "text/markdown" -> Icons.AutoMirrored.Filled.InsertDriveFile // Using AutoMirrored for LTR/RTL
//        "text/csv" -> Icons.Filled.DataObject // Or a more specific CSV icon if you have one
//        "application/pdf" -> Icons.Filled.PictureAsPdf
//        "image/jpeg", "image/png", "image/gif", "image/webp" -> Icons.Filled.Image
//        // Add more specific icons as needed
//        else -> Icons.AutoMirrored.Filled.InsertDriveFile // Default icon
//    }
//}
//
//// Helper function to format file size
//fun formatFileSize(sizeBytes: Long): String {
//    if (sizeBytes <= 0) return "0 B"
//    val units = arrayOf("B", "KB", "MB", "GB", "TB")
//    val digitGroups = (Math.log10(sizeBytes.toDouble()) / Math.log10(1024.0)).toInt()
//    return String.format("%.1f %s", sizeBytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
//}
//
//// Helper function to format date
//fun formatDate(timestamp: Long): String {
//    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
//    return sdf.format(Date(timestamp))
//}
//
//
//@Composable
//fun ProcessingStatusIndicator(status: ProcessingStatus) {
//    val statusText = stringResource(R.string.lfm_processing_status_desc, status.name)
//    val iconVectorAndTint = when (status) {
//        ProcessingStatus.PENDING -> Icons.Filled.HourglassEmpty to MaterialTheme.colorScheme.onSurfaceVariant
//        ProcessingStatus.PROCESSING -> Icons.Filled.Sync to MaterialTheme.colorScheme.onSurfaceVariant
//        ProcessingStatus.SUCCESS -> Icons.Filled.CheckCircle to MaterialTheme.colorScheme.primary
//        ProcessingStatus.FAILURE -> Icons.Filled.Error to MaterialTheme.colorScheme.error
//        ProcessingStatus.UNSUPPORTED -> Icons.Filled.Error to MaterialTheme.colorScheme.error
//        ProcessingStatus.ERROR -> Icons.Filled.Error to MaterialTheme.colorScheme.error
//        ProcessingStatus.FAILED -> Icons.Filled.Error to MaterialTheme.colorScheme.error
//        ProcessingStatus.NONE -> null
//    }
//
//    iconVectorAndTint?.let { (icon, tint) ->
//        Icon(imageVector = icon, contentDescription = statusText, tint = tint, modifier = Modifier.size(18.dp))
//    }
//}

fun getIconForMimeType(mimeType: String?): ImageVector { return when (mimeType?.lowercase(Locale.ROOT)) { "text/plain", "text/markdown" -> Icons.AutoMirrored.Filled.InsertDriveFile; "text/csv" -> Icons.Filled.DataObject; "application/pdf" -> Icons.Filled.PictureAsPdf; "image/jpeg", "image/png", "image/gif", "image/webp" -> Icons.Filled.Image; else -> Icons.AutoMirrored.Filled.InsertDriveFile } }
fun formatFileSize(sizeBytes: Long): String { if (sizeBytes <= 0) return "0 B"; val units = arrayOf("B", "KB", "MB", "GB", "TB"); val digitGroups = (Math.log10(sizeBytes.toDouble()) / Math.log10(1024.0)).toInt(); return String.format(Locale.US, "%.1f %s", sizeBytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups]) }
fun formatDate(timestamp: Long): String { val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()); return sdf.format(Date(timestamp)) }
@Composable
fun ProcessingStatusIndicator(status: ProcessingStatus) { val statusText = stringResource(R.string.lfm_processing_status_desc, status.name); val iconVectorAndTint = when (status) { ProcessingStatus.PENDING -> Icons.Filled.HourglassEmpty to MaterialTheme.colorScheme.onSurfaceVariant; ProcessingStatus.PROCESSING -> Icons.Filled.Sync to MaterialTheme.colorScheme.onSurfaceVariant; ProcessingStatus.SUCCESS -> Icons.Filled.CheckCircle to MaterialTheme.colorScheme.primary; ProcessingStatus.FAILURE, ProcessingStatus.UNSUPPORTED, ProcessingStatus.ERROR, ProcessingStatus.FAILED -> Icons.Filled.Error to MaterialTheme.colorScheme.error; ProcessingStatus.NONE -> null }; iconVectorAndTint?.let { (icon, tint) -> Icon(imageVector = icon, contentDescription = statusText, tint = tint, modifier = Modifier.size(18.dp)) } }
//fun Long.formatFileSizes(): String {
//    if (this < 0) return "N/A"
//    if (this < 1024) return "$this B"
//    val kb = this / 1024.0
//    if (kb < 1024) return String.format(Locale.US, "%.2f KB", kb)
//    val mb = kb / 1024.0
//    if (mb < 1024) return String.format(Locale.US, "%.2f MB", mb)
//    val gb = mb / 1024.0
//    return String.format(Locale.US, "%.2f GB", gb)
//}
//fun formatFileSize(sizeInBytes: Long): String {
//        if (sizeInBytes < 1024) return "$sizeInBytes B"
//    val kb = sizeInBytes / 1024
//    if (kb < 1024) return "$kb KB"
//    val mb = kb / 1024
//    if (mb < 1024) return "$mb MB"
//    val gb = mb / 1024
//    return "$gb GB"
//}
//// --- Date Formatter ---
//@Composable
//fun formatDate(timestamp: Long): String {
//    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }
//    return dateFormat.format(Date(timestamp))
//}

//fun Long.formatFileSize(): String {
//    if (this < 0) return "N/A"
//    if (this < 1024) return "$this B"
//    val kb = this / 1024.0
//    if (kb < 1024) return String.format(Locale.US, "%.2f KB", kb)
//    val mb = kb / 1024.0
//    if (mb < 1024) return String.format(Locale.US, "%.2f MB", mb)
//    val gb = mb / 1024.0
//    return String.format(Locale.US, "%.2f GB", gb)
//}
// --- MimeType to Icon Mapper ---
//fun getIconForMimeType(mimeType: String?): ImageVector {
//    return when (mimeType?.lowercase(Locale.getDefault())) {
//        "text/plain", "text/markdown" -> Icons.Filled.Description
//        "text/csv", "application/csv", "text/comma-separated-values" -> Icons.Filled.TableView
//        "application/pdf" -> Icons.Filled.PictureAsPdf
//        "image/jpeg", "image/png", "image/gif", "image/webp" -> Icons.Filled.Image
//        "video/mp4", "video/webm" -> Icons.Filled.Videocam
//        "audio/mpeg", "audio/ogg", "audio/wav" -> Icons.Filled.Audiotrack
//        "application/zip", "application/rar" -> Icons.Filled.Archive
//        else -> Icons.AutoMirrored.Filled.InsertDriveFile // Generic file icon
//    }
//}

// --- Processing Status Indicator Composable ---
//@Composable
//fun ProcessingStatusIndicator(statusPair: Pair<ProcessingStatus, String?>?) {
//    statusPair?.let { (status, message) ->
//        val (statusIcon, statusColor, statusText) = when (status) {
//            ProcessingStatus.PENDING -> Triple(Icons.Filled.Refresh, MaterialTheme.colorScheme.outline, "Pending: ${message ?: ""}")
//            ProcessingStatus.SUCCESS -> Triple(Icons.Filled.CheckCircle, MaterialTheme.colorScheme.primary, "Ready: ${message ?: ""}")
//            ProcessingStatus.ERROR -> Triple(Icons.Filled.Error, MaterialTheme.colorScheme.error, "Error: ${message ?: ""}")
//            ProcessingStatus.UNSUPPORTED -> Triple(Icons.AutoMirrored.Filled.NoteAdd, MaterialTheme.colorScheme.secondary, "Unsupported: ${message ?: ""}")
//            ProcessingStatus.NONE -> TODO()
//            ProcessingStatus.PROCESSING -> TODO()
//            ProcessingStatus.FAILED -> TODO()
//            ProcessingStatus.FAILURE -> TODO()
//        }
//        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
//            Icon(statusIcon, contentDescription = "Status", tint = statusColor, modifier = Modifier.size(16.dp))
//            Spacer(modifier = Modifier.width(4.dp))
//            Text(
//                statusText,
//                style = MaterialTheme.typography.labelSmall,
//                color = statusColor,
//                maxLines = 1,
//                overflow = TextOverflow.Ellipsis
//            )
//        }
//    }
//}

//@Composable
//fun ProcessingStatusIndicator(status: ProcessingStatus) {
//    val (icon, color) = when (status) {
//        ProcessingStatus.PENDING -> Icons.Filled.HourglassEmpty to MaterialTheme.colorScheme.onSurfaceVariant
//        ProcessingStatus.PROCESSING -> Icons.Filled.Sync to MaterialTheme.colorScheme.secondary // Using Sync for rotating/processing
//        ProcessingStatus.SUCCESS -> Icons.Filled.CheckCircle to MaterialTheme.colorScheme.tertiary // Usually a green-ish color
//        ProcessingStatus.FAILED -> Icons.Filled.Error to MaterialTheme.colorScheme.error
//        ProcessingStatus.NONE -> return // Don't show an icon if not processed yet, or choose a default
//        ProcessingStatus.UNSUPPORTED -> TODO()
//        ProcessingStatus.ERROR -> TODO()
//    }
//    Icon(imageVector = icon, contentDescription = "Status: $status", tint = color, modifier = Modifier.size(18.dp))
//}