package com.shuva.jotter.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SpellCheckTest {
    @Test
    fun `tags and note links are not flagged`() {
        val text = "teh #recipie see [[Shoping list]] and wrod\n[[ x\nzzq"
        fun at(word: String) = Triple(text.indexOf(word), word.length, listOf("fix"))
        val found = misspellingsIn(text, listOf(at("teh"), at("recipie"), at("Shoping"), at("wrod"), at("zzq"), Triple(200, 3, emptyList())))
        // "zzq" is on a new line after an unclosed "[[", so it still counts.
        assertEquals(listOf("teh", "wrod", "zzq"), found.map { it.word })
        assertEquals(text.indexOf("wrod") + 4, found[1].end)
    }

    @Test
    fun `a flagged word is kept only while it is unchanged`() {
        val m = listOf(Misspelling(0, 3, "teh", listOf("the")), Misspelling(4, 8, "wrod", listOf("word")))
        assertEquals(m, m.stillIn("teh wrod"))
        assertEquals(listOf(m[0]), m.stillIn("teh word")) // fixed
        assertEquals(emptyList<Misspelling>(), m.stillIn("A teh wrod")) // moved: wait for the next check
    }
}
