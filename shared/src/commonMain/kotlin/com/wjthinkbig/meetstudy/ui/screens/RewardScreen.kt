package com.wjthinkbig.meetstudy.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wjthinkbig.meetstudy.data.SessionRecord
import com.wjthinkbig.meetstudy.domain.FocusState
import com.wjthinkbig.meetstudy.domain.StarRules
import kotlinx.coroutines.delay

@Composable
fun RewardScreen(record: SessionRecord, starsToday: Int, historyLoaded: Boolean, onProof: () -> Unit, onReport: () -> Unit, onHome: () -> Unit) {
    val focused = record.segments.filter { it.state == FocusState.STUDYING }.sumOf { it.endMs - it.startMs }
    var sharing by remember { mutableStateOf(false) }
    val canProof = StarRules.proofStarAllowed(starsToday) && !record.proofStar
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        if (record.goalStar) {
            Text("오늘 목표 달성!", fontSize = 40.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(1 + if (record.proofStar) 1 else 0) { PopStar(it) }
            }
        } else {
            Text("오늘은 여기까지!", fontSize = 40.sp)
            if (record.proofStar) {
                PopStar(0)
                Text("인증 완료! 별 1개를 받았어요", fontSize = 22.sp, textAlign = TextAlign.Center)
            } else Text("인증하면 별을 받을 수 있어요", fontSize = 22.sp, textAlign = TextAlign.Center)
        }
        Text("순공부 ${formatDuration(focused)}", fontSize = 24.sp)
        BigButton(if (canProof) "공부 인증하고 별 더 받기" else "오늘 별은 다 모았어요", enabled = canProof && historyLoaded, onClick = onProof)
        SoftButton("공유하기") { sharing = true }
        SoftButton("리포트 보기", enabled = historyLoaded, onClick = onReport)
        TextButton(onClick = onHome) { Text("홈으로", fontSize = 16.sp) }
    }
    if (sharing) ShareSheet(record, starsToday) { sharing = false }
}

@Composable
private fun PopStar(index: Int) {
    val scale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 250L)
        scale.animateTo(1f, spring(Spring.DampingRatioHighBouncy, Spring.StiffnessLow))
    }
    Text("★", fontSize = 64.sp, color = Color(0xFFFFC107), modifier = Modifier.scale(scale.value))
}
