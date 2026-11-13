package com.shuva.jotter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
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
import com.shuva.jotter.data.NoteEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * The journal opens only with the notes passcode (or fingerprint), the same one locked notes use; the first
 * time, with no passcode yet, it asks to create one. Entries are locked notes, so they're encrypted too.
 */
@Composable
fun JournalGate(editor: EditorViewModel, fingerprint: String?, onCancel: () -> Unit, content: @Composable () -> Unit) {
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
    val submit: suspend (CharArray) -> String? = { pw -> if (editor.enterPassword(pw)) null else if (old) "Wrong password" else "Wrong passcode" }
    mode?.let { m ->
        if (old) OldPasswordDialog(onDismiss = onCancel, onSubmit = submit)
        else PasscodeDialog(
            m, onDismiss = onCancel,
            intro = "The journal is locked. Choose a passcode to open it; locked notes use the same one.",
            onOldPassword = if (m == PasswordMode.ENTER) ({ old = true }) else null, onSubmit = submit,
        )
    }
}

/** A calendar page: the journal. */
const val ICON_JOURNAL = "M5 4h14v16H5zM5 9h14M9 2v4M15 2v4M8 13h2M12 13h2M8 16h2"

/** Each day's journal entry is the note with this id, so any phone opens (and syncs) the same note for a day. */
fun journalId(day: LocalDate) = "journal-$day"

/** The day a journal entry is for, or null for any other note. */
fun journalDay(note: NoteEntity): LocalDate? =
    note.id.takeIf { it.startsWith("journal-") }?.let { runCatching { LocalDate.parse(it.removePrefix("journal-")) }.getOrNull() }

/** A new entry's title: the day written out ("Friday, 13 November 2026"). */
fun journalTitle(day: LocalDate): String = day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))

/** A month at a time; days with an entry carry a dot. Tapping a day opens its entry, starting one if needed. */
@Composable
fun JournalScreen(notes: List<NoteEntity>, onOpenDay: (LocalDate) -> Unit, onBack: () -> Unit) {
    val today = LocalDate.now()
    var month by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    val shown = YearMonth.parse(month)
    val written = remember(notes) { notes.mapNotNull(::journalDay).toSet() }
    val locale = Locale.getDefault()
    val firstDay = WeekFields.of(locale).firstDayOfWeek
    val blanks = (shown.atDay(1).dayOfWeek.value - firstDay.value + 7) % 7
    Scaffold(topBar = { NavBar("Folders", onBack) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            PageTitle("Journal")
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconAction(Ico.BACK, "Previous month", { month = shown.minusMonths(1).toString() })
                Text(
                    shown.month.getDisplayName(TextStyle.FULL_STANDALONE, locale) + " " + shown.year,
                    Modifier.weight(1f), style = JType.rowTitle, color = jc.ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                IconAction(Ico.CHEVRON, "Next month", { month = shown.plusMonths(1).toString() })
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row {
                    for (k in 0 until 7) {
                        Text(
                            DayOfWeek.of((firstDay.value - 1 + k) % 7 + 1).getDisplayName(TextStyle.NARROW, locale),
                            Modifier.weight(1f), style = JType.small, color = jc.muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
                val cells = List(blanks) { null } + (1..shown.lengthOfMonth()).map { shown.atDay(it) }
                cells.chunked(7).forEach { week ->
                    Row {
                        week.forEach { day ->
                            Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                                if (day != null) DayCell(day, day == today, day in written) { onOpenDay(day) }
                            }
                        }
                        repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            val count = written.count { YearMonth.from(it) == shown }
            Text(
                when (count) { 0 -> "No entries this month. Tap a day to write one."; 1 -> "1 entry this month"; else -> "$count entries this month" },
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), style = JType.secondary, color = jc.muted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DayCell(day: LocalDate, isToday: Boolean, hasEntry: Boolean, onClick: () -> Unit) {
    val label = journalTitle(day) + if (hasEntry) ", has an entry" else ""
    Column(
        Modifier.size(44.dp).clip(CircleShape)
            .then(if (isToday) Modifier.border(1.5.dp, jc.accent, CircleShape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text(
            day.dayOfMonth.toString(), style = JType.body.copy(fontWeight = if (hasEntry) FontWeight.SemiBold else FontWeight.Normal),
            color = if (isToday) jc.accent else jc.ink,
        )
        Box(Modifier.padding(top = 2.dp).size(5.dp).clip(CircleShape).background(if (hasEntry) jc.accent else androidx.compose.ui.graphics.Color.Transparent))
    }
}
