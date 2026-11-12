package com.wjthinkbig.meetstudy.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

actual suspend fun decodePhoto(path: String): androidx.compose.ui.graphics.ImageBitmap? = TODO("iOS: phase 4")

@Composable
actual fun PhotoThumbnail(path: String, modifier: Modifier): Unit = TODO("iOS: phase 4")
