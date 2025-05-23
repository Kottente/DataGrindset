package com.example.datagrindset.ui.navigation

import android.net.Uri
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class Screen(val route: String) {
    object FileManagerScreen : Screen("fileManager")
    object TxtFileAnalysisScreen : Screen("txtAnalysisScreen") {
        // Route will be "txtAnalysisScreen/{fileUri}/{fileName}"
        fun createRoute(fileUri: Uri, fileName: String): String {
            val encodedUri = URLEncoder.encode(fileUri.toString(), StandardCharsets.UTF_8.toString())
            val encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString())
            return "txtAnalysisScreen/$encodedUri/$encodedFileName"
        }
    }
    object CsvFileAnalysisScreen : Screen("csvAnalysisScreen") {
        // Route will be "csvAnalysisScreen/{fileUri}/{fileName}"
        fun createRoute(fileUri: Uri, fileName: String): String {
            val encodedUri = URLEncoder.encode(fileUri.toString(), StandardCharsets.UTF_8.toString())
            val encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString())
            return "csvAnalysisScreen/$encodedUri/$encodedFileName"
        }
    }
}