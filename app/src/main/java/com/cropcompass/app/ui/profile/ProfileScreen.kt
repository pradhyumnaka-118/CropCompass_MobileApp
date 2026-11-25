package com.cropcompass.app.ui.profile

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.cropcompass.app.ui.auth.AuthState
import com.cropcompass.app.ui.MarketUiState
import com.cropcompass.app.ui.MarketViewModel
import com.cropcompass.app.ui.components.*

@Composable fun ProfileScreen(auth: AuthState, state: MarketUiState, vm: MarketViewModel, onLogout: () -> Unit, onAbout: () -> Unit = {}, onInsights: () -> Unit = {}) {
    var rate by rememberSaveable(state.settings.transportRate) { mutableStateOf(state.settings.transportRate.toString()) }
    var radius by rememberSaveable(state.settings.radiusKm) { mutableStateOf(if(state.settings.radiusKm>200) "All Karnataka" else state.settings.radiusKm.toInt().toString()) }
    Page {
        Heading(tr("Profile"),auth.profile?.name ?: "Guest demo")
        Text(auth.profile?.email ?: "Local data stays on this device. Sign in to sync your own reports.")
        RegionSelector(state,vm::region)
        Text("Language / ಭಾಷೆ",style=MaterialTheme.typography.titleMedium)
        Row {
            TextButton(onClick={vm.language("en")},modifier=Modifier.testTag("language-en")){Text("English")}
            TextButton(onClick={vm.language("kn")},modifier=Modifier.testTag("language-kn")){Text("ಕನ್ನಡ")}
        }
        OutlinedTextField(rate,{rate=it},label={Text("Transport estimate (₹ / quintal / km)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth())
        Notice("Default ₹0.05 / quintal / km is an editable assumption, not a freight quote. Estimated road distance = straight-line distance × 1.3.")
        Choice("Search radius (km)",radius,listOf("25","50","100","150","200","All Karnataka")){radius=it}
        Button(onClick={vm.configure(state.settings.language,rate.toDoubleOrNull() ?: Double.NaN,if(radius=="All Karnataka")99999.0 else radius.toDoubleOrNull() ?: 200.0)}){Text("Save cost settings")}
        Text("Data: ${state.snapshot.info?.source ?: "Loading"}\nData as of ${state.snapshot.info?.lastDate ?: "—"}")
        if(vm.container.remote.configured && state.snapshot.info?.dataSource=="real") OutlinedButton(onClick=vm::refresh,enabled=!state.refreshing){Text(if(state.refreshing) "Refreshing…" else "Refresh official observations")}
        OutlinedButton(onClick=onAbout){Text(tr("About the data"))}
        OutlinedButton(onClick=onInsights){Text(tr("Market insights"))}
        OutlinedButton(onClick=onLogout){Text(if(auth.guest) "Leave guest demo" else "Log out")}
    }
}
