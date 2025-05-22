package com.example.datagrindset.ui

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    fileUri: Uri
) {
    val context = LocalContext.current.applicationContext
    val viewModel: CsvFileViewModel = viewModel(
        factory = CsvFileViewModelFactory(context as android.app.Application, fileUri)
    )

    val fileName by viewModel.fileName.collectAsState()
    val headers by viewModel.headers.collectAsState()
    val rows by viewModel.rows.collectAsState()
    val rowCount by viewModel.rowCount.collectAsState()
    val columnCount by viewModel.columnCount.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    val horizontalScrollState = rememberScrollState()

    Scaffold(
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
                .padding(horizontal = 8.dp)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (error != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Error: $error", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                }
            } else {
                // Basic Info
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Text("Rows: $rowCount", style = MaterialTheme.typography.bodyMedium)
                    Text("Columns: $columnCount", style = MaterialTheme.typography.bodyMedium)
                }
                HorizontalDivider()

                // CSV Table
                if (headers.isNotEmpty() || rows.isNotEmpty()) {
                    Column(modifier = Modifier.horizontalScroll(horizontalScrollState)) {
                        // Header Row
                        if (headers.isNotEmpty()) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                headers.forEach { header ->
                                    TableCell(
                                        text = header,
                                        isHeader = true,
                                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp) // Equal weight for now
                                    )
                                }
                            }
                            HorizontalDivider()
                        }

                        // Data Rows
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(rows) { rowData ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                    // Ensure rowData has enough elements for each header, or pad if necessary
                                    val displayableRowData = rowData.take(headers.size).toMutableList()
                                    while (displayableRowData.size < headers.size) {
                                        displayableRowData.add("") // Pad with empty strings if row is shorter than headers
                                    }

                                    displayableRowData.forEachIndexed { index, cell ->
                                        TableCell(
                                            text = cell,
                                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                                        )
                                    }
                                }
                                HorizontalDivider(
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("CSV is empty or could not be parsed correctly.")
                    }
                }
            }
        }
    }
}

@Composable
fun TableCell(text: String, modifier: Modifier = Modifier, isHeader: Boolean = false) {
    Text(
        text = text,
        modifier = modifier.padding(vertical = 8.dp), // Increased padding for better readability
        fontSize = if (isHeader) 14.sp else 13.sp,
        fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
        maxLines = 2, // Allow cell content to wrap slightly
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Start // Align text to the start of the cell
    )
}

@Preview(showBackground = true)
@Composable
fun CsvFileAnalysisScreenPreview() {
    DataGrindsetTheme {
        // For preview, you might need to mock the ViewModel or provide a dummy URI
        // that your preview ViewModel setup can handle.
        val dummyUri = "content://com.example.datagrindset.provider/dummy.csv".toUri()
        CsvFileAnalysisScreen(
            navController = NavController(LocalContext.current),
            fileUri = dummyUri
        )
    }
}