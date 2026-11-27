package com.margin.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AislesTest {
    @Test fun findsTheAisle() {
        assertEquals(Aisle.PRODUCE, aisleOf("2 kg Potatoes"))
        assertEquals(Aisle.PRODUCE, aisleOf("Bananas"))
        assertEquals(Aisle.DAIRY, aisleOf("Eggs (12)"))
        assertEquals(Aisle.FROZEN, aisleOf("Ice cream"))
        assertEquals(Aisle.PANTRY, aisleOf("olive oil"))
        assertEquals(Aisle.HOUSEHOLD, aisleOf("AA batteries"))
        assertEquals(Aisle.BAKERY, aisleOf("Bread"))
        assertEquals(Aisle.OTHER, aisleOf("Birthday card"))
        assertEquals(Aisle.SNACKS, aisleOf("Birthday card", mapOf(itemKey("birthday cards") to Aisle.SNACKS)))
    }

    @Test fun groupsUnderHeadingsInShopOrder() {
        val blocks = listOf(
            Block(type = BlockType.TITLE, text = "Shopping"),
            Block(type = BlockType.CHECK, text = "Milk", checked = true),
            Block(type = BlockType.CHECK, text = "Batteries"),
            Block(type = BlockType.CHECK, text = "Apples"),
            Block(type = BlockType.CHECK, text = "Cheese"),
            Block(type = BlockType.CHECK, text = "Oranges"),
            Block(text = ""),
        )
        val out = byAisle(blocks)
        assertEquals(
            listOf("Shopping", "Fruit & veg", "Apples", "Oranges", "Dairy & eggs", "Cheese", "Household", "Batteries", "Done", "Milk", ""),
            out.map { it.text },
        )
        // Sorting again gives the same, without doubling the headings.
        assertEquals(out.map { it.text }, byAisle(out).map { it.text })
        // Ticking one off and sorting again moves it under Done.
        val ticked = out.map { if (it.text == "Cheese") it.copy(checked = true) else it }
        assertEquals(listOf("Shopping", "Fruit & veg", "Apples", "Oranges", "Household", "Batteries", "Done", "Cheese", "Milk", ""), byAisle(ticked).map { it.text })
    }
}
