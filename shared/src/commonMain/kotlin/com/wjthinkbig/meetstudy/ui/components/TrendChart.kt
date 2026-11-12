package com.wjthinkbig.meetstudy.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wjthinkbig.meetstudy.domain.DayTotal

private val Bar = Color(0xFFB7E4CF)
private val Today = Color(0xFF4CC38A)

/** Bars = focused minutes per day (last one is today). Numbers are also printed as text under the chart. */
@Composable
fun TrendChart(days: List<DayTotal>, modifier: Modifier = Modifier) {
    val maxMin = (days.maxOfOrNull { it.focusedMs } ?: 0L).coerceAtLeast(60_000L)
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val slot = size.width / days.size
            val w = slot * 0.7f
            days.forEachIndexed { i, d ->
                val h = size.height * d.focusedMs / maxMin
                drawRoundRect(
                    if (i == days.lastIndex) Today else Bar,
                    Offset(i * slot + (slot - w) / 2, size.height - h), Size(w, h), CornerRadius(6f, 6f),
                )
            }
        }
        // ponytail: 7 labelled columns fit; for 30 days we label only the totals line and today's column to stay readable (>=14sp).
        if (days.size <= 7) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                days.forEach { d ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${d.focusedMs / 60_000}분", fontSize = 14.sp)
                        Text("★${d.stars}", fontSize = 14.sp)
                        Text("${d.date.day}일", fontSize = 14.sp)
                    }
                }
            }
        } else {
            val t = days.last()
            Text("오늘 ${t.focusedMs / 60_000}분 · ★${t.stars}", fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            Text("하루 최고 ${days.maxOf { it.focusedMs } / 60_000}분", fontSize = 14.sp)
        }
    }
}
