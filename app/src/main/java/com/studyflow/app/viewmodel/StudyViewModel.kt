package com.studyflow.app.viewmodel

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.studyflow.app.StudyForegroundService
import com.studyflow.app.data.BackupManager
import com.studyflow.app.data.DailyBoardItem
import com.studyflow.app.data.DailyBoardType
import com.studyflow.app.data.StudyDatabase
import com.studyflow.app.data.StudySession
import com.studyflow.app.data.Subject
import com.studyflow.app.data.SubjectWeekStat
import com.studyflow.app.data.calculateWeeklyFocusScore
import com.studyflow.app.data.calculateWeeklySubjectDistribution
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class StudyState { IDLE, RUNNING, PAUSED }

private const val PREFS_NAME         = "studyflow_session"
private const val KEY_STATE          = "state"
private const val KEY_SESSION_START  = "session_start"
private const val KEY_PAUSED_TOTAL   = "paused_total"
private const val KEY_PAUSE_START    = "pause_start"
private const val KEY_DAY_RESET      = "day_reset_time"
private const val KEY_LOCAL_ACCUM    = "local_accum_today"
private const val KEY_SAVED_DATE     = "saved_date"
private const val KEY_DAILY_GOAL     = "daily_goal_ms"
private const val KEY_LINKED_ITEM    = "active_linked_board_item"

/** Number of color swatches available in the UI's SubjectColors palette. */
private const val SUBJECT_COLOR_COUNT = 8

class StudyViewModel(application: Application) : AndroidViewModel(application) {

    private val ctx           = application.applicationContext
    private val dao            = StudyDatabase.getDatabase(application).studyDao()
    private val dailyBoardDao  = StudyDatabase.getDatabase(application).dailyBoardDao()
    private val prefs          = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val backup         = BackupManager(application)

    val subjects: StateFlow<List<Subject>> = dao.getAllSubjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allSessions: StateFlow<List<StudySession>> = dao.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Focus Score (derived — same source of truth as allSessions) ────────────
    // Both of these are pure functions of allSessions + today's date. They
    // recompute automatically whenever a session is added, edited, deleted,
    // reassigned, or restored from backup — no separate cache, no UI-side
    // recalculation, nothing that can drift out of sync.

