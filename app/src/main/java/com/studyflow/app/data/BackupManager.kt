package com.studyflow.app.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class BackupManager(private val context: Context) {

    private val dao       = StudyDatabase.getDatabase(context).studyDao()
    private val FILE_NAME = "studyflow_backup.json"
    private val FOLDER    = "StudyFlow"

    suspend fun autoBackup() = withContext(Dispatchers.IO) {
        try {
            val sessions = dao.getAllSessionsOnce()
            val subjects = dao.getAllSubjectsOnce()
            writeToDownloads(buildJson(sessions, subjects))
        } catch (e: Exception) { e.printStackTrace() }
    }

    suspend fun restoreIfNeeded() = withContext(Dispatchers.IO) {
        try {
            if (dao.getAllSessionsOnce().isNotEmpty()) return@withContext
            val json = readFromDownloads() ?: return@withContext
            parseAndRestore(json)
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun buildJson(sessions: List<StudySession>, subjects: List<Subject>): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()))
        val subArr = JSONArray()
        subjects.forEach { s ->
            subArr.put(JSONObject().apply {
                put("id", s.id); put("name", s.name); put("colorIndex", s.colorIndex)
            })
        }
        root.put("subjects", subArr)
        val sesArr = JSONArray()
        sessions.forEach { s ->
            sesArr.put(JSONObject().apply {
                put("id", s.id); put("subjectId", s.subjectId)
                put("subjectName", s.subjectName); put("subjectColorIndex", s.subjectColorIndex)
                put("durationMillis", s.durationMillis); put("testCount", s.testCount)
                put("note", s.note); put("date", s.date); put("timestamp", s.timestamp)
            })
        }
        root.put("sessions", sesArr)
        return root.toString(2)
    }

    private suspend fun parseAndRestore(json: String) {
        val root     = JSONObject(json)
        val subjects = root.optJSONArray("subjects")
        val sessions = root.optJSONArray("sessions")
        subjects?.let {
            for (i in 0 until it.length()) {
                val o = it.getJSONObject(i)
                dao.insertSubject(Subject(id = o.getInt("id"), name = o.getString("name"), colorIndex = o.getInt("colorIndex")))
            }
        }
        sessions?.let {
            for (i in 0 until it.length()) {
                val o = it.getJSONObject(i)
                dao.insertSession(StudySession(
                    id = o.getInt("id"), subjectId = o.getInt("subjectId"),
                    subjectName = o.getString("subjectName"), subjectColorIndex = o.getInt("subjectColorIndex"),
                    durationMillis = o.getLong("durationMillis"), testCount = o.optInt("testCount", 0),
                    note = o.optString("note", ""), date = o.getString("date"), timestamp = o.getLong("timestamp"),
                ))
            }
        }
    }

    private fun writeToDownloads(content: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            // Delete existing file first
            val existing = resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Downloads._ID),
                "${MediaStore.Downloads.DISPLAY_NAME}=? AND ${MediaStore.Downloads.RELATIVE_PATH}=?",
                arrayOf(FILE_NAME, "Download/$FOLDER/"),
                null,
            )
            existing?.use { c ->
                if (c.moveToFirst()) {
                    val id  = c.getLong(c.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                    val uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI.buildUpon().appendPath(id.toString()).build()
                    resolver.delete(uri, null, null)
                }
            }
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, FILE_NAME)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/$FOLDER/")
            }
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)?.let {
                resolver.openOutputStream(it)?.use { os -> os.write(content.toByteArray()) }
            }
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER)
            dir.mkdirs()
            File(dir, FILE_NAME).writeText(content)
        }
    }

    private fun readFromDownloads(): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val cursor   = resolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Downloads._ID),
                    "${MediaStore.Downloads.DISPLAY_NAME}=? AND ${MediaStore.Downloads.RELATIVE_PATH}=?",
                    arrayOf(FILE_NAME, "Download/$FOLDER/"),
                    null,
                )
                cursor?.use { c ->
                    if (c.moveToFirst()) {
                        val id  = c.getLong(c.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                        val uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI.buildUpon().appendPath(id.toString()).build()
                        resolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
                    } else null
                }
            } else {
                val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "$FOLDER/$FILE_NAME")
                if (file.exists()) file.readText() else null
            }
        } catch (e: Exception) { null }
    }
}
