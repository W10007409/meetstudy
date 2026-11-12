package com.wjthinkbig.meetstudy.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Shows the saved photo at [path], downsampled. */
@Composable
expect fun PhotoThumbnail(path: String, modifier: Modifier)
