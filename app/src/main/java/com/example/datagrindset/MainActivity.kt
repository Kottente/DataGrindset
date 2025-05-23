package com.example.datagrindset

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel // Keep this for analysis ViewModels
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.datagrindset.ui.CsvFileAnalysisScreen
import com.example.datagrindset.ui.LocalFileManagerScreen
import com.example.datagrindset.ui.TxtFileAnalysisScreen
import com.example.datagrindset.ui.navigation.Screen
import com.example.datagrindset.ui.theme.DataGrindsetTheme
import com.example.datagrindset.viewmodel.CsvFileViewModelFactory
import com.example.datagrindset.viewmodel.LocalFileManagerViewModel
import com.example.datagrindset.viewmodel.LocalFileManagerViewModelFactory
import com.example.datagrindset.TxtFileViewModelFactory
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class MainActivity : ComponentActivity() {

    private val localFileManagerViewModel: LocalFileManagerViewModel by viewModels {
        LocalFileManagerViewModelFactory(application)
    }

    private val openDirectoryLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { treeUri ->
            Log.d("MainActivity", "OpenDocumentTree result URI: $treeUri")
            try {
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                applicationContext.contentResolver.takePersistableUriPermission(treeUri, takeFlags)
                Log.i("MainActivity", "Successfully took persistable permissions for tree: $treeUri")

                val persistedPermissions = applicationContext.contentResolver.persistedUriPermissions
                val hasPermission = persistedPermissions.any { it.uri == treeUri && it.isReadPermission }
                if (hasPermission) {
                    Log.i("MainActivity", "Verified: Persisted read permission is HELD for $treeUri")
                } else {
                    Log.e("MainActivity", "Verification FAILED: Persisted read permission is NOT HELD for $treeUri after taking.")
                    Toast.makeText(this, "Failed to secure folder permissions. Please try again.", Toast.LENGTH_LONG).show()
                    localFileManagerViewModel.setRootTreeUri(null)
                    return@let
                }
                localFileManagerViewModel.setRootTreeUri(treeUri)
            } catch (e: SecurityException) {
                Log.e("MainActivity", "SecurityException taking persistable permissions for tree: $treeUri", e)
                Toast.makeText(this, "Failed to get folder permissions. Please try again.", Toast.LENGTH_LONG).show()
                localFileManagerViewModel.setRootTreeUri(null)
            } catch (e: Exception) {
                Log.e("MainActivity", "Exception during OpenDocumentTree result handling for $treeUri", e)
                Toast.makeText(this, "An error occurred with folder selection.", Toast.LENGTH_LONG).show()
                localFileManagerViewModel.setRootTreeUri(null)
            }
        } ?: run {
            Log.d("MainActivity", "OpenDocumentTree result URI is null (user cancelled or error).")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            DataGrindsetTheme {
                val navController = rememberNavController()
                val application = this.applicationContext as Application

                val navigateToAnalysisTarget by localFileManagerViewModel.navigateToAnalysisTarget.collectAsStateWithLifecycle()

                LaunchedEffect(navigateToAnalysisTarget) {
                    navigateToAnalysisTarget?.let { fileEntry ->
                        val route = when (fileEntry.mimeType?.lowercase()) {
                            "text/plain", "text/markdown" ->
                                Screen.TxtFileAnalysisScreen.createRoute(fileEntry.uri, fileEntry.name)
                            "text/csv", "text/comma-separated-values", "application/csv" ->
                                Screen.CsvFileAnalysisScreen.createRoute(fileEntry.uri, fileEntry.name)
                            else -> null
                        }
                        route?.let {
                            navController.navigate(it)
                            localFileManagerViewModel.didNavigateToAnalysisScreen()
                        }
                    }
                }

                NavHost(navController = navController, startDestination = Screen.FileManagerScreen.route) {
                    composable(Screen.FileManagerScreen.route) {
                        LocalFileManagerScreen(
                            navController = navController,
                            viewModel = localFileManagerViewModel,
                            onSelectRootDirectoryClicked = { // Pass the callback
                                Log.d("MainActivity", "LocalFileManagerScreen requests OpenDocumentTree.")
                                openDirectoryLauncher.launch(null)
                            }
                        )
                    }

                    composable(
                        route = Screen.TxtFileAnalysisScreen.route + "/{fileUri}/{fileName}",
                        arguments = listOf(
                            navArgument("fileUri") { type = NavType.StringType },
                            navArgument("fileName") { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val encodedFileUriString = backStackEntry.arguments?.getString("fileUri")
                        val encodedFileNameString = backStackEntry.arguments?.getString("fileName")
                        if (encodedFileUriString != null && encodedFileNameString != null) {
                            val decodedUriString = URLDecoder.decode(encodedFileUriString, StandardCharsets.UTF_8.toString())
                            val decodedFileNameString = URLDecoder.decode(encodedFileNameString, StandardCharsets.UTF_8.toString())
                            val fileUri = Uri.parse(decodedUriString)

                            val txtViewModel: com.example.datagrindset.viewmodel.TxtFileViewModel = viewModel(
                                factory = TxtFileViewModelFactory(application, fileUri, decodedFileNameString)
                            )
                            TxtFileAnalysisScreen(navController = navController, fileUri = fileUri, viewModel = txtViewModel)
                        } else {
                            Text("Error: TXT file URI or FileName not provided.")
                        }
                    }

                    composable(
                        route = Screen.CsvFileAnalysisScreen.route + "/{fileUri}/{fileName}",
                        arguments = listOf(
                            navArgument("fileUri") { type = NavType.StringType },
                            navArgument("fileName") { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val encodedFileUriString = backStackEntry.arguments?.getString("fileUri")
                        val encodedFileNameString = backStackEntry.arguments?.getString("fileName")
                        if (encodedFileUriString != null && encodedFileNameString != null) {
                            val decodedUriString = URLDecoder.decode(encodedFileUriString, StandardCharsets.UTF_8.toString())
                            val decodedFileNameString = URLDecoder.decode(encodedFileNameString, StandardCharsets.UTF_8.toString())
                            val fileUri = Uri.parse(decodedUriString)

                            val csvViewModel: com.example.datagrindset.viewmodel.CsvFileViewModel = viewModel(
                                factory = CsvFileViewModelFactory(application, fileUri, decodedFileNameString)
                            )
                            CsvFileAnalysisScreen(navController = navController, fileUri = fileUri, viewModel = csvViewModel)
                        } else {
                            Text("Error: CSV file URI or FileName not provided.")
                        }
                    }
                }
            }
        }
    }
}