package com.wjthinkbig.meetstudy.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Decodes the saved photo (downsampled) off the main thread; null if unreadable. */
suspend fun loadPhoto(path: String): androidx.compose.ui.graphics.ImageBitmap? = decodePhoto(path)

expect suspend fun decodePhoto(path: String): androidx.compose.ui.graphics.ImageBitmap?

/** Shows the saved photo at [path], downsampled. */
@Composable
expect fun PhotoThumbnail(path: String, modifier: Modifier)
