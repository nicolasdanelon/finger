package com.finger

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class InetWidgetProvider : android.appwidget.AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Pintar cache al toque + pedir actualización fresca en background
        appWidgetIds.forEach { renderCached(context, appWidgetManager, it) }
        UpdateWorker.enqueue(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH_CLICK) {
            // Feedback inmediato: mostrar "actualizando…"
            triggerManualRefresh(context)
        }
    }

    companion object {
        const val ACTION_REFRESH_CLICK = "com.finger.ACTION_REFRESH"
        const val EXTRA_TAB = "tab"
        const val TAB_INTERNET = "internet"

        fun triggerManualRefresh(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, InetWidgetProvider::class.java))
            ids.forEach { renderLoading(context, mgr, it) }
            UpdateWorker.enqueue(context)
        }

        fun renderCached(context: Context, mgr: AppWidgetManager, widgetId: Int) {
            updateWidget(context, mgr, widgetId, WidgetStore.snapshot(context), loading = false)
        }

        fun renderLoading(context: Context, mgr: AppWidgetManager, widgetId: Int) {
            val s = WidgetStore.snapshot(context).copy(ip = context.getString(R.string.widget_updating))
            updateWidget(context, mgr, widgetId, s, loading = true)
        }

        fun updateWidget(
            context: Context,
            mgr: AppWidgetManager,
            widgetId: Int,
            s: WidgetStore.Snapshot,
            loading: Boolean
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_inet)

            // Conexión
            views.setTextViewText(
                R.id.tv_conn,
                if (s.connected) s.typeLabel else context.getString(R.string.conn_none)
            )
            val dotConn = when {
                !s.connected -> R.drawable.dot_red
                s.transport == "WIFI" -> R.drawable.dot_green
                s.transport == "CELLULAR" -> R.drawable.dot_green
                s.transport == "ETHERNET" -> R.drawable.dot_green
                else -> R.drawable.dot_yellow
            }
            views.setImageViewResource(R.id.dot_conn, dotConn)

            // IP
            views.setTextViewText(R.id.tv_ip, s.ip)

            // VPN
            views.setTextViewText(
                R.id.tv_vpn,
                if (s.vpnOn) context.getString(R.string.widget_vpn_on)
                else context.getString(R.string.widget_vpn_off)
            )
            views.setImageViewResource(
                R.id.dot_vpn,
                if (s.vpnOn) R.drawable.dot_green else R.drawable.dot_grey
            )

            views.setTextViewText(
                R.id.tv_updated,
                if (loading) context.getString(R.string.widget_updating)
                else s.updatedLabel
            )

            // Botón refresh
            val refreshIntent = Intent(context, WidgetRefreshReceiver::class.java).apply {
                action = WidgetRefreshReceiver.ACTION
            }
            val flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    android.app.PendingIntent.FLAG_IMMUTABLE
            val pi = android.app.PendingIntent.getBroadcast(context, 0, refreshIntent, flags)
            views.setOnClickPendingIntent(R.id.btn_refresh, pi)

            // Tap en el widget abre la app en el tab Internet
            val openApp = Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_TAB, TAB_INTERNET)
            }
            val piOpen = android.app.PendingIntent.getActivity(context, 1, openApp, flags)
            views.setOnClickPendingIntent(R.id.widget_root, piOpen)

            mgr.updateAppWidget(widgetId, views)
        }

        fun refreshAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, InetWidgetProvider::class.java))
            val s = WidgetStore.snapshot(context)
            ids.forEach { updateWidget(context, mgr, it, s, loading = false) }
        }
    }
}
