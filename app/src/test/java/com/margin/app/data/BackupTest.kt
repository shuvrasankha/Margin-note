package com.margin.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.ZoneOffset

class BackupTest {
    private fun note(id: String, updated: Long, cipher: String? = null, deleted: Boolean = false) = NoteEntity(
        id = id, folderId = "f1", blocks = listOf(Block(type = BlockType.TITLE, text = id)), title = id, searchText = "",
        createdAt = 1, updatedAt = updated, cipher = cipher, deleted = deleted,
    )
    private val folder = FolderEntity(id = "f1", name = "Work", createdAt = 1, updatedAt = 5)

    @Test
    fun `a backup holds notes, folders and files exactly, and leaves out tombstones`() {
        val out = ByteArrayOutputStream()
        val files = mapOf("a1b2.jpg" to byteArrayOf(1, 2, 3), "c3d4.mlk" to byteArrayOf(9))
        val info = writeBackup(
            out, listOf(folder, folder.copy(id = "gone", deleted = true)),
            listOf(note("n1", 10), note("locked", 11, cipher = "v1.x.y.z.w"), note("dead", 12, deleted = true)),
            files.keys + "../evil", { files[it]?.let(::ByteArrayInputStream) }, now = 99,
        )
        assertEquals(2, info.noteCount); assertEquals(2, info.fileCount)
        val got = mutableMapOf<String, ByteArray>()
        val back = readBackup(ByteArrayInputStream(out.toByteArray())) { n, s -> got[n] = s.readBytes() }
        assertEquals(listOf(folder), back.info.folders)
        assertEquals(setOf("n1", "locked"), back.notes.map { it.id }.toSet())
        assertEquals("v1.x.y.z.w", back.notes.first { it.id == "locked" }.cipher) // locked stays sealed
        assertEquals(files.keys, got.keys)
        assertTrue(files["a1b2.jpg"]!!.contentEquals(got["a1b2.jpg"]))
        assertEquals(99, back.info.createdAt)
    }

    @Test(expected = NotABackupException::class)
    fun `a zip that isn't a backup is refused`() {
        val out = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { it.putNextEntry(java.util.zip.ZipEntry("x.txt")); it.write(1); it.closeEntry() }
        readBackup(ByteArrayInputStream(out.toByteArray())) { _, _ -> }
    }

    @Test
    fun `restore adds what's missing or older and keeps newer work here`() {
        val backup = BackupContents(
            BackupInfo(createdAt = 0, folders = listOf(folder), noteCount = 3, fileCount = 0),
            listOf(note("missing", 10), note("older-here", 20), note("newer-here", 20)),
        )
        val plan = restorePlan(
            localFolders = emptyList(),
            localNotes = listOf(note("older-here", 15), note("newer-here", 30, deleted = true)),
            backup = backup,
        )
        assertEquals(listOf("missing", "older-here"), plan.notes.map { it.id })
        assertTrue(plan.notes.all { it.syncedUpdatedAt == null }) // they sync on to other devices
        assertEquals(listOf("f1"), plan.folders.map { it.id })
    }

    @Test
    fun `names sort by time and pruning keeps the newest`() {
        val name = backupName(1_700_000_000_000, ZoneOffset.UTC)
        assertEquals("margin-backup-2023-11-14-221320.zip", name)
        assertTrue(isBackupName(name))
        assertEquals(1_700_000_000_000, backupTimeOf(name, ZoneOffset.UTC))
        assertNull(backupTimeOf("notes.zip"))
        val names = (1..12).map { backupName(it * 86_400_000L, ZoneOffset.UTC) } + "holiday.jpg"
        val pruned = backupsToPrune(names, 10)
        assertEquals(names.take(2).toSet(), pruned.toSet())
    }
}
