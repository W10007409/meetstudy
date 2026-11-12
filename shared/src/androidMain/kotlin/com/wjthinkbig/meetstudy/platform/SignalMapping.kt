package com.wjthinkbig.meetstudy.platform

import com.wjthinkbig.meetstudy.domain.FrameSignals
import kotlin.math.abs

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

/**
 * Identity re-check cadence: first near-frontal face frame after the face (re)appears, then every [intervalMs]
 * after a match, or [retryMs] after a mismatch / embed failure. A single mismatch is not trusted (emits null);
 * false is emitted only on the 2nd consecutive mismatch. The mismatch count resets on a match or when the face leaves.
 */
class IdentitySchedule(
    private val intervalMs: Long = 30_000,
    private val retryMs: Long = 3_000,
    private val maxAngleDeg: Float = 20f,
) {
    private var nextMs: Long? = null // null = due on the next near-frontal frame
    private var mismatches = 0

    /** [yaw]/[pitch] are raw ML Kit angles; turned heads embed poorly, so the check waits until near-frontal. */
    fun due(now: Long, yaw: Float, pitch: Float): Boolean =
        abs(yaw) <= maxAngleDeg && abs(pitch) <= maxAngleDeg && nextMs.let { it == null || now >= it }

    /** Records a check outcome ([match] null = embed failed); returns the identityMatch value to emit. */
    fun result(now: Long, match: Boolean?): Boolean? {
        nextMs = now + if (match == true) intervalMs else retryMs
        return when (match) {
            true -> { mismatches = 0; true }
            false -> if (++mismatches >= 2) false else null
            null -> null
        }
    }

    fun faceGone() { nextMs = null; mismatches = 0 }
}
