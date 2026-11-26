package com.margin.app.ui

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import com.margin.app.data.BlockType
import com.margin.app.data.Drawing
import com.margin.app.data.MAX_INDENT
import com.margin.app.data.Mark
import com.margin.app.data.NoteEntity
import com.margin.app.data.NoteTemplate
import com.margin.app.data.Paper
import com.margin.app.data.PdfFilePrintAdapter
import com.margin.app.data.TagCount
import com.margin.app.data.Tint
import com.margin.app.data.color
import com.margin.app.data.displayTitle
import com.margin.app.data.epochDay
import com.margin.app.data.findTags
import com.margin.app.data.invoke
import com.margin.app.data.lastEdited
import com.margin.app.data.plainAnswer
import com.margin.app.data.plainText
import com.margin.app.data.resolveLink
import com.margin.app.data.safeFileName
import com.margin.app.data.shareText
import com.margin.app.data.tagKey
import com.margin.app.data.todoCount
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.margin.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource

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
    var stats by remember { mutableStateOf(false) }
    var sortingList by remember { mutableStateOf(false) }
    var dueFor by remember { mutableStateOf<Int?>(null) }
    var history by remember { mutableStateOf(false) }
    var linked by remember { mutableStateOf(false) }
    var asking by remember { mutableStateOf(false) }
    val savedTemplates by vm.templates.collectAsState()
    /** Find in note: null = closed. [findAt] is the current match. */
    var find by remember { mutableStateOf<String?>(null) }
    var findAt by remember { mutableIntStateOf(0) }
    val matches = remember(vm.doc.text, find) {
        val q = find.orEmpty()
        if (q.isBlank()) emptyList() else Regex(Regex.escape(q), RegexOption.IGNORE_CASE).findAll(vm.doc.text).map { it.range }.toList()
    }
    // Replacing a match shortens the list; the position wraps instead of pointing past the end.
    val matchAt = if (matches.isEmpty()) 0 else findAt.mod(matches.size)
    var replacement by remember { mutableStateOf("") }
    androidx.activity.compose.BackHandler(enabled = find != null) { find = null }
    androidx.activity.compose.BackHandler(enabled = vm.mathKeys && vm.focused) { vm.mathKeys = false }
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
    /** Share as image: the card preview and style picker is open. */
    var carding by remember { mutableStateOf(false) }
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
        else Toast.makeText(context, context.getString(R.string.editor_margin_needs_the_microphone_to), Toast.LENGTH_LONG).show()
    }
    // Reminders: set from More, shown under the date line, rung as a notification.
    val reminderAt by vm.reminderAt.collectAsState()
    val reminderRepeat by vm.reminderRepeat.collectAsState()
    val placeReminder by vm.placeReminder.collectAsState()
    var reminding by remember { mutableStateOf(false) }
    var placing by remember { mutableStateOf(false) }
    val placeAccess = rememberPlaceAccess()
    placeAccess.Dialogs()
    /** The to-do target date picker is open. */
    var pickingTarget by remember { mutableStateOf(false) }
    var pickingRepeat by remember { mutableStateOf(false) }
    var movingTasks by remember { mutableStateOf(false) }
    var sortingAisles by remember { mutableStateOf(false) }
    if (sortingAisles) {
        val items = remember { vm.doc.toBlocks().filter { it.type == com.margin.app.data.BlockType.CHECK && !it.checked && it.attachment == null && it.text.isNotBlank() }.map { it.text.trim() } }
        AisleSortDialog(
            items, remember { vm.aisleChoices() }, onChoose = vm::chooseAisle,
            onSort = { sortingAisles = false; vm.sortByAisle(); toast(context.getString(R.string.editor_sorted_by_aisle_undo_puts)) },
            onDismiss = { sortingAisles = false },
        )
    }
    if (movingTasks) {
        val all by vm.linkTargets.collectAsState()
        val tasks = remember { vm.doc.toBlocks().withIndex().filter { it.value.type == com.margin.app.data.BlockType.CHECK && it.value.attachment == null && it.value.table == null }.map { it.index to it.value } }
        MoveTasksDialog(
            tasks, remember(all) { com.margin.app.data.ListWidgetData.lists(all).filter { it.id != vm.noteId } }, vm.canMoveTasks,
            onDone = { lines, to, title, copy ->
                movingTasks = false
                vm.moveTasks(lines, to, title, copy) { name, gained ->
                    val n = lines.size
                    toast(
                        context.resources.getQuantityString(if (gained == 0) R.plurals.editor_already_has else if (copy) R.plurals.editor_copied_items else R.plurals.editor_moved_items, n, n, name),
                    )
                }
            },
            onDismiss = { movingTasks = false },
        )
    }
    if (pickingRepeat) ListRepeatDialog(vm.listRepeat, onPick = { pickingRepeat = false; vm.changeListRepeat(it) }, onDismiss = { pickingRepeat = false })
    var pickingPaper by remember { mutableStateOf(false) }
    if (pickingPaper) PaperDialog(vm.paper, onPick = { pickingPaper = false; vm.changePaper(it) }, onDismiss = { pickingPaper = false })
    var pickingFont by remember { mutableStateOf(false) }
    var pickingColor by remember { mutableStateOf(false) }
    if (pickingColor) ColorDialog(vm.color, onPick = { pickingColor = false; vm.changeColor(it) }, onDismiss = { pickingColor = false })
    if (pickingFont) FontDialog(vm.font, onPick = { pickingFont = false; vm.changeFont(it) }, onDismiss = { pickingFont = false })
    if (pickingTarget) {
        TargetDateDialog(
            vm.targetDate,
            onPick = { pickingTarget = false; vm.changeTargetDate(it) },
            onRemove = { pickingTarget = false; vm.changeTargetDate(null) },
            onDismiss = { pickingTarget = false },
        )
    }
    // Without "Alarms & reminders" (off by default on Android 14+), Android rings a reminder up to an hour late.
    // Then, once only, the full-screen alert (Android 14+), so a reminder on a locked phone fills the screen.
    fun askForExactAlarms() {
        val pkg = android.net.Uri.parse("package:${context.packageName}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !vm.remindersOnTime) {
            toast(context.getString(R.string.editor_to_ring_on_time_allow))
            runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg)) }
        } else if (Build.VERSION.SDK_INT >= 34 && vm.shouldAskFullScreen()) {
            toast(context.getString(R.string.editor_to_show_reminders_full_screen))
            runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg)) }
        }
    }
    // Android 9 and older need storage permission to save a card to Photos.
    var pendingSave by remember { mutableStateOf<(suspend () -> Unit)?>(null) }
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val save = pendingSave; pendingSave = null
        if (granted && save != null) scope.launch { save() } else if (!granted) toast(context.getString(R.string.editor_margin_needs_storage_access_to))
    }
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) toast(context.getString(R.string.editor_reminder_set_but_notifications_are))
        else askForExactAlarms()
    }
    val inShade by vm.inShade.collectAsState()
    val shadePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { vm.pinToShade(); toast(context.getString(R.string.editor_pinned_to_notifications)) }
        else toast(context.getString(R.string.editor_notifications_are_off_for_margin))
    }
    val reminderScope = rememberCoroutineScope()
    if (reminding) {
        ReminderDialog(
            current = reminderAt, currentRepeat = reminderRepeat, onTime = vm.remindersOnTime, calendarFor = vm.noteId,
            onPick = { time, repeat, calendar ->
                reminding = false
                vm.setReminder(time, repeat)
                // The note's first line is the event's title; the rest (never a locked note's) its description.
                val lines = vm.doc.texts
                reminderToCalendar(
                    context, reminderScope, vm.noteId, lines.first().trim(),
                    if (vm.locked) null else lines.drop(1).joinToString("\n").trim().take(2000), time, repeat, calendar,
                )
                if (Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) notifyPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                else askForExactAlarms()
            },
            onRemove = {
                reminding = false
                vm.clearReminder()
                val id = vm.noteId
                reminderScope.launch(kotlinx.coroutines.Dispatchers.IO) { com.margin.app.data.CalendarEvents.remove(context, id) }
            },
            onDismiss = { reminding = false },
            place = placeReminder, onPlace = { reminding = false; placing = true },
        )
    }
    if (placing) PlaceReminderDialog(
        current = placeReminder, recent = remember { vm.recentPlaces() },
        onPick = { place ->
            placing = false
            vm.setPlace(place)
            // Then location in the background, said why first; the place is kept either way.
            placeAccess.ensure(background = true) { ok ->
                if (ok) vm.watchPlaces() else placeNotWatched(context)
                if (Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) notifyPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        onRemove = { placing = false; vm.clearPlace() },
        onDismiss = { placing = false },
    )
    drawing?.let { (line, d) ->
        val onError = { msg: String -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
        DrawingScreen(
            d, vm.doc.texts.first().trim(),
            onDone = { result -> drawing = null; result?.let { vm.saveDrawing(it, line, onError) } },
            onText = { remaining, text -> drawing = null; vm.drawingToText(remaining, line, text, onError) },
        )
    }
    val folderName = folders.firstOrNull { it.folder.id == vm.folderId }?.folder?.name

    // A phone on its side with the keyboard up has no room for the top bar; the toolbar still hides the keyboard.
    val cramped = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 480 &&
        WindowInsets.ime.getBottom(LocalDensity.current) > 0
    Scaffold(
        containerColor = jc.bg,
        topBar = { Column(Modifier.background(jc.bg).then(if (cramped) Modifier.statusBarsPadding() else Modifier)) {
            if (!cramped) NavBar(backLabel, onBack, color = jc.bg) {
                if (vm.trashed) {
                    TextAction(stringResource(R.string.editor_recover), { vm.recover(onBack) })
                    TextAction(stringResource(R.string.common_delete), { vm.deleteForever(onBack) }, color = jc.danger)
                } else if (vm.loaded) {
                    if (vm.canUndo && !vm.needsUnlock && !vm.viewOnly) IconAction(Ico.UNDO, stringResource(R.string.common_undo), vm::undo, color = jc.ink)
                    // Lock editing: a closed lock while the note is view-only, an open one while it can be changed.
                    if ((vm.exists || vm.doc.text.isNotBlank()) && !vm.needsUnlock) IconAction(
                        if (vm.viewOnly) Ico.LOCK else Ico.LOCK_OPEN,
                        if (vm.viewOnly) stringResource(R.string.editor_editing_locked_unlock_editing) else stringResource(R.string.editor_lock_editing),
                        { focusManager.clearFocus(); vm.toggleViewOnly() },
                        // Locked: amber on a soft circle, so the state shows at a glance.
                        color = if (vm.viewOnly) jc.accent else jc.ink, soft = vm.viewOnly,
                    )
                    // A filled pill, so finishing an edit reads as the main action in the bar.
                    if (vm.focused && !vm.viewOnly) Box(
                        Modifier.padding(start = 4.dp).height(36.dp).clip(JShape.pill).background(jc.accent)
                            .clickable(role = Role.Button) { focusManager.clearFocus() }.padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(stringResource(R.string.common_done), style = JType.item.copy(fontSize = 15.sp), color = jc.bg, fontWeight = FontWeight.SemiBold) }
                    // Reminder: amber while one is set.
                    if (vm.canRemind) IconAction(
                        Ico.ALARM, reminderAt?.let { stringResource(R.string.editor_reminder_change_it, reminderLabel(context, it, reminderRepeat)) }
                            ?: placeReminder?.let { stringResource(R.string.editor_reminder_change_it, it.phraseText(context)) } ?: stringResource(R.string.editor_remind_me),
                        { reminding = true }, fill = reminderAt != null || placeReminder != null,
                        color = if (reminderAt != null || placeReminder != null) jc.accent else jc.ink,
                    )
                    if (!vm.needsUnlock) IconAction(Ico.SHARE, stringResource(R.string.common_share), { sharing = !sharing; formatting = false; attaching = false }, color = jc.ink)
                    Box {
                        IconAction(Ico.MORE, stringResource(R.string.common_more), { menu = true }, fill = true, color = jc.ink)
                        JMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            val lockTile: (() -> Unit)? = when {
                                !vm.locked -> {
                                    {
                                        if (unlocked) vm.lock()
                                        else scope.launch { passwordFor = if (vm.hasPassword()) PasswordMode.ENTER else PasswordMode.CREATE }
                                    }
                                }
                                !vm.needsUnlock -> vm::removeLock
                                else -> null
                            }
                            // Pin, Lock and Share as tiles across the top, then the rest as rows.
                            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                                listOfNotNull(
                                    if (vm.exists) Triple(if (vm.pinned) stringResource(R.string.common_unpin) else stringResource(R.string.common_pin), Ico.PIN, vm::togglePin) else null,
                                    lockTile?.let { Triple(if (vm.locked) stringResource(R.string.editor_remove_lock) else stringResource(R.string.editor_lock), Ico.LOCK, it) },
                                    if (vm.canRemind) Triple(stringResource(R.string.editor_remind), Ico.ALARM) { reminding = true } else null,
                                    if (!vm.needsUnlock) Triple(stringResource(R.string.common_share), Ico.SHARE) { sharing = true; formatting = false; attaching = false } else null,
                                ).forEachIndexed { i, (label, icon, onClick) ->
                                    if (i > 0) Box(
                                        Modifier.width(1.dp).fillMaxHeight().background(jc.line),
                                    )
                                    Column(
                                        Modifier.weight(1f).height(68.dp).clickable(role = Role.Button) { menu = false; onClick() },
                                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                                    ) {
                                        JIcon(icon, jc.ink, size = 20.dp, stroke = 1.8f)
                                        Text(label, style = JType.caption, color = jc.ink)
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
                                    JIcon(icon, if (danger) jc.danger else jc.muted, size = 18.dp, stroke = 1.8f)
                                    Spacer(Modifier.width(12.dp))
                                    Text(label, Modifier.weight(1f), style = JType.item, color = c)
                                }
                            }
                            var first = true
                            if (!vm.needsUnlock) { item(stringResource(R.string.editor_find_in_note), Ico.SEARCH, gap = true) { find = ""; findAt = 0; focusManager.clearFocus() }; first = false }
                            item(stringResource(R.string.editor_move_to_folder), Ico.MOVE, gap = first) { moving = true }
                            if (!vm.readOnly && !vm.needsUnlock) item(if (vm.targetDate != null) stringResource(R.string.editor_change_target_date) else stringResource(R.string.editor_set_target_date), ICON_TARGET) { pickingTarget = true }
                            if (vm.canSortAisles && todoCount(vm.doc.toBlocks()).total > 1) item(stringResource(R.string.editor_sort_list), Ico.SORT) { sortingList = true }
                            if (vm.canSortAisles && todoCount(vm.doc.toBlocks()).total > 1) item(stringResource(R.string.editor_sort_by_aisle), ICON_AISLE) { sortingAisles = true }
                            if (vm.canSortAisles && vm.tickedTasks > 0) item(stringResource(R.string.editor_clear_ticked_items), Ico.CHECK) {
                                val n = vm.tickedTasks
                                vm.clearTicked()
                                toast(context.resources.getQuantityString(R.plurals.editor_removed_ticked, n, n))
                            }
                            if (vm.canCopyTasks && todoCount(vm.doc.toBlocks()).total > 0) item(if (vm.canMoveTasks) stringResource(R.string.editor_move_items) else stringResource(R.string.editor_copy_items), Ico.MOVE) { movingTasks = true }
                            if (inShade) item(stringResource(R.string.editor_unpin_from_notifications), Ico.PIN) { vm.unpinFromShade(); toast(context.getString(R.string.editor_unpinned_from_notifications)) }
                            else if (vm.canRemind) item(stringResource(R.string.editor_pin_to_notifications), Ico.PIN) {
                                if (Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                                    != android.content.pm.PackageManager.PERMISSION_GRANTED
                                ) shadePermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                else { vm.pinToShade(); toast(context.getString(R.string.editor_pinned_to_notifications)) }
                            }
                            if (!vm.readOnly && !vm.needsUnlock) item(stringResource(R.string.editor_paper_item, stringResource(vm.paper.labelRes)), Ico.GRID) { pickingPaper = true }
                            if (vm.exists && !vm.needsUnlock) item(stringResource(R.string.editor_duplicate), Ico.COPY) { vm.duplicate(onOpenNote) }
                            if (vm.canSaveTemplate) item(stringResource(R.string.editor_save_as_template), ICON_TEMPLATE) { vm.saveAsTemplate { toast(context.getString(R.string.editor_saved_as_template, it)) } }
                            // Locked notes are never sent anywhere.
                            if (!vm.locked && !vm.needsUnlock) item(stringResource(R.string.editor_ask_ai), Ico.SPARKLE) { asking = true }
                            item(stringResource(R.string.editor_linked_notes, vm.linkedNotes(targets).size), Ico.LINK, gap = true) { linked = true }
                            item(stringResource(R.string.editor_note_info), Ico.INFO) { info = true }
                            if (!vm.needsUnlock) item(stringResource(R.string.editor_writing_stats), Ico.PEN) { stats = true }
                            if (vm.exists && !vm.locked && !vm.needsUnlock) item(stringResource(R.string.editor_version_history), ICON_HISTORY) { history = true }
                            if (vm.exists) item(if (vm.archived) stringResource(R.string.common_unarchive) else stringResource(R.string.common_archive), Ico.ARCHIVE, gap = true) {
                                val archiving = !vm.archived
                                vm.toggleArchive()
                                toast(if (archiving) context.getString(R.string.editor_archived_find_it_in_library) else context.getString(R.string.editor_unarchived))
                                if (archiving) onBack()
                            }
                            item(stringResource(R.string.common_delete), Ico.TRASH, gap = !vm.exists, danger = true) { vm.delete(onBack) }
                        }
                    }
                }
            }
            find?.let { q ->
                FindBar(q, matches.size, matchAt, onChange = { find = it; findAt = 0 }, onStep = { d ->
                    if (matches.isNotEmpty()) findAt = (matchAt + d).mod(matches.size)
                }, onClose = { find = null })
                if (!vm.readOnly) ReplaceBar(
                    replacement, matches.size, onChange = { replacement = it },
                    onReplace = {
                        matches.getOrNull(matchAt)?.let { vm.replaceRanges(listOf(it), replacement) }
                        // The next match slides into this position, unless the new text is itself a match.
                        findAt = if (replacement.contains(q, ignoreCase = true)) matchAt + 1 else matchAt
                    },
                    onReplaceAll = {
                        val n = matches.size
                        vm.replaceRanges(matches, replacement); findAt = 0
                        toast(context.resources.getQuantityString(R.plurals.editor_replaced_matches, n, n))
                    },
                )
            }
        } },
        bottomBar = {
            if (!vm.readOnly && vm.loaded && !vm.needsUnlock && find == null) {
                val known by vm.otherTags.collectAsState()
                Column(Modifier.background(jc.bg).windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))) {
                    LinkSuggestions(vm.linkSuggestions(targets), vm.linkTyped(), folders, vm::completeLink)
                    TagSuggestions(vm.tagSuggestions(known), vm::completeTag)
                    if (formatting) FormatSheet(vm, onClose = { formatting = false })
                    if (carding) {
                        val blocks = remember { vm.doc.toBlocks() }
                        val date = remember { java.text.DateFormat.getDateInstance(java.text.DateFormat.LONG).format(java.util.Date(vm.editedAt ?: System.currentTimeMillis())) }
                        val title = remember { blocks.first().text.trim().ifEmpty { context.getString(R.string.editor_note) } }
                        // The note's first photo (not a drawing or scan page's PDF), if its file is here to read.
                        val photo = remember { blocks.firstNotNullOfOrNull { b -> b.attachment?.takeIf { it.isImage }?.let(vm.files::file)?.takeIf { it.exists() } } }
                        CardDialog(blocks, date, photo, onDismiss = { carding = false }, onSave = { options ->
                            carding = false
                            if (android.os.Build.VERSION.SDK_INT < 29 && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                != android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) { pendingSave = { saveCards(context, blocks, date, options, photo, title, ::toast) }; storagePermission.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) }
                            else scope.launch { saveCards(context, blocks, date, options, photo, title, ::toast) }
                        }) { options ->
                            carding = false
                            scope.launch {
                                val files = withContext(Dispatchers.IO) {
                                    runCatching {
                                        val pages = noteCards(context, blocks, date, options, photo)
                                        pages.mapIndexed { i, draw ->
                                            val bmp = draw()
                                            val name = safeFileName(title) + (if (pages.size > 1) " ${i + 1}" else "") + ".png"
                                            vm.files.exportFile(name).also { f -> f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
                                                .also { bmp.recycle() }
                                        }
                                    }.getOrNull()
                                } ?: return@launch toast(context.getString(R.string.editor_couldnt_make_the_image))
                                context.startActivity(Intent.createChooser(vm.files.sendIntent(files, "image/png", title), null))
                            }
                        }
                    }
                    if (sharing) ShareSheet(
                        title = vm.doc.texts.first().trim(),
                        words = remember(vm.doc) { vm.doc.texts.drop(1).sumOf { t -> t.split(Regex("\\s+")).count { it.isNotBlank() } } },
                        onClose = { sharing = false },
                        onCard = { sharing = false; carding = true },
                        onCopy = {
                            sharing = false
                            context.getSystemService(android.content.ClipboardManager::class.java)
                                .setPrimaryClip(android.content.ClipData.newPlainText(vm.doc.texts.first().trim(), shareText(vm.doc.toBlocks())))
                            // Android 13+ shows its own copied notice.
                            if (android.os.Build.VERSION.SDK_INT < 33) toast(context.getString(R.string.editor_copied))
                        },
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
                                val file = vm.exportFile(pdf) ?: return@launch toast(context.getString(R.string.editor_couldnt_make_the_file))
                                val title = vm.doc.texts.first().trim().ifEmpty { context.getString(R.string.editor_note) }
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
                    // Scan document: Google's scanner finds the page edges, crops and cleans up; the pages come back as one PDF.
                    val docScanner = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
                        if (r.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
                        val pdf = com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult.fromActivityResultIntent(r.data)?.pdf
                            ?: return@rememberLauncherForActivityResult toast(context.getString(R.string.editor_the_scan_didnt_come_back))
                        vm.addScan(context, pdf.uri) { msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
                    }
                    fun startVoice() {
                        val mic = android.Manifest.permission.RECORD_AUDIO
                        val wanted = listOfNotNull(mic, if (Build.VERSION.SDK_INT >= 33) android.Manifest.permission.POST_NOTIFICATIONS else null)
                        if (wanted.all { androidx.core.content.ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED }) recording = true
                        else micPermission.launch(wanted.toTypedArray())
                    }
                    fun startDocScan() {
                        val activity = context.findActivity() ?: return toast(context.getString(R.string.editor_couldnt_open_the_scanner))
                        val options = com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.Builder()
                            .setGalleryImportAllowed(true)
                            .setResultFormats(com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
                            .setScannerMode(com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                            .build()
                        com.google.mlkit.vision.documentscanner.GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
                            .addOnSuccessListener { docScanner.launch(androidx.activity.result.IntentSenderRequest.Builder(it).build()) }
                            .addOnFailureListener { toast(context.getString(R.string.editor_the_document_scanner_isnt_available)) }
                    }
                    // An app shortcut ("Scan document", "Voice note") opened this new note: start it once the note has loaded.
                    LaunchedEffect(vm.startAction, vm.loaded) {
                        val action = vm.startAction ?: return@LaunchedEffect
                        if (!vm.loaded) return@LaunchedEffect
                        vm.startAction = null
                        when (action) {
                            EditorViewModel.START_SCAN -> startDocScan()
                            EditorViewModel.START_VOICE -> startVoice()
                        }
                    }
                    if (attaching) AttachSheet(
                        onClose = { attaching = false },
                        onFile = { attaching = false; picker.launch(arrayOf("*/*")) },
                        onVoice = { attaching = false; startVoice() },
                        onDraw = { attaching = false; drawing = null to null },
                        onCamera = {
                            attaching = false
                            try { camera.launch(vm.photoTarget()) } catch (_: android.content.ActivityNotFoundException) {
                                vm.photoTaken(false) {}
                                toast(context.getString(R.string.editor_no_camera_app_on_this))
                            }
                        },
                        onLink = { attaching = false; addingLink = true },
                        onTable = { attaching = false; vm.insertTable() },
                        onTemplate = { attaching = false; vm.insertTemplate(it) },
                        saved = savedTemplates,
                        onSaved = { attaching = false; vm.insertSavedTemplate(it) },
                        onEditSaved = { attaching = false; onOpenNote(it) },
                        onDeleteSaved = { vm.deleteTemplate(it); toast(context.getString(R.string.editor_template_moved_to_recently_deleted)) },
                        onPdf = { attaching = false; picker.launch(arrayOf(com.margin.app.data.PDF_MIME)) },
                        onScanDoc = { attaching = false; startDocScan() },
                        onScan = {
                            attaching = false
                            try { scanCamera.launch(vm.scanTarget()) } catch (_: android.content.ActivityNotFoundException) {
                                vm.scanTaken(false, context)
                                toast(context.getString(R.string.editor_no_camera_app_on_this))
                            }
                        },
                    )
                    val showCount by vm.writing.showCount.collectAsState()
                    if (!formatting && !attaching && !sharing) TaskBar(vm) { dueFor = it }
                    if (showCount && !formatting && !attaching && !sharing) WordCountBar(vm) { stats = true }
                    EditorToolbar(
                        vm, formatting, attaching,
                        onFormat = { formatting = !formatting; attaching = false; sharing = false }, onHideKeyboard = { vm.mathKeys = false; focusManager.clearFocus() },
                        onAttach = { attaching = !attaching; formatting = false; sharing = false }, onDraw = { drawing = null to null },
                        onNew = { formatting = false; attaching = false; onNewNote(vm.folderId) },
                        onFont = { formatting = false; attaching = false; sharing = false; pickingFont = true },
                        onColor = { formatting = false; attaching = false; sharing = false; pickingColor = true },
                        onAi = if (!vm.locked && !vm.needsUnlock) ({ formatting = false; attaching = false; sharing = false; asking = true }) else null,
                    )
                    if (vm.mathKeys && vm.focused) MathKeyboard(vm.value, vm::onValueChange, onLetters = { vm.mathKeys = false })
                }
            }
        },
    ) { padding ->
        if (!vm.loaded) return@Scaffold
        // No imePadding here: the bottom bar already sits above the keyboard and Scaffold's padding includes it.
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(scroll)
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            if (vm.needsUnlock) {
                LockedNote(vm, fingerprint, listOfNotNull(vm.editedAt?.let { stringResource(R.string.editor_edited_time, fullTime(context, it)) }, folderName).joinToString(" · "))
            } else {
                // "Saved" once this visit's edits are written.
                val todo = remember(vm.doc) { todoCount(vm.doc.toBlocks()) }
                // When and where, then the lock: a to-do list can still be ticked off; only its wording is locked.
                Text(
                    androidx.compose.ui.text.buildAnnotatedString {
                        append(listOfNotNull(vm.editedAt?.let { fullTime(context, it) } ?: stringResource(R.string.editor_now), folderName?.let { stringResource(R.string.editor_in, it) }, stringResource(R.string.editor_saved).takeIf { vm.canUndo && vm.saved && vm.exists })
                            .joinToString(" · "))
                        if (vm.viewOnly && !vm.trashed) {
                            append(" · ")
                            withStyle(androidx.compose.ui.text.SpanStyle(color = jc.ink)) { append(if (todo.total > 0) stringResource(R.string.editor_locked_ticking_still_works) else stringResource(R.string.editor_locked)) }
                        }
                    },
                    Modifier.padding(bottom = 12.dp), style = JType.small, color = jc.muted,
                )
                reminderAt?.takeIf { !vm.readOnly }?.let { at ->
                    Row(
                        Modifier.padding(bottom = 10.dp).clip(JShape.xs).clickable(role = Role.Button) { reminding = true }
                            .background(jc.selected).padding(horizontal = 8.dp, vertical = 4.dp)
                            .semantics { contentDescription = context.getString(R.string.editor_reminder_tap_to_change_it, reminderLabel(context, at, reminderRepeat)) },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        JIcon(Ico.ALARM, jc.accent, size = 14.dp, stroke = 2f)
                        Text(reminderLabel(context, at, reminderRepeat), style = JType.small, color = jc.accent)
                    }
                }
                placeReminder?.takeIf { !vm.readOnly }?.let { PlaceChip(it) { placing = true } }
                // To-do progress, under the title: shown once the note has checklist lines or a target date.
                val todoHeader: (@Composable () -> Unit)? = if (todo.total > 0 || vm.targetDate != null) { {
                    TodoHeader(
                        todo, startDay = epochDay(vm.createdAt ?: System.currentTimeMillis(), java.time.ZoneId.systemDefault()),
                        target = vm.targetDate, editable = !vm.readOnly, onPickTarget = { pickingTarget = true },
                        repeat = vm.listRepeat, onPickRepeat = { pickingRepeat = true }, streak = vm.listStreak,
                        hideTicked = vm.hideTicked, onHideTicked = { vm.hideTicked = !vm.hideTicked },
                    )
                } } else null
                vm.conflictWith?.let { original -> ConflictChooser(original.displayTitle, vm, onBack) }
                if (vm.trashed) {
                    Text(stringResource(R.string.editor_recover_this_note_to_edit), Modifier.padding(bottom = 10.dp), style = JType.small, color = jc.muted)
                }
                val density = LocalDensity.current
                BlockPhoneKeyboard(vm.mathKeys) { NoteField(
                    vm, matches, matchAt, Modifier.onGloballyPositioned { fieldTop = it.positionInParent().y.roundToInt() },
                    onReveal = { y -> scope.launch { scroll.animateScrollTo((fieldTop + y - with(density) { 96.dp.toPx() }).roundToInt().coerceAtLeast(0)) } },
                    onOpenDrawing = { line, d -> drawing = line to d },
                    onViewPhoto = { viewing = it },
                    onOpenLink = { title ->
                        val target = resolveLink(title, vm.linkTargets.value)
                        if (target == null) Toast.makeText(context, context.getString(R.string.editor_note_not_found), Toast.LENGTH_SHORT).show() else onOpenNote(target)
                    },
                    onPreviewLink = { title ->
                        val target = resolveLink(title, vm.linkTargets.value)
                        if (target == null) Toast.makeText(context, context.getString(R.string.editor_note_not_found), Toast.LENGTH_SHORT).show()
                        else { haptic.performHapticFeedback(HapticFeedbackType.LongPress); peek = target }
                    },
                    underTitle = todoHeader,
                ) }
                // The note's tags; tapping one opens that tag's list.
                val noteTags = remember(vm.doc.text) { findTags(vm.doc.text).map { it.second }.distinctBy(::tagKey) }
                if (noteTags.isNotEmpty() && !vm.focused && !vm.trashed) { // tag lists leave out deleted notes
                    FlowRow(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        noteTags.forEach { name ->
                            Box(
                                Modifier.height(28.dp).clip(JShape.md).background(jc.surface)
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
    if (stats) WritingStatsDialog(vm) { stats = false }
    if (sortingList) SortListDialog(vm.anyDue, onPick = { sortingList = false; vm.sortTasks(it); toast(context.getString(R.string.editor_sorted_undo, context.getString(it.labelRes).lowercase())) }) { sortingList = false }
    dueFor?.let { line ->
        DueDateDialog(vm.doc.lines.getOrNull(line)?.due, onPick = { vm.setDue(line, it); dueFor = null }) { dueFor = null }
    }
    if (history) VersionHistory(vm) { history = false }
    peek?.let { n -> LinkedNotePreview(n, onOpen = { peek = null; onOpenNote(n) }, onDismiss = { peek = null }) }
    if (asking) {
        // Text selected when AI was opened: its actions can work on just that. (Not tied to focus: the ⋯ menu
        // takes it, but the selection is still what was picked.)
        val picked = remember { vm.value.selection.takeIf { !it.collapsed } }
        val pickedRaw = remember { picked?.let { vm.value.text.substring(it.min, it.max) } }
        val pickedText = remember { pickedRaw?.replace(com.margin.app.data.ATTACHMENT_CHAR.toString(), "")?.takeIf { it.isNotBlank() } }
        // Only plain text in one part (title or body) can be replaced: a photo, file or table in it would be lost.
        val replaceable = remember {
            picked != null && run {
                val a = vm.doc.lineAt(picked.min); val z = vm.doc.lineAt(picked.max)
                (a == 0) == (z == 0) && (a..z).none { vm.doc.lines.getOrNull(it)?.isObject == true }
            }
        }
        AiSheet(
        vm.ai, noteText = shareText(vm.doc.toBlocks()),
        selection = pickedText,
        onReplaceSelection = if (vm.readOnly || picked == null || !replaceable) null else ({ answer ->
            // The note may have changed while the answer came: replace only if that text is still there.
            if (picked.max <= vm.value.text.length && vm.value.text.substring(picked.min, picked.max) == pickedRaw) {
                vm.replaceSelection(picked, plainAnswer(answer)); asking = false
            } else toast(context.getString(R.string.editor_the_note_changed_so_the))
        }),
        onTitle = if (vm.readOnly) null else ({ vm.setTitle(it); asking = false }),
        onChecklist = if (vm.readOnly) null else ({ vm.addChecklist(it); asking = false }),
        // Tags go in as a plain line at the end, so they work as the note's tags.
        onTags = if (vm.readOnly) null else ({ vm.insertText(it, afterLine = vm.doc.lines.lastIndex); asking = false }),
        // Added at the end of the note, not after wherever the cursor last was.
        onInsert = if (vm.readOnly) null else ({ vm.insertText(com.margin.app.data.untagged(plainAnswer(it)), afterLine = vm.doc.lines.lastIndex, ai = true); asking = false }),
        // Only a note of plain text can be replaced: its photos and files aren't sent, so they'd be lost.
        onReplace = if (vm.readOnly || !vm.textOnly) null else ({ vm.replaceBody(plainAnswer(it)); asking = false }),
        onClose = { asking = false },
    )
    }
    if (linked) {
        val notes = vm.linkedNotes(targets)
        MarginDialog(
            onDismissRequest = { linked = false },
            title = { Text(stringResource(R.string.editor_linked_notes_2)) },
            text = {
                if (notes.isEmpty()) {
                    Text(stringResource(R.string.editor_no_note_links_here_yet), style = JType.secondary, color = jc.muted)
                } else Column {
                    notes.forEach { n ->
                        Text(
                            n.displayTitle, Modifier.fillMaxWidth().clickable { linked = false; onOpenNote(n) }.padding(vertical = 12.dp),
                            style = JType.row, color = jc.accent, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { linked = false }) { Text(stringResource(R.string.common_close)) } },
        )
    }
    if (moving) {
        MoveDialog(
            folders, currentFolderId = vm.folderId, subject = vm.doc.texts.first().ifBlank { stringResource(R.string.editor_new_note) },
            onDismiss = { moving = false },
        ) { vm.moveTo(it); moving = false }
    }
    passwordFor?.let { mode ->
        var old by remember { mutableStateOf(false) }
        val submit: suspend (CharArray) -> String? = { pw ->
            if (vm.enterPassword(pw)) { passwordFor = null; vm.lock(); null } else if (old) context.getString(R.string.editor_wrong_password) else context.getString(R.string.editor_wrong_passcode)
        }
        if (old) OldPasswordDialog(onDismiss = { passwordFor = null }, onSubmit = submit)
        else PasscodeDialog(mode, onDismiss = { passwordFor = null }, onOldPassword = if (mode == PasswordMode.ENTER) ({ old = true }) else null, onSubmit = submit)
    }
}

/** Find in note: the query, "2 of 5", previous/next and Done. */
@Composable
internal fun FindBar(query: String, count: Int, at: Int, onChange: (String) -> Unit, onStep: (Int) -> Unit, onClose: () -> Unit) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { requester.requestFocus() } }
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SearchField(query, onChange, stringResource(R.string.editor_find_in_note), Modifier.weight(1f).focusRequester(requester))
        Text(if (query.isBlank()) "" else if (count == 0) stringResource(R.string.editor_none) else stringResource(R.string.editor_of, at + 1, count), Modifier.padding(horizontal = 6.dp), style = JType.small, color = jc.muted)
        IconAction("M6 15l6-6 6 6", stringResource(R.string.editor_previous_match), { onStep(-1) }, enabled = count > 1)
        IconAction("M6 9l6 6 6-6", stringResource(R.string.editor_next_match), { onStep(1) }, enabled = count > 1)
        TextAction(stringResource(R.string.common_done), onClose, bold = true)
    }
}

/** Find in note, second row: the new text, then Replace (the current match) and All. */
@Composable
internal fun ReplaceBar(text: String, count: Int, onChange: (String) -> Unit, onReplace: () -> Unit, onReplaceAll: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier.weight(1f).height(40.dp).clip(JShape.sm).background(jc.surface).padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (text.isEmpty()) Text(stringResource(R.string.editor_replace_with), style = JType.row, color = jc.muted, maxLines = 1)
            BasicTextField(
                text, onChange, singleLine = true, textStyle = JType.row.copy(color = jc.ink),
                cursorBrush = SolidColor(jc.accent), modifier = Modifier.fillMaxWidth().semantics { contentDescription = context.getString(R.string.editor_replace_with) },
            )
        }
        TextAction(stringResource(R.string.editor_replace), onReplace, enabled = count > 0)
        TextAction(stringResource(R.string.editor_all), onReplaceAll, enabled = count > 0)
    }
}

/** Note info: dates, folder and size. */
@Composable
internal fun NoteInfoDialog(vm: EditorViewModel, folderName: String?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val blocks = vm.doc.toBlocks()
    val body = com.margin.app.data.bodyText(blocks)
    val words = com.margin.app.data.countWords(body)
    val attachments = blocks.count { it.attachment != null }
    val rows = listOfNotNull(
        stringResource(R.string.editor_created) to (vm.createdAt?.let { fullTime(context, it) } ?: stringResource(R.string.editor_not_saved_yet)),
        stringResource(R.string.editor_edited) to (vm.editedAt?.let { fullTime(context, it) } ?: stringResource(R.string.editor_now)),
        folderName?.let { stringResource(R.string.editor_folder) to it },
        stringResource(R.string.editor_words) to "$words",
        stringResource(R.string.editor_characters) to "${body.count { !it.isWhitespace() }}",
        stringResource(R.string.editor_reading_time) to readingText(words),
        (stringResource(R.string.editor_attachments) to "$attachments").takeIf { attachments > 0 },
        (stringResource(R.string.editor_locked) to stringResource(R.string.common_yes)).takeIf { vm.locked },
    )
    MarginDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_note_info)) },
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
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
    )
}

@Composable
internal fun passwordColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = jc.accent, unfocusedBorderColor = jc.line, focusedLabelColor = jc.accent, cursorColor = jc.accent,
)

/** The title of a locked note, plus the way in: the passcode (or an old password), or a fingerprint if that's set up. */
@Composable
internal fun LockedNote(vm: EditorViewModel, fingerprint: String?, meta: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
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
                Biometric.Result.Cancelled -> pad = true // "Use password" on the prompt: show the keypad, as the journal does
                Biometric.Result.Invalidated -> vm.unlockError = context.getString(R.string.editor_fingerprints_changed_use_your_passcode)
                is Biometric.Result.Failed -> vm.unlockError = r.message
            }
        }
    }
    Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(jc.surface), contentAlignment = Alignment.Center) {
            JIcon(Ico.LOCK, jc.ink, size = 32.dp, stroke = 1.6f)
        }
        Text(title.ifBlank { stringResource(R.string.editor_new_note) }, Modifier.padding(top = 24.dp, bottom = 8.dp), style = JType.heading.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold), color = jc.ink, textAlign = TextAlign.Center)
        Text(stringResource(R.string.editor_this_note_is_locked), Modifier.padding(bottom = 4.dp), style = JType.secondary.copy(fontSize = 16.sp, lineHeight = 23.sp), color = jc.muted)
        if (meta.isNotEmpty()) Text(meta, style = JType.small, color = jc.muted, textAlign = TextAlign.Center)
        when {
            !pad -> {
                PrimaryButton(stringResource(R.string.editor_view_note), viewNote, Modifier.fillMaxWidth().padding(top = 32.dp), enabled = !vm.unlocking)
                vm.unlockError?.let { Text(it, Modifier.padding(top = 8.dp), style = JType.small, color = jc.danger, textAlign = TextAlign.Center) }
                Box(Modifier.padding(top = 8.dp)) { TextAction(stringResource(R.string.editor_enter_passcode_instead), { pad = true; vm.unlockError = null }) }
            }
            old -> {
                OutlinedTextField(
                    password, { password = it; vm.unlockError = null }, singleLine = true, label = { Text(stringResource(R.string.editor_old_notes_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = vm.unlockError != null,
                    supportingText = vm.unlockError?.let { { Text(it) } },
                    shape = JShape.sm, colors = passwordColors(),
                    modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
                )
                PrimaryButton(
                    if (vm.unlocking) stringResource(R.string.editor_unlocking) else stringResource(R.string.editor_unlock),
                    { vm.unlock(password.toCharArray()); password = "" },
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    enabled = password.isNotEmpty() && !vm.unlocking,
                )
            }
            else -> PasscodePad(vm.unlockError, enabled = !vm.unlocking, onComplete = { vm.unlock(it.toCharArray()) }, modifier = Modifier.padding(top = 28.dp))
        }
        if (pad) {
            Box(Modifier.padding(top = 8.dp)) {
                TextAction(if (old) stringResource(R.string.editor_use_passcode) else stringResource(R.string.editor_use_old_password), { old = !old; vm.unlockError = null })
            }
            if (canBio) Box { TextAction(stringResource(R.string.editor_use_fingerprint), { pad = false; old = false; vm.unlockError = null }) }
        }
        Text(
            stringResource(R.string.editor_locked_notes_are_encrypted_on),
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
internal fun OldPasswordDialog(onDismiss: () -> Unit, onSubmit: suspend (CharArray) -> String?) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    MarginDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_enter_your_old_notes_password)) },
        text = {
            OutlinedTextField(
                password, { password = it; error = null }, singleLine = true, label = { Text(stringResource(R.string.editor_password)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                isError = error != null, supportingText = error?.let { { Text(it) } },
                shape = JShape.sm, colors = passwordColors(),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    busy = true
                    error = onSubmit(password.toCharArray())
                    busy = false
                }
            }, enabled = password.isNotEmpty() && !busy) { Text(if (busy) stringResource(R.string.editor_checking) else stringResource(R.string.common_ok), fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/** Known tags that finish the one being typed; tapping one completes it. */
@Composable
internal fun TagSuggestions(tags: List<TagCount>, onPick: (String) -> Unit) {
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
internal fun LinkSuggestions(notes: List<NoteEntity>, typed: String, folders: List<FolderRow>, onPick: (String) -> Unit) {
    if (notes.isEmpty()) return
    val context = LocalContext.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).clip(JShape.md)
            .border(1.dp, jc.line, JShape.md).background(jc.bg),
    ) {
        Text(stringResource(R.string.editor_link_to_note), Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 6.dp), style = JType.section.copy(fontSize = 12.sp), color = jc.muted)
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
                Text(title, style = JType.itemTitle, color = jc.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(folder, listTime(context, note.lastEdited)).joinToString(" · "), style = JType.small, color = jc.muted, maxLines = 1)
            }
        }
    }
}

