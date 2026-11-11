package com.shuva.jotter.sync

import com.shuva.jotter.data.Block
import com.shuva.jotter.data.BlockType
import com.shuva.jotter.data.NoteEntity
import com.shuva.jotter.data.withBlocks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncUxTest {
    @Test
    fun `banner speaks only when signed in and something is stuck`() {
        assertNull(syncBanner(signedIn = false, online = false, pending = 3, lastError = "x"))
        assertNull(syncBanner(signedIn = true, online = true, pending = 3, lastError = null)) // will sync shortly
        assertNull(syncBanner(signedIn = true, online = false, pending = 0, lastError = null))
        assertEquals(SyncBanner("Keep writing. Changes are saved on this phone and sync when you're back online.", retry = false, title = "You're offline"), syncBanner(true, false, 1, "boom"))
        assertTrue(syncBanner(true, true, 0, "HTTP 500")!!.retry)
    }

    private fun note(id: String, title: String, folder: String = "f", edited: Long = 1, trashed: Long? = null) =
        NoteEntity(id, folder, emptyList(), "", "", createdAt = 0, updatedAt = 0)
            .withBlocks(listOf(Block(type = BlockType.TITLE, text = title)), edited).copy(trashedAt = trashed)

    @Test
    fun `a conflict copy finds the note it came from`() {
        val local = note("a", "Plan", edited = 5)
        val remote = note("a", "Plan", edited = 9).withBlocks(listOf(Block(type = BlockType.TITLE, text = "Plan"), Block(text = "changed")), 9)
        val copy = resolveNoteConflict(local, remote, "copy", 10).conflictCopy!!
        assertEquals("Plan (conflict)", copy.title)
        val otherFolder = note("b", "Plan", folder = "g", edited = 20)
        val trashed = note("c", "Plan", trashed = 3)
        assertEquals("a", conflictOriginal(copy, listOf(remote, otherFolder, trashed, copy))?.id) // same folder wins
        assertEquals("b", conflictOriginal(copy, listOf(otherFolder, trashed))?.id)
        assertNull(conflictOriginal(copy, listOf(trashed, copy)))
        assertNull(conflictOriginal(remote, listOf(local))) // not a conflict copy
    }
}
