# Day Planner

An Android day planner built around reliable, repeatable task alarms. Everything in the app is oriented
around one question: *when the reminder fires, does it actually reach the person, even on a locked,
sleeping, or battery-optimised phone?*

The app is a working implementation of the specification in [`plan-insintial.md`](plan-insintial.md), built
in milestones: schema + repeat engine first, then a hard alarm spike, then the editing UI.

## Highlights

- **Repeat engine** — pure Kotlin, no Android dependencies, fully unit tested. Supports fixed schedules
  (daily / weekly with weekday masks / monthly with day-of-month, nth-weekday or last-day rules / yearly /
  minute and hour intervals) and *after completion* schedules (e.g. "water the plants 3 days after I last
  did it") with end-by-date, end-after-count and pause-until constraints.
- **Alarm spike** — `AlarmManager.setAlarmClock()` scheduling (Doze exempt), foreground service with a
  looping alarm stream, vibration waveform, a full-screen intent that shows over the lock screen, and
  re-arming after reboot, time change or app update.
- **Occurrence materialisation** — repeat rules are expanded into concrete `Occurrence` rows in Room so the
  app can show a real agenda and record what actually happened (done / partial / skipped + skip reason).
- **Permission wizard** — one screen that reports the real status of every permission an alarm needs, with
  a direct jump to the exact system settings page for each one.

## Requirements

- Android Studio (Koala or newer) or a command line JDK 17
- Android SDK Platform 34, Build-Tools 34.0.0
- `minSdk 26`, `targetSdk 34`, `compileSdk 34`

## Build and run

```bash
# From the project root
./gradlew assembleDebug        # debug APK -> app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # repeat engine unit tests
./gradlew installDebug         # install on a connected device or emulator
```

Or open the folder in Android Studio and press Run.

`local.properties` is machine specific and is not committed. Android Studio writes it automatically; for a
command line build create it with:

```
sdk.dir=/path/to/Android/Sdk
```

## Project layout

```
app/src/main/java/com/yourname/dayplanner/
├── MainActivity.kt              # permission wizard + alarm spike controls
├── data/
│   ├── Enums.kt                 # presets, frequencies, repeat modes, statuses
│   ├── Entities.kt              # Item, RepeatRule, ReminderOffset, ChecklistEntry,
│   │                            # Occurrence, DailyReview, WeeklyReview
│   ├── Converters.kt            # LocalDate and Int/Long list converters
│   ├── AppDatabase.kt           # Room database
│   ├── ItemDao.kt               # read/write items
│   ├── RuleDao.kt               # repeat rules
│   └── OccurrenceDao.kt         # agenda queries + status updates
├── engine/
│   ├── RepeatEngine.kt          # recurrence maths (pure Kotlin)
│   └── OccurrenceGenerator.kt   # materialises rules into occurrences
└── alarm/
    ├── AlarmScheduler.kt        # setAlarmClock scheduling + persisted spike alarm
    ├── AlarmReceiver.kt         # broadcast -> foreground service
    ├── AlarmService.kt          # sound, vibration, full-screen notification
    ├── AlarmActivity.kt         # lock-screen alarm screen
    └── BootReceiver.kt          # re-arms after reboot / time change

app/src/test/java/com/yourname/dayplanner/engine/RepeatEngineTest.kt
```

## Permissions

| Permission | Why |
| --- | --- |
| `POST_NOTIFICATIONS` | Android 13+ requires it before any alarm notification can appear |
| `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` | exact delivery on Android 12+ |
| `USE_FULL_SCREEN_INTENT` | alarm screen over the lock screen |
| `SYSTEM_ALERT_WINDOW` | bring the alarm screen up when the app is in the background |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | foreground service that plays the alarm |
| `RECEIVE_BOOT_COMPLETED` | re-arm alarms after a reboot |
| `VIBRATE`, `WAKE_LOCK` | vibration and waking the device |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | keep the alarm alive under Doze |

## Alarm spike test checklist

Run these on a real device (a Samsung A53 was the reference device) before trusting the app:

1. **Foreground** — schedule "Alarm in 1 minute" with the app open. Sound + vibration + screen appear.
2. **Lock screen** — schedule 2 minutes, lock the phone. The alarm screen appears over the keyguard and
   sounds even in silent mode (alarms use the alarm audio stream).
3. **Doze** — set battery usage to *Unrestricted*, leave the phone still and unplugged for 10 minutes, then
   confirm the alarm still fires.
4. **Background** — schedule, swipe the app away, wait. The alarm must still fire.
5. **Reboot** — schedule 10 minutes out, reboot, unlock once. The alarm is re-armed by `BootReceiver`.
6. **OEM battery manager** — on Samsung, set the app to *Never sleeping apps* and confirm alarms survive a
   few hours of idle time.

Record device model, Android version and the result of each scenario.

## Licence

Personal project; no licence file yet.
