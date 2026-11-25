package com.cropcompass.app.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlin.coroutines.resume

@Suppress("MissingPermission")
private suspend fun locate(context: Context): Location? = withTimeoutOrNull(15_000) {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val providers = manager.getProviders(true).filter { it == LocationManager.GPS_PROVIDER || it == LocationManager.NETWORK_PROVIDER }
    val last = providers.mapNotNull { manager.getLastKnownLocation(it) }.maxByOrNull { it.time }
    if (last != null && System.currentTimeMillis()-last.time < 10*60*1000) return@withTimeoutOrNull last
    val provider = providers.firstOrNull { it == LocationManager.NETWORK_PROVIDER } ?: providers.firstOrNull() ?: return@withTimeoutOrNull null
    suspendCancellableCoroutine { continuation ->
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) { manager.removeUpdates(this); if (continuation.isActive) continuation.resume(location) }
        }
        manager.requestLocationUpdates(provider,0L,0f,listener,Looper.getMainLooper())
        continuation.invokeOnCancellation { manager.removeUpdates(listener) }
    }
}
@Composable fun LocationPicker(onLocation: (Double, Double) -> Unit) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var rationale by remember { mutableStateOf(false) }; var message by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }
    val load: () -> Unit = {
        scope.launch {
            busy = true
            try { val value=locate(context); if (value != null) { onLocation(value.latitude,value.longitude); message="Location found." } else message="Location unavailable. Select your region manually or enable location services." }
            catch (e: Exception) { if (e is CancellationException) throw e; message="Location unavailable. Please select manually." }
            finally { busy=false }
        }
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) load() else message="Location permission denied. Manual selection still works."
    }
    OutlinedButton(onClick = {
        if (ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED) load() else rationale=true
    },enabled=!busy) { Text(if(busy) "Finding location…" else "Use my location to suggest a mandi") }
    message?.let { Text(it,style=MaterialTheme.typography.bodySmall) }
    if(rationale) AlertDialog(onDismissRequest={rationale=false},title={Text("Use your location?")},text={Text("Crop Compass uses a location fix to suggest nearby mandis. It is not uploaded. You can always select manually.")},confirmButton={TextButton(onClick={rationale=false; permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION))}){Text("Continue")}},dismissButton={TextButton(onClick={rationale=false}){Text("Choose manually")}})
}
