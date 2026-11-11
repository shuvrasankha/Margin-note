package com.shuva.jotter.sync

import com.shuva.jotter.data.isSafeAttachmentName
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

    @Test fun refusesNamesThatCouldLeaveTheFolder() {
        assertTrue(isSafeAttachmentName("0b9e-11ef.jpg"))
        assertTrue(isSafeAttachmentName("abc"))
        assertFalse(isSafeAttachmentName("../shared_prefs/x.xml"))
        assertFalse(isSafeAttachmentName("a/b"))
        assertFalse(isSafeAttachmentName(""))
    }
}
