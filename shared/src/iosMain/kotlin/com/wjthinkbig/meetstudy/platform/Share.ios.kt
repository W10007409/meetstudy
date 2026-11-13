package com.wjthinkbig.meetstudy.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIImage
import platform.UIKit.popoverPresentationController

actual fun ImageBitmap.encodePng(): ByteArray =
    checkNotNull(Image.makeFromBitmap(asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)) { "png encode failed" }.bytes

actual fun shareImage(pngBytes: ByteArray, text: String) {
    val top = topViewController() ?: return
    val image = UIImage.imageWithData(pngBytes.toNSData()) ?: return
    val sheet = UIActivityViewController(listOf(image, text), null)
    // iPad shows the sheet as a popover and crashes without an anchor.
    sheet.popoverPresentationController?.let { it.sourceView = top.view; it.sourceRect = top.view.bounds }
    top.presentViewController(sheet, true, null)
}
