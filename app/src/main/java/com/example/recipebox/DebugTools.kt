package com.example.recipebox

import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.ApplicationInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Set to false to hide the debug tools even in debug builds.
 * They are never shown in release builds, whatever this is set to.
 */
private const val SHOW_DEBUG_TOOLS = true

/** A thin strip on the home screen: view the whole database as JSON, or wipe it. */
@Composable
fun DebugBar(vm: RecipeViewModel) {
    val context = LocalContext.current
    val isDebugBuild = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    if (!SHOW_DEBUG_TOOLS || !isDebugBuild) return

    val scope = rememberCoroutineScope()
    var json by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "DEBUG",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
        Spacer(Modifier.weight(1f))
        TextButton(onClick = {
            scope.launch {
                json = try {
                    vm.debugDatabaseJson()
                } catch (e: Exception) {
                    "Couldn't read the database: ${e.message}"
                }
            }
        }) { Text("View database") }
        TextButton(onClick = { confirmDelete = true }) { Text("Delete everything") }
    }

    json?.let { text ->
        AlertDialog(
            onDismissRequest = { json = null },
            title = { Text("Database (JSON)") },
            text = {
                Box(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    SelectionContainer {
                        Text(
                            text,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("RecipeBox database", text))
                }) { Text("Copy") }
            },
            dismissButton = { TextButton(onClick = { json = null }) { Text("Close") } }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete everything?") },
            text = {
                Text("This permanently deletes all recipes, categories and saved images. It can't be undone.")
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.debugDeleteEverything()
                    confirmDelete = false
                }) { Text("Delete everything") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}
