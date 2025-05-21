package com.example.datagrindset.ui

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.datagrindset.ui.theme.DataGrindsetTheme
import com.example.datagrindset.viewmodel.TxtFileViewModel
import com.example.datagrindset.viewmodel.TxtFileViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TxtFileAnalysisScreen(
    navController: NavController,
    fileUri: Uri
) {
    val context = LocalContext.current.applicationContext
    val viewModel: TxtFileViewModel = viewModel(
        factory = TxtFileViewModelFactory(context as android.app.Application, fileUri)
    )

    val fileName by viewModel.fileName.collectAsState()
    val fileContent by viewModel.fileContent.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    var showMenu by remember { mutableStateOf(false) }
    // var showSearchBar by remember { mutableStateOf(false) } // Future state for search bar visibility
    // Add more states here for UI interactions like font size, search query, edit mode, etc.
    // For example:
    // var currentFontSize by remember { mutableStateOf(14.sp) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(fileName, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Placeholder for menu items - will be expanded
                    IconButton(onClick = {
                        // TODO: Implement search functionality
                        // e.g., showSearchBar = !showSearchBar
                        // For now, just a placeholder action or log
                        println("Search button clicked")
                    }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search in file")
                    }

                    // More Options Menu
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More Options")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Summary") },
                            onClick = { viewModel.generateSummary(); showMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Edit") }, // This will eventually change the TopAppBar actions too
                            onClick = { viewModel.toggleEditMode(); showMenu = false }
                        )
                        // Font scaling options can be added here or as direct icons too
                        // Example:
                        // DropdownMenuItem(
                        //     text = { Text("Increase Font Size") },
                        //     onClick = { viewModel.increaseFontSize(); showMenu = false }
                        // )
                        // DropdownMenuItem(
                        //     text = { Text("Decrease Font Size") },
                        //     onClick = { viewModel.decreaseFontSize(); showMenu = false }
                        // )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                error != null -> {
                    Text(
                        text = "Error: $error",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center).padding(16.dp)
                    )
                }
                fileContent != null -> {
                    Text(
                        text = fileContent!!, // Content is not null here
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        fontFamily = FontFamily.Monospace, // Good for text files
                        fontSize = 14.sp // Will be dynamic later: currentFontSize
                    )
                }
                else -> {
                    Text(
                        "No content loaded or file is empty.",
                        modifier = Modifier.align(Alignment.Center).padding(16.dp)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TxtFileAnalysisScreenPreview() {
    DataGrindsetTheme {
        // Creating a dummy URI for preview. This won't load actual content in preview.
        val dummyUri = Uri.parse("content://com.example.datagrindset.provider/dummy.txt")
        TxtFileAnalysisScreen(
            navController = NavController(LocalContext.current), // Simple NavController for preview
            fileUri = dummyUri
        )
    }
}