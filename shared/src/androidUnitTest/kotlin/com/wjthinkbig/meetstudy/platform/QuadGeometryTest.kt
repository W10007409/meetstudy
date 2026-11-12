package com.wjthinkbig.meetstudy.platform

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuadGeometryTest {
    private val square = floatArrayOf(10f, 10f, 90f, 10f, 90f, 90f, 10f, 90f) // TL,TR,BR,BL

    @Test
    fun ordersCornersFromAnyStart() {
        val shuffled = floatArrayOf(90f, 90f, 10f, 10f, 10f, 90f, 90f, 10f)
        assertContentEquals(square, orderCorners(shuffled))
    }

    @Test
    fun areaIsShoelace() = assertEquals(6400f, quadArea(square))

    @Test
    fun bowtieIsNotConvex() {
        assertTrue(isConvexQuad(square))
        assertFalse(isConvexQuad(floatArrayOf(10f, 10f, 90f, 90f, 90f, 10f, 10f, 90f)))
    }

    @Test
    fun needsTwentyPercentOfFrame() {
        assertTrue(isDocumentQuad(square, 150, 100)) // 6400 / 15000 = 43%
        assertFalse(isDocumentQuad(square, 400, 400)) // 4%
    }
}
