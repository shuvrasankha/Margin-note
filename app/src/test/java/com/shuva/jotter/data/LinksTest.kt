package com.shuva.jotter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinksTest {
    private fun note(id: String, title: String, edited: Long, trashed: Boolean = false) = NoteEntity(
        id = id, folderId = "f", blocks = listOf(Block(type = BlockType.TITLE, text = title)), title = title,
        searchText = "", createdAt = 0, updatedAt = edited, editedAt = edited, trashedAt = if (trashed) 1 else null,
    )

    @Test fun findsLinksAndSkipsBlankOnes() {
        val text = "See [[Plan]] and [[ Trip notes ]], not [[ ]] or [[a\nb]]"
        assertEquals(listOf("Plan", "Trip notes"), findLinks(text).map { it.second })
        assertEquals(4..11, findLinks(text).first().first)
    }

    @Test fun prefixWhileTyping() {
        assertEquals(6 to "Pl", linkPrefixAt("see [[Pl", 8))
        assertEquals(2 to "", linkPrefixAt("[[", 2))
        assertNull(linkPrefixAt("see [[Plan]] x", 14)) // closed
        assertNull(linkPrefixAt("[[a\nb", 5)) // other line
        assertNull(linkPrefixAt("no link", 7))
    }

    @Test fun resolvesByTitleIgnoringCaseNewestWinsTrashIgnored() {
        val notes = listOf(note("a", "Plan", 1), note("b", "plan", 5), note("c", "Plan", 9, trashed = true))
        assertEquals("b", resolveLink(" PLAN ", notes)?.id)
        assertNull(resolveLink("Other", notes))
    }

    @Test fun replacesOnlyMatchingLinks() {
        assertEquals("[[New]] [[Other]] [[New]]", replaceLinks(Block(text = "[[old]] [[Other]] [[Old ]]"), "Old", "New").text)
    }

    @Test fun backlinksFindLiveNotesLinkingToTheTitle() {
        fun linking(id: String, text: String, edited: Long, trashed: Boolean = false) =
            note(id, id, edited, trashed).let { it.copy(blocks = it.blocks + Block(text = text)) }
        val notes = listOf(linking("a", "see [[plan]]", 1), linking("b", "[[ Plan ]] again", 5), linking("c", "[[Planning]]", 9),
            linking("d", "[[Plan]]", 9, trashed = true), linking("self", "[[Plan]]", 9))
        assertEquals(listOf("b", "a"), backlinks("Plan", "self", notes).map { it.id })
        assertEquals(emptyList<NoteEntity>(), backlinks(" ", "self", notes))
    }
}
