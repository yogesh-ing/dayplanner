package com.yourname.dayplanner

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourname.dayplanner.data.AppDatabase
import com.yourname.dayplanner.data.Frequency
import com.yourname.dayplanner.data.Item
import com.yourname.dayplanner.data.ItemOccurrence
import com.yourname.dayplanner.data.RepeatRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/* --------------------------------------------------------------- progress */

internal enum class ProgressRange(val label: String) {
    WEEK("This week"),
    MONTH("This month"),
    QUARTER("90 days")
}

/** How a scheduled day went. */
internal enum class DayOutcome { DONE, PARTIAL, SKIPPED, MISSED, UPCOMING, NONE }

/** Number of occurrences of one weekday that were scheduled, and how many were kept. */
internal data class WeekdayStat(val day: DayOfWeek, val done: Int, val planned: Int) {
    val ratio: Float get() = if (planned == 0) 0f else done.toFloat() / planned
}

/**
 * Everything the progress screen shows for one item over one range.
 *
 * `settled` deliberately excludes days still in the future, so adherence is not
 * diluted by work that has not come due yet.
 */
internal data class Progress(
    val item: Item,
    val from: LocalDate,
    val to: LocalDate,
    val done: Int,
    val partial: Int,
    val skipped: Int,
    val missed: Int,
    val upcoming: Int,
    val doneMinutes: Int,
    val actualMinutes: Long?,
    val marks: Map<LocalDate, DayOutcome>,
    val weekdays: List<WeekdayStat>,
    val reasons: List<Pair<String, Int>>
) {
    val completed: Int get() = done + partial
    val settled: Int get() = done + partial + skipped + missed
    val adherence: Float get() = if (settled == 0) 0f else completed.toFloat() / settled
}

internal fun progressRange(range: ProgressRange, today: LocalDate): Pair<LocalDate, LocalDate> =
    when (range) {
        ProgressRange.WEEK -> mondayOf(today) to today
        ProgressRange.MONTH -> today.withDayOfMonth(1) to today
        ProgressRange.QUARTER -> today.minusDays(89) to today
    }

private fun rank(outcome: DayOutcome): Int = when (outcome) {
    DayOutcome.DONE -> 5
    DayOutcome.PARTIAL -> 4
    DayOutcome.SKIPPED -> 3
    DayOutcome.MISSED -> 2
    DayOutcome.UPCOMING -> 1
    DayOutcome.NONE -> 0
}

/** Roll occurrences into per-day outcomes, weekday rates and minute totals. */
internal fun buildProgress(
    item: Item,
    rows: List<ItemOccurrence>,
    range: ProgressRange,
    today: LocalDate,
    now: Long,
    zone: ZoneId
): Progress {
    val (from, to) = progressRange(range, today)
    val marks = HashMap<LocalDate, DayOutcome>()
    var done = 0
    var partial = 0
    var skipped = 0
    var missed = 0
    var upcoming = 0
    var doneMinutes = 0
    var actualMinutes = 0L
    var measured = false
    val weekdayPlanned = IntArray(7)
    val weekdayDone = IntArray(7)
    val reasons = HashMap<String, Int>()

    for (row in rows) {
        val date = Instant.ofEpochMilli(row.scheduledAt).atZone(zone).toLocalDate()
        if (date.isBefore(from) || date.isAfter(to)) continue
        val index = date.dayOfWeek.value - 1
        val outcome = when (row.status) {
            "DONE" -> DayOutcome.DONE
            "PARTIAL" -> DayOutcome.PARTIAL
            "SKIPPED" -> DayOutcome.SKIPPED
            else -> if (date.isBefore(today) || row.scheduledAt <= now) DayOutcome.MISSED
            else DayOutcome.UPCOMING
        }
        val previous = marks[date] ?: DayOutcome.NONE
        if (rank(outcome) > rank(previous)) marks[date] = outcome

        when (outcome) {
            DayOutcome.DONE -> {
                done++; doneMinutes += row.durationMin
                weekdayDone[index]++; weekdayPlanned[index]++
            }
            DayOutcome.PARTIAL -> {
                partial++; doneMinutes += row.durationMin
                weekdayDone[index]++; weekdayPlanned[index]++
            }
            DayOutcome.SKIPPED -> {
                skipped++; weekdayPlanned[index]++
                val reason = row.skipReason
                if (reason != null) reasons[reason] = (reasons[reason] ?: 0) + 1
            }
            DayOutcome.MISSED -> {
                missed++; weekdayPlanned[index]++
            }
            DayOutcome.UPCOMING -> upcoming++
            DayOutcome.NONE -> Unit
        }

        val start = row.actualStart
        val end = row.actualEnd
        if ((outcome == DayOutcome.DONE || outcome == DayOutcome.PARTIAL) &&
            start != null && end != null && end > start
        ) {
            actualMinutes += end - start
            measured = true
        }
    }

    return Progress(
        item = item,
        from = from,
        to = to,
        done = done,
        partial = partial,
        skipped = skipped,
        missed = missed,
        upcoming = upcoming,
        doneMinutes = doneMinutes,
        actualMinutes = if (measured) actualMinutes else null,
        marks = marks,
        weekdays = DayOfWeek.values().map { day ->
            val i = day.value - 1
            WeekdayStat(day, weekdayDone[i], weekdayPlanned[i])
        },
        reasons = reasons.entries
            .sortedByDescending { it.value }
            .map { it.key to it.value }
    )
}

