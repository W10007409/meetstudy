package com.wjthinkbig.meetstudy.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.wjthinkbig.meetstudy.data.appFilesDir
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.UIKit.UIImageJPEGRepresentation
import platform.VisionKit.VNDocumentCameraScan
import platform.VisionKit.VNDocumentCameraViewController
import platform.VisionKit.VNDocumentCameraViewControllerDelegateProtocol
import platform.darwin.NSObject

/**
 * The system document scanner finds the page edges, shoots by itself once the page is steady and flattens it:
 * the same job the Android actual does with ML Kit text + OpenCV quads. It also has a manual shutter.
 */
@Composable
actual fun ProofCamera(sessionId: String, onCaptured: (photoPath: String) -> Unit, onCancel: () -> Unit) {
    val captured = rememberUpdatedState(onCaptured)
    val cancelled = rememberUpdatedState(onCancel)
    // remember keeps the delegate alive: the scanner holds it weakly.
    val delegate = remember(sessionId) { ScanDelegate(sessionId, { captured.value(it) }, { cancelled.value() }) }
    Box(Modifier.fillMaxSize().background(Color.Black))
    DisposableEffect(delegate) {
        if (!VNDocumentCameraViewController.supported) {
            logProof("scanner unsupported")
            cancelled.value()
            return@DisposableEffect onDispose {}
        }
        val scanner = VNDocumentCameraViewController().also { it.delegate = delegate }
        topViewController()?.presentViewController(scanner, true, null)
        // Both callbacks make the caller drop this composable, so dismissing happens in one place.
        onDispose { scanner.dismissViewControllerAnimated(true, null) }
    }
}

private class ScanDelegate(
    private val sessionId: String,
    private val onCaptured: (String) -> Unit,
    private val onCancel: () -> Unit,
) : NSObject(), VNDocumentCameraViewControllerDelegateProtocol {
    override fun documentCameraViewController(controller: VNDocumentCameraViewController, didFinishWithScan: VNDocumentCameraScan) {
        val path = save(didFinishWithScan)
        logProof(if (path != null) "captured pages=${didFinishWithScan.pageCount}" else "save failed")
        if (path != null) onCaptured(path) else onCancel()
    }

    override fun documentCameraViewController(controller: VNDocumentCameraViewController, didFailWithError: NSError) {
        logProof("scanner failed")
        onCancel()
    }

    override fun documentCameraViewControllerDidCancel(controller: VNDocumentCameraViewController) = onCancel()

    private fun save(scan: VNDocumentCameraScan): String? {
        if (scan.pageCount == 0uL) return null
        val dir = appFilesDir() + "/proofs"
        NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
        val path = "$dir/$sessionId.jpg"
        val jpg = UIImageJPEGRepresentation(scan.imageOfPageAtIndex(0uL), 0.85) ?: return null
        return if (jpg.writeToFile(path, true)) path else null
    }
}

actual fun logProof(msg: String) = println("MeetStudyProof: $msg")
