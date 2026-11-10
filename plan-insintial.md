# Day Planner: Starter Kit

**Defaults applied:** critical items ring during sleep (per-item toggle), focus = full-screen with auto-return and optional screen pinning, unreviewed tasks count as Not done after the morning enforcement, manual WhatsApp share in v1 (Telegram later), and I'll build it with whichever LLM you use.

**App name ideas:** *Kept*, *Cadence*, *DayLock*, *Tally*. I'll use `com.yourname.dayplanner` in the code, so swap in your own package.

## 0. Your Samsung A53: What to Know

Your phone will probably be on Android 14 or 15 (One UI 6/7). Check Settings → About phone → Software information.

Samsung is one of the more aggressive brands at killing background apps, so once the alarm spike works, do this setup:

1. Settings → Apps → *YourApp* → Battery → **Unrestricted**
2. Settings → Battery → Background usage limits → turn **off** "Put unused apps to sleep", and add the app to **Never sleeping apps**
3. Open Recents, tap the app icon, and choose **Keep open** (lock)
4. Settings → Notifications → Lock screen notifications → **Show content**
5. Never "Force stop" the app, because Android cancels all its alarms when you do

---

## 1. Wireframes (Text)

**Today**
```
┌────────────────────────────────────┐
│ Tue 14 Oct               6/10 done │
│ [🐱 Feed cat] [🪴 Water plants]     │  ← micro strip (tap = done)
│ 💊 Medicine 2/4 given · next 18:00 │
├────────────────────────────────────┤
│ 07:00 ✅ Wake + water              │
│ 07:30 ✅ Exercise            45m   │
│ 09:00 ▶ Study: Chapter 3  ◀ NOW    │  ← highlighted, progress bar
│        ▓▓▓▓▓░░░░░  38 min left     │
│ 11:00 ⏳ Project work        90m   │
│ 13:00 ⏳ Lunch               45m   │
│ ...                                │
│ 22:00 📝 Daily check-in            │
│ 23:00 💤 Sleep (locked)            │
├────────────────────────────────────┤
│ NEXT UP: Project work in 12 min    │
│ [Today] [Plan] [Report] [Settings] │
└────────────────────────────────────┘
```

**Add / Edit item** (progressive: only the top part shows at first)
```
┌────────────────────────────────────┐
│ ← New item                  [Save] │
│ Preset: [Routine][Project][Micro]  │
│         [Interval][One-time][Event]│
│         [Deadline][Checklist][Inbox]│
│ Title  [__________________________]│
│ When   [09:00]  Duration [45 min]  │
│ Repeat [Every 6 hours ▾]           │
│ ☑ High priority (full-screen alert)│
│ Sound  [Sound+Vibrate ▾]           │
│ ▾ More                             │
│   Weekdays  M T W T F S S          │
│   Active window [24h ▾]            │
│   Mode (•) Fixed ( ) After completion│
│   Ends [Never ▾]   Pause until [ ] │
│   Advance reminders [1d][1h]       │
│   ☐ Critical (rings until "Given") │
│   ☐ Ring during sleep              │
│   ☐ Focus lock   ☐ Allow overlap   │
│   Minimum version [5 min walk]     │
│   ☐ Add to Google Calendar         │
└────────────────────────────────────┘
```

**Full-screen alert**
```
┌────────────────────────────────────┐
│            09:00                   │
│        STUDY: CHAPTER 3            │
│          90 min block              │
│   "Start now. Future you agrees."  │
│                                    │
│   [        ▶  START        ]       │
│   [   ✔ DONE   ] [ ⏰ Snooze 5 (2 left)]│
│   [ Hold to skip → reason ]        │
│                                    │
│   Next: Project work 11:00         │
└────────────────────────────────────┘
```

**10 PM check-in** (one item per card, swipe through)
```
┌────────────────────────────────────┐
│ Check-in  3 of 9                   │
│ Exercise · 07:30                   │
│ [✅ Done] [🟡 Partial] [❌ Not done]│
│ Why? (if not done)                 │
│ [Tired][No time][Forgot][Distracted]│
│ [Unplanned][Other]                 │
└────────────────────────────────────┘
 → Micro-tasks → Energy/Mood → Win → Plan tomorrow (Top 3) → Wind-down
```

**Report**
```
┌────────────────────────────────────┐
│ THIS WEEK  Consistency 74%  ▲ +6   │
│ ▁▃▅▆▅▇█ (7-day)   30-day: ▂▃▃▅▆▆▇  │
│ Bounce-back rate: 5/6 misses       │
│ Run without 2 misses in a row: 12d │
├────────────────────────────────────┤
│ Why you fail:  Tired 41% · Forgot 22%│
│ Best time: 7–10 AM   Worst: 3–5 PM │
│ ⚠ Fix or Drop: "Read 30 min" (38%) │
│ 💊 Medicine: 26 on time / 2 late / 0 missed│
│ [Share weekly report]              │
└────────────────────────────────────┘
```

