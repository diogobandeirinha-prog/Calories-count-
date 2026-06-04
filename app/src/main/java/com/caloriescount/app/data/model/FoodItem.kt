package com.caloriescount.app.data.model

import kotlinx.serialization.Serializable

/**
 * A single identified food component within a dish/photo.
 *
 * [calories] are kcal for the estimated [quantity]; [proteinG] is grams of protein.
 */
@Serializable
data class FoodItem(
    val name: String,
    val quantity: String = "",
    val calories: Double = 0.0,
    val proteinG: Double = 0.0
)