/** "Aa" opens the format sheet; the checklist button turns the current line(s) into checklist items. */
@Composable
internal fun EditorToolbar(
    vm: EditorViewModel, formatting: Boolean, attaching: Boolean, onFormat: () -> Unit, onHideKeyboard: () -> Unit, onAttach: () -> Unit, onDraw: () -> Unit,
    onNew: () -> Unit, onFont: () -> Unit, onColor: () -> Unit, onAi: (() -> Unit)? = null,
) {
    val focusedType = if (vm.focused) vm.doc.lines.getOrNull(vm.cursorLine)?.type else null
    val canFormat = focusedType != null && focusedType != BlockType.TITLE
    // A floating rounded bar: tools share the width evenly, a tool that is on gets a tinted circle.
    @Composable fun RowScope.Slot(on: Boolean = false, content: @Composable () -> Unit) = Box(
        Modifier.weight(1f), contentAlignment = Alignment.Center,
    ) { Box(Modifier.clip(CircleShape).background(if (on) jc.selected else Color.Transparent)) { content() } }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)
            .shadow(JElevation.card, JShape.pill).clip(JShape.pill).background(jc.card).border(1.dp, jc.line, JShape.pill)
            .height(52.dp).padding(start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The tools used on most notes stay in the bar; font, note links and the math keyboard are under More.
        // Tools are plain ink; only one that is on turns amber, on its tinted circle.
        val accent = jc.accent; val ink = jc.ink
        fun tint(on: Boolean) = if (on) accent else ink
        Slot(formatting) { GlyphAction(stringResource(R.string.editor_aa), stringResource(R.string.editor_text_style), onFormat, JType.rowTitle.copy(fontSize = 18.sp), color = tint(formatting)) }
        val checking = focusedType == BlockType.CHECK
        Slot(checking) { IconAction(Ico.CHECKLIST, stringResource(R.string.editor_checklist), { vm.setType(BlockType.CHECK) }, color = tint(checking), enabled = canFormat, size = 20.dp) }
        Slot(attaching) { IconAction(Ico.ATTACH, stringResource(R.string.editor_attach), onAttach, color = tint(attaching), size = 20.dp) }
        Slot { IconAction(Ico.PEN, stringResource(R.string.editor_draw), onDraw, color = jc.ink, size = 20.dp) }
        // AI: tinted while text is selected, since it then works on the selection.
        onAi?.let { ai ->
            val picked = vm.focused && !vm.value.selection.collapsed
            Slot(picked) { IconAction(Ico.SPARKLE, if (picked) stringResource(R.string.editor_ask_ai_about_the_selection) else stringResource(R.string.editor_ask_ai_2), ai, color = tint(picked), size = 20.dp) }
        }
        // While the math keyboard is up, its button stays in the bar so it can be put away.
        if (vm.focused && vm.mathKeys) Slot(true) {
            GlyphAction("π", stringResource(R.string.editor_phone_keyboard), { vm.mathKeys = false }, JType.rowTitle.copy(fontSize = 20.sp))
        }
        Slot { ToolbarMore(vm, canFormat, onFont, onColor) }
        if (vm.focused) Slot { IconAction(Ico.HIDE_KEYBOARD, stringResource(R.string.editor_hide_keyboard), onHideKeyboard, color = jc.muted, size = 20.dp) }
        else Slot { IconAction(Ico.COMPOSE, stringResource(R.string.editor_new_note_2), onNew, color = jc.ink, size = 20.dp) }
    }
}

