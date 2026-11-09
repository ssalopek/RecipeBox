package com.example.recipebox

import android.app.Application
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.recipebox.data.AppDatabase
import com.example.recipebox.data.Category
import com.example.recipebox.data.ImageStore
import com.example.recipebox.data.Platform
import com.example.recipebox.data.Recipe
import com.example.recipebox.data.RecipeCategory
import com.example.recipebox.data.RecipeExtractor
import com.example.recipebox.data.UrlTools
import com.example.recipebox.data.imageFileNames
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class RecipeViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val dao = db.recipeDao()

    private fun toast(message: String) =
        Toast.makeText(getApplication<Application>(), message, Toast.LENGTH_LONG).show()

    /** Runs a database job without letting an unexpected error crash the app. */
    private fun launchSafely(errorMessage: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast(errorMessage)
            }
        }
    }

    // ---------- Categories ----------

    val categories: StateFlow<List<Category>> = dao.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedCategory = MutableStateFlow<Long?>(null)
    val selectedCategory: StateFlow<Long?> = _selectedCategory.asStateFlow()
    fun selectCategory(id: Long?) { _selectedCategory.value = id }

    fun observeCategoriesOf(recipeId: Long): Flow<List<Category>> =
        dao.observeCategoriesOf(recipeId)

    /** Calls onResult(newId), or onResult(null) if the name is empty, taken, or saving failed. */
    fun createCategory(name: String, onResult: (Long?) -> Unit = {}) {
        val clean = name.trim()
        if (clean.isEmpty()) {
            onResult(null)
            return
        }
        viewModelScope.launch {
            val id = try {
                if (dao.findCategoryByName(clean) != null) null
                else dao.insertCategory(Category(name = clean)).takeIf { it != -1L }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            onResult(id)
        }
    }

    /** Calls onResult(false) if the name is empty, taken by another category, or saving failed. */
    fun renameCategory(id: Long, name: String, onResult: (Boolean) -> Unit) {
        val clean = name.trim()
        if (clean.isEmpty()) {
            onResult(false)
            return
        }
        viewModelScope.launch {
            val ok = try {
                val clash = dao.findCategoryByName(clean)
                if (clash != null && clash.id != id) {
                    false
                } else {
                    dao.renameCategory(id, clean)
                    true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            onResult(ok)
        }
    }

    fun deleteCategory(id: Long) {
        launchSafely("Couldn't delete the category") {
            dao.deleteCategory(id)
            if (_selectedCategory.value == id) _selectedCategory.value = null
        }
    }

    /** Drops category IDs that no longer exist, so a deleted category can't break a save. */
    private suspend fun validCategoryIds(ids: Set<Long>): Set<Long> {
        if (ids.isEmpty()) return ids
        val existing = dao.categoryIds().toSet()
        return ids.filter { it in existing }.toSet()
    }

    private suspend fun setCategories(recipeId: Long, ids: Set<Long>) {
        db.withTransaction {
            dao.clearLinks(recipeId)
            dao.insertLinks(validCategoryIds(ids).map { RecipeCategory(recipeId, it) })
        }
    }

    /** Inserts the recipe and its category links together, or neither. */
    private suspend fun insertWithCategories(recipe: Recipe, categoryIds: Set<Long>): Long =
        db.withTransaction {
            val id = dao.insert(recipe)
            dao.insertLinks(validCategoryIds(categoryIds).map { RecipeCategory(id, it) })
            id
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

    // ---------- Import ----------

    /** Only a signal for the navigation to open the import screen. The data is in importDraft. */
    private val _sharedText = MutableStateFlow<String?>(null)
    val sharedText: StateFlow<String?> = _sharedText.asStateFlow()
    fun consumeShared() { _sharedText.value = null }

    var importDraft by mutableStateOf<ImportDraft?>(null)
        private set
    private var importJob: Job? = null

    /** Called when another app shares text or a link to this one. */
    fun onShared(text: String?) {
        if (text == null) return
        startImport(text)
        _sharedText.value = text
    }

    private fun startImport(text: String) {
        importJob?.cancel()

        val rawUrl = UrlTools.extractUrl(text)
        val url = rawUrl?.let { UrlTools.normalize(it) }
        val platform = url?.let { UrlTools.identifyPlatform(it) }

        val draft = ImportDraft(text, url, platform)
        draft.body = if (rawUrl == null) text else text.replace(rawUrl, "").trim()
        importDraft = draft
        if (url == null) return

        importJob = viewModelScope.launch {
            try {
                val existing = dao.findBySourceUrl(url)
                if (existing != null) {
                    draft.duplicateId = existing.id
                    draft.loading = false
                    return@launch
                }

                val result = RecipeExtractor.extract(url, platform ?: Platform.OTHER)
                if (result != null) {
                    draft.title = result.title
                    draft.body = result.body.ifBlank { draft.body }
                    draft.image = result.image
                    draft.note = if (result.body.isBlank()) {
                        "Only the title was available. Paste the caption or recipe text below."
                    } else {
                        "Check the text below and fix anything that looks off before saving."
                    }
                } else {
                    draft.note =
                        "Couldn't read this link automatically. Paste the caption or recipe text below."
                }
                draft.loading = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                draft.note = "Couldn't read this link. Paste the caption or recipe text below."
                draft.loading = false
            }
        }
    }

    /** Leaves the import screen: forgets the draft and stops any fetch still running. */
    fun finishImport() {
        importJob?.cancel()
        importJob = null
        importDraft = null
    }

    /** Downloads the image, saves the recipe with its categories, then calls onDone. */
    fun saveImport(onDone: () -> Unit) {
        val draft = importDraft ?: return
        if (draft.saving) return
        draft.saving = true

        viewModelScope.launch {
            var imageName = ""
            try {
                if (draft.url == null || dao.findBySourceUrl(draft.url) == null) {
                    val imageUrl = draft.image
                    if (imageUrl != null) {
                        imageName = ImageStore.save(getApplication(), imageUrl).orEmpty()
                        if (imageName.isEmpty()) {
                            toast("The image couldn't be downloaded. Saving without it.")
                        }
                    }
                    insertWithCategories(
                        Recipe(
                            title = draft.title.trim(),
                            bodyText = draft.body,
                            sourceUrl = draft.url,
                            sourcePlatform = draft.platform?.name,
                            imageName = imageName
                        ),
                        draft.selected
                    )
                }
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Don't leave an orphaned image file behind
                if (imageName.isNotEmpty()) ImageStore.deleteAll(getApplication(), listOf(imageName))
                draft.saving = false
                draft.note = "Couldn't save the recipe. Please try again."
            }
        }
    }

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
        launchSafely("Couldn't save the recipe") {
            if (existing == null) {
                insertWithCategories(
                    Recipe(title = title.trim(), bodyText = body),
                    categoryIds
                )
            } else {
                db.withTransaction {
                    dao.update(
                        existing.copy(
                            title = title.trim(),
                            bodyText = body,
                            imageName = if (removeImage) "" else existing.imageName
                        )
                    )
                    setCategories(existing.id, categoryIds)
                }
                if (removeImage) {
                    ImageStore.deleteAll(getApplication(), existing.imageFileNames)
                }
            }
        }
    }

    fun delete(recipe: Recipe) {
        launchSafely("Couldn't delete the recipe") {
            dao.delete(recipe)
            ImageStore.deleteAll(getApplication(), recipe.imageFileNames)
        }
    }

    // ---------- Debug tools (see DebugTools.kt) ----------

    /** Every recipe, with its category names, plus all categories, as pretty-printed JSON. */
    suspend fun debugDatabaseJson(): String {
        val recipes = dao.observeAll().first()
        val categories = dao.observeCategories().first()
        val links = dao.observeLinks().first()
        val categoryNames = categories.associate { it.id to it.name }

        val recipeArray = JSONArray()
        recipes.forEach { r ->
            recipeArray.put(
                JSONObject().apply {
                    put("id", r.id)
                    put("title", r.title)
                    put("bodyText", r.bodyText)
                    put("sourceUrl", r.sourceUrl ?: JSONObject.NULL)
                    put("sourcePlatform", r.sourcePlatform ?: JSONObject.NULL)
                    put("imageName", r.imageName)
                    put("createdAt", r.createdAt)
                    put(
                        "categories",
                        JSONArray(
                            links.filter { it.recipeId == r.id }
                                .mapNotNull { categoryNames[it.categoryId] }
                        )
                    )
                }
            )
        }
        val root = JSONObject()
        root.put("recipes", recipeArray)
        root.put("categories", JSONArray(categories.map { it.name }))
        // org.json writes "/" as "\/", which makes links hard to read
        return root.toString(2).replace("\\/", "/")
    }

    /** Deletes every recipe, category and saved image. */
    fun debugDeleteEverything() {
        launchSafely("Couldn't delete the database") {
            withContext(Dispatchers.IO) { db.clearAllTables() }
            ImageStore.deleteAllFiles(getApplication())
            _selectedCategory.value = null
            _query.value = ""
        }
    }
}
