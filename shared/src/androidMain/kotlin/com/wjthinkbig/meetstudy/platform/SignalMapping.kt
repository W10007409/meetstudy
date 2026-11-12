package com.wjthinkbig.meetstudy.platform

import com.wjthinkbig.meetstudy.domain.FrameSignals

class FaceReading(val yaw: Float, val pitch: Float, val leftEyeOpen: Float?, val rightEyeOpen: Float?)

/** Image coords: y grows downward. */
class PoseReading(
    val leftShoulderLikelihood: Float,
    val rightShoulderLikelihood: Float,
    val shoulderAvgY: Float,
    val noseY: Float?,
)

fun toSignals(timeMs: Long, face: FaceReading?, pose: PoseReading?): FrameSignals {
    val personVisible = face != null ||
        (pose != null && pose.leftShoulderLikelihood > 0.5f && pose.rightShoulderLikelihood > 0.5f)
    return FrameSignals(
        timeMs = timeMs,
        faceVisible = face != null,
        identityMatch = null,
        yaw = face?.yaw,
        pitch = face?.pitch,
        eyesOpenProb = face?.let { listOfNotNull(it.leftEyeOpen, it.rightEyeOpen).maxOrNull() },
        personVisible = personVisible,
        noseBelowShoulders = if (face == null && personVisible) pose?.noseY?.let { it > pose.shoulderAvgY } else null,
        phoneVisible = false,
    )
}

/** Intersects the box with the bitmap; returns [x, y, width, height] or null if empty. */
fun clipRect(left: Int, top: Int, right: Int, bottom: Int, w: Int, h: Int): IntArray? {
    val l = maxOf(left, 0); val t = maxOf(top, 0)
    val r = minOf(right, w); val b = minOf(bottom, h)
    return if (r > l && b > t) intArrayOf(l, t, r - l, b - t) else null
}

/** Identity re-check cadence: first face frame after the face (re)appears, then every [intervalMs] while visible. */
class IdentitySchedule(private val intervalMs: Long = 30_000) {
    private var lastMs: Long? = null
    fun due(now: Long): Boolean = lastMs.let { it == null || now - it >= intervalMs }
    fun checked(now: Long) { lastMs = now }
    fun faceGone() { lastMs = null }
}