/** The toolbar's More button: the rarer tools. The menu doesn't take focus, so the keyboard and cursor stay. */
@Composable
internal fun ToolbarMore(vm: EditorViewModel, canFormat: Boolean, onFont: () -> Unit, onColor: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconAction(Ico.MORE, stringResource(R.string.editor_more_tools), { open = true }, color = jc.ink, fill = true, size = 20.dp)
        // Opens without taking focus from the note, so the keyboard stays up.
        JMenu(expanded = open, onDismissRequest = { open = false }, width = 240.dp, focusable = false) {
            @Composable
            fun tool(text: String, icon: @Composable () -> Unit, enabled: Boolean = true, onClick: () -> Unit) = DropdownMenuItem(
                text = { Text(text, style = JType.row, color = if (enabled) jc.ink else jc.muted) },
                leadingIcon = icon, enabled = enabled, onClick = { open = false; onClick() },
            )
            tool(stringResource(R.string.editor_font_item, stringResource(vm.font.labelRes)), { JIcon(Ico.TEXT, jc.ink, size = 20.dp, stroke = 2f) }, enabled = !vm.readOnly && !vm.needsUnlock, onClick = onFont)
            tool(stringResource(R.string.editor_colour_item, stringResource(vm.color.labelRes)), {
                Box(Modifier.size(18.dp).clip(CircleShape).background(if (vm.color == com.margin.app.data.NoteColor.NONE) jc.card else Color(vm.color.argb)).border(1.5.dp, jc.ink, CircleShape))
            }, enabled = !vm.trashed && !vm.needsUnlock, onClick = onColor)
            if (vm.linksOn) tool(stringResource(R.string.editor_link_to_a_note), { JIcon(Ico.LINK, jc.ink, size = 20.dp, stroke = 2f) }, enabled = canFormat, onClick = vm::startLink)
            if (vm.focused && !vm.mathKeys) tool(stringResource(R.string.editor_math_keyboard), { Text("π", style = JType.rowTitle.copy(fontSize = 18.sp), color = jc.ink) }) { vm.mathKeys = true }
        }
    }
}

