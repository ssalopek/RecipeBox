package com.example.recipebox.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {
    @Query("SELECT * FROM recipes ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Recipe>>

    @Query("SELECT * FROM recipes WHERE id = :id")
    fun observeById(id: Long): Flow<Recipe?>

    @Query("SELECT * FROM recipes WHERE sourceUrl = :url LIMIT 1")
    suspend fun findBySourceUrl(url: String): Recipe?

    @Insert
    suspend fun insert(recipe: Recipe): Long

    @Update
    suspend fun update(recipe: Recipe)

    @Delete
    suspend fun delete(recipe: Recipe)

    // ---------- Categories ----------

    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE")
    fun observeCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findCategoryByName(name: String): Category?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategory(category: Category): Long

    @Query("SELECT * FROM recipe_categories")
    fun observeLinks(): Flow<List<RecipeCategory>>

    @Query(
        "SELECT c.* FROM categories c " +
                "INNER JOIN recipe_categories rc ON rc.categoryId = c.id " +
                "WHERE rc.recipeId = :recipeId ORDER BY c.name COLLATE NOCASE"
    )
    fun observeCategoriesOf(recipeId: Long): Flow<List<Category>>

    @Query("DELETE FROM recipe_categories WHERE recipeId = :recipeId")
    suspend fun clearLinks(recipeId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLinks(links: List<RecipeCategory>)

    @Query("UPDATE categories SET name = :name WHERE id = :id")
    suspend fun renameCategory(id: Long, name: String)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategory(id: Long)
}