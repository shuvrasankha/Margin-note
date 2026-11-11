package com.shuva.jotter.sync

import com.shuva.jotter.data.FolderEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class SyncEngineTest {
    private val drive = FakeDrive()
    private val clock = Clock()
    private val phone = Device(drive, clock, "phone")
    private val tablet = Device(drive, clock, "tablet")

    @Test
    fun `new note uploads and reaches a second device`() = runBlocking {
        phone.write("n1", "Groceries", "milk")
        phone.engine.sync()
        tablet.engine.sync()

        assertEquals("Groceries", tablet.note("n1").title)
        assertEquals(1, drive.noteFiles().size)
        assertTrue(phone.store.queue().isEmpty())
        assertTrue(tablet.store.queue().isEmpty())
    }

    @Test
    fun `last write wins when only one side changed`() = runBlocking {
        phone.write("n1", "v1")
        phone.engine.sync(); tablet.engine.sync()

        tablet.write("n1", "v2 from tablet")
        tablet.engine.sync()
        val report = phone.engine.sync()

        assertEquals("v2 from tablet", phone.note("n1").title)
        assertEquals(1, report.downloaded)
        assertEquals(0, report.conflicts)
        assertEquals(1, drive.noteFiles().size) // no conflict copy
    }

    @Test
    fun `both changed - newer copy wins and the loser is kept as a conflict copy`() = runBlocking {
        phone.write("n1", "Plan")
        phone.engine.sync(); tablet.engine.sync()

        phone.write("n1", "Plan", "phone edit")     // older
        tablet.write("n1", "Plan", "tablet edit")   // newer
        phone.engine.sync()                          // phone uploads first (no conflict yet)
        val report = tablet.engine.sync()            // tablet sees both changed

        assertEquals(1, report.conflicts)
        assertEquals("tablet edit", tablet.note("n1").searchText) // newer wins
        val copy = tablet.store.notes.values.single { it.id != "n1" }
        assertEquals("Plan (conflict)", copy.title)
        assertEquals("phone edit", copy.searchText)               // loser's content kept

        phone.engine.sync()
        assertEquals(listOf("Plan", "Plan (conflict)"), phone.titles())
        assertEquals("tablet edit", phone.note("n1").searchText)
        assertEquals(2, drive.noteFiles().size)
        assertTrue(phone.store.queue().isEmpty() && tablet.store.queue().isEmpty())
    }

    @Test
    fun `conflict tie on updatedAt goes to the remote copy`() {
        phone.write("n1", "x", "local")
        val local = phone.note("n1").copy(updatedAt = 5_000)
        tablet.write("n1", "x", "remote")
        val remote = tablet.note("n1").copy(updatedAt = 5_000)
        val result = resolveNoteConflict(local, remote, "copy", 9_000)
        assertFalse(result.localWins)
        assertEquals("local", result.conflictCopy!!.searchText)
    }

    @Test
    fun `tombstone propagates a permanent delete`() = runBlocking {
        phone.write("n1", "Old note")
        phone.engine.sync(); tablet.engine.sync()

        phone.deleteForever("n1")
        phone.engine.sync()
        tablet.engine.sync()

        assertTrue(tablet.note("n1").deleted)
        assertTrue(tablet.note("n1").blocks.isEmpty())
        assertTrue(drive.note("n1")!!.deleted) // tombstone stays in Drive
        assertTrue(tablet.titles().isEmpty())
    }

    @Test
    fun `edit that loses to a tombstone survives as a conflict copy`() = runBlocking {
        phone.write("n1", "Ideas")
        phone.engine.sync(); tablet.engine.sync()

        tablet.write("n1", "Ideas", "keep me")  // older edit
        phone.deleteForever("n1")               // newer delete
        phone.engine.sync()
        tablet.engine.sync()

        assertTrue(tablet.note("n1").deleted)
        assertEquals(listOf("Ideas (conflict)"), tablet.titles())
        assertEquals("keep me", tablet.store.notes.values.single { !it.deleted }.searchText)
    }

    @Test
    fun `tombstone that loses to a newer edit produces no copy`() = runBlocking {
        phone.write("n1", "Ideas")
        phone.engine.sync(); tablet.engine.sync()

        phone.deleteForever("n1")               // older delete
        tablet.write("n1", "Ideas", "revived")  // newer edit
        phone.engine.sync()
        tablet.engine.sync()
        phone.engine.sync()

        assertEquals(listOf("Ideas"), tablet.titles())
        assertEquals("revived", phone.note("n1").searchText)
        assertFalse(phone.note("n1").deleted)
        assertEquals(1, drive.noteFiles().size)
    }

    @Test
    fun `offline edits queue up and are all sent once online`() = runBlocking {
        drive.online = false
        phone.write("a", "First")
        phone.write("b", "Second")
        phone.write("a", "First, edited")
        phone.write("c", "Third")
        assertEquals(4, phone.store.queue().size) // 3 notes + the never-synced default folder

        try {
            phone.engine.sync()
            fail("sync should fail while offline")
        } catch (expected: IOException) {
        }
        assertEquals(4, phone.store.queue().size) // nothing lost, nothing marked synced
        assertTrue(drive.files.isEmpty())

        phone.write("b", "Second, edited offline")
        drive.online = true
        val report = phone.engine.sync()

        assertTrue(phone.store.queue().isEmpty())
        assertEquals(3 + 1, report.uploaded) // 3 notes + default folder in the manifest
        assertEquals("First, edited", drive.note("a")!!.title)
        assertEquals("Second, edited offline", drive.note("b")!!.title)
        assertEquals(0, phone.engine.sync().uploaded) // queue drained, second sync is a no-op
    }

    @Test
    fun `an edit made during a sync is not overwritten by a download`() = runBlocking {
        phone.write("n1", "v1")
        phone.engine.sync(); tablet.engine.sync()
        tablet.write("n1", "v2"); tablet.engine.sync()

        // Phone edits locally after deciding to download but before saving: simulated by
        // saving the remote copy with a stale expected timestamp.
        phone.write("n1", "local typing")
        val remote = drive.note("n1")!!
        phone.store.saveNote(remote.copy(syncedUpdatedAt = remote.updatedAt), expectedUpdatedAt = 1L)

        assertEquals("local typing", phone.note("n1").title)
        assertTrue(phone.note("n1") in phone.store.queue())
    }

    @Test
    fun `folders sync through the manifest with last write wins`() = runBlocking {
        phone.store.folders["f1"] = FolderEntity("f1", "Work", createdAt = clock.tick(), updatedAt = clock.t)
        phone.engine.sync(); tablet.engine.sync()
        assertEquals("Work", tablet.store.folders.getValue("f1").name)

        phone.store.folders["f1"] = phone.store.folders.getValue("f1").copy(name = "Job", updatedAt = clock.tick())
        tablet.store.folders["f1"] = tablet.store.folders.getValue("f1").copy(name = "Office", updatedAt = clock.tick())
        phone.engine.sync(); tablet.engine.sync(); phone.engine.sync()

        assertEquals("Office", phone.store.folders.getValue("f1").name)
        assertEquals("Office", tablet.store.folders.getValue("f1").name)
        assertEquals(1, drive.files.values.count { it.name == MANIFEST_NAME })
    }

    @Test
    fun `identical default folders on two fresh devices do not conflict`() = runBlocking {
        phone.engine.sync()
        val report = tablet.engine.sync()
        assertEquals(0, report.conflicts)
        assertTrue(tablet.store.queue().isEmpty())
        assertNull(tablet.store.notes.values.firstOrNull())
    }

    @Test
    fun `restore replaces local copies with the Drive versions`() = runBlocking {
        phone.write("n1", "From Drive")
        phone.engine.sync()

        tablet.write("n1", "Stale local")      // same id, different local content
        tablet.write("local-only", "Only here")
        tablet.engine.restore()

        assertEquals("From Drive", tablet.note("n1").title)
        assertEquals("Only here", tablet.note("local-only").title)
        assertTrue(drive.note("local-only") != null) // local-only notes are pushed after restore
    }
}
