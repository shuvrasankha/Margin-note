package com.margin.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

// ---------- Folders ----------

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun FoldersScreen(
    vm: FoldersViewModel, onOpen: (String) -> Unit, onSearch: () -> Unit, onNew: () -> Unit, onEdit: () -> Unit, onSettings: () -> Unit,
    onTodos: () -> Unit, onReminders: () -> Unit, onJournal: () -> Unit, onHabits: () -> Unit, onBack: () -> Unit,
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
        topBar = { NavBar("All Notes", onBack) { TextAction("Edit", onEdit); IconAction(Ico.SETTINGS, "Settings", onSettings) } },
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
                    Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp).fillMaxWidth().height(40.dp).clip(JShape.sm)
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
            item {
                val notes by vm.liveNotes.collectAsState()
                FolderListRow(ICON_JOURNAL, "Journal", remember(notes) { notes.count { journalDay(it) != null } }, onClick = onJournal)
                FolderListRow(ICON_HABITS, "Habits", remember(notes) { notes.count(::isHabit) }, onClick = onHabits)
            }
            item { SectionLabel("Folders") }
            items(regular, key = { it.folder.id }) { row ->
                Column(Modifier.animateItem()) {
                    FolderListRow(Ico.FOLDER, row.folder.name, row.count, row.depth, onClick = { onOpen(row.folder.id) }, onLongClick = { editing = row })
                }
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
internal fun DeleteFolderDialog(row: FolderRow, rows: List<FolderRow>, onDismiss: () -> Unit, onDelete: () -> Unit) {
    val hasSubfolders = rows.any { it.folder.parentId == row.folder.id }
    MarginDialog(
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
        title = { Text("New Folder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Folder name") }, shape = JShape.sm)
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
internal fun describe(filter: SmartFilter, tags: List<TagCount>): String {
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
internal fun FolderListRow(icon: String, name: String, count: Int, depth: Int = 0, sub: String? = null, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
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
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }, shape = JShape.sm)
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
internal fun NameDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    isValid: (String) -> Boolean = { it.isNotBlank() },
    label: String = "Name",
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    MarginDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(label) }, shape = JShape.sm) },
        confirmButton = { TextButton(onClick = { onSave(name) }, enabled = isValid(name)) { Text("Save", fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
