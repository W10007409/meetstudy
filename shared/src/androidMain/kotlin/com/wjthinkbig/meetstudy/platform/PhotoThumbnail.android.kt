package com.wjthinkbig.meetstudy.platform

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
actual fun PhotoThumbnail(path: String, modifier: Modifier) {
    val bmp = produceState<ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap()
        }
    }.value
    if (bmp != null) Image(bmp, contentDescription = "인증 사진", modifier = modifier)
}
