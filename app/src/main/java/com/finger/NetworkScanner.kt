package com.finger

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class NetDevice(
    val ip: String,
    val hostname: String,
    val mac: String,
    val rttMs: Long? = null,
    /** ping | tcp | arp | yo */
    val via: String = ""
)

data class LocalNet(
    val ownIp: String,
    val prefixLength: Short,
    val hosts: List<String>,
    val gateway: String?,
    val iface: String?
)

data class ScanStats(
    val durationMs: Long,
    val pingOk: Int,
    val tcpOk: Int,
    val arpEntries: Int
)

data class ScanResult(
    val devices: List<NetDevice>,
    val local: LocalNet?,
    val stats: ScanStats
)

data class PingResult(val ok: Boolean, val ms: Long?)

object NetworkScanner {

    /**
     * Red local vía LinkProperties de la red Wi-Fi activa (no pide LOCATION).
     * Fallback a enumerar NetworkInterface si LinkProperties no da IPv4.
     */
    fun getLocalNetwork(ctx: Context): LocalNet? {
        try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork
            if (net != null) {
                val caps = cm.getNetworkCapabilities(net)
                val lp = cm.getLinkProperties(net)
                val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                if (isWifi && lp != null) {
                    val gw = lp.routes
                        .mapNotNull { it.gateway as? Inet4Address }
                        .firstOrNull()?.hostAddress
                    for (la in lp.linkAddresses) {
                        val addr = la.address as? Inet4Address ?: continue
                        if (addr.isLoopbackAddress || addr.isLinkLocalAddress) continue
                        val prefix = la.prefixLength.toShort()
                        val hosts = buildHostList(addr, prefix) ?: continue
                        return LocalNet(addr.hostAddress!!, prefix, hosts, gw, lp.interfaceName)
                    }
                }
            }
        } catch (_: Exception) { }
        // Fallback clásico por interfaces (wlan primero)
        try {
            val ifaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: return null
            val sorted = ifaces.sortedBy {
                if (it.name.contains("wlan", ignoreCase = true)) 0 else 1
            }
            for (iface in sorted) {
                if (!iface.isUp || iface.isLoopback || iface.isVirtual) continue
                for (ia in iface.interfaceAddresses) {
                    val addr = ia.address as? Inet4Address ?: continue
                    if (addr.isLoopbackAddress || addr.isLinkLocalAddress) continue
                    if (!addr.isSiteLocalAddress) continue
                    val hosts = buildHostList(addr, ia.networkPrefixLength) ?: continue
                    return LocalNet(addr.hostAddress!!, ia.networkPrefixLength, hosts, null, iface.name)
                }
            }
        } catch (_: Exception) { }
        return null
    }

    /** Máx 254 hosts: si máscara < /24 se escanea solo el /24 propio. */
    private fun buildHostList(ownIp: Inet4Address, prefix: Short): List<String>? {
        val ipInt = inetToInt(ownIp)
        val effectivePrefix = if (prefix < 24) 24.toShort() else prefix
        val mask = if (effectivePrefix == 0.toShort()) 0
            else (-1 shl (32 - effectivePrefix.toInt()))
        val network = ipInt and mask
        val broadcast = network or mask.inv()
        if (broadcast - network > 256) return null
        val list = ArrayList<String>(254)
        var cur = network + 1
        while (cur < broadcast) {
            if (cur != ipInt) list.add(intToInet(cur))
            cur++
        }
        return list
    }

    private fun inetToInt(addr: Inet4Address): Int {
        val b = addr.address
        return ((b[0].toInt() and 0xFF) shl 24) or
               ((b[1].toInt() and 0xFF) shl 16) or
               ((b[2].toInt() and 0xFF) shl 8) or
               (b[3].toInt() and 0xFF)
    }

    private fun intToInet(v: Int): String =
        "${(v ushr 24) and 0xFF}.${(v ushr 16) and 0xFF}.${(v ushr 8) and 0xFF}.${v and 0xFF}"

    fun ipToLong(ip: String): Long {
        return try {
            val p = ip.split(".")
            (p[0].toLong() shl 24) + (p[1].toLong() shl 16) + (p[2].toLong() shl 8) + p[3].toLong()
        } catch (_: Exception) { Long.MAX_VALUE }
    }

    /**
     * Ping del sistema (/system/bin/ping). Funciona sin root, a diferencia
     * de InetAddress.isReachable que en Android suele devolver false siempre.
     * Bloqueante: llamar desde Dispatchers.IO.
     */
    fun pingOnce(ip: String, timeoutSec: Int = 1): PingResult {
        return try {
            val proc = ProcessBuilder("ping", "-c", "1", "-W", timeoutSec.toString(), ip)
                .redirectErrorStream(true)
                .start()
            val out = proc.inputStream.bufferedReader().readText()
            val code = proc.waitFor()
            if (code == 0) {
                val ms = Regex("""time=([\d.]+)""").find(out)
                    ?.groupValues?.get(1)?.toDoubleOrNull()?.toLong()
                PingResult(true, ms)
            } else {
                PingResult(false, null)
            }
        } catch (_: Exception) {
            PingResult(false, null)
        }
    }

    private fun tcpAlive(ip: String): Boolean {
        for (port in intArrayOf(80, 443, 445, 22, 53, 139, 8080, 8443, 8000, 9100, 554, 1883)) {
            try {
                Socket().use { s ->
                    s.connect(InetSocketAddress(ip, port), 220)
                    return true
                }
            } catch (_: Exception) { }
        }
        return false
    }

    /** ARP del kernel + `ip neigh show` (algunos equipos restringen /proc). */
    fun readArpTable(): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        try {
            val f = File("/proc/net/arp")
            if (f.canRead()) {
                f.readLines().drop(1).forEach { line ->
                    val parts = line.trim().split(Regex("\\s+"))
                    if (parts.size >= 4) {
                        val mac = parts[3].lowercase()
                        if (mac != "00:00:00:00:00:00" && mac.contains(":")) {
                            map.putIfAbsent(parts[0], mac)
                        }
                    }
                }
            }
        } catch (_: Exception) { }
        try {
            val proc = ProcessBuilder("ip", "neigh", "show")
                .redirectErrorStream(true)
                .start()
            val out = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            Regex("""(\d+\.\d+\.\d+\.\d+).*?lladdr\s+([0-9a-fA-F:]{17})""")
                .findAll(out)
                .forEach { m ->
                    val mac = m.groupValues[2].lowercase()
                    if (mac != "00:00:00:00:00:00") map.putIfAbsent(m.groupValues[1], mac)
                }
        } catch (_: Exception) { }
        return map
    }

    private fun resolveHostname(ip: String): String {
        return try {
            val name = InetAddress.getByName(ip).canonicalHostName?.trim().orEmpty()
            if (name.isEmpty() || name == ip) "desconocido" else name
        } catch (_: Exception) {
            "desconocido"
        }
    }

    private fun sameSubnet(ip: String, local: LocalNet): Boolean {
        return try {
            val own = InetAddress.getByName(local.ownIp) as Inet4Address
            val other = InetAddress.getByName(ip) as? Inet4Address ?: return false
            val prefix = if (local.prefixLength < 24) 24 else local.prefixLength.toInt()
            val mask = -1 shl (32 - prefix)
            (inetToInt(own) and mask) == (inetToInt(other) and mask)
        } catch (_: Exception) { false }
    }

    /**
     * Escaneo en 3 fases:
     *  1) ping binario (detecta casi todo, puebla ARP),
     *  2) TCP connect (para los que bloquean ICMP pero tienen puertos abiertos),
     *  3) resto de la tabla ARP en la subred (silenciosos: no responden nada
     *     pero el kernel los vio — antes estos se perdían).
     */
    suspend fun scan(
        ctx: Context,
        onFound: (NetDevice) -> Unit = {},
        onProgress: (scanned: Int, total: Int) -> Unit = { _, _ -> }
    ): ScanResult =
        withContext(Dispatchers.IO) {
            val t0 = System.currentTimeMillis()
            val local = getLocalNetwork(ctx)
                ?: return@withContext ScanResult(emptyList(), null, ScanStats(0, 0, 0, 0))
            val alive = ConcurrentHashMap<String, NetDevice>()
            var pingOk = 0
            var tcpOk = 0

            // Fase 1: un ping por IP a TODO el rango, en paralelo.
            // Esto puebla la tabla ARP del kernel con cada equipo que responde.
            val semPing = Semaphore(100)
            val scanned = AtomicInteger(0)
            val total = local.hosts.size
            local.hosts.map { ip ->
                async {
                    ensureActive()
                    semPing.withPermit {
                        try {
                            val r = pingOnce(ip)
                            if (r.ok) {
                                pingOk++
                                val dev = NetDevice(ip, resolveHostname(ip), readArpTable()[ip] ?: "—", r.ms, "ping")
                                alive[ip] = dev
                                onFound(dev)
                            }
                        } finally {
                            onProgress(scanned.incrementAndGet(), total)
                        }
                    }
                }
            }.awaitAll()

            // Fase 2: TCP para los que no respondieron ping
            val missing = local.hosts.filter { !alive.containsKey(it) }
            val semTcp = Semaphore(64)
            missing.map { ip ->
                async {
                    ensureActive()
                    semTcp.withPermit {
                        if (tcpAlive(ip)) {
                            tcpOk++
                            val dev = NetDevice(ip, resolveHostname(ip), readArpTable()[ip] ?: "—", null, "tcp")
                            alive[ip] = dev
                            onFound(dev)
                        }
                    }
                }
            }.awaitAll()

            // Fase 3: ARP restante en subred (equipos mudos pero vistos por el kernel)
            val arp = readArpTable()
            for ((ip, mac) in arp) {
                ensureActive()
                if (alive.containsKey(ip)) continue
                if (ip == local.ownIp) continue
                if (!sameSubnet(ip, local)) continue
                val dev = NetDevice(ip, resolveHostname(ip), mac, null, "arp")
                alive[ip] = dev
                onFound(dev)
            }

            val arpFinal = readArpTable()
            val enriched = alive.values
                .map { it.copy(mac = arpFinal[it.ip] ?: it.mac) }
                .sortedBy { ipToLong(it.ip) }
            val devices = listOf(NetDevice(local.ownIp, "este equipo", "—", null, "yo")) + enriched
            val stats = ScanStats(System.currentTimeMillis() - t0, pingOk, tcpOk, arpFinal.size)
            ScanResult(devices, local, stats)
        }
}
