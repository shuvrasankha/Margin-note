package com.margin.app.ui

import android.content.Intent
import android.os.Build
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.margin.app.data.NoteEntity
import com.margin.app.data.NoteTemplate
import com.margin.app.data.color
import com.margin.app.data.dialable
import com.margin.app.data.displayTitle
import com.margin.app.data.invoke
import com.margin.app.data.isLocked
import com.margin.app.data.lastEdited
import com.margin.app.data.plainText
import com.margin.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource

internal const val DAY = 24 * 60 * 60 * 1000L

/** List time: "9:41 AM" today, "Yesterday", a weekday within the week, a date before that. */
internal fun listTime(context: android.content.Context, time: Long): String = when {
    DateUtils.isToday(time) -> DateUtils.formatDateTime(context, time, DateUtils.FORMAT_SHOW_TIME)
    DateUtils.isToday(time + DAY) -> context.getString(R.string.common_yesterday)
    System.currentTimeMillis() - time < 6 * DAY -> DateUtils.formatDateTime(context, time, DateUtils.FORMAT_SHOW_WEEKDAY)
    else -> DateUtils.formatDateTime(context, time, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH)
}

/** "Today, 9:41 AM", "Yesterday, 8:02 PM" or a date with the time. */
internal fun fullTime(context: android.content.Context, time: Long): String {
    val clock = DateUtils.formatDateTime(context, time, DateUtils.FORMAT_SHOW_TIME)
    return when {
        DateUtils.isToday(time) -> context.getString(R.string.common_today_at, clock)
        DateUtils.isToday(time + DAY) -> context.getString(R.string.common_yesterday_at, clock)
        else -> context.getString(R.string.common_date_at, DateUtils.formatDateTime(context, time, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH), clock)
    }
}

/** A conical flask. */
internal fun templateIcon(t: NoteTemplate) = when (t) {
    NoteTemplate.LAB -> ICON_FLASK
    NoteTemplate.MEETING -> "M9 11a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM3 20a6 6 0 0 1 12 0M16 5a3 3 0 0 1 0 6M21 20a6 6 0 0 0-4-5.7"
    NoteTemplate.LECTURE -> "M2 9l10-5 10 5-10 5zM6 11v5c3 2 9 2 12 0v-5M22 9v6"
    NoteTemplate.DAILY -> Ico.SUN
    NoteTemplate.RECIPE -> "M4 11h16M5 11v6a3 3 0 0 0 3 3h8a3 3 0 0 0 3-3v-6M9 4v4M12 3v5M15 4v4"
    NoteTemplate.TRIP -> "M12 21s-7-6.5-7-12a7 7 0 0 1 14 0c0 5.5-7 12-7 12zM12 11.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5"
}

/** A page with lines to fill in: a template. */
internal const val ICON_TEMPLATE = "M6 3h12v18H6zM9 8h6M9 12h6M9 16h3"

internal const val ICON_FLASK = "M9 3h6M10 3v6l-5.5 9.5A1.5 1.5 0 0 0 6 21h12a1.5 1.5 0 0 0 1.5-2.5L14 9V3M7.5 15h9"

internal const val ICON_CALL = "M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2"
internal const val ICON_MESSAGE = "M4 5h16v11H9l-5 4z"
internal const val ICON_CHAT = "M12 3a9 9 0 0 0-7.8 13.5L3 21l4.6-1.2A9 9 0 1 0 12 3zM9 9.5c0 3 2.5 5.5 5.5 5.5"
internal const val ICON_EDIT = "M4 20h4L19 9l-4-4L4 16zM13.5 6.5l4 4"

/** What to do with a phone number in a note: call, message, WhatsApp, copy, or (while editable) edit it. */
/** Space between a note's title and its first line, in sp. */
internal const val TITLE_GAP = 12f

