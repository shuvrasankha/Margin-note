package com.margin.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.margin.app.data.NoteEntity
import com.margin.app.data.isLocked
import com.margin.app.data.snippet
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import com.margin.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource

/**
 * The journal opens only with the notes passcode (or fingerprint), the same one locked notes use; the first
 * time, with no passcode yet, it asks to create one. Entries are locked notes, so they're encrypted too.
 */
@Composable
fun JournalGate(
    editor: EditorViewModel, fingerprint: String?, onCancel: () -> Unit,
    intro: String = stringResource(R.string.journal_the_journal_is_locked_choose),
    content: @Composable () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val unlocked by editor.sessionUnlocked.collectAsState()
    if (unlocked) return content()
    var mode by remember { mutableStateOf<PasswordMode?>(null) }
    var old by remember { mutableStateOf(false) } // a notes password from before passcodes
    val activity = LocalContext.current as? FragmentActivity
    LaunchedEffect(Unit) {
        val has = editor.hasPassword()
        if (has && fingerprint != null && activity != null) {
            val (iv, data) = fingerprint.split('.').map { java.util.Base64.getUrlDecoder().decode(it) }
            val r = Biometric.decrypt(activity, data, iv)
            if (r is Biometric.Result.Ok && editor.enterPassword(String(r.bytes, Charsets.UTF_8).toCharArray().also { r.bytes.fill(0) })) return@LaunchedEffect
        }
        mode = if (has) PasswordMode.ENTER else PasswordMode.CREATE
    }
    val submit: suspend (CharArray) -> String? = { pw -> if (editor.enterPassword(pw)) null else if (old) context.getString(R.string.journal_wrong_password) else context.getString(R.string.journal_wrong_passcode) }
    mode?.let { m ->
        if (old) OldPasswordDialog(onDismiss = onCancel, onSubmit = submit)
        else PasscodeDialog(
            m, onDismiss = onCancel,
            intro = intro,
            onOldPassword = if (m == PasswordMode.ENTER) ({ old = true }) else null, onSubmit = submit,
        )
    }
}

/** A calendar page: the journal. */
const val ICON_JOURNAL = "M5 4h14v16H5zM5 9h14M9 2v4M15 2v4M8 13h2M12 13h2M8 16h2"

/** Each day's journal entry is the note with this id, so any phone opens (and syncs) the same note for a day. */
fun journalId(day: LocalDate) = "journal-$day"

/** The daily "write in your journal" reminder's key in [com.margin.app.data.Reminders]; it isn't a note. */
const val JOURNAL_REMINDER = "journal-reminder"

/** The day a journal entry is for, or null for any other note. */
fun journalDay(note: NoteEntity): LocalDate? =
    note.id.takeIf { it.startsWith("journal-") }?.let { runCatching { LocalDate.parse(it.removePrefix("journal-")) }.getOrNull() }

/** A new entry's title: the day written out ("Friday, 13 November 2026"). */
fun journalTitle(day: LocalDate): String = day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))

/**
 * A month at a time (swipe or the arrows to move), with the days around it greyed in so each week is whole;
 * the week holding today sits on a band. Days with an entry carry a dot, days to come can't be opened.
 * Below: a button for today's entry and the month's entries, newest first, as an agenda.
 */
/** Today's prompt, above the write button while today has no entry; tapping it opens the entry. */
@Composable
private fun JournalPrompt(today: LocalDate, onWrite: () -> Unit) {
    // A question to start from, one a day, the same all day.
    val prompts = androidx.compose.ui.res.stringArrayResource(R.array.journal_prompts)
    val prompt = prompts[(today.dayOfYear - 1) % prompts.size]
    Column(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp).fillMaxWidth().clip(JShape.md).background(jc.card)
            .clickable(role = Role.Button, onClick = onWrite).padding(16.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(R.string.journal_todays_prompt), style = JType.section, color = jc.accent)
        Text(prompt, style = JType.item, color = jc.ink)
    }
}

