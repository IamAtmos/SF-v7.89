package com.studyflow.app.viewmodel

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.studyflow.app.AlarmReceiver
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TimerViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val ACTION_STOP_ALARM = "com.studyflow.app.STOP_ALARM"
        private const val CHANNEL_ID     = "studyflow_timer"
        private const val NOTIFICATION_ID = 1001

        @Volatile private var currentRingtone: Ringtone? = null

        fun stopAlarmStatic(context: Context) {
            currentRingtone?.stop()
            currentRingtone = null
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .cancel(NOTIFICATION_ID)
        }
    }

    private val ctx   = application.applicationContext
    private val prefs = application.getSharedPreferences("studyflow_timer", Context.MODE_PRIVATE)

    private val _presets     = MutableStateFlow(mutableListOf(5, 10, 25, 50))
    val presets: StateFlow<MutableList<Int>> = _presets

    private val _totalMillis     = MutableStateFlow(25 * 60_000L)
    val totalMillis: StateFlow<Long> = _totalMillis

    private val _remainingMillis = MutableStateFlow(25 * 60_000L)
    val remainingMillis: StateFlow<Long> = _remainingMillis

    private val _isRunning   = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _isFinished  = MutableStateFlow(false)
    val isFinished: StateFlow<Boolean> = _isFinished

    private var timerJob: Job? = null

    fun addPreset(minutes: Int) {
        if (minutes <= 0 || minutes > 999 || _presets.value.contains(minutes)) return
        _presets.value = _presets.value.toMutableList().also { it.add(minutes); it.sort() }
    }

    fun removePreset(minutes: Int) {
        _presets.value = _presets.value.toMutableList().also { it.remove(minutes) }
    }

    fun setPreset(minutes: Int) {
        if (_isRunning.value) return
        _totalMillis.value     = minutes * 60_000L
        _remainingMillis.value = minutes * 60_000L
        _isFinished.value      = false
        persistWidgetState()
    }

    fun setCustomTime(minutes: Int) {
        if (_isRunning.value || minutes <= 0) return
        _totalMillis.value     = minutes * 60_000L
        _remainingMillis.value = minutes * 60_000L
        _isFinished.value      = false
        persistWidgetState()
    }

    fun start(context: Context) {
        if (_isRunning.value) return
        stopAlarmStatic(context)   // clear any lingering alarm
        _isRunning.value  = true
        _isFinished.value = false
        persistWidgetState()

        timerJob = viewModelScope.launch {
            while (_remainingMillis.value > 0 && _isRunning.value) {
                delay(1_000L)
                if (!_isRunning.value) break
                _remainingMillis.value = (_remainingMillis.value - 1_000L).coerceAtLeast(0)
                persistWidgetState()
                if (_remainingMillis.value == 0L) {
                    _isRunning.value  = false
                    _isFinished.value = true
                    persistWidgetState()
                    fireAlarm(context)
                }
            }
        }
    }

    fun pause() {
        _isRunning.value = false
        timerJob?.cancel()
        persistWidgetState()
    }

    fun reset() {
        pause()
        stopAlarmStatic(ctx)
        _remainingMillis.value = _totalMillis.value
        _isFinished.value      = false
        persistWidgetState()
    }

    /** Called from UI dismiss button */
    fun dismissAlarm() {
        stopAlarmStatic(ctx)
        _isFinished.value = false
        _remainingMillis.value = _totalMillis.value
        persistWidgetState()
    }

    // ── Alarm ─────────────────────────────────────────────────────────────────

    private fun fireAlarm(context: Context) {
        ensureChannel(context)
        vibrate(context)

        // Play ringtone with a reference we can stop
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        currentRingtone = RingtoneManager.getRingtone(context, alarmUri)?.also { it.play() }

        // Notification with "Stop Alarm" action
        val stopPending = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, AlarmReceiver::class.java).setAction(ACTION_STOP_ALARM),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val openPending = PendingIntent.getActivity(
            context, 1,
            Intent(context, Class.forName("com.studyflow.app.MainActivity"))
                .apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("⏳ StudyFlow — Timer Done!")
            .setContentText("تایمر تموم شد. ضربه بزن برای بستن.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_media_pause, "Stop Alarm", stopPending)
            .build()

        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, notification)
    }

    private fun vibrate(context: Context) {
        val pattern = longArrayOf(0, 500, 200, 500, 200, 1000)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)
                ?.defaultVibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.let { v ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    v.vibrate(VibrationEffect.createWaveform(pattern, -1))
                else @Suppress("DEPRECATION") v.vibrate(pattern, -1)
            }
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Timer Alarm", NotificationManager.IMPORTANCE_HIGH)
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun persistWidgetState() {
        prefs.edit()
            .putLong("remaining_ms", _remainingMillis.value)
            .putBoolean("is_running", _isRunning.value)
            .putBoolean("is_finished", _isFinished.value)
            .apply()
    }

    override fun onCleared() { super.onCleared(); timerJob?.cancel() }
}
