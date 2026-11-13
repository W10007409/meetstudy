package com.wjthinkbig.meetstudy.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wjthinkbig.meetstudy.platform.CameraPreview
import com.wjthinkbig.meetstudy.resources.Res
import com.wjthinkbig.meetstudy.resources.icon_check_arm
import com.wjthinkbig.meetstudy.resources.icon_check_face
import com.wjthinkbig.meetstudy.resources.icon_check_light
import org.jetbrains.compose.resources.painterResource
import com.wjthinkbig.meetstudy.platform.FrameSource
import com.wjthinkbig.meetstudy.ui.guide.PlacementGuideAnimation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

private val CHECKS = listOf(
    "얼굴과 책이 같이 보이게" to Res.drawable.icon_check_face,
    "팔 길이만큼 떨어뜨려서" to Res.drawable.icon_check_arm,
    "밝은 곳에서, 충전기 연결" to Res.drawable.icon_check_light,
)

/** Latest-frame face visibility; no frame for more than 1s counts as not visible. */
@Composable
internal fun rememberFaceVisible(source: FrameSource): Boolean {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(source) {
        var lastFrame = TimeSource.Monotonic.markNow()
        coroutineScope {
            launch {
                while (true) {
                    delay(250)
                    if (lastFrame.elapsedNow().inWholeMilliseconds > 1_000) visible = false
                }
            }
            source.signals.collect { lastFrame = TimeSource.Monotonic.markNow(); visible = it.faceVisible }
        }
    }
    return visible
}

@Composable
fun PlacementGuideScreen(source: FrameSource, onStart: () -> Unit) {
    val faceVisible = rememberFaceVisible(source)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("태블릿을 이렇게 놓아요", fontSize = 28.sp)
        PlacementGuideAnimation(Modifier.widthIn(max = 480.dp).fillMaxWidth())
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CHECKS.forEach { (text, icon) ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(icon), null, Modifier.size(44.dp))
                    Text(text, fontSize = 22.sp)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CameraPreview(Modifier.size(96.dp).clip(RoundedCornerShape(20.dp)))
            Text(if (faceVisible) "얼굴 인식됨 ✓" else "얼굴을 비춰 주세요", fontSize = 22.sp)
        }
        BigButton("공부 시작", enabled = faceVisible, onClick = onStart)
    }
}
