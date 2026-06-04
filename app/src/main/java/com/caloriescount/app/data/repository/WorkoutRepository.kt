package com.caloriescount.app.data.repository

import com.caloriescount.app.data.db.WorkoutDao
import com.caloriescount.app.data.db.WorkoutEntity
import com.caloriescount.app.data.model.WorkoutIntensity
import kotlinx.coroutines.flow.Flow

class WorkoutRepository(private val dao: WorkoutDao) {

    fun observeAll(): Flow<List<WorkoutEntity>> = dao.observeAll()

    /**
     * Log a workout. Calories burned are estimated from intensity (MET), the user's
     * bodyweight and duration; the protein bonus comes from the intensity tier.
     */
    suspend fun addWorkout(
        name: String,
        durationMin: Int,
        intensity: WorkoutIntensity,
        weightKg: Double
    ): Long {
        val workout = WorkoutEntity(
            timestamp = System.currentTimeMillis(),
            name = name.ifBlank { intensity.label + " workout" },
            durationMin = durationMin,
            intensity = intensity.name,
            caloriesBurned = intensity.caloriesBurned(weightKg, durationMin),
            proteinBonusG = intensity.proteinBonusG
        )
        return dao.insert(workout)
    }

    suspend fun delete(id: Long) = dao.deleteById(id)
}
