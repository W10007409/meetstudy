package com.wjthinkbig.meetstudy.platform

import androidx.compose.runtime.Composable

// ponytail: MobileFaceNet needs the TFLite runtime (or a CoreML conversion) on iOS; until then identity check is off,
// which the enroll and study screens already handle ("본인 확인 기능이 꺼져 있어요").
private object NoFaceIdentity : FaceIdentity {
    override val available = false
    override suspend fun embed(face: FaceCrop): FloatArray = error("no face model on iOS")
}

@Composable
actual fun rememberFaceIdentity(): FaceIdentity = NoFaceIdentity
