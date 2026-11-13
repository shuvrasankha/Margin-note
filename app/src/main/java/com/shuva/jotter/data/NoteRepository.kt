package com.shuva.jotter.data

import androidx.room.withTransaction
import com.shuva.jotter.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** All local edits go through here. Room is the source of truth; each edit queues a debounced sync. */
@Singleton
class NoteRepository @Inject constructor(
    private val db: JotterDatabase,
    private val scheduler: SyncScheduler,
    private val appPrefs: AppPrefs,
) {
    private val folders = db.folders()
    private val notes = db.notes()
    private val versions = db.versions()
    private val texts = db.attachmentText()
    private fun now() = System.currentTimeMillis()

    fun liveFolders(): Flow<List<FolderEntity>> = folders.observeLive()
    fun folder(id: String): Flow<FolderEntity?> = folders.observe(id)
    fun folderCounts(): Flow<List<FolderCount>> = notes.observeCounts()
    fun trashCount(): Flow<Int> = notes.observeTrashCount()
    fun notesIn(folderId: String): Flow<List<NoteEntity>> = notes.observeInFolder(folderId)
    fun trash(): Flow<List<NoteEntity>> = notes.observeTrash()
    fun liveNotes(): Flow<List<NoteEntity>> = notes.observeLive()
    fun lockedCount(): Flow<Int> = notes.observeLockedCount()
    /** Changes waiting to go to Drive: notes and folders edited since the last sync. */
    fun pendingCount(): Flow<Int> = kotlinx.coroutines.flow.combine(notes.observeDirtyCount(), folders.observeDirtyCount()) { n, f -> n + f }

    fun search(query: String): Flow<List<NoteEntity>> {
        val escaped = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return notes.observeSearch("%$escaped%")
    }

    suspend fun getNote(id: String) = notes.get(id)

    suspend fun ensureDefaultFolder() {
        if (folders.get(DEFAULT_FOLDER_ID) == null) {
            // updatedAt = 0 so every device's default folder starts out identical.
            folders.upsert(FolderEntity(DEFAULT_FOLDER_ID, DEFAULT_FOLDER_NAME, isDefault = true, createdAt = 0, updatedAt = 0))
        }
    }

    suspend fun createFolder(
        name: String,
        sortBy: NoteSort = NoteSort.EDITED,
        ascending: Boolean = false,
        parentId: String? = null,
        smartFilter: SmartFilter? = null,
    ) {
        val now = now()
        // Smart folders are always top level, and nothing goes inside a smart folder.
        val parent = parentId?.let { folders.get(it) }?.takeIf { !it.deleted && !it.isSmart && smartFilter == null }
        // A new folder goes after the others it's listed with.
        val all = folders.all().filter { !it.deleted }
        val parents = effectiveParents(all)
        val group = if (smartFilter != null) "smart" else "in:" + parent?.id
        val last = all.filter { reorderGroup(it, parents) == group }.maxOfOrNull { it.sortOrder } ?: -1
        folders.upsert(
            FolderEntity(
                newId(), name.trim(), sortBy = sortBy, sortAscending = ascending, createdAt = now, updatedAt = now,
                parentId = parent?.id, smartFilter = smartFilter, sortOrder = last + 1,
            ),
        )
        scheduler.requestDebounced()
    }

    suspend fun setSmartFilter(id: String, filter: SmartFilter) {
        val folder = folders.get(id)?.takeIf { it.isSmart && !it.deleted } ?: return
        if (folder.smartFilter == filter) return
        folders.upsert(folder.copy(smartFilter = filter, updatedAt = now()))
        scheduler.requestDebounced()
    }

    /** Moves a folder into [parentId] (null = top level). Not into itself, its own subfolders or a smart folder. */
    suspend fun moveFolder(id: String, parentId: String?) {
        val all = folders.all()
        val folder = all.firstOrNull { it.id == id }?.takeIf { !it.deleted && !it.isDefault && !it.isSmart } ?: return
        if (parentId != null) {
            val parent = all.firstOrNull { it.id == parentId } ?: return
            if (parent.deleted || parent.isSmart || parentId in subtreeIds(all, id)) return
        }
        if (folder.parentId == parentId) return
        folders.upsert(folder.copy(parentId = parentId, updatedAt = now()))
        scheduler.requestDebounced()
    }

    /** Edit folders: [ids] in their new order, all from one group (see [reorderGroup]). Only changed folders are rewritten. */
    suspend fun reorderFolders(ids: List<String>) {
        val now = now()
        var changed = false
        db.withTransaction {
            ids.forEachIndexed { i, id ->
                val f = folders.get(id)?.takeIf { !it.deleted && !it.isDefault && it.sortOrder != i } ?: return@forEachIndexed
                folders.upsert(f.copy(sortOrder = i, updatedAt = now))
                changed = true
            }
        }
        if (changed) scheduler.requestDebounced()
    }

    suspend fun setFolderSort(id: String, sortBy: NoteSort, ascending: Boolean) {
        val folder = folders.get(id) ?: return
        if (folder.sortBy == sortBy && folder.sortAscending == ascending) return
        folders.upsert(folder.copy(sortBy = sortBy, sortAscending = ascending, updatedAt = now()))
        scheduler.requestDebounced()
    }

    suspend fun renameFolder(id: String, name: String) {
        val folder = folders.get(id) ?: return
        if (folder.isDefault || name.isBlank()) return
        folders.upsert(folder.copy(name = name.trim(), updatedAt = now()))
        scheduler.requestDebounced()
    }

    /** Deletes the folder and its subfolders; their notes move to Recently Deleted. A smart folder holds no notes. */
    suspend fun deleteFolder(id: String) {
        val all = folders.all()
        val folder = all.firstOrNull { it.id == id } ?: return
        if (folder.isDefault) return
        val now = now()
        val ids = subtreeIds(all, id)
        db.withTransaction {
            ids.forEach { fid ->
                notes.liveInFolder(fid).forEach { notes.upsert(it.copy(trashedAt = now, isPinned = false, updatedAt = now)) }
                all.firstOrNull { it.id == fid && !it.deleted }?.let { folders.upsert(it.copy(deleted = true, updatedAt = now)) }
            }
        }
        scheduler.requestDebounced()
    }

    /**
     * Creates the note on first save, so an untouched new note never exists. With [cipher] the note is
     * stored locked: only the title stays readable, the sealed body goes in [NoteEntity.cipher].
     * Without it the note is stored unlocked (this is also how a lock is removed).
     */
    suspend fun saveNote(id: String, folderId: String, blocks: List<Block>, cipher: String? = null) {
        val now = now()
        val existing = notes.get(id)
        if (existing != null && (existing.trashedAt != null || existing.deleted)) return // trashed notes are read-only
        val base = existing ?: NoteEntity(
            id = id, folderId = folderId, blocks = emptyList(), title = "", searchText = "",
            createdAt = now, updatedAt = now,
        )
        val stored = if (cipher != null) blocks.take(1) else blocks
        notes.upsert(base.withBlocks(stored, now).copy(cipher = cipher))
        if (cipher != null) versions.deleteFor(id) // no readable copies of a locked note
        else if (existing?.blocks != blocks) keepVersion(id, blocks, now)
        scheduler.requestDebounced()
    }

    /**
     * Version history: one version per editing session. Saves within [SESSION_MS] of the last version update it;
     * after a pause a new version starts. The newest [KEEP_VERSIONS] are kept.
     */
    private suspend fun keepVersion(id: String, blocks: List<Block>, now: Long) {
        if (blocks.all { it.plainText.isBlank() }) return
        val last = versions.latest(id)
        if (last != null && now - last.savedAt < SESSION_MS) versions.upsert(last.copy(savedAt = now, blocks = blocks))
        else {
            versions.upsert(NoteVersion(noteId = id, savedAt = now, blocks = blocks))
            versions.trim(id, KEEP_VERSIONS)
        }
    }

    fun versionsOf(noteId: String): Flow<List<NoteVersion>> = versions.observe(noteId)

    /** Text read from attachments that contains [query] (for search results). */
    fun attachmentTextMatching(query: String): Flow<List<AttachmentText>> {
        val escaped = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return texts.observeMatching("%$escaped%")
    }

    suspend fun indexedAttachments(): Set<String> = texts.files().toSet()
    suspend fun saveAttachmentText(file: String, text: String) = texts.upsert(AttachmentText(file, text))
    suspend fun forgetAttachmentText(files: Collection<String>) { if (files.isNotEmpty()) texts.delete(files.toList()) }

    /** Every note and folder as stored, for Export all. */
    suspend fun everything() = notes.all() to folders.all()

    /** Every locked note that still exists, Recently Deleted included. */
    suspend fun lockedNotes(): List<NoteEntity> = notes.all().filter { !it.deleted && it.isLocked }

    /**
     * Re-seals every locked note with [newPassword] (fresh salts). [open] gives each note's body with the
     * current password; a note it can't open (locked with some other password) is left as it is.
     * Returns how many notes were left unchanged.
     */
    suspend fun changeLockPassword(newPassword: CharArray, open: suspend (String) -> List<Block>?): Int {
        val now = now()
        var skipped = 0
        val resealed = lockedNotes().mapNotNull { note ->
            val body = open(note.cipher!!)
            if (body == null) { skipped++; return@mapNotNull null }
            val params = NoteCrypto.KeyParams(NoteCrypto.newSalt(), NoteCrypto.ITERATIONS)
            val key = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                NoteCrypto.deriveKey(newPassword, params.salt, params.iterations)
            }
            note.copy(cipher = NoteCrypto.seal(body, key, params), updatedAt = now)
        }
        db.withTransaction { resealed.forEach { notes.upsert(it) } }
        if (resealed.isNotEmpty()) scheduler.requestDebounced()
        return skipped
    }

    /** Moving isn't editing: the note keeps its edited date. */
    suspend fun moveNotes(ids: Collection<String>, folderId: String) {
        val target = folders.get(folderId)?.takeIf { !it.deleted && !it.isSmart } ?: return
        val now = now()
        db.withTransaction {
            ids.mapNotNull { notes.get(it) }.filter { !it.deleted && it.trashedAt == null && it.folderId != target.id }
                .forEach { notes.upsert(it.copy(folderId = target.id, updatedAt = now)) }
        }
        scheduler.requestDebounced()
    }

    /**
     * Undo for a delete or move: puts each note back in the folder, pinned state and Recently Deleted state
     * it had in [previous]. Notes deleted for good since then are left alone.
     */
    suspend fun restoreState(previous: Collection<NoteEntity>) {
        val now = now()
        db.withTransaction {
            previous.forEach { old ->
                val cur = notes.get(old.id)?.takeIf { !it.deleted } ?: return@forEach
                val folder = folders.get(old.folderId)?.takeIf { !it.deleted }?.id ?: DEFAULT_FOLDER_ID
                notes.upsert(cur.copy(folderId = folder, isPinned = old.isPinned, trashedAt = old.trashedAt, updatedAt = now))
            }
        }
        scheduler.requestDebounced()
    }

    suspend fun setPinned(ids: Collection<String>, pinned: Boolean) {
        val now = now()
        db.withTransaction {
            ids.mapNotNull { notes.get(it) }.filter { !it.deleted && it.trashedAt == null && it.isPinned != pinned }
                .forEach { notes.upsert(it.copy(isPinned = pinned, updatedAt = now)) }
        }
        scheduler.requestDebounced()
    }

    suspend fun moveToTrash(ids: Collection<String>) {
        val now = now()
        db.withTransaction {
            ids.mapNotNull { notes.get(it) }.filter { !it.deleted && it.trashedAt == null }
                .forEach { notes.upsert(it.copy(trashedAt = now, isPinned = false, updatedAt = now)) }
        }
        scheduler.requestDebounced()
    }

    /**
     * Rewrites the tag in every note outside Recently Deleted. This is a text edit, so those notes count as
     * edited. In a locked note only the title can be rewritten (the body can't be read without the password).
     */
    suspend fun renameTag(key: String, newName: String) {
        if (!isValidTagName(newName)) return
        val now = now()
        var changed = false
        db.withTransaction {
            notes.live().filter { key in it.tagKeys }.forEach { note ->
                val blocks = note.blocks.map { replaceTag(it, key, newName) }
                if (blocks != note.blocks) {
                    notes.upsert(note.withBlocks(blocks, now))
                    changed = true
                }
            }
        }
        if (changed) scheduler.requestDebounced()
    }

    /**
     * After a note's title changed from [oldTitle] to [newTitle], points links to it at the new title. Skipped
     * when another live note still has the old title (those links still lead somewhere). A text edit, like
     * renaming a tag; locked notes' bodies can't be read, so links inside them aren't updated.
     */
    suspend fun renameLinks(noteId: String, oldTitle: String, newTitle: String) {
        if (oldTitle.isBlank() || newTitle.isBlank() || linkKey(oldTitle) == linkKey(newTitle)) return
        val now = now()
        var changed = false
        db.withTransaction {
            val live = notes.live()
            if (live.any { it.id != noteId && linkKey(it.title) == linkKey(oldTitle) }) return@withTransaction
            live.filter { it.id != noteId }.forEach { note ->
                val blocks = note.blocks.map { replaceLinks(it, oldTitle, newTitle) }
                if (blocks != note.blocks) {
                    notes.upsert(note.withBlocks(blocks, now))
                    changed = true
                }
            }
        }
        if (changed) scheduler.requestDebounced()
    }

    suspend fun togglePin(id: String) = update(id) { it.copy(isPinned = !it.isPinned, updatedAt = now()) }

    /** Sets or clears ([day] null) a to-do list's target end date; [day] is a LocalDate epoch day. */
    suspend fun setTargetDate(id: String, day: Long?) = update(id) { it.copy(targetDate = day, updatedAt = now()) }
    suspend fun setPaper(id: String, paper: Paper) =
        update(id) { it.copy(paper = paper.name.takeIf { paper != Paper.PLAIN }, updatedAt = now()) }

    suspend fun moveToTrash(id: String) = update(id) { it.copy(trashedAt = now(), isPinned = false, updatedAt = now()) }

    /** Back to the original folder, or to the default folder if that one is gone. */
    suspend fun recover(id: String) {
        val note = notes.get(id) ?: return
        val folder = folders.get(note.folderId)
        val target = if (folder != null && !folder.deleted) folder.id else DEFAULT_FOLDER_ID
        update(id) { it.copy(trashedAt = null, folderId = target, updatedAt = now()) }
    }

    suspend fun deleteForever(id: String) = update(id) { it.toTombstone(now()) }

    /**
     * A copy of the note in the same folder, titled "<title> copy" so links keep pointing at the original.
     * Attachments share files with the original (nothing edits a file in place). A locked note's copy keeps its lock.
     */
    suspend fun duplicate(id: String): NoteEntity? {
        val note = notes.get(id)?.takeIf { !it.deleted && it.trashedAt == null } ?: return null
        val now = now()
        val blocks = note.blocks.mapIndexed { i, b -> if (i == 0) b.copy(id = newId(), text = note.displayTitle + " copy") else b.copy(id = newId()) }
        val copy = note.copy(id = newId(), isPinned = false, createdAt = now, syncedUpdatedAt = null)
            .withBlocks(blocks, now) // a locked note's blocks are just its title; the cipher is copied as is
        notes.upsert(copy)
        scheduler.requestDebounced()
        return copy
    }

    suspend fun emptyTrash() {
        val now = now()
        notes.trashed().forEach { notes.upsert(it.toTombstone(now)) }
        scheduler.requestDebounced()
    }

    /** Attachment files any stored note still uses. */
    suspend fun attachmentsInUse(): Set<String> {
        versions.deleteOrphans() // history of notes deleted for good
        // Older versions can be restored, so the files they show are kept too.
        val old = versions.allBlocks().flatMap { json ->
            runCatching { JotterJson.decodeFromString<List<Block>>(json) }.getOrDefault(emptyList()).mapNotNull { it.attachment?.file }
        }
        return referencedAttachments(notes.all()) + old
    }

    /** Auto-purge after the "Keep deleted notes" setting (30 days by default). Runs on app start and in every sync. */
    suspend fun purgeExpiredTrash() {
        val now = now()
        val retention = appPrefs.keepDeletedDays.value * 24L * 60 * 60 * 1000
        val expired = notes.trashed().filter { it.isTrashExpired(now, retention) }
        expired.forEach { notes.upsert(it.toTombstone(now)) }
        if (expired.isNotEmpty()) scheduler.requestDebounced()
    }

    /**
     * After a sync: live notes whose folder was deleted on another device (or that ended up in a smart
     * folder) go to the default folder, and folders whose parent is gone or in a loop move to the top level.
     */
    suspend fun fixOrphans() {
        val all = folders.all()
        val noteFolderIds = all.filter { !it.deleted && !it.isSmart }.map { it.id }.toSet()
        val orphans = notes.live().filter { it.folderId !in noteFolderIds }
        orphans.forEach { notes.upsert(it.copy(folderId = DEFAULT_FOLDER_ID, updatedAt = now())) }
        val parents = effectiveParents(all)
        val loose = all.filter { !it.deleted && it.parentId != null && parents[it.id] == null }
        loose.forEach { folders.upsert(it.copy(parentId = null, updatedAt = now())) }
        if (orphans.isNotEmpty() || loose.isNotEmpty()) scheduler.requestDebounced()
    }

    private suspend fun update(id: String, change: (NoteEntity) -> NoteEntity) {
        val note = notes.get(id) ?: return
        if (note.deleted) return
        notes.upsert(change(note))
        scheduler.requestDebounced()
    }
}

private const val SESSION_MS = 10 * 60 * 1000L
private const val KEEP_VERSIONS = 50
