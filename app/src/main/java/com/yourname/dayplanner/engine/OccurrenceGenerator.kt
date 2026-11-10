package com.yourname.dayplanner.engine

import com.yourname.dayplanner.data.*
import java.time.*
import java.time.ZoneId

class OccurrenceGenerator(private val db: AppDatabase) {
    private val zone = ZoneId.systemDefault()
    private fun LocalDateTime.millis() = atZone(zone).toInstant().toEpochMilli()

    suspend fun topUp(days: Long = 14, now: LocalDateTime = LocalDateTime.now()) {
        val end = now.plusDays(days)
        for (item in db.itemDao().allActive()) {
            val rule = db.ruleDao().get(item.id) ?: continue
            val defaultTime = item.startMinuteOfDay ?: 540
            val lastDone = db.occurrenceDao().lastCompletedAt(item.id)
                ?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone) }

            val times = if (rule.frequency != Frequency.NONE && rule.mode == RepeatMode.AFTER_COMPLETION) {
                if (db.occurrenceDao().pendingCount(item.id) > 0) emptyList()
                else listOf(maxOf(RepeatEngine.nextAfterCompletion(rule, defaultTime, lastDone), now.plusMinutes(1)))
            } else {
                val before = db.occurrenceDao().countBefore(item.id, now.millis())
                RepeatEngine.occurrences(rule, defaultTime, now, end, lastDone, before)
            }
            db.occurrenceDao().insertAll(times.map { Occurrence(itemId = item.id, scheduledAt = it.millis()) })
        }
    }
}