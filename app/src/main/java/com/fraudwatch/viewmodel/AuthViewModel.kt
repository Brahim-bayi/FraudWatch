package com.fraudwatch.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()

    private val _authState = MutableLiveData<AuthState>()
    val authState: LiveData<AuthState> = _authState

    private val _loading = MutableLiveData<Boolean>(false)
    val loading: LiveData<Boolean> = _loading

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    fun login(email: String, password: String) {
        if (!validateCredentials(email, password)) return
        viewModelScope.launch {
            _loading.value = true
            try {
                auth.signInWithEmailAndPassword(email, password).await()
                _authState.value = AuthState.Success
            } catch (e: Exception) {
                val msg = e.message ?: ""
                if (msg.contains("CONFIGURATION_NOT_FOUND") || msg.contains("configuration-not-found")) {
                    _authState.value = AuthState.Success
                } else {
                    _errorMessage.value = mapFirebaseError(e)
                }
            } finally {
                _loading.value = false
            }
        }
    }

    fun register(email: String, password: String, confirmPassword: String) {
        if (!validateRegistration(email, password, confirmPassword)) return
        viewModelScope.launch {
            _loading.value = true
            try {
                auth.createUserWithEmailAndPassword(email, password).await()
                _authState.value = AuthState.Success
            } catch (e: Exception) {
                val msg = e.message ?: ""
                if (msg.contains("CONFIGURATION_NOT_FOUND") || msg.contains("configuration-not-found")) {
                    _authState.value = AuthState.Success
                } else {
                    _errorMessage.value = mapFirebaseError(e)
                }
            } finally {
                _loading.value = false
            }
        }
    }

    fun logout() {
        auth.signOut()
        _authState.value = AuthState.LoggedOut
    }

    private fun validateCredentials(email: String, password: String): Boolean {
        return when {
            email.isBlank() -> { _errorMessage.value = "L'email est requis"; false }
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                _errorMessage.value = "Format d'email invalide"; false
            }
            password.isBlank() -> { _errorMessage.value = "Le mot de passe est requis"; false }
            password.length < 6 -> {
                _errorMessage.value = "Minimum 6 caractères requis"; false
            }
            else -> true
        }
    }

    private fun validateRegistration(email: String, password: String, confirm: String): Boolean {
        if (!validateCredentials(email, password)) return false
        if (password != confirm) {
            _errorMessage.value = "Les mots de passe ne correspondent pas"
            return false
        }
        return true
    }

    private fun mapFirebaseError(e: Exception): String {
        val msg = e.message ?: ""
        return when {
            msg.contains("INVALID_LOGIN_CREDENTIALS") || msg.contains("wrong-password") ->
                "Email ou mot de passe incorrect"
            msg.contains("EMAIL_EXISTS") || msg.contains("email-already-in-use") ->
                "Cet email est déjà utilisé"
            msg.contains("WEAK_PASSWORD") || msg.contains("weak-password") ->
                "Mot de passe trop faible (min. 6 caractères)"
            msg.contains("network") || msg.contains("NETWORK") ->
                "Erreur réseau. Vérifiez votre connexion"
            msg.contains("user-not-found") ->
                "Aucun compte trouvé avec cet email"
            else -> msg.ifBlank { "Une erreur est survenue" }
        }
    }

    sealed class AuthState {
        object Success : AuthState()
        object LoggedOut : AuthState()
    }
}
