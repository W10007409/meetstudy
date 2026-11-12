package com.wjthinkbig.meetstudy.domain

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
