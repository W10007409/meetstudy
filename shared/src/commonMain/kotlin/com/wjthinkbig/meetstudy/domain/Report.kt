package com.wjthinkbig.meetstudy.domain

import kotlinx.datetime.toLocalDateTime

data class ReportSummary(
    val totalMs: Long,
    val focusedMs: Long,
    val focusRate: Float,
    val byState: Map<FocusState, Pair<Int, Long>>, // count, duration; STUDYING excluded
    val events: List<Segment>, // non-STUDYING, chronological
)

fun buildReport(segments: List<Segment>): ReportSummary {
    val total = segments.sumOf { it.endMs - it.startMs }
    val focused = segments.filter { it.state == FocusState.STUDYING }.sumOf { it.endMs - it.startMs }
    val events = segments.filter { it.state != FocusState.STUDYING }.sortedBy { it.startMs }
    val byState = events.groupBy { it.state }.mapValues { (_, v) -> v.size to v.sumOf { it.endMs - it.startMs } }
    return ReportSummary(total, focused, if (total == 0L) 0f else focused.toFloat() / total, byState, events)
}

/** Every day boundary (weekly bars, streaks, "today", planner hours) is Korean time on both student app and teacher web. */
val SEOUL: kotlinx.datetime.TimeZone = kotlinx.datetime.TimeZone.of("Asia/Seoul")

/** "흐트러진 시간": distraction of any kind. BREAK is allowed rest, so it is not lost. */
val LOST_STATES: Set<FocusState> = setOf(
    FocusState.AWAY, FocusState.LOOKING_AWAY, FocusState.CAMERA_GAZE, FocusState.DROWSY, FocusState.PHONE, FocusState.APP_LEFT,
)

fun lostMs(segments: List<Segment>): Long = segments.filter { it.state in LOST_STATES }.sumOf { it.endMs - it.startMs }

/** Focused share of the whole session, rounded half up to a whole percent. */
fun focusRatePct(segments: List<Segment>): Int {
    val total = segments.sumOf { it.endMs - it.startMs }
    if (total == 0L) return 0
    val focused = segments.filter { it.state == FocusState.STUDYING }.sumOf { it.endMs - it.startMs }
    return ((focused * 200 + total) / (total * 2)).toInt()
}

/** The same student's session that started most recently before [r]. */
fun previousSession(all: List<com.wjthinkbig.meetstudy.data.SessionRecord>, r: com.wjthinkbig.meetstudy.data.SessionRecord) =
    all.filter { it.startMs < r.startMs }.maxByOrNull { it.startMs }

/** One study-planner row: an hour of the day and the state of each of its 60 minutes (null = not in the session). */
data class PlannerRow(val hour: Int, val minutes: List<FocusState?>)

/**
 * Study-planner rows from the first segment's hour to the hour holding the last instant. A minute takes the state of
 * the segment covering its :30 mark, so a 10-second blip never colours a whole minute.
 */
@OptIn(kotlin.time.ExperimentalTime::class)
fun plannerRows(segments: List<Segment>, tz: kotlinx.datetime.TimeZone): List<PlannerRow> {
    if (segments.isEmpty()) return emptyList()
    val hourMs = 3_600_000L
    // Korea has no DST and whole-hour offsets, so hour starts are aligned on the offset.
    fun hourStart(ms: Long): Long {
        val local = kotlin.time.Instant.fromEpochMilliseconds(ms).toLocalDateTime(tz)
        val offsetMs = (local.hour * 60 + local.minute) * 60_000L + local.second * 1000L + local.nanosecond / 1_000_000
        return ms - offsetMs % hourMs
    }
    val first = hourStart(segments.minOf { it.startMs })
    val last = hourStart(segments.maxOf { it.endMs } - 1)
    val rows = mutableListOf<PlannerRow>()
    var h = first
    while (h <= last) {
        val hour = kotlin.time.Instant.fromEpochMilliseconds(h).toLocalDateTime(tz).hour
        rows += PlannerRow(hour, List(60) { m ->
            val t = h + m * 60_000L + 30_000L
            segments.firstOrNull { t >= it.startMs && t < it.endMs }?.state
        })
        h += hourMs
    }
    return rows
}
