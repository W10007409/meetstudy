package com.wjthinkbig.meetstudy.platform

import androidx.compose.runtime.Composable

/** Rear-camera workbook capture: fires by itself once a document with enough text is held steady. Saves to proofs/{sessionId}.jpg. */
@Composable
expect fun ProofCamera(sessionId: String, onCaptured: (photoPath: String) -> Unit, onCancel: () -> Unit)

/** Proof-flow diagnostics under tag MeetStudyProof: class names and counts only. */
expect fun logProof(msg: String)
