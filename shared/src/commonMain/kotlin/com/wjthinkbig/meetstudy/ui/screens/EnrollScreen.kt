package com.wjthinkbig.meetstudy.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wjthinkbig.meetstudy.domain.ENROLL_POSES
import com.wjthinkbig.meetstudy.domain.EnrollFrame
import com.wjthinkbig.meetstudy.domain.EnrollHint
import com.wjthinkbig.meetstudy.domain.EnrollPose
import com.wjthinkbig.meetstudy.domain.EnrollTracker
import com.wjthinkbig.meetstudy.domain.FrameSignals
import com.wjthinkbig.meetstudy.domain.averageNormalized
import com.wjthinkbig.meetstudy.platform.CameraPreview
import com.wjthinkbig.meetstudy.platform.FaceVault
import com.wjthinkbig.meetstudy.platform.rememberFaceIdentity
import com.wjthinkbig.meetstudy.platform.rememberFrameSource
import kotlin.coroutines.cancellation.CancellationException

private fun poseText(p: EnrollPose?) = when (p) {
    EnrollPose.Front -> "정면을 봐요"
    EnrollPose.Turn -> "고개를 옆으로 살짝 돌려요"
    EnrollPose.TurnOther -> "이번엔 반대쪽으로 살짝"
    EnrollPose.Up -> "턱을 살짝 들어요"
    EnrollPose.Down -> "턱을 살짝 내려요"
    null -> "다 됐어요!"
}

private fun hintText(h: EnrollHint) = when (h) {
    EnrollHint.NoFace -> "얼굴이 동그라미 안에 보이게 해 주세요"
    EnrollHint.MoveCloser -> "조금 더 가까이 와요"
    EnrollHint.MoveBack -> "조금 뒤로 가요"
    EnrollHint.Center -> "얼굴을 동그라미 가운데에 맞춰요"
    EnrollHint.DoPose -> " " // the pose line above already says what to do
    EnrollHint.TooMuch -> "조금만 덜 움직여요"
    EnrollHint.Hold -> "좋아요! 그대로 멈춰요"
    EnrollHint.Done -> "저장하는 중이에요"
}

private val GuideGreen = Color(0xFF3DDC97)
private val HintYellow = Color(0xFFFFD966)

@Composable
fun EnrollScreen(onDone: () -> Unit) {
    val source = rememberFrameSource(enablePhone = false)
    val identity = rememberFaceIdentity()
    val tracker = remember { EnrollTracker() }
    var consent by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }
    var step by remember { mutableIntStateOf(0) }
    var frame by remember { mutableStateOf(EnrollFrame(EnrollHint.NoFace, 0f, false)) }
    var saveFailed by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CameraPreview(Modifier.fillMaxSize())
        FaceGuide(
            fits = started && frame.hint == EnrollHint.Hold,
            progress = if (started) frame.holdProgress else 0f,
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("내 얼굴 등록하기", fontSize = 32.sp, color = Color.White)
            if (started) {
                LinearProgressIndicator(
                    progress = { step / ENROLL_POSES.size.toFloat() },
                    modifier = Modifier.fillMaxWidth(0.5f).height(12.dp).clip(RoundedCornerShape(6.dp)),
                )
                Text("${(step + 1).coerceAtMost(ENROLL_POSES.size)} / ${ENROLL_POSES.size}", fontSize = 20.sp, color = Color.White)
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp)
                .clip(RoundedCornerShape(28.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                !identity.available -> {
                    Text("지금은 본인 확인 기능이 꺼져 있어요", fontSize = 22.sp, color = Color.White, textAlign = TextAlign.Center)
                    BigButton("계속하기", enabled = true, onClick = onDone)
                }
                !started -> {
                    Text("동그라미 안에 얼굴을 맞추면 자동으로 찍혀요", fontSize = 22.sp, color = Color.White, textAlign = TextAlign.Center)
                    Row(
                        Modifier.clip(RoundedCornerShape(16.dp)).clickable { consent = !consent }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = consent, onCheckedChange = { consent = it }, modifier = Modifier.size(48.dp))
                        Text("얼굴 정보는 이 기기에만 저장돼요", fontSize = 22.sp, color = Color.White)
                    }
                    if (saveFailed) Text("저장에 실패했어요. 다시 해볼까요?", fontSize = 22.sp, color = Color.White, textAlign = TextAlign.Center)
                    BigButton(if (saveFailed) "다시 해보기" else "시작하기", enabled = consent) {
                        saveFailed = false; tracker.reset(); step = 0; started = true
                    }
                }
                else -> {
                    Text(poseText(ENROLL_POSES.getOrNull(step)), fontSize = 36.sp, color = Color.White, textAlign = TextAlign.Center)
                    Text(
                        hintText(frame.hint),
                        fontSize = 22.sp,
                        color = if (frame.hint == EnrollHint.Hold) GuideGreen else HintYellow,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }

    if (started && identity.available) {
        LaunchedEffect(source) {
            val captured = mutableListOf<FloatArray>()
            source.signals.collect { s: FrameSignals ->
                val f = tracker.update(s)
                frame = f
                if (!f.captureNow) return@collect
                val face = source.latestFace.value ?: return@collect
                // Suspends collection; stale frames are dropped meanwhile. A failed embed just retries on a later frame.
                captured += try { identity.embed(face) } catch (e: CancellationException) { throw e } catch (e: Exception) { return@collect }
                tracker.captured()
                step = tracker.step
                if (captured.size == ENROLL_POSES.size) {
                    try {
                        FaceVault.save(averageNormalized(captured))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        saveFailed = true; started = false; step = 0
                        return@collect
                    }
                    onDone()
                }
            }
        }
    }
}

/** Dark scrim with an oval cut-out; the oval turns green and a ring fills while the pose is held. */
@Composable
private fun FaceGuide(fits: Boolean, progress: Float, modifier: Modifier) {
    Canvas(modifier) {
        // Sized to a face at arm's length (ML Kit box ≈ 25–40% of the image height).
        val ovalH = minOf(size.height * 0.45f, size.width * 0.6f / 0.75f)
        val ovalW = ovalH * 0.75f
        val oval = Rect(Offset((size.width - ovalW) / 2f, (size.height - ovalH) / 2f), Size(ovalW, ovalH))
        val scrim = Path.combine(
            PathOperation.Difference,
            Path().apply { addRect(Rect(Offset.Zero, size)) },
            Path().apply { addOval(oval) },
        )
        drawPath(scrim, Color.Black.copy(alpha = 0.6f))
        val stroke = 8.dp.toPx()
        drawOval(if (fits) GuideGreen else Color.White, oval.topLeft, oval.size, style = Stroke(stroke))
        if (progress > 0f) {
            val ring = oval.inflate(stroke * 1.5f)
            drawArc(GuideGreen, -90f, 360f * progress, false, ring.topLeft, ring.size, style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}

@Composable
internal fun BigButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(0.6f).height(72.dp),
    ) { Text(text, fontSize = 26.sp) }
}
