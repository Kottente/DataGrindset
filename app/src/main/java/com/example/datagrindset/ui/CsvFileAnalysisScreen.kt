package com.example.datagrindset.ui

import android.app.Application
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.datagrindset.ui.theme.DataGrindsetTheme
import com.example.datagrindset.viewmodel.CsvFileViewModel
import com.example.datagrindset.viewmodel.CsvFileViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvFileAnalysisScreen(
    navController: NavController,
    fileUri: Uri, // Kept for reference, ViewModel is primary source
    viewModel: CsvFileViewModel // Passed in
) {
    val fileName by viewModel.fileName.collectAsState()
    val rowCount by viewModel.rowCount.collectAsState()
    val columnCount by viewModel.columnCount.collectAsState()
    val headers by viewModel.headers.collectAsState()
    val previewData by viewModel.previewData.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Long
            )
            viewModel.clearError() // Optional: clear error after showing
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
                // Add actions here if needed (e.g., refresh, share)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (error != null && headers.isEmpty() && previewData.isEmpty()) { // Show error prominently if loading failed
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Error: $error",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                // File Info Section
                Text("File Information", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                InfoRow(label = "Name:", value = fileName)
                InfoRow(label = "Total Rows (incl. header if present):", value = rowCount.toString())
                InfoRow(label = "Columns:", value = columnCount.toString())

                Spacer(modifier = Modifier.height(16.dp))

                // Data Table Section
                if (headers.isNotEmpty() || previewData.isNotEmpty()) {
                    Text("Data Preview", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                    Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            if (headers.isNotEmpty()) {
                                item {
                                    Row(Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
                                        headers.forEach { header ->
                                            TableCell(text = header, isHeader = true)
                                        }
                                    }
                                    HorizontalDivider()
                                }
                            }
                            itemsIndexed(previewData) { index, rowData ->
                                Row {
                                    // Ensure rowData matches header size or pad if necessary
                                    val displayRow = rowData.take(headers.size).toMutableList()
                                    while (displayRow.size < headers.size) {
                                        displayRow.add("") // Pad with empty strings if row is shorter
                                    }
                                    displayRow.forEach { cellData ->
                                        TableCell(text = cellData)
                                    }
                                }
                                if (index < previewData.size - 1) {
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                } else if (!isLoading) { // Only show if not loading and no data/headers
                    Text(
                        "No data or headers to display. The file might be empty or not a valid CSV.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(0.4f))
        Text(text = value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun TableCell(text: String, isHeader: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = if (isHeader) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
        fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
        modifier = modifier
            .defaultMinSize(minWidth = 100.dp) // Give cells a minimum width
            .padding(horizontal = 8.dp, vertical = 12.dp),
        maxLines = 1, // Prevent text wrapping for simplicity in table view
        overflow = TextOverflow.Ellipsis
    )
}


@Preview(showBackground = true)
@Composable
fun CsvFileAnalysisScreenPreview() {
    DataGrindsetTheme {
        val dummyUri = "content://com.example.datagrindset.provider/dummy.csv".toUri()
        val context = LocalContext.current
        val dummyViewModel = CsvFileViewModel(
            application = context.applicationContext as Application,
            initialFileUri = dummyUri,
            initialFileName = "dummy.csv"
        )
        LaunchedEffect(Unit) {
            (dummyViewModel.headers as MutableStateFlow).value = listOf("ID", "Name", "Value")
            (dummyViewModel.previewData as MutableStateFlow).value = listOf(
                listOf("1", "Alice", "100"),
                listOf("2", "Bob", "200")
            )
            (dummyViewModel.rowCount as MutableStateFlow).value = 3
            (dummyViewModel.columnCount as MutableStateFlow).value = 3
        }

        CsvFileAnalysisScreen(
            navController = NavController(context),
            fileUri = dummyUri,
            viewModel = dummyViewModel
        )
    }
}