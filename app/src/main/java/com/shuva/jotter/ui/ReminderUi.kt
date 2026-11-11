package com.shuva.jotter.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shuva.jotter.data.reminderChoices
import androidx.compose.foundation.layout.fillMaxSize
import com.shuva.jotter.data.isLocked
import com.shuva.jotter.data.snippet
import com.shuva.jotter.data.displayTitle
import com.shuva.jotter.data.NoteEntity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.HorizontalDivider
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

private const val DAY_MS = 24 * 60 * 60 * 1000L

/**
 * Picks when to be reminded about a note: quick choices, or a date and time of your own. [current] is the
 * reminder already set, which can be removed. [onTime] false adds a line about late reminders and a link to
 * the "Alarms & reminders" setting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderDialog(current: Long?, onTime: Boolean, onPick: (Long) -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var custom by remember { mutableStateOf(false) }
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val now = remember { ZonedDateTime.now() }

    // Wider than Android's default dialog: the calendar needs about 360dp.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(horizontal = 16.dp).widthIn(max = 400.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(jc.bg).verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp),
        ) {
            Text(
                if (!custom) "Remind me" else if (pickedDate == null) "Pick a day" else "Pick a time",
                Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = JType.rowTitle, color = jc.ink,
            )
            current?.let {
                if (!custom) Text("Set for ${reminderLabel(context, it)}", Modifier.padding(horizontal = 20.dp), style = JType.small, color = jc.muted)
            }
            when {
                !custom -> {
                    Column(Modifier.padding(top = 10.dp)) {
                        reminderChoices(now).forEach { (label, at) ->
                            val icon = when (label) {
                                "This evening" -> Ico.SUNSET
                                "Tomorrow morning" -> Ico.SUN
                                "Next week" -> Ico.CALENDAR
                                else -> Ico.CLOCK
                            }
                            ChoiceRow(icon, label, reminderLabel(context, at.toInstant().toEpochMilli())) { onPick(at.toInstant().toEpochMilli()) }
                        }
                        ChoiceRow(Ico.CALENDAR_PICK, "Pick date & time…", null) { custom = true }
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
                    // In Jotter's colours rather than Material's default purple and pink.
                    val colors = TimePickerDefaults.colors(
                        clockDialColor = jc.surface, clockDialSelectedContentColor = jc.bg, clockDialUnselectedContentColor = jc.ink,
                        selectorColor = jc.accent, containerColor = jc.bg,
                        periodSelectorBorderColor = jc.line, periodSelectorSelectedContainerColor = jc.selected,
                        periodSelectorUnselectedContainerColor = jc.bg, periodSelectorSelectedContentColor = jc.accent,
                        periodSelectorUnselectedContentColor = jc.muted,
                        timeSelectorSelectedContainerColor = jc.selected, timeSelectorUnselectedContainerColor = jc.surface,
                        timeSelectorSelectedContentColor = jc.accent, timeSelectorUnselectedContentColor = jc.ink,
                    )
                    Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) { TimePicker(state, colors = colors) }
                    error?.let { Text(it, Modifier.padding(horizontal = 20.dp), style = JType.small, color = jc.danger) }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
                        TextAction("Back", { pickedDate = null; error = null })
                        TextAction("Set reminder", {
                            val at = ZonedDateTime.of(pickedDate, LocalTime.of(state.hour, state.minute), ZoneId.systemDefault()).toInstant().toEpochMilli()
                            if (at <= System.currentTimeMillis()) error = "That time has passed. Pick a later one."
                            else onPick(at)
                        }, bold = true)
                    }
                }
            }
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
        Text(label, Modifier.weight(1f), style = JType.row.copy(fontSize = 16.sp), color = if (danger) jc.danger else jc.ink)
        detail?.let { Text(it, style = JType.small, color = jc.muted) }
    }
}

/**
 * The Reminders list: notes with a reminder set on this phone, soonest first, grouped into Today, Tomorrow,
 * This week and Later. A reminder that has rung is gone (it was the notification), so only upcoming ones show.
 */
@Composable
fun RemindersScreen(notes: List<NoteEntity>, reminders: Map<String, Long>, onOpen: (NoteEntity) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val rows = remember(notes, reminders) {
        notes.mapNotNull { n -> reminders[n.id]?.let { n to it } }.sortedBy { it.second }
    }
    val today = LocalDate.now()
    fun group(at: Long): String {
        val day = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate()
        return when {
            !day.isAfter(today) -> "Today"
            day == today.plusDays(1) -> "Tomorrow"
            day.isBefore(today.plusDays(7)) -> "This week"
            else -> "Later"
        }
    }
    Scaffold(topBar = { NavBar("Folders", onBack) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item { PageTitle("Reminders") }
            if (rows.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        JIcon(Ico.BELL, jc.muted, size = 40.dp)
                        Text("No reminders", Modifier.padding(top = 16.dp), style = JType.rowTitle, color = jc.ink)
                        Text(
                            "Open a note and tap the bell at the top to be reminded about it.",
                            Modifier.padding(top = 6.dp), style = JType.secondary, color = jc.muted, textAlign = TextAlign.Center,
                        )
                    }
                }
                return@LazyColumn
            }
            rows.groupBy { group(it.second) }.forEach { (label, items) ->
                item(key = "h:$label") { SectionLabel(label) }
                items(items, key = { it.first.id }) { (note, at) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpen(note) }.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        JIcon(Ico.BELL, jc.accent, size = 20.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(note.displayTitle, style = JType.rowTitle, color = jc.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(reminderLabel(context, at), style = JType.secondary, color = jc.accent, maxLines = 1)
                            if (!note.isLocked) note.snippet.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = JType.small, color = jc.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(start = 54.dp, end = 20.dp), color = jc.line)
                }
            }
        }
    }
}
