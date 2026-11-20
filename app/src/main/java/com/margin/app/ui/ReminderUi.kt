package com.margin.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.margin.app.data.reminderChoices
import com.margin.app.data.CalendarEvents
import kotlinx.coroutines.launch
import com.margin.app.data.Repeat
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxSize
import com.margin.app.data.isLocked
import com.margin.app.data.snippet
import com.margin.app.data.displayTitle
import com.margin.app.data.NoteEntity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/** "Today, 6:00 PM", "Tomorrow, 9:00 AM" or "Mon, Nov 17, 9:00 AM". */
fun reminderLabel(context: Context, time: Long): String {
    val clock = DateUtils.formatDateTime(context, time, DateUtils.FORMAT_SHOW_TIME)
    val day = DAY_MS
    return when {
        DateUtils.isToday(time) -> "Today, $clock"
        DateUtils.isToday(time - day) -> "Tomorrow, $clock"
        else -> DateUtils.formatDateTime(
            context, time, DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH,
        ) + ", " + clock
    }
}

/** [reminderLabel] with how it repeats: "Tomorrow, 9:00 AM · Weekly". */
fun reminderLabel(context: Context, time: Long, repeat: Repeat): String =
    reminderLabel(context, time) + if (repeat == Repeat.NEVER) "" else " · ${repeat.label}"

private const val DAY_MS = 24 * 60 * 60 * 1000L

/**
 * Picks when to be reminded about a note: quick choices, or a date and time of your own. [current] is the
 * reminder already set, which can be removed. [onTime] false adds a line about late reminders and a link to
 * the "Alarms & reminders" setting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderDialog(
    current: Long?, currentRepeat: Repeat, onTime: Boolean, onPick: (Long, Repeat, Boolean) -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit,
    calendarFor: String? = null,
    /** The note's place reminder, and opening the place picker; null where place reminders aren't offered. */
    place: com.margin.app.data.Place? = null, onPlace: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    // On when this reminder is already on the calendar, else as last time.
    var toCalendar by remember { mutableStateOf(calendarFor != null && (CalendarEvents.has(context, calendarFor) || calendarPref(context))) }
    var custom by remember { mutableStateOf(false) }
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var repeat by remember { mutableStateOf(if (current != null) currentRepeat else Repeat.NEVER) }
    val now = remember { ZonedDateTime.now() }

    // Wider than Android's default dialog: the calendar needs about 360dp.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(horizontal = 16.dp).widthIn(max = 400.dp).fillMaxWidth().clip(JShape.lg).background(jc.bg).verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp),
        ) {
            Text(
                if (!custom) "Remind me" else if (pickedDate == null) "Pick a day" else "Pick a time",
                Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = JType.rowTitle, color = jc.ink,
            )
            current?.let {
                if (!custom) Text("Set for ${reminderLabel(context, it, currentRepeat)}", Modifier.padding(horizontal = 20.dp), style = JType.small, color = jc.muted)
            }
            if (!custom) RepeatChips(repeat) { repeat = it }
            if (!custom && calendarFor != null) Box(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                CalendarSwitch(toCalendar) { toCalendar = it }
            }
            when {
                !custom -> {
                    Column(Modifier.padding(top = 4.dp)) {
                        // Only the repeat changed: keep the time already set.
                        if (current != null && repeat != currentRepeat && current > System.currentTimeMillis()) {
                            ChoiceRow(Ico.CHECK, "Keep this time", reminderLabel(context, current)) { onPick(current, repeat, toCalendar) }
                        }
                        reminderChoices(now).forEach { (label, at) ->
                            val icon = when (label) {
                                "This evening" -> Ico.SUNSET
                                "Tomorrow morning" -> Ico.SUN
                                "Next week" -> Ico.CALENDAR
                                else -> Ico.CLOCK
                            }
                            ChoiceRow(icon, label, reminderLabel(context, at.toInstant().toEpochMilli())) { onPick(at.toInstant().toEpochMilli(), repeat, toCalendar) }
                        }
                        ChoiceRow(Ico.CALENDAR_PICK, "Pick date & time…", null) { custom = true }
                        onPlace?.let { ChoiceRow(Ico.PLACE, if (place == null) "At a place…" else "Change place…", place?.label, onClick = it) }
                        if (current != null) ChoiceRow(Ico.TRASH, "Remove reminder", null, danger = true, onClick = onRemove)
                    }
                    if (!onTime) {
                        Text(
                            "Android may deliver reminders a few minutes late unless Margin is allowed to set alarms.",
                            Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp), style = JType.small, color = jc.muted,
                        )
                        Box(Modifier.padding(start = 12.dp)) {
                            TextAction("Allow alarms", {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) runCatching {
                                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                                }
                            })
                        }
                    }
                    Box(Modifier.fillMaxWidth().padding(end = 12.dp), contentAlignment = Alignment.CenterEnd) { TextAction("Cancel", onDismiss) }
                }
                pickedDate == null -> {
                    val today = LocalDate.now()
                    val state = rememberDatePickerState(
                        initialSelectedDateMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                        selectableDates = object : SelectableDates {
                            // The picker's days are UTC midnights.
                            override fun isSelectableDate(utcTimeMillis: Long) =
                                !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isBefore(today)
                            override fun isSelectableYear(year: Int) = year >= today.year
                        },
                    )
                    DatePicker(
                        state, title = null, headline = null, showModeToggle = false,
                        colors = DatePickerDefaults.colors(
                            containerColor = jc.bg, selectedDayContainerColor = jc.accent, selectedDayContentColor = jc.bg,
                            todayDateBorderColor = jc.accent, todayContentColor = jc.accent,
                            selectedYearContainerColor = jc.accent, selectedYearContentColor = jc.bg,
                        ),
                    )
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
                        TextAction("Back", { custom = false })
                        TextAction("Next", {
                            state.selectedDateMillis?.let { pickedDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                        }, bold = true, enabled = state.selectedDateMillis != null)
                    }
                }
                else -> {
                    val start = now.plusHours(1)
                    val state = rememberTimePickerState(start.hour, 0, is24Hour = DateFormat.is24HourFormat(context))
                    val colors = marginTimeColors()
                    Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) { TimePicker(state, colors = colors) }
                    error?.let { Text(it, Modifier.padding(horizontal = 20.dp), style = JType.small, color = jc.danger) }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
                        TextAction("Back", { pickedDate = null; error = null })
                        TextAction("Set reminder", {
                            val at = ZonedDateTime.of(pickedDate, LocalTime.of(state.hour, state.minute), ZoneId.systemDefault()).toInstant().toEpochMilli()
                            if (at <= System.currentTimeMillis()) error = "That time has passed. Pick a later one."
                            else onPick(at, repeat, toCalendar)
                        }, bold = true)
                    }
                }
            }
        }
    }
}

