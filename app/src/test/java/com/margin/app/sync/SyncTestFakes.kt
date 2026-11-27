package com.margin.app.sync

import com.margin.app.data.Block
import com.margin.app.data.BlockType
import com.margin.app.data.DEFAULT_FOLDER_ID
import com.margin.app.data.FolderEntity
import com.margin.app.data.MarginJson
import com.margin.app.data.NoteEntity
import com.margin.app.data.isDirty
import com.margin.app.data.toTombstone
import com.margin.app.data.withBlocks
import java.io.IOException

class Clock(var t: Long = 1_000) {
    fun tick(): Long { t += 1_000; return t }
}

/** In-memory appDataFolder. Set [online] = false to simulate no network. */
class FakeDrive : DriveStore {
    data class File(val name: String, val content: String, val updatedAt: Long)

    var online = true
    val files = mutableMapOf<String, File>()
    private var nextId = 0

    override suspend fun list(): List<RemoteFile> {
        ensureOnline()
        return files.map { (id, f) -> RemoteFile(id, f.name, f.updatedAt) }
    }

    override suspend fun download(fileId: String): String { ensureOnline(); return files.getValue(fileId).content }

    override suspend fun upload(name: String, fileId: String?, content: String, updatedAt: Long): String {
        ensureOnline()
        val id = fileId ?: "file-${nextId++}"
        files[id] = File(name, content, updatedAt)
        revisions[id] = revisions.getOrDefault(id, emptyList()) + content
        return id
    }

    override suspend fun delete(fileId: String) { ensureOnline(); files.remove(fileId); revisions.remove(fileId) }

    /** Every version of each file, oldest first, as Drive's revision history keeps them. */
    val revisions = mutableMapOf<String, List<String>>()

    override suspend fun dropOldRevisions(fileId: String) { ensureOnline(); revisions[fileId] = listOfNotNull(revisions[fileId]?.last()) }

    fun noteRevisions(id: String) = files.entries.first { it.value.name == noteFileName(id) }.key.let { revisions.getValue(it) }

    fun note(id: String): NoteEntity? =
        files.values.firstOrNull { it.name == noteFileName(id) }?.let { MarginJson.decodeFromString<NoteEntity>(it.content) }

    fun noteFiles() = files.values.filter { it.name.startsWith("note-") }

    private fun ensureOnline() { if (!online) throw IOException("offline") }
}

class FakeStore : SyncLocalStore {
    val folders = mutableMapOf<String, FolderEntity>()
    val notes = mutableMapOf<String, NoteEntity>()

    override suspend fun allFolders() = folders.values.toList()
    override suspend fun allNotes() = notes.values.toList()

    override suspend fun saveFolder(folder: FolderEntity, expectedUpdatedAt: Long?, force: Boolean) {
        if (force || folders[folder.id]?.updatedAt == expectedUpdatedAt) folders[folder.id] = folder
    }

    override suspend fun saveNote(note: NoteEntity, expectedUpdatedAt: Long?, force: Boolean) {
        if (force || notes[note.id]?.updatedAt == expectedUpdatedAt) notes[note.id] = note
    }

    override suspend fun markFolderSynced(id: String, updatedAt: Long) {
        folders[id]?.let { folders[id] = it.copy(syncedUpdatedAt = updatedAt) }
    }

    override suspend fun markNoteSynced(id: String, updatedAt: Long) {
        notes[id]?.let { notes[id] = it.copy(syncedUpdatedAt = updatedAt) }
    }

    /** The offline edit queue: records not yet confirmed by Drive. */
    fun queue() = notes.values.filter { it.isDirty } + folders.values.filter { it.isDirty }
}

/** One phone: its own local store, sharing a Drive and clock with other devices. */
class Device(drive: FakeDrive, private val clock: Clock, name: String) {
    val store = FakeStore()
    private var copies = 0
    val engine = SyncEngine(store, drive, now = { clock.tick() }, newId = { "$name-copy-${copies++}" })

    init {
        store.folders[DEFAULT_FOLDER_ID] = FolderEntity(DEFAULT_FOLDER_ID, "Notes", isDefault = true, createdAt = 0, updatedAt = 0)
    }

    fun write(id: String, title: String, body: String = "") {
        val now = clock.tick()
        val base = store.notes[id] ?: NoteEntity(
            id = id, folderId = DEFAULT_FOLDER_ID, blocks = emptyList(), title = "", searchText = "",
            createdAt = now, updatedAt = now,
        )
        val blocks = listOf(Block(id = "$id-t", type = BlockType.TITLE, text = title), Block(id = "$id-b", text = body))
        store.notes[id] = base.withBlocks(blocks, now)
    }

    /** Locks the note: only the title stays readable; the body becomes a (fake) sealed string. */
    fun lock(id: String) {
        val n = store.notes.getValue(id)
        store.notes[id] = n.copy(blocks = n.blocks.take(1), searchText = "", cipher = "j1.sealed", updatedAt = clock.tick())
    }

    fun deleteForever(id: String) { store.notes[id] = store.notes.getValue(id).toTombstone(clock.tick()) }

    fun note(id: String) = store.notes.getValue(id)
    fun titles() = store.notes.values.filter { !it.deleted }.map { it.title }.sorted()
}
