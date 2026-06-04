package com.caloriescount.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodEntryDao {

    @Insert
    suspend fun insert(entry: FoodEntryEntity): Long

    @Update
    suspend fun update(entry: FoodEntryEntity)

    /** All visible entries (tombstones excluded), newest first. Day/week/month
     *  aggregation is done in memory using the device time zone so bucket
     *  boundaries match what the user sees. */
    @Query("SELECT * FROM food_entries WHERE deleted = 0 ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<FoodEntryEntity>>

    @Query("SELECT * FROM food_entries WHERE deleted = 0 AND timestamp BETWEEN :start AND :end ORDER BY timestamp DESC")
    fun observeBetween(start: Long, end: Long): Flow<List<FoodEntryEntity>>

    @Query("SELECT * FROM food_entries WHERE id = :id")
    suspend fun getById(id: Long): FoodEntryEntity?

    // --- offline-first sync ---

    /** Rows that still need to be pushed to the remote server, oldest first. */
    @Query("SELECT * FROM food_entries WHERE syncStatus = 'PENDING' ORDER BY updatedAt ASC")
    suspend fun pendingSync(): List<FoodEntryEntity>

    /** Live count of un-synced rows — drives the "N pending" UI indicator. */
    @Query("SELECT COUNT(*) FROM food_entries WHERE syncStatus = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query("UPDATE food_entries SET remoteId = :remoteId, syncStatus = 'SYNCED' WHERE id = :id")
    suspend fun markSynced(id: Long, remoteId: String)

    /** Turn an already-synced row into a tombstone awaiting a remote delete. */
    @Query("UPDATE food_entries SET deleted = 1, syncStatus = 'PENDING', updatedAt = :updatedAt WHERE id = :id")
    suspend fun markDeleted(id: Long, updatedAt: Long)

    @Query("DELETE FROM food_entries WHERE id = :id")
    suspend fun hardDeleteById(id: Long)
}
