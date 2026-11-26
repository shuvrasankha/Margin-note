package com.margin.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.graphics.graphicsLayer
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
import com.margin.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource

/** A tick in a circle: the habit tracker. */
const val ICON_HABITS = "M12 3a9 9 0 1 1 0 18 9 9 0 0 1 0-18M8 12l3 3 5-6"

/**
 * A habit is a note whose id starts with "habit-": its title is the habit's name and each body line is one day
 * it was done, as an ISO date. So it syncs, searches and goes to Recently Deleted like any note.
 */
fun isHabit(note: NoteEntity) = note.id.startsWith("habit-")

fun habitDays(note: NoteEntity): Set<LocalDate> =
    note.blocks.drop(1).mapNotNull { runCatching { LocalDate.parse(it.text.trim()) }.getOrNull() }.toSet()

/** Every day of the week: a habit with no schedule, and the journal. */
val EVERY_DAY: Set<DayOfWeek> = DayOfWeek.entries.toSet()

/**
 * Ticked days in a row up to [today], or up to yesterday while today isn't ticked yet. Only days in [on]
 * count against it: a day off breaks nothing, and a tick on one still adds to the run.
 */
fun habitStreak(days: Set<LocalDate>, today: LocalDate, on: Set<DayOfWeek> = EVERY_DAY): Int {
    val first = days.minOrNull() ?: return 0
    var d = if (today in days) today else today.minusDays(1)
    var n = 0
    while (d >= first) {
        if (d in days) n++ else if (d.dayOfWeek in on) break
        d = d.minusDays(1)
    }
    return n
}

/** The next time [at] comes after [now]: today if it's still ahead, else tomorrow. */
fun nextAt(at: java.time.LocalTime, now: java.time.ZonedDateTime): java.time.ZonedDateTime =
    now.with(at).truncatedTo(java.time.temporal.ChronoUnit.MINUTES).let { if (it.isAfter(now)) it else it.plusDays(1) }

/** The habit's blocks with [day] ticked, newest day first; unchanged when it's already ticked. */
fun habitTicked(note: NoteEntity, day: LocalDate): List<com.margin.app.data.Block> = habitBlocks(note, habitDays(note) + day)

/** A habit's icon is kept as one "icon:<key>" line after the title, so it syncs and backs up with the habit. */
internal const val ICON_LINE = "icon:"

/** The picked icon's key, or null for none. */
fun habitIconKey(note: NoteEntity): String? =
    note.blocks.drop(1).firstOrNull { it.text.startsWith(ICON_LINE) }?.text?.removePrefix(ICON_LINE)?.takeIf { it in HABIT_ICONS }

fun habitIcon(note: NoteEntity): String? = habitIconKey(note)?.let { HABIT_ICONS[it] }

/** A habit's schedule is one "days:1,3,5" line (Monday = 1), written only when it isn't every day. */
private const val DAYS_LINE = "days:"

/** The weekdays [note] is due on; every day when none are set. */
fun habitSchedule(note: NoteEntity): Set<DayOfWeek> =
    note.blocks.drop(1).firstOrNull { it.text.startsWith(DAYS_LINE) }?.text?.removePrefix(DAYS_LINE)
        ?.split(',')?.mapNotNull { it.trim().toIntOrNull()?.takeIf { n -> n in 1..7 }?.let(DayOfWeek::of) }?.toSet()
        ?.ifEmpty { null } ?: EVERY_DAY

/** How a schedule reads, as a string resource and the day names it takes: "Every day", "All but Sun", "Mon, Wed, Fri". */
fun scheduleWords(on: Set<DayOfWeek>, locale: Locale = Locale.getDefault()): Pair<Int, String> = when (on) {
    EVERY_DAY -> R.string.habits_every_day to ""
    EVERY_DAY - DayOfWeek.SATURDAY - DayOfWeek.SUNDAY -> R.string.habits_weekdays to ""
    setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) -> R.string.habits_weekends to ""
    else -> if (on.size == 6) R.string.habits_all_but to (EVERY_DAY - on).first().getDisplayName(TextStyle.SHORT, locale) else R.string.habits_days_list to on.sorted().joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
}

/** "Every day", "Weekdays", "Weekends" or the days themselves: "Mon, Wed, Fri". */
@Composable
fun scheduleLabel(on: Set<DayOfWeek>, locale: Locale = Locale.getDefault()): String =
    scheduleWords(on, locale).let { (res, days) -> stringResource(res, days) }

