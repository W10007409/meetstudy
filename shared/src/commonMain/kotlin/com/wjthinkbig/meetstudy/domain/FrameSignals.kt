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
)

data class Baseline(val yaw: Float, val pitch: Float)
