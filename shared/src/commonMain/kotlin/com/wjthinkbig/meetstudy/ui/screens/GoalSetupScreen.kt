package com.wjthinkbig.meetstudy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PRESETS = listOf(30, 60, 90, 120)

/** [onDone] receives the goal in milliseconds. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoalSetupScreen(onDone: (goalMs: Long) -> Unit) {
    var preset by remember { mutableStateOf<Int?>(60) }
    var custom by remember { mutableStateOf("") }
    val minutes = preset ?: custom.toIntOrNull()?.takeIf { it in 1..600 }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
    ) {
        Text("오늘은 얼마나 공부할까요?", fontSize = 32.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PRESETS.forEach { m ->
                FilterChip(selected = preset == m, onClick = { preset = m }, label = { Text("${m}분", fontSize = 22.sp) })
            }
            FilterChip(selected = preset == null, onClick = { preset = null }, label = { Text("직접 입력", fontSize = 22.sp) })
        }
        if (preset == null) {
            OutlinedTextField(
                value = custom,
                onValueChange = { custom = it.filter(Char::isDigit).take(3) },
                label = { Text("분 (1~600)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        BigButton("다음", enabled = minutes != null) { onDone(minutes!! * 60_000L) }
    }
}
