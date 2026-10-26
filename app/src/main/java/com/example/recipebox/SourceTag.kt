package com.example.recipebox

import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import com.example.recipebox.data.UrlTools

@Composable
fun SourceTag(url: String, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    AssistChip(
        onClick = { runCatching { uriHandler.openUri(url) } },
        label = { Text(UrlTools.sourceLabel(url)) },
        modifier = modifier
    )
}