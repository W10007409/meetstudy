package com.wjthinkbig.meetstudy.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BaselineCalibratorTest {
    // Defaults = reading pose (tablet at the desk corner), not facing the camera.
    private fun frame(t: Long, face: Boolean = true, yaw: Float = 30f, pitch: Float = -25f) = FrameSignals(
        timeMs = t, faceVisible = face, identityMatch = null,
        yaw = if (face) yaw else null, pitch = if (face) pitch else null,
        eyesOpenProb = null, personVisible = true, noseBelowShoulders = null, phoneVisible = false,
    )

    private fun run(endMs: Long, f: (Int) -> FrameSignals): BaselineCalibrator {
        val c = BaselineCalibrator(3_000)
        var i = 0
        var t = 0L
        while (t <= endMs) { c.add(f(i++).copy(timeMs = t)); t += 100 }
        return c
    }

    @Test fun doneAfterThreeSeconds() {
        assertFalse(run(2_500) { frame(0) }.isDone())
        assertTrue(run(3_000) { frame(0) }.isDone())
    }

    @Test fun neverFailsWhenTheFaceIsMostlyLost() {
        // Head bowed over the workbook: the face is caught only now and then. Use what we have.
        val b = run(3_000) { i -> frame(0, face = i % 3 == 0) }.result()
        assertEquals(30f, b.yaw, 0.01f)
    }

    @Test fun noFaceAtAllFallsBackToDefault() {
        assertEquals(DEFAULT_BASELINE, run(3_000) { frame(0, face = false) }.result())
    }

    @Test fun medianIgnoresGlances() {
        val b = run(3_000) { i -> if (i % 5 == 0) frame(0, yaw = -40f, pitch = 10f) else frame(0) }.result()
        assertEquals(30f, b.yaw, 0.01f)
        assertEquals(-25f, b.pitch, 0.01f)
    }

    @Test fun lookingAtTheScreenFallsBackToDefault() {
        assertEquals(DEFAULT_BASELINE, run(3_000) { frame(0, yaw = 2f, pitch = -3f) }.result())
    }
}
