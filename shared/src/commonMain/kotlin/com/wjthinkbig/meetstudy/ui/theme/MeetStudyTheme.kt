package com.wjthinkbig.meetstudy.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Calm study desk: warm paper, ink text, the launcher icon's teal as the one strong colour.
val Teal = Color(0xFF1F8A82)
val TealSoft = Color(0xFFDDF1EE)
val Paper = Color(0xFFFAF7F2)
val Ink = Color(0xFF1F2A37)
val InkSoft = Color(0xFF5B6675)
val Card = Color(0xFFFFFFFF)

private val Scheme = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = TealSoft,
    onPrimaryContainer = Color(0xFF0F4F4A),
    secondaryContainer = TealSoft,
    onSecondaryContainer = Color(0xFF0F4F4A),
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEFEAE2),
    onSurfaceVariant = InkSoft,
    outline = Color(0xFFD8D1C6),
)

@Composable
fun MeetStudyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Scheme,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp),
        ),
    ) {
        Surface(Modifier.fillMaxSize(), color = Paper, contentColor = Ink, content = content)
    }
}
