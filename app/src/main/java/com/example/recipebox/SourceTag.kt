package com.example.recipebox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.recipebox.data.UrlTools

/** A small rounded label. Pass onClick to make it tappable. */
@Composable
fun TagPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val base = modifier.clip(CircleShape)
    Surface(
        shape = CircleShape,
        color = color,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = if (onClick != null) base.clickable(role = Role.Button, onClick = onClick) else base
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
        )
    }
}

/** Shows where a recipe came from. Tapping it opens the original link. */
@Composable
fun SourceTag(url: String, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    TagPill(
        text = UrlTools.sourceLabel(url),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
        onClick = { runCatching { uriHandler.openUri(url) } }
    )
}
