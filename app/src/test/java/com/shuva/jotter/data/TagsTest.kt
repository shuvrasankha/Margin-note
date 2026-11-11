package com.shuva.jotter.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TagsTest {
    private fun names(text: String) = findTags(text).map { it.second }

    private fun note(id: String, created: Long, vararg lines: String) = NoteEntity(
        id = id, folderId = "f", blocks = lines.map { Block(text = it) }, title = "", searchText = "",
        createdAt = created, updatedAt = created,
    )

    @Test
    fun `finds tags anywhere in a line`() {
        assertEquals(listOf("work", "Plan_B", "to-do"), names("#work then #Plan_B, and (#to-do)."))
    }

    @Test
    fun `ignores numbers, words with a hash inside and lone hashes`() {
        assertEquals(emptyList<String>(), names("item #1, a#b, C# and # alone, ##x"))
    }

    @Test
    fun `range covers the hash and the name, not a trailing dash`() {
        val (range, name) = findTags("see #trip- now").single()
        assertEquals("trip", name)
        assertEquals(4..8, range)
    }

    @Test
    fun `counts are per note, case-insensitive, first spelling wins`() {
        val notes = listOf(
            note("b", 2, "Title", "#work #WORK"),
            note("a", 1, "#Work", "#home"),
        )
        assertEquals(
            listOf(TagCount("home", "home", 1), TagCount("work", "Work", 2)),
            tagCounts(notes),
        )
        assertEquals(setOf("work"), notes[0].tagKeys)
    }

    @Test
    fun `finds the tag being typed at the cursor`() {
        assertEquals(6 to "tr", tagPrefixAt("Pack #tr", 8))
        assertEquals(6 to "", tagPrefixAt("Pack #", 6))
        assertEquals(null, tagPrefixAt("Pack #trip now", 14)) // cursor after a space
        assertEquals(null, tagPrefixAt("a#b", 3)) // glued to a word
    }

    @Test
    fun `renaming rewrites every spelling of the tag and nothing else`() {
        assertEquals("#trips and #trips, #travels", replaceTag(Block(text = "#Travel and #travel, #travels"), "travel", "trips").text)
        assertEquals(true, isValidTagName("to-do"))
        assertEquals(false, isValidTagName("12"))
        assertEquals(false, isValidTagName("two words"))
    }
}
