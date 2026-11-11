package com.shuva.jotter.ui

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shuva.jotter.data.NoteEntity
import com.shuva.jotter.data.TodoCount
import com.shuva.jotter.data.TodoList
import com.shuva.jotter.data.TodoStatus
import com.shuva.jotter.data.displayTitle
import com.shuva.jotter.data.todoReport
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** A calendar with a target and a tick: the to-do target date. */
const val ICON_TARGET = "M4 6h16v14H4zM4 10h16M8 3v4M16 3v4M9 15l2 2 4-4"

/** Today as an epoch day, in this phone's time zone. */
fun todayEpochDay(): Long = LocalDate.now().toEpochDay()

/** "Today", "Tomorrow", "Yesterday", "Fri, 20 Nov", or with the year when it isn't this year. */
fun targetLabel(context: Context, day: Long): String {
    val today = todayEpochDay()
    return when (day) {
        today -> "Today"
        today + 1 -> "Tomorrow"
        today - 1 -> "Yesterday"
        else -> {
            val millis = LocalDate.ofEpochDay(day).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val year = if (LocalDate.ofEpochDay(day).year == LocalDate.now().year) DateUtils.FORMAT_NO_YEAR else DateUtils.FORMAT_SHOW_YEAR
            DateUtils.formatDateTime(
                context, millis,
                DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or year,
            )
        }
    }
}

/** How a list stands, in words: "On track", "Overdue by 2 days"... */
fun statusLabel(status: TodoStatus, target: Long?, today: Long): String = when (status) {
    TodoStatus.DONE -> "All done"
    TodoStatus.OVERDUE -> "Overdue by " + ((today - (target ?: today)).let { if (it == 1L) "1 day" else "$it days" })
    TodoStatus.DUE_TODAY -> "Due today"
    TodoStatus.BEHIND -> "Behind"
    TodoStatus.ON_TRACK -> "On track"
    TodoStatus.NO_TARGET -> "No target date"
}

/** The colour a status is shown in: red when late, amber when it needs a look, muted otherwise. */
@Composable
fun statusColor(status: TodoStatus): Color = when (status) {
    TodoStatus.OVERDUE -> jc.danger
    TodoStatus.DUE_TODAY, TodoStatus.BEHIND -> jc.accent
    else -> jc.muted
}

/** A thin rounded bar filled to [fraction]. */
@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, color: Color = jc.accent, height: androidx.compose.ui.unit.Dp = 6.dp) {
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)).background(jc.line)) {
        if (fraction > 0f) Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(height / 2)).background(color))
    }
}

/**
 * Top of a to-do note: "3 of 5 done", the target date (tap to change it, or to add one), a progress bar and
 * where the list stands. Counts come from the note as typed, so they move as tasks are ticked.
 */
@Composable
fun TodoHeader(count: TodoCount, startDay: Long, target: Long?, editable: Boolean, onPickTarget: () -> Unit) {
    val context = LocalContext.current
    val today = todayEpochDay()
    val status = com.shuva.jotter.data.todoStatus(count, startDay, target, today)
    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (count.total == 0) "No tasks yet" else "${count.done} of ${count.total} done",
                Modifier.weight(1f), style = JType.small.copy(fontWeight = FontWeight.SemiBold), color = jc.ink,
            )
            val label = target?.let { "Target " + targetLabel(context, it) } ?: if (editable) "Add target date" else null
            if (label != null) {
                Row(
                    Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = editable, role = Role.Button, onClick = onPickTarget)
                        .background(if (target != null) jc.selected else androidx.compose.ui.graphics.Color.Transparent)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .semantics { contentDescription = if (target != null) "$label. Tap to change it." else label },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val c = if (status == TodoStatus.OVERDUE) jc.danger else jc.accent
                    JIcon(ICON_TARGET, c, size = 14.dp, stroke = 2f)
                    Text(label, style = JType.small, color = c)
                }
            }
        }
        if (count.total > 0) {
            ProgressBar(count.fraction, color = if (status == TodoStatus.OVERDUE) jc.danger else jc.accent)
            if (target != null || count.complete) Text(statusLabel(status, target, today), style = JType.small, color = statusColor(status))
        }
    }
}

/** Picks a list's target end date (today or later); [current] can be removed. [onPick] gets an epoch day. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetDateDialog(current: Long?, onPick: (Long) -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    val today = LocalDate.now()
    val state = rememberDatePickerState(
        // The picker's days are UTC midnights.
        initialSelectedDateMillis = (current?.let(LocalDate::ofEpochDay) ?: today.plusDays(7)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) =
                !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isBefore(today)
            override fun isSelectableYear(year: Int) = year >= today.year
        },
    )
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(horizontal = 16.dp).widthIn(max = 400.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(jc.bg)
                .verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
        ) {
            Text("Target end date", Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = JType.rowTitle, color = jc.ink)
            Text(
                "When this list should be finished. The progress report shows whether you're on track.",
                Modifier.padding(horizontal = 20.dp), style = JType.small, color = jc.muted,
            )
            DatePicker(
                state, title = null, headline = null, showModeToggle = false,
                colors = DatePickerDefaults.colors(
                    containerColor = jc.bg, selectedDayContainerColor = jc.accent, selectedDayContentColor = jc.bg,
                    todayDateBorderColor = jc.accent, todayContentColor = jc.accent,
                    selectedYearContainerColor = jc.accent, selectedYearContentColor = jc.bg,
                ),
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (current != null) TextAction("Remove", onRemove, color = jc.danger)
                Spacer(Modifier.weight(1f))
                TextAction("Cancel", onDismiss)
                TextAction("Set", {
                    state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()) }
                }, bold = true, enabled = state.selectedDateMillis != null)
            }
        }
    }
}

/**
 * The progress report: every to-do list (a note with checklist lines or a target date), with the tasks done
 * across them, how many lists are overdue, due today, due this week or behind, and each list grouped by where
 * it stands. Tap a list to open it.
 */
