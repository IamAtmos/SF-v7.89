package com.studyflow.app.ui

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studyflow.app.data.StudySession
import com.studyflow.app.data.Subject
import com.studyflow.app.data.calculateDailyFocusScore
import com.studyflow.app.ui.theme.*
import com.studyflow.app.viewmodel.StudyViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(viewModel: StudyViewModel) {
    val allSessions by viewModel.allSessions.collectAsState()
    val subjects    by viewModel.subjects.collectAsState()
    val grouped = remember(allSessions) {
        allSessions.groupBy { it.date }.entries.sortedByDescending { it.key }
    }

    if (grouped.isEmpty()) {
        Box(Modifier.fillMaxSize().background(Background), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("📚", fontSize = 52.sp)
                Text("No history yet", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Text("Finish a block to see it here", color = TextSecondary, fontSize = 14.sp)
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Background),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        grouped.forEach { (date, dateSessions) ->
            item(key = date) {
                DayCard(date = date, sessions = dateSessions, subjects = subjects, viewModel = viewModel)
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun DayCard(date: String, sessions: List<StudySession>, subjects: List<Subject>, viewModel: StudyViewModel) {
    val totalMs    = sessions.sumOf { it.durationMillis }
    val totalTests = sessions.sumOf { it.testCount }
    val bySubject  = sessions.groupBy { it.subjectName }.entries
        .sortedByDescending { (_, s) -> s.sumOf { it.durationMillis } }
    val segmentData = bySubject.map { (_, s) ->
        val dur   = s.sumOf { it.durationMillis }
        val color = SubjectColors[s.first().subjectColorIndex % SubjectColors.size]
        Triple(color, if (totalMs > 0) dur.toFloat() / totalMs else 0f, dur)
    }
    val displayDate = remember(date) {
        runCatching {
            SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH).format(
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(date)!!)
        }.getOrDefault(date)
    }
    val focusScore = remember(sessions) { calculateDailyFocusScore(sessions) }

    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface).padding(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(displayDate, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(110.dp)) {
                    SegmentedArc(segments = segmentData, modifier = Modifier.size(110.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(formatDurationShort(totalMs), color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        if (totalTests > 0) { Spacer(Modifier.height(2.dp)); Text("$totalTests tests", color = TextSecondary, fontSize = 10.sp) }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    bySubject.forEach { (subjectName, subSessions) ->
                        SubjectRow(
                            subjectName = subjectName,
                            sessions    = subSessions,
                            subjects    = subjects,
                            onReassign  = { newSubject -> viewModel.reassignSessions(subSessions, newSubject) },
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatPill("⏱ ${formatDurationShort(totalMs)}", "Total")
                if (totalTests > 0) StatPill("📝 $totalTests", "Tests")
                StatPill("🎯 $focusScore", "Focus")
            }
        }
    }
}

@Composable
private fun SubjectRow(
    subjectName: String,
    sessions: List<StudySession>,
    subjects: List<Subject>,
    onReassign: (Subject) -> Unit,
) {
    val totalDur   = sessions.sumOf { it.durationMillis }
    val totalTests = sessions.sumOf { it.testCount }
    val colorIdx   = sessions.first().subjectColorIndex
    val color      = SubjectColors[colorIdx % SubjectColors.size]
    val notes      = sessions.filter { it.note.isNotBlank() }.map { it.note }
    var expanded        by remember { mutableStateOf(false) }
    var showReassign    by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f).clickable(enabled = notes.isNotEmpty()) { expanded = !expanded },
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                Text(subjectName, color = TextPrimary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (notes.isNotEmpty()) Text(if (expanded) "▲" else "▼", color = TextSecondary.copy(0.5f), fontSize = 9.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatDurationShort(totalDur), color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    if (totalTests > 0) Text("$totalTests tests", color = TextSecondary, fontSize = 11.sp)
                }
                // Edit subject tag button
                Icon(
                    Icons.Default.Edit, null,
                    tint     = TextSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.size(14.dp).clickable { showReassign = true },
                )
            }
        }

        AnimatedVisibility(visible = expanded && notes.isNotEmpty(), enter = expandVertically(), exit = shrinkVertically()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, bottom = 4.dp)
                    .clip(RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
                    .background(SurfaceVariant.copy(0.6f)).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                notes.forEach { note ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                        Text("·", color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(note, color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                }
            }
        }
    }

    if (showReassign) {
        ReassignSubjectDialog(
            currentName = subjectName,
            subjects    = subjects,
            onSelect    = { newSubject -> onReassign(newSubject); showReassign = false },
            onDismiss   = { showReassign = false },
        )
    }
}

@Composable
private fun ReassignSubjectDialog(
    currentName: String,
    subjects: List<Subject>,
    onSelect: (Subject) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Surface,
        title = { Text("Change Subject Tag", color = TextPrimary, fontWeight = FontWeight.SemiBold) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Currently: $currentName", color = TextSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                subjects.filter { it.name != currentName }.forEach { subject ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceVariant)
                            .clickable { onSelect(subject) }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(SubjectColors[subject.colorIndex % SubjectColors.size]))
                        Text(subject.name, color = TextPrimary, fontSize = 15.sp)
                    }
                }
                if (subjects.filter { it.name != currentName }.isEmpty()) {
                    Text("No other subjects available.", color = TextSecondary, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } },
    )
}

@Composable
private fun StatPill(value: String, label: String) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(SurfaceVariant).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(value, color = Primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = TextSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun SegmentedArc(segments: List<Triple<Color, Float, Long>>, modifier: Modifier = Modifier) {
    val strokeWidth = 14.dp
    Canvas(modifier = modifier) {
        val stroke  = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        val inset   = strokeWidth.toPx() / 2f
        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
        val topLeft = Offset(inset, inset)
        var startAngle = -90f
        if (segments.isEmpty()) {
            drawArc(SurfaceVariant, 0f, 360f, false, topLeft, arcSize, style = Stroke(strokeWidth.toPx()))
            return@Canvas
        }
        segments.forEach { (color, frac, _) ->
            drawArc(color, startAngle, frac * 360f * 0.98f, false, topLeft, arcSize, style = stroke)
            startAngle += frac * 360f
        }
    }
}
