package com.wjthinkbig.meetstudy.ui.guide

import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wjthinkbig.meetstudy.resources.Res
import com.wjthinkbig.meetstudy.resources.guide_a_base
import com.wjthinkbig.meetstudy.resources.guide_a_tablet
import com.wjthinkbig.meetstudy.resources.guide_b_full
import com.wjthinkbig.meetstudy.resources.guide_b_place
import com.wjthinkbig.meetstudy.resources.guide_b_reach
import com.wjthinkbig.meetstudy.resources.guide_c_backlight
import com.wjthinkbig.meetstudy.resources.guide_c_close
import com.wjthinkbig.meetstudy.resources.guide_c_flat
import com.wjthinkbig.meetstudy.resources.guide_c_side
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val Teal = Color(0xFF2FA39A)
private val Ok = Color(0xFF3DBE7A)
private val Bad = Color(0xFFE5484D)
private val Ink = Color(0xFF2B2F36)
private val Paper = Color(0xFFF4F1EA)

private enum class Scene { Angle, Place, Reach, Success, Bad }

private class Step(val scene: Scene, val startMs: Int, val endMs: Int, val caption: String, val art: DrawableResource? = null)

private val STEPS = listOf(
    Step(Scene.Angle, 0, 3_800, "책상 앞 모서리에 두고\n정면에서 45도까지 돌려요"),
    Step(Scene.Place, 3_800, 5_800, "태블릿을 거치대에 세워요", Res.drawable.guide_b_place),
    Step(Scene.Reach, 5_800, 7_800, "팔 길이만큼 떨어뜨려요", Res.drawable.guide_b_reach),
    Step(Scene.Success, 7_800, 10_600, "얼굴과 책이 함께 보이면 성공!", Res.drawable.guide_b_full),
    Step(Scene.Bad, 10_600, 11_600, "너무 가까우면 안 돼요", Res.drawable.guide_c_close),
    Step(Scene.Bad, 11_600, 12_600, "눕혀 두면 얼굴이 안 보여요", Res.drawable.guide_c_flat),
    Step(Scene.Bad, 12_600, 13_600, "너무 옆에 두면 안 돼요", Res.drawable.guide_c_side),
    Step(Scene.Bad, 13_600, 14_600, "창문을 등지면 얼굴이 어두워요", Res.drawable.guide_c_backlight),
)
private val CYCLE_MS = STEPS.last().endMs

/** 0..1 progress of [t] between [a] and [b] ms. */
private fun seg(t: Float, a: Int, b: Int) = ((t - a) / (b - a)).coerceIn(0f, 1f)

/**
 * Looping placement guide built from the generated art (docs/image-prompts.md): top-down angle diagram,
 * placing the tablet, arm-length distance, camera view on face + book, then four don'ts.
 * The view fan, angle band, check/cross marks and captions are drawn here, not in the art.
 */
@Composable
fun PlacementGuideAnimation(modifier: Modifier = Modifier, compact: Boolean = false) {
    val t by rememberInfiniteTransition(label = "guide").animateFloat(
        initialValue = 0f,
        targetValue = CYCLE_MS.toFloat(),
        animationSpec = infiniteRepeatable(tween(CYCLE_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "t",
    )
    val step = STEPS.first { t < it.endMs || it === STEPS.last() }
    val local = t - step.startMs
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.weight(1f, fill = false).aspectRatio(4f / 3f, matchHeightConstraintsFirst = true).clip(RoundedCornerShape(28.dp)).background(Paper),
            contentAlignment = Alignment.Center,
        ) {
            val fadeIn = seg(local, 0, 250)
            when (step.scene) {
                Scene.Angle -> AngleScene(local, Modifier.graphicsLayer { alpha = fadeIn })
                Scene.Place -> Image(
                    painterResource(step.art!!), null, Modifier.fillMaxSize().graphicsLayer {
                        alpha = fadeIn
                        translationY = -size.height * 0.25f * (1f - EaseOutBack.transform(seg(local, 0, 800)))
                    },
                    contentScale = ContentScale.Fit,
                )
                Scene.Reach -> Image(painterResource(step.art!!), null, Modifier.fillMaxSize().graphicsLayer { alpha = fadeIn })
                Scene.Success -> SuccessScene(step.art!!, local, Modifier.graphicsLayer { alpha = fadeIn })
                Scene.Bad -> BadCard(step.art!!, local)
            }
        }
        Text(
            step.caption,
            fontSize = if (compact) 17.sp else 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (step.scene == Scene.Bad) Bad else Ink,
            textAlign = TextAlign.Center,
        )
        StepDots(STEPS.indexOf(step).coerceAtMost(4))
    }
}

