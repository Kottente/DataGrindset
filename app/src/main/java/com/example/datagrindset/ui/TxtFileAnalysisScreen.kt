package com.example.datagrindset.ui

import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.datagrindset.ui.theme.DataGrindsetTheme
import com.example.datagrindset.viewmodel.TxtFileViewModel
import com.example.datagrindset.viewmodel.TxtFileViewModelFactory
import kotlinx.coroutines.launch

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

    // ViewModel States
    val fileName by viewModel.fileName.collectAsState()
    val fileContent by viewModel.fileContent.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val currentResultIndex by viewModel.currentResultIndex.collectAsState()
    val totalResults by viewModel.totalResults.collectAsState()
    val replaceQuery by viewModel.replaceQuery.collectAsState()
    val isEditMode by viewModel.isEditMode.collectAsState()
    val editableContent by viewModel.editableContent.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val saveError by viewModel.saveError.collectAsState()
    val hasUnsavedChanges by viewModel.hasUnsavedChanges.collectAsState()
    val showDiscardConfirmDialog by viewModel.showDiscardConfirmDialog.collectAsState()

    // Local UI states
    var showMenu by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(false) }

    val searchBarFocusRequester = remember { FocusRequester() }
    val editorFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    val snackbarHostState = remember { SnackbarHostState() }
    val textScrollState = rememberScrollState()
    val editorScrollState = rememberScrollState()
    var textLayoutResultState by remember { mutableStateOf<TextLayoutResult?>(null) }

    val saveAsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
        onResult = { newFileUri: Uri? -> viewModel.onSaveAsUriReceived(newFileUri) }
    )

    LaunchedEffect(Unit) {
        viewModel.initiateSaveAsEvent.collect { suggestedName ->
            suggestedName?.let { saveAsLauncher.launch(it) }
        }
    }

    val currentHighlightColor = MaterialTheme.colorScheme.primaryContainer
    val otherHighlightColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)

    LaunchedEffect(currentResultIndex, textLayoutResultState, searchResults.size) {
        val textLayoutResult = textLayoutResultState
        if (currentResultIndex != -1 && searchResults.isNotEmpty() && textLayoutResult != null) {
            val currentMatchRange = searchResults.getOrNull(currentResultIndex)
            currentMatchRange?.let { matchRange ->
                try {
                    val offset = matchRange.first.coerceIn(0, textLayoutResult.layoutInput.text.length - 1)
                    val boundingBox = textLayoutResult.getBoundingBox(offset)
                    textScrollState.animateScrollTo(boundingBox.top.toInt())
                } catch (e: Exception) { Log.e("TxtFileAnalysisScreen", "Error calculating scroll: ${e.message}") }
            }
        }
    }
    LaunchedEffect(isEditMode, showSearchBar) {
        if (isEditMode) {
            editorFocusRequester.requestFocus()
        } else if (showSearchBar) { // If not in edit mode, but search bar is shown
            searchBarFocusRequester.requestFocus()
        }
    }


    LaunchedEffect(saveError) {
        saveError?.let {
            snackbarHostState.showSnackbar(message = it, duration = SnackbarDuration.Long)
            viewModel.clearSaveError()
        }
    }

    LaunchedEffect(isEditMode) {
        if (isEditMode) editorFocusRequester.requestFocus()
        else if (showSearchBar) searchBarFocusRequester.requestFocus() // Re-focus search if exiting edit mode into search
    }

    val annotatedFileContent = remember(fileContent, searchQuery, searchResults, currentResultIndex, currentHighlightColor, otherHighlightColor) {
        buildAnnotatedString {
            val content = fileContent
            if (content.isNullOrEmpty()) { return@buildAnnotatedString }
            append(content)
            if (searchQuery.isNotBlank() && searchResults.isNotEmpty()) {
                searchResults.forEachIndexed { index, range ->
                    try {
                        if (range.first >= 0 && range.last < content.length && range.first <= range.last) {
                            addStyle(style = SpanStyle(background = if (index == currentResultIndex) currentHighlightColor else otherHighlightColor), start = range.first, end = range.last + 1)
                        } else { Log.w("TxtFileAnalysisScreen", "Skipping invalid range $range for content length ${content.length}") }
                    } catch (e: Exception) { Log.e("TxtFileAnalysisScreen", "Error applying style for range $range: ${e.message}") }
                }
            }
        }
    }

    BackHandler(enabled = isEditMode && hasUnsavedChanges) { viewModel.attemptExitEditMode() }

    if (showDiscardConfirmDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelDiscardDialog() },
            title = { Text("Unsaved Changes") },
            text = { Text("You have unsaved changes. What would you like to do?") },
            confirmButton = { TextButton(onClick = { viewModel.saveAndExitEditMode() }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { viewModel.confirmDiscardChanges() }) { Text("Discard") } }
            // No neutralButton here. For a "Cancel" that just closes the dialog,
            // the user can click outside (handled by onDismissRequest) or press back.
            // If an explicit "Cancel" button is desired alongside Save & Discard,
            // it would need a custom dialog or different button arrangement.
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (showSearchBar) { // If search bar is active, it takes the title slot
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.onSearchQueryChanged(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(searchBarFocusRequester)
                                .padding(end = 8.dp), // Allow space for actions
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
                    } else { // Otherwise, show "Edit: FileName" or "FileName"
                        Text(
                            text = if (isEditMode) "Edit: $fileName" else fileName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis // Handle long filenames
                        )
                    }
                },
                navigationIcon = {
                    // Navigation icon logic remains: Close search bar if active, else close edit mode, else back.
                    if (showSearchBar) {
                        IconButton(onClick = {
                            showSearchBar = false
                            viewModel.clearSearch()
                            focusManager.clearFocus(true)
                        }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close Search") }
                    } else if (isEditMode) {
                        IconButton(onClick = { viewModel.attemptExitEditMode() }) { Icon(Icons.Filled.Close, "Close Edit Mode") }
                    } else {
                        IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    }
                },
                actions = {
                    if (showSearchBar) {
                        // Actions when search bar is visible (in title slot)
                        if (isEditMode) {
                            IconButton(onClick = { viewModel.replaceCurrentMatch() }, enabled = totalResults > 0) {
                                Icon(Icons.Filled.FindReplace, contentDescription = "Replace Current")
                            }
                        }
                        if (totalResults > 0) {
                            Text("${currentResultIndex + 1}/$totalResults", Modifier.align(Alignment.CenterVertically).padding(horizontal = 4.dp))
                        }
                        IconButton(onClick = { viewModel.goToPreviousMatch() }, enabled = totalResults > 0) {
                            Icon(Icons.Filled.KeyboardArrowUp, "Previous")
                        }
                        IconButton(onClick = { viewModel.goToNextMatch() }, enabled = totalResults > 0) {
                            Icon(Icons.Filled.KeyboardArrowDown, "Next")
                        }
                        // Clear search query button can be added here if BasicTextField's clear is not enough
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear Search Text")
                            }
                        }
                    } else {
                        // Actions when search bar is NOT visible (filename is in title slot)
                        IconButton(onClick = { showSearchBar = true }) { // Search Icon
                            Icon(Icons.Filled.Search, "Search")
                        }
                        if (isEditMode) {
                            IconButton(onClick = { viewModel.initiateSaveAs() }) {
                                Icon(Icons.Filled.SaveAs, contentDescription = "Save As")
                            }
                            if (isSaving) {
                                CircularProgressIndicator(Modifier.size(24.dp).padding(horizontal = 8.dp)) // Added padding
                            } else {
                                IconButton(onClick = { viewModel.saveChanges { viewModel.exitEditMode() } }) {
                                    Icon(Icons.Filled.Done, contentDescription = "Save Changes")
                                }
                            }
                        } else { // Not in edit mode, not searching
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Filled.MoreVert, "More Options")
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(text = { Text("Edit") }, onClick = { viewModel.enterEditMode(); showMenu = false })
                                DropdownMenuItem(text = { Text("Summary") }, onClick = { viewModel.generateSummary(); showMenu = false })
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (showSearchBar && isEditMode) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = replaceQuery,
                        onValueChange = { viewModel.onReplaceQueryChanged(it) }, // This should now work
                        label = { Text("Replace with...") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { viewModel.replaceAllMatches() }, enabled = totalResults > 0) { Text("All") }
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    error != null -> Text("Error: $error", color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.Center).padding(16.dp))
                    isEditMode -> OutlinedTextField(
                        value = editableContent,
                        onValueChange = { viewModel.onEditableContentChanged(it) },
                        modifier = Modifier.fillMaxSize().verticalScroll(editorScrollState).padding(8.dp).focusRequester(editorFocusRequester),
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.outline)
                    )
                    fileContent != null -> SelectionContainer {
                        Text(text = annotatedFileContent, modifier = Modifier.fillMaxSize().verticalScroll(textScrollState).padding(16.dp), fontFamily = FontFamily.Monospace, fontSize = 14.sp, onTextLayout = { textLayoutResultState = it })
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
        val dummyUri = "content://com.example.datagrindset.provider/dummy.txt".toUri()
        TxtFileAnalysisScreen(navController = NavController(LocalContext.current), fileUri = dummyUri)
    }
}