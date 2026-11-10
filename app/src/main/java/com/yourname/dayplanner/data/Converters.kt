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