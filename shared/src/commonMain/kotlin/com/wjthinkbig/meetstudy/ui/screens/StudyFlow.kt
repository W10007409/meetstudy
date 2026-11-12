package com.wjthinkbig.meetstudy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.wjthinkbig.meetstudy.domain.Baseline
import com.wjthinkbig.meetstudy.platform.PlatformBackHandler
import com.wjthinkbig.meetstudy.platform.rememberFrameSource

private enum class Step { Goal, Identity, Guide, Baseline, Study }

/** Setup flow. Owns the single frame source; Task 8's study screen replaces the placeholder in the same flow. */
@Composable
fun StudyFlow(onExit: () -> Unit) {
    val source = rememberFrameSource(enablePhone = false)
    var step by remember { mutableStateOf(Step.Goal) }
    var goalMs by remember { mutableStateOf(0L) }
    var baseline by remember { mutableStateOf<Baseline?>(null) }

    // Back = previous setup step. Guide goes back to Goal (not Identity: the check only re-runs when the face reappears).
    PlatformBackHandler {
        when (step) {
            Step.Goal -> onExit()
            Step.Identity, Step.Guide -> step = Step.Goal
            Step.Baseline -> step = Step.Guide
            Step.Study -> onExit()
        }
    }

    when (step) {
        Step.Goal -> GoalSetupScreen { goalMs = it; step = Step.Identity }
        Step.Identity -> IdentityCheckScreen(source) { step = Step.Guide }
        Step.Guide -> PlacementGuideScreen(source) { step = Step.Baseline }
        Step.Baseline -> BaselineScreen(source) { baseline = it; step = Step.Study }
        Step.Study -> Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("공부 화면은 다음 단계에서 만들어요", fontSize = 24.sp)
            Text("목표 ${goalMs / 60_000}분", fontSize = 20.sp)
            Text("기준 자세 yaw=${baseline?.yaw} pitch=${baseline?.pitch}", fontSize = 16.sp)
            BigButton("홈으로", enabled = true, onClick = onExit)
        }
    }
}
