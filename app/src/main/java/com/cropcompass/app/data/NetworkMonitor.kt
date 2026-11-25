package com.cropcompass.app.data

import android.content.Context
import android.net.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class NetworkMonitor(context: Context) {
    private val manager=context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val online=callbackFlow {
        fun available(capabilities:NetworkCapabilities?)=capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)==true && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        fun publish(){trySend(available(manager.activeNetwork?.let{manager.getNetworkCapabilities(it)}))}
        val callback=object:ConnectivityManager.NetworkCallback(){override fun onAvailable(network:Network){publish()};override fun onLost(network:Network){trySend(false)};override fun onCapabilitiesChanged(network:Network,capabilities:NetworkCapabilities){trySend(available(capabilities))}}
        publish();manager.registerDefaultNetworkCallback(callback)
        awaitClose{manager.unregisterNetworkCallback(callback)}
    }.distinctUntilChanged()
}
