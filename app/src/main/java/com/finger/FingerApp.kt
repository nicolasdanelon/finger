package com.finger

import android.app.Application
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper

/**
 * NetworkCallback a nivel app: cualquier cambio (WiFi, datos, VPN on/off)
 * refresca el widget (debounce 2s) y deja estado fresco para el tab Internet.
 * Funciona mientras el proceso viva; el WorkManager periódico + refresh
 * manual cubren el resto.
 */
class FingerApp : Application() {

    private val handler = Handler(Looper.getMainLooper())
    private val refreshTask = Runnable { UpdateWorker.enqueue(this) }

    override fun onCreate() {
        super.onCreate()
        UpdateWorker.schedulePeriodic(this)

        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        try {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = scheduleRefresh()
                override fun onLost(network: Network) = scheduleRefresh()
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
                    scheduleRefresh()
                override fun onLinkPropertiesChanged(network: Network, props: LinkProperties) =
                    scheduleRefresh()
            })
        } catch (_: Exception) {
            // Sin callback no pasa nada grave: queda el periódico + manual.
        }
    }

    private fun scheduleRefresh() {
        handler.removeCallbacks(refreshTask)
        handler.postDelayed(refreshTask, 2000)
    }
}
