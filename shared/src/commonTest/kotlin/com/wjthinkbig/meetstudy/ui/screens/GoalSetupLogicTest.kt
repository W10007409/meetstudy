package com.wjthinkbig.meetstudy.ui.screens

import kotlin.test.Test
import kotlin.test.assertEquals

class GoalSetupLogicTest {
    @Test fun stepsStayInRange() {
        assertEquals(70, stepGoal(60, 10))
        assertEquals(GOAL_MIN, stepGoal(10, -10))
        assertEquals(GOAL_MAX, stepGoal(300, 10))
    }

    @Test fun captionReadsNaturally() {
        assertEquals("30분", goalCaption(30))
        assertEquals("1시간", goalCaption(60))
        assertEquals("1시간 30분", goalCaption(90))
    }
}

class GoalDialTest {
    @Test fun oneTurnIsAnHourAndCrossingTwelveDoesNotJump() {
        assertEquals(75f, dialMinutes(60f, 90f))        // quarter turn = 15 min
        assertEquals(65f, dialMinutes(60f, -330f))      // 350deg -> 20deg read as +30deg, not -330
        assertEquals(55f, dialMinutes(60f, 330f))       // 20deg -> 350deg read as -30deg
    }

    @Test fun dialClampsAndSnapsToFiveMinutes() {
        assertEquals(GOAL_MIN.toFloat(), dialMinutes(6f, -90f))
        assertEquals(GOAL_MAX.toFloat(), dialMinutes(299f, 90f))
        assertEquals(45, snapGoal(46.9f))
        assertEquals(50, snapGoal(47.6f))
    }

    @Test fun endTimeIsKoreanTwelveHour() {
        assertEquals("오전 12:05", clockLabel(0, 5))
        assertEquals("오후 3:40", clockLabel(15, 40))
    }
}
