package com.wjthinkbig.meetstudy.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class AutoCaptureGateTest {
    @Test
    fun capturesAfterOneSecondHold() {
        val g = AutoCaptureGate()
        assertEquals(0f, g.onFrame(0, true, 50))
        assertEquals(0.5f, g.onFrame(500, true, 50))
        assertEquals(1f, g.onFrame(1000, true, 50))
    }

    @Test
    fun resetsWhenQuadLost() {
        val g = AutoCaptureGate()
        g.onFrame(0, true, 50); g.onFrame(800, true, 50)
        assertEquals(0f, g.onFrame(900, false, 50))
        assertEquals(0f, g.onFrame(1000, true, 50)) // restarts from here
        assertEquals(1f, g.onFrame(2000, true, 50))
    }

    @Test
    fun requiresTenChars() {
        val g = AutoCaptureGate()
        for (t in 0..2000L step 200) assertEquals(0f, g.onFrame(t, true, 9))
    }
}
