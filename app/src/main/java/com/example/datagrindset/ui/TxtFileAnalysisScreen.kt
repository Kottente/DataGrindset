package com.example.datagrindset.ui

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.*
import androidx.activity.compose.BackHandler // Import BackHandler
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.datagrindset.ui.theme.DataGrindsetTheme
import com.example.datagrindset.viewmodel.TxtFileViewModel
import com.example.datagrindset.viewmodel.TxtFileViewModelFactory
import androidx.core.net.toUri
import androidx.activity.compose.rememberLauncherForActivityResult // Import
import androidx.activity.result.contract.ActivityResultContracts

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
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val currentResultIndex by viewModel.currentResultIndex.collectAsState()
    val totalResults by viewModel.totalResults.collectAsState()
    var showSearchBar by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    // Edit mode states
    val isEditMode by viewModel.isEditMode.collectAsState()
    val editableContent by viewModel.editableContent.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val saveError by viewModel.saveError.collectAsState()
    val hasUnsavedChanges by viewModel.hasUnsavedChanges.collectAsState()
    val showDiscardConfirmDialog by viewModel.showDiscardConfirmDialog.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val editorScrollState = rememberScrollState()

    val saveAsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"), // MIME type
        onResult = { newFileUri: Uri? ->
            viewModel.onSaveAsUriReceived(newFileUri)
        }
    )

    // --- Collect "Save As" event from ViewModel ---
    LaunchedEffect(Unit) { // Keyed to Unit to run once
        viewModel.initiateSaveAsEvent.collect { suggestedName ->
            suggestedName?.let {
                saveAsLauncher.launch(it) // Launch the SAF file picker
            }
        }
    }

    var textLayoutResultState by remember { mutableStateOf<TextLayoutResult?>(null) }
    val textScrollState = rememberScrollState()
    val currentHighlightColor = MaterialTheme.colorScheme.primaryContainer
    val otherHighlightColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
    val annotatedFileContent = remember(
        fileContent,
        searchQuery,
        searchResults,
        currentResultIndex,
        currentHighlightColor, // Add colors as keys to recompute if theme changes
        otherHighlightColor
    ) {
        buildAnnotatedString {
            val content = fileContent
            if (content.isNullOrEmpty()) {
                return@buildAnnotatedString
            }
            append(content)

            if (searchQuery.isNotBlank() && searchResults.isNotEmpty()) {
                searchResults.forEachIndexed { index, range ->
                    try {
                        if (range.first >= 0 && range.last < content.length && range.first <= range.last) {
                            addStyle(
                                style = SpanStyle(
                                    background = if (index == currentResultIndex) currentHighlightColor
                                    else otherHighlightColor
                                ),
                                start = range.first,
                                end = range.last + 1
                            )
                        } else {
                            Log.w("TxtFileAnalysisScreen", "Skipping invalid range $range for content length ${content.length}")
                        }
                    } catch (e: IndexOutOfBoundsException) {
                        Log.e("TxtFileAnalysisScreen", "IndexOutOfBoundsException applying style for range $range: ${e.message}")
                    } catch (e: Exception) {
                        Log.e("TxtFileAnalysisScreen", "Generic error applying style for range $range: ${e.message}")
                    }
                }
            }
        }
    }

    // Scroll to current search result
    LaunchedEffect(currentResultIndex, textLayoutResultState, searchResults.size) {
        val textLayoutResult = textLayoutResultState // Use the state here
        if (currentResultIndex != -1 && searchResults.isNotEmpty() && textLayoutResult != null) {
            val currentMatchRange = searchResults.getOrNull(currentResultIndex)
            currentMatchRange?.let { matchRange ->
                try {
                    // Ensure the offset is within the text length
                    val offset = matchRange.first.coerceIn(0, textLayoutResult.layoutInput.text.length -1)
                    val boundingBox = textLayoutResult.getBoundingBox(offset)
                    val scrollPosition = boundingBox.top

                    // Get the height of the visible area of the text composable
                    // This requires the Text composable to be in a BoxWithConstraints or similar
                    // For simplicity, we'll scroll to bring the top of the match into view.
                    // A more advanced solution might try to center it.
                    // We also need to consider the current scroll position and padding.
                    // A simple scroll:
                    textScrollState.animateScrollTo(scrollPosition.toInt())

                } catch (e: Exception) {
                    Log.e("TxtFileAnalysisScreen", "Error calculating scroll position: ${e.message}")
                }
            }
        }
    }

    LaunchedEffect(saveError) { // Show snackbar for save errors
        saveError?.let {
            snackbarHostState.showSnackbar(message = it, duration = SnackbarDuration.Long)
            viewModel.clearSaveError() // Clear error after showing
            // Optionally clear the error in VM after showing: viewModel.clearSaveError()
        }
    }

    LaunchedEffect(isEditMode) { // Request focus for editor when entering edit mode
        if (isEditMode) {
            focusRequester.requestFocus() // Use the same focusRequester or a new one for editor
        }
    }
    // --- Back Press Handling for Discard Confirmation ---
    BackHandler(enabled = isEditMode && hasUnsavedChanges) {
        viewModel.attemptExitEditMode() // This will show the dialog
    }

    // --- Discard Confirmation Dialog ---
    if (showDiscardConfirmDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelDiscardDialog() }, // User dismissed by clicking outside or back
            title = { Text("Unsaved Changes") },
            text = { Text("You have unsaved changes. What would you like to do?") },
            confirmButton = {
                TextButton(onClick = { viewModel.saveAndExitEditMode() }) {
                    Text("Save")
                }
            },
            dismissButton = { // This will now be our "Discard"
                Row { // Use a Row to add a "Cancel" button if desired, or just have Discard
                    TextButton(onClick = { viewModel.cancelDiscardDialog() }) { // Acts as a "Cancel" for the dialog itself
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp)) // Some space
                    TextButton(onClick = { viewModel.confirmDiscardChanges() }) {
                        Text("Discard")
                    }
                }
            }
            // Removed neutralButton
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (isEditMode) {
                        Text("Edit: $fileName", maxLines = 1)
                    } else if (showSearchBar) {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.onSearchQueryChanged(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .padding(end = 8.dp),
                            textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    if (searchQuery.isEmpty()) {
                                        Text("Search in file...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    innerTextField()
                                }
                            }
                        )
                        LaunchedEffect(Unit) { focusRequester.requestFocus() } // Focus search
                    } else {
                        Text(fileName, maxLines = 1)
                    }
                },
                navigationIcon = {
                    if (isEditMode) {
                        IconButton(onClick = { viewModel.attemptExitEditMode() }) { // Cancel Edit
                            Icon(Icons.Filled.Close, contentDescription = "Close Edit Mode")
                        }
                    } else if (showSearchBar) {
                        IconButton(onClick = { showSearchBar = false; viewModel.clearSearch(); focusManager.clearFocus() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close Search")
                        }
                    } else {
                        IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    }
                },
                actions = {
                    if (isEditMode) {
                        IconButton(onClick = { viewModel.initiateSaveAs() }) {
                            Icon(Icons.Filled.SaveAs, contentDescription = "Save As")
                        }
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 16.dp))
                        } else {
                            IconButton(onClick = { viewModel.saveChanges { viewModel.exitEditMode() } }) {
                                Icon(Icons.Filled.Done, contentDescription = "Save Changes")
                            }
                        }
                    } else if (showSearchBar) {
                        // ... (Search actions: result count, up/down arrows - remain the same) ...
                        if (totalResults > 0) { Text("${currentResultIndex + 1}/$totalResults", Modifier.align(Alignment.CenterVertically).padding(horizontal = 8.dp)) }
                        IconButton(onClick = { viewModel.goToPreviousMatch() }, enabled = totalResults > 0) { Icon(Icons.Filled.KeyboardArrowUp, "Previous") }
                        IconButton(onClick = { viewModel.goToNextMatch() }, enabled = totalResults > 0) { Icon(Icons.Filled.KeyboardArrowDown, "Next") }
                    } else { // Read mode, no search bar
                        IconButton(onClick = { showSearchBar = true }) { Icon(Icons.Filled.Search, "Search") }
                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Filled.MoreVert, "More Options") }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text("Edit") }, onClick = { viewModel.enterEditMode(); showMenu = false })
                            DropdownMenuItem(text = { Text("Summary") }, onClick = { viewModel.generateSummary(); showMenu = false })
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) { // Ensure Box fills width
                when {
                    isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    error != null -> Text("Error: $error", color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.Center).padding(16.dp))
                    isEditMode -> {
                        OutlinedTextField(
                            value = editableContent,
                            onValueChange = { viewModel.onEditableContentChanged(it) },
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(editorScrollState)
                                .padding(8.dp) // Standard padding for text fields
                                .focusRequester(focusRequester), // For focus on entering edit mode
                            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                            colors = OutlinedTextFieldDefaults.colors( // Match theme
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    }
                    fileContent != null -> { // Read mode
                        SelectionContainer {
                            Text(
                                text = annotatedFileContent,
                                modifier = Modifier.fillMaxSize().verticalScroll(textScrollState).padding(16.dp),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                onTextLayout = { layoutResult -> textLayoutResultState = layoutResult }
                            )
                        }
                    }
                    else -> Text("No content loaded or file is empty.", Modifier.align(Alignment.Center).padding(16.dp))
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
        val dummyUri = "content://com.example.datagrindset.provider/dummy.txt".toUri()
        TxtFileAnalysisScreen(
            navController = NavController(LocalContext.current), // Simple NavController for preview
            fileUri = dummyUri
        )
    }
}