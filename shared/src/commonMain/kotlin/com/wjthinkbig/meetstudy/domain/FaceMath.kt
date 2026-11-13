package com.wjthinkbig.meetstudy.domain

import kotlin.math.sqrt

const val IDENTITY_THRESHOLD = 0.50f // ponytail: provisional until on-device calibration (128-d unaligned crops)

fun cosine(a: FloatArray, b: FloatArray): Float {
    require(a.size == b.size) { "size mismatch" }
    var dot = 0f; var na = 0f; var nb = 0f
    for (i in a.indices) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
    val d = sqrt(na) * sqrt(nb)
    return if (d == 0f) 0f else dot / d
}

/**
 * Eye-open probability from the eye contour's height / width, for platforms without an eye-open classifier (iOS Vision).
 * ponytail: 0.12 closed / 0.25 open are textbook eye-aspect values; tune on device if eyes-closed fires wrongly.
 */
fun eyeOpenFromAspect(aspect: Float): Float = ((aspect - 0.12f) / (0.25f - 0.12f)).coerceIn(0f, 1f)

private fun normalized(v: FloatArray): FloatArray {
    val n = sqrt(v.fold(0f) { s, x -> s + x * x })
    return if (n == 0f) v.copyOf() else FloatArray(v.size) { v[it] / n }
}

/** Mean of the unit-normalized vectors, re-normalized to unit length. */
fun averageNormalized(list: List<FloatArray>): FloatArray {
    require(list.isNotEmpty()) { "empty" }
    val sum = FloatArray(list[0].size)
    for (v in list) normalized(v).forEachIndexed { i, x -> sum[i] += x }
    return normalized(sum)
}
