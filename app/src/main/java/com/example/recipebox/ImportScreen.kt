package com.example.recipebox

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.recipebox.data.Platform
import com.example.recipebox.data.RecipeExtractor
import com.example.recipebox.data.UrlTools

@Composable
fun ImportScreen(
    vm: RecipeViewModel,
    sharedText: String,
    onSaved: () -> Unit,
    onDuplicate: (Long) -> Unit,
    onCancel: () -> Unit
) {
    val rawUrl = remember(sharedText) { UrlTools.extractUrl(sharedText) }
    val url = remember(rawUrl) { rawUrl?.let { UrlTools.normalize(it) } }
    val platform = remember(url) { url?.let { UrlTools.identifyPlatform(it) } }

    var title by remember { mutableStateOf("") }
    var body by remember {
        mutableStateOf(
            if (rawUrl == null) sharedText else sharedText.replace(rawUrl, "").trim()
        )
    }
    val images = remember { mutableStateListOf<String>() }
    var loading by remember { mutableStateOf(url != null) }
    var saving by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(url) {
        if (url == null) return@LaunchedEffect

        val existing = vm.findBySourceUrl(url)
        if (existing != null) {
            onDuplicate(existing.id)
            return@LaunchedEffect
        }

        val result = RecipeExtractor.extract(url, platform ?: Platform.OTHER)
        if (result != null) {
            title = result.title
            body = result.body.ifBlank { body }
            images.clear()
            images.addAll(result.images)
            note = if (result.body.isBlank()) {
                "Only the title was available. Paste the caption or recipe text below."
            } else {
                "Check the text below and fix anything that looks off before saving."
            }
        } else {
            note = "Couldn't read this link automatically. Paste the caption or recipe text below."
        }
        loading = false
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Import recipe", style = MaterialTheme.typography.titleLarge)

            if (url != null) {
                SourceTag(url)
            } else {
                Text(
                    "No link found in the shared text. You can still save it as a recipe.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Fetching recipe…", style = MaterialTheme.typography.bodySmall)
            } else {
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }

            if (images.isNotEmpty()) {
                ImageGallery(
                    models = images.toList(),
                    onRemove = { index -> images.removeAt(index) }
                )
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                enabled = !loading && !saving,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Ingredients and steps") },
                enabled = !loading && !saving,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !loading && !saving && title.isNotBlank(),
                    onClick = {
                        saving = true
                        vm.saveImported(title, body, url, platform?.name, images.toList(), onSaved)
                    }
                ) { Text(if (saving) "Saving…" else "Save") }
                OutlinedButton(enabled = !saving, onClick = onCancel) { Text("Cancel") }
            }
        }
    }
}