/** Calendar weeks (Monday first) covering [from]..[to], with nulls outside the range. */
internal fun weeksOf(from: LocalDate, to: LocalDate): List<List<LocalDate?>> {
    val weeks = mutableListOf<List<LocalDate?>>()
    var cursor = mondayOf(from)
    while (!cursor.isAfter(to)) {
        weeks += (0..6).map { offset ->
            val day = cursor.plusDays(offset.toLong())
            if (day.isBefore(from) || day.isAfter(to)) null else day
        }
        cursor = cursor.plusWeeks(1)
    }
    return weeks
}

/* ------------------------------------------------------------------- ui */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProgressPanel(itemId: Long, onClose: () -> Unit) {
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    val scope = rememberCoroutineScope()
    var range by remember { mutableStateOf(ProgressRange.MONTH) }
    var progress by remember { mutableStateOf<Progress?>(null) }
    var rule by remember { mutableStateOf<RepeatRule?>(null) }
    var loading by remember { mutableStateOf(true) }
    var editing by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var confirming by remember { mutableStateOf<ManageAction?>(null) }

    LaunchedEffect(itemId, range, refresh) {
        loading = true
        var found: RepeatRule? = null
        val shown = withContext(Dispatchers.IO) {
            val db = AppDatabase.get(context)
            val item = db.itemDao().get(itemId)
            found = db.ruleDao().get(itemId)
            if (item == null) {
                null
            } else {
                val today = LocalDate.now()
                val (from, to) = progressRange(range, today)
                val rows = db.occurrenceDao().itemHistory(
                    itemId,
                    from.atStartOfDay(zone).toInstant().toEpochMilli(),
                    to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                )
                buildProgress(item, rows, range, today, System.currentTimeMillis(), zone)
            }
        }
        rule = found
        progress = shown
        loading = false
    }

    val ready = progress
    if (editing && ready != null) {
        BackHandler { editing = false }
        val ruleStart = rule?.startDate ?: LocalDate.now()
        ItemFormPanel(
            heading = "Edit item",
            dayText = ruleStart.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)),
            dayNote = "Start date stays as it was",
            initial = NewItemDraft(
                title = ready.item.title,
                category = ready.item.category,
                startMinuteOfDay = ready.item.startMinuteOfDay ?: 540,
                durationMin = ready.item.durationMin,
                repeat = choiceOf(rule)
            ),
            onCancel = { editing = false },
            onSave = { draft ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        updateItem(
                            AppDatabase.get(context),
                            ready.item,
                            draft,
                            ruleStart,
                            LocalDate.now()
                        )
                    }
                    editing = false
                    refresh++
                }
            }
        )
        return
    }

    Scaffold(
        containerColor = PageBg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            progress?.item?.title ?: "Progress",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Ink,
                            maxLines = 1
                        )
                        progress?.let {
                            Text(it.item.category, fontSize = 11.sp, color = Muted)
                        }
                    }
                },
                navigationIcon = { TextButton(onClick = onClose) { Text("Back", color = Accent) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PageBg)
            )
        }
    ) { insets ->
        val shown = progress
        if (loading || shown == null) {
            Box(
                Modifier.fillMaxSize().padding(insets),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = Accent) }
        } else {
            Column(
                Modifier.fillMaxSize().padding(insets)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ChoiceRow(ProgressRange.values().toList(), range, { it.label }) { range = it }
                HeadlineCard(shown)
                if (shown.settled == 0 && shown.upcoming == 0) {
                    Caption("Nothing scheduled in this range yet.")
                } else {
                    CalendarCard(shown)
                    WeekdayCard(shown)
                    when {
                        shown.reasons.isNotEmpty() -> ReasonCard(shown)
                        shown.skipped + shown.missed > 0 -> Caption(
                            "${shown.skipped + shown.missed} day" +
                                (if (shown.skipped + shown.missed == 1) "" else "s") +
                                " slipped without a recorded reason."
                        )
                    }
                }
                ManageCard(
                    onEdit = { editing = true },
                    onStop = { confirming = ManageAction.STOP },
                    onDelete = { confirming = ManageAction.DELETE }
                )
                Spacer(Modifier.height(28.dp))
            }
        }
    }

    val pending = confirming
    val target = progress?.item
    if (pending != null && target != null) {
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = {
                Text(
                    if (pending == ManageAction.STOP) "Stop this item?" else "Delete this item?",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink
                )
            },
            text = {
                Text(
                    if (pending == ManageAction.STOP)
                        "It stops appearing on your timetable from today. Everything already logged stays, so this screen keeps its history."
                    else
                        "This removes the item, its repeat rule and every day it was scheduled, including the ones you completed.",
                    fontSize = 13.sp,
                    color = Muted
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirming = null
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            val db = AppDatabase.get(context)
                            if (pending == ManageAction.STOP) stopItem(db, target, LocalDate.now())
                            else deleteItem(db, target)
                        }
                        onClose()
                    }
                }) {
                    Text(
                        if (pending == ManageAction.STOP) "Stop" else "Delete",
                        color = if (pending == ManageAction.STOP) Accent else Danger
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = null }) { Text("Cancel", color = Muted) }
            },
            containerColor = CardBg
        )
    }
}

