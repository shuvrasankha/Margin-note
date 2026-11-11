package com.shuva.jotter.ui

import com.shuva.jotter.data.ATTACHMENT_CHAR
import com.shuva.jotter.data.Attachment
import com.shuva.jotter.data.Block
import com.shuva.jotter.data.BlockType
import com.shuva.jotter.data.Mark
import com.shuva.jotter.data.MarkSpan
import org.junit.Assert.assertEquals
import org.junit.Test

class EditorDocTest {
    private var ids = 0
    private fun id() = "new${ids++}"
    private fun doc(vararg lines: Pair<String, BlockType>) =
        EditorDoc.fromBlocks(lines.mapIndexed { i, (t, type) -> Block(id = "b$i", type = type, text = t) })

    @Test
    fun `typing right after Enter lands on the new line with the list style carried over`() {
        var d = doc("Shopping" to BlockType.TITLE, "milk" to BlockType.BULLET)
        d = d.applyEdit("Shopping\nmilk\n", 14, ::id)
        d = d.applyEdit("Shopping\nmilk\nb", 15, ::id)
        assertEquals(listOf("Shopping", "milk", "b"), d.texts)
        assertEquals(listOf(BlockType.TITLE, BlockType.BULLET, BlockType.BULLET), d.lines.map { it.type })
        assertEquals(listOf("b0", "b1", "new0"), d.lines.map { it.id })
    }

    @Test
    fun `Enter on an empty list line ends the list without adding a line`() {
        val d = doc("T" to BlockType.TITLE, "" to BlockType.CHECK).applyEdit("T\n\n", 3, ::id)
        assertEquals(listOf("T", ""), d.texts)
        assertEquals(BlockType.BODY, d.lines[1].type)
    }

    @Test
    fun `backspace at the start of a line merges it into the line above`() {
        val d = doc("T" to BlockType.TITLE, "ab" to BlockType.HEADING, "cd" to BlockType.BULLET)
            .applyEdit("T\nabcd", 4, ::id)
        assertEquals(listOf("T", "abcd"), d.texts)
        assertEquals(listOf("b0", "b1"), d.lines.map { it.id })
        assertEquals(BlockType.HEADING, d.lines[1].type)
    }

    @Test
    fun `Enter after the title starts a body line and blank lines keep their own ids`() {
        var d = doc("T" to BlockType.TITLE)
        d = d.applyEdit("T\n", 2, ::id)
        d = d.applyEdit("T\n\n", 3, ::id)
        assertEquals(listOf(BlockType.TITLE, BlockType.BODY, BlockType.BODY), d.lines.map { it.type })
        assertEquals(listOf("b0", "new0", "new1"), d.lines.map { it.id })
    }

    @Test
    fun `pasting several lines adds one styled line per newline`() {
        val d = doc("T" to BlockType.TITLE, "x" to BlockType.NUMBERED).applyEdit("T\nx1\n2\n3", 8, ::id)
        assertEquals(listOf("T", "x1", "2", "3"), d.texts)
        assertEquals(List(3) { BlockType.NUMBERED }, d.lines.drop(1).map { it.type })
    }

    @Test
    fun `toggling with auto-sort moves the checked line and keeps text and styles aligned`() {
        val d = doc("T" to BlockType.TITLE, "a" to BlockType.CHECK, "b" to BlockType.CHECK).toggleChecked(1, autoSort = true)
        assertEquals(listOf("T", "b", "a"), d.texts)
        assertEquals(listOf(false, false, true), d.lines.map { it.checked })
    }

    @Test fun marksSurviveEditsSavingAndChecklistSorting() {
        fun Block.styled() = text.substring(marks[0].start, marks[0].end)
        var d = doc("Title" to BlockType.TITLE, "one two" to BlockType.CHECK, "three" to BlockType.CHECK)
        d = d.withMark(Mark.BOLD, 10, 17) // "two", the line break, "thr"
        val saved = d.toBlocks()
        assertEquals(listOf(MarkSpan(4, 7, Mark.BOLD)), saved[1].marks)
        assertEquals(listOf(MarkSpan(0, 3, Mark.BOLD)), saved[2].marks)
        assertEquals(saved, EditorDoc.fromBlocks(saved).toBlocks())
        d = d.applyEdit("Title\nX one two\nthree", 8) // typing before the styled text moves it
        assertEquals("two", d.toBlocks()[1].styled())
        val sorted = d.toggleChecked(1, autoSort = true).toBlocks() // the ticked line moves with its style
        assertEquals("X one two", sorted[2].text)
        assertEquals("two", sorted[2].styled())
        assertEquals(emptyList<MarkSpan>(), d.withMark(Mark.BOLD, 12, 19).marks) // same selection again removes it
    }

    @Test fun attachmentsKeepALineOfTheirOwn() {
        val a = Attachment("f.jpg", "photo.jpg", "image/jpeg")
        val start = EditorDoc.fromBlocks(
            listOf(Block(type = BlockType.TITLE, text = "T"), Block(text = "\uFFFC", attachment = a), Block(text = "next")),
        )
        // Typed right after the attachment: the text moves to its own line.
        val typed = start.applyEdit("T\n\uFFFCx\nnext", 4)
        assertEquals(listOf("T", "\uFFFC", "x", "next"), typed.texts)
        assertEquals(a, typed.lines[1].attachment)
        // Backspace at the start of the next line can't pull text onto the attachment's line.
        assertEquals(listOf("T", "\uFFFC", "next"), start.applyEdit("T\n\uFFFCnext", 3).texts)
        // Deleting the placeholder deletes the attachment.
        val gone = start.applyEdit("T\n\nnext", 2)
        assertEquals(listOf("T", "", "next"), gone.texts)
        assertEquals(null, gone.lines[1].attachment)
        // It survives saving.
        assertEquals(a, EditorDoc.fromBlocks(start.toBlocks()).lines[1].attachment)
    }