    val weeklyFocusScore: StateFlow<Int> = allSessions
        .map { sessions -> calculateWeeklyFocusScore(sessions, getToday()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val weeklySubjectDistribution: StateFlow<List<SubjectWeekStat>> = allSessions
        .map { sessions -> calculateWeeklySubjectDistribution(sessions, getToday()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Daily Board (isolated table — does not affect study statistics) ────────
    // Reactive directly off Room, same pattern as everything else in this
    // ViewModel: DAO Flow -> StateFlow -> UI. Filtered to "today" freshly on
    // every emission so day-rollover while the app is open is handled
    // correctly, exactly like totalTodayMillis already does.

    private val allDailyBoardItems: StateFlow<List<DailyBoardItem>> = dailyBoardDao.getAllItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val todaysBoardItems: StateFlow<List<DailyBoardItem>> = allDailyBoardItems
        .map { items -> items.filter { it.date == getToday() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _activeLinkedItemId = MutableStateFlow(
        prefs.getInt(KEY_LINKED_ITEM, -1).takeIf { it != -1 }
    )
    val activeLinkedItemId: StateFlow<Int?> = _activeLinkedItemId

    /** The Subject a currently-linked Daily Board item points to, if any — used to
     *  pre-select the right subject in Finish Block without extra user steps. */
    val activeLinkedSubject: StateFlow<Subject?> = combine(
        _activeLinkedItemId, allDailyBoardItems, subjects,
    ) { itemId, items, subs ->
        val item = items.firstOrNull { it.id == itemId }
        subs.firstOrNull { it.id == item?.linkedSubjectId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _studyState         = MutableStateFlow(StudyState.IDLE)
    val studyState: StateFlow<StudyState> = _studyState

    private val _totalTodayMillis   = MutableStateFlow(0L)
    val totalTodayMillis: StateFlow<Long> = _totalTodayMillis

    private val _currentBlockMillis = MutableStateFlow(0L)
    val currentBlockMillis: StateFlow<Long> = _currentBlockMillis

    private val _dailyGoalMillis    = MutableStateFlow(prefs.getLong(KEY_DAILY_GOAL, 6 * 3600_000L))
    val dailyGoalMillis: StateFlow<Long> = _dailyGoalMillis

    private val _streak             = MutableStateFlow(0)
    val streak: StateFlow<Int> = _streak

    private var sessionStart    = 0L
    private var pauseStart      = 0L
    private var pausedTotal     = 0L
    private var dayResetTime    = 0L
    private var localAccumToday = 0L

    init {
        restoreState()
        viewModelScope.launch { backup.restoreIfNeeded() }
        viewModelScope.launch { while (true) { refreshTotals(); delay(1_000) } }
        viewModelScope.launch { allSessions.collect { recalcStreak(it) } }
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private fun restoreState() {
        val savedDate = prefs.getString(KEY_SAVED_DATE, "") ?: ""
        val today     = getToday()
        val restored  = runCatching {
            StudyState.valueOf(prefs.getString(KEY_STATE, "IDLE") ?: "IDLE")
        }.getOrDefault(StudyState.IDLE)

        dayResetTime    = prefs.getLong(KEY_DAY_RESET, 0L)
        localAccumToday = if (savedDate == today) prefs.getLong(KEY_LOCAL_ACCUM, 0L) else 0L
        sessionStart    = prefs.getLong(KEY_SESSION_START, 0L)
        pausedTotal     = prefs.getLong(KEY_PAUSED_TOTAL, 0L)
        pauseStart      = prefs.getLong(KEY_PAUSE_START, 0L)
        _studyState.value = if (savedDate == today && sessionStart > 0L) restored else StudyState.IDLE
    }

    private fun persistState() {
        prefs.edit()
            .putString(KEY_STATE, _studyState.value.name)
            .putLong(KEY_SESSION_START, sessionStart)
            .putLong(KEY_PAUSED_TOTAL, pausedTotal)
            .putLong(KEY_PAUSE_START, pauseStart)
            .putLong(KEY_DAY_RESET, dayResetTime)
            .putLong(KEY_LOCAL_ACCUM, localAccumToday)
            .putString(KEY_SAVED_DATE, getToday())
            .apply()
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    private fun refreshTotals() {
        val today    = getToday()
        val savedDb  = allSessions.value
            .filter { it.date == today && it.timestamp > dayResetTime }
            .sumOf { it.durationMillis }
        val displayed    = maxOf(savedDb, localAccumToday)
        val currentBlock = liveBlockMillis()
        _currentBlockMillis.value = currentBlock
        _totalTodayMillis.value   = displayed + currentBlock
    }

    private fun liveBlockMillis(): Long = when (_studyState.value) {
        StudyState.RUNNING -> maxOf(0L, System.currentTimeMillis() - sessionStart - pausedTotal)
        StudyState.PAUSED  -> maxOf(0L, pauseStart - sessionStart - pausedTotal)
        StudyState.IDLE    -> 0L
    }

    // ── Streak ────────────────────────────────────────────────────────────────

    private fun recalcStreak(sessions: List<StudySession>) {
        val fmt   = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dates = sessions.map { it.date }.toSortedSet().sortedDescending()
        if (dates.isEmpty()) { _streak.value = 0; return }
        val today = getToday()
        val cal   = Calendar.getInstance()
        val startDate = when {
            dates.first() == today -> today
            else -> {
                val yest = Calendar.getInstance().also { it.add(Calendar.DAY_OF_YEAR, -1) }
                val yStr = fmt.format(yest.time)
                if (dates.first() == yStr) yStr else return.also { _streak.value = 0 }
            }
        }
        cal.time = fmt.parse(startDate) ?: return
        var streak = 0
        for (date in dates) {
            if (date == fmt.format(cal.time)) { streak++; cal.add(Calendar.DAY_OF_YEAR, -1) }
            else break
        }
        _streak.value = streak
    }

    // ── Study actions ─────────────────────────────────────────────────────────

    fun startStudy() {
        sessionStart = System.currentTimeMillis(); pausedTotal = 0L; pauseStart = 0L
        _studyState.value = StudyState.RUNNING
        persistState(); startFgService()
    }

    fun pauseStudy() {
        pauseStart = System.currentTimeMillis()
        _studyState.value = StudyState.PAUSED
        persistState()
    }

    fun resumeStudy() {
        pausedTotal += System.currentTimeMillis() - pauseStart; pauseStart = 0L
        _studyState.value = StudyState.RUNNING
        persistState(); startFgService()
    }

    fun finishBlock(subject: Subject, note: String, testCount: Int) {
        val now        = System.currentTimeMillis()
        val extraPause = if (_studyState.value == StudyState.PAUSED) now - pauseStart else 0L
        val duration   = maxOf(0L, now - sessionStart - pausedTotal - extraPause)
        localAccumToday += duration
        sessionStart = 0L; pausedTotal = 0L; pauseStart = 0L
        _studyState.value = StudyState.IDLE
        persistState(); stopFgService()

        // Capture + clear the linked Daily Board item (if any) before the
        // async insert — this block is the one and only place a linked
        // item gets auto-completed, and only when a real session is saved.
        val linkedItemId = _activeLinkedItemId.value
        clearLinkedBoardItem()

        viewModelScope.launch {
            val sessionId = dao.insertSession(
                StudySession(
                    subjectId = subject.id, subjectName = subject.name,
                    subjectColorIndex = subject.colorIndex, durationMillis = duration,
                    testCount = testCount, note = note, date = getToday(),
                )
            )
            if (linkedItemId != null) {
                dailyBoardDao.getItemById(linkedItemId)?.let { item ->
                    dailyBoardDao.updateItem(
                        item.copy(isCompleted = true, linkedSessionId = sessionId.toInt())
                    )
                }
            }
            backup.autoBackup()
        }
    }

    fun endDay() {
        dayResetTime = System.currentTimeMillis(); localAccumToday = 0L
        sessionStart = 0L; pausedTotal = 0L; pauseStart = 0L
        _studyState.value = StudyState.IDLE
        persistState(); stopFgService()
    }

    // ── Manual entry ──────────────────────────────────────────────────────────

    fun addManualSession(subject: Subject, date: String, durationMillis: Long, note: String, testCount: Int) =
        viewModelScope.launch {
            dao.insertSession(
                StudySession(
                    subjectId = subject.id, subjectName = subject.name,
                    subjectColorIndex = subject.colorIndex, durationMillis = durationMillis,
                    testCount = testCount, note = note, date = date,
                    timestamp = System.currentTimeMillis(),
                )
            )
            backup.autoBackup()
        }

    // ── Session editing ───────────────────────────────────────────────────────

    fun reassignSessions(sessions: List<StudySession>, newSubject: Subject) = viewModelScope.launch {
        sessions.forEach { session ->
            dao.updateSession(
                session.copy(
                    subjectId = newSubject.id,
                    subjectName = newSubject.name,
                    subjectColorIndex = newSubject.colorIndex,
                )
            )
        }
    }

    // ── Daily Board ───────────────────────────────────────────────────────────
    // A lightweight layer on top of the existing architecture. GENERAL items
    // never touch Subject/StudySession at all. STUDY items reuse the exact
    // same Subject system Finish Block already uses, so a completed session
    // flows through History/Stats/Focus Score exactly like any other session
    // — there is no parallel statistics path.

    fun addDailyBoardItem(title: String, note: String, type: DailyBoardType) = viewModelScope.launch {
        val trimmedTitle = title.trim()
        val linkedSubjectId = if (type == DailyBoardType.STUDY) {
            resolveOrCreateSubjectId(trimmedTitle)
        } else null

        dailyBoardDao.insertItem(
            DailyBoardItem(
                title = trimmedTitle,
                note = note.trim(),
                type = type.name,
                date = getToday(),
                linkedSubjectId = linkedSubjectId,
            )
        )
    }

    /** Finds an existing Subject matching this title (case-insensitive) or creates one. */
    private suspend fun resolveOrCreateSubjectId(title: String): Int {
        val existingSubjects = dao.getAllSubjectsOnce()
        val match = existingSubjects.firstOrNull { it.name.equals(title, ignoreCase = true) }
        if (match != null) return match.id

        val newColorIndex = existingSubjects.size % SUBJECT_COLOR_COUNT
        val newId = dao.insertSubject(Subject(name = title, colorIndex = newColorIndex))
        return newId.toInt()
    }

    /** Manual checkbox toggle — local state only, never touches study statistics. */
    fun toggleDailyBoardItem(item: DailyBoardItem) = viewModelScope.launch {
        dailyBoardDao.updateItem(item.copy(isCompleted = !item.isCompleted))
    }

    fun deleteDailyBoardItem(item: DailyBoardItem) = viewModelScope.launch {
        if (_activeLinkedItemId.value == item.id) clearLinkedBoardItem()
        dailyBoardDao.deleteItem(item)
    }

    /**
     * Links the given Daily Board item to the focus-session pipeline and starts
     * a session if none is running. If a block is already RUNNING or PAUSED,
     * this only attaches the item to it — it never resets an in-progress block.
     */
    fun startStudySessionForItem(item: DailyBoardItem) {
        _activeLinkedItemId.value = item.id
        prefs.edit().putInt(KEY_LINKED_ITEM, item.id).apply()
        if (_studyState.value == StudyState.IDLE) {
            startStudy()
        }
    }

    private fun clearLinkedBoardItem() {
        _activeLinkedItemId.value = null
        prefs.edit().remove(KEY_LINKED_ITEM).apply()
    }

    // ── Daily goal ────────────────────────────────────────────────────────────

    fun setDailyGoal(millis: Long) {
        _dailyGoalMillis.value = millis
        prefs.edit().putLong(KEY_DAILY_GOAL, millis).apply()
    }

    // ── Subject management ────────────────────────────────────────────────────

    fun addSubject(name: String, colorIndex: Int) = viewModelScope.launch {
        dao.insertSubject(Subject(name = name, colorIndex = colorIndex))
    }

    fun editSubject(subject: Subject, newName: String, newColorIndex: Int) = viewModelScope.launch {
        dao.updateSubject(subject.copy(name = newName, colorIndex = newColorIndex))
    }

    fun deleteSubject(subject: Subject) = viewModelScope.launch { dao.deleteSubject(subject) }

    // ── Foreground service ────────────────────────────────────────────────────

    private fun startFgService() {
        val i = StudyForegroundService.startIntent(ctx)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
        else ctx.startService(i)
    }

    private fun stopFgService() = ctx.stopService(StudyForegroundService.startIntent(ctx))

    // ── Date helpers ──────────────────────────────────────────────────────────

    fun getToday(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    fun formatDate(date: String): String {
        val today = getToday()
        val cal   = Calendar.getInstance().also { it.add(Calendar.DAY_OF_YEAR, -1) }
        val yest  = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
        return when (date) {
            today -> "Today"
            yest  -> "Yesterday"
            else  -> runCatching {
                SimpleDateFormat("MMM d", Locale.ENGLISH).format(
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(date)!!)
            }.getOrDefault(date)
        }
    }
}
