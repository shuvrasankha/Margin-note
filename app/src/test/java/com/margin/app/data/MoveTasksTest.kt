package com.margin.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MoveTasksTest {
    private fun task(text: String, checked: Boolean = false) = Block(type = BlockType.CHECK, text = text, checked = checked)
    private val title = Block(type = BlockType.TITLE, text = "Tomorrow")

    @Test fun addsAfterTheLastTaskWithoutDuplicates() {
        val dest = listOf(title, task("Call Sam"), task("Pay rent", checked = true), Block(text = "Notes below"))
        val moved = listOf(task("Email Jo"), task("call sam"), task("Pay rent"))
        val out = withTasksAdded(dest, moved)
        assertEquals(listOf("Tomorrow", "Call Sam", "Pay rent", "Email Jo", "Notes below"), out.blocks.map { it.text })
        // "Pay rent" was done there; brought in as to do, it's to do again.
        assertEquals(false, out.blocks[2].checked)
        assertEquals(2, out.added)
        assertNotEquals(moved[0].id, out.blocks[3].id)
    }

    @Test fun intoANoteWithoutTasks() {
        val out = withTasksAdded(listOf(title, Block(text = "")), listOf(task("Milk")))
        assertEquals(listOf("Tomorrow", "Milk"), out.blocks.map { it.text })
    }

    @Test fun newListAndRemoving() {
        val list = newListBlocks("Later", listOf(task("A").copy(indent = 2)))
        assertEquals(BlockType.TITLE, list[0].type)
        assertEquals(0, list[1].indent)
        val src = listOf(title, task("A"), task("B"), task("C"))
        assertEquals(listOf("Tomorrow", "B"), withoutLines(src, setOf(0, 1, 3)).map { it.text })
    }
}
