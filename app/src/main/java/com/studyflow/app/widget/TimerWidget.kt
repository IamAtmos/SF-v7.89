package com.studyflow.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import com.studyflow.app.MainActivity
import com.studyflow.app.ui.formatTime

class TimerWidgetRefreshCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        TimerWidget().update(context, glanceId)
    }
}

class TimerWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs      = context.getSharedPreferences("studyflow_timer", Context.MODE_PRIVATE)
        val remaining  = prefs.getLong("remaining_ms", 25 * 60_000L)
        val isRunning  = prefs.getBoolean("is_running", false)
        val isFinished = prefs.getBoolean("is_finished", false)
        provideContent { TimerWidgetContent(remaining, isRunning, isFinished) }
    }
}

@Composable
private fun TimerWidgetContent(remaining: Long, isRunning: Boolean, isFinished: Boolean) {
    val ctx   = LocalContext.current
    val green = ColorProvider(Color(0xFF69FF47))
    val white = ColorProvider(Color(0xFFFFFFFF))
    val muted = ColorProvider(Color(0xFF9E9E9E))
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(0xFF1E1E1E)))
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clickable(actionStartActivity(Intent(ctx, MainActivity::class.java))),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        Text("⏳", style = TextStyle(fontSize = 22.sp))
        Spacer(GlanceModifier.width(10.dp))
        Column(horizontalAlignment = Alignment.Horizontal.Start, verticalAlignment = Alignment.Vertical.CenterVertically, modifier = GlanceModifier.defaultWeight()) {
            Text(
                if (isFinished) "Done! ✓" else formatTime(remaining),
                style = TextStyle(color = if (isFinished) green else white, fontSize = 20.sp, fontWeight = FontWeight.Bold),
            )
            Text(
                when { isFinished -> "Tap to open"; isRunning -> "Running"; else -> "Paused" },
                style = TextStyle(color = if (isRunning) green else muted, fontSize = 11.sp),
            )
        }
        Text("↺", style = TextStyle(color = muted, fontSize = 14.sp),
            modifier = GlanceModifier.clickable(actionRunCallback<TimerWidgetRefreshCallback>()))
    }
}

class TimerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TimerWidget()
}
