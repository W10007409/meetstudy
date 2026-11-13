package com.wjthinkbig.meetstudy.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

private class Preset(val minutes: Int, val tag: String)
private val PRESETS = listOf(Preset(30, "가볍게"), Preset(60, "기본"), Preset(90, "집중"), Preset(120, "도전"))

internal const val GOAL_STEP_MIN = 5
internal const val GOAL_MIN = 5
internal const val GOAL_MAX = 300
private const val MIN_PER_TURN = 60f // one full turn of the dial = 1 hour, like a kitchen timer

internal fun stepGoal(minutes: Int, delta: Int): Int = (minutes + delta).coerceIn(GOAL_MIN, GOAL_MAX)

/** Dial drag: [deltaDeg] of rotation (clockwise +) moves the raw minute value; the shown value snaps to 5 min. */
internal fun dialMinutes(raw: Float, deltaDeg: Float): Float =
    (raw + normalizeDeg(deltaDeg) / 360f * MIN_PER_TURN).coerceIn(GOAL_MIN.toFloat(), GOAL_MAX.toFloat())

internal fun snapGoal(raw: Float): Int = ((raw / GOAL_STEP_MIN).roundToInt() * GOAL_STEP_MIN).coerceIn(GOAL_MIN, GOAL_MAX)

/** Shortest signed angle, so crossing 12 o'clock does not jump a whole turn. */
internal fun normalizeDeg(d: Float): Float {
    var x = d % 360f
    if (x > 180f) x -= 360f
    if (x < -180f) x += 360f
    return x
}

internal fun goalCaption(minutes: Int): String = when {
    minutes < 60 -> "${minutes}분"
    minutes % 60 == 0 -> "${minutes / 60}시간"
    else -> "${minutes / 60}시간 ${minutes % 60}분"
}

internal fun clockLabel(hour: Int, minute: Int): String {
    val h12 = if (hour % 12 == 0) 12 else hour % 12
    return "${if (hour < 12) "오전" else "오후"} $h12:${minute.toString().padStart(2, '0')}"
}

@OptIn(ExperimentalTime::class)
private fun endsAt(goal: Int): String {
    val t = (Clock.System.now() + goal.minutes).toLocalDateTime(TimeZone.currentSystemDefault())
    return clockLabel(t.hour, t.minute)
}

