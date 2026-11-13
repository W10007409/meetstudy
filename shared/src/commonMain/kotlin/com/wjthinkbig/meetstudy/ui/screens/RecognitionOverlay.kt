package com.wjthinkbig.meetstudy.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wjthinkbig.meetstudy.domain.FrameSignals
import com.wjthinkbig.meetstudy.domain.StudyPoseModel
import com.wjthinkbig.meetstudy.domain.isCameraGaze

// ML Kit pose landmark pairs: face, arms and hands, torso.
private val BONES = listOf(
    1 to 2, 2 to 3, 3 to 7, 4 to 5, 5 to 6, 6 to 8, 9 to 10,
    11 to 12, 11 to 13, 13 to 15, 15 to 17, 15 to 19, 15 to 21, 17 to 19,
    12 to 14, 14 to 16, 16 to 18, 16 to 20, 16 to 22, 18 to 20,
    11 to 23, 12 to 24, 23 to 24,
)
private const val MIN_LIKELIHOOD = 0.5f
private val BoneColor = Color(0xFF3EE6D0)
private val FaceColor = Color(0xFFFFD54F)

/** Maps upright image pixels onto the mirrored, FILL_CENTER front-camera preview. */
internal fun previewPoint(x: Float, y: Float, imgW: Int, imgH: Int, viewW: Float, viewH: Float): Offset {
    val s = maxOf(viewW / imgW, viewH / imgH)
    val ox = (viewW - imgW * s) / 2f; val oy = (viewH - imgH * s) / 2f
    return Offset((imgW - x) * s + ox, y * s + oy)
}

private fun f1(v: Float?) = v?.let { ((it * 10).toInt() / 10f).toString() } ?: "-"
private fun yn(v: Boolean?) = when (v) { true -> "O"; false -> "X"; null -> "-" }

/** Test-build view of what the detectors see: face box, skeleton and the numbers the focus decision uses. */
@Composable
internal fun BoxScope.RecognitionOverlay(s: FrameSignals?, poses: StudyPoseModel, stateText: String) {
    val d = s?.debug
    Canvas(Modifier.matchParentSize()) {
        if (d == null) return@Canvas
        fun p(x: Float, y: Float) = previewPoint(x, y, d.imgW, d.imgH, size.width, size.height)
        d.face?.let { f ->
            val a = p(f[0], f[1]); val b = p(f[2], f[3])
            drawRect(FaceColor, Offset(minOf(a.x, b.x), minOf(a.y, b.y)), Size(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.y - a.y)), style = Stroke(4.dp.toPx()))
        }
        d.pose?.let { a ->
            fun ok(i: Int) = a[i * 3 + 2] >= MIN_LIKELIHOOD
            fun pt(i: Int) = p(a[i * 3], a[i * 3 + 1])
            for ((i, j) in BONES) if (ok(i) && ok(j)) drawLine(BoneColor, pt(i), pt(j), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
            for (i in 0 until 33) if (ok(i)) drawCircle(Color.White, 4.dp.toPx(), pt(i))
        }
    }
    val y = s?.yaw; val pi = s?.pitch
    val lines = buildList {
        add("상태  $stateText")
        add("얼굴 ${yn(s?.faceVisible)}  yaw ${f1(y)}  pitch ${f1(pi)}  눈 ${f1(s?.eyesOpenProb)}")
        add("본인 ${yn(s?.identityMatch)}  사람 ${yn(s?.personVisible)}  폰 ${yn(s?.phoneVisible)}  엎드림 ${yn(s?.noseBelowShoulders)}")
        if (y != null && pi != null) add("공부 방향 ${yn(poses.isStudyPose(y, pi))}  카메라 응시 ${yn(isCameraGaze(y, pi))}")
        val cs = poses.clusters
        add("익힌 방향 ${cs.count { it.trusted }}/${cs.size}: " + cs.joinToString(" ") { "(${f1(it.yaw)},${f1(it.pitch)}${if (it.trusted) "✓" else ""})" })
        if (d?.pose == null) add("뼈대: 계산 중… (0.5초마다)")
    }
    Column(
        Modifier.align(Alignment.BottomStart).padding(12.dp).padding(bottom = 44.dp)
            .background(Color(0xB3000000), RoundedCornerShape(12.dp)).padding(10.dp),
    ) {
        lines.forEach { Text(it, color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace) }
    }
}

@Composable
internal fun RecognitionToggle(on: Boolean, modifier: Modifier, onToggle: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(50)).background(if (on) BoneColor else Color(0x99000000))
            .clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            if (on) "인식 보기 끄기" else "인식 보기",
            color = if (on) Color.Black else Color.White, fontSize = 15.sp,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}
