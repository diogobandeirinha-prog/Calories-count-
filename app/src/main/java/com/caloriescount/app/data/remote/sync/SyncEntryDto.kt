package com.caloriescount.app.data.remote.sync

import com.caloriescount.app.data.db.FoodEntryEntity
import com.caloriescount.app.data.model.FoodItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire shape sent to the remote server for a logged meal. */
@Serializable
data class SyncEntryDto(
    @SerialName("local_id") val localId: Long,
    @SerialName("remote_id") val remoteId: String? = null,
    val timestamp: Long,
    @SerialName("meal_name") val mealName: String,
    @SerialName("total_calories") val totalCalories: Double,
    @SerialName("total_protein_g") val totalProteinG: Double,
    @SerialName("total_carbs_g") val totalCarbsG: Double,
    @SerialName("total_fats_g") val totalFatsG: Double,
    val items: List<FoodItem>,
    val notes: String,
    @SerialName("updated_at") val updatedAt: Long
) {
    companion object {
        fun from(e: FoodEntryEntity) = SyncEntryDto(
            localId = e.id,
            remoteId = e.remoteId,
            timestamp = e.timestamp,
            mealName = e.mealName,
            totalCalories = e.totalCalories,
            totalProteinG = e.totalProteinG,
            totalCarbsG = e.totalCarbsG,
            totalFatsG = e.totalFatsG,
            items = e.items,
            notes = e.notes,
            updatedAt = e.updatedAt
        )
    }
}
