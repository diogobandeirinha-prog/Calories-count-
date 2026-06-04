package com.caloriescount.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.caloriescount.app.data.model.FoodItem

/**
 * One logged meal/photo. Totals are stored denormalised so range queries stay cheap,
 * while [items] preserves the per-component breakdown for the detail view.
 *
 * Sync metadata supports the offline-first model: every local mutation sets
 * [syncStatus] = PENDING; the sync worker pushes pending rows and flips them to SYNCED.
 * Deletes of already-synced rows become tombstones ([deleted] = true) so the remote
 * delete can be replayed before the row is finally removed locally.
 */
@Entity(
    tableName = "food_entries",
    indices = [Index("syncStatus"), Index("timestamp")]
)
data class FoodEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Epoch millis of when the meal was logged (used for day/week/month bucketing). */
    val timestamp: Long,
    val mealName: String,
    val totalCalories: Double,
    val totalProteinG: Double,
    val totalCarbsG: Double = 0.0,
    val totalFatsG: Double = 0.0,
    val items: List<FoodItem>,
    val notes: String = "",
    /** Local file path or content uri of the saved photo, if any. */
    val photoPath: String? = null,

    // --- sync metadata ---
    val remoteId: String? = null,
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    /** Tombstone flag: row is awaiting a remote delete, hidden from the UI. */
    val deleted: Boolean = false,
    val updatedAt: Long = 0L
)
