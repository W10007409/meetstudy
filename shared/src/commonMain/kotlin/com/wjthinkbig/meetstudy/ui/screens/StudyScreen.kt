package com.wjthinkbig.meetstudy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.wjthinkbig.meetstudy.data.SessionRecord
import com.wjthinkbig.meetstudy.data.SessionStore
import com.wjthinkbig.meetstudy.data.StudyPoseStore
import com.wjthinkbig.meetstudy.data.appFilesDir
import com.wjthinkbig.meetstudy.domain.Baseline
import com.wjthinkbig.meetstudy.domain.BreakPolicy
import com.wjthinkbig.meetstudy.domain.FocusState
import com.wjthinkbig.meetstudy.domain.FocusStateMachine
import com.wjthinkbig.meetstudy.domain.FrameSignals
import com.wjthinkbig.meetstudy.domain.Segment
import com.wjthinkbig.meetstudy.domain.StarRules
import com.wjthinkbig.meetstudy.domain.StudyPoseModel
import com.wjthinkbig.meetstudy.domain.StudySession
import com.wjthinkbig.meetstudy.platform.CameraPreview
import com.wjthinkbig.meetstudy.platform.FrameSource
import com.wjthinkbig.meetstudy.platform.KeepScreenOn
import com.wjthinkbig.meetstudy.platform.PlatformBackHandler
import com.wjthinkbig.meetstudy.platform.monotonicNowMs
import com.wjthinkbig.meetstudy.ui.theme.OfflineColor
import com.wjthinkbig.meetstudy.ui.theme.stateColor
import com.wjthinkbig.meetstudy.ui.theme.stateLabel
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val STALE_MS = 3_000L
private val BREAK_MS = BreakPolicy().maxMinutesEach * 60_000L

@OptIn(ExperimentalTime::class)
private fun nowEpochMs(): Long = Clock.System.now().toEpochMilliseconds()

/** Session timing runs on the monotonic clock (wall-clock jumps can't fake study time); records need epoch ms. */
internal fun toEpoch(segments: List<Segment>, offsetMs: Long): List<Segment> =
    segments.map { it.copy(startMs = it.startMs + offsetMs, endMs = it.endMs + offsetMs) }

/** "mm:ss" under an hour, "h:mm:ss" from an hour (or "hh:mm:ss" with [alwaysHours]); negatives show as zero. */
internal fun formatClock(ms: Long, alwaysHours: Boolean = false): String {
    val total = maxOf(0L, ms) / 1000
    val h = total / 3600
    fun two(n: Long) = n.toString().padStart(2, '0')
    val ms2 = "${two(total / 60 % 60)}:${two(total % 60)}"
    return when {
        alwaysHours -> "${two(h)}:$ms2"
        h > 0 -> "$h:$ms2"
        else -> ms2
    }
}

/** A dead camera must not count as studying; overrides (break, app left) already own the state. */
internal fun shouldFeedSynthetic(lastRealFrameMs: Long, nowMs: Long, state: FocusState): Boolean =
    state != FocusState.BREAK && state != FocusState.APP_LEFT && nowMs - lastRealFrameMs >= STALE_MS

internal fun syntheticFrame(timeMs: Long) = FrameSignals(
    timeMs = timeMs, faceVisible = false, identityMatch = null, yaw = null, pitch = null,
    eyesOpenProb = null, personVisible = false, noseBelowShoulders = null, phoneVisible = false,
)

/** [endMs] and [segments] are monotonic; [id] is fixed per session so retries can't create a second record. */
private class Ended(val id: String, val segments: List<Segment>, val endMs: Long, val goalReached: Boolean)

/** Plain holder so 5 Hz frames don't recompose the screen. */
private class FrameWatch(var lastRealMs: Long)

