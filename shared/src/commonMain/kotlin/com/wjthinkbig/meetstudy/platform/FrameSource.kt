package com.wjthinkbig.meetstudy.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.wjthinkbig.meetstudy.domain.FrameSignals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** 112x112 face crop (ARGB). Memory only: never persist or log. */
class FaceCrop(val size: Int, val argb: IntArray)

interface FrameSource {
    val signals: Flow<FrameSignals>
    val latestFace: StateFlow<FaceCrop?>
}

@Composable
expect fun rememberFrameSource(enablePhone: Boolean): FrameSource

@Composable
expect fun CameraPreview(modifier: Modifier)
