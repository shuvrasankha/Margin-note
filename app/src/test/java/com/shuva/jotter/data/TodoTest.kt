package com.shuva.jotter.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class TodoTest {
    private fun tasks(done: Int, open: Int) =
        listOf(Block(type = BlockType.TITLE, text = "List")) +
            List(done) { Block(type = BlockType.CHECK, text = "d$it", checked = true) } +
            List(open) { Block(type = BlockType.CHECK, text = "o$it") } +
            Block(type = BlockType.BODY, text = "not a task")

    @Test fun `counts only checklist lines`() {
        assertEquals(TodoCount(2, 5), todoCount(tasks(2, 3)))
        assertEquals(TodoCount(0, 0), todoCount(listOf(Block(type = BlockType.TITLE, text = "x"))))
    }

    @Test fun `status against the target day`() {
        val half = TodoCount(5, 10)
        // Made on day 0, due on day 10.
        assertEquals(TodoStatus.ON_TRACK, todoStatus(half, 0, 10, 5)) // half done, half the time gone
        assertEquals(TodoStatus.ON_TRACK, todoStatus(half, 0, 10, 6)) // within the slack
        assertEquals(TodoStatus.BEHIND, todoStatus(half, 0, 10, 7))
        assertEquals(TodoStatus.DUE_TODAY, todoStatus(half, 0, 10, 10))
        assertEquals(TodoStatus.OVERDUE, todoStatus(half, 0, 10, 11))
        assertEquals(TodoStatus.DONE, todoStatus(TodoCount(10, 10), 0, 10, 20)) // done wins over overdue
        assertEquals(TodoStatus.NO_TARGET, todoStatus(half, 0, null, 5))
        assertEquals(TodoStatus.ON_TRACK, todoStatus(TodoCount(0, 3), 4, 4 + 30, 4)) // made today
    }

    @Test fun `report lists urgent lists first and leaves out locked and plain notes`() {
        val zone = ZoneOffset.UTC
        val today = LocalDate.of(2026, 11, 11).toEpochDay()
        val made = LocalDate.of(2026, 11, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        fun note(id: String, blocks: List<Block>, target: Long?, cipher: String? = null) = NoteEntity(
            id = id, folderId = "f", blocks = blocks, title = id, searchText = "", createdAt = made, updatedAt = made,
            targetDate = target, cipher = cipher,
        )
        val report = todoReport(
            listOf(
                note("later", tasks(1, 1), today + 20),
                note("done", tasks(2, 0), today - 3),
                note("overdue", tasks(1, 2), today - 1),
                note("plain", listOf(Block(type = BlockType.TITLE, text = "p")), null),
                note("locked", tasks(0, 2), today, cipher = "x"),
                note("today", tasks(0, 1), today),
                note("noDate", tasks(0, 4), null),
            ),
            today, zone,
        )
        assertEquals(listOf("overdue", "today", "later", "noDate", "done"), report.lists.map { it.note.id })
        assertEquals(4, report.done)
        assertEquals(12, report.total)
        assertEquals(1, report.count(TodoStatus.OVERDUE))
        assertEquals(0, report.dueThisWeek(today))
    }
}
