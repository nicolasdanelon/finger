package com.finger

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.NetworkInterface

data class NetStatus(
    val connected: Boolean = false,
    val typeLabel: String = "",
    val transport: String = "NONE", // WIFI, CELLULAR, ETHERNET, VPN, OTHER, NONE
    val vpnOn: Boolean = false,
    val vpnTransport: Boolean = false
)

object NetworkHelper {

    fun getStatus(context: Context): NetStatus {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val none = context.getString(R.string.conn_none)
        val active = cm.activeNetwork
            ?: return NetStatus(typeLabel = none, vpnOn = isVpnInterfaceUp())
        val caps = cm.getNetworkCapabilities(active)

        if (caps == null) {
            return NetStatus(typeLabel = none, vpnOn = isVpnInterfaceUp())
        }

        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        // Transporte principal (prioridad visual: WIFI > CELL > ETHERNET)
        val (label, transport) = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> context.getString(R.string.conn_wifi) to "WIFI"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> context.getString(R.string.conn_mobile) to "CELLULAR"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> context.getString(R.string.conn_ethernet) to "ETHERNET"
            else -> context.getString(R.string.conn_connected) to "OTHER"
        }

        val vpnTransport = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        // VPN "prendida" = la red activa pasa por VPN, o hay interfaz tun activa
        // (algunas VPNs dejan la red base como activa, por eso chequeamos interfaz también)
        val vpnOn = vpnTransport || isVpnInterfaceUp() || hasVpnNetwork(cm)

        return NetStatus(
            connected = hasInternet || isConnectedUnvalidated(caps),
            typeLabel = if (hasInternet || isConnectedUnvalidated(caps)) label else none,
            transport = if (hasInternet || isConnectedUnvalidated(caps)) transport else "NONE",
            vpnOn = vpnOn,
            vpnTransport = vpnTransport
        )
    }

    private fun isConnectedUnvalidated(caps: NetworkCapabilities): Boolean =
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

    /** ¿Hay alguna red del sistema que use transporte VPN? */
    @Suppress("DEPRECATION")
    private fun hasVpnNetwork(cm: ConnectivityManager): Boolean {
        return try {
            cm.allNetworks.any { n ->
                cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            }
        } catch (_: Exception) {
            false
        }
    }

    /** Fallback: buscar interfaces tun0 / ppp0 / wg0 típicas de VPN. */
    fun isVpnInterfaceUp(): Boolean {
        return try {
            NetworkInterface.getNetworkInterfaces()?.toList()?.any { ni ->
                try {
                    ni.isUp && (
                            ni.name.startsWith("tun") ||
                                    ni.name.startsWith("ppp") ||
                                    ni.name.startsWith("wg") ||
                                    ni.name.contains("vpn", ignoreCase = true)
                            )
                } catch (_: Exception) {
                    false
                }
            } == true
        } catch (_: Exception) {
            false
        }
    }
}
