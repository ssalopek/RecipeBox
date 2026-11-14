package com.example.recipebox

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.recipebox.data.Category
import com.example.recipebox.data.ImageStore
import com.example.recipebox.data.coverImage
import com.example.recipebox.ui.theme.recipeFieldColors
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

    // rememberSaveable keeps what you typed when the theme, font size or language changes
    var title by rememberSaveable(id) { mutableStateOf("") }
    var body by rememberSaveable(id) { mutableStateOf("") }
    var removeImage by rememberSaveable(id) { mutableStateOf(false) }
    // A picture chosen from the phone. It is only saved when you tap Save.
    var pickedImage by rememberSaveable(id) { mutableStateOf<Uri?>(null) }
    var selected by rememberSaveable(id, stateSaver = LongSetSaver) {
        mutableStateOf(emptySet<Long>())
    }
    var initialised by rememberSaveable(id) { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    // Fill the fields from the saved recipe once, and never overwrite later edits
    val ready = assigned != null && (id == 0L || existing != null)
    LaunchedEffect(ready) {
        if (ready && !initialised) {
            title = existing?.title.orEmpty()
            body = existing?.bodyText.orEmpty()
            selected = assigned.orEmpty().map { it.id }.toSet()
            initialised = true
        }
    }

    // The system photo picker needs no storage permission
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pickedImage = uri
            removeImage = false
        }
    }
    val launchPicker = {
        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Scaffold { padding ->
        // imePadding shrinks this column to the space above the keyboard. Without it the
        // keyboard covers the bottom of the text, and you cannot scroll down to it.
        Column(Modifier
            .padding(padding)
            .consumeWindowInsets(padding)
            .imePadding()
            .fillMaxSize()) {
            // Everything except the buttons scrolls, so the recipe text can be as tall as it needs
            // to be and the title and categories simply scroll out of the way while you type.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (id == 0L) "New recipe" else "Edit recipe",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    if (existing != null) {
                        TextButton(
                            onClick = { confirmDelete = true },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) { Text("Delete") }
                    }
                }

                // Small thumbnail. The image found on the page is the default, but you can
                // remove it or replace it with a picture from your phone.
                val coverName = existing?.coverImage
                val sourceUrl = existing?.sourceUrl
                val hasOriginal = coverName != null
                val shownImage: Any? = pickedImage
                    ?: coverName?.takeIf { !removeImage }?.let { ImageStore.file(context, it) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (shownImage != null) {
                        AsyncImage(
                            model = shownImage,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(MaterialTheme.shapes.small)
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        sourceUrl?.let { SourceTag(it) }
                        if (shownImage != null) {
                            Row {
                                TextButton(onClick = { launchPicker() }) { Text("Change image") }
                                TextButton(onClick = {
                                    pickedImage = null
                                    removeImage = hasOriginal
                                }) { Text("Remove image") }
                            }
                        } else if (removeImage && hasOriginal) {
                            Text(
                                "Image will be removed when you save",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row {
                                TextButton(onClick = { removeImage = false }) { Text("Undo") }
                                TextButton(onClick = { launchPicker() }) { Text("Choose image") }
                            }
                        } else {
                            TextButton(onClick = { launchPicker() }) { Text("Add image") }
                        }
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next
                    ),
                    shape = MaterialTheme.shapes.small,
                    colors = recipeFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                CategoryPicker(
                    vm = vm,
                    selected = selected,
                    onSelectedChange = { selected = it },
                    compact = true
                )

                // The part that matters most: tall from the start, and it keeps growing with the text
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Ingredients and steps") },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    ),
                    shape = MaterialTheme.shapes.small,
                    colors = recipeFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 420.dp)
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
                    enabled = title.isNotBlank() && initialised,
                    onClick = {
                        vm.save(existing, title, body, removeImage, selected, pickedImage)
                        onDone()
                    }
                ) { Text("Save") }
                OutlinedButton(onClick = onDone) { Text("Cancel") }
            }
        }
    }

    if (confirmDelete) {
        val recipe = existing
        if (recipe != null) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Delete this recipe?") },
                text = { Text("This can't be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmDelete = false
                            vm.delete(recipe)
                            onDeleted()
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
                }
            )
        }
    }
}
