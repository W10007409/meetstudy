package com.wjthinkbig.meetstudy.platform

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.wjthinkbig.meetstudy.domain.MoveDetector

@Composable
actual fun DeviceMoveWatcher(onMoved: () -> Unit) {
    val context = LocalContext.current
    val callback = rememberUpdatedState(onMoved)
    DisposableEffect(context) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accel = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val detector = MoveDetector()
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                if (detector.onSample(SystemClock.elapsedRealtime(), e.values[0], e.values[1], e.values[2])) callback.value()
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        // No accelerometer: no auto-recalibration, the session still works.
        if (accel != null) sm.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm?.unregisterListener(listener) }
    }
}
