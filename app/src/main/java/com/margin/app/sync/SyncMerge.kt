package com.margin.app.sync

import com.margin.app.data.BlockType
import com.margin.app.data.FolderEntity
import com.margin.app.data.NoteEntity
import com.margin.app.data.isLocked
import com.margin.app.data.withBlocks
import com.margin.app.data.lastEdited

enum class SyncAction { NONE, UPLOAD, DOWNLOAD, CONFLICT }

/**
 * Decides what to do with one record. "Changed" means its updatedAt differs from the version
 * both sides agreed on at the last sync ([localSyncedAt]).
 */
fun decide(localUpdatedAt: Long?, localSyncedAt: Long?, remoteUpdatedAt: Long?): SyncAction {
    if (localUpdatedAt == null) return if (remoteUpdatedAt == null) SyncAction.NONE else SyncAction.DOWNLOAD
    if (remoteUpdatedAt == null) return SyncAction.UPLOAD
    val localChanged = localUpdatedAt != localSyncedAt
    val remoteChanged = remoteUpdatedAt != localSyncedAt
    return when {
        localChanged && remoteChanged -> SyncAction.CONFLICT
        localChanged -> SyncAction.UPLOAD
        remoteChanged -> SyncAction.DOWNLOAD
        else -> SyncAction.NONE
    }
}

data class NoteConflictResult(val localWins: Boolean, val conflictCopy: NoteEntity?)

/**
 * Both copies changed since the last sync. Last write (higher updatedAt) wins; a tie goes to the
 * remote copy so every device picks the same winner. The losing copy is kept as a new note titled
 * "<title> (conflict)", unless it's a tombstone (nothing to keep) or both copies are identical.
 *
 * A lock sticks: when only one side is locked (and neither is deleted), the locked one wins whatever its time, so
 * the plain text never replaces it. Plain-text edits made here are kept as a [NoteEntity.localOnly] copy that
 * never syncs; plain text that came from Drive isn't copied, so it can't reappear beside the locked note.
 */
fun resolveNoteConflict(local: NoteEntity, remote: NoteEntity, copyId: String, now: Long): NoteConflictResult {
    if (sameContent(local, remote)) return NoteConflictResult(localWins = false, conflictCopy = null)
    if (local.isLocked != remote.isLocked && !local.deleted && !remote.deleted) {
        if (local.isLocked) return NoteConflictResult(localWins = true, conflictCopy = null)
        val copy = if (isEmptyVersionOf(local, remote)) null else conflictCopyOf(local, copyId, now).copy(localOnly = true)
        return NoteConflictResult(localWins = false, conflictCopy = copy)
    }
    val localWins = local.updatedAt > remote.updatedAt
    val loser = if (localWins) remote else local
    val winner = if (localWins) local else remote
    val copy = if (loser.deleted || isEmptyVersionOf(loser, winner)) null else conflictCopyOf(loser, copyId, now)
    return NoteConflictResult(localWins, copy)
}

/** Folders have no body to preserve: plain last-write-wins, tie to remote. */
fun folderLocalWins(local: FolderEntity, remote: FolderEntity): Boolean =
    local.copy(syncedUpdatedAt = null) != remote && local.updatedAt > remote.updatedAt

/** [loser] is only [winner]'s title (a note caught by a sync just after it was started): keeping it saves nothing. */
private fun isEmptyVersionOf(loser: NoteEntity, winner: NoteEntity) =
    loser.cipher == null && loser.title.trim() == winner.title.trim() &&
        loser.blocks.drop(1).all { it.text.isBlank() && it.attachment == null && it.table == null }

private fun sameContent(a: NoteEntity, b: NoteEntity) =
    a.copy(updatedAt = 0, syncedUpdatedAt = null) == b.copy(updatedAt = 0, syncedUpdatedAt = null)

private fun conflictCopyOf(loser: NoteEntity, copyId: String, now: Long): NoteEntity {
    val blocks = loser.blocks.ifEmpty { listOf(com.margin.app.data.Block(type = BlockType.TITLE)) }
    val titleBlock = blocks.first()
    val renamed = listOf(titleBlock.copy(text = "${titleBlock.text.trim().ifBlank { "New Note" }}$CONFLICT_SUFFIX")) +
        blocks.drop(1)
    return loser.copy(id = copyId, createdAt = now, syncedUpdatedAt = null).withBlocks(renamed, now)
}

/** The banner above the folders: why changes aren't reaching Drive. [retry] means tapping it should sync again. */
data class SyncBanner(val text: String, val retry: Boolean, val title: String? = null)

fun syncBanner(signedIn: Boolean, online: Boolean, pending: Int, lastError: String?): SyncBanner? = when {
    !signedIn -> null
    !online && pending > 0 -> SyncBanner("Keep writing. Changes are saved on this phone and sync when you're back online.", retry = false, title = "You're offline")
    !online -> null
    lastError != null -> SyncBanner("Couldn't sync with Google Drive. Tap to try again.", retry = true)
    else -> null
}

const val CONFLICT_SUFFIX = " (conflict)"

/**
 * The note a conflict copy ([resolveNoteConflict] titles it "<title> (conflict)") was split from: a live note
 * with the original title, preferring the copy's folder, then the most recently edited. Null if it's gone.
 */
// shortcut: found by title (copies don't store the original's id); a renamed original isn't found, so its copy shows no chooser.
fun conflictOriginal(copy: NoteEntity, notes: List<NoteEntity>): NoteEntity? {
    if (!copy.title.endsWith(CONFLICT_SUFFIX)) return null
    val title = copy.title.removeSuffix(CONFLICT_SUFFIX).trim()
    return notes.filter { it.id != copy.id && !it.deleted && it.trashedAt == null && it.title.trim() == title }
        .sortedWith(compareByDescending<NoteEntity> { it.folderId == copy.folderId }.thenByDescending { it.lastEdited })
        .firstOrNull()
}