/** The design's bottom sheet look, shared by Format and Add to note: grab handle, bold title, round close button. */
@Composable
internal fun SheetPanel(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(
        Modifier.fillMaxWidth().clip(JShape.sheet)
            .background(jc.bg).border(1.dp, jc.line, JShape.sheet)
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).size(36.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(jc.switchOff))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = JType.subheading.copy(fontWeight = FontWeight.Bold), color = jc.ink)
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(jc.surface).clickable(role = Role.Button, onClick = onClose)
                    .semantics { contentDescription = context.getString(R.string.common_close) },
                contentAlignment = Alignment.Center,
            ) { JIcon(Ico.CLOSE, jc.muted, size = 14.dp, stroke = 2.6f) }
        }
        content()
    }
}

/** A clock with an arrow going back. */
internal const val ICON_HISTORY = "M3 12a9 9 0 1 0 3-6.7L3 8M3 3v5h5M12 7v5l3 2"

/**
 * Version history: the note as it was at the end of each editing session (kept on this phone, up to 50).
 * Tap one to read it; Restore puts it back as an edit, so Undo returns to the current text.
 */
@Composable
internal fun VersionHistory(vm: EditorViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val versions by remember { vm.versions() }.collectAsState(initial = null)
    val current = vm.doc.toBlocks()
    // The newest version is usually the text as it is now; it's not offered.
    val older = versions?.filter { it.blocks.map { b -> b.plainText to b.attachment } != current.map { b -> b.plainText to b.attachment } }
    var open by remember { mutableStateOf<com.margin.app.data.NoteVersion?>(null) }
    Dialog(onDismissRequest = { if (open != null) open = null else onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        FullWindow()
        Column(Modifier.fillMaxSize().background(jc.bg).systemBarsPadding()) {
            val v = open
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button) { if (v != null) open = null else onClose() }
                        .semantics { contentDescription = if (v != null) context.getString(R.string.editor_back_to_versions) else context.getString(R.string.common_close) },
                    contentAlignment = Alignment.Center,
                ) { JIcon(if (v != null) Ico.BACK else Ico.CLOSE, jc.accent, size = 20.dp, stroke = 2.2f) }
                Text(
                    if (v != null) versionTime(context, v.savedAt) else stringResource(R.string.editor_version_history),
                    Modifier.weight(1f).padding(start = 4.dp), style = JType.rowTitle, color = jc.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (v != null && !vm.readOnly) TextButton(onClick = { vm.restoreVersion(v.blocks); onClose() }) {
                    Text(stringResource(R.string.common_restore), fontWeight = FontWeight.SemiBold, color = jc.accent)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(jc.line))
            when {
                v != null -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    v.blocks.forEachIndexed { i, b ->
                        val text = b.attachment?.let { "[" + it.name + "]" } ?: b.table?.let { stringResource(R.string.editor_table) }
                            ?: (if (b.type == com.margin.app.data.BlockType.CHECK) (if (b.checked) "☑ " else "☐ ") else "") + b.text
                        Text(
                            text,
                            style = if (i == 0) JType.rowTitle.copy(fontSize = 22.sp) else JType.body,
                            color = if (b.attachment != null || b.table != null) jc.muted else jc.ink,
                        )
                    }
                }
                older == null -> Unit
                older.isEmpty() -> Column(
                    Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                ) {
                    JIcon(ICON_HISTORY, jc.muted, size = 40.dp, stroke = 1.6f)
                    Text(stringResource(R.string.editor_no_earlier_versions_yet), style = JType.rowTitle, color = jc.ink)
                    Text(
                        stringResource(R.string.editor_a_version_is_kept_each),
                        style = JType.secondary, color = jc.muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(older, key = { it.id }) { ver ->
                        val preview = remember(ver) {
                            ver.blocks.drop(1).map { it.plainText }.filter { it.isNotBlank() }.take(2).joinToString(" · ").ifEmpty { context.getString(R.string.editor_no_additional_text) }
                        }
                        Column(
                            Modifier.fillMaxWidth().clickable { open = ver }.padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(versionTime(context, ver.savedAt), style = JType.itemTitle, color = jc.ink)
                            Text(
                                (ver.blocks.firstOrNull()?.text?.ifBlank { null }?.let { "$it · " } ?: "") + preview,
                                style = JType.secondary, color = jc.muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Box(Modifier.padding(start = 20.dp).fillMaxWidth().height(1.dp).background(jc.line))
                    }
                }
            }
        }
    }
}

/** "Today, 4:23 pm", "Yesterday, 9:05 am" or "3 Nov 2026, 9:05 am". */
internal fun versionTime(context: android.content.Context, at: Long): String {
    val time = android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(at))
    val day = when {
        android.text.format.DateUtils.isToday(at) -> context.getString(R.string.common_today)
        android.text.format.DateUtils.isToday(at + 86_400_000L) -> context.getString(R.string.common_yesterday)
        else -> java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(at))
    }
    return "$day, $time"
}

