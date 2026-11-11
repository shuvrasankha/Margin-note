package com.shuva.jotter.sync

import com.shuva.jotter.data.Block
import com.shuva.jotter.data.BlockType
import com.shuva.jotter.data.NoteEntity
import com.shuva.jotter.data.withBlocks
import com.shuva.jotter.data.TRASH_RETENTION_MS
import com.shuva.jotter.data.isTrashExpired
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergeTest {
    @Test
    fun `decide covers every combination`() {
        assertEquals(SyncAction.NONE, decide(null, null, null))
        assertEquals(SyncAction.DOWNLOAD, decide(null, null, 5))      // only remote has it
        assertEquals(SyncAction.UPLOAD, decide(5, null, null))        // only local has it
        assertEquals(SyncAction.NONE, decide(5, 5, 5))                // in sync
        assertEquals(SyncAction.UPLOAD, decide(7, 5, 5))              // local changed
        assertEquals(SyncAction.DOWNLOAD, decide(5, 5, 9))            // remote changed
        assertEquals(SyncAction.CONFLICT, decide(7, 5, 9))            // both changed
        assertEquals(SyncAction.CONFLICT, decide(7, null, 9))         // never synced, both exist
    }

    @Test
    fun `identical copies never produce a conflict copy`() {
        val n = note(updatedAt = 1)
        val result = resolveNoteConflict(n.copy(updatedAt = 10), n.copy(updatedAt = 20), "c", 30)
        assertFalse(result.localWins)
        assertEquals(null, result.conflictCopy)
    }

    @Test
    fun `newer local copy wins and remote becomes the conflict copy`() {
        val result = resolveNoteConflict(note(updatedAt = 20, text = "local"), note(updatedAt = 10, text = "remote"), "c", 30)
        assertTrue(result.localWins)
        assertEquals("remote", result.conflictCopy!!.searchText)
        assertEquals("c", result.conflictCopy!!.id)
        assertEquals(null, result.conflictCopy!!.syncedUpdatedAt) // copy is queued for upload
    }

    @Test
    fun `Recently Deleted expires after exactly 30 days`() {
        val trashed = note(updatedAt = 0).copy(trashedAt = 1_000)
        assertFalse(trashed.isTrashExpired(1_000 + TRASH_RETENTION_MS - 1))
        assertTrue(trashed.isTrashExpired(1_000 + TRASH_RETENTION_MS))
        assertFalse(note(updatedAt = 0).isTrashExpired(Long.MAX_VALUE)) // not in trash
    }

    private fun note(updatedAt: Long, text: String = "") = NoteEntity(
        id = "n", folderId = "f", blocks = emptyList(), title = "", searchText = "", createdAt = 0, updatedAt = 0,
    ).withBlocks(listOf(Block(id = "t", type = BlockType.TITLE, text = "T"), Block(id = "b", text = text)), updatedAt)
}
