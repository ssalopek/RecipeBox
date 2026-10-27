package com.example.recipebox

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.example.recipebox.data.Category

/** onCreate gets the name and a callback: call it with true to close the dialog, false to show the error. */
@Composable
fun NewCategoryDialog(
    onDismiss: () -> Unit,
    onCreate: (String, (Boolean) -> Unit) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New category") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    error = null
                },
                label = { Text("Name") },
                singleLine = true,
                isError = error != null,
                supportingText = { error?.let { Text(it) } }
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onCreate(name.trim()) { ok ->
                        if (ok) onDismiss()
                        else error = "A category with this name already exists"
                    }
                }
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * Tap chips to assign or unassign categories. Long-press a chip to rename or delete it.
 * "+ New category" creates one and selects it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryPicker(
    vm: RecipeViewModel,
    selected: Set<Long>,
    onSelectedChange: (Set<Long>) -> Unit,
    modifier: Modifier = Modifier
) {
    val categories by vm.categories.collectAsState()
    var showNew by remember { mutableStateOf(false) }
    var managing by remember { mutableStateOf<Category?>(null) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Categories", style = MaterialTheme.typography.labelLarge)
        if (categories.isNotEmpty()) {
            Text(
                "Hold a category to rename or delete it",
                style = MaterialTheme.typography.bodySmall
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { category ->
                CategoryChip(
                    label = category.name,
                    selected = category.id in selected,
                    onClick = {
                        onSelectedChange(
                            if (category.id in selected) selected - category.id
                            else selected + category.id
                        )
                    },
                    onLongClick = { managing = category }
                )
            }
            AssistChip(onClick = { showNew = true }, label = { Text("+ New category") })
        }
    }

    if (showNew) {
        NewCategoryDialog(
            onDismiss = { showNew = false },
            onCreate = { name, done ->
                vm.createCategory(name) { newId ->
                    if (newId != null) onSelectedChange(selected + newId)
                    done(newId != null)
                }
            }
        )
    }

    managing?.let { category ->
        ManageCategoryDialog(
            category = category,
            onDismiss = { managing = null },
            onRename = { name, onResult -> vm.renameCategory(category.id, name, onResult) },
            onDelete = {
                vm.deleteCategory(category.id)
                onSelectedChange(selected - category.id)
            }
        )
    }
}

/** Read-only category tags for the reading view. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryTags(categories: List<Category>, modifier: Modifier = Modifier) {
    if (categories.isEmpty()) return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        categories.forEach { category ->
            SuggestionChip(onClick = {}, label = { Text(category.name) })
        }
    }
}

/** A filter-style chip that also supports long-press. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(8.dp)
    Surface(
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surface,
        border = if (selected) null
        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .clip(shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun ManageCategoryDialog(
    category: Category,
    onDismiss: () -> Unit,
    onRename: (String, (Boolean) -> Unit) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(category.name) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete \"${category.name}\"?") },
            text = { Text("Recipes in this category are kept. They just won't be in it anymore.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    onDismiss()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Back") }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Edit category") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = null
                    },
                    label = { Text("Name") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        onRename(name) { ok ->
                            if (ok) onDismiss()
                            else error = "A category with this name already exists"
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirmDelete = true }) { Text("Delete") }
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
            }
        )
    }
}