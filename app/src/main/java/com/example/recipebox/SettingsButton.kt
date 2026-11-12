package com.example.recipebox

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Three vertical dots, drawn by hand so the app needs no icon library. Opens the settings. */
@Composable
fun SettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurface
    IconButton(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = "Settings" }
    ) {
        Canvas(Modifier.size(24.dp)) {
            val radius = 2.2.dp.toPx()
            listOf(0.18f, 0.5f, 0.82f).forEach { y ->
                drawCircle(color = color, radius = radius, center = Offset(size.width / 2, size.height * y))
            }
        }
    }
}
