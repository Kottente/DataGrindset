package com.example.datagrindset.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class AuthResult(
    val user: FirebaseUser? = null,
    val error: String? = null
)

open class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    open val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _authResult = MutableStateFlow<AuthResult?>(null)
    val authResult: StateFlow<AuthResult?> = _authResult.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val TAG = "AuthViewModel"
    init {
        auth.addAuthStateListener { firebaseAuth ->
            _currentUser.value = firebaseAuth.currentUser
            if (firebaseAuth.currentUser != null) {
                Log.d(TAG, "Auth state changed: User is ${firebaseAuth.currentUser?.uid}, DisplayName: ${firebaseAuth.currentUser?.displayName}")
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

    fun signOut() {
        auth.signOut()
        _currentUser.value = null
        _authResult.value = null
    }

    fun clearAuthResult() {
        _authResult.value = null
    }
}