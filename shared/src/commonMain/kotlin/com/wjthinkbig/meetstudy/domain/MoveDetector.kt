package com.wjthinkbig.meetstudy.domain

import kotlin.math.acos
import kotlin.math.sqrt

/**
 * Tells when the device (and so the camera) was moved to a new position: the gravity direction stays at least
 * [tiltDeg] away from where it was for [holdMs]. Brief bumps and taps on the screen do not count.
 * ponytail: sliding the stand flat on the desk without tilting is not caught; add a face-position check if that matters.
 */
class MoveDetector(private val tiltDeg: Float = 10f, private val holdMs: Long = 2_000) {
    private var ref: FloatArray? = null
    private var awaySince: Long? = null

    /** Feed raw accelerometer m/s^2; returns true once per move, after the device has rested in its new spot. */
    fun onSample(timeMs: Long, x: Float, y: Float, z: Float): Boolean {
        val n = sqrt(x * x + y * y + z * z)
        if (n < 1f) return false
        val g = floatArrayOf(x / n, y / n, z / n)
        val r = ref ?: run { ref = g; return false }
        val cos = (r[0] * g[0] + r[1] * g[1] + r[2] * g[2]).coerceIn(-1f, 1f)
        val deg = acos(cos) * 180f / kotlin.math.PI.toFloat()
        if (deg < tiltDeg) { awaySince = null; return false }
        val since = awaySince ?: timeMs.also { awaySince = it }
        if (timeMs - since < holdMs) return false
        ref = g; awaySince = null
        return true
    }
}
