package com.mtaaconnect.android.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Forest = Color(0xFF173C37)
private val Pine = Color(0xFF2B5A52)
private val Sand = Color(0xFFF5F2EC)
private val Ivory = Color(0xFFFFFCF8)
private val Ink = Color(0xFF1A211E)
private val Stone = Color(0xFF6B726F)
private val Gold = Color(0xFF8D682F)

private val LightColors = lightColorScheme(
    primary = Forest,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6F0EC),
    onPrimaryContainer = Pine,
    secondary = Gold,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E5D1),
    onSecondaryContainer = Color(0xFF513B1C),
    background = Sand,
    surface = Ivory,
    surfaceVariant = Color(0xFFE7E0D5),
    onSurface = Ink,
    onSurfaceVariant = Stone,
    outline = Color(0xFFD7D0C4),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val MtaaShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun MtaaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        shapes = MtaaShapes,
        content = content,
    )
}
