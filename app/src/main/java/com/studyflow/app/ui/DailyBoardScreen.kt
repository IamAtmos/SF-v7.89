package com.studyflow.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studyflow.app.data.DailyBoardItem
import com.studyflow.app.data.DailyBoardType
import com.studyflow.app.ui.theme.*
import com.studyflow.app.viewmodel.StudyViewModel

@Composable
fun DailyBoardScreen(
    viewModel: StudyViewModel,
    onStartStudySession: (DailyBoardItem) -> Unit,
) {
    val items by viewModel.todaysBoardItems.collectAsState()
    var showAddItem by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Background)) {
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("📋", fontSize = 52.sp)
                    Text("Nothing on the board yet", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    Text("Add something to do today", color = TextSecondary, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    BoardItemRow(
                        item = item,
                        onToggle = { viewModel.toggleDailyBoardItem(item) },
                        onDelete = { viewModel.deleteDailyBoardItem(item) },
                        onStartSession = { onStartStudySession(item) },
                    )
                }
                item { Spacer(Modifier.height(72.dp)) } // room for the add button
            }
        }

        // ── Add Item button (bottom, consistent with Manual Entry style) ────────
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Primary)
                .clickable { showAddItem = true },
            contentAlignment = Alignment.Center,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("＋", color = OnPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Add Item", color = OnPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }
    }

    if (showAddItem) {
        AddBoardItemSheet(
            onSave    = { title, note, type -> viewModel.addDailyBoardItem(title, note, type); showAddItem = false },
            onDismiss = { showAddItem = false },
        )
    }
}

// ─── Board Item Row ────────────────────────────────────────────────────────────

@Composable
private fun BoardItemRow(
    item: DailyBoardItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onStartSession: () -> Unit,
) {
    val isStudy = item.type == DailyBoardType.STUDY.name

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {

            // Checkbox
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (item.isCompleted) Primary else Color.Transparent)
                    .border(1.5.dp, if (item.isCompleted) Primary else SurfaceVariant, CircleShape)
                    .clickable { onToggle() },
                contentAlignment = Alignment.Center,
            ) {
                if (item.isCompleted) {
                    Icon(Icons.Default.Check, null, tint = OnPrimary, modifier = Modifier.size(14.dp))
                }
            }

            Spacer(Modifier.width(12.dp))

            // Title + note + (optional) start-session button
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.title,
                    color = if (item.isCompleted) TextSecondary else TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                )
                if (item.note.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        item.note,
                        color = TextSecondary.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                    )
                }
                if (isStudy && !item.isCompleted) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Primary.copy(alpha = 0.14f))
                            .border(1.dp, Primary.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .clickable { onStartSession() }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text("▶  Start Study Session", color = Primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            Icon(
                Icons.Default.Delete, null,
                tint = TextSecondary.copy(alpha = 0.4f),
                modifier = Modifier.size(16.dp).clickable { onDelete() },
            )
        }
    }
}

// ─── Add Item Sheet ────────────────────────────────────────────────────────────

@Composable
private fun AddBoardItemSheet(
    onSave: (String, String, DailyBoardType) -> Unit,
    onDismiss: () -> Unit,
) {
    var title    by remember { mutableStateOf("") }
    var note     by remember { mutableStateOf("") }
    var type     by remember { mutableStateOf(DailyBoardType.GENERAL) }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickable { onDismiss() })

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Surface)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("New Item", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Default.Close, null, tint = TextSecondary, modifier = Modifier.clickable { onDismiss() })
            }

            // Type toggle
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceVariant).padding(4.dp),
            ) {
                TypeToggleButton("Study", selected = type == DailyBoardType.STUDY, modifier = Modifier.weight(1f)) {
                    type = DailyBoardType.STUDY
                }
                TypeToggleButton("General", selected = type == DailyBoardType.GENERAL, modifier = Modifier.weight(1f)) {
                    type = DailyBoardType.GENERAL
                }
            }

            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("Title", color = TextSecondary) },
                placeholder = { Text(if (type == DailyBoardType.STUDY) "e.g. Math" else "e.g. Gym", color = TextSecondary) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = fieldColorsBoard(), shape = RoundedCornerShape(12.dp),
            )

            OutlinedTextField(
                value = note, onValueChange = { note = it },
                label = { Text("Note (optional)", color = TextSecondary) },
                placeholder = { Text(if (type == DailyBoardType.STUDY) "e.g. Review exercises 1-20" else "e.g. Leg day", color = TextSecondary) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = fieldColorsBoard(), shape = RoundedCornerShape(12.dp),
            )

            val canSave = title.isNotBlank()
            Box(
                modifier = Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (canSave) Primary else SurfaceVariant)
                    .clickable(enabled = canSave) { onSave(title, note, type) },
                contentAlignment = Alignment.Center,
            ) {
                Text("Add Item", color = if (canSave) OnPrimary else TextSecondary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TypeToggleButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) Primary.copy(alpha = 0.18f) else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) Primary else TextSecondary, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun fieldColorsBoard() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
    focusedBorderColor = Primary, unfocusedBorderColor = SurfaceVariant, cursorColor = Primary,
    focusedContainerColor = SurfaceVariant, unfocusedContainerColor = SurfaceVariant,
)
