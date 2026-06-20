package com.studyflow.app

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.studyflow.app.viewmodel.TimerViewModel

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TimerViewModel.ACTION_STOP_ALARM) {
            TimerViewModel.stopAlarmStatic(context)
        }
    }
}
