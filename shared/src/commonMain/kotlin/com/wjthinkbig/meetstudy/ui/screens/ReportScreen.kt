package com.wjthinkbig.meetstudy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wjthinkbig.meetstudy.data.SessionRecord
import com.wjthinkbig.meetstudy.domain.FocusState
import com.wjthinkbig.meetstudy.domain.Segment
import com.wjthinkbig.meetstudy.domain.buildReport
import com.wjthinkbig.meetstudy.domain.dailyTotals
import com.wjthinkbig.meetstudy.domain.streakDays
import com.wjthinkbig.meetstudy.ui.components.TimelineBar
import com.wjthinkbig.meetstudy.ui.components.TrendChart
import com.wjthinkbig.meetstudy.ui.theme.stateColor
import com.wjthinkbig.meetstudy.ui.theme.stateLabel
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** "59초" / "3분 10초" / "1시간 5분". */
fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return when {
        s >= 3600 -> "${s / 3600}시간 ${s % 3600 / 60}분"
        s >= 60 -> "${s / 60}분 ${s % 60}초"
        else -> "${s}초"
    }
}

/** "14:32 자리 비움 3분 10초" (local time of segment start). */
@OptIn(ExperimentalTime::class)
fun eventLine(s: Segment, tz: TimeZone): String {
    val t = Instant.fromEpochMilliseconds(s.startMs).toLocalDateTime(tz)
    return "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')} " +
        "${stateLabel(s.state)} ${formatDuration(s.endMs - s.startMs)}"
}

@Composable
fun ReportScreen(record: SessionRecord, allRecords: List<SessionRecord>, tz: TimeZone, today: LocalDate, onBack: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = onBack) { Text("< 뒤로", fontSize = 18.sp) }
        TabRow(selectedTabIndex = tab) {
            listOf("이번 공부", "주간", "월간").forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, fontSize = 18.sp) })
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (tab) {
                0 -> SessionTab(record, tz)
                1 -> TrendTab(allRecords, tz, today, 7)
                else -> TrendTab(allRecords, tz, today, 30)
            }
        }
    }
}

@Composable
private fun SessionTab(record: SessionRecord, tz: TimeZone) {
    val r = buildReport(record.segments)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard("총 시간", formatDuration(r.totalMs), Modifier.weight(1f))
        StatCard("순공부", formatDuration(r.focusedMs), Modifier.weight(1f))
        StatCard("집중률", "${(r.focusRate * 100).toInt()}%", Modifier.weight(1f))
    }
    Text("구간 보기", fontSize = 18.sp, fontWeight = FontWeight.Bold)
    TimelineBar(record.segments)
    // Counts and times per state (BREAK included = 휴식 횟수·시간), colored like the timeline.
    r.byState.forEach { (state, v) ->
        Row {
            Text("● ", fontSize = 16.sp, color = stateColor(state))
            Text("${stateLabel(state)} ${v.first}번 · ${formatDuration(v.second)}", fontSize = 16.sp)
        }
    }
    if (FocusState.BREAK !in r.byState) Text("휴식 0번", fontSize = 16.sp)
    Text("있었던 일", fontSize = 18.sp, fontWeight = FontWeight.Bold)
    if (r.events.isEmpty()) Text("끝까지 집중했어요!", fontSize = 16.sp)
    r.events.forEach { Text(eventLine(it, tz), fontSize = 16.sp) }
}

@Composable
private fun TrendTab(all: List<SessionRecord>, tz: TimeZone, today: LocalDate, days: Int) {
    val d = dailyTotals(all, tz, today.minus(days - 1, DateTimeUnit.DAY), today)
    Text("순공부 ${formatDuration(d.sumOf { it.focusedMs })}", fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Text("별 ${d.sumOf { it.stars }}개 · 연속 ${streakDays(all, tz, today)}일 달성", fontSize = 18.sp)
    TrendChart(d)
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(label, fontSize = 14.sp, color = Color.Gray)
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}
