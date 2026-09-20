package com.finger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Recibe el tap en ⟳ del widget y dispara actualización (evita ANR: delega a WorkManager). */
class WidgetRefreshReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION = "com.finger.ACTION_REFRESH"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION) {
            InetWidgetProvider.triggerManualRefresh(context.applicationContext)
        }
    }
}
