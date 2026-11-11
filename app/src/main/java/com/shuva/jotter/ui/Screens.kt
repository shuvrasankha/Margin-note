package com.shuva.jotter.ui

import android.os.Build
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import com.shuva.jotter.data.GrammarIssue
import com.shuva.jotter.data.MathSuggestion
import com.shuva.jotter.data.Plot
import com.shuva.jotter.data.mathSuggestionAt
import com.shuva.jotter.data.plotsIn
import com.shuva.jotter.data.effectiveParents
import com.shuva.jotter.data.reorderGroup
import androidx.compose.animation.core.animate
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.stateDescription
import com.shuva.jotter.data.KEEP_DELETED_DAYS
import com.shuva.jotter.data.Toggle
import com.shuva.jotter.data.invoke
import kotlin.math.roundToInt
import androidx.compose.foundation.Image
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.shuva.jotter.data.Attachment
import com.shuva.jotter.data.Drawing
import com.shuva.jotter.data.JotterJson
import com.shuva.jotter.data.isDrawing
import com.shuva.jotter.data.isAudio
import com.shuva.jotter.data.isDirty
import com.shuva.jotter.data.PdfFilePrintAdapter
import com.shuva.jotter.data.decodeImage
import com.shuva.jotter.data.isLink
import com.shuva.jotter.data.hostOf
import com.shuva.jotter.data.epochDay
import com.shuva.jotter.data.todoCount
import com.shuva.jotter.data.TodoStatus
import com.shuva.jotter.data.todoReport
import androidx.compose.ui.platform.LocalClipboardManager
import com.shuva.jotter.data.dialable
import com.shuva.jotter.data.findPhones
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.shuva.jotter.data.LinkPreviews
import com.shuva.jotter.data.LoadedPreview
import com.shuva.jotter.data.color
import com.shuva.jotter.data.MAX_INDENT
import com.shuva.jotter.data.Tint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import com.shuva.jotter.data.Mark
import android.widget.Toast
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.shuva.jotter.data.findLinks
import com.shuva.jotter.data.linkKey
import com.shuva.jotter.data.lastEdited
import com.shuva.jotter.data.resolveLink
import android.content.Intent
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.semantics.selected
import com.shuva.jotter.data.Accent
import com.shuva.jotter.data.TextSize
import com.shuva.jotter.data.ThemeMode
import com.shuva.jotter.data.shareText
import com.shuva.jotter.data.tagKey
import android.app.Activity
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.shuva.jotter.data.plainText
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.em
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.shuva.jotter.data.BlockType
import com.shuva.jotter.data.DEFAULT_FOLDER_ID
import com.shuva.jotter.data.NoteEntity
import com.shuva.jotter.data.NoteSort
import com.shuva.jotter.data.SmartFilter
import com.shuva.jotter.data.TagCount
import com.shuva.jotter.data.displayTitle
import com.shuva.jotter.data.findTags
import com.shuva.jotter.data.isLocked
import com.shuva.jotter.data.isSmart
import com.shuva.jotter.data.isValidTagName
import com.shuva.jotter.data.newId
import com.shuva.jotter.data.snippet
import com.shuva.jotter.data.subtreeIds
import com.shuva.jotter.sync.signInWithGoogle
import kotlinx.coroutines.launch

private const val DAY = 24 * 60 * 60 * 1000L

/** List time: "9:41 AM" today, "Yesterday", a weekday within the week, a date before that. */
private fun listTime(context: android.content.Context, time: Long): String = when {
    DateUtils.isToday(time) -> DateUtils.formatDateTime(context, time, DateUtils.FORMAT_SHOW_TIME)
    DateUtils.isToday(time + DAY) -> "Yesterday"
    System.currentTimeMillis() - time < 6 * DAY -> DateUtils.formatDateTime(context, time, DateUtils.FORMAT_SHOW_WEEKDAY)
    else -> DateUtils.formatDateTime(context, time, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH)
}

/** "Today, 9:41 AM", "Yesterday, 8:02 PM" or a date with the time. */
private fun fullTime(context: android.content.Context, time: Long): String {
    val clock = DateUtils.formatDateTime(context, time, DateUtils.FORMAT_SHOW_TIME)
    return when {
        DateUtils.isToday(time) -> "Today, $clock"
        DateUtils.isToday(time + DAY) -> "Yesterday, $clock"
        else -> DateUtils.formatDateTime(context, time, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH) + ", " + clock
    }
}

private fun plural(n: Int, one: String, many: String = one + "s") = "$n " + if (n == 1) one else many

private const val ICON_CALL = "M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2"
private const val ICON_MESSAGE = "M4 5h16v11H9l-5 4z"
private const val ICON_CHAT = "M12 3a9 9 0 0 0-7.8 13.5L3 21l4.6-1.2A9 9 0 1 0 12 3zM9 9.5c0 3 2.5 5.5 5.5 5.5"
private const val ICON_EDIT = "M4 20h4L19 9l-4-4L4 16zM13.5 6.5l4 4"

