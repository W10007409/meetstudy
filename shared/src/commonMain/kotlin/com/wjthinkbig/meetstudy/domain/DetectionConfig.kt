package com.wjthinkbig.meetstudy.domain

data class DetectionConfig(
    val awayMs: Long = 10_000,
    val lookAwayMs: Long = 15_000,
    val drowsyMs: Long = 20_000,
    val phoneMs: Long = 10_000,
    val yawLimitDeg: Float = 35f,
    val pitchUpLimitDeg: Float = 25f,
    val pitchDownAllowDeg: Float = 40f,
    val eyesClosedProb: Float = 0.3f,
    val eyesRulePitchDownMaxDeg: Float = 15f, // eyes-closed rule ignored when the head points further down than this (camera-relative)
    val returnDebounceMs: Long = 2_000,
    val candidateGraceMs: Long = 1_000,
    val cameraGazeMs: Long = 10_000, // staring at this device (timer screen) longer than a glance
    val cameraGazeDeg: Float = 12f,
    val poseMatchDeg: Float = 12f, // same study direction when this close
    val poseTrustMs: Long = 180_000, // dwell before a learned direction counts as studying
)
