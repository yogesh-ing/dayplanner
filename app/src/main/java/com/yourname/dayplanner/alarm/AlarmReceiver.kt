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