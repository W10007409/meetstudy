package com.wjthinkbig.meetstudy.ui.screens

import com.wjthinkbig.meetstudy.domain.FocusState
import com.wjthinkbig.meetstudy.domain.Segment
import com.wjthinkbig.meetstudy.ui.components.timelineFractions
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(kotlin.time.ExperimentalTime::class)
class ReportFormatTest {
    @Test fun durations() {
        assertEquals("59초", formatDuration(59_000))
        assertEquals("3분 10초", formatDuration(190_000))
        assertEquals("1시간 5분", formatDuration(3_900_000))
    }

    @Test fun eventLineUsesLocalTime() {
        val tz = TimeZone.of("Asia/Seoul")
        val start = LocalDateTime(2026, 11, 12, 14, 32).toInstant(tz).toEpochMilliseconds()
        val seg = Segment(FocusState.AWAY, start, start + 190_000)
        assertEquals("14:32 자리 비움 3분 10초", eventLine(seg, tz))
    }

    @Test fun fractionsProportional() {
        val f = timelineFractions(listOf(
            Segment(FocusState.STUDYING, 0, 300), Segment(FocusState.AWAY, 300, 400), Segment(FocusState.STUDYING, 400, 1000),
        ))
        assertEquals(listOf(0.3f, 0.1f, 0.6f), f)
        assertEquals(1f, f.sum(), 1e-4f)
    }
}
