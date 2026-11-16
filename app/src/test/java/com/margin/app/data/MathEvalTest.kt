package com.margin.app.data

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

    @Test fun graphsOfEquationsInXAndY() {
        fun plot(line: String) = (suggest(line) as MathSuggestion.Graph).plot
        assertEquals(9.0, plot("Y = x^2").f(3.0), 1e-9) // phone keyboards capitalise the line's first letter
        assertEquals(2.0, plot("2x + y = 4").f(1.0), 1e-9)
        assertEquals(3.0, plot("y - 2x = 1").f(1.0), 1e-9)
        assertEquals(3.0, plot("x y = 6").f(2.0), 1e-9)
        assertNull(plot("2x + y = 4").other)
        val circle = plot("x^2 + y^2 = 25")
        assertEquals(4.0, circle.f(3.0), 1e-9)
        assertEquals(-4.0, circle.other!!(3.0), 1e-9)
        assertTrue(circle.f(6.0).isNaN()) // outside the circle
        assertEquals(setOf(2.0, -2.0), plot("x = y^2").let { setOf(it.f(4.0), it.other!!(4.0)) })
        // A function under another equation is still a graph, not a pair to solve.
        assertTrue(suggest("y = x^2\ny = 2x + 1") is MathSuggestion.Graph)
        assertTrue(suggest("x y = 6\nY = 8e^(-x^2)sin(5x)") is MathSuggestion.Graph)
        assertNull(suggest("x + sin(y) = 1")) // not drawn, and not "solved" from the part after x
        assertTrue(suggest("2x + y = 4\nx - y = 2") !is MathSuggestion.Graph) // a pair is still solved
    }

    @Test fun graphsOfFunctionPowersAndLogBases() {
        fun y(line: String, x: Double) = (suggest(line) as MathSuggestion.Graph).plot.f(x)
        val s = Math.sin(1.0)
        assertEquals(s * s, y("y = sin²(x)", 1.0), 1e-9)
        assertEquals(s * s, y("y = sin^2(x)", 1.0), 1e-9)
        assertEquals(s * s * 3, y("y = 3sin^2 x", 1.0), 1e-9)
        assertEquals(Math.PI / 2, y("y = sin⁻¹(x)", 1.0), 1e-9)
        assertEquals(Math.PI / 4, y("y = tan^-1(x)", 1.0), 1e-9)
        assertEquals(2.0, y("y = log10(x)", 100.0), 1e-9)
        assertEquals(3.0, y("y = log₂(x)", 8.0), 1e-9)
        assertEquals(3.0, y("y = log2(x)", 8.0), 1e-9)
        assertEquals(Math.sin(4.0), y("y = sin(x^2)", 2.0), 1e-9) // a power inside the brackets is untouched
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

    @Test fun aTableGraphsItsNumberColumnsAgainstTheFirst() {
        val g = tableGraph(NoteTable(listOf(listOf("t", "height", "note"), listOf("1", "2", "ok"), listOf("3", "6", ""), listOf("2", "4", "x"))))!!
        assertEquals(listOf("height"), g.plots.map { it.label })
        assertEquals(5.0, g.plots[0].f(2.5), 1e-9)
        assertTrue(g.plots[0].f(0.0).isNaN()) // nothing drawn outside the points
        assertEquals(2.0, g.centreX, 1e-9)
        assertNull(tableGraph(NoteTable(listOf(listOf("a", "b"), listOf("1", "x")))))
    }
}
