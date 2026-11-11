package com.shuva.jotter.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ShareTextTest {
    private fun b(type: BlockType, text: String, checked: Boolean = false) = Block(type = type, text = text, checked = checked)

    @Test fun marksListLinesAndRestartsNumbering() {
        val text = shareText(
            listOf(
                b(BlockType.TITLE, "Trip"),
                b(BlockType.NUMBERED, "Pack"), b(BlockType.NUMBERED, "Drive"),
                b(BlockType.BODY, "Then"),
                b(BlockType.NUMBERED, "Sleep"),
                b(BlockType.BULLET, "a"), b(BlockType.DASHED, "b"),
                b(BlockType.CHECK, "done", checked = true), b(BlockType.CHECK, "todo"),
                b(BlockType.BODY, ""),
            ),
        )
        assertEquals("Trip\n1. Pack\n2. Drive\nThen\n1. Sleep\n• a\n- b\n[x] done\n[ ] todo", text)
    }
}
