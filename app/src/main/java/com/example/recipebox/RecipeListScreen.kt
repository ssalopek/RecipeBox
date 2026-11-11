package com.example.recipebox

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.recipebox.data.Category
import com.example.recipebox.data.ImageStore
import com.example.recipebox.data.coverImage
import com.example.recipebox.ui.theme.recipeFieldColors

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecipeListScreen(
    vm: RecipeViewModel,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    onEdit: (Long) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val recipes by vm.recipes.collectAsState()
    val query by vm.query.collectAsState()
    val categories by vm.categories.collectAsState()
    val selectedCategory by vm.selectedCategory.collectAsState()
    var showNewCategory by remember { mutableStateOf(false) }
    var managing by remember { mutableStateOf<Category?>(null) }

    // Selection mode: long-press a recipe to start, then tap recipes to check or uncheck them
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable(stateSaver = LongSetSaver) {
        mutableStateOf(emptySet<Long>())
    }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    // Only recipes that are on screen can be edited or deleted
    val selectedRecipes = remember(recipes, selected) { recipes.filter { it.id in selected } }
    val allSelected = recipes.isNotEmpty() && selectedRecipes.size == recipes.size

    fun exitSelection() {
        selecting = false
        selected = emptySet()
        confirmDelete = false
    }

    // Back leaves selection mode instead of closing the app
    BackHandler(enabled = selecting) { exitSelection() }

    Scaffold(
        floatingActionButton = {
            if (!selecting) {
                ExtendedFloatingActionButton(
                    onClick = onAdd,
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                    shape = RoundedCornerShape(16.dp)
                ) { Text("+ Add recipe", fontWeight = FontWeight.SemiBold) }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            DebugBar(vm)

            if (selecting) {
                SelectionBar(
                    count = selectedRecipes.size,
                    allSelected = allSelected,
                    onToggleAll = {
                        selected = if (allSelected) emptySet() else recipes.map { it.id }.toSet()
                    },
                    canEdit = selectedRecipes.size == 1,
                    onEdit = {
                        val id = selectedRecipes.single().id
                        exitSelection()
                        onEdit(id)
                    },
                    canDelete = selectedRecipes.isNotEmpty(),
                    onDelete = { confirmDelete = true },
                    onClose = { exitSelection() }
                )
            } else {
                Text(
                    "Recipes",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp)
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        CategoryChip(
                            label = "All",
                            selected = selectedCategory == null,
                            onClick = { vm.selectCategory(null) }
                        )
                    }
                    items(categories, key = { it.id }) { category ->
                        CategoryChip(
                            label = category.name,
                            selected = selectedCategory == category.id,
                            onClick = {
                                vm.selectCategory(
                                    if (selectedCategory == category.id) null else category.id
                                )
                            },
                            onLongClick = { managing = category }
                        )
                    }
                    item {
                        CategoryChip(
                            label = "+ New",
                            selected = false,
                            onClick = { showNewCategory = true },
                            accent = true
                        )
                    }
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = vm::setQuery,
                    placeholder = { Text("Search recipes") },
                    singleLine = true,
                    shape = CircleShape,
                    colors = recipeFieldColors(),
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            TextButton(onClick = { vm.setQuery("") }) { Text("Clear") }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp)
                        .semantics { contentDescription = "Search recipes" }
                )
            }

            if (recipes.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        when {
                            query.isNotBlank() -> "No recipes match \"$query\"."
                            selectedCategory != null -> "No recipes in this category yet."
                            else -> "No recipes yet. Tap + Add recipe to start."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    // extra room at the bottom so the Add recipe button never covers the last card
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(recipes, key = { it.id }) { recipe ->
                        val isSelected = recipe.id in selected
                        OutlinedCard(
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = if (isSelected) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLowest
                                }
                            ),
                            border = BorderStroke(
                                if (isSelected) 2.dp else 1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (selecting) {
                                            selected =
                                                if (isSelected) selected - recipe.id
                                                else selected + recipe.id
                                        } else {
                                            onOpen(recipe.id)
                                        }
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (selecting) {
                                            selected =
                                                if (isSelected) selected - recipe.id
                                                else selected + recipe.id
                                        } else {
                                            selecting = true
                                            selected = setOf(recipe.id)
                                        }
                                    }
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (selecting) {
                                    Checkbox(checked = isSelected, onCheckedChange = null)
                                }
                                recipe.coverImage?.let { name ->
                                    AsyncImage(
                                        model = ImageStore.file(context, name),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                    )
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        recipe.title,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    recipe.sourceUrl?.let {
                                        SourceTag(it, Modifier.padding(top = 4.dp))
                                    }
                                    if (recipe.bodyText.isNotBlank()) {
                                        Text(
                                            recipe.bodyText,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete && selectedRecipes.isNotEmpty()) {
        val count = selectedRecipes.size
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(if (count == 1) "Delete this recipe?" else "Delete $count recipes?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteRecipes(selectedRecipes)
                        exitSelection()
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

    if (showNewCategory) {
        NewCategoryDialog(
            onDismiss = { showNewCategory = false },
            onCreate = { name, done ->
                vm.createCategory(name) { id -> done(id != null) }
            }
        )
    }

    managing?.let { category ->
        ManageCategoryDialog(
            category = category,
            onDismiss = { managing = null },
            onRename = { name, onResult -> vm.renameCategory(category.id, name, onResult) },
            onDelete = { vm.deleteCategory(category.id) }
        )
    }
}