---

## 2. Milestone 1: Project Setup and Room Schema

**Create the project:** Android Studio → New Project → *Empty Activity* (Compose), min SDK 26, Kotlin DSL.

**`app/build.gradle.kts`** additions (the KSP version must match your Kotlin version, so check the KSP releases page):
```kotlin
plugins {
    id("com.google.devtools.ksp") version "<matches-your-kotlin-version>"
}
dependencies {
    val room = "2.6.1"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    testImplementation("junit:junit:4.13.2")
}
```

**`data/Enums.kt`**
```kotlin
package com.yourname.dayplanner.data

enum class Preset { ROUTINE, PROJECT, MICRO, INTERVAL, ONE_TIME, EVENT, DEADLINE, CHECKLIST, INBOX }
enum class SoundMode { SOUND_VIBRATE, VIBRATE, SOUND, SILENT }
enum class Frequency { NONE, MINUTES, HOURS, DAYS, WEEKLY, MONTHLY, YEARLY }
enum class RepeatMode { FIXED, AFTER_COMPLETION }
enum class MonthRule { DAY_OF_MONTH, NTH_WEEKDAY, LAST_DAY }
enum class EndType { NEVER, ON_DATE, AFTER_COUNT }
enum class OccStatus { PENDING, DONE, PARTIAL, SKIPPED, NO_RESPONSE }
enum class SkipReason { TIRED, NO_TIME, FORGOT, DISTRACTED, UNPLANNED, OTHER }
```

**`data/Entities.kt`**
```kotlin
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
    val startMinuteOfDay: Int? = null,      // null = no fixed time (inbox / flexible)
    val durationMin: Int = 15,
    val flexWindowStartMin: Int? = null,    // micro-tasks: "evening" = 1080..1260
    val flexWindowEndMin: Int? = null,
    val highPriority: Boolean = true,       // true = full-screen alert (default ON)
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
    val interval: Int = 1,                  // every N units
    val weekdaysMask: Int = 0,              // bit0=Mon ... bit6=Sun
    val monthRule: MonthRule = MonthRule.DAY_OF_MONTH,
    val dayOfMonth: Int? = null,            // null = use startDate's day
    val nthWeek: Int = 1,                   // 1..4, or -1 = last (NTH_WEEKDAY)
    val timesOfDay: List<Int> = emptyList(),// minutes since midnight; empty = item's start time
    val windowStartMin: Int? = null,        // active window for HOURS/MINUTES; both null = 24h
    val windowEndMin: Int? = null,
    val mode: RepeatMode = RepeatMode.FIXED,
    val startDate: LocalDate,
    val endType: EndType = EndType.NEVER,
    val endDate: LocalDate? = null,
    val endCount: Int? = null,
    val pausedUntil: LocalDate? = null      // inclusive
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
    val scheduledAt: Long,                  // epoch millis
    val status: OccStatus = OccStatus.PENDING,
    val skipReason: SkipReason? = null,
    val actualStart: Long? = null,
    val actualEnd: Long? = null,            // used as "completed at" for after-completion logic
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
```

The **unique index on (itemId, scheduledAt)** makes the "top up the next 14 days" job safe to run any number of times, because duplicates are ignored.

**`data/Converters.kt`**
```kotlin
package com.yourname.dayplanner.data

import androidx.room.TypeConverter
import java.time.LocalDate

class Converters {
    @TypeConverter fun dateToLong(d: LocalDate?): Long? = d?.toEpochDay()
    @TypeConverter fun longToDate(v: Long?): LocalDate? = v?.let { LocalDate.ofEpochDay(it) }

    @TypeConverter fun intListToString(l: List<Int>): String = l.joinToString(",")
    @TypeConverter fun stringToIntList(s: String): List<Int> =
        if (s.isBlank()) emptyList() else s.split(",").map { it.toInt() }

    @TypeConverter fun longListToString(l: List<Long>): String = l.joinToString(",")
    @TypeConverter fun stringToLongList(s: String): List<Long> =
        if (s.isBlank()) emptyList() else s.split(",").map { it.toLong() }
}
```

> Room has two converters with the same JVM signature pattern here (`List<Int>` and `List<Long>`). If you get a "multiple converters" error, add `@TypeConverters` on the specific fields or wrap the lists in a tiny value class. Paste the error to your LLM and it will fix it in one step.

