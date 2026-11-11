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
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.yourname.dayplanner.alarm.AlarmScheduler
import com.yourname.dayplanner.data.AppDatabase
import com.yourname.dayplanner.data.DayEntry
import com.yourname.dayplanner.data.DemoSeed
import com.yourname.dayplanner.data.OccStatus
import com.yourname.dayplanner.engine.OccurrenceGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/* -------------------------------------------------------------- categories */

private fun categoryColor(category: String): Color = when (category) {
    "Study" -> Color(0xFF4F46E5)
    "Routine" -> Color(0xFF0EA5E9)
    "Health" -> Color(0xFF12B76A)
    "Project" -> Color(0xFFF79009)
    "Personal" -> Color(0xFFEE46BC)
    "Growth" -> Color(0xFF7A5AF8)
    else -> Color(0xFF667085)
}

/* ---------------------------------------------------------------- helpers */

/** Week starts Monday. */
internal fun mondayOf(day: LocalDate): LocalDate =
    day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

private fun hhmm(minuteOfDay: Int): String =
    String.format(Locale.ENGLISH, "%02d:%02d", (minuteOfDay / 60) % 24, minuteOfDay % 60)

internal fun durationText(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60}"
}

private fun weekdayLabel(mask: Int): String {
    if (mask == 0) return "Weekly"
    if (mask == 0b0011111) return "Mon-Fri"
    if (mask == 0b1111111) return "Daily"
    val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val picked = names.filterIndexed { i, _ -> (mask shr i) and 1 == 1 }
    return if (picked.size <= 3) picked.joinToString(", ") else "${picked.size} days"
}

private fun repeatLabel(frequency: String?, interval: Int, weekdaysMask: Int): String {
    val n = interval.coerceAtLeast(1)
    return when (frequency) {
        "MINUTES" -> "Every $n min"
        "HOURS" -> "Every $n h"
        "DAYS" -> if (n == 1) "Daily" else "Every $n days"
        "WEEKLY" -> weekdayLabel(weekdaysMask)
        "MONTHLY" -> if (n == 1) "Monthly" else "Every $n months"
        "YEARLY" -> "Yearly"
        else -> "Once"
    }
}