    @Test fun cursorAfterRewriteStaysAfterTextTypedBeforeAnAttachment() {
        val a = ATTACHMENT_CHAR
        // "G" typed at the start of the attachment line; the doc moves it to its own line above.
        assertEquals(4, cursorAfterRewrite("T\nAG${a}", 4, "T\nAG\n$a")) // stays after "G", not in front of the attachment
        // Typed after the attachment: the line break lands before the cursor, which moves past it.
        assertEquals(5, cursorAfterRewrite("T\n${a}G", 4, "T\n$a\nG"))
        // A shorter result than the cursor position falls back to counting from the end.
        assertEquals(2, cursorAfterRewrite("T\n\n", 3, "T\n"))
    }

    @Test
    fun `smart checklist shortcut turns a bracket prefix into a checklist line`() {
        val d = doc("T" to BlockType.TITLE, "[ ] milk" to BlockType.BODY, "[x] eggs" to BlockType.BODY, "[ ]x" to BlockType.BODY, "[ ] a" to BlockType.BULLET)
        val milk = d.withChecklistShortcut(1)
        assertEquals("milk", milk.texts[1])
        assertEquals(BlockType.CHECK to false, milk.lines[1].type to milk.lines[1].checked)
        val eggs = d.withChecklistShortcut(2)
        assertEquals("eggs", eggs.texts[2]); assertEquals(true, eggs.lines[2].checked)
        assertEquals(d, d.withChecklistShortcut(3)) // no space after the brackets
        assertEquals(d, d.withChecklistShortcut(4)) // only body lines
        val title = doc("[ ] t" to BlockType.TITLE)
        assertEquals(title, title.withChecklistShortcut(0)) // never the title
    }

    @Test
    fun `list shortcut turns a typed marker into a list line`() {
        val d = doc("T" to BlockType.TITLE, "- " to BlockType.BODY, "* a" to BlockType.BODY, "12) " to BlockType.BODY, "-x" to BlockType.BODY, "- " to BlockType.CHECK)
        val dash = d.withListShortcut(1, 4) // cursor after "- "
        assertEquals("", dash.texts[1]); assertEquals(BlockType.DASHED, dash.lines[1].type)
        val bullet = d.withListShortcut(2, 7)
        assertEquals("a", bullet.texts[2]); assertEquals(BlockType.BULLET, bullet.lines[2].type)
        assertEquals(d, d.withListShortcut(2, 9)) // cursor not right after the marker
        val numbered = d.withListShortcut(3, 13)
        assertEquals("", numbered.texts[3]); assertEquals(BlockType.NUMBERED, numbered.lines[3].type)
        assertEquals(d, d.withListShortcut(4, 16)) // no space
        assertEquals(d, d.withListShortcut(5, 19)) // only body lines
        val title = doc("- " to BlockType.TITLE)
        assertEquals(title, title.withListShortcut(0, 2))
    }

    @Test
    fun `drop order places the dragged row by its centre among its siblings`() {
        // Siblings at rows 1, 3 and 4 (row 2 is row 1's subfolder); rows are 10 tall.
        assertEquals(listOf(3, 1, 4) to 1, dropOrder(listOf(1, 3, 4), 1, 37f, 10f)) // dragged row 1 below row 3's centre
        assertEquals(listOf(1, 3, 4) to 0, dropOrder(listOf(1, 3, 4), 1, 15f, 10f)) // not moved
        assertEquals(listOf(4, 1, 3) to 0, dropOrder(listOf(1, 3, 4), 4, 5f, 10f)) // dragged to the top
    }
}

class EditorDocTableTest {
    private val table = com.shuva.jotter.data.NoteTable(listOf(listOf("A", "B"), listOf("1", "2")))
    private val doc = EditorDoc.fromBlocks(
        listOf(
            com.shuva.jotter.data.Block(type = com.shuva.jotter.data.BlockType.TITLE, text = "T"),
            com.shuva.jotter.data.Block(text = com.shuva.jotter.data.ATTACHMENT_CHAR.toString(), table = table),
            com.shuva.jotter.data.Block(text = ""),
        ),
    )

    @org.junit.Test fun tableSurvivesRoundTripAndTypingAfterIt() {
        org.junit.Assert.assertEquals(table, doc.toBlocks()[1].table)
        val typed = doc.text + "x"
        val after = doc.applyEdit(typed, typed.length)
        org.junit.Assert.assertEquals(table, after.lines[1].table)
        org.junit.Assert.assertEquals("x", after.texts[2])
    }

    @org.junit.Test fun textTypedOnTheTableLineMovesToItsOwnLine() {
        val at = doc.lineStart(1) + 1 // right after the placeholder
        val typed = doc.text.substring(0, at) + "hi" + doc.text.substring(at)
        val after = doc.applyEdit(typed, at + 2)
        org.junit.Assert.assertEquals(table, after.lines[1].table)
        org.junit.Assert.assertEquals("hi", after.texts[2])
        org.junit.Assert.assertEquals(null, after.lines[2].table)
    }

    @org.junit.Test fun deletingThePlaceholderRemovesTheTable() {
        val start = doc.lineStart(1)
        val typed = doc.text.removeRange(start, start + 1)
        val after = doc.applyEdit(typed, start)
        org.junit.Assert.assertEquals(true, after.lines.none { it.table != null })
    }
}