**`data/Daos.kt`**
```kotlin
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
    @Query("SELECT * FROM items WHERE active = 1 ORDER BY startMinuteOfDay")
    fun observeActive(): Flow<List<Item>>
}

@Dao
interface RuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(rule: RepeatRule)
    @Query("SELECT * FROM repeat_rules WHERE itemId = :itemId") suspend fun get(itemId: Long): RepeatRule?
}

@Dao
interface OccurrenceDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(list: List<Occurrence>): List<Long>

    @Query("SELECT * FROM occurrences WHERE scheduledAt >= :from AND scheduledAt < :to ORDER BY scheduledAt")
    fun observeBetween(from: Long, to: Long): Flow<List<Occurrence>>

    @Query("SELECT * FROM occurrences WHERE status = 'PENDING' AND scheduledAt > :now ORDER BY scheduledAt")
    suspend fun upcomingPending(now: Long): List<Occurrence>

    @Query("SELECT COUNT(*) FROM occurrences WHERE itemId = :itemId AND scheduledAt < :before")
    suspend fun countBefore(itemId: Long, before: Long): Int

    @Query("SELECT COUNT(*) FROM occurrences WHERE itemId = :itemId AND status = 'PENDING'")
    suspend fun pendingCount(itemId: Long): Int

    @Query("SELECT MAX(actualEnd) FROM occurrences WHERE itemId = :itemId AND status IN ('DONE','PARTIAL')")
    suspend fun lastCompletedAt(itemId: Long): Long?

    @Query("UPDATE occurrences SET status = :status, actualEnd = :at, skipReason = :reason WHERE id = :id")
    suspend fun mark(id: Long, status: OccStatus, at: Long, reason: SkipReason? = null)
}
```

**`data/AppDatabase.kt`**
```kotlin
package com.yourname.dayplanner.data

import android.content.Context
import androidx.room.*

@Database(
    entities = [Item::class, RepeatRule::class, ReminderOffset::class, ChecklistEntry::class,
        Occurrence::class, DailyReview::class, WeeklyReview::class],
    version = 1, exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun ruleDao(): RuleDao
    abstract fun occurrenceDao(): OccurrenceDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(ctx: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(ctx.applicationContext, AppDatabase::class.java, "planner.db")
                .build().also { instance = it }
        }
    }
}
```

**Done when:** the project builds with no errors.

---

## 3. Milestone 2: Repeat Engine + Tests

The engine is **pure Kotlin with no Android or database dependencies**, so you can test it in milliseconds.

