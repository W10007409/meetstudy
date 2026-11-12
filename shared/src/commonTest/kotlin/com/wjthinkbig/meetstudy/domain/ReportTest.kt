package com.wjthinkbig.meetstudy.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ReportTest {
    private val min = 60_000L
    private fun seg(s: FocusState, from: Long, to: Long) = Segment(s, from * min, to * min)

    @Test
    fun reportComputesRateAndEventCounts() {
        val r = buildReport(
            listOf(
                seg(FocusState.STUDYING, 0, 20), seg(FocusState.AWAY, 20, 23),
                seg(FocusState.STUDYING, 23, 43), seg(FocusState.LOOKING_AWAY, 43, 44),
                seg(FocusState.STUDYING, 44, 54), seg(FocusState.LOOKING_AWAY, 54, 55),
                seg(FocusState.BREAK, 55, 60),
            ),
        )
        assertEquals(60 * min, r.totalMs)
        assertEquals(50 * min, r.focusedMs)
        assertEquals(50f / 60f, r.focusRate)
        assertEquals(2 to 2 * min, r.byState[FocusState.LOOKING_AWAY])
        assertEquals(1 to 5 * min, r.byState[FocusState.BREAK])
        assertFalse(FocusState.STUDYING in r.byState)
        assertEquals(4, r.events.size)
        assertEquals(r.events.sortedBy { it.startMs }, r.events)
    }

    @Test
    fun emptyReportHasZeroRate() {
        assertEquals(0f, buildReport(emptyList()).focusRate)
    }
}
