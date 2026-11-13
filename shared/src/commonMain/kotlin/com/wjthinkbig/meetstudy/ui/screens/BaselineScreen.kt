package com.wjthinkbig.meetstudy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wjthinkbig.meetstudy.domain.Baseline
import com.wjthinkbig.meetstudy.domain.BaselineCalibrator
import com.wjthinkbig.meetstudy.domain.isCameraGaze
import com.wjthinkbig.meetstudy.platform.CameraPreview
import com.wjthinkbig.meetstudy.platform.FrameSource
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.withTimeoutOrNull

private const val BASELINE_MS = 3_000L
private const val BASELINE_GUARD_MS = 15_000L // includes time to look down from the screen to the workbook

/** 3-second countdown while frames feed the calibrator; a null result offers a retry. */
@Composable
fun BaselineScreen(source: FrameSource, onDone: (Baseline) -> Unit) {
    var attempt by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    var remaining by remember { mutableIntStateOf(3) }

    LaunchedEffect(source, attempt) {
        failed = false
        remaining = 3
        val calibrator = BaselineCalibrator(BASELINE_MS)
        var start: Long? = null
        // Wall-clock guard: no frames (or a stalled camera) must not hang the screen.
        val finished = withTimeoutOrNull(BASELINE_GUARD_MS) {
        source.signals.takeWhile { s ->
            // Start the 3 s only once the child has looked down from the screen to the workbook.
            val y = s.yaw; val p = s.pitch
            if (start == null && (!s.faceVisible || y == null || p == null || isCameraGaze(y, p))) return@takeWhile true
            val t0 = start ?: s.timeMs.also { start = it }
            calibrator.add(s)
            val elapsed = s.timeMs - t0
            remaining = (((BASELINE_MS - elapsed) + 999) / 1000).toInt().coerceIn(0, 3)
            elapsed < BASELINE_MS
        }.collect {}
        }
        val b = if (finished == null) null else calibrator.result()
        if (b != null) onDone(b) else failed = true
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("평소처럼 문제집을 봐요", fontSize = 32.sp)
        Text("화면 말고 문제집을 보고 있으면 시작돼요", fontSize = 20.sp)
        CameraPreview(Modifier.fillMaxWidth().height(360.dp).clip(RoundedCornerShape(32.dp)))
        if (failed) {
            Text("문제집을 보고 가만히 있어 볼까요?", fontSize = 26.sp)
            BigButton("다시 해보기", enabled = true) { attempt++ }
        } else {
            Text("$remaining", fontSize = 96.sp)
        }
    }
}
