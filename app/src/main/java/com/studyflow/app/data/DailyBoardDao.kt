package com.studyflow.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyBoardDao {

    @Query("SELECT * FROM daily_board_items ORDER BY createdAt ASC")
    fun getAllItems(): Flow<List<DailyBoardItem>>

    @Query("SELECT * FROM daily_board_items WHERE id = :id LIMIT 1")
    suspend fun getItemById(id: Int): DailyBoardItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: DailyBoardItem): Long

    @Update
    suspend fun updateItem(item: DailyBoardItem)

    @Delete
    suspend fun deleteItem(item: DailyBoardItem)
}
