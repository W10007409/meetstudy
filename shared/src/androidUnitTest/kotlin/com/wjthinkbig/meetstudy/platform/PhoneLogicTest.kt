package com.wjthinkbig.meetstudy.platform

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhoneLogicTest {
    @Test fun runsAtMostOncePerSecond() {
        assertTrue(PhoneSchedule.shouldRun(null, 5_000, faceOrPerson = true, enabled = true, disabled = false))
        assertFalse(PhoneSchedule.shouldRun(5_000, 5_999, true, true, false))
        assertTrue(PhoneSchedule.shouldRun(5_000, 6_000, true, true, false))
    }

    @Test fun skippedWithoutFaceOrPersonOrWhenOffOrDisabled() {
        assertFalse(PhoneSchedule.shouldRun(null, 5_000, faceOrPerson = false, enabled = true, disabled = false))
        assertFalse(PhoneSchedule.shouldRun(null, 5_000, true, enabled = false, disabled = false))
        assertFalse(PhoneSchedule.shouldRun(null, 5_000, true, true, disabled = true))
    }

    @Test fun budgetDisablesOnlyAfterTenSlowSamples() {
        val b = InferenceBudget()
        repeat(9) { b.record(500) }
        assertFalse(b.overBudget)
        b.record(500)
        assertTrue(b.overBudget)
    }

    @Test fun budgetStaysOnAtExactly150AndUsesLastTen() {
        val b = InferenceBudget()
        repeat(10) { b.record(150) }
        assertFalse(b.overBudget)
        assertTrue(b.avgMs == 150L)
        repeat(10) { b.record(100) } // old slow samples roll out
        assertTrue(b.avgMs == 100L)
    }
}
