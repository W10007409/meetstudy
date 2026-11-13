package com.wjthinkbig.meetstudy.domain

/**
 * [poses] holds where this child studies; it is seeded with the calibrated workbook pose and keeps learning
 * from the frames it sees (see [StudyPoseModel]). Pass a persisted model to carry learning across sessions.
 */
class FocusStateMachine(
    baseline: Baseline,
    private val config: DetectionConfig = DetectionConfig(),
    val poses: StudyPoseModel = StudyPoseModel(config = config),
) {
    init { poses.seed(baseline) }

    /** The camera was moved: re-anchor the study pose. */
    fun recalibrate(b: Baseline) = poses.reset(b)

    var state: FocusState = FocusState.STUDYING
        private set

    private var candidate: FocusState? = null
    private var candidateSince = 0L
    private var interruptSince: Long? = null // first frame not matching the candidate, of any kind
    private var clearSince: Long? = null
    private var identityMatch: Boolean? = null
    private var appLeft = false
    private var onBreak = false
    private var lastFrameMs: Long? = null
    private var drowsyByEyes = false // which DROWSY rule fired last; they wait different times

    /** What the current frames point to, before its threshold makes it the state; null when nothing differs. */
    val pending: FocusState? get() = candidate?.takeIf { it != state }

    /** When [pending] becomes the state if it persists. Only meaningful while [pending] is non-null. */
    val pendingDueMs: Long get() = candidateSince + (candidate?.let(::thresholdMs) ?: 0L)

    fun onFrame(s: FrameSignals): StateChange? {
        if (state == FocusState.APP_LEFT || state == FocusState.BREAK) return null
        s.identityMatch?.let { identityMatch = it }
        val c = candidateOf(s)
        learn(s, c)
        var back: StateChange? = null
        if (c == null) {
            if (state != FocusState.STUDYING) {
                val start = clearSince ?: s.timeMs.also { clearSince = it }
                if (s.timeMs - start >= config.returnDebounceMs) back = enter(FocusState.STUDYING, start)
            }
        } else clearSince = null
        if (back != null) return back
        trackCandidate(c, s.timeMs)
        val k = candidate ?: return null
        if (k == state || s.timeMs - candidateSince < thresholdMs(k)) return null
        return enter(k, candidateSince)
    }

    // Short interruptions (< candidateGraceMs) keep the pending candidate; longer ones replace/drop it.
    // Mixed non-matching kinds count as one interruption; at grace the latest frame decides:
    // clear drops the candidate, another kind replaces it with candidateSince = interruption start.
    private fun trackCandidate(c: FocusState?, t: Long) {
        if (candidate == null) {
            if (c != null) { candidate = c; candidateSince = t }
            return
        }
        if (c == candidate) { interruptSince = null; return }
        val since = interruptSince ?: t.also { interruptSince = it }
        if (t - since >= config.candidateGraceMs) {
            candidate = c
            candidateSince = since
            interruptSince = null
        }
    }

    fun setAppLeft(left: Boolean, timeMs: Long): StateChange? { appLeft = left; return applyOverrides(timeMs) }
    fun setBreak(onBreak: Boolean, timeMs: Long): StateChange? { this.onBreak = onBreak; return applyOverrides(timeMs) }

    fun forceAway(timeMs: Long): StateChange = enter(FocusState.AWAY, timeMs)

    // Precedence APP_LEFT > BREAK; STUDYING only when both are off.
    private fun applyOverrides(t: Long): StateChange? {
        val target = when {
            appLeft -> FocusState.APP_LEFT
            onBreak -> FocusState.BREAK
            state == FocusState.APP_LEFT || state == FocusState.BREAK -> FocusState.STUDYING
            else -> return null
        }
        return if (target == state) null else enter(target, t)
    }

    private fun enter(next: FocusState, at: Long): StateChange {
        state = next
        candidate = null
        interruptSince = null
        clearSince = null
        return StateChange(next, at)
    }

    private fun thresholdMs(k: FocusState) = when (k) {
        FocusState.AWAY -> config.awayMs
        FocusState.PHONE -> config.phoneMs
        FocusState.DROWSY -> if (drowsyByEyes) config.eyesClosedMs else config.drowsyMs
        FocusState.CAMERA_GAZE -> config.cameraGazeMs
        else -> config.lookAwayMs
    }

    /** Learns only from frames that look like attention somewhere other than this device: studying or plain looking away. */
    private fun learn(s: FrameSignals, c: FocusState?) {
        val dt = lastFrameMs?.let { (s.timeMs - it).coerceIn(0L, 1_000L) } ?: 0L
        lastFrameMs = s.timeMs
        val y = s.yaw; val p = s.pitch
        if (s.faceVisible && y != null && p != null && (c == null || c == FocusState.LOOKING_AWAY)) poses.observe(y, p, dt)
    }

    // Priority: AWAY > PHONE > DROWSY > CAMERA_GAZE > LOOKING_AWAY; null = studying.
    private fun candidateOf(s: FrameSignals): FocusState? {
        if ((!s.faceVisible && !s.personVisible) || identityMatch == false) return FocusState.AWAY
        if (s.phoneVisible) return FocusState.PHONE
        // Eye-open probability drops when the face points down (reading), so only trust it with the head up.
        val headDown = s.pitch?.let { it < -config.eyesRulePitchDownMaxDeg } == true
        val eyesClosed = !headDown && s.eyesOpenProb?.let { it < config.eyesClosedProb } == true
        if (eyesClosed) { drowsyByEyes = true; return FocusState.DROWSY }
        if (!s.faceVisible && s.personVisible && s.noseBelowShoulders == true) { drowsyByEyes = false; return FocusState.DROWSY }
        val y = s.yaw; val p = s.pitch
        if (s.faceVisible && y != null && p != null) {
            if (isCameraGaze(y, p, config.cameraGazeDeg)) return FocusState.CAMERA_GAZE
            if (!poses.isStudyPose(y, p)) return FocusState.LOOKING_AWAY
        }
        return null
    }
}
