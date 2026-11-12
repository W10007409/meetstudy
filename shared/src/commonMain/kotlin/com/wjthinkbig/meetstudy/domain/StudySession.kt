package com.wjthinkbig.meetstudy.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable
data class Segment(val state: FocusState, val startMs: Long, val endMs: Long)

data class BreakPolicy(val maxCount: Int = 3, val maxMinutesEach: Int = 10)

/** Turns the machine's StateChanges into contiguous Segments and adds goal/break bookkeeping. */
class StudySession(
    private val goalMs: Long,
    startMs: Long,
    private val machine: FocusStateMachine,
    private val breakPolicy: BreakPolicy = BreakPolicy(),
) {
    private val _state = MutableStateFlow(machine.state)
    val state: StateFlow<FocusState> = _state

    private val closed = mutableListOf<Segment>()
    private var openState = machine.state
    private var openStart = startMs
    private var breaksUsed = 0
    private var onBreak = false
    private var breakStart = 0L

    fun onFrame(s: FrameSignals) = apply(machine.onFrame(s))

    fun onAppLeft(left: Boolean, timeMs: Long) = apply(machine.setAppLeft(left, timeMs))

    fun startBreak(timeMs: Long): Boolean {
        if (breaksLeft() == 0 || machine.state == FocusState.BREAK || machine.state == FocusState.APP_LEFT) return false
        breaksUsed++
        onBreak = true
        breakStart = timeMs
        apply(machine.setBreak(true, timeMs))
        return true
    }

    fun endBreak(timeMs: Long) {
        if (!onBreak) return // already ended by overrun
        onBreak = false
        apply(machine.setBreak(false, timeMs))
    }

    fun tick(timeMs: Long) {
        val limitEnd = breakStart + breakPolicy.maxMinutesEach * 60_000L
        if (!onBreak || timeMs <= limitEnd) return
        onBreak = false
        apply(machine.setBreak(false, limitEnd))
        // Still APP_LEFT? Leave it; the away penalty only applies when the break was the sole override.
        if (machine.state == FocusState.STUDYING) apply(machine.forceAway(limitEnd))
    }

    fun focusedMs(nowMs: Long): Long =
        closed.filter { it.state == FocusState.STUDYING }.sumOf { it.endMs - it.startMs } +
            if (openState == FocusState.STUDYING) maxOf(0L, nowMs - openStart) else 0L

    fun breaksLeft(): Int = breakPolicy.maxCount - breaksUsed

    fun isGoalReached(nowMs: Long): Boolean = focusedMs(nowMs) >= goalMs

    fun finish(nowMs: Long): List<Segment> {
        close(maxOf(nowMs, openStart))
        openStart = maxOf(nowMs, openStart)
        return closed.toList()
    }

    private fun apply(change: StateChange?) {
        if (change != null) {
            close(maxOf(change.startedAtMs, openStart)) // clamp defensively; machine never goes earlier
            openState = change.state
            openStart = maxOf(change.startedAtMs, openStart)
        }
        _state.value = machine.state
    }

    private fun close(endMs: Long) {
        if (endMs > openStart) closed += Segment(openState, openStart, endMs)
    }
}
