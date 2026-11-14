package com.shuva.jotter.data

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteFontTest {
    private val note = NoteEntity(id = "n", folderId = "f", blocks = emptyList(), title = "", searchText = "", createdAt = 0, updatedAt = 0)

    @Test fun readsTheStoredFont() = assertEquals(NoteFont.SERIF, note.copy(font = "SERIF").fontStyle)

    @Test fun noneOrUnknownIsTheDefault() {
        assertEquals(NoteFont.DEFAULT, note.fontStyle)
        assertEquals(NoteFont.DEFAULT, note.copy(font = "COMIC").fontStyle) // a font added by a newer version
    }

    @Test fun syncsWithTheNote() =
        assertEquals(NoteFont.MONO, JotterJson.decodeFromString<NoteEntity>(JotterJson.encodeToString(note.copy(font = "MONO"))).fontStyle)
}