@Composable
private fun HeadlineCard(progress: Progress) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, Hairline, RoundedCornerShape(18.dp))
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${(progress.adherence * 100).toInt()}%",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink
                )
                Text(
                    " kept",
                    fontSize = 14.sp,
                    color = Muted,
                    modifier = Modifier.padding(bottom = 5.dp, start = 2.dp)
                )
                Spacer(Modifier.weight(1f))
                Surface(color = AccentSoft, shape = RoundedCornerShape(50)) {
                    Text(
                        "${progress.completed} of ${progress.settled}",
                        color = Accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress.adherence },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                color = Good,
                trackColor = Track
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                MiniStat("Kept", durationText(progress.doneMinutes))
                MiniStat("Missed", "${progress.missed}")
                MiniStat("Skipped", "${progress.skipped}")
                MiniStat(
                    "Actual",
                    progress.actualMinutes?.let { durationText((it / 60000L).toInt()) } ?: "—"
                )
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column {
        Text(label, fontSize = 11.sp, color = Muted)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink)
    }
}

@Composable
private fun CalendarCard(progress: Progress) {
    val weeks = remember(progress.from, progress.to) { weeksOf(progress.from, progress.to) }
    val span = progress.from.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)) + " – " +
        progress.to.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, Hairline, RoundedCornerShape(18.dp))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                span.uppercase(Locale.ENGLISH),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                color = Muted
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (day in DayOfWeek.values()) {
                    Text(
                        day.getDisplayName(TextStyle.NARROW, Locale.ENGLISH),
                        fontSize = 9.sp,
                        color = Faint,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            for (week in weeks) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (day in week) {
                        val outcome = day?.let { progress.marks[it] ?: DayOutcome.NONE } ?: DayOutcome.NONE
                        Box(
                            Modifier.weight(1f).aspectRatio(1f)
                                .clip(RoundedCornerShape(5.dp))
                                .background(cellColor(outcome, day != null))
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Legend(Good, "Kept")
                Legend(Danger, "Skipped")
                Legend(Danger.copy(alpha = 0.28f), "Missed")
                Legend(Track, "Ahead")
            }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 10.sp, color = Muted)
    }
}

