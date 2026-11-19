package com.margin.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WithTaskTest {
    private fun b(type: BlockType, text: String) = Block(type = type, text = text)

    @Test fun goesAfterTheLastTask() {
        val out = withTask(listOf(b(BlockType.TITLE, "Trip"), b(BlockType.CHECK, "Pack"), b(BlockType.BODY, "Notes below")), "Book taxi")
        assertEquals(listOf("Trip", "Pack", "Book taxi", "Notes below"), out.map { it.text })
        assertEquals(BlockType.CHECK, out[2].type)
    }

    @Test fun fillsAnEmptyLastLineWhenThereAreNoTasks() {
        val out = withTask(listOf(b(BlockType.TITLE, "Shop"), b(BlockType.BODY, "")), "Milk")
        assertEquals(listOf("Shop", "Milk"), out.map { it.text })
    }
}
