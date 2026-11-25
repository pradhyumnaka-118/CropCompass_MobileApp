package com.cropcompass.app.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.cropcompass.app.R
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import com.cropcompass.app.ui.components.*
import com.cropcompass.app.util.Validation

@Composable fun AuthScreen(state: AuthState, configured: Boolean, register: Boolean, onSwitch: () -> Unit, onGuest: () -> Unit, onSubmit: (String, String, String, String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    // Passwords deliberately stay in memory, outside saved instance state.
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var district by rememberSaveable { mutableStateOf("Kolar") }
    Page {
        Spacer(Modifier.height(12.dp))
        Icon(Icons.Outlined.Eco, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(60.dp))
        Heading(if (register) "Grow with us." else "Welcome to\nCrop Compass", stringResource(R.string.tagline))
        Text(if (register) "Create your farmer account" else "Sign in to save commodities and report prices.", style = MaterialTheme.typography.titleMedium)
        if (!configured) Notice("Account services are not configured on this build. Explore the guest preview below.")
        state.error?.let { Notice(it, error = true) }
        if (register) OutlinedTextField(name, { name = it }, label = { Text("Full name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, modifier = Modifier.fillMaxWidth())
        if (register) {
            OutlinedTextField(confirm, { confirm = it }, label = { Text("Confirm password") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, modifier = Modifier.fillMaxWidth())
            Choice("District", district, Validation.districts) { district = it }
        }
        Button(onClick = { onSubmit(name, email, password, confirm, district) }, enabled = !state.loading && configured, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            if (state.loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Text(if (register) "Create account" else "Sign in")
        }
        TextButton(onClick = onSwitch, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) { Text(if (register) "Already registered? Sign in" else "New here? Create an account") }
        OutlinedButton(onClick = onGuest, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) { Text("Explore guest demo") }
        Text("This preview introduces Crop Compass. Sign in when account services are connected to save your profile.", style = MaterialTheme.typography.bodySmall)
    }
}
