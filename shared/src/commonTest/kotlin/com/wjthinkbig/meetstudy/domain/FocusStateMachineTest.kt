package com.wjthinkbig.meetstudy.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FocusStateMachineTest {
    private val base = Baseline(yaw = 5f, pitch = -10f)
    private fun sm() = FocusStateMachine(base)

    private fun frame(
        t: Long, face: Boolean = true, identity: Boolean? = null, yaw: Float? = base.yaw, pitch: Float? = base.pitch,
        eyes: Float? = 0.9f, person: Boolean = true, noseBelow: Boolean? = null, phone: Boolean = false,
    ) = FrameSignals(t, face, identity, yaw, pitch, eyes, person, noseBelow, phone)

    private fun studying(t: Long) = frame(t)
    private fun noFace(t: Long, person: Boolean = false, noseBelow: Boolean? = null) =
        frame(t, face = false, yaw = null, pitch = null, eyes = null, person = person, noseBelow = noseBelow)

    /** Feeds frames every 500ms in [fromMs, toMs] inclusive; returns all non-null changes. */
    private fun FocusStateMachine.feed(fromMs: Long, toMs: Long, f: (Long) -> FrameSignals): List<StateChange> =
        (fromMs..toMs step 500).mapNotNull { onFrame(f(it)) }

    @Test fun awayAfterTenSecondsIsRetroactive() {
        val m = sm()
        assertEquals(emptyList(), m.feed(0, 9_500) { noFace(it) })
        assertEquals(StateChange(FocusState.AWAY, 0), m.onFrame(noFace(10_000)))
        assertEquals(FocusState.AWAY, m.state)
    }

    @Test fun faceLostButPersonPresentStaysStudying() {
        val m = sm()
        assertEquals(emptyList(), m.feed(0, 30_000) { noFace(it, person = true, noseBelow = false) })
        assertEquals(FocusState.STUDYING, m.state)
    }

    @Test fun lookingDownWithinFortyDegreesIsStudying() {
        val m = sm()
        assertEquals(emptyList(), m.feed(0, 30_000) { frame(it, pitch = base.pitch - 39) })
    }

    @Test fun pitchBeyondDownLimitIsLookingAway() {
        val m = sm()
        assertEquals(listOf(StateChange(FocusState.LOOKING_AWAY, 0)), m.feed(0, 15_000) { frame(it, pitch = base.pitch - 41) })
    }

    @Test fun pitchUpBeyondLimitIsLookingAway() {
        val m = sm()
        assertEquals(listOf(StateChange(FocusState.LOOKING_AWAY, 0)), m.feed(0, 15_000) { frame(it, pitch = base.pitch + 26) })
    }

    @Test fun yawBeyondLimitFor15sIsLookingAway() {
        val m = sm()
        assertEquals(emptyList(), m.feed(0, 14_500) { frame(it, yaw = base.yaw + 36) })
        assertNull(m.onFrame(frame(14_900, yaw = base.yaw + 36)))
        assertEquals(StateChange(FocusState.LOOKING_AWAY, 0), m.onFrame(frame(15_000, yaw = base.yaw + 36)))
    }

    @Test fun eyesClosed20sIsDrowsy() {
        val m = sm()
        assertEquals(listOf(StateChange(FocusState.DROWSY, 0)), m.feed(0, 20_000) { frame(it, eyes = 0.1f) })
    }

    @Test fun headDownOnDesk20sIsDrowsy() {
        val m = sm()
        assertEquals(
            listOf(StateChange(FocusState.DROWSY, 0)),
            m.feed(0, 20_000) { noFace(it, person = true, noseBelow = true) },
        )
    }

    @Test fun phone10sIsPhone() {
        val m = sm()
        assertEquals(listOf(StateChange(FocusState.PHONE, 0)), m.feed(0, 10_000) { frame(it, phone = true) })
    }

    @Test fun identityMismatchCountsAsAway() {
        val m = sm()
        assertEquals(listOf(StateChange(FocusState.AWAY, 0)), m.feed(0, 10_000) { frame(it, identity = false) })
    }

    @Test fun identityNullKeepsLastKnownValue() {
        val m = sm()
        m.onFrame(frame(0, identity = false))
        assertEquals(StateChange(FocusState.AWAY, 0), m.feed(500, 10_000) { frame(it, identity = null) }.single())
    }

    @Test fun candidateKindChangeRestartsTimer() {
        val m = sm()
        m.feed(0, 9_000) { frame(it, yaw = base.yaw + 36) } // LOOKING_AWAY candidate since 0
        // switch to phone at 9.5s: needs its own 10s
        assertEquals(emptyList(), m.feed(9_500, 19_000) { frame(it, phone = true) })
        assertEquals(StateChange(FocusState.PHONE, 9_500), m.onFrame(frame(19_500, phone = true)))
    }

    @Test fun returnToStudyingNeedsTwoSecondsOfClearSignal() {
        val m = sm()
        m.feed(0, 10_000) { noFace(it) }
        assertEquals(FocusState.AWAY, m.state)
        assertNull(m.onFrame(studying(11_000)))
        assertNull(m.onFrame(studying(12_900)))
        assertNull(m.onFrame(noFace(13_000))) // clear broken at 1.9s
        assertNull(m.onFrame(studying(14_000)))
        assertNull(m.onFrame(studying(15_500)))
        assertEquals(StateChange(FocusState.STUDYING, 14_000), m.onFrame(studying(16_000)))
    }

    @Test fun appLeftIsImmediateAndOverrides() {
        val m = sm()
        assertEquals(StateChange(FocusState.APP_LEFT, 1_000), m.setAppLeft(true, 1_000))
        assertEquals(emptyList(), m.feed(1_500, 30_000) { noFace(it) })
        assertEquals(FocusState.APP_LEFT, m.state)
        assertEquals(StateChange(FocusState.STUDYING, 31_000), m.setAppLeft(false, 31_000))
    }

    @Test fun breakSuppressesDetection() {
        val m = sm()
        assertEquals(StateChange(FocusState.BREAK, 0), m.setBreak(true, 0))
        assertEquals(emptyList(), m.feed(500, 30_000) { noFace(it) })
        assertEquals(StateChange(FocusState.STUDYING, 30_500), m.setBreak(false, 30_500))
    }

    @Test fun resumeDoesNotFireStaleCandidate() {
        val m = sm()
        m.feed(0, 5_000) { noFace(it) }
        m.setBreak(true, 5_500)
        m.setBreak(false, 6_000)
        assertNull(m.onFrame(noFace(10_500))) // timer restarts after resume: 4.5s < 10s
        assertEquals(StateChange(FocusState.AWAY, 10_500), m.feed(11_000, 20_500) { noFace(it) }.single())
    }
}
