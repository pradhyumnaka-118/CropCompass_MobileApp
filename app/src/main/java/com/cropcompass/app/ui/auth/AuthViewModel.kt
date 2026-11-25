package com.cropcompass.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cropcompass.app.AppContainer
import com.cropcompass.app.data.model.UserProfile
import com.cropcompass.app.util.Validation
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AuthState(val initializing: Boolean = true, val profile: UserProfile? = null, val guest: Boolean = false, val loading: Boolean = false, val error: String? = null, val district: String = "Kolar")
class AuthViewModel(private val container: AppContainer) : ViewModel() {
    private val mutable = MutableStateFlow(AuthState())
    val state = mutable.asStateFlow()
    val configured = container.accounts.configured
    init { viewModelScope.launch {
        try { mutable.value = AuthState(initializing = false, profile = container.accounts.profile(), guest = container.preferences.guest.first(), district = container.preferences.district.first()) }
        catch (e: Exception) { if (e is CancellationException) throw e; mutable.value = AuthState(initializing = false, error = "Could not restore your profile. Sign in again when connected.") }
    } }
    fun submit(register: Boolean, name: String, email: String, password: String, confirm: String, district: String) {
        if (state.value.loading) return
        val error = if (register) Validation.registration(name, email, password, confirm, district) else Validation.login(email, password)
        if (error != null) { mutable.update { it.copy(error = error) }; return }
        viewModelScope.launch {
            mutable.update { it.copy(loading = true, error = null) }
            try {
                val user = if (register) container.accounts.register(name, email, password, district) else container.accounts.login(email, password)
                container.preferences.guest(false)
                mutable.value = AuthState(initializing = false, profile = user)
                com.cropcompass.app.data.WorkScheduler.reports(container.context)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                val message = when (e) {
                    is FirebaseNetworkException -> "Could not connect. Check your internet connection and try again."
                    is FirebaseTooManyRequestsException -> "Too many attempts. Please wait before trying again."
                    is FirebaseAuthUserCollisionException -> "An account already uses this email. Please sign in."
                    is FirebaseAuthInvalidCredentialsException, is FirebaseAuthInvalidUserException -> "Email or password is incorrect. Please try again."
                    else -> "Could not complete your request. Check your connection and try again."
                }
                mutable.update { it.copy(loading = false, error = message) }
            }
        }
    }
    fun guest() { viewModelScope.launch {
        try {
            container.accounts.logout()
            container.preferences.guest(true)
            mutable.value = AuthState(initializing = false, guest = true, district = container.preferences.district.first())
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            mutable.update { it.copy(error = "Could not save guest preferences. Please try again.") }
        }
    } }
    fun logout() { viewModelScope.launch {
        container.accounts.logout()
        try { container.preferences.guest(false) }
        catch (e: Exception) { if (e is CancellationException) throw e }
        mutable.value = AuthState(initializing = false)
    } }
    fun district(value: String) { viewModelScope.launch {
        try {
            if (state.value.profile != null) container.accounts.updateDistrict(value)
            container.preferences.district(value)
            mutable.update { it.copy(profile = it.profile?.copy(district = value), district = value, error = null) }
        } catch (e: Exception) { if (e is CancellationException) throw e; mutable.update { it.copy(error = "District could not be saved. Try again when connected.") } }
    } }
}
