package com.wjthinkbig.meetstudy.domain

enum class FocusState { STUDYING, AWAY, LOOKING_AWAY, CAMERA_GAZE, DROWSY, PHONE, APP_LEFT, BREAK }

data class StateChange(val state: FocusState, val startedAtMs: Long)
