package com.example.recipebox

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

@Composable
fun ImportScreen(
    vm: RecipeViewModel,
    onSaved: () -> Unit,
    onDuplicate: (Long) -> Unit,
    onCancel: () -> Unit
) {
    // The import state lives in the ViewModel so it survives configuration changes (theme, font size, language).
    // It is null only for a moment while the screen is being left.
    val draft = vm.importDraft ?: return

    // Already saved this link? Open the existing recipe instead of importing again.
    draft.duplicateId?.let { id ->
        LaunchedEffect(id) { onDuplicate(id) }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Import recipe", style = MaterialTheme.typography.titleLarge)

            if (draft.url != null) {
                SourceTag(draft.url)
            } else {
                Text(
                    "No link found in the shared text. You can still save it as a recipe.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (draft.loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Fetching recipe…", style = MaterialTheme.typography.bodySmall)
            } else {
                draft.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }

            draft.image?.let { imageUrl ->
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                TextButton(enabled = !draft.saving, onClick = { draft.image = null }) {
                    Text("Remove image")
                }
            }

            OutlinedTextField(
                value = draft.title,
                onValueChange = { draft.title = it },
                label = { Text("Title") },
                singleLine = true,
                enabled = !draft.loading && !draft.saving,
                modifier = Modifier.fillMaxWidth()
            )

            CategoryPicker(
                vm = vm,
                selected = draft.selected,
                onSelectedChange = { draft.selected = it }
            )

            OutlinedTextField(
                value = draft.body,
                onValueChange = { draft.body = it },
                label = { Text("Ingredients and steps") },
                enabled = !draft.loading && !draft.saving,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !draft.loading && !draft.saving && draft.title.isNotBlank(),
                    onClick = { vm.saveImport(onSaved) }
                ) { Text(if (draft.saving) "Saving…" else "Save") }
                OutlinedButton(enabled = !draft.saving, onClick = onCancel) { Text("Cancel") }
            }
        }
    }
}
