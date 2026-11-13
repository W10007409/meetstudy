package com.wjthinkbig.meetstudy.platform

// ponytail: iOS has no face-embedding model yet (see FaceIdentity.ios.kt), so nothing is ever enrolled.
// Store in the Keychain when TFLite/CoreML identity lands on iOS.
actual object FaceVault {
    actual fun save(embedding: FloatArray): Unit = error("no face identity on iOS")
    actual fun load(): FloatArray? = null
    actual fun clear() {}
}
