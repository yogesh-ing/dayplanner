package com.yourname.dayplanner

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourname.dayplanner.data.AppDatabase
import com.yourname.dayplanner.data.Frequency
import com.yourname.dayplanner.data.Item
import com.yourname.dayplanner.data.Preset
import com.yourname.dayplanner.data.RepeatRule
import com.yourname.dayplanner.engine.OccurrenceGenerator
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/* ------------------------------------------------------------ item form */

/** What the form collected; nothing is written until Save. */
internal data class NewItemDraft(
    val title: String,
    val category: String,
    val startMinuteOfDay: Int,
    val durationMin: Int,
    val repeat: RepeatChoice
)

internal enum class RepeatChoice(val label: String) {
    ONCE("Once"),
    DAILY("Daily"),
    WEEKDAYS("Mon–Fri"),
    WEEKLY("Weekly")
}

private val CATEGORIES = listOf("Routine", "Study", "Health", "Project", "Personal", "Growth")
private val DURATIONS = listOf(15, 30, 45, 60, 90, 120)

/** The repeat rule a draft describes. */
private fun ruleFor(draft: NewItemDraft, itemId: Long, startDate: LocalDate): RepeatRule =
    when (draft.repeat) {
        RepeatChoice.ONCE ->
            RepeatRule(itemId = itemId, frequency = Frequency.NONE, startDate = startDate)
        RepeatChoice.DAILY ->
            RepeatRule(itemId = itemId, frequency = Frequency.DAYS, interval = 1, startDate = startDate)
        RepeatChoice.WEEKDAYS ->
            RepeatRule(
                itemId = itemId,
                frequency = Frequency.WEEKLY,
                weekdaysMask = 0b0011111,
                startDate = startDate
            )
        RepeatChoice.WEEKLY ->
            RepeatRule(
                itemId = itemId,
                frequency = Frequency.WEEKLY,
                weekdaysMask = 1 shl (startDate.dayOfWeek.value - 1),
                startDate = startDate
            )
    }

private fun startOfDayMillis(day: LocalDate): Long =
    day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

/**
 * Writes the item and its repeat rule, then materialises occurrences so the new
 * entry shows in the timetable straight away instead of after the next launch.
 */
internal suspend fun addItem(db: AppDatabase, draft: NewItemDraft, startDate: LocalDate) {
    val id = db.itemDao().insert(
        Item(
            title = draft.title.trim(),
            preset = if (draft.repeat == RepeatChoice.ONCE) Preset.ONE_TIME else Preset.ROUTINE,
            category = draft.category,
            startMinuteOfDay = draft.startMinuteOfDay,
            durationMin = draft.durationMin,
            highPriority = draft.category == "Study" || draft.category == "Project"
        )
    )
    db.ruleDao().upsert(ruleFor(draft, id, startDate))
    // Start from the chosen day so a past or future start date still lands.
    OccurrenceGenerator(db).topUp(45, minOf(startDate.atStartOfDay(), LocalDateTime.now()))
}

/**
 * Applies edited details. Days already completed and days already past are left
 * alone, so history keeps the times it actually had; only the pending future is
 * rebuilt, which is what lets a changed time or repeat actually take effect.
 */
internal suspend fun updateItem(
    db: AppDatabase,
    item: Item,
    draft: NewItemDraft,
    ruleStart: LocalDate,
    today: LocalDate
) {
    db.itemDao().update(
        item.copy(
            title = draft.title.trim(),
            preset = if (draft.repeat == RepeatChoice.ONCE) Preset.ONE_TIME else Preset.ROUTINE,
            category = draft.category,
            startMinuteOfDay = draft.startMinuteOfDay,
            durationMin = draft.durationMin,
            highPriority = draft.category == "Study" || draft.category == "Project"
        )
    )
    db.ruleDao().upsert(ruleFor(draft, item.id, ruleStart))
    db.occurrenceDao().deletePendingFrom(item.id, startOfDayMillis(today))
    OccurrenceGenerator(db).topUp(45, minOf(today.atStartOfDay(), LocalDateTime.now()))
}

/**
 * Stops future scheduling while keeping the item and everything already logged.
 * Deactivating the item is what makes the generator skip it from now on.
 */
internal suspend fun stopItem(db: AppDatabase, item: Item, today: LocalDate) {
    db.itemDao().update(item.copy(active = false))
    db.occurrenceDao().deletePendingFrom(item.id, startOfDayMillis(today))
}

