package com.studyflow.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Daily Board item types. STUDY items can be linked to a Subject and started
 * as a real focus session; GENERAL items are pure checklist entries that never
 * touch the study/statistics pipeline.
 */
enum class DailyBoardType { STUDY, GENERAL }

/**
 * A single Daily Board entry.
 *
 * Intentionally minimal — per spec, Daily Board is NOT a statistics system.
 * It stores only: title, note, type, completed state, and an optional link
 * back to the Subject/Session it produced. No progress %, no scores, no
 * goals, no time estimates live here or anywhere derived from this table.
 */
@Entity(tableName = "daily_board_items")
data class DailyBoardItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val note: String = "",
    val type: String,                     // DailyBoardType.name ("STUDY" | "GENERAL")
    val isCompleted: Boolean = false,
    val date: String,                     // "yyyy-MM-dd" — the day this item belongs to
    val linkedSubjectId: Int? = null,      // STUDY items only — which Subject "Start Study Session" uses
    val linkedSessionId: Int? = null,      // set once a real StudySession completes this item
    val createdAt: Long = System.currentTimeMillis(),
)
