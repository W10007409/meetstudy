package com.wjthinkbig.meetstudy.platform

object PhoneSchedule {
    const val INTERVAL_MS = 1_000L

    fun shouldRun(lastRunMs: Long?, nowMs: Long, faceOrPerson: Boolean, enabled: Boolean, disabled: Boolean): Boolean =
        enabled && !disabled && faceOrPerson && (lastRunMs == null || nowMs - lastRunMs >= INTERVAL_MS)
}

/** Rolling mean of the last [WINDOW] inference times; over budget once a full window averages above [LIMIT_MS]. */
class InferenceBudget {
    private val samples = ArrayDeque<Long>()
    val avgMs: Long get() = if (samples.isEmpty()) 0 else samples.sum() / samples.size
    val overBudget: Boolean get() = samples.size >= WINDOW && avgMs > LIMIT_MS

    fun record(ms: Long) {
        samples.addLast(ms)
        if (samples.size > WINDOW) samples.removeFirst()
    }

    companion object {
        const val WINDOW = 10
        const val LIMIT_MS = 150L
    }
}
