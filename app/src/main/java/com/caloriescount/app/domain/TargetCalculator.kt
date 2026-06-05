package com.caloriescount.app.domain

import com.caloriescount.app.data.model.Sex
import com.caloriescount.app.data.model.UserProfile
import kotlin.math.roundToInt

/** Baseline daily targets derived from a [UserProfile]. */
data class NutritionTargets(
    val bmr: Double,
    val tdee: Double,
    val calorieTarget: Double,
    val proteinTarget: Double
) {
    val calorieTargetRounded: Int get() = (calorieTarget / 10).roundToInt() * 10
    val proteinTargetRounded: Int get() = proteinTarget.roundToInt()
}

/**
 * Computes baseline targets using the **Mifflin-St Jeor** equation:
 *
 *   BMR = 10·kg + 6.25·cm − 5·age + s   (s = +5 for male, −161 for female)
 *   TDEE = BMR × activity multiplier
 *   calorie target = TDEE × goal factor
 *   protein target = bodyweight(kg) × goal protein-per-kg
 */
object TargetCalculator {

    fun basalMetabolicRate(profile: UserProfile): Double {
        val sexConstant = if (profile.sex == Sex.MALE) 5.0 else -161.0
        return 10.0 * profile.weightKg +
            6.25 * profile.heightCm -
            5.0 * profile.age +
            sexConstant
    }

    fun compute(profile: UserProfile): NutritionTargets {
        val bmr = basalMetabolicRate(profile)
        val tdee = bmr * profile.activityLevel.multiplier
        val calories = tdee * profile.goal.calorieFactor
        val protein = profile.weightKg * profile.goal.proteinPerKg
        return NutritionTargets(
            bmr = bmr,
            tdee = tdee,
            calorieTarget = calories,
            proteinTarget = protein
        )
    }
}
