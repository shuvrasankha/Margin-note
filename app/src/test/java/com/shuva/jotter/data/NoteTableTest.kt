package com.shuva.jotter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoteTableTest {
    private val t = NoteTable(listOf(listOf("Item", "Qty"), listOf("Milk", "2"), listOf("Eggs", "12")))

    @Test fun normalizedPadsRaggedRowsAndKeepsCellsOnOneLine() {
        val n = NoteTable(listOf(listOf("a", "b\nc"), listOf("d"))).normalized()
        assertEquals(listOf(listOf("a", "b c"), listOf("d", "")), n.rows)
        assertEquals(listOf(listOf("")), NoteTable(emptyList()).normalized().rows)
    }

    @Test fun rowsAndColumnsAddAndRemoveButNeverBelowOne() {
        assertEquals(listOf("Milk", "2"), t.addRow(1).rows[2])
        assertEquals(listOf("", ""), t.addRow(1).rows[1])
        assertEquals(listOf("Item", "", "Qty"), t.addColumn(1).rows[0])
        assertEquals(listOf(listOf("Item"), listOf("Milk"), listOf("Eggs")), t.removeColumn(1).rows)
        assertEquals(2, t.removeRow(1).rowCount)
        val one = NoteTable(listOf(listOf("x")))
        assertEquals(one, one.removeRow(0))
        assertEquals(one, one.removeColumn(0))
    }

    @Test fun withCellChangesOneCell() {
        assertEquals(listOf("Milk", "3"), t.withCell(1, 1, "3").rows[1])
    }

    @Test fun plainTextAndMarkdown() {
        assertEquals("Item | Qty\nMilk | 2\nEggs | 12", t.plainText)
        assertEquals("| Item | Qty |\n| --- | --- |\n| Milk | 2 |\n| Eggs | 12 |", t.markdown())
        assertEquals("| a\\|b |\n| --- |", NoteTable(listOf(listOf("a|b"))).markdown())
    }

    @Test fun storedInJsonOnlyWhenPresent() {
        val plain = Block(id = "x", text = "hi")
        assertEquals(false, JotterJson.encodeToString(Block.serializer(), plain).contains("table"))
        val b = Block(id = "y", text = ATTACHMENT_CHAR.toString(), table = t)
        assertEquals(b, JotterJson.decodeFromString(Block.serializer(), JotterJson.encodeToString(Block.serializer(), b)))
        assertNull(plain.table)
        assertEquals(t.plainText, b.plainText)
    }
}
