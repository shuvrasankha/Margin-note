package com.margin.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawingWeightTest {
    @Test fun weightsAreLeftOutOfTheJsonWhenThereAreNone() {
        val plain = PenStroke(0xFF111111, 3f, listOf(1f, 2f, 3f, 4f))
        assertFalse(MarginJson.encodeToString(PenStroke.serializer(), plain).contains("weights"))
        val weighted = plain.copy(weights = listOf(1f, 0.55f))
        assertEquals(weighted, MarginJson.decodeFromString(PenStroke.serializer(), MarginJson.encodeToString(PenStroke.serializer(), weighted)))
    }

    @Test fun readsDrawingsSavedBeforeWeights() {
        val old = """{"width":10,"height":20,"strokes":[{"color":4279308561,"width":3,"points":[1,2,3,4],"erase":false}]}"""
        assertNull(MarginJson.decodeFromString(Drawing.serializer(), old).strokes[0].weights)
        // And a newer drawing's unknown fields don't stop it opening.
        val newer = """{"width":10,"height":20,"strokes":[{"color":1,"width":3,"points":[1,2],"weights":[0.5],"future":true}]}"""
        assertEquals(listOf(0.5f), MarginJson.decodeFromString(Drawing.serializer(), newer).strokes[0].weights)
    }

    @Test fun weightsStayBetweenTheFloorAndFullWidth() {
        assertEquals(1f, weightFromSpeed(0f, null))
        assertEquals(MIN_WEIGHT, weightFromSpeed(50f, null))
        assertEquals(MIN_WEIGHT, weightFromPressure(0f))
        assertEquals(1f, weightFromPressure(1f))
        // Eased: one fast point after a slow one only thins a little.
        val next = weightFromSpeed(50f, 1f)
        assertTrue(next < 1f && next > 0.75f)
        assertEquals(next, Math.round(next * 100) / 100f) // two decimals
    }

    @Test fun weightsOnlyCountWhenTheyMatchThePoints() {
        val s = PenStroke(0, 4f, listOf(0f, 0f, 1f, 1f, 2f, 2f), weights = listOf(1f, 0.5f, 0.6f))
        assertEquals(listOf(1f, 0.5f, 0.6f), s.usableWeights())
        assertNull(s.copy(weights = listOf(1f, 0.5f)).usableWeights()) // out of step
        assertNull(s.copy(tool = PenTool.MARKER).usableWeights())
        assertNull(s.copy(erase = true).usableWeights())
    }

    @Test fun wholeStrokeEraserHitsWhatItCrosses() {
        val strokes = listOf(
            PenStroke(0, 2f, listOf(0f, 50f, 100f, 50f)), // a ruler line: two points
            PenStroke(0, 2f, listOf(0f, 200f, 10f, 200f, 20f, 200f)),
            PenStroke(0, 40f, listOf(50f, 50f), erase = true), // erase strokes are never picked
            PenStroke(0, 4f, listOf(300f, 300f)), // a dot
        )
        // A fast swipe straight across the ruler line, with no touch point near it.
        assertEquals(setOf(0), strokes.hitBy(50f, 0f, 50f, 100f, radius = 5f))
        assertEquals(setOf(1), strokes.hitBy(15f, 190f, 15f, 192f, radius = 7.5f)) // 8 away: reached with the stroke's half width (1)
        assertEquals(emptySet<Int>(), strokes.hitBy(15f, 150f, 15f, 160f, radius = 5f))
        assertEquals(setOf(3), strokes.hitBy(305f, 300f, 305f, 300f, radius = 4f))
    }
}
