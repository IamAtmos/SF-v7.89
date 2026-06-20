package com.studyflow.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studyflow.app.ui.theme.*
import com.studyflow.app.viewmodel.TimerViewModel

@Composable
fun TimerScreen(viewModel: TimerViewModel) {
    val remaining  by viewModel.remainingMillis.collectAsState()
    val total      by viewModel.totalMillis.collectAsState()
    val isRunning  by viewModel.isRunning.collectAsState()
    val isFinished by viewModel.isFinished.collectAsState()
    val presets    by viewModel.presets.collectAsState()
    val context    = LocalContext.current

    var showAddPreset  by remember { mutableStateOf(false) }
    var showCustomTime by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize().background(Background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp),
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {

            // ── Timer circle ──────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(230.dp)
                    .clip(CircleShape)
                    .background(Surface)
                    .border(
                        2.dp,
                        when {
                            isFinished -> Primary
                            isRunning  -> Primary.copy(alpha = 0.5f)
                            else       -> SurfaceVariant
                        },
                        CircleShape,
                    )
                    .clickable(enabled = !isRunning && !isFinished) { showCustomTime = true },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text          = formatTime(remaining),
                        color         = if (isFinished) Primary else TextPrimary,
                        fontSize      = 50.sp,
                        fontWeight    = FontWeight.Light,
                        letterSpacing = (-2).sp,
                    )
                    if (!isRunning && !isFinished) {
                        Spacer(Modifier.height(4.dp))
                        Text("tap to set time", color = TextSecondary.copy(alpha = 0.4f), fontSize = 11.sp)
                    }
                    if (isFinished) {
                        Spacer(Modifier.height(6.dp))
                        Text("⏰  Time's up!", color = Primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // ── Dismiss banner (shown when finished) ──────────────────────────
            if (isFinished) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Primary.copy(alpha = 0.12f))
                        .border(1.dp, Primary.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        .clickable { viewModel.dismissAlarm() }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("✓", color = Primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Dismiss Alarm",
                            color      = Primary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize   = 16.sp,
                        )
                    }
                }
            }

            // ── Start / Pause + Reset ─────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!isRunning) {
                    Button(
                        onClick  = { viewModel.start(context) },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape    = RoundedCornerShape(14.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = if (isFinished) SurfaceVariant else Primary),
                        enabled  = !isFinished,
                    ) {
                        Text("▶  Start", color = if (isFinished) TextSecondary else OnPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                } else {
                    Button(
                        onClick  = { viewModel.pause() },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape    = RoundedCornerShape(14.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = SurfaceVariant),
                    ) {
                        Text("⏸  Pause", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }

                OutlinedButton(
                    onClick  = { viewModel.reset() },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape    = RoundedCornerShape(14.dp),
                    border   = BorderStroke(1.dp, SurfaceVariant),
                ) {
                    Text("↺  Reset", color = TextSecondary, fontSize = 16.sp)
                }
            }

            // ── Presets ───────────────────────────────────────────────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Presets  ·  hold to remove", color = TextSecondary, fontSize = 12.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(presets, key = { it }) { min ->
                        val selected = total == min * 60_000L
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) Primary.copy(0.18f) else Surface)
                                .border(1.dp, if (selected) Primary else SurfaceVariant, RoundedCornerShape(10.dp))
                                .pointerInput(min) {
                                    detectTapGestures(
                                        onTap       = { viewModel.setPreset(min) },
                                        onLongPress = { viewModel.removePreset(min) },
                                    )
                                }
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${min}m",
                                color      = if (selected) Primary else TextSecondary,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize   = 14.sp,
                            )
                        }
                    }
                    item {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Surface)
                                .border(1.dp, SurfaceVariant, RoundedCornerShape(10.dp))
                                .clickable { showAddPreset = true }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("+", color = Primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showAddPreset) {
        MinutePickerDialog("Add Preset", onConfirm = { viewModel.addPreset(it); showAddPreset = false }, onDismiss = { showAddPreset = false })
    }
    if (showCustomTime) {
        MinutePickerDialog("Set Custom Time", onConfirm = { viewModel.setCustomTime(it); showCustomTime = false }, onDismiss = { showCustomTime = false })
    }
}

@Composable
private fun MinutePickerDialog(title: String, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var text   by remember { mutableStateOf("") }
    val parsed = text.toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Surface,
        title            = { Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold) },
        text             = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value         = text,
                    onValueChange = { if (it.length <= 3) text = it },
                    label         = { Text("Minutes", color = TextSecondary) },
                    singleLine    = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedTextColor     = TextPrimary,
                        unfocusedTextColor   = TextPrimary,
                        focusedBorderColor   = Primary,
                        unfocusedBorderColor = SurfaceVariant,
                        cursorColor          = Primary,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (parsed != null && parsed !in 1..999) {
                    Text("Enter 1–999", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { parsed?.takeIf { it in 1..999 }?.let(onConfirm) }, enabled = parsed != null && parsed in 1..999) {
                Text("OK", color = Primary, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } },
    )
}
