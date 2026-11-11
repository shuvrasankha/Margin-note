package com.shuva.jotter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MathEvalTest {
    private fun answer(line: String) = mathAnswerAt(line, line.length)
    private fun suggest(text: String) = mathSuggestionAt(text, text.length)
    private fun solution(text: String) = (suggest(text) as? MathSuggestion.Solution)?.text

    @Test fun basicSums() {
        assertEquals("51", answer("12 × 4 + 3 ="))
        assertEquals("51", answer("12*4+3="))
        assertEquals("15", answer("5x3 ="))
        assertEquals("2.5", answer("10 ÷ 4 ="))
        assertEquals("0.3", answer("0.1 + 0.2 ="))
        assertEquals("14", answer("2(3 + 4) ="))
        assertEquals("-4", answer("-2^2 ="))
        assertEquals("512", answer("2^3^2 ="))
        assertEquals("3", answer("√9 ="))
        assertEquals("2469134", answer("1,234,567 × 2 ="))
        assertEquals("0", answer("3 − 3 ="))
        assertEquals("25", answer("5² ="))
    }

    @Test fun functionsAndConstants() {
        assertEquals("0.5", answer("sin(30°) ="))
        assertEquals("1", answer("sin 90° ="))
        assertEquals("-1", answer("cos(π) ="))
        assertEquals("3", answer("log(1000) ="))
        assertEquals("1", answer("ln(e) ="))
        assertEquals("120", answer("5! ="))
        assertEquals("7", answer("|3 - 10| ="))
        assertEquals("6.28318530718", answer("2pi ="))
        assertEquals("10", answer("log2(1024) ="))
        assertEquals("4", answer("sqrt(16) ="))
        assertEquals("3.5", answer("(2 + 5) / 2 ="))
    }

    @Test fun percentages() {
        assertEquals("30", answer("200 × 15% ="))
        assertEquals("220", answer("200 + 10% ="))
        assertEquals("180", answer("200 - 10% ="))
    }

    @Test fun wordsInFrontAreIgnored() {
        assertEquals("20", answer("Lunch 12 + 8 ="))
        assertEquals("15", answer("box 5x3 ="))
        assertEquals("-3", answer("Total: -5 + 2 ="))
    }

    @Test fun variablesAndFunctionsFromEarlierLines() {
        assertEquals("1500", answer("rent = 1200\nbills = 300\nrent + bills ="))
        assertEquals("8", answer("a = 5 + 3 = 8\na ="))
        assertEquals("17", answer("f(x) = x^2 + 1\nf(4) ="))
        assertEquals("40", answer("w = 4\nh = 10\nw h =").let { it ?: answer("w = 4\nh = 10\nw×h =") })
    }

    @Test fun solvesEquations() {
        assertEquals("x = 4", solution("2x + 3 = 11"))
        assertEquals("x = -2 or 2", solution("x^2 = 4"))
        assertEquals("x = -3 or 2", solution("x² + x - 6 = 0"))
        assertEquals("t = 100", solution("Solve 3t - 50 = 250"))
        assertEquals("x = 1", solution("2^x = 2"))
        assertEquals("n = 2", solution("price = 10\nprice × n = 20"))
        assertNull(solution("x^2 = -1"))   // no real answer
        assertNull(suggest("rent = 1200")) // a definition, not an equation
        assertNull(suggest("2 + 2 = 4"))   // nothing to solve
        assertNull(suggest("2x + 3 = 11 → x = 4"))
    }

    @Test fun graphs() {
        val g = suggest("y = x^2") as MathSuggestion.Graph
        assertEquals(9.0, g.plot.f(3.0), 1e-9)
        val f = suggest("f(x) = sin(x)") as MathSuggestion.Graph
        assertEquals(0.0, f.plot.f(0.0), 1e-9)
        assertTrue(suggest("a = 2\ny = a x + 1") is MathSuggestion.Graph)
        assertTrue(suggest("x = 3\ny = 2x") is MathSuggestion.Graph)
        assertEquals(listOf("y = x^2", "g(t) = t + 1"), plotsIn("Notes\ny = x^2\n5 + 5 =\ng(t) = t + 1").map { it.label })
    }

    @Test fun onlyTheCursorsLineCounts() {
        val text = "1 + 1 =\n2 × 3 =\nnext"
        assertEquals("2", mathAnswerAt(text, 7))
        assertEquals("6", mathAnswerAt(text, 15))
        assertEquals("2", mathAnswerAt("1 + 1 =  ", 9)) // trailing spaces are fine
    }

    @Test fun noAnswer() {
        assertNull(answer("12 + 3"))         // no "="
        assertNull(answer("5 ="))            // just a number
        assertNull(answer("a = b ="))
        assertNull(answer("1 / 0 ="))
        assertNull(answer("(1 + 2 ="))
        assertNull(answer("1 + 1 =="))
        assertNull(answer("1,23 + 1 ="))
        assertNull(mathAnswerAt("1 + 1 = 2", 7)) // already answered
        assertNull(mathAnswerAt("1 + 1 =", 3))   // cursor mid-line
    }

    @Test fun valuesNamedInSentences() {
        assertEquals("144", suggestAnswer("Let a=10 and b =2\n(a+b)^2 ="))
        assertEquals("25", suggestAnswer("a=2, b=3\n(a+b)^2 ="))
        assertEquals("78.5398163397", suggestAnswer("where r = 5 cm\npi r^2 ="))
        assertEquals("2400", suggestAnswer("rent = 1,200 and months = 2\nrent * months ="))
        assertEquals("6", suggestAnswer("Let a = 2; b = a + 1\na b ="))
        assertEquals("11", suggestAnswer("Let x = 5. Then y = 6.\nx + y ="))
        // A name being given a value isn't an equation to solve.
        assertNull(suggest("Let a=10"))
        assertNull(suggest("Let a=10 and b =2"))
        // Still solved: an unknown on its own side with a number.
        assertEquals("x = 5", solution("2x = 10"))
    }

    private fun suggestAnswer(text: String) = (suggest(text) as? MathSuggestion.Answer)?.text
}
