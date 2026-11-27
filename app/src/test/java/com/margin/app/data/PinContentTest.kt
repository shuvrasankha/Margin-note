package com.margin.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinContentTest {
    private fun note(blocks: List<Block>, cipher: String? = null) =
        NoteEntity("n", "f", blocks, blocks.first().text, "", createdAt = 0, updatedAt = 0, cipher = cipher)

    @Test fun listShowsWhatIsLeftAndTheNextTask() {
        val milk = Block(type = BlockType.CHECK, text = "Milk", checked = true)
        val bread = Block(type = BlockType.CHECK, text = "Bread")
        val jam = Block(type = BlockType.CHECK, text = "Jam")
        val c = pinContent(note(listOf(Block(type = BlockType.TITLE, text = "Shop"), milk, bread, jam)))
        assertEquals("Shop", c.title)
        assertEquals(listOf("○ Bread", "○ Jam"), c.lines)
        assertEquals("1 of 3 done", c.summary)
        assertEquals(bread.id to "Bread", c.next)
    }

    @Test fun finishedListAndPlainNote() {
        val done = pinContent(note(listOf(Block(type = BlockType.TITLE, text = "Shop"), Block(type = BlockType.CHECK, text = "Milk", checked = true))))
        assertEquals(listOf("All done"), done.lines)
        assertNull(done.next)
        val plain = pinContent(note(listOf(Block(type = BlockType.TITLE, text = "Parking"), Block(text = "Level 3, bay 42"), Block(text = ""))))
        assertEquals(listOf("Level 3, bay 42"), plain.lines)
        assertNull(plain.summary)
    }

    @Test fun lockedNoteShowsNothing() {
        val c = pinContent(note(listOf(Block(type = BlockType.TITLE, text = "Codes")), cipher = "sealed"))
        assertTrue(c.locked)
        assertEquals(listOf("Locked note"), c.lines)
        assertNull(c.next)
    }
}

class TicksFromTest {
    @Test fun takesTicksAndKeepsUnsavedEdits() {
        val open = listOf(
            Block(type = BlockType.TITLE, text = "Shop"),
            Block(type = BlockType.CHECK, text = "Milk"),
            Block(type = BlockType.CHECK, text = "Eggs"),
            Block(type = BlockType.CHECK, text = "Tea (new, not saved yet)"),
        )
        val stored = listOf(
            Block(type = BlockType.TITLE, text = "Shop"),
            Block(type = BlockType.CHECK, text = "Milk"),
            Block(type = BlockType.CHECK, text = "Eggs", checked = true),
        )
        val out = withTicksFrom(open, stored)
        assertEquals(listOf(false, false, true, false), out.map { it.checked })
        assertEquals(open.map { it.text }, out.map { it.text })
    }
}
