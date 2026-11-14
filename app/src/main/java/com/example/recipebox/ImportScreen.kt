package com.example.recipebox

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.recipebox.ui.theme.recipeFieldColors

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

    // The system photo picker needs no storage permission
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) draft.pickedImage = uri }
    val launchPicker = {
        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Scaffold { padding ->
        // imePadding shrinks this column to the space above the keyboard
        Column(Modifier
            .padding(padding)
            .consumeWindowInsets(padding)
            .imePadding()
            .fillMaxSize()) {
            // Everything except the buttons scrolls, so the recipe text can be tall
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Import recipe", style = MaterialTheme.typography.headlineSmall)

                if (draft.url != null) {
                    SourceTag(draft.url)
                } else {
                    Text(
                        "No link found in the shared text. You can still save it as a recipe.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (draft.loading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        "Fetching recipe…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    draft.note?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }


                // The image found on the page is the default. You can replace it with a picture
                // from your phone, or remove it.
                val preview: Any? = draft.pickedImage ?: draft.image
                if (preview != null) {
                    AsyncImage(
                        model = preview,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(MaterialTheme.shapes.medium)
                    )
                    Row {
                        TextButton(enabled = !draft.saving, onClick = { launchPicker() }) {
                            Text("Change image")
                        }
                        TextButton(enabled = !draft.saving, onClick = {
                            draft.image = null
                            draft.pickedImage = null
                        }) { Text("Remove image") }
                    }
                } else {
                    TextButton(
                        enabled = !draft.loading && !draft.saving,
                        onClick = { launchPicker() }
                    ) { Text("Add image") }
                }

                OutlinedTextField(
                    value = draft.title,
                    onValueChange = { draft.title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    colors = recipeFieldColors(),
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
                    shape = MaterialTheme.shapes.small,
                    colors = recipeFieldColors(),
                    enabled = !draft.loading && !draft.saving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 360.dp)
                )
            }

            // Save and Cancel stay on screen above the keyboard
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    enabled = !draft.loading && !draft.saving && draft.title.isNotBlank(),
                    onClick = { vm.saveImport(onSaved) }
                ) { Text(if (draft.saving) "Saving…" else "Save") }
                OutlinedButton(enabled = !draft.saving, onClick = onCancel) { Text("Cancel") }
            }
        }
    }
}
