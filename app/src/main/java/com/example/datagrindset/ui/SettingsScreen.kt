package com.example.datagrindset.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.example.datagrindset.viewmodel.AuthViewModel
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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

// Updated to use the new string resource names I provided
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
    currentLanguageCode: String
) {
    val context = LocalContext.current
    val currentUser by authViewModel.currentUser.collectAsState()
    LaunchedEffect(currentUser) {
        Log.d("SettingsScreen", "Current user state: UID: ${currentUser?.uid}, Email: ${currentUser?.email}, DisplayName: ${currentUser?.displayName}")
    }
    var isUserLoggedIn by remember { mutableStateOf(false) }
    var selectedTheme by remember { mutableStateOf(availableThemes.first()) }
    var isThemeDropdownExpanded by remember { mutableStateOf(false) }

    val currentSelectedLanguageObject = remember(currentLanguageCode) {
        availableLanguages.find { it.code == currentLanguageCode }
            ?: availableLanguages.find { it.code == Locale.getDefault().language } // Fallback to system default if current is not in list
            ?: availableLanguages.first() // Ultimate fallback
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
                        //.verticalScroll(rememberScrollState()), // Added for scrollability
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
                        // Optionally, show UID for debugging or if nickname is blank
                        if (user.displayName.isNullOrBlank()) {
                            Text("${stringResource(R.string.settings_profile_uid_label)}: ${user.uid}")
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = {
                                authViewModel.signOut()
                                Toast.makeText(context, signedOutToastText, Toast.LENGTH_SHORT)
                                    .show()
                                // Navigate to login screen after sign out
                                navController.navigate("login") {
                                    popUpTo("fileManager") {
                                        inclusive = true
                                    } // Clear fileManager and everything above
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
                            // Optional: popUpTo("settings") { inclusive = true } to remove settings from backstack
                        }
                    }) {
                        Text(stringResource(R.string.settings_sign_in_button))
                    }
                    OutlinedButton(onClick = {
                        navController.navigate("signup") {
                            // Optional: popUpTo("settings") { inclusive = true }
                        }
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
                        DropdownMenuItem(
                            text = { Text(stringResource(language.displayNameResId)) },
                            onClick = {
                                isLanguageDropdownExpanded = false
                                if (language.code != currentLanguageCode) {
                                    onLanguageSelected(language.code)
                                    // Consider showing a toast after language change is fully applied by activity recreation
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
                    val email = "vusmailed@mail.ru" // Keep your actual email
                    val subject = "App Support Request" // Or make this a string resource
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

//@Preview(showBackground = true, name = "Settings Screen (Logged In)", locale = "en")
//@Composable
//fun SettingsScreenPreviewLoggedIn() {
//    // Fake AuthViewModel for preview
//    class FakeAuthViewModel(app: Application) : AuthViewModel(app) {
//        private val _fakeUser = MutableStateFlow<FirebaseUser?>(
//            object : FirebaseUser() {
//                override fun getEmail(): String = "preview@example.com"
//                override fun getUid(): String = "fakeuid"
//                // Implement other necessary abstract members if any, or use a mock framework
//            }
//        )
//        override val currentUser: StateFlow<FirebaseUser?> = _fakeUser
//    }
//    val context = LocalContext.current
//    DataGrindsetTheme {
//        SettingsScreen(
//            navController = rememberNavController(),
//            authViewModel = FakeAuthViewModel(context.applicationContext as Application),
//            onLanguageSelected = {},
//            currentLanguageCode = "en"
//        )
//    }
//}
//
//@Preview(showBackground = true, name = "Settings Screen (Logged Out)", locale = "en")
//@Composable
//fun SettingsScreenPreviewLoggedOut() {
//    class FakeAuthViewModelLoggedOut(app: Application) : AuthViewModel(app) {
//        private val _fakeUser = MutableStateFlow<FirebaseUser?>(null)
//        override val currentUser: StateFlow<FirebaseUser?> = _fakeUser
//    }
//    val context = LocalContext.current
//    DataGrindsetTheme {
//        SettingsScreen(
//            navController = rememberNavController(),
//            authViewModel = FakeAuthViewModelLoggedOut(context.applicationContext as Application),
//            onLanguageSelected = {},
//            currentLanguageCode = "en"
//        )
//    }
//}