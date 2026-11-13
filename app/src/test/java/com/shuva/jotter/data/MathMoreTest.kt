package com.shuva.jotter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MathMoreTest {
    private fun suggest(text: String) = mathSuggestionAt(text, text.length)
    private fun answer(text: String) = (suggest(text) as? MathSuggestion.Answer)?.text
    private fun solution(text: String) = (suggest(text) as? MathSuggestion.Solution)?.text
    private fun solved(text: String) = (suggest(text) as? MathSuggestion.Solved)?.text

    @Test fun systemsOfEquations() {
        assertEquals("x = 6, y = 4", solved("x + y = 10, x - y = 2"))
        assertEquals("x = 6, y = 4", solved("Note\nx + y = 10\nx - y = 2"))
        assertEquals("x = 3, y = 2", solved("2x + 3y = 12 and x - y = 1"))
        assertEquals("x = 1, y = 2, z = 3", solved("x + y + z = 6\n2x - y + z = 3\nx + 2y - z = 2"))
        assertEquals("(x, y) = (3, 4) or (4, 3)", solved("x^2 + y^2 = 25\nx + y = 7"))
        assertEquals("x = 1/3, y = 2/3", solved("x + y = 1, 2x - y = 0"))
        // "x + y = 10" on its own line names nothing: y isn't 10.
        assertNull(suggest("x + y = 10"))
        assertNull(suggest("Let a=10 and b =2"))
    }

    @Test fun inequalities() {
        assertEquals("x > 2", solved("2x + 3 > 7"))
        assertEquals("x ≤ -3", solved("x + 5 <= 2"))
        assertEquals("-2 < x < 2", solved("x^2 < 4"))
        assertEquals("x ≤ -1 or x ≥ 1", solved("x² ≥ 1"))
        assertEquals("x > 0", solved("1/x > 0"))
        assertEquals("true for every x", solved("x^2 + 1 > 0"))
        assertEquals("no solution", solved("x^2 < -1"))
        assertEquals("x = 0", solved("x^2 ≤ 0"))
        assertEquals("t ≥ 1/3", solved("3t ≥ 1"))
    }

    @Test fun equationsWithoutRealRoots() {
        assertEquals("x = ±i", solved("x^2 = -1"))
        assertEquals("x = -1 ± 2i", solved("x^2 + 2x + 5 = 0"))
        assertEquals("true for every x", solved("2x + 2 = 2(x + 1)"))
        assertEquals("no real solution", solved("x + 1 = x"))
    }

    @Test fun nicerRoots() {
        assertEquals("x = 1/3", solution("3x = 1"))
        assertEquals("x = -√2 or √2", solution("x^2 = 2"))
        assertEquals("x = 1/3", solution("(x - 1/3)^2 = 0")) // touches zero without crossing
        assertEquals("x = 0", solution("x^2 = 0"))
        assertEquals("x = 3", solution("x^3 = 27"))
    }

    @Test fun fractionsAndBigNumbers() {
        assertEquals("7/12 ≈ 0.583333333333", answer("1/3 + 1/4 ="))
        assertEquals("0.75", answer("1/2 + 1/4 ="))
        assertEquals("1099511627776", answer("2^40 ="))
        assertEquals("1.26765060023 × 10^30", answer("2^100 ="))
        assertEquals("3000", answer("1.5e3 × 2 ="))
        assertEquals("1.0002", answer("2E-4 + 1 ="))
    }

    @Test fun moreFunctions() {
        assertEquals("30", answer("15% of 200 ="))
        assertEquals("2", answer("17 mod 5 ="))
        assertEquals("10", answer("nCr(5, 2) ="))
        assertEquals("20", answer("nPr(5, 2) ="))
        assertEquals("6", answer("gcd(12, 18) ="))
        assertEquals("36", answer("lcm(12, 18) ="))
        assertEquals("9", answer("max(3, 9, 2) ="))
        assertEquals("3", answer("log(8, 2) ="))
        assertEquals("-2", answer("root(-8, 3) ="))
        assertEquals("3.14", answer("round(3.14159, 2) ="))
        assertEquals("2", answer("sec(60°) ="))
        assertEquals("81", answer("3⁴ ="))
        assertEquals("31.4159265359", answer("r = 5\n2πr ="))
        assertEquals("12", answer("x = 3\ny = 4\nxy ="))
    }

    @Test fun derivatives() {
        assertEquals("3x^2", answer("d/dx x^3 ="))
        assertEquals("6x + 2", answer("d/dx 3x^2 + 2x - 7 ="))
        assertEquals("cos(x)", answer("d/dx sin(x) ="))
        assertEquals("2cos(2x)", answer("d/dx sin(2x) ="))
        assertEquals("1/x", answer("derivative of ln(x) ="))
        assertEquals("12", answer("d/dx x^3 at 2 ="))
        assertEquals("6x", answer("d²/dx² x^3 ="))
        assertEquals("e^x", answer("d/dx e^x ="))
        assertEquals("8", answer("f(x) = x^2 + 1\nf'(4) ="))
        assertEquals("2x", answer("f(x) = x^2 + 1\nf'(x) ="))
        assertEquals("2", answer("f(x) = x^2 + 1\nf''(3) ="))
    }

    @Test fun integralsAndSums() {
        assertEquals("9", answer("∫ x^2 dx from 0 to 3 ="))
        assertEquals("2", answer("integral of sin(x) from 0 to π ="))
        assertEquals("1/3 ≈ 0.3333333333", answer("∫ t^2 dt from 0 to 1 ="))
        assertEquals("385", answer("Σ k^2 for k from 1 to 10 ="))
        assertEquals("5050", answer("sum of n for n from 1 to 100 ="))
    }

    @Test fun oldThingsStillWork() {
        assertEquals("x = 4", solution("2x + 3 = 11"))
        assertEquals("51", answer("12 × 4 + 3 ="))
        assertEquals("15", answer("5x3 ="))
        assertEquals("2.71828182846", answer("e ="))
    }
}
