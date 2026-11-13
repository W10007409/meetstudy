package com.wjthinkbig.meetstudy.platform

import platform.posix.CLOCK_MONOTONIC_RAW
import platform.posix.clock_gettime_nsec_np

// Keeps counting while the device sleeps, like Android's elapsedRealtime, so away time stays right.
actual fun monotonicNowMs(): Long = (clock_gettime_nsec_np(CLOCK_MONOTONIC_RAW.toUInt()) / 1_000_000u).toLong()
