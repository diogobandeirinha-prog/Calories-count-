package com.caloriescount.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteFoodDao {

    /** Pinned favorites first, then most-used, then most-recent. */
    @Query(
        "SELECT * FROM favorite_foods ORDER BY pinned DESC, useCount DESC, lastUsedAt DESC LIMIT :limit"
    )
    fun observeTop(limit: Int): Flow<List<FavoriteFoodEntity>>

    @Query("SELECT * FROM favorite_foods WHERE nameKey = :nameKey LIMIT 1")
    suspend fun findByNameKey(nameKey: String): FavoriteFoodEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(food: FavoriteFoodEntity): Long

    @Update
    suspend fun update(food: FavoriteFoodEntity)

    @Query("UPDATE favorite_foods SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("DELETE FROM favorite_foods WHERE id = :id")
    suspend fun delete(id: Long)
}
