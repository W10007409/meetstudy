package com.wjthinkbig.meetstudy.platform

import androidx.compose.runtime.Composable

/** Hardware/system back. Android: intercepts it; iOS has no back button, so it is a no-op. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)
