package com.mersadai.app.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class NetworkMonitor(context: Context) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)

    val isOnline: Flow<Boolean> = callbackFlow {
        fun emitCurrent() {
            val network = manager.activeNetwork
            val capabilities = manager.getNetworkCapabilities(network)
            trySend(capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = emitCurrent()
            override fun onLost(network: Network) = emitCurrent()
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = emitCurrent()
        }
        manager.registerDefaultNetworkCallback(callback)
        emitCurrent()
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }
}
