package com.shuva.jotter.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Whether the phone has an internet connection, for the offline banner. Sync itself waits on WorkManager's network constraint. */
@Singleton
class Connectivity @Inject constructor(@ApplicationContext context: Context) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val _online = MutableStateFlow(hasInternet(manager.activeNetwork))
    val online: StateFlow<Boolean> = _online

    init {
        // Lives as long as the app (a singleton), so it's never unregistered.
        manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                _online.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
            override fun onLost(network: Network) { _online.value = false }
        })
    }

    private fun hasInternet(network: Network?) =
        network != null && manager.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}
