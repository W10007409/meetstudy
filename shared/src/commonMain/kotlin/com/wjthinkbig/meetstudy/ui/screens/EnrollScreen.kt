package com.wjthinkbig.meetstudy.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wjthinkbig.meetstudy.domain.FrameSignals
import com.wjthinkbig.meetstudy.domain.averageNormalized
import com.wjthinkbig.meetstudy.platform.CameraPreview
import com.wjthinkbig.meetstudy.platform.FaceVault
import com.wjthinkbig.meetstudy.platform.rememberFaceIdentity
import com.wjthinkbig.meetstudy.platform.rememberFrameSource
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs

private class EnrollStep(val label: String, val fits: (yaw: Float, pitch: Float) -> Boolean)

// ML Kit: yaw > 0 = the child turns to their own left; pitch > 0 = looking up.
// ponytail: yaw sign not yet verified with a person on device; flip the 왼/오 ranges if a step never completes.
private val STEPS = listOf(
    EnrollStep("정면") { y, p -> abs(y) < 10f && abs(p) < 10f },
    EnrollStep("왼쪽으로 살짝") { y, p -> y in 10f..30f && abs(p) < 15f },
    EnrollStep("오른쪽으로 살짝") { y, p -> y in -30f..-10f && abs(p) < 15f },
    EnrollStep("위로 살짝") { y, p -> p in 8f..25f && abs(y) < 15f },
    EnrollStep("아래로 살짝") { y, p -> p in -25f..-8f && abs(y) < 15f },
)
private const val HOLD_MS = 500L

@Composable
fun EnrollScreen(onDone: () -> Unit) {
    val source = rememberFrameSource(enablePhone = false)
    val identity = rememberFaceIdentity()
    var consent by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }
    var step by remember { mutableIntStateOf(0) }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("내 얼굴 등록하기", fontSize = 32.sp)
        CameraPreview(Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(32.dp)))
        when {
            !identity.available -> {
                Text("지금은 본인 확인 기능이 꺼져 있어요", fontSize = 22.sp, textAlign = TextAlign.Center)
                BigButton("계속하기", enabled = true, onClick = onDone)
            }
            !started -> {
                Row(
                    Modifier.clip(RoundedCornerShape(16.dp)).clickable { consent = !consent }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = consent, onCheckedChange = { consent = it }, modifier = Modifier.size(48.dp))
                    Text("얼굴 정보는 이 기기에만 저장돼요", fontSize = 22.sp)
                }
                BigButton("시작하기", enabled = consent) { started = true }
            }
            else -> {
                Text("${(step + 1).coerceAtMost(STEPS.size)} / ${STEPS.size}", fontSize = 22.sp)
                LinearProgressIndicator(
                    progress = { step / STEPS.size.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(8.dp)),
                )
                Text(STEPS.getOrNull(step)?.label ?: "다 됐어요!", fontSize = 40.sp)
                Text("얼굴을 화면에 보여 주고 잠깐 멈춰요", fontSize = 20.sp)
            }
        }
    }

    if (started && identity.available) {
        LaunchedEffect(source) {
            val captured = mutableListOf<FloatArray>()
            var holdStart: Long? = null
            source.signals.collect { s: FrameSignals ->
                val current = STEPS.getOrNull(captured.size) ?: return@collect
                val y = s.yaw; val p = s.pitch
                if (!s.faceVisible || y == null || p == null || !current.fits(y, p)) { holdStart = null; return@collect }
                val since = holdStart ?: s.timeMs.also { holdStart = it }
                val face = source.latestFace.value
                if (s.timeMs - since < HOLD_MS || face == null) return@collect
                // Suspends collection; stale frames are dropped meanwhile. A failed embed just retries on a later frame.
                captured += try { identity.embed(face) } catch (e: CancellationException) { throw e } catch (e: Exception) { return@collect }
                holdStart = null
                step = captured.size
                if (captured.size == STEPS.size) {
                    FaceVault.save(averageNormalized(captured))
                    onDone()
                }
            }
        }
    }
}

@Composable
private fun BigButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(0.6f).height(72.dp),
    ) { Text(text, fontSize = 26.sp) }
}
