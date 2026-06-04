package com.caloriescount.app.data.model

import kotlin.math.roundToInt

/**
 * Workout intensity → MET value (for the calories-burned estimate) and a protein
 * bonus added to the day's protein goal to support recovery.
 *
 * Calories burned ≈ MET × bodyweight(kg) × hours.
 */
enum class WorkoutIntensity(
    val label: String,
    val met: Double,
    val proteinBonusG: Double
) {
    LIGHT("Light", 3.5, 0.0),
    MODERATE("Moderate", 6.0, 10.0),
    INTENSE("Intense", 9.0, 25.0);

    fun caloriesBurned(weightKg: Double, durationMin: Int): Double {
        val hours = durationMin / 60.0
        return (met * weightKg * hours)
    }

    companion object {
        fun estimate(weightKg: Double, durationMin: Int, intensity: WorkoutIntensity): Int =
            intensity.caloriesBurned(weightKg, durationMin).roundToInt()
    }
}
