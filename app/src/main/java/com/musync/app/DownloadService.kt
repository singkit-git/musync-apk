package com.musync.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Keeps the app alive while downloads run (so they survive leaving the app) and shows a
 * progress notification. The actual work lives in [Downloader]; this service only observes it.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observer: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel(this)
        val downloader = (application as MusyncApplication).downloader
        startInForeground(build(downloader.activeTitles(), 0f, 1))

        if (observer == null) {
            observer = scope.launch {
                combine(downloader.states, downloader.progress) { states, progress ->
                    states.filterValues { it == DlState.Downloading }.keys.map { it to (progress[it] ?: 0f) }
                }.collect { active ->
                    if (active.isEmpty()) {
                        ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        val avg = active.map { it.second }.average().toFloat()
                        nm().notify(NOTIF_ID, build(downloader.activeTitles(), avg, active.size))
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startInForeground(n: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else 0
        ServiceCompat.startForeground(this, NOTIF_ID, n, type)
    }

    private fun build(titles: List<String>, progress: Float, count: Int): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val title = if (count > 1) "Downloading $count songs" else "Downloading"
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(titles.firstOrNull() ?: "Musync")
            .setProgress(100, (progress * 100).toInt(), progress <= 0f)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(open)
            .build()
    }

    private fun nm() = getSystemService(NotificationManager::class.java)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "downloads"
        private const val NOTIF_ID = 4201

        fun ensureChannel(ctx: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = ctx.getSystemService(NotificationManager::class.java)
                if (nm.getNotificationChannel(CHANNEL) == null) {
                    nm.createNotificationChannel(
                        NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW)
                    )
                }
            }
        }

        fun start(ctx: Context) {
            runCatching {
                androidx.core.content.ContextCompat.startForegroundService(
                    ctx, Intent(ctx, DownloadService::class.java)
                )
            }
        }
    }
}
