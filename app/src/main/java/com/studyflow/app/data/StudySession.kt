package com.studyflow.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "study_sessions")
data class StudySession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val subjectId: Int,
    val subjectName: String,
    val subjectColorIndex: Int,
    val durationMillis: Long,
    val testCount: Int = 0,
    val note: String = "",
    val date: String,                   // "yyyy-MM-dd"
    val timestamp: Long = System.currentTimeMillis(),
)
