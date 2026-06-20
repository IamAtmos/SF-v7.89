package com.studyflow.app.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * ── Focus Score ──────────────────────────────────────────────────────────────
 *
 * This file is the SINGLE source of truth for all Focus Score calculations.
 * It operates only on [StudySession] rows already persisted in Room — the
 * exact same data used everywhere else in the app (History, Stats, Streak,
 * Backup). It does not read app foreground/background state, screen on/off,
 * or any Android usage-tracking APIs.
 *
 * Because every screen derives its numbers from this same set of pure
 * functions over the same `allSessions` stream, Daily Focus Score and Weekly
 * Focus Score can never drift apart or disagree — there is nothing else to
 * keep in sync. This holds true whether sessions were created live, added
 * manually, restored from backup, or edited/reassigned afterward.
 */

// Fixed reference caps used to normalize each component (MVP version).
// Intentionally simple and NOT tied to user-specific settings (e.g. Daily
// Goal) so the score stays predictable and easy to reason about.
private const val TIME_CAP_MINUTES  = 240   // 4h of study  -> full 50% time component
private const val SESSION_CAP_COUNT = 4     // 4 sessions   -> full 30% session component
private const val TEST_CAP_COUNT    = 30    // 30 tests     -> full 20% test component

private const val TIME_WEIGHT    = 50.0
private const val SESSION_WEIGHT = 30.0
private const val TEST_WEIGHT    = 20.0

/**
 * Daily Focus Score (0-100) computed purely from a day's [StudySession] rows.
 *
 * - Study Time   -> 50% (saturates at [TIME_CAP_MINUTES])
 * - Session Count -> 30% (saturates at [SESSION_CAP_COUNT])
 * - Test Count    -> 20% (saturates at [TEST_CAP_COUNT])
 */
fun calculateDailyFocusScore(sessions: List<StudySession>): Int {
    if (sessions.isEmpty()) return 0

    val totalMinutes = sessions.sumOf { it.durationMillis } / 60_000.0
    val sessionCount = sessions.size
    val testCount    = sessions.sumOf { it.testCount }

    val timeComponent    = (totalMinutes / TIME_CAP_MINUTES).coerceIn(0.0, 1.0) * TIME_WEIGHT
    val sessionComponent = (sessionCount.toDouble() / SESSION_CAP_COUNT).coerceIn(0.0, 1.0) * SESSION_WEIGHT
    val testComponent    = (testCount.toDouble() / TEST_CAP_COUNT).coerceIn(0.0, 1.0) * TEST_WEIGHT

    return (timeComponent + sessionComponent + testComponent).coerceIn(0.0, 100.0).toInt()
}

// Recency weights for the rolling 7-day Weekly Focus Score.
// Index 0 = today, 1 = yesterday, ... 6 = six days ago.
private val RECENCY_WEIGHTS = listOf(1.0, 0.9, 0.8, 0.7, 0.6, 0.5, 0.4)

/**
 * Weekly Focus Score = a recency-weighted average of the last 7 Daily Focus
 * Scores (today + 6 previous days). It NEVER recalculates from raw session
 * data using different logic — each day's contribution comes from
 * [calculateDailyFocusScore], so the daily and weekly numbers are always
 * consistent with each other and with what History shows for those days.
 *
 * Days with no sessions simply score 0 for that day (not excluded), which
 * matches "Today/Yesterday/etc." always being part of the window per spec.
 */
fun calculateWeeklyFocusScore(allSessions: List<StudySession>, today: String): Int {
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val todayCal = (fmt.parse(today)?.let { d -> Calendar.getInstance().apply { time = d } })
        ?: Calendar.getInstance()

    val byDate = allSessions.groupBy { it.date }

    var weightedSum = 0.0
    var weightTotal = 0.0

    for (i in RECENCY_WEIGHTS.indices) {
        val cal = (todayCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -i) }
        val dateStr   = fmt.format(cal.time)
        val dayScore  = calculateDailyFocusScore(byDate[dateStr] ?: emptyList())
        val weight    = RECENCY_WEIGHTS[i]
        weightedSum += dayScore * weight
        weightTotal += weight
    }

    if (weightTotal == 0.0) return 0
    return (weightedSum / weightTotal).coerceIn(0.0, 100.0).toInt()
}

/** Aggregated per-subject totals for the current calendar week. */
data class SubjectWeekStat(
    val name: String,
    val colorIndex: Int,
    val totalMs: Long,
    val testCount: Int,
)

/**
 * Weekly Subject Distribution — totals per subject for the same calendar
 * week as [today] (Mon-Sun bucketing via week-of-year, matching the bucket
 * already shown by the "This Week" chart in Stats). Sorted by study time
 * descending so the most-studied subject appears first.
 */
fun calculateWeeklySubjectDistribution(allSessions: List<StudySession>, today: String): List<SubjectWeekStat> {
    val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val weekFmt = SimpleDateFormat("w_yyyy", Locale.getDefault())
    val todayWeekKey = weekFmt.format(dateFmt.parse(today) ?: Date())

    return allSessions
        .filter { session ->
            val parsed = runCatching { dateFmt.parse(session.date) }.getOrNull() ?: return@filter false
            weekFmt.format(parsed) == todayWeekKey
        }
        .groupBy { it.subjectName }
        .map { (name, sessions) ->
            SubjectWeekStat(
                name       = name,
                colorIndex = sessions.first().subjectColorIndex,
                totalMs    = sessions.sumOf { it.durationMillis },
                testCount  = sessions.sumOf { it.testCount },
            )
        }
        .sortedByDescending { it.totalMs }
}
