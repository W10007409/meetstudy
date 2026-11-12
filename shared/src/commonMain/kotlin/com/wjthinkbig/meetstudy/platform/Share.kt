package com.wjthinkbig.meetstudy.platform

import androidx.compose.ui.graphics.ImageBitmap

/** Opens the OS share sheet with the PNG attached. */
expect fun shareImage(pngBytes: ByteArray, text: String)

expect fun ImageBitmap.encodePng(): ByteArray