/** What to do with a phone number in a note: call, message, WhatsApp, copy, or (while editable) edit it. */
@Composable
private fun PhoneActions(number: String, onEdit: (() -> Unit)?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    fun open(intent: Intent, missing: String) {
        onDismiss()
        try { context.startActivity(intent) } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(context, missing, Toast.LENGTH_SHORT).show()
        }
    }
    val actions = listOfNotNull(
        Triple(ICON_CALL, "Call") { open(Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:" + dialable(number))), "No app on this phone can make calls.") },
        Triple(ICON_MESSAGE, "Message") { open(Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:" + dialable(number))), "No app on this phone can send messages.") },
        Triple(ICON_CHAT, "WhatsApp") { onDismiss(); openWhatsApp(context, number) },
        Triple(Ico.COPY, "Copy number") {
            clipboard.setText(androidx.compose.ui.text.AnnotatedString(number.trim()))
            onDismiss()
            if (android.os.Build.VERSION.SDK_INT < 33) Toast.makeText(context, "Number copied", Toast.LENGTH_SHORT).show() // 13+ shows its own
        },
        onEdit?.let { Triple(ICON_EDIT, "Edit number") { it() } },
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(number.trim()) },
        text = {
            Column {
                actions.forEach { (icon, label, action) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = action)
                            .padding(horizontal = 4.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        JIcon(icon, jc.accent, size = 22.dp, stroke = 2f)
                        Text(label, style = JType.row, color = jc.ink)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Opens a WhatsApp chat with [number]. WhatsApp needs the country code: a number written without one gets the
 * phone's country (from the SIM, the network, then the language setting). Tries WhatsApp, then WhatsApp
 * Business, then the wa.me page in a browser.
 */
private fun openWhatsApp(context: android.content.Context, number: String) {
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
    Toast.makeText(context, "WhatsApp isn't installed.", Toast.LENGTH_SHORT).show()
}

/**
 * Each line break is shown as a zero-width space at the end of its line, so a tap on an empty line (or past
 * the end of one) can land just after it, at the start of the NEXT line: the empty title of a note with text
 * below it couldn't be reached at all. When the tapped row holds the character before the cursor but not the
 * cursor, the cursor goes back to the end of the tapped line.
 */
private fun placeTap(v: TextFieldValue, tap: Offset, layout: TextLayoutResult?): TextFieldValue {
    val l = layout ?: return v
    val o = v.selection.start
    if (!v.selection.collapsed || o <= 0 || o > v.text.length || v.text[o - 1] != '\n') return v
    if (l.layoutInput.text.length != v.text.length) return v // layout from an older frame
    val row = l.getLineForVerticalPosition(tap.y)
    return if (l.getLineForOffset(o) != row && l.getLineForOffset(o - 1) == row) v.copy(selection = TextRange(o - 1)) else v
}

/** A linked note at a glance: its title, when it was edited and its first lines, with a button to open it. */
@Composable
private fun LinkedNotePreview(note: NoteEntity, onOpen: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val body = remember(note) {
        note.blocks.drop(1).filter { it.attachment == null && it.plainText.isNotBlank() }.take(12).joinToString("\n") { it.plainText.trim() }
    }
    val attachments = remember(note) { note.blocks.count { it.attachment != null } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(note.displayTitle, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Edited " + fullTime(context, note.lastEdited), style = JType.small, color = jc.muted)
                when {
                    note.isLocked -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        JIcon(Ico.LOCK, jc.muted, size = 15.dp, stroke = 2f)
                        Text("This note is locked.", style = JType.secondary, color = jc.muted)
                    }
                    body.isEmpty() && attachments == 0 -> Text("No text yet.", style = JType.secondary, color = jc.muted)
                    else -> {
                        if (body.isNotEmpty()) Text(body, style = JType.secondary, color = jc.ink, maxLines = 8, overflow = TextOverflow.Ellipsis)
                        if (attachments > 0) Text(plural(attachments, "attachment"), style = JType.small, color = jc.muted)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onOpen) { Text("Open") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** Centered icon, heading, explanation and an optional button. */
@Composable
private fun EmptyState(
    title: String,
    text: String,
    icon: String = Ico.EMPTY_NOTE,
    button: String? = null,
    onButton: () -> Unit = {},
    secondary: String? = null,
    onSecondary: () -> Unit = {},
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 44.dp).padding(bottom = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        JIcon(icon, jc.muted, size = 56.dp, stroke = 1.2f)
        Text(title, Modifier.padding(top = 20.dp, bottom = 8.dp), style = JType.heading, color = jc.ink, textAlign = TextAlign.Center)
        Text(text, style = JType.secondary.copy(fontSize = 16.sp, lineHeight = 23.sp), color = jc.muted, textAlign = TextAlign.Center)
        if (button != null) PrimaryButton(button, onButton, Modifier.padding(top = 28.dp))
        if (secondary != null) Box(Modifier.padding(top = 8.dp)) { TextAction(secondary, onSecondary) }
    }
}

/** Search found nothing: what was searched, other things to try, and a note titled with the query. */
@Composable
private fun NoResults(query: String, showTrash: Boolean, onTry: (String) -> Unit, onTrash: () -> Unit, onNew: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Column(Modifier.fillMaxWidth().padding(top = 72.dp, start = 16.dp, end = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            JIcon(Ico.SEARCH, jc.muted, size = 48.dp, stroke = 1.3f)
            Text("No notes match “$query”", Modifier.padding(top = 20.dp, bottom = 8.dp), style = JType.heading.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold), color = jc.ink, textAlign = TextAlign.Center)
            Text("Searched titles, text, checklists and attachments.", style = JType.secondary.copy(fontSize = 15.sp, lineHeight = 21.sp), color = jc.muted, textAlign = TextAlign.Center)
        }
        val word = query.split(' ').firstOrNull { it.isNotBlank() }?.takeIf { it != query }
        val tries = listOfNotNull(
            word?.let { Triple(it, "Search one word", Ico.SEARCH) to { onTry(it) } },
            if (showTrash) Triple("Recently Deleted", "Check notes you removed", Ico.TRASH) to onTrash else null,
        )
        if (tries.isNotEmpty()) {
            Text("Try", Modifier.padding(top = 40.dp, bottom = 4.dp), style = JType.section, color = jc.muted)
            tries.forEach { (t, onClick) ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button, onClick = onClick),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    JIcon(t.third, jc.accent, size = 18.dp, stroke = 1.8f)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(t.first, style = JType.row.copy(fontSize = 16.sp), color = jc.ink)
                        Text(t.second, style = JType.small, color = jc.muted)
                    }
                }
                HorizontalDivider(color = jc.line)
            }
        }
        Row(
            Modifier.padding(top = 20.dp, bottom = 24.dp).fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp))
                .border(1.dp, jc.line, RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onNew),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            JIcon(Ico.COMPOSE, jc.ink, size = 18.dp, stroke = 2f)
            Text("New note “$query”", style = JType.row.copy(fontSize = 16.sp), color = jc.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------- Folders ----------

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun FoldersScreen(
    vm: FoldersViewModel, onOpen: (String) -> Unit, onSearch: () -> Unit, onNew: () -> Unit, onEdit: () -> Unit, onSettings: () -> Unit,
    onTodos: () -> Unit, onReminders: () -> Unit,
) {
    val syncBanner by vm.syncBanner.collectAsState()
    val rows by vm.rows.collectAsState()
    val trashCount by vm.trashCount.collectAsState()
    val tags by vm.tags.collectAsState()
    val (allCount, pinnedCount) = vm.libraryCounts.collectAsState().value
    /** New Folder: open with this parent preselected ("" = top level); null = closed. */
    var creatingIn by remember { mutableStateOf<String?>(null) }
    var smartEditing by remember { mutableStateOf<FolderRow?>(null) }
    /** Name for a new smart folder whose conditions are being picked. */
    var creatingSmart by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<FolderRow?>(null) }
    var renaming by remember { mutableStateOf<FolderRow?>(null) }
    var moving by remember { mutableStateOf<FolderRow?>(null) }
    var deleting by remember { mutableStateOf<FolderRow?>(null) }
    var renamingTag by remember { mutableStateOf<TagCount?>(null) }
    val regular = rows.filter { !it.folder.isSmart }
    val smart = rows.filter { it.folder.isSmart }

    Scaffold(
        topBar = { NavBar(null, {}) { TextAction("Edit", onEdit); TextAction("Settings", onSettings) } },
        bottomBar = {
            BottomBar {
                IconAction(Ico.NEW_FOLDER, "New folder", { creatingIn = "" }, size = 24.dp)
                IconAction(Ico.COMPOSE, "New note", onNew, size = 24.dp)
            }
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item { PageTitle("Folders") }
            item {
                // Opens All Notes with its search field focused.
                Row(
                    Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp).fillMaxWidth().height(40.dp).clip(RoundedCornerShape(12.dp))
                        .background(jc.surface).clickable(role = Role.Button, onClick = onSearch).semantics { contentDescription = "Search" }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    JIcon(Ico.SEARCH, jc.muted, size = 17.dp, stroke = 2f)
                    Text("Search", style = JType.row, color = jc.muted)
                }
            }
            syncBanner?.let { banner -> item { SyncBannerRow(banner, vm::retrySync) } }
            item { SectionLabel("Library") }
            item { FolderListRow(Ico.FILE, "All Notes", allCount, onClick = { onOpen(ALL_ID) }) }
            item { FolderListRow(Ico.PIN, "Pinned", pinnedCount, onClick = { onOpen(PINNED_ID) }) }
            item {
                // To-do lists not finished yet; the screen shows the progress report.
                val notes by vm.liveNotes.collectAsState()
                val open = remember(notes) {
                    todoReport(notes, todayEpochDay(), java.time.ZoneId.systemDefault()).lists.count { it.status != TodoStatus.DONE }
                }
                FolderListRow(Ico.CHECKLIST, "To-dos", open, onClick = onTodos)
            }
            item {
                // Notes with a reminder set on this phone (of notes still around).
                val notes by vm.liveNotes.collectAsState()
                val reminders by vm.reminders.collectAsState()
                val count = remember(notes, reminders) { notes.count { it.id in reminders } }
                FolderListRow(Ico.BELL, "Reminders", count, onClick = onReminders)
            }
            item { SectionLabel("Folders") }
            items(regular, key = { it.folder.id }) { row ->
                FolderListRow(Ico.FOLDER, row.folder.name, row.count, row.depth, onClick = { onOpen(row.folder.id) }, onLongClick = { editing = row })
            }
            if (smart.isNotEmpty()) {
                item { SectionLabel("Smart folders") }
                items(smart, key = { it.folder.id }) { row ->
                    FolderListRow(
                        Ico.SMART_FOLDER, row.folder.name, row.count, sub = describe(row.folder.smartFilter!!, tags),
                        onClick = { onOpen(row.folder.id) }, onLongClick = { editing = row },
                    )
                }
            }
            if (tags.isNotEmpty()) {
                item { SectionLabel("Tags") }
                item {
                    FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        tags.forEach { tag ->
                            Chip("#" + tag.name, onClick = { onOpen(TAG_PREFIX + tag.key) }, trailing = "${tag.count}", onLongClick = { renamingTag = tag })
                        }
                    }
                }
            }
            item { SectionLabel("More") }
            item { FolderListRow(Ico.TRASH, "Recently Deleted", trashCount, onClick = { onOpen(TRASH_ID) }) }
            item {
                Text(
                    "Press and hold a folder or tag for more options.",
                    Modifier.padding(horizontal = 20.dp, vertical = 16.dp), style = JType.small, color = jc.muted,
                )
            }
        }
    }

    creatingIn?.let { parent ->
        NewFolderDialog(regular, parent.ifEmpty { null }, allowSmart = true, onDismiss = { creatingIn = null }) { name, parentId, smart ->
            creatingIn = null
            if (smart) creatingSmart = name else vm.create(name, parentId)
        }
    }
    creatingSmart?.let { initialName ->
        SmartFolderDialog("New smart folder", initialName, SmartFilter(), tags, onDismiss = { creatingSmart = null }) { name, filter ->
            vm.create(name, smartFilter = filter); creatingSmart = null
        }
    }
    smartEditing?.let { row ->
        SmartFolderDialog("Edit smart folder", row.folder.name, row.folder.smartFilter!!, tags, onDismiss = { smartEditing = null }) { name, filter ->
            vm.rename(row.folder.id, name); vm.setSmartFilter(row.folder.id, filter); smartEditing = null
        }
    }
    editing?.let { row ->
        val folder = row.folder
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(folder.name) },
            text = {
                Column {
                    @Composable
                    fun option(label: String, danger: Boolean = false, action: () -> Unit) = Text(
                        label, Modifier.fillMaxWidth().clickable { action(); editing = null }.padding(vertical = 12.dp),
                        style = JType.row, color = if (danger) jc.danger else jc.ink,
                    )
                    when {
                        folder.isSmart -> {
                            option("Edit") { smartEditing = row }
                            option("Delete", danger = true) { deleting = row }
                        }
                        folder.isDefault -> option("New folder inside") { creatingIn = folder.id }
                        else -> {
                            option("Rename") { renaming = row }
                            option("New folder inside") { creatingIn = folder.id }
                            option("Move") { moving = row }
                            option("Delete", danger = true) { deleting = row }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
    renaming?.let { row ->
        NameDialog("Rename folder", row.folder.name, onDismiss = { renaming = null }) { vm.rename(row.folder.id, it); renaming = null }
    }
    moving?.let { row ->
        // Not into itself or anything inside it; smart folders hold no folders.
        val inside = subtreeIds(rows.map { it.folder }, row.folder.id)
        val parentName = rows.firstOrNull { it.folder.id == row.folder.parentId }?.folder?.name
        FolderSheet(
            title = "Move folder", subject = row.folder.name,
            subjectNote = if (parentName != null) "Currently in $parentName" else "Currently at the top level",
            rows = regular.filter { it.folder.id !in inside }, currentId = row.folder.parentId,
            topLevel = row.folder.parentId != null,
            onDismiss = { moving = null },
        ) { vm.moveFolder(row.folder.id, it); moving = null }
    }
    deleting?.let { row -> DeleteFolderDialog(row, rows, onDismiss = { deleting = null }) { vm.delete(row.folder.id); deleting = null } }
    renamingTag?.let { tag ->
        NameDialog(
            "Rename #${tag.name} in ${plural(tag.count, "note")}", tag.name,
            onDismiss = { renamingTag = null },
            isValid = { isValidTagName(it.trim().removePrefix("#")) },
            label = "Tag",
        ) { vm.renameTag(tag.key, it); renamingTag = null }
    }
}

@Composable
private fun DeleteFolderDialog(row: FolderRow, rows: List<FolderRow>, onDismiss: () -> Unit, onDelete: () -> Unit) {
    val hasSubfolders = rows.any { it.folder.parentId == row.folder.id }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete \"${row.folder.name}\"?") },
        text = {
            Text(
                when {
                    row.folder.isSmart -> "The notes it shows aren't affected."
                    hasSubfolders -> "Its subfolders are deleted too. Their notes and its notes move to Recently Deleted."
                    else -> "Its notes will move to Recently Deleted."
                },
            )
        },
        confirmButton = { TextButton(onClick = onDelete) { Text("Delete", color = jc.danger) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** New Folder: name, smart toggle (conditions are picked next) and where it goes. */
@Composable
private fun NewFolderDialog(
    regular: List<FolderRow>,
    initialParent: String?,
    allowSmart: Boolean,
    onDismiss: () -> Unit,
    onCreate: (name: String, parentId: String?, smart: Boolean) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var smart by remember { mutableStateOf(false) }
    var parent by remember { mutableStateOf(initialParent) }
    var picking by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Folder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Folder name") }, shape = RoundedCornerShape(12.dp))
                if (allowSmart) Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Smart folder", style = JType.row, color = jc.ink)
                        Text("Fill it automatically by tag, date or type", style = JType.small, color = jc.muted)
                    }
                    JSwitch(smart, { smart = it })
                }
                // Smart folders are always top level.
                if (!smart) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Inside", Modifier.weight(1f), style = JType.row, color = jc.ink)
                    Box {
                        TextAction(regular.firstOrNull { it.folder.id == parent }?.folder?.name ?: "Top level", { picking = true })
                        DropdownMenu(expanded = picking, onDismissRequest = { picking = false }) {
                            DropdownMenuItem(text = { Text("Top level") }, onClick = { parent = null; picking = false })
                            regular.forEach { r ->
                                DropdownMenuItem(
                                    text = { Text(r.folder.name, Modifier.padding(start = 16.dp * r.depth)) },
                                    onClick = { parent = r.folder.id; picking = false },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name.trim(), parent.takeIf { !smart }, smart) }, enabled = name.isNotBlank()) {
                Text(if (smart) "Next" else "Create", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Edit folders: drag the handle to reorder a folder among the ones listed with it (same parent, or the smart
 * folders); tap a name to rename; the red button deletes. The default folder stays first and can't change.
 */
@Composable
fun EditFoldersScreen(vm: FoldersViewModel, onBack: () -> Unit) {
    val rows by vm.rows.collectAsState()
    var renaming by remember { mutableStateOf<FolderRow?>(null) }
    var deleting by remember { mutableStateOf<FolderRow?>(null) }
    var creating by remember { mutableStateOf(false) }
    val parents = remember(rows) { effectiveParents(rows.map { it.folder }) }
    val groups = remember(rows, parents) { rows.map { reorderGroup(it.folder, parents) } }
    var dragged by remember { mutableStateOf<Int?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val rowPx = with(LocalDensity.current) { EDIT_ROW.toPx() }
    val accent = jc.accent
    fun siblings(i: Int) = groups.indices.filter { groups[it] != null && groups[it] == groups[i] }
    // Where the dragged folder would land: its new group order, and the row the drop line sits above.
    val drop = dragged?.let { d -> dropOrder(siblings(d), d, d * rowPx + rowPx / 2 + dragY, rowPx) }
    val lineAt = drop?.let { (order, k) ->
        val others = order.filter { it != dragged }
        if (k < others.size) others[k]
        else { // after the last sibling's subfolders
            val last = others.lastOrNull() ?: dragged!!
            (last + 1 until rows.size).firstOrNull { rows[it].depth <= rows[last].depth } ?: rows.size
        }
    }
    fun commit(order: List<Int>) = vm.reorder(order.map { rows[it].folder.id })

    Scaffold(
        topBar = { NavBar("Folders", onBack) { TextAction("Done", onBack, bold = true) } },
        bottomBar = {
            BottomBar {
                TextAction("New Folder", { creating = true })
                Text(plural(rows.count { !it.folder.isSmart }, "folder"), style = JType.small, color = jc.muted)
                Spacer(Modifier.width(44.dp))
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState(), enabled = dragged == null)) {
            PageTitle("Folders")
            Text("Drag to reorder. Tap a name to rename.", Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp), style = JType.secondary, color = jc.muted)
            Column {
                rows.forEachIndexed { i, row ->
                    val f = row.folder
                    val lifted = i == dragged
                    val sibs = if (groups[i] == null) emptyList() else siblings(i)
                    val pos = sibs.indexOf(i)
                    Row(
                        Modifier.fillMaxWidth().height(EDIT_ROW).zIndex(if (lifted) 1f else 0f)
                            .graphicsLayer { if (lifted) { translationY = dragY; shadowElevation = 12.dp.toPx() } }
                            .background(jc.bg)
                            .drawBehind { if (lineAt == i) drawRect(accent, size = Size(size.width, 2.dp.toPx())) }
                            .semantics {
                                // Reordering without dragging, for screen readers.
                                customActions = listOfNotNull(
                                    CustomAccessibilityAction("Move up") { commit(sibs.toMutableList().apply { add(pos - 1, removeAt(pos)) }); true }.takeIf { pos > 0 },
                                    CustomAccessibilityAction("Move down") { commit(sibs.toMutableList().apply { add(pos + 1, removeAt(pos)) }); true }.takeIf { pos in 0 until sibs.lastIndex },
                                )
                            }
                            .padding(start = 16.dp + 24.dp * row.depth, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (f.isDefault) Spacer(Modifier.size(24.dp))
                        else Box(
                            Modifier.size(24.dp).clip(CircleShape).background(jc.danger).clickable(role = Role.Button) { deleting = row }
                                .semantics { contentDescription = "Delete ${f.name}" },
                            contentAlignment = Alignment.Center,
                        ) { Box(Modifier.size(10.dp, 2.dp).background(Color.White)) }
                        JIcon(if (f.isSmart) Ico.SMART_FOLDER else Ico.FOLDER, jc.accent, size = 20.dp)
                        Text(
                            f.name,
                            Modifier.weight(1f).clickable(enabled = !f.isDefault, role = Role.Button) { renaming = row },
                            style = JType.row, color = jc.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text("${row.count}", style = JType.secondary, color = jc.muted)
                        if (f.isDefault) Spacer(Modifier.size(44.dp))
                        else Box(
                            Modifier.size(44.dp).pointerInput(i, rows) {
                                detectDragGestures(
                                    onDragStart = { dragged = i; dragY = 0f },
                                    onDrag = { change, amount -> change.consume(); dragY += amount.y },
                                    // Computed here: this handler outlives the composition that created it.
                                    onDragEnd = { commit(dropOrder(siblings(i), i, i * rowPx + rowPx / 2 + dragY, rowPx).first); dragged = null; dragY = 0f },
                                    onDragCancel = { dragged = null; dragY = 0f },
                                )
                            }.semantics { contentDescription = "Reorder ${f.name}" },
                            contentAlignment = Alignment.Center,
                        ) { JIcon("M5 9h14M5 15h14", jc.muted, size = 22.dp) }
                    }
                    HorizontalDivider(Modifier.padding(start = 20.dp), color = jc.line)
                }
                if (lineAt == rows.size) HorizontalDivider(thickness = 2.dp, color = jc.accent)
            }
        }
    }
    renaming?.let { row ->
        NameDialog("Rename folder", row.folder.name, onDismiss = { renaming = null }) { vm.rename(row.folder.id, it); renaming = null }
    }
    deleting?.let { row -> DeleteFolderDialog(row, rows, onDismiss = { deleting = null }) { vm.delete(row.folder.id); deleting = null } }
    if (creating) {
        NewFolderDialog(rows.filter { !it.folder.isSmart }, null, allowSmart = false, onDismiss = { creating = false }) { name, parentId, _ ->
            vm.create(name, parentId); creating = false
        }
    }
}

private val EDIT_ROW = 56.dp

/**
 * Drag-to-reorder within one group. [siblings] are the group's row indices in order, [dragged] one of them,
 * [centerY] the dragged row's centre now. Rows are [rowPx] tall. Returns the group's new order and the slot
 * the dragged row takes among the others.
 */
fun dropOrder(siblings: List<Int>, dragged: Int, centerY: Float, rowPx: Float): Pair<List<Int>, Int> {
    val others = siblings.filter { it != dragged }
    val k = others.count { it * rowPx + rowPx / 2 < centerY }
    return others.toMutableList().apply { add(k, dragged) } to k
}

/** One line describing what a smart folder shows, e.g. "#trips or #work · Has a checklist". */
private fun describe(filter: SmartFilter, tags: List<TagCount>): String {
    val names = filter.tags.map { key -> "#" + (tags.firstOrNull { it.key == key }?.name ?: key) }
    val parts = listOfNotNull(
        names.takeIf { it.isNotEmpty() }?.joinToString(if (filter.allTags) " and " else " or "),
        "Has a checklist".takeIf { filter.hasChecklist },
        "Pinned".takeIf { filter.pinnedOnly },
        "Has attachments".takeIf { filter.hasAttachments },
        filter.editedWithinDays?.let { "Edited in the last $it days" },
    )
    return if (parts.isEmpty()) "Every note" else parts.joinToString(" · ")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderListRow(icon: String, name: String, count: Int, depth: Int = 0, sub: String? = null, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    val start = 20.dp + 24.dp * depth
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (sub == null) 48.dp else 60.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = start, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        JIcon(icon, jc.accent, size = 20.dp)
        Column(Modifier.weight(1f).padding(vertical = if (sub == null) 0.dp else 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = JType.row, color = jc.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            sub?.let { Text(it, style = JType.small, color = jc.muted, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        Text("$count", style = JType.secondary, color = jc.muted)
        JIcon(Ico.CHEVRON, jc.muted, size = 14.dp, stroke = 2.2f)
    }
    HorizontalDivider(Modifier.padding(start = start, end = 20.dp), color = jc.line)
}

/** Name plus filter for a smart folder. Every condition that's set must match. */
@Composable
private fun SmartFolderDialog(
    title: String,
    initialName: String,
    initial: SmartFilter,
    tags: List<TagCount>,
    onDismiss: () -> Unit,
    onSave: (String, SmartFilter) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var filter by remember { mutableStateOf(initial) }

    @Composable
    fun choice(label: String, selected: Boolean, onClick: () -> Unit) = Pill(label, selected, onClick)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }, shape = RoundedCornerShape(12.dp))
                Text("Shows notes that match all of these.", style = JType.small, color = jc.muted)
                Text("Tags", Modifier.padding(top = 8.dp), style = JType.section, color = jc.muted)
                if (tags.isEmpty()) {
                    Text("No tags yet. Type #word in a note to add one.", style = JType.small, color = jc.muted)
                } else {
                    tags.forEach { tag ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = tag.key in filter.tags,
                                onCheckedChange = { on -> filter = filter.copy(tags = if (on) filter.tags + tag.key else filter.tags - tag.key) },
                            )
                            Text("#" + tag.name, style = JType.row)
                        }
                    }
                    if (filter.tags.size > 1) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            choice("Any of these", !filter.allTags) { filter = filter.copy(allTags = false) }
                            choice("All of these", filter.allTags) { filter = filter.copy(allTags = true) }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Has a checklist", Modifier.weight(1f), style = JType.row)
                    JSwitch(filter.hasChecklist, { filter = filter.copy(hasChecklist = it) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Pinned", Modifier.weight(1f), style = JType.row)
                    JSwitch(filter.pinnedOnly, { filter = filter.copy(pinnedOnly = it) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Has attachments", Modifier.weight(1f), style = JType.row)
                    JSwitch(filter.hasAttachments, { filter = filter.copy(hasAttachments = it) })
                }
                Text("Edited", Modifier.padding(top = 8.dp), style = JType.section, color = jc.muted)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(null to "Any time", 7 to "Last 7 days", 30 to "Last 30 days").forEach { (days, label) ->
                        choice(label, filter.editedWithinDays == days) { filter = filter.copy(editedWithinDays = days) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name.trim(), filter) }, enabled = name.isNotBlank()) { Text("Save", fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    isValid: (String) -> Boolean = { it.isNotBlank() },
    label: String = "Name",
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(label) }, shape = RoundedCornerShape(12.dp)) },
        confirmButton = { TextButton(onClick = { onSave(name) }, enabled = isValid(name)) { Text("Save", fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------- Note list ----------

@Composable
fun NoteListScreen(
    vm: NotesViewModel,
    folderId: String,
    search: Boolean,
    folderName: String,
    folders: List<FolderRow>,
    previewLines: Int,
    grid: Boolean,
    onGrid: (Boolean) -> Unit,
    onOpen: (NoteEntity) -> Unit,
    onNew: () -> Unit,
    onNewWith: (String) -> Unit,
    onTrash: () -> Unit,
    onSettings: () -> Unit,
    onBack: () -> Unit,
) {
    LaunchedEffect(folderId) { vm.show(folderId) }
    val filter by vm.filter.collectAsState()
    val hasNotes by vm.hasNotes.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    // Offer undo for the last delete or move. A newer one replaces it; leaving the list drops it.
    LaunchedEffect(vm.undo) {
        val action = vm.undo ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(action.message, actionLabel = "Undo", duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) vm.undo(action) else vm.undoExpired(action)
    }
    DisposableEffect(Unit) { onDispose { vm.undo?.let(vm::undoExpired) } }
    val notes by vm.notes.collectAsState()
    val entries by vm.entries.collectAsState()
    val folder by vm.folder.collectAsState()
    val query by vm.query.collectAsState()
    val results by vm.searchResults.collectAsState()
    val scope by vm.searchScope.collectAsState()
    val isTrash = folderId == TRASH_ID
    // No new-note button in a tag list, Pinned or a smart folder: a note shows there only once it matches.
    val canCreate = !isTrash && folderId != PINNED_ID && !folderId.startsWith(TAG_PREFIX) && folder?.isSmart != true
    val keepDays by vm.keepDeletedDays.collectAsState()
    var confirmEmpty by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    /** Notes being moved: the selection or one swiped note. */
    var moving by remember { mutableStateOf<Set<String>?>(null) }
    val selecting = vm.selected.isNotEmpty()
    androidx.activity.compose.BackHandler(enabled = selecting) { vm.clearSelection() }
    // Notes that left the list (deleted, moved elsewhere) can't stay selected.
    LaunchedEffect(notes) {
        val ids = notes.map { it.id }.toSet()
        vm.selected.filter { it !in ids }.forEach(vm::toggleSelected)
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                Snackbar(
                    data, shape = RoundedCornerShape(14.dp), containerColor = jc.ink, contentColor = jc.bg, actionColor = jc.bg,
                )
            }
        },
        topBar = {
            if (selecting) NavBar(null, {}) { TextAction("Done", vm::clearSelection, bold = true) }
            else NavBar("Folders", onBack) {
                if (!isTrash && query.isBlank()) {
                    val f = folder
                    if (f != null) SortMenu(f.sortBy, f.sortAscending, grid = grid, onGrid = onGrid, onChange = vm::setSort)
                    else ViewMenu(grid, onGrid)
                }
                TextAction("Settings", onSettings)
            }
        },
        bottomBar = {
            when {
                selecting && isTrash -> BottomBar {
                    val n = vm.selected.size
                    TextAction("Recover $n", vm::recoverSelected)
                    Text("$n selected", style = JType.small, color = jc.muted)
                    TextAction("Delete $n", { confirmDelete = true }, color = jc.danger)
                }
                selecting -> BottomBar {
                    val allPinned = notes.filter { it.id in vm.selected }.all { it.isPinned }
                    Row {
                        IconAction(Ico.PIN, if (allPinned) "Unpin" else "Pin", vm::pinSelected, fill = allPinned)
                        IconAction(Ico.MOVE, "Move", { moving = vm.selected })
                    }
                    Text("${vm.selected.size} selected", style = JType.small, color = jc.muted)
                    // Same width as the left pair so the count stays centred.
                    Row(Modifier.width(88.dp), horizontalArrangement = Arrangement.End) { IconAction(Ico.TRASH, "Delete", vm::trashSelected, color = jc.danger) }
                }
                isTrash -> if (notes.isNotEmpty()) BottomBar {
                    Spacer(Modifier.width(44.dp))
                    Text(plural(notes.size, "Note"), style = JType.small, color = jc.muted)
                    TextAction("Delete all", { confirmEmpty = true }, color = jc.danger)
                }
                else -> BottomBar {
                    Spacer(Modifier.size(44.dp))
                    Text(if (query.isBlank()) plural(notes.size, "Note") else plural(notes.size, "Result"), style = JType.small, color = jc.muted)
                    if (canCreate) IconAction(Ico.COMPOSE, "New note", onNew, size = 24.dp) else Spacer(Modifier.size(44.dp))
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            PageTitle(folderName)
            if (isTrash) {
                Text(
                    "Notes stay here for $keepDays days, then they're removed for good.",
                    Modifier.padding(horizontal = 20.dp).padding(bottom = 4.dp),
                    style = JType.secondary.copy(lineHeight = 21.sp), color = jc.muted,
                )
            } else {
                SearchField(query, { vm.query.value = it; if (it.isBlank()) vm.searchScope.value = SearchScope.TOP }, "Search all notes", Modifier.padding(horizontal = 20.dp), autoFocus = search)
                if (hasNotes) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // In Pinned every note is pinned already, so that pill would do nothing.
                        if (query.isBlank()) ListFilter.entries.filterNot { it == ListFilter.PINNED && folderId == PINNED_ID }
                            .forEach { f -> Pill(f.label, f == filter, { vm.filter.value = f }) }
                        else SearchScope.entries.forEach { sc ->
                            val n = when (sc) { SearchScope.TOP -> null; SearchScope.NOTES -> results.notes.size; SearchScope.ATTACHMENTS -> results.attachments.size }
                            Pill(if (n == null) sc.label else "${sc.label} · $n", sc == scope, { vm.searchScope.value = sc })
                        }
                    }
                }
            }
            when {
                notes.isEmpty() && hasNotes && filter == ListFilter.PINNED -> EmptyState("No pinned notes", "Swipe right on a note to pin it.", icon = Ico.PIN)
                notes.isEmpty() && hasNotes && filter == ListFilter.CHECKLISTS -> EmptyState("No checklists", "Notes with a checklist show up here.", icon = Ico.CHECKLIST)
                query.isNotBlank() && entries.isEmpty() -> NoResults(
                    query.trim(), showTrash = !isTrash, onTry = { vm.query.value = it }, onTrash = onTrash, onNew = { onNewWith(query.trim()) },
                )
                notes.isEmpty() && isTrash -> EmptyState("Nothing here", "Notes you delete stay here for $keepDays days.", icon = Ico.TRASH)
                notes.isEmpty() && canCreate -> EmptyState(
                    "No notes yet", "Notes you write or move into $folderName will show up here.", button = "New note", onButton = onNew,
                    secondary = if (folder != null) "Move existing notes here" else null, onSecondary = { picking = true },
                )
                notes.isEmpty() -> EmptyState("No notes yet", "Notes show up here when they match.")
                // Search results and Recently Deleted keep rows: their extra details (match, days left) need the width.
                grid && !isTrash && query.isBlank() -> NoteGrid(entries, folder?.sortBy ?: NoteSort.EDITED, vm, onOpen)
                else -> NoteList(
                    entries, folder?.sortBy ?: NoteSort.EDITED, isTrash, previewLines, keepDays, query.trim(),
                    folders.associate { it.folder.id to it.folder.name }, vm, onOpen, onMove = { moving = setOf(it.id) },
                )
            }
        }
    }

    moving?.let { ids ->
        val count = ids.size
        val one = notes.firstOrNull { it.id in ids }
        MoveDialog(
            folders, currentFolderId = if (count == 1 && one != null) one.folderId else folderId,
            subject = if (count == 1) one?.displayTitle else plural(count, "note"),
            title = if (count == 1) "Move note" else "Move notes",
            onDismiss = { moving = null },
        ) { id -> vm.moveNotes(ids, id, folders.firstOrNull { it.folder.id == id }?.folder?.name ?: "folder"); moving = null }
    }
    if (picking) {
        val all by vm.allNotes.collectAsState()
        PickNotesDialog(all.filter { it.folderId != folderId }, folders, folderName, onDismiss = { picking = false }) { ids ->
            vm.moveNotes(ids, folderId, folderName, pool = all); picking = false
        }
    }
    if (confirmDelete) {
        val n = vm.selected.size
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${plural(n, "note")} permanently?") },
            text = { Text("This can't be undone.") },
            confirmButton = { TextButton(onClick = { vm.deleteSelectedForever(); confirmDelete = false }) { Text("Delete $n", color = jc.danger) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("Delete all notes permanently?") },
            text = { Text("This can't be undone.") },
            confirmButton = { TextButton(onClick = { vm.emptyTrash(); confirmEmpty = false }) { Text("Delete all", color = jc.danger) } },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun NoteList(
    entries: List<ListEntry>,
    sortBy: NoteSort,
    isTrash: Boolean,
    previewLines: Int,
    keepDays: Int,
    query: String,
    folderNames: Map<String, String>,
    vm: NotesViewModel,
    onOpen: (NoteEntity) -> Unit,
    onMove: (NoteEntity) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(entries, key = {
            when (it) {
                is ListEntry.Row -> it.note.id
                is ListEntry.Header -> "h:" + it.text
                is ListEntry.AttachmentRow -> "a:" + it.note.id + ":" + it.attachment.file
            }
        }) { entry ->
            when (entry) {
                is ListEntry.Header -> SectionHeader(entry.text, entry.collapsed, vm::togglePinnedCollapsed)
                is ListEntry.AttachmentRow -> AttachmentHitRow(entry, query, folderNames[entry.note.folderId], onOpen)
                is ListEntry.Row -> {
                    val note = entry.note
                    // Recently Deleted: when it was deleted (or the days left near the end) and where it came from.
                    val trashMeta = if (!isTrash) null else {
                        val left = keepDays - ((System.currentTimeMillis() - (note.trashedAt ?: 0)) / DAY).toInt()
                        val context = LocalContext.current
                        listOfNotNull(
                            if (left <= 7) plural(left.coerceAtLeast(0), "day") + " left" else "Deleted " + listTime(context, note.trashedAt ?: 0),
                            folderNames[note.folderId]?.let { "from $it" },
                        ).joinToString(" · ")
                    }
                    val search = if (query.isEmpty()) null else folderNames[note.folderId].orEmpty() to searchSnippet(note, query)
                    NoteRow(note, note.sortTime(sortBy), isTrash, previewLines, trashMeta, vm, onOpen, onMove, query, search)
                }
            }
        }
    }
}

/** [text] with the search match tinted. */
@Composable
private fun withHit(text: String, hit: IntRange?) = androidx.compose.ui.text.buildAnnotatedString {
    append(text)
    if (hit != null && hit.last < text.length) addStyle(SpanStyle(background = jc.accent.copy(alpha = 0.25f), color = jc.ink), hit.first, hit.last + 1)
}

/** Search: an attachment whose name matches, with the note it's in. Opens that note. */
@Composable
private fun AttachmentHitRow(entry: ListEntry.AttachmentRow, query: String, folderName: String?, onOpen: (NoteEntity) -> Unit) {
    val name = entry.attachment.name
    val at = name.indexOf(query, ignoreCase = true)
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(entry.note) }.padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(44.dp, 52.dp).clip(RoundedCornerShape(8.dp)).background(jc.surface), contentAlignment = Alignment.Center) {
            JIcon(if (entry.attachment.isAudio) Ico.MIC else Ico.FILE, jc.muted, size = 22.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(withHit(name, if (at < 0) null else at until at + query.length), style = JType.row, color = jc.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull("In " + entry.note.displayTitle, folderName).joinToString(" · "),
                style = JType.small, color = jc.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Offline with changes waiting, or sync failed (tap to try again). */
@Composable
private fun SyncBannerRow(banner: com.shuva.jotter.sync.SyncBanner, onRetry: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 20.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(jc.surface)
            .let { if (banner.retry) it.clickable(role = Role.Button, onClick = onRetry) else it }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        JIcon(Ico.CLOUD_OFF, if (banner.retry) jc.danger else jc.muted, size = 20.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            banner.title?.let { Text(it, style = JType.rowTitle.copy(fontSize = 15.sp), color = jc.ink) }
            Text(banner.text, style = JType.small.copy(lineHeight = 18.sp), color = if (banner.title != null) jc.muted else jc.ink)
        }
    }
}

/** "Move existing notes here": tick notes from other folders. */
@Composable
private fun PickNotesDialog(notes: List<NoteEntity>, folders: List<FolderRow>, folderName: String, onDismiss: () -> Unit, onMove: (Set<String>) -> Unit) {
    var picked by remember { mutableStateOf(emptySet<String>()) }
    val names = remember(folders) { folders.associate { it.folder.id to it.folder.name } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move notes to $folderName") },
        text = {
            if (notes.isEmpty()) Text("There are no notes in other folders.", style = JType.secondary, color = jc.muted)
            else LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(notes.sortedByDescending { it.lastEdited }, key = { it.id }) { n ->
                    val on = n.id in picked
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { picked = if (on) picked - n.id else picked + n.id }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CheckCircle(on)
                        Column(Modifier.weight(1f)) {
                            Text(n.displayTitle, style = JType.row, color = jc.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            names[n.folderId]?.let { Text(it, style = JType.small, color = jc.muted) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onMove(picked) }, enabled = picked.isNotEmpty()) {
                Text(if (picked.isEmpty()) "Move" else "Move ${picked.size}", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Sort key and direction for the open folder, behind a "⋯" button (or a [label] text button). Picking a
 * key starts it in its natural direction.
 */
@Composable
private fun SortMenu(
    sortBy: NoteSort, ascending: Boolean, label: String? = null,
    /** Shown with a View as section (List or Grid) when set. */
    grid: Boolean? = null, onGrid: (Boolean) -> Unit = {},
    onChange: (NoteSort, Boolean) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        if (label == null) IconAction(Ico.MORE, if (grid != null) "View and sort" else "Sort", { open = true }, fill = true) else TextAction(label, { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            @Composable
            fun item(text: String, checked: Boolean, onClick: () -> Unit) = DropdownMenuItem(
                text = { Text(text, style = JType.row) },
                leadingIcon = { if (checked) JIcon(Ico.CHECK, jc.accent, size = 18.dp, stroke = 2.6f) else Spacer(Modifier.size(18.dp)) },
                onClick = { onClick(); open = false },
            )
            if (grid != null) {
                ViewChoices(grid) { onGrid(it); open = false }
                HorizontalDivider(color = jc.line)
            }
            Text("Sort by", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = JType.section, color = jc.muted)
            listOf(NoteSort.EDITED to "Date edited", NoteSort.CREATED to "Date created", NoteSort.TITLE to "Title").forEach { (key, text) ->
                item(text, key == sortBy) { onChange(key, key == NoteSort.TITLE) }
            }
            HorizontalDivider(color = jc.line)
            val (first, second) = if (sortBy == NoteSort.TITLE) "A to Z" to "Z to A" else "Oldest first" to "Newest first"
            listOf(true to first, false to second).forEach { (asc, text) -> item(text, asc == ascending) { onChange(sortBy, asc) } }
        }
    }
}

/** Lists without their own sort (smart lists): just List or Grid. */
@Composable
private fun ViewMenu(grid: Boolean, onGrid: (Boolean) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconAction(Ico.MORE, "View", { open = true }, fill = true)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ViewChoices(grid) { onGrid(it); open = false }
        }
    }
}

private const val ICON_LIST = "M9 6h11M9 12h11M9 18h11M4.5 6h.01M4.5 12h.01M4.5 18h.01"
private const val ICON_GRID = "M4 4h6.5v6.5H4zM13.5 4H20v6.5h-6.5zM4 13.5h6.5V20H4zM13.5 13.5H20V20h-6.5z"

/** The View as section of a menu: List and Grid with their icons; the one in use is highlighted. */
@Composable
private fun ViewChoices(grid: Boolean, onPick: (Boolean) -> Unit) {
    Text("View as", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = JType.section, color = jc.muted)
    listOf(Triple(false, "List", ICON_LIST), Triple(true, "Grid", ICON_GRID)).forEach { (value, text, icon) ->
        val on = grid == value
        val color = if (on) jc.accent else jc.ink
        DropdownMenuItem(
            text = { Text(text, style = JType.row, color = color, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal) },
            leadingIcon = { JIcon(icon, color, size = 20.dp, stroke = 2f) },
            onClick = { onPick(value) },
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp).clip(RoundedCornerShape(10.dp))
                .background(if (on) jc.accent.copy(alpha = 0.12f) else Color.Transparent)
                .semantics { selected = on },
        )
    }
}

/** Notes as two columns of cards, each as tall as its text; section headers (Pinned, Today...) span both. */
@Composable
private fun NoteGrid(entries: List<ListEntry>, sortBy: NoteSort, vm: NotesViewModel, onOpen: (NoteEntity) -> Unit) {
    LazyVerticalStaggeredGrid(
        StaggeredGridCells.Fixed(2), Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalItemSpacing = 10.dp,
    ) {
        entries.forEach { entry ->
            when (entry) {
                is ListEntry.Header -> item(key = "h:" + entry.text, span = StaggeredGridItemSpan.FullLine) {
                    // Nudged left so the header's text lines up with the text inside the cards.
                    Box(Modifier.offset(x = (-4).dp)) { SectionHeader(entry.text, entry.collapsed, vm::togglePinnedCollapsed) }
                }
                is ListEntry.Row -> item(key = entry.note.id) { NoteCard(entry.note, entry.note.sortTime(sortBy), vm, onOpen) }
                is ListEntry.AttachmentRow -> Unit // only in search results, which use the list
            }
        }
    }
}

/** A note in the grid: title, its first lines and the time. Tap opens it; long-press selects, as in the list. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(note: NoteEntity, time: Long, vm: NotesViewModel, onOpen: (NoteEntity) -> Unit) {
    val context = LocalContext.current
    val selecting = vm.selected.isNotEmpty()
    val isSelected = note.id in vm.selected
    // The same preview text as the list rows (attachments read as "Drawing", "Voice note (1:52)"...), more of it.
    val body = remember(note) { note.searchText.lineSequence().filter { it.isNotBlank() }.take(8).joinToString("\n") { it.trim() } }
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(if (isSelected) jc.selected else jc.bg)
            .border(if (isSelected) 2.dp else 1.dp, if (isSelected) jc.accent else jc.line, shape)
            .combinedClickable(
                onClick = { if (selecting) vm.toggleSelected(note.id) else onOpen(note) },
                onLongClick = { vm.toggleSelected(note.id) },
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (note.isLocked) JIcon(Ico.LOCK, jc.muted, size = 15.dp, stroke = 2f, modifier = Modifier.padding(top = 3.dp))
            Text(note.displayTitle, Modifier.weight(1f), style = JType.rowTitle.copy(fontSize = 16.sp, lineHeight = 21.sp), color = jc.ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (selecting) CheckCircle(isSelected)
        }
        Text(
            when {
                note.isLocked -> "Locked"
                body.isEmpty() -> "No additional text"
                else -> body
            },
            style = JType.small.copy(fontSize = 14.sp, lineHeight = 19.sp), color = jc.muted, maxLines = 8, overflow = TextOverflow.Ellipsis,
        )
        Text(listTime(context, time), style = JType.small.copy(fontSize = 12.sp), color = jc.muted, maxLines = 1)
    }
}

/** A header with [collapsed] set (Pinned) shows or hides its notes when tapped. */
@Composable
private fun SectionHeader(text: String, collapsed: Boolean? = null, onToggle: () -> Unit = {}) {
    val modifier = if (collapsed == null) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().clickable(onClick = onToggle)
    Row(modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), style = JType.section, color = jc.muted)
        if (collapsed != null) JIcon(if (collapsed) Ico.CHEVRON else "M6 9l6 6 6-6", jc.muted, size = 14.dp, stroke = 2.4f)
    }
}

/** Picks a folder to move notes to. Smart folders aren't offered; the one they're in now is marked. */
@Composable
private fun MoveDialog(
    folders: List<FolderRow>,
    currentFolderId: String,
    subject: String?,
    title: String = "Move note",
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val current = folders.firstOrNull { it.folder.id == currentFolderId }?.folder?.name
    FolderSheet(
        title = title, subject = subject, subjectNote = current?.let { "Currently in $it" },
        rows = folders.filter { !it.folder.isSmart }, currentId = currentFolderId,
        onDismiss = onDismiss, onPick = { id -> id?.let(onPick) },
    )
}

/**
 * Swipe right = pin/unpin. Swipe left shows Move and Delete; a long swipe left deletes. Long-press selects;
 * while anything is selected, a tap adds or removes a note and swipes are off. Recently Deleted has
 * selection but no swipes. [trashMeta] replaces the time and preview there.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteRow(
    note: NoteEntity,
    time: Long,
    isTrash: Boolean,
    previewLines: Int,
    trashMeta: String?,
    vm: NotesViewModel,
    onOpen: (NoteEntity) -> Unit,
    onMove: (NoteEntity) -> Unit,
    query: String = "",
    /** Search result: the folder name and the snippet around the match. */
    search: Pair<String, SearchSnippet>? = null,
) {
    val context = LocalContext.current
    val selecting = vm.selected.isNotEmpty()
    val isSelected = note.id in vm.selected
    val scope = rememberCoroutineScope()
    var offset by remember { mutableFloatStateOf(0f) }
    suspend fun settle(to: Float) = animate(offset, to) { v, _ -> offset = v }
    val reveal = with(LocalDensity.current) { 160.dp.toPx() }
    var width by remember { mutableIntStateOf(1) }
    fun close() { scope.launch { settle(0f) } }
    val content = @Composable {
        Row(
            Modifier.fillMaxWidth()
                .background(if (isSelected) jc.selected else jc.bg)
                .combinedClickable(
                    onClick = { if (offset != 0f) close() else if (selecting) vm.toggleSelected(note.id) else onOpen(note) },
                    onLongClick = { vm.toggleSelected(note.id) },
                )
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (selecting) CheckCircle(isSelected)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (note.isLocked) JIcon(Ico.LOCK, jc.muted, size = 15.dp, stroke = 2f)
                    val title = note.displayTitle
                    val at = if (search == null) -1 else title.indexOf(query, ignoreCase = true)
                    Text(
                        withHit(title, if (at < 0) null else at until at + query.length),
                        Modifier.weight(1f, fill = search != null), style = JType.rowTitle, color = jc.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (search != null) Text(search.first, style = JType.small, color = jc.muted, maxLines = 1)
                }
                if (trashMeta != null) {
                    Text(trashMeta, style = JType.secondary, color = jc.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    return@Column
                }
                if (search != null) {
                    Text(withHit(search.second.text, search.second.hit), style = JType.secondary, color = jc.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    return@Column
                }
                // Preview: 0 lines shows only the time; 2 lines adds the next line of text below.
                val lines = if (note.isLocked) listOf("Locked")
                else note.searchText.lineSequence().filter { it.isNotBlank() }.take(2).toList().ifEmpty { listOf("No additional text") }
                val signedIn by vm.signedIn.collectAsState()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(listTime(context, time), style = JType.secondary, color = jc.ink, maxLines = 1)
                    if (signedIn && note.isDirty) {
                        JIcon(Ico.CLOUD, jc.muted, size = 15.dp, stroke = 2f, modifier = Modifier.semantics { contentDescription = "Waiting to sync" })
                    }
                    if (previewLines > 0) {
                        Text(lines[0], style = JType.secondary, color = jc.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (previewLines > 1 && lines.size > 1) {
                    Text(lines[1], style = JType.secondary, color = jc.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    if (isTrash) {
        content()
    } else {
        val pinLabel = if (note.isPinned) "Unpin" else "Pin"
        Box(
            Modifier.fillMaxWidth().onSizeChanged { width = it.width }.semantics {
                // The swipe actions for screen readers.
                customActions = listOf(
                    CustomAccessibilityAction(pinLabel) { vm.togglePin(note.id); true },
                    CustomAccessibilityAction("Move") { onMove(note); true },
                    CustomAccessibilityAction("Delete") { vm.trash(note.id); true },
                )
            },
        ) {
            @Composable
            fun action(label: String, icon: String, color: Color, modifier: Modifier, onClick: () -> Unit) = Column(
                modifier.fillMaxHeight().background(color).clickable(onClick = onClick),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            ) {
                JIcon(icon, Color.White, size = 20.dp)
                Text(label, style = JType.small, color = Color.White)
            }
            if (offset > 0f) {
                Row(Modifier.matchParentSize().background(jc.ink).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        JIcon(Ico.PIN, jc.bg, size = 20.dp)
                        Text(pinLabel, style = JType.small, color = jc.bg)
                    }
                }
            } else if (offset < 0f) {
                Row(Modifier.matchParentSize(), horizontalArrangement = Arrangement.End) {
                    // A long swipe stretches Delete over the row.
                    val deleteWidth = with(LocalDensity.current) { maxOf(reveal / 2, -offset - reveal / 2).toDp() }
                    action("Move", Ico.MOVE, Color(0xFF8E8E93), Modifier.width(80.dp)) { close(); onMove(note) }
                    action("Delete", Ico.TRASH, jc.danger, Modifier.width(deleteWidth)) { vm.trash(note.id); offset = 0f }
                }
            }
            Box(
                Modifier.offset { IntOffset(offset.roundToInt(), 0) }.draggable(
                    orientation = Orientation.Horizontal,
                    enabled = !selecting,
                    state = rememberDraggableState { d -> offset = (offset + d).coerceIn(-width.toFloat(), width * 0.5f) },
                    onDragStopped = {
                        val v = offset
                        when {
                            v > width * 0.25f -> { vm.togglePin(note.id); settle(0f) }
                            v < -width * 0.6f -> { vm.trash(note.id); offset = 0f }
                            v < -reveal / 2 -> settle(-reveal)
                            else -> settle(0f)
                        }
                    },
                ),
            ) { content() }
        }
    }
    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = jc.line)
}

// ---------- Editor ----------

@Composable
fun EditorScreen(
    vm: EditorViewModel,
    noteId: String,
    folderId: String,
    folders: List<FolderRow>,
    fingerprint: String?,
    backLabel: String,
    onBack: () -> Unit,
    onOpenTag: (String) -> Unit,
    onOpenNote: (NoteEntity) -> Unit,
    /** New note in this note's folder (the id of that folder). */
    onNewNote: (String) -> Unit,
) {
    LaunchedEffect(noteId) { vm.open(noteId, folderId) }
    var moving by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var formatting by remember { mutableStateOf(false) }
    var attaching by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    var linked by remember { mutableStateOf(false) }
    /** Find in note: null = closed. [findAt] is the current match. */
    var find by remember { mutableStateOf<String?>(null) }
    var findAt by remember { mutableIntStateOf(0) }
    val matches = remember(vm.doc.text, find) {
        val q = find.orEmpty()
        if (q.isBlank()) emptyList() else Regex(Regex.escape(q), RegexOption.IGNORE_CASE).findAll(vm.doc.text).map { it.range }.toList()
    }
    androidx.activity.compose.BackHandler(enabled = find != null) { find = null }
    val scroll = rememberScrollState()
    var fieldTop by remember { mutableIntStateOf(0) }
    val targets by vm.linkTargets.collectAsState()
    /** Asking for the notes password before locking: CREATE (none exists yet) or ENTER. */
    var passwordFor by remember { mutableStateOf<PasswordMode?>(null) }
    val scope = rememberCoroutineScope()
    val unlocked by vm.sessionUnlocked.collectAsState()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    // The drawing being edited: its line (null for a new one) and strokes.
    var drawing by remember { mutableStateOf<Pair<Int?, Drawing?>?>(null) }
    var recording by remember { mutableStateOf(false) }
    var addingLink by remember { mutableStateOf(false) }
    /** The linked note being previewed (press and hold a [[link]]); null = none. */
    var peek by remember { mutableStateOf<NoteEntity?>(null) }
    val haptic = LocalHapticFeedback.current
    var sharing by remember { mutableStateOf(false) }
    /** The photo open in the viewer, by its line's id; null = closed. */
    var viewing by remember { mutableStateOf<String?>(null) }
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> vm.photoTaken(ok, ::toast) }
    // OCR: a photo taken just to read its text, and the dialog to check that text before adding it.
    val scanCamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> vm.scanTaken(ok, context.applicationContext) }
    vm.scan?.let { state ->
        ScanDialog(state, onInsert = { text -> vm.insertText(text, (state as? ScanState.Found)?.afterLine) }, onDismiss = vm::dismissScan)
    }
    if (addingLink) WebLinkDialog(onDismiss = { addingLink = false }) { url, title, done ->
        vm.addLink(url, title) { error -> if (error == null) addingLink = false; done(error) }
    }
    viewing?.let { id -> PhotoViewer(vm, id, onClose = { viewing = null }, onError = ::toast) }
    if (recording) {
        RecordingDialog(
            vm.doc.texts.first().trim(),
            onSave = { file, ms -> recording = false; vm.saveRecording(file, ms) { msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() } },
            onDismiss = { recording = false },
        )
    }
    // The notification permission is asked with the microphone; without it recording still works, just with no notification.
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[android.Manifest.permission.RECORD_AUDIO] == true) recording = true
        else Toast.makeText(context, "Margin needs the microphone to record voice notes.", Toast.LENGTH_LONG).show()
    }
    // Reminders: set from More, shown under the date line, rung as a notification.
    val reminderAt by vm.reminderAt.collectAsState()
    var reminding by remember { mutableStateOf(false) }
    /** The to-do target date picker is open. */
    var pickingTarget by remember { mutableStateOf(false) }
    if (pickingTarget) {
        TargetDateDialog(
            vm.targetDate,
            onPick = { pickingTarget = false; vm.changeTargetDate(it) },
            onRemove = { pickingTarget = false; vm.changeTargetDate(null) },
            onDismiss = { pickingTarget = false },
        )
    }
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) toast("Reminder set, but notifications are off for Jotter, so it won't show. Turn them on in the phone's settings.")
    }
    if (reminding) {
        ReminderDialog(
            current = reminderAt, onTime = vm.remindersOnTime,
            onPick = { time ->
                reminding = false
                vm.setReminder(time)
                toast("You'll be reminded " + reminderLabel(context, time).replaceFirstChar { it.lowercaseChar() } + ".")
                if (Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) notifyPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            },
            onRemove = { reminding = false; vm.clearReminder() },
            onDismiss = { reminding = false },
        )
    }
    drawing?.let { (line, d) ->
        val onError = { msg: String -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
        DrawingScreen(
            d, vm.doc.texts.first().trim(),
            onDone = { result -> drawing = null; result?.let { vm.saveDrawing(it, line, onError) } },
            onText = { remaining, text -> drawing = null; vm.drawingToText(remaining, line, text, onError) },
        )
    }
    val folderName = folders.firstOrNull { it.folder.id == vm.folderId }?.folder?.name

    Scaffold(
        topBar = { Column(Modifier.background(jc.bg)) {
            NavBar(backLabel, onBack) {
                if (vm.readOnly) {
                    TextAction("Recover", { vm.recover(onBack) })
                    TextAction("Delete", { vm.deleteForever(onBack) }, color = jc.danger)
                } else if (vm.loaded) {
                    if (vm.canUndo && !vm.needsUnlock) IconAction(Ico.UNDO, "Undo", vm::undo)
                    if (vm.focused) TextAction("Done", { focusManager.clearFocus() }, bold = true)
                    // Reminder: amber while one is set.
                    if (vm.canRemind) IconAction(
                        Ico.BELL, reminderAt?.let { "Reminder, ${reminderLabel(context, it)}. Change it" } ?: "Remind me",
                        { reminding = true }, fill = reminderAt != null,
                    )
                    if (!vm.needsUnlock) IconAction(Ico.SHARE, "Share", { sharing = !sharing; formatting = false; attaching = false })
                    Box {
                        IconAction(Ico.MORE, "More", { menu = true }, fill = true)
                        DropdownMenu(
                            expanded = menu, onDismissRequest = { menu = false },
                            modifier = Modifier.width(262.dp), shape = RoundedCornerShape(14.dp), containerColor = jc.bg,
                            border = BorderStroke(1.dp, jc.line), shadowElevation = 12.dp,
                        ) {
                            val lockTile: (() -> Unit)? = when {
                                !vm.locked -> {
                                    {
                                        if (vm.hasAttachments) Toast.makeText(context, "Notes with attachments can't be locked yet.", Toast.LENGTH_LONG).show()
                                        else if (unlocked) vm.lock()
                                        else scope.launch { passwordFor = if (vm.hasPassword()) PasswordMode.ENTER else PasswordMode.CREATE }
                                    }
                                }
                                !vm.needsUnlock -> vm::removeLock
                                else -> null
                            }
                            // Pin, Lock and Share as tiles across the top, then the rest as rows.
                            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                                listOfNotNull(
                                    if (vm.exists) Triple(if (vm.pinned) "Unpin" else "Pin", Ico.PIN, vm::togglePin) else null,
                                    lockTile?.let { Triple(if (vm.locked) "Remove lock" else "Lock", Ico.LOCK, it) },
                                    if (!vm.needsUnlock) Triple("Share", Ico.SHARE) { sharing = true; formatting = false; attaching = false } else null,
                                ).forEachIndexed { i, (label, icon, onClick) ->
                                    if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(jc.line))
                                    Column(
                                        Modifier.weight(1f).height(68.dp).clickable(role = Role.Button) { menu = false; onClick() },
                                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                                    ) {
                                        JIcon(icon, jc.ink, size = 20.dp, stroke = 1.8f)
                                        Text(label, style = JType.small.copy(fontSize = 12.sp), color = jc.ink)
                                    }
                                }
                            }
                            @Composable
                            fun item(label: String, icon: String, gap: Boolean = false, danger: Boolean = false, onClick: () -> Unit) {
                                Box(Modifier.fillMaxWidth().height(if (gap) 6.dp else 1.dp).background(if (gap) jc.surface else jc.line))
                                Row(
                                    Modifier.fillMaxWidth().height(44.dp).clickable(role = Role.Button) { menu = false; onClick() }.padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    val c = if (danger) jc.danger else jc.ink
                                    Text(label, Modifier.weight(1f), style = JType.row.copy(fontSize = 16.sp), color = c)
                                    JIcon(icon, c, size = 18.dp, stroke = 1.8f)
                                }
                            }
                            var first = true
                            if (!vm.needsUnlock) { item("Find in note", Ico.SEARCH, gap = true) { find = ""; findAt = 0; focusManager.clearFocus() }; first = false }
                            item("Move to folder…", Ico.MOVE, gap = first) { moving = true }
                            if (vm.canRemind) item(if (reminderAt != null) "Change reminder…" else "Remind me…", Ico.BELL) { reminding = true }
                            if (!vm.readOnly && !vm.needsUnlock) item(if (vm.targetDate != null) "Change target date…" else "Set target date…", ICON_TARGET) { pickingTarget = true }
                            if (vm.exists && !vm.needsUnlock) item("Duplicate", Ico.COPY) { vm.duplicate(onOpenNote) }
                            item("Linked notes · ${vm.linkedNotes(targets).size}", Ico.LINK, gap = true) { linked = true }
                            item("Note info", Ico.INFO) { info = true }
                            item("Delete", Ico.TRASH, gap = true, danger = true) { vm.delete(onBack) }
                        }
                    }
                }
            }
            find?.let { q ->
                FindBar(q, matches.size, findAt, onChange = { find = it; findAt = 0 }, onStep = { d ->
                    if (matches.isNotEmpty()) findAt = (findAt + d).mod(matches.size)
                }, onClose = { find = null })
            }
        } },
        bottomBar = {
            if (!vm.readOnly && vm.loaded && !vm.needsUnlock && find == null) {
                val known by vm.otherTags.collectAsState()
                Column(Modifier.background(jc.bg).windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))) {
                    LinkSuggestions(vm.linkSuggestions(targets), vm.linkTyped(), folders, vm::completeLink)
                    TagSuggestions(vm.tagSuggestions(known), vm::completeTag)
                    if (formatting) FormatSheet(vm, onClose = { formatting = false })
                    if (sharing) ShareSheet(
                        onClose = { sharing = false },
                        onText = {
                            sharing = false
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                                .putExtra(Intent.EXTRA_SUBJECT, vm.doc.texts.first().trim())
                                .putExtra(Intent.EXTRA_TEXT, shareText(vm.doc.toBlocks()))
                            context.startActivity(Intent.createChooser(send, null))
                        },
                        onFile = { pdf, print ->
                            sharing = false
                            scope.launch {
                                val file = vm.exportFile(pdf) ?: return@launch toast("Couldn't make the file.")
                                val title = vm.doc.texts.first().trim().ifEmpty { "Note" }
                                if (print) {
                                    val printer = context.getSystemService(android.content.Context.PRINT_SERVICE) as android.print.PrintManager
                                    printer.print(title, PdfFilePrintAdapter(file, file.name), null)
                                } else {
                                    val send = vm.files.sendIntent(file, if (pdf) "application/pdf" else "text/markdown", title)
                                    context.startActivity(Intent.createChooser(send, null))
                                }
                            }
                        },
                    )
                    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                        uri?.let { vm.attach(it) { msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() } }
                    }
                    if (attaching) AttachSheet(
                        onClose = { attaching = false },
                        onFile = { attaching = false; picker.launch(arrayOf("*/*")) },
                        onVoice = {
                            attaching = false
                            val mic = android.Manifest.permission.RECORD_AUDIO
                            val wanted = listOfNotNull(mic, if (Build.VERSION.SDK_INT >= 33) android.Manifest.permission.POST_NOTIFICATIONS else null)
                            if (wanted.all { androidx.core.content.ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED }) recording = true
                            else micPermission.launch(wanted.toTypedArray())
                        },
                        onDraw = { attaching = false; drawing = null to null },
                        onCamera = {
                            attaching = false
                            try { camera.launch(vm.photoTarget()) } catch (_: android.content.ActivityNotFoundException) {
                                vm.photoTaken(false) {}
                                toast("No camera app on this phone.")
                            }
                        },
                        onLink = { attaching = false; addingLink = true },
                        onTable = { attaching = false; vm.insertTable() },
                        onScan = {
                            attaching = false
                            try { scanCamera.launch(vm.scanTarget()) } catch (_: android.content.ActivityNotFoundException) {
                                vm.scanTaken(false, context)
                                toast("No camera app on this phone.")
                            }
                        },
                    )
                    EditorToolbar(
                        vm, formatting, attaching,
                        onFormat = { formatting = !formatting; attaching = false; sharing = false }, onHideKeyboard = { focusManager.clearFocus() },
                        onAttach = { attaching = !attaching; formatting = false; sharing = false }, onDraw = { drawing = null to null },
                        onNew = { formatting = false; attaching = false; onNewNote(vm.folderId) },
                    )
                }
            }
        },
    ) { padding ->
        if (!vm.loaded) return@Scaffold
        // No imePadding here: the bottom bar already sits above the keyboard and Scaffold's padding includes it.
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(scroll).padding(horizontal = 24.dp, vertical = 12.dp)) {
            if (vm.needsUnlock) {
                LockedNote(vm, fingerprint, listOfNotNull(vm.editedAt?.let { "Edited " + fullTime(context, it) }, folderName).joinToString(" · "))
            } else {
                // "Saved" once this visit's edits are written.
                Text(
                    listOfNotNull(vm.editedAt?.let { fullTime(context, it) } ?: "Now", folderName, "Saved".takeIf { vm.canUndo && vm.saved && vm.exists })
                        .joinToString(" · "),
                    Modifier.padding(bottom = 10.dp), style = JType.small, color = jc.muted,
                )
                reminderAt?.takeIf { !vm.readOnly }?.let { at ->
                    Row(
                        Modifier.padding(bottom = 10.dp).clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { reminding = true }
                            .background(jc.selected).padding(horizontal = 8.dp, vertical = 4.dp)
                            .semantics { contentDescription = "Reminder: ${reminderLabel(context, at)}. Tap to change it." },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        JIcon(Ico.BELL, jc.accent, size = 14.dp, stroke = 2f)
                        Text(reminderLabel(context, at), style = JType.small, color = jc.accent)
                    }
                }
                // To-do progress: shown once the note has checklist lines or a target date.
                val todo = remember(vm.doc) { todoCount(vm.doc.toBlocks()) }
                if (todo.total > 0 || vm.targetDate != null) {
                    TodoHeader(
                        todo, startDay = epochDay(vm.createdAt ?: System.currentTimeMillis(), java.time.ZoneId.systemDefault()),
                        target = vm.targetDate, editable = !vm.readOnly, onPickTarget = { pickingTarget = true },
                    )
                }
                vm.conflictWith?.let { original -> ConflictChooser(original.displayTitle, vm, onBack) }
                if (vm.readOnly) {
                    Text("Recover this note to edit it.", Modifier.padding(bottom = 10.dp), style = JType.small, color = jc.muted)
                }
                val density = LocalDensity.current
                NoteField(
                    vm, matches, findAt, Modifier.onGloballyPositioned { fieldTop = it.positionInParent().y.roundToInt() },
                    onReveal = { y -> scope.launch { scroll.animateScrollTo((fieldTop + y - with(density) { 96.dp.toPx() }).roundToInt().coerceAtLeast(0)) } },
                    onOpenDrawing = { line, d -> drawing = line to d },
                    onViewPhoto = { viewing = it },
                    onOpenLink = { title ->
                        val target = resolveLink(title, vm.linkTargets.value)
                        if (target == null) Toast.makeText(context, "Note not found", Toast.LENGTH_SHORT).show() else onOpenNote(target)
                    },
                    onPreviewLink = { title ->
                        val target = resolveLink(title, vm.linkTargets.value)
                        if (target == null) Toast.makeText(context, "Note not found", Toast.LENGTH_SHORT).show()
                        else { haptic.performHapticFeedback(HapticFeedbackType.LongPress); peek = target }
                    },
                )
                // The note's tags; tapping one opens that tag's list.
                val noteTags = remember(vm.doc.text) { findTags(vm.doc.text).map { it.second }.distinctBy(::tagKey) }
                if (noteTags.isNotEmpty() && !vm.focused && !vm.readOnly) { // tag lists leave out deleted notes
                    FlowRow(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        noteTags.forEach { name ->
                            Box(
                                Modifier.height(28.dp).clip(RoundedCornerShape(14.dp)).background(jc.surface)
                                    .clickable(role = Role.Button) { onOpenTag(tagKey(name)) }.padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text("#$name", style = JType.small, color = jc.ink) }
                        }
                    }
                }
            }
        }
    }
    if (info) NoteInfoDialog(vm, folderName) { info = false }
    peek?.let { n -> LinkedNotePreview(n, onOpen = { peek = null; onOpenNote(n) }, onDismiss = { peek = null }) }
    if (linked) {
        val notes = vm.linkedNotes(targets)
        AlertDialog(
            onDismissRequest = { linked = false },
            title = { Text("Linked notes") },
            text = {
                if (notes.isEmpty()) {
                    Text("No note links here yet. Type [[ and this note's title in another note to link it.", style = JType.secondary, color = jc.muted)
                } else Column {
                    notes.forEach { n ->
                        Text(
                            n.displayTitle, Modifier.fillMaxWidth().clickable { linked = false; onOpenNote(n) }.padding(vertical = 12.dp),
                            style = JType.row, color = jc.accent, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { linked = false }) { Text("Close") } },
        )
    }
    if (moving) {
        MoveDialog(
            folders, currentFolderId = vm.folderId, subject = vm.doc.texts.first().ifBlank { "New Note" },
            onDismiss = { moving = false },
        ) { vm.moveTo(it); moving = false }
    }
    passwordFor?.let { mode ->
        var old by remember { mutableStateOf(false) }
        val submit: suspend (CharArray) -> String? = { pw ->
            if (vm.enterPassword(pw)) { passwordFor = null; vm.lock(); null } else if (old) "Wrong password" else "Wrong passcode"
        }
        if (old) OldPasswordDialog(onDismiss = { passwordFor = null }, onSubmit = submit)
        else PasscodeDialog(mode, onDismiss = { passwordFor = null }, onOldPassword = if (mode == PasswordMode.ENTER) ({ old = true }) else null, onSubmit = submit)
    }
}

/** Find in note: the query, "2 of 5", previous/next and Done. */
@Composable
private fun FindBar(query: String, count: Int, at: Int, onChange: (String) -> Unit, onStep: (Int) -> Unit, onClose: () -> Unit) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { requester.requestFocus() } }
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SearchField(query, onChange, "Find in note", Modifier.weight(1f).focusRequester(requester))
        Text(if (query.isBlank()) "" else if (count == 0) "None" else "${at + 1} of $count", Modifier.padding(horizontal = 6.dp), style = JType.small, color = jc.muted)
        IconAction("M6 15l6-6 6 6", "Previous match", { onStep(-1) }, enabled = count > 1)
        IconAction("M6 9l6 6 6-6", "Next match", { onStep(1) }, enabled = count > 1)
        TextAction("Done", onClose, bold = true)
    }
}

/** Note info: dates, folder and size. */
@Composable
private fun NoteInfoDialog(vm: EditorViewModel, folderName: String?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val blocks = vm.doc.toBlocks()
    val body = blocks.drop(1).filter { it.attachment == null }.joinToString("\n") { it.plainText }
    val words = body.split(Regex("\\s+")).count { it.isNotBlank() }
    val attachments = blocks.count { it.attachment != null }
    val rows = listOfNotNull(
        "Created" to (vm.createdAt?.let { fullTime(context, it) } ?: "Not saved yet"),
        "Edited" to (vm.editedAt?.let { fullTime(context, it) } ?: "Now"),
        folderName?.let { "Folder" to it },
        "Words" to "$words",
        "Characters" to "${body.count { !it.isWhitespace() }}",
        ("Attachments" to "$attachments").takeIf { attachments > 0 },
        ("Locked" to "Yes").takeIf { vm.locked },
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Note info") },
        text = {
            Column {
                rows.forEach { (k, v) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(k, Modifier.weight(1f), style = JType.row, color = jc.muted)
                        Text(v, style = JType.row, color = jc.ink)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun passwordColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = jc.accent, unfocusedBorderColor = jc.line, focusedLabelColor = jc.accent, cursorColor = jc.accent,
)

/** The title of a locked note, plus the way in: the passcode (or an old password), or a fingerprint if that's set up. */
@Composable
private fun LockedNote(vm: EditorViewModel, fingerprint: String?, meta: String) {
    var password by remember { mutableStateOf("") }
    var old by remember { mutableStateOf(false) } // notes locked before passcodes
    val activity = LocalContext.current as? FragmentActivity
    val canBio = fingerprint != null && activity != null
    var pad by remember { mutableStateOf(!canBio) } // with fingerprint unlock, the keypad is the fallback
    val scope = rememberCoroutineScope()
    val title = vm.doc.texts.first()
    val viewNote: () -> Unit = {
        scope.launch {
            val (iv, data) = fingerprint!!.split('.').map { java.util.Base64.getUrlDecoder().decode(it) }
            when (val r = Biometric.decrypt(activity!!, data, iv)) {
                is Biometric.Result.Ok -> vm.unlock(String(r.bytes, Charsets.UTF_8).toCharArray().also { r.bytes.fill(0) })
                Biometric.Result.Cancelled -> {}
                Biometric.Result.Invalidated -> vm.unlockError = "Fingerprints changed. Use your passcode, then turn fingerprint unlock on again in Settings."
                is Biometric.Result.Failed -> vm.unlockError = r.message
            }
        }
    }
    Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(jc.surface), contentAlignment = Alignment.Center) {
            JIcon(Ico.LOCK, jc.ink, size = 32.dp, stroke = 1.6f)
        }
        Text(title.ifBlank { "New Note" }, Modifier.padding(top = 24.dp, bottom = 8.dp), style = JType.heading.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold), color = jc.ink, textAlign = TextAlign.Center)
        Text("This note is locked.", Modifier.padding(bottom = 4.dp), style = JType.secondary.copy(fontSize = 16.sp, lineHeight = 23.sp), color = jc.muted)
        if (meta.isNotEmpty()) Text(meta, style = JType.small, color = jc.muted, textAlign = TextAlign.Center)
        when {
            !pad -> {
                PrimaryButton("View note", viewNote, Modifier.fillMaxWidth().padding(top = 32.dp), enabled = !vm.unlocking)
                vm.unlockError?.let { Text(it, Modifier.padding(top = 8.dp), style = JType.small, color = jc.danger, textAlign = TextAlign.Center) }
                Box(Modifier.padding(top = 8.dp)) { TextAction("Enter passcode instead", { pad = true; vm.unlockError = null }) }
            }
            old -> {
                OutlinedTextField(
                    password, { password = it; vm.unlockError = null }, singleLine = true, label = { Text("Old notes password") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = vm.unlockError != null,
                    supportingText = vm.unlockError?.let { { Text(it) } },
                    shape = RoundedCornerShape(12.dp), colors = passwordColors(),
                    modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
                )
                PrimaryButton(
                    if (vm.unlocking) "Unlocking…" else "Unlock",
                    { vm.unlock(password.toCharArray()); password = "" },
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    enabled = password.isNotEmpty() && !vm.unlocking,
                )
            }
            else -> PasscodePad(vm.unlockError, enabled = !vm.unlocking, onComplete = { vm.unlock(it.toCharArray()) }, modifier = Modifier.padding(top = 28.dp))
        }
        if (pad) {
            Box(Modifier.padding(top = 8.dp)) {
                TextAction(if (old) "Use passcode" else "Use old password", { old = !old; vm.unlockError = null })
            }
            if (canBio) Box { TextAction("Use fingerprint", { pad = false; old = false; vm.unlockError = null }) }
        }
        Text(
            "Locked notes are encrypted, on this device and in your Google Drive backup. Only the title is visible in your list and in search.",
            Modifier.padding(top = 32.dp), style = JType.small.copy(lineHeight = 19.sp), color = jc.muted, textAlign = TextAlign.Center,
        )
    }
}

enum class PasswordMode { CREATE, ENTER }

/**
 * Asks for a notes password from before passcodes (notes locked with it still open with it). [onSubmit]
 * returns an error to show, or null when done.
 */
@Composable
private fun OldPasswordDialog(onDismiss: () -> Unit, onSubmit: suspend (CharArray) -> String?) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter your old notes password") },
        text = {
            OutlinedTextField(
                password, { password = it; error = null }, singleLine = true, label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                isError = error != null, supportingText = error?.let { { Text(it) } },
                shape = RoundedCornerShape(12.dp), colors = passwordColors(),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    busy = true
                    error = onSubmit(password.toCharArray())
                    busy = false
                }
            }, enabled = password.isNotEmpty() && !busy) { Text(if (busy) "Checking…" else "OK", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Known tags that finish the one being typed; tapping one completes it. */
@Composable
private fun TagSuggestions(tags: List<TagCount>, onPick: (String) -> Unit) {
    if (tags.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tags.forEach { tag -> Chip("#" + tag.name, onClick = { onPick(tag.name) }) }
    }
}

/** Notes to finish a "[[" link with, as a card: title (typed part tinted), then folder and date. */
@Composable
private fun LinkSuggestions(notes: List<NoteEntity>, typed: String, folders: List<FolderRow>, onPick: (String) -> Unit) {
    if (notes.isEmpty()) return
    val context = LocalContext.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).clip(RoundedCornerShape(14.dp))
            .border(1.dp, jc.line, RoundedCornerShape(14.dp)).background(jc.bg),
    ) {
        Text("Link to note", Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 6.dp), style = JType.section.copy(fontSize = 12.sp), color = jc.muted)
        notes.take(3).forEachIndexed { i, note ->
            if (i > 0) HorizontalDivider(color = jc.line)
            val q = typed.trim()
            val at = if (q.isEmpty()) -1 else note.title.indexOf(q, ignoreCase = true)
            val title = androidx.compose.ui.text.buildAnnotatedString {
                append(note.title)
                if (at >= 0) addStyle(SpanStyle(color = jc.accent), at, at + q.length)
            }
            val folder = folders.firstOrNull { it.folder.id == note.folderId }?.folder?.name
            Column(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).background(if (i == 0) jc.surface else jc.bg)
                    .clickable(role = Role.Button) { onPick(note.title) }.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
            ) {
                Text(title, style = JType.row.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold), color = jc.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(folder, listTime(context, note.lastEdited)).joinToString(" · "), style = JType.small, color = jc.muted, maxLines = 1)
            }
        }
    }
}

/** "Aa" opens the format sheet; the checklist button turns the current line(s) into checklist items. */
@Composable
private fun EditorToolbar(
    vm: EditorViewModel, formatting: Boolean, attaching: Boolean, onFormat: () -> Unit, onHideKeyboard: () -> Unit, onAttach: () -> Unit, onDraw: () -> Unit,
    onNew: () -> Unit,
) {
    val focusedType = if (vm.focused) vm.doc.lines.getOrNull(vm.cursorLine)?.type else null
    val canFormat = focusedType != null && focusedType != BlockType.TITLE
    HorizontalDivider(color = jc.line)
    // Design: accent icons spread across the bar.
    Row(
        Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        GlyphAction("Aa", "Text style", onFormat, JType.rowTitle.copy(fontSize = 19.sp), color = if (formatting) jc.ink else jc.accent)
        IconAction(Ico.CHECKLIST, "Checklist", { vm.setType(BlockType.CHECK) }, color = if (focusedType == BlockType.CHECK) jc.ink else jc.accent, enabled = canFormat)
        if (vm.linksOn) IconAction(Ico.LINK, "Link to a note", vm::startLink, enabled = canFormat)
        // Locked notes can't take attachments: the files wouldn't be encrypted.
        IconAction(Ico.ATTACH, "Attach", onAttach, color = if (attaching) jc.ink else jc.accent, enabled = !vm.locked)
        IconAction(Ico.PEN, "Draw", onDraw, enabled = !vm.locked)
        if (vm.focused) IconAction(Ico.HIDE_KEYBOARD, "Hide keyboard", onHideKeyboard)
        IconAction(Ico.COMPOSE, "New note", onNew)
    }
}

/** The design's bottom sheet look, shared by Format and Add to note: grab handle, bold title, round close button. */
@Composable
private fun SheetPanel(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).background(jc.bg)
            .border(1.dp, jc.line, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).size(36.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(jc.switchOff))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = JType.subheading.copy(fontWeight = FontWeight.Bold), color = jc.ink)
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(jc.surface).clickable(role = Role.Button, onClick = onClose)
                    .semantics { contentDescription = "Close" },
                contentAlignment = Alignment.Center,
            ) { JIcon(Ico.CLOSE, jc.muted, size = 14.dp, stroke = 2.6f) }
        }
        content()
    }
}

/** "Add to note": the attachment kinds Jotter has, as tiles. */
@Composable
private fun AttachSheet(
    onClose: () -> Unit, onFile: () -> Unit, onVoice: () -> Unit, onDraw: () -> Unit, onCamera: () -> Unit, onLink: () -> Unit,
    onScan: () -> Unit, onTable: () -> Unit,
) {
    val tiles = listOf(
        Triple("Camera", Ico.CAMERA, onCamera), Triple("Photo or file", Ico.FILE, onFile), Triple("Voice note", Ico.MIC, onVoice),
        Triple("Drawing", Ico.PEN, onDraw), Triple("Web link", Ico.GLOBE, onLink), Triple("Scan text", Ico.SCAN, onScan),
        Triple("Table", ICON_TABLE, onTable),
    )
    SheetPanel("Add to note", onClose) {
        tiles.chunked(3).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { (label, icon, onClick) ->
                Column(
                    Modifier.weight(1f).height(88.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, jc.line, RoundedCornerShape(14.dp))
                        .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                ) {
                    JIcon(icon, jc.ink)
                    Text(label, style = JType.small.copy(fontWeight = FontWeight.Medium), color = jc.ink, maxLines = 1)
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        } }
    }
}

/**
 * On a sync conflict copy: both devices changed "[title]" before syncing, and this is the other version.
 * Keep this one (it replaces the original), keep the other (this copy is deleted) or keep both.
 */
@Composable
private fun ConflictChooser(title: String, vm: EditorViewModel, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, jc.line, RoundedCornerShape(14.dp)).padding(16.dp),
    ) {
        Text("Two versions", style = JType.rowTitle.copy(fontSize = 17.sp), color = jc.ink)
        Text(
            "“$title” was edited on two devices while one was offline. Nothing has been lost — this is the other version. Pick what to keep.",
            Modifier.padding(top = 6.dp, bottom = 14.dp), style = JType.secondary.copy(fontSize = 15.sp, lineHeight = 21.sp), color = jc.muted,
        )
        PrimaryButton("Keep this version", { vm.keepThisVersion(onBack) }, Modifier.fillMaxWidth())
        Box(
            Modifier.padding(top = 8.dp).fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, jc.line, RoundedCornerShape(14.dp))
                .clickable(role = Role.Button, onClick = vm::keepBothVersions),
            contentAlignment = Alignment.Center,
        ) { Text("Keep both as separate notes", style = JType.row, color = jc.ink) }
        Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp)) { TextAction("Keep the other version", { vm.keepOtherVersion(onBack) }) }
        Text("The version you don't keep goes to Recently Deleted.", Modifier.align(Alignment.CenterHorizontally), style = JType.small, color = jc.muted, textAlign = TextAlign.Center)
    }
}

/** Share note: send a copy as PDF, Markdown or plain text, or print it. */
@Composable
private fun ShareSheet(onClose: () -> Unit, onText: () -> Unit, onFile: (pdf: Boolean, print: Boolean) -> Unit) {
    SheetPanel("Share note", onClose) {
        Text("Send a copy", style = JType.section, color = jc.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(
                Triple("PDF", Ico.FILE) { onFile(true, false) },
                Triple("Markdown", Ico.MARKDOWN) { onFile(false, false) },
                Triple("Plain text", Ico.TEXT, onText),
                Triple("Print", Ico.PRINT) { onFile(true, true) },
            ).forEach { (label, icon, onClick) ->
                Column(
                    Modifier.weight(1f).height(80.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, jc.line, RoundedCornerShape(14.dp))
                        .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                ) {
                    JIcon(icon, jc.ink)
                    Text(label, style = JType.small.copy(fontWeight = FontWeight.Medium), color = jc.ink, maxLines = 1)
                }
            }
        }
    }
}

/** Web link: the address, and a title that's read from the page when left empty. [onAdd] reports an error or null. */
@Composable
private fun WebLinkDialog(onDismiss: () -> Unit, onAdd: (String, String, (String?) -> Unit) -> Unit) {
    var url by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add web link") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    url, { url = it; error = null }, label = { Text("Address") }, singleLine = true, isError = error != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                OutlinedTextField(title, { title = it }, label = { Text("Title (optional)") }, singleLine = true)
                Text(error ?: if (busy) "Getting the page title…" else "Leave the title empty to use the page's own.", style = JType.small, color = if (error != null) jc.danger else jc.muted)
            }
        },
        confirmButton = {
            TextButton(onClick = { busy = true; onAdd(url, title) { e -> busy = false; error = e } }, enabled = url.isNotBlank() && !busy) {
                Text("Add", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * The note's photos full screen: swipe between them, rotate or remove one. Removing and rotating are
 * normal edits, so Undo brings the photo back.
 */
@Composable
private fun PhotoViewer(vm: EditorViewModel, startId: String, onClose: () -> Unit, onError: (String) -> Unit) {
    val photos = vm.doc.lines.withIndex().filter { it.value.attachment?.isImage == true }
    if (photos.isEmpty()) { LaunchedEffect(Unit) { onClose() }; return }
    val pager = rememberPagerState(photos.indexOfFirst { it.value.id == startId }.coerceAtLeast(0)) { photos.size }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(pager, Modifier.fillMaxSize(), key = { photos.getOrNull(it)?.value?.id ?: it }) { page ->
                val a = photos.getOrNull(page)?.value?.attachment ?: return@HorizontalPager
                val file = vm.files.file(a)
                val image by produceState<ImageBitmap?>(null, file) {
                    value = withContext(Dispatchers.IO) { runCatching { decodePreview(file, 2048) }.getOrNull() }
                }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    image?.let { Image(it, a.name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                        ?: Text(if (file.exists()) "" else "Not downloaded yet.", color = Color.White, style = JType.secondary)
                }
            }
            val current = photos.getOrNull(pager.currentPage.coerceAtMost(photos.lastIndex))
            Row(
                Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.5f)).statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconAction(Ico.CLOSE, "Close", onClose, color = Color.White)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(current?.value?.attachment?.name.orEmpty(), style = JType.rowTitle, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (photos.size > 1) Text("${pager.currentPage + 1} of ${photos.size}", style = JType.small, color = Color.White.copy(alpha = 0.7f))
                }
                Spacer(Modifier.size(44.dp))
            }
            if (!vm.readOnly && current != null) Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.5f)).navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
            ) {
                IconAction(Ico.ROTATE, "Rotate", { vm.rotateImage(current.index, onError) }, color = Color.White)
                // OCR: closes the viewer and shows the text found, to add below the photo.
                val context = LocalContext.current
                IconAction(Ico.SCAN, "Get text from photo", { onClose(); vm.scanPhoto(current.index, context.applicationContext) }, color = Color.White)
                IconAction(Ico.TRASH, "Remove", { vm.removeAttachment(current.index) }, color = Color.White)
            }
        }
    }
}

