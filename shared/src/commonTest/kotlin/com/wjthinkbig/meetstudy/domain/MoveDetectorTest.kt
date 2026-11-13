package com.wjthinkbig.meetstudy.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class MoveDetectorTest {
    // Gravity tilted by [deg] around the x axis.
    private fun g(deg: Double) = floatArrayOf(0f, (9.8 * kotlin.math.cos(deg * kotlin.math.PI / 180)).toFloat(), (9.8 * kotlin.math.sin(deg * kotlin.math.PI / 180)).toFloat())

    private fun feed(d: MoveDetector, fromMs: Long, toMs: Long, deg: Double): Int {
        var fired = 0
        var t = fromMs
        while (t <= toMs) { val v = g(deg); if (d.onSample(t, v[0], v[1], v[2])) fired++; t += 50 }
        return fired
    }

    @Test fun restingOrSmallWobbleNeverFires() {
        val d = MoveDetector()
        assertEquals(0, feed(d, 0, 10_000, 0.0))
        assertEquals(0, feed(d, 10_050, 20_000, 6.0)) // a tap or a small lean of the stand
    }

    @Test fun aBriefBumpDoesNotFire() {
        val d = MoveDetector()
        feed(d, 0, 1_000, 0.0)
        assertEquals(0, feed(d, 1_050, 2_000, 30.0)) // under 2 s
        assertEquals(0, feed(d, 2_050, 5_000, 0.0))
    }

    @Test fun aNewRestingAngleFiresOnceThenBecomesTheReference() {
        val d = MoveDetector()
        feed(d, 0, 1_000, 0.0)
        assertEquals(1, feed(d, 1_050, 8_000, 20.0))
        assertEquals(1, feed(d, 8_050, 14_000, 0.0)) // moved back: that is another move
    }
}