@Composable
fun JournalScreen(
    notes: List<NoteEntity>, onOpenDay: (LocalDate) -> Unit, onDelete: (LocalDate) -> Unit, onBack: () -> Unit, backLabel: String = stringResource(R.string.journal_library),
    reminderAt: Long? = null, onSetReminder: (java.time.LocalTime) -> Unit = {}, onClearReminder: () -> Unit = {},
) {
    val today = LocalDate.now()
    val thisMonth = YearMonth.from(today)
    var month by rememberSaveable { mutableStateOf(thisMonth.toString()) }
    val shown = YearMonth.parse(month)
    val written = remember(notes) { notes.mapNotNull(::journalDay).toSet() }
    val locale = Locale.getDefault()
    val firstDay = WeekFields.of(locale).firstDayOfWeek
    val blanks = (shown.atDay(1).dayOfWeek.value - firstDay.value + 7) % 7
    val canGoOn = shown < thisMonth
    var drag by remember { mutableFloatStateOf(0f) }
    var deleting by remember { mutableStateOf<LocalDate?>(null) }
    var reminding by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val notifyPermission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) android.widget.Toast.makeText(context, context.getString(R.string.journal_reminder_set_but_notifications_are), android.widget.Toast.LENGTH_LONG).show()
    }
    if (reminding) JournalReminderDialog(
        reminderAt,
        onPick = { time ->
            reminding = false
            onSetReminder(time)
            if (android.os.Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) notifyPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        },
        onRemove = { reminding = false; onClearReminder() },
        onDismiss = { reminding = false },
    )
    deleting?.let { day ->
        MarginDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.journal_delete_this_entry)) },
            text = { Text(stringResource(R.string.journal_moves_to_recently_deleted_opening, journalTitle(day))) },
            confirmButton = { TextButton(onClick = { onDelete(day); deleting = null }) { Text(stringResource(R.string.common_delete), color = jc.danger) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
    val pageScroll = rememberScrollState()
    Scaffold(containerColor = jc.page, topBar = {
        NavBar(backLabel, onBack, color = jc.page) {
            if (shown != thisMonth) TextAction(stringResource(R.string.common_today), { month = thisMonth.toString() })
            IconAction(
                Ico.ALARM, reminderAt?.let { stringResource(R.string.journal_daily_reminder_at_change_it, timeLabel(context, it)) } ?: stringResource(R.string.journal_remind_me_to_write),
                { reminding = true }, fill = reminderAt != null, color = if (reminderAt != null) jc.accent else jc.ink,
            )
        }
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(pageScroll)) {
            // "Journal", then the streak and counts in one quiet line.
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.journal_journal), Modifier.semantics { heading() }, style = JType.pageTitle, color = jc.ink)
                val streak = habitStreak(written, today)
                val inMonth = written.count { YearMonth.from(it) == YearMonth.from(today) }
                Text(
                    listOf(
                        if (streak == 0) stringResource(R.string.journal_no_streak_yet) else pluralStringResource(R.plurals.journal_days_in_a_row, streak, streak),
                        pluralStringResource(R.plurals.journal_entries_this_month, inMonth, inMonth), stringResource(R.string.journal_in_all, written.size),
                    ).joinToString(" · "),
                    style = JType.secondary, color = jc.muted,
                )
            }
            Column(
                Modifier.padding(horizontal = 16.dp).clip(JShape.lg).background(jc.card).padding(horizontal = 6.dp, vertical = 10.dp)
                    // Swipe left for the next month, right for the one before.
                    .pointerInput(canGoOn) {
                        detectHorizontalDragGestures(
                            onDragStart = { drag = 0f },
                            onDragEnd = {
                                if (drag < -80.dp.toPx() && canGoOn) month = shown.plusMonths(1).toString()
                                else if (drag > 80.dp.toPx()) month = shown.minusMonths(1).toString()
                            },
                        ) { _, dx -> drag += dx }
                    },
            ) {
                // The month and the arrows to move, on the calendar itself.
                Row(Modifier.fillMaxWidth().padding(start = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        shown.month.getDisplayName(TextStyle.FULL_STANDALONE, locale) + " " + shown.year,
                        Modifier.weight(1f).semantics { heading() }, style = JType.subheading, color = jc.ink,
                    )
                    IconAction(Ico.BACK, stringResource(R.string.journal_previous_month), { month = shown.minusMonths(1).toString() }, color = jc.ink, size = 20.dp)
                    IconAction(Ico.CHEVRON, stringResource(R.string.journal_next_month), { month = shown.plusMonths(1).toString() }, enabled = canGoOn, color = jc.ink, size = 20.dp)
                }
                Row(Modifier.padding(bottom = 2.dp)) {
                    for (k in 0 until 7) {
                        val dow = DayOfWeek.of((firstDay.value - 1 + k) % 7 + 1)
                        Text(
                            dow.getDisplayName(TextStyle.SHORT_STANDALONE, locale).uppercase(locale),
                            Modifier.weight(1f), style = JType.section.copy(fontSize = 11.sp, letterSpacing = 0.06.em),
                            color = if (shown == thisMonth && dow == today.dayOfWeek) jc.accent else jc.muted, textAlign = TextAlign.Center,
                        )
                    }
                }
                val first = shown.atDay(1).minusDays(blanks.toLong())
                val weeks = (blanks + shown.lengthOfMonth() + 6) / 7
                for (w in 0 until weeks) {
                    val week = (0 until 7).map { first.plusDays(w * 7L + it) }
                    Row(
                        Modifier.padding(vertical = 2.dp).clip(JShape.sm)
                            .then(if (today in week) Modifier.background(jc.page) else Modifier),
                    ) {
                        week.forEach { day ->
                            Box(Modifier.weight(1f).height(50.dp), contentAlignment = Alignment.Center) {
                                DayCell(day, day == today, day > today, YearMonth.from(day) != shown, day in written) { onOpenDay(day) }
                            }
                        }
                    }
                }
            }
            if (today !in written) JournalPrompt(today) { onOpenDay(today) }
            PrimaryButton(
                if (today in written) stringResource(R.string.journal_open_todays_entry) else stringResource(R.string.journal_write_todays_entry), { onOpenDay(today) },
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            )
            val entries = written.filter { YearMonth.from(it) == shown }.sortedDescending()
            SectionLabel(if (entries.isEmpty()) stringResource(R.string.journal_no_entries) else pluralStringResource(R.plurals.journal_entries, entries.size, entries.size), Modifier.padding(top = 0.dp))
            if (entries.isEmpty()) Text(
                stringResource(R.string.journal_tap_a_day_to_write), Modifier.padding(horizontal = 20.dp), style = JType.secondary, color = jc.muted,
            )
            else {
                val byDay = notes.associateBy { journalDay(it) }
                DividedCard(entries, 76.dp) { day ->
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpenDay(day) }.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            Modifier.size(48.dp).clip(JShape.sm).background(if (day == today) jc.accent else jc.page),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                        ) {
                            Text("${day.dayOfMonth}", style = JType.subheading, color = if (day == today) jc.bg else jc.ink)
                            Text(
                                day.dayOfWeek.getDisplayName(TextStyle.SHORT_STANDALONE, locale).uppercase(locale),
                                style = JType.caption.copy(fontSize = 10.sp, lineHeight = 12.sp), color = if (day == today) jc.bg else jc.muted,
                            )
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                if (day == today) stringResource(R.string.common_today) else day.dayOfWeek.getDisplayName(TextStyle.FULL_STANDALONE, locale),
                                style = JType.rowTitle, color = jc.ink,
                            )
                            val note = byDay[day]
                            val preview = note?.takeIf { !it.isLocked }?.snippet?.takeIf { it.isNotBlank() }
                            Text(
                                preview ?: day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)),
                                style = JType.small, color = jc.muted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                        IconAction(Ico.TRASH, stringResource(R.string.journal_delete_entry_for, journalTitle(day)), { deleting = day }, color = jc.muted, size = 20.dp)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