/** One row of the timeline, already formatted for display. */
private data class UiSlot(
    val id: Long,
    val itemId: Long,
    val start: String,
    val end: String,
    val title: String,
    val category: String,
    val repeat: String,
    val duration: String,
    val done: Boolean,
    val isNext: Boolean
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
            DayPlannerTheme {
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
        val context = LocalContext.current
        val zone = remember { ZoneId.systemDefault() }
        val today = remember { LocalDate.now() }

        var selected by remember { mutableStateOf(today) }
        var weekEntries by remember { mutableStateOf<List<DayEntry>>(emptyList()) }
        var loaded by remember { mutableStateOf(false) }
        var version by remember { mutableIntStateOf(0) }
        var adding by remember { mutableStateOf(false) }
        var inspecting by remember { mutableStateOf<UiSlot?>(null) }

        val weekStart = mondayOf(selected)
        val scope = rememberCoroutineScope()

        // Seed demo items once, then materialise occurrences from two weeks back onward.
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                val db = AppDatabase.get(context)
                val empty = db.itemDao().allActive().isEmpty()
                DemoSeed.ensure(db)
                OccurrenceGenerator(db).topUp(45, mondayOf(today).minusWeeks(2).atStartOfDay())
                if (empty) DemoSeed.markSampleDone(db)
            }
            version++
        }

        // Everything scheduled in the visible week, reloaded when the week changes.
        LaunchedEffect(weekStart, version) {
            val from = weekStart.atStartOfDay(zone).toInstant().toEpochMilli()
            val to = weekStart.plusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli()
            weekEntries = withContext(Dispatchers.IO) {
                AppDatabase.get(context).occurrenceDao().dayEntries(from, to)
            }
            loaded = true
        }

        // Tapping a row completes it; tapping again clears the completion.
        val toggle: (UiSlot) -> Unit = { slot ->
            scope.launch {
                withContext(Dispatchers.IO) {
                    val dao = AppDatabase.get(context).occurrenceDao()
                    if (slot.done) dao.mark(slot.id, OccStatus.PENDING, null, null)
                    else dao.mark(slot.id, OccStatus.DONE, System.currentTimeMillis(), null)
                }
                version++
            }
        }

        val byDay = remember(weekEntries, zone) {
            weekEntries.groupBy { Instant.ofEpochMilli(it.scheduledAt).atZone(zone).toLocalDate() }
        }
        val dayRows = byDay[selected].orEmpty()
        val now = System.currentTimeMillis()

        val done = dayRows.count { it.status == "DONE" || it.status == "PARTIAL" }
        val total = dayRows.size
        val plannedMin = dayRows.sumOf { it.durationMin }
        val focusMin = dayRows
            .filter { it.category == "Study" || it.category == "Project" }
            .sumOf { it.durationMin }
        val next = if (selected < today) null else dayRows.firstOrNull {
            it.status == "PENDING" && (selected > today || it.scheduledAt >= now)
        }

        val slots = remember(dayRows, next?.id, zone) {
            dayRows.map { e ->
                val start = Instant.ofEpochMilli(e.scheduledAt).atZone(zone).toLocalTime()
                val startMin = start.hour * 60 + start.minute
                UiSlot(
                    id = e.id,
                    itemId = e.itemId,
                    start = hhmm(startMin),
                    end = hhmm(startMin + e.durationMin),
                    title = e.title,
                    category = e.category,
                    repeat = repeatLabel(e.frequency, e.repeatInterval, e.weekdaysMask),
                    duration = durationText(e.durationMin),
                    done = e.status == "DONE" || e.status == "PARTIAL",
                    isNext = next?.id == e.id
                )
            }
        }

        val title = if (selected == today) "Today"
        else selected.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        val subtitle = selected.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH))

        if (adding) {
            BackHandler { adding = false }
            NewItemPanel(
                startDate = selected,
                onCancel = { adding = false },
                onSave = { draft ->
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            addItem(AppDatabase.get(context), draft, selected)
                        }
                        adding = false
                        version++
                    }
                }
            )
            return
        }

        val inspected = inspecting
        if (inspected != null) {
            BackHandler { inspecting = null }
            ProgressPanel(itemId = inspected.itemId, onClose = { inspecting = null; version++ })
            return
        }

        Scaffold(
            containerColor = PageBg,
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { adding = true },
                    containerColor = Accent,
                    contentColor = Color.White,
                    icon = { Text("+", fontSize = 20.sp) },
                    text = { Text("New item", fontSize = 14.sp, fontWeight = FontWeight.Medium) }
                )
            },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                            Text(subtitle, fontSize = 12.sp, color = Muted)
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
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { WeekStrip(weekStart, selected, byDay, onWeek = { selected = selected.plusWeeks(it) }) { selected = it } }
                item { SummaryCard(done, total, plannedMin, focusMin, if (next == null) "—" else hhmm(timeOf(next.scheduledAt, zone))) }
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
                if (loaded && slots.isEmpty()) {
                    item { EmptyDay() }
                }
                items(slots, key = { it.id }) { slot ->
                    TimelineRow(slot, onToggle = { toggle(slot) }, onOpen = { inspecting = slot })
                }
            }
        }
    }

    @Composable
    private fun WeekStrip(
        weekStart: LocalDate,
        selected: LocalDate,
        byDay: Map<LocalDate, List<DayEntry>>,
        onWeek: (Long) -> Unit,
        onSelect: (LocalDate) -> Unit
    ) {
        val range = weekStart.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)) + " – " +
            weekStart.plusDays(6).format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))
        Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    range.uppercase(Locale.ENGLISH),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    color = Muted,
                    modifier = Modifier.weight(1f)
                )
                Chevron("‹") { onWeek(-1) }
                Spacer(Modifier.width(4.dp))
                Chevron("›") { onWeek(1) }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 0..6) {
                    val day = weekStart.plusDays(i.toLong())
                    val isSelected = day == selected
                    val rows = byDay[day].orEmpty()
                    val allDone = rows.isNotEmpty() && rows.all { it.status == "DONE" || it.status == "PARTIAL" }
                    Column(
                        Modifier.weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) Accent else CardBg)
                            .border(1.dp, if (isSelected) Accent else Hairline, RoundedCornerShape(12.dp))
                            .clickable { onSelect(day) }
                            .padding(vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.ENGLISH),
                            fontSize = 10.sp,
                            color = if (isSelected) Color.White.copy(alpha = 0.85f) else Muted
                        )
                        Text(
                            "${day.dayOfMonth}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSelected) Color.White else Ink
                        )
                        Box(
                            Modifier.padding(top = 3.dp).size(4.dp).clip(CircleShape).background(
                                when {
                                    isSelected -> Color.White
                                    rows.isEmpty() -> Color.Transparent
                                    allDone -> Good
                                    else -> Accent.copy(alpha = 0.5f)
                                }
                            )
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun Chevron(glyph: String, onClick: () -> Unit) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) { Text(glyph, fontSize = 18.sp, color = Muted) }
    }

    @Composable
    private fun EmptyDay() {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            elevation = CardDefaults.cardElevation(0.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, Hairline, RoundedCornerShape(14.dp))
        ) {
            Text(
                "Nothing planned for this day.",
                fontSize = 13.sp,
                color = Muted,
                modifier = Modifier.padding(16.dp)
            )
        }
    }

    @Composable
    private fun SummaryCard(done: Int, total: Int, plannedMin: Int, focusMin: Int, nextAt: String) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg),
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
                    progress = { if (total == 0) 0f else done.toFloat() / total },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                    color = Accent,
                    trackColor = Track
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    Stat("Planned", durationText(plannedMin))
                    Stat("Focus", durationText(focusMin))
                    Stat("Next", nextAt)
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
    private fun TimelineRow(slot: UiSlot, onToggle: () -> Unit, onOpen: () -> Unit) {
        val color = categoryColor(slot.category)
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Column(Modifier.width(46.dp).padding(top = 14.dp), horizontalAlignment = Alignment.End) {
                Text(
                    slot.start,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (slot.isNext) Accent else Muted
                )
                Text(slot.end, fontSize = 10.sp, color = Faint)
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
                colors = CardDefaults.cardColors(containerColor = CardBg),
                elevation = CardDefaults.cardElevation(0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onToggle)
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
                            color = if (slot.done) Faint else Ink,
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
                        Text(slot.duration, fontSize = 11.sp, color = Faint)
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = AccentSoft,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable(onClick = onOpen)
                        ) {
                            Text(
                                "Stats",
                                color = Accent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
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
                .background(CardBg, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(label, fontSize = 14.sp, color = Ink)
                Text(
                    if (ok) "Granted" else "Not granted",
                    fontSize = 11.sp,
                    color = if (ok) Good else Danger
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

/** Minute of day for an epoch-millis timestamp in the given zone. */
private fun timeOf(epochMillis: Long, zone: ZoneId): Int {
    val t = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalTime()
    return t.hour * 60 + t.minute
}
