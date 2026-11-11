package com.shuva.jotter.ui

import com.shuva.jotter.data.Block
import com.shuva.jotter.data.BlockType
import com.shuva.jotter.data.JotterJson
import com.shuva.jotter.data.MAX_INDENT
import com.shuva.jotter.data.Mark
import com.shuva.jotter.data.MarkSpan
import com.shuva.jotter.data.Tint
import com.shuva.jotter.data.shareText
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BlockStylesTest {
    private fun doc(vararg texts: String) = EditorDoc.fromBlocks(texts.mapIndexed { i, t -> Block(id = "b$i", type = if (i == 0) BlockType.TITLE else BlockType.BODY, text = t) })

    @Test
    fun `quote toggles on body lines but never the title`() {
        val d = doc("T", "a", "b").withQuote(0, 2)
        assertEquals(listOf(false, true, true), d.lines.map { it.quote })
        assertEquals(listOf(false, false, false), d.withQuote(1, 2).lines.map { it.quote })
    }

    @Test
    fun `indent stays between 0 and the max and skips the title`() {
        var d = doc("T", "a")
        repeat(MAX_INDENT + 2) { d = d.withIndent(0, 1, 1) }
        assertEquals(listOf(0, MAX_INDENT), d.lines.map { it.indent })
        assertEquals(MAX_INDENT - 1, d.withIndent(1, 1, -1).lines[1].indent)
    }

    @Test
    fun `Enter keeps quote and indent, and Enter on an empty quote line ends the quote`() {
        var d = doc("T", "a").withQuote(1, 1).withIndent(1, 1, 1)
        d = d.applyEdit("T\na\n", 4) { "n" }
        assertEquals(true to 1, d.lines[2].quote to d.lines[2].indent)
        d = d.applyEdit("T\na\n\n", 5) { "m" }
        assertEquals(3, d.lines.size)
        assertFalse(d.lines[2].quote)
        assertEquals(1, d.lines[2].indent)
    }

    @Test
    fun `highlight takes a colour and a new colour recolours instead of removing`() {
        val d = doc("T", "hello world")
        val blue = d.withMark(Mark.HIGHLIGHT, 2, 7, Tint.BLUE)
        assertEquals(listOf(MarkSpan(2, 7, Mark.HIGHLIGHT, Tint.BLUE)), blue.marks)
        val green = blue.withMark(Mark.HIGHLIGHT, 2, 4, Tint.GREEN)
        assertEquals(
            listOf(MarkSpan(2, 4, Mark.HIGHLIGHT, Tint.GREEN), MarkSpan(4, 7, Mark.HIGHLIGHT, Tint.BLUE)),
            green.marks.sortedBy { it.start },
        )
        assertEquals(emptyList<MarkSpan>(), blue.withMark(Mark.HIGHLIGHT, 2, 7, Tint.BLUE).marks) // same colour again removes
        assertEquals(listOf(MarkSpan(2, 7, Mark.HIGHLIGHT)), d.withMark(Mark.HIGHLIGHT, 2, 7).marks) // amber stores no tint
        // Styles survive a save: blocks keep the tint, quote and indent.
        val saved = EditorDoc.fromBlocks(green.withQuote(1, 1).withIndent(1, 1, 2).toBlocks())
        assertEquals(green.withQuote(1, 1).withIndent(1, 1, 2), saved)
    }

    @Test
    fun `new keys are left out when unset and old JSON still reads`() {
        val plain = listOf(Block(id = "x", text = "a", marks = listOf(MarkSpan(0, 1, Mark.HIGHLIGHT))))
        val json = JotterJson.encodeToString(ListSerializer(Block.serializer()), plain)
        assertFalse("quote" in json || "indent" in json || "tint" in json)
        val styled = listOf(Block(id = "x", text = "a", quote = true, indent = 2, marks = listOf(MarkSpan(0, 1, Mark.HIGHLIGHT, Tint.GREY))))
        assertEquals(styled, JotterJson.decodeFromString(ListSerializer(Block.serializer()), JotterJson.encodeToString(ListSerializer(Block.serializer()), styled)))
    }

    @Test
    fun `shared text shows indent and quote`() {
        assertEquals("T\n  > quoted", shareText(listOf(Block(type = BlockType.TITLE, text = "T"), Block(text = "quoted", quote = true, indent = 1))))
    }
}
