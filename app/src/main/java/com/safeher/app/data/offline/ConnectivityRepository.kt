package com.safeher.app.data.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ConnectivityRepository private constructor(context: Context) {
    private val connectivityManager = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private val _isOnline = MutableStateFlow(hasInternet())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refresh(network)
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = refresh(network)
        override fun onLost(network: Network) { _isOnline.value = hasInternet() }
    }

    init { connectivityManager.registerDefaultNetworkCallback(callback) }

    fun currentlyOnline(): Boolean = hasInternet()

    private fun refresh(network: Network) {
        _isOnline.value = connectivityManager.getNetworkCapabilities(network)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    private fun hasInternet(): Boolean = connectivityManager.activeNetwork?.let { network ->
        connectivityManager.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    } ?: false

    companion object {
        @Volatile private var instance: ConnectivityRepository? = null
        fun get(context: Context): ConnectivityRepository = instance ?: synchronized(this) {
            instance ?: ConnectivityRepository(context).also { instance = it }
        }
    }
}