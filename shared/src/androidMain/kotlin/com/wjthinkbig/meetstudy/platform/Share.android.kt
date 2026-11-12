package com.wjthinkbig.meetstudy.platform

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.FileProvider
import com.wjthinkbig.meetstudy.data.appContext
import java.io.ByteArrayOutputStream
import java.io.File

actual fun ImageBitmap.encodePng(): ByteArray =
    ByteArrayOutputStream().also { asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

actual fun shareImage(pngBytes: ByteArray, text: String) {
    val ctx = checkNotNull(appContext) { "FaceVault.init not called" }
    val file = File(ctx.cacheDir, "share/meetstudy-share.png").also { it.parentFile?.mkdirs() }
    file.writeBytes(pngBytes)
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(send, "공유하기").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
