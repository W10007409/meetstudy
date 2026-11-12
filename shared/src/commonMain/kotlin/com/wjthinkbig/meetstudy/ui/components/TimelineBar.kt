package com.wjthinkbig.meetstudy.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.wjthinkbig.meetstudy.domain.Segment
import com.wjthinkbig.meetstudy.ui.theme.stateColor

fun timelineFractions(segments: List<Segment>): List<Float> {
    val total = segments.sumOf { it.endMs - it.startMs }.toFloat()
    return if (total <= 0f) emptyList() else segments.map { (it.endMs - it.startMs) / total }
}

@Composable
fun TimelineBar(segments: List<Segment>, modifier: Modifier = Modifier) {
    val fractions = timelineFractions(segments)
    Canvas(modifier.fillMaxWidth().height(36.dp)) {
        var x = 0f
        segments.zip(fractions).forEach { (s, f) ->
            drawRoundRect(stateColor(s.state), Offset(x, 0f), Size(f * size.width, size.height), CornerRadius(0f))
            x += f * size.width
        }
    }
}
