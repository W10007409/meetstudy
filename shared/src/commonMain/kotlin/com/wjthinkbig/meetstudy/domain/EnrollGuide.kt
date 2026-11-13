package com.wjthinkbig.meetstudy.domain

import kotlin.math.abs
import kotlin.math.sign

/** Face position in the upright camera image, normalized 0..1 (center x/y, box height). Mirroring does not matter. */
data class FaceBox(val cx: Float, val cy: Float, val h: Float)

enum class EnrollPose { Front, Turn, TurnOther, Up, Down }

enum class EnrollHint { NoFace, MoveCloser, MoveBack, Center, DoPose, TooMuch, Hold, Done }

data class EnrollFrame(val hint: EnrollHint, val holdProgress: Float, val captureNow: Boolean)

val ENROLL_POSES = EnrollPose.entries

/**
 * Drives face enrollment: the face must sit inside the on-screen oval and match the current pose for [holdMs].
 * Turn steps ignore the yaw sign (it differs between devices / mirroring); the second turn just needs the other side.
 * A misfit shorter than [graceMs] (one or two flickering frames at 5 fps) does not reset the hold.
 */
class EnrollTracker(private val holdMs: Long = 600, private val graceMs: Long = 450) {
    var step = 0
        private set
    private var holdStart: Long? = null
    private var lastFitMs = 0L
    private var lastYaw = 0f
    private var turnSign = 0f

    fun update(s: FrameSignals): EnrollFrame {
        val pose = ENROLL_POSES.getOrNull(step) ?: return EnrollFrame(EnrollHint.Done, 1f, false)
        val hint = hintFor(pose, s)
        if (hint != EnrollHint.Hold) {
            if (s.timeMs - lastFitMs > graceMs) holdStart = null
            return EnrollFrame(hint, holdStart?.let { progress(s.timeMs, it) } ?: 0f, false)
        }
        lastFitMs = s.timeMs
        lastYaw = s.yaw ?: 0f
        val start = holdStart ?: s.timeMs.also { holdStart = it }
        val p = progress(s.timeMs, start)
        return EnrollFrame(EnrollHint.Hold, p, p >= 1f)
    }

    /** Call after the frame flagged [EnrollFrame.captureNow] was embedded successfully. */
    fun captured() {
        if (ENROLL_POSES.getOrNull(step) == EnrollPose.Turn) turnSign = sign(lastYaw)
        step++
        holdStart = null
    }

    fun reset() {
        step = 0; holdStart = null; turnSign = 0f
    }

    private fun progress(now: Long, start: Long) = ((now - start).toFloat() / holdMs).coerceIn(0f, 1f)

    private fun hintFor(pose: EnrollPose, s: FrameSignals): EnrollHint {
        val y = s.yaw; val p = s.pitch
        if (!s.faceVisible || y == null || p == null) return EnrollHint.NoFace
        s.faceBox?.let { b ->
            if (b.h < 0.2f) return EnrollHint.MoveCloser
            if (b.h > 0.75f) return EnrollHint.MoveBack
            if (abs(b.cx - 0.5f) > 0.2f || abs(b.cy - 0.5f) > 0.22f) return EnrollHint.Center
        }
        val ay = abs(y); val ap = abs(p)
        return when (pose) {
            EnrollPose.Front -> if (ay < 12f && ap < 12f) EnrollHint.Hold else EnrollHint.DoPose
            EnrollPose.Turn, EnrollPose.TurnOther -> when {
                ay > 40f -> EnrollHint.TooMuch
                ay >= 12f && ap < 20f && (pose == EnrollPose.Turn || sign(y) == -turnSign) -> EnrollHint.Hold
                else -> EnrollHint.DoPose
            }
            EnrollPose.Up, EnrollPose.Down -> {
                val want = if (pose == EnrollPose.Up) p else -p // positive pitch = looking up
                when {
                    want > 30f -> EnrollHint.TooMuch
                    want >= 7f && ay < 18f -> EnrollHint.Hold
                    else -> EnrollHint.DoPose
                }
            }
        }
    }
}
