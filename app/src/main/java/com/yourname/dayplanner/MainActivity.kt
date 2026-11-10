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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.yourname.dayplanner.alarm.AlarmScheduler
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/* ---------------------------------------------------------------- palette */

private val Ink = Color(0xFF101828)
private val Muted = Color(0xFF667085)
private val Hairline = Color(0xFFE4E7EC)
private val PageBg = Color(0xFFF7F8FA)
private val Accent = Color(0xFF4F46E5)
private val AccentSoft = Color(0xFFEEF0FF)
private val Good = Color(0xFF12B76A)

private fun categoryColor(category: String): Color = when (category) {
    "Study" -> Color(0xFF4F46E5)
    "Routine" -> Color(0xFF0EA5E9)
    "Health" -> Color(0xFF12B76A)
    "Project" -> Color(0xFFF79009)
    "Personal" -> Color(0xFFEE46BC)
    "Growth" -> Color(0xFF7A5AF8)
    else -> Color(0xFF667085)
}

/* ------------------------------------------------------------ demo data */

/**
 * Placeholder timetable so the screen can be reviewed before it is backed by
 * the Room occurrence query. Swap this list for the DAO flow later.
 */
private data class Slot(
    val time: String,
    val endTime: String,
    val title: String,
    val category: String,
    val repeat: String,
    val duration: String,
    val done: Boolean = false,
    val isNext: Boolean = false
)

private val demoTimetable = listOf(
    Slot("06:45", "07:15", "Morning routine", "Routine", "Daily", "30 min", done = true),
    Slot("07:30", "09:00", "Data structures: graphs", "Study", "Mon-Fri", "1 h 30", done = true),
    Slot("09:15", "09:45", "Breakfast with family", "Personal", "Daily", "30 min", done = true),
    Slot("10:00", "11:00", "Maturity Ledge: layout fixes", "Project", "Mon, Wed, Fri", "1 h", isNext = true),
    Slot("11:10", "11:25", "Stretch + water", "Health", "Every 90 min", "15 min"),
    Slot("12:30", "13:15", "Lunch", "Personal", "Daily", "45 min"),
    Slot("14:00", "15:30", "Compose: state hoisting", "Study", "Mon-Fri", "1 h 30"),
    Slot("16:00", "16:30", "Walk outside", "Health", "Daily", "30 min"),
    Slot("18:30", "19:00", "Evening review", "Growth", "Daily", "30 min"),
    Slot("21:30", "22:00", "Journal + plan tomorrow", "Routine", "Daily", "30 min")
)

/* --------------------------------------------------------------- activity */

