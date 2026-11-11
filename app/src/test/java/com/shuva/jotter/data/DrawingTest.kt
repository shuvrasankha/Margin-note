package com.shuva.jotter.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawingTest {
    @Test fun fitsInsideKeepingShape() {
        val d = Drawing(1000f, 500f)
        assertEquals(0.5f, d.fitScale(500f, 400f)) // width decides
        assertEquals(0.4f, d.fitScale(900f, 200f)) // height decides
        assertEquals(1f, Drawing(0f, 0f).fitScale(10f, 10f)) // never divides by zero
    }

    @Test fun survivesJson() {
        val d = Drawing(10f, 20f, listOf(PenStroke(0xFF111111, 3f, listOf(1f, 2f, 3f, 4f)), PenStroke(0, 9f, listOf(5f, 5f), erase = true)))
        assertEquals(d, JotterJson.decodeFromString<Drawing>(JotterJson.encodeToString(d)))
    }

    @Test fun cropsToInkIgnoringEraser() {
        val d = Drawing(1000f, 2000f, listOf(PenStroke(0, 2f, listOf(100f, 200f, 300f, 250f)), PenStroke(0, 50f, listOf(0f, 0f), erase = true)))
        val c = d.cropped(pad = 9f)
        assertEquals(220f, c.width) // 200 of ink + 2 x (1 half-width + 9 pad)
        assertEquals(70f, c.height)
        assertEquals(listOf(10f, 10f, 210f, 60f), c.strokes[0].points)
        assertEquals(Drawing(5f, 5f), Drawing(5f, 5f).cropped(9f)) // blank stays as is
    }

    @Test fun defaultToolAndGridLeaveJsonAsBefore() {
        val old = Drawing(10f, 20f, listOf(PenStroke(1, 2f, listOf(1f, 2f))))
        val json = JotterJson.encodeToString(old)
        assertFalse("tool" in json || "grid" in json) // older versions read it unchanged
        val d = Drawing(10f, 20f, listOf(PenStroke(1, 2f, listOf(1f, 2f), tool = PenTool.MARKER)), grid = true)
        assertEquals(d, JotterJson.decodeFromString<Drawing>(JotterJson.encodeToString(d)))
        assertEquals(PenTool.PEN, JotterJson.decodeFromString<Drawing>(json).strokes[0].tool)
    }

    @Test fun lassoPicksStrokesMostlyInside() {
        val square = listOf(0f, 0f, 100f, 0f, 100f, 100f, 0f, 100f)
        assertTrue(pointInPolygon(50f, 50f, square))
        assertFalse(pointInPolygon(150f, 50f, square))
        val d = Drawing(200f, 200f, listOf(
            PenStroke(0, 1f, listOf(10f, 10f, 20f, 20f, 30f, 30f)), // inside
            PenStroke(0, 1f, listOf(90f, 50f, 150f, 50f, 160f, 50f)), // mostly outside
            PenStroke(0, 1f, listOf(150f, 150f)), // outside
        ))
        assertEquals(setOf(0), d.strokesInside(square))
    }

    @Test fun movesAndBoundsOnlyTheSelection() {
        val s = listOf(PenStroke(0, 1f, listOf(1f, 2f, 5f, 8f)), PenStroke(0, 1f, listOf(0f, 0f)))
        val m = s.moved(setOf(0), 10f, -1f)
        assertEquals(listOf(11f, 1f, 15f, 7f), m[0].points)
        assertEquals(s[1], m[1])
        assertArrayEquals(floatArrayOf(1f, 2f, 5f, 8f), s.bounds(setOf(0)), 0f)
        assertNull(s.bounds(emptySet()))
    }

    @Test fun rulerSnapsNearFortyFiveDegrees() {
        assertEquals(listOf(0f, 0f, 10f, 0f), straightLine(0f, 0f, 10f, 0.3f).map { Math.round(it * 100) / 100f }) // ~1.7° snaps flat
        assertEquals(listOf(0f, 0f, 10f, 5f), straightLine(0f, 0f, 10f, 5f)) // ~26° stays as drawn
        val diag = straightLine(0f, 0f, 10f, 9.5f)
        assertEquals(diag[2], diag[3], 0.001f) // ~43.5° snaps to 45°
    }
}

class InkRowsTest {
    @org.junit.Test fun keepsWidthAndCropsRows() {
        val d = Drawing(400f, 800f, listOf(PenStroke(0xFF111111, 4f, listOf(10f, 300f, 390f, 340f))))
        val r = d.inkRows(10f)
        org.junit.Assert.assertEquals(400f, r.width)
        org.junit.Assert.assertEquals(64f, r.height) // 300-12 .. 340+12
        org.junit.Assert.assertEquals(listOf(10f, 12f, 390f, 52f), r.strokes[0].points)
        org.junit.Assert.assertEquals(20f, Drawing(400f, 800f).inkRows(10f).height)
    }

    @Test fun growsToHoldInkDrawnPastThePage() {
        val d = Drawing(100f, 100f, listOf(PenStroke(0, 2f, listOf(-20f, 50f, 150f, 50f)), PenStroke(0, 2f, listOf(50f, -30f))))
        val g = d.grownToFit(9f) // half width 1 + pad 9 = 10 around each point
        assertEquals(190f, g.width) // -30 .. 160
        assertEquals(140f, g.height) // -40 .. 100
        assertEquals(listOf(10f, 90f, 180f, 90f), g.strokes[0].points)
        assertEquals(listOf(80f, 10f), g.strokes[1].points)
        val inside = Drawing(100f, 100f, listOf(PenStroke(0, 2f, listOf(50f, 50f))))
        assertTrue(inside.grownToFit(9f) === inside)
    }
}
