package com.example.datagrindset.ui

import android.app.Application
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.navigation.NavController
import com.example.datagrindset.ui.theme.DataGrindsetTheme
import com.example.datagrindset.viewmodel.CsvFileViewModel
import kotlinx.coroutines.flow.MutableStateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvFileAnalysisScreen(
    navController: NavController,
    fileUri: Uri, // Kept for reference, ViewModel is primary data source
    viewModel: CsvFileViewModel
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
            viewModel.clearError() // Clear error after showing
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
            } else if (error != null && headers.isEmpty() && previewData.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Error loading file: $error",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                Text("CSV Analysis", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))
                InfoRow(label = "File Name:", value = fileName)
                InfoRow(label = "Total Rows (incl. header if parsed):", value = rowCount.toString())
                InfoRow(label = "Number of Columns (based on header):", value = columnCount.toString())

                Spacer(modifier = Modifier.height(16.dp))

                if (headers.isNotEmpty()) {
                    Text("Headers", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                    Surface( // Corrected: Wrap Row in Surface
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        tonalElevation = 1.dp, // Apply elevation to Surface
                        shape = MaterialTheme.shapes.small // Optional: apply shape
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            headers.forEach { header ->
                                TableCell(text = header, isHeader = true)
                            }
                        }
                    }
                    HorizontalDivider()
                } else if (!isLoading){
                    Text("No headers found or could not be parsed.", style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (previewData.isNotEmpty()) {
                    Text("Data Preview (First ${previewData.size} data rows)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(previewData) { rowData ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                val cellsToDisplay = if (headers.isNotEmpty()) rowData.take(headers.size) else rowData
                                cellsToDisplay.forEach { cellData ->
                                    TableCell(text = cellData)
                                }
                                if (headers.isNotEmpty() && rowData.size < headers.size) {
                                    repeat(headers.size - rowData.size) {
                                        TableCell(text = "")
                                    }
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                } else if (headers.isNotEmpty() && !isLoading) {
                    Text("No data rows found in the preview.", style = MaterialTheme.typography.bodyMedium)
                } else if (!isLoading && headers.isEmpty()) {
                    Text("No data to display. The file might be empty or incorrectly formatted.", style = MaterialTheme.typography.bodyMedium)
                }
                if (error != null && (headers.isNotEmpty() || previewData.isNotEmpty())) {
                    Text("Note: $error", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top=8.dp))
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
        Text(text = label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.4f))
        Text(text = value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun TableCell(text: String, isHeader: Boolean = false) {
    Text(
        text = text,
        style = if (isHeader) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
        fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .defaultMinSize(minWidth = 100.dp)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        maxLines = 1,
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