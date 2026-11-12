package com.wjthinkbig.meetstudy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wjthinkbig.meetstudy.platform.FaceVault
import com.wjthinkbig.meetstudy.ui.screens.EnrollScreen
import com.wjthinkbig.meetstudy.ui.screens.StudyFlow

@Composable
fun App() {
    MaterialTheme {
        var needEnroll by remember { mutableStateOf(FaceVault.load() == null) }
        var studying by remember { mutableStateOf(false) }
        // Each branch owns its frame source, so only one camera consumer exists at a time.
        when {
            needEnroll -> EnrollScreen(onDone = { needEnroll = false })
            studying -> StudyFlow(onExit = { studying = false })
            else -> Home(onStart = { studying = true }, onReEnroll = { FaceVault.clear(); needEnroll = true })
        }
    }
}

@Composable
private fun Home(onStart: () -> Unit, onReEnroll: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
    ) {
        Button(
            onClick = onStart,
            shape = RoundedCornerShape(40.dp),
            modifier = Modifier.fillMaxWidth(0.8f).height(120.dp),
        ) { Text("공부 시작하기", fontSize = 36.sp) }
        TextButton(onClick = onReEnroll) { Text("얼굴 다시 등록", fontSize = 16.sp) }
    }
}
