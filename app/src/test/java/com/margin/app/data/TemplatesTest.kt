package com.margin.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplatesTest {
    @Test fun everyTemplateStartsWithAHeadingAndHasRoomToWrite() {
        NoteTemplate.entries.forEach { t ->
            val b = t.blocks()
            assertEquals(t.label, BlockType.HEADING, b.first().type)
            assertTrue(t.label, b[1].table == null && b[1].text.isEmpty()) // the cursor lands here
            b.filter { it.table != null }.forEach { assertEquals(ATTACHMENT_CHAR.toString(), it.text) }
        }
    }

    @Test fun labNoteIsUnchanged() {
        val b = NoteTemplate.LAB.blocks()
        assertEquals(listOf("Aim", "Materials", "Method", "Observations", "Result", "Conclusion"), b.filter { it.type == BlockType.HEADING }.map { it.text })
        assertEquals(listOf("Trial", "Reading", "Notes"), b.single { it.table != null }.table!!.rows.first())
    }

    @Test fun keysRoundTripAndSavedTemplatesStayOutOfAllNotes() {
        NoteTemplate.entries.forEach { assertEquals(it, builtInTemplate(templateKey(it))) }
        assertEquals(null, builtInTemplate("template-abc"))
        val note = { id: String -> NoteEntity(id, DEFAULT_FOLDER_ID, emptyList(), "", "", createdAt = 0, updatedAt = 0) }
        assertTrue(isTemplate(note("template-1")))
        assertEquals(false, com.margin.app.ui.inAllNotes(note("template-1")))
        assertEquals(true, com.margin.app.ui.inAllNotes(note("n1")))
    }
}
