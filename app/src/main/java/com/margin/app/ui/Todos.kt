package com.margin.app.ui

import androidx.compose.foundation.lazy.rememberLazyListState
import android.content.Context
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.margin.app.data.NoteEntity
import com.margin.app.data.TodoCount
import com.margin.app.data.TodoList
import com.margin.app.data.TodoStatus
import com.margin.app.data.displayTitle
import com.margin.app.data.todoReport
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import com.margin.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource

/** A calendar with a target and a tick: the to-do target date. */
const val ICON_TARGET = "M4 6h16v14H4zM4 10h16M8 3v4M16 3v4M9 15l2 2 4-4"

/** Today as an epoch day, in this phone's time zone. */
fun todayEpochDay(): Long = LocalDate.now().toEpochDay()

/** "Today", "Tomorrow", "Yesterday", "Fri, 20 Nov", or with the year when it isn't this year. */
fun targetLabel(context: Context, day: Long): String {
    val today = todayEpochDay()
    return when (day) {
        today -> context.getString(R.string.common_today)
        today + 1 -> context.getString(R.string.common_tomorrow)
        today - 1 -> context.getString(R.string.common_yesterday)
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
fun statusLabel(context: Context, status: TodoStatus, target: Long?, today: Long): String = when (status) {
    TodoStatus.DONE -> context.getString(R.string.todos_status_all_done)
    TodoStatus.OVERDUE -> (today - (target ?: today)).toInt().let { context.resources.getQuantityString(R.plurals.todos_status_overdue_days, it, it) }
    TodoStatus.DUE_TODAY -> context.getString(R.string.todos_due_today)
    TodoStatus.BEHIND -> context.getString(R.string.todos_behind)
    TodoStatus.ON_TRACK -> context.getString(R.string.todos_on_track)
    TodoStatus.NO_TARGET -> context.getString(R.string.todos_no_target_date)
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
fun TodoHeader(
    count: TodoCount, startDay: Long, target: Long?, editable: Boolean, onPickTarget: () -> Unit,
    repeat: com.margin.app.data.ListRepeat? = null, onPickRepeat: () -> Unit = {}, streak: Int = 0,
    hideTicked: Boolean = false, onHideTicked: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val today = todayEpochDay()
    val status = com.margin.app.data.todoStatus(count, startDay, target, today)
    val tint = if (status == TodoStatus.OVERDUE) jc.danger else jc.accent
    // No card: a thin bar with the count beside it, then small chips for the target date and repeat.
    Column(Modifier.padding(bottom = 14.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val shown by androidx.compose.animation.core.animateFloatAsState(count.fraction, JMotion.standard(), label = "progress")
            Box(
                Modifier.weight(1f).height(4.dp).clip(JShape.pill).background(jc.line)
                    .semantics { contentDescription = if (count.total == 0) context.getString(R.string.todos_no_tasks_yet) else context.getString(R.string.todos_of_done, count.done, count.total) },
            ) { Box(Modifier.fillMaxWidth(shown).fillMaxHeight().clip(JShape.pill).background(tint)) }
            Text(if (count.total == 0) stringResource(R.string.todos_no_tasks_yet) else stringResource(R.string.todos_of, count.done, count.total), style = JType.small, color = jc.muted, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        // A repeating list's run of rounds ticked off in full.
        val run = com.margin.app.data.shownStreak(streak, count)
        if (repeat != null && count.total > 0 && run > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                JIcon(Ico.SPARKLE, jc.accent, size = 12.dp, stroke = 2f)
                Text(listStreakText(run, repeat), style = JType.small, color = jc.accent, maxLines = 1)
            }
        }
        val sub = if (count.total > 0 && (target != null || count.complete)) statusLabel(context, status, target, today) else null
        val late = status == TodoStatus.OVERDUE && target != null
        val targetLabel = target?.let { targetLabel(context, it) + (sub?.let { s -> " · $s" } ?: "") }
            ?: sub ?: if (editable) stringResource(R.string.todos_target_date) else null
        // A list done again and again (a routine, the weekly shop) unticks itself each round.
        val again = repeat?.let { listRepeatText(it) } ?: if (editable && count.total > 0) stringResource(R.string.todos_repeat) else null
        val canHide = onHideTicked != null && (count.done > 0 || hideTicked)
        if (targetLabel != null || again != null || canHide) {
            // Wraps rather than scrolls, so a long target date can't push the other chips off screen.
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (targetLabel != null) HeaderChip(
                    ICON_TARGET, targetLabel, set = target != null, tint = tint, editable = editable, onClick = onPickTarget, alert = late,
                    description = if (target != null && editable) stringResource(R.string.todos_tap_to_change_it, targetLabel) else if (target == null) stringResource(R.string.todos_add_target_date) else targetLabel,
                )
                if (again != null) HeaderChip(
                    Ico.SYNC, again, set = repeat != null, tint = tint, editable = editable, onClick = onPickRepeat,
                    description = if (repeat != null) stringResource(if (editable) R.string.todos_unticks_tap else R.string.todos_unticks, again.lowercase()) else stringResource(R.string.todos_repeat_this_list),
                )
                // Folding ticked tasks only changes the view, so it works on a locked list too.
                if (canHide) HeaderChip(
                    if (hideTicked) Ico.EYE else Ico.EYE_OFF, stringResource(if (hideTicked) R.string.todos_show_ticked_count else R.string.todos_hide_ticked_count, count.done),
                    set = true, tint = tint, editable = true, onClick = onHideTicked!!,
                    description = if (hideTicked) stringResource(R.string.todos_ticked_hidden_tap_to_show, count.done) else stringResource(R.string.todos_hide_ticked, count.done),
                )
            }
        }
    }
}

/** A small chip under the progress bar: tinted while not set yet (an action), plain once set, red when late. */
@Composable
private fun HeaderChip(
    icon: String, label: String, set: Boolean, tint: androidx.compose.ui.graphics.Color, editable: Boolean, onClick: () -> Unit, description: String,
    alert: Boolean = false,
) {
    Row(
        Modifier.height(34.dp).clip(JShape.sm).background(if (alert) jc.danger.copy(alpha = 0.16f) else jc.card)
            .clickable(enabled = editable, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp).semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Neutral whether set or not: a grey icon and ink text; only a late date turns red.
        JIcon(icon, if (alert) jc.danger else jc.muted, size = 14.dp, stroke = 2f)
        Text(label, style = JType.small, color = if (alert) jc.danger else jc.ink, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** Picks how often a checklist unticks itself: never, every day, every week on a day, or every month. */
@Composable
fun ListRepeatDialog(current: com.margin.app.data.ListRepeat?, onPick: (com.margin.app.data.ListRepeat?) -> Unit, onDismiss: () -> Unit) {
    var weekly by remember { mutableStateOf(current?.kind == com.margin.app.data.ListRepeat.Kind.WEEKLY) }
    val day = current?.takeIf { it.kind == com.margin.app.data.ListRepeat.Kind.WEEKLY }?.day ?: LocalDate.now().dayOfWeek
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.clip(JShape.lg).background(jc.bg).padding(vertical = 16.dp)) {
            Text(stringResource(R.string.todos_repeat_this_list), Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = JType.rowTitle, color = jc.ink)
            Text(stringResource(R.string.todos_every_task_unticks_at_the), Modifier.padding(horizontal = 20.dp), style = JType.small, color = jc.muted)
            Spacer(Modifier.height(8.dp))
            @Composable fun option(label: String, on: Boolean, onClick: () -> Unit) = Row(
                Modifier.fillMaxWidth().height(52.dp).clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, Modifier.weight(1f), style = JType.item, color = if (on) jc.accent else jc.ink)
                if (on) JIcon(Ico.CHECK, jc.accent, size = 18.dp, stroke = 2.6f)
            }
            option(stringResource(R.string.todos_never), current == null && !weekly) { onPick(null) }
            option(stringResource(R.string.todos_every_day), current?.kind == com.margin.app.data.ListRepeat.Kind.DAILY && !weekly) { onPick(com.margin.app.data.ListRepeat(com.margin.app.data.ListRepeat.Kind.DAILY)) }
            option(stringResource(R.string.todos_every_week), weekly) { weekly = true }
            if (weekly) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    java.time.DayOfWeek.entries.forEach { d ->
                        val on = current?.kind == com.margin.app.data.ListRepeat.Kind.WEEKLY && d == day
                        val everyDay = stringResource(R.string.todos_every_weekday, d.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()))
                        Text(
                            d.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()),
                            Modifier.clip(JShape.md).background(if (on) jc.accent else jc.bg)
                                .border(1.dp, if (on) jc.accent else jc.line, JShape.md)
                                .clickable(role = Role.RadioButton) { onPick(com.margin.app.data.ListRepeat(com.margin.app.data.ListRepeat.Kind.WEEKLY, d)) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .semantics { contentDescription = everyDay },
                            style = JType.small, color = if (on) jc.bg else jc.ink,
                        )
                    }
                }
            }
            option(stringResource(R.string.todos_every_month), current?.kind == com.margin.app.data.ListRepeat.Kind.MONTHLY && !weekly) { onPick(com.margin.app.data.ListRepeat(com.margin.app.data.ListRepeat.Kind.MONTHLY)) }
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
            Modifier.padding(horizontal = 16.dp).widthIn(max = 400.dp).fillMaxWidth().clip(JShape.lg).background(jc.bg)
                .verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
        ) {
            Text(stringResource(R.string.todos_target_end_date), Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = JType.rowTitle, color = jc.ink)
            Text(
                stringResource(R.string.todos_when_this_list_should_be),
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
                if (current != null) TextAction(stringResource(R.string.common_remove), onRemove, color = jc.danger)
                Spacer(Modifier.weight(1f))
                TextAction(stringResource(R.string.common_cancel), onDismiss)
                TextAction(stringResource(R.string.common_set), {
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
fun TodosScreen(
    notes: List<NoteEntity>, onOpen: (NoteEntity) -> Unit, onBack: () -> Unit, backLabel: String = stringResource(R.string.todos_library),
    onToggle: (NoteEntity, String) -> Unit = { _, _ -> }, onAdd: (NoteEntity, String) -> Unit = { _, _ -> },
    onNewList: (String, List<String>) -> Unit = { _, _ -> },
    /** As the Today tab: habits, the journal and reminders under the lists, and today's date over the title. */
    extras: TodayExtras? = null,
) {
    val context = LocalContext.current
    // Moves on at midnight if the screen is left open.
    var today by remember { mutableLongStateOf(todayEpochDay()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); today = todayEpochDay() } }
    val zone = ZoneId.systemDefault()
    val report = remember(notes, today) { todoReport(notes, today, zone) }
    // Lists opened out in place, to tick and add tasks without opening the note.
    var expanded by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(listOf<String>()) }
    var creating by remember { androidx.compose.runtime.mutableStateOf(false) }
    if (creating) NewListDialog(onDismiss = { creating = false }) { title, tasks -> creating = false; onNewList(title, tasks) }
    val pageList = rememberLazyListState()
    // Each time the screen opens, its sections rise in one after another.
    val enter = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, androidx.compose.animation.core.tween(650, easing = androidx.compose.animation.core.LinearEasing)) }
    val rise: (Int) -> Modifier = { Modifier.stagger({ enter.value }, it) }
    // On its tab, the new-list button sits beside the title instead of in a bar of its own.
    val tabRoot = LocalTabRoot.current && extras != null
    Scaffold(containerColor = jc.page, topBar = {
        if (tabRoot) TabTop()
        else NavBar(backLabel, onBack, color = jc.page) { IconAction(Ico.COMPOSE, stringResource(R.string.todos_new_to_do_list), { creating = true }) }
    }) { padding ->
        LazyColumn(state = pageList, modifier = Modifier.padding(padding).fillMaxSize()) {
            item { Box(rise(0)) {
                if (tabRoot) TitleWithActions({ DateHeading(LocalDate.ofEpochDay(today), stringResource(R.string.common_today)) }) {
                    IconAction(Ico.COMPOSE, stringResource(R.string.todos_new_to_do_list), { creating = true }, soft = true)
                }
                else if (extras != null) DateHeading(LocalDate.ofEpochDay(today), stringResource(R.string.common_today)) else PageTitle(stringResource(R.string.todos_to_dos))
            } }
            if (report.lists.isEmpty() && extras != null) {
                item { Column(rise(1)) {
                    SectionLabel(stringResource(R.string.todos_to_dos))
                    DividedCard(listOf(Unit), 16.dp) {
                        CardRow(stringResource(R.string.todos_no_to_do_lists_yet), stringResource(R.string.todos_start_one_or_add_checklist), onClick = { creating = true }, icon = Ico.CHECKLIST) {
                            JIcon(Ico.PLUS, jc.accent, size = 20.dp, stroke = 2f)
                        }
                    }
                } }
            } else if (report.lists.isEmpty()) {
                item { Box(rise(1)) {
                    BigEmpty(
                        Ico.CHECKLIST, stringResource(R.string.todos_no_to_do_lists_yet),
                        stringResource(R.string.todos_start_a_list_here_or),
                        button = stringResource(R.string.todos_new_list), onButton = { creating = true },
                    )
                } }
                return@LazyColumn
            }
            if (report.lists.isNotEmpty()) item { Box(rise(1)) { Summary(report) } }
            var block = 2
            TodoStatus.entries.forEach { status ->
                val lists = report.lists.filter { it.status == status }
                if (lists.isEmpty()) return@forEach
                val k = block++
                item(key = "s:" + status.name) {
                    Column(Modifier.animateItem(fadeInSpec = JMotion.standard(), placementSpec = JMotion.standard(), fadeOutSpec = JMotion.quick()).then(rise(k))) {
                        SectionLabel(
                            when (status) {
                                TodoStatus.OVERDUE -> stringResource(R.string.todos_overdue)
                                TodoStatus.DUE_TODAY -> stringResource(R.string.todos_due_today)
                                TodoStatus.BEHIND -> stringResource(R.string.todos_behind)
                                TodoStatus.ON_TRACK -> stringResource(R.string.todos_on_track)
                                TodoStatus.NO_TARGET -> stringResource(R.string.todos_no_target_date)
                                TodoStatus.DONE -> stringResource(R.string.common_done)
                            },
                        )
                        DividedCard(lists, 70.dp) { l ->
                            val open = l.note.id in expanded
                            Column {
                                ListRow(l, context, today, open) { expanded = if (open) expanded - l.note.id else expanded + l.note.id }
                                androidx.compose.animation.AnimatedVisibility(open) {
                                    ListTasks(l, onToggle = { id -> onToggle(l.note, id) }, onAdd = { onAdd(l.note, it) }, onOpen = { onOpen(l.note) })
                                }
                            }
                        }
                    }
                }
            }
            if (extras != null) todayExtras(extras, LocalDate.ofEpochDay(today)) { rise(block + it) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** What the Today tab shows under the to-do lists, and where each row goes. */
class TodayExtras(
    val habits: List<NoteEntity>, val onToggleHabit: (NoteEntity, LocalDate) -> Unit, val onHabits: () -> Unit,
    val journaled: Boolean, val onJournal: () -> Unit,
    val remindersToday: Int, val nextReminder: LocalDate?, val onReminders: () -> Unit,
)

/** Habits to tick off right here, then the journal and reminders. */
private fun androidx.compose.foundation.lazy.LazyListScope.todayExtras(x: TodayExtras, today: LocalDate, rise: (Int) -> Modifier) {
    item(key = "habits") {
        Column(rise(0)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(stringResource(R.string.todos_habits), Modifier.weight(1f))
                Text(
                    if (x.habits.isEmpty()) "" else stringResource(R.string.todos_all_habits),
                    Modifier.padding(end = 12.dp, top = 16.dp).clip(JShape.sm).clickable(role = Role.Button, onClick = x.onHabits).padding(horizontal = 8.dp, vertical = 6.dp),
                    style = JType.small, color = jc.accent, fontWeight = FontWeight.Medium,
                )
            }
            if (x.habits.isEmpty()) DividedCard(listOf(Unit), 16.dp) {
                CardRow(stringResource(R.string.todos_add_a_habit), stringResource(R.string.todos_tick_it_off_each_day), onClick = x.onHabits, icon = ICON_HABITS) {
                    JIcon(Ico.CHEVRON, jc.muted, size = 16.dp, stroke = 2f)
                }
            } else DividedCard(x.habits, 64.dp) { note ->
                val days = remember(note) { habitDays(note) }
                val on = remember(note) { habitSchedule(note) }
                val done = today in days
                val (week, weekDue) = habitWeek(days, on, today)
                val streak = habitStreak(days, today, on)
                val habitAction = if (done) stringResource(R.string.todos_untick_habit, note.title) else stringResource(R.string.todos_mark_habit_done, note.title)
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = x.onHabits).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    CheckCircle(
                        done, size = 34.dp,
                        modifier = Modifier.clip(androidx.compose.foundation.shape.CircleShape)
                            .clickable(role = Role.Checkbox) { x.onToggleHabit(note, today) }
                            .semantics { contentDescription = habitAction },
                    )
                    habitIcon(note)?.let { IconTile(it, jc.accent) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(note.title, style = JType.rowTitle, color = jc.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(
                                if (today.dayOfWeek !in on && !done) stringResource(R.string.todos_day_off) else null,
                                if (streak > 0) pluralStringResource(R.plurals.todos_days_in_a_row, streak, streak) else null, stringResource(R.string.todos_of_this_week, week, weekDue),
                            ).joinToString(" · "),
                            style = JType.small, color = jc.muted, maxLines = 1,
                        )
                    }
                }
            }
        }
    }
    item(key = "more") {
        Column(rise(1)) {
            SectionLabel(stringResource(R.string.todos_also_today))
            DividedCard(listOf(0, 1), 58.dp) { i ->
                if (i == 0) CardRow(stringResource(R.string.todos_journal), if (x.journaled) stringResource(R.string.todos_todays_entry_is_written) else stringResource(R.string.todos_write_todays_entry), onClick = x.onJournal, icon = ICON_JOURNAL) {
                    if (x.journaled) JIcon(Ico.CHECK, jc.accent, size = 18.dp, stroke = 2.4f)
                    JIcon(Ico.CHEVRON, jc.muted, size = 16.dp, stroke = 2f)
                } else {
                    val next = x.nextReminder
                    val sub = when {
                        x.remindersToday > 0 -> stringResource(R.string.todos_today, x.remindersToday)
                        next != null -> if (next == today.plusDays(1)) stringResource(R.string.todos_reminders_next_tomorrow) else stringResource(R.string.todos_reminders_next, next.format(java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM", java.util.Locale.getDefault())))
                        else -> stringResource(R.string.todos_none_set)
                    }
                    CardRow(stringResource(R.string.todos_reminders), sub, onClick = x.onReminders, icon = Ico.ALARM) { JIcon(Ico.CHEVRON, jc.muted, size = 16.dp, stroke = 2f) }
                }
            }
        }
    }
}

/** The totals: a ring for the share of tasks done, and tiles for the lists that need a look. */
@Composable
private fun Summary(report: com.margin.app.data.TodoReport) {
    // Just the overall progress: the sections below already list what's overdue and due.
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(JShape.lg).background(jc.card).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // The percentage counts up as the ring grows (same timing as ProgressRing).
        val shown = remember { androidx.compose.animation.core.Animatable(0f) }
        LaunchedEffect(report.fraction) { shown.animateTo(report.fraction, androidx.compose.animation.core.tween(700, 80, JMotion.ease)) }
        ProgressRing(report.fraction, Modifier.size(64.dp), stroke = 6.dp) {
            Text("${(shown.value * 100).toInt()}%", style = JType.small, color = jc.ink, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(stringResource(R.string.todos_of_tasks_done, report.done, report.total), style = JType.rowTitle, color = jc.ink)
            Text(pluralStringResource(R.plurals.todos_across_lists, report.lists.size, report.lists.size), style = JType.secondary, color = jc.muted)
        }
    }
}

/** One list: a small ring, its title, "3 of 5 · Target Fri, 20 Nov" and where it stands. */
@Composable
private fun ListRow(list: TodoList, context: Context, today: Long, open: Boolean, onClick: () -> Unit) {
    val target = list.note.targetDate
    val color = if (list.status == TodoStatus.OVERDUE) jc.danger else jc.accent
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = if (open) stringResource(R.string.todos_hide_tasks) else stringResource(R.string.todos_show_tasks)) { onClick() }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ProgressRing(list.count.fraction, Modifier.size(40.dp), color = color, stroke = 4.dp) {
            if (list.count.complete) JIcon(Ico.CHECK, color, size = 16.dp, stroke = 2.6f)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(list.note.displayTitle, style = JType.rowTitle, color = jc.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    if (list.count.total > 0) stringResource(R.string.todos_of, list.count.done, list.count.total) else stringResource(R.string.todos_no_tasks_yet),
                    target?.let { targetLabel(context, it) },
                ).joinToString(" · "),
                style = JType.small, color = jc.muted, maxLines = 1,
            )
        }
        val sc = statusColor(list.status)
        // "No target date" and "All done" would only repeat the section heading above.
        if (list.status != TodoStatus.NO_TARGET && list.status != TodoStatus.DONE) Text(
            statusLabel(context, list.status, target, today), Modifier.clip(JShape.pill).background(sc.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 4.dp),
            style = JType.caption, color = sc, fontWeight = FontWeight.SemiBold, maxLines = 1,
        )
    }
}

/**
 * A list opened out: its open tasks to tick, the done ones folded under "N done", a field to add a task, and
 * a way into the note itself.
 */
@Composable
private fun ListTasks(list: TodoList, onToggle: (String) -> Unit, onAdd: (String) -> Unit, onOpen: () -> Unit) {
    val tasks = list.note.blocks.filter { it.type == com.margin.app.data.BlockType.CHECK && it.attachment == null }
    val (done, open) = tasks.partition { it.checked }
    var showDone by remember { androidx.compose.runtime.mutableStateOf(false) }
    var typed by remember { androidx.compose.runtime.mutableStateOf("") }
    fun add() { if (typed.isNotBlank()) { onAdd(typed.trim()); typed = "" } }
    Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 12.dp, bottom = 10.dp)) {
        if (open.isEmpty() && tasks.isNotEmpty()) Text(stringResource(R.string.todos_all_done), Modifier.padding(start = 6.dp, top = 2.dp, bottom = 6.dp), style = JType.small, color = jc.success, fontWeight = FontWeight.SemiBold)
        open.forEach { t -> TaskRow(t.text, false) { onToggle(t.id) } }
        if (done.isNotEmpty()) {
            Text(
                if (showDone) stringResource(R.string.todos_hide_done, done.size) else stringResource(R.string.todos_done_show, done.size),
                Modifier.clip(JShape.xs).clickable(role = Role.Button) { showDone = !showDone }.padding(horizontal = 6.dp, vertical = 8.dp),
                style = JType.small, color = jc.accent,
            )
            if (showDone) done.forEach { t -> TaskRow(t.text, true) { onToggle(t.id) } }
        }
        // Add a task: Enter adds and keeps the field ready for the next one.
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.text.BasicTextField(
                typed, { typed = it.replace("\n", "") }, Modifier.weight(1f).padding(start = 6.dp, top = 10.dp, bottom = 10.dp),
                textStyle = JType.row.copy(color = jc.ink), singleLine = true,
                cursorBrush = androidx.compose.ui.graphics.SolidColor(jc.accent),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done, capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { add() }),
                decorationBox = { inner ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        JIcon(Ico.PLUS, jc.muted, size = 18.dp)
                        Spacer(Modifier.size(12.dp))
                        Box { if (typed.isEmpty()) Text(stringResource(R.string.todos_add_a_task), style = JType.row, color = jc.muted); inner() }
                    }
                },
            )
            if (typed.isNotBlank()) TextAction(stringResource(R.string.common_add), ::add, bold = true)
        }
        TextAction(stringResource(R.string.todos_open_note), onOpen)
    }
}

