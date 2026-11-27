package com.margin.app.ui

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.margin.app.data.SmartFilter
import com.margin.app.data.TagCount
import com.margin.app.data.TodoStatus
import com.margin.app.data.color
import com.margin.app.data.effectiveParents
import com.margin.app.data.invoke
import com.margin.app.data.isSmart
import com.margin.app.data.isValidTagName
import com.margin.app.data.reorderGroup
import com.margin.app.data.subtreeIds
import com.margin.app.data.todoReport
import com.margin.app.data.isTracker
import com.margin.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource

// ---------- Folders ----------

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun FoldersScreen(
    vm: FoldersViewModel, onOpen: (String) -> Unit, onSearch: () -> Unit, onNew: () -> Unit, onEdit: () -> Unit, onSettings: () -> Unit,
    onTodos: () -> Unit, onReminders: () -> Unit, onJournal: () -> Unit, onHabits: () -> Unit, onTrackers: () -> Unit, onPasswords: () -> Unit, onBack: () -> Unit,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val syncBanner by vm.syncBanner.collectAsState()
    val rows by vm.rows.collectAsState()
    val trashCount by vm.trashCount.collectAsState()
    val archiveCount by vm.archiveCount.collectAsState()
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

    val pageList = rememberLazyListState()
    Scaffold(
        containerColor = jc.page,
        topBar = {
            val tabRoot = LocalTabRoot.current
            if (tabRoot) TabTop()
            else NavBar(stringResource(R.string.folders_all_notes), onBack, color = jc.page) {
                if (tabRoot) IconAction(Ico.NEW_FOLDER, stringResource(R.string.folders_new_folder), { creatingIn = "" })
                TextAction(stringResource(R.string.common_edit), onEdit)
                if (!tabRoot) IconAction(Ico.GEAR, stringResource(R.string.common_settings), onSettings)
            }
        },
        bottomBar = {
            if (!LocalTabRoot.current) BottomBar() {
                IconAction(Ico.NEW_FOLDER, stringResource(R.string.folders_new_folder), { creatingIn = "" }, size = 24.dp)
                IconAction(Ico.COMPOSE, stringResource(R.string.folders_new_note), onNew, size = 24.dp)
            }
        },
    ) { padding ->
        // Each time the screen opens, its parts rise in one after another; the tiles come row by row.
        val enter = remember { androidx.compose.animation.core.Animatable(0f) }
        LaunchedEffect(Unit) { enter.animateTo(1f, androidx.compose.animation.core.tween(650, easing = androidx.compose.animation.core.LinearEasing)) }
        val rise: (Int) -> Modifier = { Modifier.stagger({ enter.value }, it) }
        LazyColumn(state = pageList, modifier = Modifier.padding(padding).fillMaxSize()) {
            item { Box(rise(0)) {
                if (LocalTabRoot.current) TitleWithActions({
                    Column {
                        Text(
                            stringResource(R.string.folders_notes_and_folders, pluralStringResource(R.plurals.folders_notes_count, allCount, allCount), pluralStringResource(R.plurals.folders_count, regular.size, regular.size)), Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp),
                            style = JType.small, color = jc.muted, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        )
                        PageTitle(stringResource(R.string.folders_library))
                    }
                }) {
                    IconAction(Ico.NEW_FOLDER, stringResource(R.string.folders_new_folder), { creatingIn = "" }, soft = true)
                    TextAction(stringResource(R.string.common_edit), onEdit)
                } else PageTitle(stringResource(R.string.folders_folders))
            } }
            item {
                // Opens All Notes with its search field focused.
                Row(
                    rise(1).padding(horizontal = 16.dp).padding(bottom = 8.dp).fillMaxWidth().height(44.dp).clip(JShape.sm)
                        .background(jc.card).clickable(role = Role.Button, onClick = onSearch).semantics { contentDescription = ctx.getString(R.string.common_search) }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    JIcon(Ico.SEARCH, jc.muted, size = 17.dp, stroke = 2f)
                    Text(stringResource(R.string.common_search), style = JType.row, color = jc.muted, modifier = Modifier.weight(1f))
                    // Voice commands: "Remind me…", "Add … to my shopping list".
                    val context = androidx.compose.ui.platform.LocalContext.current
                    Box(
                        Modifier.fillMaxHeight().width(48.dp).clip(JShape.xs).clickable(role = Role.Button, onClickLabel = stringResource(R.string.folders_voice_command)) {
                            context.startActivity(android.content.Intent(context, VoiceCommandActivity::class.java).setAction(VoiceCommandActivity.ACTION))
                        }.semantics { contentDescription = context.getString(R.string.folders_voice_command) },
                        contentAlignment = Alignment.Center,
                    ) { JIcon(Ico.MIC, jc.accent, size = 20.dp, stroke = 2f) }
                }
            }
            syncBanner?.let { banner -> item { SyncBannerRow(banner, vm::retrySync) } }
            item { SectionLabel(stringResource(R.string.folders_collections), rise(2)) }
            item {
                val notes by vm.liveNotes.collectAsState()
                val reminders by vm.reminders.collectAsState()
                // To-do lists not finished yet (the screen shows the progress report), and reminders of notes still around.
                val openTodos = remember(notes) {
                    todoReport(notes, todayEpochDay(), java.time.ZoneId.systemDefault()).lists.count { it.status != TodoStatus.DONE }
                }
                val places by vm.places.collectAsState()
                val reminderCount = remember(notes, reminders, places) { notes.count { it.id in reminders || it.id in places } }
                val tiles = listOf(
                    LibraryItem(Ico.FILE, stringResource(R.string.folders_all_notes), allCount) { onOpen(ALL_ID) },
                    LibraryItem(Ico.PIN, stringResource(R.string.folders_pinned), pinnedCount) { onOpen(PINNED_ID) },
                    LibraryItem(Ico.CHECKLIST, stringResource(R.string.folders_to_dos), openTodos, onTodos),
                    LibraryItem(Ico.ALARM, stringResource(R.string.folders_reminders), reminderCount, onReminders),
                    LibraryItem(ICON_JOURNAL, stringResource(R.string.folders_journal), remember(notes) { notes.count { journalDay(it) != null } }, onJournal),
                    LibraryItem(ICON_HABITS, stringResource(R.string.folders_habits), remember(notes) { notes.count(::isHabit) }, onHabits),
                    LibraryItem(ICON_TRACKERS, stringResource(R.string.folders_trackers), remember(notes) { notes.count { isTracker(it) } }, onTrackers),
                    LibraryItem(ICON_PASSWORDS, stringResource(R.string.folders_passwords), remember(notes) { notes.count { com.margin.app.data.isLogin(it) } }, onPasswords),
                )
                // Two to a row. Plain rows, not a lazy grid: this sits inside the list.
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    tiles.chunked(2).forEachIndexed { r, pair ->
                        Row(rise(3 + r), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { LibraryTile(it, Modifier.weight(1f)) }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            item { SectionLabel(stringResource(R.string.folders_folders), rise(7)) }
            itemsIndexed(regular, key = { _, it -> it.folder.id }) { i, row ->
                Column(Modifier.animateItem().then(rise(8))) {
                    FolderListRow(
                        Ico.FOLDER, row.folder.name, row.count, row.depth, onClick = { onOpen(row.folder.id) }, onLongClick = { editing = row },
                        first = i == 0, last = i == regular.lastIndex,
                    )
                }
            }
            if (smart.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.folders_smart_folders), rise(9)) }
                itemsIndexed(smart, key = { _, it -> it.folder.id }) { i, row ->
                    Box(rise(9)) { FolderListRow(
                        Ico.SMART_FOLDER, row.folder.name, row.count, sub = describe(row.folder.smartFilter!!, tags),
                        onClick = { onOpen(row.folder.id) }, onLongClick = { editing = row },
                        first = i == 0, last = i == smart.lastIndex,
                    ) }
                }
            }
            if (tags.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.folders_tags), rise(9)) }
                item {
                    FlowRow(
                        rise(9).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        tags.forEach { tag ->
                            Chip("#" + tag.name, onClick = { onOpen(TAG_PREFIX + tag.key) }, trailing = "${tag.count}", onLongClick = { renamingTag = tag })
                        }
                    }
                }
            }
            item { SectionLabel(stringResource(R.string.common_more), rise(9)) }
            item { Box(rise(9)) { FolderListRow(Ico.ARCHIVE, stringResource(R.string.common_archive), archiveCount, onClick = { onOpen(ARCHIVE_ID) }, last = false) } }
            item { Box(rise(9)) { FolderListRow(Ico.TRASH, stringResource(R.string.folders_recently_deleted), trashCount, onClick = { onOpen(TRASH_ID) }, first = false) } }
            item {
                Text(
                    stringResource(R.string.folders_press_and_hold_a_folder),
                    rise(9).padding(horizontal = 20.dp, vertical = 16.dp), style = JType.small, color = jc.muted,
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
        SmartFolderDialog(stringResource(R.string.folders_new_smart_folder), initialName, SmartFilter(), tags, onDismiss = { creatingSmart = null }) { name, filter ->
            vm.create(name, smartFilter = filter); creatingSmart = null
        }
    }
    smartEditing?.let { row ->
        SmartFolderDialog(stringResource(R.string.folders_edit_smart_folder), row.folder.name, row.folder.smartFilter!!, tags, onDismiss = { smartEditing = null }) { name, filter ->
            vm.rename(row.folder.id, name); vm.setSmartFilter(row.folder.id, filter); smartEditing = null
        }
    }
    editing?.let { row ->
        val folder = row.folder
        MarginDialog(
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
                            option(stringResource(R.string.common_edit)) { smartEditing = row }
                            option(stringResource(R.string.common_delete), danger = true) { deleting = row }
                        }
                        folder.isDefault -> option(stringResource(R.string.folders_new_folder_inside)) { creatingIn = folder.id }
                        else -> {
                            option(stringResource(R.string.common_rename)) { renaming = row }
                            option(stringResource(R.string.folders_new_folder_inside)) { creatingIn = folder.id }
                            option(stringResource(R.string.common_move)) { moving = row }
                            option(stringResource(R.string.common_delete), danger = true) { deleting = row }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
    renaming?.let { row ->
        NameDialog(stringResource(R.string.folders_rename_folder), row.folder.name, onDismiss = { renaming = null }, taken = { n -> nameTaken(rows, row.folder.parentId, n, except = row.folder.id) }) { vm.rename(row.folder.id, it); renaming = null }
    }
    moving?.let { row ->
        // Not into itself or anything inside it; smart folders hold no folders.
        val inside = subtreeIds(rows.map { it.folder }, row.folder.id)
        val parentName = rows.firstOrNull { it.folder.id == row.folder.parentId }?.folder?.name
        FolderSheet(
            title = stringResource(R.string.folders_move_folder), subject = row.folder.name,
            subjectNote = if (parentName != null) stringResource(R.string.folders_currently_in, parentName) else stringResource(R.string.folders_currently_at_the_top_level),
            rows = regular.filter { it.folder.id !in inside }, currentId = row.folder.parentId,
            topLevel = row.folder.parentId != null,
            onDismiss = { moving = null },
        ) { vm.moveFolder(row.folder.id, it); moving = null }
    }
    deleting?.let { row -> DeleteFolderDialog(row, rows, onDismiss = { deleting = null }) { vm.delete(row.folder.id); deleting = null } }
    renamingTag?.let { tag ->
        NameDialog(
            pluralStringResource(R.plurals.folders_rename_tag_in_notes, tag.count, tag.count, tag.name), tag.name,
            onDismiss = { renamingTag = null },
            isValid = { isValidTagName(it.trim().removePrefix("#")) },
            label = stringResource(R.string.folders_tag),
        ) { vm.renameTag(tag.key, it); renamingTag = null }
    }
}

@Composable
internal fun DeleteFolderDialog(row: FolderRow, rows: List<FolderRow>, onDismiss: () -> Unit, onDelete: () -> Unit) {
    val hasSubfolders = rows.any { it.folder.parentId == row.folder.id }
    MarginDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.folders_delete, row.folder.name)) },
        text = {
            Text(
                when {
                    row.folder.isSmart -> stringResource(R.string.folders_the_notes_it_shows_arent)
                    hasSubfolders -> stringResource(R.string.folders_its_subfolders_are_deleted_too)
                    else -> stringResource(R.string.folders_its_notes_will_move_to)
                },
            )
        },
        confirmButton = { TextButton(onClick = onDelete) { Text(stringResource(R.string.common_delete), color = jc.danger) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/** New Folder: name, smart toggle (conditions are picked next) and where it goes. */
@Composable
internal fun NewFolderDialog(
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
    MarginDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.folders_new_folder_2)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    name, { name = it }, singleLine = true, label = { Text(stringResource(R.string.folders_folder_name)) }, shape = JShape.sm,
                    supportingText = if (nameTaken(regular, if (smart) null else parent, name)) { { Text(stringResource(R.string.folders_name_taken)) } } else null,
                )
                if (allowSmart) Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.folders_smart_folder), style = JType.row, color = jc.ink)
                        Text(stringResource(R.string.folders_fill_it_automatically_by_tag), style = JType.small, color = jc.muted)
                    }
                    JSwitch(smart, { smart = it }, label = stringResource(R.string.folders_smart_folder))
                }
                // Smart folders are always top level.
                if (!smart) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.folders_inside), Modifier.weight(1f), style = JType.row, color = jc.ink)
                    Box {
                        TextAction(regular.firstOrNull { it.folder.id == parent }?.folder?.name ?: stringResource(R.string.folders_top_level), { picking = true })
                        JMenu(expanded = picking, onDismissRequest = { picking = false }, width = 240.dp) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.folders_top_level)) }, onClick = { parent = null; picking = false })
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
                Text(if (smart) stringResource(R.string.common_next) else stringResource(R.string.common_create), fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/**
 * Edit folders: drag the handle to reorder a folder among the ones listed with it (same parent, or the smart
 * folders); tap a name to rename; the red button deletes. The default folder stays first and can't change.
 */
@Composable
fun EditFoldersScreen(vm: FoldersViewModel, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
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

    val pageScroll = rememberScrollState()
    Scaffold(
        topBar = { NavBar(stringResource(R.string.folders_folders), onBack) { TextAction(stringResource(R.string.common_done), onBack, bold = true) } },
        bottomBar = {
            BottomBar() {
                TextAction(stringResource(R.string.folders_new_folder_2), { creating = true })
                CountText(rows.count { !it.folder.isSmart }, JType.small, jc.muted) { pluralStringResource(R.plurals.folders_count, it, it) }
                Spacer(Modifier.width(44.dp))
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(pageScroll, enabled = dragged == null)) {
            PageTitle(stringResource(R.string.folders_folders))
            Text(stringResource(R.string.folders_drag_to_reorder_tap_a), Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp), style = JType.secondary, color = jc.muted)
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
                                    CustomAccessibilityAction(context.getString(R.string.folders_move_up)) { commit(sibs.toMutableList().apply { add(pos - 1, removeAt(pos)) }); true }.takeIf { pos > 0 },
                                    CustomAccessibilityAction(context.getString(R.string.folders_move_down)) { commit(sibs.toMutableList().apply { add(pos + 1, removeAt(pos)) }); true }.takeIf { pos in 0 until sibs.lastIndex },
                                )
                            }
                            .padding(start = 6.dp + 24.dp * row.depth, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // A 44dp slot takes the tap; the red dot stays 24dp and where it was (the row's start inset shrank by 10dp).
                        if (f.isDefault) Spacer(Modifier.width(44.dp))
                        else Box(
                            Modifier.fillMaxHeight().width(44.dp).clickable(role = Role.Button) { deleting = row }
                                .semantics { contentDescription = context.getString(R.string.folders_delete_2, f.name) },
                            contentAlignment = Alignment.Center,
                        ) { Box(Modifier.size(24.dp).clip(CircleShape).background(jc.danger), contentAlignment = Alignment.Center) { Box(Modifier.size(10.dp, 2.dp).background(Color.White)) } }
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
                            }.semantics { contentDescription = context.getString(R.string.folders_reorder, f.name) },
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
        NameDialog(stringResource(R.string.folders_rename_folder), row.folder.name, onDismiss = { renaming = null }, taken = { n -> nameTaken(rows, row.folder.parentId, n, except = row.folder.id) }) { vm.rename(row.folder.id, it); renaming = null }
    }
    deleting?.let { row -> DeleteFolderDialog(row, rows, onDismiss = { deleting = null }) { vm.delete(row.folder.id); deleting = null } }
    if (creating) {
        NewFolderDialog(rows.filter { !it.folder.isSmart }, null, allowSmart = false, onDismiss = { creating = false }) { name, parentId, _ ->
            vm.create(name, parentId); creating = false
        }
    }
}

