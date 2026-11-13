package com.wjthinkbig.meetstudy.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoCaptureGateTest {
    @Test
    fun capturesAfterOneSecondHold() {
        val g = AutoCaptureGate()
        assertEquals(0f, g.onFrame(0, true, 50))
        assertEquals(0.5f, g.onFrame(500, true, 50))
        assertEquals(1f, g.onFrame(1000, true, 50))
    }

    @Test
    fun resetsWhenQuadLostLongerThanGrace() {
        val g = AutoCaptureGate()
        g.onFrame(0, true, 50); g.onFrame(800, true, 50)
        g.onFrame(900, false, 50)
        assertEquals(0f, g.onFrame(1400, false, 50))
        assertEquals(0f, g.onFrame(1500, true, 50)) // restarts from here
        assertEquals(1f, g.onFrame(2500, true, 50))
    }

    @Test
    fun oneFlickerFrameKeepsTheHoldButNeverCapturesOnIt() {
        val g = AutoCaptureGate()
        g.onFrame(0, true, 50); g.onFrame(600, true, 50)
        val flicker = g.onFrame(1000, false, 50)
        assertTrue(flicker > 0f && flicker < 1f)
        assertEquals(1f, g.onFrame(1200, true, 50))
    }

    @Test
    fun requiresTenChars() {
        val g = AutoCaptureGate()
        for (t in 0..2000L step 200) assertEquals(0f, g.onFrame(t, true, 9))
    }

    @Test
    fun closeUpPageWithoutEdgesCapturesOnDenseText() {
        val g = AutoCaptureGate()
        g.onFrame(0, false, 80, textAreaFrac = 0.6f)
        assertEquals(1f, g.onFrame(1000, false, 80, textAreaFrac = 0.6f))
    }

    @Test
    fun documentRule() {
        assertTrue(looksLikeDocument(quadFound = true, chars = 10, textAreaFrac = 0f))
        assertFalse(looksLikeDocument(quadFound = true, chars = 9, textAreaFrac = 0.9f)) // blank A4 outline
        assertTrue(looksLikeDocument(quadFound = false, chars = 30, textAreaFrac = 0.25f))
        assertFalse(looksLikeDocument(quadFound = false, chars = 29, textAreaFrac = 0.9f))
        assertFalse(looksLikeDocument(quadFound = false, chars = 200, textAreaFrac = 0.1f)) // a sticker or label in the corner
    }
}
