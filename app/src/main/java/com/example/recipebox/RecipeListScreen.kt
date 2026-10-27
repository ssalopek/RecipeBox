package com.example.recipebox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.recipebox.data.ImageStore
import com.example.recipebox.data.coverImage
import com.example.recipebox.data.Category
@Composable
fun RecipeListScreen(
    vm: RecipeViewModel,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit
) {
    val context = LocalContext.current
    val recipes by vm.recipes.collectAsState()
    val query by vm.query.collectAsState()
    val categories by vm.categories.collectAsState()
    val selectedCategory by vm.selectedCategory.collectAsState()
    var showNewCategory by remember { mutableStateOf(false) }
    var managing by remember { mutableStateOf<Category?>(null) }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAdd) { Text("Add recipe") }
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { vm.selectCategory(null) },
                        label = { Text("All") }
                    )
                }
                items(categories, key = { it.id }) { category ->
                    CategoryChip(
                        label = category.name,
                        selected = selectedCategory == category.id,
                        onClick = {
                            vm.selectCategory(if (selectedCategory == category.id) null else category.id)
                        },
                        onLongClick = { managing = category }
                    )
                }
                item {
                    AssistChip(
                        onClick = { showNewCategory = true },
                        label = { Text("+ New category") }
                    )
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = vm::setQuery,
                label = { Text("Search recipes") },
                singleLine = true,
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        TextButton(onClick = { vm.setQuery("") }) { Text("Clear") }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            )

            if (recipes.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        when {
                            query.isNotBlank() -> "No recipes match \"$query\"."
                            selectedCategory != null -> "No recipes in this category yet."
                            else -> "No recipes yet. Tap Add recipe to start."
                        }
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(recipes, key = { it.id }) { recipe ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { onOpen(recipe.id) }
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                recipe.coverImage?.let { name ->
                                    AsyncImage(
                                        model = ImageStore.file(context, name),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        recipe.title,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    recipe.sourceUrl?.let { SourceTag(it) }
                                    if (recipe.bodyText.isNotBlank()) {
                                        Text(
                                            recipe.bodyText,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodyMedium
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