package com.wjthinkbig.meetstudy.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EnrollTrackerTest {
    private val centered = FaceBox(0.5f, 0.5f, 0.4f)

    private fun sig(t: Long, yaw: Float, pitch: Float, box: FaceBox? = centered, face: Boolean = true) = FrameSignals(
        timeMs = t, faceVisible = face, identityMatch = null, yaw = if (face) yaw else null, pitch = if (face) pitch else null,
        eyesOpenProb = 1f, personVisible = face, noseBelowShoulders = null, phoneVisible = false, faceBox = box,
    )

    /** Holds the pose from t until capture; returns the time of the capture frame. */
    private fun holdUntilCapture(tr: EnrollTracker, t0: Long, yaw: Float, pitch: Float): Long {
        var t = t0
        while (t < t0 + 2_000) {
            if (tr.update(sig(t, yaw, pitch)).captureNow) { tr.captured(); return t }
            t += 200
        }
        error("no capture for yaw=$yaw pitch=$pitch at step ${tr.step}")
    }

    @Test
    fun fullSequenceWorksWithEitherTurnDirectionFirst() {
        for (first in listOf(25f, -25f)) {
            val tr = EnrollTracker()
            var t = holdUntilCapture(tr, 0, 0f, 0f)
            t = holdUntilCapture(tr, t + 200, first, 0f)
            assertFalse(tr.update(sig(t + 200, first, 0f)).hint == EnrollHint.Hold, "same side must not count twice")
            t = holdUntilCapture(tr, t + 400, -first, 0f)
            t = holdUntilCapture(tr, t + 200, 0f, 15f)
            holdUntilCapture(tr, t + 200, 0f, -15f)
            assertEquals(ENROLL_POSES.size, tr.step)
            assertEquals(EnrollHint.Done, tr.update(sig(t + 5_000, 0f, 0f)).hint)
        }
    }

    @Test
    fun placementHints() {
        val tr = EnrollTracker()
        assertEquals(EnrollHint.NoFace, tr.update(sig(0, 0f, 0f, face = false)).hint)
        assertEquals(EnrollHint.MoveCloser, tr.update(sig(0, 0f, 0f, FaceBox(0.5f, 0.5f, 0.1f))).hint)
        assertEquals(EnrollHint.MoveBack, tr.update(sig(0, 0f, 0f, FaceBox(0.5f, 0.5f, 0.9f))).hint)
        assertEquals(EnrollHint.Center, tr.update(sig(0, 0f, 0f, FaceBox(0.85f, 0.5f, 0.4f))).hint)
        assertEquals(EnrollHint.Hold, tr.update(sig(0, 0f, 0f, box = null)).hint) // sources without a box skip placement
    }

    @Test
    fun overTurnAsksForLess() {
        val tr = EnrollTracker()
        holdUntilCapture(tr, 0, 0f, 0f)
        assertEquals(EnrollHint.TooMuch, tr.update(sig(5_000, 50f, 0f)).hint)
    }

    @Test
    fun oneFlickerFrameDoesNotResetHold() {
        val tr = EnrollTracker()
        tr.update(sig(0, 0f, 0f))
        tr.update(sig(200, 0f, 0f))
        tr.update(sig(400, 0f, 0f, face = false)) // single dropped detection
        assertTrue(tr.update(sig(600, 0f, 0f)).captureNow)
    }

    @Test
    fun longerMisfitResetsHold() {
        val tr = EnrollTracker()
        tr.update(sig(0, 0f, 0f))
        tr.update(sig(200, 0f, 0f))
        tr.update(sig(400, 30f, 0f))
        tr.update(sig(800, 30f, 0f))
        assertFalse(tr.update(sig(1_000, 0f, 0f)).captureNow)
    }
}
