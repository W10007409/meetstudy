package com.wjthinkbig.meetstudy.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
    AdaptivePane(
        Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        main = { m, compact ->
            Column(m, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
                Text("태블릿을 이렇게 놓아요", fontSize = if (compact) 22.sp else 28.sp, fontWeight = FontWeight.Bold)
                PlacementGuideAnimation(Modifier.weight(1f).fillMaxWidth(), compact)
            }
        },
        side = { m, compact, _ ->
            Column(m, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp)) {
                Column(
                    Modifier.widthIn(max = 480.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White)
                        .padding(horizontal = 16.dp, vertical = if (compact) 8.dp else 12.dp),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp),
                ) {
                    CHECKS.forEach { (text, icon) ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Image(painterResource(icon), null, Modifier.size(if (compact) 32.dp else 44.dp))
                            Text(text, fontSize = if (compact) 16.sp else 20.sp)
                        }
                    }
                }
                Row(
                    Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CameraPreview(Modifier.size(if (compact) 64.dp else 88.dp).clip(CircleShape))
                    Text(
                        if (faceVisible) "얼굴이 보여요 ✓" else "카메라에 얼굴을 비춰 주세요",
                        fontSize = if (compact) 17.sp else 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (faceVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BigButton("공부 시작", enabled = faceVisible, onClick = onStart)
            }
        },
    )
}