/**
 * Line styles and lists for the line(s) at the cursor, in a panel above the toolbar. It's part of the
 * screen rather than a separate window, so the note keeps focus (and the keyboard stays up) while it's open.
 */
@Composable
private fun FormatSheet(vm: EditorViewModel, onClose: () -> Unit) {
    val focusedType = if (vm.focused) vm.doc.lines.getOrNull(vm.cursorLine)?.type else null
    val enabled = focusedType != null && focusedType != BlockType.TITLE
    SheetPanel("Format", onClose) {
        if (!enabled) {
            Text(
                if (focusedType == BlockType.TITLE) "The first line is always the title. Move to another line to format it."
                else "Tap a line in the note to format it.",
                style = JType.small, color = jc.muted,
            )
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple(BlockType.HEADING, "Heading", JType.heading.copy(fontSize = 19.sp)),
                Triple(BlockType.SUBHEADING, "Subheading", JType.rowTitle),
                Triple(BlockType.BODY, "Body", JType.secondary),
                Triple(BlockType.MONOSPACED, "Mono", JType.secondary.copy(fontFamily = FontFamily.Monospace, fontSize = 14.sp)),
            ).forEach { (type, label, style) ->
                val on = focusedType == type
                Box(
                    Modifier.height(44.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (on) jc.selected else jc.bg)
                        .border(if (on) 1.5.dp else 1.dp, if (on) jc.accent else jc.line, RoundedCornerShape(10.dp))
                        .clickable(enabled = enabled, role = Role.Button) { vm.setType(type) }.padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(label, style = style, color = if (enabled) jc.ink else jc.muted) }
            }
        }
        // Character styles work on selected text, the title included.
        val hasSelection = vm.focused && !vm.value.selection.collapsed
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(jc.line),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            listOf(
                Triple(Mark.BOLD, "B", SpanStyle(fontWeight = FontWeight.Bold)),
                Triple(Mark.ITALIC, "I", SpanStyle(fontStyle = FontStyle.Italic)),
                Triple(Mark.UNDERLINE, "U", SpanStyle(textDecoration = TextDecoration.Underline)),
                Triple(Mark.HIGHLIGHT, "H", SpanStyle(background = vm.highlightTint.swatch())),
            ).forEach { (mark, glyph, look) ->
                val on = hasSelection && vm.hasMark(mark)
                val label = mark.name.lowercase().replaceFirstChar { it.uppercase() }
                Box(
                    Modifier.weight(1f).height(48.dp).background(if (on) jc.ink else jc.surface)
                        .clickable(enabled = hasSelection, role = Role.Button) { vm.toggleMark(mark) }
                        .semantics { contentDescription = label; selected = on },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        androidx.compose.ui.text.buildAnnotatedString { withStyle(look) { append(glyph) } },
                        style = JType.rowTitle, color = if (on) jc.bg else if (hasSelection) jc.ink else jc.muted,
                    )
                }
            }
        }
        if (!hasSelection) Text("Select text to make it bold, italic, underlined or highlighted.", style = JType.small, color = jc.muted)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(jc.line),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            listOf(
                Triple(BlockType.BULLET, Ico.BULLETS, "Bulleted list"),
                Triple(BlockType.DASHED, Ico.DASHES, "Dashed list"),
                Triple(BlockType.NUMBERED, Ico.NUMBERS, "Numbered list"),
                Triple(BlockType.CHECK, Ico.CHECKLIST, "Checklist"),
            ).forEach { (type, icon, label) ->
                val on = focusedType == type
                Box(
                    Modifier.weight(1f).height(48.dp).background(if (on) jc.ink else jc.surface)
                        .clickable(enabled = enabled, role = Role.Button) { vm.setType(type) }
                        .semantics { contentDescription = label },
                    contentAlignment = Alignment.Center,
                ) { JIcon(icon, if (on) jc.bg else if (enabled) jc.ink else jc.muted, size = 20.dp) }
            }
        }
        val line = if (vm.focused) vm.doc.lines.getOrNull(vm.cursorLine) else null
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(jc.line),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            listOf(
                Triple(Ico.QUOTE, "Quote", vm::toggleQuote),
                Triple(Ico.OUTDENT, "Indent less") { vm.indent(-1) },
                Triple(Ico.INDENT, "Indent more") { vm.indent(1) },
            ).forEachIndexed { i, (icon, label, onClick) ->
                val on = i == 0 && line?.quote == true
                val can = enabled && when (i) { 1 -> (line?.indent ?: 0) > 0; 2 -> (line?.indent ?: 0) < MAX_INDENT; else -> true }
                Box(
                    Modifier.weight(1f).height(48.dp).background(if (on) jc.ink else jc.surface)
                        .clickable(enabled = can, role = Role.Button, onClick = onClick)
                        .semantics { contentDescription = label; if (i == 0) selected = on },
                    contentAlignment = Alignment.Center,
                ) { JIcon(icon, if (on) jc.bg else if (can) jc.ink else jc.muted, size = 20.dp) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Highlight colour", Modifier.weight(1f), style = JType.secondary, color = jc.muted)
            Tint.entries.forEach { t ->
                val on = t == vm.highlightTint
                val name = t.name.lowercase().replaceFirstChar { it.uppercase() }
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(t.swatch())
                        .border(if (on) 2.dp else 0.dp, if (on) jc.ink else Color.Transparent, CircleShape)
                        .clickable(role = Role.RadioButton) { vm.pickTint(t) }
                        .semantics { contentDescription = name; selected = on },
                )
            }
        }
    }
}

