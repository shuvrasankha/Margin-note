package com.margin.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.margin.app.data.NoteEntity
import com.margin.app.data.TodoStatus
import com.margin.app.data.todoReport
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.margin.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource

/** What the home screen's agenda strip shows, and where each part goes. */
data class TodayGlance(
    val openTodos: Int, val remindersToday: Int, val journaled: Boolean,
    /** The day of the next reminder after today, when none rings today. */
    val nextReminderDay: LocalDate?,
    val onTodos: () -> Unit, val onReminders: () -> Unit, val onJournal: () -> Unit,
    /** To-do lists past their target date. */
    val overdue: Int = 0,
    /** Habits not yet ticked today, by name, and how many habits there are. */
    val habitsLeft: List<String> = emptyList(), val habits: Int = 0,
    val onHabits: () -> Unit = {},
)

/** The glance for [today] from the live [notes] and the [reminders] (note id to when it rings). */
fun todayGlance(
    notes: List<NoteEntity>, reminders: Map<String, Long>, today: LocalDate, zone: ZoneId,
    onTodos: () -> Unit, onReminders: () -> Unit, onJournal: () -> Unit, onHabits: () -> Unit = {},
): TodayGlance {
    val ids = notes.mapTo(HashSet()) { it.id }
    val lists = todoReport(notes, today.toEpochDay(), zone).lists
    val habits = notes.filter(::isHabit).sortedBy { it.createdAt }
    return TodayGlance(
        openTodos = lists.count { it.status != TodoStatus.DONE },
        remindersToday = reminders.count { (id, at) -> id in ids && Instant.ofEpochMilli(at).atZone(zone).toLocalDate() == today },
        nextReminderDay = reminders.filterKeys { it in ids }.values
            .map { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }.filter { it.isAfter(today) }.minOrNull(),
        journaled = notes.any { journalDay(it) == today },
        onTodos = onTodos, onReminders = onReminders, onJournal = onJournal,
        overdue = lists.count { it.status == TodoStatus.OVERDUE },
        habitsLeft = habits.filter { today.dayOfWeek in habitSchedule(it) && today !in habitDays(it) }.map { it.title }, habits = habits.size, onHabits = onHabits,
    )
}

/** Today's date, small and uppercase, over a tab's [title] ("Notes", "Today"); read out as one heading. */
@Composable
fun DateHeading(today: LocalDate, title: String) {
    val locale = Locale.getDefault()
    val date = remember(today) { today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale)).uppercase(locale) }
    Column(Modifier.semantics(mergeDescendants = true) { heading() }) {
        Text(date, Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp), style = JType.section, color = jc.muted)
        PageTitle(title)
    }
}

/**
 * One line of today under the home screen's title: open to-dos, habits left (or reminders, with no habits) and
 * the journal, as three parts of one card. Each opens its place; the whole of it lives on the Today tab.
 */
@Composable
fun TodayCards(g: TodayGlance, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp).height(IntrinsicSize.Min).clip(JShape.md).background(jc.card),
    ) {
        AgendaPart(
            if (g.openTodos == 0) stringResource(R.string.today_glance_no_to_dos) else pluralStringResource(R.plurals.today_todos, g.openTodos, g.openTodos),
            if (g.overdue > 0) pluralStringResource(R.plurals.today_overdue, g.overdue, g.overdue) else if (g.openTodos == 0) stringResource(R.string.today_glance_all_clear) else stringResource(R.string.today_glance_on_track),
            g.onTodos, dot = g.overdue > 0, alert = g.overdue > 0,
        )
        PartRule()
        if (g.habits > 0) AgendaPart(
            if (g.habitsLeft.isEmpty()) stringResource(R.string.today_glance_habits) else pluralStringResource(R.plurals.today_habits, g.habitsLeft.size, g.habitsLeft.size),
            if (g.habitsLeft.isEmpty()) stringResource(R.string.today_glance_all_done) else stringResource(R.string.today_habit_left, g.habitsLeft.first()),
            g.onHabits,
        ) else {
            val next = g.nextReminderDay
            AgendaPart(
                stringResource(R.string.today_glance_reminders),
                when { g.remindersToday > 0 -> pluralStringResource(R.plurals.today_reminders_today, g.remindersToday, g.remindersToday); next != null -> stringResource(R.string.today_next_day, dayShort(next, LocalDate.now())); else -> stringResource(R.string.today_glance_none_set) },
                g.onReminders,
            )
        }
        PartRule()
        AgendaPart(stringResource(R.string.today_glance_journal), if (g.journaled) stringResource(R.string.today_glance_written) else stringResource(R.string.today_glance_write_today), g.onJournal, accentSub = !g.journaled)
    }
}

@Composable
private fun PartRule() = Box(Modifier.padding(vertical = 12.dp).width(1.dp).fillMaxHeight().background(jc.line))

@Composable
private fun RowScope.AgendaPart(
    title: String, sub: String, onClick: () -> Unit,
    dot: Boolean = false, alert: Boolean = false, accentSub: Boolean = false,
) {
    Column(
        Modifier.weight(1f).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = JType.item.copy(fontSize = 15.sp), color = jc.ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (dot) Box(Modifier.size(6.dp).clip(androidx.compose.foundation.shape.CircleShape).background(jc.danger))
        }
        Text(
            sub, style = JType.small, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = when { alert -> jc.danger; accentSub -> jc.accent; else -> jc.muted },
        )
    }
}

/** "Tomorrow", a weekday within the week ("Thu"), else the date ("20 Nov"). */
@Composable
private fun dayShort(day: LocalDate, today: LocalDate): String = when {
    day == today.plusDays(1) -> stringResource(R.string.today_tomorrow_lower)
    day.isBefore(today.plusDays(7)) -> day.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault()))
    else -> day.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
}
