package com.wjthinkbig.meetstudy.domain

import kotlin.math.sqrt

/** Collects frames for [durationMs] and yields the child's resting head pose, or null if the sample is unusable. */
class BaselineCalibrator(private val durationMs: Long = 3_000) {
    private val frames = mutableListOf<FrameSignals>()

    fun add(s: FrameSignals) { frames += s }

    fun result(): Baseline? {
        if (frames.isEmpty() || frames.last().timeMs - frames.first().timeMs < durationMs) return null
        val face = frames.filter { it.faceVisible && it.yaw != null && it.pitch != null }
        if (face.size < frames.size * 0.8) return null
        val yaws = face.map { it.yaw!! }
        val mean = yaws.average()
        if (sqrt(yaws.sumOf { (it - mean) * (it - mean) } / yaws.size) >= 8.0) return null
        val b = Baseline(mean.toFloat(), face.map { it.pitch!! }.average().toFloat())
        // The reference is the study pose (eyes on the workbook); staring at this screen is not studying.
        return if (isCameraGaze(b.yaw, b.pitch)) null else b
    }
}
