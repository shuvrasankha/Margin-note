package com.shuva.jotter.sync

import com.shuva.jotter.data.FolderEntity
import com.shuva.jotter.data.JotterJson
import com.shuva.jotter.data.NoteEntity
import com.shuva.jotter.data.newId
import kotlinx.serialization.Serializable

/** A file in the Drive appDataFolder. [updatedAt] comes from the file's appProperties. */
data class RemoteFile(val fileId: String, val name: String, val updatedAt: Long?)

interface DriveStore {
    suspend fun list(): List<RemoteFile>
    suspend fun download(fileId: String): String
    /** Creates the file when [fileId] is null, else replaces it. Returns the file ID. */
    suspend fun upload(name: String, fileId: String?, content: String, updatedAt: Long): String
}

interface SyncLocalStore {
    suspend fun allFolders(): List<FolderEntity>
    suspend fun allNotes(): List<NoteEntity>
    /**
     * Writes [folder] only if the local row still has [expectedUpdatedAt] (null = row absent),
     * so an edit made during the sync is never overwritten. [force] skips the check (restore).
     */
    suspend fun saveFolder(folder: FolderEntity, expectedUpdatedAt: Long?, force: Boolean = false)
    suspend fun saveNote(note: NoteEntity, expectedUpdatedAt: Long?, force: Boolean = false)
    suspend fun markFolderSynced(id: String, updatedAt: Long)
    suspend fun markNoteSynced(id: String, updatedAt: Long)
}

@Serializable
data class Manifest(val folders: List<FolderEntity>)

data class SyncReport(val uploaded: Int = 0, val downloaded: Int = 0, val conflicts: Int = 0)

const val MANIFEST_NAME = "manifest.json"
fun noteFileName(id: String) = "note-$id.json"
private fun noteIdFromName(name: String) = name.removePrefix("note-").removeSuffix(".json")

/**
 * Drive layout: one JSON file per note plus manifest.json holding all folders.
 * The offline queue is simply every local record whose updatedAt != syncedUpdatedAt; each
 * successful sync drains it, and a failed one leaves it in place for next time.
 */
class SyncEngine(
    private val local: SyncLocalStore,
    private val drive: DriveStore,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = ::newId,
) {
    private var uploaded = 0
    private var downloaded = 0
    private var conflicts = 0

    suspend fun sync(): SyncReport {
        uploaded = 0; downloaded = 0; conflicts = 0
        val remote = drive.list()
        syncFolders(remote.firstOrNull { it.name == MANIFEST_NAME })
        syncNotes(remote.filter { it.name.startsWith("note-") && it.name.endsWith(".json") })
        return SyncReport(uploaded, downloaded, conflicts)
    }

    /** Copies everything in Drive over the local copies, then syncs to push local-only records. */
    suspend fun restore(): SyncReport {
        val remote = drive.list()
        remote.firstOrNull { it.name == MANIFEST_NAME }?.let { file ->
            readManifest(file).forEach { local.saveFolder(it.copy(syncedUpdatedAt = it.updatedAt), null, force = true) }
        }
        remote.filter { it.name.startsWith("note-") }.forEach { file ->
            val note = JotterJson.decodeFromString<NoteEntity>(drive.download(file.fileId))
            local.saveNote(note.copy(syncedUpdatedAt = note.updatedAt), null, force = true)
        }
        return sync()
    }

    private suspend fun syncFolders(manifestFile: RemoteFile?) {
        val remoteFolders = manifestFile?.let { readManifest(it) }.orEmpty().associateBy { it.id }
        val localFolders = local.allFolders().associateBy { it.id }
        val merged = mutableListOf<FolderEntity>()
        val toMarkSynced = mutableListOf<FolderEntity>()

        for (id in localFolders.keys + remoteFolders.keys) {
            val l = localFolders[id]
            val r = remoteFolders[id]
            when (decide(l?.updatedAt, l?.syncedUpdatedAt, r?.updatedAt)) {
                SyncAction.NONE -> merged += (r ?: l!!)
                SyncAction.UPLOAD -> { merged += l!!; toMarkSynced += l }
                SyncAction.DOWNLOAD -> { takeRemoteFolder(r!!, l?.updatedAt); merged += r }
                SyncAction.CONFLICT -> {
                    if (l!!.name != r!!.name || l.deleted != r.deleted) conflicts++
                    if (folderLocalWins(l, r)) { merged += l; toMarkSynced += l } else { takeRemoteFolder(r, l.updatedAt); merged += r }
                }
            }
        }

        if (toMarkSynced.isNotEmpty() || (manifestFile == null && merged.isNotEmpty())) {
            val content = JotterJson.encodeToString(Manifest(merged.sortedBy { it.id }))
            drive.upload(MANIFEST_NAME, manifestFile?.fileId, content, merged.maxOf { it.updatedAt })
            toMarkSynced.forEach { local.markFolderSynced(it.id, it.updatedAt) }
            uploaded += toMarkSynced.size
        }
    }

    private suspend fun syncNotes(remoteFiles: List<RemoteFile>) {
        val remoteById = remoteFiles.associateBy { noteIdFromName(it.name) }
        val localById = local.allNotes().associateBy { it.id }

        for (id in localById.keys + remoteById.keys) {
            val l = localById[id]
            val rf = remoteById[id]
            when (decide(l?.updatedAt, l?.syncedUpdatedAt, rf?.updatedAt)) {
                SyncAction.NONE -> Unit
                SyncAction.UPLOAD -> uploadNote(l!!, rf?.fileId)
                SyncAction.DOWNLOAD -> takeRemoteNote(fetchNote(rf!!), l?.updatedAt)
                SyncAction.CONFLICT -> {
                    val r = fetchNote(rf!!)
                    val result = resolveNoteConflict(l!!, r, newId(), now())
                    if (result.conflictCopy != null || result.localWins) conflicts++
                    if (result.localWins) uploadNote(l, rf.fileId) else takeRemoteNote(r, l.updatedAt)
                    result.conflictCopy?.let { copy ->
                        local.saveNote(copy, expectedUpdatedAt = null)
                        uploadNote(copy, null)
                    }
                }
            }
        }
    }

    private suspend fun uploadNote(note: NoteEntity, fileId: String?) {
        drive.upload(noteFileName(note.id), fileId, JotterJson.encodeToString(note), note.updatedAt)
        local.markNoteSynced(note.id, note.updatedAt)
        uploaded++
    }

    private suspend fun takeRemoteNote(remote: NoteEntity, expectedLocal: Long?) {
        local.saveNote(remote.copy(syncedUpdatedAt = remote.updatedAt), expectedLocal)
        downloaded++
    }

    private suspend fun takeRemoteFolder(remote: FolderEntity, expectedLocal: Long?) {
        local.saveFolder(remote.copy(syncedUpdatedAt = remote.updatedAt), expectedLocal)
        downloaded++
    }

    private suspend fun fetchNote(file: RemoteFile): NoteEntity =
        JotterJson.decodeFromString(drive.download(file.fileId))

    private suspend fun readManifest(file: RemoteFile): List<FolderEntity> =
        JotterJson.decodeFromString<Manifest>(drive.download(file.fileId)).folders
}
