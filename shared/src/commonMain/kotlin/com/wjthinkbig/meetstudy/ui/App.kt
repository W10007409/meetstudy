package com.wjthinkbig.meetstudy.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wjthinkbig.meetstudy.domain.FrameSignals
import com.wjthinkbig.meetstudy.platform.CameraPreview
import com.wjthinkbig.meetstudy.platform.FaceVault
import com.wjthinkbig.meetstudy.platform.rememberFrameSource
import com.wjthinkbig.meetstudy.ui.screens.EnrollScreen

@Composable
fun App() {
    MaterialTheme {
        var needEnroll by remember { mutableStateOf(FaceVault.load() == null) }
        // Each branch owns its frame source, so the debug screen's source starts after enrollment and sees the new embedding.
        if (needEnroll) {
            EnrollScreen(onDone = { needEnroll = false })
        } else {
            DebugScreen(onReEnroll = { FaceVault.clear(); needEnroll = true })
        }
    }
}

// Temporary debug screen (Task 5); later tasks replace it.
@Composable
private fun DebugScreen(onReEnroll: () -> Unit) {
    val source = rememberFrameSource(enablePhone = false)
    var last by remember { mutableStateOf<FrameSignals?>(null) }
    var identity by remember { mutableStateOf<Boolean?>(null) }
    var frames by remember { mutableIntStateOf(0) }
    LaunchedEffect(source) {
        source.signals.collect {
            last = it; frames++
            it.identityMatch?.let { m -> identity = m }
        }
    }
    Column(Modifier.fillMaxSize()) {
        CameraPreview(Modifier.fillMaxWidth().height(360.dp))
        val s = last
        Text(
            "frames=$frames face=${s?.faceVisible} yaw=${s?.yaw} pitch=${s?.pitch}\n" +
                "eyes=${s?.eyesOpenProb} person=${s?.personVisible} noseBelow=${s?.noseBelowShoulders}\n" +
                "identity=$identity",
        )
        Button(onClick = onReEnroll) { Text("다시 등록") }
    }
}
