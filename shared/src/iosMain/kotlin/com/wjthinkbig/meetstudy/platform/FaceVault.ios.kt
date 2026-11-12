package com.wjthinkbig.meetstudy.platform

actual object FaceVault {
    actual fun save(embedding: FloatArray): Unit = TODO("iOS: phase 4")
    actual fun load(): FloatArray? = TODO("iOS: phase 4")
    actual fun clear(): Unit = TODO("iOS: phase 4")
}
