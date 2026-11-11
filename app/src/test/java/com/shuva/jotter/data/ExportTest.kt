package com.shuva.jotter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

class ExportTest {
    private fun b(text: String, type: BlockType = BlockType.BODY, checked: Boolean = false, marks: List<MarkSpan> = emptyList(), quote: Boolean = false, indent: Int = 0) =
        Block(type = type, text = text, checked = checked, marks = marks, quote = quote, indent = indent)

    @Test
    fun `note becomes Markdown with lists, quotes, code and escapes`() {
        val md = noteMarkdown(listOf(
            b("Trip", BlockType.TITLE),
            b("Plan", BlockType.HEADING),
            b("#tags at the start stay text"),
            b("2. not a list"),
            b(""),
            b("one", BlockType.NUMBERED), b("two", BlockType.NUMBERED), b("nested", BlockType.BULLET, indent = 1),
            b("done", BlockType.CHECK, checked = true),
            b("said once", quote = true), b("and again", quote = true),
            b("val x = 1", BlockType.MONOSPACED), b("", BlockType.MONOSPACED),
            b("a *star*"),
        ))
        assertEquals(
            "# Trip\n\n## Plan\n\n\\#tags at the start stay text\n\n2\\. not a list\n\n1. one\n2. two\n  - nested\n- [x] done\n\n" +
                "> said once\n> and again\n\n```\nval x = 1\n\n```\n\na \\*star\\*",
            md,
        )
    }

    @Test
    fun `inline styles nest properly when they overlap`() {
        // Italic inside bold stays inside it; the highlight is separate.
        val nested = listOf(MarkSpan(0, 6, Mark.BOLD), MarkSpan(2, 4, Mark.ITALIC), MarkSpan(7, 9, Mark.HIGHLIGHT, Tint.BLUE))
        assertEquals("**ab*cd*ef** <mark>gh</mark>", inlineMarkdown("abcdef gh", nested))
        // Bold ending inside italic: only what's needed closes and reopens.
        val overlap = listOf(MarkSpan(0, 4, Mark.BOLD), MarkSpan(2, 6, Mark.ITALIC))
        assertEquals("**ab*cd****ef*", inlineMarkdown("abcdef", overlap))
        assertEquals("a\\\\b\\_c", inlineMarkdown("a\\b_c", listOf(MarkSpan(5, 5, Mark.BOLD)))) // backslash and _ escaped
    }

    @Test
    fun `attachments become images, links or placeholders`() {
        val photo = Attachment("p.jpg", "beach.jpg", "image/jpeg")
        val pdf = Attachment("d.pdf", "ticket.pdf", "application/pdf")
        val link = Attachment("l.url", "Example", LINK_MIME)
        val blocks = listOf(b("T", BlockType.TITLE), Block(attachment = photo), Block(attachment = pdf), Block(attachment = link))
        assertEquals("# T\n\n![beach.jpg](att/p.jpg)\n\n*[ticket.pdf]*\n\n[Example](https://e.com/a%20b)",
            noteMarkdown(blocks, attachmentRef = { if (it.isImage) "att/" + it.file else null }, linkUrl = { "https://e.com/a b" }))
    }

    private fun folder(id: String, name: String, parent: String? = null) = FolderEntity(id, name, id == DEFAULT_FOLDER_ID, 0, 0, parentId = parent)
    private fun note(id: String, title: String, folder: String, created: Long = 0, blocks: List<Block> = listOf(b(title, BlockType.TITLE))) =
        NoteEntity(id, folder, blocks, title, "", createdAt = created, updatedAt = created)

    @Test
    fun `export paths follow folders, stay safe and never collide`() {
        val folders = listOf(folder(DEFAULT_FOLDER_ID, "Notes"), folder("w", "Work"), folder("s", "Q1/Q2", parent = "w"))
        val paths = exportPaths(
            listOf(note("a", "Plan", "s", 1), note("b", "plan", "s", 2), note("c", "a:b?", "gone", 3), note("d", "", "w", 4)),
            folders,
        )
        assertEquals("Work/Q1_Q2/Plan.md", paths["a"])
        assertEquals("Work/Q1_Q2/plan 2.md", paths["b"])
        assertEquals("Notes/a_b_.md", paths["c"])
        assertEquals("Work/New Note.md", paths["d"])
        assertEquals("Untitled", safeFileName(" .. "))
    }

    @Test
    fun `zip holds live notes and the files they use, not deleted or locked text`() {
        val dir = createTempDir()
        File(dir, "p.jpg").writeBytes(byteArrayOf(1, 2, 3))
        val photo = Attachment("p.jpg", "beach.jpg", "image/jpeg")
        val notes = listOf(
            note("a", "Trip", DEFAULT_FOLDER_ID, blocks = listOf(b("Trip", BlockType.TITLE), Block(attachment = photo))),
            note("b", "Gone", DEFAULT_FOLDER_ID).copy(trashedAt = 5),
            note("c", "Secret", DEFAULT_FOLDER_ID).copy(cipher = "xyz"),
        )
        val out = ByteArrayOutputStream()
        val count = writeMarkdownZip(out, notes, listOf(folder(DEFAULT_FOLDER_ID, "Notes"))) { File(dir, it.file) }
        assertEquals(2, count)
        val entries = mutableMapOf<String, String>()
        ZipInputStream(out.toByteArray().inputStream()).use { z ->
            while (true) { val e = z.nextEntry ?: break; entries[e.name] = z.readBytes().decodeToString() }
        }
        assertEquals(setOf("Notes/Trip.md", "Notes/Secret.md", "attachments/p.jpg"), entries.keys)
        assertEquals("# Trip\n\n![beach.jpg](../attachments/p.jpg)\n", entries["Notes/Trip.md"])
        assertTrue("xyz" !in entries.getValue("Notes/Secret.md"))
        dir.deleteRecursively()
    }

    @Test
    fun `shared text and photos make a note`() {
        assertEquals(listOf("Hello", "line 2", ""), sharedNoteBlocks(null, "Hello\nline 2\n", null).map { it.text })
        assertEquals(listOf("Article", "https://e.com", ""), sharedNoteBlocks("Article", "https://e.com", null).map { it.text })
        val photo = Attachment("p.jpg", "IMG_1.jpg", "image/jpeg")
        val blocks = sharedNoteBlocks(null, null, photo)
        assertEquals("IMG_1.jpg", blocks[0].text)
        assertEquals(photo, blocks[1].attachment)
        assertEquals(BlockType.TITLE, blocks[0].type)
    }
}
