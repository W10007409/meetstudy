package com.wjthinkbig.meetstudy.platform

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation

// Quarter size, like the Android inSampleSize = 4.
actual suspend fun decodePhoto(path: String): ImageBitmap? = withContext(Dispatchers.Default) {
    val full = UIImage.imageWithContentsOfFile(path) ?: return@withContext null
    val small = full.size.useContents { full.imageByPreparingThumbnailOfSize(CGSizeMake(width / 4, height / 4)) } ?: full
    val jpg = UIImageJPEGRepresentation(small, 0.9) ?: return@withContext null
    org.jetbrains.skia.Image.makeFromEncoded(jpg.toByteArray()).toComposeImageBitmap()
}

@Composable
actual fun PhotoThumbnail(path: String, modifier: Modifier) {
    val bmp = produceState<ImageBitmap?>(null, path) { value = decodePhoto(path) }.value
    if (bmp != null) Image(bmp, contentDescription = "인증 사진", modifier = modifier)
}
