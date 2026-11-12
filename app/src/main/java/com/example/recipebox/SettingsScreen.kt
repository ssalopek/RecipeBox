package com.example.recipebox

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = viewModel()) {
    // The system file picker does the saving and opening, so the app needs no storage permission
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> if (uri != null) vm.exportBackup(uri) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) vm.importBackup(uri) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row { OutlinedButton(onClick = onBack) { Text("Back") } }

            Text("Settings", style = MaterialTheme.typography.headlineMedium)
            Text("Backup", style = MaterialTheme.typography.titleMedium)

            SettingsCard(
                title = "Export backup",
                text = "Saves all recipes, categories and images into one .zip file. " +
                    "The recipes are stored as readable JSON inside it.",
                button = "Export",
                enabled = !vm.busy
            ) {
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                exportLauncher.launch("recipebox-backup-$date.zip")
            }

            SettingsCard(
                title = "Import backup",
                text = "Adds the recipes from a backup file. Recipes that are already in the app " +
                    "are skipped, and nothing is deleted.",
                button = "Choose file",
                enabled = !vm.busy
            ) {
                importLauncher.launch(arrayOf("*/*"))
            }

            if (vm.busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    "Working…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            vm.message?.let { message ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(message, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    text: String,
    button: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    OutlinedCard(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onClick, enabled = enabled) { Text(button) }
        }
    }
}
