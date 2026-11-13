package com.wjthinkbig.meetstudy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wjthinkbig.meetstudy.domain.Baseline
import com.wjthinkbig.meetstudy.domain.BaselineCalibrator
import com.wjthinkbig.meetstudy.platform.CameraPreview
import com.wjthinkbig.meetstudy.platform.FrameSource
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.withTimeoutOrNull

internal const val BASELINE_MS = 3_000L
private const val BASELINE_GUARD_MS = 6_000L // a stalled camera must not hang the screen

/** Collects [BASELINE_MS] of frames (or gives up at the guard) and always moves on; it only anchors the camera spot. */
internal suspend fun calibrate(source: FrameSource, onRemaining: (Int) -> Unit = {}): Baseline {
    val calibrator = BaselineCalibrator(BASELINE_MS)
    var start: Long? = null
    withTimeoutOrNull(BASELINE_GUARD_MS) {
        source.signals.takeWhile { s ->
            calibrator.add(s)
            val t0 = start ?: s.timeMs.also { start = it }
            onRemaining((((BASELINE_MS - (s.timeMs - t0)) + 999) / 1000).toInt().coerceIn(0, 3))
            !calibrator.isDone()
        }.collect {}
    }
    return calibrator.result()
}

@Composable
fun BaselineScreen(source: FrameSource, onDone: (Baseline) -> Unit) {
    var remaining by remember { mutableIntStateOf(3) }
    LaunchedEffect(source) { onDone(calibrate(source) { remaining = it }) }

    Column(
        Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("카메라 위치를 잡을게요", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("평소처럼 문제집을 보고 있어요", fontSize = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(28.dp)), contentAlignment = Alignment.Center) {
            CameraPreview(Modifier.fillMaxSize())
            Text("$remaining", fontSize = 96.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White)
        }
    }
}