**`engine/RepeatEngine.kt`**
```kotlin
package com.yourname.dayplanner.engine

import com.yourname.dayplanner.data.*
import java.time.*
import java.time.temporal.ChronoUnit
import kotlin.math.min

object RepeatEngine {
    private const val MAX_RESULTS = 2000

    /** Mask helper: Monday = bit0 ... Sunday = bit6 */
    fun bit(d: DayOfWeek): Int = 1 shl (d.value - 1)

    /**
     * Occurrences in [from, to).
     * @param defaultTimeMin item's start time, used when rule.timesOfDay is empty
     * @param lastCompletedAt only used for AFTER_COMPLETION rules
     * @param generatedBefore how many occurrences of this item exist before `from` (for AFTER_COUNT)
     */
    fun occurrences(
        rule: RepeatRule,
        defaultTimeMin: Int,
        from: LocalDateTime,
        to: LocalDateTime,
        lastCompletedAt: LocalDateTime? = null,
        generatedBefore: Int = 0
    ): List<LocalDateTime> {
        if (!from.isBefore(to)) return emptyList()

        val raw: List<LocalDateTime> = when {
            rule.frequency == Frequency.NONE -> dayBased(rule, defaultTimeMin, from, to)
            rule.mode == RepeatMode.AFTER_COMPLETION ->
                listOf(nextAfterCompletion(rule, defaultTimeMin, lastCompletedAt))
            rule.frequency == Frequency.MINUTES || rule.frequency == Frequency.HOURS ->
                subDay(rule, defaultTimeMin, from, to)
            else -> dayBased(rule, defaultTimeMin, from, to)
        }

        val endDate = rule.endDate
        val paused = rule.pausedUntil
        var result = raw.asSequence()
            .filter { !it.isBefore(from) && it.isBefore(to) }
            .filter { endDate == null || rule.endType != EndType.ON_DATE || !it.toLocalDate().isAfter(endDate) }
            .filter { paused == null || it.toLocalDate().isAfter(paused) }

        val endCount = rule.endCount
        if (rule.endType == EndType.AFTER_COUNT && endCount != null) {
            val remaining = endCount - generatedBefore
            if (remaining <= 0) return emptyList()
            result = result.take(remaining)
        }
        return result.toList()
    }

    /** Next due time for "after completion" rules (e.g. water plants 3 days after last time). */
    fun nextAfterCompletion(rule: RepeatRule, defaultTimeMin: Int, lastCompletedAt: LocalDateTime?): LocalDateTime {
        val n = rule.interval.coerceAtLeast(1).toLong()
        val timeMin = rule.timesOfDay.minOrNull() ?: defaultTimeMin
        var next: LocalDateTime = if (lastCompletedAt == null) {
            at(rule.startDate, timeMin)
        } else when (rule.frequency) {
            Frequency.MINUTES -> lastCompletedAt.plusMinutes(n)
            Frequency.HOURS -> lastCompletedAt.plusHours(n)
            Frequency.DAYS -> at(lastCompletedAt.toLocalDate().plusDays(n), timeMin)
            Frequency.WEEKLY -> at(lastCompletedAt.toLocalDate().plusWeeks(n), timeMin)
            Frequency.MONTHLY -> at(lastCompletedAt.toLocalDate().plusMonths(n), timeMin)
            Frequency.YEARLY -> at(lastCompletedAt.toLocalDate().plusYears(n), timeMin)
            Frequency.NONE -> at(rule.startDate, timeMin)
        }
        if (next.toLocalDate().isBefore(rule.startDate)) next = at(rule.startDate, timeMin)
        val p = rule.pausedUntil
        if (p != null && !next.toLocalDate().isAfter(p)) next = at(p.plusDays(1), timeMin)
        return next
    }

    // ---------- day-based (NONE, DAYS, WEEKLY, MONTHLY, YEARLY) ----------

    private fun dayBased(rule: RepeatRule, defaultTimeMin: Int, from: LocalDateTime, to: LocalDateTime): List<LocalDateTime> {
        val times = rule.timesOfDay.ifEmpty { listOf(defaultTimeMin) }.sorted()
        val out = mutableListOf<LocalDateTime>()
        var d = maxOf(from.toLocalDate(), rule.startDate)
        val last = to.toLocalDate()
        while (!d.isAfter(last) && out.size < MAX_RESULTS) {
            if (matchesDay(rule, d)) times.forEach { out += at(d, it) }
            d = d.plusDays(1)
        }
        return out
    }

    private fun matchesDay(rule: RepeatRule, d: LocalDate): Boolean {
        val start = rule.startDate
        if (d.isBefore(start)) return false
        val n = rule.interval.coerceAtLeast(1)
        return when (rule.frequency) {
            Frequency.NONE -> d == start
            Frequency.DAYS -> ChronoUnit.DAYS.between(start, d) % n == 0L
            Frequency.WEEKLY -> {
                val mask = if (rule.weekdaysMask == 0) bit(start.dayOfWeek) else rule.weekdaysMask
                val weeks = ChronoUnit.WEEKS.between(
                    start.with(DayOfWeek.MONDAY), d.with(DayOfWeek.MONDAY)
                )
                (mask and bit(d.dayOfWeek)) != 0 && weeks % n == 0L
            }
            Frequency.MONTHLY -> {
                val months = ChronoUnit.MONTHS.between(YearMonth.from(start), YearMonth.from(d))
                months % n == 0L && monthDayMatches(rule, start, d)
            }
            Frequency.YEARLY -> {
                (d.year - start.year) % n == 0 && d.month == start.month &&
                    d.dayOfMonth == min(start.dayOfMonth, d.lengthOfMonth()) // Feb 29 -> Feb 28
            }
            else -> false
        }
    }

    private fun monthDayMatches(rule: RepeatRule, start: LocalDate, d: LocalDate): Boolean =
        when (rule.monthRule) {
            MonthRule.DAY_OF_MONTH ->
                d.dayOfMonth == min(rule.dayOfMonth ?: start.dayOfMonth, d.lengthOfMonth()) // 31st clamps
            MonthRule.LAST_DAY -> d.dayOfMonth == d.lengthOfMonth()
            MonthRule.NTH_WEEKDAY -> {
                val wd = firstWeekday(rule.weekdaysMask) ?: start.dayOfWeek
                d.dayOfWeek == wd && if (rule.nthWeek > 0) {
                    (d.dayOfMonth - 1) / 7 + 1 == rule.nthWeek
                } else {
                    d.plusDays(7).month != d.month // last occurrence in the month
                }
            }
        }

    private fun firstWeekday(mask: Int): DayOfWeek? =
        DayOfWeek.values().firstOrNull { (mask and bit(it)) != 0 }

    // ---------- sub-day (MINUTES, HOURS) ----------

    private fun subDay(rule: RepeatRule, defaultTimeMin: Int, from: LocalDateTime, to: LocalDateTime): List<LocalDateTime> {
        val n = rule.interval.coerceAtLeast(1).toLong()
        val step = if (rule.frequency == Frequency.HOURS) n * 60 else n
        val out = mutableListOf<LocalDateTime>()
        val ws = rule.windowStartMin
        val we = rule.windowEndMin

        if (ws != null && we != null) {
            // Windowed: restart every day at window start (e.g. every 45 min, 09:00-18:00)
            val endOffset = if (we >= ws) we else we + 1440 // window may cross midnight
            var d = maxOf(from.toLocalDate().minusDays(1), rule.startDate)
            while (!d.isAfter(to.toLocalDate()) && out.size < MAX_RESULTS) {
                var off = ws.toLong()
                while (off <= endOffset) {
                    out += d.atStartOfDay().plusMinutes(off)
                    off += step
                }
                d = d.plusDays(1)
            }
        } else {
            // Continuous 24h chain anchored at startDate + first time (e.g. every 6 h from 06:00)
            val first = rule.timesOfDay.minOrNull() ?: defaultTimeMin
            val anchor = at(rule.startDate, first)
            val diff = Duration.between(anchor, from).toMinutes()
            var t = if (diff <= 0) anchor else anchor.plusMinutes(((diff + step - 1) / step) * step)
            while (t.isBefore(to) && out.size < MAX_RESULTS) {
                out += t
                t = t.plusMinutes(step)
            }
        }
        return out
    }

    private fun at(d: LocalDate, minuteOfDay: Int): LocalDateTime =
        d.atStartOfDay().plusMinutes(minuteOfDay.toLong())
}
```

