package com.margin.app.ui

import androidx.compose.ui.graphics.asAndroidBitmap

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.margin.app.data.AppPrefs
import com.margin.app.data.paperStyle
import com.margin.app.data.fontStyle
import com.margin.app.data.ATTACHMENT_CHAR
import com.margin.app.data.NoteTable
import com.margin.app.data.PDF_MIME
import com.margin.app.data.isPdf
import com.margin.app.data.Attachment
import com.margin.app.data.AttachmentFiles
import com.margin.app.data.DRAWING_MIME
import com.margin.app.data.Drawing
import com.margin.app.data.MarginJson
import com.margin.app.data.isDrawing
import com.margin.app.data.clock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import com.margin.app.data.Appearance
import com.margin.app.data.Toggle
import com.margin.app.data.invoke
import com.margin.app.data.isLocked
import com.margin.app.data.recognizeText
import com.margin.app.data.isPasscode
import com.margin.app.data.backlinks
import com.margin.app.data.Block
import com.margin.app.data.isTemplate
import com.margin.app.data.BlockType
import com.margin.app.data.FolderEntity
import com.margin.app.data.LockSession
import com.margin.app.data.Mark
import com.margin.app.data.Tint
import com.margin.app.data.newId
import com.margin.app.data.writeMarkdownZip
import com.margin.app.data.noteMarkdown
import com.margin.app.data.NotePdf
import com.margin.app.data.safeFileName
import com.margin.app.data.DEFAULT_FOLDER_ID
import com.margin.app.data.sharedNoteBlocks
import com.margin.app.data.LINK_MIME
import com.margin.app.data.hostOf
import com.margin.app.data.fetchPageTitle
import com.margin.app.data.normalizeUrl
import com.margin.app.data.MAX_ATTACHMENT_BYTES
import com.margin.app.data.NoteCrypto
import com.margin.app.data.SmartFilter
import com.margin.app.data.folderTree
import com.margin.app.data.matches
import com.margin.app.data.NoteEntity
import com.margin.app.data.lastEdited
import com.margin.app.data.linkPrefixAt
import com.margin.app.data.NoteRepository
import com.margin.app.data.NoteSort
import com.margin.app.data.SyncPrefs
import com.margin.app.data.TagCount
import com.margin.app.data.tagCounts
import com.margin.app.data.tagKey
import com.margin.app.data.tagKeys
import com.margin.app.data.tagPrefixAt
import com.margin.app.sync.DriveAuthorizer
import com.margin.app.sync.GoogleAccount
import com.margin.app.sync.NeedsUserConsentException
import com.margin.app.sync.SyncManager
import com.margin.app.sync.Connectivity
import com.margin.app.sync.SyncBanner
import com.margin.app.sync.syncBanner
import com.margin.app.sync.conflictOriginal
import com.margin.app.sync.CONFLICT_SUFFIX
import com.margin.app.sync.SyncScheduler
import com.margin.app.sync.signOutOfGoogle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject

// ---------- Navigation ----------

const val TRASH_ID = "trash"
/** A note list for one tag uses folderId = TAG_PREFIX + tag key. */
const val TAG_PREFIX = "tag:"
/** Library lists: every note outside Recently Deleted, and the pinned ones. */
const val ALL_ID = "all"
const val PINNED_ID = "pinned"

/** A list that isn't a stored folder. */
/** Journal entries and habits have their own screens, so All Notes leaves them out (search still finds them). */
fun inAllNotes(note: NoteEntity) = !isHabit(note) && !isTemplate(note) && journalDay(note) == null

fun isVirtualList(id: String) = id == TRASH_ID || id == ALL_ID || id == PINNED_ID || id.startsWith(TAG_PREFIX)

sealed interface Screen {
    data object Folders : Screen
    data class Notes(val folderId: String, val search: Boolean = false) : Screen // folderId == TRASH_ID shows Recently Deleted
    data class Editor(val noteId: String, val folderId: String) : Screen
    data object Settings : Screen
    data object EditFolders : Screen
    /** The to-do progress report. */
    data object Todos : Screen
    /** Notes with a reminder set on this phone. */
    data object Reminders : Screen
    /** The journal calendar: a note per day. */
    data object Journal : Screen
    data object Habits : Screen
}

/** Something another app shared to Margin: text (with an optional subject) and/or a photo. */
data class SharedIn(val subject: String?, val text: String?, val image: android.net.Uri?, val pdf: Boolean = false)

/** Reads a share intent; null for anything else (or a share with nothing usable in it). */
fun sharedIn(intent: android.content.Intent?): SharedIn? {
    // "Open with Margin" on a PDF: a new note holding it.
    if (intent?.action == android.content.Intent.ACTION_VIEW && intent.type == "application/pdf") {
        return intent.data?.let { SharedIn(null, null, it, pdf = true) }
    }
    if (intent?.action != android.content.Intent.ACTION_SEND) return null
    // A shared photo or PDF is copied into the note.
    val image = if (intent.type?.startsWith("image/") == true || intent.type == "application/pdf")
        androidx.core.content.IntentCompat.getParcelableExtra(intent, android.content.Intent.EXTRA_STREAM, android.net.Uri::class.java) else null
    val text = intent.getStringExtra(android.content.Intent.EXTRA_TEXT)
    if (image == null && text.isNullOrBlank()) return null
    return SharedIn(intent.getStringExtra(android.content.Intent.EXTRA_SUBJECT), text, image, pdf = image != null && intent.type == "application/pdf")
}

