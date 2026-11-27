package com.margin.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedListTest {
    @Test fun readsMarginsOwnShareText() {
        val blocks = listOf(
            Block(type = BlockType.TITLE, text = "Shopping list"),
            Block(type = BlockType.CHECK, text = "Milk"),
            Block(type = BlockType.CHECK, text = "Eggs", checked = true),
        )
        val list = parseSharedList(null, shareText(blocks))!!
        assertEquals("Shopping list", list.title)
        assertEquals(listOf(SharedList.Item("Milk", false), SharedList.Item("Eggs", true)), list.items)
    }

    @Test fun readsOtherAppsStyles() {
        val list = parseSharedList("Groceries", "☐ Bread\n✅ Butter\n- [ ] Jam\n* [x] Tea\n✓ Rice")!!
        assertEquals("Groceries", list.title)
        assertEquals(listOf(false, true, false, true, true), list.items.map { it.checked })
        assertEquals(listOf("Bread", "Butter", "Jam", "Tea", "Rice"), list.items.map { it.text })
    }

    @Test fun ignoresTextThatIsNotAList() {
        assertNull(parseSharedList(null, "Hello\nhow are you"))
        assertNull(parseSharedList(null, "Trip notes\nWe drove for hours.\nThen lunch.\nThen a hike.\n[ ] book hotel"))
        assertNull(parseSharedList(null, "[ ]"))
    }

    @Test fun mergeAddsNewTicksDoneAndNeverUnticks() {
        val blocks = listOf(
            Block(type = BlockType.TITLE, text = "Shopping"),
            Block(type = BlockType.CHECK, text = "Milk"),
            Block(type = BlockType.CHECK, text = "Eggs", checked = true),
            Block(type = BlockType.BODY, text = "Bring bags"),
        )
        val shared = SharedList("Shopping list", listOf(
            SharedList.Item("milk", true), SharedList.Item("Eggs", false), SharedList.Item("Bread", false), SharedList.Item("Jam", true),
        ))
        val m = mergeSharedList(blocks, shared)
        assertEquals(2, m.added)
        assertEquals(1, m.ticked)
        assertEquals(listOf("Shopping", "Milk", "Eggs", "Bread", "Jam", "Bring bags"), m.blocks.map { it.text })
        assertEquals(listOf(false, true, true, false, true, false), m.blocks.map { it.checked })
        assertTrue(m.changed)
        // Sharing the same list again changes nothing.
        val again = mergeSharedList(m.blocks, shared)
        assertEquals(0, again.added + again.ticked)
    }

    @Test fun mergeIntoListWithoutTasks() {
        val m = mergeSharedList(listOf(Block(type = BlockType.TITLE, text = "Shopping"), Block()), SharedList("Shopping", listOf(SharedList.Item("Milk", false))))
        assertEquals(listOf(BlockType.TITLE, BlockType.CHECK), m.blocks.map { it.type })
    }
}
