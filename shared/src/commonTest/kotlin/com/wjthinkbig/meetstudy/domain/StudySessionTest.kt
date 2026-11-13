package com.wjthinkbig.meetstudy.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StudySessionTest {
    private val base = Baseline(yaw = 30f, pitch = -25f) // reading pose, not facing the camera
    private fun session(goalMs: Long = 60_000) = StudySession(goalMs, 0, FocusStateMachine(base))

    private fun frame(t: Long, face: Boolean) = FrameSignals(
        t, face, null, if (face) base.yaw else null, if (face) base.pitch else null,
        if (face) 0.9f else null, personVisible = face, noseBelowShoulders = null, phoneVisible = false,
    )

    private fun StudySession.feed(fromMs: Long, toMs: Long, face: Boolean) {
        for (t in fromMs..toMs step 500) onFrame(frame(t, face))
    }

    @Test fun focusedTimeExcludesRetroactiveAway() {
        val s = session()
        s.feed(0, 59_500, true)
        s.feed(60_000, 70_000, false)
        assertEquals(FocusState.AWAY, s.state.value)
        assertEquals(60_000, s.focusedMs(70_000))
    }

    @Test fun goalReachedOnlyCountsStudying() {
        val s = session(goalMs = 60_000)
        s.feed(0, 29_500, true)
        s.feed(30_000, 59_500, false)
        s.feed(60_000, 90_000, true)
        assertFalse(s.isGoalReached(89_000))
        assertTrue(s.isGoalReached(90_000))
    }

    @Test fun breakLimitThreeTimes() {
        val s = session()
        repeat(3) { i ->
            assertTrue(s.startBreak(i * 1_000L))
            s.endBreak(i * 1_000L + 500)
        }
        assertEquals(0, s.breaksLeft())
        assertFalse(s.startBreak(4_000))
    }

    @Test fun breakOverrunBecomesAway() {
        val s = session()
        assertTrue(s.startBreak(0))
        s.tick(600_000)
        assertEquals(FocusState.BREAK, s.state.value)
        s.tick(600_001)
        assertEquals(FocusState.AWAY, s.state.value)
        s.endBreak(610_000) // no-op after overrun
        val segs = s.finish(610_000)
        assertEquals(Segment(FocusState.AWAY, 600_000, 610_000), segs.last())
    }

    @Test fun appLeftPausesAndResumes() {
        val s = session()
        s.onAppLeft(true, 5_000)
        s.onAppLeft(false, 15_000)
        assertEquals(
            listOf(
                Segment(FocusState.STUDYING, 0, 5_000),
                Segment(FocusState.APP_LEFT, 5_000, 15_000),
                Segment(FocusState.STUDYING, 15_000, 20_000),
            ),
            s.finish(20_000),
        )
    }

    @Test fun finishClosesSegmentsContiguously() {
        val s = session()
        s.feed(0, 29_500, true)
        s.feed(30_000, 40_000, false)
        s.startBreak(41_000) // AWAY counts as distracted, break allowed
        s.endBreak(45_000)
        val segs = s.finish(50_000)
        assertEquals(0, segs.first().startMs)
        assertEquals(50_000, segs.last().endMs)
        segs.zipWithNext().forEach { (a, b) -> assertEquals(a.endMs, b.startMs) }
    }
}
