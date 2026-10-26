package com.example.recipebox.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recipes",
    indices = [Index(value = ["sourceUrl"], unique = true)]
)
data class Recipe(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val bodyText: String,
    val sourceUrl: String? = null,
    val sourcePlatform: String? = null,
    val imagePaths: String = "",
    val createdAt: Long = System.currentTimeMillis()
)