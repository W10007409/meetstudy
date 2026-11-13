package com.wjthinkbig.meetstudy.ui.theme

import androidx.compose.ui.graphics.Color
import com.wjthinkbig.meetstudy.domain.FocusState

/** Camera trouble now; parent-side offline later. */
val OfflineColor = Color(0xFFFF6B6B)

fun stateColor(s: FocusState): Color = when (s) {
    FocusState.STUDYING -> Color(0xFF4CC38A)
    FocusState.AWAY -> Color(0xFF9AA3AE)
    FocusState.LOOKING_AWAY, FocusState.CAMERA_GAZE, FocusState.DROWSY, FocusState.PHONE, FocusState.APP_LEFT -> Color(0xFFFF9F43)
    FocusState.BREAK -> Color(0xFF5AA9FF)
}

fun stateLabel(s: FocusState): String = when (s) {
    FocusState.STUDYING -> "공부 중"
    FocusState.AWAY -> "자리 비움"
    FocusState.LOOKING_AWAY -> "다른 곳 보는 중"
    FocusState.CAMERA_GAZE -> "카메라 보는 중"
    FocusState.DROWSY -> "졸고 있어요"
    FocusState.PHONE -> "폰 사용 중"
    FocusState.APP_LEFT -> "앱 나감"
    FocusState.BREAK -> "휴식 중"
}
