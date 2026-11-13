package com.wjthinkbig.meetstudy.platform

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.AVFoundation.AVCaptureVideoOrientation
import platform.AVFoundation.AVCaptureVideoOrientationPortrait
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIApplication
import platform.UIKit.UIInterfaceOrientationUnknown
import platform.UIKit.UIViewController
import platform.posix.memcpy

/** Topmost presented view controller, to present system sheets over the Compose UI. */
internal fun topViewController(): UIViewController? {
    var vc = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (vc?.presentedViewController != null) vc = vc.presentedViewController
    return vc
}

/** UIInterfaceOrientation and AVCaptureVideoOrientation share raw values. Main thread only. */
internal fun currentVideoOrientation(): AVCaptureVideoOrientation {
    val o = UIApplication.sharedApplication.statusBarOrientation
    return if (o == UIInterfaceOrientationUnknown) AVCaptureVideoOrientationPortrait else o
}

internal fun ByteArray.toNSData(): NSData = usePinned { NSData.create(bytes = it.addressOf(0), length = size.convert()) }

internal fun NSData.toByteArray(): ByteArray = ByteArray(length.toInt()).also {
    if (it.isNotEmpty()) it.usePinned { p -> memcpy(p.addressOf(0), bytes, length) }
}
