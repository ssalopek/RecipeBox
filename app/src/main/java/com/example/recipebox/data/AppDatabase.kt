package com.example.recipebox.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Recipe::class, Category::class, RecipeCategory::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recipes ADD COLUMN imagePaths TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `categories` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`name` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_name` " +
                            "ON `categories` (`name`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recipe_categories` (" +
                            "`recipeId` INTEGER NOT NULL, " +
                            "`categoryId` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`recipeId`, `categoryId`), " +
                            "FOREIGN KEY(`recipeId`) REFERENCES `recipes`(`id`) " +
                            "ON UPDATE NO ACTION ON DELETE CASCADE, " +
                            "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) " +
                            "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_recipe_categories_categoryId` " +
                            "ON `recipe_categories` (`categoryId`)"
                )
            }
        }

        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "recipebox.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}