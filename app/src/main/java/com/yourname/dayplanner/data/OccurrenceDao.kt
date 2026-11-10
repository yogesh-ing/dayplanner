package com.yourname.dayplanner.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface OccurrenceDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAll(list: List<Occurrence>): List<Long>

    @Query("SELECT * FROM occurrences WHERE scheduledAt >= :from AND scheduledAt < :to ORDER BY scheduledAt") fun observeBetween(from: Long, to: Long): Flow<List<Occurrence>>

    @Query("SELECT * FROM occurrences WHERE status = 'PENDING' AND scheduledAt > :now ORDER BY scheduledAt") suspend fun upcomingPending(now: Long): List<Occurrence>

    @Query("SELECT COUNT(*) FROM occurrences WHERE itemId = :itemId AND scheduledAt < :before") suspend fun countBefore(itemId: Long, before: Long): Int

    @Query("SELECT COUNT(*) FROM occurrences WHERE itemId = :itemId AND status = 'PENDING'") suspend fun pendingCount(itemId: Long): Int

    @Query("SELECT MAX(actualEnd) FROM occurrences WHERE itemId = :itemId AND status IN ('DONE','PARTIAL')") suspend fun lastCompletedAt(itemId: Long): Long?

    @Query("UPDATE occurrences SET status = :status, actualEnd = :at, skipReason = :reason WHERE id = :id") suspend fun mark(id: Long, status: OccStatus, at: Long, reason: SkipReason?)
}