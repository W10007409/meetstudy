package com.wjthinkbig.meetstudy.platform

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Test-build switches. The frame source runs the pose detector on every few frames while [recognitionView] is on. */
object DebugFlags {
    var recognitionView by mutableStateOf(false)
}
