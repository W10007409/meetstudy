package com.wjthinkbig.meetstudy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.rememberCoroutineScope
import com.wjthinkbig.meetstudy.data.SessionRecord
import com.wjthinkbig.meetstudy.data.SessionStore
import com.wjthinkbig.meetstudy.data.appFilesDir
import com.wjthinkbig.meetstudy.platform.FaceVault
import com.wjthinkbig.meetstudy.platform.PlatformBackHandler
import com.wjthinkbig.meetstudy.ui.screens.ReportScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import com.wjthinkbig.meetstudy.ui.screens.EnrollScreen
import com.wjthinkbig.meetstudy.ui.screens.StudyFlow

@Composable
fun App() {
    MaterialTheme {
        var needEnroll by remember { mutableStateOf(FaceVault.load() == null) }
        var studying by remember { mutableStateOf(false) }
        var history by remember { mutableStateOf<ReportData?>(null) }
        // Each branch owns its frame source, so only one camera consumer exists at a time.
        when {
            needEnroll -> EnrollScreen(onDone = { needEnroll = false })
            studying -> StudyFlow(onExit = { studying = false })
            history != null -> {
                val h = history!!
                PlatformBackHandler { history = null }
                ReportScreen(h.latest, h.all, h.tz, h.today) { history = null }
            }
            else -> Home(
                onStart = { studying = true },
                onReEnroll = { FaceVault.clear(); needEnroll = true },
                onHistory = { history = it },
            )
        }
    }
}

private class ReportData(val latest: SessionRecord, val all: List<SessionRecord>, val tz: TimeZone, val today: LocalDate)

@Composable
private fun Home(onStart: () -> Unit, onReEnroll: () -> Unit, onHistory: (ReportData) -> Unit) {
    val scope = rememberCoroutineScope()
    var empty by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
    ) {
        Button(
            onClick = onStart,
            shape = RoundedCornerShape(40.dp),
            modifier = Modifier.fillMaxWidth(0.8f).height(120.dp),
        ) { Text("공부 시작하기", fontSize = 36.sp) }
        OutlinedButton(onClick = {
            scope.launch {
                val tz = TimeZone.currentSystemDefault()
                val all = withContext(Dispatchers.Default) { SessionStore(appFilesDir()).all() }
                val latest = all.maxByOrNull { it.startMs }
                if (latest == null) empty = true else onHistory(ReportData(latest, all, tz, todayIn(tz)))
            }
        }) { Text("내 기록", fontSize = 20.sp) }
        if (empty) Text("아직 기록이 없어요", fontSize = 16.sp)
        TextButton(onClick = onReEnroll) { Text("얼굴 다시 등록", fontSize = 16.sp) }
    }
}

@OptIn(ExperimentalTime::class)
internal fun todayIn(tz: TimeZone): LocalDate = Clock.System.todayIn(tz)
