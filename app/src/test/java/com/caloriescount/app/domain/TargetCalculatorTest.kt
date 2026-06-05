package com.caloriescount.app.domain

import com.caloriescount.app.data.model.ActivityLevel
import com.caloriescount.app.data.model.FitnessGoal
import com.caloriescount.app.data.model.Sex
import com.caloriescount.app.data.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies the Mifflin-St Jeor baseline target maths.
 *
 * Reference profile: 80 kg, 180 cm, 30 yrs.
 *   BMR(male)   = 10·80 + 6.25·180 − 5·30 + 5   = 1780
 *   BMR(female) = 10·80 + 6.25·180 − 5·30 − 161 = 1614
 *   TDEE(moderate ×1.55) on the male BMR        = 2759
 */
class TargetCalculatorTest {

    private val base = UserProfile(
        sex = Sex.MALE,
        age = 30,
        heightCm = 180.0,
        weightKg = 80.0,
        activityLevel = ActivityLevel.MODERATE,
        goal = FitnessGoal.MAINTENANCE
    )

    @Test
    fun bmr_male_usesPlus5Constant() {
        assertEquals(1780.0, TargetCalculator.basalMetabolicRate(base), 0.001)
    }

    @Test
    fun bmr_female_usesMinus161Constant() {
        val female = base.copy(sex = Sex.FEMALE)
        assertEquals(1614.0, TargetCalculator.basalMetabolicRate(female), 0.001)
    }

    @Test
    fun tdee_appliesActivityMultiplier() {
        val targets = TargetCalculator.compute(base)
        assertEquals(1780.0, targets.bmr, 0.001)
        assertEquals(1780.0 * 1.55, targets.tdee, 0.001) // 2759.0
    }

    @Test
    fun maintenance_keepsTdeeAnd1_6gPerKgProtein() {
        val targets = TargetCalculator.compute(base)
        assertEquals(2759.0, targets.calorieTarget, 0.001)
        assertEquals(128.0, targets.proteinTarget, 0.001) // 1.6 × 80
        assertEquals(2760, targets.calorieTargetRounded)
        assertEquals(128, targets.proteinTargetRounded)
    }

    @Test
    fun aggressiveFatLoss_appliesDeficitAndHighProtein() {
        val targets = TargetCalculator.compute(base.copy(goal = FitnessGoal.AGGRESSIVE_FAT_LOSS))
        assertEquals(2759.0 * 0.75, targets.calorieTarget, 0.001) // 2069.25
        assertEquals(176.0, targets.proteinTarget, 0.001)          // 2.2 × 80
        assertEquals(2070, targets.calorieTargetRounded)
        assertEquals(176, targets.proteinTargetRounded)
    }

    @Test
    fun leanMuscleGain_appliesSurplus() {
        val targets = TargetCalculator.compute(base.copy(goal = FitnessGoal.LEAN_MUSCLE_GAIN))
        assertEquals(2759.0 * 1.10, targets.calorieTarget, 0.001) // 3034.9
        assertEquals(160.0, targets.proteinTarget, 0.001)          // 2.0 × 80
        assertEquals(3030, targets.calorieTargetRounded)
        assertEquals(160, targets.proteinTargetRounded)
    }

    @Test
    fun sedentaryActivity_lowersTdeeBelowModerate() {
        val sedentary = TargetCalculator.compute(base.copy(activityLevel = ActivityLevel.SEDENTARY))
        assertEquals(1780.0 * 1.2, sedentary.tdee, 0.001) // 2136.0
    }

    @Test
    fun calorieRounding_isToNearestTen() {
        // 1614(female BMR) × 1.2(sedentary) = 1936.8 → nearest 10 = 1940
        val profile = base.copy(sex = Sex.FEMALE, activityLevel = ActivityLevel.SEDENTARY)
        val targets = TargetCalculator.compute(profile)
        assertEquals(1936.8, targets.calorieTarget, 0.001)
        assertEquals(1940, targets.calorieTargetRounded)
    }
}
