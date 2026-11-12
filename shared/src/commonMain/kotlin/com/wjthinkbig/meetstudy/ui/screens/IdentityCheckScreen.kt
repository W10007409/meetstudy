package com.wjthinkbig.meetstudy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wjthinkbig.meetstudy.domain.FrameSignals
import com.wjthinkbig.meetstudy.platform.CameraPreview
import com.wjthinkbig.meetstudy.platform.FrameSource
import kotlinx.coroutines.delay

private const val SKIP_AFTER_MS = 5_000L

/** "본인이 아니에요" shows after a mismatch and clears once the face leaves the frame. */
internal fun nextMismatch(shown: Boolean, s: FrameSignals): Boolean = when {
    s.identityMatch == false -> true
    !s.faceVisible -> false
    else -> shown
}

/** Passes on a match; shows "본인이 아니에요" on a mismatch and keeps waiting; skips if no check ever arrives. */
@Composable
fun IdentityCheckScreen(source: FrameSource, onPassed: () -> Unit) {
    var mismatch by remember { mutableStateOf(false) }
    var skipped by remember { mutableStateOf(false) }

    LaunchedEffect(source) {
        var faceSince: Long? = null
        var everChecked = false
        source.signals.collect { s ->
            if (s.identityMatch != null) everChecked = true
            if (s.identityMatch == true) { onPassed(); return@collect }
            mismatch = nextMismatch(mismatch, s)
            if (!s.faceVisible) { faceSince = null; return@collect }
            val since = faceSince ?: s.timeMs.also { faceSince = it }
            if (!everChecked && s.timeMs - since >= SKIP_AFTER_MS) {
                skipped = true
                delay(1_500)
                onPassed()
            }
        }
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("나 맞는지 확인해요", fontSize = 32.sp)
        CameraPreview(Modifier.fillMaxWidth().height(360.dp).clip(RoundedCornerShape(32.dp)))
        Text(
            when {
                skipped -> "본인 확인을 건너뛰었어요"
                mismatch -> "본인이 아니에요"
                else -> "얼굴을 화면에 비춰 주세요"
            },
            fontSize = 26.sp,
            textAlign = TextAlign.Center,
        )
    }
}
