package com.example.recipebox

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.example.recipebox.data.ImageStore
import com.example.recipebox.data.imageFiles

@Composable
fun RecipeViewScreen(
    vm: RecipeViewModel,
    id: Long,
    onEdit: () -> Unit,
    onBack: () -> Unit
) {
    val recipe by remember(id) { vm.observe(id) }.collectAsState(initial = null)

    // Keep the screen awake while cooking
    val view = LocalView.current
    val context = LocalContext.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    Scaffold { padding ->
        recipe?.let { r ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onBack) { Text("Back") }
                    Button(onClick = onEdit) { Text("Edit") }
                }

                Text(r.title, style = MaterialTheme.typography.headlineSmall)
                r.sourceUrl?.let { SourceTag(it) }
                ImageGallery(r.imageFiles().map { ImageStore.file(context, it) })

                SelectionContainer {
                    Text(r.bodyText, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}