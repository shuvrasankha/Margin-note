package com.margin.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.margin.app.data.NoteEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** A tick in a circle: the habit tracker. */
const val ICON_HABITS = "M12 3a9 9 0 1 1 0 18 9 9 0 0 1 0-18M8 12l3 3 5-6"

/**
 * A habit is a note whose id starts with "habit-": its title is the habit's name and each body line is one day
 * it was done, as an ISO date. So it syncs, searches and goes to Recently Deleted like any note.
 */
fun isHabit(note: NoteEntity) = note.id.startsWith("habit-")

fun habitDays(note: NoteEntity): Set<LocalDate> =
    note.blocks.drop(1).mapNotNull { runCatching { LocalDate.parse(it.text.trim()) }.getOrNull() }.toSet()

/** Days in a row up to [today], or up to yesterday while today isn't ticked yet. */
fun habitStreak(days: Set<LocalDate>, today: LocalDate): Int {
    var d = if (today in days) today else today.minusDays(1)
    var n = 0
    while (d in days) { n++; d = d.minusDays(1) }
    return n
}

/** The next time [at] comes after [now]: today if it's still ahead, else tomorrow. */
fun nextAt(at: java.time.LocalTime, now: java.time.ZonedDateTime): java.time.ZonedDateTime =
    now.with(at).truncatedTo(java.time.temporal.ChronoUnit.MINUTES).let { if (it.isAfter(now)) it else it.plusDays(1) }

/** The habit's blocks with [day] ticked, newest day first; unchanged when it's already ticked. */
fun habitTicked(note: NoteEntity, day: LocalDate): List<com.margin.app.data.Block> =
    listOf(note.blocks.first()) + (habitDays(note) + day).sortedDescending().map { com.margin.app.data.Block(text = it.toString()) }

/** The longest run of days in a row, ever. */
fun habitBest(days: Set<LocalDate>): Int =
    days.filter { it.minusDays(1) !in days }.maxOfOrNull { start -> generateSequence(start) { it.plusDays(1) }.takeWhile { it in days }.count() } ?: 0

/**
 * Every habit with a tick for today, the last week as dots and the streak. Tapping a habit opens a month
 * under it where any past day can be ticked or unticked.
 */
