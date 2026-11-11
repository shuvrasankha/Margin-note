package com.shuva.jotter.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattingTest {
    private fun line(text: String, type: BlockType = BlockType.CHECK, checked: Boolean = false) =
        Block(id = text, type = type, text = text, checked = checked)

    private fun List<Block>.sorted(index: Int) =
        sortCheckedRun(index, isCheck = { it.type == BlockType.CHECK }, isChecked = { it.checked }).map { it.text }

    @Test
    fun `checking an item moves it below the unchecked items of its checklist`() {
        val note = listOf(
            line("Title", BlockType.TITLE),
            line("a", checked = true), line("b"), line("c", checked = true), line("d"),
            line("after", BlockType.BODY),
        )
        assertEquals(listOf("Title", "b", "d", "a", "c", "after"), note.sorted(2))
    }

    @Test
    fun `only the checklist run containing the item is sorted`() {
        val note = listOf(
            line("x", checked = true), line("y"),
            line("break", BlockType.BODY),
            line("p", checked = true), line("q"),
        )
        assertEquals(listOf("x", "y", "break", "q", "p"), note.sorted(3))
    }

    @Test
    fun `a non-checklist index leaves the note unchanged`() {
        val note = listOf(line("h", BlockType.HEADING), line("a", checked = true), line("b"))
        assertEquals(listOf("h", "a", "b"), note.sorted(0))
    }

    @Test
    fun `new styles survive a JSON round trip`() {
        val blocks = listOf(BlockType.SUBHEADING, BlockType.MONOSPACED, BlockType.DASHED).map { Block(type = it, text = "t") }
        val json = JotterJson.encodeToString(blocks)
        assertEquals(blocks, JotterJson.decodeFromString<List<Block>>(json))
    }
}