class NavViewModel : ViewModel() {
    // All Notes is the home screen: Back there leaves the app; its Folders button opens the folders.
    val stack = mutableStateListOf<Screen>(Screen.Notes(ALL_ID))
    /** A share waiting for its folder; kept here so it survives rotation. */
    var incoming by mutableStateOf<SharedIn?>(null)
    val current get() = stack.last()
    fun push(screen: Screen) { stack.add(screen) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
}

// ---------- Folders ----------

/** A folder as the folders screen shows it: [depth] for nesting; for a smart folder, [count] is its matches. */
data class FolderRow(val folder: FolderEntity, val count: Int, val depth: Int = 0)

@HiltViewModel
class FoldersViewModel @Inject constructor(
    private val repo: NoteRepository,
    private val appPrefs: AppPrefs,
    private val files: AttachmentFiles,
    syncPrefs: SyncPrefs,
    connectivity: Connectivity,
    private val scheduler: SyncScheduler,
    private val alarms: com.margin.app.data.Reminders,
) : ViewModel() {
    /** Note id → when its reminder rings, for the Reminders list. */
    val reminders: StateFlow<Map<String, Long>> = alarms.all
    val reminderRepeats: StateFlow<Map<String, com.margin.app.data.Repeat>> = alarms.repeats

    /** Offline with changes waiting, or the last sync failed. Null when there's nothing to say. */
    val syncBanner: StateFlow<SyncBanner?> = combine(syncPrefs.status, connectivity.online, repo.pendingCount()) { s, online, pending ->
        syncBanner(s.signedIn, online, pending, s.lastError)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun retrySync() = scheduler.requestNow()

    /**
     * "Save to Margin": a new note in [folderId] from shared text and/or a shared photo. [onSaved] gets its id;
     * a photo that can't be copied (too big, unreadable) is reported and the text is still saved.
     */
    fun saveShared(subject: String?, text: String?, image: android.net.Uri?, folderId: String, onError: (String) -> Unit, onSaved: (String) -> Unit) {
        viewModelScope.launch {
            val attachment = image?.let {
                try { files.import(it) } catch (e: Exception) { onError(e.message ?: "Couldn't copy the file"); null }
            }
            if (attachment == null && text.isNullOrBlank() && subject.isNullOrBlank()) return@launch
            val id = newId()
            repo.saveNote(id, folderId, sharedNoteBlocks(subject, text, attachment))
            onSaved(id)
        }
    }

    /** Opens [day]'s journal entry, starting it (titled with the date, in Notes) when there isn't one. */
    fun openJournal(day: java.time.LocalDate, onReady: (id: String, folderId: String) -> Unit) {
        viewModelScope.launch {
            val id = journalId(day)
            // shortcut: an entry deleted for good can't be started again (saveNote keeps tombstones); give it a new id if that comes up.
            // A deleted entry comes back from Recently Deleted rather than leaving the day stuck on it.
            if (repo.getNote(id)?.trashedAt != null) repo.recover(id)
            val note = repo.getNote(id) ?: repo.saveNote(id, DEFAULT_FOLDER_ID, sharedNoteBlocks(journalTitle(day), null, null)).let { repo.getNote(id) }
            onReady(id, note?.folderId ?: DEFAULT_FOLDER_ID)
        }
    }

    fun addHabit(name: String) = viewModelScope.launch {
        if (name.isNotBlank()) repo.saveNote("habit-" + newId(), DEFAULT_FOLDER_ID, listOf(Block(type = BlockType.TITLE, text = name.trim())))
    }
    /** Ticks [day] off for the habit [note], or unticks it: the body is rewritten as one line per day, newest first. */
    fun toggleHabit(note: NoteEntity, day: java.time.LocalDate) = viewModelScope.launch {
        val days = habitDays(note).let { if (day in it) it - day else it + day }.sortedDescending()
        repo.saveNote(note.id, note.folderId, listOf(note.blocks.first()) + days.map { Block(text = it.toString()) })
    }
    fun renameHabit(note: NoteEntity, name: String) = viewModelScope.launch {
        if (name.isNotBlank()) repo.saveNote(note.id, note.folderId, listOf(note.blocks.first().copy(text = name.trim())) + note.blocks.drop(1))
    }
    fun deleteHabit(note: NoteEntity) = viewModelScope.launch { alarms.clear(note.id); repo.moveToTrash(note.id) }
    /** Rings the habit [note] every day at [at], or no more for null. */
    fun setHabitReminder(note: NoteEntity, at: java.time.LocalTime?) {
        if (at == null) alarms.clear(note.id)
        else alarms.set(note.id, nextAt(at, java.time.ZonedDateTime.now()).toInstant().toEpochMilli(), com.margin.app.data.Repeat.DAILY)
    }

    /** Moves [day]'s journal entry to Recently Deleted. */
    fun deleteJournal(day: java.time.LocalDate) = viewModelScope.launch { repo.moveToTrash(journalId(day)) }

    val rows: StateFlow<List<FolderRow>> = combine(repo.liveFolders(), repo.folderCounts(), repo.liveNotes()) { folders, counts, notes ->
        val byId = counts.associate { it.folderId to it.count }
        val now = System.currentTimeMillis()
        folderTree(folders).map { (folder, depth) ->
            val count = folder.smartFilter?.let { f -> notes.count { f.matches(it, now) } } ?: byId[folder.id] ?: 0
            FolderRow(folder, count, depth)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val trashCount: StateFlow<Int> = repo.trashCount().stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** Library counts: all notes and pinned notes outside Recently Deleted. */
    /** Every note outside Recently Deleted, for the to-do report. */
    val liveNotes: StateFlow<List<NoteEntity>> = repo.liveNotes().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val libraryCounts: StateFlow<Pair<Int, Int>> = repo.liveNotes().map { all -> all.count(::inAllNotes) to all.count { it.isPinned } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0 to 0)

    /** Tags used in notes outside Recently Deleted, with note counts. */
    val tags: StateFlow<List<TagCount>> = repo.liveNotes().map(::tagCounts)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun create(name: String, parentId: String? = null, smartFilter: SmartFilter? = null) = viewModelScope.launch {
        val (sortBy, ascending) = appPrefs.defaultSort.value
        if (name.isNotBlank()) repo.createFolder(name, sortBy, ascending, parentId, smartFilter)
    }
    fun setSmartFilter(id: String, filter: SmartFilter) = viewModelScope.launch { repo.setSmartFilter(id, filter) }
    fun moveFolder(id: String, parentId: String?) = viewModelScope.launch { repo.moveFolder(id, parentId) }
    fun rename(id: String, name: String) = viewModelScope.launch { repo.renameFolder(id, name) }
    fun reorder(ids: List<String>) = viewModelScope.launch { repo.reorderFolders(ids) }
    fun delete(id: String) = viewModelScope.launch { repo.deleteFolder(id) }
    fun renameTag(key: String, newName: String) = viewModelScope.launch { repo.renameTag(key, newName.trim().removePrefix("#")) }
}

// ---------- Note list / search / Recently Deleted ----------

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NotesViewModel @Inject constructor(
    private val repo: NoteRepository,
    private val appPrefs: AppPrefs,
    syncPrefs: SyncPrefs,
) : ViewModel() {
    /** Two panes: the note open beside the list, shown tinted in it. Null on a phone. */
    var openId by mutableStateOf<String?>(null)

    /** Notes show "waiting to sync" only when there is a Drive to sync to. */
    val signedIn: StateFlow<Boolean> = syncPrefs.status.map { it.signedIn }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val folderId = MutableStateFlow<String?>(null)
    val query = MutableStateFlow("")

    /** Every note in the open list, before the filter pills. */
    private val listed: StateFlow<List<NoteEntity>> = combine(folderId, query) { f, q -> f to q.trim() }
        .flatMapLatest { (f, q) ->
            when {
                f == null -> flowOf(emptyList())
                f == TRASH_ID -> repo.trash()
                q.isNotEmpty() -> combine(repo.search(q), appPrefs.toggles) { found, t -> // all folders, excludes Recently Deleted
                    (if (t(Toggle.HIDE_LOCKED_IN_SEARCH)) found.filterNot { it.isLocked } else found).filterNot(::isTemplate)
                }
                f == ALL_ID -> repo.liveNotes().map { all -> all.filter(::inAllNotes) }
                f == PINNED_ID -> repo.liveNotes().map { all -> all.filter { it.isPinned } }
                f.startsWith(TAG_PREFIX) -> repo.liveNotes().map { all ->
                    val key = f.removePrefix(TAG_PREFIX)
                    all.filter { key in it.tagKeys && !isTemplate(it) }
                }
                else -> repo.folder(f).flatMapLatest { folder ->
                    val filter = folder?.smartFilter
                    if (filter == null) repo.notesIn(f)
                    else repo.liveNotes().map { all -> all.filter { filter.matches(it, System.currentTimeMillis()) } }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The filter pill above the list. Not used in Recently Deleted. */
    val filter = MutableStateFlow(ListFilter.ALL)

    /** True when the list has notes before filtering, so an empty filter result can say so. */
    val hasNotes: StateFlow<Boolean> = listed.map { it.isNotEmpty() }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** The filter pills are hidden while searching, so they don't narrow the results. */
    val notes: StateFlow<List<NoteEntity>> = combine(listed, filter, folderId, query) { list, filter, f, q ->
        if (f == TRASH_ID || q.isNotBlank()) list else list.filter(filter::matches)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Search: the scope pill, and the results split into notes and attachments. */
    val searchScope = MutableStateFlow(SearchScope.TOP)
    /** Text read from photos and PDFs that contains the query, by attachment file. */
    private val textHits = query.map { it.trim() }.distinctUntilChanged().flatMapLatest { q ->
        if (q.isEmpty()) flowOf(emptyMap()) else repo.attachmentTextMatching(q).map { rows -> rows.associate { it.file to it.text } }
    }
    val searchResults: StateFlow<SearchResults> = combine(notes, query, textHits, ::searchResults)
        .stateIn(viewModelScope, SharingStarted.Eagerly, SearchResults(emptyList(), emptyList()))

    /** The open folder (null for Recently Deleted); its sort setting drives the list. */
    val folder: StateFlow<FolderEntity?> = folderId
        .flatMapLatest { f -> if (f == null || isVirtualList(f)) flowOf(null) else repo.folder(f) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val entries: StateFlow<List<ListEntry>> = combine(
        notes, folder, folderId, combine(query, searchResults, searchScope, ::Triple), combine(appPrefs.collapsedPinned, appPrefs.toggles, ::Pair),
    ) { list, folder, f, (q, results, scope), (collapsed, toggles) ->
        if (q.isNotBlank() && f != TRASH_ID) results.entries(scope) else buildListing(
            list,
            sortBy = folder?.sortBy ?: NoteSort.EDITED,
            ascending = folder?.sortAscending ?: false,
            isTrash = f == TRASH_ID,
            now = System.currentTimeMillis(),
            zone = ZoneId.systemDefault(),
            locale = Locale.getDefault(),
            pinnedCollapsed = f in collapsed,
            groupByDate = toggles(Toggle.GROUP_BY_DATE),
            pinnedSection = f != PINNED_ID,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Ids of the notes picked by long-press; non-empty means the list is in selection mode. */
    var selected by mutableStateOf(emptySet<String>()); private set

    fun show(folder: String) {
        if (folderId.value != folder) {
            query.value = ""
            searchScope.value = SearchScope.TOP
            selected = emptySet()
            filter.value = ListFilter.ALL
            undo = null
        }
        folderId.value = folder
    }

    fun toggleSelected(id: String) { selected = if (id in selected) selected - id else selected + id }
    fun clearSelection() { selected = emptySet() }

    fun togglePinnedCollapsed() { folderId.value?.let(appPrefs::togglePinnedCollapsed) }

    /** Pins the selection, or unpins it when every selected note is already pinned. */
    fun pinSelected() {
        val ids = selected
        val allPinned = notes.value.filter { it.id in ids }.all { it.isPinned }
        viewModelScope.launch { repo.setPinned(ids, !allPinned) }
        clearSelection()
    }

    fun trashSelected() {
        val ids = selected
        offerUndo(ids, if (ids.size == 1) "Note deleted" else "${ids.size} notes deleted")
        viewModelScope.launch { repo.moveToTrash(ids) }
        clearSelection()
    }

    val keepDeletedDays = appPrefs.keepDeletedDays

    /** Every note outside Recently Deleted, for "Move existing notes here". */
    val allNotes: StateFlow<List<NoteEntity>> = repo.liveNotes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun recoverSelected() {
        val ids = selected
        viewModelScope.launch { ids.forEach { repo.recover(it) } }
        clearSelection()
    }

    fun deleteSelectedForever() {
        val ids = selected
        viewModelScope.launch { ids.forEach { repo.deleteForever(it) } }
        clearSelection()
    }

    /** Moves [ids] (the selection, a swiped note or notes picked for an empty folder) with an undo offer. */
    fun moveNotes(ids: Set<String>, folderId: String, folderName: String, pool: List<NoteEntity> = notes.value) {
        offerUndo(ids, (if (ids.size == 1) "Moved to " else "${ids.size} notes moved to ") + folderName, pool)
        viewModelScope.launch { repo.moveNotes(ids, folderId) }
        clearSelection()
    }

    /** The last delete or move, which the list offers to undo for a few seconds. */
    var undo by mutableStateOf<UndoAction?>(null); private set

    private fun offerUndo(ids: Collection<String>, message: String, pool: List<NoteEntity> = notes.value) {
        val before = pool.filter { it.id in ids }
        undo = if (before.isEmpty()) null else UndoAction(message, before)
    }

    fun undo(action: UndoAction) {
        if (undo === action) undo = null
        viewModelScope.launch { repo.restoreState(action.before) }
    }

    fun undoExpired(action: UndoAction) { if (undo === action) undo = null }

    /** Saved templates, for a folder's "New notes start from" choice. */
    val templates: StateFlow<List<NoteEntity>> = repo.templates().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** New notes in the open folder start from template [key] (null: blank). */
    fun setFolderTemplate(key: String?) {
        val f = folderId.value ?: return
        if (!isVirtualList(f)) viewModelScope.launch { repo.setFolderTemplate(f, key) }
    }

    fun setSort(sortBy: NoteSort, ascending: Boolean) {
        val f = folderId.value ?: return
        if (!isVirtualList(f)) viewModelScope.launch { repo.setFolderSort(f, sortBy, ascending) }
    }

    fun togglePin(id: String) = viewModelScope.launch { repo.togglePin(id) }
    fun trash(id: String) {
        offerUndo(listOf(id), "Note deleted")
        viewModelScope.launch { repo.moveToTrash(id) }
    }
    fun emptyTrash() = viewModelScope.launch { repo.emptyTrash() }
}

/** Filter pills above a note list. Locked notes never count as checklists: their body can't be read. */
enum class ListFilter(val label: String) {
    ALL("All"), PINNED("Pinned"), CHECKLISTS("Checklists");

    fun matches(note: NoteEntity) = when (this) {
        ALL -> true
        PINNED -> note.isPinned
        CHECKLISTS -> note.blocks.any { it.type == BlockType.CHECK }
    }
}

/** [before] is each note as it was, so undo can put it back. */
class UndoAction(val message: String, val before: List<NoteEntity>)

// ---------- Editor ----------

/** Where OCR is: reading the photo, the text it found (to go after line [afterLine], or the cursor's), or why it failed. */
sealed interface ScanState {
    data object Reading : ScanState
    data class Found(val text: String, val afterLine: Int?) : ScanState
    data class Failed(val message: String) : ScanState
}

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val repo: NoteRepository,
    private val appPrefs: AppPrefs,
    private val session: LockSession,
    val files: AttachmentFiles,
    private val reminders: com.margin.app.data.Reminders,
    val ai: com.margin.app.data.AiKeys,
) : ViewModel() {
    companion object {
        const val START_SCAN = "scan"
        const val START_VOICE = "voice"
    }

    private val openId = MutableStateFlow("")

    /** When this note's reminder rings, or null; changes as reminders are set, snoozed and rung. */
    val reminderAt: StateFlow<Long?> = combine(reminders.all, openId) { all, id -> all[id] }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Sets the reminder; a new note is saved first (an empty one can't have a reminder). */
    fun setReminder(time: Long, repeat: com.margin.app.data.Repeat) { if (noteId.isNotEmpty() && !readOnly) afterSave { reminders.set(it, time, repeat) } }

    /** How this note's reminder repeats. */
    val reminderRepeat: StateFlow<com.margin.app.data.Repeat> = combine(reminders.repeats, openId) { all, id -> all[id] ?: com.margin.app.data.Repeat.NEVER }
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.margin.app.data.Repeat.NEVER)

    /** A reminder can be set: the note is stored, or has text that will be saved first. */
    val canRemind get() = !readOnly && !needsUnlock && (exists || doc.text.isNotBlank())
    fun clearReminder() { if (noteId.isNotEmpty()) reminders.clear(noteId) }
    val remindersOnTime get() = reminders.canRingOnTime()
    /** Whether a ringing reminder may fill the screen (Android 14+ asks the user for this). */
    val remindersFullScreen get() = reminders.canFillScreen()
    /** Asks for the full-screen permission once only, so declining isn't met with the same screen every time. */
    fun shouldAskFullScreen() = !remindersFullScreen && reminders.markAskedFullScreen()

    /** Tags from the other notes; this note's half-typed tag must not suggest itself. */
    val otherTags: StateFlow<List<TagCount>> = combine(repo.liveNotes(), openId) { all, id -> tagCounts(all.filter { it.id != id }) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Line styles; [value] holds the same text plus cursor and IME state for the single text field. */
    /** Notes a link can lead to (everything outside Recently Deleted). */
    val linkTargets: StateFlow<List<NoteEntity>> = repo.liveNotes().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var current by mutableStateOf(EditorDoc.fromBlocks(emptyList()))
    /** Every change goes through here, so the doc before it can be undone. Typing within a second counts as one step. */
    var doc: EditorDoc
        get() = current
        private set(next) {
            if (next == current) return
            val now = android.os.SystemClock.uptimeMillis()
            if (history.isEmpty() || now - lastChangeAt > 1000) history.addLast(current)
            if (history.size > 100) history.removeFirst()
            lastChangeAt = now
            current = next
            canUndo = true
        }
    private val history = ArrayDeque<EditorDoc>()
    private var lastChangeAt = 0L
    var canUndo by mutableStateOf(false); private set

    /** Back to the doc before the last step; the cursor goes to where that step changed the text. */
    fun undo() {
        if (readOnly || needsUnlock) return
        val previous = history.removeLastOrNull() ?: return
        val now = current.text
        var suffix = 0
        while (suffix < now.length && suffix < previous.text.length && now[now.length - 1 - suffix] == previous.text[previous.text.length - 1 - suffix]) suffix++
        current = previous
        canUndo = history.isNotEmpty()
        lastChangeAt = 0
        value = TextFieldValue(previous.text, TextRange(previous.text.length - suffix))
        changed()
    }

    /** False while an edit waits to be written; the editor then doesn't say "Saved". */
    var saved by mutableStateOf(true); private set
    /** The note is stored (not a new, still-empty one). */
    var exists by mutableStateOf(false); private set

    /** Open note is a sync conflict copy: the note it was split from. Null otherwise (or when either is locked). */
    var conflictWith by mutableStateOf<NoteEntity?>(null); private set

    /** Conflict chooser: this copy's text replaces the original's (which keeps its title); the copy goes to Recently Deleted. */
    fun keepThisVersion(onDone: () -> Unit) {
        val original = conflictWith ?: return
        val blocks = doc.toBlocks().let { b -> listOf(b.first().copy(text = original.title)) + b.drop(1) }
        conflictWith = null
        viewModelScope.launch { repo.saveNote(original.id, original.folderId, blocks) }
        delete(onDone)
    }

    /** Conflict chooser: keep the original as it is; this copy goes to Recently Deleted. */
    fun keepOtherVersion(onDone: () -> Unit) {
        conflictWith = null
        delete(onDone)
    }

    /** Conflict chooser: keep both notes; this one just loses its "(conflict)" title. */
    fun keepBothVersions() {
        if (conflictWith == null || readOnly) return
        conflictWith = null
        val blocks = doc.toBlocks()
        doc = EditorDoc.fromBlocks(listOf(blocks.first().copy(text = blocks.first().text.removeSuffix(CONFLICT_SUFFIX))) + blocks.drop(1))
        value = TextFieldValue(doc.text, TextRange(0))
        changed()
    }
    var pinned by mutableStateOf(false); private set
    /** When the note was created; null until a new note is first saved. */
    var createdAt by mutableStateOf<Long?>(null); private set
    /** The open note's to-do target end date (an epoch day), or null. */
    var targetDate by mutableStateOf<Long?>(null); private set
    /** The paper behind the note's text. */
    var paper by mutableStateOf(com.margin.app.data.Paper.PLAIN); private set
    var font by mutableStateOf(com.margin.app.data.NoteFont.DEFAULT); private set
    var value by mutableStateOf(TextFieldValue()); private set
    var readOnly by mutableStateOf(false); private set
    var loaded by mutableStateOf(false); private set
    /** True right after a new note opens, so the UI puts the keyboard on the title. */
    var focusOnOpen by mutableStateOf(false)
    var focused by mutableStateOf(false)
    /** The math keypad shows in place of the phone's keyboard. */
    var mathKeys by mutableStateOf(false)

    /** The note is locked (its body is stored encrypted). */
    var locked by mutableStateOf(false); private set
    /** Locked and not opened yet: only the title can be shown. */
    var needsUnlock by mutableStateOf(false); private set
    var unlockError by mutableStateOf<String?>(null)
    var unlocking by mutableStateOf(false); private set
    private var sealed: String? = null
    private var lockParams: NoteCrypto.KeyParams? = null

    init {
        // When the app locks (it went to the background), hide an open locked note's body again.
        viewModelScope.launch {
            session.unlocked.collect { unlocked ->
                if (!unlocked && locked && !needsUnlock) hideBody()
                if (!unlocked) files.forgetRevealed() // no decrypted copies once the notes lock
            }
        }
    }

    private var noteId = ""
    var folderId by mutableStateOf(""); private set
    /** When the open note was last edited; null for a new note. */
    var editedAt by mutableStateOf<Long?>(null); private set
    private var isNew = false
    /** Title when the note was opened; links elsewhere follow a rename when the note is closed. */
    private var titleAtOpen = ""
    private var lastSaved: List<Block> = emptyList()
    private var saveJob: Job? = null

    fun open(noteId: String, folderId: String) {
        if (noteId == this.noteId) return
        this.noteId = noteId
        this.folderId = folderId
        openId.value = noteId
        loaded = false
        focused = false
        viewModelScope.launch {
            val note = repo.getNote(noteId)
            isNew = note == null
            note?.let { this@EditorViewModel.folderId = it.folderId }
            editedAt = note?.lastEdited
            exists = note != null
            pinned = note?.isPinned == true
            createdAt = note?.createdAt
            targetDate = note?.targetDate
            paper = note?.paperStyle ?: com.margin.app.data.Paper.PLAIN
            font = note?.fontStyle ?: com.margin.app.data.NoteFont.DEFAULT
            conflictWith = note?.takeIf { it.trashedAt == null && !it.isLocked }
                ?.let { conflictOriginal(it, repo.liveNotes().first()) }?.takeIf { !it.isLocked }
            saved = true
            titleAtOpen = note?.title.orEmpty()
            val stored = note?.blocks.orEmpty()
            sealed = note?.cipher
            locked = sealed != null
            lockParams = sealed?.let(NoteCrypto::paramsOf)
            unlockError = null
            val body = sealed?.let { session.open(it) }
            body?.let { files.reveal(it) }
            needsUnlock = locked && body == null
            // A new note in a folder with a template starts from it; left untouched, it still isn't saved.
            val start = if (isNew && !noteId.startsWith("journal-") && !noteId.startsWith("habit-"))
                repo.folder(this@EditorViewModel.folderId).first()?.template?.let { repo.templateBody(it) } else null
            show(if (start != null) listOf(Block(type = BlockType.TITLE)) + start else if (body != null) stored.take(1) + body else stored)
            if (start != null) value = TextFieldValue(doc.text, TextRange(0)) // type the title first
            if (isNew) lastSaved = doc.toBlocks()
            readOnly = note?.trashedAt != null
            focusOnOpen = isNew
            loaded = true
            // Journal entries are always locked notes (the journal itself asks for the passcode first).
            if (noteId.startsWith("journal-") && !locked && !readOnly && session.unlocked.value) lock()
        }
    }

    private fun show(blocks: List<Block>) {
        current = EditorDoc.fromBlocks(blocks)
        history.clear()
        canUndo = false
        value = TextFieldValue(doc.text, TextRange(doc.text.length))
        lastSaved = blocks
    }

    private fun hideBody() {
        saveJob?.cancel()
        needsUnlock = true
        focused = false
        show(lastSaved.take(1))
    }

    /** Tries the notes password on this note; success unlocks every locked note until the app goes to the background. */
    fun unlock(password: CharArray) {
        val cipher = sealed ?: return
        viewModelScope.launch {
            unlocking = true
            val wasPasscode = isPasscode(password)
            val body = session.unlock(password, cipher)
            password.fill('\u0000')
            unlocking = false
            if (body == null) {
                unlockError = if (wasPasscode) "Wrong passcode" else "Wrong password"
            } else {
                unlockError = null
                files.reveal(body)
                needsUnlock = false
                show(lastSaved.take(1) + body)
            }
        }
    }

    /** Whether a notes password already exists (some note is locked with it). */
    suspend fun hasPassword() = session.unlocked.value || repo.lockedNotes().isNotEmpty()

    val sessionUnlocked get() = session.unlocked

    /**
     * Checks [password] against an existing locked note, or, when there is none yet, makes it the notes
     * password. Returns false if it's wrong.
     */
    suspend fun enterPassword(password: CharArray): Boolean {
        val sample = repo.lockedNotes().firstOrNull()?.cipher
        val ok = if (sample == null) { session.startWith(password); true } else session.unlock(password, sample) != null
        password.fill('\u0000')
        return ok
    }


    /** Copies a picked file in and adds it on its own line after the cursor's line. */
    fun attach(uri: android.net.Uri, onError: (String) -> Unit) {
        if (readOnly || needsUnlock) return
        viewModelScope.launch {
            val a = try { files.import(uri) } catch (e: Exception) { onError(e.message ?: "Couldn't attach the file"); return@launch }
            insertAttachment(a)
        }
    }

    /** Adds a scanned document (the scanner's PDF at [uri]) after the cursor's line, named for when it was scanned. */
    fun addScan(context: android.content.Context, uri: android.net.Uri, onError: (String) -> Unit) {
        if (readOnly || needsUnlock) return
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { it.readBytes() } }
                val stamp = java.text.SimpleDateFormat("d MMM yyyy, HH.mm", java.util.Locale.getDefault()).format(java.util.Date())
                insertAttachment(files.write(bytes, "pdf", "Scan $stamp.pdf", com.margin.app.data.PDF_MIME))
            } catch (e: Exception) {
                onError(e.message ?: "Couldn't add the scan")
            }
        }
    }

    /** The line id of the table open in the table editor, or null. */
    var editingTable by mutableStateOf<String?>(null)

    /** Adds an empty table on its own line after the cursor's line and opens it in the table editor. */
    fun insertTable() {
        if (readOnly || needsUnlock) return
        val blocks = doc.toBlocks().toMutableList()
        val at = cursorLine + 1
        blocks.add(at, Block(text = ATTACHMENT_CHAR.toString(), table = NoteTable.blank()))
        if (at == blocks.lastIndex) blocks.add(Block())
        doc = EditorDoc.fromBlocks(blocks)
        value = TextFieldValue(doc.text, TextRange(doc.lineStart(at + 1)))
        changed()
        editingTable = doc.lines[at].id
    }

    /** Saved templates, for Add to note. */
    val templates: StateFlow<List<NoteEntity>> = repo.templates().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** True when this note can be saved as a template: open, readable and not itself a template. */
    val canSaveTemplate get() = loaded && !needsUnlock && !locked && !isTemplate(noteId) && doc.toBlocks().any { it.text.isNotBlank() }

    /** Saves this note (without attachments) as a template, then calls [onDone] with its name. */
    fun saveAsTemplate(onDone: (String) -> Unit) {
        if (!canSaveTemplate) return
        val blocks = doc.toBlocks()
        viewModelScope.launch {
            repo.saveTemplate(blocks)
            onDone(blocks.first().text.ifBlank { "Untitled" })
        }
    }

    /** Adds saved template [id]'s body after the cursor's line. */
    fun insertSavedTemplate(id: String) {
        if (readOnly || needsUnlock) return
        viewModelScope.launch { repo.templateBody(id)?.let { insertBlocks(it) } }
    }

    /** Moves saved template [id] to Recently Deleted. */
    fun deleteTemplate(id: String) = viewModelScope.launch { repo.moveToTrash(id) }

    private fun insertBlocks(body: List<Block>) {
        if (body.isEmpty()) return
        val blocks = doc.toBlocks().toMutableList()
        val at = cursorLine + 1
        blocks.addAll(at, body)
        doc = EditorDoc.fromBlocks(blocks)
        value = TextFieldValue(doc.text, TextRange(doc.lineStart(at + 1)))
        changed()
    }

    /** Adds [template]'s skeleton after the cursor's line, with the cursor on its first empty line. */
    fun insertTemplate(template: com.margin.app.data.NoteTemplate) {
        if (readOnly || needsUnlock) return
        insertBlocks(template.blocks())
    }

    /** Replaces the table on the line with id [lineId] (an edit from the table editor; Undo brings the old one back). */
    fun updateTable(lineId: String, table: NoteTable) {
        if (readOnly) return
        val at = doc.lines.indexOfFirst { it.id == lineId && it.table != null }
        if (at < 0 || doc.lines[at].table == table) return
        doc = EditorDoc.fromBlocks(doc.toBlocks().mapIndexed { i, b -> if (i == at) b.copy(table = table.normalized()) else b })
        changed()
    }

    private fun insertAttachment(a: Attachment) {
        val blocks = doc.toBlocks().toMutableList()
        val at = cursorLine + 1
        blocks.add(at, Block(text = ATTACHMENT_CHAR.toString(), attachment = a))
        if (at == blocks.lastIndex) blocks.add(Block()) // a line to keep typing on
        doc = EditorDoc.fromBlocks(blocks)
        value = TextFieldValue(doc.text, TextRange(doc.lineStart(at + 1)))
        changed()
    }

    /** Moves a finished recording into the attachments, after the cursor's line. */
    fun saveRecording(file: java.io.File, ms: Long, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                if (readOnly || needsUnlock) return@launch
                // shortcut: copies through memory; fine up to the 30-minute cap (~14 MB).
                val bytes = withContext(Dispatchers.IO) { file.readBytes() }
                insertAttachment(files.write(bytes, "m4a", "Voice note (${clock(ms)})", "audio/mp4"))
            } catch (e: Exception) {
                onError(e.message ?: "Couldn't save the recording")
            } finally {
                withContext(Dispatchers.IO) { file.delete() }
            }
        }
    }

    /** Saves a drawing: a new one goes after the cursor's line; an edited one ([line]) replaces its old file. */
    fun saveDrawing(drawing: Drawing, line: Int?, onError: (String) -> Unit) {
        if (readOnly || needsUnlock) return
        viewModelScope.launch {
            val a = try {
                files.write(MarginJson.encodeToString(drawing).toByteArray(), "json", "Drawing", DRAWING_MIME)
            } catch (e: Exception) { onError(e.message ?: "Couldn't save the drawing"); return@launch }
            if (line == null || doc.lines.getOrNull(line)?.attachment?.isDrawing != true) { insertAttachment(a); return@launch }
            doc = EditorDoc.fromBlocks(doc.toBlocks().mapIndexed { i, b -> if (i == line) b.copy(attachment = a) else b })
            changed()
        }
    }

    /** OCR in progress or done: shown as a dialog to check the text before it goes into the note. */
    var scan by mutableStateOf<ScanState?>(null); private set
    private var pendingScan: java.io.File? = null
    private var scanJob: kotlinx.coroutines.Job? = null

    /** Where the camera should save a photo taken only to read its text (it's deleted afterwards). */
    fun scanTarget(): android.net.Uri {
        val (file, uri) = files.newPhotoTarget()
        pendingScan = file
        return uri
    }

    /** Camera result for Scan text: reads the photo's text, then drops the photo. */
    fun scanTaken(ok: Boolean, context: android.content.Context) {
        val file = pendingScan ?: return
        pendingScan = null
        if (!ok || readOnly) { viewModelScope.launch(Dispatchers.IO) { file.delete() }; return }
        read(context, android.net.Uri.fromFile(file), afterLine = null) { withContext(Dispatchers.IO) { file.delete() } }
    }

    /** Reads the text in the photo on line [line], to add below it. */
    fun scanPhoto(line: Int, context: android.content.Context) {
        val a = doc.lines.getOrNull(line)?.attachment?.takeIf { it.isImage } ?: return
        val file = files.file(a)
        if (!file.exists()) { scan = ScanState.Failed("This photo isn't downloaded yet. It arrives with the next sync."); return }
        read(context, android.net.Uri.fromFile(file), afterLine = line) {}
    }

    private fun read(context: android.content.Context, uri: android.net.Uri, afterLine: Int?, cleanUp: suspend () -> Unit) {
        scan = ScanState.Reading
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            scan = try {
                val text = recognizeText(context, uri)
                if (text.isBlank()) ScanState.Failed("No text found. Try a sharper photo with the text filling more of it.")
                else ScanState.Found(text, afterLine)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                ScanState.Failed("Couldn't read the photo: " + (e.message ?: "unknown error"))
            } finally {
                cleanUp()
            }
        }
    }

    fun dismissScan() { scanJob?.cancel(); scan = null }

    /** Adds [text] as note lines after line [afterLine] (default: the cursor's line) and puts the cursor after it. */
    fun insertText(text: String, afterLine: Int? = null, ai: Boolean = false) {
        scan = null
        if (readOnly || needsUnlock || text.isBlank()) return
        val blocks = doc.toBlocks().toMutableList()
        addLines(blocks, (afterLine ?: cursorLine) + 1, text, ai)
    }

    // ---------- PDF tools ----------

    /** What a PDF tool is doing ("Merging…"), shown in the viewer; null when idle. */
    var pdfBusy by mutableStateOf<String?>(null); private set

    /** What a PDF tool made: a copy to replace the PDF with (it keeps its name), new PDFs to add under it, or text. */
    class PdfOutcome(val replace: ByteArray? = null, val add: List<Pair<String, ByteArray>> = emptyList(), val text: String? = null)

    /**
     * Runs [work] off the main thread on the PDF on the line with id [lineId], then puts its outcome in the note
     * as one edit (so Undo reverses it): the replacement on that line, new PDFs and text right after it.
     */
    fun pdfTool(
        context: android.content.Context, lineId: String, label: String, onError: (String) -> Unit, onDone: () -> Unit = {},
        work: (android.content.Context, Attachment, java.io.File) -> PdfOutcome,
    ) {
        if (readOnly || pdfBusy != null) return
        val a = doc.lines.firstOrNull { it.id == lineId }?.attachment?.takeIf { it.isPdf } ?: return
        val file = files.file(a)
        if (!file.exists()) { onError("This PDF isn't downloaded yet. It arrives with the next sync."); return }
        val app = context.applicationContext
        pdfBusy = label
        viewModelScope.launch {
            try {
                val out = withContext(Dispatchers.IO) { work(app, a, file) }
                val replaced = out.replace?.let { files.write(it, "pdf", a.name, PDF_MIME) }
                val added = out.add.map { (name, bytes) -> files.write(bytes, "pdf", name, PDF_MIME) }
                val at = doc.lines.indexOfFirst { it.id == lineId }
                if (at < 0) return@launch // the PDF was removed meanwhile
                val blocks = doc.toBlocks().toMutableList()
                if (replaced != null) blocks[at] = blocks[at].copy(attachment = replaced)
                added.forEachIndexed { k, n -> blocks.add(at + 1 + k, Block(text = ATTACHMENT_CHAR.toString(), attachment = n)) }
                val text = out.text?.takeIf { it.isNotBlank() }
                if (text != null) addLines(blocks, at + 1 + added.size, text)
                else {
                    if (at + added.size == blocks.lastIndex) blocks.add(Block())
                    doc = EditorDoc.fromBlocks(blocks)
                    // The field's text must match the doc's, or it can't map the cursor (lines were added).
                    val cursor = if (added.isEmpty()) value.selection.start.coerceAtMost(doc.text.length) else doc.lineStart(at + added.size + 1)
                    value = TextFieldValue(doc.text, TextRange(cursor))
                    changed()
                }
                onDone()
            } catch (e: Exception) {
                onError(e.message ?: "That didn't work on this PDF.")
            } finally {
                pdfBusy = null
            }
        }
    }

    /** The PDFs in this note other than the one on line [lineId], for Merge. */
    fun otherPdfs(context: Context, lineId: String): List<Attachment> = doc.lines.filter { it.id != lineId }.mapNotNull { it.attachment?.takeIf { a -> a.isPdf && com.margin.app.data.Pdf.info(context, fileOf(a))?.locked != true } }

    fun fileOf(a: Attachment) = files.file(a)

    /** Copies a PDF picked on the phone into the attachments (not into the note), for Merge. */
    suspend fun importForMerge(uri: android.net.Uri): Attachment = files.import(uri)

    /** [text] as lines at block [index] of [blocks], which becomes the note, with the cursor after the text. */
    private fun addLines(blocks: MutableList<Block>, index: Int, text: String, ai: Boolean = false) {
        val from = index.coerceIn(1, blocks.size)
        val lines = text.trimEnd().lines()
        // An AI card starts with an empty line that holds its badge and buttons.
        blocks.addAll(from, (if (ai) listOf(Block(ai = true)) else emptyList()) + lines.map { Block(text = it, ai = ai) })
        val at = if (ai) from + 1 else from
        val last = at + lines.size - 1
        if (last == blocks.lastIndex) blocks.add(Block()) // a line to keep typing on
        doc = EditorDoc.fromBlocks(blocks)
        value = TextFieldValue(doc.text, TextRange(doc.lineStart(last) + lines.last().length))
        changed()
    }

    /**
     * Handwriting turned into [text] in the drawing on line [line] (null: a new drawing). [remaining] is what's
     * left of the drawing, saved with the text below it; null removes the drawing and puts the text in its place.
     * One step, so one undo puts it all back.
     */
    fun drawingToText(remaining: Drawing?, line: Int?, text: String, onError: (String) -> Unit) {
        if (readOnly || needsUnlock || locked || text.isBlank()) return
        viewModelScope.launch {
            val a = remaining?.let { d ->
                try {
                    files.write(MarginJson.encodeToString(d).toByteArray(), "json", "Drawing", DRAWING_MIME)
                } catch (e: Exception) { onError(e.message ?: "Couldn't save the drawing"); return@launch }
            }
            val blocks = doc.toBlocks().toMutableList()
            val old = line?.takeIf { blocks.getOrNull(it)?.attachment?.isDrawing == true }
            val at = when {
                old != null && a != null -> { blocks[old] = blocks[old].copy(attachment = a); old + 1 }
                old != null -> { blocks.removeAt(old); old }
                a != null -> (cursorLine + 1).coerceIn(1, blocks.size).also { blocks.add(it, Block(text = ATTACHMENT_CHAR.toString(), attachment = a)) } + 1
                else -> cursorLine + 1
            }
            addLines(blocks, at, text)
        }
    }

    /** Pictures can go in: an open, unlocked note that isn't in Recently Deleted. */
    val canAddImage get() = !readOnly && !needsUnlock && !locked

    /** A graph saved as a picture, after the cursor's line (the graph's line). [label] names it after its curves. */
    fun addGraph(image: androidx.compose.ui.graphics.ImageBitmap, label: String, onError: (String) -> Unit) {
        if (!canAddImage) return
        viewModelScope.launch {
            try {
                val png = withContext(Dispatchers.Default) {
                    java.io.ByteArrayOutputStream().also { image.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
                }
                insertAttachment(files.write(png, "png", "Graph of $label", "image/png"))
            } catch (e: Exception) {
                onError(e.message ?: "Couldn't save the graph")
            }
        }
    }

    /** The file the camera is writing into, between launching it and getting the result. */
    private var pendingPhoto: java.io.File? = null

    /** Where the camera app should save the photo. */
    fun photoTarget(): android.net.Uri {
        val (file, uri) = files.newPhotoTarget()
        pendingPhoto = file
        return uri
    }

    /** Camera result: adds the photo after the cursor's line, or drops the empty file when cancelled. */
    // shortcut: if Android kills Margin while the camera is open, the photo isn't added; the unused file is cleaned up a day later.
    fun photoTaken(ok: Boolean, onError: (String) -> Unit) {
        val file = pendingPhoto ?: return
        pendingPhoto = null
        viewModelScope.launch {
            val size = withContext(Dispatchers.IO) { if (file.exists()) file.length() else 0L }
            when {
                !ok || size == 0L || readOnly || locked -> withContext(Dispatchers.IO) { file.delete() }
                size > MAX_ATTACHMENT_BYTES -> { withContext(Dispatchers.IO) { file.delete() }; onError(AttachmentFiles.TooLarge().message!!) }
                else -> {
                    val name = "Photo " + java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date())
                    insertAttachment(Attachment(file.name, name, "image/jpeg"))
                }
            }
        }
    }

    /**
     * Adds a web link after the cursor's line. With no [title], it's read from the page, falling back to the
     * site's name when that fails. [onDone] gets null on success or the reason it failed.
     */
    fun addLink(url: String, title: String, onDone: (String?) -> Unit) {
        val clean = normalizeUrl(url) ?: return onDone("That doesn't look like a web address.")
        if (readOnly || needsUnlock) return onDone(null)
        viewModelScope.launch {
            try {
                val name = title.trim().ifEmpty { withContext(Dispatchers.IO) { fetchPageTitle(clean) } ?: hostOf(clean) }
                insertAttachment(files.write(clean.toByteArray(), "url", name, LINK_MIME))
                onDone(null)
            } catch (e: Exception) {
                onDone(e.message ?: "Couldn't add the link")
            }
        }
    }

    /** This note as a PDF or Markdown file to share or print. Null if writing it failed. */
    suspend fun exportFile(pdf: Boolean): java.io.File? {
        val blocks = doc.toBlocks()
        val name = safeFileName(blocks.first().text.ifBlank { "Note" }) + if (pdf) ".pdf" else ".md"
        return withContext(Dispatchers.IO) {
            runCatching {
                val file = files.exportFile(name)
                file.outputStream().use { out ->
                    if (pdf) NotePdf.write(blocks, files::file, out)
                    else out.write(noteMarkdown(blocks, linkUrl = { a -> runCatching { files.file(a).readText().trim() }.getOrNull() }).toByteArray())
                }
                file
            }.getOrNull()
        }
    }

    /** Turns the photo on [line] a quarter turn clockwise (a new file; Undo brings the old one back). */
    fun rotateImage(line: Int, onError: (String) -> Unit) {
        val a = doc.lines.getOrNull(line)?.attachment?.takeIf { it.isImage } ?: return
        if (readOnly) return
        val id = doc.lines[line].id
        viewModelScope.launch {
            val turned = try { files.rotated(a) } catch (e: Exception) { onError(e.message ?: "Couldn't rotate the photo"); return@launch }
            // The line may have moved while rotating; find it again by id.
            val at = doc.lines.indexOfFirst { it.id == id && it.attachment == a }
            if (at < 0) return@launch
            doc = EditorDoc.fromBlocks(doc.toBlocks().mapIndexed { i, b -> if (i == at) b.copy(attachment = turned) else b })
            changed()
        }
    }

    /** Set by an app shortcut: what the newly opened note starts with ([START_SCAN], [START_VOICE]); the editor clears it. */
    var startAction by mutableStateOf<String?>(null)

    /** This note's earlier versions, newest first (see NoteRepository.keepVersion). */
    fun versions() = repo.versionsOf(noteId)

    /** True when every line is text, so the body can be swapped for new text without losing anything. */
    val textOnly get() = doc.lines.none { it.isObject }

    /**
     * Swaps everything under the title for [text] as one edit, so Undo brings the old text back. Only for a
     * [textOnly] note; a first line repeating the title is dropped.
     */
    fun replaceBody(text: String) {
        if (readOnly || needsUnlock || !textOnly || text.isBlank()) return
        val title = doc.toBlocks().first()
        val lines = text.trim().lines().let { if (it.first().trim() == title.text.trim()) it.drop(1) else it }.dropWhile { it.isBlank() }
        if (lines.isEmpty()) return
        lastChangeAt = 0 // its own undo step
        doc = EditorDoc.fromBlocks(listOf(title) + lines.map { com.margin.app.data.Block(text = it) })
        value = TextFieldValue(doc.text, TextRange(0))
        changed()
    }

    /** Find in note: puts [with] in place of each of [ranges] (offsets in the doc's text), as one undo step. */
    fun replaceRanges(ranges: List<IntRange>, with: String) {
        if (readOnly || needsUnlock || ranges.isEmpty()) return
        val text = with.replace('\n', ' ')
        var next = doc
        // Last match first, so the earlier offsets stay valid.
        ranges.sortedByDescending { it.first }.forEach { r ->
            if (r.first >= 0 && r.last < next.text.length) next = next.applyEdit(next.text.replaceRange(r, text), r.first + text.length)
        }
        if (next == doc) return
        lastChangeAt = 0 // its own undo step
        doc = next
        value = TextFieldValue(next.text, TextRange((ranges.minOf { it.first } + text.length).coerceIn(0, next.text.length)))
        changed()
    }

    /** Puts an earlier version back as an ordinary edit, so Undo returns to what was there. */
    fun restoreVersion(blocks: List<com.margin.app.data.Block>) {
        if (readOnly || needsUnlock) return
        lastChangeAt = 0 // its own undo step
        doc = EditorDoc.fromBlocks(blocks)
        value = TextFieldValue(doc.text, TextRange(0))
        changed()
    }

    /** The lines of the AI card that starts on line [start]. */
    private fun aiRun(start: Int): IntRange {
        var end = start
        while (doc.lines.getOrNull(end + 1)?.ai == true) end++
        return start..end
    }

    /** Folds the AI card starting on line [start] down to its badge, or opens it again. */
    fun toggleAiFold(start: Int) {
        val meta = doc.lines.getOrNull(start)?.takeIf { it.ai } ?: return
        if (readOnly) return
        doc = doc.copy(lines = doc.lines.toMutableList().apply { this[start] = meta.copy(folded = !meta.folded) })
        value = TextFieldValue(doc.text, TextRange(doc.lineStart(start) + doc.texts[start].length))
        changed()
    }

    /** Deletes the AI card starting on line [start], text and all. */
    fun removeAiCard(start: Int) {
        if (readOnly || doc.lines.getOrNull(start)?.ai != true) return
        val run = aiRun(start)
        lastChangeAt = 0 // its own undo step
        doc = EditorDoc.fromBlocks(doc.toBlocks().filterIndexed { i, _ -> i !in run })
        value = TextFieldValue(doc.text, TextRange(doc.lineStart(start.coerceAtMost(doc.lines.lastIndex))))
        changed()
    }

    fun removeAttachment(line: Int) {
        if (readOnly || doc.lines.getOrNull(line)?.isObject != true) return
        doc = EditorDoc.fromBlocks(doc.toBlocks().filterIndexed { i, _ -> i != line })
        value = TextFieldValue(doc.text, TextRange(doc.lineStart(line.coerceAtMost(doc.lines.lastIndex))))
        changed()
    }

    fun lock() {
        if (readOnly || locked || needsUnlock) return
        if (isNew && doc.toBlocks().all { it.text.isBlank() }) return
        viewModelScope.launch {
            val params = NoteCrypto.KeyParams(NoteCrypto.newSalt(), NoteCrypto.ITERATIONS)
            session.key(params) ?: return@launch // derives and caches it
            lockParams = params
            locked = true
            lastSaved = emptyList() // force a save in the locked form
            persist()
        }
    }

    /** Saves pending edits now (before the notes passcode changes under them). */
    suspend fun saveNow() = persist()

    /** After the passcode changed: this open locked note was re-sealed with a new salt, so pick up its params and key. */
    fun rekeyed() {
        if (!locked || needsUnlock) return
        viewModelScope.launch {
            val cipher = repo.getNote(noteId)?.cipher ?: return@launch
            val params = NoteCrypto.paramsOf(cipher)
            session.key(params) ?: return@launch
            sealed = cipher
            lockParams = params
            changed() // edits made while the key was being derived weren't saved yet
        }
    }

    /** Stores the note unlocked again. Only while it's open. */
    fun removeLock() {
        if (readOnly || !locked || needsUnlock) return
        locked = false
        lockParams = null
        sealed = null
        lastSaved = emptyList()
        viewModelScope.launch { persist() }
    }

    fun close(keepShown: Boolean = false) {
        flush()
        val id = noteId
        val newTitle = doc.texts.first().trim()
        if (loaded && !readOnly && !needsUnlock && id.isNotEmpty() && titleAtOpen.isNotBlank()) {
            val oldTitle = titleAtOpen
            viewModelScope.launch { repo.renameLinks(id, oldTitle, newTitle) }
            if (keepShown) titleAtOpen = newTitle // the close that follows has nothing left to rename
        }
        if (keepShown) return
        noteId = ""
        loaded = false // the next note must not briefly show this one
    }

    /**
     * [close] for a note whose screen is animating away: saved now, but it stays on screen for [ms] so the
     * screen doesn't go blank as it slides off. Skipped if the note was opened again meanwhile ([gone] is false).
     */
    fun closeSoon(ms: Long, gone: () -> Boolean) {
        val id = noteId
        close(keepShown = true)
        viewModelScope.launch {
            kotlinx.coroutines.delay(ms)
            if (noteId == id && gone()) close()
        }
    }

    /** Line the cursor is on, and the range of lines the selection covers. */
    val cursorLine get() = doc.lineAt(value.selection.start)
    private val selectedLines get() = doc.lineAt(value.selection.min)..doc.lineAt(value.selection.max)

    fun onValueChange(v: TextFieldValue) {
        if (readOnly) return
        if (v.text == value.text) { value = v; return } // cursor or IME composition only
        var next = doc.applyEdit(v.text, v.selection.end)
        if (appPrefs.toggles.value(Toggle.SMART_CHECKLISTS)) next = next.withChecklistShortcut(next.lineAt(v.selection.end))
        next = next.withListShortcut(next.lineAt(v.selection.end), v.selection.end)
        doc = next
        // The doc can differ from what was typed (Enter on an empty list line, text kept off an attachment's line).
        value = if (next.text == v.text) v
        else TextFieldValue(next.text, TextRange(cursorAfterRewrite(v.text, v.selection.end, next.text)))
        changed()
    }

    /** Format panel: bold, italic, ... on the selected text; again on fully styled text removes it. */
    fun toggleMark(mark: Mark) {
        if (readOnly || needsUnlock) return
        doc = doc.withMark(mark, value.selection.min, value.selection.max, highlightTint)
        changed()
    }

    /** The colour the highlight button uses. Picking one also highlights the selected text in it. */
    var highlightTint by mutableStateOf(Tint.AMBER); private set

    fun pickTint(tint: Tint) {
        highlightTint = tint
        if (!value.selection.collapsed && !doc.hasMark(Mark.HIGHLIGHT, value.selection.min, value.selection.max, tint)) toggleMark(Mark.HIGHLIGHT)
    }

    fun toggleQuote() {
        if (readOnly) return
        doc = doc.withQuote(selectedLines.first, selectedLines.last)
        changed()
    }

    /** [by] = 1 indents the selected lines a step, -1 outdents. */
    fun indent(by: Int) {
        if (readOnly) return
        doc = doc.withIndent(selectedLines.first, selectedLines.last, by)
        changed()
    }

    fun hasMark(mark: Mark) = doc.hasMark(mark, value.selection.min, value.selection.max)

    /** Toolbar: styles the selected lines (never the title). Same style again = back to body. */
    fun setType(type: BlockType) {
        if (readOnly) return
        doc = doc.withType(type, selectedLines.first, selectedLines.last)
        changed()
    }

    fun toggleChecked(line: Int) {
        if (readOnly) return
        doc = doc.toggleChecked(line, appPrefs.autoSortChecked.value)
        value = value.copy(text = doc.text, composition = null)
        changed()
    }

    /** Up to five of [known] tags that complete the one being typed at the cursor. */
    fun tagSuggestions(known: List<TagCount>): List<TagCount> {
        if (!focused || readOnly || !value.selection.collapsed) return emptyList()
        val (_, typed) = tagPrefixAt(value.text, value.selection.start) ?: return emptyList()
        val key = tagKey(typed)
        return known.filter { it.key.startsWith(key) && it.key != key }.take(5)
    }

    fun completeTag(name: String) {
        if (needsUnlock) return
        val (start, _) = tagPrefixAt(value.text, value.selection.start) ?: return
        val end = value.selection.start
        val text = value.text.substring(0, start) + name + " " + value.text.substring(end)
        onValueChange(TextFieldValue(text, TextRange(start + name.length + 1)))
    }

    /** Notes matching an unfinished "[[..." at the cursor, newest first; not this note or untitled ones. */
    /** What's been typed after "[[" at the cursor, or "". */
    fun linkTyped(): String = linkPrefixAt(value.text, value.selection.start)?.second ?: ""

    val linksOn get() = appPrefs.toggles.value(Toggle.LINKS)
    val grammarOn get() = appPrefs.toggles.value(Toggle.GRAMMAR)
    /** The cloud check, which only ever sees notes that aren't locked. */
    val cloudGrammarOn get() = grammarOn && appPrefs.toggles.value(Toggle.CLOUD_GRAMMAR) && !locked

    fun linkSuggestions(notes: List<NoteEntity>): List<NoteEntity> {
        if (!linksOn || !focused || readOnly || !value.selection.collapsed) return emptyList()
        val (_, typed) = linkPrefixAt(value.text, value.selection.start) ?: return emptyList()
        val q = typed.trim().lowercase()
        return notes.filter { it.id != noteId && it.title.isNotBlank() && it.title.lowercase().contains(q) }
            .sortedByDescending { it.lastEdited }.take(5)
    }

    /** Finishes the link being typed as "[[title]] ", dropping a "]]" the cursor is already in front of. */
    fun completeLink(title: String) {
        if (needsUnlock) return
        val (start, _) = linkPrefixAt(value.text, value.selection.start) ?: return
        var end = value.selection.start
        if (value.text.startsWith("]]", end)) end += 2
        val insert = title.trim() + "]] "
        val text = value.text.substring(0, start) + insert + value.text.substring(end)
        onValueChange(TextFieldValue(text, TextRange(start + insert.length)))
    }

    /** Toolbar link button: starts a link at the cursor; the suggestions take it from there. */
    fun startLink() {
        if (readOnly || needsUnlock || cursorLine == 0) return
        val at = value.selection.min
        val text = value.text.substring(0, at) + "[[" + value.text.substring(value.selection.max)
        onValueChange(TextFieldValue(text, TextRange(at + 2)))
    }

    /** Moving isn't editing. A note that hasn't been saved yet just gets saved in the new folder. */
    fun moveTo(target: String) {
        if (readOnly || target == folderId) return
        folderId = target
        if (!isNew) viewModelScope.launch { repo.moveNotes(listOf(noteId), target) }
    }

    /** Saves any pending edit first, then runs [action] on the stored note (skipped for a new note that's still empty). */
    private fun afterSave(action: suspend (String) -> Unit) {
        saveJob?.cancel()
        val save = prepareSave()
        val id = noteId
        viewModelScope.launch {
            save?.invoke()
            if (repo.getNote(id) != null) action(id)
        }
    }

    /** Sets ([day], an epoch day) or clears (null) the to-do target date; a new note is saved first. */
    fun changeTargetDate(day: Long?) {
        if (readOnly) return
        targetDate = day
        afterSave { repo.setTargetDate(it, day) }
    }

    /** Sets the paper behind the note's text; a new note is saved first. */
    fun changePaper(p: com.margin.app.data.Paper) {
        if (readOnly) return
        paper = p
        afterSave { repo.setPaper(it, p) }
    }

    /** Sets the typeface of the note's text; a new note is saved first. */
    fun changeFont(f: com.margin.app.data.NoteFont) {
        if (readOnly) return
        font = f
        afterSave { repo.setFont(it, f) }
    }

    fun togglePin() { if (!readOnly) afterSave { repo.togglePin(it); pinned = repo.getNote(it)?.isPinned == true } }

    fun duplicate(onDone: (NoteEntity) -> Unit) { if (!readOnly && !needsUnlock) afterSave { id -> repo.duplicate(id)?.let(onDone) } }

    fun delete(onDone: () -> Unit) {
        if (readOnly) return
        clearReminder()
        afterSave { repo.moveToTrash(it) }
        onDone()
    }

    /** Notes that link to this one, by its current title. */
    fun linkedNotes(all: List<NoteEntity>) = backlinks(doc.texts.first(), noteId, all)


    fun recover(onDone: () -> Unit) = viewModelScope.launch { repo.recover(noteId); noteId = ""; onDone() }
    fun deleteForever(onDone: () -> Unit) = viewModelScope.launch { repo.deleteForever(noteId); noteId = ""; onDone() }

    private fun changed() {
        saved = false
        saveJob?.cancel()
        saveJob = viewModelScope.launch { delay(400); persist() }
    }

    /**
     * Saves now. Everything up to the database write happens right away, including sealing a locked note
     * (with a key already derived), so this is safe to call just before the app locks.
     */
    fun flush() {
        saveJob?.cancel()
        prepareSave()?.let { viewModelScope.launch { it() } }
    }

    private suspend fun persist() { prepareSave()?.invoke() }

    private fun prepareSave(): (suspend () -> Unit)? {
        if (!loaded || readOnly || needsUnlock) return null
        val current = doc.toBlocks()
        if (current == lastSaved) { saved = true; return null }
        if (isNew && current.all { it.text.isBlank() }) return null // don't create empty notes
        val sealWith = if (locked) {
            val params = lockParams ?: return null
            (session.cachedKey(params) ?: return null) to params
        } else null
        lastSaved = current
        isNew = false
        val id = noteId
        val folder = folderId
        return {
            // A locked note's photos and files are encrypted with it; removing the lock makes them readable again.
            val needed = current.any { b -> b.attachment?.let { if (sealWith != null) !it.isSealed else it.isSealed } == true }
            val (blocks, swapped) = when {
                !needed -> current to emptyMap()
                sealWith != null -> files.sealAll(current)
                else -> files.unsealAll(current)
            }
            if (swapped.isNotEmpty() && id == noteId) swapAttachments(swapped, current, blocks)
            val cipher = sealWith?.let { (key, params) -> NoteCrypto.seal(blocks.drop(1), key, params).also { if (id == noteId) sealed = it } }
            repo.saveNote(id, folder, blocks, cipher)
            // The readable copies of files that are now encrypted go at once, unless another note still uses them.
            if (swapped.isNotEmpty() && sealWith != null) files.dropUnused(swapped.keys, repo.attachmentsInUse())
            saved = true; exists = true; if (createdAt == null) createdAt = System.currentTimeMillis()
        }
    }

    /** After a save swapped files (encrypted, or readable again): the open doc and its undo steps use the new ones. */
    private fun swapAttachments(swapped: Map<String, Attachment>, before: List<Block>, after: List<Block>) {
        fun EditorDoc.swap() = copy(lines = lines.map { m -> m.attachment?.let { swapped[it.file] }?.let { m.copy(attachment = it) } ?: m })
        current = current.swap()
        for (i in history.indices) history[i] = history[i].swap()
        if (lastSaved == before) lastSaved = after
    }
}

// ---------- Settings ----------

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: SyncPrefs,
    private val sync: SyncManager,
    private val authorizer: DriveAuthorizer,
    private val scheduler: SyncScheduler,
    private val appPrefs: AppPrefs,
    private val session: LockSession,
    private val repo: NoteRepository,
    private val files: AttachmentFiles,
    val ai: com.margin.app.data.AiKeys,
    connectivity: Connectivity,
) : ViewModel() {
    val status = prefs.status
    val pending: StateFlow<Int> = repo.pendingCount().stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val online = connectivity.online

    /** Export all: every note as Markdown in a zip at [uri] (picked by the user). [onDone] gets a message to show. */
    fun exportAll(uri: android.net.Uri, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val message = try {
                val (notes, folders) = repo.everything()
                val count = withContext(Dispatchers.IO) {
                    val out = context.contentResolver.openOutputStream(uri) ?: throw java.io.IOException("Couldn't open the file")
                    out.use { writeMarkdownZip(it, notes, folders) { a -> files.file(a) } }
                }
                "Exported $count ${if (count == 1) "note" else "notes"}."
            } catch (e: Exception) {
                "Export failed: ${e.message ?: "unknown error"}"
            }
            onDone(message)
        }
    }

    /** An import is running. */
    var importing by mutableStateOf(false); private set

    /**
     * Import: reads the picked files (zips, .enex, .json from Keep, .md, .txt, .html), stores their photos and
     * files, then the notes, each source in a folder of its own. [onDone] gets a message to show.
     */
    fun importFiles(uris: List<android.net.Uri>, onDone: (String) -> Unit) {
        if (uris.isEmpty() || importing) return
        importing = true
        viewModelScope.launch {
            val message = try {
                val read = withContext(Dispatchers.IO) { readImports(uris) }
                val count = repo.importNotes(read.notes)
                val roots = read.notes.mapNotNull { it.folder.firstOrNull() }.distinct()
                if (count == 0) {
                    "No notes found. Margin can import a Google Keep Takeout zip, Evernote .enex files, and Markdown, text or HTML files."
                } else buildString {
                    append("Imported $count ${if (count == 1) "note" else "notes"} into ${roots.joinToString(" and ") { "“$it”" }}.")
                    if (read.failed > 0) append(" ${read.failed} ${if (read.failed == 1) "file" else "files"} couldn't be read.")
                    if (read.missing > 0) append(" ${read.missing} ${if (read.missing == 1) "attachment was" else "attachments were"} missing or over 20 MB.")
                }
            } catch (e: Exception) {
                "Import failed: ${e.message ?: "unknown error"}"
            }
            importing = false
            onDone(message)
        }
    }

    private class ImportRead(val notes: List<com.margin.app.data.ImportedNote>, val failed: Int, val missing: Int)

    private suspend fun readImports(uris: List<android.net.Uri>): ImportRead {
        val dir = java.io.File(context.cacheDir, "import").apply { deleteRecursively(); mkdirs() }
        val notes = mutableListOf<com.margin.app.data.ImportedNote>()
        var failed = 0
        var missing = 0
        suspend fun keep(result: com.margin.app.data.ImportResult) {
            failed += result.failed
            result.notes.forEach { n ->
                val blocks = n.blocks.map { b ->
                    val a = b.attachment ?: return@map b
                    val bytes = result.file(a.file)
                    if (bytes == null || bytes.size > com.margin.app.data.MAX_ATTACHMENT_BYTES) {
                        missing++
                        b.copy(text = "[${a.name}]", attachment = null)
                    } else {
                        val ext = a.name.substringAfterLast('.', "").lowercase().takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }
                            ?: com.margin.app.data.extensionFor(a.mime)
                        b.copy(attachment = files.write(bytes, ext, a.name, a.mime))
                    }
                }
                notes += n.copy(blocks = blocks)
            }
        }
        try {
            uris.forEach { uri ->
                val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "Import"
                val tmp = java.io.File(dir, com.margin.app.data.newId())
                val input = context.contentResolver.openInputStream(uri)
                if (input == null) { failed++; return@forEach }
                input.use { inp -> tmp.outputStream().use { inp.copyTo(it) } }
                val zip = tmp.inputStream().use { s -> val b = ByteArray(2); s.read(b) == 2 && b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte() }
                if (zip) java.util.zip.ZipFile(tmp).use { keep(com.margin.app.data.parseImport(com.margin.app.data.ZipSource(it))) }
                else keep(com.margin.app.data.parseImport(com.margin.app.data.FileSource(name, tmp)))
                tmp.delete()
            }
        } finally {
            dir.deleteRecursively()
        }
        return ImportRead(notes, failed, missing)
    }

    val lockedCount: StateFlow<Int> = repo.lockedCount().stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val unlocked = session.unlocked
    val fingerprint = appPrefs.fingerprint

    fun lockNow() = session.lock()

    /** Checks the current notes password; on success the session is unlocked. */
    suspend fun checkPassword(password: CharArray): Boolean {
        val sample = repo.lockedNotes().firstOrNull()?.cipher ?: return false
        val ok = session.unlock(password, sample) != null
        password.fill('\u0000')
        return ok
    }

    /** Re-locks every locked note with [newPassword]. Turns fingerprint unlock off (it stored the old one). */
    fun changePassword(newPassword: CharArray) = viewModelScope.launch {
        busy = true
        try {
            val skipped = repo.changeLockPassword(newPassword) { session.open(it) }
            session.startWith(newPassword)
            appPrefs.setFingerprint(null)
            message = if (skipped == 0) "Passcode changed" else "Passcode changed. $skipped note(s) use a different password and weren't changed."
        } finally {
            newPassword.fill('\u0000')
            busy = false
        }
    }

    fun passwordForFingerprint(): CharArray? = session.passwordCopy()
    /** Unlocked with an old password: time to switch to a passcode. */
    val legacyPassword = session.legacy
    fun setFingerprint(blob: String?) = appPrefs.setFingerprint(blob)
    val autoSortChecked = appPrefs.autoSortChecked
    val toggles = appPrefs.toggles
    fun setToggle(t: Toggle, on: Boolean) = appPrefs.setToggle(t, on)
    val keepDeletedDays = appPrefs.keepDeletedDays
    fun setKeepDeletedDays(days: Int) = viewModelScope.launch { appPrefs.setKeepDeletedDays(days); repo.purgeExpiredTrash() }
    fun setAutoSortChecked(on: Boolean) = appPrefs.setAutoSortChecked(on)
    val appearance = appPrefs.appearance
    fun setAppearance(a: Appearance) = appPrefs.setAppearance(a)
    val welcomeDone = appPrefs.welcomeDone
    fun setWelcomeDone() = appPrefs.setWelcomeDone()
    val defaultSort = appPrefs.defaultSort
    fun setDefaultSort(sortBy: NoteSort, ascending: Boolean) = appPrefs.setDefaultSort(sortBy, ascending)
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null)
    /** Set when Drive access needs the user's approval; the screen launches it. */
    var consentIntent by mutableStateOf<PendingIntent?>(null); private set
    private var pendingAction: (suspend () -> Unit)? = null

    fun onSignedIn(account: GoogleAccount) {
        prefs.setAccount(account.email, account.displayName)
        scheduler.onAppStart()
        syncNow()
    }

    fun signOut() = viewModelScope.launch {
        runCatching { signOutOfGoogle(context) }
        prefs.clearAccount()
        message = "Signed out. Your notes stay on this device."
    }

    fun syncNow() = run { val r = sync.sync(); message = "Synced: ${r.uploaded} sent, ${r.downloaded} received" }

    fun restore() = run { sync.restore(); message = "Restored from your Google account" }

    fun onConsentResult(data: Intent?) {
        consentIntent = null
        val action = pendingAction
        pendingAction = null
        val granted = runCatching { authorizer.resultFromIntent(data).accessToken != null }.getOrDefault(false)
        if (granted && action != null) run(action) else message = "Google Drive access was not granted"
    }

    private fun run(action: suspend () -> Unit) {
        if (busy) return
        viewModelScope.launch {
            busy = true
            try {
                action()
            } catch (e: NeedsUserConsentException) {
                pendingAction = action
                consentIntent = e.pendingIntent
            } catch (e: Exception) {
                message = e.message ?: "Sync failed"
            } finally {
                busy = false
            }
        }
    }
}
