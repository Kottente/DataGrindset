// In your UI package, e.g., ui/FileComponents.kt or directly in FileManagerScreen.kt

package com.example.datagrindset.ui // Or your appropriate package

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.* // For various icons
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.datagrindset.LocalAnalyzableFile
import com.example.datagrindset.ProcessingStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
@Composable
fun LocalFileItem(
    file: LocalAnalyzableFile,
    onProcess: (LocalAnalyzableFile) -> Unit,
    onDelete: (LocalAnalyzableFile) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // File Icon
            Icon(
                imageVector = getIconForMimeType(file.mimeType),
                contentDescription = "File type",
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(12.dp))

            // File Details Column
            Column(modifier = Modifier.weight(1f)) {
                Text(file.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Size: ${formatFileSize(file.size)}", style = MaterialTheme.typography.bodySmall)
                    Text("Modified: ${formatDate(file.dateModified)}", style = MaterialTheme.typography.bodySmall)
                }
                if (file.processingStatus != ProcessingStatus.NONE || file.processingResultSummary != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProcessingStatusIndicator(file.processingStatus)
                        file.processingResultSummary?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = FontStyle.Italic,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Action Buttons Column
            Column(horizontalAlignment = Alignment.End) {
                IconButton(onClick = { onProcess(file) }) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Process File", tint = MaterialTheme.colorScheme.secondary) // Or Icons.Filled.Settings for gears
                }
                IconButton(onClick = { onDelete(file) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete File", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun ProcessingStatusIndicator(status: ProcessingStatus) {
    val (icon, color) = when (status) {
        ProcessingStatus.PENDING -> Icons.Filled.HourglassEmpty to MaterialTheme.colorScheme.onSurfaceVariant
        ProcessingStatus.PROCESSING -> Icons.Filled.Sync to MaterialTheme.colorScheme.secondary // Using Sync for rotating/processing
        ProcessingStatus.SUCCESS -> Icons.Filled.CheckCircle to MaterialTheme.colorScheme.tertiary // Usually a green-ish color
        ProcessingStatus.FAILED -> Icons.Filled.Error to MaterialTheme.colorScheme.error
        ProcessingStatus.NONE -> return // Don't show an icon if not processed yet, or choose a default
    }
    Icon(imageVector = icon, contentDescription = "Status: $status", tint = color, modifier = Modifier.size(18.dp))
}


// Helper function to get an icon based on MIME type (basic example)
fun getIconForMimeType(mimeType: String?): ImageVector {
    return when (mimeType?.lowercase()) {
        "text/csv" -> Icons.Filled.Description // Or a more specific CSV icon if you have one
        "application/json" -> Icons.Filled.DataObject
        "application/pdf" -> Icons.Filled.PictureAsPdf
        "image/jpeg", "image/png" -> Icons.Filled.Image
        else -> Icons.AutoMirrored.Filled.InsertDriveFile // Generic file icon
    }
}

// Helper function to format file size
fun formatFileSize(sizeInBytes: Long): String {
    if (sizeInBytes < 1024) return "$sizeInBytes B"
    val kb = sizeInBytes / 1024
    if (kb < 1024) return "$kb KB"
    val mb = kb / 1024
    if (mb < 1024) return "$mb MB"
    val gb = mb / 1024
    return "$gb GB"
}

// Helper function to format date
fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) // e.g., May 20, 2025
    return sdf.format(Date(timestamp))
}
