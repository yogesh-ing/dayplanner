package com.yourname.dayplanner.engine

import com.yourname.dayplanner.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Unit tests for [RepeatEngine].
 *
 * These cover the recurrence maths that the alarm system depends on:
 * day-based repeats, weekday masks, sub-day intervals and after-completion rules.
 */
class RepeatEngineTest {

    private fun bit(d: DayOfWeek): Int = 1 shl (d.value - 1)

    private fun rule(
        frequency: Frequency,
        interval: Int = 1,
        startDate: LocalDate = LocalDate.of(2024, 1, 1),
        times: List<Int> = listOf(540),
        weekdaysMask: Int = 0,
        monthRule: MonthRule = MonthRule.DAY_OF_MONTH,
        dayOfMonth: Int? = null,
        nthWeek: Int = 1,
        mode: RepeatMode = RepeatMode.FIXED
    ) = RepeatRule(
        itemId = 1L,
        frequency = frequency,
        interval = interval,
        weekdaysMask = weekdaysMask,
        monthRule = monthRule,
        dayOfMonth = dayOfMonth,
        nthWeek = nthWeek,
        timesOfDay = times,
        windowStartMin = null,
        windowEndMin = null,
        mode = mode,
        startDate = startDate,
        endType = EndType.NEVER,
        endDate = null,
        endCount = null,
        pausedUntil = null
    )

    @Test
    fun `daily repeat produces one occurrence per day`() {
        val r = rule(Frequency.DAYS, startDate = LocalDate.of(2024, 1, 1))
        val from = LocalDateTime.of(2024, 1, 1, 0, 0)
        val to = from.plusDays(4)

        val occurrences = RepeatEngine.occurrences(r, 540, from, to)

        assertEquals(4, occurrences.size)
        assertEquals(LocalDateTime.of(2024, 1, 1, 9, 0), occurrences[0])
        assertEquals(LocalDateTime.of(2024, 1, 4, 9, 0), occurrences[3])
    }

