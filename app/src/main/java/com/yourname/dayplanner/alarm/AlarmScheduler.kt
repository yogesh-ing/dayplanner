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