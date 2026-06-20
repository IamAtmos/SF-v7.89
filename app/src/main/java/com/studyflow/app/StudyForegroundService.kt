package com.studyflow.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.studyflow.app.ui.formatDuration
import kotlinx.coroutines.*

class StudyForegroundService : Service() {

    companion object {
        const val CHANNEL_ID       = "studyflow_session_channel"
        const val NOTIFICATION_ID  = 2001
        const val ACTION_STOP      = "com.studyflow.app.STOP_SESSION"

        fun startIntent(context: Context)  = Intent(context, StudyForegroundService::class.java)
        fun stopIntent(context: Context)   = Intent(context, StudyForegroundService::class.java).setAction(ACTION_STOP)
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())

        scope.launch {
            while (isActive) {
                updateNotification()
                delay(1_000)
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Notification ──────────────────────────────────────────────────────────

    private fun buildNotification(): Notification {
        val prefs       = getSharedPreferences("studyflow_session", Context.MODE_PRIVATE)
        val state       = prefs.getString("state", "IDLE") ?: "IDLE"
        val sessionStart = prefs.getLong("session_start", 0L)
        val pausedTotal  = prefs.getLong("paused_total", 0L)
        val pauseStart   = prefs.getLong("pause_start", 0L)

        val elapsed = when (state) {
            "RUNNING" -> maxOf(0L, System.currentTimeMillis() - sessionStart - pausedTotal)
            "PAUSED"  -> maxOf(0L, pauseStart - sessionStart - pausedTotal)
            else      -> 0L
        }

        val icon    = if (state == "PAUSED") "⏸" else "📚"
        val status  = if (state == "PAUSED") "Paused" else "Studying"
        val timeStr = formatDuration(elapsed)

        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle("$icon StudyFlow — $status")
            .setContentText("Current block: $timeStr")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Study Session",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }
}
