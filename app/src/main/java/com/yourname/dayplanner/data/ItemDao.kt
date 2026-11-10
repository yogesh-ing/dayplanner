package com.yourname.dayplanner.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Insert suspend fun insert(item: Item): Long
    @Update suspend fun update(item: Item)
    @Delete suspend fun delete(item: Item)
    @Query("SELECT * FROM items WHERE id = :id") suspend fun get(id: Long): Item?
    @Query("SELECT * FROM items WHERE active = 1") suspend fun allActive(): List<Item>
    @Query("SELECT * FROM items WHERE active = 1 ORDER BY startMinuteOfDay") fun observeActive(): Flow<List<Item>>
}