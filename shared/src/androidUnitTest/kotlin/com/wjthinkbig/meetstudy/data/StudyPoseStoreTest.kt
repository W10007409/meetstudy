package com.wjthinkbig.meetstudy.data

import com.wjthinkbig.meetstudy.domain.PoseCluster
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class StudyPoseStoreTest {
    private val dir = Files.createTempDirectory("poses").toFile()

    @Test
    fun roundTripsAndClears() {
        val store = StudyPoseStore(dir.path)
        assertEquals(emptyList(), store.load())
        val c = listOf(PoseCluster(30f, -25f, 5_000, true), PoseCluster(-45f, 10f, 200_000, true))
        store.save(c)
        assertEquals(c, store.load())
        store.clear()
        assertEquals(emptyList(), store.load())
    }

    @Test
    fun corruptFileStartsOver() {
        File(dir, "study_poses.json").writeText("{not json")
        assertEquals(emptyList(), StudyPoseStore(dir.path).load())
    }
}
