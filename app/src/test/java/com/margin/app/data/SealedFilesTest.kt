package com.margin.app.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SealedFilesTest {
    @Test
    fun `a file sealed with its key opens only with that key`() {
        val photo = ByteArray(5000) { (it * 31).toByte() }
        val key = NoteCrypto.newFileKey()
        val sealed = NoteCrypto.sealBytes(photo, key)
        assertFalse(sealed.toList().windowed(16).contains(photo.copyOfRange(100, 116).toList())) // no plain bytes on disk
        assertArrayEquals(photo, NoteCrypto.openBytes(sealed, key))
        assertNull(NoteCrypto.openBytes(sealed, NoteCrypto.newFileKey()))
        assertNull(NoteCrypto.openBytes(photo, key)) // not a sealed file
        sealed[sealed.size - 1] = (sealed.last() + 1).toByte()
        assertNull(NoteCrypto.openBytes(sealed, key)) // tampered
    }

    @Test
    fun `file keys travel inside the locked body, which only the passcode opens`() {
        val a = Attachment("x.mlk", "Passport.jpg", "image/jpeg", NoteCrypto.newFileKey())
        val body = listOf(Block(text = ATTACHMENT_CHAR.toString(), attachment = a))
        val params = NoteCrypto.KeyParams(NoteCrypto.newSalt(), 1000)
        val sealed = NoteCrypto.seal(body, NoteCrypto.deriveKey("123456".toCharArray(), params.salt, 1000), params)
        assertFalse(a.key!! in sealed)
        assertEquals(a, NoteCrypto.open(sealed, NoteCrypto.deriveKey("123456".toCharArray(), params.salt, 1000))!!.single().attachment)
    }

    @Test
    fun `a locked note's placeholders are not searchable text and old notes still read`() {
        val note = NoteEntity(id = "n", folderId = "f", blocks = emptyList(), title = "", searchText = "", createdAt = 0, updatedAt = 0)
            .withBlocks(listOf(Block(type = BlockType.TITLE, text = "Passport"), sealedPlaceholder("x.mlk")), 1)
        assertEquals("", note.searchText)
        assertFalse(SmartFilter(hasAttachments = true).matches(note, 1))
        assertTrue("x.mlk" in referencedAttachments(listOf(note)))
        assertNull(MarginJson.decodeFromString<Attachment>("""{"file":"a.jpg","name":"a","mime":"image/jpeg"}""").key)
    }

    @Test
    fun `attachment names from synced or restored notes can't leave the attachments folder`() {
        assertTrue(isSafeAttachmentName("a1b2-c3.jpg"))
        listOf("../databases/margin.db", "..", "/etc/hosts", "a/b.jpg", "", ".invalid", "x.mlk\u0000").forEach { assertFalse(it, isSafeAttachmentName(it)) }
    }
}
