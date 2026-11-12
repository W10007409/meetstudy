package com.wjthinkbig.meetstudy.domain

enum class FocusState { STUDYING, AWAY, LOOKING_AWAY, DROWSY, PHONE, APP_LEFT, BREAK }

data class StateChange(val state: FocusState, val startedAtMs: Long)
