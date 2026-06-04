package com.caloriescount.app.data.model

import kotlinx.serialization.Serializable

/**
 * A single identified food component within a dish/photo or a spoken meal.
 *
 * [weightGrams] is the estimated portion weight; [calories] are kcal for that portion;
 * [proteinG], [carbsG] and [fatsG] are grams of each macronutrient.
 */
@Serializable
data class FoodItem(
    val name: String,
    val quantity: String = "",
    val weightGrams: Double = 0.0,
    val calories: Double = 0.0,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatsG: Double = 0.0
)