/** One task: a round tick box and its text, struck through once done. */
@Composable
private fun TaskRow(text: String, checked: Boolean, onToggle: () -> Unit) {
    val taskState = stringResource(if (checked) R.string.todos_task_done else R.string.todos_task_to_do, text)
    Row(
        Modifier.fillMaxWidth().clip(JShape.xs).clickable(role = Role.Checkbox, onClickLabel = if (checked) stringResource(R.string.todos_mark_not_done) else stringResource(R.string.todos_mark_done)) { onToggle() }
            .padding(horizontal = 6.dp, vertical = 8.dp).semantics { contentDescription = taskState },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(20.dp).clip(androidx.compose.foundation.shape.CircleShape)
                .background(if (checked) jc.accent else Color.Transparent)
                .then(if (checked) Modifier else Modifier.border(1.6.dp, jc.muted, androidx.compose.foundation.shape.CircleShape)),
            contentAlignment = Alignment.Center,
        ) { if (checked) JIcon(Ico.CHECK, jc.bg, size = 13.dp, stroke = 2.8f) }
        Text(
            text.ifBlank { stringResource(R.string.todos_untitled_task) }, Modifier.weight(1f), style = JType.row,
            color = if (checked) jc.muted else jc.ink,
            textDecoration = if (checked) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
        )
    }
}

