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
