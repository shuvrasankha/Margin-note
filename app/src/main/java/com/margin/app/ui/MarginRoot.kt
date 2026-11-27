package com.margin.app.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.material3.TextButton
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
import com.margin.app.data.isLogin
import com.margin.app.data.color
import com.margin.app.data.invoke
import com.margin.app.data.isSmart
import com.margin.app.data.newId
import com.margin.app.data.isTracker
import kotlinx.coroutines.launch
import com.margin.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource

// ---------- Root ----------

@Composable
fun MarginRoot(
    nav: NavViewModel,
    folders: FoldersViewModel,
    notes: NotesViewModel,
    editor: EditorViewModel,
    settings: SettingsViewModel,
    vault: VaultViewModel,
    backups: BackupsViewModel,
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
    val switchPassword: (CharArray) -> kotlinx.coroutines.Job = { code ->
        rootScope.launch {
            editor.saveNow() // an open locked note is saved with the old key before the notes are re-sealed
            settings.changePassword(code).join()
            editor.rekeyed()
            Toast.makeText(context, settings.message ?: context.getString(R.string.margin_root_passcode_set), Toast.LENGTH_LONG).show()
        }
    }
    if (legacy && !skipSwitch) {
        PasscodeDialog(
            PasswordMode.CREATE, onDismiss = { skipSwitch = true },
            intro = stringResource(R.string.root_legacy_passcode_intro),
        ) { code ->
            skipSwitch = true
            switchPassword(code)
            null
        }
    }
    // "Open with Margin" on a PDF: straight to reading it, in the note that holds it (no folder to pick).
    nav.incoming?.takeIf { it.open }?.let { share ->
        LaunchedEffect(share) {
            nav.incoming = null
            val uri = share.image ?: return@LaunchedEffect
            folders.openPdf(uri, onError = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }) { place ->
                editor.openPdf = place
                if ((nav.current as? Screen.Editor)?.noteId == place.noteId) return@openPdf
                if (nav.current is Screen.Editor) editor.close()
                nav.push(Screen.Editor(place.noteId, place.folderId))
            }
        }
    }
    // A list shared back (Share → Text from someone's Margin): update the list of the same name rather than file a copy.
    val allNotes by folders.liveNotes.collectAsState()
    val listMatch = nav.incoming?.takeIf { !it.open }?.let { share -> remember(share, allNotes) { folders.listMatch(share, allNotes) } }
    if (listMatch != null) {
        val share = nav.incoming!!
        val m = listMatch.merge
        val name = listMatch.note.title.ifBlank { listMatch.list.title }
        MarginDialog(
            onDismissRequest = { nav.incoming = null },
            title = { Text(if (m.changed) stringResource(R.string.margin_root_update, name) else stringResource(R.string.margin_root_is_up_to_date, name)) },
            text = {
                Text(
                    if (!m.changed) stringResource(R.string.margin_root_it_already_has_everything_on)
                    else if (m.added > 0 && m.ticked > 0) pluralStringResource(R.plurals.root_share_adds_ticks, m.added, m.added, m.ticked.toString())
                    else if (m.added > 0) pluralStringResource(R.plurals.root_share_adds, m.added, m.added)
                    else pluralStringResource(R.plurals.root_share_ticks, m.ticked, m.ticked),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    nav.incoming = null
                    folders.mergeShared(listMatch) { id, folderId ->
                        if (nav.current is Screen.Editor) editor.close()
                        nav.push(Screen.Editor(id, folderId))
                    }
                }) { Text(if (m.changed) stringResource(R.string.margin_root_update_list) else stringResource(R.string.margin_root_open_list)) }
            },
            dismissButton = { TextButton(onClick = { nav.incoming = share.copy(asNote = true) }) { Text(stringResource(R.string.margin_root_save_as_new_note)) } },
        )
    } else nav.incoming?.takeIf { !it.open }?.let { share ->
        FolderSheet(
            title = stringResource(R.string.margin_root_save_to_margin),
            subject = share.subject?.takeIf { it.isNotBlank() } ?: share.text?.lineSequence()?.firstOrNull { it.isNotBlank() } ?: if (share.pdf) stringResource(R.string.margin_root_pdf) else stringResource(R.string.margin_root_photo),
            subjectNote = if (share.image != null) (if (share.pdf) stringResource(R.string.margin_root_with_a_pdf) else stringResource(R.string.margin_root_with_a_photo)) else null,
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
    // Saved passwords need a passphrase: a 6-digit passcode is guessed offline in hours once the vault syncs or
    // is backed up. On Passwords it's required; elsewhere (saved ones exist) it's asked once per unlock.
    val passphrase by settings.passphrase.collectAsState()
    val unlocked by settings.unlocked.collectAsState()
    val hasLogins by settings.hasLogins.collectAsState()
    var skipVault by remember { mutableStateOf(false) }
    LaunchedEffect(unlocked) { if (!unlocked) skipVault = false }
    val onPasswords = nav.current == Screen.Passwords
    if (unlocked && !passphrase && !(legacy && !skipSwitch) && (onPasswords || (hasLogins && !skipVault))) {
        PasscodeDialog(
            PasswordMode.CREATE, onDismiss = { if (onPasswords) back() else skipVault = true }, passphraseOnly = true,
            intro = stringResource(R.string.root_passwords_need_a_passphrase, com.margin.app.data.MIN_PASSPHRASE),
        ) { phrase -> switchPassword(phrase).join(); null } // the dialog stays busy until the notes are re-sealed
    }
    fun listName(folderId: String) = when {
        folderId == TRASH_ID -> context.getString(R.string.margin_root_recently_deleted)
        folderId == ALL_ID -> context.getString(R.string.margin_root_all_notes)
        folderId == PINNED_ID -> context.getString(R.string.margin_root_pinned)
        folderId == ARCHIVE_ID -> context.getString(R.string.common_archive)
        folderId.startsWith(TAG_PREFIX) -> "#" + (tags.firstOrNull { TAG_PREFIX + it.key == folderId }?.name ?: folderId.removePrefix(TAG_PREFIX))
        else -> rows.firstOrNull { it.folder.id == folderId }?.folder?.name ?: context.getString(R.string.margin_root_notes)
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(jc.bg)) {
    // Tablets and unfolded foldables: the list on the left, the open note beside it. A phone on its side is too
    // short for that and keeps one screen at a time.
    val wide = maxWidth >= 640.dp && maxHeight >= 480.dp
    // A phone on its side: the tabs go down the left so the short height is all content.
    val rail = maxHeight < 480.dp && maxWidth > maxHeight
    val readable = maxWidth >= 640.dp
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
    // Back on another tab goes to Notes first; Back on Notes leaves the app.
    androidx.activity.compose.BackHandler(enabled = nav.stack.size == 1 && Tab.of(nav.current).let { it != null && it != Tab.NOTES }) {
        nav.switchTo(Tab.NOTES.root)
    }
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
    val context = androidx.compose.ui.platform.LocalContext.current
    /** The screen under this one. */
    val below = nav.stack.getOrNull(shown.depth - 2)
    /** A Library screen's back label: the list it was opened from (the home screen's cards), else Library. */
    fun backTo(below: Screen?) = when (below) {
        is Screen.Notes -> listName(below.folderId); Screen.Features -> context.getString(R.string.margin_root_features)
        Screen.Todos -> if (shown.depth == 2) context.getString(R.string.common_today) else context.getString(R.string.margin_root_to_dos); else -> context.getString(R.string.margin_root_library)
    }
        CompositionLocalProvider(LocalTabRoot provides (shown.depth == 1 && Tab.of(shown.screen) != null)) {
        when (val screen = shown.screen) {
            Screen.Folders -> FoldersScreen(
                folders,
                // All Notes is the screen underneath; go back to it rather than stacking another.
                onOpen = {
                    when {
                        it == ALL_ID && nav.stack.size == 1 -> nav.switchTo(Tab.NOTES.root)
                        it == ALL_ID && nav.stack.size == 2 && nav.stack[0] == Screen.Notes(ALL_ID) -> back()
                        else -> nav.push(Screen.Notes(it))
                    }
                },
                onSearch = { nav.push(Screen.Notes(ALL_ID, search = true)) },
                onNew = { nav.push(Screen.Editor(newId(), DEFAULT_FOLDER_ID)) },
                onEdit = { nav.push(Screen.EditFolders) },
                onSettings = { nav.push(Screen.Settings) },
                onTodos = { nav.push(Screen.Todos) },
                onReminders = { nav.push(Screen.Reminders) },
                onJournal = { nav.push(Screen.Journal) },
                onHabits = { nav.push(Screen.Habits) },
                onTrackers = { nav.push(Screen.Trackers) },
                onPasswords = { nav.push(Screen.Passwords) },
                onBack = ::back,
            )
            Screen.EditFolders -> EditFoldersScreen(folders, onBack = ::back)
            Screen.Reminders -> {
                val all by folders.liveNotes.collectAsState()
                val reminders by folders.reminders.collectAsState()
                val repeats by folders.reminderRepeats.collectAsState()
                val places by folders.places.collectAsState()
                RemindersScreen(
                    all, reminders, repeats, onOpen = { if (isHabit(it)) nav.push(Screen.Habits) else openNote(Screen.Editor(it.id, it.folderId)) }, onBack = ::back, backLabel = backTo(below),
                    onTime = folders.remindersOnTime, onJournal = { nav.push(Screen.Journal) },
                    onSet = { note, time, repeat -> folders.setReminder(note.id, time, repeat) }, onRemove = { folders.clearReminder(it.id) },
                    onCreate = folders::newReminder,
                    places = places, onRemovePlace = { folders.clearPlace(it.id) },
                )
            }
            Screen.Journal -> {
                val all by folders.liveNotes.collectAsState()
                val journalReminder by folders.journalReminderAt.collectAsState()
                JournalGate(editor, fingerprint, onCancel = ::back) {
                    // From the journal nudge: straight on to today's entry, once unlocked.
                    LaunchedEffect(Unit) {
                        if (editor.openTodayJournal) {
                            editor.openTodayJournal = false
                            folders.openJournal(java.time.LocalDate.now()) { id, folder -> nav.push(Screen.Editor(id, folder)) }
                        }
                    }
                    JournalScreen(
                        all, onOpenDay = { day -> folders.openJournal(day) { id, folder -> nav.push(Screen.Editor(id, folder)) } }, onDelete = folders::deleteJournal,
                        onBack = ::back, backLabel = backTo(below),
                        reminderAt = journalReminder, onSetReminder = folders::setJournalReminder, onClearReminder = folders::clearJournalReminder,
                    )
                }
            }
            Screen.Passwords -> JournalGate(
                editor, fingerprint, onCancel = ::back,
                intro = stringResource(R.string.margin_root_passwords_are_locked_choose_a, com.margin.app.data.MIN_PASSPHRASE), passphraseOnly = true,
            ) { PasswordsScreen(vault, onBack = ::back, backLabel = backTo(below)) }
            Screen.Habits -> {
                val all by folders.liveNotes.collectAsState()
                HabitsScreen(all, onToggle = folders::toggleHabit, onAdd = folders::addHabit, onRename = folders::renameHabit, onIcon = folders::setHabitIcon, onSchedule = folders::setHabitSchedule, onDelete = folders::deleteHabit, onBack = ::back, backLabel = backTo(below),
                    reminders = folders.reminders.collectAsState().value, onRemind = folders::setHabitReminder)
            }
            Screen.Trackers -> {
                val all by folders.liveNotes.collectAsState()
                TrackersScreen(all, onOpen = { nav.push(Screen.Tracker(it.id)) }, onAdd = folders::addTracker, onSave = folders::saveTracker, onBack = ::back, photos = folders.trackerPhotos)
            }
            is Screen.Tracker -> {
                val all by folders.liveNotes.collectAsState()
                TrackerScreen(
                    all, screen.id, backLabel = when (below) { Screen.Trackers -> stringResource(R.string.margin_root_trackers); is Screen.Notes -> listName(below.folderId); else -> stringResource(R.string.common_back) },
                    onRename = folders::renameTracker, onDelete = folders::deleteTracker, onSave = folders::saveTracker, onBack = ::back, photos = folders.trackerPhotos,
                )
            }
            Screen.Todos -> {
                val all by folders.liveNotes.collectAsState()
                val reminders by folders.reminders.collectAsState()
                // As the Today tab: habits, the journal and reminders too.
                val extras = if (shown.depth != 1) null else remember(all, reminders) {
                    val zone = java.time.ZoneId.systemDefault()
                    val day = java.time.LocalDate.now()
                    val g = todayGlance(all, reminders, day, zone, {}, {}, {})
                    TodayExtras(
                        habits = all.filter(::isHabit).sortedBy { it.createdAt }, onToggleHabit = folders::toggleHabit, onHabits = { nav.push(Screen.Habits) },
                        journaled = g.journaled, onJournal = { nav.push(Screen.Journal) },
                        remindersToday = g.remindersToday, nextReminder = g.nextReminderDay, onReminders = { nav.push(Screen.Reminders) },
                    )
                }
                TodosScreen(
                    all, onOpen = { openNote(Screen.Editor(it.id, it.folderId)) }, onBack = ::back, backLabel = backTo(below),
                    onToggle = folders::toggleTask, onAdd = folders::addTask, onNewList = folders::newTodoList, extras = extras,
                )
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
                onOpen = {
                    // A saved password opens in Passwords; one in Recently Deleted is recovered first.
                    if (isLogin(it)) Toast.makeText(context, if (it.trashedAt == null) context.getString(R.string.margin_root_open_it_in_passwords) else context.getString(R.string.margin_root_recover_it_to_see_it), Toast.LENGTH_SHORT).show()
                    // A tracker's lines have a format the plain editor could break, so it opens in Trackers.
                    else if (isTracker(it)) {
                        if (it.trashedAt == null) nav.push(Screen.Tracker(it.id))
                        else Toast.makeText(context, context.getString(R.string.margin_root_recover_it_to_see_it_2), Toast.LENGTH_SHORT).show()
                    }
                    else openNote(Screen.Editor(it.id, it.folderId))
                },
                onNew = { openNote(Screen.Editor(newId(), screen.folderId.takeIf { !isVirtualList(it) } ?: DEFAULT_FOLDER_ID)) },
                onNewWith = { q ->
                    val f = screen.folderId.takeIf { !isVirtualList(it) } ?: DEFAULT_FOLDER_ID
                    folders.saveShared(q, null, null, f, onError = {}) { id -> openNote(Screen.Editor(id, f)) }
                },
                onTrash = { nav.push(Screen.Notes(TRASH_ID)) },
                onSettings = { nav.push(Screen.Settings) },
                backLabel = if (shown.depth == 1) null else when (val previous = below) {
                    is Screen.Notes -> listName(previous.folderId)
                    is Screen.Editor -> stringResource(R.string.common_back)
                    Screen.Todos -> if (shown.depth == 2) stringResource(R.string.common_today) else stringResource(R.string.margin_root_to_dos)
                    Screen.Reminders -> stringResource(R.string.margin_root_reminders)
                    Screen.Journal -> stringResource(R.string.margin_root_journal)
                    Screen.Habits -> stringResource(R.string.margin_root_habits)
                    Screen.Trackers -> stringResource(R.string.margin_root_trackers)
                    Screen.Settings -> stringResource(R.string.common_settings)
                    else -> stringResource(R.string.margin_root_library)
                },
                onFolders = { nav.push(Screen.Folders) },
                onBack = ::back,
                glance = if (screen.folderId == ALL_ID && shown.depth == 1) {
                    val all by folders.liveNotes.collectAsState()
                    val reminders by folders.reminders.collectAsState()
                    remember(all, reminders) {
                        todayGlance(
                            all, reminders, java.time.LocalDate.now(), java.time.ZoneId.systemDefault(),
                            onTodos = { nav.switchTo(Tab.TODAY.root) }, onReminders = { nav.push(Screen.Reminders) }, onJournal = { nav.push(Screen.Journal) },
                            onHabits = { nav.push(Screen.Habits) },
                        )
                    }
                } else null,
            )
            is Screen.Editor -> {
                val backLabel = when (below) {
                    is Screen.Notes -> listName(below.folderId)
                    is Screen.Editor -> stringResource(R.string.common_back) // came here through a link
                    Screen.Todos -> if (shown.depth == 2) stringResource(R.string.common_today) else stringResource(R.string.margin_root_to_dos)
                    Screen.Reminders -> stringResource(R.string.margin_root_reminders)
                    Screen.Journal -> stringResource(R.string.margin_root_journal)
                    Screen.Habits -> stringResource(R.string.margin_root_habits)
                    Screen.Trackers -> stringResource(R.string.margin_root_trackers)
                    else -> stringResource(R.string.margin_root_library)
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
            Screen.Settings -> SettingsScreen(settings, backups, onBack = ::back, onFeatures = { nav.push(Screen.Features) })
            Screen.Features -> FeaturesScreen(onBack = ::back, onOpen = { nav.push(it) })
        }
        }
    }

    // A tab's root screen: the tab bar sits under it and takes the navigation bar's inset.
    val tab = Tab.of(entry.screen)?.takeIf { entry.depth == 1 }
    Row(Modifier.fillMaxSize()) {
    if (rail) AnimatedVisibility(tab != null, enter = slideInHorizontally { -it }, exit = slideOutHorizontally { -it }) {
        var shownTab by remember { mutableStateOf(Tab.NOTES) }
        if (tab != null) shownTab = tab
        TabBar(shownTab, onSelect = { nav.switchTo(it.root) }, onNew = { nav.push(Screen.Editor(newId(), DEFAULT_FOLDER_ID)) }, vertical = true)
    }
    Column(Modifier.weight(1f).fillMaxHeight()) {
    transition.AnimatedContent(
        modifier = Modifier.weight(1f).fillMaxWidth().background(jc.bg)
            .then(if (tab != null && !rail) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier)
            .then(if (tab != null && rail) Modifier.consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start)) else Modifier),
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
    Box(Modifier.fillMaxSize().background(if (shown.screen == Screen.Settings || shown.screen == Screen.Features) jc.page else jc.bg)) {
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
            readable -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.widthIn(max = 760.dp).fillMaxHeight()) { Content(shown) }
            }
            else -> Content(shown)
        }
    // A screen on its way in or out takes no taps: a second tap on a note mustn't open it twice.
    if (shown != entry) Box(Modifier.fillMaxSize().pointerInput(Unit) {})
    }
    }
    AnimatedVisibility(tab != null && !rail, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
        // Keeps the last tab lit while the bar slides away.
        var shownTab by remember { mutableStateOf(Tab.NOTES) }
        if (tab != null) shownTab = tab
        TabBar(
            shownTab, onSelect = { nav.switchTo(it.root) },
            onNew = { nav.push(Screen.Editor(newId(), DEFAULT_FOLDER_ID)) },
        )
    }
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
        Text(stringResource(R.string.margin_root_no_note_open), style = JType.subheading, color = jc.ink)
        Text(stringResource(R.string.margin_root_pick_a_note_from_the), style = JType.para, color = jc.muted, textAlign = TextAlign.Center)
        TextAction(stringResource(R.string.margin_root_new_note), onNew, bold = true)
    }
}
