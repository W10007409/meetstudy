package com.wjthinkbig.meetstudy.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.wjthinkbig.meetstudy.platform.rememberFrameSource

// Temporary debug screen (Task 5); later tasks replace it.
@Composable
fun App() {
    MaterialTheme {
        val source = rememberFrameSource(enablePhone = false)
        var last by remember { mutableStateOf<FrameSignals?>(null) }
        var frames by remember { mutableIntStateOf(0) }
        LaunchedEffect(source) { source.signals.collect { last = it; frames++ } }
        Column(Modifier.fillMaxSize()) {
            CameraPreview(Modifier.fillMaxWidth().height(360.dp))
            val s = last
            Text(
                "frames=$frames face=${s?.faceVisible} yaw=${s?.yaw} pitch=${s?.pitch}\n" +
                    "eyes=${s?.eyesOpenProb} person=${s?.personVisible} noseBelow=${s?.noseBelowShoulders}",
            )
        }
    }
}
