package com.wjthinkbig.meetstudy.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wjthinkbig.meetstudy.data.SessionRecord
import com.wjthinkbig.meetstudy.domain.buildReport
import com.wjthinkbig.meetstudy.domain.stars
import com.wjthinkbig.meetstudy.ui.screens.formatDuration
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private val Mint = Color(0xFF7ED6B4)
private val Sky = Color(0xFF8EC5FF)
private val Butter = Color(0xFFFFD966)
private val Coral = Color(0xFFFF9B85)
private val Navy = Color(0xFF2E3A59)

/** Design size 360x450 dp rendered at a fixed 3x density = exactly 1080x1350 px on every device. */
const val ShareCardDensity = 3f
const val ShareCardPxWidth = 1080
const val ShareCardPxHeight = 1350
val ShareCardWidth = 360.dp
val ShareCardHeight = 450.dp

fun shareText(focusedMs: Long, stars: Int) = "오늘 MeetStudy로 ${focusedMs / 60_000}분 공부했어요! ★$stars"

@OptIn(ExperimentalTime::class)
fun cardDateLabel(epochMs: Long, tz: TimeZone): String {
    val d = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(tz)
    val dow = when (d.dayOfWeek) {
        DayOfWeek.MONDAY -> "월"
        DayOfWeek.TUESDAY -> "화"
        DayOfWeek.WEDNESDAY -> "수"
        DayOfWeek.THURSDAY -> "목"
        DayOfWeek.FRIDAY -> "금"
        DayOfWeek.SATURDAY -> "토"
        else -> "일"
    }
    return "${d.monthNumber}월 ${d.dayOfMonth}일 ($dow)"
}

/** No face images, camera frames or names: only time, rate, stars and the workbook proof photo. */
@Composable
fun ShareCard(record: SessionRecord, starsToday: Int, photo: ImageBitmap?, modifier: Modifier = Modifier) {
    val report = buildReport(record.segments)
    // Fixed look: ignore the user's font scale so the image is identical on any device.
    CompositionLocalProvider(LocalDensity provides Density(ShareCardDensity, 1f)) {
        Box(modifier.background(Brush.verticalGradient(listOf(Sky, Mint))).padding(20.dp)) {
            Column(
                Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)).background(Color.White).padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(cardDateLabel(record.startMs, TimeZone.currentSystemDefault()), fontSize = 20.sp, color = Navy)
                Text("순공부", fontSize = 16.sp, color = Navy)
                Text(formatDuration(report.focusedMs), fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Navy)
                Text("집중률 ${(report.focusRate * 100).toInt()}%", fontSize = 22.sp, color = Coral, fontWeight = FontWeight.Bold)
                Text("★".repeat(record.stars()).ifEmpty { "☆" }, fontSize = 32.sp, color = Butter)
                Text("오늘 모은 별 ${starsToday}개", fontSize = 14.sp, color = Navy)
                photo?.let {
                    Image(it, null, Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Fit)
                }
                Text("MeetStudy", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Mint)
            }
        }
    }
}
