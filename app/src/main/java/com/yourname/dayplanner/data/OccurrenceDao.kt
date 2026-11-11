package com.yourname.dayplanner.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** One scheduled row as the timetable renders it: occurrence + item + repeat rule. */
data class DayEntry(
    val id: Long,
    val scheduledAt: Long,
    val status: String,
    val title: String,
    val category: String,
    val durationMin: Int,
    val frequency: String?,
    val repeatInterval: Int,
    val weekdaysMask: Int
)

@Dao
interface OccurrenceDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAll(list: List<Occurrence>): List<Long>

    @Query("SELECT * FROM occurrences WHERE scheduledAt >= :from AND scheduledAt < :to ORDER BY scheduledAt") fun observeBetween(from: Long, to: Long): Flow<List<Occurrence>>

    @Query("SELECT * FROM occurrences WHERE status = 'PENDING' AND scheduledAt > :now ORDER BY scheduledAt") suspend fun upcomingPending(now: Long): List<Occurrence>

    @Query("SELECT COUNT(*) FROM occurrences WHERE itemId = :itemId AND scheduledAt < :before") suspend fun countBefore(itemId: Long, before: Long): Int

    @Query("SELECT COUNT(*) FROM occurrences WHERE itemId = :itemId AND status = 'PENDING'") suspend fun pendingCount(itemId: Long): Int

    @Query("SELECT COUNT(*) FROM occurrences WHERE status IN ('DONE','PARTIAL')") suspend fun doneCount(): Int

    @Query("SELECT MAX(actualEnd) FROM occurrences WHERE itemId = :itemId AND status IN ('DONE','PARTIAL')") suspend fun lastCompletedAt(itemId: Long): Long?

    @Query("UPDATE occurrences SET status = :status, actualEnd = :at, skipReason = :reason WHERE id = :id") suspend fun mark(id: Long, status: OccStatus, at: Long?, reason: SkipReason?)

    /** Everything scheduled in [from, to), with the item and rule needed to render it. */
    @Query(
        """
        SELECT o.id AS id, o.scheduledAt AS scheduledAt, o.status AS status,
               i.title AS title, i.category AS category, i.durationMin AS durationMin,
               r.frequency AS frequency, r.interval AS repeatInterval, r.weekdaysMask AS weekdaysMask
        FROM occurrences o
        JOIN items i ON i.id = o.itemId
        LEFT JOIN repeat_rules r ON r.itemId = o.itemId
        WHERE o.scheduledAt >= :from AND o.scheduledAt < :to
        ORDER BY o.scheduledAt
        """
    )
    suspend fun dayEntries(from: Long, to: Long): List<DayEntry>
}