/** How many of the 7 days up to [today] the habit was due, and how many of those were ticked. */
fun habitWeek(days: Set<LocalDate>, on: Set<DayOfWeek>, today: LocalDate): Pair<Int, Int> {
    val due = (0..6).map { today.minusDays(it.toLong()) }.filter { it.dayOfWeek in on }
    return due.count { it in days } to due.size
}

/**
 * The habit rewritten as its title, its icon line ([icon], or the one it has), its schedule line ([on], or
 * the one it has), then [days] newest first.
 */
fun habitBlocks(
    note: NoteEntity, days: Collection<LocalDate>, icon: String? = habitIconKey(note), on: Set<DayOfWeek> = habitSchedule(note),
): List<com.margin.app.data.Block> =
    listOf(note.blocks.first()) + listOfNotNull(
        icon?.let { com.margin.app.data.Block(text = ICON_LINE + it) },
        on.takeIf { it != EVERY_DAY && it.isNotEmpty() }?.let { com.margin.app.data.Block(text = DAYS_LINE + it.sorted().joinToString(",") { d -> "${d.value}" }) },
    ) + days.sortedDescending().map { com.margin.app.data.Block(text = it.toString()) }

/** Line icons a habit can wear, in the app's style (24 x 24, round stroke). */
val HABIT_ICONS: Map<String, String> = linkedMapOf(
    "run" to "M3 12h4l3-8 4 16 3-8h4",
    "lift" to "M6 7v10M18 7v10M3 10v4M21 10v4M6 12h12",
    "walk" to "M3 20l6-10 4 6 3-4 5 8zM9 10l1.5 2.5",
    "water" to "M12 3s-6 7-6 11a6 6 0 0 0 12 0c0-4-6-11-6-11z",
    "food" to "M5 3v5a2 2 0 0 0 4 0V3M7 3v18M17 21V3c-2 1-3 4-3 8h3",
    "sleep" to Ico.MOON,
    "sun" to "M8 12a4 4 0 1 0 8 0a4 4 0 1 0-8 0M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4",
    "read" to "M12 6a2 2 0 0 0-2-2H4v14h6a2 2 0 0 1 2 2zM12 6a2 2 0 0 1 2-2h6v14h-6a2 2 0 0 0-2 2",
    "write" to Ico.PEN,
    "music" to "M9 18V5l11-2v13M3 18a3 3 0 1 0 6 0a3 3 0 1 0-6 0M14 16a3 3 0 1 0 6 0a3 3 0 1 0-6 0",
    "plant" to "M12 21v-9M12 12C12 7 8.5 4 4 4c0 5 3.5 8 8 8zM12 15c0-4 3-7 8-7 0 4-3 7-8 7",
    "heart" to "M12 20s-7-4.5-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.5-7 10-7 10z",
    "money" to "M12 3v18M17 7H9.5a3 3 0 0 0 0 6h5a3 3 0 0 1 0 6H6",
    "learn" to "M2 9l10-5 10 5-10 5zM6 11v5c3 2 9 2 12 0v-5M22 9v6",
    "clean" to "M4 21l7-7M14 3l7 7-6 6-7-7zM9 9l6 6",
    "call" to "M4 5h16v11H9l-5 4z",
    "coffee" to "M4 9h13v5a5 5 0 0 1-5 5H9a5 5 0 0 1-5-5zM17 11h1.5a2.5 2.5 0 0 1 0 5H17M8 3v3M12 3v3",
    "pill" to "M10.5 3.5a5 5 0 0 1 7 7l-7 7a5 5 0 0 1-7-7zM7 7l7 7",
)

/** A grid of [HABIT_ICONS]; tapping one picks it, "None" clears it. */
@Composable
private fun HabitIconDialog(current: String?, onDismiss: () -> Unit, onPick: (String?) -> Unit) {
    MarginDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.habits_icon)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HABIT_ICONS.entries.chunked(6).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { (key, path) ->
                            val on = key == current
                            val picked = stringResource(R.string.habits_icon_picked, key)
                            Box(
                                Modifier.size(44.dp).clip(JShape.sm).background(if (on) jc.accent else jc.selected)
                                    .clickable(role = Role.RadioButton) { onPick(key) }
                                    .semantics { contentDescription = if (on) picked else key },
                                contentAlignment = Alignment.Center,
                            ) { JIcon(path, if (on) jc.bg else jc.accent, size = 22.dp, stroke = 2f) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_done)) } },
        dismissButton = if (current != null) ({ TextButton(onClick = { onPick(null) }) { Text(stringResource(R.string.habits_none), color = jc.danger) } }) else null,
    )
}