/** Top-down: the tablet slides to the desk's front corner, then turns 0 -> 40 degrees inside the allowed 0..45 band. */
@Composable
private fun AngleScene(local: Float, modifier: Modifier) {
    val slide = EaseOutCubic.transform(seg(local, 200, 900))
    val turn = EaseOutCubic.transform(seg(local, 1_100, 2_300))
    val angle = 40f * turn
    BoxWithConstraints(modifier.aspectRatio(1f)) {
        val w = maxWidth
        Image(painterResource(Res.drawable.guide_a_base), null, Modifier.fillMaxSize())
        // Pivot = tablet centre in the source canvas (0.80, 0.21); the sprite is 0.326 x 0.234 of it.
        val cx = 0.80f + 0.4f * (1f - slide); val cy = 0.21f
        Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = seg(local, 1_000, 1_400) }) {
            val c = Offset(size.width * cx, size.height * cy)
            val r = size.width * 0.42f
            // Screen faces +y (toward the student) at 0 degrees; a clockwise turn swings it toward the student's side.
            drawArc(Ok.copy(alpha = 0.22f), 90f, 45f, true, c - Offset(r, r), Size(r * 2, r * 2))
            drawArc(Ok, 90f, 45f, false, c - Offset(r, r), Size(r * 2, r * 2), style = Stroke(3.dp.toPx()))
            val a = ((90f + angle) * PI / 180).toFloat()
            drawLine(Teal, c, c + Offset(cos(a) * r, sin(a) * r), strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
        }
        Image(
            painterResource(Res.drawable.guide_a_tablet), null,
            Modifier.size(w * 0.326f, w * 0.234f)
                .offset(w * (cx - 0.163f), w * (cy - 0.117f))
                .graphicsLayer { rotationZ = angle },
        )
        Text(
            "${angle.toInt()}°",
            Modifier.offset(w * 0.52f, w * 0.40f).graphicsLayer { alpha = seg(local, 1_100, 1_400) }
                .clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.9f)).padding(horizontal = 10.dp, vertical = 4.dp),
            fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Teal,
        )
    }
}

/** Camera view fan from the tablet, then green boxes on the face and the book, then a check badge. */
@Composable
private fun SuccessScene(art: DrawableResource, local: Float, modifier: Modifier) {
    val fan = EaseOutCubic.transform(seg(local, 300, 1_000))
    val face = seg(local, 1_000, 1_300)
    val book = seg(local, 1_300, 1_600)
    val badge = EaseOutBack.transform(seg(local, 1_700, 2_100))
    Box(modifier.aspectRatio(1448f / 1086f)) {
        Image(painterResource(art), null, Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            // Fractions measured on guide_b_full: camera (0.884, 0.46), face and book boxes.
            val cam = Offset(size.width * 0.884f, size.height * 0.46f)
            val r = size.width * 0.82f
            val sweep = 54f * fan
            drawArc(Color(0x33FFD966), 186f - sweep / 2, sweep, true, cam - Offset(r, r), Size(r * 2, r * 2))
            frac(0.12f, 0.0f, 0.32f, 0.29f, face)
            frac(0.16f, 0.44f, 0.54f, 0.67f, book)
        }
        Box(
            Modifier.align(Alignment.TopEnd).padding(16.dp).size(56.dp)
                .graphicsLayer { scaleX = badge; scaleY = badge }
                .clip(CircleShape).background(Ok),
            contentAlignment = Alignment.Center,
        ) { Text("✓", fontSize = 32.sp, color = Color.White, fontWeight = FontWeight.Bold) }
    }
}

private fun DrawScope.frac(l: Float, t: Float, r: Float, b: Float, alpha: Float) {
    if (alpha <= 0f) return
    drawRoundRect(
        Ok.copy(alpha = alpha), Offset(size.width * l, size.height * t), Size(size.width * (r - l), size.height * (b - t)),
        CornerRadius(12.dp.toPx()), style = Stroke(4.dp.toPx()),
    )
}

@Composable
private fun BadCard(art: DrawableResource, local: Float) {
    val pop = EaseOutBack.transform(seg(local, 150, 450))
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = seg(local, 0, 150) }) {
        Image(painterResource(art), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        Box(
            Modifier.align(Alignment.TopEnd).padding(16.dp).size(56.dp)
                .graphicsLayer { scaleX = pop; scaleY = pop }
                .clip(CircleShape).background(Bad),
            contentAlignment = Alignment.Center,
        ) { Text("✕", fontSize = 30.sp, color = Color.White, fontWeight = FontWeight.Bold) }
    }
}

/** Five dots: four guide steps plus one for all the don'ts. */
@Composable
private fun StepDots(active: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(5) { i ->
            Box(Modifier.size(if (i == active) 22.dp else 8.dp, 8.dp).clip(CircleShape).background(if (i == active) Teal else Color(0xFFCBD2D9)))
        }
    }
}
