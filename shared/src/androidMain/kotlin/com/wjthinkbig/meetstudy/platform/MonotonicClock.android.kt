package com.wjthinkbig.meetstudy.platform

import android.os.SystemClock

actual fun monotonicNowMs(): Long = SystemClock.elapsedRealtime()
