package com.wjthinkbig.meetstudy.platform

/** Monotonic ms (not wall clock): the clock FrameSignals.timeMs and all session timing use. */
expect fun monotonicNowMs(): Long
