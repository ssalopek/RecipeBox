package com.example.recipebox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage

/** models can be URL strings or local Files. Pass onRemove to show a remove button. */
@Composable
fun ImageGallery(
    models: List<Any>,
    modifier: Modifier = Modifier,
    onRemove: ((Int) -> Unit)? = null
) {
    if (models.isEmpty()) return
    var enlarged by remember { mutableStateOf<Any?>(null) }

    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(models) { index, model ->
            Box {
                AsyncImage(
                    model = model,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .height(140.dp)
                        .width(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { enlarged = model }
                )
                if (onRemove != null) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(28.dp)
                            .clickable { onRemove(index) }
                    ) {
                        Box(contentAlignment = Alignment.Center) { Text("✕") }
                    }
                }
            }
        }
    }

    enlarged?.let { model ->
        Dialog(onDismissRequest = { enlarged = null }) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().clickable { enlarged = null }
            )
        }
    }
}