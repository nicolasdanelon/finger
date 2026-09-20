package com.finger

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object WidgetStore {
    private const val PREFS = "inet_widget_prefs"

    fun save(
        context: Context,
        status: NetStatus,
        publicIp: String?,
        ipError: Boolean = false
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("type_label", status.typeLabel)
            .putString("transport", status.transport)
            .putBoolean("connected", status.connected)
            .putBoolean("vpn", status.vpnOn)
            .putString("ip", publicIp ?: "—")
            .putBoolean("ip_error", ipError)
            .putLong("updated_at", System.currentTimeMillis())
            .apply()
    }

    fun snapshot(context: Context): Snapshot {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ts = p.getLong("updated_at", 0L)
        return Snapshot(
            typeLabel = p.getString("type_label", "…") ?: "…",
            transport = p.getString("transport", "NONE") ?: "NONE",
            connected = p.getBoolean("connected", false),
            vpnOn = p.getBoolean("vpn", false),
            ip = p.getString("ip", "…") ?: "…",
            ipError = p.getBoolean("ip_error", false),
            updatedLabel = if (ts == 0L) context.getString(R.string.widget_never) else
                SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ts))
        )
    }

    data class Snapshot(
        val typeLabel: String,
        val transport: String,
        val connected: Boolean,
        val vpnOn: Boolean,
        val ip: String,
        val ipError: Boolean,
        val updatedLabel: String
    )
}
