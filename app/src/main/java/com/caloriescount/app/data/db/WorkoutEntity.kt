package com.caloriescount.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A logged workout. Its [caloriesBurned] are added back to the day's calorie allowance
 * and [proteinBonusG] to the day's protein goal, so logging an intense session
 * recalibrates the remaining targets for that day.
 */
@Entity(tableName = "workouts", indices = [Index("timestamp")])
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val name: String,
    val durationMin: Int,
    /** [com.caloriescount.app.data.model.WorkoutIntensity] name. */
    val intensity: String,
    val caloriesBurned: Double,
    val proteinBonusG: Double
)
