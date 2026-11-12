package com.wjthinkbig.meetstudy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wjthinkbig.meetstudy.data.SessionRecord
import com.wjthinkbig.meetstudy.data.SessionStore
import com.wjthinkbig.meetstudy.data.appFilesDir
import com.wjthinkbig.meetstudy.domain.StarRules
import com.wjthinkbig.meetstudy.domain.localDate
import com.wjthinkbig.meetstudy.platform.PhotoThumbnail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone

/** Awards the proof star (cap recomputed now from the store) and persists it. Keeps the photo on failure and offers retry. */
@Composable
internal fun ProofSaving(record: SessionRecord, photoPath: String, tz: TimeZone, onSaved: (SessionRecord) -> Unit) {
    var attempt by remember { mutableStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(attempt) {
        failed = false
        val updated = withContext(Dispatchers.Default) {
            try {
                val store = SessionStore(appFilesDir())
                val allowed = StarRules.proofStarAllowed(store.starsOn(record.localDate(tz), tz)) && !record.proofStar
                record.copy(proofStar = allowed || record.proofStar, proofPhotoPath = photoPath).also { store.update(it) }
            } catch (e: Exception) { null }
        }
        if (updated != null) onSaved(updated) else failed = true
    }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)) {
        if (failed) {
            Text("저장에 실패했어요. 다시 해볼까요?", fontSize = 26.sp)
            BigButton("다시 저장", enabled = true) { attempt++ }
        } else Text("저장하는 중…", fontSize = 26.sp)
    }
}

@Composable
fun ProofDoneScreen(record: SessionRecord, onReport: () -> Unit, onHome: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
        Text("공부 인증 완료!", fontSize = 40.sp)
        record.proofPhotoPath?.let { PhotoThumbnail(it, Modifier.height(240.dp)) }
        if (record.proofStar) Text("+1 ★", fontSize = 40.sp) else Text("오늘 별은 다 모았어요", fontSize = 22.sp)
        OutlinedButton(onClick = onReport) { Text("리포트 보기", fontSize = 20.sp) }
        TextButton(onClick = onHome) { Text("홈으로", fontSize = 16.sp) }
    }
}
