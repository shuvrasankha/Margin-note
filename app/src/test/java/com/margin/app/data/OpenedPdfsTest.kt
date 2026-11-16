package com.margin.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenedPdfsTest {
    private fun note(id: String, vararg blocks: Block, trashed: Boolean = false, cipher: String? = null) = NoteEntity(
        id = id, folderId = "f-$id", blocks = listOf(Block(type = BlockType.TITLE, text = id)) + blocks, title = id, searchText = "",
        createdAt = 0, updatedAt = 0, trashedAt = if (trashed) 1L else null, cipher = cipher,
    )
    private fun pdf(line: String, file: String) = Block(id = line, text = ATTACHMENT_CHAR.toString(), attachment = Attachment(file, "$file.pdf", PDF_MIME))

    @Test fun sha256OfBytes() {
        val (sha, size) = sha256("abc".byteInputStream())
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha)
        assertEquals(3L, size)
    }

    @Test fun rememberedNoteIsFoundWhileItStillHoldsThePdf() {
        val notes = listOf(note("a", pdf("L1", "x")))
        assertEquals(PdfPlace("a", "f-a", "L1"), rememberedPdf(notes, "a", "L1"))
        assertNull(rememberedPdf(notes, "a", "L2")) // the PDF was taken out of the note
        assertNull(rememberedPdf(notes, "b", "L1")) // the note is gone
        assertNull(rememberedPdf(listOf(note("a", pdf("L1", "x"), trashed = true)), "a", "L1"))
    }

    @Test fun lockedNoteIsTrustedUnread() {
        // A locked note's body can't be read, so the remembered line is taken on trust.
        assertEquals(PdfPlace("a", "f-a", "L1"), rememberedPdf(listOf(note("a", cipher = "sealed")), "a", "L1"))
    }

    @Test fun samePdfMatchesBytesAndSkipsOtherSizes() {
        val notes = listOf(note("a", pdf("L1", "small")), note("b", pdf("L2", "big")), note("c", pdf("L3", "big2"), trashed = true))
        val sizes = mapOf("small" to 10L, "big" to 99L, "big2" to 99L)
        val read = mutableListOf<String>()
        val hit = samePdf(notes, "H", 99L, { sizes[it.file] ?: -1 }) { read += it.file; if (it.file == "big") "H" else "other" }
        assertEquals(PdfPlace("b", "f-b", "L2"), hit)
        assertEquals(listOf("big"), read) // only same-size files are hashed; the trashed one isn't looked at
        assertNull(samePdf(notes, "nope", 99L, { sizes[it.file] ?: -1 }) { "H" })
    }
}
