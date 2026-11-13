package com.wjthinkbig.meetstudy.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import com.wjthinkbig.meetstudy.domain.MoveDetector
import kotlinx.cinterop.useContents
import platform.CoreMotion.CMMotionManager
import platform.Foundation.NSOperationQueue

private const val G = 9.81 // CoreMotion reports g; MoveDetector expects m/s^2

@Composable
actual fun DeviceMoveWatcher(onMoved: () -> Unit) {
    val callback = rememberUpdatedState(onMoved)
    DisposableEffect(Unit) {
        val mm = CMMotionManager()
        val detector = MoveDetector()
        // No accelerometer: no auto-recalibration, the session still works.
        if (mm.accelerometerAvailable) {
            mm.accelerometerUpdateInterval = 0.06
            mm.startAccelerometerUpdatesToQueue(NSOperationQueue.mainQueue) { data, _ ->
                data?.acceleration?.useContents {
                    if (detector.onSample(monotonicNowMs(), (x * G).toFloat(), (y * G).toFloat(), (z * G).toFloat())) callback.value()
                }
            }
        }
        onDispose { mm.stopAccelerometerUpdates() }
    }
}