@Composable
fun TodosScreen(notes: List<NoteEntity>, onOpen: (NoteEntity) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    // Moves on at midnight if the screen is left open.
    var today by remember { mutableLongStateOf(todayEpochDay()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); today = todayEpochDay() } }
    val zone = ZoneId.systemDefault()
    val report = remember(notes, today) { todoReport(notes, today, zone) }
    Scaffold(topBar = { NavBar("Folders", onBack) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item { PageTitle("To-dos") }
            if (report.lists.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        JIcon(Ico.CHECKLIST, jc.muted, size = 40.dp)
                        Text("No to-do lists yet", Modifier.padding(top = 16.dp), style = JType.rowTitle, color = jc.ink)
                        Text(
                            "Add checklist items to a note and it shows up here. Set a target date from the note's … menu to see whether you're on track.",
                            Modifier.padding(top = 6.dp), style = JType.secondary, color = jc.muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
                return@LazyColumn
            }
            item { Summary(report, today) }
            TodoStatus.entries.forEach { status ->
                val lists = report.lists.filter { it.status == status }
                if (lists.isEmpty()) return@forEach
                item(key = "h:" + status.name) {
                    SectionLabel(
                        when (status) {
                            TodoStatus.OVERDUE -> "Overdue"
                            TodoStatus.DUE_TODAY -> "Due today"
                            TodoStatus.BEHIND -> "Behind"
                            TodoStatus.ON_TRACK -> "On track"
                            TodoStatus.NO_TARGET -> "No target date"
                            TodoStatus.DONE -> "Done"
                        },
                    )
                }
                items(lists, key = { it.note.id }) { ListRow(it, context, today, onOpen) }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** The totals: share of tasks done, a bar, and counts of lists needing attention. */
@Composable
private fun Summary(report: com.shuva.jotter.data.TodoReport, today: Long) {
    Column(
        Modifier.padding(horizontal = 20.dp).padding(bottom = 4.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(jc.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${(report.fraction * 100).toInt()}%", style = JType.pageTitle.copy(fontSize = 34.sp, lineHeight = 38.sp), color = jc.ink)
            Text("done", Modifier.padding(bottom = 5.dp), style = JType.secondary, color = jc.muted)
        }
        ProgressBar(report.fraction, height = 8.dp)
        val lists = report.lists.size
        Text(
            "${report.done} of ${report.total} tasks across $lists ${if (lists == 1) "list" else "lists"}",
            style = JType.small, color = jc.muted,
        )
        val stats = listOf(
            Triple(report.count(TodoStatus.OVERDUE), "overdue", jc.danger),
            Triple(report.count(TodoStatus.DUE_TODAY), "due today", jc.accent),
            Triple(report.dueThisWeek(today), "due this week", jc.ink),
            Triple(report.count(TodoStatus.BEHIND), "behind", jc.accent),
            Triple(report.count(TodoStatus.DONE), "done", jc.muted),
        ).filter { it.first > 0 }
        if (stats.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                stats.forEach { (n, label, color) ->
                    Column {
                        Text("$n", style = JType.rowTitle, color = color)
                        Text(label, style = JType.small.copy(fontSize = 12.sp), color = jc.muted)
                    }
                }
            }
        }
    }
}

/** One list: its title, a bar, "3 of 5 · Target Fri, 20 Nov" and where it stands. */
@Composable
private fun ListRow(list: TodoList, context: Context, today: Long, onOpen: (NoteEntity) -> Unit) {
    val target = list.note.targetDate
    Column(
        Modifier.fillMaxWidth().clickable { onOpen(list.note) }.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(list.note.displayTitle, Modifier.weight(1f), style = JType.rowTitle, color = jc.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(statusLabel(list.status, target, today), style = JType.small, color = statusColor(list.status), maxLines = 1)
        }
        if (list.count.total > 0) ProgressBar(list.count.fraction, color = if (list.status == TodoStatus.OVERDUE) jc.danger else jc.accent, height = 4.dp)
        Text(
            listOfNotNull(
                if (list.count.total > 0) "${list.count.done} of ${list.count.total}" else "No tasks yet",
                target?.let { "Target " + targetLabel(context, it) },
            ).joinToString(" · "),
            style = JType.small, color = jc.muted, maxLines = 1,
        )
    }
    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = jc.line)
}
