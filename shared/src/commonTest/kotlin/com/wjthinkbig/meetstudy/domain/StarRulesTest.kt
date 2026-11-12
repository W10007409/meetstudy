package com.wjthinkbig.meetstudy.domain

import com.wjthinkbig.meetstudy.data.SessionRecord
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(kotlin.time.ExperimentalTime::class)
class StarRulesTest {
    private val tz = TimeZone.UTC
    private val today = LocalDate(2026, 11, 12)
    private fun rec(date: LocalDate, goal: Boolean, proof: Boolean = false, focusedMin: Long = 10): SessionRecord {
        val start = date.atStartOfDayIn(tz).toEpochMilliseconds() + 3_600_000
        return SessionRecord(
            "$date-$goal", start, start + focusedMin * 60_000, 600_000,
            listOf(Segment(FocusState.STUDYING, start, start + focusedMin * 60_000)), goal, proof, null,
        )
    }

    @Test
    fun goalStarDeniedWhenNotReached() {
        assertFalse(StarRules.goalStarAllowed(false, 0))
        assertTrue(StarRules.goalStarAllowed(true, 0))
    }

    @Test
    fun dailyCapCountsAcrossSessions() {
        assertFalse(StarRules.goalStarAllowed(true, 2))
        assertFalse(StarRules.proofStarAllowed(2))
        assertTrue(StarRules.proofStarAllowed(1))
    }

    @Test
    fun streakCountsConsecutiveGoalDays() {
        val rs = listOf(
            rec(today, true), rec(today.minus(1, kotlinx.datetime.DateTimeUnit.DAY), true),
            rec(today.minus(2, kotlinx.datetime.DateTimeUnit.DAY), true),
            rec(today.minus(3, kotlinx.datetime.DateTimeUnit.DAY), false),
        )
        assertEquals(3, streakDays(rs, tz, today))
    }

    @Test
    fun streakSurvivesUntilTodayEnds() {
        val rs = listOf(rec(today.minus(1, kotlinx.datetime.DateTimeUnit.DAY), true))
        assertEquals(1, streakDays(rs, tz, today))
        assertEquals(0, streakDays(emptyList(), tz, today))
    }

    @Test
    fun dailyTotalsZeroFillsAndSums() {
        val y = today.minus(1, kotlinx.datetime.DateTimeUnit.DAY)
        val out = dailyTotals(listOf(rec(today, true, true, 10), rec(today, false, false, 5)), tz, y, today)
        assertEquals(listOf(DayTotal(y, 0, 0), DayTotal(today, 15 * 60_000L, 2)), out)
    }
}
