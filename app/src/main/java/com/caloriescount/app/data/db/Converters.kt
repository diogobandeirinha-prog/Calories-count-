package com.caloriescount.app.data.db

import androidx.room.TypeConverter
import com.caloriescount.app.data.model.FoodItem
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    @TypeConverter
    fun fromFoodItems(items: List<FoodItem>): String = json.encodeToString(items)

    @TypeConverter
    fun toFoodItems(value: String): List<FoodItem> =
        if (value.isBlank()) emptyList() else json.decodeFromString(value)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
    }
}
