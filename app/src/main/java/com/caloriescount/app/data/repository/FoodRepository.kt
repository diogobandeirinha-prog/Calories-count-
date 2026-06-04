package com.caloriescount.app.data.repository

import com.caloriescount.app.data.db.FavoriteFoodDao
import com.caloriescount.app.data.db.FavoriteFoodEntity
import com.caloriescount.app.data.db.FoodEntryDao
import com.caloriescount.app.data.db.FoodEntryEntity
import com.caloriescount.app.data.db.SyncStatus
import com.caloriescount.app.data.model.FoodItem
import com.caloriescount.app.data.remote.sync.RemoteFoodApi
import com.caloriescount.app.data.sync.SyncManager
import com.caloriescount.app.data.sync.SyncOutcome
import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for macro history. All reads are local Room [Flow]s (real-time,
 * offline). All writes hit the local DB first (offline-first) and then request a background
 * sync; the UI never blocks on the network.
 */
class FoodRepository(
    private val foodDao: FoodEntryDao,
    private val favoriteDao: FavoriteFoodDao,
    private val remoteApi: RemoteFoodApi,
    private val syncManager: SyncManager
) {

    // --- reads (real-time, local) ---

    fun observeAll(): Flow<List<FoodEntryEntity>> = foodDao.observeAll()

    fun observePendingSyncCount(): Flow<Int> = foodDao.observePendingCount()

    fun observeFavorites(limit: Int = 8): Flow<List<FavoriteFoodEntity>> =
        favoriteDao.observeTop(limit)

    suspend fun getById(id: Long): FoodEntryEntity? = foodDao.getById(id)

    // --- writes (offline-first) ---

    suspend fun addEntry(
        timestamp: Long,
        mealName: String,
        items: List<FoodItem>,
        notes: String,
        photoPath: String?
    ): Long {
        val now = System.currentTimeMillis()
        val entry = FoodEntryEntity(
            timestamp = timestamp,
            mealName = mealName.ifBlank { "Meal" },
            totalCalories = items.sumOf { it.calories },
            totalProteinG = items.sumOf { it.proteinG },
            totalCarbsG = items.sumOf { it.carbsG },
            totalFatsG = items.sumOf { it.fatsG },
            items = items,
            notes = notes,
            photoPath = photoPath,
            syncStatus = SyncStatus.PENDING,
            updatedAt = now
        )
        val id = foodDao.insert(entry)
        items.forEach { cacheFavorite(it, now) }
        syncManager.requestSync()
        return id
    }

    suspend fun delete(entry: FoodEntryEntity) {
        if (entry.remoteId != null) {
            // Already on the server — tombstone it so the remote delete can be replayed.
            foodDao.markDeleted(entry.id, System.currentTimeMillis())
            syncManager.requestSync()
        } else {
            // Never synced — safe to remove immediately.
            foodDao.hardDeleteById(entry.id)
        }
    }

    suspend fun setFavoritePinned(id: Long, pinned: Boolean) = favoriteDao.setPinned(id, pinned)

    // --- favorite/frequent foods cache ---

    private suspend fun cacheFavorite(item: FoodItem, now: Long) {
        val name = item.name.trim()
        if (name.isBlank()) return
        val key = name.lowercase()
        val existing = favoriteDao.findByNameKey(key)
        if (existing == null) {
            favoriteDao.insert(
                FavoriteFoodEntity(
                    nameKey = key,
                    name = name,
                    quantity = item.quantity,
                    weightGrams = item.weightGrams,
                    calories = item.calories,
                    proteinG = item.proteinG,
                    carbsG = item.carbsG,
                    fatsG = item.fatsG,
                    useCount = 1,
                    lastUsedAt = now
                )
            )
        } else {
            favoriteDao.update(
                existing.copy(
                    name = name,
                    quantity = item.quantity,
                    weightGrams = item.weightGrams,
                    calories = item.calories,
                    proteinG = item.proteinG,
                    carbsG = item.carbsG,
                    fatsG = item.fatsG,
                    useCount = existing.useCount + 1,
                    lastUsedAt = now
                )
            )
        }
    }

    // --- sync pass (invoked by the WorkManager worker) ---

    suspend fun syncPending(): SyncOutcome {
        if (!remoteApi.isConfigured) return SyncOutcome.NoServer
        val pending = foodDao.pendingSync()
        for (entry in pending) {
            try {
                if (entry.deleted) {
                    entry.remoteId?.let { remoteApi.delete(it) }
                    foodDao.hardDeleteById(entry.id)
                } else {
                    val remoteId = remoteApi.upsert(entry)
                    foodDao.markSynced(entry.id, remoteId)
                }
            } catch (e: Exception) {
                // Transient (offline / server error): stop and let WorkManager retry.
                return SyncOutcome.Retry
            }
        }
        return SyncOutcome.Success
    }
}
