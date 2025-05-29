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
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.datagrindset.ui.theme.DataGrindsetTheme
import com.example.datagrindset.viewmodel.TxtFileViewModel
import com.example.datagrindset.TxtFileViewModelFactory
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TxtFileAnalysisScreen(
    navController: NavController,
    fileUri: Uri
) {
    val context = LocalContext.current.applicationContext

    // Extract filename from URI for the factory
    val fileName = remember(fileUri) {
        val cursor = context.contentResolver.query(fileUri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) it.getString(nameIndex) else "Unknown File"
            } else "Unknown File"
        } ?: "Unknown File"
    }

    val viewModel: TxtFileViewModel = viewModel(
        factory = TxtFileViewModelFactory(context as android.app.Application, fileUri, fileName)
    )

    // ViewModel States
    val fileNameState by viewModel.fileName.collectAsState()
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
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()

    // Basic Summary States
    val summaryResult by viewModel.summaryResult.collectAsState()
    val isGeneratingSummary by viewModel.isGeneratingSummary.collectAsState()
    val summaryError by viewModel.summaryError.collectAsState()

    // Detailed Summary States
    val detailedSummaryData by viewModel.detailedSummaryData.collectAsState()
    val isGeneratingDetailedSummary by viewModel.isGeneratingDetailedSummary.collectAsState()
    val detailedSummaryError by viewModel.detailedSummaryError.collectAsState()

    // Local UI states
    var showMenu by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(false) }
    var showSummarySheet by remember { mutableStateOf(false) }
    var showDetailedSummarySheet by remember { mutableStateOf(false) }

    val searchBarFocusRequester = remember { FocusRequester() }
    val editorFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    val snackbarHostState = remember { SnackbarHostState() }
    val textScrollState = rememberScrollState()
    val editorScrollState = rememberScrollState()
    var textLayoutResultState by remember { mutableStateOf<TextLayoutResult?>(null) }

    val basicSummarySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val detailedSummarySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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

    LaunchedEffect(currentResultIndex, textLayoutResultState, searchResults.size, isEditMode, editableContent, fileContent) {
        val activeScrollState = if (isEditMode) editorScrollState else textScrollState
        val contentForLayout = if (isEditMode) editableContent else fileContent ?: ""
        if (currentResultIndex != -1 && searchResults.isNotEmpty() && contentForLayout.isNotEmpty()) {
            val currentMatchRange = searchResults.getOrNull(currentResultIndex)
            currentMatchRange?.let { matchRange ->
                if (!isEditMode && textLayoutResultState != null) {
                    val currentTextLayoutResult = textLayoutResultState
                    currentTextLayoutResult?.let {
                        try {
                            val offset = matchRange.first.coerceIn(0, it.layoutInput.text.length - 1)
                            val boundingBox = it.getBoundingBox(offset)
                            val lineForOffset = it.getLineForOffset(offset)
                            val lineTop = it.getLineTop(lineForOffset)
                            val lineBottom = it.getLineBottom(lineForOffset)
                            val lineHeight = lineBottom - lineTop
                            val targetScrollPosition = (boundingBox.top - lineHeight * 2).coerceAtLeast(0f).toInt()
                            activeScrollState.animateScrollTo(targetScrollPosition)
                        } catch (e: Exception) {
                            Log.w("TxtFileScreen", "Error scrolling to search result: ${e.message}")
                        }
                    }
                } else if (isEditMode) {
                    // For edit mode, we can't use TextLayoutResult, so just scroll to approximate position
                    val lines = contentForLayout.take(matchRange.first).count { it == '\n' }
                    val approximateY = lines * 20 // Rough estimate of line height
                    activeScrollState.animateScrollTo(approximateY.coerceAtLeast(0))
                } else {

                }
            }
        }
    }

    LaunchedEffect(saveError) {
        saveError?.let {
            snackbarHostState.showSnackbar(message = it, duration = SnackbarDuration.Long)
            viewModel.clearSaveError()
        }
    }

    LaunchedEffect(isEditMode, showSearchBar) {
        delay(100)
        if (showSearchBar) {
            searchBarFocusRequester.requestFocus()
        } else if (isEditMode) {
            editorFocusRequester.requestFocus()
        }
    }

    val annotatedFileContentForReadMode = remember(fileContent, searchQuery, searchResults, currentResultIndex, currentHighlightColor, otherHighlightColor) {
        buildAnnotatedString {
            val content = fileContent
            if (content.isNullOrEmpty()) return@buildAnnotatedString
            append(content)
            if (searchQuery.isNotBlank() && searchResults.isNotEmpty()) {
                searchResults.forEachIndexed { index, range ->
                    try {
                        if (range.first >= 0 && range.last < content.length && range.first <= range.last) {
                            addStyle(
                                style = SpanStyle(
                                    background = if (index == currentResultIndex) currentHighlightColor else otherHighlightColor
                                ),
                                start = range.first,
                                end = range.last + 1
                            )
                        }
                    } catch (e: Exception) {
                        Log.w("TxtFileScreen", "Error highlighting search result $index: ${e.message}")
                    }
                }
            }
        }
    }

    BackHandler(enabled = (isEditMode && hasUnsavedChanges) || showSearchBar || showSummarySheet || showDetailedSummarySheet) {
        when {
            showSummarySheet -> {
                showSummarySheet = false
                viewModel.clearSummary()
            }
            showDetailedSummarySheet -> {
                showDetailedSummarySheet = false
                viewModel.clearDetailedSummary()
            }
            showSearchBar -> {
                showSearchBar = false
                viewModel.clearSearch()
                focusManager.clearFocus(true)
            }
            isEditMode && hasUnsavedChanges -> viewModel.attemptExitEditMode()
        }
    }

    if (showDiscardConfirmDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelDiscardDialog() },
            title = { Text("Unsaved Changes") },
            text = { Text("You have unsaved changes. What would you like to do?") },
            confirmButton = {
                TextButton(onClick = { viewModel.saveAndExitEditMode() }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.confirmDiscardChanges() }) {
                    Text("Discard")
                }
            },
            icon = { Icon(Icons.Filled.Warning, contentDescription = "Warning") }
        )
    }

    // Basic Summary Bottom Sheet
    if (showSummarySheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showSummarySheet = false
                viewModel.clearSummary()
            },
            sheetState = basicSummarySheetState,
            windowInsets = WindowInsets(0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("File Summary", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 16.dp))
                if (isGeneratingSummary) {
                    CircularProgressIndicator(modifier = Modifier.padding(vertical = 20.dp))
                } else if (summaryError != null) {
                    Text("Error: $summaryError", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 20.dp))
                } else if (summaryResult != null) {
                    SummaryInfoRow("Lines:", summaryResult!!.lineCount.toString())
                    SummaryInfoRow("Words:", summaryResult!!.wordCount.toString())
                    SummaryInfoRow("Characters (with spaces):", summaryResult!!.charCount.toString())
                    SummaryInfoRow("Characters (no spaces):", summaryResult!!.charCountWithoutSpaces.toString())
                } else {
                    Text("No summary available.", modifier = Modifier.padding(vertical = 20.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    showSummarySheet = false
                    viewModel.clearSummary()
                }) {
                    Text("Close")
                }
            }
        }
    }

    // Detailed Summary Bottom Sheet
    if (showDetailedSummarySheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showDetailedSummarySheet = false
                viewModel.clearDetailedSummary()
            },
            sheetState = detailedSummarySheetState,
            windowInsets = WindowInsets(0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Keywords / Topics", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 16.dp))
                if (isGeneratingDetailedSummary) {
                    CircularProgressIndicator(modifier = Modifier.padding(vertical = 20.dp))
                } else if (detailedSummaryError != null) {
                    Text("Error: $detailedSummaryError", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 20.dp))
                } else if (!detailedSummaryData.isNullOrEmpty()) {
                    detailedSummaryData?.forEach { (keyword, count) ->
                        SummaryInfoRow("\"${keyword}\":", count.toString() + if (count == 0) "" else " occurrences")
                    }
                } else {
                    Text("No detailed summary available or no keywords found.", modifier = Modifier.padding(vertical = 20.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    showDetailedSummarySheet = false
                    viewModel.clearDetailedSummary()
                }) {
                    Text("Close")
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (showSearchBar) {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.onSearchQueryChanged(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(searchBarFocusRequester),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        "Search in file...",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                                innerTextField()
                            }
                        )
                    } else {
                        Text(
                            text = if (isEditMode) "Edit: $fileNameState" else fileNameState,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    if (showSearchBar) {
                        IconButton(onClick = {
                            showSearchBar = false
                            viewModel.clearSearch()
                            focusManager.clearFocus(true)
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close Search")
                        }
                    } else if (isEditMode) {
                        IconButton(onClick = { viewModel.attemptExitEditMode() }) {
                            Icon(Icons.Filled.Close, "Close Edit Mode")
                        }
                    } else {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    }
                },
                actions = {
                    if (showSearchBar) {
                        if (isEditMode) {
                            IconButton(
                                onClick = { viewModel.replaceCurrentMatch() },
                                enabled = totalResults > 0
                            ) {
                                Icon(Icons.Filled.FindReplace, "Replace Current")
                            }
                        }
                        if (totalResults > 0) {
                            Text(
                                "${currentResultIndex + 1}/$totalResults",
                                Modifier
                                    .align(Alignment.CenterVertically)
                                    .padding(horizontal = 4.dp)
                            )
                        }
                        IconButton(
                            onClick = { viewModel.goToPreviousMatch() },
                            enabled = totalResults > 0
                        ) {
                            Icon(Icons.Filled.KeyboardArrowUp, "Previous")
                        }
                        IconButton(
                            onClick = { viewModel.goToNextMatch() },
                            enabled = totalResults > 0
                        ) {
                            Icon(Icons.Filled.KeyboardArrowDown, "Next")
                        }
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                Icon(Icons.Filled.Clear, "Clear Search Text")
                            }
                        }
                    } else { // Search bar is NOT visible
                        if (isEditMode) {
                            IconButton(
                                onClick = { viewModel.undo() },
                                enabled = canUndo
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Undo, "Undo")
                            }
                            IconButton(
                                onClick = { viewModel.redo() },
                                enabled = canRedo
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Redo, "Redo")
                            }
                        }
                        IconButton(onClick = { showSearchBar = true }) {
                            Icon(Icons.Filled.Search, "Search")
                        }
                        if (isEditMode) {
                            IconButton(onClick = { viewModel.initiateSaveAs() }) {
                                Icon(Icons.Filled.SaveAs, "Save As")
                            }
                            if (isSaving) {
                                CircularProgressIndicator(
                                    Modifier
                                        .size(24.dp)
                                        .padding(horizontal = 8.dp)
                                )
                            } else {
                                IconButton(onClick = {
                                    viewModel.saveChanges { viewModel.exitEditMode() }
                                }) {
                                    Icon(Icons.Filled.Done, "Save")
                                }
                            }
                        } else { // Not in edit mode, not searching
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Filled.MoreVert, "More Options")
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Edit") },
                                    onClick = {
                                        viewModel.enterEditMode()
                                        showMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Summary (Basic)") },
                                    onClick = {
                                        viewModel.generateSummary()
                                        showSummarySheet = true
                                        showMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Summary (Detailed)") },
                                    onClick = {
                                        viewModel.generateDetailedSummary()
                                        showDetailedSummarySheet = true
                                        showMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (showSearchBar && isEditMode) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = replaceQuery,
                            onValueChange = { viewModel.onReplaceQueryChanged(it) },
                            label = { Text("Replace with...") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { viewModel.replaceAllMatches() },
                            enabled = totalResults > 0
                        ) {
                            Text("All")
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    error != null -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Filled.Error,
                                    contentDescription = "Error",
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    "Error loading file",
                                    style = MaterialTheme.typography.headlineSmall,
                                    modifier = Modifier.padding(top = 16.dp)
                                )
                                Text(
                                    error!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 8.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    isEditMode -> {
                        BasicTextField(
                            value = editableContent,
                            onValueChange = { viewModel.onEditableContentChanged(it) },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                                .verticalScroll(editorScrollState)
                                .focusRequester(editorFocusRequester),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 20.sp
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                        )
                    }
                    else -> {
                        SelectionContainer {
                            Text(
                                text = annotatedFileContentForReadMode,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                                    .verticalScroll(textScrollState),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 20.sp
                                ),
                                onTextLayout = { textLayoutResult ->
                                    textLayoutResultState = textLayoutResult
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontWeight = FontWeight.Medium)
        Text(value, color = MaterialTheme.colorScheme.primary)
    }
}

@Preview(showBackground = true)
@Composable
fun TxtFileAnalysisScreenPreview() {
    DataGrindsetTheme {
        // Preview content would go here
    }
}