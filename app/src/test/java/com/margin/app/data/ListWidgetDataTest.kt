package com.margin.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ListWidgetDataTest {
    private fun note(id: String, title: String, vararg tasks: Pair<String, Boolean>, updated: Long = 0, cipher: String? = null) = NoteEntity(
        id = id, folderId = DEFAULT_FOLDER_ID, title = title, searchText = title, createdAt = 0, updatedAt = updated, cipher = cipher,
        blocks = listOf(Block(type = BlockType.TITLE, text = title)) + tasks.map { (t, done) -> Block(id = t, type = BlockType.CHECK, text = t, checked = done) },
    )

    @Test fun openTasksFirst() {
        val n = note("a", "Shopping", "milk" to true, "eggs" to false, "bread" to false)
        assertEquals(listOf("eggs", "bread", "milk"), ListWidgetData.tasks(n).map { it.text })
    }

    @Test fun picksChosenThenShoppingThenLatest() {
        val notes = listOf(
            note("todo", "Week", "x" to false, updated = 9),
            note("shop", "Groceries", "y" to false, updated = 1),
            note("plain", "Ideas"),
            note("locked", "Shopping list", "z" to false, updated = 20, cipher = "sealed"),
        )
        assertEquals("todo", ListWidgetData.pick(notes, "todo")?.id)
        assertEquals("shop", ListWidgetData.pick(notes, null)?.id)
        assertEquals("shop", ListWidgetData.pick(notes, "gone")?.id)
        assertEquals("todo", ListWidgetData.pick(notes.filter { it.id != "shop" }, null)?.id)
        assertEquals(listOf("todo", "shop"), ListWidgetData.lists(notes).map { it.id })
    }

    @Test fun togglesOneTask() {
        val n = note("a", "L", "milk" to false, "eggs" to false)
        assertEquals(listOf(true, false), ListWidgetData.toggled(n.blocks, "milk").drop(1).map { it.checked })
    }
}
