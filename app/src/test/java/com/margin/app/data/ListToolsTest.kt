package com.margin.app.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class ListToolsTest {
    private fun task(t: String, done: Boolean = false, due: Long? = null, indent: Int = 0) = Block(type = BlockType.CHECK, text = t, checked = done, due = due, indent = indent)
    private val title = Block(type = BlockType.TITLE, text = "List")

    @Test fun quantities() {
        assertEquals("Eggs" to 12, splitQty("Eggs ×12"))
        assertEquals("Eggs" to 3, splitQty("Eggs x3 "))
        assertEquals("Box 5" to 1, splitQty("Box 5"))
        assertEquals("Eggs ×2", withQty("Eggs", 2))
        assertEquals("Eggs", withQty("Eggs ", 1))
        assertEquals(itemName("eggs"), itemName("Eggs  ×12"))
    }

    @Test fun clearsTickedAndEmptyHeadings() {
        val blocks = listOf(
            title, Block(type = BlockType.SUBHEADING, text = "Dairy & eggs"), task("Milk", true),
            Block(type = BlockType.SUBHEADING, text = "Bakery"), task("Bread"),
            Block(text = "note to self"), Block(type = BlockType.SUBHEADING, text = "Done"), task("Jam", true),
        )
        assertEquals(listOf("List", "Bakery", "Bread", "note to self"), withoutTicked(blocks).map { it.text })
        assertEquals(2, tickedCount(blocks))
    }

    @Test fun sortsWithinRunsAndKeepsSubtasks() {
        val blocks = listOf(title, task("pears"), task("apples"), task("green", indent = 1), Block(text = "—"), task("Zucchini"), task("banana"))
        assertEquals(listOf("List", "apples", "green", "pears", "—", "banana", "Zucchini"), sortedTasks(blocks, ListSort.AZ, Locale.UK).map { it.text })
        assertEquals(listOf("List", "pears", "apples", "green", "—", "Zucchini", "banana"), sortedTasks(blocks, ListSort.ZA, Locale.UK).map { it.text })
        val due = listOf(title, task("later"), task("soon", due = 5), task("sooner", due = 2), task("done", true))
        assertEquals(listOf("sooner", "soon", "later", "done"), sortedTasks(due, ListSort.DUE).drop(1).map { it.text })
        assertEquals(listOf("later", "soon", "sooner", "done"), sortedTasks(listOf(title, task("done", true)) + due.drop(1).dropLast(1), ListSort.UNDONE).drop(1).map { it.text })
    }

    @Test fun suggestsPastItemsMostUsedFirst() {
        fun note(id: String, vararg items: String, locked: Boolean = false) = NoteEntity(
            id, "f", listOf(title) + items.map { task(it) }, "List", "", createdAt = 0, updatedAt = 0, cipher = if (locked) "x" else null,
        )
        val past = pastItems(listOf(note("a", "Milk ×2", "Mint"), note("b", "milk", "Mince"), note("c", "Mango", locked = true), note("me", "Muesli")), "me")
        assertEquals(listOf("Milk", "Mint", "Mince"), past.map { it.text })
        assertEquals(listOf("Milk", "Mint", "Mince"), suggestItems(past, "Mi", emptySet()).map { it.text })
        assertEquals(listOf("Mint", "Mince"), suggestItems(past, "mi", setOf("milk")).map { it.text })
        assertEquals(emptyList<PastItem>(), suggestItems(past, "M", emptySet()))
        assertEquals(emptyList<PastItem>(), suggestItems(past, "milk", emptySet()))
    }

    @Test fun dueLabelsAndOverdue() {
        val today = LocalDate.of(2026, 11, 20) // a Friday
        val d = today.toEpochDay()
        assertEquals("Today", dueLabel(d, today, Locale.UK))
        assertEquals("Tomorrow", dueLabel(d + 1, today, Locale.UK))
        assertEquals("Tue", dueLabel(d + 4, today, Locale.UK))
        assertEquals("3 Dec", dueLabel(d + 13, today, Locale.UK))
        assertTrue(overdue(task("x", due = d - 1), today))
        assertEquals(false, overdue(task("x", true, due = d - 1), today))
    }

    @Test fun dueDateIsStoredOnlyWhenSet() {
        val json = Json { ignoreUnknownKeys = true }
        assertEquals(false, json.encodeToString(Block.serializer(), task("a")).contains("due"))
        assertEquals(7L, json.decodeFromString(Block.serializer(), json.encodeToString(Block.serializer(), task("a", due = 7))).due)
        assertNull(json.decodeFromString(Block.serializer(), """{"id":"1","type":"CHECK","text":"a"}""").due)
    }
}