**`test/.../RepeatEngineTest.kt`** (in `app/src/test/java/...`)
```kotlin
package com.yourname.dayplanner.engine

import com.yourname.dayplanner.data.*
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.*

class RepeatEngineTest {
    private fun date(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d)
    private fun dt(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0) = LocalDateTime.of(y, m, d, h, min)

    private fun rule(
        freq: Frequency, start: LocalDate, interval: Int = 1, mask: Int = 0,
        monthRule: MonthRule = MonthRule.DAY_OF_MONTH, nth: Int = 1,
        times: List<Int> = emptyList(), ws: Int? = null, we: Int? = null,
        mode: RepeatMode = RepeatMode.FIXED, endType: EndType = EndType.NEVER,
        endDate: LocalDate? = null, endCount: Int? = null, paused: LocalDate? = null
    ) = RepeatRule(
        itemId = 0, frequency = freq, interval = interval, weekdaysMask = mask, monthRule = monthRule,
        nthWeek = nth, timesOfDay = times, windowStartMin = ws, windowEndMin = we, mode = mode,
        startDate = start, endType = endType, endDate = endDate, endCount = endCount, pausedUntil = paused
    )

    @Test fun every6Hours_24hWindow_crossesMidnight() {
        val r = rule(Frequency.HOURS, date(2025, 1, 10), interval = 6)
        val res = RepeatEngine.occurrences(r, 360, dt(2025, 1, 10), dt(2025, 1, 12))
        assertEquals(7, res.size)
        assertEquals(dt(2025, 1, 10, 6), res.first())
        assertEquals(dt(2025, 1, 11, 18), res.last())
        assertEquals(dt(2025, 1, 11, 0), res[3])
    }

    @Test fun every45Min_windowed_9to18() {
        val r = rule(Frequency.MINUTES, date(2025, 1, 10), interval = 45, ws = 540, we = 1080)
        val res = RepeatEngine.occurrences(r, 540, dt(2025, 1, 10), dt(2025, 1, 11))
        assertEquals(13, res.size)
        assertEquals(dt(2025, 1, 10, 9, 0), res.first())
        assertEquals(dt(2025, 1, 10, 18, 0), res.last())
    }

    @Test fun daily_threeFixedTimes() {
        val r = rule(Frequency.DAYS, date(2025, 1, 1), times = listOf(480, 840, 1200))
        val res = RepeatEngine.occurrences(r, 0, dt(2025, 1, 1), dt(2025, 1, 3))
        assertEquals(6, res.size)
    }

    @Test fun every3Days_fixed() {
        val r = rule(Frequency.DAYS, date(2025, 1, 1), interval = 3)
        val res = RepeatEngine.occurrences(r, 600, dt(2025, 1, 1), dt(2025, 1, 11))
        assertEquals(listOf(1, 4, 7, 10), res.map { it.dayOfMonth })
    }

    @Test fun weekly_monWedFri() {
        val mask = RepeatEngine.bit(DayOfWeek.MONDAY) or RepeatEngine.bit(DayOfWeek.WEDNESDAY) or
            RepeatEngine.bit(DayOfWeek.FRIDAY)
        val r = rule(Frequency.WEEKLY, date(2025, 1, 6), mask = mask)
        val res = RepeatEngine.occurrences(r, 1080, dt(2025, 1, 6), dt(2025, 1, 13))
        assertEquals(listOf(6, 8, 10), res.map { it.dayOfMonth })
    }

    @Test fun everyTwoWeeks_monday() {
        val r = rule(Frequency.WEEKLY, date(2025, 1, 6), interval = 2, mask = RepeatEngine.bit(DayOfWeek.MONDAY))
        val res = RepeatEngine.occurrences(r, 600, dt(2025, 1, 6), dt(2025, 2, 3))
        assertEquals(listOf(date(2025, 1, 6), date(2025, 1, 20)), res.map { it.toLocalDate() })
    }

    @Test fun monthly_lastFriday() {
        val r = rule(Frequency.MONTHLY, date(2025, 1, 31), monthRule = MonthRule.NTH_WEEKDAY, nth = -1,
            mask = RepeatEngine.bit(DayOfWeek.FRIDAY))
        val res = RepeatEngine.occurrences(r, 600, dt(2025, 1, 1), dt(2025, 4, 1))
        assertEquals(listOf(date(2025, 1, 31), date(2025, 2, 28), date(2025, 3, 28)), res.map { it.toLocalDate() })
    }

    @Test fun monthly_day31_clampsToMonthEnd() {
        val r = rule(Frequency.MONTHLY, date(2025, 1, 31))
        val res = RepeatEngine.occurrences(r, 600, dt(2025, 1, 1), dt(2025, 5, 1))
        assertEquals(listOf(31, 28, 31, 30), res.map { it.dayOfMonth })
    }

    @Test fun yearly_feb29_birthdayFallsOnFeb28InNonLeapYear() {
        val r = rule(Frequency.YEARLY, date(2024, 2, 29))
        val res = RepeatEngine.occurrences(r, 540, dt(2025, 1, 1), dt(2026, 1, 1))
        assertEquals(listOf(date(2025, 2, 28)), res.map { it.toLocalDate() })
    }

    @Test fun afterCompletion_every3Days_resetsFromActualDoneTime() {
        val r = rule(Frequency.DAYS, date(2025, 1, 1), interval = 3, mode = RepeatMode.AFTER_COMPLETION)
        val res = RepeatEngine.occurrences(r, 1140, dt(2025, 1, 10), dt(2025, 1, 20), dt(2025, 1, 10, 21, 0))
        assertEquals(listOf(dt(2025, 1, 13, 19, 0)), res)
    }

    @Test fun afterCompletion_every6Hours_medicineGivenLate() {
        val r = rule(Frequency.HOURS, date(2025, 1, 1), interval = 6, mode = RepeatMode.AFTER_COMPLETION)
        val next = RepeatEngine.nextAfterCompletion(r, 360, dt(2025, 1, 10, 14, 20))
        assertEquals(dt(2025, 1, 10, 20, 20), next)
    }

    @Test fun endsAfterCount_respectsAlreadyGenerated() {
        val r = rule(Frequency.DAYS, date(2025, 1, 1), endType = EndType.AFTER_COUNT, endCount = 3)
        val res = RepeatEngine.occurrences(r, 600, dt(2025, 1, 2), dt(2025, 1, 20), generatedBefore = 1)
        assertEquals(2, res.size)
    }

    @Test fun endsOnDate_isInclusive() {
        val r = rule(Frequency.DAYS, date(2025, 1, 1), endType = EndType.ON_DATE, endDate = date(2025, 1, 3))
        assertEquals(3, RepeatEngine.occurrences(r, 600, dt(2025, 1, 1), dt(2025, 1, 10)).size)
    }

    @Test fun pausedUntil_skipsPausedDays() {
        val r = rule(Frequency.DAYS, date(2025, 1, 1), paused = date(2025, 1, 4))
        val res = RepeatEngine.occurrences(r, 600, dt(2025, 1, 1), dt(2025, 1, 7))
        assertEquals(listOf(5, 6), res.map { it.dayOfMonth })
    }
}
```

