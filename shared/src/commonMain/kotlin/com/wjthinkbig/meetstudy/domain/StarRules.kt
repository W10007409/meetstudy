package com.wjthinkbig.meetstudy.domain

import com.wjthinkbig.meetstudy.data.SessionRecord
import kotlinx.datetime.DateTimeUnit
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

object StarRules {
    const val DAILY_CAP = 2
    fun goalStarAllowed(goalReached: Boolean, starsToday: Int) = goalReached && starsToday < DAILY_CAP
    fun proofStarAllowed(starsToday: Int) = starsToday < DAILY_CAP
}

data class DayTotal(val date: LocalDate, val focusedMs: Long, val stars: Int)

@OptIn(ExperimentalTime::class)
internal fun SessionRecord.localDate(tz: TimeZone): LocalDate =
    Instant.fromEpochMilliseconds(startMs).toLocalDateTime(tz).date

internal fun SessionRecord.stars() = (if (goalStar) 1 else 0) + (if (proofStar) 1 else 0)

fun dailyTotals(records: List<SessionRecord>, tz: TimeZone, from: LocalDate, to: LocalDate): List<DayTotal> {
    val byDate = records.groupBy { it.localDate(tz) }
    val out = mutableListOf<DayTotal>()
    var d = from
    while (d <= to) {
        val rs = byDate[d].orEmpty()
        val focused = rs.sumOf { r -> r.segments.filter { it.state == FocusState.STUDYING }.sumOf { it.endMs - it.startMs } }
        out += DayTotal(d, focused, rs.sumOf { it.stars() })
        d = d.plus(1, DateTimeUnit.DAY)
    }
    return out
}

/** Consecutive days with a goal star, ending today; if today has none yet, the streak still counts through yesterday. */
fun streakDays(records: List<SessionRecord>, tz: TimeZone, today: LocalDate): Int {
    val goalDays = records.filter { it.goalStar }.map { it.localDate(tz) }.toSet()
    var d = if (today in goalDays) today else today.minus(1, DateTimeUnit.DAY)
    var n = 0
    while (d in goalDays) { n++; d = d.minus(1, DateTimeUnit.DAY) }
    return n
}
