package com.margin.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoEditTest {
    private fun near(a: CropRect, b: CropRect) {
        listOf(a.l to b.l, a.t to b.t, a.r to b.r, a.b to b.b).forEach { (x, y) -> assertEquals(y, x, 1e-5f) }
    }

    @Test fun fourTurnsAndTwoFlipsGetBack() {
        val e = PhotoEdit(crop = CropRect(0.1f, 0.2f, 0.6f, 0.9f), straighten = 5f)
        listOf(e.turnedRight().turnedRight().turnedRight().turnedRight(), e.flippedH().flippedH(), e.turnedLeft().turnedRight()).forEach {
            near(it.crop, e.crop)
            assertEquals(e, it.copy(crop = e.crop))
        }
    }

    @Test fun cropFollowsTheTurn() {
        // The top-left corner of the photo ends up top-right after a clockwise turn.
        near(CropRect(0f, 0f, 0.5f, 0.25f).turned(), CropRect(0.75f, 0f, 1f, 0.5f))
        near(CropRect(0.1f, 0.2f, 0.4f, 0.6f).flippedH(), CropRect(0.6f, 0.2f, 0.9f, 0.6f))
        near(CropRect(0.1f, 0.2f, 0.4f, 0.6f).flippedV(), CropRect(0.1f, 0.4f, 0.4f, 0.8f))
    }

    @Test fun turningAFlipSwapsIt() {
        // Flipping left-to-right then turning is the same picture as turning then flipping upside down.
        val e = PhotoEdit().flippedH().turnedRight()
        assertEquals(1, e.turns); assertFalse(e.flipH); assertTrue(e.flipV)
    }

    @Test fun identity() {
        assertTrue(PhotoEdit().isIdentity)
        assertTrue(PhotoEdit(crop = CropRect(0.0001f, 0f, 1f, 0.9999f)).isIdentity)
        assertFalse(PhotoEdit(scale = 0.5f).isIdentity)
        assertFalse(PhotoEdit(crop = CropRect(0.1f, 0f, 1f, 1f)).isIdentity)
        assertFalse(PhotoEdit().turnedRight().isIdentity)
    }

    @Test fun straightenCoversCorners() {
        assertEquals(1f, straightenScale(400, 300, 0f), 0f)
        // A square tilted 45° must grow by √2.
        assertEquals(1.41421f, straightenScale(100, 100, 45f), 1e-4f)
        assertEquals(straightenScale(400, 300, 10f), straightenScale(400, 300, -10f), 0f)
        assertTrue(straightenScale(400, 300, 10f) > 1f)
    }

    @Test fun savedSize() {
        assertEquals(4000 to 3000, editedSize(4000, 3000, PhotoEdit()))
        assertEquals(3000 to 4000, editedSize(4000, 3000, PhotoEdit(turns = 1)))
        assertEquals(1000 to 750, editedSize(4000, 3000, PhotoEdit(crop = CropRect(0f, 0f, 0.5f, 0.5f), scale = 0.5f)))
    }

    @Test fun colorMatrixIsIdentityAtZero() {
        val m = photoColorMatrix(0f, 0f, 0f)
        val id = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
        id.forEachIndexed { i, v -> assertEquals("at $i", v, m[i], 1e-5f) }
        // Grey: every row takes the same mix.
        val g = photoColorMatrix(0f, 0f, -1f)
        assertEquals(g[0], g[5], 1e-5f); assertEquals(g[1], g[6], 1e-5f); assertEquals(g[2], g[12], 1e-5f)
        // Brighter shifts up, flat contrast squashes to grey.
        assertTrue(photoColorMatrix(0.5f, 0f, 0f)[4] > 0f)
        assertTrue(photoColorMatrix(0f, -1f, 0f)[0] < 0.1f)
    }

    @Test fun freeDragClampsToThePhoto() {
        val c = CropRect(0.2f, 0.2f, 0.8f, 0.8f)
        near(dragCrop(c, CropHandle.LEFT, -0.5f, 0f, null), CropRect(0f, 0.2f, 0.8f, 0.8f))
        near(dragCrop(c, CropHandle.BOTTOM_RIGHT, 0.1f, 0.05f, null), CropRect(0.2f, 0.2f, 0.9f, 0.85f))
        // Can't cross over the other side.
        assertEquals(0.06f, dragCrop(c, CropHandle.RIGHT, -1f, 0f, null).w, 1e-5f)
        near(dragCrop(c, CropHandle.MOVE, 0.5f, -0.5f, null), CropRect(0.4f, 0f, 1f, 0.6f))
    }

    @Test fun fixedShapeDragKeepsTheShape() {
        val c = cropToAspect(CropRect.FULL, 0.5f)
        near(c, CropRect(0.25f, 0f, 0.75f, 1f))
        val d = dragCrop(c, CropHandle.TOP_LEFT, 0.1f, 0.3f, 0.5f)
        assertEquals(0.5f, d.w / d.h, 1e-4f)
        assertEquals(c.r, d.r, 1e-5f); assertEquals(c.b, d.b, 1e-5f)
        // Sides do nothing when the shape is fixed.
        assertEquals(c, dragCrop(c, CropHandle.LEFT, 0.1f, 0f, 0.5f))
        // Growing past the photo stops at its edge, still the same shape.
        val g = dragCrop(d, CropHandle.TOP_LEFT, -1f, -1f, 0.5f)
        assertEquals(0.5f, g.w / g.h, 1e-4f); assertTrue(g.t >= -1e-5f && g.l >= -1e-5f)
    }
}
