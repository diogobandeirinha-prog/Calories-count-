package com.caloriescount.app.data.remote

import com.caloriescount.app.data.model.FoodItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Strict JSON contract we ask the model to return (matches the json_schema we send). */
@Serializable
data class NutritionAnalysis(
    @SerialName("meal_name") val mealName: String = "Meal",
    val items: List<AnalyzedItem> = emptyList(),
    @SerialName("total_calories") val totalCalories: Double = 0.0,
    @SerialName("total_protein_g") val totalProteinG: Double = 0.0,
    @SerialName("total_carbs_g") val totalCarbsG: Double = 0.0,
    @SerialName("total_fats_g") val totalFatsG: Double = 0.0,
    val notes: String = ""
)

@Serializable
data class AnalyzedItem(
    val name: String,
    val quantity: String = "",
    @SerialName("weight_grams") val weightGrams: Double = 0.0,
    val calories: Double = 0.0,
    @SerialName("protein_g") val proteinG: Double = 0.0,
    @SerialName("carbs_g") val carbsG: Double = 0.0,
    @SerialName("fats_g") val fatsG: Double = 0.0
) {
    fun toFoodItem() = FoodItem(
        name = name,
        quantity = quantity,
        weightGrams = weightGrams,
        calories = calories,
        proteinG = proteinG,
        carbsG = carbsG,
        fatsG = fatsG
    )
}

/** Outcome of an analysis attempt (photo or voice). */
sealed interface AnalysisOutcome {
    data class Success(val analysis: NutritionAnalysis) : AnalysisOutcome
    data class Error(val message: String) : AnalysisOutcome
}