/** A highlight colour, see-through so text stays readable in light and dark mode. */
fun Tint.swatch(): Color = when (this) {
    Tint.AMBER -> Color(0xFFE8A33D)
    Tint.BLUE -> Color(0xFF3B82F6)
    Tint.GREEN -> Color(0xFF34A853)
    Tint.GREY -> Color(0xFF8E8E93)
}.copy(alpha = 0.35f)

private val GUTTER = 30.sp
private const val INDENT_STEP = 24f // sp per indent step
private const val QUOTE_GAP = 16f // sp between a quote's bar and its text

/** Space in sp before a line's marker or text: its indent, then the quote bar. */
private fun leadOf(meta: LineMeta) = INDENT_STEP * meta.indent + if (meta.quote) QUOTE_GAP else 0f

@Composable
private fun lineStyle(type: BlockType): TextStyle = when (type) {
    BlockType.TITLE -> JType.noteTitle
    BlockType.HEADING -> JType.heading
    BlockType.SUBHEADING -> JType.subheading
    BlockType.MONOSPACED -> TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontFamily = FontFamily.Monospace)
    else -> JType.body
}.copy(color = jc.ink)

/**
 * The whole note in one text field, so Enter, Backspace and fast typing never move focus between
 * fields. Each line gets its style through a visual transformation (same text, so offsets map 1:1);
 * list markers and checkboxes are drawn in the left gutter, aligned to the laid-out lines.
 */
