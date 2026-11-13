package com.wjthinkbig.meetstudy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Pill = RoundedCornerShape(50)
private val ButtonWidth = Modifier.widthIn(max = 480.dp).fillMaxWidth()

/** Main action: solid teal pill. */
@Composable
internal fun BigButton(
    text: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    color: Color? = null,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = Pill,
        colors = if (color != null) ButtonDefaults.buttonColors(containerColor = color) else ButtonDefaults.buttonColors(),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        contentPadding = PaddingValues(horizontal = 24.dp),
        modifier = ButtonWidth.heightIn(min = 64.dp).then(modifier),
    ) { Text(text, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
}

/** Secondary action: soft teal pill. */
@Composable
internal fun SoftButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        shape = Pill,
        contentPadding = PaddingValues(horizontal = 20.dp),
        modifier = ButtonWidth.heightIn(min = 56.dp).then(modifier),
    ) { Text(text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold) }
}

/**
 * One screen, no scrolling: [main] takes the free space and [side] holds controls.
 * Landscape (tablet on its side) puts them side by side; portrait (phone or tablet upright) stacks them.
 * `compact` = small screen (phone), for smaller type and widgets.
 */
@Composable
internal fun AdaptivePane(
    modifier: Modifier = Modifier,
    main: @Composable (Modifier, compact: Boolean) -> Unit,
    side: @Composable (Modifier, compact: Boolean, wide: Boolean) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight
        val compact = minOf(maxWidth, maxHeight) < 560.dp
        if (wide) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                main(Modifier.weight(1.3f).fillMaxHeight(), compact)
                side(Modifier.weight(1f), compact, true)
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                main(Modifier.weight(1f).fillMaxWidth(), compact)
                side(Modifier.fillMaxWidth(), compact, false)
            }
        }
    }
}
