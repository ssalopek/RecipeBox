package com.example.recipebox

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.flowOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import com.example.recipebox.data.ImageStore
import com.example.recipebox.data.coverImage
@Composable
fun RecipeEditScreen(
    vm: RecipeViewModel,
    id: Long,
    onDone: () -> Unit,
    onDeleted: () -> Unit
) {
    val existing by remember(id) {
        if (id == 0L) flowOf(null) else vm.observe(id)
    }.collectAsState(initial = null)

    var title by remember(existing) { mutableStateOf(existing?.title ?: "") }
    var body by remember(existing) { mutableStateOf(existing?.bodyText ?: "") }
    val context = LocalContext.current
    var removeImage by remember(existing) { mutableStateOf(false) }

    Scaffold { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            existing?.sourceUrl?.let { SourceTag(it) }
            existing?.coverImage?.takeIf { !removeImage }?.let { name ->
                AsyncImage(
                    model = ImageStore.file(context, name),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                TextButton(onClick = { removeImage = true }) { Text("Remove image") }
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Ingredients and steps") },
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = title.isNotBlank(),
                    onClick = {
                        vm.save(existing, title, body, removeImage)
                        onDone()
                    }
                ) { Text("Save") }

                OutlinedButton(onClick = onDone) { Text("Cancel") }

                existing?.let { recipe ->
                    TextButton(onClick = {
                        vm.delete(recipe)
                        onDeleted()
                    }) { Text("Delete") }
                }
            }
        }
    }
}