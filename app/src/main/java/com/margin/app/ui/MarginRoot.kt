package com.margin.app.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.margin.app.data.DEFAULT_FOLDER_ID
import com.margin.app.data.color
import com.margin.app.data.invoke
import com.margin.app.data.isSmart
import com.margin.app.data.newId
import kotlinx.coroutines.launch

// ---------- Root ----------

@Composable
fun MarginRoot(
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
            subject = share.subject?.takeIf { it.isNotBlank() } ?: share.text?.lineSequence()?.firstOrNull { it.isNotBlank() } ?: if (share.pdf) "PDF" else "Photo",
            subjectNote = if (share.image != null) (if (share.pdf) "With a PDF" else "With a photo") else null,
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
    /** Leaving the editor for another kind of screen: the note stays visible until its screen has slid away. */
    fun leaveEditor() = editor.closeSoon(JMotion.screenMs + 80L) { nav.current !is Screen.Editor }
    fun back() {
        if (nav.current is Screen.Editor) {
            if (nav.stack.getOrNull(nav.stack.lastIndex - 1) is Screen.Editor) editor.close() else leaveEditor()
        }
        nav.pop()
    }
    fun listName(folderId: String) = when {
        folderId == TRASH_ID -> "Recently Deleted"
        folderId == ALL_ID -> "All Notes"
        folderId == PINNED_ID -> "Pinned"
        folderId.startsWith(TAG_PREFIX) -> "#" + (tags.firstOrNull { TAG_PREFIX + it.key == folderId }?.name ?: folderId.removePrefix(TAG_PREFIX))
        else -> rows.firstOrNull { it.folder.id == folderId }?.folder?.name ?: "Notes"
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(jc.bg)) {
    // Tablets and unfolded foldables: the list on the left, the open note beside it. A phone on its side is too
    // short for that and keeps one screen at a time.
    val wide = maxWidth >= 640.dp && maxHeight >= 480.dp
    val listWidth = (maxWidth * 0.38f).coerceIn(300.dp, 400.dp)
    /** In two panes: where the list on the left is in the stack (under any notes opened from it); null for one pane. */
    val listAt: Int? = if (!wide) null else {
        var i = nav.stack.lastIndex
        while (i > 0 && nav.stack[i] is Screen.Editor) i--
        i.takeIf { nav.stack[it].let { s -> s is Screen.Notes || s == Screen.Todos || s == Screen.Reminders } }
    }
    val openNoteId = (nav.current as? Screen.Editor)?.noteId?.takeIf { listAt != null }
    SideEffect { notes.openId = openNoteId }
    /** Opens a note from a list. In two panes it takes the place of the note already open beside the list. */
    fun openNote(target: Screen.Editor) {
        if (listAt != null && nav.current is Screen.Editor) {
            if (nav.current == target) return
            editor.close()
            while (nav.current is Screen.Editor) nav.pop()
        }
        nav.push(target)
    }
    // Screens slide in over the one before and back off it; the back gesture drags the screen along with the finger.
    // In two panes the list is the screen; notes opened beside it change only the right pane.
    val entry = if (listAt != null) NavEntry(listAt + 1, nav.stack[listAt], split = true) else NavEntry(nav.stack.size, nav.current)
    val seek = remember { SeekableTransitionState(entry) }
    val transition = rememberTransition(seek, label = "screen")
    LaunchedEffect(entry) { seek.animateTo(entry) }
    androidx.activity.compose.PredictiveBackHandler(enabled = nav.stack.size > 1) { progress ->
        val previous = nav.stack[nav.stack.lastIndex - 1]
        // No peeking at a screen that shares this one's view model (it would load its own note or folder into it),
        // nor at a note, which would be opened just to be looked at, nor at the list that is already in view.
        val peek = previous !is Screen.Editor && !(nav.current is Screen.Notes && previous is Screen.Notes) &&
            !(listAt != null && nav.current is Screen.Editor)
        try {
            progress.collect { if (peek) seek.seekTo(it.progress, NavEntry(entry.depth - 1, previous)) }
            back()
        } catch (e: kotlinx.coroutines.CancellationException) {
            if (peek) rootScope.launch { seek.animateTo(entry) }
            throw e
        }
    }

    /** One screen of the stack. */
    @Composable
    fun Content(shown: NavEntry) {
    /** The screen under this one. */
    val below = nav.stack.getOrNull(shown.depth - 2)
        when (val screen = shown.screen) {
            Screen.Folders -> FoldersScreen(
                folders,
                // All Notes is the screen underneath; go back to it rather than stacking another.
                onOpen = { if (it == ALL_ID && nav.stack.size == 2 && nav.stack[0] == Screen.Notes(ALL_ID)) back() else nav.push(Screen.Notes(it)) },
                onSearch = { nav.push(Screen.Notes(ALL_ID, search = true)) },
                onNew = { nav.push(Screen.Editor(newId(), DEFAULT_FOLDER_ID)) },
                onEdit = { nav.push(Screen.EditFolders) },
                onSettings = { nav.push(Screen.Settings) },
                onTodos = { nav.push(Screen.Todos) },
                onReminders = { nav.push(Screen.Reminders) },
                onJournal = { nav.push(Screen.Journal) },
                onHabits = { nav.push(Screen.Habits) },
                onBack = ::back,
            )
            Screen.EditFolders -> EditFoldersScreen(folders, onBack = ::back)
            Screen.Reminders -> {
                val all by folders.liveNotes.collectAsState()
                val reminders by folders.reminders.collectAsState()
                val repeats by folders.reminderRepeats.collectAsState()
                RemindersScreen(all, reminders, repeats, onOpen = { if (isHabit(it)) nav.push(Screen.Habits) else openNote(Screen.Editor(it.id, it.folderId)) }, onBack = ::back)
            }
            Screen.Journal -> {
                val all by folders.liveNotes.collectAsState()
                JournalGate(editor, fingerprint, onCancel = ::back) { JournalScreen(all, onOpenDay = { day -> folders.openJournal(day) { id, folder -> nav.push(Screen.Editor(id, folder)) } }, onDelete = folders::deleteJournal, onBack = ::back) }
            }
            Screen.Habits -> {
                val all by folders.liveNotes.collectAsState()
                HabitsScreen(all, onToggle = folders::toggleHabit, onAdd = folders::addHabit, onRename = folders::renameHabit, onDelete = folders::deleteHabit, onBack = ::back,
                    reminders = folders.reminders.collectAsState().value, onRemind = folders::setHabitReminder)
            }
            Screen.Todos -> {
                val all by folders.liveNotes.collectAsState()
                TodosScreen(all, onOpen = { openNote(Screen.Editor(it.id, it.folderId)) }, onBack = ::back)
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
                onOpen = { openNote(Screen.Editor(it.id, it.folderId)) },
                onNew = { openNote(Screen.Editor(newId(), screen.folderId.takeIf { !isVirtualList(it) } ?: DEFAULT_FOLDER_ID)) },
                onNewWith = { q ->
                    val f = screen.folderId.takeIf { !isVirtualList(it) } ?: DEFAULT_FOLDER_ID
                    folders.saveShared(q, null, null, f, onError = {}) { id -> openNote(Screen.Editor(id, f)) }
                },
                onTrash = { nav.push(Screen.Notes(TRASH_ID)) },
                onSettings = { nav.push(Screen.Settings) },
                backLabel = if (shown.depth == 1) null else when (val previous = below) {
                    is Screen.Notes -> listName(previous.folderId)
                    is Screen.Editor -> "Back"
                    Screen.Todos -> "To-dos"
                    Screen.Reminders -> "Reminders"
                    Screen.Journal -> "Journal"
                    Screen.Habits -> "Habits"
                    Screen.Settings -> "Settings"
                    else -> "Folders"
                },
                onFolders = { nav.push(Screen.Folders) },
                onBack = ::back,
            )
            is Screen.Editor -> {
                val backLabel = when (below) {
                    is Screen.Notes -> listName(below.folderId)
                    is Screen.Editor -> "Back" // came here through a link
                    Screen.Todos -> "To-dos"
                    Screen.Reminders -> "Reminders"
                    Screen.Journal -> "Journal"
                    Screen.Habits -> "Habits"
                    else -> "Folders"
                }
                EditorScreen(
                    editor, screen.noteId, screen.folderId, rows, fingerprint, backLabel, onBack = ::back,
                    // Save and close the note first; coming back reopens it from the database.
                    onOpenTag = { key -> leaveEditor(); nav.push(Screen.Notes(TAG_PREFIX + key)) },
                    onOpenNote = { note -> editor.close(); nav.push(Screen.Editor(note.id, note.folderId)) },
                    // Replaces this note, so Back goes where this note's Back went.
                    onNewNote = { folder -> editor.close(); nav.pop(); nav.push(Screen.Editor(newId(), folder)) },
                )
            }
            Screen.Settings -> SettingsScreen(settings, onBack = ::back)
        }
    }

    transition.AnimatedContent(
        modifier = Modifier.fillMaxSize().background(jc.bg),
        transitionSpec = {
            val from = initialState
            val to = targetState
            val sameKind = (from.screen is Screen.Editor && to.screen is Screen.Editor) || (from.screen is Screen.Notes && to.screen is Screen.Notes)
            when {
                // Two notes or two lists share a view model, so the old one fades out before it can show the new one's content.
                sameKind || from.depth == to.depth -> fadeIn(tween(200, delayMillis = 90)) togetherWith fadeOut(tween(90))
                to.depth > from.depth -> slideInHorizontally(JMotion.screen()) { it } togetherWith slideOutHorizontally(JMotion.screen()) { -it / 4 }
                else -> slideInHorizontally(JMotion.screen()) { -it / 4 } togetherWith slideOutHorizontally(JMotion.screen()) { it }
            }.apply { targetContentZIndex = to.depth.toFloat() }
        },
    ) { shown ->
    Box(Modifier.fillMaxSize().background(if (shown.screen == Screen.Settings) jc.page else jc.bg)) {
        when {
            shown.split -> Row(Modifier.fillMaxSize()) {
                Box(Modifier.width(listWidth).fillMaxHeight()) { Content(shown) }
                VerticalDivider(color = jc.line)
                // The note beside the list, or a placeholder; one note gives way to the next with a fade (they share a view model).
                val detail = (nav.current as? Screen.Editor)?.let { NavEntry(nav.stack.size, it) }?.takeIf { listAt != null }
                AnimatedContent(
                    detail, Modifier.weight(1f).fillMaxHeight(), label = "detail",
                    transitionSpec = { fadeIn(tween(200, delayMillis = 90)) togetherWith fadeOut(tween(90)) },
                ) { d ->
                    if (d == null) NoNoteOpen {
                        val list = nav.stack.getOrNull(shown.depth - 1) as? Screen.Notes
                        openNote(Screen.Editor(newId(), list?.folderId?.takeIf { !isVirtualList(it) } ?: DEFAULT_FOLDER_ID))
                    } else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        Box(Modifier.widthIn(max = 840.dp).fillMaxHeight()) { Content(d) }
                    }
                }
            }
            // Other screens on a wide window keep a readable width, centred.
            wide -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.widthIn(max = 760.dp).fillMaxHeight()) { Content(shown) }
            }
            else -> Content(shown)
        }
    // A screen on its way in or out takes no taps: a second tap on a note mustn't open it twice.
    if (shown != entry) Box(Modifier.fillMaxSize().pointerInput(Unit) {})
    }
    }
    }
}

/** A screen and how deep in the stack it is: deeper slides in from the right, shallower slides back. [split]: shown with the note beside it. */
internal data class NavEntry(val depth: Int, val screen: Screen, val split: Boolean = false)

/** Two panes with no note open: the right pane says so and offers a new one. */
@Composable
internal fun NoNoteOpen(onNew: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(jc.bg).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        JIcon(Ico.EMPTY_NOTE, jc.muted, size = 40.dp)
        Text("No note open", style = JType.subheading, color = jc.ink)
        Text("Pick a note from the list, or start a new one.", style = JType.para, color = jc.muted, textAlign = TextAlign.Center)
        TextAction("New Note", onNew, bold = true)
    }
}
