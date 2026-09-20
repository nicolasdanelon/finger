package com.finger

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object IpRepository {
    const val IP_URL = "https://ip.x9.ar/ip"
    const val ALL_JSON_URL = "https://ip.x9.ar/all.json"
    const val IFCONFIG_ME_URL = "https://ifconfig.me/ip"

    // Acepta IPv4 o IPv6 (rechaza páginas de error HTML).
    private val IP_LIKE = Regex("^[0-9a-fA-F.:]+$")

    data class PublicIp(val ip: String, val raw: String = ip)

    /**
     * Trae la IP pública desde afuera. Orden: ip.x9.ar/all.json,
     * ip.x9.ar/ip plano, y si x9 se cae, https://ifconfig.me/ip.
     */
    suspend fun fetchPublicIp(): Result<PublicIp> = withContext(Dispatchers.IO) {
        var lastError: Exception = Exception("No IP source available")

        // 1. Principal: ip.x9.ar JSON (1 sola llamada, trae todo).
        try {
            val jsonText = httpGet(ALL_JSON_URL)
            val ip = cleanIp(JSONObject(jsonText).optString("ip", ""))
            if (ip != null) return@withContext Result.success(PublicIp(ip, jsonText))
        } catch (e: Exception) {
            lastError = e
        }

        // 2. ip.x9.ar plano.
        try {
            val ip = cleanIp(httpGet(IP_URL))
            if (ip != null) return@withContext Result.success(PublicIp(ip))
        } catch (e: Exception) {
            lastError = e
        }

        // 3. Backup: ifconfig.me plano.
        try {
            val ip = cleanIp(httpGet(IFCONFIG_ME_URL))
            if (ip != null) return@withContext Result.success(PublicIp(ip))
            Result.failure(lastError)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Primera línea no vacía que parezca IP, o null. */
    private fun cleanIp(body: String): String? {
        val candidate = body.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
        return if (candidate != null && IP_LIKE.matches(candidate)) candidate else null
    }

    private fun httpGet(urlStr: String): String {
        val url = URL(urlStr)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json, text/plain, */*")
            setRequestProperty("User-Agent", "finger-android/1.0")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw Exception("HTTP $code de $urlStr")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