/** [onDone] receives the goal in milliseconds. Tap/drag only, no keyboard. */
@Composable
fun GoalSetupScreen(onDone: (goalMs: Long) -> Unit) {
    var free by rememberSaveable { mutableStateOf(false) }
    var preset by rememberSaveable { mutableIntStateOf(60) }
    var custom by rememberSaveable { mutableIntStateOf(45) }
    val minutes = if (free) custom else preset

    BoxWithConstraints(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        val compact = minOf(maxWidth, maxHeight) < 560.dp
        val wide = maxWidth > maxHeight
        // Whatever height is left after title, tabs, end time and button goes to the dial / cards.
        val body = (maxHeight - if (compact) 300.dp else 360.dp).coerceIn(180.dp, 420.dp)
        Column(
            Modifier.widthIn(max = if (wide) 640.dp else 560.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 14.dp else 20.dp),
        ) {
            Text("오늘은 얼마나 공부할까요?", fontSize = if (compact) 24.sp else 32.sp, fontWeight = FontWeight.Bold)
            ModeTabs(free, compact) { free = it }
            Box(Modifier.height(body).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (free) FreeDial(custom, body, compact) { custom = it }
                else PresetGrid(preset, compact) { preset = it }
            }
            Text(
                "${goalCaption(minutes)} 공부하면 ${endsAt(minutes)}에 끝나요",
                fontSize = if (compact) 16.sp else 19.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BigButton("${goalCaption(minutes)}으로 시작") { onDone(minutes * 60_000L) }
        }
    }
}

@Composable
private fun ModeTabs(free: Boolean, compact: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.widthIn(max = 420.dp).fillMaxWidth().clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp),
    ) {
        listOf(false to "추천 시간", true to "직접 맞추기").forEach { (value, label) ->
            val on = value == free
            Box(
                Modifier.weight(1f).height(if (compact) 44.dp else 52.dp).clip(RoundedCornerShape(50))
                    .background(if (on) Color.White else Color.Transparent).clickable { onChange(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, fontSize = if (compact) 16.sp else 18.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PresetGrid(selected: Int, compact: Boolean, onPick: (Int) -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PRESETS.chunked(2).forEach { row ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { p ->
                    val on = p.minutes == selected
                    Column(
                        Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(24.dp))
                            .background(if (on) MaterialTheme.colorScheme.primary else Color.White)
                            .border(2.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
                            .clickable { onPick(p.minutes) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(goalCaption(p.minutes), fontSize = if (compact) 24.sp else 32.sp, fontWeight = FontWeight.Bold, color = if (on) Color.White else MaterialTheme.colorScheme.onSurface)
                        Text(p.tag, fontSize = if (compact) 14.sp else 17.sp, color = if (on) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** Kitchen-timer dial: drag around the ring (one turn = 1 hour, keeps going past 1 hour), fine-tune with -/+5. */
@Composable
private fun FreeDial(minutes: Int, height: Dp, compact: Boolean, onChange: (Int) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val lap = MaterialTheme.colorScheme.primaryContainer
    var raw by remember { mutableFloatStateOf(minutes.toFloat()) }
    var lastDeg by remember { mutableFloatStateOf(0f) }
    val dial = (height - 8.dp).coerceAtMost(360.dp)
    fun angleOf(p: Offset, c: Offset) = (atan2(p.x - c.x, -(p.y - c.y)) * 180f / PI.toFloat())

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 20.dp)) {
        RoundStep("−", minutes > GOAL_MIN, compact) { stepGoal(minutes, -GOAL_STEP_MIN).also { raw = it.toFloat(); onChange(it) } }
        Box(Modifier.size(dial), contentAlignment = Alignment.Center) {
            Canvas(
                Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { lastDeg = angleOf(it, Offset(size.width / 2f, size.height / 2f)) },
                        ) { change, _ ->
                            val d = angleOf(change.position, Offset(size.width / 2f, size.height / 2f))
                            raw = dialMinutes(raw, d - lastDeg)
                            lastDeg = d
                            onChange(snapGoal(raw))
                        }
                    }
                    .pointerInput(Unit) {
                        // Tap on the ring jumps within the current hour, e.g. tap at 3 o'clock -> h:15.
                        detectTapGestures { p ->
                            var deg = angleOf(p, Offset(size.width / 2f, size.height / 2f))
                            if (deg < 0) deg += 360f
                            val hours = (raw.toInt() / 60)
                            raw = (hours * 60 + deg / 360f * MIN_PER_TURN).coerceIn(GOAL_MIN.toFloat(), GOAL_MAX.toFloat())
                            onChange(snapGoal(raw))
                        }
                    },
            ) {
                val stroke = size.minDimension * 0.09f
                val r = size.minDimension / 2f - stroke
                val c = center
                val tl = Offset(c.x - r, c.y - r)
                val sz = androidx.compose.ui.geometry.Size(r * 2, r * 2)
                drawCircle(if (minutes >= 60) lap else track, r, c, style = Stroke(stroke))
                // Tick marks every 5 minutes, longer each quarter hour.
                for (i in 0 until 12) {
                    val a = i * 30f * PI.toFloat() / 180f
                    val inner = r - stroke * (if (i % 3 == 0) 1.6f else 1.1f)
                    val outer = r - stroke * 0.7f
                    drawLine(Color(0xFFB9B2A6), Offset(c.x + inner * sin(a), c.y - inner * cos(a)), Offset(c.x + outer * sin(a), c.y - outer * cos(a)), strokeWidth = 4f, cap = StrokeCap.Round)
                }
                val part = (minutes % 60) / 60f * 360f
                val sweep = if (minutes % 60 == 0) 360f else part
                drawArc(primary, -90f, sweep, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
                val ka = ((-90f + sweep) * PI / 180f).toFloat()
                val knob = Offset(c.x + r * cos(ka), c.y + r * sin(ka))
                drawCircle(Color.White, stroke * 0.85f, knob)
                drawCircle(primary, stroke * 0.85f, knob, style = Stroke(6f))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(goalCaption(minutes), fontSize = if (compact) 30.sp else 40.sp, fontWeight = FontWeight.Bold, color = primary)
                Text("돌려서 맞춰요", fontSize = if (compact) 13.sp else 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        RoundStep("+", minutes < GOAL_MAX, compact) { stepGoal(minutes, GOAL_STEP_MIN).also { raw = it.toFloat(); onChange(it) } }
    }
}

@Composable
private fun RoundStep(label: String, enabled: Boolean, compact: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(if (compact) 48.dp else 64.dp).clip(CircleShape)
            .background(if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = if (compact) 26.sp else 34.sp, fontWeight = FontWeight.Bold, color = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else Color(0xFFB0B6BE))
    }
}
