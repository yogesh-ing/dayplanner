package com.yourname.dayplanner.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(rule: RepeatRule)
    @Query("SELECT * FROM repeat_rules WHERE itemId = :itemId") suspend fun get(itemId: Long): RepeatRule?
}