**Run:** right-click the test class → Run. If a test fails, paste the failure to your LLM and say "fix the engine, not the test, unless the test expectation is wrong."

### The glue: `engine/OccurrenceGenerator.kt`
Run it daily from WorkManager and after every item save.
```kotlin
package com.yourname.dayplanner.engine

import com.yourname.dayplanner.data.*
import java.time.*

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
                // Only ONE pending occurrence at a time. If overdue, it fires in 1 minute.
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
```
After-completion items work like this: when you mark one done, `actualEnd` is stored and `topUp()` runs. That creates the next occurrence, counted from when you actually did it.

---

## 4. Milestone 4: The Alarm Spike (Full-Screen Alert)

This is a **throwaway-quality test** with no database. It exists to prove that on your A53 the alarm fires reliably and takes over the screen. The same pipeline gets reused later.

**Pipeline:** `setAlarmClock` → `AlarmReceiver` → `AlarmService` (foreground; plays the alarm sound, vibrates, posts a full-screen notification) → `AlarmActivity` (shows over the lock screen).

**`AndroidManifest.xml`**
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
    <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM"/>
    <uses-permission android:name="android.permission.USE_EXACT_ALARM"/>
    <uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT"/>
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/>
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"/>
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>
    <uses-permission android:name="android.permission.VIBRATE"/>
    <uses-permission android:name="android.permission.WAKE_LOCK"/>
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"/>

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="Day Planner"
        android:theme="@style/Theme.AppCompat.DayNight.NoActionBar">
        <!-- If your wizard theme is different, keep your own theme name here. -->

        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity>

        <activity
            android:name=".alarm.AlarmActivity"
            android:exported="false"
            android:showWhenLocked="true"
            android:turnScreenOn="true"
            android:excludeFromRecents="true"
            android:launchMode="singleTask"/>

        <receiver android:name=".alarm.AlarmReceiver" android:exported="false"/>

        <receiver android:name=".alarm.BootReceiver" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED"/>
                <action android:name="android.intent.action.MY_PACKAGE_REPLACED"/>
                <action android:name="android.intent.action.TIME_SET"/>
                <action android:name="android.intent.action.TIMEZONE_CHANGED"/>
            </intent-filter>
        </receiver>

        <service
            android:name=".alarm.AlarmService"
            android:exported="false"
            android:foregroundServiceType="mediaPlayback"/>
    </application>