@Composable
private fun cellColor(outcome: DayOutcome, inRange: Boolean): Color = when (outcome) {
    DayOutcome.DONE -> Good
    DayOutcome.PARTIAL -> Good.copy(alpha = 0.45f)
    DayOutcome.SKIPPED -> Danger
    DayOutcome.MISSED -> Danger.copy(alpha = 0.28f)
    DayOutcome.UPCOMING -> Track
    DayOutcome.NONE -> if (inRange) Hairline else Color.Transparent
}

@Composable
private fun WeekdayCard(progress: Progress) {
    val busiest = progress.weekdays.maxOf { it.planned }
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, Hairline, RoundedCornerShape(18.dp))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "BY WEEKDAY",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                color = Muted
            )
            if (busiest == 0) {
                Text("No completed pattern yet.", fontSize = 12.sp, color = Muted)
            } else {
                for (stat in progress.weekdays) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stat.day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                            fontSize = 11.sp,
                            color = Muted,
                            modifier = Modifier.width(34.dp)
                        )
                        Box(
                            Modifier.weight(1f).height(8.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Track)
                        ) {
                            if (stat.ratio > 0f) {
                                Box(
                                    Modifier.fillMaxWidth(stat.ratio.coerceIn(0f, 1f))
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(50))
                                        .background(Good)
                                )
                            }
                        }
                        Text(
                            if (stat.planned == 0) "—" else "${stat.done}/${stat.planned}",
                            fontSize = 11.sp,
                            color = Faint,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(44.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReasonCard(progress: Progress) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, Hairline, RoundedCornerShape(18.dp))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "WHY IT SLIPPED",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                color = Muted
            )
            for ((reason, count) in progress.reasons) {
                Row(Modifier.fillMaxWidth()) {
                    Text(reasonLabel(reason), fontSize = 13.sp, color = Ink, modifier = Modifier.weight(1f))
                    Text("$count", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Danger)
                }
            }
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(text, fontSize = 12.sp, color = Muted, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
}

private fun reasonLabel(raw: String): String =
    raw.lowercase(Locale.ENGLISH).split('_')
        .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase(Locale.ENGLISH) } }

/* --------------------------------------------------------------- manage */

internal enum class ManageAction { STOP, DELETE }

/** Which of the form's repeat choices an existing rule corresponds to. */
private fun choiceOf(rule: RepeatRule?): RepeatChoice = when {
    rule == null -> RepeatChoice.ONCE
    rule.frequency == Frequency.NONE -> RepeatChoice.ONCE
    rule.frequency == Frequency.DAYS && rule.interval <= 1 -> RepeatChoice.DAILY
    rule.frequency == Frequency.WEEKLY && rule.weekdaysMask == 0b0011111 -> RepeatChoice.WEEKDAYS
    rule.frequency == Frequency.WEEKLY -> RepeatChoice.WEEKLY
    else -> RepeatChoice.DAILY
}

@Composable
private fun ManageCard(onEdit: () -> Unit, onStop: () -> Unit, onDelete: () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, Hairline, RoundedCornerShape(18.dp))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "MANAGE",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                color = Muted
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("Edit") }
                OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f)) { Text("Stop") }
                OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) { Text("Delete") }
            }
            Text(
                "Stopping keeps every day you already logged; deleting does not.",
                fontSize = 11.sp,
                color = Muted
            )
        }
    }
}
