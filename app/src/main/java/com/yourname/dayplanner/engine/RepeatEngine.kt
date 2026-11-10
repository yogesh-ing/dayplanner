package com.yourname.dayplanner.engine

import com.yourname.dayplanner.data.*
import java.time.*
import java.time.temporal.ChronoUnit
import kotlin.math.min

object RepeatEngine {
    private const val MAX_RESULTS = 2000

    /** Mask helper: Monday = bit0 ... Sunday = bit6 */
    fun bit(d: DayOfWeek): Int = 1 shl (d.value - 1)

    /** Occurrences in [from, to). */
    @Suppress("unused")
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
                    d.dayOfMonth == min(start.dayOfMonth, d.lengthOfMonth())
            }
            else -> false
        }
    }

    private fun monthDayMatches(rule: RepeatRule, start: LocalDate, d: LocalDate): Boolean {
        return when (rule.monthRule) {
            MonthRule.DAY_OF_MONTH ->
                d.dayOfMonth == min(rule.dayOfMonth ?: start.dayOfMonth, d.lengthOfMonth())
            MonthRule.LAST_DAY -> d.dayOfMonth == d.lengthOfMonth()
            MonthRule.NTH_WEEKDAY -> {
                val wd = firstWeekday(rule.weekdaysMask) ?: start.dayOfWeek
                d.dayOfWeek == wd && if (rule.nthWeek > 0) {
                    (d.dayOfMonth - 1) / 7 + 1 == rule.nthWeek
                } else {
                    d.plusDays(7).month != d.month
                }
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
            val endOffset = if (we >= ws) we else we + 1440
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