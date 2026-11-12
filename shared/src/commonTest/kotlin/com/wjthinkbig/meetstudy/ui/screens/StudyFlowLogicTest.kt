package com.wjthinkbig.meetstudy.ui.screens

import com.wjthinkbig.meetstudy.data.SessionRecord
import com.wjthinkbig.meetstudy.domain.FrameSignals
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StudyFlowLogicTest {
    private fun rec(id: String, proof: Boolean = false) =
        SessionRecord(id, 0, 1_000, 1_000, emptyList(), goalStar = true, proofStar = proof, proofPhotoPath = null)

    @Test fun reportHistoryUsesUpdatedRecord() {
        val updated = rec("b", proof = true)
        val h = withUpdated(listOf(rec("a"), rec("b")), updated)
        assertEquals(listOf(rec("a"), updated), h)
        assertEquals(listOf(updated), withUpdated(emptyList(), updated)) // load failed: record still counted
    }

    @Test fun historyReadErrorIsEmptyAndLogsClassNameOnly() {
        val logs = mutableListOf<String>()
        val h = readHistoryOrEmpty({ throw IllegalStateException("secret /path") }, { logs += it })
        assertEquals(emptyList(), h)
        assertEquals(listOf("history load failed: IllegalStateException"), logs)
        assertEquals(listOf(rec("a")), readHistoryOrEmpty({ listOf(rec("a")) }, { logs += it }))
    }

    @Test fun mismatchMessageClearsWhenFaceLeaves() {
        fun f(face: Boolean, id: Boolean?) = FrameSignals(0, face, id, null, null, null, face, null, false)
        assertTrue(nextMismatch(false, f(true, false)))
        assertTrue(nextMismatch(true, f(true, null)))
        assertFalse(nextMismatch(true, f(false, null)))
    }
}
