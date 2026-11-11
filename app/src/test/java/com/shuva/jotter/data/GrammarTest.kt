package com.shuva.jotter.data

import org.junit.Assert.assertEquals
import org.junit.Test

class GrammarTest {
    /** Each issue as "original→fix". */
    private fun fixes(text: String) = grammarRules(text).map { "${it.original}→${it.replacements.first()}" }

    @Test fun commonMistakes() {
        assertEquals(listOf("the the→the"), fixes("I saw the the cat"))
        assertEquals(listOf("a apple→an apple", "an car→a car"), fixes("We ate a apple in an car"))
        assertEquals(emptyList<String>(), fixes("a user, an hour, a university, an FBI agent, an honest man"))
        assertEquals(listOf("could of→could have"), fixes("We could of gone"))
        assertEquals(listOf("its not→it's not", "it's own→its own"), fixes("Well its not on it's own"))
        assertEquals(listOf("Your welcome→You're welcome"), fixes("Your welcome"))
        assertEquals(listOf("better then→better than"), fixes("This is better then that"))
        assertEquals(listOf("Their is→There is"), fixes("Their is a cat"))
        assertEquals(listOf("he don't→he doesn't", "they was→they were"), fixes("he don't know, they was late"))
        assertEquals(listOf("dont→don't", "im→I'm"), fixes("I dont think im ready"))
        assertEquals(listOf("alot→a lot"), fixes("Thanks alot"))
    }

    @Test fun capitalsAndSpacing() {
        assertEquals(listOf("i→I", "i→I"), fixes("so i said i'm here")) // the i of i'm
        assertEquals(listOf("then→Then"), fixes("It rained. then it stopped."))
        assertEquals(listOf(" ,→,"), fixes("A word , then more"))
    }

    @Test fun leavesGoodWritingAlone() {
        assertEquals(emptyList<String>(), fixes("I think that that is fine. He had had enough."))
        assertEquals(emptyList<String>(), fixes("Use e.g. this or i.e. that, etc. and more"))
        assertEquals(emptyList<String>(), fixes("Take vitamin A and C"))
        assertEquals(emptyList<String>(), fixes("the IM team"))
        assertEquals(emptyList<String>(), fixes("its color is red. It's raining"))
        assertEquals(emptyList<String>(), fixes("x = a apple")) // math lines are skipped
        assertEquals(emptyList<String>(), fixes("see [[the the]] and #dont"))
        assertEquals(emptyList<String>(), fixes("first line\nsecond line"))
    }

    @Test fun offsetsAcrossLines() {
        val g = grammarRules("Title\nI saw a apple").single()
        assertEquals("a apple", "Title\nI saw a apple".substring(g.start, g.end))
    }

    @Test fun parsesLanguageToolSkippingSpelling() {
        val text = "He go home. Teh end"
        val json = """{"software":{},"matches":[
            {"message":"Use 'goes'.","offset":3,"length":2,"replacements":[{"value":"goes"},{"value":"went"}],"rule":{"id":"X","issueType":"grammar"}},
            {"message":"Typo","offset":12,"length":3,"replacements":[{"value":"The"}],"rule":{"id":"Y","issueType":"misspelling"}}]}"""
        val issues = parseLanguageTool(json, text)
        assertEquals(1, issues.size)
        assertEquals(GrammarIssue(3, 5, "go", "Use 'goes'.", listOf("goes", "went")), issues[0])
    }

    @Test fun mergeKeepsFirstOnOverlap() {
        val a = GrammarIssue(0, 5, "x", "a", emptyList())
        val b = GrammarIssue(3, 8, "y", "b", emptyList())
        val c = GrammarIssue(10, 12, "z", "c", emptyList())
        assertEquals(listOf(a, c), mergeIssues(listOf(a), listOf(b, c)))
    }
}
