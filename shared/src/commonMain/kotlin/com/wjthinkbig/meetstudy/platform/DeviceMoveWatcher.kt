package com.wjthinkbig.meetstudy.platform

import androidx.compose.runtime.Composable

/** Calls [onMoved] when the device was moved to a new position and has come to rest there. */
@Composable
expect fun DeviceMoveWatcher(onMoved: () -> Unit)