/** "Repeat" with a pill for each choice; the picked one is filled. */
@Composable
private fun RepeatChips(selected: Repeat, onSelect: (Repeat) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Repeat", style = JType.small, color = jc.muted)
        Repeat.entries.forEach { r ->
            val on = r == selected
            Text(
                r.label,
                Modifier.clip(JShape.md).background(if (on) jc.accent else jc.bg)
                    .border(1.dp, if (on) jc.accent else jc.line, JShape.md)
                    .clickable(role = Role.RadioButton) { onSelect(r) }.padding(horizontal = 12.dp, vertical = 6.dp),
                style = JType.small, color = if (on) jc.bg else jc.ink,
            )
        }
    }
}

@Composable
private fun ChoiceRow(icon: String, label: String, detail: String?, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        JIcon(icon, if (danger) jc.danger else jc.accent, size = 20.dp, stroke = 1.9f)
        Text(label, Modifier.weight(1f), style = JType.item, color = if (danger) jc.danger else jc.ink)
        detail?.let { Text(it, style = JType.small, color = jc.muted) }
    }
}

/** One line on the Reminders list: a note's reminder, or the daily journal reminder ([note] null). */
private data class ReminderEntry(val note: NoteEntity?, val at: Long, val repeat: Repeat)

/**
 * The Reminders list: notes with a reminder set on this phone, soonest first, grouped into Missed, Today,
 * Tomorrow, This week and Later, with the daily journal reminder among them. Each note's reminder can be
 * changed or removed right here with its clock button.
 */