</manifest>
```
> Keep the `android:theme` your project wizard generated if it differs, because a wrong theme name will fail the build.

**`alarm/AlarmScheduler.kt`**
```kotlin
package com.yourname.dayplanner.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.yourname.dayplanner.MainActivity

object AlarmScheduler {
    const val EXTRA_TITLE = "title"
    private const val PREFS = "spike"

    fun canSchedule(ctx: Context): Boolean {
        val am = ctx.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
    }

    /** SPIKE: one test alarm, remembered in prefs so BootReceiver can re-arm it. */
    fun schedule(ctx: Context, id: Long, triggerAt: Long, title: String): Boolean {
        if (!canSchedule(ctx)) return false
        val am = ctx.getSystemService(AlarmManager::class.java)

        val fire = PendingIntent.getBroadcast(
            ctx, id.toInt(),
            Intent(ctx, AlarmReceiver::class.java)
                .setData(Uri.parse("alarm://$id"))
                .putExtra(EXTRA_TITLE, title),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val show = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // setAlarmClock = most reliable; exempt from Doze. Shows a small alarm icon in the status bar.
        am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, show), fire)

        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("time", triggerAt).putString("title", title).apply()
        return true
    }

    fun rescheduleFromPrefs(ctx: Context) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val t = p.getLong("time", 0L)
        if (t > System.currentTimeMillis()) schedule(ctx, 1, t, p.getString("title", "Task") ?: "Task")
    }
}
```

**`alarm/AlarmReceiver.kt`**
```kotlin
package com.yourname.dayplanner.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(AlarmScheduler.EXTRA_TITLE) ?: "Task"
        ContextCompat.startForegroundService(
            context,
            Intent(context, AlarmService::class.java).putExtra(AlarmScheduler.EXTRA_TITLE, title)
        )
    }
}
```

**`alarm/BootReceiver.kt`**
```kotlin
package com.yourname.dayplanner.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Real version: run OccurrenceGenerator.topUp() and re-arm all upcoming alarms from the DB.
        AlarmScheduler.rescheduleFromPrefs(context)
    }
}
```

**`alarm/AlarmService.kt`**
```kotlin
package com.yourname.dayplanner.alarm

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.*
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

class AlarmService : Service() {
    companion object {
        const val ACTION_STOP = "stop"
        const val CHANNEL_ID = "alarm_fullscreen"
        const val NOTIF_ID = 42
        private const val AUTO_STOP_MS = 2 * 60_000L
    }

    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopAlarm(); return START_NOT_STICKY }

        val title = intent?.getStringExtra(AlarmScheduler.EXTRA_TITLE) ?: "Task"
        createChannel()

        val activityIntent = Intent(this, AlarmActivity::class.java)
            .putExtra(AlarmScheduler.EXTRA_TITLE, title)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val fullScreenPi = PendingIntent.getActivity(
            this, 0, activityIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPi = PendingIntent.getService(
            this, 1, Intent(this, AlarmService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText("Time to start")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenPi, true)
            .setContentIntent(fullScreenPi)
            .setOngoing(true)
            .addAction(0, "Stop", stopPi)
            .build()

        ServiceCompat.startForeground(
            this, NOTIF_ID, notification,
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        )

        startSound()
        vibrate()

        // Full-screen intents only take over when the phone is locked/off. If you're using the
        // phone, the "Display over other apps" permission lets us launch the screen directly.
        if (Settings.canDrawOverlays(this)) startActivity(activityIntent)

        handler.postDelayed({ stopAlarm() }, AUTO_STOP_MS) // safety: never ring forever in the spike
        return START_NOT_STICKY
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val ch = NotificationChannel(CHANNEL_ID, "Task alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)        // we play our own alarm-stream sound
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(ch)
        }
    }

    private fun startSound() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)   // plays on the alarm volume stream
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            )
            setDataSource(this@AlarmService, uri)
            isLooping = true
            prepare(); start()
        }
    }

    private fun vibrate() {
        val pattern = longArrayOf(0, 600, 400, 600, 400, 600, 1500)
        val effect = VibrationEffect.createWaveform(pattern, 0)
        if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java).defaultVibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).vibrate(effect)
        }
    }

    private fun stopAlarm() {
        handler.removeCallbacksAndMessages(null)
        player?.runCatching { stop(); release() }; player = null
        if (Build.VERSION.SDK_INT >= 31) getSystemService(VibratorManager::class.java).defaultVibrator.cancel()
        else @Suppress("DEPRECATION") (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() { stopAlarm(); super.onDestroy() }
}
```

**`alarm/AlarmActivity.kt`**
```kotlin
package com.yourname.dayplanner.alarm

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val title = intent.getStringExtra(AlarmScheduler.EXTRA_TITLE) ?: "Task"
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) { AlarmScreen(title) { dismiss() } }
            }
        }
    }

    private fun dismiss() {
        startService(Intent(this, AlarmService::class.java).setAction(AlarmService.ACTION_STOP))
        finish()
    }
}

