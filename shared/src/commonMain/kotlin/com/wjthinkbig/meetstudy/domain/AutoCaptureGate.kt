package com.wjthinkbig.meetstudy.domain

/**
 * A frame looks like a workbook page: a page outline with some text in it, or, when the camera is close enough that
 * the page edges leave the frame, plenty of text spread over a large part of it ([textAreaFrac] = text bounds / frame).
 */
fun looksLikeDocument(quadFound: Boolean, chars: Int, textAreaFrac: Float, minChars: Int = 10): Boolean =
    (quadFound && chars >= minChars) || (chars >= minChars * 3 && textAreaFrac >= 0.25f)

/**
 * Progress 0..1 while a document-like frame is held; 1 = capture now. A miss shorter than [graceMs] (edge or OCR
 * flicker) keeps the hold but never reports 1, so capture only fires on a frame that actually looks like a page.
 */
class AutoCaptureGate(private val holdMs: Long = 1_000, private val minChars: Int = 10, private val graceMs: Long = 400) {
    private var startMs: Long? = null
    private var lastOkMs = 0L

    fun onFrame(timeMs: Long, quadFound: Boolean, textChars: Int, textAreaFrac: Float = 0f): Float {
        val start = startMs
        if (!looksLikeDocument(quadFound, textChars, textAreaFrac, minChars)) {
            if (start == null || timeMs - lastOkMs > graceMs) { startMs = null; return 0f }
            return progress(timeMs, start).coerceAtMost(0.99f)
        }
        lastOkMs = timeMs
        return progress(timeMs, start ?: timeMs.also { startMs = it })
    }

    fun reset() { startMs = null }

    private fun progress(now: Long, start: Long) = ((now - start).toFloat() / holdMs).coerceIn(0f, 1f)
}
