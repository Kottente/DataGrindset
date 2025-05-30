package com.example.datagrindset.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ColorLens
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
import com.example.datagrindset.R
import com.example.datagrindset.ui.theme.DataGrindsetTheme
import java.util.Locale
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.datagrindset.ThemeOption
import com.example.datagrindset.viewmodel.AuthViewModel
import com.example.datagrindset.viewmodel.SettingsViewModel
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow


data class AppTheme(val name: String, val description: String)
data class AppLanguage(val code: String, val displayNameResId: Int)


val availableLanguages = listOf(
    AppLanguage("en", R.string.settings_lang_english),
    AppLanguage("ru", R.string.settings_lang_russian),
    AppLanguage("el", R.string.settings_lang_greek)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    authViewModel: AuthViewModel,
    onLanguageSelected: (String) -> Unit,
    currentLanguageCode: String,
    settingsViewModel: SettingsViewModel
) {
    val context = LocalContext.current
    val currentUser by authViewModel.currentUser.collectAsState()
    LaunchedEffect(currentUser) {
        Log.d("SettingsScreen", "Current user state: UID: ${currentUser?.uid}, Email: ${currentUser?.email}, DisplayName: ${currentUser?.displayName}")
    }
    var isUserLoggedIn by remember { mutableStateOf(false) }

    val availableThemesFromVM = settingsViewModel.availableThemes
    val selectedThemeName by settingsViewModel.selectedThemeName.collectAsStateWithLifecycle()
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }

    val currentSelectedLanguageObject = remember(currentLanguageCode) {
        availableLanguages.find { it.code == currentLanguageCode }
            ?: availableLanguages.find { it.code == Locale.getDefault().language }
            ?: availableLanguages.first()
    }
    var isLanguageDropdownExpanded by remember { mutableStateOf(false) }

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
                .padding(all = 4.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // --- Authentication Section ---
            SettingsSectionTitle(title = stringResource(R.string.settings_auth_section_title))


                if (currentUser != null) {
                    val user = currentUser!!
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(all = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_profile_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        user.displayName?.let { nickname ->
                            if (nickname.isNotBlank()) {
                                Text("${stringResource(R.string.settings_profile_nickname_label)}: $nickname")
                            }
                        }
                        user.email?.let { email ->
                            Text("${stringResource(R.string.settings_profile_email_label)}: $email")
                        }
                        if (user.displayName.isNullOrBlank()) {
                            Text("${stringResource(R.string.settings_profile_uid_label)}: ${user.uid}")
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = {
                                authViewModel.signOut()
                                Toast.makeText(context, signedOutToastText, Toast.LENGTH_SHORT)
                                    .show()
                                navController.navigate("login") {
                                    popUpTo("fileManager") {
                                        inclusive = true
                                    }
                                    launchSingleTop = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.settings_sign_out_button))
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                    Button(onClick = {
                        navController.navigate("login") {
                        }
                    }) {
                        Text(stringResource(R.string.settings_sign_in_button))
                    }
                    OutlinedButton(onClick = {
                        navController.navigate("signup") {
                        }
                    }) {
                        Text(stringResource(R.string.settings_sign_up_button))
                    }
                }

        }
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // --- Theme Selection Section ---
            Text(
                stringResource(R.string.settings_theme_section_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp)
            )
            Card(modifier = Modifier.fillMaxWidth().clickable { showThemeDialog = true }) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ColorLens, contentDescription = stringResource(R.string.settings_select_theme_desc), modifier = Modifier.padding(end = 12.dp))
                        Column {
                            Text(stringResource(R.string.settings_current_theme_label), style = MaterialTheme.typography.bodyLarge)
                            Text(selectedThemeName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = stringResource(R.string.settings_select_theme_desc))
                }
            }

            if (showThemeDialog) {
                AlertDialog(
                    onDismissRequest = { showThemeDialog = false },
                    title = { Text(stringResource(R.string.settings_select_theme_desc)) },
                    text = {
                        LazyColumn {
                            items(availableThemesFromVM.size) { index ->
                                val theme = availableThemesFromVM[index]
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            settingsViewModel.selectTheme(theme.name)
                                            showThemeDialog = false
                                        }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = theme.name == selectedThemeName,
                                        onClick = {
                                            settingsViewModel.selectTheme(theme.name)
                                            showThemeDialog = false
                                        }
                                    )
                                    Spacer(Modifier.width(16.dp))
                                    Column {
                                        Text(theme.name, style = MaterialTheme.typography.bodyLarge)
                                        Text(theme.description, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showThemeDialog = false }) {
                            Text(stringResource(R.string.lfm_cancel_button))
                        }
                    }
                )
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
                        DropdownMenuItem(
                            text = { Text(stringResource(language.displayNameResId)) },
                            onClick = {
                                isLanguageDropdownExpanded = false
                                if (language.code != currentLanguageCode) {
                                    onLanguageSelected(language.code)
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
