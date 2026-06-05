package com.caloriescount.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies the MET-based calories-burned estimate that feeds the daily recalibration,
 * and the per-tier protein bonus.
 */
class WorkoutIntensityTest {

    @Test
    fun intense_oneHour_at80kg_burns720() {
        // 9.0 MET × 80 kg × 1.0 h = 720
        assertEquals(720.0, WorkoutIntensity.INTENSE.caloriesBurned(weightKg = 80.0, durationMin = 60), 0.001)
    }

    @Test
    fun moderate_halfHour_at80kg_burns240() {
        // 6.0 MET × 80 kg × 0.5 h = 240
        assertEquals(240.0, WorkoutIntensity.MODERATE.caloriesBurned(weightKg = 80.0, durationMin = 30), 0.001)
    }

    @Test
    fun light_scalesWithDurationAndWeight() {
        // 3.5 MET × 70 kg × (45/60) h = 183.75
        assertEquals(183.75, WorkoutIntensity.LIGHT.caloriesBurned(weightKg = 70.0, durationMin = 45), 0.001)
    }

    @Test
    fun estimate_roundsToWholeCalories() {
        assertEquals(184, WorkoutIntensity.estimate(weightKg = 70.0, durationMin = 45, intensity = WorkoutIntensity.LIGHT))
    }

    @Test
    fun proteinBonus_scalesWithIntensity() {
        assertEquals(0.0, WorkoutIntensity.LIGHT.proteinBonusG, 0.0)
        assertEquals(10.0, WorkoutIntensity.MODERATE.proteinBonusG, 0.0)
        assertEquals(25.0, WorkoutIntensity.INTENSE.proteinBonusG, 0.0)
    }

    @Test
    fun zeroDuration_burnsNothing() {
        assertEquals(0.0, WorkoutIntensity.INTENSE.caloriesBurned(weightKg = 80.0, durationMin = 0), 0.001)
    }
}
