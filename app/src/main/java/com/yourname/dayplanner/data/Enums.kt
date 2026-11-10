package com.yourname.dayplanner.data

enum class Preset { ROUTINE, PROJECT, MICRO, INTERVAL, ONE_TIME, EVENT, DEADLINE, CHECKLIST, INBOX }
enum class SoundMode { SOUND_VIBRATE, VIBRATE, SOUND, SILENT }
enum class Frequency { NONE, MINUTES, HOURS, DAYS, WEEKLY, MONTHLY, YEARLY }
enum class RepeatMode { FIXED, AFTER_COMPLETION }
enum class MonthRule { DAY_OF_MONTH, NTH_WEEKDAY, LAST_DAY }
enum class EndType { NEVER, ON_DATE, AFTER_COUNT }
enum class OccStatus { PENDING, DONE, PARTIAL, SKIPPED, NO_RESPONSE }
enum class SkipReason { TIRED, NO_TIME, FORGOT, DISTRACTED, UNPLANNED, OTHER }