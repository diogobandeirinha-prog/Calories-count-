package com.caloriescount.app.data.repository

import com.caloriescount.app.data.db.FoodEntryDao
import com.caloriescount.app.data.db.FoodEntryEntity
import com.caloriescount.app.data.model.FoodItem
import kotlinx.coroutines.flow.Flow

class FoodRepository(private val dao: FoodEntryDao) {

    fun observeAll(): Flow<List<FoodEntryEntity>> = dao.observeAll()

    suspend fun getById(id: Long): FoodEntryEntity? = dao.getById(id)

    suspend fun addEntry(
        timestamp: Long,
        mealName: String,
        items: List<FoodItem>,
        notes: String,
        photoPath: String?
    ): Long {
        val entry = FoodEntryEntity(
            timestamp = timestamp,
            mealName = mealName.ifBlank { "Meal" },
            totalCalories = items.sumOf { it.calories },
            totalProteinG = items.sumOf { it.proteinG },
            totalCarbsG = items.sumOf { it.carbsG },
            totalFatsG = items.sumOf { it.fatsG },
            items = items,
            notes = notes,
            photoPath = photoPath
        )
        return dao.insert(entry)
    }

    suspend fun delete(entry: FoodEntryEntity) = dao.delete(entry)
}
