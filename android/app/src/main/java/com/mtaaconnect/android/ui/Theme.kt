package com.mtaaconnect.android.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Forest = Color(0xFF155B4A)
private val Sand = Color(0xFFF6F5F0)
private val Ink = Color(0xFF1C2925)

private val LightColors = lightColorScheme(
    primary = Forest,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEFE8),
    onPrimaryContainer = Color(0xFF103D32),
    secondary = Color(0xFFAE7138),
    background = Sand,
    surface = Color(0xFFFFFEFB),
    surfaceVariant = Color(0xFFEDEDE6),
    onSurface = Ink,
    error = Color(0xFFB3261E),
)

private val MtaaShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
)

@Composable
fun MtaaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        shapes = MtaaShapes,
        content = content,
    )
}
