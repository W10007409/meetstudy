package com.wjthinkbig.meetstudy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.wjthinkbig.meetstudy.data.SessionStore
import com.wjthinkbig.meetstudy.data.appFilesDir
import com.wjthinkbig.meetstudy.domain.localDate
import com.wjthinkbig.meetstudy.domain.stars
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
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
import com.wjthinkbig.meetstudy.platform.PlatformBackHandler
import com.wjthinkbig.meetstudy.ui.todayIn
import com.wjthinkbig.meetstudy.platform.rememberFrameSource

private enum class Step { Goal, Identity, Guide, Baseline, Study, Reward, Proof, Report }

/** Setup → study → reward. Owns the single frame source shared by every camera step. */
@Composable
fun StudyFlow(onExit: () -> Unit) {
    val source = rememberFrameSource(enablePhone = true)
    var step by remember { mutableStateOf(Step.Goal) }
    var goalMs by remember { mutableStateOf(0L) }
    var baseline by remember { mutableStateOf<Baseline?>(null) }
    var record by remember { mutableStateOf<SessionRecord?>(null) }
    var history by remember { mutableStateOf<List<SessionRecord>>(emptyList()) }
    val tz = remember { TimeZone.currentSystemDefault() }
    val today = remember { todayIn(tz) }
    // Store read happens off the main thread whenever we land on Reward (the record was saved just before).
    LaunchedEffect(step == Step.Reward) {
        if (step == Step.Reward) history = withContext(Dispatchers.Default) { SessionStore(appFilesDir()).all() }
    }

    // Back = previous setup step. Guide goes back to Goal (not Identity: the check only re-runs when the face reappears).
    // Study handles back itself (end-confirm dialog), so this handler stays out of its way.
    PlatformBackHandler(enabled = step != Step.Study) {
        when (step) {
            Step.Goal -> onExit()
            Step.Identity, Step.Guide -> step = Step.Goal
            Step.Baseline -> step = Step.Guide
            Step.Study, Step.Reward -> onExit()
            Step.Proof, Step.Report -> step = Step.Reward
        }
    }

    when (step) {
        Step.Goal -> GoalSetupScreen { goalMs = it; step = Step.Identity }
        Step.Identity -> IdentityCheckScreen(source) { step = Step.Guide }
        Step.Guide -> PlacementGuideScreen(source) { step = Step.Baseline }
        Step.Baseline -> BaselineScreen(source) { baseline = it; step = Step.Study }
        Step.Study -> StudyScreen(source, goalMs, checkNotNull(baseline)) { record = it; step = Step.Reward }
        Step.Reward -> {
            val r = checkNotNull(record)
            RewardScreen(r, history.filter { it.localDate(tz) == today }.sumOf { it.stars() }, { step = Step.Proof }, { step = Step.Report }, onExit)
        }
        // ponytail: Task 11 replaces this with the workbook-proof camera.
        Step.Proof -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)) {
            Text("문제집 인증은 다음 단계에서 열려요", fontSize = 28.sp)
            BigButton("돌아가기", enabled = true) { step = Step.Reward }
        }
        Step.Report -> ReportScreen(checkNotNull(record), history, tz, today) { step = Step.Reward }
    }
}
