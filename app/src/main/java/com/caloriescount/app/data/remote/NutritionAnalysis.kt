package com.caloriescount.app.data.remote

import com.caloriescount.app.data.model.FoodItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Strict JSON contract we ask the model to return. */
@Serializable
data class NutritionAnalysis(
    @SerialName("meal_name") val mealName: String = "Meal",
    val items: List<AnalyzedItem> = emptyList(),
    @SerialName("total_calories") val totalCalories: Double = 0.0,
    @SerialName("total_protein_g") val totalProteinG: Double = 0.0,
    val notes: String = ""
)

@Serializable
data class AnalyzedItem(
    val name: String,
    val quantity: String = "",
    val calories: Double = 0.0,
    @SerialName("protein_g") val proteinG: Double = 0.0
) {
    fun toFoodItem() = FoodItem(name = name, quantity = quantity, calories = calories, proteinG = proteinG)
}

/** Outcome of an analysis attempt. */
sealed interface AnalysisOutcome {
    data class Success(val analysis: NutritionAnalysis) : AnalysisOutcome
    data class Error(val message: String) : AnalysisOutcome
}
