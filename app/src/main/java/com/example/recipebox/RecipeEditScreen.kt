package com.example.recipebox

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.recipebox.data.Category
import com.example.recipebox.data.ImageStore
import com.example.recipebox.data.coverImage
import kotlinx.coroutines.flow.flowOf

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

    // null until the saved categories have loaded
    val assigned by remember(id) {
        if (id == 0L) flowOf(emptyList<Category>()) else vm.observeCategoriesOf(id)
    }.collectAsState(initial = null)

    val context = LocalContext.current
    var title by remember(existing) { mutableStateOf(existing?.title ?: "") }
    var body by remember(existing) { mutableStateOf(existing?.bodyText ?: "") }
    var removeImage by remember(existing) { mutableStateOf(false) }

    var selected by remember(id) { mutableStateOf(emptySet<Long>()) }
    var selectionLoaded by remember(id) { mutableStateOf(false) }
    LaunchedEffect(assigned) {
        val current = assigned
        if (!selectionLoaded && current != null) {
            selected = current.map { it.id }.toSet()
            selectionLoaded = true
        }
    }

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

            CategoryPicker(
                vm = vm,
                selected = selected,
                onSelectedChange = { selected = it }
            )

            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Ingredients and steps") },
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = title.isNotBlank() && selectionLoaded,
                    onClick = {
                        vm.save(existing, title, body, removeImage, selected)
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