internal fun DayCell(day: LocalDate, isToday: Boolean, isLater: Boolean, outside: Boolean, hasEntry: Boolean, onClick: () -> Unit) {
    val label = when {
        isToday && hasEntry -> stringResource(R.string.journal_day_today_has_entry, journalTitle(day))
        isToday -> stringResource(R.string.journal_day_today, journalTitle(day))
        hasEntry -> stringResource(R.string.journal_day_has_entry, journalTitle(day))
        else -> journalTitle(day)
    }
    Column(
        Modifier.size(42.dp).clip(CircleShape)
            .then(if (isToday) Modifier.background(jc.accent) else Modifier)
            .clickable(enabled = !isLater, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text(
            day.dayOfMonth.toString(),
            style = JType.row.copy(fontSize = 18.sp, fontWeight = if (hasEntry || isToday) FontWeight.SemiBold else FontWeight.Normal),
            color = when { isToday -> jc.bg; isLater || outside -> jc.muted.copy(alpha = 0.5f); else -> jc.ink },
        )
        Box(Modifier.padding(top = 2.dp).size(4.dp).clip(CircleShape).background(when { !hasEntry -> Color.Transparent; isToday -> jc.bg; else -> jc.accent }))
    }
}


private fun timeLabel(context: android.content.Context, at: Long): String =
    android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(at))

/** Picks the time of the daily journal reminder, or turns it off. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun JournalReminderDialog(current: Long?, onPick: (java.time.LocalTime) -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val start = current?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalTime() } ?: java.time.LocalTime.of(21, 0)
    val state = androidx.compose.material3.rememberTimePickerState(start.hour, start.minute, is24Hour = android.text.format.DateFormat.is24HourFormat(context))
    MarginDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.journal_daily_journal_reminder)) },
        text = {
            Column {
                Text(stringResource(R.string.journal_a_nudge_each_day_at), style = JType.small, color = jc.muted)
                Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.TimePicker(state, colors = marginTimeColors())
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(java.time.LocalTime.of(state.hour, state.minute)) }) { Text(if (current == null) stringResource(R.string.journal_remind_me) else stringResource(R.string.common_save)) } },
        dismissButton = {
            if (current != null) TextButton(onClick = onRemove) { Text(stringResource(R.string.journal_turn_off), color = jc.danger) }
            else TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}