/** A page between scan corners. */
internal const val ICON_SCAN_DOC = "M3 8V4h4M17 4h4v4M21 16v4h-4M7 20H3v-4M8 7h8v10H8zM10 10h4M10 13h4"

/** The activity behind a Compose context (it can be wrapped, e.g. by a theme). */
internal tailrec fun android.content.Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** "Add to note": the attachment kinds Margin has, as tiles. */
@Composable
internal fun AttachSheet(
    onClose: () -> Unit, onFile: () -> Unit, onVoice: () -> Unit, onDraw: () -> Unit, onCamera: () -> Unit, onLink: () -> Unit,
    onScan: () -> Unit, onTable: () -> Unit, onPdf: () -> Unit, onScanDoc: () -> Unit, onTemplate: (NoteTemplate) -> Unit,
    saved: List<NoteEntity>, onSaved: (String) -> Unit, onEditSaved: (NoteEntity) -> Unit, onDeleteSaved: (String) -> Unit,
) {
    /** The saved template whose Edit / Delete menu is open (long-press). */
    var menuFor by remember { mutableStateOf<String?>(null) }
    // What's already out there (camera, files, scans), then what you make in the note.
    val groups = listOf(
        stringResource(R.string.editor_capture) to listOf(
            Triple(stringResource(R.string.editor_camera), Ico.CAMERA, onCamera), Triple(stringResource(R.string.editor_photo_file), Ico.FILE, onFile), Triple(stringResource(R.string.editor_pdf), ICON_PDF, onPdf),
            Triple(stringResource(R.string.editor_voice), Ico.MIC, onVoice), Triple(stringResource(R.string.editor_scan_text), Ico.SCAN, onScan), Triple(stringResource(R.string.editor_scan_doc), ICON_SCAN_DOC, onScanDoc),
        ),
        stringResource(R.string.common_create) to listOf(Triple(stringResource(R.string.editor_drawing), Ico.PEN, onDraw), Triple(stringResource(R.string.editor_table_2), ICON_TABLE, onTable), Triple(stringResource(R.string.editor_web_link), Ico.GLOBE, onLink)),
        stringResource(R.string.editor_templates) to NoteTemplate.entries.map { t -> Triple(stringResource(t.labelRes), templateIcon(t)) { onTemplate(t) } },
    ) + listOfNotNull(saved.takeIf { it.isNotEmpty() }?.let { list ->
        stringResource(R.string.editor_your_templates_hold_to_edit) to list.map { n -> Triple(n.title.ifBlank { stringResource(R.string.common_untitled) }, ICON_TEMPLATE) { onSaved(n.id) } }
    })
    SheetPanel(stringResource(R.string.editor_add_to_note), onClose) {
      // Scrolls once saved templates make it taller than the screen allows.
      Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        groups.forEach { (name, tiles) ->
            val savedGroup = name == stringResource(R.string.editor_your_templates_hold_to_edit)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(name.uppercase(), style = JType.section, color = jc.muted)
                // Four to a row: a quiet icon on a rounded square, the name under it.
                tiles.chunked(4).forEachIndexed { r, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEachIndexed { c, (label, icon, onClick) ->
                            val note = if (savedGroup) saved.getOrNull(r * 4 + c) else null
                            Box(Modifier.weight(1f)) {
                                Column(
                                    Modifier.fillMaxWidth().clip(JShape.sm)
                                        .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = note?.let { n -> { menuFor = n.id } })
                                        .padding(vertical = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Box(
                                        Modifier.size(56.dp).clip(JShape.md).background(jc.surface),
                                        contentAlignment = Alignment.Center,
                                    ) { JIcon(icon, jc.ink, size = 24.dp, stroke = 1.8f) }
                                    Text(label, style = JType.caption.copy(fontWeight = FontWeight.Medium), color = jc.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (note != null) JMenu(menuFor == note.id, { menuFor = null }, width = 220.dp) {
                                    DropdownMenuItem({ Text(stringResource(R.string.editor_edit_template)) }, { menuFor = null; onEditSaved(note) })
                                    DropdownMenuItem({ Text(stringResource(R.string.editor_delete_template), color = jc.danger) }, { menuFor = null; onDeleteSaved(note.id) })
                                }
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
      }
    }
}

/**
 * On a sync conflict copy: both devices changed "[title]" before syncing, and this is the other version.
 * Keep this one (it replaces the original), keep the other (this copy is deleted) or keep both.
 */
@Composable
internal fun ConflictChooser(title: String, vm: EditorViewModel, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 14.dp).clip(JShape.md).background(jc.card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(Ico.SYNC, jc.accent, size = 36.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.editor_two_versions_of_this_note), style = JType.rowTitle, color = jc.ink)
                Text(
                    stringResource(R.string.editor_was_changed_on_two_devices, title),
                    style = JType.secondary, color = jc.muted,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.weight(1f).height(44.dp).clip(JShape.sm).background(jc.ink).clickable(role = Role.Button) { vm.keepThisVersion(onBack) },
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.editor_keep_this_one), style = JType.row, color = jc.bg, fontWeight = FontWeight.SemiBold) }
            Box(
                Modifier.weight(1f).height(44.dp).clip(JShape.sm).background(jc.surface).clickable(role = Role.Button) { vm.keepOtherVersion(onBack) },
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.editor_keep_the_other), style = JType.row, color = jc.ink, fontWeight = FontWeight.SemiBold) }
        }
        var comparing by remember { mutableStateOf(false) }
        Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextAction(stringResource(R.string.editor_compare), { comparing = true })
            TextAction(stringResource(R.string.editor_keep_both), vm::keepBothVersions)
        }
        if (comparing) vm.conflictWith?.let { other -> CompareVersions(vm, other, onBack) { comparing = false } }
    }
}

