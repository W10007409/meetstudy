package com.wjthinkbig.meetstudy.platform

import androidx.compose.runtime.Composable

@Composable
actual fun DeviceMoveWatcher(onMoved: () -> Unit) {} // iOS: phase 4 (CoreMotion)
