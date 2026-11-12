package com.wjthinkbig.meetstudy.data

import com.wjthinkbig.meetstudy.domain.Segment
import kotlinx.serialization.Serializable

@Serializable
data class SessionRecord(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val goalMs: Long,
    val segments: List<Segment>,
    val goalStar: Boolean,
    val proofStar: Boolean,
    val proofPhotoPath: String?,
)
