package com.finger

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class UpdateWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext

        // 1. Estado local (conexión + VPN) — instantáneo, sin red
        val status = NetworkHelper.getStatus(ctx)

        // 2. IP pública desde afuera (ip.x9.ar, backup ifconfig.me).
        // Si no hay conexión, no intentar.
        var publicIp: String? = null
        var ipError = false
        if (status.connected) {
            val r = IpRepository.fetchPublicIp()
            if (r.isSuccess) publicIp = r.getOrNull()?.ip
            else ipError = true
        } else {
            publicIp = ctx.getString(R.string.ip_no_network)
        }

        // Si falla la IP pero había valor previo, conservarlo y marcar error
        if ((publicIp == null) && ipError) {
            val prev = WidgetStore.snapshot(ctx)
            publicIp = if (prev.ip.isNotBlank()) prev.ip else ctx.getString(R.string.ip_error)
        }

        WidgetStore.save(ctx, status, publicIp, ipError)
        InetWidgetProvider.refreshAll(ctx)
        return Result.success()
    }

    companion object {
        private const val ONE_TIME = "inet_update_once"
        private const val PERIODIC = "inet_update_periodic"

        fun enqueue(context: Context) {
            val req = OneTimeWorkRequestBuilder<UpdateWorker>().build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(ONE_TIME, ExistingWorkPolicy.REPLACE, req)
        }

        fun schedulePeriodic(context: Context) {
            val req = PeriodicWorkRequestBuilder<UpdateWorker>(30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC, androidx.work.ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
