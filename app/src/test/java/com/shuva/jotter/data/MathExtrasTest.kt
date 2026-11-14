package com.shuva.jotter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MathExtrasTest {
    private fun answer(text: String) = (mathSuggestionAt(text, text.length) as? MathSuggestion.Answer)?.text

    @Test fun totalAddsTheListAbove() {
        assertEquals("1855.5", answer("Budget 2026\nRent 1,200\nFood 300\nCoffee \$4.50\nBus pass 351\nTotal ="))
        assertEquals("15", answer("Budget\nRent 1,200\n\nX 12\nY 3\nGrand total ="))
    }

    @Test fun totalStopsAtABlankLineOrAnEarlierTotal() {
        assertEquals("15", answer("Note\nOld 100\n\nA 10\nB 5\nTotal ="))
        assertEquals("7", answer("Note\nA 1\nB 2\nSubtotal = 3\nC 3\nD 4\nTotal ="))
    }

    @Test fun totalUsesSumAnswersAndAverages() {
        assertEquals("60", answer("Note\n12 × 4 = 48\nTips 12\nSum ="))
        assertEquals("20", answer("Note\nMon 10\nTue 20\nWed 30\nAverage ="))
        assertNull(answer("Note\nNo numbers here\nTotal ="))
    }

    @Test fun conversions() {
        assertEquals("3.10686 miles", answer("Note\n5 km in miles ="))
        assertEquals("154.324 lb", answer("Note\n70kg to lb ="))
        assertEquals("37.7778 °C", answer("Note\n100 °F in °C ="))
        assertEquals("212 f", answer("Note\n100 c to f ="))
        assertEquals("90 minutes", answer("Note\n1.5 hours in minutes ="))
        assertEquals("2 GB", answer("Note\n2000 MB to GB ="))
        assertEquals("16.0934 km", answer("Note\n2 × 5 miles in km ="))
    }

    @Test fun mismatchedOrUnknownUnitsAreNotConversions() {
        assertNull(convert("5 km in kg"))
        assertNull(convert("5 apples in pears"))
        assertEquals("15", answer("Note\n10 + 5 ="))
    }

    @Test fun physicsConstantsFillInOnlyWhatIsLeftUndefined() {
        assertEquals("599584916", answer("Note\nc × 2 ="))
        assertEquals("19.6133", answer("Note\nm = 2\nF = m g ="))
        assertEquals("20", answer("Note\ng = 10\n2 g =")) // a value in the note wins
        assertEquals("1.60218 × 10^-19 J", answer("Note\n1 eV in J ="))
        assertEquals("202.65 kPa", answer("Note\n2 atm in kPa ="))
        // Single letters still solve as unknowns.
        assertEquals(listOf("5"), (mathSuggestionAt("Note\n2h = 10", 12) as MathSuggestion.Solution).values)
    }
}
