package com.wjthinkbig.meetstudy.domain

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

class FaceMathTest {
    @Test
    fun cosineOfSameVectorIsOne() {
        val v = floatArrayOf(0.3f, -1.2f, 2.5f, 0.0f)
        assertTrue(abs(cosine(v, v) - 1f) < 1e-5f)
    }

    @Test
    fun cosineOfOrthogonalIsZero() {
        assertTrue(abs(cosine(floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 3f, 0f))) < 1e-6f)
    }

    @Test
    fun averageIsUnitLength() {
        val avg = averageNormalized(listOf(floatArrayOf(3f, 4f, 0f), floatArrayOf(0f, 10f, 1f), floatArrayOf(-2f, 1f, 5f)))
        val norm = sqrt(avg.fold(0f) { s, x -> s + x * x })
        assertTrue(abs(norm - 1f) < 1e-5f)
    }

    @Test
    fun eyeAspectMapsClosedToZeroAndOpenToOne() {
        assertTrue(eyeOpenFromAspect(0.05f) == 0f)
        assertTrue(eyeOpenFromAspect(0.35f) == 1f)
        assertTrue(eyeOpenFromAspect(0.185f) in 0.4f..0.6f)
    }
}
