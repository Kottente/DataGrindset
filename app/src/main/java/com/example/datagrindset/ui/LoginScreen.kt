package com.example.datagrindset.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.datagrindset.R
import com.example.datagrindset.viewmodel.AuthViewModel
import com.google.firebase.auth.FirebaseUser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    navController: NavController,
    authViewModel: AuthViewModel = viewModel(),
    onGoogleSignInClicked: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val context = LocalContext.current

    val authResult by authViewModel.authResult.collectAsState()
    val isLoading by authViewModel.isLoading.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsState()

    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            val greetingName = if (authResult?.isNewUser == true) {
                currentUser?.displayName ?: currentUser?.email // Use display name if new and available
            } else {
                currentUser?.displayName ?: currentUser?.email // Or for existing user
            }
            Toast.makeText(context, context.getString(R.string.login_welcome_back_toast, greetingName ?: "User"), Toast.LENGTH_SHORT).show()
            navController.navigate("fileManager") {
                popUpTo("login") { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(authResult) {
        authResult?.let { result ->
            if (result.error != null) {
                Toast.makeText(context, "Error: ${result.error}", Toast.LENGTH_LONG).show()
                authViewModel.clearAuthResult() // Clear the result to avoid re-showing toast
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.login_screen_title)) })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(R.string.login_welcome_message), style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.login_email_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.login_password_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (email.isNotBlank() && password.isNotBlank()) {
                        authViewModel.signInWithEmailPassword(email, password)
                    } else {
                        Toast.makeText(context, context.getString(R.string.login_fill_all_fields_toast), Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(stringResource(R.string.login_button_text))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onGoogleSignInClicked,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_google_logo), // You'll need to add this drawable
                    contentDescription = "Google sign in",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.login_google_button_text))
            }
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = { navController.navigate("signup") }) {
                Text(stringResource(R.string.login_no_account_prompt))
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = {
                // Navigate to fileManager without login - for guest access if you want it
                // For now, this is a placeholder or can be removed if guest access isn't desired.
                // If you want to allow access without login, you'd navigate to "fileManager"
                // and SettingsScreen would need to handle the null user state gracefully.

                //Toast.makeText(context, "Guest access (Not Implemented for Settings)", Toast.LENGTH_SHORT).show()
                navController.navigate("fileManager") {
                    popUpTo("login") { inclusive = true }
                    launchSingleTop = true
                }
            }) {
                Text(stringResource(R.string.login_guest_access_button))
            }
        }
    }
}