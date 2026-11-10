package com.yourname.dayplanner.data

import androidx.room.*
import java.time.LocalDate

@Entity(tableName = "items")
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val preset: Preset,
    val category: String = "General",
    val notes: String = "",
    val startMinuteOfDay: Int? = null,
    val durationMin: Int = 15,
    val flexWindowStartMin: Int? = null,
    val flexWindowEndMin: Int? = null,
    val highPriority: Boolean = true,
    val soundMode: SoundMode = SoundMode.SOUND_VIBRATE,
    val criticalMode: Boolean = false,
    val ringDuringSleep: Boolean = false,
    val allowOverlap: Boolean = false,
    val focusLock: Boolean = false,
    val minimumVersionText: String? = null,
    val addToCalendar: Boolean = false,
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "repeat_rules",
    foreignKeys = [ForeignKey(Item::class, ["id"], ["itemId"], onDelete = ForeignKey.CASCADE)]
)
data class RepeatRule(
    @PrimaryKey val itemId: Long,
    val frequency: Frequency = Frequency.NONE,
    val interval: Int = 1,
    val weekdaysMask: Int = 0,
    val monthRule: MonthRule = MonthRule.DAY_OF_MONTH,
    val dayOfMonth: Int? = null,
    val nthWeek: Int = 1,
    val timesOfDay: List<Int> = emptyList(),
    val windowStartMin: Int? = null,
    val windowEndMin: Int? = null,
    val mode: RepeatMode = RepeatMode.FIXED,
    val startDate: LocalDate,
    val endType: EndType = EndType.NEVER,
    val endDate: LocalDate? = null,
    val endCount: Int? = null,
    val pausedUntil: LocalDate? = null
)

@Entity(
    tableName = "reminder_offsets",
    foreignKeys = [ForeignKey(Item::class, ["id"], ["itemId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("itemId")]
)
data class ReminderOffset(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val minutesBefore: Int
)

@Entity(
    tableName = "checklist_entries",
    foreignKeys = [ForeignKey(Item::class, ["id"], ["itemId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("itemId")]
)
data class ChecklistEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val text: String,
    val done: Boolean = false
)

@Entity(
    tableName = "occurrences",
    foreignKeys = [ForeignKey(Item::class, ["id"], ["itemId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("scheduledAt"), Index(value = ["itemId", "scheduledAt"], unique = true)]
)
data class Occurrence(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val scheduledAt: Long,
    val status: OccStatus = OccStatus.PENDING,
    val skipReason: SkipReason? = null,
    val actualStart: Long? = null,
    val actualEnd: Long? = null,
    val snoozeCount: Int = 0,
    val note: String? = null
)

@Entity(tableName = "daily_reviews")
data class DailyReview(
    @PrimaryKey val date: LocalDate,
    val energy: Int? = null,
    val mood: Int? = null,
    val win: String? = null,
    val top3: List<Long> = emptyList(),
    val score: Float? = null,
    val completedAt: Long? = null
)

@Entity(tableName = "weekly_reviews")
data class WeeklyReview(
    @PrimaryKey val weekStart: LocalDate,
    val insight: String? = null,
    val commitment: String? = null,
    val commitmentKept: Boolean? = null
)