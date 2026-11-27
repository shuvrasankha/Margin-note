package com.margin.app.sync

import com.margin.app.data.isSafeAttachmentName
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AttachmentSyncTest {
    @Test fun uploadsWhatDriveLacksAndDownloadsWhatThisDeviceLacks() = runBlocking {
        val drive = FakeDrive()
        val phoneA = Files.createTempDirectory("a").toFile()
        val phoneB = Files.createTempDirectory("b").toFile()
        val bytes = byteArrayOf(0, 1, 2, -1, 42)
        File(phoneA, "abc.jpg").writeBytes(bytes)

        assertEquals(1, syncAttachments(drive, phoneA, setOf("abc.jpg")))
        assertEquals(0, syncAttachments(drive, phoneA, setOf("abc.jpg"))) // already there
        assertEquals(1, syncAttachments(drive, phoneB, setOf("abc.jpg")))
        assertArrayEquals(bytes, File(phoneB, "abc.jpg").readBytes())
    }

    @Test fun removesFilesNoNoteUsesFromDrive() = runBlocking {
        val drive = FakeDrive()
        val phone = Files.createTempDirectory("p").toFile()
        File(phone, "plain.jpg").writeBytes(byteArrayOf(1))
        File(phone, "kept.jpg").writeBytes(byteArrayOf(2))
        syncAttachments(drive, phone, setOf("plain.jpg", "kept.jpg"))
        // The note was locked: its photo is now "sealed.jpg", and nothing uses the readable "plain.jpg".
        File(phone, "sealed.jpg").writeBytes(byteArrayOf(3))
        syncAttachments(drive, phone, setOf("sealed.jpg", "kept.jpg"))
        assertEquals(setOf("att-sealed.jpg", "att-kept.jpg"), drive.files.values.map { it.name }.toSet())
        // Deleted for good (or emptied from Recently Deleted): its files go too; notes' own files are left alone.
        drive.upload("note-x", null, "{}", 0)
        syncAttachments(drive, phone, emptySet())
        assertEquals(setOf("note-x"), drive.files.values.map { it.name }.toSet())
    }

    @Test fun refusesNamesThatCouldLeaveTheFolder() {
        assertTrue(isSafeAttachmentName("0b9e-11ef.jpg"))
        assertTrue(isSafeAttachmentName("abc"))
        assertFalse(isSafeAttachmentName("../shared_prefs/x.xml"))
        assertFalse(isSafeAttachmentName("a/b"))
        assertFalse(isSafeAttachmentName(""))
    }
}
