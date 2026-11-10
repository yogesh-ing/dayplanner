package com.yourname.dayplanner.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Seeds a handful of realistic items and repeat rules the first time the app runs,
 * so the timetable has something to show before any item-creation screen exists.
 * It is a no-op once the database holds items.
 */
object DemoSeed {
    private const val MON_FRI = 0b0011111          // bit0 = Mon ... bit6 = Sun
    private const val MON_WED_FRI = 0b0010101

    private fun mondayThisWeek(day: LocalDate = LocalDate.now()) =
        day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private class Seed(
        val title: String,
        val category: String,
        val preset: Preset,
        val minuteOfDay: Int,
        val durationMin: Int,
        val rule: (itemId: Long) -> RepeatRule
    )

    suspend fun ensure(db: AppDatabase) {
        if (db.itemDao().allActive().isNotEmpty()) return

        val start = mondayThisWeek().minusWeeks(2)
        val daily: (Long) -> RepeatRule = { id ->
            RepeatRule(itemId = id, frequency = Frequency.DAYS, interval = 1, startDate = start)
        }
        val weekly: (Int) -> (Long) -> RepeatRule = { mask ->
            { id -> RepeatRule(itemId = id, frequency = Frequency.WEEKLY, interval = 1, weekdaysMask = mask, startDate = start) }
        }

        val seeds = listOf(
            Seed("Morning routine", "Routine", Preset.ROUTINE, 6 * 60 + 45, 30, daily),
            Seed("Data structures: graphs", "Study", Preset.PROJECT, 7 * 60 + 30, 90, weekly(MON_FRI)),
            Seed("Breakfast with family", "Personal", Preset.ROUTINE, 9 * 60 + 15, 30, daily),
            Seed("Maturity Ledge: layout fixes", "Project", Preset.PROJECT, 10 * 60, 60, weekly(MON_WED_FRI)),
            Seed("Stretch + water", "Health", Preset.INTERVAL, 11 * 60 + 10, 15) { id ->
                RepeatRule(
                    itemId = id,
                    frequency = Frequency.MINUTES,
                    interval = 90,
                    startDate = start,
                    windowStartMin = 11 * 60 + 10,
                    windowEndMin = 11 * 60 + 40
                )
            },
            Seed("Lunch", "Personal", Preset.EVENT, 12 * 60 + 30, 45, daily),
            Seed("Compose: state hoisting", "Study", Preset.PROJECT, 14 * 60, 90, weekly(MON_FRI)),
            Seed("Walk outside", "Health", Preset.ROUTINE, 16 * 60, 30, daily),
            Seed("Evening review", "Growth", Preset.ROUTINE, 18 * 60 + 30, 30, daily),
            Seed("Journal + plan tomorrow", "Routine", Preset.ROUTINE, 21 * 60 + 30, 30, daily)
        )

        for (s in seeds) {
            val id = db.itemDao().insert(
                Item(
                    title = s.title,
                    preset = s.preset,
                    category = s.category,
                    startMinuteOfDay = s.minuteOfDay,
                    durationMin = s.durationMin,
                    highPriority = s.category == "Study" || s.category == "Project"
                )
            )
            db.ruleDao().upsert(s.rule(id))
        }
    }

    /** Marks the first few slots of today as done once, so the progress UI is visible. */
    suspend fun markSampleDone(db: AppDatabase) {
        if (db.occurrenceDao().doneCount() > 0) return
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val from = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val rows = db.occurrenceDao().dayEntries(from, to)
        val now = System.currentTimeMillis()
        rows.take(3).forEach { db.occurrenceDao().mark(it.id, OccStatus.DONE, now, null) }
    }
}
