package com.wjthinkbig.meetstudy.domain

data class FrameSignals(
    val timeMs: Long,
    val faceVisible: Boolean,
    val identityMatch: Boolean?, // null = not checked this frame
    val yaw: Float?,
    val pitch: Float?, // degrees, negative = down
    val eyesOpenProb: Float?, // larger of the two eyes
    val personVisible: Boolean, // upper body; only meaningful when no face
    val noseBelowShoulders: Boolean?, // head-down-on-desk signal
    val phoneVisible: Boolean,
    val faceBox: FaceBox? = null, // only set by sources that know the face position
    val debug: DebugFrame? = null, // only while the recognition view is on
)

/**
 * What the detectors saw, for the on-screen recognition view (test builds). Upright image pixels, not mirrored.
 * [face] = left, top, right, bottom; [pose] = x, y, inFrameLikelihood for each ML Kit pose landmark (33).
 * Memory only: never persist or log.
 */
class DebugFrame(val imgW: Int, val imgH: Int, val face: FloatArray?, val pose: FloatArray?)

data class Baseline(val yaw: Float, val pitch: Float)
