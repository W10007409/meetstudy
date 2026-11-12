package com.wjthinkbig.meetstudy.platform

import kotlin.math.abs

/** Orders 4 points (x0,y0,..) as TL, TR, BR, BL using sum/difference extremes. */
fun orderCorners(p: FloatArray): FloatArray {
    val idx = 0..3
    val tl = idx.minBy { p[it * 2] + p[it * 2 + 1] }
    val br = idx.maxBy { p[it * 2] + p[it * 2 + 1] }
    val tr = idx.minBy { p[it * 2 + 1] - p[it * 2] }
    val bl = idx.maxBy { p[it * 2 + 1] - p[it * 2] }
    return floatArrayOf(p[tl * 2], p[tl * 2 + 1], p[tr * 2], p[tr * 2 + 1], p[br * 2], p[br * 2 + 1], p[bl * 2], p[bl * 2 + 1])
}

/** Shoelace area of a 4-point polygon. */
fun quadArea(q: FloatArray): Float {
    var s = 0f
    for (i in 0..3) { val j = (i + 1) % 4; s += q[i * 2] * q[j * 2 + 1] - q[j * 2] * q[i * 2 + 1] }
    return abs(s) / 2f
}

fun isConvexQuad(q: FloatArray): Boolean {
    var sign = 0
    for (i in 0..3) {
        val a = i; val b = (i + 1) % 4; val c = (i + 2) % 4
        val cross = (q[b * 2] - q[a * 2]) * (q[c * 2 + 1] - q[b * 2 + 1]) - (q[b * 2 + 1] - q[a * 2 + 1]) * (q[c * 2] - q[b * 2])
        val s = if (cross > 0) 1 else if (cross < 0) -1 else return false
        if (sign == 0) sign = s else if (s != sign) return false
    }
    return true
}

/** Convex and covering at least [minRatio] of the frame. */
fun isDocumentQuad(q: FloatArray, w: Int, h: Int, minRatio: Float = 0.2f): Boolean =
    isConvexQuad(q) && quadArea(q) >= minRatio * w * h
