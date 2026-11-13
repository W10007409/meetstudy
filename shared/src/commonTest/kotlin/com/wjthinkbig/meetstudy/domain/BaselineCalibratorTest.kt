package com.wjthinkbig.meetstudy.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BaselineCalibratorTest {
    // Defaults = reading pose (tablet at the desk corner), not facing the camera.
    private fun frame(t: Long, face: Boolean = true, yaw: Float = 30f, pitch: Float = -25f) = FrameSignals(
        timeMs = t, faceVisible = face, identityMatch = null,
        yaw = if (face) yaw else null, pitch = if (face) pitch else null,
        eyesOpenProb = null, personVisible = true, noseBelowShoulders = null, phoneVisible = false,
    )

    private fun run(endMs: Long, f: (Int) -> FrameSignals): Baseline? {
        val c = BaselineCalibrator(3_000)
        var i = 0
        var t = 0L
        while (t <= endMs) { c.add(f(i++).copy(timeMs = t)); t += 100 }
        return c.result()
    }

    @Test fun needsThreeSeconds() {
        assertNull(run(2_500) { frame(0) })
        assertNotNull(run(3_000) { frame(0) })
    }

    @Test fun rejectsWhenMostlyNoFace() {
        assertNull(run(3_000) { i -> frame(0, face = i % 3 == 0) }) // 33% with face
    }

    @Test fun rejectsWhenUnstable() {
        assertNull(run(3_000) { i -> frame(0, yaw = if (i % 2 == 0) 50f else 10f) })
    }

    @Test fun averagesYawPitch() {
        val b = run(3_000) { i -> frame(0, yaw = 30f + (i % 2), pitch = -25f) }!!
        assertEquals(30.5f, b.yaw, 0.2f)
        assertEquals(-25f, b.pitch, 0.01f)
    }

    @Test fun rejectsLookingAtTheScreen() {
        assertNull(run(3_000) { frame(0, yaw = 2f, pitch = -3f) })
    }
}
