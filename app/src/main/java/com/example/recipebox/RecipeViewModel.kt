package com.example.recipebox

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.recipebox.data.AppDatabase
import com.example.recipebox.data.Recipe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.recipebox.data.UrlTools
import kotlinx.coroutines.flow.combine
import com.example.recipebox.data.ImageStore

class RecipeViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).recipeDao()
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    fun setQuery(q: String) { _query.value = q }

    val recipes = combine(dao.observeAll(), _query) { all, q ->
        val needle = q.trim()
        if (needle.isEmpty()) all
        else all.filter { r ->
            r.title.contains(needle, ignoreCase = true) ||
                    r.bodyText.contains(needle, ignoreCase = true) ||
                    (r.sourceUrl?.let { UrlTools.sourceLabel(it).contains(needle, ignoreCase = true) } ?: false)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _sharedText = MutableStateFlow<String?>(null)
    val sharedText: StateFlow<String?> = _sharedText.asStateFlow()

    fun onShared(text: String?) { _sharedText.value = text }
    fun consumeShared() { _sharedText.value = null }

    fun observe(id: Long): Flow<Recipe?> = dao.observeById(id)

    suspend fun findBySourceUrl(url: String): Recipe? = dao.findBySourceUrl(url)

    fun save(existing: Recipe?, title: String, body: String, removeImage: Boolean = false) {
        viewModelScope.launch {
            if (existing == null) {
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
            }
        }
    }

    fun saveImported(
        title: String,
        body: String,
        sourceUrl: String?,
        platform: String?,
        imageUrl: String?,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            if (sourceUrl == null || dao.findBySourceUrl(sourceUrl) == null) {
                val imageName = imageUrl?.let { ImageStore.save(getApplication(), it) }.orEmpty()
                dao.insert(
                    Recipe(
                        title = title.trim(),
                        bodyText = body,
                        sourceUrl = sourceUrl,
                        sourcePlatform = platform,
                        imageName = imageName
                    )
                )
            }
            onDone()
        }
    }

    fun delete(recipe: Recipe) {
        viewModelScope.launch {
            dao.delete(recipe)
            ImageStore.deleteAll(getApplication(), recipe.imageName.lines().filter { it.isNotBlank() })
        }
    }
}