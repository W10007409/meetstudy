package com.wjthinkbig.meetstudy.ui.components

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(kotlin.time.ExperimentalTime::class)
class ShareCardTest {
    @Test fun shareTextUsesMinutesAndStars() {
        assertEquals("오늘 MeetStudy로 26분 공부했어요! ★2", shareText(26 * 60_000L, 2))
    }

    @Test fun dateLabelIsLocalWithWeekday() {
        val seoul = TimeZone.of("Asia/Seoul")
        val ms = LocalDateTime(2026, 11, 13, 9, 0).toInstant(seoul).toEpochMilliseconds()
        assertEquals("11월 13일 (금)", cardDateLabel(ms, seoul))
    }
}
