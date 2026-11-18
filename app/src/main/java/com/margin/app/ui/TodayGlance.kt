package com.margin.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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

/** What the home screen's "today" cards show, and where each goes. */
data class TodayGlance(
    val openTodos: Int, val remindersToday: Int, val journaled: Boolean,
    /** The day of the next reminder after today, when none rings today. */
    val nextReminderDay: LocalDate?,
    val onTodos: () -> Unit, val onReminders: () -> Unit, val onJournal: () -> Unit,
)

/** The glance for [today] from the live [notes] and the [reminders] (note id to when it rings). */
fun todayGlance(
    notes: List<NoteEntity>, reminders: Map<String, Long>, today: LocalDate, zone: ZoneId,
    onTodos: () -> Unit, onReminders: () -> Unit, onJournal: () -> Unit,
): TodayGlance {
    val ids = notes.mapTo(HashSet()) { it.id }
    return TodayGlance(
        openTodos = todoReport(notes, today.toEpochDay(), zone).lists.count { it.status != TodoStatus.DONE },
        remindersToday = reminders.count { (id, at) -> id in ids && Instant.ofEpochMilli(at).atZone(zone).toLocalDate() == today },
        nextReminderDay = reminders.filterKeys { it in ids }.values
            .map { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }.filter { it.isAfter(today) }.minOrNull(),
        journaled = notes.any { journalDay(it) == today },
        onTodos = onTodos, onReminders = onReminders, onJournal = onJournal,
    )
}

/** Today's date, small and uppercase, over the home screen's title. */
@Composable
fun DateHeading(today: LocalDate) {
    val locale = Locale.getDefault()
    val weekday = remember(today) { today.format(DateTimeFormatter.ofPattern("EEEE", locale)).uppercase(locale) }
    val date = remember(today) { today.format(DateTimeFormatter.ofPattern("d MMMM", locale)) }
    // The home screen's heading is today's date; read out as one heading.
    Column(Modifier.semantics(mergeDescendants = true) { heading() }) {
        Text(weekday, Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp), style = JType.section, color = jc.muted)
        PageTitle(date)
    }
}

/** Three small cards: to-do lists still open, reminders ringing today, and whether today's journal is written. */
@Composable
fun TodayCards(g: TodayGlance, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GlanceCard(Ico.CHECKLIST, "${g.openTodos}", if (g.openTodos == 1) "To-do open" else "To-dos open", g.onTodos, Modifier.weight(1f))
        // Reminders ringing today; on a day with none, when the next one rings, so the card never just reads 0.
        val next = g.nextReminderDay
        val (value, label) = when {
            g.remindersToday > 0 -> "${g.remindersToday}" to "Today"
            next != null -> dayShort(next, LocalDate.now()) to "Next up"
            else -> "None" to "Reminders"
        }
        GlanceCard(Ico.ALARM, value, label, g.onReminders, Modifier.weight(1f), accent = g.remindersToday > 0)
        GlanceCard(ICON_JOURNAL, if (g.journaled) "Done" else "Write", "Journal", g.onJournal, Modifier.weight(1f), accent = !g.journaled)
    }
}

/** "Tomorrow", a weekday within the week ("Thu"), else the date ("20 Nov"). */
private fun dayShort(day: LocalDate, today: LocalDate): String = when {
    day == today.plusDays(1) -> "Tomorrow"
    day.isBefore(today.plusDays(7)) -> day.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault()))
    else -> day.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
}

@Composable
private fun GlanceCard(icon: String, value: String, label: String, onClick: () -> Unit, modifier: Modifier, accent: Boolean = false) {
    val press = remember { MutableInteractionSource() }
    Column(
        modifier.pressScale(press).clip(JShape.lg).background(jc.card)
            .clickable(interactionSource = press, indication = androidx.compose.material3.ripple(), role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IconTile(icon, size = 26.dp)
        Column {
            Text(value, style = JType.rowTitle.copy(fontSize = 20.sp, lineHeight = 24.sp), fontWeight = FontWeight.Bold, color = if (accent) jc.accent else jc.ink, maxLines = 1)
            Text(label, style = JType.small, color = jc.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
