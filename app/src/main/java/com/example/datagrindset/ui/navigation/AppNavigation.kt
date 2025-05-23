package com.example.datagrindset.ui.navigation
//
//import android.app.Application
//import android.net.Uri
//import android.util.Log
//import androidx.compose.material.Text
//import androidx.compose.runtime.Composable
//import androidx.compose.runtime.getValue
//import androidx.compose.ui.platform.LocalContext
//import androidx.lifecycle.compose.collectAsStateWithLifecycle
//import androidx.lifecycle.viewmodel.compose.viewModel
//import androidx.navigation.NavHostController
//import androidx.navigation.NavType
//import androidx.navigation.compose.NavHost
//import androidx.navigation.compose.composable
//import androidx.navigation.navArgument
//import com.example.datagrindset.ui.CsvFileAnalysisScreen
//import com.example.datagrindset.ui.LocalFileManagerScreen
//import com.example.datagrindset.ui.TxtFileAnalysisScreen
//import com.example.datagrindset.viewmodel.CsvFileViewModelFactory
//import com.example.datagrindset.viewmodel.LocalFileManagerViewModel
//import com.example.datagrindset.viewmodel.TxtFileViewModelFactory
//import java.net.URLDecoder
//import java.nio.charset.StandardCharsets
//
//@Composable
//fun AppNavigation(
//    navController: NavHostController,
//    localFileManagerViewModel: LocalFileManagerViewModel,
//    onSelectRootDirectory: () -> Unit
//) {
//    val rootTreeUri by localFileManagerViewModel.rootTreeUri.collectAsStateWithLifecycle()
//    val canNavigateUp by localFileManagerViewModel.canNavigateUp.collectAsStateWithLifecycle()
//    val currentPathDisplay by localFileManagerViewModel.currentPathDisplay.collectAsStateWithLifecycle()
//    val directoryEntries by localFileManagerViewModel.directoryEntries.collectAsStateWithLifecycle()
//    val fileProcessingStatusMap by localFileManagerViewModel.fileProcessingStatusMap.collectAsStateWithLifecycle()
//    val searchText by localFileManagerViewModel.searchText.collectAsStateWithLifecycle()
//    val sortOption by localFileManagerViewModel.sortOption.collectAsStateWithLifecycle()
//    val navigateToAnalysisTarget by localFileManagerViewModel.navigateToAnalysisTarget.collectAsStateWithLifecycle()
//
//    val application = LocalContext.current.applicationContext as Application
//
//    NavHost(navController = navController, startDestination = Screen.FileManagerScreen.route) {
//        composable(Screen.FileManagerScreen.route) {
//            LocalFileManagerScreen(
//                rootUriSelected = rootTreeUri != null,
//                canNavigateUp = canNavigateUp,
//                currentPath = currentPathDisplay,
//                entries = directoryEntries,
//                fileProcessingStatusMap = fileProcessingStatusMap,
//                searchText = searchText,
//                onSearchTextChanged = localFileManagerViewModel::onSearchTextChanged,
//                currentSortOption = sortOption,
//                onSortOptionSelected = localFileManagerViewModel::onSortOptionSelected,
//                onSelectRootDirectoryClicked = onSelectRootDirectory,
//                onNavigateToFolder = localFileManagerViewModel::navigateTo,
//                onNavigateUp = localFileManagerViewModel::navigateUp,
//                onPrepareFileForAnalysis = localFileManagerViewModel::prepareFileForAnalysis,
//                onDeleteEntry = localFileManagerViewModel::deleteEntry,
//                navigateToAnalysisTarget = navigateToAnalysisTarget,
//                onDidNavigateToAnalysisScreen = localFileManagerViewModel::didNavigateToAnalysisScreen,
//                navController = navController
//            )
//        }
//
//        composable(
//            route = Screen.TxtFileAnalysisScreen.route + "/{fileUri}/{fileName}",
//            arguments = listOf(
//                navArgument("fileUri") { type = NavType.StringType },
//                navArgument("fileName") { type = NavType.StringType }
//            )
//        ) { backStackEntry ->
//            val encodedFileUriString = backStackEntry.arguments?.getString("fileUri")
//            val encodedFileNameString = backStackEntry.arguments?.getString("fileName")
//            if (encodedFileUriString != null && encodedFileNameString != null) {
//                val decodedUriString = URLDecoder.decode(encodedFileUriString, StandardCharsets.UTF_8.toString())
//                val decodedFileNameString = URLDecoder.decode(encodedFileNameString, StandardCharsets.UTF_8.toString())
//
//                // Attempt to reconstruct URI more explicitly - Long shot
//                val tempUri = Uri.parse(decodedUriString)
//                val fileUri = Uri.Builder()
//                    .scheme(tempUri.scheme)
//                    .authority(tempUri.authority)
//                    .path(tempUri.path) // Path should be correctly decoded already
//                    .query(tempUri.query) // Preserve query if any
//                    .fragment(tempUri.fragment) // Preserve fragment if any
//                    .build()
//
//                Log.d("AppNavigation", "TXT Nav: Decoded URI String: $decodedUriString, Rebuilt URI: $fileUri, Decoded FileName: $decodedFileNameString")
//
//                val txtViewModel: com.example.datagrindset.viewmodel.TxtFileViewModel = viewModel(
//                    factory = TxtFileViewModelFactory(application, fileUri, decodedFileNameString)
//                )
//                TxtFileAnalysisScreen(navController = navController, fileUri = fileUri, viewModel = txtViewModel)
//            } else {
//                Log.e("AppNavigation", "TXT Nav: fileUri or fileName argument is null.")
//                Text("Error: TXT file URI or FileName not provided.")
//            }
//        }
//
//        composable(
//            route = Screen.CsvFileAnalysisScreen.route + "/{fileUri}/{fileName}",
//            arguments = listOf(
//                navArgument("fileUri") { type = NavType.StringType },
//                navArgument("fileName") { type = NavType.StringType }
//            )
//        ) { backStackEntry ->
//            val encodedFileUriString = backStackEntry.arguments?.getString("fileUri")
//            val encodedFileNameString = backStackEntry.arguments?.getString("fileName")
//            if (encodedFileUriString != null && encodedFileNameString != null) {
//                val decodedUriString = URLDecoder.decode(encodedFileUriString, StandardCharsets.UTF_8.toString())
//                val decodedFileNameString = URLDecoder.decode(encodedFileNameString, StandardCharsets.UTF_8.toString())
//
//                // Attempt to reconstruct URI more explicitly - Long shot
//                val tempUri = Uri.parse(decodedUriString)
//                val fileUri = Uri.Builder()
//                    .scheme(tempUri.scheme)
//                    .authority(tempUri.authority)
//                    .path(tempUri.path)
//                    .query(tempUri.query)
//                    .fragment(tempUri.fragment)
//                    .build()
//
//                Log.d("AppNavigation", "CSV Nav: Decoded URI String: $decodedUriString, Rebuilt URI: $fileUri, Decoded FileName: $decodedFileNameString")
//
//                val csvViewModel: com.example.datagrindset.viewmodel.CsvFileViewModel = viewModel(
//                    factory = CsvFileViewModelFactory(application, fileUri, decodedFileNameString)
//                )
//                CsvFileAnalysisScreen(navController = navController, fileUri = fileUri, viewModel = csvViewModel)
//            } else {
//                Log.e("AppNavigation", "CSV Nav: fileUri or fileName argument is null.")
//                Text("Error: CSV file URI or FileName not provided.")
//            }
//        }
//    }
//}