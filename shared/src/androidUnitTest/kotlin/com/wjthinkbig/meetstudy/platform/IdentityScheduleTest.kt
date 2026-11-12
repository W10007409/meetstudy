package com.wjthinkbig.meetstudy.platform

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IdentityScheduleTest {
    @Test fun firstFrameThenEvery30sAndAgainAfterReappear() {
        val s = IdentitySchedule()
        assertTrue(s.due(1_000)); s.checked(1_000)
        assertFalse(s.due(20_000))
        assertTrue(s.due(31_000)); s.checked(31_000)
        s.faceGone()
        assertTrue(s.due(32_000))
    }
}
