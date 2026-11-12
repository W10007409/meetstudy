package com.wjthinkbig.meetstudy.domain

import kotlin.math.abs

class FocusStateMachine(private val baseline: Baseline, private val config: DetectionConfig = DetectionConfig()) {
    var state: FocusState = FocusState.STUDYING
        private set

    private var candidate: FocusState? = null
    private var candidateSince = 0L
    private var clearSince: Long? = null
    private var identityMatch: Boolean? = null

    fun onFrame(s: FrameSignals): StateChange? {
        if (state == FocusState.APP_LEFT || state == FocusState.BREAK) return null
        s.identityMatch?.let { identityMatch = it }
        val c = candidateOf(s)
        if (c == null) {
            candidate = null
            if (state == FocusState.STUDYING) return null
            val start = clearSince ?: s.timeMs.also { clearSince = it }
            return if (s.timeMs - start >= config.returnDebounceMs) enter(FocusState.STUDYING, start) else null
        }
        clearSince = null
        if (c != candidate) { candidate = c; candidateSince = s.timeMs }
        if (c == state || s.timeMs - candidateSince < thresholdMs(c)) return null
        return enter(c, candidateSince)
    }

    fun setAppLeft(left: Boolean, timeMs: Long) = override(FocusState.APP_LEFT, left, timeMs)
    fun setBreak(onBreak: Boolean, timeMs: Long) = override(FocusState.BREAK, onBreak, timeMs)

    private fun override(kind: FocusState, on: Boolean, timeMs: Long): StateChange? = when {
        on && state != kind -> enter(kind, timeMs)
        !on && state == kind -> enter(FocusState.STUDYING, timeMs)
        else -> null
    }

    private fun enter(next: FocusState, at: Long): StateChange {
        state = next
        candidate = null
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
        val eyesClosed = s.eyesOpenProb?.let { it < config.eyesClosedProb } == true
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