@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
@Composable
fun StudyScreen(source: FrameSource, goalMs: Long, baseline: Baseline, onFinished: (SessionRecord) -> Unit) {
    val startMs = remember { monotonicNowMs() }
    val startEpochMs = remember { nowEpochMs() }
    val poseStore = remember { StudyPoseStore(appFilesDir()) }
    val machine = remember { FocusStateMachine(baseline, poses = StudyPoseModel(poseStore.load())) }
    val session = remember { StudySession(goalMs, startMs, machine) }
    val store = remember { SessionStore(appFilesDir()) }
    val watch = remember { FrameWatch(startMs) }
    val state by session.state.collectAsState()
    val pending by session.pending.collectAsState()
    var now by remember { mutableLongStateOf(startMs) }
    var breakStartMs by remember { mutableLongStateOf(0L) }
    var ended by remember { mutableStateOf<Ended?>(null) }
    var saveFailed by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var confirmEnd by remember { mutableStateOf(false) }

    // Segments live in [ended] until a save succeeds, so a failed write never loses the session.
    fun save(e: Ended) {
        if (saving) return
        saving = true
        val offset = startEpochMs - startMs
        val saved = try {
            val tz = TimeZone.currentSystemDefault()
            val day = Instant.fromEpochMilliseconds(startEpochMs).toLocalDateTime(tz).date // the record is filed under its start date
            SessionRecord(
                id = e.id, startMs = startEpochMs, endMs = e.endMs + offset, goalMs = goalMs,
                segments = toEpoch(e.segments, offset),
                goalStar = StarRules.goalStarAllowed(e.goalReached, store.starsOn(day, tz)),
                proofStar = false, proofPhotoPath = null,
            ).also { store.save(it) }
        } catch (ex: Exception) {
            null
        }
        saving = false
        saveFailed = saved == null
        if (saved != null) onFinished(saved)
    }

    fun end() {
        if (ended != null) return
        val t = monotonicNowMs()
        val reached = session.isGoalReached(t)
        val e = Ended(Uuid.random().toString(), session.finish(t), t, reached)
        // Learned study directions are a convenience: a failed write must not block saving the session.
        try { poseStore.save(machine.poses.clusters) } catch (ex: Exception) { }
        ended = e
        save(e)
    }

    KeepScreenOn()
    PlatformBackHandler { if (ended == null) confirmEnd = true }

    LaunchedEffect(source) {
        source.signals.collect { s ->
            if (ended != null) return@collect
            watch.lastRealMs = s.timeMs
            session.onFrame(s)
        }
    }

    LaunchedEffect(Unit) {
        while (ended == null) {
            val t = monotonicNowMs()
            session.tick(t)
            if (shouldFeedSynthetic(watch.lastRealMs, t, session.state.value)) session.onFrame(syntheticFrame(t))
            now = t
            if (session.isGoalReached(t)) end()
            delay(1_000)
        }
    }

    // The camera stops in the background too; give it the 3 s grace again on return.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { if (ended == null) session.onAppLeft(true, monotonicNowMs()) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        if (ended == null) {
            val t = monotonicNowMs()
            watch.lastRealMs = t
            session.onAppLeft(false, t)
        }
    }

    if (ended != null) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        ) {
            if (saveFailed) {
                Text("기록 저장에 실패했어요", fontSize = 28.sp)
                BigButton("다시 시도", enabled = !saving) { ended?.let(::save) }
            } else {
                Text("저장하는 중…", fontSize = 24.sp)
            }
        }
        return
    }

    val focused = session.focusedMs(now)
    val onBreak = state == FocusState.BREAK
    val cameraStale = shouldFeedSynthetic(watch.lastRealMs, now, state)
    val color = stateColor(state)

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(28.dp)).background(Color.Black)) {
            CameraPreview(Modifier.fillMaxSize())
            Column(Modifier.align(Alignment.TopStart).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(
                    text = when {
                        cameraStale -> "카메라를 확인해 주세요"
                        onBreak -> "${stateLabel(state)} · 남은 휴식 ${formatClock(BREAK_MS - (now - breakStartMs))}"
                        else -> stateLabel(state)
                    },
                    color = if (cameraStale) OfflineColor else color,
                )
                val p = pending
                if (p != null && !cameraStale && !onBreak) {
                    StatusChip("${pendingLabel(p.state)} · ${secondsUntil(p.dueMs, now)}초", Color.Black.copy(alpha = 0.6f))
                }
            }
            Surface(Modifier.align(Alignment.TopEnd).padding(16.dp), shape = RoundedCornerShape(50), color = Color(0xCCEFF1F4)) {
                Text("충전기를 연결해 주세요", fontSize = 14.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { (focused.toFloat() / goalMs).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxSize(),
                    color = color,
                    strokeWidth = 18.dp,
                    trackColor = Color(0xFFE6E9ED),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(formatClock(goalMs - focused), fontSize = 52.sp)
                    Text("순공부 ${formatClock(focused, alwaysHours = true)}", fontSize = 16.sp)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = {
                        val t = monotonicNowMs()
                        if (onBreak) session.endBreak(t)
                        else if (session.startBreak(t)) breakStartMs = t
                        now = t
                    },
                    enabled = onBreak || session.breaksLeft() > 0,
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = stateColor(FocusState.BREAK)),
                    modifier = Modifier.height(72.dp),
                ) { Text(if (onBreak) "휴식 끝내기" else "휴식 (${session.breaksLeft()}회 남음)", fontSize = 22.sp) }
                OutlinedButton(
                    onClick = { confirmEnd = true },
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.height(72.dp),
                ) { Text("공부 끝내기", fontSize = 22.sp) }
            }
        }
    }

    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("정말 끝낼까요?") },
            text = { Text("지금까지 공부한 시간은 저장돼요") },
            confirmButton = { TextButton({ confirmEnd = false; end() }) { Text("끝내기") } },
            dismissButton = { TextButton({ confirmEnd = false }) { Text("계속 공부하기") } },
        )
    }
}

/** Seconds left until a pending state is recorded, never below 0. */
internal fun secondsUntil(dueMs: Long, nowMs: Long): Long = maxOf(0L, (dueMs - nowMs + 999) / 1000)

private fun pendingLabel(s: FocusState) = when (s) {
    FocusState.AWAY -> "자리를 비운 것 같아요"
    FocusState.LOOKING_AWAY -> "딴 곳을 보고 있어요"
    FocusState.CAMERA_GAZE -> "카메라를 보고 있어요"
    FocusState.DROWSY -> "졸린 것 같아요"
    FocusState.PHONE -> "폰이 보여요"
    else -> "확인 중"
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(color).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text, fontSize = 18.sp, color = Color.White)
    }
}