/** How one line of a comparison reads: in both versions, only in this one, or only in the other. */
internal enum class DiffSide { BOTH, THIS, OTHER }

/** Lines of two texts in order, each marked by where it appears (a longest-common-subsequence line diff). */
internal fun lineDiff(mine: List<String>, theirs: List<String>): List<Pair<DiffSide, String>> {
    // shortcut: O(n·m) table; a note long enough for this to hurt just shows both versions whole.
    if (mine.size.toLong() * theirs.size > 4_000_000) return mine.map { DiffSide.THIS to it } + theirs.map { DiffSide.OTHER to it }
    val n = mine.size; val m = theirs.size
    val t = Array(n + 1) { IntArray(m + 1) }
    for (i in n - 1 downTo 0) for (j in m - 1 downTo 0)
        t[i][j] = if (mine[i] == theirs[j]) t[i + 1][j + 1] + 1 else maxOf(t[i + 1][j], t[i][j + 1])
    val out = ArrayList<Pair<DiffSide, String>>()
    var i = 0; var j = 0
    while (i < n || j < m) when {
        i < n && j < m && mine[i] == theirs[j] -> { out += DiffSide.BOTH to mine[i]; i++; j++ }
        j >= m || (i < n && t[i + 1][j] >= t[i][j + 1]) -> { out += DiffSide.THIS to mine[i]; i++ }
        else -> { out += DiffSide.OTHER to theirs[j]; j++ }
    }
    return out
}

