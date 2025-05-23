package com.example.datagrindset.ui

import android.annotation.SuppressLint
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
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.datagrindset.ui.theme.DataGrindsetTheme
import com.example.datagrindset.viewmodel.CsvFileViewModel
import com.example.datagrindset.viewmodel.CsvFileViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvFileAnalysisScreen(
    navController: NavController,
    fileUri: Uri,
    viewModel: CsvFileViewModel // ViewModel is passed in

) {
    val context = LocalContext.current.applicationContext
//    val viewModel: CsvFileViewModel = viewModel(
//        factory = CsvFileViewModelFactory(context as android.app.Application, fileUri)
//    )

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
            } else if (error != null && headers.isEmpty()) { // Show error prominently if loading failed critically
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Error: $error", color = MaterialTheme.colorScheme.error)
                }
            } else {
                // File Info
                Text("CSV Analysis", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))
                InfoRow(label = "File Name:", value = fileName)
                InfoRow(label = "Total Rows (incl. header):", value = rowCount.toString())
                InfoRow(label = "Number of Columns:", value = columnCount.toString())

                Spacer(modifier = Modifier.height(16.dp))

                // Headers
                if (headers.isNotEmpty()) {
                    Text("Headers", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()) // Horizontal scroll for headers
                            .padding(bottom = 8.dp)
                    ) {
                        headers.forEach { header ->
                            TableCell(text = header, isHeader = true)
                        }
                    }
                    HorizontalDivider()
                } else {
                    Text("No headers found or could not be parsed.", style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Data Preview
                if (previewData.isNotEmpty()) {
                    Text("Data Preview (First ${previewData.size} rows)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(previewData) { rowData ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()) // Horizontal scroll for data rows
                            ) {
                                rowData.forEach { cellData ->
                                    TableCell(text = cellData)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                } else if (headers.isNotEmpty()) { // Headers were found, but no data rows
                    Text("No data rows found in the preview.", style = MaterialTheme.typography.bodyMedium)
                } else if (!isLoading) { // No headers and no data, and not loading
                    Text("No data to display. The file might be empty or incorrectly formatted.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.4f))
        Text(text = value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun TableCell(text: String, isHeader: Boolean = false, weight: Float = 1f) {
    Text(
        text = text,
        style = if (isHeader) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
        fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 12.dp)
            .defaultMinSize(minWidth = 100.dp), // Ensure columns have a minimum width
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@SuppressLint("ViewModelConstructorInComposable")
@Preview(showBackground = true)
@Composable
fun CsvFileAnalysisScreenPreview() {
    DataGrindsetTheme {
        val dummyUri = "content://com.example.datagrindset.provider/dummy.csv".toUri()
        val context = LocalContext.current
        val dummyViewModel = CsvFileViewModel(
            application = context.applicationContext as Application,
            initialFileUri = dummyUri,
            initialFileName = "dummy.csv" // Provide the filename
        )
        CsvFileAnalysisScreen(
            navController = NavController(context),
            fileUri = dummyUri,
            viewModel = dummyViewModel // Pass the dummy ViewModel
        )
    }
}