@Composable
private fun NoteField(
    vm: EditorViewModel,
    matches: List<IntRange>,
    matchAt: Int,
    modifier: Modifier,
    onReveal: (Float) -> Unit,
    onOpenDrawing: (Int, Drawing) -> Unit,
    onViewPhoto: (String) -> Unit,
    onOpenLink: (String) -> Unit,
    onPreviewLink: (String) -> Unit,
) {
    val requester = remember { FocusRequester() }
    // Lives next to the field so the field is attached when it runs: on a new note, and again after
    // rotation, when the field is recreated but the ViewModel remembers it had focus.
    LaunchedEffect(Unit) {
        if (vm.focusOnOpen || vm.focused) runCatching { requester.requestFocus() }
        vm.focusOnOpen = false
    }
    val doc = vm.doc
    val styles = BlockType.entries.associateWith { lineStyle(it) }
    val muted = jc.muted
    val monoBackground = jc.surface
    val quoteBar = jc.muted.copy(alpha = 0.5f)
    val tagColor = jc.accent
    val targets by vm.linkTargets.collectAsState()
    val targetKeys = remember(targets) { targets.map { linkKey(it.title) }.toSet() }
    val links = remember(doc) { findLinks(doc.text) }
    // Phone numbers: a tap offers call, message, WhatsApp, copy, or editing the number.
    val phones = remember(doc) { findPhones(doc.text) }
    var phoneAt by remember { mutableStateOf<Pair<String, Int>?>(null) }
    phoneAt?.let { (number, offset) ->
        PhoneActions(
            number,
            onEdit = if (vm.readOnly) null else ({
                phoneAt = null
                vm.onValueChange(vm.value.copy(selection = TextRange(offset.coerceIn(0, vm.value.text.length))))
                runCatching { requester.requestFocus() }
            }),
            onDismiss = { phoneAt = null },
        )
    }
    // The table editor, for a table just added or tapped.
    vm.editingTable?.let { id ->
        val line = vm.doc.lines.indexOfFirst { it.id == id }
        val table = vm.doc.lines.getOrNull(line)?.table
        if (table == null) LaunchedEffect(id) { vm.editingTable = null }
        else androidx.compose.runtime.key(id) {
            TableEditor(
                table,
                onDone = { vm.updateTable(id, it); vm.editingTable = null },
                onDelete = { vm.removeAttachment(line); vm.editingTable = null },
            )
        }
    }
    val context = LocalContext.current
    val px = LocalDensity.current
    // An attachment's line is as tall as its card, which is drawn over it.
    val imageLine = with(px) { IMAGE_CARD.toSp() }
    val fileLine = with(px) { FILE_CARD.toSp() }
    // Drawings sit in the text like handwriting, so each one's line is as tall as its ink at the page width.
    // A plain copy: the remembered text below must re-run when a drawing finishes loading.
    val drawings = rememberDrawings(doc, vm.files).toMap()
    var pageWidth by remember { mutableIntStateOf(0) }
    val drawingGap = with(px) { (DRAWING_GAP * 2).toPx() }
    // Photos fill the page width at their own shape (up to MAX_PHOTO tall), so their lines are sized from that.
    val photos = rememberPhotoSizes(doc, vm.files).toMap()
    val photoGap = with(px) { 12.dp.toPx() }
    fun photoLine(a: Attachment) = photos[a.file]?.takeIf { pageWidth > 0 }?.let { (w, h) ->
        with(px) { (minOf(pageWidth * h / w, MAX_PHOTO.toPx()) + photoGap).toSp() }
    } ?: imageLine
    fun drawingLine(a: Attachment) = drawings[a.file]?.takeIf { pageWidth > 0 }?.let { d ->
        with(px) { (inlineHeight(d, pageWidth.toFloat()) + drawingGap).coerceIn(MIN_DRAWING.toPx(), MAX_DRAWING.toPx()).toSp() }
    } ?: imageLine
    // A line that is just a link to a note ("[[Plan]]") shows a card with the note's first lines under it.
    val linkPreviews = remember(doc, targets) { linkPreviewLines(doc, targets) }
    fun previewExtra(rows: Int) = with(px) { linkCardHeight(rows).toSp() }
    val findColor = jc.accent
    val annotated = remember(doc, styles, muted, tagColor, targetKeys, imageLine, matches, matchAt, drawings, pageWidth, linkPreviews, photos) {
        buildAnnotatedString {
            // Each ParagraphStyle range is already its own paragraph, so a real '\n' would add an empty one
            // after every line. Show a zero-width space in its place (same length, so offsets still map 1:1).
            append(doc.text.replace('\n', '​'))
            var start = 0
            doc.texts.forEachIndexed { i, line ->
                val meta = doc.lines[i]
                val style = styles.getValue(meta.type)
                val end = start + line.length
                val paragraphEnd = if (i < doc.lines.lastIndex) end + 1 else end
                val indent = (leadOf(meta) + if (meta.type in MARKER_TYPES) GUTTER.value else 0f).sp
                val preview = linkPreviews[i]
                val height = meta.table?.let { with(px) { tableHeight(it).toSp() } }
                    ?: meta.attachment?.let { if (it.isDrawing) drawingLine(it) else if (it.isImage) photoLine(it) else fileLine }
                    ?: preview?.let { (style.lineHeight.value + previewExtra(it.second.size).value).sp } ?: style.lineHeight
                // A one-row paragraph is trimmed to its font height by default; an attachment's line must keep its full height.
                // A link with a preview keeps its text at the top, leaving the space below for the card.
                val heightStyle = when {
                    meta.isObject -> LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
                    preview != null -> LineHeightStyle(LineHeightStyle.Alignment.Top, LineHeightStyle.Trim.None)
                    else -> null
                }
                addStyle(ParagraphStyle(lineHeight = height, textIndent = TextIndent(indent, indent), lineHeightStyle = heightStyle), start, paragraphEnd)
                val checked = meta.type == BlockType.CHECK && meta.checked
                addStyle(
                    style.toSpanStyle().copy(
                        color = if (checked) muted else style.color,
                        textDecoration = if (checked) TextDecoration.LineThrough else null,
                    ),
                    start, end,
                )
                // An empty line has no text to carry its font, so it would be laid out at body size: an empty title
                // came out short, the "Title" hint overlapped the next line and the cursor stuck out above it.
                // Its line break (a zero-width space) takes the font instead.
                if (line.isEmpty() && paragraphEnd > start) {
                    addStyle(SpanStyle(fontSize = style.fontSize, fontWeight = style.fontWeight, fontFamily = style.fontFamily), start, paragraphEnd)
                }
                if (meta.isObject) addStyle(SpanStyle(color = Color.Transparent), start, end) // the card covers it
                start = end + 1
            }
            // Bold, italic, ... from the Format panel.
            // shortcut: underline replaces a checked line's strike-through on that text; combine decorations if it matters.
            doc.marks.forEach { m ->
                val style = when (m.mark) {
                    Mark.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                    Mark.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                    Mark.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
                    Mark.HIGHLIGHT -> SpanStyle(background = m.color.swatch())
                }
                addStyle(style, m.start.coerceIn(0, doc.text.length), m.end.coerceIn(0, doc.text.length))
            }
            // Tags stand out from the text around them (after line styles, so they win).
            findTags(doc.text).forEach { (range, _) ->
                addStyle(SpanStyle(color = tagColor, fontWeight = FontWeight.SemiBold), range.first, range.last + 1)
            }
            // Links: underlined in the accent colour, or greyed and struck through when the note is gone.
            links.forEach { (range, title) ->
                val found = linkKey(title) in targetKeys
                addStyle(
                    SpanStyle(
                        color = if (found) tagColor else muted,
                        textDecoration = if (found) TextDecoration.Underline else TextDecoration.LineThrough,
                    ),
                    range.first, range.last + 1,
                )
            }
            // Phone numbers: underlined in the accent colour, like links.
            phones.forEach { (range, _) ->
                addStyle(SpanStyle(color = tagColor, textDecoration = TextDecoration.Underline), range.first, range.last + 1)
            }
            // Find in note: every match tinted, the current one stronger.
            matches.forEachIndexed { i, r ->
                addStyle(SpanStyle(background = findColor.copy(alpha = if (i == matchAt) 0.45f else 0.18f)), r.first, r.last + 1)
            }
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    /** Where the last finger touched the field, to put a tapped cursor on the row that was tapped (see [placeTap]). */
    var lastTap by remember { mutableStateOf<Offset?>(null) }
    LaunchedEffect(matches, matchAt, layout) {
        val l = layout ?: return@LaunchedEffect
        val r = matches.getOrNull(matchAt) ?: return@LaunchedEffect
        if (r.first < l.layoutInput.text.length) onReveal(l.getBoundingBox(r.first).top)
    }
    val density = LocalDensity.current
    val activity = LocalContext.current as? Activity
    var hadFocus by remember { mutableStateOf(false) }

    // The built-in cursor is as tall as the line height; ours is drawn the height of the text instead.
    // shortcut: it blinks on a fixed beat rather than staying solid while typing.
    val blink by rememberInfiniteTransition(label = "cursor").animateFloat(
        1f, 0f, infiniteRepeatable(keyframes { durationMillis = 1000; 1f at 0; 1f at 499; 0f at 500 }), label = "blink",
    )
    val cursorColor = jc.accent
    val spelling = rememberMisspellings(doc.text, enabled = !vm.readOnly)
    val squiggle = jc.danger
    // Grammar mistakes, underlined in blue. Where both flag the same words, a typo wins, but our grammar
    // check (which explains itself) wins over the keyboard's own grammar flags.
    val grammarFound = rememberGrammarIssues(doc.text, enabled = !vm.readOnly && vm.grammarOn, cloud = vm.cloudGrammarOn)
    val misspelled = spelling.filter { m -> !m.grammar || grammarFound.none { it.start < m.end && m.start < it.end } }
    val grammar = grammarFound.filter { g -> misspelled.none { it.start < g.end && g.start < it.end } }
    val grammarLine = GRAMMAR_BLUE
    // Suggestions for a flagged word the cursor was just put in (by a tap, not by typing).
    var fixing by remember { mutableStateOf<Misspelling?>(null) }
    var grammarFix by remember { mutableStateOf<GrammarIssue?>(null) }
    var graphing by remember { mutableStateOf<List<Plot>?>(null) }
    var lastValue by remember { mutableStateOf(vm.value) }
    LaunchedEffect(vm.value) {
        val v = vm.value
        val tapped = v.text == lastValue.text && v.selection != lastValue.selection && v.selection.collapsed
        fixing = if (tapped) misspelled.firstOrNull { v.selection.start in it.start..it.end }?.takeIf { it.suggestions.isNotEmpty() } else null
        grammarFix = if (tapped && fixing == null) grammar.firstOrNull { v.selection.start in it.start..it.end } else null
        lastValue = v
    }

    Box(modifier.fillMaxWidth().onSizeChanged { pageWidth = it.width }) {
        BasicTextField(
            value = vm.value,
            onValueChange = { v ->
                val tap = lastTap
                lastTap = null
                vm.onValueChange(if (tap != null && v.text == vm.value.text) placeTap(v, tap, layout) else v)
            },
            readOnly = vm.readOnly,
            // A new note's only line is its empty title, with no characters at all to style: size it as a title.
            textStyle = (if (doc.text.isEmpty()) styles.getValue(BlockType.TITLE) else JType.body).copy(color = jc.ink),
            cursorBrush = SolidColor(Color.Transparent),
            visualTransformation = { TransformedText(annotated, OffsetMapping.Identity) },
            onTextLayout = { layout = it },
            modifier = Modifier.fillMaxWidth()
                // A tap on a link opens its note instead of placing the cursor; pressing and holding previews it.
                // A tap on a phone number shows what to do with it.
                // Notes each touch's position (without taking it) so [placeTap] can check where a tap put the cursor.
                .pointerInput(Unit) {
                    awaitEachGesture { lastTap = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial).position }
                }
                .pointerInput(links, phones) {
                    awaitEachGesture {
                        val down = awaitFirstDown(pass = PointerEventPass.Initial)
                        val l = layout ?: return@awaitEachGesture
                        if (l.layoutInput.text.length != doc.text.length) return@awaitEachGesture
                        val offset = l.getOffsetForPosition(down.position)
                        // getOffsetForPosition snaps to the nearest character; make sure the finger is on the link's row.
                        fun hit(range: IntRange) = offset in range.first..range.last &&
                            l.getBoundingBox(offset.coerceAtMost(l.layoutInput.text.length - 1)).let { down.position.y in it.top..it.bottom }
                        val link = links.firstOrNull { hit(it.first) }
                        if (link == null) {
                            val phone = phones.firstOrNull { hit(it.first) } ?: return@awaitEachGesture
                            down.consume()
                            val up = waitForUpOrCancellation(PointerEventPass.Initial) ?: return@awaitEachGesture
                            up.consume()
                            phoneAt = phone.second to offset
                            return@awaitEachGesture
                        }
                        down.consume()
                        var cancelled = false
                        val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            waitForUpOrCancellation(PointerEventPass.Initial).also { if (it == null) cancelled = true }
                        }
                        if (cancelled) return@awaitEachGesture
                        if (up == null) {
                            onPreviewLink(link.second)
                            // Swallow the rest of the press so it doesn't select text underneath.
                            do {
                                val e = awaitPointerEvent(PointerEventPass.Initial)
                                e.changes.forEach { it.consume() }
                            } while (e.changes.any { it.pressed })
                            return@awaitEachGesture
                        }
                        up.consume()
                        onOpenLink(link.second)
                    }
                }
                .focusRequester(requester)
                .onFocusChanged {
                    // Only a real loss clears the flag: not the "unfocused" report a freshly created field sends
                    // first, and not the teardown during rotation. That way focus comes back after rotating.
                    if (it.isFocused) {
                        hadFocus = true
                        vm.focused = true
                    } else if (hadFocus && activity?.isChangingConfigurations != true) {
                        hadFocus = false
                        vm.focused = false
                    }
                }
                .drawWithContent {
                    drawContent()
                    val l = layout ?: return@drawWithContent
                    val flagged = misspelled.map { Triple(it.start, it.end, squiggle) } + grammar.map { Triple(it.start, it.end, grammarLine) }
                    if (l.layoutInput.text.length == doc.text.length) flagged.forEach { (start, end, color) ->
                        // A wavy underline under the flagged words (per row, should they wrap).
                        val wave = 2.dp.toPx()
                        val step = 3.dp.toPx()
                        (start until end).groupBy { l.getLineForOffset(it) }.forEach { (row, offsets) ->
                            val y = l.getLineBaseline(row) + 3.dp.toPx()
                            val x0 = l.getBoundingBox(offsets.first()).left
                            val x1 = l.getBoundingBox(offsets.last()).right
                            val path = Path().apply {
                                moveTo(x0, y)
                                var x = x0
                                var up = true
                                while (x < x1) { x = minOf(x + step, x1); lineTo(x, if (up) y - wave / 2 else y + wave / 2); up = !up }
                            }
                            drawPath(path, color, style = Stroke(1.2.dp.toPx()))
                        }
                    }
                    val sel = vm.value.selection
                    if (!vm.focused || vm.readOnly || !sel.collapsed || l.layoutInput.text.length != doc.text.length) return@drawWithContent
                    val at = sel.start.coerceIn(0, doc.text.length)
                    // The doc line the cursor is on decides the font size (title, heading, body...).
                    var lineStart = 0
                    val i = doc.texts.indexOfFirst { t -> (at <= lineStart + t.length).also { if (!it) lineStart += t.length + 1 } }
                    val fs = styles.getValue(doc.lines[i.coerceAtLeast(0)].type).fontSize.let { if (it.isSp) it else JType.body.fontSize }.toPx()
                    val baseline = l.getLineBaseline(l.getLineForOffset(at))
                    val x = l.getHorizontalPosition(at, true)
                    drawRect(cursorColor.copy(alpha = blink), Offset(x, baseline - fs * 0.92f), Size(2.dp.toPx(), fs * 1.16f))
                }
                .drawBehind {
                    val l = layout ?: return@drawBehind
                    forEachLineBounds(doc, l) { i, top, bottom, _ ->
                        if (doc.lines[i].type == BlockType.MONOSPACED) {
                            drawRect(monoBackground, topLeft = Offset(-8.dp.toPx(), top), size = Size(size.width + 16.dp.toPx(), bottom - top))
                        }
                        if (doc.lines[i].quote) {
                            drawRect(quoteBar, topLeft = Offset((INDENT_STEP * doc.lines[i].indent).sp.toPx(), top), size = Size(3.dp.toPx(), bottom - top))
                        }
                    }
                },
        )
        if (doc.texts.first().isEmpty()) {
            Text("Title", style = styles.getValue(BlockType.TITLE).copy(color = muted))
        }
        val l = layout
        fixing?.takeIf { l != null && it.end <= l.layoutInput.text.length }?.let { m ->
            val box = l!!.getBoundingBox(m.start)
            Popup(
                offset = IntOffset(box.left.toInt(), (box.bottom + with(density) { 4.dp.toPx() }).toInt()),
                onDismissRequest = { fixing = null },
            ) {
                Row(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(jc.bg).border(1.dp, jc.line, RoundedCornerShape(12.dp)).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    m.suggestions.take(3).forEach { word ->
                        TextAction(word, {
                            fixing = null
                            val t = vm.value.text
                            if (t.regionMatches(m.start, m.word, 0, m.word.length)) {
                                vm.onValueChange(TextFieldValue(t.replaceRange(m.start, m.end, word), TextRange(m.start + word.length)))
                            }
                        })
                    }
                }
            }
        }
        // A tapped grammar mistake: why it's flagged, and the fixes to choose from.
        grammarFix?.takeIf { l != null && it.end <= l.layoutInput.text.length }?.let { g ->
            val box = l!!.getBoundingBox(g.start)
            Popup(
                offset = IntOffset(box.left.toInt(), (box.bottom + with(density) { 4.dp.toPx() }).toInt()),
                onDismissRequest = { grammarFix = null },
            ) {
                Column(
                    Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(12.dp)).background(jc.bg)
                        .border(1.dp, jc.line, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(g.message, style = JType.small, color = jc.muted)
                    if (g.replacements.isNotEmpty()) Row(Modifier.offset(x = (-8).dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        g.replacements.take(3).forEach { fix ->
                            TextAction(fix.ifBlank { "Remove" }, {
                                grammarFix = null
                                val t = vm.value.text
                                if (t.regionMatches(g.start, g.original, 0, g.original.length)) {
                                    vm.onValueChange(TextFieldValue(t.replaceRange(g.start, g.end, fix), TextRange(g.start + fix.length)))
                                }
                            }, bold = true)
                        }
                    }
                }
            }
        }
        // Math on the line the cursor ends: a sum ending in "=" gets its answer, an equation its solution, and
        // "y = x^2" a graph. The chip sits just after the cursor; a tap types the answer or opens the graph.
        val sel = vm.value.selection
        val mathOn = fixing == null && grammarFix == null && vm.focused && !vm.readOnly && sel.collapsed
        val math = remember(vm.value.text, sel.start, mathOn) { if (mathOn) mathSuggestionAt(vm.value.text, sel.start) else null }
        if (math != null && l != null && sel.start <= l.layoutInput.text.length) {
            val at = l.getCursorRect(sel.start)
            val (label, spoken) = when (math) {
                is MathSuggestion.Answer -> math.text to "Answer ${math.text}. Tap to add it."
                is MathSuggestion.Solution -> math.text to "${math.text}. Tap to add it."
                is MathSuggestion.Graph -> "Show graph" to "Show graph"
            }
            Popup(offset = IntOffset((at.right + with(density) { 6.dp.toPx() }).toInt(), (at.center.y - with(density) { 22.dp.toPx() }).toInt())) {
                Box(Modifier.clip(RoundedCornerShape(12.dp)).background(jc.accent.copy(alpha = 0.12f)).semantics { contentDescription = spoken }) {
                    TextAction(label, {
                        val t = vm.value.text
                        val c = vm.value.selection.start
                        val insert = when (math) {
                            is MathSuggestion.Answer -> if (t[c - 1] == ' ') math.text else " ${math.text}"
                            is MathSuggestion.Solution -> (if (t[c - 1] == ' ') "" else " ") + "→ ${math.text}"
                            is MathSuggestion.Graph -> { graphing = plotsIn(t); null }
                        }
                        if (insert != null && mathSuggestionAt(t, c) == math) {
                            vm.onValueChange(TextFieldValue(t.replaceRange(c, c, insert), TextRange(c + insert.length)))
                        }
                    }, bold = true)
                }
            }
        }
        graphing?.let { plots ->
            val context = LocalContext.current
            GraphDialog(plots, onDismiss = { graphing = null }, onAdd = if (vm.canAddImage) { image ->
                graphing = null
                vm.addGraph(image, plots.joinToString(", ") { it.label }) { msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
            } else null)
        }
        layout?.let { l ->
            var number = 0
            forEachLineBounds(doc, l) { i, top, bottom, firstBottom ->
                val meta = doc.lines[i]
                number = if (meta.type == BlockType.NUMBERED) number + 1 else 0
                meta.table?.let { t ->
                    TableCard(
                        t,
                        onOpen = { if (!vm.readOnly) vm.editingTable = meta.id },
                        onRemove = if (vm.readOnly || !vm.focused) null else ({ vm.removeAttachment(i) }),
                        modifier = Modifier.offset { IntOffset(0, top.toInt()) }.fillMaxWidth()
                            .height(with(density) { (bottom - top).toDp() }).padding(vertical = TABLE_GAP),
                    )
                    return@forEachLineBounds
                }
                meta.attachment?.let { a ->
                    val d = if (a.isDrawing) drawings[a.file] else null
                    if (d != null) {
                        InlineDrawingLine(
                            d, a,
                            onOpen = { if (!vm.readOnly) onOpenDrawing(i, d) },
                            // The remove button shows only while editing, so a finished note reads like plain ink.
                            onRemove = if (vm.readOnly || !vm.focused) null else ({ vm.removeAttachment(i) }),
                            modifier = Modifier.offset { IntOffset(0, top.toInt()) }.fillMaxWidth()
                                .height(with(density) { (bottom - top).toDp() }).padding(vertical = DRAWING_GAP),
                        )
                        return@forEachLineBounds
                    }
                    AttachmentCard(
                        a, vm.files.file(a),
                        onOpen = { d ->
                            if (d != null) { if (!vm.readOnly) onOpenDrawing(i, d); return@AttachmentCard }
                            if (a.isImage) { onViewPhoto(meta.id); return@AttachmentCard }
                            try { context.startActivity(vm.files.viewIntent(a)) } catch (_: android.content.ActivityNotFoundException) {
                                Toast.makeText(context, "No app on this phone can open this file.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onRemove = if (vm.readOnly) null else ({ vm.removeAttachment(i) }),
                        modifier = Modifier.offset { IntOffset(0, top.toInt()) }.fillMaxWidth()
                            .height(with(density) { (bottom - top).toDp() }).padding(vertical = 6.dp),
                    )
                    return@forEachLineBounds
                }
                linkPreviews[i]?.let { (note, rows) ->
                    val card = linkCardHeight(rows.size)
                    LinkPreviewCard(
                        rows,
                        onOpen = { onOpenLink(note.title) },
                        modifier = Modifier.offset { IntOffset(0, (bottom - card.toPx()).toInt()) }.fillMaxWidth().height(card)
                            .padding(start = with(density) { leadOf(meta).sp.toDp() }, top = LINK_CARD_GAP, bottom = LINK_CARD_GAP),
                    )
                    return@forEachLineBounds
                }
                if (meta.type !in MARKER_TYPES) return@forEachLineBounds
                val style = styles.getValue(meta.type)
                // Markers line up with the first row of a wrapped line.
                val firstRow = firstBottom - top
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .offset { IntOffset(leadOf(meta).sp.roundToPx(), top.toInt()) }
                        .size(with(density) { GUTTER.toDp() }, with(density) { minOf(firstRow, bottom - top).toDp() }),
                ) {
                    when (meta.type) {
                        BlockType.BULLET -> Text("•", style = style)
                        BlockType.DASHED -> Text("–", style = style)
                        BlockType.NUMBERED -> Text("$number.", style = style)
                        // Sized to the line: at a small text size a fixed 22dp circle is taller than the line and they overlap.
                        else -> CheckCircle(
                            meta.checked,
                            size = with(density) { minOf(firstRow * 0.8f, GUTTER.toPx() * 0.75f).toDp() }.coerceAtMost(22.dp),
                            modifier = Modifier.clip(CircleShape).clickable(enabled = !vm.readOnly, role = Role.Checkbox) { vm.toggleChecked(i) }
                                .semantics { contentDescription = if (meta.checked) "Checked" else "Not checked" },
                        )
                    }
                }
            }
        }
    }
}

/** Grammar underlines: blue, as in most writing apps, so they read differently from red spelling ones. */
private val GRAMMAR_BLUE = Color(0xFF3B82F6)

/** A note link's preview card: up to this many lines of the note, each one row. */
private const val LINK_CARD_ROWS = 3
private val LINK_CARD_GAP = 6.dp
private val LINK_CARD_ROW = 20.dp
private val LINK_CARD_PADDING = 10.dp

/** How much taller a link's line gets to fit a card of [rows] rows, including the space around it. */
private fun linkCardHeight(rows: Int) = LINK_CARD_ROW * rows + LINK_CARD_PADDING * 2 + LINK_CARD_GAP * 2

/**
 * Lines that are nothing but a link to an existing note (not the title, not a list item), with the note and the
 * lines its card shows: its first lines of text, or why there are none.
 */
private fun linkPreviewLines(doc: EditorDoc, notes: List<NoteEntity>): Map<Int, Pair<NoteEntity, List<String>>> {
    val out = HashMap<Int, Pair<NoteEntity, List<String>>>()
    doc.texts.forEachIndexed { i, text ->
        val meta = doc.lines[i]
        if (i == 0 || meta.isObject || meta.type in MARKER_TYPES) return@forEachIndexed
        val link = findLinks(text).singleOrNull()?.takeIf { (range, _) -> text.substring(0, range.first).isBlank() && text.substring(range.last + 1).isBlank() }
            ?: return@forEachIndexed
        val note = resolveLink(link.second, notes) ?: return@forEachIndexed
        val rows = when {
            note.isLocked -> listOf("Locked note")
            else -> note.blocks.drop(1).filter { it.attachment == null && it.plainText.isNotBlank() }.flatMap { it.plainText.lines() }.take(LINK_CARD_ROWS).map { it.trim() }
                .ifEmpty { listOf(if (note.blocks.any { it.attachment != null }) "Attachments only" else "No text yet") }
        }
        out[i] = note to rows
    }
    return out
}

/** The first lines of a linked note, under the link. Tap opens the note. */
@Composable
private fun LinkPreviewCard(rows: List<String>, onOpen: () -> Unit, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(jc.surface).clickable(role = Role.Button, onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = LINK_CARD_PADDING),
    ) {
        rows.forEach { row ->
            Text(
                row, Modifier.height(LINK_CARD_ROW), style = JType.secondary.copy(lineHeight = 20.sp), color = jc.muted,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The tallest a photo shows in a note; a taller one is narrower than the page, keeping its shape. */
private val MAX_PHOTO = 520.dp

/**
 * Every photo's upright width and height in [doc], by file name, read from the file's header (not decoded).
 * One not downloaded yet (or unreadable) is missing, and its line keeps the default height.
 */
@Composable
private fun rememberPhotoSizes(doc: EditorDoc, files: com.shuva.jotter.data.AttachmentFiles): Map<String, Pair<Float, Float>> {
    val photos = doc.lines.mapNotNull { it.attachment?.takeIf { a -> a.isImage } }
    val loaded = remember { mutableStateMapOf<String, Pair<Float, Float>>() }
    LaunchedEffect(photos.map { it.file }) {
        photos.filter { it.file !in loaded }.forEach { a ->
            withContext(Dispatchers.IO) { runCatching { photoSize(files.file(a)) }.getOrNull() }?.let { loaded[a.file] = it }
        }
    }
    return loaded
}

/** Width and height as the photo is shown: a camera photo turned by its EXIF orientation has them swapped. */
private fun photoSize(file: java.io.File): Pair<Float, Float>? {
    if (!file.exists()) return null
    val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeFile(file.path, o)
    if (o.outWidth <= 0 || o.outHeight <= 0) return null
    val turned = runCatching {
        android.media.ExifInterface(file.path).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1) in 5..8
    }.getOrDefault(false)
    return if (turned) o.outHeight.toFloat() to o.outWidth.toFloat() else o.outWidth.toFloat() to o.outHeight.toFloat()
}

/** Space above and below a drawing's ink, and the limits on how tall its line can be. */
private val DRAWING_GAP = 4.dp
private val MIN_DRAWING = 40.dp
private val MAX_DRAWING = 1600.dp

/** Every drawing in [doc] read from its file, by file name; one not downloaded yet (or unreadable) is missing. */
@Composable
private fun rememberDrawings(doc: EditorDoc, files: com.shuva.jotter.data.AttachmentFiles): Map<String, Drawing> {
    val names = doc.lines.mapNotNull { it.attachment?.takeIf { a -> a.isDrawing } }
    val loaded = remember { mutableStateMapOf<String, Drawing>() }
    LaunchedEffect(names.map { it.file }) {
        names.filter { it.file !in loaded }.forEach { a ->
            withContext(Dispatchers.IO) { runCatching { JotterJson.decodeFromString<Drawing>(files.file(a).readText()) }.getOrNull() }
                ?.let { loaded[a.file] = it }
        }
    }
    return loaded
}

/** A drawing as part of the text: ink on the page, no card. Tap to edit it. */
@Composable
private fun InlineDrawingLine(d: Drawing, a: Attachment, onOpen: () -> Unit, onRemove: (() -> Unit)?, modifier: Modifier) {
    Box(
        modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onOpen)
            .semantics { contentDescription = "Drawing. Tap to edit it." },
    ) {
        InlineDrawing(d, Modifier.fillMaxSize())
        if (onRemove != null) {
            Box(
                Modifier.align(Alignment.TopEnd).size(28.dp).clip(CircleShape).background(jc.surface)
                    .clickable(role = Role.Button, onClick = onRemove).semantics { contentDescription = "Remove ${a.name}" },
                contentAlignment = Alignment.Center,
            ) { JIcon(Ico.CLOSE, jc.muted, size = 12.dp, stroke = 2.6f) }
        }
    }
}

private val IMAGE_CARD = 162.dp // design: a 150dp card plus 6dp space above and below
private val FILE_CARD = 76.dp

/** A photo (whole, at its own aspect ratio) or a file (icon and name). Tap opens it in another app. */
@Composable
private fun AttachmentCard(a: Attachment, file: java.io.File, onOpen: (Drawing?) -> Unit, onRemove: (() -> Unit)?, modifier: Modifier) {
    val image by produceState<ImageBitmap?>(null, file) {
        value = if (a.isImage) withContext(Dispatchers.IO) { runCatching { decodePreview(file) }.getOrNull() } else null
    }
    val drawing by produceState<Drawing?>(null, file) {
        value = if (a.isDrawing) withContext(Dispatchers.IO) { runCatching { JotterJson.decodeFromString<Drawing>(file.readText()) }.getOrNull() } else null
    }
    if (a.isLink) {
        val context = LocalContext.current
        val url by produceState<String?>(null, file) { value = withContext(Dispatchers.IO) { runCatching { file.readText().trim() }.getOrNull() } }
        // The page's picture and description, fetched once and kept in the cache; the card shows without them until then.
        val preview by produceState<LoadedPreview?>(null, url) {
            val u = url ?: return@produceState
            value = withContext(Dispatchers.IO) { runCatching { LinkPreviews(context.cacheDir).load(u) }.getOrNull() }
        }
        val thumb = remember(preview) { preview?.image?.asImageBitmap() }
        Box(
            modifier.clip(RoundedCornerShape(12.dp)).background(jc.surface).clickable(role = Role.Button) {
                val u = url ?: return@clickable
                try { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(u))) } catch (_: android.content.ActivityNotFoundException) {
                    Toast.makeText(context, "No app on this phone can open web links.", Toast.LENGTH_SHORT).show()
                }
            }.semantics { contentDescription = a.name + ", web link" },
        ) {
            Row(Modifier.fillMaxSize().padding(end = 14.dp, start = if (thumb == null) 14.dp else 0.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (thumb != null) Image(thumb, null, Modifier.fillMaxHeight().aspectRatio(1f), contentScale = ContentScale.Crop)
                else JIcon(Ico.GLOBE, jc.accent, size = 22.dp)
                Column(Modifier.weight(1f).padding(end = 28.dp)) {
                    Text(a.name, style = JType.rowTitle, color = jc.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    preview?.description?.let { Text(it, style = JType.small, color = jc.ink, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    Text(
                        url?.let(::hostOf) ?: if (file.exists()) "" else "Not downloaded yet. It arrives with the next sync.",
                        style = JType.small, color = jc.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (onRemove != null) RemoveButton(a, onRemove, Modifier.align(Alignment.TopEnd))
        }
        return
    }
    if (a.isAudio) {
        Box(modifier.clip(RoundedCornerShape(12.dp)).background(jc.surface)) {
            AudioCard(a, file, Modifier.fillMaxSize())
            if (onRemove != null) RemoveButton(a, onRemove, Modifier.align(Alignment.TopEnd))
        }
        return
    }
    // A photo keeps its own shape: as tall as the card, as wide as its aspect ratio allows, with a thin border.
    image?.takeIf { drawing == null && it.width > 0 && it.height > 0 }?.let { img ->
        Box(modifier) {
            val shape = RoundedCornerShape(12.dp)
            Box(
                Modifier.aspectRatio(img.width.toFloat() / img.height, matchHeightConstraintsFirst = true).clip(shape)
                    .border(1.dp, jc.line, shape).clickable(role = Role.Button) { onOpen(null) }.semantics { contentDescription = a.name },
            ) {
                Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                if (onRemove != null) RemoveButton(a, onRemove, Modifier.align(Alignment.TopEnd))
            }
        }
        return
    }
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(jc.surface).clickable(role = Role.Button) { onOpen(drawing) }.semantics { contentDescription = a.name }) {
        drawing?.let { InlineDrawing(it, Modifier.fillMaxSize()) } ?: Row(
            Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            JIcon(Ico.ATTACH, jc.accent, size = 22.dp)
            Column(Modifier.weight(1f)) {
                Text(a.name, style = JType.rowTitle, color = jc.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (file.exists()) a.mime.substringAfter('/') else "Not downloaded yet. It arrives with the next sync.",
                    style = JType.small, color = jc.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onRemove != null) RemoveButton(a, onRemove, Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun RemoveButton(a: Attachment, onRemove: () -> Unit, modifier: Modifier) {
    Box(
        modifier.padding(6.dp).size(28.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f))
            .clickable(role = Role.Button, onClick = onRemove).semantics { contentDescription = "Remove ${a.name}" },
        contentAlignment = Alignment.Center,
    ) { JIcon(Ico.CLOSE, Color.White, size = 12.dp, stroke = 2.6f) }
}

/** A screen-sized preview of a photo. */
private fun decodePreview(file: java.io.File, maxSide: Int = 1440): ImageBitmap? = decodeImage(file, maxSide)?.asImageBitmap()

private val MARKER_TYPES = setOf(BlockType.BULLET, BlockType.DASHED, BlockType.NUMBERED, BlockType.CHECK)

/** Calls [block] with each note line's vertical bounds (all of its wrapped rows) and its first row's bottom in [layout]. */
private inline fun forEachLineBounds(doc: EditorDoc, layout: TextLayoutResult, block: (Int, Float, Float, Float) -> Unit) {
    if (layout.layoutInput.text.length != doc.text.length) return // layout is from an older frame
    var start = 0
    doc.texts.forEachIndexed { i, line ->
        val end = start + line.length
        val first = layout.getLineForOffset(start)
        val top = layout.getLineTop(first)
        val bottom = layout.getLineBottom(layout.getLineForOffset(end))
        block(i, top, bottom, layout.getLineBottom(first))
        start = end + 1
    }
}

// ---------- Settings ----------

@Composable
private fun SettingsLabel(text: String) {
    Text(text, Modifier.padding(start = 16.dp, top = 28.dp, bottom = 8.dp), style = JType.section, color = jc.muted)
}

@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val status by vm.status.collectAsState()
    val pending by vm.pending.collectAsState()
    val online by vm.online.collectAsState()
    val lockedCount by vm.lockedCount.collectAsState()
    val unlocked by vm.unlocked.collectAsState()
    val fingerprint by vm.fingerprint.collectAsState()
    var askCurrent by remember { mutableStateOf<(() -> Unit)?>(null) }
    var newPassword by remember { mutableStateOf(false) }
    val autoSort by vm.autoSortChecked.collectAsState()
    val appearance by vm.appearance.collectAsState()
    val defaultSort by vm.defaultSort.collectAsState()
    val toggles by vm.toggles.collectAsState()
    val keepDays by vm.keepDeletedDays.collectAsState()
    val exportContext = LocalContext.current
    val exportTo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { vm.exportAll(it) { msg -> Toast.makeText(exportContext, msg, Toast.LENGTH_LONG).show() } }
    }
    var shorterKeep by remember { mutableStateOf<Int?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmRestore by remember { mutableStateOf(false) }
    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        vm.onConsentResult(it.data)
    }
    LaunchedEffect(vm.consentIntent) {
        vm.consentIntent?.let { consentLauncher.launch(IntentSenderRequest.Builder(it.intentSender).build()) }
    }

    Scaffold(
        containerColor = jc.page,
        topBar = { Box(Modifier.background(jc.page)) { NavBar(null, {}, color = jc.page) { TextAction("Done", onBack, bold = true) } } },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            PageTitle("Settings")
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 48.dp)) {
                // Account and sync
                Card {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(jc.surface), contentAlignment = Alignment.Center) {
                            JIcon("M4 12a8 8 0 0 1 14-5l2-2v6h-6l2-2a5 5 0 1 0 1 6", jc.accent, size = 20.dp)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(if (status.signedIn) "Sync is on" else "Back up to Google Drive", style = JType.rowTitle, color = jc.ink)
                            Text(
                                if (status.signedIn) listOfNotNull(status.email ?: status.name, "Last sync " + (status.lastSyncAt?.let { listTime(context, it) } ?: "never")).joinToString(" · ")
                                else "Optional. Signed in, your notes back up to hidden app storage in your Google Drive.",
                                style = JType.small, color = jc.muted,
                            )
                            if (status.signedIn) {
                                val waiting = if (pending == 1) "1 change" else "$pending changes"
                                Text(
                                    when {
                                        !online -> if (pending > 0) "Offline · $waiting waiting" else "Offline"
                                        pending > 0 -> "$waiting waiting to sync"
                                        else -> "Everything is synced"
                                    },
                                    style = JType.small, color = if (!online || pending > 0) jc.ink else jc.muted,
                                )
                            }
                        }
                    }
                    CardDivider()
                    if (status.signedIn) {
                        CardRow(if (vm.busy) "Working…" else "Sync now", labelColor = jc.accent, onClick = { if (!vm.busy) vm.syncNow() })
                        CardDivider()
                        CardRow("Restore from Google account", labelColor = jc.accent, onClick = { if (!vm.busy) confirmRestore = true })
                        CardDivider()
                        CardRow("Sign out", labelColor = jc.danger, onClick = { if (!vm.busy) vm.signOut() })
                    } else {
                        CardRow("Sign in with Google", labelColor = jc.accent, onClick = {
                            scope.launch {
                                runCatching { signInWithGoogle(context as Activity) }
                                    .onSuccess { vm.message = null; vm.onSignedIn(it) }
                                    .onFailure {
                                        // Backing out of the account picker is not an error.
                                        vm.message = if (it is androidx.credentials.exceptions.GetCredentialCancellationException) null
                                        else "Sign-in failed: ${it.message ?: it.javaClass.simpleName}"
                                    }
                            }
                        })
                    }
                }
                status.lastError?.let { Text("Last error: $it", Modifier.padding(start = 16.dp, top = 8.dp), style = JType.small, color = jc.danger) }
                vm.message?.let { Text(it, Modifier.padding(start = 16.dp, top = 8.dp), style = JType.small, color = jc.muted) }

                SettingsLabel("Appearance")
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        @Composable
                        fun <T> choices(title: String, options: List<Pair<T, String>>, current: T, onPick: (T) -> Unit) {
                            Text(title, style = JType.row, color = jc.ink)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                options.forEach { (value, label) -> Pill(label, value == current, { onPick(value) }) }
                            }
                        }
                        choices("Theme", listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark"), appearance.theme) {
                            vm.setAppearance(appearance.copy(theme = it))
                        }
                        Text("Accent colour", Modifier.padding(top = 6.dp), style = JType.row, color = jc.ink)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            val dark = isDark(appearance.theme)
                            Accent.entries.forEach { a ->
                                val color = accentColors(a).let { if (dark) it.second else it.first }
                                val name = a.name.lowercase().replaceFirstChar { it.uppercase() }
                                Box(
                                    Modifier.size(36.dp).clip(CircleShape).background(color)
                                        .border(3.dp, if (a == appearance.accent) jc.ink else Color.Transparent, CircleShape)
                                        .clickable(role = Role.RadioButton) { vm.setAppearance(appearance.copy(accent = a)) }
                                        .semantics { contentDescription = name; selected = a == appearance.accent },
                                    contentAlignment = Alignment.Center,
                                ) { if (a == appearance.accent) JIcon(Ico.CHECK, if (dark) Color.Black else Color.White, size = 18.dp, stroke = 2.6f) }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Text size", Modifier.weight(1f), style = JType.row, color = jc.ink)
                            Text(appearance.textSize.label, style = JType.secondary, color = jc.muted)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("A", style = JType.small, color = jc.muted)
                            Slider(
                                value = appearance.textSize.ordinal.toFloat(),
                                onValueChange = { vm.setAppearance(appearance.copy(textSize = TextSize.entries[it.roundToInt()])) },
                                valueRange = 0f..TextSize.entries.lastIndex.toFloat(),
                                steps = TextSize.entries.size - 2,
                                modifier = Modifier.weight(1f).semantics { stateDescription = appearance.textSize.label },
                                colors = SliderDefaults.colors(thumbColor = jc.accent, activeTrackColor = jc.accent, inactiveTrackColor = jc.line),
                            )
                            Text("A", style = JType.heading, color = jc.muted)
                        }
                        Spacer(Modifier.height(6.dp))
                        choices("Preview lines in lists", listOf(0 to "None", 1 to "1 line", 2 to "2 lines"), appearance.previewLines) {
                            vm.setAppearance(appearance.copy(previewLines = it))
                        }
                    }
                }

                SettingsLabel("Writing")
                Card {
                    CardRow("Move checked items to the bottom", sub = "In checklists, ticked items sink below the rest.") {
                        JSwitch(autoSort, { vm.setAutoSortChecked(it) })
                    }
                    CardDivider()
                    CardRow("Smart checklists", sub = "Lines that start with [ ] become checkboxes.") {
                        JSwitch(toggles(Toggle.SMART_CHECKLISTS), { vm.setToggle(Toggle.SMART_CHECKLISTS, it) })
                    }
                    CardDivider()
                    CardRow("Link with [[ ]]", sub = "Type two brackets to link another note.") {
                        JSwitch(toggles(Toggle.LINKS), { vm.setToggle(Toggle.LINKS, it) })
                    }
                    CardDivider()
                    CardRow("Check grammar", sub = "Underlines common mistakes in blue. Tap one to fix it. Works offline.") {
                        JSwitch(toggles(Toggle.GRAMMAR), { vm.setToggle(Toggle.GRAMMAR, it) })
                    }
                    CardDivider()
                    CardRow(
                        "Better grammar checking",
                        sub = "Sends note text to LanguageTool (languagetool.org) for a fuller check. Locked notes are never sent.",
                    ) {
                        JSwitch(toggles(Toggle.CLOUD_GRAMMAR), { vm.setToggle(Toggle.CLOUD_GRAMMAR, it) }, enabled = toggles(Toggle.GRAMMAR))
                    }
                }

                SettingsLabel("Notes list")
                Card {
                    val (sortBy, ascending) = defaultSort
                    val key = when (sortBy) { NoteSort.EDITED -> "Date edited"; NoteSort.CREATED -> "Date created"; NoteSort.TITLE -> "Title" }
                    val direction = if (sortBy == NoteSort.TITLE) (if (ascending) "A to Z" else "Z to A")
                    else if (ascending) "oldest first" else "newest first"
                    CardRow("Sort new folders by", sub = "$key, $direction") {
                        SortMenu(sortBy, ascending, label = "Change", onChange = vm::setDefaultSort)
                    }
                    CardDivider()
                    CardRow("Group by date", sub = "Today, Yesterday, Previous 7 Days, …") {
                        JSwitch(toggles(Toggle.GROUP_BY_DATE), { vm.setToggle(Toggle.GROUP_BY_DATE, it) })
                    }
                }

                SettingsLabel("Privacy & data")
                Card {
                    CardRow("Hide locked notes in search") {
                        JSwitch(toggles(Toggle.HIDE_LOCKED_IN_SEARCH), { vm.setToggle(Toggle.HIDE_LOCKED_IN_SEARCH, it) })
                    }
                    CardDivider()
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Keep deleted notes", style = JType.row, color = jc.ink)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Shorter removes older deleted notes for good, so it asks first.
                            KEEP_DELETED_DAYS.forEach { d -> Pill("$d days", d == keepDays, { if (d < keepDays) shorterKeep = d else vm.setKeepDeletedDays(d) }) }
                        }
                    }
                    CardDivider()
                    CardRow(
                        "Export all as Markdown", sub = "A zip of every note with its photos and files. Locked notes export only their title.",
                        labelColor = jc.accent, onClick = { exportTo.launch("Margin export ${java.time.LocalDate.now()}.zip") },
                    )
                }

                SettingsLabel("Locked notes")
                Card {
                    if (lockedCount == 0) {
                        CardRow("No locked notes", sub = "Lock a note from the ⋯ menu in the editor. You'll create a 6-digit passcode the first time.")
                    } else {
                        CardRow(
                            plural(lockedCount, "locked note"),
                            sub = if (unlocked) "Unlocked until Margin goes to the background." else "Locked.",
                        )
                        if (unlocked) {
                            CardDivider()
                            CardRow("Lock now", labelColor = jc.accent, onClick = vm::lockNow)
                        }
                        CardDivider()
                        CardRow("Change passcode", labelColor = jc.accent, onClick = {
                            if (vm.busy) return@CardRow
                            if (unlocked) newPassword = true else askCurrent = { newPassword = true }
                        })
                        val activity = context as? FragmentActivity
                        if (activity != null && Biometric.available(activity)) {
                            CardDivider()
                            CardRow("Unlock with fingerprint") {
                                JSwitch(fingerprint != null, { on ->
                                    if (!on) {
                                        vm.setFingerprint(null)
                                        Biometric.forget()
                                    } else {
                                        val enable = {
                                            scope.launch {
                                                val pw = vm.passwordForFingerprint() ?: return@launch
                                                val bytes = String(pw).toByteArray(Charsets.UTF_8)
                                                pw.fill('\u0000')
                                                when (val r = Biometric.encrypt(activity, bytes)) {
                                                    is Biometric.Result.Ok -> {
                                                        val b64 = java.util.Base64.getUrlEncoder().withoutPadding()
                                                        vm.setFingerprint(b64.encodeToString(r.iv) + "." + b64.encodeToString(r.bytes))
                                                    }
                                                    is Biometric.Result.Failed -> vm.message = r.message
                                                    else -> {}
                                                }
                                                bytes.fill(0)
                                            }
                                            Unit
                                        }
                                        if (unlocked) enable() else askCurrent = enable
                                    }
                                })
                            }
                        }
                    }
                }
            }
        }
    }

    askCurrent?.let { then ->
        var old by remember { mutableStateOf(false) }
        val submit: suspend (CharArray) -> String? = { pw ->
            // An old password brings up the switch to a passcode (JotterRoot), which replaces this step.
            if (vm.checkPassword(pw)) { askCurrent = null; if (!vm.legacyPassword.value) then(); null } else if (old) "Wrong password" else "Wrong passcode"
        }
        if (old) OldPasswordDialog(onDismiss = { askCurrent = null }, onSubmit = submit)
        else PasscodeDialog(PasswordMode.ENTER, onDismiss = { askCurrent = null }, onOldPassword = { old = true }, onSubmit = submit)
    }
    if (newPassword) {
        PasscodeDialog(PasswordMode.CREATE, onDismiss = { newPassword = false }) { password ->
            vm.changePassword(password)
            newPassword = false
            null
        }
    }
    shorterKeep?.let { d ->
        AlertDialog(
            onDismissRequest = { shorterKeep = null },
            title = { Text("Keep deleted notes for $d days?") },
            text = { Text("Notes in Recently Deleted for longer than $d days are removed for good now.") },
            confirmButton = { TextButton(onClick = { vm.setKeepDeletedDays(d); shorterKeep = null }) { Text("Keep $d days", color = jc.danger) } },
            dismissButton = { TextButton(onClick = { shorterKeep = null }) { Text("Cancel") } },
        )
    }
    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Restore from Google account?") },
            text = { Text("Notes saved in your Google account replace the copies on this device. Notes that exist only on this device are kept.") },
            confirmButton = { TextButton(onClick = { vm.restore(); confirmRestore = false }) { Text("Restore", fontWeight = FontWeight.SemiBold) } },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancel") } },
        )
    }
}

