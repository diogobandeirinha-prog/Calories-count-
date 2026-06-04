package com.caloriescount.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {

    @Insert
    suspend fun insert(workout: WorkoutEntity): Long

    @Query("SELECT * FROM workouts ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp DESC")
    fun observeBetween(start: Long, end: Long): Flow<List<WorkoutEntity>>

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun deleteById(id: Long)
}
