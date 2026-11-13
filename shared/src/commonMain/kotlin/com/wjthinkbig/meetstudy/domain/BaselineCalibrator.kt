package com.wjthinkbig.meetstudy.domain

/** Reading pose below a front camera; used when the face was not caught well enough during calibration. */
val DEFAULT_BASELINE = Baseline(yaw = 0f, pitch = -25f)

/**
 * Collects a few seconds of frames and yields the child's starting study pose. It never fails: calibration only
 * places the camera, and [StudyPoseModel] keeps learning the real study directions afterwards.
 */
class BaselineCalibrator(private val durationMs: Long = 3_000) {
    private val frames = mutableListOf<FrameSignals>()

    fun add(s: FrameSignals) { frames += s }

    fun isDone(): Boolean = frames.size > 1 && frames.last().timeMs - frames.first().timeMs >= durationMs

    fun result(): Baseline {
        val face = frames.filter { it.faceVisible && it.yaw != null && it.pitch != null }
        if (face.size < 5) return DEFAULT_BASELINE
        // Median: a glance or a lost frame does not drag the pose.
        val b = Baseline(face.map { it.yaw!! }.sorted()[face.size / 2], face.map { it.pitch!! }.sorted()[face.size / 2])
        // Staring at this screen is not the study pose.
        return if (isCameraGaze(b.yaw, b.pitch)) DEFAULT_BASELINE else b
    }
}
