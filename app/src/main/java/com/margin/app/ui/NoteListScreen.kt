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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.margin.app.data.Drawing
import com.margin.app.data.NoteEntity
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
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

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
    // Locking the selected notes: asks for the notes passcode first unless notes are already unlocked.
    var lockPasscode by remember { mutableStateOf<PasswordMode?>(null) }
    val lockScope = rememberCoroutineScope()
    val lockContext = LocalContext.current
    val lockSelected = {
        vm.lockSelected { n ->
            val msg = when (n) { 0 -> "Those notes are already locked"; 1 -> "Note locked"; else -> "$n notes locked" }
            android.widget.Toast.makeText(lockContext, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    lockPasscode?.let { mode ->
        var old by remember { mutableStateOf(false) }
        val submit: suspend (CharArray) -> String? = { pw ->
            if (vm.enterPassword(pw)) { lockPasscode = null; lockSelected(); null } else if (old) "Wrong password" else "Wrong passcode"
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

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                Snackbar(
                    data, shape = JShape.md, containerColor = jc.ink, contentColor = jc.bg, actionColor = jc.bg,
                )
            }
        },
        topBar = {
            if (selecting) NavBar(null, {}) { TextAction("Done", vm::clearSelection, bold = true) }
            else NavBar(
                backLabel, onBack,
                leading = if (backLabel != null) null else ({
                    Row(
                        Modifier.heightIn(min = 44.dp).clip(JShape.xs).clickable(role = Role.Button, onClick = onFolders)
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        JIcon(Ico.FOLDER, jc.accent, stroke = 2f)
                        Text("Folders", style = JType.row, color = jc.accent)
                    }
                }),
            ) {
                if (!isTrash && query.isBlank()) {
                    val f = folder
                    if (f != null) SortMenu(
                        f.sortBy, f.sortAscending, grid = grid, onGrid = onGrid, onChange = vm::setSort,
                        templateLabel = if (f.isSmart) null else templateLabel(f.template, savedTemplates), onTemplate = { pickingTemplate = true },
                    )
                    else ViewMenu(grid, onGrid)
                }
                IconAction(Ico.SETTINGS, "Settings", onSettings)
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
                    Row(Modifier.width(88.dp), horizontalArrangement = Arrangement.End) {
                        IconAction(Ico.LOCK, "Lock with passcode", { lockScope.launch { vm.passcodeNeeded()?.let { lockPasscode = it } ?: lockSelected() } })
                        IconAction(Ico.TRASH, "Delete", vm::trashSelected, color = jc.danger)
                    }
                }
                isTrash -> if (notes.isNotEmpty()) BottomBar {
                    Spacer(Modifier.width(44.dp))
                    CountText(notes.size, JType.small, jc.muted) { plural(it, "Note") }
                    TextAction("Delete all", { confirmEmpty = true }, color = jc.danger)
                }
                else -> BottomBar {
                    Spacer(Modifier.size(44.dp))
                    CountText(notes.size, JType.small, jc.muted) { if (query.isBlank()) plural(it, "Note") else plural(it, "Result") }
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
                    style = JType.para, color = jc.muted,
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
        MarginDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${plural(n, "note")} permanently?") },
            text = { Text("This can't be undone.") },
            confirmButton = { TextButton(onClick = { vm.deleteSelectedForever(); confirmDelete = false }) { Text("Delete $n", color = jc.danger) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
    if (confirmEmpty) {
        MarginDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("Delete all notes permanently?") },
            text = { Text("This can't be undone.") },
            confirmButton = { TextButton(onClick = { vm.emptyTrash(); confirmEmpty = false }) { Text("Delete all", color = jc.danger) } },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("Cancel") } },
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
) {
    // The last note before a section header (or the end) has no line under it.
    val ends = remember(entries) { entries.filterIndexed { i, e -> e is ListEntry.Row && entries.getOrNull(i + 1) !is ListEntry.Row }.toSet() }
    LazyColumn(Modifier.fillMaxSize()) {
        items(entries, key = {
            when (it) {
                is ListEntry.Row -> it.note.id
                is ListEntry.Header -> "h:" + it.text
                is ListEntry.AttachmentRow -> "a:" + it.note.id + ":" + it.attachment.file
            }
        }) { entry ->
            Column(Modifier.animateItem(fadeInSpec = JMotion.standard(), placementSpec = JMotion.standard(), fadeOutSpec = JMotion.quick())) {
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
                    NoteRow(note, note.sortTime(sortBy), isTrash, previewLines, trashMeta, vm, onOpen, onMove, query, search, divider = entry !in ends)
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
        Box(Modifier.size(44.dp, 52.dp).clip(JShape.xs).background(jc.surface), contentAlignment = Alignment.Center) {
            val a = entry.attachment
            JIcon(if (a.isAudio) Ico.MIC else if (a.isImage) Ico.CAMERA else if (a.isPdf) ICON_PDF else Ico.FILE, jc.muted, size = 22.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(withHit(name, if (at < 0) null else at until at + query.length), style = JType.row, color = jc.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // Found by the words inside it (read from the photo or PDF): show where.
            entry.snippet?.let { Text(withHit(it.text, it.hit), style = JType.secondary, color = jc.muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Text(
                listOfNotNull("In " + entry.note.displayTitle, folderName).joinToString(" · "),
                style = JType.small, color = jc.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Offline with changes waiting, or sync failed (tap to try again). */
@Composable
internal fun SyncBannerRow(banner: com.margin.app.sync.SyncBanner, onRetry: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 20.dp, vertical = 4.dp).fillMaxWidth().clip(JShape.sm).background(jc.surface)
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
internal fun PickNotesDialog(notes: List<NoteEntity>, folders: List<FolderRow>, folderName: String, onDismiss: () -> Unit, onMove: (Set<String>) -> Unit) {
    var picked by remember { mutableStateOf(emptySet<String>()) }
    val names = remember(folders) { folders.associate { it.folder.id to it.folder.name } }
    MarginDialog(
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
internal fun SortMenu(
    sortBy: NoteSort, ascending: Boolean, label: String? = null,
    /** Shown with a View as section (List or Grid) when set. */
    grid: Boolean? = null, onGrid: (Boolean) -> Unit = {},
    onChange: (NoteSort, Boolean) -> Unit,
    /** When set, a "New notes start from" item showing this template's name, which calls [onTemplate]. */
    templateLabel: String? = null, onTemplate: () -> Unit = {},
) {
    var open by remember { mutableStateOf(false) }
    Box {
        if (label == null) IconAction(Ico.MORE, if (grid != null) "View and sort" else "Sort", { open = true }, fill = true) else TextAction(label, { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
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
            Text("Sort by", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = JType.section, color = jc.muted)
            listOf(Triple(NoteSort.EDITED, "Date edited", ICON_EDIT), Triple(NoteSort.CREATED, "Date created", Ico.CALENDAR), Triple(NoteSort.TITLE, "Title", Ico.TEXT))
                .forEach { (key, text, icon) -> item(text, icon, key == sortBy) { onChange(key, key == NoteSort.TITLE) } }
            HorizontalDivider(color = jc.line)
            val (first, second) = if (sortBy == NoteSort.TITLE) "A to Z" to "Z to A" else "Oldest first" to "Newest first"
            listOf(Triple(true, first, Ico.ARROW_UP), Triple(false, second, Ico.ARROW_DOWN))
                .forEach { (asc, text, icon) -> item(text, icon, asc == ascending) { onChange(sortBy, asc) } }
            if (templateLabel != null) {
                HorizontalDivider(color = jc.line)
                item("New notes: $templateLabel", ICON_TEMPLATE, false, onTemplate)
            }
        }
    }
}

/** The name of template [key] (a folder's [FolderEntity.template]); "Blank" for none or one that's gone. */
internal fun templateLabel(key: String?, saved: List<NoteEntity>): String =
    key?.let { k -> com.margin.app.data.builtInTemplate(k)?.label ?: saved.firstOrNull { it.id == k }?.title?.ifBlank { "Untitled" } } ?: "Blank"

/** Picks what new notes in a folder start from: blank, a built-in template or a saved one. */
@Composable
internal fun TemplatePicker(current: String?, saved: List<NoteEntity>, onDismiss: () -> Unit, onPick: (String?) -> Unit) {
    val options = listOf<Pair<String?, String>>(null to "Blank") +
        NoteTemplate.entries.map { com.margin.app.data.templateKey(it) to it.label } +
        saved.map { it.id to it.title.ifBlank { "Untitled" } }
    // A saved template that was deleted reads as Blank.
    val on = current?.takeIf { k -> options.any { it.first == k } }
    MarginDialog(
        onDismissRequest = onDismiss, containerColor = jc.card,
        title = { Text("New notes start from", style = JType.rowTitle, color = jc.ink) },
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
        confirmButton = { TextAction("Cancel", onDismiss) },
    )
}

/** Lists without their own sort (smart lists): just List or Grid. */
@Composable
internal fun ViewMenu(grid: Boolean, onGrid: (Boolean) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconAction(Ico.MORE, "View", { open = true }, fill = true)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ViewChoices(grid) { onGrid(it); open = false }
        }
    }
}

internal const val ICON_LIST = "M9 6h11M9 12h11M9 18h11M4.5 6h.01M4.5 12h.01M4.5 18h.01"
internal const val ICON_GRID = "M4 4h6.5v6.5H4zM13.5 4H20v6.5h-6.5zM4 13.5h6.5V20H4zM13.5 13.5H20V20h-6.5z"

/** The View as section of a menu: List and Grid with their icons; the one in use is highlighted. */
@Composable
internal fun ViewChoices(grid: Boolean, onPick: (Boolean) -> Unit) {
    Text("View as", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = JType.section, color = jc.muted)
    listOf(Triple(false, "List", ICON_LIST), Triple(true, "Grid", ICON_GRID)).forEach { (value, text, icon) ->
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

/** Notes as two columns of cards, each as tall as its text; section headers (Pinned, Today...) span both. */
@Composable
internal fun NoteGrid(entries: List<ListEntry>, sortBy: NoteSort, vm: NotesViewModel, onOpen: (NoteEntity) -> Unit) {
    LazyVerticalStaggeredGrid(
        StaggeredGridCells.Fixed(2), Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalItemSpacing = 10.dp,
    ) {
        entries.forEach { entry ->
            when (entry) {
                is ListEntry.Header -> item(key = "h:" + entry.text, span = StaggeredGridItemSpan.FullLine) {
                    // Nudged left so the header's text lines up with the text inside the cards.
                    Box(Modifier.animateItem().offset(x = (-4).dp)) { SectionHeader(entry.text, entry.collapsed, vm::togglePinnedCollapsed) }
                }
                is ListEntry.Row -> item(key = entry.note.id) {
                    Box(Modifier.animateItem(fadeInSpec = JMotion.standard(), placementSpec = JMotion.standard(), fadeOutSpec = JMotion.quick())) {
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
    val shape = JShape.md
    val fill by animateColorAsState(if (isSelected || note.id == vm.openId) jc.selected else jc.bg, JMotion.standard(), label = "cardFill")
    val edge by animateColorAsState(if (isSelected) jc.accent else jc.line, JMotion.standard(), label = "cardEdge")
    val press = remember { MutableInteractionSource() }
    Column(
        Modifier.fillMaxWidth().pressScale(press).clip(shape).background(fill)
            .border(if (isSelected) 2.dp else 1.dp, edge, shape)
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
            else if (note.isPinned) JIcon(Ico.PIN, jc.accent, size = 16.dp, stroke = 2f, modifier = Modifier.padding(top = 3.dp).semantics { contentDescription = "Pinned" })
        }
        Text(
            when {
                journalDay(note) != null -> "Journal"
                isLogin(note) -> "Password"
                isHabit(note) -> "Habit"
                note.isLocked -> "Locked"
                body.isEmpty() -> "No additional text"
                else -> body
            },
            style = JType.label, color = jc.muted, maxLines = 8, overflow = TextOverflow.Ellipsis,
        )
        Text(listTime(context, time), style = JType.caption, color = jc.muted, maxLines = 1)
    }
}

/** A header with [collapsed] set (Pinned) shows or hides its notes when tapped. */
@Composable
internal fun SectionHeader(text: String, collapsed: Boolean? = null, onToggle: () -> Unit = {}) {
    val modifier = if (collapsed == null) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().clickable(onClick = onToggle)
    Row(modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), style = JType.section, color = jc.muted)
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
    val rowFill by animateColorAsState(if (isSelected || note.id == vm.openId) jc.selected else jc.bg, JMotion.standard(), label = "rowFill")
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
                        JIcon(Ico.PIN, jc.accent, size = 16.dp, stroke = 2f, modifier = Modifier.semantics { contentDescription = "Pinned" })
                    }
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
                val lines = if (journalDay(note) != null) listOf("Journal") else if (isLogin(note)) listOf("Password") else if (isHabit(note)) listOf("Habit") else if (note.isLocked) listOf("Locked")
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
            fun action(label: String, icon: String, color: Color, modifier: Modifier, scale: Float = 1f, onClick: () -> Unit) = Column(
                modifier.fillMaxHeight().background(color).clickable(onClick = onClick),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            ) {
                JIcon(icon, Color.White, size = 20.dp, modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale })
                Text(label, style = JType.small, color = Color.White)
            }
            if (offset > 0f) {
                Row(Modifier.matchParentSize().background(jc.ink).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        JIcon(Ico.PIN, jc.bg, size = 20.dp, modifier = Modifier.graphicsLayer { scaleX = armScale; scaleY = armScale })
                        Text(pinLabel, style = JType.small, color = jc.bg)
                    }
                }
            } else if (offset < 0f) {
                Row(Modifier.matchParentSize(), horizontalArrangement = Arrangement.End) {
                    // A long swipe stretches Delete over the row.
                    val deleteWidth = with(LocalDensity.current) { maxOf(reveal / 2, -offset - reveal / 2).toDp() }
                    // Fixed fills in both themes, dark enough for the white labels (5.99:1 and 5.70:1).
                    action("Move", Ico.MOVE, Color(0xFF636366), Modifier.width(80.dp)) { close(); onMove(note) }
                    action("Delete", Ico.TRASH, Color(0xFFC4291C), Modifier.width(deleteWidth), armScale) { scope.launch { swipeAway() } }
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
