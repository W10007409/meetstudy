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