@Composable
private fun AlarmScreen(title: String, onStop: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("TIME TO START", fontSize = 16.sp)
        Spacer(Modifier.height(16.dp))
        Text(title.uppercase(), fontSize = 34.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(48.dp))
        Button(onClick = onStop, modifier = Modifier.fillMaxWidth().height(64.dp)) {
            Text("STOP", fontSize = 20.sp)
        }
    }
}
```

**`MainActivity.kt`** (a test panel that you'll replace later)
```kotlin
package com.yourname.dayplanner

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.yourname.dayplanner.alarm.AlarmScheduler

class MainActivity : ComponentActivity() {
    private var tick by mutableIntStateOf(0)
    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    override fun onResume() { super.onResume(); tick++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { Surface(Modifier.fillMaxSize()) { Panel() } } }
    }

    private fun open(action: String) =
        startActivity(Intent(action, Uri.parse("package:$packageName")))

    @Composable
    private fun Panel() {
        tick // re-read statuses whenever we return to the app
        val nm = getSystemService(NotificationManager::class.java)
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Alarm spike", style = MaterialTheme.typography.headlineSmall)

            Row("Notifications", NotificationManagerCompat.from(this@MainActivity).areNotificationsEnabled()) {
                if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            Row("Exact alarms", AlarmScheduler.canSchedule(this@MainActivity)) {
                if (Build.VERSION.SDK_INT >= 31) open(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            }
            Row("Full-screen intent", Build.VERSION.SDK_INT < 34 || nm.canUseFullScreenIntent()) {
                if (Build.VERSION.SDK_INT >= 34) open(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
            }
            Row("Display over other apps", Settings.canDrawOverlays(this@MainActivity)) {
                open(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            }
            Row("Battery: unrestricted",
                getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)) {
                open(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            }

            Spacer(Modifier.height(12.dp))
            Button(onClick = { schedule(60) }, Modifier.fillMaxWidth()) { Text("Alarm in 1 minute") }
            Button(onClick = { schedule(120) }, Modifier.fillMaxWidth()) { Text("Alarm in 2 min (lock phone now)") }
            Button(onClick = { schedule(600) }, Modifier.fillMaxWidth()) { Text("Alarm in 10 min (then reboot)") }
        }
    }

    @Composable
    private fun Row(label: String, ok: Boolean, onFix: () -> Unit) {
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text((if (ok) "✅ " else "❌ ") + label)
            if (!ok) OutlinedButton(onClick = onFix) { Text("Fix") }
        }
    }

    private fun schedule(seconds: Long) {
        val ok = AlarmScheduler.schedule(this, 1, System.currentTimeMillis() + seconds * 1000, "Study: Chapter 3")
        Toast.makeText(this, if (ok) "Scheduled in ${seconds}s" else "Grant exact alarm permission first", Toast.LENGTH_SHORT).show()
    }
}
```

### Spike Test Checklist (Do All 6 and Report Back)

| # | Test | Pass if |
|---|---|---|
| 1 | Tap "Alarm in 1 minute" and keep the app open | Full-screen alert shows with sound and vibration |
| 2 | "Alarm in 2 min", lock the phone | Screen turns on, alert shows over the lock screen |
| 3 | "Alarm in 1 min", open YouTube or Chrome | Alert takes over the screen (needs the overlay permission) |
| 4 | Swipe the app away from Recents, then schedule an alarm | Still fires |
| 5 | "Alarm in 10 min", reboot, don't open the app | Still fires after reboot (the boot receiver re-arms it) |
| 6 | Schedule an alarm for about 30 minutes ahead, leave the phone idle on the table | Fires on time (Doze test) |

---

## 5. Your Next Steps

1. Create the project and add the Milestone 1 and 2 files.
2. Run the unit tests and fix any failures. This should take about one sitting.
3. Add the Milestone 4 spike, grant all the permissions, and run the 6 tests.
4. **Reply to me with:**
   - Which tests passed or failed (and any error text, copied exactly)
   - Your Android version
   - Whether alarms fired in test 3 (while you were in another app) and test 6 (idle)

Depending on the results, I'll move on to one of these:

| If | Then |
|---|---|
| All pass | **Milestone 3** (add/edit form with presets, progressive options and repeat editor), then **Milestone 5** (permission wizard) |
| Test 3 or 6 fails | Fix the reliability issues first (Samsung-specific tweaks, a watchdog worker). Nothing else matters if alarms are unreliable. |
| Engine tests fail | Fix them before anything else, since every screen depends on the engine |