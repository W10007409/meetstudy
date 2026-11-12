package com.wjthinkbig.meetstudy.domain

/** Progress 0..1 while a document-like frame (quad + enough text) is held continuously; 1 = capture now. */
class AutoCaptureGate(private val holdMs: Long = 1_000, private val minChars: Int = 10) {
    private var startMs: Long? = null

    fun onFrame(timeMs: Long, quadFound: Boolean, textChars: Int): Float {
        if (!quadFound || textChars < minChars) { startMs = null; return 0f }
        val start = startMs ?: timeMs.also { startMs = it }
        return ((timeMs - start).toFloat() / holdMs).coerceIn(0f, 1f)
    }

    fun reset() { startMs = null }
}