    @Test
    fun `daily repeat every three days skips the right days`() {
        val r = rule(Frequency.DAYS, interval = 3, startDate = LocalDate.of(2024, 1, 1))
        val from = LocalDateTime.of(2024, 1, 1, 0, 0)
        val to = from.plusDays(10)

        val occurrences = RepeatEngine.occurrences(r, 540, from, to)

        assertEquals(
            listOf(
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 1, 4),
                LocalDate.of(2024, 1, 7),
                LocalDate.of(2024, 1, 10)
            ),
            occurrences.map { it.toLocalDate() }
        )
    }

    @Test
    fun `weekly repeat honours the weekday mask`() {
        val mondayAndFriday = bit(DayOfWeek.MONDAY) or bit(DayOfWeek.FRIDAY)
        val r = rule(
            Frequency.WEEKLY,
            startDate = LocalDate.of(2024, 1, 1), // Monday
            times = listOf(600, 660),
            weekdaysMask = mondayAndFriday
        )
        val from = LocalDateTime.of(2024, 1, 1, 0, 0)
        val to = from.plusWeeks(2)

        val occurrences = RepeatEngine.occurrences(r, 540, from, to)

        assertTrue(occurrences.isNotEmpty())
        assertTrue(
            occurrences.all { it.dayOfWeek == DayOfWeek.MONDAY || it.dayOfWeek == DayOfWeek.FRIDAY }
        )
        // Two weekdays x two times per day x two weeks
        assertEquals(8, occurrences.size)
    }

    @Test
    fun `minutes frequency steps by the interval`() {
        val r = rule(
            Frequency.MINUTES,
            interval = 30,
            startDate = LocalDate.of(2024, 1, 1),
            times = emptyList()
        )
        val from = LocalDateTime.of(2024, 1, 1, 9, 0)
        val to = from.plusMinutes(180)

        val occurrences = RepeatEngine.occurrences(r, 540, from, to)

        assertEquals(6, occurrences.size)
        assertEquals(LocalDateTime.of(2024, 1, 1, 9, 0), occurrences[0])
        assertEquals(30, occurrences[1].minute)
        assertEquals(LocalDateTime.of(2024, 1, 1, 11, 30), occurrences[5])
    }

    @Test
    fun `hours frequency steps by the interval`() {
        val r = rule(
            Frequency.HOURS,
            interval = 2,
            startDate = LocalDate.of(2024, 1, 1),
            times = listOf(480) // anchor the sub-day grid at 08:00
        )
        val from = LocalDateTime.of(2024, 1, 1, 8, 0)
        val to = from.plusHours(8)

        val occurrences = RepeatEngine.occurrences(r, 540, from, to)

        assertEquals(listOf(8, 10, 12, 14), occurrences.map { it.hour })
    }

    @Test
    fun `monthly repeat on a fixed day of month`() {
        val r = rule(
            Frequency.MONTHLY,
            startDate = LocalDate.of(2024, 1, 15),
            times = listOf(540),
            dayOfMonth = 15
        )
        val from = LocalDateTime.of(2024, 1, 1, 0, 0)
        val to = LocalDateTime.of(2024, 4, 30, 0, 0)

        val occurrences = RepeatEngine.occurrences(r, 540, from, to)

        assertEquals(
            listOf(
                LocalDate.of(2024, 1, 15),
                LocalDate.of(2024, 2, 15),
                LocalDate.of(2024, 3, 15),
                LocalDate.of(2024, 4, 15)
            ),
            occurrences.map { it.toLocalDate() }
        )
    }

    @Test
    fun `after completion schedules from the last completion time`() {
        val r = rule(
            Frequency.DAYS,
            interval = 3,
            startDate = LocalDate.of(2024, 1, 1),
            mode = RepeatMode.AFTER_COMPLETION
        )
        val lastDone = LocalDateTime.of(2024, 1, 5, 7, 30)

        val next = RepeatEngine.nextAfterCompletion(r, 540, lastDone)

        // Three days after completion, snapped to the configured 9:00 time
        assertEquals(LocalDateTime.of(2024, 1, 8, 9, 0), next)
    }

    @Test
    fun `after completion without history starts on the start date`() {
        val r = rule(
            Frequency.DAYS,
            interval = 3,
            startDate = LocalDate.of(2024, 1, 1),
            mode = RepeatMode.AFTER_COMPLETION
        )

        val next = RepeatEngine.nextAfterCompletion(r, 540, null)

        assertEquals(LocalDateTime.of(2024, 1, 1, 9, 0), next)
    }

    @Test
    fun `one time rule fires only on its start date`() {
        val r = rule(Frequency.NONE, startDate = LocalDate.of(2024, 3, 4))
        val from = LocalDateTime.of(2024, 3, 1, 0, 0)
        val to = from.plusDays(10)

        val occurrences = RepeatEngine.occurrences(r, 540, from, to)

        assertEquals(1, occurrences.size)
        assertEquals(LocalDateTime.of(2024, 3, 4, 9, 0), occurrences[0])
    }

    @Test
    fun `empty range produces no occurrences`() {
        val r = rule(Frequency.DAYS, startDate = LocalDate.of(2024, 1, 1))
        val at = LocalDateTime.of(2024, 1, 1, 0, 0)

        val occurrences = RepeatEngine.occurrences(r, 540, at, at)

        assertTrue(occurrences.isEmpty())
    }

    @Test
    fun `start date is honoured even when the window starts earlier`() {
        val r = rule(Frequency.DAYS, startDate = LocalDate.of(2024, 6, 1))
        val from = LocalDateTime.of(2024, 5, 1, 0, 0)
        val to = LocalDateTime.of(2024, 6, 3, 0, 0)

        val occurrences = RepeatEngine.occurrences(r, 540, from, to)

        assertEquals(
            listOf(LocalDate.of(2024, 6, 1), LocalDate.of(2024, 6, 2)),
            occurrences.map { it.toLocalDate() }
        )
        assertTrue(occurrences.all { it.toLocalTime() == LocalTime.of(9, 0) })
    }
}