@Composable
fun HabitsScreen(
    notes: List<NoteEntity>, onToggle: (NoteEntity, LocalDate) -> Unit, onAdd: (String) -> Unit, onRename: (NoteEntity, String) -> Unit,
    onDelete: (NoteEntity) -> Unit, onBack: () -> Unit,
    reminders: Map<String, Long> = emptyMap(), onRemind: (NoteEntity, java.time.LocalTime?) -> Unit = { _, _ -> },
) {
    var reminding by remember { mutableStateOf<NoteEntity?>(null) }
    reminding?.let { note ->
        HabitTimeDialog(reminders[note.id], onDismiss = { reminding = null }) { reminding = null; onRemind(note, it) }
    }
    val today = LocalDate.now()
    val habits = remember(notes) { notes.filter(::isHabit).sortedBy { it.createdAt } }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<NoteEntity?>(null) }
    var renaming by remember { mutableStateOf<NoteEntity?>(null) }
    renaming?.let { note -> NameDialog("Rename habit", note.title, onDismiss = { renaming = null }, label = "Habit") { renaming = null; onRename(note, it) } }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    if (adding) NameDialog("New habit", "", onDismiss = { adding = false }, label = "Habit") { adding = false; onAdd(it) }
    deleting?.let { note ->
        MarginDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${note.title}\"?") },
            text = { Text("It moves to Recently Deleted with its ${plural(habitDays(note).size, "day")}.") },
            confirmButton = { TextButton(onClick = { onDelete(note); deleting = null }) { Text("Delete", color = jc.danger) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
    Scaffold(topBar = { NavBar("Folders", onBack) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            PageTitle("Habits")
            if (habits.isEmpty()) Text(
                "Add a habit and tick it off each day.", Modifier.padding(horizontal = 20.dp), style = JType.secondary, color = jc.muted,
            )
            else Text(
                "${habits.count { today in habitDays(it) }} of ${habits.size} done today",
                Modifier.padding(horizontal = 20.dp), style = JType.secondary, color = jc.muted,
            )
            habits.forEach { note ->
                val days = remember(note) { habitDays(note) }
                val streak = habitStreak(days, today)
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) { open = if (open == note.id) null else note.id }
                        .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    CheckCircle(
                        today in days, size = 28.dp,
                        modifier = Modifier.clip(CircleShape).clickable(role = Role.Checkbox) { onToggle(note, today) }
                            .semantics { contentDescription = "${note.title}, today" },
                    )
                    Column(Modifier.weight(1f)) {
                        Text(note.title, style = JType.rowTitle, color = jc.ink)
                        Text(
                            if (streak == 0) "No streak yet" else "${plural(streak, "day")} in a row",
                            style = JType.secondary, color = if (streak == 0) jc.muted else jc.accent,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        for (k in 6 downTo 0) {
                            val d = today.minusDays(k.toLong())
                            Box(Modifier.size(9.dp).clip(CircleShape).background(if (d in days) jc.accent else jc.line))
                        }
                    }
                    IconAction(Ico.TRASH, "Delete ${note.title}", { deleting = note }, color = jc.muted, size = 20.dp)
                }
                if (open == note.id) {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Best ${plural(habitBest(days), "day")} · ${days.count { it > today.minusDays(30) && it <= today }} of the last 30 · ${days.size} in all",
                            Modifier.weight(1f), style = JType.secondary, color = jc.muted,
                        )
                        TextButton(onClick = { renaming = note }) { Text("Rename") }
                    }
                    val time = reminders[note.id]?.let { timeLabel(it) }
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Button) { reminding = note }.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        JIcon(Ico.BELL, if (time != null) jc.accent else jc.muted, size = 18.dp)
                        Text(if (time != null) "Reminds you daily at $time" else "Remind me daily", Modifier.weight(1f), style = JType.secondary, color = if (time != null) jc.ink else jc.muted)
                        Text(if (time != null) "Change" else "Set", style = JType.secondary, color = jc.accent)
                    }
                    HabitMonth(days, today) { onToggle(note, it) }
                }
                HorizontalDivider(Modifier.padding(start = 62.dp), color = jc.line)
            }
            PrimaryButton("New habit", { adding = true }, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp))
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** One month of a habit, whole weeks, with arrows to move; tapping a day up to today ticks or unticks it. */
@Composable
private fun HabitMonth(days: Set<LocalDate>, today: LocalDate, onToggle: (LocalDate) -> Unit) {
    val thisMonth = YearMonth.from(today)
    var month by rememberSaveable { mutableStateOf(thisMonth.toString()) }
    val shown = YearMonth.parse(month)
    val locale = Locale.getDefault()
    val firstDay = WeekFields.of(locale).firstDayOfWeek
    val blanks = (shown.atDay(1).dayOfWeek.value - firstDay.value + 7) % 7
    Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                shown.month.getDisplayName(TextStyle.FULL_STANDALONE, locale) + " " + shown.year,
                Modifier.weight(1f), style = JType.subheading, color = jc.ink,
            )
            IconAction(Ico.BACK, "Previous month", { month = shown.minusMonths(1).toString() })
            IconAction(Ico.CHEVRON, "Next month", { month = shown.plusMonths(1).toString() }, enabled = shown < thisMonth)
        }
        Row(Modifier.padding(bottom = 2.dp)) {
            for (k in 0 until 7) {
                Text(
                    DayOfWeek.of((firstDay.value - 1 + k) % 7 + 1).getDisplayName(TextStyle.SHORT_STANDALONE, locale).uppercase(locale),
                    Modifier.weight(1f), style = JType.section.copy(fontSize = 11.sp, letterSpacing = 0.06.em), color = jc.muted, textAlign = TextAlign.Center,
                )
            }
        }
        val first = shown.atDay(1).minusDays(blanks.toLong())
        for (w in 0 until (blanks + shown.lengthOfMonth() + 6) / 7) {
            val week = (0 until 7).map { first.plusDays(w * 7L + it) }
            Row(Modifier.padding(vertical = 2.dp).clip(JShape.sm).then(if (today in week) Modifier.background(jc.surface) else Modifier)) {
                week.forEach { day ->
                    Box(Modifier.weight(1f).height(46.dp), contentAlignment = Alignment.Center) {
                        DayCell(day, day == today, day > today, YearMonth.from(day) != shown, day in days) { onToggle(day) }
                    }
                }
            }
        }
    }
}

@Composable
private fun timeLabel(at: Long): String {
    val context = androidx.compose.ui.platform.LocalContext.current
    return android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(at))
}

/** Picks the time of day a habit's daily reminder rings; Off removes it. Skipped on days it's already ticked. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun HabitTimeDialog(current: Long?, onDismiss: () -> Unit, onPick: (java.time.LocalTime?) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val start = current?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalTime() } ?: java.time.LocalTime.of(20, 0)
    val state = androidx.compose.material3.rememberTimePickerState(start.hour, start.minute, is24Hour = android.text.format.DateFormat.is24HourFormat(context))
    MarginDialog(
        onDismissRequest = onDismiss, containerColor = jc.bg,
        title = { Text("Remind me daily") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.material3.TimePicker(state, colors = marginTimeColors())
                Text("Not on days you've already ticked it.", style = JType.small, color = jc.muted)
            }
        },
        confirmButton = { TextButton(onClick = { onPick(java.time.LocalTime.of(state.hour, state.minute)) }) { Text("Set") } },
        dismissButton = {
            Row {
                if (current != null) TextButton(onClick = { onPick(null) }) { Text("Off", color = jc.danger) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