@Composable
fun RemindersScreen(
    notes: List<NoteEntity>, reminders: Map<String, Long>, repeats: Map<String, Repeat>, onOpen: (NoteEntity) -> Unit, onBack: () -> Unit,
    backLabel: String = "Folders", onTime: Boolean = true, onJournal: () -> Unit = {},
    onSet: (NoteEntity, Long, Repeat) -> Unit = { _, _, _ -> }, onRemove: (NoteEntity) -> Unit = {},
    /** Makes the note for a new reminder and returns its id. */
    onCreate: (title: String, details: String, at: Long, repeat: Repeat) -> String = { _, _, _, _ -> "" },
    places: Map<String, com.margin.app.data.Place> = emptyMap(), onRemovePlace: (NoteEntity) -> Unit = {},
) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf<ReminderEntry?>(null) }
    var creating by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Ticks each minute so countdowns and the Missed group stay right while the screen is open.
    val now by androidx.compose.runtime.produceState(System.currentTimeMillis()) {
        while (true) { kotlinx.coroutines.delay(60_000L - value % 60_000L); value = System.currentTimeMillis() }
    }
    val rows = remember(notes, reminders, repeats) {
        val byId = notes.associateBy { it.id }
        reminders.mapNotNull { (id, at) ->
            val repeat = repeats[id] ?: Repeat.NEVER
            when {
                id == JOURNAL_REMINDER -> ReminderEntry(null, at, repeat)
                else -> byId[id]?.let { ReminderEntry(it, at, repeat) }
            }
        }.sortedBy { it.at }
    }
    val placeRows = remember(notes, places) {
        val byId = notes.associateBy { it.id }
        places.mapNotNull { (id, p) -> byId[id]?.let { it to p } }.sortedBy { it.first.displayTitle.lowercase() }
    }
    val today = LocalDate.now()
    fun group(at: Long): String {
        if (at < now) return "Missed"
        val day = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate()
        return when {
            !day.isAfter(today) -> "Today"
            day == today.plusDays(1) -> "Tomorrow"
            day.isBefore(today.plusDays(7)) -> "This week"
            else -> "Later"
        }
    }
    fun open(e: ReminderEntry) { e.note?.let(onOpen) ?: onJournal() }
    Scaffold(
        containerColor = jc.page,
        topBar = { NavBar(backLabel, onBack, color = jc.page) },
        floatingActionButton = { if (rows.isNotEmpty()) FloatingAction(Ico.PLUS, "New reminder") { creating = true } },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item { PageTitle("Reminders") }
            if (!onTime && rows.isNotEmpty()) item {
                // Without the exact-alarm permission Android batches alarms, so say so where it matters.
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(JShape.md).background(jc.card)
                        .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IconTile(Ico.CLOCK, jc.danger)
                    Text("Reminders may arrive a few minutes late.", Modifier.weight(1f), style = JType.small, color = jc.ink)
                    TextAction("Allow", {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) runCatching {
                            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                        }
                    }, bold = true)
                }
            }
            if (rows.isEmpty() && placeRows.isEmpty()) {
                item {
                    BigEmpty(
                        Ico.ALARM, "No reminders", "Make one here, or open a note and tap the alarm clock at the top to be reminded about it.",
                        button = "New reminder", onButton = { creating = true },
                    )
                }
                return@LazyColumn
            }
            // With one reminder the card would only repeat the row below it.
            if (rows.size > 1) item {
                // The next one still to come, large, with how long until it rings and a few counts.
                val next = rows.firstOrNull { it.at >= now } ?: rows.first()
                val todayCount = rows.count { it.at >= now && group(it.at) == "Today" }
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(JShape.lg).background(jc.card)
                        .clickable { open(next) }.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        IconTile(Ico.ALARM, size = 48.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(if (next.at >= now) countdown(next.at, now).let { c -> if (c.startsWith("in ")) "Next up · $c" else "Next up" } else "Missed", style = JType.caption, color = if (next.at >= now) jc.muted else jc.danger)
                            Text(next.note?.displayTitle ?: "Write in your journal", style = JType.subheading, color = jc.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(reminderLabel(context, next.at, next.repeat), style = JType.secondary, color = jc.accent, maxLines = 1)
                        }
                    }
                    // Counts worth showing: none at 0, and no row for a single one (the list below says as much).
                    val stats = listOf(
                        todayCount to "Today", rows.count { it.at >= now } to "Coming up", rows.count { it.repeat != Repeat.NEVER } to "Repeating",
                    ).filter { it.first > 0 }
                    if (stats.size >= 2) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        stats.forEach { (n, label) -> StatTile("$n", label, Modifier.weight(1f)) }
                    }
                }
            }
            rows.groupBy { group(it.at) }.forEach { (label, items) ->
                item(key = "g:$label") {
                    Column(Modifier.animateItem(fadeInSpec = JMotion.standard(), placementSpec = JMotion.standard(), fadeOutSpec = JMotion.quick())) {
                        SectionLabel(label)
                        DividedCard(items, 60.dp) { e -> ReminderRow(e, missed = label == "Missed", onOpen = { open(e) }, onEdit = { editing = e }) }
                    }
                }
            }
            if (placeRows.isNotEmpty()) item(key = "g:places") {
                Column {
                    SectionLabel("At a place")
                    DividedCard(placeRows, 60.dp) { (note, place) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpen(note) }.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            IconTile(Ico.PLACE)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(note.displayTitle, style = JType.rowTitle, color = jc.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(place.phrase, style = JType.secondary, color = jc.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconAction(Ico.CLOSE, "Remove place reminder", { onRemovePlace(note) }, size = 20.dp)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    if (creating) NewReminderDialog(
        onTime = onTime, onDismiss = { creating = false },
        onSave = { title, details, at, repeat, calendar ->
            creating = false
            val id = onCreate(title, details, at, repeat)
            reminderToCalendar(context, scope, id, title, details, at, repeat, calendar)
        },
    )
    editing?.let { e ->
        val note = e.note ?: return@let
        ReminderDialog(
            current = e.at, currentRepeat = e.repeat, onTime = onTime, calendarFor = note.id,
            onPick = { time, repeat, calendar ->
                editing = null
                onSet(note, time, repeat)
                reminderToCalendar(context, scope, note.id, note.displayTitle, note.snippet.takeIf { !note.isLocked }, time, repeat, calendar)
            },
            onRemove = {
                editing = null
                onRemove(note)
                scope.launch(kotlinx.coroutines.Dispatchers.IO) { CalendarEvents.remove(context, note.id) }
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun ReminderRow(e: ReminderEntry, missed: Boolean, onOpen: () -> Unit, onEdit: () -> Unit) {
    val context = LocalContext.current
    val note = e.note
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconTile(if (note == null) Ico.CALENDAR else Ico.ALARM, if (missed) jc.danger else jc.accent)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(note?.displayTitle ?: "Journal", style = JType.rowTitle, color = jc.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(reminderLabel(context, e.at, e.repeat), style = JType.secondary, color = if (missed) jc.danger else jc.accent, maxLines = 1)
            val sub = when {
                note == null -> "Daily reminder to write today's entry"
                isHabit(note) -> "Habit"
                note.isLocked -> null
                else -> note.snippet.takeIf { it.isNotBlank() }
            }
            sub?.let { Text(it, style = JType.small, color = jc.muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        // Habits and the journal set their reminder on their own screens, so only notes get the button.
        if (note != null && !isHabit(note)) IconAction(Ico.CLOCK, "Change reminder", onEdit, size = 20.dp)
        else Spacer(Modifier.width(12.dp))
    }
}

/**
 * A new reminder from the Reminders screen: a title (it becomes a note in Notes, so there's somewhere to tap
 * through to), optional details, and when. "Add to phone calendar" also puts it on the phone's default calendar
 * as an event; the switch is remembered for next time.
 */
@Composable
private fun NewReminderDialog(onTime: Boolean, onDismiss: () -> Unit, onSave: (String, String, Long, Repeat, Boolean) -> Unit) {
    val context = LocalContext.current
    var toCalendar by remember { mutableStateOf(calendarPref(context)) }
    var title by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var details by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var at by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(reminderChoices(ZonedDateTime.now()).first().second.toInstant().toEpochMilli())
    }
    var repeat by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(Repeat.NEVER) }
    var picking by remember { mutableStateOf(false) }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    MarginDialog(
        onDismissRequest = onDismiss,
        title = { Text("New reminder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    title, { title = it }, Modifier.fillMaxWidth().focusRequester(focus), singleLine = true, shape = JShape.sm,
                    label = { Text("Remind me to…") }, colors = passwordColors(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
                )
                androidx.compose.material3.OutlinedTextField(
                    details, { details = it }, Modifier.fillMaxWidth(), minLines = 2, maxLines = 4, shape = JShape.sm,
                    label = { Text("Details (optional)") }, colors = passwordColors(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
                )
                Row(
                    Modifier.fillMaxWidth().clip(JShape.sm).background(jc.surface).clickable(role = Role.Button) { picking = true }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    JIcon(Ico.CLOCK, jc.accent, size = 20.dp)
                    Column(Modifier.weight(1f)) {
                        Text("When", style = JType.small, color = jc.muted)
                        Text(reminderLabel(context, at, repeat), style = JType.item, color = jc.ink)
                    }
                    Text("Change", style = JType.small, color = jc.accent)
                }
                CalendarSwitch(toCalendar) { toCalendar = it }
            }
        },
        confirmButton = {
            TextAction("Save", {
                if (at <= System.currentTimeMillis()) picking = true else onSave(title.trim(), details.trim(), at, repeat, toCalendar)
            }, bold = true, enabled = title.isNotBlank())
        },
        dismissButton = { TextAction("Cancel", onDismiss) },
    )
    if (picking) ReminderDialog(
        current = null, currentRepeat = repeat, onTime = onTime,
        onPick = { time, r, _ -> at = time; repeat = r; picking = false },
        onRemove = {}, onDismiss = { picking = false },
    )
}

private const val FORM_PREFS = "reminder_form"
private const val KEY_CALENDAR = "add_to_calendar"

/** Whether "Add to phone calendar" was on last time. */
private fun calendarPref(context: Context) = context.getSharedPreferences(FORM_PREFS, Context.MODE_PRIVATE).getBoolean(KEY_CALENDAR, false)

/**
 * "Add to phone calendar" with its switch. Turning it on asks for calendar access the first time; the choice
 * is remembered for the next reminder.
 */
@Composable
private fun CalendarSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(CalendarEvents.allowed(context)) }
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
    ) { allowed = CalendarEvents.allowed(context) }
    fun set(value: Boolean) {
        onChange(value)
        context.getSharedPreferences(FORM_PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_CALENDAR, value).apply()
        if (value && !allowed) ask.launch(CalendarEvents.PERMISSIONS)
    }
    Row(
        Modifier.fillMaxWidth().clip(JShape.sm).clickable(role = Role.Switch) { set(!on) }.padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        JIcon(Ico.CALENDAR, jc.accent, size = 20.dp)
        Column(Modifier.weight(1f)) {
            Text("Add to phone calendar", style = JType.item, color = jc.ink)
            Text(
                if (!on || allowed) "Also saves it as an event in your default calendar."
                else "Calendar access is off, so your calendar app will open to save it.",
                style = JType.small, color = jc.muted,
            )
        }
        JSwitch(on, ::set)
    }
}

/**
 * After a reminder is set: with [toCalendar], puts it on the phone's calendar too (moving the note's event if it
 * has one), else takes any event it had off. Says what happened in a toast; when Margin can't write to the
 * calendar, the calendar app opens with the event filled in.
 */
internal fun reminderToCalendar(
    context: Context, scope: kotlinx.coroutines.CoroutineScope, noteId: String, title: String, details: String?, at: Long, repeat: Repeat, toCalendar: Boolean,
) {
    // "today, 6:00 PM" and "tomorrow, …" read as words; a weekday keeps its capital ("Mon, Nov 23").
    val label = reminderLabel(context, at, repeat)
    val said = "You'll be reminded " + if (label.startsWith("Today") || label.startsWith("Tomorrow")) label.replaceFirstChar { it.lowercaseChar() } else "on $label"
    fun toast(text: String, long: Boolean = false) = android.widget.Toast.makeText(context, text, if (long) android.widget.Toast.LENGTH_LONG else android.widget.Toast.LENGTH_SHORT).show()
    scope.launch {
        if (!toCalendar) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { CalendarEvents.remove(context, noteId) }
            toast("$said.")
            return@launch
        }
        val name = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { CalendarEvents.add(context, noteId, title, details, at, repeat) }
        if (name != null) toast("$said, and it's in your calendar ($name).", long = true)
        else {
            toast("$said.")
            runCatching { context.startActivity(CalendarEvents.insertIntent(title, details, at, repeat)) }
        }
    }
}

/** "in 45 minutes", "in 3 hours", "in 2 days": how long until [at]. */
private fun countdown(at: Long, now: Long): String = DateUtils.getRelativeTimeSpanString(at, now, DateUtils.MINUTE_IN_MILLIS).toString().replaceFirstChar { it.lowercaseChar() }

/** The time picker in Margin's colours rather than Material's default purple and pink. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun marginTimeColors() = TimePickerDefaults.colors(
    clockDialColor = jc.surface, clockDialSelectedContentColor = jc.bg, clockDialUnselectedContentColor = jc.ink,
    selectorColor = jc.accent, containerColor = jc.bg,
    periodSelectorBorderColor = jc.line, periodSelectorSelectedContainerColor = jc.selected,
    periodSelectorUnselectedContainerColor = jc.bg, periodSelectorSelectedContentColor = jc.accent,
    periodSelectorUnselectedContentColor = jc.muted,
    timeSelectorSelectedContainerColor = jc.selected, timeSelectorUnselectedContainerColor = jc.surface,
    timeSelectorSelectedContentColor = jc.accent, timeSelectorUnselectedContentColor = jc.ink,
)
