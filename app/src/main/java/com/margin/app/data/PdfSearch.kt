package com.margin.app.data

/**
 * A page's text for Find: its letters in reading order, and for each character of [text] its box (left, top,
 * right, bottom) in points on the page as shown, from the top-left. Spaces between words and lines have no box
 * (NaN), so a phrase can be found across a line break but nothing is highlighted in the gap.
 */
class PageText(val text: String, val boxes: FloatArray) {
    init { require(boxes.size == text.length * 4) }
    companion object { val EMPTY = PageText("", FloatArray(0)) }
}

/** Builds a [PageText] a letter at a time. */
class PageTextBuilder {
    private val text = StringBuilder()
    private val boxes = ArrayList<Float>()

    fun letters(s: String, left: Float, top: Float, right: Float, bottom: Float) {
        // A glyph can stand for several characters ("ﬁ" is "fi"): they share its box, split along its width.
        val n = s.length
        if (n == 0) return
        for (i in 0 until n) {
            text.append(s[i])
            boxes += left + (right - left) * i / n; boxes += top; boxes += left + (right - left) * (i + 1) / n; boxes += bottom
        }
    }

    /** One space between words or lines (never two in a row, and none at the start). */
    fun gap() {
        if (text.isEmpty() || text.last() == ' ') return
        text.append(' ')
        repeat(4) { boxes += Float.NaN }
    }

    fun build(): PageText {
        // Drop a trailing space.
        if (text.isNotEmpty() && text.last() == ' ') { text.setLength(text.length - 1); repeat(4) { boxes.removeAt(boxes.lastIndex) } }
        return PageText(text.toString(), boxes.toFloatArray())
    }
}

/** One place [query] was found: characters [start] until [end] of page [page]'s text. */
data class PdfMatch(val page: Int, val start: Int, val end: Int)

/**
 * Every place [query] appears, page by page, top to bottom. Ignores case, and any run of spaces or line breaks
 * in the query matches any run of them on the page. At most [limit] matches.
 */
fun findInPages(pages: List<String>, query: String, limit: Int = 2000): List<PdfMatch> {
    val words = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return emptyList()
    val pattern = Regex(words.joinToString("\\s+") { Regex.escape(it) }, setOf(RegexOption.IGNORE_CASE))
    val out = ArrayList<PdfMatch>()
    pages.forEachIndexed { p, text ->
        for (m in pattern.findAll(text)) {
            out += PdfMatch(p, m.range.first, m.range.last + 1)
            if (out.size >= limit) return out
        }
    }
    return out
}

/**
 * The boxes to highlight for [match] on [page]: one per line it runs over (letters side by side on the same
 * line are joined into one box), as left, top, right, bottom in points.
 */
fun matchBoxes(page: PageText, match: PdfMatch): List<FloatArray> {
    val out = ArrayList<FloatArray>()
    var cur: FloatArray? = null
    for (i in match.start until match.end.coerceAtMost(page.text.length)) {
        val l = page.boxes[i * 4]; val t = page.boxes[i * 4 + 1]; val r = page.boxes[i * 4 + 2]; val b = page.boxes[i * 4 + 3]
        if (l.isNaN()) continue
        val c = cur
        // The same line: overlapping heights, and this letter starts at or after the box's left side.
        if (c != null && t < c[3] && b > c[1] && l >= c[0] - 1f) {
            c[0] = minOf(c[0], l); c[1] = minOf(c[1], t); c[2] = maxOf(c[2], r); c[3] = maxOf(c[3], b)
        } else {
            cur = floatArrayOf(l, t, r, b).also { out += it }
        }
    }
    return out
}