internal val EDIT_ROW = 56.dp

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
@Composable
internal fun describe(filter: SmartFilter, tags: List<TagCount>): String {
    val names = filter.tags.map { key -> "#" + (tags.firstOrNull { it.key == key }?.name ?: key) }
    val parts = listOfNotNull(
        names.takeIf { it.isNotEmpty() }?.joinToString(stringResource(if (filter.allTags) R.string.folders_tags_join_and else R.string.folders_tags_join_or)),
        stringResource(R.string.folders_filter_has_checklist).takeIf { filter.hasChecklist },
        stringResource(R.string.folders_filter_pinned).takeIf { filter.pinnedOnly },
        stringResource(R.string.folders_filter_has_attachments).takeIf { filter.hasAttachments },
        filter.editedWithinDays?.let { pluralStringResource(R.plurals.folders_filter_edited_within, it, it) },
    )
    return if (parts.isEmpty()) stringResource(R.string.folders_filter_every_note) else parts.joinToString(" · ")
}

/** A Library place: its icon, name, count and where a tap goes. */
internal data class LibraryItem(val icon: String, val name: String, val count: Int, val onClick: () -> Unit)

/** A Library place as a compact card: a quiet icon, the name and its count on one line. */
@Composable
internal fun LibraryTile(item: LibraryItem, modifier: Modifier = Modifier) {
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Row(
        modifier.height(60.dp).pressScale(press).clip(JShape.md).background(jc.card)
            .clickable(interactionSource = press, indication = androidx.compose.material3.ripple(), role = Role.Button, onClick = item.onClick)
            .padding(horizontal = 14.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconTile(item.icon, jc.accent)
        Text(item.name, Modifier.weight(1f), style = JType.item.copy(fontSize = 15.sp), color = jc.ink, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        CountText(item.count, JType.small.copy(fontSize = 14.sp), jc.muted)
    }
}

/**
 * A folder in a rounded group: [first] and [last] round the group's top and bottom corners, and rows between
 * them are split by a hairline. Sub-folders indent by [depth].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FolderListRow(
    icon: String, name: String, count: Int, depth: Int = 0, sub: String? = null, onClick: () -> Unit, onLongClick: (() -> Unit)? = null,
    first: Boolean = true, last: Boolean = true,
) {
    val shape = JShape.group(first, last)
    val start = 16.dp + 22.dp * depth
    Column(Modifier.padding(horizontal = 16.dp).clip(shape).background(jc.card)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = if (sub == null) 52.dp else 64.dp)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(start = start, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconTile(icon)
            Column(Modifier.weight(1f).padding(vertical = if (sub == null) 0.dp else 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, style = JType.row, color = jc.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                sub?.let { Text(it, style = JType.small, color = jc.muted, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
            CountText(count, JType.secondary, jc.muted)
            JIcon(Ico.CHEVRON, jc.muted, size = 14.dp, stroke = 2.2f)
        }
        if (!last) HorizontalDivider(Modifier.padding(start = start + 42.dp), color = jc.line)
    }
}

/** Name plus filter for a smart folder. Every condition that's set must match. */
@Composable
internal fun SmartFolderDialog(
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

    MarginDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            // No scroll of its own: the dialog scrolls its text (two nested scrolls crash).
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(stringResource(R.string.folders_name)) }, shape = JShape.sm)
                Text(stringResource(R.string.folders_shows_notes_that_match_all), style = JType.small, color = jc.muted)
                Text(stringResource(R.string.folders_tags), Modifier.padding(top = 8.dp), style = JType.section, color = jc.muted)
                if (tags.isEmpty()) {
                    Text(stringResource(R.string.folders_no_tags_yet_type_word), style = JType.small, color = jc.muted)
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
                            choice(stringResource(R.string.folders_any_of_these), !filter.allTags) { filter = filter.copy(allTags = false) }
                            choice(stringResource(R.string.folders_all_of_these), filter.allTags) { filter = filter.copy(allTags = true) }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.folders_has_a_checklist), Modifier.weight(1f), style = JType.row)
                    JSwitch(filter.hasChecklist, { filter = filter.copy(hasChecklist = it) }, label = stringResource(R.string.folders_has_a_checklist))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.folders_pinned), Modifier.weight(1f), style = JType.row)
                    JSwitch(filter.pinnedOnly, { filter = filter.copy(pinnedOnly = it) }, label = stringResource(R.string.folders_pinned))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.folders_has_attachments), Modifier.weight(1f), style = JType.row)
                    JSwitch(filter.hasAttachments, { filter = filter.copy(hasAttachments = it) }, label = stringResource(R.string.folders_has_attachments))
                }
                Text(stringResource(R.string.folders_edited), Modifier.padding(top = 8.dp), style = JType.section, color = jc.muted)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(null to stringResource(R.string.folders_any_time), 7 to stringResource(R.string.folders_last_7_days), 30 to stringResource(R.string.folders_last_30_days)).forEach { (days, label) ->
                        choice(label, filter.editedWithinDays == days) { filter = filter.copy(editedWithinDays = days) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name.trim(), filter) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.common_save), fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/** Whether another folder next to this one already has [name] (allowed, but easy to mix up). */
private fun nameTaken(rows: List<FolderRow>, parentId: String?, name: String, except: String? = null) =
    name.isNotBlank() && rows.any { it.folder.id != except && it.folder.parentId == parentId && it.folder.name.trim().equals(name.trim(), ignoreCase = true) }

@Composable
internal fun NameDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    isValid: (String) -> Boolean = { it.isNotBlank() },
    label: String = stringResource(R.string.folders_name),
    /** Whether another folder beside this one already has the name: allowed, but shown as a warning. */
    taken: (String) -> Boolean = { false },
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    MarginDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(label) }, shape = JShape.sm, supportingText = if (taken(name)) { { Text(stringResource(R.string.folders_name_taken)) } } else null) },
        confirmButton = { TextButton(onClick = { onSave(name) }, enabled = isValid(name)) { Text(stringResource(R.string.common_save), fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