/** Removes the item; the schema cascade takes its rule and occurrences with it. */
internal suspend fun deleteItem(db: AppDatabase, item: Item) {
    db.itemDao().delete(item)
}

/** Next :00 or :30, so the default time is never in the past. */
private fun defaultStartMinute(): Int {
    val now = LocalTime.now()
    val minutes = ((now.minute / 30) + 1) * 30
    return ((now.hour + minutes / 60) % 24) * 60 + minutes % 60
}

private fun minuteLabel(minuteOfDay: Int): String =
    String.format(Locale.ENGLISH, "%02d:%02d", (minuteOfDay / 60) % 24, minuteOfDay % 60)

private fun durationLabel(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60}"
}

/** The same form for a new item and for editing one that exists. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ItemFormPanel(
    heading: String,
    dayText: String,
    dayNote: String,
    initial: NewItemDraft,
    onCancel: () -> Unit,
    onSave: (NewItemDraft) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initial.title) }
    var category by remember { mutableStateOf(initial.category) }
    var duration by remember { mutableIntStateOf(initial.durationMin) }
    var repeat by remember { mutableStateOf(initial.repeat) }
    var startMinute by remember { mutableIntStateOf(initial.startMinuteOfDay) }

    val canSave = title.isNotBlank()

    Scaffold(
        containerColor = PageBg,
        topBar = {
            TopAppBar(
                title = { Text(heading, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink) },
                navigationIcon = { TextButton(onClick = onCancel) { Text("Cancel", color = Accent) } },
                actions = {
                    TextButton(
                        onClick = { onSave(NewItemDraft(title.trim(), category, startMinute, duration, repeat)) },
                        enabled = canSave
                    ) {
                        Text(
                            "Save",
                            color = if (canSave) Accent else Muted,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PageBg)
            )
        }
    ) { insets ->
        Column(
            Modifier.fillMaxSize().padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("What is it?") },
                placeholder = { Text("e.g. Read 20 pages") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Field("When") {
                Row(
                    Modifier.fillMaxWidth()
                        .border(1.dp, Hairline, RoundedCornerShape(12.dp))
                        .background(CardBg, RoundedCornerShape(12.dp))
                        .clickable {
                            TimePickerDialog(
                                context,
                                { _, hour, minute -> startMinute = hour * 60 + minute },
                                startMinute / 60,
                                startMinute % 60,
                                true
                            ).show()
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(dayText, fontSize = 14.sp, color = Ink)
                        Text(dayNote, fontSize = 11.sp, color = Muted)
                    }
                    Text(minuteLabel(startMinute), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Accent)
                }
            }

            Field("How long") { ChoiceRow(DURATIONS, duration, { durationLabel(it) }) { duration = it } }
            Field("Category") { ChoiceRow(CATEGORIES, category, { it }) { category = it } }
            Field("Repeats") { ChoiceRow(RepeatChoice.entries, repeat, { it.label }) { repeat = it } }

            Text(
                if (repeat == RepeatChoice.ONCE) "Only on $dayText."
                else "Scheduled ahead, so it shows in the week strip straight away.",
                fontSize = 12.sp,
                color = Muted
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
internal fun NewItemPanel(
    startDate: LocalDate,
    onCancel: () -> Unit,
    onSave: (NewItemDraft) -> Unit
) {
    ItemFormPanel(
        heading = "New item",
        dayText = startDate.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)),
        dayNote = "Starts on the day you were viewing",
        initial = NewItemDraft(
            title = "",
            category = CATEGORIES.first(),
            startMinuteOfDay = defaultStartMinute(),
            durationMin = 30,
            repeat = RepeatChoice.DAILY
        ),
        onCancel = onCancel,
        onSave = onSave
    )
}

@Composable
private fun Field(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label.uppercase(Locale.ENGLISH),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            color = Muted
        )
        content()
    }
}

@Composable
internal fun <T> ChoiceRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Surface(
                shape = RoundedCornerShape(50),
                color = if (isSelected) Accent else CardBg,
                modifier = Modifier
                    .border(1.dp, if (isSelected) Accent else Hairline, RoundedCornerShape(50))
                    .clip(RoundedCornerShape(50))
                    .clickable { onSelect(option) }
            ) {
                Text(
                    label(option),
                    fontSize = 12.sp,
                    color = if (isSelected) Color.White else Ink,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
        }
    }
}
