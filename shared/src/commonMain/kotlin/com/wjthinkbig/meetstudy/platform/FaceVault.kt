package com.wjthinkbig.meetstudy.platform

/** Enrolled face embedding, encrypted at rest on this device only. */
expect object FaceVault {
    fun save(embedding: FloatArray)
    fun load(): FloatArray?
    fun clear()
}