/** A note's body as lines to compare: tasks keep their tick, files and tables show by name. */
private fun compareLines(blocks: List<com.margin.app.data.Block>): List<String> = blocks.drop(1).flatMap { b ->
    b.attachment?.let { listOf("[" + it.name + "]") } ?: b.table?.let { listOf("[Table]") }
        ?: b.text.lines().map { (if (b.type == com.margin.app.data.BlockType.CHECK) (if (b.checked) "☑ " else "☐ ") else "") + it }
}

/** Both versions of a conflicted note as one list: lines only here, lines only in the other, and what they share. */
@Composable
private fun CompareVersions(vm: EditorViewModel, other: com.margin.app.data.NoteEntity, onBack: () -> Unit, onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val diff = remember(vm.doc, other) { lineDiff(compareLines(vm.doc.toBlocks()), compareLines(other.blocks)) }
    val mineTint = jc.accent
    val otherTint = jc.danger
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        FullWindow()
        Column(Modifier.fillMaxSize().background(jc.bg).systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClose).semantics { contentDescription = context.getString(R.string.common_close) },
                    contentAlignment = Alignment.Center,
                ) { JIcon(Ico.CLOSE, jc.ink, size = 20.dp, stroke = 2.2f) }
                Text(stringResource(R.string.editor_compare_versions), Modifier.weight(1f).padding(start = 4.dp), style = JType.rowTitle, color = jc.ink)
            }
            Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                @Composable fun key(color: androidx.compose.ui.graphics.Color, label: String) = Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                    Text(label, style = JType.small, color = jc.muted)
                }
                key(mineTint, stringResource(R.string.editor_only_in_this_one))
                key(otherTint, stringResource(R.string.editor_only_in_the_other))
            }
            Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(1.dp).background(jc.line))
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(vertical = 12.dp)) {
                if (diff.none { it.first != DiffSide.BOTH }) item {
                    Text(stringResource(R.string.editor_the_text_is_the_same), Modifier.padding(20.dp), style = JType.secondary, color = jc.muted)
                }
                items(diff.size) { k ->
                    val (side, line) = diff[k]
                    val tint = when (side) { DiffSide.THIS -> mineTint; DiffSide.OTHER -> otherTint; DiffSide.BOTH -> null }
                    Row(
                        Modifier.fillMaxWidth().background(tint?.copy(alpha = 0.12f) ?: androidx.compose.ui.graphics.Color.Transparent)
                            .padding(horizontal = 20.dp, vertical = 6.dp)
                            .semantics(mergeDescendants = true) {
                                contentDescription = when (side) { DiffSide.THIS -> context.getString(R.string.editor_only_in_this_one_line, line); DiffSide.OTHER -> context.getString(R.string.editor_only_in_the_other_line, line); DiffSide.BOTH -> line }
                            },
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(when (side) { DiffSide.THIS -> "+"; DiffSide.OTHER -> "−"; DiffSide.BOTH -> " " }, Modifier.width(12.dp), style = JType.body, color = tint ?: jc.muted)
                        Text(line.ifEmpty { " " }, style = JType.body, color = if (tint == null) jc.muted else jc.ink)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.weight(1f).height(48.dp).clip(JShape.sm).background(jc.ink).clickable(role = Role.Button) { onClose(); vm.keepThisVersion(onBack) },
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.editor_keep_this_one), style = JType.row, color = jc.bg, fontWeight = FontWeight.SemiBold) }
                Box(
                    Modifier.weight(1f).height(48.dp).clip(JShape.sm).background(jc.surface).clickable(role = Role.Button) { onClose(); vm.keepOtherVersion(onBack) },
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.editor_keep_the_other), style = JType.row, color = jc.ink, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

/** Share note: what's going out (title, size), the ways to send a copy, then copy and print. */
@Composable
internal fun ShareSheet(
    title: String, words: Int, onClose: () -> Unit, onText: () -> Unit, onCopy: () -> Unit, onFile: (pdf: Boolean, print: Boolean) -> Unit,
    onCard: () -> Unit,
) {
    SheetPanel(stringResource(R.string.editor_share_note), onClose) {
        Row(
            Modifier.fillMaxWidth().clip(JShape.md).border(1.dp, jc.line, JShape.md).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconTile(Ico.TEXT)
            Column(Modifier.weight(1f)) {
                Text(title.ifBlank { stringResource(R.string.editor_new_note) }, style = JType.rowTitle, color = jc.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(pluralStringResource(R.plurals.common_words, words, words), style = JType.small, color = jc.muted)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.editor_send_a_copy_as), style = JType.section, color = jc.muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple(stringResource(R.string.editor_image), Ico.CAMERA, onCard),
                    Triple(stringResource(R.string.editor_text), Ico.TEXT, onText),
                    Triple(stringResource(R.string.editor_pdf), ICON_PDF) { onFile(true, false) },
                    Triple(stringResource(R.string.editor_markdown), Ico.MARKDOWN) { onFile(false, false) },
                ).forEach { (label, icon, onClick) ->
                    Column(
                        Modifier.weight(1f).height(92.dp).clip(JShape.md).background(jc.surface)
                            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
                    ) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(jc.accent.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
                            JIcon(icon, jc.accent, size = 21.dp, stroke = 1.9f)
                        }
                        Text(label, style = JType.small.copy(fontWeight = FontWeight.Medium), color = jc.ink, maxLines = 1)
                    }
                }
            }
        }
        Column(Modifier.clip(JShape.md).background(jc.surface)) {
            listOf(Triple(stringResource(R.string.editor_copy_text), Ico.COPY, onCopy), Triple(stringResource(R.string.editor_print), Ico.PRINT) { onFile(true, true) }).forEachIndexed { i, (label, icon, onClick) ->
                if (i > 0) HorizontalDivider(Modifier.padding(start = 58.dp), color = jc.line)
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    IconTile(icon)
                    Text(label, Modifier.weight(1f), style = JType.row, color = jc.ink)
                }
            }
        }
    }
}

