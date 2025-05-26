package com.example.datagrindset.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.datagrindset.R // Import R class for resources
import com.example.datagrindset.ui.theme.DataGrindsetTheme
import java.util.Locale
import androidx.core.net.toUri

data class AppTheme(val name: String, val description: String)
data class AppLanguage(val code: String, val displayNameResId: Int)

val availableThemes = listOf(
    AppTheme("Classic", "Orange/Black/Brown"),
    AppTheme("Sky", "Blue/White/Black"),
    AppTheme("Forest Lagoon", "Blue/Green/Brown"),
    AppTheme("Noir & Blanc", "Black/White"),
    AppTheme("Strawberry Milk", "Pink/White"),
    AppTheme("Strawberry & Kiwi", "Pink/Lite Green")
)

val availableLanguages = listOf(
    AppLanguage("en", R.string.settings_lang_english),
    AppLanguage("ru", R.string.settings_lang_russian),
    AppLanguage("el", R.string.settings_lang_greek)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    onLanguageSelected: (String) -> Unit,
    currentLanguageCode: String
) {
    val context = LocalContext.current

    var isUserLoggedIn by remember { mutableStateOf(false) }
    var selectedTheme by remember { mutableStateOf(availableThemes.first()) }
    var isThemeDropdownExpanded by remember { mutableStateOf(false) }

    val currentSelectedLanguageObject = remember(currentLanguageCode) {
        availableLanguages.find { it.code == currentLanguageCode } ?: availableLanguages.first()
    }
    var isLanguageDropdownExpanded by remember { mutableStateOf(false) }

    // Resolve strings that will be used in non-composable lambdas (like onClick)
    val signedOutToastText = stringResource(R.string.settings_signed_out_toast)
    val signInPlaceholderToastText = stringResource(R.string.settings_sign_in_placeholder_toast)
    val signUpPlaceholderToastText = stringResource(R.string.settings_sign_up_placeholder_toast)
    val noEmailAppToastText = stringResource(R.string.settings_no_email_app_toast)


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back_button_desc)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(all = 16.dp),
        ) {
            // --- Authentication Section ---
            SettingsSectionTitle(title = stringResource(R.string.settings_auth_section_title))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isUserLoggedIn) {
                    Button(onClick = {
                        isUserLoggedIn = false
                        Toast.makeText(context, signedOutToastText, Toast.LENGTH_SHORT).show()
                    }) {
                        Text(stringResource(R.string.settings_sign_out_button))
                    }
                } else {
                    Button(onClick = {
                        Toast.makeText(context, signInPlaceholderToastText, Toast.LENGTH_SHORT).show()
                    }) {
                        Text(stringResource(R.string.settings_sign_in_button))
                    }
                    OutlinedButton(onClick = {
                        Toast.makeText(context, signUpPlaceholderToastText, Toast.LENGTH_SHORT).show()
                    }) {
                        Text(stringResource(R.string.settings_sign_up_button))
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // --- Theme Selection Section ---
            SettingsSectionTitle(title = stringResource(R.string.settings_theme_section_title))
            Text("${stringResource(R.string.settings_current_theme_label)}: ${selectedTheme.name} (${selectedTheme.description})")
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { isThemeDropdownExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(selectedTheme.name)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(R.string.settings_select_theme_desc))
                }
                DropdownMenu(
                    expanded = isThemeDropdownExpanded,
                    onDismissRequest = { isThemeDropdownExpanded = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    availableThemes.forEach { theme ->
                        // Resolve formatted string outside lambda if possible, or pass args to Toast
                        val themeAppliedText = stringResource(R.string.settings_theme_applied_toast, theme.name)
                        DropdownMenuItem(
                            text = { Text("${theme.name} (${theme.description})") },
                            onClick = {
                                selectedTheme = theme
                                isThemeDropdownExpanded = false
                                Toast.makeText(context, themeAppliedText, Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // --- Language Selection Section ---
            SettingsSectionTitle(title = stringResource(R.string.settings_lang_section_title))
            Text("${stringResource(R.string.settings_current_lang_label)}: ${stringResource(currentSelectedLanguageObject.displayNameResId)}")
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { isLanguageDropdownExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(currentSelectedLanguageObject.displayNameResId))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(R.string.settings_select_lang_desc))
                }
                DropdownMenu(
                    expanded = isLanguageDropdownExpanded,
                    onDismissRequest = { isLanguageDropdownExpanded = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    availableLanguages.forEach { language ->
                       // val languageDisplayName = stringResource(language.displayNameResId)
                        //val langSetToastText = stringResource(R.string.settings_lang_set_toast, languageDisplayName)
                        DropdownMenuItem(
                            text = { Text(stringResource(language.displayNameResId)) },
                            onClick = {
                                isLanguageDropdownExpanded = false
                                if (language.code != currentLanguageCode) {
                                    onLanguageSelected(language.code)
                                    //Toast.makeText(context, langSetToastText, Toast.LENGTH_LONG).show()
                                }
                            }
                        )
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // --- Support Section ---
            SettingsSectionTitle(title = stringResource(R.string.settings_support_section_title))
            Button(
                onClick = {
                    val email = "vusmailed@mail.ru"
                    val subject = "App Support Request"
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = "mailto:".toUri()
                        putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
                        putExtra(Intent.EXTRA_SUBJECT, subject)
                    }
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, noEmailAppToastText, Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_contact_support_button))
            }
        }
    }
}

@Composable
fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Preview(showBackground = true, name = "Settings Screen (English)", locale = "en")
@Composable
fun SettingsScreenPreviewEnglish() {
    DataGrindsetTheme {
        SettingsScreen(
            navController = rememberNavController(),
            onLanguageSelected = {},
            currentLanguageCode = "en"
        )
    }
}

@Preview(showBackground = true, name = "Settings Screen (Russian)", locale = "ru")
@Composable
fun SettingsScreenPreviewRussian() {
    DataGrindsetTheme {
        SettingsScreen(
            navController = rememberNavController(),
            onLanguageSelected = {},
            currentLanguageCode = "ru"
        )
    }
}

@Preview(showBackground = true, name = "Settings Screen (Greek)", locale = "el")
@Composable
fun SettingsScreenPreviewGreek() {
    DataGrindsetTheme {
        SettingsScreen(
            navController = rememberNavController(),
            onLanguageSelected = {},
            currentLanguageCode = "el"
        )
    }
}