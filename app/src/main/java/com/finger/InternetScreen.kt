package com.finger

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DotOk = Color(0xFF4CAF50)
private val DotBad = Color(0xFFF44336)
private val DotIdle = Color(0xFF9E9E9E)

@Composable
private fun StatusDot(color: Color) {
    Box(Modifier.size(10.dp).background(color, CircleShape))
}

/** Tab Internet: conexión, IP pública (ip.x9.ar), VPN. Misma data que el widget. */
@Composable
fun InternetScreen() {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val activity = ctx as? Activity
    val scope = rememberCoroutineScope()

    var connLabel by remember { mutableStateOf("…") }
    var connOk by remember { mutableStateOf(false) }
    var ip by remember { mutableStateOf("…") }
    var vpnOn by remember { mutableStateOf(false) }
    var vpnDetail by remember { mutableStateOf("") }
    var updated by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var showPinHint by remember { mutableStateOf(false) }

    fun refresh() {
        if (loading) return
        loading = true
        showPinHint = false
        connLabel = "…"
        ip = appCtx.getString(R.string.main_querying_ip)
        vpnDetail = "…"
        scope.launch {
            val status = withContext(Dispatchers.IO) { NetworkHelper.getStatus(appCtx) }
            connLabel = status.typeLabel.ifEmpty { appCtx.getString(R.string.conn_none) }
            connOk = status.connected
            vpnOn = status.vpnOn
            vpnDetail = appCtx.getString(
                when {
                    status.vpnTransport -> R.string.vpn_detail_transport
                    status.vpnOn -> R.string.vpn_detail_iface
                    else -> R.string.vpn_detail_none
                }
            )
            ip = if (!status.connected) {
                appCtx.getString(R.string.ip_no_network)
            } else {
                withContext(Dispatchers.IO) { IpRepository.fetchPublicIp() }
                    .getOrNull()?.ip ?: appCtx.getString(R.string.ip_error)
            }
            updated = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            // También refrescar el widget real
            UpdateWorker.enqueue(appCtx)
            loading = false
        }
    }

    fun pinWidget() {
        val mgr = activity?.getSystemService(AppWidgetManager::class.java)
        val provider = ComponentName(appCtx, InetWidgetProvider::class.java)
        if (mgr?.isRequestPinAppWidgetSupported == true) {
            mgr.requestPinAppWidget(provider, null, null)
        } else {
            showPinHint = true
        }
    }

    // Recargar al entrar al tab y ante cambios de red (igual patrón que FingerScreen)
    LaunchedEffect(Unit) { refresh() }
    DisposableEffect(ctx) {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { refresh() }
            override fun onLost(network: Network) { refresh() }
            override fun onCapabilitiesChanged(n: Network, c: NetworkCapabilities) { refresh() }
        }
        try {
            cm.registerDefaultNetworkCallback(cb)
        } catch (_: Exception) {
        }
        onDispose {
            try {
                cm.unregisterNetworkCallback(cb)
            } catch (_: Exception) {
            }
        }
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.main_subtitle), style = MaterialTheme.typography.titleMedium)

        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(if (connOk) DotOk else DotBad)
                    Spacer(Modifier.size(8.dp))
                    Text(connLabel, fontWeight = FontWeight.Bold)
                }
                Text(
                    stringResource(R.string.main_ip_label),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    ip,
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(if (vpnOn) DotOk else DotIdle)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        stringResource(if (vpnOn) R.string.vpn_state_on else R.string.vpn_state_off),
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(vpnDetail, style = MaterialTheme.typography.bodySmall)
                Text(
                    stringResource(R.string.widget_updated_label) + " " + updated,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { refresh() }, enabled = !loading) {
                Text(stringResource(R.string.main_refresh))
            }
            OutlinedButton(onClick = { pinWidget() }) {
                Text(stringResource(R.string.main_add_widget))
            }
        }

        if (showPinHint) {
            Text(
                stringResource(R.string.main_hint_manual),
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            Text(
                stringResource(R.string.main_hint),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