/** Web link: the address, and a title that's read from the page when left empty. [onAdd] reports an error or null. */
@Composable
internal fun WebLinkDialog(onDismiss: () -> Unit, onAdd: (String, String, (String?) -> Unit) -> Unit) {
    var url by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    MarginDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_add_web_link)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    url, { url = it; error = null }, label = { Text(stringResource(R.string.editor_address)) }, singleLine = true, isError = error != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.editor_title_optional)) }, singleLine = true)
                Text(error ?: if (busy) stringResource(R.string.editor_getting_the_page_title) else stringResource(R.string.editor_leave_the_title_empty_to), style = JType.small, color = if (error != null) jc.danger else jc.muted)
            }
        },
        confirmButton = {
            TextButton(onClick = { busy = true; onAdd(url, title) { e -> busy = false; error = e } }, enabled = url.isNotBlank() && !busy) {
                Text(stringResource(R.string.common_add), fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/**
 * The note's photos full screen: swipe between them, rotate or remove one. Removing and rotating are
 * normal edits, so Undo brings the photo back.
 */
@Composable
internal fun PhotoViewer(vm: EditorViewModel, startId: String, onClose: () -> Unit, onError: (String) -> Unit) {
    val photos = vm.doc.lines.withIndex().filter { it.value.attachment?.isImage == true }
    if (photos.isEmpty()) { LaunchedEffect(Unit) { onClose() }; return }
    val pager = rememberPagerState(photos.indexOfFirst { it.value.id == startId }.coerceAtLeast(0)) { photos.size }
    var editing by remember { mutableStateOf<String?>(null) }
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
                        ?: Text(if (file.exists()) "" else stringResource(R.string.editor_not_downloaded_yet), color = Color.White, style = JType.secondary)
                }
            }
            val current = photos.getOrNull(pager.currentPage.coerceAtMost(photos.lastIndex))
            Row(
                Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.5f)).statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconAction(Ico.CLOSE, stringResource(R.string.common_close), onClose, color = Color.White)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(current?.value?.attachment?.name.orEmpty(), style = JType.rowTitle, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (photos.size > 1) Text(stringResource(R.string.editor_of, pager.currentPage + 1, photos.size), style = JType.small, color = Color.White.copy(alpha = 0.7f))
                }
                Spacer(Modifier.size(44.dp))
            }
            if (!vm.readOnly && current != null) Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.5f)).navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
            ) {
                IconAction(Ico.EDIT, stringResource(R.string.editor_edit_photo), { editing = current.value.id }, color = Color.White)
                IconAction(Ico.ROTATE, stringResource(R.string.editor_rotate), { vm.rotateImage(current.index, onError) }, color = Color.White)
                // OCR: closes the viewer and shows the text found, to add below the photo.
                val context = LocalContext.current
                IconAction(Ico.SCAN, stringResource(R.string.editor_get_text_from_photo), { onClose(); vm.scanPhoto(current.index, context.applicationContext) }, color = Color.White)
                IconAction(Ico.TRASH, stringResource(R.string.common_remove), { vm.removeAttachment(current.index) }, color = Color.White)
            }
        }
        // Crop, straighten, colours, size: saved as one edit, so Undo brings the photo back as it was.
        editing?.let { id ->
            val line = vm.doc.lines.indexOfFirst { it.id == id }
            val a = vm.doc.lines.getOrNull(line)?.attachment?.takeIf { it.isImage }
            if (a == null) { LaunchedEffect(id) { editing = null }; return@let }
            PhotoEditor(vm.files.file(a), a.name, onCancel = { editing = null }) { edit, done ->
                vm.editImage(vm.doc.lines.indexOfFirst { it.id == id }, edit, onDone = { editing = null }, onError = { onError(it); done() })
            }
        }
    }
}

/**
 * Line styles and lists for the line(s) at the cursor, in a panel above the toolbar. It's part of the
 * screen rather than a separate window, so the note keeps focus (and the keyboard stays up) while it's open.
 */
@Composable
internal fun FormatSheet(vm: EditorViewModel, onClose: () -> Unit) {
    val focusedType = if (vm.focused) vm.doc.lines.getOrNull(vm.cursorLine)?.type else null
    val enabled = focusedType != null && focusedType != BlockType.TITLE
    SheetPanel(stringResource(R.string.editor_format), onClose) {
        if (!enabled) {
            Text(
                if (focusedType == BlockType.TITLE) stringResource(R.string.editor_the_first_line_is_always)
                else stringResource(R.string.editor_tap_a_line_in_the),
                style = JType.small, color = jc.muted,
            )
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple(BlockType.HEADING, stringResource(R.string.editor_heading), JType.heading.copy(fontSize = 19.sp)),
                Triple(BlockType.SUBHEADING, stringResource(R.string.editor_subheading), JType.rowTitle),
                Triple(BlockType.BODY, stringResource(R.string.editor_body), JType.secondary),
                Triple(BlockType.MONOSPACED, stringResource(R.string.editor_mono), JType.secondary.copy(fontFamily = FontFamily.Monospace, fontSize = 14.sp)),
            ).forEach { (type, label, style) ->
                val on = focusedType == type
                Box(
                    Modifier.height(44.dp).clip(JShape.sm)
                        .background(if (on) jc.selected else jc.bg)
                        .border(if (on) 1.5.dp else 1.dp, if (on) jc.accent else jc.line, JShape.sm)
                        .clickable(enabled = enabled, role = Role.Button) { vm.setType(type) }.padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(label, style = style, color = if (enabled) jc.ink else jc.muted) }
            }
        }
        // Character styles work on selected text, the title included.
        val hasSelection = vm.focused && !vm.value.selection.collapsed
        Row(
            Modifier.fillMaxWidth().clip(JShape.sm).background(jc.line),
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
        if (!hasSelection) Text(stringResource(R.string.editor_select_text_to_make_it), style = JType.small, color = jc.muted)
        Row(
            Modifier.fillMaxWidth().clip(JShape.sm).background(jc.line),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            listOf(
                Triple(BlockType.BULLET, Ico.BULLETS, stringResource(R.string.editor_bulleted_list)),
                Triple(BlockType.DASHED, Ico.DASHES, stringResource(R.string.editor_dashed_list)),
                Triple(BlockType.NUMBERED, Ico.NUMBERS, stringResource(R.string.editor_numbered_list)),
                Triple(BlockType.CHECK, Ico.CHECKLIST, stringResource(R.string.editor_checklist)),
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
            Modifier.fillMaxWidth().clip(JShape.sm).background(jc.line),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            listOf(
                Triple(Ico.QUOTE, stringResource(R.string.editor_quote), vm::toggleQuote),
                Triple(Ico.OUTDENT, stringResource(R.string.editor_indent_less)) { vm.indent(-1) },
                Triple(Ico.INDENT, stringResource(R.string.editor_indent_more)) { vm.indent(1) },
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
            Text(stringResource(R.string.editor_highlight_colour), Modifier.weight(1f), style = JType.secondary, color = jc.muted)
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


/** Saves the note's cards to the phone's Pictures/Margin, then says how many went in. */
internal suspend fun saveCards(context: android.content.Context, blocks: List<com.margin.app.data.Block>, date: String, options: CardOptions, photo: java.io.File?, title: String, toast: (String) -> Unit) {
    val saved = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val pages = noteCards(context, blocks, date, options, photo)
            pages.forEachIndexed { i, draw ->
                val bmp = draw()
                val name = safeFileName(title) + (if (pages.size > 1) " ${i + 1}" else "") + ".png"
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                    if (android.os.Build.VERSION.SDK_INT >= 29) {
                        put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/Margin")
                        put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
                    } else {
                        val dir = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES), "Margin").apply { mkdirs() }
                        @Suppress("DEPRECATION") put(android.provider.MediaStore.Images.Media.DATA, java.io.File(dir, name).path)
                    }
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("no uri")
                try {
                    resolver.openOutputStream(uri)!!.use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                } catch (e: Exception) { resolver.delete(uri, null, null); throw e } finally { bmp.recycle() }
                if (android.os.Build.VERSION.SDK_INT >= 29) resolver.update(uri, android.content.ContentValues().apply { put(android.provider.MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            }
            pages.size
        }.getOrNull()
    }
    toast(when (saved) { null -> context.getString(R.string.editor_couldnt_save_the_image); 1 -> context.getString(R.string.editor_saved_to_photos); else -> context.resources.getQuantityString(R.plurals.editor_saved_images, saved, saved) })
}
