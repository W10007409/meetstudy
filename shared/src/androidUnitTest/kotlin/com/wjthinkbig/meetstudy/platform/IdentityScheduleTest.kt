package com.wjthinkbig.meetstudy.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IdentityScheduleTest {
    @Test fun firstFrameThenEvery30sAndAgainAfterReappear() {
        val s = IdentitySchedule()
        assertTrue(s.due(1_000, 0f, 0f)); assertEquals(true, s.result(1_000, true))
        assertFalse(s.due(20_000, 0f, 0f))
        assertTrue(s.due(31_000, 0f, 0f)); s.result(31_000, true)
        s.faceGone()
        assertTrue(s.due(32_000, 0f, 0f))
    }

    @Test fun pendingWhileHeadTurned() {
        val s = IdentitySchedule()
        assertFalse(s.due(1_000, 25f, 0f))
        assertFalse(s.due(1_200, 0f, -21f))
        assertTrue(s.due(1_400, -20f, 20f))
    }

    @Test fun retriesAfterThreeSecondsOnMismatch() {
        val s = IdentitySchedule()
        s.result(1_000, false)
        assertFalse(s.due(3_900, 0f, 0f))
        assertTrue(s.due(4_000, 0f, 0f))
    }

    @Test fun falseOnlyAfterTwoConsecutiveMismatches() {
        val s = IdentitySchedule()
        assertNull(s.result(1_000, false))
        assertEquals(true, s.result(4_000, true)) // match resets the count
        assertNull(s.result(34_000, false))
        assertEquals(false, s.result(37_000, false))
    }

    @Test fun embedFailureRetriesInThreeSeconds() {
        val s = IdentitySchedule()
        assertNull(s.result(1_000, null))
        assertFalse(s.due(3_999, 0f, 0f))
        assertTrue(s.due(4_000, 0f, 0f))
    }
}
