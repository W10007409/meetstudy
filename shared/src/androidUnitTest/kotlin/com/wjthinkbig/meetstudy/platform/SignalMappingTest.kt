package com.wjthinkbig.meetstudy.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SignalMappingTest {
    private val shouldersUp = PoseReading(0.9f, 0.9f, shoulderAvgY = 300f, noseY = 100f)

    @Test fun noFaceShouldersVisibleNoseAbove() {
        val s = toSignals(0, null, shouldersUp)
        assertFalse(s.faceVisible); assertTrue(s.personVisible); assertEquals(false, s.noseBelowShoulders)
    }

    @Test fun noseBelowShoulders() {
        assertEquals(true, toSignals(0, null, PoseReading(0.9f, 0.9f, 300f, 400f)).noseBelowShoulders)
    }

    @Test fun noseMissingGivesNull() {
        assertNull(toSignals(0, null, PoseReading(0.9f, 0.9f, 300f, null)).noseBelowShoulders)
    }

    @Test fun faceVisible() {
        val s = toSignals(5, FaceReading(10f, -20f, 0.2f, 0.8f), shouldersUp)
        assertTrue(s.faceVisible); assertTrue(s.personVisible); assertNull(s.noseBelowShoulders)
        assertEquals(0.8f, s.eyesOpenProb); assertEquals(10f, s.yaw); assertEquals(-20f, s.pitch)
    }

    @Test fun eyesBothNullIsNull() {
        assertNull(toSignals(0, FaceReading(0f, 0f, null, null), null).eyesOpenProb)
    }

    @Test fun lowShoulderLikelihoodNotPerson() {
        val s = toSignals(0, null, PoseReading(0.9f, 0.3f, 300f, 100f))
        assertFalse(s.personVisible); assertNull(s.noseBelowShoulders)
    }

    @Test fun noPoseYet() {
        assertFalse(toSignals(0, null, null).personVisible)
    }

    @Test fun clipNegativeLeftTopShrinksNotShifts() {
        assertEquals(listOf(0, 0, 40, 60), clipRect(-10, -20, 40, 60, 100, 100)!!.toList())
    }

    @Test fun clipRightBottomOverflow() {
        assertEquals(listOf(80, 90, 20, 10), clipRect(80, 90, 150, 150, 100, 100)!!.toList())
    }

    @Test fun clipFullyOutsideIsNull() {
        assertNull(clipRect(-50, -50, -1, 10, 100, 100))
        assertNull(clipRect(100, 0, 150, 50, 100, 100))
    }
}