class MainActivity : ComponentActivity() {
    private var tick by mutableIntStateOf(0)
    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    override fun onResume() { super.onResume(); tick++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(background = PageBg, surface = Color.White)) {
                Surface(Modifier.fillMaxSize(), color = PageBg) {
                    var setup by remember { mutableStateOf(false) }
                    if (setup) {
                        BackHandler { setup = false }
                        SetupPanel(onBack = { setup = false })
                    } else {
                        Timetable(onSetup = { setup = true })
                    }
                }
            }
        }
    }

    private fun open(action: String) =
        startActivity(Intent(action, Uri.parse("package:$packageName")))

    /* --------------------------------------------------------- timetable */

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Timetable(onSetup: () -> Unit) {
        val today = remember {
            LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH))
        }
        val done = demoTimetable.count { it.done }
        val total = demoTimetable.size

        Scaffold(
            containerColor = PageBg,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Today", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                            Text(today, fontSize = 12.sp, color = Muted)
                        }
                    },
                    actions = {
                        TextButton(onClick = onSetup) {
                            Text("Alarm setup", color = Accent, fontSize = 13.sp)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = PageBg)
                )
            }
        ) { insets ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(insets),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { SummaryCard(done, total) }
                item {
                    Text(
                        "SCHEDULE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        color = Muted,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
                    )
                }
                items(demoTimetable) { slot -> TimelineRow(slot) }
            }
        }
    }

    @Composable
    private fun SummaryCard(done: Int, total: Int) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(0.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, Hairline, RoundedCornerShape(18.dp))
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$done", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text(
                        " of $total done",
                        fontSize = 14.sp,
                        color = Muted,
                        modifier = Modifier.padding(bottom = 5.dp, start = 2.dp)
                    )
                    Spacer(Modifier.weight(1f))
                    Surface(color = AccentSoft, shape = RoundedCornerShape(50)) {
                        Text(
                            "${total - done} left",
                            color = Accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { done.toFloat() / total },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                    color = Accent,
                    trackColor = Color(0xFFEDEFF3)
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    Stat("Planned", "6 h 30")
                    Stat("Focus", "3 h")
                    Stat("Next", "10:00")
                }
            }
        }
    }

    @Composable
    private fun Stat(label: String, value: String) {
        Column {
            Text(label, fontSize = 11.sp, color = Muted)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        }
    }

    @Composable
    private fun TimelineRow(slot: Slot) {
        val color = categoryColor(slot.category)
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Column(Modifier.width(46.dp).padding(top = 14.dp), horizontalAlignment = Alignment.End) {
                Text(
                    slot.time,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (slot.isNext) Accent else Muted
                )
                Text(slot.endTime, fontSize = 10.sp, color = Color(0xFF98A2B3))
            }
            Column(
                Modifier.width(26.dp).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier.padding(top = 18.dp)
                        .size(if (slot.isNext) 11.dp else 8.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Box(Modifier.width(2.dp).weight(1f).background(Hairline))
            }
            Spacer(Modifier.width(6.dp))
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .border(
                        width = if (slot.isNext) 2.dp else 1.dp,
                        color = if (slot.isNext) Accent else Hairline,
                        shape = RoundedCornerShape(14.dp)
                    )
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            slot.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (slot.done) Color(0xFF98A2B3) else Ink,
                            textDecoration = if (slot.done) TextDecoration.LineThrough else TextDecoration.None,
                            modifier = Modifier.weight(1f)
                        )
                        if (slot.isNext) {
                            Surface(color = Accent, shape = RoundedCornerShape(50), modifier = Modifier.padding(top = 3.dp)) {
                                Text(
                                    "NEXT",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        } else if (slot.done) {
                            Box(
                                Modifier.size(18.dp).clip(CircleShape).background(Good.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) { Text("✓", fontSize = 11.sp, color = Good) }
                        }
                    }
                    Spacer(Modifier.height(7.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = color.copy(alpha = 0.10f), shape = RoundedCornerShape(50)) {
                            Text(
                                slot.category,
                                color = color,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("\u21BB ${slot.repeat}", fontSize = 11.sp, color = Muted)
                        Spacer(Modifier.weight(1f))
                        Text(slot.duration, fontSize = 11.sp, color = Color(0xFF98A2B3))
                    }
                }
            }
        }
    }

    /* ------------------------------------------------------- alarm spike */

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SetupPanel(onBack: () -> Unit) {
        tick // re-read statuses whenever we return to the app
        val nm = getSystemService(NotificationManager::class.java)
        Scaffold(
            containerColor = PageBg,
            topBar = {
                TopAppBar(
                    title = { Text("Alarm setup", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink) },
                    navigationIcon = { TextButton(onClick = onBack) { Text("Back", color = Accent) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = PageBg)
                )
            }
        ) { insets ->
            Column(
                Modifier.fillMaxSize().padding(insets).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "These permissions decide whether a reminder actually reaches you on a locked or sleeping phone.",
                    fontSize = 13.sp,
                    color = Muted
                )
                Spacer(Modifier.height(4.dp))

                PermissionRow("Notifications", NotificationManagerCompat.from(this@MainActivity).areNotificationsEnabled()) {
                    if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                PermissionRow("Exact alarms", AlarmScheduler.canSchedule(this@MainActivity)) {
                    if (Build.VERSION.SDK_INT >= 31) open(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                }
                PermissionRow("Full-screen intent", Build.VERSION.SDK_INT < 34 || nm.canUseFullScreenIntent()) {
                    if (Build.VERSION.SDK_INT >= 34) open(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                }
                PermissionRow("Display over other apps", Settings.canDrawOverlays(this@MainActivity)) {
                    open(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                }
                PermissionRow(
                    "Battery: unrestricted",
                    getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
                ) { open(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) }

                Spacer(Modifier.height(12.dp))
                Button(onClick = { schedule(60) }, Modifier.fillMaxWidth()) { Text("Alarm in 1 minute") }
                OutlinedButton(onClick = { schedule(120) }, Modifier.fillMaxWidth()) {
                    Text("Alarm in 2 min (lock phone now)")
                }
                OutlinedButton(onClick = { schedule(600) }, Modifier.fillMaxWidth()) {
                    Text("Alarm in 10 min (then reboot)")
                }
            }
        }
    }

    @Composable
    private fun PermissionRow(label: String, ok: Boolean, onFix: () -> Unit) {
        Row(
            Modifier.fillMaxWidth()
                .border(1.dp, Hairline, RoundedCornerShape(12.dp))
                .background(Color.White, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(label, fontSize = 14.sp, color = Ink)
                Text(
                    if (ok) "Granted" else "Not granted",
                    fontSize = 11.sp,
                    color = if (ok) Good else Color(0xFFB42318)
                )
            }
            if (!ok) {
                OutlinedButton(onClick = onFix, contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Text("Fix")
                }
            }
        }
    }

    private fun schedule(seconds: Long) {
        val ok = AlarmScheduler.schedule(this, 1, System.currentTimeMillis() + seconds * 1000, "Study: Chapter 3")
        Toast.makeText(
            this,
            if (ok) "Scheduled in ${seconds}s" else "Grant exact alarm permission first",
            Toast.LENGTH_SHORT
        ).show()
    }
}
