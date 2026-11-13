package com.wjthinkbig.meetstudy.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StudyPoseModelTest {
    private val book = Baseline(yaw = 30f, pitch = -25f) // camera at the desk corner, child reading

    private fun seeded() = StudyPoseModel().apply { seed(book) }

    @Test
    fun cameraGazeIsFaceTowardThisDevice() {
        assertTrue(isCameraGaze(0f, 0f))
        assertTrue(isCameraGaze(-11f, 8f))
        assertFalse(isCameraGaze(30f, -25f))
        assertFalse(isCameraGaze(0f, -20f)) // head bowed over a book right under the camera
    }

    @Test
    fun seededPoseAndItsEnvelopeAreStudy() {
        val m = seeded()
        assertTrue(m.isStudyPose(30f, -25f))
        assertTrue(m.isStudyPose(60f, -60f)) // still inside +-35 yaw / -40 pitch around the book
        assertFalse(m.isStudyPose(-40f, -25f))
    }

    @Test
    fun aSecondTargetBecomesStudyAfterThreeMinutesOfDwell() {
        val m = seeded()
        val tablet = -45f to 10f // another tablet on the far side, outside the book envelope
        assertFalse(m.isStudyPose(tablet.first, tablet.second))
        repeat(179) { m.observe(tablet.first, tablet.second, 1_000) }
        assertFalse(m.isStudyPose(tablet.first, tablet.second))
        m.observe(tablet.first, tablet.second, 1_000)
        assertTrue(m.isStudyPose(tablet.first, tablet.second))
    }

    @Test
    fun cameraGazeIsNeverLearned() {
        val m = seeded()
        repeat(600) { m.observe(2f, 3f, 1_000) }
        assertEquals(1, m.clusters.size)
        assertFalse(m.isStudyPose(-35f, 3f)) // nothing drifted toward the camera direction
    }

    @Test
    fun wanderingDoesNotAccumulateIntoOneCluster() {
        val m = seeded()
        // A different direction every second: no single cluster gets close to 3 minutes.
        repeat(600) { i -> m.observe(-60f + (i % 9) * 15f, 30f, 1_000) }
        assertTrue(m.clusters.filter { it.trusted }.size == 1)
    }

    @Test
    fun learnedClustersSurviveACopy() {
        val m = seeded()
        repeat(180) { m.observe(-45f, 10f, 1_000) }
        val restored = StudyPoseModel(m.clusters)
        assertTrue(restored.isStudyPose(-45f, 10f))
        assertTrue(restored.isStudyPose(30f, -25f))
    }
}

class StudyPoseModelResetTest {
    @Test
    fun resetForgetsOldCameraSpot() {
        val m = StudyPoseModel().apply { seed(Baseline(30f, -25f)) }
        repeat(180) { m.observe(-45f, 10f, 1_000) }
        m.reset(Baseline(-10f, -30f))
        assertEquals(1, m.clusters.size)
        assertTrue(m.isStudyPose(-10f, -30f))
        assertFalse(m.isStudyPose(-45f, 40f))
    }
}
