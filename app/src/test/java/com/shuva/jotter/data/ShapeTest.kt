package com.shuva.jotter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class ShapeTest {
    private val wobble = java.util.Random(7)
    private fun jitter(v: Float, amount: Float = 3f) = v + (wobble.nextFloat() - 0.5f) * 2 * amount

    private fun corners(closed: List<Float>): Int {
        // Points of a snapped outline lie on a few straight sides; count direction changes over 20°, going
        // round once more past the start so the corner where it closes counts too.
        val out = closed + closed.subList(2, 4)
        var turns = 0
        var last: Double? = null
        for (i in 2 until out.size step 2) {
            val a = Math.toDegrees(kotlin.math.atan2((out[i + 1] - out[i - 1]).toDouble(), (out[i] - out[i - 2]).toDouble()))
            if (last != null && kotlin.math.abs(((a - last + 540) % 360) - 180) > 20) turns++
            last = a
        }
        return turns
    }

    private fun loop(vararg pts: Pair<Float, Float>, step: Float = 6f): List<Float> {
        val out = mutableListOf<Float>()
        val all = pts.toList() + pts[0]
        for (i in 0 until all.lastIndex) {
            val (x0, y0) = all[i]; val (x1, y1) = all[i + 1]
            val n = (hypot(x1 - x0, y1 - y0) / step).toInt().coerceAtLeast(1)
            for (k in 0 until n) { out += jitter(x0 + (x1 - x0) * k / n); out += jitter(y0 + (y1 - y0) * k / n) }
        }
        out += jitter(all.last().first); out += jitter(all.last().second)
        return out
    }

    @Test fun nearlyStraightStrokeBecomesALine() {
        val p = (0..40).flatMap { listOf(it * 10f, jitter(100f + it * 0.3f)) }
        val line = recognizeShape(p)!!
        assertEquals(4, line.size)
        assertEquals(line[1], line[3], 0.01f) // snapped level
    }

    @Test fun wobblyCircleBecomesARoundCircle() {
        val p = (0..80).flatMap { k -> val t = 2 * Math.PI * k / 80; listOf(jitter(200 + 100 * cos(t).toFloat()), jitter(200 + 100 * sin(t).toFloat())) }
        val c = recognizeShape(p)!!
        val radii = (c.indices step 2).map { hypot(c[it] - 200f, c[it + 1] - 200f) }
        assertTrue(radii.all { it in 90f..110f })
        assertTrue(radii.max() - radii.min() < 1f) // perfectly round
    }

    @Test fun boxBecomesAnUprightRectangle() {
        // Started partway along the top side, as people often do.
        val p = loop(150f to 100f, 300f to 102f, 302f to 220f, 98f to 218f, 100f to 101f)
        val r = recognizeShape(p)!!
        val xs = r.filterIndexed { i, _ -> i % 2 == 0 }; val ys = r.filterIndexed { i, _ -> i % 2 == 1 }
        // Upright: the corners sit on the box's sides, so each point is on its left/right edge or its top/bottom edge.
        assertTrue(xs.indices.all { i ->
            minOf(kotlin.math.abs(xs[i] - xs.min()), kotlin.math.abs(xs[i] - xs.max()), kotlin.math.abs(ys[i] - ys.min()), kotlin.math.abs(ys[i] - ys.max())) < 0.01f
        })
        assertEquals(4, corners(r))
    }

    @Test fun triangleKeepsThreeCorners() {
        val t = recognizeShape(loop(100f to 300f, 200f to 100f, 300f to 300f))
        assertNotNull(t)
        assertEquals(3, corners(t!!))
    }

    @Test fun scribbleAndOpenCurveStayAsDrawn() {
        val s = (0..60).flatMap { k -> val t = k / 6.0; listOf((k * 5).toFloat(), (100 + 60 * sin(t)).toFloat()) }
        assertNull(recognizeShape(s))
        assertNull(recognizeShape(listOf(0f, 0f, 1f, 1f, 2f, 2f, 3f, 3f))) // too small
    }
}