// ---------- Launch ----------

/**
 * The full logo on a cold start: the icon beside the "Margin" wordmark (Inter Bold, -4.5% tracking) with the
 * tagline under it. Sized in dp, not sp, so the lockup keeps its proportions at any font size.
 */
@Composable
fun LaunchScreen() {
    val px = LocalDensity.current
    val icon = 56.dp
    val word = with(px) { (icon * 1.06f).toSp() } // the icon is about 1.3x the wordmark's capital height
    val tagline = with(px) { (icon * 0.3f).toSp() }
    Box(
        Modifier.fillMaxSize().background(jc.bg).semantics { contentDescription = "Margin. Write first. Organise later." },
        contentAlignment = Alignment.Center,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(icon * 0.32f)) {
                MarginMark(icon)
                Text(
                    "Margin",
                    style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = word, lineHeight = word, letterSpacing = (-0.045).em),
                    color = jc.ink,
                )
            }
            Text(
                "Write first. Organise later.", Modifier.padding(top = icon * 0.36f),
                style = TextStyle(fontFamily = Inter, fontSize = tagline, lineHeight = tagline * 1.3f), color = jc.muted,
            )
        }
    }
}

// ---------- Welcome ----------

/** Shown once, on first launch. */
@Composable
fun WelcomeScreen(onStart: () -> Unit, onBackup: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(jc.bg).systemBarsPadding().padding(horizontal = 32.dp).padding(top = 72.dp, bottom = 24.dp),
    ) {
        MarginMark(72.dp)
        Text("Margin", Modifier.padding(top = 28.dp, bottom = 8.dp), style = JType.pageTitle.copy(fontSize = 40.sp, lineHeight = 46.sp, letterSpacing = (-0.03).em), color = jc.ink)
        Text("Write first. Organise later.", Modifier.padding(bottom = 48.dp), style = JType.body.copy(fontSize = 20.sp, lineHeight = 28.sp), color = jc.muted)
        Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
            listOf(
                Triple("M5 3h10l4 4v14H5zM9 12h6M9 16h4", "Opens to a blank page", "Tap Start writing and type. The first line becomes the title."),
                Triple("M4 12a8 8 0 0 1 14-5l2-2v6h-6l2-2a5 5 0 1 0 1 6", "Syncs quietly", "Every change is saved and backs up to your Google Drive when you turn it on."),
                Triple("M6 11V8a6 6 0 0 1 12 0v3M5 11h14v10H5z", "Private by default", "Lock any note with your passcode. Nothing is shared unless you share it."),
            ).forEach { (icon, title, text) ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { JIcon(icon, jc.accent, size = 24.dp) }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(title, style = JType.rowTitle, color = jc.ink)
                        Text(text, style = JType.secondary.copy(lineHeight = 21.sp), color = jc.muted)
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton("Start writing", onStart, Modifier.fillMaxWidth())
        Box(
            Modifier.fillMaxWidth().padding(top = 8.dp).height(44.dp).clickable(role = Role.Button, onClick = onBackup),
            contentAlignment = Alignment.Center,
        ) { Text("Turn on backup", style = JType.secondary.copy(fontWeight = FontWeight.Medium), color = jc.accent) }
    }
}

