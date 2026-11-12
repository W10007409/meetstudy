package com.wjthinkbig.meetstudy.ui.guide

import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

private val Mint = Color(0xFF7ED6B4)
private val Sky = Color(0xFF8EC5FF)
private val Butter = Color(0xFFFFD966)
private val Coral = Color(0xFFFF9B85)
private val Navy = Color(0xFF2E3A59)

private const val CYCLE_MS = 7_000

/** 0..1 progress of [t] between [a] and [b]. */
private fun seg(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)

/**
 * Scenes (one loop): 1 kid walks in, 2 tablet drops on the desk corner and tilts 45 degrees,
 * 3 camera view fan opens with sparkles, 4 thumbs-up pops, then a short pause.
 */
@Composable
fun PlacementGuideAnimation(modifier: Modifier = Modifier) {
    val t by rememberInfiniteTransition(label = "guide").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(CYCLE_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "t",
    )
    val walk = seg(t, 0f, 0.12f)
    val drop = EaseOutBack.transform(seg(t, 0.14f, 0.28f))
    val tilt = seg(t, 0.30f, 0.38f)
    val fov = seg(t, 0.40f, 0.50f)
    val thumbs = EaseOutBack.transform(seg(t, 0.58f, 0.68f))
    Box(modifier.aspectRatio(1.6f).clip(RoundedCornerShape(28.dp))) {
        Canvas(Modifier.fillMaxSize()) { drawRect(Sky.copy(alpha = 0.35f)) }
        DeskLayer(Modifier.fillMaxSize())
        KidLayer(Modifier.fillMaxSize(), walk)
        FovLayer(Modifier.fillMaxSize(), fov, sparkleTime = t)
        TabletLayer(Modifier.fillMaxSize(), drop, tilt)
        ThumbsUpLayer(Modifier.fillMaxSize(), thumbs)
    }
}

// ponytail: each layer is Canvas shapes; swap its body for Image(painterResource(guide_*.png)) when art lands.

private val DrawScope.deskTop get() = size.height * 0.72f
private val DrawScope.tabletBase get() = Offset(size.width * 0.70f, deskTop)
private val DrawScope.kidHeadY get() = size.height * 0.40f

@Composable
fun DeskLayer(modifier: Modifier) = Canvas(modifier) {
    drawRect(Butter, Offset(0f, deskTop), Size(size.width, size.height - deskTop))
    drawLine(Navy, Offset(0f, deskTop), Offset(size.width, deskTop), strokeWidth = 6f)
    val book = Offset(size.width * 0.12f, deskTop - size.height * 0.05f)
    val bookSize = Size(size.width * 0.16f, size.height * 0.05f)
    drawRoundRect(Coral, book, bookSize, CornerRadius(8f))
    drawRoundRect(Navy, book, bookSize, CornerRadius(8f), Stroke(4f))
}

/** [walk] 0..1: kid slides in from the left and stops behind the desk. */
@Composable
fun KidLayer(modifier: Modifier, walk: Float) = Canvas(modifier) {
    val r = size.height * 0.11f
    val x = size.width * (-0.15f + 0.37f * walk)
    val bob = if (walk < 1f) sin(walk * PI.toFloat() * 6f) * 6f else 0f
    val head = Offset(x, kidHeadY + bob)
    val bodyTop = Offset(x - r * 1.2f, head.y + r)
    val bodySize = Size(r * 2.4f, deskTop - head.y - r)
    drawRoundRect(Mint, bodyTop, bodySize, CornerRadius(r))
    drawRoundRect(Navy, bodyTop, bodySize, CornerRadius(r), Stroke(5f))
    drawCircle(Coral.copy(alpha = 0.6f), r, head)
    drawCircle(Navy, r, head, style = Stroke(5f))
    drawCircle(Navy, r * 0.1f, head + Offset(-r * 0.35f, -r * 0.1f))
    drawCircle(Navy, r * 0.1f, head + Offset(r * 0.35f, -r * 0.1f))
}

