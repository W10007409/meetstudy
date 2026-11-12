package com.wjthinkbig.meetstudy.platform

import androidx.compose.runtime.Composable

@Composable
actual fun ProofCamera(sessionId: String, onCaptured: (photoPath: String) -> Unit, onCancel: () -> Unit): Unit = TODO("iOS: phase 4")

actual fun logProof(msg: String) = println("MeetStudyProof: $msg")