/** The longest run ever, counted as in [habitStreak]: days off in [on] neither count nor break it. */
fun habitBest(days: Set<LocalDate>, on: Set<DayOfWeek> = EVERY_DAY): Int {
    val first = days.minOrNull() ?: return 0
    var best = 0; var run = 0
    var d = first
    val last = days.max()
    while (d <= last) {
        if (d in days) run++ else if (d.dayOfWeek in on) run = 0
        best = maxOf(best, run)
        d = d.plusDays(1)
    }
    return best
}

/**
 * Every habit with a tick for today, the last week as dots and the streak. Tapping a habit opens a month
 * under it where any past day can be ticked or unticked.
 */
@Composable
fun HabitsScreen(
    notes: List<NoteEntity>, onToggle: (NoteEntity, LocalDate) -> Unit, onAdd: (String, String?) -> Unit, onRename: (NoteEntity, String) -> Unit, onIcon: (NoteEntity, String?) -> Unit, onSchedule: (NoteEntity, Set<DayOfWeek>) -> Unit,
    onDelete: (NoteEntity) -> Unit, onBack: () -> Unit, backLabel: String = stringResource(R.string.habits_library),
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
    renaming?.let { note -> NameDialog(stringResource(R.string.habits_rename_habit), note.title, onDismiss = { renaming = null }, label = stringResource(R.string.habits_habit)) { renaming = null; onRename(note, it) } }
    var picking by remember { mutableStateOf<NoteEntity?>(null) }
    picking?.let { note -> HabitIconDialog(habitIconKey(note), onDismiss = { picking = null }) { picking = null; onIcon(note, it) } }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    // Named first, then its icon; closing the icon picker still adds the habit, without one.
    var named by remember { mutableStateOf<String?>(null) }
    if (adding) NameDialog(stringResource(R.string.habits_new_habit), "", onDismiss = { adding = false }, label = stringResource(R.string.habits_habit)) { adding = false; if (it.isNotBlank()) named = it }
    named?.let { name -> HabitIconDialog(null, onDismiss = { named = null; onAdd(name, null) }) { named = null; onAdd(name, it) } }
    deleting?.let { note ->
        MarginDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.habits_delete, note.title)) },
            text = { Text(habitDays(note).size.let { pluralStringResource(R.plurals.habits_delete_moves_days, it, it) }) },
            confirmButton = { TextButton(onClick = { onDelete(note); deleting = null }) { Text(stringResource(R.string.common_delete), color = jc.danger) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
    val pageScroll = rememberScrollState()
    Scaffold(containerColor = jc.page, topBar = { NavBar(backLabel, onBack, color = jc.page) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(pageScroll)) {
            if (habits.isEmpty()) PageTitle(stringResource(R.string.habits_habits)) else HabitsTitle(habits, today)
            if (habits.isEmpty()) BigEmpty(
                ICON_HABITS, stringResource(R.string.habits_little_things_every_day), stringResource(R.string.habits_add_a_habit_tick_it),
                button = stringResource(R.string.habits_new_habit), onButton = { adding = true },
            )
            else {
                habits.forEach { note ->
                    val days = remember(note) { habitDays(note) }
                    HabitCard(
                        note, days, today, expanded = open == note.id, reminder = reminders[note.id],
                        onExpand = { open = if (open == note.id) null else note.id }, onToggle = { onToggle(note, it) },
                        onRename = { renaming = note }, onDelete = { deleting = note }, onRemind = { reminding = note }, onIcon = { picking = note }, onSchedule = { onSchedule(note, it) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
                Text(
                    stringResource(R.string.habits_small_and_daily_works_best),
                    Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = JType.secondary, color = jc.muted,
                )
                SecondaryButton(stringResource(R.string.habits_new_habit), { adding = true }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** "Habits", then how today and the week are going in one line. */
@Composable
private fun HabitsTitle(habits: List<NoteEntity>, today: LocalDate) {
    val all = remember(habits) { habits.map { habitDays(it) to habitSchedule(it) } }
    val due = all.count { (_, on) -> today.dayOfWeek in on }
    val done = all.count { (d, on) -> today.dayOfWeek in on && today in d }
    // Ticks on due days in the last 7, out of every due day in them.
    val weeks = all.map { (d, on) -> habitWeek(d, on, today) }
    val week = weeks.sumOf { it.first }
    val weekDue = weeks.sumOf { it.second }.coerceAtLeast(1)
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.habits_habits), style = JType.pageTitle, color = jc.ink)
        Text(
            if (due == 0) stringResource(R.string.habits_summary_all_off, week * 100 / weekDue) else stringResource(R.string.habits_summary_done, done, due, week * 100 / weekDue),
            style = JType.secondary, color = jc.muted,
        )
    }
}

/**
 * One habit: today's tick, the streak, and the last seven days. Tapping the card opens its
 * numbers, the reminder, a month to tick past days, and Rename and Delete.
 */
@Composable
private fun HabitCard(
    note: NoteEntity, days: Set<LocalDate>, today: LocalDate, expanded: Boolean, reminder: Long?,
    onExpand: () -> Unit, onToggle: (LocalDate) -> Unit, onRename: () -> Unit, onDelete: () -> Unit, onRemind: () -> Unit, onIcon: () -> Unit, onSchedule: (Set<DayOfWeek>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val on = remember(note) { habitSchedule(note) }
    val dueToday = today.dayOfWeek in on
    val streak = habitStreak(days, today, on)
    val (weekDone, weekDue) = habitWeek(days, on, today)
    val locale = Locale.getDefault()
    Column(modifier.fillMaxWidth().clip(JShape.lg).background(jc.card).animateContentSize(JMotion.standard())) {
        Column(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = if (expanded) stringResource(R.string.common_close) else stringResource(R.string.common_open), onClick = onExpand).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                CheckCircle(
                    today in days, size = 44.dp,
                    modifier = Modifier.clip(CircleShape).clickable(role = Role.Checkbox) { onToggle(today) }
                        .semantics { contentDescription = context.getString(R.string.habits_today, note.title) },
                )
                habitIcon(note)?.let { IconTile(it, jc.accent, size = 36.dp) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(note.title, style = JType.rowTitle, color = jc.ink, maxLines = 2)
                    Text(
                        listOfNotNull(scheduleLabel(on, locale), reminder?.let { stringResource(R.string.habits_at_time, timeLabel(it)) }, if (today in days) null else if (dueToday) stringResource(R.string.habits_not_done_today) else stringResource(R.string.habits_day_off)).joinToString(" · "),
                        style = JType.secondary, color = jc.muted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                if (reminder != null) JIcon(Ico.ALARM, jc.muted, size = 18.dp, modifier = Modifier.semantics { contentDescription = context.getString(R.string.habits_reminder_on) })
                val turn by androidx.compose.animation.core.animateFloatAsState(if (expanded) 90f else 0f, JMotion.standard(), label = "open")
                JIcon(Ico.CHEVRON, jc.muted, size = 14.dp, stroke = 2.4f, modifier = Modifier.graphicsLayer { rotationZ = turn })
            }
            Row(Modifier.fillMaxWidth()) {
                for (k in 6 downTo 0) {
                    val d = today.minusDays(k.toLong())
                    val ticked = d in days
                    val off = d.dayOfWeek !in on
                    val dayName = d.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
                    val dayState = stringResource(if (ticked) R.string.habits_day_done else if (off) R.string.habits_day_day_off else R.string.habits_day_not_done, dayName)
                    Column(
                        // Shown only: past days are ticked in the month below, so a tap meant to open the card can't untick one.
                        Modifier.weight(1f).padding(vertical = 4.dp)
                            .semantics { contentDescription = dayState },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            d.dayOfWeek.getDisplayName(TextStyle.NARROW_STANDALONE, locale), style = JType.caption,
                            color = if (k == 0) jc.accent else jc.muted, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        )
                        // A day off and not ticked: a small dot instead of a box to fill.
                        Box(
                            Modifier.size(32.dp).clip(JShape.xs)
                                .background(if (ticked) jc.accent else if (k == 0 || off) Color.Transparent else jc.page)
                                .then(if (k == 0 && !ticked && !off) Modifier.border(2.dp, jc.accent, JShape.xs) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (ticked) JIcon(Ico.CHECK, jc.bg, size = 14.dp, stroke = 3f)
                            else if (off) Box(Modifier.size(5.dp).clip(CircleShape).background(jc.line))
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("$streak", stringResource(R.string.habits_day_streak), Modifier.weight(1f), color = if (streak > 0) jc.accent else jc.ink)
                StatTile("${habitBest(days, on)}", stringResource(R.string.habits_best_streak), Modifier.weight(1f))
                StatTile("$weekDone/$weekDue", stringResource(R.string.habits_this_week), Modifier.weight(1f))
            }
        }
        if (expanded) {
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = jc.line)
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("${days.count { it > today.minusDays(30) && it <= today }}/30", stringResource(R.string.habits_last_30_days), Modifier.weight(1f))
                StatTile("${days.size}", stringResource(R.string.habits_in_all), Modifier.weight(1f))
            }
            HabitDays(on, onSchedule)
            val icon = habitIcon(note)
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp).clickable(role = Role.Button, onClick = onIcon).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconTile(icon ?: ICON_HABITS, if (icon != null) jc.accent else jc.muted)
                Text(stringResource(R.string.habits_icon), Modifier.weight(1f), style = JType.item, color = jc.ink)
                Text(if (icon != null) stringResource(R.string.common_change) else stringResource(R.string.habits_pick), style = JType.secondary, color = jc.accent)
            }
            val time = reminder?.let { timeLabel(it) }
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp).clickable(role = Role.Button, onClick = onRemind).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconTile(Ico.ALARM, if (time != null) jc.accent else jc.muted)
                Text(if (time != null) stringResource(R.string.habits_reminds_you_daily_at, time) else stringResource(R.string.habits_remind_me_daily), Modifier.weight(1f), style = JType.item, color = jc.ink)
                Text(if (time != null) stringResource(R.string.common_change) else stringResource(R.string.common_set), style = JType.secondary, color = jc.accent)
            }
            HabitMonth(days, today, onToggle)
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onRename) { Text(stringResource(R.string.common_rename), color = jc.accent) }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.common_delete), color = jc.danger) }
            }
        }
    }
}

