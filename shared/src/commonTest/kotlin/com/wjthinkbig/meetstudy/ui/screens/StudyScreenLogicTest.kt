package com.wjthinkbig.meetstudy.ui.screens

import com.wjthinkbig.meetstudy.domain.FocusState
import com.wjthinkbig.meetstudy.domain.Segment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StudyScreenLogicTest {
    @Test fun formatClockUnderAnHour() {
        assertEquals("00:00", formatClock(0))
        assertEquals("00:59", formatClock(59_999))
        assertEquals("25:00", formatClock(1_500_000))
        assertEquals("59:59", formatClock(3_599_999))
    }

    @Test fun formatClockHours() {
        assertEquals("1:00:00", formatClock(3_600_000))
        assertEquals("2:05:09", formatClock(7_509_000))
    }

    @Test fun formatClockNegativeClampsToZero() = assertEquals("00:00", formatClock(-5_000))

    @Test fun formatClockAlwaysHours() {
        assertEquals("00:00:00", formatClock(0, alwaysHours = true))
        assertEquals("01:02:03", formatClock(3_723_000, alwaysHours = true))
    }

    @Test fun syntheticOnlyAfterThreeSecondsWithoutFrames() {
        assertFalse(shouldFeedSynthetic(lastRealFrameMs = 1_000, nowMs = 3_999, state = FocusState.STUDYING))
        assertTrue(shouldFeedSynthetic(lastRealFrameMs = 1_000, nowMs = 4_000, state = FocusState.STUDYING))
        assertTrue(shouldFeedSynthetic(lastRealFrameMs = 1_000, nowMs = 60_000, state = FocusState.AWAY))
    }

    @Test fun noSyntheticOnBreakOrAppLeft() {
        assertFalse(shouldFeedSynthetic(lastRealFrameMs = 0, nowMs = 60_000, state = FocusState.BREAK))
        assertFalse(shouldFeedSynthetic(lastRealFrameMs = 0, nowMs = 60_000, state = FocusState.APP_LEFT))
    }

    @Test fun syntheticFrameLooksLikeEmptyRoom() {
        val s = syntheticFrame(42)
        assertEquals(42, s.timeMs)
        assertFalse(s.faceVisible)
        assertFalse(s.personVisible)
        assertFalse(s.phoneVisible)
    }

    @Test fun segmentsShiftToEpochKeepingDurations() {
        val mono = listOf(
            Segment(FocusState.STUDYING, 1_000, 4_000),
            Segment(FocusState.AWAY, 4_000, 9_500),
        )
        val epoch = toEpoch(mono, offsetMs = 1_794_000_000_000 - 1_000)
        assertEquals(
            listOf(
                Segment(FocusState.STUDYING, 1_794_000_000_000, 1_794_000_003_000),
                Segment(FocusState.AWAY, 1_794_000_003_000, 1_794_000_008_500),
            ),
            epoch,
        )
        assertEquals(mono.map { it.endMs - it.startMs }, epoch.map { it.endMs - it.startMs })
    }
}
