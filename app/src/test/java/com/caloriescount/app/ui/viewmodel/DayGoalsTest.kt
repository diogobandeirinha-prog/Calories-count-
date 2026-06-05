package com.caloriescount.app.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the dynamic daily recalibration: logging a workout adds its burned calories
 * back to the allowance and its protein bonus to the protein goal, and the "remaining"
 * figures follow.
 */
class DayGoalsTest {

    private fun goals(
        consumedCalories: Double = 0.0,
        consumedProtein: Double = 0.0,
        baseCalorieGoal: Double = 2000.0,
        baseProteinGoal: Double = 150.0,
        exerciseCalories: Double = 0.0,
        exerciseProteinBonus: Double = 0.0
    ) = DayGoals(
        consumedCalories = consumedCalories,
        consumedProtein = consumedProtein,
        baseCalorieGoal = baseCalorieGoal,
        baseProteinGoal = baseProteinGoal,
        exerciseCalories = exerciseCalories,
        exerciseProteinBonus = exerciseProteinBonus
    )

    @Test
    fun noWorkout_goalsEqualBaseline_andNotBoosted() {
        val g = goals(consumedCalories = 1500.0, consumedProtein = 100.0)
        assertEquals(2000.0, g.calorieGoal, 0.0)
        assertEquals(150.0, g.proteinGoal, 0.0)
        assertEquals(500.0, g.remainingCalories, 0.0)
        assertEquals(50.0, g.remainingProtein, 0.0)
        assertFalse(g.boostedByExercise)
    }

    @Test
    fun intenseWorkout_addsCaloriesBackAndBumpsProtein() {
        // 500 kcal burned + 25 g protein bonus from an intense session.
        val g = goals(
            consumedCalories = 1500.0,
            consumedProtein = 100.0,
            exerciseCalories = 500.0,
            exerciseProteinBonus = 25.0
        )
        assertEquals(2500.0, g.calorieGoal, 0.0)          // 2000 + 500
        assertEquals(175.0, g.proteinGoal, 0.0)           // 150 + 25
        assertEquals(1000.0, g.remainingCalories, 0.0)    // 2500 − 1500
        assertEquals(75.0, g.remainingProtein, 0.0)       // 175 − 100
        assertTrue(g.boostedByExercise)
    }

    @Test
    fun overEating_yieldsNegativeRemaining() {
        val g = goals(consumedCalories = 2600.0, consumedProtein = 180.0)
        assertEquals(-600.0, g.remainingCalories, 0.0)    // over by 600
        assertEquals(-30.0, g.remainingProtein, 0.0)
    }

    @Test
    fun calorieOnlyBurn_stillCountsAsBoosted() {
        val g = goals(exerciseCalories = 300.0, exerciseProteinBonus = 0.0)
        assertTrue(g.boostedByExercise)
        assertEquals(2300.0, g.calorieGoal, 0.0)
        assertEquals(150.0, g.proteinGoal, 0.0)
    }

    @Test
    fun proteinBonusOnly_countsAsBoosted() {
        val g = goals(exerciseProteinBonus = 10.0)
        assertTrue(g.boostedByExercise)
        assertEquals(160.0, g.proteinGoal, 0.0)
    }

    @Test
    fun multipleWorkouts_sumIntoRemaining() {
        // Caller sums today's workouts before constructing DayGoals; verify the arithmetic.
        val totalBurned = 250.0 + 500.0
        val totalBonus = 10.0 + 25.0
        val g = goals(
            consumedCalories = 1800.0,
            consumedProtein = 120.0,
            exerciseCalories = totalBurned,
            exerciseProteinBonus = totalBonus
        )
        assertEquals(2750.0, g.calorieGoal, 0.0)          // 2000 + 750
        assertEquals(950.0, g.remainingCalories, 0.0)     // 2750 − 1800
        assertEquals(185.0, g.proteinGoal, 0.0)           // 150 + 35
        assertEquals(65.0, g.remainingProtein, 0.0)       // 185 − 120
    }
}
