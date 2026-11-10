package com.yourname.dayplanner.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

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
