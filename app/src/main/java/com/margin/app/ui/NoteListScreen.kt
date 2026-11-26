package com.margin.app.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.margin.app.data.Drawing
import com.margin.app.data.NoteEntity
import com.margin.app.data.colorMark
import com.margin.app.data.isLogin
import com.margin.app.data.NoteSort
import com.margin.app.data.NoteTemplate
import com.margin.app.data.color
import com.margin.app.data.displayTitle
import com.margin.app.data.invoke
import com.margin.app.data.isAudio
import com.margin.app.data.isDirty
import com.margin.app.data.isLocked
import com.margin.app.data.isPdf
import com.margin.app.data.isSmart
import com.margin.app.data.lastEdited
import com.margin.app.data.snippet
import com.margin.app.data.isTracker
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import com.margin.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource

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
    /** Null on the home screen: it shows a Folders button instead of Back. */
    backLabel: String?,
    onFolders: () -> Unit,
    onBack: () -> Unit,
    /** The home screen's date and "today" cards; null elsewhere. */
    glance: TodayGlance? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(folderId) { vm.show(folderId) }
    val filter by vm.filter.collectAsState()
    val hasNotes by vm.hasNotes.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    // Offer undo for the last delete or move. A newer one replaces it; leaving the list drops it.
    LaunchedEffect(vm.undo) {
        val action = vm.undo ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(action.message, actionLabel = context.getString(R.string.common_undo), duration = SnackbarDuration.Short)
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
    val canCreate = !isTrash && folderId != PINNED_ID && folderId != ARCHIVE_ID && !folderId.startsWith(TAG_PREFIX) && folder?.isSmart != true
    val keepDays by vm.keepDeletedDays.collectAsState()
    var confirmEmpty by remember { mutableStateOf(false) }
    var arranging by remember { mutableStateOf(false) }
    if (arranging) ArrangeNotesDialog(notes.manualOrder(), onDone = { vm.arrange(it) }, onDismiss = { arranging = false })
    var confirmDelete by remember { mutableStateOf(false) }
    // Locking the selected notes: asks for the notes passcode first unless notes are already unlocked.
    var lockPasscode by remember { mutableStateOf<PasswordMode?>(null) }
    val lockScope = rememberCoroutineScope()
    val lockContext = LocalContext.current
    val lockSelected = {
        vm.lockSelected { n ->
            val msg = if (n == 0) lockContext.getString(R.string.notes_already_locked) else lockContext.resources.getQuantityString(R.plurals.notes_locked_count, n, n)
            android.widget.Toast.makeText(lockContext, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    lockPasscode?.let { mode ->
        var old by remember { mutableStateOf(false) }
        val submit: suspend (CharArray) -> String? = { pw ->
            if (vm.enterPassword(pw)) { lockPasscode = null; lockSelected(); null } else if (old) context.getString(R.string.notes_wrong_password) else context.getString(R.string.notes_wrong_passcode)
        }
        if (old) OldPasswordDialog(onDismiss = { lockPasscode = null }, onSubmit = submit)
        else PasscodeDialog(mode, onDismiss = { lockPasscode = null }, onOldPassword = if (mode == PasswordMode.ENTER) ({ old = true }) else null, onSubmit = submit)
    }
    var picking by remember { mutableStateOf(false) }
    var pickingTemplate by remember { mutableStateOf(false) }
    val savedTemplates by vm.templates.collectAsState()
    if (pickingTemplate) folder?.let { f ->
        TemplatePicker(f.template, savedTemplates, onDismiss = { pickingTemplate = false }) { vm.setFolderTemplate(it); pickingTemplate = false }
    }
    /** Notes being moved: the selection or one swiped note. */
    var moving by remember { mutableStateOf<Set<String>?>(null) }
    val selecting = vm.selected.isNotEmpty()
    androidx.activity.compose.BackHandler(enabled = selecting) { vm.clearSelection() }
    // Notes that left the list (deleted, moved elsewhere) can't stay selected.
    LaunchedEffect(notes) {
        val ids = notes.map { it.id }.toSet()
        vm.selected.filter { it !in ids }.forEach(vm::toggleSelected)
    }

    // The floating New note button, wherever notes can be made; the lists leave room under it.
    val tabRoot = LocalTabRoot.current
    val showNew = canCreate && !selecting && !tabRoot
    val listEnd = if (showNew) 96.dp else 24.dp
    val homeHeader = glance != null && tabRoot && backLabel == null
    val listState = rememberLazyListState()
    val gridState = rememberLazyStaggeredGridState()
    val showGrid = grid && !isTrash && query.isBlank()
    Scaffold(
        containerColor = jc.page,
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                Snackbar(
                    data, shape = JShape.md, containerColor = jc.ink, contentColor = jc.bg, actionColor = jc.bg,
                )
            }
        },
        floatingActionButton = {
            if (showNew) FloatingAction(Ico.COMPOSE, stringResource(R.string.notes_new_note), onNew)
        },
        topBar = {
            if (selecting) NavBar(null, {}, color = jc.page) { TextAction(stringResource(R.string.common_done), vm::clearSelection, bold = true) }
            // Home on its tab has nothing for a bar to hold: its menu sits beside the title instead.
            else if (homeHeader) TabTop()
            else NavBar(
                backLabel, onBack, color = jc.page,
                leading = if (backLabel != null || tabRoot) null else ({
                    // A soft pill, matching the round buttons on the right.
                    Row(
                        Modifier.padding(start = 6.dp).height(40.dp).clip(JShape.pill).background(jc.card)
                            .clickable(role = Role.Button, onClick = onFolders).padding(start = 12.dp, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        JIcon(Ico.FOLDER, jc.accent, size = 20.dp, stroke = 2f)
                        Text(stringResource(R.string.notes_folders), style = JType.row, color = jc.ink, fontWeight = FontWeight.Medium)
                    }
                }),
            ) {
                if (!isTrash && query.isBlank()) {
                    val f = folder
                    if (f != null) SortMenu(
                        f.sortBy, f.sortAscending, grid = grid, onGrid = onGrid, onChange = vm::setSort,
                            manual = if (f.isSmart) null else f.manualOrder, onManual = { vm.setManual(true); arranging = true }, onArrange = { arranging = true }, soft = backLabel == null,
                        templateLabel = if (f.isSmart) null else templateLabel(f.template, savedTemplates), onTemplate = { pickingTemplate = true },
                    )
                    else ViewMenu(grid, onGrid, soft = backLabel == null)
                }
                if (!tabRoot) IconAction(Ico.GEAR, stringResource(R.string.common_settings), onSettings, soft = backLabel == null)
                // Same margin as the Folders pill on the left.
                if (backLabel == null) Spacer(Modifier.width(4.dp))
            }
        },
        bottomBar = {
            when {
                selecting && isTrash -> BottomBar() {
                    val n = vm.selected.size
                    TextAction(stringResource(R.string.notes_recover, n), vm::recoverSelected)
                    Text(stringResource(R.string.notes_selected, n), style = JType.small, color = jc.muted)
                    TextAction(stringResource(R.string.notes_delete, n), { confirmDelete = true }, color = jc.danger)
                }
                selecting -> BottomBar() {
                    val allPinned = notes.filter { it.id in vm.selected }.all { it.isPinned }
                    Row {
                        IconAction(Ico.PIN, if (allPinned) stringResource(R.string.common_unpin) else stringResource(R.string.common_pin), vm::pinSelected, fill = allPinned)
                        IconAction(Ico.MOVE, stringResource(R.string.common_move), { moving = vm.selected })
                        IconAction(Ico.ARCHIVE, if (folderId == ARCHIVE_ID) stringResource(R.string.common_unarchive) else stringResource(R.string.common_archive), vm::archiveSelected)
                    }
                    Text(stringResource(R.string.notes_selected, vm.selected.size), style = JType.small, color = jc.muted)
                    // Same width as the left pair so the count stays centred.
                    Row(Modifier.width(132.dp), horizontalArrangement = Arrangement.End) {
                        IconAction(Ico.LOCK, stringResource(R.string.notes_lock_with_passcode), { lockScope.launch { vm.passcodeNeeded()?.let { lockPasscode = it } ?: lockSelected() } })
                        IconAction(Ico.TRASH, stringResource(R.string.common_delete), vm::trashSelected, color = jc.danger)
                    }
                }
                isTrash -> if (notes.isNotEmpty()) BottomBar() {
                    Spacer(Modifier.width(44.dp))
                    CountText(notes.size, JType.small, jc.muted) { pluralStringResource(R.plurals.notes_count, it, it) }
                    TextAction(stringResource(R.string.notes_delete_all), { confirmEmpty = true }, color = jc.danger)
                }
                tabRoot -> {}
                else -> BottomBar() {
                    Spacer(Modifier.size(44.dp))
                    CountText(notes.size, JType.small, jc.muted) { pluralStringResource(if (query.isBlank()) R.plurals.notes_count else R.plurals.notes_results_count, it, it) }
                    // New note is the floating button; this keeps the count centred.
                    Spacer(Modifier.size(44.dp))
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Home shows today's date in place of "All Notes".
            if (glance != null) Row(Modifier.intro(0), verticalAlignment = Alignment.Bottom) {
                Box(Modifier.weight(1f)) { DateHeading(remember { java.time.LocalDate.now() }, stringResource(R.string.notes_notes)) }
                // The sort and view menu, level with the title.
                if (homeHeader && !selecting && query.isBlank()) Box(Modifier.padding(end = 12.dp, bottom = 12.dp)) {
                    folder?.let { f ->
                        SortMenu(
                            f.sortBy, f.sortAscending, grid = grid, onGrid = onGrid, onChange = vm::setSort,
                            manual = if (f.isSmart) null else f.manualOrder, onManual = { vm.setManual(true); arranging = true }, onArrange = { arranging = true }, soft = true,
                            templateLabel = if (f.isSmart) null else templateLabel(f.template, savedTemplates), onTemplate = { pickingTemplate = true },
                        )
                    } ?: ViewMenu(grid, onGrid, soft = true)
                }
            } else PageTitle(folderName)
            // Out of the way while searching or picking notes.
            AnimatedVisibility(glance != null && query.isBlank() && !selecting) {
                glance?.takeIf { androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp >= 480 }?.let { TodayCards(it, Modifier.intro(1).padding(bottom = 14.dp)) }
            }
            if (isTrash) {
                Text(
                    stringResource(R.string.notes_notes_stay_here_for_days, keepDays),
                    Modifier.padding(horizontal = 20.dp).padding(bottom = 4.dp),
                    style = JType.para, color = jc.muted,
                )
            } else {
                SearchField(query, { vm.query.value = it; if (it.isBlank()) vm.searchScope.value = SearchScope.TOP }, stringResource(R.string.notes_search_all_notes), Modifier.intro(2).padding(horizontal = 16.dp), autoFocus = search, fill = jc.card,
                    onVoice = { lockContext.startActivity(android.content.Intent(lockContext, VoiceCommandActivity::class.java).setAction(VoiceCommandActivity.ACTION)) })
                if (hasNotes) {
                    Row(
                        Modifier.intro(3).fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // In Pinned every note is pinned already, so that pill would do nothing.
                        if (query.isBlank()) vm.filters.collectAsState().value.filterNot { it == ListFilter.PINNED && folderId == PINNED_ID }
                            .forEach { f -> Pill(if (f == ListFilter.ALL && f == filter && notes.isNotEmpty()) stringResource(R.string.notes_all, notes.size) else stringResource(f.labelRes), f == filter, { vm.filter.value = f }) }
                        else SearchScope.entries.forEach { sc ->
                            val n = when (sc) { SearchScope.TOP -> null; SearchScope.NOTES -> results.notes.size; SearchScope.ATTACHMENTS -> results.attachments.size }
                            Pill(if (n == null) stringResource(sc.labelRes) else stringResource(R.string.notes_scope_count, stringResource(sc.labelRes), n), sc == scope, { vm.searchScope.value = sc })
                        }
                    }
                }
            }
            when {
                notes.isEmpty() && hasNotes && filter == ListFilter.PINNED -> EmptyState(stringResource(R.string.notes_no_pinned_notes), stringResource(R.string.notes_swipe_right_on_a_note), icon = Ico.PIN)
                notes.isEmpty() && hasNotes && filter == ListFilter.CHECKLISTS -> EmptyState(stringResource(R.string.notes_no_checklists), stringResource(R.string.notes_notes_with_a_checklist_show), icon = Ico.CHECKLIST)
                notes.isEmpty() && hasNotes && filter != ListFilter.ALL -> EmptyState(stringResource(R.string.notes_no_notes, stringResource(filter.labelRes).lowercase()), stringResource(R.string.notes_notes_that_match_show_up), icon = Ico.FILE)
                query.isNotBlank() && entries.isEmpty() -> NoResults(
                    query.trim(), showTrash = !isTrash, onTry = { vm.query.value = it }, onTrash = onTrash, onNew = { onNewWith(query.trim()) },
                )
                notes.isEmpty() && isTrash -> EmptyState(stringResource(R.string.notes_nothing_here), stringResource(R.string.notes_notes_you_delete_stay_here, keepDays), icon = Ico.TRASH)
                notes.isEmpty() && canCreate -> EmptyState(
                    stringResource(R.string.notes_no_notes_yet), stringResource(R.string.notes_notes_you_write_or_move, folderName), button = stringResource(R.string.notes_new_note), onButton = onNew,
                    secondary = if (folder != null) stringResource(R.string.notes_move_existing_notes_here) else null, onSecondary = { picking = true },
                )
                notes.isEmpty() -> EmptyState(stringResource(R.string.notes_no_notes_yet), stringResource(R.string.notes_notes_show_up_here_when))
                // Search results and Recently Deleted keep rows: their extra details (match, days left) need the width.
                showGrid -> NoteGrid(entries, folder?.sortBy ?: NoteSort.EDITED, vm, onOpen, listEnd, gridState)
                else -> NoteList(
                    entries, folder?.sortBy ?: NoteSort.EDITED, isTrash, previewLines, keepDays, query.trim(),
                    folders.associate { it.folder.id to it.folder.name }, vm, onOpen, onMove = { moving = setOf(it.id) }, bottom = listEnd, state = listState,
                )
            }
        }
    }

    moving?.let { ids ->
        val count = ids.size
        val one = notes.firstOrNull { it.id in ids }
        MoveDialog(
            folders, currentFolderId = if (count == 1 && one != null) one.folderId else folderId,
            subject = if (count == 1) one?.displayTitle else pluralStringResource(R.plurals.notes_move_subject, count, count),
            title = if (count == 1) stringResource(R.string.notes_move_note) else stringResource(R.string.notes_move_notes),
            onDismiss = { moving = null },
        ) { id -> vm.moveNotes(ids, id, folders.firstOrNull { it.folder.id == id }?.folder?.name ?: context.getString(R.string.notes_folder_fallback)); moving = null }
    }
    if (picking) {
        val all by vm.allNotes.collectAsState()
        PickNotesDialog(all.filter { it.folderId != folderId }, folders, folderName, onDismiss = { picking = false }) { ids ->
            vm.moveNotes(ids, folderId, folderName, pool = all); picking = false
        }
    }
    if (confirmDelete) {
        val n = vm.selected.size
        MarginDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(pluralStringResource(R.plurals.notes_delete_forever_title, n, n)) },
            text = { Text(stringResource(R.string.notes_this_cant_be_undone)) },
            confirmButton = { TextButton(onClick = { vm.deleteSelectedForever(); confirmDelete = false }) { Text(stringResource(R.string.notes_delete, n), color = jc.danger) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
    if (confirmEmpty) {
        MarginDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text(stringResource(R.string.notes_delete_all_notes_permanently)) },
            text = { Text(stringResource(R.string.notes_this_cant_be_undone)) },
            confirmButton = { TextButton(onClick = { vm.emptyTrash(); confirmEmpty = false }) { Text(stringResource(R.string.notes_delete_all), color = jc.danger) } },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

@Composable
internal fun NoteList(
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
    bottom: Dp = 24.dp,
    state: LazyListState = rememberLazyListState(),
) {
    // Notes between section headers sit in one rounded card: the first rounds its top, the last its bottom
    // and has no line under it.
    fun isNote(e: ListEntry?) = e is ListEntry.Row || e is ListEntry.AttachmentRow
    val ends = remember(entries) { entries.filterIndexed { i, e -> isNote(e) && !isNote(entries.getOrNull(i + 1)) }.toSet() }
    val starts = remember(entries) { entries.filterIndexed { i, e -> isNote(e) && !isNote(entries.getOrNull(i - 1)) }.toSet() }
    // Rows under the "Yesterday" header show the time: the day is already in the header.
    val underYesterday = remember(entries) {
        var header: String? = null
        buildSet { entries.forEach { e -> if (e is ListEntry.Header) header = e.text else if (e is ListEntry.Row && header == "Yesterday") add(e.note.id) } }
    }
    LazyColumn(Modifier.fillMaxSize(), state, contentPadding = PaddingValues(bottom = bottom)) {
        itemsIndexed(entries, key = { _, it ->
            when (it) {
                is ListEntry.Row -> it.note.id
                is ListEntry.Header -> "h:" + it.text
                is ListEntry.AttachmentRow -> "a:" + it.note.id + ":" + it.attachment.file
            }
        }) { index, entry ->
            val group = if (entry is ListEntry.Header) Modifier
            else Modifier.padding(horizontal = 16.dp).padding(top = if (entry in starts) 6.dp else 0.dp)
                .clip(JShape.group(entry in starts, entry in ends))
                .background(jc.card)
            Column(Modifier.animateItem(fadeInSpec = JMotion.standard(), placementSpec = JMotion.standard(), fadeOutSpec = JMotion.quick()).intro(4 + index).then(group)) {
            when (entry) {
                is ListEntry.Header -> SectionHeader(listHeaderLabel(entry.text), entry.collapsed, vm::togglePinnedCollapsed)
                is ListEntry.AttachmentRow -> AttachmentHitRow(entry, query, folderNames[entry.note.folderId], onOpen)
                is ListEntry.Row -> {
                    val note = entry.note
                    // Recently Deleted: when it was deleted (or the days left near the end) and where it came from.
                    val trashMeta = if (!isTrash) null else {
                        val left = keepDays - ((System.currentTimeMillis() - (note.trashedAt ?: 0)) / DAY).toInt()
                        val context = LocalContext.current
                        listOfNotNull(
                            if (left <= 7) left.coerceAtLeast(0).let { pluralStringResource(R.plurals.notes_trash_days_left, it, it) } else stringResource(R.string.notes_trash_deleted_at, listTime(context, note.trashedAt ?: 0)),
                            folderNames[note.folderId]?.let { stringResource(R.string.notes_from, it) },
                        ).joinToString(" · ")
                    }
                    val search = if (query.isEmpty()) null else folderNames[note.folderId].orEmpty() to searchSnippet(note, query)
                    NoteRow(note, note.sortTime(sortBy), isTrash, previewLines, trashMeta, vm, onOpen, onMove, query, search, divider = entry !in ends, timeOnly = note.id in underYesterday)
                }
            }
            }
        }
    }
}

/** [text] with the search match tinted. */
@Composable
internal fun withHit(text: String, hit: IntRange?) = androidx.compose.ui.text.buildAnnotatedString {
    append(text)
    if (hit != null && hit.last < text.length) addStyle(SpanStyle(background = jc.accent.copy(alpha = 0.25f), color = jc.ink), hit.first, hit.last + 1)
}

/** Search: an attachment whose name matches, with the note it's in. Opens that note. */
@Composable
internal fun AttachmentHitRow(entry: ListEntry.AttachmentRow, query: String, folderName: String?, onOpen: (NoteEntity) -> Unit) {
    val name = entry.attachment.name
    val at = name.indexOf(query, ignoreCase = true)
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(entry.note) }.padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(44.dp, 52.dp).clip(JShape.xs).background(jc.page), contentAlignment = Alignment.Center) {
            val a = entry.attachment
            JIcon(if (a.isAudio) Ico.MIC else if (a.isImage) Ico.CAMERA else if (a.isPdf) ICON_PDF else Ico.FILE, jc.muted, size = 22.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(withHit(name, if (at < 0) null else at until at + query.length), style = JType.row, color = jc.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // Found by the words inside it (read from the photo or PDF): show where.
            entry.snippet?.let { Text(withHit(snippetLabel(it), it.hit), style = JType.secondary, color = jc.muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Text(
                listOfNotNull(stringResource(R.string.notes_in_note, entry.note.displayTitle), folderName).joinToString(" · "),
                style = JType.small, color = jc.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Offline with changes waiting, or sync failed (tap to try again). */
@Composable
internal fun SyncBannerRow(banner: com.margin.app.sync.SyncBanner, onRetry: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(JShape.sm).background(jc.card)
            .let { if (banner.retry) it.clickable(role = Role.Button, onClick = onRetry) else it }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        JIcon(Ico.CLOUD_OFF, if (banner.retry) jc.danger else jc.muted, size = 20.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // The banner's English text comes from syncBanner(); show it in the app's language.
            banner.title?.let { Text(stringResource(R.string.sync_offline_title), style = JType.rowTitle.copy(fontSize = 15.sp), color = jc.ink) }
            Text(stringResource(if (banner.retry) R.string.sync_failed_text else R.string.sync_offline_text), style = JType.small.copy(lineHeight = 18.sp), color = if (banner.title != null) jc.muted else jc.ink)
        }
    }
}

/** "Move existing notes here": tick notes from other folders. */
@Composable
internal fun PickNotesDialog(notes: List<NoteEntity>, folders: List<FolderRow>, folderName: String, onDismiss: () -> Unit, onMove: (Set<String>) -> Unit) {
    var picked by remember { mutableStateOf(emptySet<String>()) }
    val names = remember(folders) { folders.associate { it.folder.id to it.folder.name } }
    MarginDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notes_move_notes_to, folderName)) },
        text = {
            if (notes.isEmpty()) Text(stringResource(R.string.notes_there_are_no_notes_in), style = JType.secondary, color = jc.muted)
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
                Text(if (picked.isEmpty()) stringResource(R.string.common_move) else stringResource(R.string.notes_move, picked.size), fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/**
 * Sort key and direction for the open folder, behind a "⋯" button (or a [label] text button). Picking a
 * key starts it in its natural direction.
 */
@Composable
internal fun SortMenu(
    sortBy: NoteSort, ascending: Boolean, label: String? = null,
    /** Shown with a View as section (List or Grid) when set. */
    grid: Boolean? = null, onGrid: (Boolean) -> Unit = {},
    onChange: (NoteSort, Boolean) -> Unit,
    /** When set, a "New notes start from" item showing this template's name, which calls [onTemplate]. */
    templateLabel: String? = null, onTemplate: () -> Unit = {},
    soft: Boolean = false,
    /** Non-null offers "Your order" (checked when true), which [onManual] turns on, and Arrange notes. */
    manual: Boolean? = null, onManual: () -> Unit = {}, onArrange: () -> Unit = {},
) {
    var open by remember { mutableStateOf(false) }
    Box {
        if (label == null) IconAction(Ico.MORE, if (grid != null) stringResource(R.string.notes_view_and_sort) else stringResource(R.string.notes_sort), { open = true }, fill = true, soft = soft) else TextAction(label, { open = true })
        JMenu(expanded = open, onDismissRequest = { open = false }, width = 240.dp) {
            @Composable
            fun item(text: String, icon: String, checked: Boolean, onClick: () -> Unit) = DropdownMenuItem(
                text = { Text(text, style = JType.row, color = if (checked) jc.accent else jc.ink, fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal) },
                leadingIcon = { JIcon(icon, if (checked) jc.accent else jc.ink, size = 20.dp, stroke = 2f) },
                trailingIcon = { if (checked) JIcon(Ico.CHECK, jc.accent, size = 18.dp, stroke = 2.6f) },
                onClick = { onClick(); open = false },
            )
            if (grid != null) {
                ViewChoices(grid) { onGrid(it); open = false }
                HorizontalDivider(color = jc.line)
            }
            Text(stringResource(R.string.notes_sort_by), Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = JType.section, color = jc.muted)
            listOf(Triple(NoteSort.EDITED, stringResource(R.string.notes_date_edited), ICON_EDIT), Triple(NoteSort.CREATED, stringResource(R.string.notes_date_created), Ico.CALENDAR), Triple(NoteSort.TITLE, stringResource(R.string.notes_title), Ico.TEXT))
                .forEach { (key, text, icon) -> item(text, icon, manual != true && key == sortBy) { onChange(key, key == NoteSort.TITLE) } }
            if (manual != null) item(stringResource(R.string.notes_your_order), Ico.SORT, manual, onManual)
            HorizontalDivider(color = jc.line)
            if (manual == true) item(stringResource(R.string.notes_arrange_notes), Ico.MOVE, false, onArrange)
            else {
                val (first, second) = if (sortBy == NoteSort.TITLE) stringResource(R.string.notes_a_to_z) to stringResource(R.string.notes_z_to_a) else stringResource(R.string.notes_oldest_first) to stringResource(R.string.notes_newest_first)
                listOf(Triple(true, first, Ico.ARROW_UP), Triple(false, second, Ico.ARROW_DOWN))
                    .forEach { (asc, text, icon) -> item(text, icon, asc == ascending) { onChange(sortBy, asc) } }
            }
            if (templateLabel != null) {
                HorizontalDivider(color = jc.line)
                item(stringResource(R.string.notes_new_notes, templateLabel), ICON_TEMPLATE, false, onTemplate)
            }
        }
    }
}

/**
 * Arrange notes: drag a note's handle to put the folder in your own order. Pinned notes stay first and move
 * among themselves. Saves on Done.
 * shortcut: the page can't scroll while a note is dragged, so a long move takes several drags; upgrade to an auto-scrolling LazyColumn if folders get long.
 */
@Composable
internal fun ArrangeNotesDialog(start: List<NoteEntity>, onDone: (List<String>) -> Unit, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var order by remember { mutableStateOf(start) }
    var dragged by remember { mutableStateOf<Int?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val rowPx = with(LocalDensity.current) { EDIT_ROW.toPx() }
    fun siblings(i: Int) = order.indices.filter { order[it].isPinned == order[i].isPinned }
    fun move(newOrder: List<Int>) {
        val sibs = newOrder.sorted()
        order = order.toMutableList().also { list -> sibs.zip(newOrder).forEach { (slot, from) -> list[slot] = order[from] } }
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                NavBar(stringResource(R.string.notes_arrange_notes_2), onDismiss) { TextAction(stringResource(R.string.common_done), { onDone(order.map { it.id }); onDismiss() }, bold = true) }
            },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().background(jc.bg).verticalScroll(rememberScrollState(), enabled = dragged == null)) {
                Text(stringResource(R.string.notes_drag_the_handle_to_move), Modifier.padding(horizontal = 20.dp, vertical = 12.dp), style = JType.secondary, color = jc.muted)
                order.forEachIndexed { i, note ->
                    val lifted = i == dragged
                    val sibs = siblings(i)
                    val pos = sibs.indexOf(i)
                    Row(
                        Modifier.fillMaxWidth().height(EDIT_ROW).zIndex(if (lifted) 1f else 0f)
                            .graphicsLayer { if (lifted) { translationY = dragY; shadowElevation = 12.dp.toPx() } }
                            .background(note.colorMark.fill())
                            .semantics {
                                customActions = listOfNotNull(
                                    CustomAccessibilityAction(context.getString(R.string.notes_move_up)) { move(sibs.toMutableList().apply { add(pos - 1, removeAt(pos)) }); true }.takeIf { pos > 0 },
                                    CustomAccessibilityAction(context.getString(R.string.notes_move_down)) { move(sibs.toMutableList().apply { add(pos + 1, removeAt(pos)) }); true }.takeIf { pos < sibs.lastIndex },
                                )
                            }
                            .padding(start = 20.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(note.displayTitle, Modifier.weight(1f), style = JType.row, color = jc.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (note.isPinned) JIcon(Ico.PIN, jc.accent, size = 16.dp, stroke = 2f)
                        Box(
                            Modifier.size(44.dp).pointerInput(i, order) {
                                detectDragGestures(
                                    onDragStart = { dragged = i; dragY = 0f },
                                    onDrag = { change, amount -> change.consume(); dragY += amount.y },
                                    onDragEnd = { move(dropOrder(siblings(i), i, i * rowPx + rowPx / 2 + dragY, rowPx).first); dragged = null; dragY = 0f },
                                    onDragCancel = { dragged = null; dragY = 0f },
                                )
                            }.semantics { contentDescription = context.getString(R.string.notes_move, note.displayTitle) },
                            contentAlignment = Alignment.Center,
                        ) { JIcon("M5 9h14M5 15h14", jc.muted, size = 22.dp) }
                    }
                    HorizontalDivider(Modifier.padding(start = 20.dp), color = jc.line)
                }
            }
        }
    }
}

/** The name of template [key] (a folder's [FolderEntity.template]); "Blank" for none or one that's gone. */
@Composable
internal fun templateLabel(key: String?, saved: List<NoteEntity>): String =
    key?.let { k -> com.margin.app.data.builtInTemplate(k)?.let { stringResource(it.labelRes) } ?: saved.firstOrNull { it.id == k }?.title?.ifBlank { stringResource(R.string.common_untitled) } } ?: stringResource(R.string.notes_template_blank)

/** Picks what new notes in a folder start from: blank, a built-in template or a saved one. */
@Composable
internal fun TemplatePicker(current: String?, saved: List<NoteEntity>, onDismiss: () -> Unit, onPick: (String?) -> Unit) {
    val options = listOf<Pair<String?, String>>(null to stringResource(R.string.notes_blank)) +
        NoteTemplate.entries.map { com.margin.app.data.templateKey(it) to stringResource(it.labelRes) } +
        saved.map { it.id to it.title.ifBlank { stringResource(R.string.common_untitled) } }
    // A saved template that was deleted reads as Blank.
    val on = current?.takeIf { k -> options.any { it.first == k } }
    MarginDialog(
        onDismissRequest = onDismiss, containerColor = jc.card,
        title = { Text(stringResource(R.string.notes_new_notes_start_from), style = JType.rowTitle, color = jc.ink) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                options.forEach { (key, label) ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(JShape.sm).clickable(role = Role.RadioButton) { onPick(key) }
                            .semantics { selected = key == on }.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, Modifier.weight(1f), style = JType.row, color = if (key == on) jc.accent else jc.ink)
                        if (key == on) JIcon(Ico.CHECK, jc.accent, size = 18.dp, stroke = 2.6f)
                    }
                }
            }
        },
        confirmButton = { TextAction(stringResource(R.string.common_cancel), onDismiss) },
    )
}

/** Lists without their own sort (smart lists): just List or Grid. */
@Composable
internal fun ViewMenu(grid: Boolean, onGrid: (Boolean) -> Unit, soft: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconAction(Ico.MORE, stringResource(R.string.notes_view), { open = true }, fill = true, soft = soft)
        JMenu(expanded = open, onDismissRequest = { open = false }, width = 220.dp) {
            ViewChoices(grid) { onGrid(it); open = false }
        }
    }
}

internal const val ICON_LIST = "M9 6h11M9 12h11M9 18h11M4.5 6h.01M4.5 12h.01M4.5 18h.01"
internal const val ICON_GRID = "M4 4h6.5v6.5H4zM13.5 4H20v6.5h-6.5zM4 13.5h6.5V20H4zM13.5 13.5H20V20h-6.5z"

/** The View as section of a menu: List and Grid with their icons; the one in use is highlighted. */
@Composable
internal fun ViewChoices(grid: Boolean, onPick: (Boolean) -> Unit) {
    Text(stringResource(R.string.notes_view_as), Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = JType.section, color = jc.muted)
    listOf(Triple(false, stringResource(R.string.notes_list), ICON_LIST), Triple(true, stringResource(R.string.notes_grid), ICON_GRID)).forEach { (value, text, icon) ->
        val on = grid == value
        val color = if (on) jc.accent else jc.ink
        DropdownMenuItem(
            text = { Text(text, style = JType.row, color = color, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal) },
            leadingIcon = { JIcon(icon, color, size = 20.dp, stroke = 2f) },
            onClick = { onPick(value) },
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp).clip(JShape.sm)
                .background(if (on) jc.accent.copy(alpha = 0.12f) else Color.Transparent)
                .semantics { selected = on },
        )
    }
}

/**
 * Home's entrance right after the launch logo: 0 to 1 once per app start (MainActivity runs it as the logo
 * fades), 1 the rest of the time so [intro] does nothing.
 */
val homeIntro = androidx.compose.runtime.mutableFloatStateOf(1f)

/** Block [i] of the home screen rises in a beat after the one before it. Reads [homeIntro] at draw time only. */
fun Modifier.intro(i: Int): Modifier = stagger({ homeIntro.floatValue }, i)

/** Block [i] of a screen's entrance, as [progress] runs 0 to 1: each rises in a beat after the one before. */
fun Modifier.stagger(progress: () -> Float, i: Int): Modifier = graphicsLayer {
    val t = ((progress() - i.coerceAtMost(9) * 0.07f) / 0.37f).coerceIn(0f, 1f)
    if (t < 1f) {
        val e = 1f - (1f - t) * (1f - t) * (1f - t) // ease out
        alpha = e
        translationY = (1f - e) * 18.dp.toPx()
    }
}

/** Notes as two columns of cards, each as tall as its text; section headers (Pinned, Today...) span both. */
@Composable
internal fun NoteGrid(
    entries: List<ListEntry>, sortBy: NoteSort, vm: NotesViewModel, onOpen: (NoteEntity) -> Unit, bottom: Dp = 24.dp,
    state: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
) {
    LazyVerticalStaggeredGrid(
        StaggeredGridCells.Fixed(2), Modifier.fillMaxSize(), state,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottom),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalItemSpacing = 10.dp,
    ) {
        entries.forEachIndexed { index, entry ->
            when (entry) {
                is ListEntry.Header -> item(key = "h:" + entry.text, span = StaggeredGridItemSpan.FullLine) {
                    // Nudged left so the header's text lines up with the text inside the cards.
                    Box(Modifier.animateItem().intro(4 + index).offset(x = (-4).dp)) { SectionHeader(listHeaderLabel(entry.text), entry.collapsed, vm::togglePinnedCollapsed) }
                }
                is ListEntry.Row -> item(key = entry.note.id) {
                    Box(Modifier.animateItem(fadeInSpec = JMotion.standard(), placementSpec = JMotion.standard(), fadeOutSpec = JMotion.quick()).intro(4 + index)) {
                        NoteCard(entry.note, entry.note.sortTime(sortBy), vm, onOpen)
                    }
                }
                is ListEntry.AttachmentRow -> Unit // only in search results, which use the list
            }
        }
    }
}

/** A note in the grid: title, its first lines and the time. Tap opens it; long-press selects, as in the list. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NoteCard(note: NoteEntity, time: Long, vm: NotesViewModel, onOpen: (NoteEntity) -> Unit) {
    val context = LocalContext.current
    val selecting = vm.selected.isNotEmpty()
    val isSelected = note.id in vm.selected
    // The same preview text as the list rows (attachments read as "Drawing", "Voice note (1:52)"...), more of it.
    val body = remember(note) { note.searchText.lineSequence().filter { it.isNotBlank() }.take(8).joinToString("\n") { it.trim() } }
    val fill by animateColorAsState(if (isSelected || note.id == vm.openId) jc.selected else note.colorMark.fill(), JMotion.standard(), label = "cardFill")
    val edge by animateColorAsState(if (isSelected) jc.accent else Color.Transparent, JMotion.standard(), label = "cardEdge")
    val press = remember { MutableInteractionSource() }
    Column(
        Modifier.fillMaxWidth().pressScale(press).clip(JShape.lg).background(fill)
            .border(2.dp, edge, JShape.lg)
            .combinedClickable(
                interactionSource = press, indication = androidx.compose.material3.ripple(),
                onClick = { if (selecting) vm.toggleSelected(note.id) else onOpen(note) },
                onLongClick = { vm.toggleSelected(note.id) },
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (note.isLocked) JIcon(Ico.LOCK, jc.muted, size = 15.dp, stroke = 2f, modifier = Modifier.padding(top = 3.dp))
            Text(note.displayTitle, Modifier.weight(1f), style = JType.itemTitle, color = jc.ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (selecting) CheckCircle(isSelected)
            else if (note.isPinned) JIcon(Ico.PIN, jc.accent, size = 16.dp, stroke = 2f, modifier = Modifier.padding(top = 3.dp).semantics { contentDescription = context.getString(R.string.notes_pinned) })
        }
        Text(
            when {
                journalDay(note) != null -> stringResource(R.string.notes_journal)
                isLogin(note) -> stringResource(R.string.notes_password)
                isHabit(note) -> stringResource(R.string.notes_habit)
                isTracker(note) -> stringResource(R.string.notes_tracker)
                note.isLocked -> stringResource(R.string.notes_locked)
                body.isEmpty() -> stringResource(R.string.notes_no_additional_text)
                else -> body
            },
            style = JType.label, color = jc.muted, maxLines = 8, overflow = TextOverflow.Ellipsis,
        )
        Text(listTime(context, time), style = JType.caption, color = jc.muted, maxLines = 1)
    }
}

/** The shown name of a [ListEntry.Header]: the listing names its fixed groups in English (kept as keys), months and years come localized. */
@Composable
internal fun listHeaderLabel(key: String): String = when (key) {
    "Today" -> stringResource(R.string.common_today)
    "Yesterday" -> stringResource(R.string.common_yesterday)
    "Previous 7 Days" -> stringResource(R.string.notes_group_previous_7_days)
    "Previous 30 Days" -> stringResource(R.string.notes_group_previous_30_days)
    "Pinned" -> stringResource(R.string.notes_group_pinned)
    "Notes" -> stringResource(R.string.notes_group_notes)
    "In attachments" -> stringResource(R.string.notes_group_in_attachments)
    else -> key
}

/** A search snippet as shown: the listing's stand-ins for a locked or empty note, in the app's language. */
@Composable
internal fun snippetLabel(s: SearchSnippet): String = when {
    s.hit != null -> s.text
    s.text == "Locked" -> stringResource(R.string.notes_snippet_locked)
    s.text == "No additional text" -> stringResource(R.string.notes_snippet_no_text)
    else -> s.text
}

/** A header with [collapsed] set (Pinned) shows or hides its notes when tapped. */
@Composable
internal fun SectionHeader(text: String, collapsed: Boolean? = null, onToggle: () -> Unit = {}) {
    val modifier = if (collapsed == null) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().clickable(onClick = onToggle)
    Row(modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), Modifier.weight(1f), style = JType.groupLabel, color = jc.muted)
        if (collapsed != null) {
            val turn by animateFloatAsState(if (collapsed) 0f else 90f, JMotion.standard(), label = "chevron")
            JIcon(Ico.CHEVRON, jc.muted, size = 14.dp, stroke = 2.4f, modifier = Modifier.graphicsLayer { rotationZ = turn })
        }
    }
}

/** Picks a folder to move notes to. Smart folders aren't offered; the one they're in now is marked. */
@Composable
internal fun MoveDialog(
    folders: List<FolderRow>,
    currentFolderId: String,
    subject: String?,
    title: String = stringResource(R.string.notes_move_note),
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val current = folders.firstOrNull { it.folder.id == currentFolderId }?.folder?.name
    FolderSheet(
        title = title, subject = subject, subjectNote = current?.let { stringResource(R.string.notes_currently_in, it) },
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
internal fun NoteRow(
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
    divider: Boolean = true,
    /** Show only the time, as the row's header already says the day. */
    timeOnly: Boolean = false,
) {
    val context = LocalContext.current
    val selecting = vm.selected.isNotEmpty()
    val isSelected = note.id in vm.selected
    val scope = rememberCoroutineScope()
    var offset by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableIntStateOf(1) }
    // Settles with the speed the finger let go at, so a flick carries on into the spring.
    suspend fun settle(to: Float, velocity: Float = 0f) =
        animate(offset, to, velocity, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)) { v, _ -> offset = v }
    /** Slides the row off to the left, then deletes it (the list then closes the gap). */
    suspend fun swipeAway(velocity: Float = 0f) {
        animate(offset, -width.toFloat(), velocity, tween(180, easing = JMotion.ease)) { v, _ -> offset = v }
        vm.trash(note.id)
    }
    val reveal = with(LocalDensity.current) { 160.dp.toPx() }
    fun close() { scope.launch { settle(0f) } }
    // Past this far, letting go acts: the icon grows and the phone ticks once as the line is crossed.
    val view = LocalView.current
    val pinArmed = offset > width * 0.25f
    val deleteArmed = offset < -width * 0.6f
    LaunchedEffect(pinArmed, deleteArmed) { if (pinArmed || deleteArmed) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    val armScale by animateFloatAsState(if (pinArmed || deleteArmed) 1.2f else 1f, JMotion.bounce(), label = "armed")
    val rowFill by animateColorAsState(if (isSelected || note.id == vm.openId) jc.selected else note.colorMark.fill(), JMotion.standard(), label = "rowFill")
    val content = @Composable {
        Row(
            Modifier.fillMaxWidth()
                .background(rowFill)
                .combinedClickable(
                    onClick = { if (offset != 0f) close() else if (selecting) vm.toggleSelected(note.id) else onOpen(note) },
                    onLongClick = { vm.toggleSelected(note.id) },
                )
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            androidx.compose.animation.AnimatedVisibility(
                selecting, enter = fadeIn(JMotion.standard()) + expandHorizontally(JMotion.standard()),
                exit = fadeOut(JMotion.quick()) + shrinkHorizontally(JMotion.standard()),
            ) { CheckCircle(isSelected) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (note.isLocked) JIcon(Ico.LOCK, jc.muted, size = 15.dp, stroke = 2f)
                    val title = note.displayTitle
                    val at = if (search == null) -1 else title.indexOf(query, ignoreCase = true)
                    Text(
                        withHit(title, if (at < 0) null else at until at + query.length),
                        Modifier.weight(1f, fill = search != null || (note.isPinned && !isTrash)), style = JType.rowTitle, color = jc.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (search != null) Text(search.first, style = JType.small, color = jc.muted, maxLines = 1)
                    if (note.isPinned && !isTrash) {
                        JIcon(Ico.PIN, jc.accent, size = 16.dp, stroke = 2f, modifier = Modifier.semantics { contentDescription = context.getString(R.string.notes_pinned) })
                    }
                }
                if (trashMeta != null) {
                    Text(trashMeta, style = JType.secondary, color = jc.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    return@Column
                }
                if (search != null) {
                    Text(withHit(snippetLabel(search.second), search.second.hit), style = JType.secondary, color = jc.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    return@Column
                }
                // Preview: 0 lines shows only the time; 2 lines adds the next line of text below.
                val lines = if (journalDay(note) != null) listOf(stringResource(R.string.notes_journal)) else if (isLogin(note)) listOf(stringResource(R.string.notes_password)) else if (isHabit(note)) listOf(stringResource(R.string.notes_habit)) else if (isTracker(note)) listOf(stringResource(R.string.notes_tracker)) else if (note.isLocked) listOf(stringResource(R.string.notes_locked))
                else note.searchText.lineSequence().filter { it.isNotBlank() }.take(2).toList().ifEmpty { listOf(stringResource(R.string.notes_no_additional_text)) }
                val signedIn by vm.signedIn.collectAsState()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (timeOnly) android.text.format.DateUtils.formatDateTime(context, time, android.text.format.DateUtils.FORMAT_SHOW_TIME) else listTime(context, time),
                        style = JType.secondary, color = jc.ink, maxLines = 1,
                    )
                    if (signedIn && note.isDirty) {
                        JIcon(Ico.CLOUD, jc.muted, size = 15.dp, stroke = 2f, modifier = Modifier.semantics { contentDescription = context.getString(R.string.notes_waiting_to_sync) })
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
        val pinLabel = if (note.isPinned) stringResource(R.string.common_unpin) else stringResource(R.string.common_pin)
        Box(
            Modifier.fillMaxWidth().onSizeChanged { width = it.width }.semantics {
                // The swipe actions for screen readers.
                customActions = listOf(
                    CustomAccessibilityAction(pinLabel) { vm.togglePin(note.id); true },
                    CustomAccessibilityAction(context.getString(R.string.common_move)) { onMove(note); true },
                    CustomAccessibilityAction(context.getString(R.string.common_delete)) { vm.trash(note.id); true },
                )
            },
        ) {
            @Composable
            fun action(label: String, icon: String, color: Color, modifier: Modifier, scale: Float = 1f, onClick: () -> Unit) = Column(
                modifier.fillMaxHeight().background(color)
                    .clickable(onClick = onClick),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            ) {
                JIcon(icon, Color.White, size = 20.dp, modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale })
                Text(label, style = JType.small, color = Color.White)
            }
            if (offset > 0f) {
                val pinOn = jc.bg
                Row(
                    Modifier.matchParentSize().background(jc.ink)
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        JIcon(Ico.PIN, pinOn, size = 20.dp, modifier = Modifier.graphicsLayer { scaleX = armScale; scaleY = armScale })
                        Text(pinLabel, style = JType.small, color = pinOn)
                    }
                }
            } else if (offset < 0f) {
                Row(Modifier.matchParentSize(), horizontalArrangement = Arrangement.End) {
                    // A long swipe stretches Delete over the row.
                    val deleteWidth = with(LocalDensity.current) { maxOf(reveal / 2, -offset - reveal / 2).toDp() }
                    // Fixed fills in both themes, dark enough for the white labels (5.99:1 and 5.70:1).
                    action(stringResource(R.string.common_move), Ico.MOVE, Color(0xFF636366), Modifier.width(80.dp)) { close(); onMove(note) }
                    action(stringResource(R.string.common_delete), Ico.TRASH, Color(0xFFC4291C), Modifier.width(deleteWidth), armScale) { scope.launch { swipeAway() } }
                }
            }
            Box(
                Modifier.offset { IntOffset(offset.roundToInt(), 0) }.draggable(
                    orientation = Orientation.Horizontal,
                    enabled = !selecting,
                    state = rememberDraggableState { d -> offset = (offset + d).coerceIn(-width.toFloat(), width * 0.5f) },
                    onDragStopped = { speed ->
                        val v = offset
                        // A quick flick counts like a longer drag.
                        val aim = v + speed * 0.12f
                        when {
                            v > width * 0.25f -> { vm.togglePin(note.id); settle(0f, speed) }
                            v < -width * 0.6f -> swipeAway(speed)
                            aim < -reveal / 2 -> settle(-reveal, speed)
                            else -> settle(0f, speed)
                        }
                    },
                ),
            ) { content() }
        }
    }
    if (divider) HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = jc.line)
}
