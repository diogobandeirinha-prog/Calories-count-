package com.caloriescount.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Locally-cached frequent/favorite food. One row per distinct food name (case-insensitive).
 *
 * [useCount] is bumped every time the food is logged, so the UI can surface "frequent"
 * foods; [pinned] lets the user mark explicit favorites that always sort first.
 * Macros are kept fresh from the most recent log so a quick re-log is accurate.
 */
@Entity(
    tableName = "favorite_foods",
    indices = [Index(value = ["nameKey"], unique = true)]
)
data class FavoriteFoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Lower-cased name used for de-duplication. */
    val nameKey: String,
    val name: String,
    val quantity: String = "",
    val weightGrams: Double = 0.0,
    val calories: Double = 0.0,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatsG: Double = 0.0,
    val useCount: Int = 0,
    val lastUsedAt: Long = 0L,
    val pinned: Boolean = false
)
