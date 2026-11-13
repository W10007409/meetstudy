package com.wjthinkbig.meetstudy.domain

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.hypot

/** A head direction the child studies toward (camera-relative degrees). Trusted ones count as studying. */
@Serializable
data class PoseCluster(val yaw: Float, val pitch: Float, val dwellMs: Long, val trusted: Boolean)

/** Face pointed at this device, i.e. at the timer screen rather than at a book or another tablet. */
fun isCameraGaze(yaw: Float, pitch: Float, maxDeg: Float = 12f): Boolean = abs(yaw) < maxDeg && abs(pitch) < maxDeg

private const val MAX_CLUSTERS = 6
private const val LEARN_RATE = 0.01f // per frame; ~20 s time constant at 5 fps

/**
 * Learns where this child looks while studying. The calibrated pose (looking at the workbook) is trusted at once;
 * any other steady direction becomes trusted after [DetectionConfig.poseTrustMs] of total dwell, across sessions,
 * so a second tablet or a monitor is picked up without setup. The camera direction is never learned.
 * ponytail: steady daydreaming in one direction for 3+ minutes would also be learned; "공부 자세 다시 배우기" resets.
 */
class StudyPoseModel(initial: List<PoseCluster> = emptyList(), private val config: DetectionConfig = DetectionConfig()) {
    private val list = initial.toMutableList()
    val clusters: List<PoseCluster> get() = list.toList()

    fun seed(b: Baseline) {
        val i = nearest(b.yaw, b.pitch)
        if (i != null) { list[i] = list[i].copy(trusted = true); return }
        if (list.size >= MAX_CLUSTERS) list.remove(list.minBy { (if (it.trusted) 1L shl 40 else 0L) + it.dwellMs })
        list += PoseCluster(b.yaw, b.pitch, 0, trusted = true)
    }

    /** Camera moved: every learned direction is relative to the old camera spot, so start over from [b]. */
    fun reset(b: Baseline) { list.clear(); seed(b) }

    /** Inside the old fixed envelope (yaw +-35, pitch -40..+25) around any trusted direction. */
    fun isStudyPose(yaw: Float, pitch: Float): Boolean = list.any { c ->
        val d = pitch - c.pitch
        c.trusted && abs(yaw - c.yaw) <= config.yawLimitDeg && d <= config.pitchUpLimitDeg && d >= -config.pitchDownAllowDeg
    }

    /** Adds [dtMs] of dwell at this direction, nudging the nearest cluster toward it or starting a new one. */
    fun observe(yaw: Float, pitch: Float, dtMs: Long) {
        if (isCameraGaze(yaw, pitch, config.cameraGazeDeg)) return
        val i = nearest(yaw, pitch)
        if (i == null) {
            if (list.size >= MAX_CLUSTERS) {
                val weakest = list.filter { !it.trusted }.minByOrNull { it.dwellMs } ?: return // all trusted: stop adding
                list.remove(weakest)
            }
            list += PoseCluster(yaw, pitch, dtMs, trusted = dtMs >= config.poseTrustMs)
            return
        }
        val c = list[i]
        val dwell = c.dwellMs + dtMs
        list[i] = PoseCluster(
            c.yaw + (yaw - c.yaw) * LEARN_RATE, c.pitch + (pitch - c.pitch) * LEARN_RATE,
            dwell, c.trusted || dwell >= config.poseTrustMs,
        )
    }

    private fun nearest(yaw: Float, pitch: Float): Int? =
        list.indices.map { it to hypot(list[it].yaw - yaw, list[it].pitch - pitch) }
            .filter { it.second <= config.poseMatchDeg }.minByOrNull { it.second }?.first
}