@Composable
internal fun PhoneActions(number: String, onEdit: (() -> Unit)?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    fun open(intent: Intent, missing: String) {
        onDismiss()
        try { context.startActivity(intent) } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(context, missing, Toast.LENGTH_SHORT).show()
        }
    }
    val actions = listOfNotNull(
        Triple(ICON_CALL, stringResource(R.string.screen_common_call)) { open(Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:" + dialable(number))), context.getString(R.string.screen_common_no_app_on_this_phone)) },
        Triple(ICON_MESSAGE, stringResource(R.string.screen_common_message)) { open(Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:" + dialable(number))), context.getString(R.string.screen_common_no_app_on_this_phone_2)) },
        Triple(ICON_CHAT, "WhatsApp") { onDismiss(); openWhatsApp(context, number) },
        Triple(Ico.COPY, stringResource(R.string.screen_common_copy_number)) {
            clipboard.setText(androidx.compose.ui.text.AnnotatedString(number.trim()))
            onDismiss()
            if (android.os.Build.VERSION.SDK_INT < 33) Toast.makeText(context, context.getString(R.string.screen_common_number_copied), Toast.LENGTH_SHORT).show() // 13+ shows its own
        },
        onEdit?.let { Triple(ICON_EDIT, stringResource(R.string.screen_common_edit_number)) { it() } },
    )
    MarginDialog(
        onDismissRequest = onDismiss,
        title = { Text(number.trim()) },
        text = {
            Column {
                actions.forEach { (icon, label, action) ->
                    Row(
                        Modifier.fillMaxWidth().clip(JShape.sm).clickable(role = Role.Button, onClick = action)
                            .padding(horizontal = 4.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        JIcon(icon, jc.accent, size = 22.dp, stroke = 2f)
                        Text(label, style = JType.row, color = jc.ink)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/**
 * Opens a WhatsApp chat with [number]. WhatsApp needs the country code: a number written without one gets the
 * phone's country (from the SIM, the network, then the language setting). Tries WhatsApp, then WhatsApp
 * Business, then the wa.me page in a browser.
 */
internal fun openWhatsApp(context: android.content.Context, number: String) {
    val raw = dialable(number)
    val international = if (raw.startsWith("+")) raw else {
        val tm = context.getSystemService(android.telephony.TelephonyManager::class.java)
        val iso = listOfNotNull(tm?.simCountryIso, tm?.networkCountryIso, java.util.Locale.getDefault().country).firstOrNull { it.isNotBlank() }
        iso?.let { android.telephony.PhoneNumberUtils.formatNumberToE164(raw, it.uppercase()) } ?: raw
    }
    val uri = android.net.Uri.parse("https://wa.me/" + international.filter { it.isDigit() })
    for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b", null)) {
        try { context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { pkg?.let(::setPackage) }); return } catch (_: android.content.ActivityNotFoundException) { }
    }
    Toast.makeText(context, context.getString(R.string.common_whatsapp_missing), Toast.LENGTH_SHORT).show()
}

/**
 * Each line break is shown as a zero-width space at the end of its line, so a tap on an empty line (or past
 * the end of one) can land just after it, at the start of the NEXT line: the empty title of a note with text
 * below it couldn't be reached at all. When the tapped row holds the character before the cursor but not the
 * cursor, the cursor goes back to the end of the tapped line.
 */
internal fun placeTap(v: TextFieldValue, tap: Offset, layout: TextLayoutResult?): TextFieldValue {
    val l = layout ?: return v
    val o = v.selection.start
    if (!v.selection.collapsed || o <= 0 || o > v.text.length || v.text[o - 1] != '\n') return v
    if (l.layoutInput.text.length != v.text.length) return v // layout from an older frame
    val row = l.getLineForVerticalPosition(tap.y)
    return if (l.getLineForOffset(o) != row && l.getLineForOffset(o - 1) == row) v.copy(selection = TextRange(o - 1)) else v
}

/** A linked note at a glance: its title, when it was edited and its first lines, with a button to open it. */
@Composable
internal fun LinkedNotePreview(note: NoteEntity, onOpen: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val body = remember(note) {
        note.blocks.drop(1).filter { it.attachment == null && it.plainText.isNotBlank() }.take(12).joinToString("\n") { it.plainText.trim() }
    }
    val attachments = remember(note) { note.blocks.count { it.attachment != null } }
    MarginDialog(
        onDismissRequest = onDismiss,
        title = { Text(note.displayTitle, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.common_edited_at, fullTime(context, note.lastEdited)), style = JType.small, color = jc.muted)
                when {
                    note.isLocked -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        JIcon(Ico.LOCK, jc.muted, size = 15.dp, stroke = 2f)
                        Text(stringResource(R.string.screen_common_this_note_is_locked), style = JType.secondary, color = jc.muted)
                    }
                    body.isEmpty() && attachments == 0 -> Text(stringResource(R.string.screen_common_no_text_yet), style = JType.secondary, color = jc.muted)
                    else -> {
                        if (body.isNotEmpty()) Text(body, style = JType.secondary, color = jc.ink, maxLines = 8, overflow = TextOverflow.Ellipsis)
                        if (attachments > 0) Text(pluralStringResource(R.plurals.common_attachments, attachments, attachments), style = JType.small, color = jc.muted)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onOpen) { Text(stringResource(R.string.common_open)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
    )
}

/** Centered icon, heading, explanation and an optional button. */
@Composable
internal fun EmptyState(
    title: String,
    text: String,
    icon: String = Ico.EMPTY_NOTE,
    button: String? = null,
    onButton: () -> Unit = {},
    secondary: String? = null,
    onSecondary: () -> Unit = {},
) {
    Column(
        Modifier.fillMaxSize().appear().padding(horizontal = 44.dp).padding(bottom = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        EmptyHeading(icon, title)
        Text(text, style = JType.secondary.copy(fontSize = 16.sp, lineHeight = 23.sp), color = jc.muted, textAlign = TextAlign.Center)
        if (button != null) PrimaryButton(button, onButton, Modifier.padding(top = 28.dp))
        if (secondary != null) Box(Modifier.padding(top = 8.dp)) { TextAction(secondary, onSecondary) }
    }
}

/** A big icon in a soft circle, a heading, a line of help and an optional button: an empty page inside a list. */
@Composable
internal fun BigEmpty(icon: String, title: String, text: String, button: String? = null, onButton: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().appear().padding(horizontal = 32.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        EmptyHeading(icon, title)
        Text(text, style = JType.para, color = jc.muted, textAlign = TextAlign.Center)
        if (button != null) PrimaryButton(button, onButton, Modifier.padding(top = 28.dp))
    }
}

/** The empty pages' icon in a soft circle over their heading. */
@Composable
private fun EmptyHeading(icon: String, title: String) {
    Box(Modifier.size(96.dp).clip(CircleShape).background(jc.selected), contentAlignment = Alignment.Center) {
        JIcon(icon, jc.accent, size = 44.dp, stroke = 1.6f)
    }
    Text(title, Modifier.padding(top = 22.dp, bottom = 8.dp), style = JType.heading, color = jc.ink, textAlign = TextAlign.Center)
}

/** Search found nothing: what was searched, other things to try, and a note titled with the query. */
@Composable
internal fun NoResults(query: String, showTrash: Boolean, onTry: (String) -> Unit, onTrash: () -> Unit, onNew: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Column(Modifier.fillMaxWidth().padding(top = 72.dp, start = 16.dp, end = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            JIcon(Ico.SEARCH, jc.muted, size = 48.dp, stroke = 1.3f)
            Text(stringResource(R.string.common_no_notes_match, query), Modifier.padding(top = 20.dp, bottom = 8.dp), style = JType.heading, color = jc.ink, textAlign = TextAlign.Center)
            Text(stringResource(R.string.screen_common_searched_titles_text_checklists_and), style = JType.para, color = jc.muted, textAlign = TextAlign.Center)
        }
        val word = query.split(' ').firstOrNull { it.isNotBlank() }?.takeIf { it != query }
        val tries = listOfNotNull(
            word?.let { Triple(it, stringResource(R.string.screen_common_search_one_word), Ico.SEARCH) to { onTry(it) } },
            if (showTrash) Triple(stringResource(R.string.screen_common_recently_deleted), stringResource(R.string.screen_common_check_notes_you_removed), Ico.TRASH) to onTrash else null,
        )
        if (tries.isNotEmpty()) {
            Text(stringResource(R.string.screen_common_try), Modifier.padding(top = 40.dp, bottom = 4.dp), style = JType.section, color = jc.muted)
            tries.forEach { (t, onClick) ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button, onClick = onClick),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    JIcon(t.third, jc.accent, size = 18.dp, stroke = 1.8f)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(t.first, style = JType.item, color = jc.ink)
                        Text(t.second, style = JType.small, color = jc.muted)
                    }
                }
                HorizontalDivider(color = jc.line)
            }
        }
        Row(
            Modifier.padding(top = 20.dp, bottom = 24.dp).fillMaxWidth().height(48.dp).clip(JShape.sm)
                .border(1.dp, jc.line, JShape.sm).clickable(role = Role.Button, onClick = onNew),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            JIcon(Ico.COMPOSE, jc.ink, size = 18.dp, stroke = 2f)
            Text(stringResource(R.string.common_new_note_named, query), style = JType.item, color = jc.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
