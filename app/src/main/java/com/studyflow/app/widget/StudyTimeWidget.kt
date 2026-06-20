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
import com.studyflow.app.data.StudyDatabase
import com.studyflow.app.ui.formatDurationShort
import java.text.SimpleDateFormat
import java.util.*

private val SUBJECT_COLORS = listOf(
    Color(0xFF69FF47), Color(0xFF4FC3F7), Color(0xFFCE93D8),
    Color(0xFFFFB74D), Color(0xFFF48FB1), Color(0xFF80CBC4),
    Color(0xFFFFD54F), Color(0xFFEF9A9A),
)

class StudyTimeWidgetRefreshCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        StudyTimeWidget().update(context, glanceId)
    }
}

class StudyTimeWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs        = context.getSharedPreferences("studyflow_session", Context.MODE_PRIVATE)
        val dao          = StudyDatabase.getDatabase(context).studyDao()
        val today        = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val sessions     = dao.getSessionsForDate(today)
        val state        = prefs.getString("state", "IDLE") ?: "IDLE"
        val sessionStart = prefs.getLong("session_start", 0L)
        val pausedTotal  = prefs.getLong("paused_total", 0L)
        val pauseStart   = prefs.getLong("pause_start", 0L)
        val localAccum   = prefs.getLong("local_accum_today", 0L)
        val dayReset     = prefs.getLong("day_reset_time", 0L)
        val liveMs = when (state) {
            "RUNNING" -> maxOf(0L, System.currentTimeMillis() - sessionStart - pausedTotal)
            "PAUSED"  -> maxOf(0L, pauseStart - sessionStart - pausedTotal)
            else -> 0L
        }
        val savedMs  = sessions.filter { it.timestamp > dayReset }.sumOf { it.durationMillis }
        val totalMs  = maxOf(savedMs, localAccum) + liveMs
        val subjects = sessions.filter { it.timestamp > dayReset }
            .groupBy { it.subjectName }.entries
            .sortedByDescending { (_, s) -> s.sumOf { it.durationMillis } }.take(3)
            .map { (n, s) -> Triple(n, s.sumOf { it.durationMillis }, s.first().subjectColorIndex) }
        val displayDate = runCatching { SimpleDateFormat("MMM d", Locale.ENGLISH).format(Date()) }.getOrDefault(today)
        provideContent { WidgetContent(displayDate, totalMs, subjects, state == "RUNNING") }
    }
}

@Composable
private fun WidgetContent(date: String, totalMs: Long, subjects: List<Triple<String, Long, Int>>, isRunning: Boolean) {
    val ctx = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(0xFF1E1E1E)))
            .padding(14.dp)
            .clickable(actionStartActivity(Intent(ctx, MainActivity::class.java))),
        verticalAlignment = Alignment.Vertical.Top,
        horizontalAlignment = Alignment.Horizontal.Start,
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
            Text(date, style = TextStyle(color = ColorProvider(Color(0xFFFFFFFF)), fontSize = 13.sp, fontWeight = FontWeight.Bold), modifier = GlanceModifier.defaultWeight())
            if (isRunning) Text("● ", style = TextStyle(color = ColorProvider(Color(0xFF69FF47)), fontSize = 11.sp))
            Text("↺", style = TextStyle(color = ColorProvider(Color(0xFF9E9E9E)), fontSize = 14.sp),
                modifier = GlanceModifier.clickable(actionRunCallback<StudyTimeWidgetRefreshCallback>()))
        }
        Spacer(GlanceModifier.height(8.dp))
        if (subjects.isEmpty()) {
            Text("No sessions today", style = TextStyle(color = ColorProvider(Color(0xFF9E9E9E)), fontSize = 12.sp))
        } else {
            subjects.forEach { (name, ms, colorIdx) ->
                val dot = SUBJECT_COLORS[colorIdx % SUBJECT_COLORS.size]
                Row(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.Vertical.CenterVertically) {
                    Box(modifier = GlanceModifier.width(8.dp).height(8.dp).background(ColorProvider(dot))) {}
                    Spacer(GlanceModifier.width(8.dp))
                    Text(name, style = TextStyle(color = ColorProvider(Color(0xFFFFFFFF)), fontSize = 12.sp), modifier = GlanceModifier.defaultWeight(), maxLines = 1)
                    Text(formatDurationShort(ms), style = TextStyle(color = ColorProvider(dot), fontSize = 12.sp, fontWeight = FontWeight.Bold))
                }
            }
        }
        Spacer(GlanceModifier.defaultWeight())
        Text(formatDurationShort(totalMs), style = TextStyle(color = ColorProvider(Color(0xFF69FF47)), fontSize = 22.sp, fontWeight = FontWeight.Bold))
    }
}

class StudyTimeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StudyTimeWidget()
}
