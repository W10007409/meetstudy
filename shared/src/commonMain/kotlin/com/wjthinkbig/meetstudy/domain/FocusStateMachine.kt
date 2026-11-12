package com.wjthinkbig.meetstudy.domain

import kotlin.math.abs

class FocusStateMachine(private val baseline: Baseline, private val config: DetectionConfig = DetectionConfig()) {
    var state: FocusState = FocusState.STUDYING
        private set

    private var candidate: FocusState? = null
    private var candidateSince = 0L
    private var interruptSince: Long? = null // first frame not matching the candidate, of any kind
    private var clearSince: Long? = null
    private var identityMatch: Boolean? = null
    private var appLeft = false
    private var onBreak = false

    fun onFrame(s: FrameSignals): StateChange? {
        if (state == FocusState.APP_LEFT || state == FocusState.BREAK) return null
        s.identityMatch?.let { identityMatch = it }
        val c = candidateOf(s)
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
        FocusState.DROWSY -> config.drowsyMs
        else -> config.lookAwayMs
    }

    // Priority: AWAY > PHONE > DROWSY > LOOKING_AWAY; null = studying.
    private fun candidateOf(s: FrameSignals): FocusState? {
        if ((!s.faceVisible && !s.personVisible) || identityMatch == false) return FocusState.AWAY
        if (s.phoneVisible) return FocusState.PHONE
        // Eye-open probability drops when the head is bowed over a workbook, so only trust it near baseline pitch.
        val headBowed = s.pitch?.let { it < baseline.pitch - config.eyesRulePitchDownMaxDeg } == true
        val eyesClosed = !headBowed && s.eyesOpenProb?.let { it < config.eyesClosedProb } == true
        if (eyesClosed || (!s.faceVisible && s.personVisible && s.noseBelowShoulders == true)) return FocusState.DROWSY
        if (s.faceVisible) {
            val yawOut = s.yaw?.let { abs(it - baseline.yaw) > config.yawLimitDeg } == true
            val pitchOut = s.pitch?.let {
                val d = it - baseline.pitch
                d > config.pitchUpLimitDeg || d < -config.pitchDownAllowDeg
            } == true
            if (yawOut || pitchOut) return FocusState.LOOKING_AWAY
        }
        return null
    }
}
