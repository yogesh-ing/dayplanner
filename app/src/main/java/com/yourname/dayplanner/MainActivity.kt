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
            Text((if (ok) "✅ " else "") + label)
            if (!ok) OutlinedButton(onClick = onFix) { Text("Fix") }
        }
    }

    private fun schedule(seconds: Long) {
        val ok = AlarmScheduler.schedule(this, 1, System.currentTimeMillis() + seconds * 1000, "Study: Chapter 3")
        Toast.makeText(this, if (ok) "Scheduled in ${seconds}s" else "Grant exact alarm permission first", Toast.LENGTH_SHORT).show()
    }
}