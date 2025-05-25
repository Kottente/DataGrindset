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
//import com.example.datagrindset.viewmodel.SummaryData // Keep this
import com.example.datagrindset.viewmodel.TxtFileViewModel
import com.example.datagrindset.TxtFileViewModelFactory
//import kotlinx.coroutines.launch

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
    var showDetailedSummarySheet by remember { mutableStateOf(false) } // For detailed summary

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
                    currentTextLayoutResult?.let { try { val offset = matchRange.first.coerceIn(0, it.layoutInput.text.length - 1); val boundingBox = it.getBoundingBox(offset); activeScrollState.animateScrollTo(boundingBox.top.toInt()) } catch (e: Exception) { Log.e("TxtFileAnalysisScreen", "Error calculating scroll for read mode: ${e.message}") } }
                }
            }
        }
    }

    LaunchedEffect(saveError) {
        saveError?.let { snackbarHostState.showSnackbar(message = it, duration = SnackbarDuration.Long); viewModel.clearSaveError() }
    }

    LaunchedEffect(isEditMode, showSearchBar) {
        if (showSearchBar) searchBarFocusRequester.requestFocus()
        else if (isEditMode) editorFocusRequester.requestFocus()
    }

    val annotatedFileContentForReadMode = remember(fileContent, searchQuery, searchResults, currentResultIndex, currentHighlightColor, otherHighlightColor) {
        buildAnnotatedString {
            val content = fileContent; if (content.isNullOrEmpty()) return@buildAnnotatedString; append(content)
            if (searchQuery.isNotBlank() && searchResults.isNotEmpty()) {
                searchResults.forEachIndexed { index, range -> try { if (range.first >= 0 && range.last < content.length && range.first <= range.last) addStyle(style = SpanStyle(background = if (index == currentResultIndex) currentHighlightColor else otherHighlightColor), start = range.first, end = range.last + 1) } catch (e: Exception) { Log.e("TxtFileAnalysisScreen", "Error applying style for range $range: ${e.message}") } }
            }
        }
    }

    BackHandler(enabled = (isEditMode && hasUnsavedChanges) || showSearchBar || showSummarySheet || showDetailedSummarySheet) {
        when {
            showSummarySheet -> { showSummarySheet = false; viewModel.clearSummary() }
            showDetailedSummarySheet -> { showDetailedSummarySheet = false; viewModel.clearDetailedSummary() }
            showSearchBar -> { showSearchBar = false; viewModel.clearSearch(); focusManager.clearFocus(true) }
            isEditMode && hasUnsavedChanges -> viewModel.attemptExitEditMode()
        }
    }

    if (showDiscardConfirmDialog) {
        AlertDialog(onDismissRequest = { viewModel.cancelDiscardDialog() }, title = { Text("Unsaved Changes") }, text = { Text("You have unsaved changes. What would you like to do?") }, confirmButton = { TextButton(onClick = { viewModel.saveAndExitEditMode() }) { Text("Save") } }, dismissButton = { TextButton(onClick = { viewModel.confirmDiscardChanges() }) { Text("Discard") } })
    }

    // Basic Summary Bottom Sheet
    if (showSummarySheet) {
        ModalBottomSheet(
            onDismissRequest = { showSummarySheet = false; viewModel.clearSummary() },
            sheetState = basicSummarySheetState,
            windowInsets = WindowInsets(0.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("File Summary", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 16.dp))
                if (isGeneratingSummary) CircularProgressIndicator(modifier = Modifier.padding(vertical = 20.dp))
                else if (summaryError != null) Text("Error: $summaryError", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 20.dp))
                else if (summaryResult != null) {
                    SummaryInfoRow("Lines:", summaryResult!!.lineCount.toString())
                    SummaryInfoRow("Words:", summaryResult!!.wordCount.toString())
                    SummaryInfoRow("Characters (with spaces):", summaryResult!!.charCount.toString())
                    SummaryInfoRow("Characters (no spaces):", summaryResult!!.charCountWithoutSpaces.toString())
                } else Text("No summary available.", modifier = Modifier.padding(vertical = 20.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { showSummarySheet = false; viewModel.clearSummary() }) { Text("Close") }
            }
        }
    }

    // Detailed Summary Bottom Sheet
    if (showDetailedSummarySheet) {
        ModalBottomSheet(
            onDismissRequest = { showDetailedSummarySheet = false; viewModel.clearDetailedSummary() },
            sheetState = detailedSummarySheetState,
            windowInsets = WindowInsets(0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding(), // Add padding for navigation bars
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
                    if (showSearchBar) BasicTextField(value = searchQuery, onValueChange = { viewModel.onSearchQueryChanged(it) }, modifier = Modifier.fillMaxWidth().focusRequester(searchBarFocusRequester).padding(end = 8.dp), textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface), cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), singleLine = true, decorationBox = { innerTextField -> Box(modifier = Modifier.fillMaxWidth()) { if (searchQuery.isEmpty()) Text("Search in file...", color = MaterialTheme.colorScheme.onSurfaceVariant); innerTextField() } })
                    else Text(text = if (isEditMode) "Edit: $fileName" else fileName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    if (showSearchBar) IconButton(onClick = { showSearchBar = false; viewModel.clearSearch(); focusManager.clearFocus(true) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close Search") }
                    else if (isEditMode) IconButton(onClick = { viewModel.attemptExitEditMode() }) { Icon(Icons.Filled.Close, "Close Edit Mode") }
                    else IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (showSearchBar) {
                        if (isEditMode) IconButton(onClick = { viewModel.replaceCurrentMatch() }, enabled = totalResults > 0) { Icon(Icons.Filled.FindReplace, "Replace Current") }
                        if (totalResults > 0) Text("${currentResultIndex + 1}/$totalResults", Modifier.align(Alignment.CenterVertically).padding(horizontal = 4.dp))
                        IconButton(onClick = { viewModel.goToPreviousMatch() }, enabled = totalResults > 0) { Icon(Icons.Filled.KeyboardArrowUp, "Previous") }
                        IconButton(onClick = { viewModel.goToNextMatch() }, enabled = totalResults > 0) { Icon(Icons.Filled.KeyboardArrowDown, "Next") }
                        if (searchQuery.isNotEmpty()) IconButton(onClick = { viewModel.onSearchQueryChanged("") }) { Icon(Icons.Filled.Clear, "Clear Search Text") }
                    } else { // Search bar is NOT visible
                        if (isEditMode) {
                            IconButton(onClick = { viewModel.undo() }, enabled = canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo") }
                            IconButton(onClick = { viewModel.redo() }, enabled = canRedo) { Icon(Icons.AutoMirrored.Filled.Redo, "Redo") }
                        }
                        IconButton(onClick = { showSearchBar = true }) { Icon(Icons.Filled.Search, "Search") }
                        if (isEditMode) {
                            IconButton(onClick = { viewModel.initiateSaveAs() }) { Icon(Icons.Filled.SaveAs, "Save As") }
                            if (isSaving) CircularProgressIndicator(Modifier.size(24.dp).padding(horizontal = 8.dp))
                            else IconButton(onClick = { viewModel.saveChanges { viewModel.exitEditMode() } }) { Icon(Icons.Filled.Done, "Save") }
                        } else { // Not in edit mode, not searching
                            IconButton(onClick = { showMenu = true }) { Icon(Icons.Filled.MoreVert, "More Options") }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(text = { Text("Edit") }, onClick = { viewModel.enterEditMode(); showMenu = false })
                                DropdownMenuItem(
                                    text = { Text("Summary (Basic)") },
                                    onClick = { viewModel.generateSummary(); showSummarySheet = true; showMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Summary (Detailed)") },
                                    onClick = { viewModel.generateDetailedSummary(); showDetailedSummarySheet = true; showMenu = false }
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
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = replaceQuery, onValueChange = { viewModel.onReplaceQueryChanged(it) }, label = { Text("Replace with...") }, modifier = Modifier.weight(1f), singleLine = true)
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { viewModel.replaceAllMatches() }, enabled = totalResults > 0) { Text("All") }
                    }
                    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    error != null -> Text("Error: $error", color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.Center).padding(16.dp))
                    isEditMode -> OutlinedTextField(value = editableContent, onValueChange = { viewModel.onEditableContentChanged(it) }, modifier = Modifier.fillMaxSize().verticalScroll(editorScrollState).padding(8.dp).focusRequester(editorFocusRequester), textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.outline))
                    fileContent != null -> SelectionContainer { Text(text = annotatedFileContentForReadMode, modifier = Modifier.fillMaxSize().verticalScroll(textScrollState).padding(16.dp), fontFamily = FontFamily.Monospace, fontSize = 14.sp, onTextLayout = { textLayoutResultState = it }) }
                    else -> Text("No content loaded or file is empty.", Modifier.align(Alignment.Center).padding(16.dp))
                }
            }
        }
    }
}

@Composable
fun SummaryInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
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