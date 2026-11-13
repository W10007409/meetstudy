package com.wjthinkbig.meetstudy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PRESETS = listOf(30, 60, 90, 120)
internal const val GOAL_STEP_MIN = 10
internal const val GOAL_MIN = 10
internal const val GOAL_MAX = 300

internal fun stepGoal(minutes: Int, delta: Int): Int = (minutes + delta).coerceIn(GOAL_MIN, GOAL_MAX)

internal fun goalCaption(minutes: Int): String = when {
    minutes < 60 -> "${minutes}분"
    minutes % 60 == 0 -> "${minutes / 60}시간"
    else -> "${minutes / 60}시간 ${minutes % 60}분"
}

/** [onDone] receives the goal in milliseconds. Tap only: presets plus -/+ 10 minutes, no keyboard. */
@Composable
fun GoalSetupScreen(onDone: (goalMs: Long) -> Unit) {
    var minutes by rememberSaveable { mutableIntStateOf(60) }
    BoxWithConstraints(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        val compact = minOf(maxWidth, maxHeight) < 560.dp
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 20.dp else 28.dp),
        ) {
            Text("오늘은 얼마나 공부할까요?", fontSize = if (compact) 24.sp else 32.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 28.dp)) {
                RoundStep("−", enabled = minutes > GOAL_MIN, compact) { minutes = stepGoal(minutes, -GOAL_STEP_MIN) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("$minutes", fontSize = if (compact) 64.sp else 88.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("분", fontSize = if (compact) 24.sp else 30.sp, modifier = Modifier.padding(start = 4.dp, bottom = if (compact) 12.dp else 18.dp))
                    }
                    Text(goalCaption(minutes), fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                RoundStep("+", enabled = minutes < GOAL_MAX, compact) { minutes = stepGoal(minutes, GOAL_STEP_MIN) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PRESETS.forEach { m ->
                    val on = m == minutes
                    Box(
                        Modifier.weight(1f).height(if (compact) 52.dp else 60.dp).clip(RoundedCornerShape(50))
                            .background(if (on) MaterialTheme.colorScheme.primary else Color.White)
                            .clickable { minutes = m },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(goalCaption(m), fontSize = if (compact) 16.sp else 19.sp, fontWeight = FontWeight.SemiBold, color = if (on) Color.White else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            BigButton("다음") { onDone(minutes * 60_000L) }
        }
    }
}

@Composable
private fun RoundStep(label: String, enabled: Boolean, compact: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(if (compact) 56.dp else 72.dp).clip(CircleShape)
            .background(if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = if (compact) 30.sp else 38.sp, fontWeight = FontWeight.Bold, color = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else Color(0xFFB0B6BE))
    }
}