/** [drop] 0..~1.1 scale/bounce onto the desk corner, [tilt] 0..1 rotates to 45 degrees. */
@Composable
fun TabletLayer(modifier: Modifier, drop: Float, tilt: Float) = Canvas(modifier) {
    if (drop <= 0f) return@Canvas
    val w = size.width * 0.12f
    val base = tabletBase.let { it.copy(y = it.y - w * 0.36f * tilt) } // keeps the tilted corner on the desk
    val h = size.height * 0.30f
    val fall = (1f - drop.coerceAtMost(1f)) * -size.height * 0.25f
    scale(0.6f + 0.4f * drop, base) {
        rotate(-45f * tilt, base) {
            val tl = Offset(base.x - w / 2, base.y - h + fall)
            drawRoundRect(Navy, tl, Size(w, h), CornerRadius(14f))
            drawRoundRect(Mint, tl + Offset(8f, 8f), Size(w - 16f, h - 16f), CornerRadius(8f))
            drawCircle(Navy, 5f, Offset(base.x, tl.y + 4f))
        }
    }
}

/** [open] 0..1 fans out from the tablet camera toward the kid, with twinkles. */
@Composable
fun FovLayer(modifier: Modifier, open: Float, sparkleTime: Float = 0f) = Canvas(modifier) {
    if (open <= 0f) return@Canvas
    val apex = Offset(tabletBase.x - size.width * 0.04f, deskTop - size.height * 0.30f)
    val kidX = size.width * 0.22f
    val spread = size.height * 0.30f
    val fan = Path().apply {
        moveTo(apex.x, apex.y)
        lineTo(kidX, kidHeadY - spread)
        lineTo(kidX, kidHeadY + spread)
        close()
    }
    scale(open, 1f, apex) {
        drawPath(fan, Butter.copy(alpha = 0.45f))
        drawPath(fan, Navy.copy(alpha = 0.5f), style = Stroke(4f))
    }
    if (open >= 1f) {
        listOf(Offset(0.4f, 0.30f), Offset(0.32f, 0.5f), Offset(0.5f, 0.42f)).forEachIndexed { i, p ->
            val a = (sin((sparkleTime * 40f + i) * 1.7f) + 1f) / 2f
            sparkle(Offset(size.width * p.x, size.height * p.y), size.height * 0.035f * (0.5f + a), Color.White.copy(alpha = 0.4f + 0.6f * a))
        }
    }
}

private fun DrawScope.sparkle(c: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y - r); lineTo(c.x + r * 0.3f, c.y - r * 0.3f); lineTo(c.x + r, c.y)
        lineTo(c.x + r * 0.3f, c.y + r * 0.3f); lineTo(c.x, c.y + r); lineTo(c.x - r * 0.3f, c.y + r * 0.3f)
        lineTo(c.x - r, c.y); lineTo(c.x - r * 0.3f, c.y - r * 0.3f); close()
    }
    drawPath(p, color)
}

/** [pop] 0..~1.1 scale bounce, badge in the top-left. */
@Composable
fun ThumbsUpLayer(modifier: Modifier, pop: Float) = Canvas(modifier) {
    if (pop <= 0f) return@Canvas
    val c = Offset(size.width * 0.85f, size.height * 0.2f)
    val r = size.height * 0.12f
    scale(pop, c) {
        drawCircle(Butter, r, c)
        drawCircle(Navy, r, c, style = Stroke(5f))
        val fist = c + Offset(-r * 0.5f, -r * 0.05f)
        val fistSize = Size(r * 0.9f, r * 0.6f)
        drawRoundRect(Coral, fist, fistSize, CornerRadius(10f))
        drawRoundRect(Coral, c + Offset(-r * 0.1f, -r * 0.6f), Size(r * 0.3f, r * 0.6f), CornerRadius(10f))
        drawRoundRect(Navy, fist, fistSize, CornerRadius(10f), Stroke(4f))
    }
}
