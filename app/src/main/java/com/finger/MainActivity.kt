package com.finger

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FingerTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    FingerScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FingerScreen() {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val scope = rememberCoroutineScope()
    var onWifi by remember { mutableStateOf(isWifiConnected(ctx)) }
    var scanning by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf<List<NetDevice>>(emptyList()) }
    var netDesc by remember { mutableStateOf<String?>(null) }
    var rangeDesc by remember { mutableStateOf<String?>(null) }
    var scannedCount by remember { mutableIntStateOf(0) }
    var totalCount by remember { mutableIntStateOf(0) }
    var statsDesc by remember { mutableStateOf<String?>(null) }
    var scanJob by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(ctx) {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { onWifi = isWifiConnected(ctx) }
            override fun onLost(network: Network) { onWifi = isWifiConnected(ctx) }
            override fun onCapabilitiesChanged(n: Network, c: NetworkCapabilities) {
                onWifi = isWifiConnected(ctx)
            }
        }
        cm.registerDefaultNetworkCallback(cb)
        onDispose { try { cm.unregisterNetworkCallback(cb) } catch (_: Exception) {} }
    }

    fun startScan() {
        if (scanning) return
        scanning = true
        devices = emptyList()
        statsDesc = null
        scannedCount = 0
        totalCount = 0
        val local = NetworkScanner.getLocalNetwork(appCtx)
        rangeDesc = local?.hosts?.let { "${it.firstOrNull()} – ${it.lastOrNull()} (${it.size})" }
        netDesc = local?.let {
            "${it.ownIp}/${it.prefixLength}" +
                (it.gateway?.let { g -> " · gw $g" } ?: "") +
                (it.iface?.let { i -> " · $i" } ?: "")
        }
        scanJob = scope.launch {
            try {
                val result = NetworkScanner.scan(appCtx,
                    onFound = { dev ->
                        devices = (devices + dev).sortedBy { NetworkScanner.ipToLong(it.ip) }
                    },
                    onProgress = { scanned, total ->
                        scannedCount = scanned
                        totalCount = total
                    }
                )
                devices = result.devices
                result.local?.let {
                    netDesc = "${it.ownIp}/${it.prefixLength}" +
                        (it.gateway?.let { g -> " · gw $g" } ?: "") +
                        (it.iface?.let { i -> " · $i" } ?: "")
                }
                val s = result.stats
                statsDesc = "%.1fs · ping:%d tcp:%d arp:%d".format(
                    s.durationMs / 1000.0, s.pingOk, s.tcpOk, s.arpEntries
                )
            } finally {
                scanning = false
            }
        }
    }

    LaunchedEffect(onWifi) {
        if (onWifi && devices.isEmpty() && !scanning) startScan()
        if (!onWifi) { scanJob?.cancel(); scanning = false }
    }

    if (!onWifi) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Conectar a WiFi", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text("No hay conexión Wi-Fi activa.")
            Spacer(Modifier.height(24.dp))
            Button(onClick = {
                ctx.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }) {
                Text("Abrir ajustes Wi-Fi")
            }
        }
    } else {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(netDesc ?: "Red Wi-Fi", style = MaterialTheme.typography.titleMedium)
                    rangeDesc?.let {
                        Text("Rango $it", style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace)
                    }
                    Text(
                        if (scanning) "Ping $scannedCount/$totalCount… ${devices.size} vivos"
                        else "${devices.size} dispositivo(s)" + (statsDesc?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (scanning) {
                    OutlinedButton(onClick = { scanJob?.cancel(); scanning = false }) {
                        Text("Detener")
                    }
                } else {
                    Button(onClick = { startScan() }) { Text("Escanear") }
                }
            }
            if (scanning) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            // Ayuda si no se ve nada: causa típica = AP isolation / VPN / datos
            if (!scanning && devices.size <= 1 && statsDesc != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Text(
                        "Solo te ves a vos. Típico: AP/client isolation del router, " +
                        "VPN activa, o red de invitados.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            PullToRefreshBox(
                isRefreshing = scanning,
                onRefresh = { startScan() },
                modifier = Modifier.weight(1f)
            ) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(devices, key = { it.ip }) { d ->
                    var shown by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { shown = true }
                    val alpha by animateFloatAsState(
                        targetValue = if (shown) 1f else 0f,
                        animationSpec = tween(500)
                    )
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth().alpha(alpha),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    d.ip,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                d.rttMs?.let {
                                    Text("$it ms",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace)
                                }
                            }
                            Text(d.hostname, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            }
        }
    }
}

private fun isWifiConnected(ctx: Context): Boolean {
    val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val net = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(net) ?: return false
    return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
}