/** A new to-do list: its name, and its tasks one per line. */
@Composable
private fun NewListDialog(onDismiss: () -> Unit, onCreate: (String, List<String>) -> Unit) {
    var title by remember { androidx.compose.runtime.mutableStateOf("") }
    var tasks by remember { androidx.compose.runtime.mutableStateOf("") }
    val items = tasks.lines().map { it.trim() }.filter { it.isNotEmpty() }
    MarginDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.todos_new_to_do_list)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    title, { title = it.replace("\n", "") }, Modifier.fillMaxWidth(), singleLine = true, shape = JShape.sm, label = { Text(stringResource(R.string.todos_name)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
                )
                androidx.compose.material3.OutlinedTextField(
                    tasks, { tasks = it }, Modifier.fillMaxWidth().heightIn(min = 120.dp), shape = JShape.sm, label = { Text(stringResource(R.string.todos_tasks_one_per_line)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
                )
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { onCreate(title.trim(), items) }, enabled = title.isNotBlank() && items.isNotEmpty()) { Text(stringResource(R.string.common_create)) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/** [com.margin.app.data.ListRepeat.label] in the app's language: "Every day", "Every Monday", "Every month". */
@Composable
internal fun listRepeatText(r: com.margin.app.data.ListRepeat): String = when (r.kind) {
    com.margin.app.data.ListRepeat.Kind.DAILY -> stringResource(R.string.todos_every_day)
    com.margin.app.data.ListRepeat.Kind.WEEKLY -> stringResource(R.string.todos_every_weekday, r.day.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()))
    com.margin.app.data.ListRepeat.Kind.MONTHLY -> stringResource(R.string.todos_every_month)
}

/** [com.margin.app.data.streakLabel] in the app's language: "5 days in a row". */
@Composable
internal fun listStreakText(n: Int, r: com.margin.app.data.ListRepeat): String = pluralStringResource(
    when (r.kind) {
        com.margin.app.data.ListRepeat.Kind.DAILY -> R.plurals.todos_streak_days
        com.margin.app.data.ListRepeat.Kind.WEEKLY -> R.plurals.todos_streak_weeks
        com.margin.app.data.ListRepeat.Kind.MONTHLY -> R.plurals.todos_streak_months
    },
    n, n,
)
