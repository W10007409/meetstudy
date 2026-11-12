package com.wjthinkbig.meetstudy.platform

import androidx.compose.runtime.Composable

/** Rear-camera workbook capture: fires by itself once a document with enough text is held steady. Saves to proofs/{sessionId}.jpg. */
@Composable
expect fun ProofCamera(sessionId: String, onCaptured: (photoPath: String) -> Unit, onCancel: () -> Unit)
