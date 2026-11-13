package com.wjthinkbig.meetstudy.data

import com.wjthinkbig.meetstudy.domain.PoseCluster
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Learned study directions (angles only, never images), kept on this device so learning carries across sessions. */
class StudyPoseStore(private val dir: String) {
    private val serializer = ListSerializer(PoseCluster.serializer())
    private val path get() = "$dir/study_poses.json"

    /** Empty when missing or unreadable: learning simply starts over from the next calibration. */
    fun load(): List<PoseCluster> =
        try { readTextFile(path)?.let { Json.decodeFromString(serializer, it) } ?: emptyList() } catch (e: Exception) { emptyList() }

    fun save(clusters: List<PoseCluster>) = writeTextFile(path, Json.encodeToString(serializer, clusters))

    fun clear() = save(emptyList())
}
