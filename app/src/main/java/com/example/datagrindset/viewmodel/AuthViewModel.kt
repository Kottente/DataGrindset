package com.example.datagrindset.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.datagrindset.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class AuthResult(
    val user: FirebaseUser? = null,
    val error: String? = null,
    val isNewUser: Boolean? = null
)

open class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val googleSignInClient: GoogleSignInClient
    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    open val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _authResult = MutableStateFlow<AuthResult?>(null)
    val authResult: StateFlow<AuthResult?> = _authResult.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val TAG = "AuthViewModel"
    init {
        // Configure Google Sign In Client for signOut
        // We need the application context to get the string resource
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(application.getString(R.string.default_web_client_id))
            .requestEmail() // Request email to be available in GoogleSignInAccount
            .build()
        googleSignInClient = GoogleSignIn.getClient(application, gso)

        auth.addAuthStateListener { firebaseAuth ->
            _currentUser.value = firebaseAuth.currentUser
            if (firebaseAuth.currentUser != null) {
                Log.d(TAG, "Auth state changed: User is ${firebaseAuth.currentUser?.uid}, DisplayName: ${firebaseAuth.currentUser?.displayName}, Email: ${firebaseAuth.currentUser?.email}")
            } else {
                Log.d(TAG, "Auth state changed: User is null")
            }
        }
    }
    fun signUpWithEmailPassword(email: String, pass: String,nickname: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _authResult.value = null // Clear previous result
            try {
                val result = auth.createUserWithEmailAndPassword(email, pass).await()
                val user = result.user
                if (user != null) {
                    // Update profile with nickname
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(nickname)
                        .build()
                    try {
                        user.updateProfile(profileUpdates).await()
                        Log.d(TAG, "User profile updated with nickname: $nickname")
                        // Re-fetch the user to ensure currentUser flow gets the updated profile
                        _currentUser.value = auth.currentUser // This will trigger listeners with the updated user
                        _authResult.value = AuthResult(user = auth.currentUser)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to update user profile", e)
                        _authResult.value = AuthResult(user = user, error = "Account created, but failed to set nickname: ${e.message}")
                    }
                } else {
                    _authResult.value = AuthResult(error = "Sign up succeeded but user was null")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Sign up failed", e)
                _authResult.value = AuthResult(error = e.message ?: "Sign up failed")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun signInWithEmailPassword(email: String, pass: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _authResult.value = null // Clear previous result
            try {
                val result = auth.signInWithEmailAndPassword(email, pass).await()
                _currentUser.value = result.user
                _authResult.value = AuthResult(user = result.user)
            } catch (e: Exception) {
                _authResult.value = AuthResult(error = e.message ?: "Sign in failed")
            } finally {
                _isLoading.value = false
            }
        }
    }
    fun signInWithGoogleToken(idToken: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _authResult.value = null
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val authTaskResult = auth.signInWithCredential(credential).await()
                val user = authTaskResult.user
                val isNewUser = authTaskResult.additionalUserInfo?.isNewUser ?: false

                Log.d(TAG, "Google sign-in successful: User is ${user?.uid}, DisplayName: ${user?.displayName}, Email: ${user?.email}, Is new: $isNewUser")
                _currentUser.value = user
                _authResult.value = AuthResult(user = user, isNewUser = isNewUser)

                // If it's a new user from Google Sign-In and they don't have a display name yet
                // (e.g., if Google account has no public name or it wasn't fetched),
                // you might want to prompt them to set a nickname in your app.
                // For now, Firebase usually populates displayName from the Google account.

            } catch (e: Exception) {
                Log.e(TAG, "Google sign-in failed", e)
                _authResult.value = AuthResult(error = e.message ?: "Google sign-in failed")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun signOut() {
        Log.d(TAG, "Signing out user: ${_currentUser.value?.uid}")
        // Sign out from Google first to clear the Google account session for the app
        googleSignInClient.signOut().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d(TAG, "GoogleSignInClient signOut successful.")
            } else {
                Log.w(TAG, "GoogleSignInClient signOut failed.", task.exception)
            }
            // Regardless of Google sign-out success, sign out from Firebase
            auth.signOut() // This will trigger AuthStateListener, which updates _currentUser
            _currentUser.value = null
            _authResult.value = null // Clear any previous auth result
            Log.d(TAG, "Firebase signOut initiated.")
        }
    }

    fun clearAuthResult() {
        _authResult.value = null
    }
}