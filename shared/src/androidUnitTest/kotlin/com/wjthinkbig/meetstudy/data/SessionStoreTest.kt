package com.wjthinkbig.meetstudy.data

import com.wjthinkbig.meetstudy.domain.FocusState
import com.wjthinkbig.meetstudy.domain.Segment
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SessionStoreTest {
    private val dir = Files.createTempDirectory("sessions").toFile().path
    private fun rec(id: String, start: Long, goal: Boolean = true, proof: Boolean = false) = SessionRecord(
        id, start, start + 1000, 500, listOf(Segment(FocusState.STUDYING, start, start + 1000)), goal, proof, "p.jpg",
    )

    @Test
    fun storeRoundTrips() {
        val a = rec("a", 0); val b = rec("b", 5000, proof = true)
        SessionStore(dir).apply { save(a); save(b) }
        assertEquals(listOf(a, b), SessionStore(dir).all())
    }

    @Test
    fun starsOnCountsByLocalDate() {
        val s = SessionStore(dir)
        s.save(rec("a", 1000, goal = true, proof = true)); s.save(rec("b", 86_400_000 + 1000))
        assertEquals(2, s.starsOn(LocalDate(1970, 1, 1), TimeZone.UTC))
        assertEquals(1, s.starsOn(LocalDate(1970, 1, 2), TimeZone.UTC))
    }

    @Test
    fun corruptFileIsKeptNotOverwritten() {
        File(dir, "sessions.json").writeText("{not json")
        val s = SessionStore(dir)
        assertEquals(emptyList(), s.all())
        s.save(rec("a", 0))
        assertEquals(1, s.all().size)
        assertTrue(File(dir).list()!!.any { it.startsWith("sessions.corrupt-") })
    }

    @Test
    fun renameFileThrowsWhenItFails() {
        assertFailsWith<java.io.IOException> { renameFile("$dir/missing.json", "$dir/other.json") }
    }
}
