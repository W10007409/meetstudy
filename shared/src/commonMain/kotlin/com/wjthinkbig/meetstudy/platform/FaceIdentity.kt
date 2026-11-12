package com.wjthinkbig.meetstudy.platform

import androidx.compose.runtime.Composable

interface FaceIdentity {
    /** False when the embedding model could not be loaded: identity check is off. */
    val available: Boolean
    suspend fun embed(face: FaceCrop): FloatArray
}

/** Closed when it leaves the composition. */
@Composable
expect fun rememberFaceIdentity(): FaceIdentity
