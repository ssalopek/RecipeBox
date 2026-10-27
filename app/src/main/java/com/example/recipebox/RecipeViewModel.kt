package com.example.recipebox

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.recipebox.data.AppDatabase
import com.example.recipebox.data.Category
import com.example.recipebox.data.ImageStore
import com.example.recipebox.data.Recipe
import com.example.recipebox.data.RecipeCategory
import com.example.recipebox.data.UrlTools
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RecipeViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val dao = db.recipeDao()

    // ---------- Categories ----------

    val categories: StateFlow<List<Category>> = dao.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedCategory = MutableStateFlow<Long?>(null)
    val selectedCategory: StateFlow<Long?> = _selectedCategory.asStateFlow()
    fun selectCategory(id: Long?) { _selectedCategory.value = id }

    fun observeCategoriesOf(recipeId: Long): Flow<List<Category>> =
        dao.observeCategoriesOf(recipeId)

    /** Calls onResult(newId), or onResult(null) if the name is empty or already taken. */
    fun createCategory(name: String, onResult: (Long?) -> Unit = {}) {
        val clean = name.trim()
        if (clean.isEmpty()) {
            onResult(null)
            return
        }
        viewModelScope.launch {
            if (dao.findCategoryByName(clean) != null) {
                onResult(null)
                return@launch
            }
            val id = dao.insertCategory(Category(name = clean))
            onResult(id.takeIf { it != -1L })
        }
    }

    private suspend fun setCategories(recipeId: Long, ids: Set<Long>) {
        db.withTransaction {
            dao.clearLinks(recipeId)
            dao.insertLinks(ids.map { RecipeCategory(recipeId, it) })
        }
    }

    /** Calls onResult(false) if another category already has that name. */
    fun renameCategory(id: Long, name: String, onResult: (Boolean) -> Unit) {
        val clean = name.trim()
        if (clean.isEmpty()) {
            onResult(false)
            return
        }
        viewModelScope.launch {
            val clash = dao.findCategoryByName(clean)
            if (clash != null && clash.id != id) {
                onResult(false)
            } else {
                dao.renameCategory(id, clean)
                onResult(true)
            }
        }
    }

    fun deleteCategory(id: Long) {
        viewModelScope.launch {
            dao.deleteCategory(id)
            if (_selectedCategory.value == id) _selectedCategory.value = null
        }
    }

    // ---------- Search and filter ----------

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    fun setQuery(q: String) { _query.value = q }

    val recipes: StateFlow<List<Recipe>> = combine(
        dao.observeAll(), _query, _selectedCategory, dao.observeLinks()
    ) { all, q, categoryId, links ->
        val inCategory = categoryId?.let { id ->
            links.filter { it.categoryId == id }.map { it.recipeId }.toSet()
        }
        val needle = q.trim()
        all.filter { r ->
            (inCategory == null || r.id in inCategory) &&
                    (needle.isEmpty() ||
                            r.title.contains(needle, ignoreCase = true) ||
                            r.bodyText.contains(needle, ignoreCase = true) ||
                            (r.sourceUrl?.let {
                                UrlTools.sourceLabel(it).contains(needle, ignoreCase = true)
                            } ?: false))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---------- Sharing ----------

    private val _sharedText = MutableStateFlow<String?>(null)
    val sharedText: StateFlow<String?> = _sharedText.asStateFlow()

    fun onShared(text: String?) { _sharedText.value = text }
    fun consumeShared() { _sharedText.value = null }

    // ---------- Recipes ----------

    fun observe(id: Long): Flow<Recipe?> = dao.observeById(id)

    suspend fun findBySourceUrl(url: String): Recipe? = dao.findBySourceUrl(url)

    fun save(
        existing: Recipe?,
        title: String,
        body: String,
        removeImage: Boolean,
        categoryIds: Set<Long>
    ) {
        viewModelScope.launch {
            val recipeId = if (existing == null) {
                dao.insert(Recipe(title = title.trim(), bodyText = body))
            } else {
                dao.update(
                    existing.copy(
                        title = title.trim(),
                        bodyText = body,
                        imageName = if (removeImage) "" else existing.imageName
                    )
                )
                if (removeImage) {
                    ImageStore.deleteAll(
                        getApplication(),
                        existing.imageName.lines().filter { it.isNotBlank() }
                    )
                }
                existing.id
            }
            setCategories(recipeId, categoryIds)
        }
    }

    fun saveImported(
        title: String,
        body: String,
        sourceUrl: String?,
        platform: String?,
        imageUrl: String?,
        categoryIds: Set<Long>,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            if (sourceUrl == null || dao.findBySourceUrl(sourceUrl) == null) {
                val imageName = imageUrl?.let { ImageStore.save(getApplication(), it) }.orEmpty()
                val recipeId = dao.insert(
                    Recipe(
                        title = title.trim(),
                        bodyText = body,
                        sourceUrl = sourceUrl,
                        sourcePlatform = platform,
                        imageName = imageName
                    )
                )
                setCategories(recipeId, categoryIds)
            }
            onDone()
        }
    }

    fun delete(recipe: Recipe) {
        viewModelScope.launch {
            dao.delete(recipe)
            ImageStore.deleteAll(
                getApplication(),
                recipe.imageName.lines().filter { it.isNotBlank() }
            )
        }
    }
}