package com.caloriescount.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.caloriescount.app.data.model.FoodItem

/**
 * One logged meal/photo. Totals are stored denormalised so range queries stay cheap,
 * while [items] preserves the per-component breakdown for the detail view.
 */
@Entity(tableName = "food_entries")
data class FoodEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Epoch millis of when the meal was logged (used for day/week/month bucketing). */
    val timestamp: Long,
    val mealName: String,
    val totalCalories: Double,
    val totalProteinG: Double,
    val items: List<FoodItem>,
    val notes: String = "",
    /** Local file path or content uri of the saved photo, if any. */
    val photoPath: String? = null
)