/** "Repeats", then a toggle for each weekday from the locale's first; the last day on can't be turned off. */
@Composable
private fun HabitDays(on: Set<DayOfWeek>, onChange: (Set<DayOfWeek>) -> Unit) {
    val locale = Locale.getDefault()
    val first = WeekFields.of(locale).firstDayOfWeek
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row {
            Text(stringResource(R.string.habits_repeats), Modifier.weight(1f), style = JType.item, color = jc.ink)
            Text(scheduleLabel(on, locale), style = JType.secondary, color = jc.muted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (0L..6L).map { first.plus(it) }.forEach { day ->
                val picked = day in on
                val dayState = stringResource(if (picked) R.string.habits_day_on else R.string.habits_day_off_state, day.getDisplayName(TextStyle.FULL, locale))
                val fill by androidx.compose.animation.animateColorAsState(if (picked) jc.accent else jc.page, JMotion.standard(), label = "day")
                Box(
                    Modifier.weight(1f).height(40.dp).clip(JShape.sm).background(fill)
                        .clickable(enabled = !(picked && on.size == 1), role = Role.Checkbox) { onChange(if (picked) on - day else on + day) }
                        .semantics { contentDescription = dayState },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        day.getDisplayName(TextStyle.NARROW_STANDALONE, locale), style = JType.small,
                        color = if (picked) jc.bg else jc.muted, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    )
                }
            }
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
            IconAction(Ico.BACK, stringResource(R.string.habits_previous_month), { month = shown.minusMonths(1).toString() })
            IconAction(Ico.CHEVRON, stringResource(R.string.habits_next_month), { month = shown.plusMonths(1).toString() }, enabled = shown < thisMonth)
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
            Row(Modifier.padding(vertical = 2.dp).clip(JShape.sm).then(if (today in week) Modifier.background(jc.page) else Modifier)) {
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
        title = { Text(stringResource(R.string.habits_remind_me_daily)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.material3.TimePicker(state, colors = marginTimeColors())
                Text(stringResource(R.string.habits_not_on_days_youve_already), style = JType.small, color = jc.muted)
            }
        },
        confirmButton = { TextButton(onClick = { onPick(java.time.LocalTime.of(state.hour, state.minute)) }) { Text(stringResource(R.string.common_set)) } },
        dismissButton = {
            Row {
                if (current != null) TextButton(onClick = { onPick(null) }) { Text(stringResource(R.string.habits_off), color = jc.danger) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            }
        },
    )
}
