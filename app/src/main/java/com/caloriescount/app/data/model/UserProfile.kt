package com.caloriescount.app.data.model

/** Biological sex — drives the Mifflin-St Jeor constant (+5 male / -161 female). */
enum class Sex(val label: String) {
    MALE("Male"),
    FEMALE("Female")
}

/** Activity level → TDEE multiplier applied to BMR. */
enum class ActivityLevel(val label: String, val description: String, val multiplier: Double) {
    SEDENTARY("Sedentary", "Little or no exercise", 1.2),
    LIGHT("Lightly active", "Light exercise 1–3 days/week", 1.375),
    MODERATE("Moderately active", "Moderate exercise 3–5 days/week", 1.55),
    ACTIVE("Very active", "Hard exercise 6–7 days/week", 1.725),
    VERY_ACTIVE("Extra active", "Very hard exercise or a physical job", 1.9)
}

/**
 * Fitness goal → calorie adjustment (factor on TDEE) and protein target (g per kg bodyweight).
 * Higher protein on the fat-loss and muscle-gain goals preserves/builds lean mass.
 */
enum class FitnessGoal(
    val label: String,
    val description: String,
    val calorieFactor: Double,
    val proteinPerKg: Double
) {
    AGGRESSIVE_FAT_LOSS("Aggressive fat loss", "~25% calorie deficit, high protein", 0.75, 2.2),
    LEAN_MUSCLE_GAIN("Lean muscle gain", "~10% calorie surplus, high protein", 1.10, 2.0),
    MAINTENANCE("Weight maintenance", "Maintain your current weight", 1.0, 1.6)
}

/** The user's collected onboarding inputs. */
data class UserProfile(
    val sex: Sex = Sex.MALE,
    val age: Int = 30,
    val heightCm: Double = 175.0,
    val weightKg: Double = 75.0,
    val activityLevel: ActivityLevel = ActivityLevel.MODERATE,
    val goal: FitnessGoal = FitnessGoal.MAINTENANCE
) {
    val isValid: Boolean
        get() = age in 13..100 && heightCm in 120.0..230.0 && weightKg in 30.0..300.0
}
