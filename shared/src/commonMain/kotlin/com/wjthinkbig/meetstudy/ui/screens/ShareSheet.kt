package com.wjthinkbig.meetstudy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import com.wjthinkbig.meetstudy.platform.loadPhoto
import com.wjthinkbig.meetstudy.platform.logProof
import com.wjthinkbig.meetstudy.ui.components.ShareCardPxHeight
import com.wjthinkbig.meetstudy.ui.components.ShareCardPxWidth
import com.wjthinkbig.meetstudy.ui.components.ShareCardDensity
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.wjthinkbig.meetstudy.data.SessionRecord
import com.wjthinkbig.meetstudy.domain.buildReport
import com.wjthinkbig.meetstudy.platform.encodePng
import com.wjthinkbig.meetstudy.platform.shareImage
import com.wjthinkbig.meetstudy.ui.components.ShareCard
import com.wjthinkbig.meetstudy.ui.components.ShareCardHeight
import com.wjthinkbig.meetstudy.ui.components.ShareCardWidth
import com.wjthinkbig.meetstudy.ui.components.shareText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val PreviewWidth = 216.dp

@Composable
fun ShareSheet(record: SessionRecord, starsToday: Int, onDismiss: () -> Unit) {
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    // Decode the proof photo first so the PNG can never be captured without it; a failed decode shares the card photo-less.
    var photo by remember { mutableStateOf<ImageBitmap?>(null) }
    var photoReady by remember { mutableStateOf(record.proofPhotoPath == null) }
    LaunchedEffect(record.proofPhotoPath) {
        val path = record.proofPhotoPath ?: return@LaunchedEffect
        photo = try { loadPhoto(path) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
        photoReady = true
    }
    val previewScale = PreviewWidth.value * LocalDensity.current.density / ShareCardPxWidth
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.background(Color.White, RoundedCornerShape(24.dp)).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // The card is laid out at a fixed 3x density (1080x1350 px). The preview scale sits outside the recording,
            // so the PNG stays full size while the on-screen preview takes a fixed real-dp box.
            Box(Modifier.size(PreviewWidth, PreviewWidth * 1.25f), contentAlignment = Alignment.Center) {
                CompositionLocalProvider(LocalDensity provides Density(ShareCardDensity, 1f)) {
                    ShareCard(
                        record, starsToday, photo,
                        Modifier.requiredSize(ShareCardWidth, ShareCardHeight).scale(previewScale)
                            .drawWithContent { layer.record { this@drawWithContent.drawContent() }; drawLayer(layer) },
                    )
                }
            }
            Button(enabled = photoReady && !busy, onClick = {
                busy = true; failed = false
                scope.launch {
                    try {
                        val bmp = layer.toImageBitmap()
                        if (bmp.width != ShareCardPxWidth || bmp.height != ShareCardPxHeight) logProof("share card size ${bmp.width}x${bmp.height}, expected 1080x1350")
                        withContext(Dispatchers.Default) {
                            shareImage(bmp.encodePng(), shareText(buildReport(record.segments).focusedMs, starsToday))
                        }
                    } catch (e: CancellationException) { throw e } catch (e: Exception) { failed = true }
                    busy = false
                }
            }) { Text("이미지로 공유", fontSize = 18.sp) }
            if (busy || !photoReady) CircularProgressIndicator(Modifier.size(28.dp))
            if (failed) Text("공유 준비에 실패했어요", fontSize = 14.sp, color = Color(0xFFD32F2F))
            OutlinedButton(onClick = {}, enabled = false) { Text("선생님에게 보내기") }
            OutlinedButton(onClick = {}, enabled = false) { Text("보호자에게 링크 보내기") }
            Text("서버 연결 후 사용 가능", fontSize = 12.sp, color = Color.Gray)
            TextButton(onClick = onDismiss) { Text("닫기") }
        }
    }
}