// ---------- Root ----------

@Composable
fun JotterRoot(
    nav: NavViewModel,
    folders: FoldersViewModel,
    notes: NotesViewModel,
    editor: EditorViewModel,
    settings: SettingsViewModel,
) {
    val rows by folders.rows.collectAsState()
    val tags by folders.tags.collectAsState()
    val fingerprint by settings.fingerprint.collectAsState()
    val appearance by settings.appearance.collectAsState()
    val welcomeDone by settings.welcomeDone.collectAsState()
    val context = LocalContext.current
    // Unlocked with a password from before passcodes: offer the switch once per unlock.
    val legacy by settings.legacyPassword.collectAsState()
    var skipSwitch by remember { mutableStateOf(false) }
    val rootScope = rememberCoroutineScope()
    LaunchedEffect(legacy) { if (!legacy) skipSwitch = false }
    if (legacy && !skipSwitch) {
        PasscodeDialog(
            PasswordMode.CREATE, onDismiss = { skipSwitch = true },
            intro = "Locked notes now use a 6-digit passcode. Choose one and your locked notes will be re-encrypted with it. " +
                "Other devices will need the new passcode too.",
        ) { code ->
            skipSwitch = true
            rootScope.launch {
                editor.saveNow() // an open locked note is saved with the old key before the notes are re-sealed
                settings.changePassword(code).join()
                editor.rekeyed()
                Toast.makeText(context, settings.message ?: "Passcode set", Toast.LENGTH_LONG).show()
            }
            null
        }
    }
    nav.incoming?.let { share ->
        FolderSheet(
            title = "Save to Margin",
            subject = share.subject?.takeIf { it.isNotBlank() } ?: share.text?.lineSequence()?.firstOrNull { it.isNotBlank() } ?: "Photo",
            subjectNote = if (share.image != null) "With a photo" else null,
            rows = rows.filter { !it.folder.isSmart }, currentId = null,
            onDismiss = { nav.incoming = null },
        ) { folderId ->
            nav.incoming = null
            if (folderId == null) return@FolderSheet
            folders.saveShared(share.subject, share.text, share.image, folderId, onError = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }) { id ->
                if (nav.current is Screen.Editor) editor.close()
                nav.push(Screen.Editor(id, folderId))
            }
        }
    }
    if (!welcomeDone) {
        WelcomeScreen(
            onStart = { settings.setWelcomeDone(); nav.push(Screen.Editor(newId(), DEFAULT_FOLDER_ID)) },
            onBackup = { settings.setWelcomeDone(); nav.push(Screen.Settings) },
        )
        return
    }
    fun back() {
        if (nav.current is Screen.Editor) editor.close()
        nav.pop()
    }
    fun listName(folderId: String) = when {
        folderId == TRASH_ID -> "Recently Deleted"
        folderId == ALL_ID -> "All Notes"
        folderId == PINNED_ID -> "Pinned"
        folderId.startsWith(TAG_PREFIX) -> "#" + (tags.firstOrNull { TAG_PREFIX + it.key == folderId }?.name ?: folderId.removePrefix(TAG_PREFIX))
        else -> rows.firstOrNull { it.folder.id == folderId }?.folder?.name ?: "Notes"
    }
    androidx.activity.compose.BackHandler(enabled = nav.stack.size > 1) { back() }

    when (val screen = nav.current) {
        Screen.Folders -> FoldersScreen(
            folders,
            onOpen = { nav.push(Screen.Notes(it)) },
            onSearch = { nav.push(Screen.Notes(ALL_ID, search = true)) },
            onNew = { nav.push(Screen.Editor(newId(), DEFAULT_FOLDER_ID)) },
            onEdit = { nav.push(Screen.EditFolders) },
            onSettings = { nav.push(Screen.Settings) },
            onTodos = { nav.push(Screen.Todos) },
            onReminders = { nav.push(Screen.Reminders) },
        )
        Screen.EditFolders -> EditFoldersScreen(folders, onBack = ::back)
        Screen.Reminders -> {
            val all by folders.liveNotes.collectAsState()
            val reminders by folders.reminders.collectAsState()
            RemindersScreen(all, reminders, onOpen = { nav.push(Screen.Editor(it.id, it.folderId)) }, onBack = ::back)
        }
        Screen.Todos -> {
            val all by folders.liveNotes.collectAsState()
            TodosScreen(all, onOpen = { nav.push(Screen.Editor(it.id, it.folderId)) }, onBack = ::back)
        }
        is Screen.Notes -> NoteListScreen(
            vm = notes,
            folderId = screen.folderId,
            search = screen.search,
            folderName = listName(screen.folderId),
            folders = rows,
            previewLines = appearance.previewLines,
            grid = appearance.gridView,
            onGrid = { settings.setAppearance(appearance.copy(gridView = it)) },
            onOpen = { nav.push(Screen.Editor(it.id, it.folderId)) },
            onNew = { nav.push(Screen.Editor(newId(), screen.folderId.takeIf { !isVirtualList(it) } ?: DEFAULT_FOLDER_ID)) },
            onNewWith = { q ->
                val f = screen.folderId.takeIf { !isVirtualList(it) } ?: DEFAULT_FOLDER_ID
                folders.saveShared(q, null, null, f, onError = {}) { id -> nav.push(Screen.Editor(id, f)) }
            },
            onTrash = { nav.push(Screen.Notes(TRASH_ID)) },
            onSettings = { nav.push(Screen.Settings) },
            onBack = ::back,
        )
        is Screen.Editor -> {
            val previous = nav.stack.getOrNull(nav.stack.lastIndex - 1)
            val backLabel = when (previous) {
                is Screen.Notes -> listName(previous.folderId)
                is Screen.Editor -> "Back" // came here through a link
                Screen.Todos -> "To-dos"
                Screen.Reminders -> "Reminders"
                else -> "Folders"
            }
            EditorScreen(
                editor, screen.noteId, screen.folderId, rows, fingerprint, backLabel, onBack = ::back,
                // Save and close the note first; coming back reopens it from the database.
                onOpenTag = { key -> editor.close(); nav.push(Screen.Notes(TAG_PREFIX + key)) },
                onOpenNote = { note -> editor.close(); nav.push(Screen.Editor(note.id, note.folderId)) },
                // Replaces this note, so Back goes where this note's Back went.
                onNewNote = { folder -> editor.close(); nav.pop(); nav.push(Screen.Editor(newId(), folder)) },
            )
        }
        Screen.Settings -> SettingsScreen(settings, onBack = ::back)
    }
}
