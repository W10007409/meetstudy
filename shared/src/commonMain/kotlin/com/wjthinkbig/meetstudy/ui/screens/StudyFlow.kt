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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wjthinkbig.meetstudy.data.SessionRecord
import com.wjthinkbig.meetstudy.domain.Baseline
import com.wjthinkbig.meetstudy.domain.FocusState
import com.wjthinkbig.meetstudy.platform.PlatformBackHandler
import com.wjthinkbig.meetstudy.platform.rememberFrameSource

private enum class Step { Goal, Identity, Guide, Baseline, Study, Reward }

/** Setup → study → reward. Owns the single frame source shared by every camera step. */
@Composable
fun StudyFlow(onExit: () -> Unit) {
    val source = rememberFrameSource(enablePhone = false)
    var step by remember { mutableStateOf(Step.Goal) }
    var goalMs by remember { mutableStateOf(0L) }
    var baseline by remember { mutableStateOf<Baseline?>(null) }
    var record by remember { mutableStateOf<SessionRecord?>(null) }

    // Back = previous setup step. Guide goes back to Goal (not Identity: the check only re-runs when the face reappears).
    // Study handles back itself (end-confirm dialog), so this handler stays out of its way.
    PlatformBackHandler(enabled = step != Step.Study) {
        when (step) {
            Step.Goal -> onExit()
            Step.Identity, Step.Guide -> step = Step.Goal
            Step.Baseline -> step = Step.Guide
            Step.Study, Step.Reward -> onExit()
        }
    }

    when (step) {
        Step.Goal -> GoalSetupScreen { goalMs = it; step = Step.Identity }
        Step.Identity -> IdentityCheckScreen(source) { step = Step.Guide }
        Step.Guide -> PlacementGuideScreen(source) { step = Step.Baseline }
        Step.Baseline -> BaselineScreen(source) { baseline = it; step = Step.Study }
        Step.Study -> StudyScreen(source, goalMs, checkNotNull(baseline)) { record = it; step = Step.Reward }
        Step.Reward -> RewardPlaceholder(checkNotNull(record), onExit)
    }
}

// ponytail: placeholder until Task 10's reward screen.
@Composable
private fun RewardPlaceholder(r: SessionRecord, onHome: () -> Unit) {
    val focused = r.segments.filter { it.state == FocusState.STUDYING }.sumOf { it.endMs - it.startMs }
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        Text(if (focused >= r.goalMs) "목표 달성!" else "오늘은 여기까지!", fontSize = 40.sp)
        Text("순공부 ${formatClock(focused, alwaysHours = true)}", fontSize = 24.sp)
        Text("받은 별 ${(if (r.goalStar) 1 else 0) + (if (r.proofStar) 1 else 0)}개", fontSize = 24.sp)
        BigButton("홈으로", enabled = true, onClick = onHome)
    }
}
