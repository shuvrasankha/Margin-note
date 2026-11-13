package com.shuva.jotter.data

import java.math.BigDecimal
import java.math.MathContext
import java.util.Locale
import kotlin.math.abs
import kotlin.random.Random

/*
 * Math in notes. A line ending in "=" gets its answer ("12 × 4 + 3 =" → 51), an equation with one unknown
 * gets solved ("2x + 3 = 11" → x = 4), and "y = x^2" or "f(x) = sin(x)" can be graphed. Earlier lines can
 * name values ("rent = 1200") and functions ("f(x) = x^2 + 1") that later lines use.
 *
 * Also: systems of equations ("x + y = 10, x - y = 2", or one per line), inequalities ("2x + 3 > 7" → x > 2),
 * complex roots ("x^2 + 1 = 0" → x = ±i), derivatives ("d/dx x^3 =" → 3x^2, "... at 2 =", "f'(2) ="),
 * definite integrals ("∫ x^2 dx from 0 to 3 ="), sums ("Σ k^2 for k from 1 to 10 ="), fractions
 * ("1/3 + 1/4 =" → 7/12), and functions such as max, min, gcd, lcm, nCr, nPr, root, log(x, base), mod.
 */

/** What the editor offers for the line the cursor ends. */
sealed interface MathSuggestion {
    /** The answer to a sum ending in "=": typed after it. */
    data class Answer(val text: String) : MathSuggestion

    /** The solutions of an equation: typed after it as " → x = 4". */
    data class Solution(val variable: String, val values: List<String>) : MathSuggestion {
        val text get() = "$variable = " + values.joinToString(" or ")
    }

    /**
     * Anything else worked out for the line, typed after it as " → …": a system's solution ("x = 6, y = 4"),
     * an inequality's ("x > 2"), complex roots ("x = ±i") or "no real solution".
     */
    data class Solved(val text: String) : MathSuggestion

    /** A function of x ("y = x^2", "f(x) = sin(x)") that can be drawn. */
    data class Graph(val plot: Plot) : MathSuggestion
}

/** A curve to draw: its [label] as written and y for a given x (NaN where it isn't defined). */
class Plot(val label: String, val f: (Double) -> Double)

/**
 * What to offer for [text] with the cursor at [cursor], or null. Only when the cursor ends its line (spaces
 * after it are fine), so nothing pops up while editing the middle of a line.
 */
fun mathSuggestionAt(text: String, cursor: Int): MathSuggestion? {
    if (cursor !in 1..text.length) return null
    val lineEnd = text.indexOf('\n', cursor).let { if (it < 0) text.length else it }
    if (text.substring(cursor, lineEnd).isNotBlank()) return null
    val lineStart = text.lastIndexOf('\n', cursor - 1) + 1
    val line = text.substring(lineStart, cursor).trim()
    if ('→' in line || "==" in line) return null
    val comparison = comparisonIn(line)
    if ('=' !in line && comparison == null) return null
    val above = text.substring(0, lineStart).lines()
    TOTAL.matchEntire(line)?.let { m -> return totalOf(m.groupValues[1], above.drop(1).dropLast(1))?.let(MathSuggestion::Answer) }
    val scope = mathScope(above)
    if (comparison != null) return inequality(comparison, scope)
    systemAt(line, above.dropLast(1), scope)?.let { return it }
    return suggestionFor(line, scope)
}

/** The plot of every graphable line in [text] (each "y = …" and "f(x) = …"), top to bottom. */
fun plotsIn(text: String): List<Plot> {
    val lines = text.lines()
    return lines.indices.mapNotNull { i -> graphOf(lines[i].trim(), mathScope(lines.subList(0, i))) }
}

/** Compatibility: just the answer to a sum ending in "=". */
fun mathAnswerAt(text: String, cursor: Int): String? = (mathSuggestionAt(text, cursor) as? MathSuggestion.Answer)?.text

private fun suggestionFor(line: String, scope: Scope): MathSuggestion? {
    if (line.endsWith('=')) {
        val body = line.dropLast(1).substringAfterLast('=').trim()
        calculus(body, scope)?.let { return MathSuggestion.Answer(it) }
        convert(body, scope)?.let { return MathSuggestion.Answer(it) }
        // "a = 5 + 3 =": the sum is what comes after the earlier "=".
        val node = longestSum(body, scope) { it.unknowns(scope).isEmpty() } ?: return null
        if (node is Num) return null // "5 =" isn't a sum
        val v = node.eval(scope)
        return if (v.isFinite()) MathSuggestion.Answer(answerText(v, fractions = '/' in body || '÷' in body)) else null
    }
    if (line.count { it == '=' } != 1) return null
    graphOf(line, scope)?.let { return MathSuggestion.Graph(it) }
    val (lhs, rhs) = line.split('=')
    if (DEFINITION.matches(lhs)) return null // "rent = 1200" names a value
    val right = parseOrNull(rhs) ?: return null
    val left = longestSum(lhs, scope) { (it.unknowns(scope) + right.unknowns(scope)).size == 1 } ?: return null
    // "Let a = 10": a name given a value, not an equation to solve.
    if (left is Name && right.unknowns(scope).isEmpty()) return null
    val name = (left.unknowns(scope) + right.unknowns(scope)).single()
    val f = { x: Double -> val s = scope.with(name, x); left.eval(s) - right.eval(s) }
    if (IDENTITY_PROBES.all { abs(f(it)) < 1e-9 }) return MathSuggestion.Solved("true for every $name")
    val roots = solve(f)
    return if (roots.isNotEmpty()) MathSuggestion.Solution(name, roots.map(::niceRoot)) else noRealRoots(name, f)
}

/** For an equation with no real roots: "true for every x", complex roots of a quadratic, or "no real solution". */
private fun noRealRoots(name: String, f: (Double) -> Double): MathSuggestion {
    val c = f(0.0)
    val a = (f(1.0) + f(-1.0)) / 2 - c
    val b = (f(1.0) - f(-1.0)) / 2
    val quadratic = a != 0.0 && listOf(a, b, c).all { it.isFinite() } &&
        listOf(2.0, -3.5, 7.0).all { x -> val y = f(x); abs(y - (a * x * x + b * x + c)) < 1e-9 * maxOf(1.0, abs(y)) }
    if (quadratic) {
        val d = b * b - 4 * a * c
        if (d < 0) {
            val re = -b / (2 * a)
            val im = Math.sqrt(-d) / (2 * abs(a))
            val imText = niceRoot(im).let { if (it == "1") "i" else if (it.startsWith("√")) "i$it" else "${it}i" }
            return MathSuggestion.Solved("$name = " + if (abs(re) < 1e-12) "±$imText" else "${niceRoot(re)} ± $imText")
        }
    }
    return MathSuggestion.Solved("no real solution")
}

/** Where "2x + 2 = 2(x + 1)" is checked for being true whatever x is. */
private val IDENTITY_PROBES = listOf(-3.7, 0.0, 1.3, 10.1, 52.9)

/** "y = <x stuff>" or "f(x) = <x stuff>" → a plot; anything else null. */
private fun graphOf(line: String, scope: Scope): Plot? {
    if (line.count { it == '=' } != 1) return null
    val (lhs, rhs) = line.split('=')
    val param = FUNCTION_DEF.matchEntire(lhs.trim())?.groupValues?.get(2) ?: if (lhs.trim() == "y") "x" else return null
    val body = parseOrNull(rhs) ?: return null
    // x is the graph's own, even if an earlier line gave x a value.
    if (body.unknowns(scope.without(param)) != setOf(param)) return null
    return Plot(line) { x -> body.eval(scope.with(param, x)) }
}

private val DEFINITION = Regex("""\s*\p{L}+\s*""")
private val FUNCTION_DEF = Regex("""(\p{L}+)\s*\(\s*(\p{L}+)\s*\)""")

/** Named values and functions from [lines] above the cursor's line, in order, later ones winning. */
private fun mathScope(lines: List<String>): Scope {
    var scope = Scope()
    for (raw in lines) {
        val line = raw.trim()
        if ('→' in line || '=' !in line) continue
        scope = proseDefinitions(line, scope)
        if (line.count { it == '=' } > 2) continue
        val parts = line.split('=')
        val lhs = parts[0].trim()
        val rhs = parts[1]
        FUNCTION_DEF.matchEntire(lhs)?.let { m ->
            val (name, param) = m.destructured
            if (name.lowercase() in FUNCTIONS || name.lowercase() in MULTI || name == "y") return@let
            val body = parseOrNull(rhs) ?: return@let
            if (!body.unknowns(scope).all { it == param }) return@let
            scope = scope.withFunction(name, param, body)
        }
        if (DEFINITION.matches(lhs) && lhs.lowercase() !in FUNCTIONS) {
            val node = parseOrNull(rhs) ?: continue
            if (node.unknowns(scope).isNotEmpty()) continue
            val v = node.eval(scope)
            if (v.isFinite()) scope = scope.with(lhs, v)
        }
    }
    return scope
}

/** Where one definition in a sentence ends: a comma (not one in 1,200), semicolon, "and", "then", "with" or "where". */
private val PROSE_SPLIT = Regex("""\s*(?:[;&]|,(?!\d)|\b(?:and|then|with|where)\b)\s*""", RegexOption.IGNORE_CASE)
/** "Let a = 10", "b=2": a name (after a space, or the start) and what follows its "=". */
private val PROSE_DEF = Regex("""(?:^|\s)(\p{L}[\p{L}\p{N}_]*)\s*=\s*(.+)""")

/**
 * [scope] plus the values a sentence gives names: "Let a=10 and b =2", "a = 2, b = 3", "where r = 5 cm". A
 * value is the longest sum after the "=" that can be worked out, so words after it ("5 cm") are left out.
 * Not the last name of a sum ("x + y = 10" says nothing about y alone).
 */
private fun proseDefinitions(line: String, scope: Scope): Scope {
    var out = scope
    for (part in line.split(PROSE_SPLIT)) {
        if (part.count { it == '=' } != 1) continue
        val m = PROSE_DEF.find(part) ?: continue
        val name = m.groupValues[1]
        if (name.lowercase() in FUNCTIONS) continue
        val before = part.substring(0, m.groups[1]!!.range.first).trimEnd()
        if (before.isNotEmpty() && (before.last() in "+-−*/^×÷·(" || before.last().isDigit())) continue
        val words = m.groupValues[2].trim().trimEnd('.', '!', '?').split(' ').filter { it.isNotEmpty() }
        val v = (words.size downTo 1).firstNotNullOfOrNull { n ->
            parseOrNull(words.take(n).joinToString(" "))?.takeIf { it.unknowns(out).isEmpty() }?.eval(out)?.takeIf { it.isFinite() }
        } ?: continue
        out = out.with(name, v)
    }
    return out
}

/**
 * The longest sum at the end of [text] that [accept]s, so words in front are left out ("Lunch 12 + 8" →
 * 12 + 8). Sums start at the beginning of the text or after a space.
 */
private fun longestSum(text: String, scope: Scope, accept: (Node) -> Boolean): Node? {
    for (i in text.indices) {
        if (text[i] == ' ' || (i > 0 && text[i - 1] != ' ')) continue
        val node = parseOrNull(text.substring(i)) ?: continue
        if (accept(node)) return node
    }
    return null
}

internal fun parseOrNull(s: String): Node? = if (s.isBlank()) null else runCatching { Parser(s).parse() }.getOrNull()

/** The value of [sum], or null when it isn't a sum (a plain number is not one) or can't be worked out. */
internal fun evaluateSum(sum: String): Double? {
    val node = parseOrNull(sum) ?: return null
    if (node is Num || node.unknowns(Scope()).isNotEmpty()) return null
    return node.eval(Scope()).takeIf { it.isFinite() }
}

/**
 * [d] as people write it: whole numbers exactly, others rounded to [digits] significant digits (so 0.1 + 0.2
 * is 0.3), and very large or small ones as "1.2 × 10^30".
 */
internal fun formatNumber(d: Double, digits: Int = 12): String {
    if (d == Math.rint(d) && abs(d) < 9e15) return d.toLong().toString()
    val a = abs(d)
    if (a.isFinite() && (a >= 1e15 || a < 1e-7)) {
        val (mantissa, exp) = String.format(Locale.ROOT, "%.${digits - 1}e", d).split('e')
        val m = BigDecimal(mantissa).stripTrailingZeros().toPlainString()
        return "$m × 10^${exp.toInt()}"
    }
    val s = BigDecimal(d).round(MathContext(digits)).stripTrailingZeros().toPlainString()
    return if (s == "-0") "0" else s
}

// A root found by bisection is good to about 10 digits; 1e-12 off zero is zero.
private fun formatRoot(d: Double) = formatNumber(if (abs(d) < 1e-9) 0.0 else d, 10)

/** "p/q" when [d] is a fraction with a small denominator (2 to 1000), else null. */
private fun fractionOf(d: Double): String? {
    if (!d.isFinite() || abs(d) > 1e9) return null
    for (q in 2..1000) {
        val p = Math.rint(d * q)
        if (abs(d - p / q) < 1e-10 * maxOf(1.0, abs(d))) return "${p.toLong()}/$q"
    }
    return null
}

/** A long decimal: one that a fraction or a square root says better. */
private fun String.isLongDecimal() = substringAfter('.', "").length > 6 && '×' !in this

/** A root as people write it: 4, 1/3, -√2, or 1.414213562 when nothing simpler fits. */
internal fun niceRoot(d: Double): String {
    val plain = formatRoot(d)
    if (!plain.isLongDecimal()) return plain
    fractionOf(d)?.let { return it }
    val sq = d * d
    val n = Math.rint(sq)
    if (n in 2.0..100000.0 && abs(sq - n) < 1e-9 * n) return (if (d < 0) "-" else "") + "√${n.toLong()}"
    return plain
}

/** An answer: a fraction too ("7/12 ≈ 0.583333333333") when [fractions] and the decimal goes on. */
private fun answerText(v: Double, fractions: Boolean, digits: Int = 12): String {
    val plain = formatNumber(v, digits)
    if (!fractions || !plain.isLongDecimal()) return plain
    return fractionOf(v)?.let { "$it ≈ $plain" } ?: plain
}

/** [d] rounded to a whole number when it's within rounding error of one. */
private fun snap(d: Double): Double = if (abs(d - Math.rint(d)) < 1e-9 * maxOf(1.0, abs(d))) Math.rint(d) else d

/** Where solving and inequalities look: -100..100 in steps of 0.025, then out to ±1,000,000 more sparsely. */
private val GRID: List<Double> = buildList {
    for (k in -4000..4000) add(k / 40.0)
    for (k in 1..400) { val big = 100 * Math.pow(10.0, k / 100.0); add(big); add(-big) }
}.sorted()

/**
 * Where [f] is zero, nearest to 0 first (at most 4), searched from -1,000,000 to 1,000,000 with more points
 * near 0: where it crosses zero, and where it only touches zero (x² = 0, (x - 1/3)² = 0).
 */
internal fun solve(f: (Double) -> Double): List<Double> {
    val xs = GRID
    val ys = xs.map(f)
    val roots = mutableListOf<Double>()
    for (k in 1 until xs.size) {
        val px = xs[k - 1]; val py = ys[k - 1]; val x = xs[k]; val y = ys[k]
        when {
            y == 0.0 -> roots += x
            py.isFinite() && y.isFinite() && py != 0.0 && (py < 0) != (y < 0) -> {
                val r = bisect(f, px, py, x)
                // A jump across an asymptote (tan, 1/x) changes sign too; a real root makes f small.
                val fr = f(r)
                if (fr.isFinite() && abs(fr) < 1e-6 * maxOf(1.0, abs(py), abs(y))) roots += r
            }
        }
    }
    // Touching zero without crossing: a dip towards zero between two grid points. Find where the slope is flat.
    var tries = 0
    for (k in 1 until xs.size - 1) {
        val y0 = ys[k - 1]; val y1 = ys[k]; val y2 = ys[k + 1]
        if (!(y0.isFinite() && y1.isFinite() && y2.isFinite()) || y1 == 0.0) continue
        if (abs(y1) > abs(y0) || abs(y1) > abs(y2) || (y0 < 0) != (y1 < 0) || (y2 < 0) != (y1 < 0)) continue
        if (abs(y1) > 0.01 * maxOf(1.0, abs(y0), abs(y2))) continue
        if (++tries > 60) break
        val h = 1e-7 * maxOf(1.0, abs(xs[k]))
        val slope = { x: Double -> f(x + h) - f(x - h) }
        val a = xs[k - 1]; val b = xs[k + 1]
        val sa = slope(a)
        if (!sa.isFinite() || (sa < 0) == (slope(b) < 0)) continue
        val r = bisect(slope, a, sa, b)
        if (abs(f(r)) < 1e-12 * maxOf(1.0, abs(y0), abs(y2))) roots += r
    }
    return roots.map { if (abs(it - Math.rint(it)) < 1e-9) Math.rint(it) else it }
        .distinctBy { formatRoot(it) }.sortedBy { abs(it) }.take(4).sorted()
}

/** Where [f] changes sign between [a] (where it is [fa]) and [b]. */
private fun bisect(f: (Double) -> Double, a0: Double, fa0: Double, b0: Double): Double {
    var a = a0; var b = b0; var fa = fa0
    for (step in 0 until 200) {
        val m = (a + b) / 2
        if (m == a || m == b) break
        val fm = f(m)
        if (fm == 0.0) return m
        if (!fm.isFinite()) { a = m; continue }
        if ((fa < 0) == (fm < 0)) { a = m; fa = fm } else b = m
    }
    return (a + b) / 2
}

// ---------- Inequalities ----------

private class Comparison(val left: String, val op: String, val right: String)

private val COMPARE = Regex("""<=|>=|≤|≥|<|>""")

/** The one comparison in [line] ("2x + 3 > 7", "x^2 <= 4"), or null. */
private fun comparisonIn(line: String): Comparison? {
    val all = COMPARE.findAll(line).toList()
    if (all.size != 1) return null
    val m = all[0]
    if ('=' in line.removeRange(m.range)) return null
    val op = when (m.value) { "<=" -> "≤"; ">=" -> "≥"; else -> m.value }
    return Comparison(line.substring(0, m.range.first), op, line.substring(m.range.last + 1))
}

/** The values of the one unknown that make [c] true, as "x > 2", "-2 ≤ x ≤ 2" or "x < -1 or x > 1". */
private fun inequality(c: Comparison, scope: Scope): MathSuggestion? {
    val right = parseOrNull(c.right) ?: return null
    val left = longestSum(c.left, scope) { (it.unknowns(scope) + right.unknowns(scope)).size == 1 } ?: return null
    val name = (left.unknowns(scope) + right.unknowns(scope)).single()
    fun check(x: Double, slack: Double): Boolean {
        val s = scope.with(name, x)
        val a = left.eval(s)
        val b = right.eval(s)
        val tol = slack * maxOf(1.0, abs(a), abs(b))
        return a.isFinite() && b.isFinite() && when (c.op) {
            "<" -> a < b - tol
            ">" -> a > b + tol
            "≤" -> a <= b + tol
            else -> a >= b - tol
        }
    }
    return MathSuggestion.Solved(intervals(name, exact = { check(it, 0.0) }, atEdge = { check(it, 1e-9) }))
}

/**
 * Where [exact] is true, as runs of the number line. Whether each end belongs is checked with [atEdge], which
 * allows for rounding error.
 */
private fun intervals(name: String, exact: (Double) -> Boolean, atEdge: (Double) -> Boolean): String {
    val t = GRID.map(exact)
    if (t.all { it }) return "true for every $name"
    if (t.none { it }) return "no solution"
    // The point between a and b where holds changes, rounded to a whole number or simple fraction when it's one.
    fun edge(a0: Double, b0: Double): Double {
        var a = a0; var b = b0
        val ta = exact(a)
        for (step in 0 until 200) {
            val m = (a + b) / 2
            if (m == a || m == b) break
            if (exact(m) == ta) a = m else b = m
        }
        // The comparison allows for rounding error, which moves the edge a little: round it to 1 part in 10^7.
        val m = (a + b) / 2
        for (q in 1..100) {
            val p = Math.rint(m * q)
            if (abs(m - p / q) < 1e-7 * maxOf(1.0, abs(m))) return p / q
        }
        return m
    }
    val parts = mutableListOf<String>()
    var i = 0
    while (i < t.size) {
        if (!t[i]) { i++; continue }
        var j = i
        while (j + 1 < t.size && t[j + 1]) j++
        val lo = if (i == 0) null else edge(GRID[i - 1], GRID[i])
        val hi = if (j == t.lastIndex) null else edge(GRID[j], GRID[j + 1])
        parts += when {
            lo != null && hi != null && niceRoot(lo) == niceRoot(hi) -> "$name = ${niceRoot(lo)}"
            lo == null -> "$name ${if (atEdge(hi!!)) "≤" else "<"} ${niceRoot(hi)}"
            hi == null -> "$name ${if (atEdge(lo)) "≥" else ">"} ${niceRoot(lo)}"
            else -> "${niceRoot(lo)} ${if (atEdge(lo)) "≤" else "<"} $name ${if (atEdge(hi)) "≤" else "<"} ${niceRoot(hi)}"
        }
        i = j + 1
    }
    return parts.joinToString(" or ")
}

// ---------- Systems of equations ----------

private class Equation(val left: Node, val right: Node, val unknowns: Set<String>)

/** "2x + y = 7" with its unknowns (single letters), or null. */
private fun equationOf(text: String, scope: Scope): Equation? {
    if (text.count { it == '=' } != 1) return null
    val (l, r) = text.split('=')
    val right = parseOrNull(r.trim().trimEnd('.')) ?: return null
    val left = longestSum(l, scope) { (it.unknowns(scope) + right.unknowns(scope)).all { n -> n.length == 1 } } ?: return null
    return Equation(left, right, left.unknowns(scope) + right.unknowns(scope))
}

/**
 * Equations solved together: several on the line ("x + y = 10, x - y = 2"), or the line's equation with two or
 * more unknowns and the equations on the lines just above it, as many as there are unknowns (2 to 4).
 */
private fun systemAt(line: String, above: List<String>, scope: Scope): MathSuggestion? {
    if (line.endsWith('=')) return null
    val eqs = mutableListOf<Equation>()
    val here = line.split(PROSE_SPLIT).map { it.trim() }.filter { it.isNotEmpty() }
    if (here.size > 1) {
        here.forEach { eqs += equationOf(it, scope) ?: return null }
    } else {
        val eq = equationOf(line, scope) ?: return null
        if (eq.unknowns.size < 2) return null
        eqs += eq
        for (raw in above.asReversed()) {
            if (eqs.size >= eqs.flatMap { it.unknowns }.toSet().size) break
            val l = raw.trim()
            if (l.isEmpty() || '→' in l) break
            val got = l.split(PROSE_SPLIT).map { it.trim() }.filter { it.isNotEmpty() }.map { equationOf(it, scope) }
            if (got.any { it == null || it.unknowns.isEmpty() }) break
            eqs.addAll(0, got.filterNotNull())
        }
    }
    val names = eqs.flatMap { it.unknowns }.toSortedSet().toList()
    if (names.size !in 2..4 || eqs.size != names.size || eqs.all { it.unknowns.size < 2 }) return null
    val solutions = solveSystem(eqs, names, scope)
    if (solutions.isEmpty()) return null
    val text = if (solutions.size == 1) names.indices.joinToString(", ") { k -> "${names[k]} = ${niceRoot(solutions[0][k])}" }
    else names.joinToString(", ", "(", ")") + " = " + solutions.joinToString(" or ") { s -> s.joinToString(", ", "(", ")") { niceRoot(it) } }
    return MathSuggestion.Solved(text)
}

/** Solutions of [eqs] for [names] (up to 4), by Newton's method from many starting points. */
private fun solveSystem(eqs: List<Equation>, names: List<String>, scope: Scope): List<DoubleArray> {
    val n = names.size
    val residual = { v: DoubleArray ->
        var s = scope
        names.forEachIndexed { i, name -> s = s.with(name, v[i]) }
        DoubleArray(eqs.size) { k ->
            val a = eqs[k].left.eval(s)
            val b = eqs[k].right.eval(s)
            (a - b) / maxOf(1.0, abs(a), abs(b))
        }
    }
    val rnd = Random(7)
    val starts = listOf(DoubleArray(n), DoubleArray(n) { 1.0 }) + List(40) { DoubleArray(n) { rnd.nextDouble(-10.0, 10.0) } }
    val found = mutableListOf<DoubleArray>()
    for (start in starts) {
        val x = newton(residual, start)?.map(::snap)?.toDoubleArray() ?: continue
        if (found.none { f -> f.indices.all { abs(f[it] - x[it]) < 1e-7 * maxOf(1.0, abs(x[it])) } }) found += x
        if (found.size > 4) return emptyList() // a whole curve of solutions
    }
    return found.sortedWith { a, b -> a.indices.firstOrNull { a[it] != b[it] }?.let { a[it].compareTo(b[it]) } ?: 0 }
}

private fun newton(f: (DoubleArray) -> DoubleArray, start: DoubleArray): DoubleArray? {
    val n = start.size
    var x = start.copyOf()
    var fx = f(x)
    if (fx.any { !it.isFinite() }) return null
    repeat(100) {
        val norm = fx.maxOf { abs(it) }
        if (norm < 1e-14) return x
        val jac = Array(n) { DoubleArray(n) }
        for (c in 0 until n) {
            val h = 1e-7 * maxOf(1.0, abs(x[c]))
            val fp = f(x.copyOf().also { it[c] += h })
            for (r in 0 until n) jac[r][c] = (fp[r] - fx[r]) / h
        }
        val d = linearSolve(jac, DoubleArray(n) { -fx[it] }) ?: return null
        var step = 1.0
        while (true) {
            val nx = DoubleArray(n) { x[it] + step * d[it] }
            val nf = f(nx)
            if (nf.all { it.isFinite() } && nf.maxOf { abs(it) } < norm) { x = nx; fx = nf; break }
            step /= 2
            if (step < 1e-6) return if (norm < 1e-10) x else null
        }
    }
    return if (fx.maxOf { abs(it) } < 1e-10) x else null
}

/** a·x = b by Gaussian elimination, or null when a is singular. */
private fun linearSolve(a: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
    val n = b.size
    val m = Array(n) { r -> a[r].copyOf() }
    val v = b.copyOf()
    for (col in 0 until n) {
        val p = (col until n).maxBy { abs(m[it][col]) }
        if (abs(m[p][col]) < 1e-12) return null
        m[col] = m[p].also { m[p] = m[col] }
        v[col] = v[p].also { v[p] = v[col] }
        for (r in col + 1 until n) {
            val k = m[r][col] / m[col][col]
            for (c in col until n) m[r][c] -= k * m[col][c]
            v[r] -= k * v[col]
        }
    }
    val x = DoubleArray(n)
    for (r in n - 1 downTo 0) {
        var s = v[r]
        for (c in r + 1 until n) s -= m[r][c] * x[c]
        x[r] = s / m[r][r]
    }
    return x
}

// ---------- Calculus ----------

/** "d/dx x^3", "d²/dx² sin(x)", "derivative of x^2 at 3", "differentiate ln(x)". */
private val DERIVATIVE = Regex("""(?i)^(?:.*\s)?(?:d([²2]?)/d(\p{L})[²2]?|derivative of|differentiate)\s*(.+?)(?:\s+at\s+(.+))?$""")
/** "f'(2)", "f''(x)": a function from an earlier line. */
private val PRIME = Regex("""^(?:.*\s)?(\p{L})('{1,2}|′{1,2}|″)\s*\((.+)\)$""")
/** "∫ x^2 dx from 0 to 3", "integral of sin(x) from 0 to π". */
private val INTEGRAL = Regex("""(?i)^(?:.*\s)?(?:∫|integral of|integrate)\s*(.+?)\s*(?:\bd(\p{L}))?\s+from\s+(.+?)\s+to\s+(.+)$""")
/** "Σ k^2 for k from 1 to 10", "sum of 1/n^2 for n from 1 to 1000". */
private val SIGMA = Regex("""(?i)^(?:.*\s)?(?:Σ|∑|sum of)\s*(.+?)\s+for\s+(\p{L})\s+from\s+(.+?)\s+to\s+(.+)$""")

/** The answer to a derivative, integral or sum ending in "=", or null when [body] is none of those. */
private fun calculus(body: String, scope: Scope): String? {
    fun value(text: String) = parseOrNull(text)?.takeIf { it.unknowns(scope).isEmpty() }?.eval(scope)?.takeIf { it.isFinite() }
    fun variableOf(node: Node, given: String?) = given ?: node.unknowns(scope).singleOrNull() ?: "x"

    PRIME.matchEntire(body)?.let { m ->
        val (name, primes, argText) = m.destructured
        val (param, fnBody) = scope.function(name) ?: return null
        val times = if (primes == "″") 2 else primes.length
        var d: Node = fnBody
        repeat(times) { d = simplify(derive(d, param, scope) ?: return null) }
        val arg = parseOrNull(argText) ?: return null
        return if (arg.unknowns(scope).isEmpty()) {
            answerText(snap(d.eval(scope.with(param, arg.eval(scope)))), fractions = true, digits = 10)
        } else show(simplify(substitute(d, param, arg)))
    }
    DERIVATIVE.matchEntire(body)?.let { m ->
        val expr = parseOrNull(m.groupValues[3]) ?: return null
        val v = variableOf(expr, m.groupValues[2].ifEmpty { null })
        val times = if (m.groupValues[1].isNotEmpty()) 2 else 1
        var d: Node? = expr
        repeat(times) { d = d?.let { derive(it, v, scope) }?.let(::simplify) }
        val at = m.groupValues[4].ifEmpty { null }
        if (at == null) return d?.let(::show)
        val x = value(at) ?: return null
        val r = d?.eval(scope.with(v, x))?.takeIf { it.isFinite() }
            ?: if (times == 1) numericDerivative({ t -> expr.eval(scope.with(v, t)) }, x) else return null
        return if (r.isFinite()) answerText(snap(r), fractions = true, digits = 10) else null
    }
    INTEGRAL.matchEntire(body)?.let { m ->
        val expr = parseOrNull(m.groupValues[1]) ?: return null
        val v = variableOf(expr, m.groupValues[2].ifEmpty { null })
        val a = value(m.groupValues[3]) ?: return null
        val b = value(m.groupValues[4]) ?: return null
        if (!(expr.unknowns(scope.with(v, 0.0)).isEmpty())) return null
        val r = integrate({ t -> expr.eval(scope.with(v, t)) }, a, b)
        return if (r.isFinite()) answerText(snapAnswer(r), fractions = true, digits = 10) else null
    }
    SIGMA.matchEntire(body)?.let { m ->
        val expr = parseOrNull(m.groupValues[1]) ?: return null
        val v = m.groupValues[2]
        val a = value(m.groupValues[3])?.let(Math::rint) ?: return null
        val b = value(m.groupValues[4])?.let(Math::rint) ?: return null
        if (b - a > 1_000_000 || !(expr.unknowns(scope.with(v, 0.0)).isEmpty())) return null
        var sum = 0.0
        var k = a
        while (k <= b) { sum += expr.eval(scope.with(v, k)); k++ }
        return if (sum.isFinite()) answerText(sum, fractions = true) else null
    }
    return null
}

/** A numeric result rounded where it's within numerical error of a whole number. */
private fun snapAnswer(d: Double) = if (abs(d - Math.rint(d)) < 1e-8 * maxOf(1.0, abs(d))) Math.rint(d) else d

private fun numericDerivative(f: (Double) -> Double, x: Double): Double {
    fun central(h: Double) = (f(x + h) - f(x - h)) / (2 * h)
    val h = 1e-3 * maxOf(1.0, abs(x))
    // Richardson extrapolation: two halvings cancel the error terms in h² and h⁴.
    val d1 = central(h); val d2 = central(h / 2); val d3 = central(h / 4)
    val r1 = (4 * d2 - d1) / 3; val r2 = (4 * d3 - d2) / 3
    return (16 * r2 - r1) / 15
}

/** ∫ f from a to b by adaptive Simpson's rule. */
private fun integrate(f: (Double) -> Double, a: Double, b: Double): Double {
    fun simpson(a: Double, fa: Double, b: Double, fb: Double, m: Double, fm: Double) = (b - a) / 6 * (fa + 4 * fm + fb)
    fun go(a: Double, fa: Double, b: Double, fb: Double, m: Double, fm: Double, whole: Double, eps: Double, depth: Int): Double {
        val lm = (a + m) / 2; val flm = f(lm)
        val rm = (m + b) / 2; val frm = f(rm)
        val left = simpson(a, fa, m, fm, lm, flm)
        val right = simpson(m, fm, b, fb, rm, frm)
        val delta = left + right - whole
        if (depth <= 0 || abs(delta) <= 15 * eps || !delta.isFinite()) return left + right + delta / 15
        return go(a, fa, m, fm, lm, flm, left, eps / 2, depth - 1) + go(m, fm, b, fb, rm, frm, right, eps / 2, depth - 1)
    }
    if (a == b) return 0.0
    val fa = f(a); val fb = f(b); val m = (a + b) / 2; val fm = f(m)
    return go(a, fa, b, fb, m, fm, simpson(a, fa, b, fb, m, fm), 1e-11, 45)
}

/** Does [this] depend on [v]? */
private fun Node.has(v: String): Boolean = when (this) {
    is Num -> false
    is Name -> name == v
    is Split -> v.length == 1 && v[0] in name
    is Neg -> a.has(v)
    is Fn1 -> a.has(v)
    is Bin -> a.has(v) || b.has(v)
    is FnN -> args.any { it.has(v) }
    is Percent -> a.has(v)
    is CallOrTimes -> name == v || arg.has(v)
}

/** [n] with [v] replaced by [r]. */
private fun substitute(n: Node, v: String, r: Node): Node = when (n) {
    is Num -> n
    is Name -> if (n.name == v) r else n
    is Split -> if (n.has(v)) substitute(n.expanded(), v, r) else n
    is Neg -> Neg(substitute(n.a, v, r))
    is Fn1 -> Fn1(n.name, substitute(n.a, v, r))
    is Bin -> Bin(n.op, substitute(n.a, v, r), substitute(n.b, v, r))
    is FnN -> FnN(n.name, n.args.map { substitute(it, v, r) })
    is Percent -> Percent(substitute(n.a, v, r))
    is CallOrTimes -> CallOrTimes(n.name, substitute(n.arg, v, r))
}

private fun num(v: Double) = Num(v)
private operator fun Node.plus(o: Node) = Bin('+', this, o)
private operator fun Node.minus(o: Node) = Bin('-', this, o)
private operator fun Node.times(o: Node) = Bin('*', this, o)
private operator fun Node.div(o: Node) = Bin('/', this, o)
private fun Node.pow(o: Node) = Bin('^', this, o)

/** d[n]/d[v], worked out by the rules of differentiation, or null for something it can't (n!, %, max). */
private fun derive(n: Node, v: String, scope: Scope): Node? {
    if (!n.has(v)) return num(0.0)
    return when (n) {
        is Num -> num(0.0)
        is Name -> num(1.0)
        is Split -> derive(n.expanded(), v, scope)
        is Neg -> derive(n.a, v, scope)?.let(::Neg)
        is Bin -> {
            val a = n.a; val b = n.b
            val da = derive(a, v, scope) ?: return null
            val db = derive(b, v, scope) ?: return null
            when (n.op) {
                '+' -> da + db
                '-' -> da - db
                '*' -> da * b + a * db
                '/' -> (da * b - a * db) / b.pow(num(2.0))
                '^' -> when {
                    !b.has(v) -> b * a.pow(b - num(1.0)) * da
                    !a.has(v) -> n * Fn1("ln", a) * db
                    else -> n * (db * Fn1("ln", a) + b * da / a)
                }
                else -> null
            }
        }
        is Fn1 -> {
            val a = n.a
            val da = derive(a, v, scope) ?: return null
            val outer: Node = when (n.name) {
                "sin" -> Fn1("cos", a)
                "cos" -> Neg(Fn1("sin", a))
                "tan" -> num(1.0) / Fn1("cos", a).pow(num(2.0))
                "sec" -> Fn1("sec", a) * Fn1("tan", a)
                "csc" -> Neg(Fn1("csc", a) * Fn1("cot", a))
                "cot" -> Neg(num(1.0) / Fn1("sin", a).pow(num(2.0)))
                "asin", "arcsin" -> num(1.0) / Fn1("sqrt", num(1.0) - a.pow(num(2.0)))
                "acos", "arccos" -> Neg(num(1.0) / Fn1("sqrt", num(1.0) - a.pow(num(2.0))))
                "atan", "arctan" -> num(1.0) / (num(1.0) + a.pow(num(2.0)))
                "sinh" -> Fn1("cosh", a)
                "cosh" -> Fn1("sinh", a)
                "tanh" -> num(1.0) / Fn1("cosh", a).pow(num(2.0))
                "ln" -> num(1.0) / a
                "log" -> num(1.0) / (a * Fn1("ln", num(10.0)))
                "log2" -> num(1.0) / (a * Fn1("ln", num(2.0)))
                "exp" -> n
                "sqrt" -> num(1.0) / (num(2.0) * n)
                "cbrt" -> num(1.0) / (num(3.0) * n.pow(num(2.0)))
                "abs" -> a / n
                "deg" -> num(Math.PI / 180)
                else -> return null
            }
            outer * da
        }
        is CallOrTimes -> {
            val fn = scope.function(n.name)
            if (fn != null) derive(substitute(fn.second, fn.first, n.arg), v, scope) else derive(Name(n.name) * n.arg, v, scope)
        }
        else -> null
    }
}

private val Node.isZero get() = this is Num && v == 0.0
private val Node.isOne get() = this is Num && v == 1.0

/** [n] tidied: numbers worked out, 0s and 1s dropped, constants in front, x·x as x^2. */
private fun simplify(n: Node): Node = when (n) {
    is Neg -> when (val a = simplify(n.a)) {
        is Num -> num(-a.v)
        is Neg -> a.a
        else -> Neg(a)
    }
    is Fn1 -> {
        val a = simplify(n.a)
        val folded = if (a.unknowns(Scope()).isEmpty()) Fn1(n.name, a).eval(Scope()) else Double.NaN
        if (folded.isFinite() && folded == Math.rint(folded)) num(folded) else Fn1(n.name, a)
    }
    is Bin -> {
        val a = simplify(n.a)
        val b = simplify(n.b)
        val folded = if (a is Num && b is Num) Bin(n.op, a, b).eval(Scope()) else Double.NaN
        when {
            folded.isFinite() && (n.op in "+-*" || folded == Math.rint(folded)) -> num(folded)
            n.op == '+' -> when {
                a.isZero -> b
                b.isZero -> a
                b is Neg -> simplify(a - b.a)
                b is Num && b.v < 0 -> a - num(-b.v)
                show(a) == show(b) -> simplify(num(2.0) * a)
                else -> a + b
            }
            n.op == '-' -> when {
                b.isZero -> a
                a.isZero -> simplify(Neg(b))
                b is Neg -> simplify(a + b.a)
                b is Num && b.v < 0 -> a + num(-b.v)
                show(a) == show(b) -> num(0.0)
                else -> a - b
            }
            n.op == '*' -> when {
                a.isZero || b.isZero -> num(0.0)
                a.isOne -> b
                b.isOne -> a
                a is Num && a.v == -1.0 -> simplify(Neg(b))
                a is Neg -> simplify(Neg(a.a * b))
                b is Neg -> simplify(Neg(a * b.a))
                b is Num && a !is Num -> simplify(b * a)
                a is Num && b is Bin && b.op == '*' && b.a is Num -> simplify(num(a.v * (b.a as Num).v) * b.b)
                a is Num && b is Bin && b.op == '/' && b.a is Num -> simplify(num(a.v * (b.a as Num).v) / b.b)
                show(a) == show(b) -> a.pow(num(2.0))
                else -> a * b
            }
            n.op == '/' -> when {
                a.isZero -> num(0.0)
                b.isOne -> a
                show(a) == show(b) -> num(1.0)
                else -> a / b
            }
            n.op == '^' -> when {
                b.isZero -> num(1.0)
                b.isOne -> a
                else -> a.pow(b)
            }
            else -> Bin(n.op, a, b)
        }
    }
    else -> n
}

/** How tightly [n] binds when written out, for brackets. */
private fun precedence(n: Node): Int = when (n) {
    is Bin -> when (n.op) { '+', '-', 'P', 'Q' -> 1; '*', '/', 'm' -> 2; '^' -> 4; else -> 0 }
    is Neg -> 3
    is Num -> show(n).let { t -> if ('/' in t) 2 else if (t.startsWith("-") || '×' in t) 3 else 5 }
    else -> 5
}

/** [n] written out the way it's typed: "3x^2 + 2", "2sin(x) × cos(x)", "1/(2√x)". */
private fun show(n: Node): String {
    fun wrap(m: Node, p: Int) = if (precedence(m) < p) "(${show(m)})" else show(m)
    return when (n) {
        is Num -> fractionOf(n.v)?.takeIf { formatNumber(n.v, 10).isLongDecimal() } ?: formatNumber(n.v, 10)
        is Name -> n.name
        is Split -> n.name
        is Neg -> "-" + wrap(n.a, 3)
        is Fn1 -> when (n.name) {
            "fact" -> wrap(n.a, 5) + "!"
            "deg" -> wrap(n.a, 5) + "°"
            "abs" -> "|${show(n.a)}|"
            else -> "${n.name}(${show(n.a)})"
        }
        is Bin -> when (n.op) {
            '+' -> "${show(n.a)} + ${wrap(n.b, 1)}"
            '-' -> "${show(n.a)} - ${wrap(n.b, 2)}"
            '*' -> {
                val l = wrap(n.a, 2)
                val r = wrap(n.b, 3)
                // 3x^2, 2sin(x), 4(x + 1): a number in front of a name or bracket is written side by side.
                if (n.a is Num && precedence(n.a) == 5 && (r.first().isLetter() || r.first() == '(')) l + r else "$l × $r"
            }
            '/' -> "${wrap(n.a, 2)}/${wrap(n.b, 3)}"
            '^' -> "${wrap(n.a, 5)}^${wrap(n.b, 5)}"
            'm' -> "${wrap(n.a, 2)} mod ${wrap(n.b, 3)}"
            'P' -> "${show(n.a)} + ${show(n.b)}"
            'Q' -> "${show(n.a)} - ${show(n.b)}"
            else -> "?"
        }
        is FnN -> "${n.name}(${n.args.joinToString(", ") { show(it) }})"
        is Percent -> wrap(n.a, 5) + "%"
        is CallOrTimes -> "${n.name}(${show(n.arg)})"
    }
}

// ---------- Parsing ----------

/** Values and functions a sum can use; [vars] includes the x being solved or graphed for. */
class Scope(
    private val vars: Map<String, Double> = emptyMap(),
    private val functions: Map<String, Pair<String, Node>> = emptyMap(),
) {
    fun with(name: String, value: Double) = Scope(vars + (name to value), functions)
    fun without(name: String) = Scope(vars - name, functions)
    fun withFunction(name: String, param: String, body: Node) = Scope(vars, functions + (name to (param to body)))
    fun value(name: String): Double? = vars[name] ?: CONSTANTS[name]
    fun function(name: String) = functions[name]
}

private val CONSTANTS = mapOf("pi" to Math.PI, "π" to Math.PI, "e" to Math.E, "τ" to 2 * Math.PI)

private val FUNCTIONS: Map<String, (Double) -> Double> = mapOf(
    "sin" to Math::sin, "cos" to Math::cos, "tan" to Math::tan,
    "sec" to { x -> 1 / Math.cos(x) }, "csc" to { x -> 1 / Math.sin(x) }, "cot" to { x -> 1 / Math.tan(x) },
    "asin" to Math::asin, "acos" to Math::acos, "atan" to Math::atan,
    "arcsin" to Math::asin, "arccos" to Math::acos, "arctan" to Math::atan,
    "sinh" to Math::sinh, "cosh" to Math::cosh, "tanh" to Math::tanh,
    "sqrt" to Math::sqrt, "cbrt" to Math::cbrt, "abs" to Math::abs, "exp" to Math::exp,
    "ln" to Math::log, "log" to Math::log10, "log2" to { x -> Math.log(x) / Math.log(2.0) },
    "floor" to Math::floor, "ceil" to Math::ceil, "round" to { x -> Math.rint(x) },
)

/** Written as symbols, not names: n! and degrees. */
private val SYMBOL_FUNCTIONS: Map<String, (Double) -> Double> = mapOf("fact" to ::factorial, "deg" to Math::toRadians)

/** Functions of two or more values: max(1, 5, 3), gcd(12, 18), nCr(5, 2), log(8, 2), root(27, 3), round(3.14159, 2). */
private val MULTI = setOf("max", "min", "gcd", "lcm", "ncr", "npr", "log", "root", "mod", "round", "hypot")

/** Two or three of these letters side by side ("xy", "ab") are a product unless an earlier line names them. */
private val PRODUCT_WORD = Regex("""[abcxyz]{2,3}""")

/** A parsed sum. */
sealed interface Node {
    fun eval(s: Scope): Double
    /** Names in it that [s] doesn't know: the unknowns to solve or graph for. */
    fun unknowns(s: Scope): Set<String>
}

private class Num(val v: Double) : Node {
    override fun eval(s: Scope) = v
    override fun unknowns(s: Scope) = emptySet<String>()
}

private class Name(val name: String) : Node {
    override fun eval(s: Scope) = s.value(name) ?: Double.NaN
    override fun unknowns(s: Scope) = if (s.value(name) == null) setOf(name) else emptySet()
}

/** "xy": x times y, unless an earlier line gave "xy" a value of its own. */
private class Split(val name: String) : Node {
    fun expanded(): Node = name.map { Name(it.toString()) as Node }.reduce { a, b -> Bin('*', a, b) }
    override fun eval(s: Scope) = s.value(name) ?: expanded().eval(s)
    override fun unknowns(s: Scope) = if (s.value(name) != null) emptySet() else expanded().unknowns(s)
}

private class Neg(val a: Node) : Node {
    override fun eval(s: Scope) = -a.eval(s)
    override fun unknowns(s: Scope) = a.unknowns(s)
}

/** A function of one value: sin, √ ([name] "sqrt"), n! ("fact"), 30° ("deg"), |x| ("abs"). */
private class Fn1(val name: String, val a: Node) : Node {
    override fun eval(s: Scope): Double {
        val f = FUNCTIONS[name] ?: SYMBOL_FUNCTIONS[name] ?: return Double.NaN
        return f(a.eval(s))
    }
    override fun unknowns(s: Scope) = a.unknowns(s)
}

/** + - * / ^, 'm' (mod), and 'P' / 'Q' ("a + 15%", "a - 15%"). */
private class Bin(val op: Char, val a: Node, val b: Node) : Node {
    override fun eval(s: Scope): Double {
        val x = a.eval(s)
        val y = b.eval(s)
        return when (op) {
            '+' -> x + y
            '-' -> x - y
            '*' -> x * y
            '/' -> x / y
            '^' -> power(x, y)
            'm' -> if (y == 0.0) Double.NaN else x.mod(y)
            'P' -> x + x * y
            'Q' -> x - x * y
            else -> Double.NaN
        }
    }
    override fun unknowns(s: Scope) = a.unknowns(s) + b.unknowns(s)
}

/** x^y, with odd roots of negative numbers real: (-8)^(1/3) is -2. */
private fun power(x: Double, y: Double): Double {
    val p = Math.pow(x, y)
    if (!p.isNaN() || x >= 0) return p
    val inv = 1 / y
    val odd = Math.rint(inv)
    return if (abs(inv - odd) < 1e-9 && odd.toLong() % 2 != 0L) -Math.pow(-x, y) else p
}

private class FnN(val name: String, val args: List<Node>) : Node {
    override fun eval(s: Scope): Double {
        val v = args.map { it.eval(s) }
        fun whole(d: Double) = d == Math.rint(d) && abs(d) < 9e15
        return when {
            name == "max" -> v.maxOrNull() ?: Double.NaN
            name == "min" -> v.minOrNull() ?: Double.NaN
            v.size != 2 -> Double.NaN
            name == "gcd" || name == "lcm" -> {
                if (!whole(v[0]) || !whole(v[1])) return Double.NaN
                val g = gcd(abs(v[0].toLong()), abs(v[1].toLong()))
                if (name == "gcd") g.toDouble() else if (g == 0L) 0.0 else abs(v[0] / g * v[1])
            }
            name == "ncr" || name == "npr" -> {
                if (!whole(v[0]) || !whole(v[1]) || v[1] < 0 || v[1] > v[0]) return Double.NaN
                var r = 1.0
                val k = if (name == "ncr") minOf(v[1], v[0] - v[1]).toInt() else v[1].toInt()
                for (i in 0 until k) r = if (name == "ncr") r * (v[0] - i) / (i + 1) else r * (v[0] - i)
                Math.rint(r)
            }
            name == "log" -> Math.log(v[0]) / Math.log(v[1])
            name == "root" -> power(v[0], 1 / v[1])
            name == "mod" -> if (v[1] == 0.0) Double.NaN else v[0].mod(v[1])
            name == "round" -> BigDecimal(v[0]).setScale(v[1].toInt(), java.math.RoundingMode.HALF_UP).toDouble()
            name == "hypot" -> Math.hypot(v[0], v[1])
            else -> Double.NaN
        }
    }
    override fun unknowns(s: Scope) = args.flatMap { it.unknowns(s) }.toSet()
}

private tailrec fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)

/** A bare "15%", which "a + 15%" treats as 15% of a. */
private class Percent(val a: Node) : Node {
    override fun eval(s: Scope) = a.eval(s) / 100
    override fun unknowns(s: Scope) = a.unknowns(s)
}

private class Fail : Exception()

private fun factorial(n: Double): Double {
    if (n < 0 || n != Math.rint(n) || n > 170) return Double.NaN
    var r = 1.0
    for (k in 2..n.toInt()) r *= k
    return r
}

private sealed interface Tok {
    data class N(val v: Double) : Tok
    data class Id(val s: String) : Tok
    data class Op(val c: Char) : Tok
}

private const val SUPERSCRIPTS = "⁰¹²³⁴⁵⁶⁷⁸⁹"

/**
 * Splits [s] into numbers, names and symbols. "5x3" multiplies; "2x" is 2 times x; "1.5e3" is 1500; "of" is
 * times ("15% of 200"); "mod" is the remainder; "x⁴" is x^4; "2πr" is 2 × π × r.
 */
private fun tokenize(s: String): List<Tok> {
    val out = mutableListOf<Tok>()
    var i = 0
    while (i < s.length) {
        val c = s[i]
        when {
            c == ' ' -> i++
            c.isDigit() || (c == '.' && s.getOrNull(i + 1)?.isDigit() == true) -> {
                val sb = StringBuilder()
                var dot = false
                while (i < s.length) {
                    val d = s[i]
                    when {
                        d.isDigit() -> sb.append(d)
                        d == '.' && !dot -> { dot = true; sb.append(d) }
                        // 1,234,567: a comma only between groups of three digits.
                        d == ',' && !dot && (1..3).all { s.getOrNull(i + it)?.isDigit() == true } && s.getOrNull(i + 4)?.isDigit() != true -> Unit
                        else -> break
                    }
                    i++
                }
                // 1.5e3, 2E-4: scientific notation (a lone "2e" is still 2 × e).
                if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                    val sign = s.getOrNull(i + 1)?.takeIf { it == '-' || it == '+' }
                    val first = i + 1 + (if (sign != null) 1 else 0)
                    if (s.getOrNull(first)?.isDigit() == true) {
                        var j = first
                        while (j < s.length && s[j].isDigit()) j++
                        sb.append('e').append(s, i + 1, j)
                        i = j
                    }
                }
                out += Tok.N(sb.toString().toDoubleOrNull() ?: throw Fail())
            }
            c.isLetter() -> {
                val start = i
                while (i < s.length && s[i].isLetter()) i++
                // log2 is a name with a digit in it.
                if (s.substring(start, i) == "log" && s.getOrNull(i) == '2') i++
                val word = s.substring(start, i)
                val prev = out.lastOrNull()
                val next = s.substring(i).trimStart().firstOrNull()
                when {
                    // "5x3": x between numbers is times.
                    (word == "x" || word == "X") && (prev is Tok.N || prev == Tok.Op(')')) && next != null && next.isDigit() -> out += Tok.Op('*')
                    word.lowercase() == "of" -> out += Tok.Op('*')
                    word.lowercase() == "mod" && next != '(' -> out += Tok.Op('m')
                    // 2πr: π and τ stand apart from the letters around them.
                    word.length > 1 && (word.contains('π') || word.contains('τ')) ->
                        Regex("""[πτ]|[^πτ]+""").findAll(word).forEach { out += Tok.Id(it.value) }
                    else -> out += Tok.Id(word)
                }
            }
            c in SUPERSCRIPTS -> {
                var v = 0
                while (i < s.length && s[i] in SUPERSCRIPTS) { v = v * 10 + SUPERSCRIPTS.indexOf(s[i]); i++ }
                out += Tok.Op('^'); out += Tok.N(v.toDouble())
            }
            else -> {
                out += Tok.Op(
                    when (c) {
                        '−', '–' -> '-'
                        '×', '·', '⋅', '∙' -> '*'
                        '÷', '∕' -> '/'
                        '∛' -> { out += Tok.Id("cbrt"); i++; continue }
                        in "+-*/^%!()√°|," -> c
                        else -> throw Fail()
                    },
                )
                i++
            }
        }
    }
    return out
}

/**
 * + − × ÷ ^ % ! √ ° mod, brackets, |abs|, functions (sin, log, max(a, b), ...), constants (π, e), names from
 * earlier lines and multiplying by writing side by side ("2x", "3(4 + 1)", "2sin(x)"). Angles are radians
 * unless marked with °.
 */
private class Parser(s: String) {
    private val t = tokenize(s)
    private var i = 0

    fun parse(): Node {
        if (t.isEmpty()) throw Fail()
        val n = expr()
        if (i != t.size) throw Fail()
        return n
    }

    private fun peek() = t.getOrNull(i)
    private fun isOp(c: Char) = peek() == Tok.Op(c)

    private fun expr(): Node {
        var v = term()
        while (isOp('+') || isOp('-')) {
            val plus = isOp('+')
            i++
            val r = term()
            // "a + 15%" adds 15 percent of a, as on a calculator.
            v = when {
                r is Percent -> Bin(if (plus) 'P' else 'Q', v, r)
                plus -> Bin('+', v, r)
                else -> Bin('-', v, r)
            }
        }
        return v
    }

    private fun term(): Node {
        var v = unary()
        while (true) {
            v = when {
                isOp('*') -> { i++; Bin('*', v, unary()) }
                isOp('/') -> { i++; Bin('/', v, unary()) }
                isOp('m') -> { i++; Bin('m', v, unary()) }
                // Side by side: 2x, 2(3), 2√9, 2sin(x), (a)(b). Not 2 3, and not after a closing "|".
                isOp('(') || isOp('√') || peek() is Tok.Id -> Bin('*', v, power())
                else -> return v
            }
        }
    }

    private fun unary(): Node = when {
        isOp('-') -> { i++; Neg(unary()) }
        isOp('+') -> { i++; unary() }
        else -> power()
    }

    private fun power(): Node {
        val base = postfix()
        if (!isOp('^')) return base
        i++
        return Bin('^', base, unary())
    }

    private fun postfix(): Node {
        var v = primary()
        while (true) {
            v = when {
                isOp('%') -> { i++; Percent(v) }
                isOp('!') -> { i++; Fn1("fact", v) }
                isOp('°') -> { i++; Fn1("deg", v) }
                else -> return v
            }
        }
    }

    private fun group(): Node = expr().also { if (!isOp(')')) throw Fail(); i++ }

    private fun primary(): Node {
        val tok = peek() ?: throw Fail()
        i++
        return when {
            tok is Tok.N -> Num(tok.v)
            tok == Tok.Op('(') -> group()
            tok == Tok.Op('|') -> expr().also { if (!isOp('|')) throw Fail(); i++ }.let { Fn1("abs", it) }
            tok == Tok.Op('√') -> Fn1("sqrt", postfix())
            tok is Tok.Id -> {
                val lower = tok.s.lowercase()
                val single = lower in FUNCTIONS
                when {
                    (single || lower in MULTI) && isOp('(') -> {
                        i++
                        val args = mutableListOf(expr())
                        while (isOp(',')) { i++; args += expr() }
                        if (!isOp(')')) throw Fail()
                        i++
                        if (args.size == 1 && single) Fn1(lower, args[0]) else FnN(lower, args)
                    }
                    // sin x / sin 30° without brackets.
                    single -> Fn1(lower, power())
                    isOp('(') -> { i++; CallOrTimes(tok.s, group()) }
                    PRODUCT_WORD.matches(tok.s) -> Split(tok.s)
                    else -> Name(tok.s)
                }
            }
            else -> throw Fail()
        }
    }
}

/** "f(3)": calls f when an earlier line defines it, otherwise multiplies (so "a(b + 1)" still works). */
private class CallOrTimes(val name: String, val arg: Node) : Node {
    override fun eval(s: Scope): Double {
        val (param, body) = s.function(name) ?: return Name(name).eval(s) * arg.eval(s)
        return body.eval(s.with(param, arg.eval(s)))
    }
    override fun unknowns(s: Scope): Set<String> = if (s.function(name) != null) arg.unknowns(s) else Name(name).unknowns(s) + arg.unknowns(s)
}

// ---------- Totals ----------

/** "Total =", "Grand total:=", "Average =": a line that totals the list above it. */
private val TOTAL = Regex("""(?i)(?:.*\s)?(total|subtotal|sum|average|avg|mean)\s*:?\s*=""")
/** A number as written in a list: 1,200 or 4.50 or -3, with any currency sign in front ignored. */
private val LIST_NUMBER = Regex("""-?\d{1,3}(?:,\d{3})+(?:\.\d+)?|-?\d+(?:\.\d+)?""")

/**
 * The total (or average, for [kind] average / avg / mean) of the list [lines] ends with, as text: each line's
 * last number, or a sum's answer after its last "=". The list runs up to a blank line or an earlier total.
 * Lines without a number (a heading) are skipped. Null when there are no numbers.
 */
internal fun totalOf(kind: String, lines: List<String>): String? {
    val values = mutableListOf<Double>()
    for (raw in lines.asReversed()) {
        val line = raw.trim().trimEnd('.')
        if (line.isEmpty()) break
        if (TOTAL_DONE.containsMatchIn(line)) break
        val part = if ('=' in line) line.substringAfterLast('=') else line
        val v = LIST_NUMBER.findAll(part).lastOrNull()?.value?.replace(",", "")?.toDoubleOrNull() ?: continue
        values += v
    }
    if (values.isEmpty()) return null
    val v = when (kind.lowercase()) { "average", "avg", "mean" -> values.average(); else -> values.sum() }
    return formatNumber(v, 10)
}

/** An earlier total line, which ends the list above a new one. */
private val TOTAL_DONE = Regex("""(?i)\b(total|subtotal|sum|average|avg|mean)\s*:?\s*=""")

// ---------- Units ----------

private enum class Dim { LENGTH, MASS, VOLUME, TIME, DATA, SPEED, AREA, TEMPERATURE }

/** Each unit's size in its dimension's base unit (metre, kilogram, litre, second, byte, m/s, m²). */
private val UNITS: Map<String, Pair<Dim, Double>> = buildMap {
    fun add(dim: Dim, size: Double, vararg names: String) = names.forEach { put(it, dim to size) }
    add(Dim.LENGTH, 0.001, "mm", "millimetre", "millimeter")
    add(Dim.LENGTH, 0.01, "cm", "centimetre", "centimeter")
    add(Dim.LENGTH, 1.0, "m", "metre", "meter")
    add(Dim.LENGTH, 1000.0, "km", "kilometre", "kilometer")
    add(Dim.LENGTH, 0.0254, "in", "inch", "inches", "\"")
    add(Dim.LENGTH, 0.3048, "ft", "foot", "feet", "'")
    add(Dim.LENGTH, 0.9144, "yd", "yard")
    add(Dim.LENGTH, 1609.344, "mi", "mile")
    add(Dim.LENGTH, 1852.0, "nmi")
    add(Dim.MASS, 1e-6, "mg")
    add(Dim.MASS, 0.001, "g", "gram", "gramme")
    add(Dim.MASS, 1.0, "kg", "kilo", "kilogram")
    add(Dim.MASS, 1000.0, "t", "tonne")
    add(Dim.MASS, 0.028349523125, "oz", "ounce")
    add(Dim.MASS, 0.45359237, "lb", "lbs", "pound")
    add(Dim.MASS, 6.35029318, "st", "stone")
    add(Dim.VOLUME, 0.001, "ml", "millilitre", "milliliter")
    add(Dim.VOLUME, 0.01, "cl")
    add(Dim.VOLUME, 1.0, "l", "litre", "liter")
    add(Dim.VOLUME, 0.00492892159375, "tsp", "teaspoon")
    add(Dim.VOLUME, 0.01478676478125, "tbsp", "tablespoon")
    add(Dim.VOLUME, 0.0295735295625, "floz")
    add(Dim.VOLUME, 0.2365882365, "cup")
    add(Dim.VOLUME, 0.473176473, "pt", "pint")
    add(Dim.VOLUME, 0.946352946, "qt", "quart")
    add(Dim.VOLUME, 3.785411784, "gal", "gallon")
    add(Dim.TIME, 0.001, "ms")
    add(Dim.TIME, 1.0, "s", "sec", "second")
    add(Dim.TIME, 60.0, "min", "minute")
    add(Dim.TIME, 3600.0, "h", "hr", "hour")
    add(Dim.TIME, 86400.0, "d", "day")
    add(Dim.TIME, 604800.0, "wk", "week")
    add(Dim.TIME, 31557600.0, "yr", "year")
    add(Dim.DATA, 1.0, "b", "byte")
    add(Dim.DATA, 1e3, "kb"); add(Dim.DATA, 1e6, "mb"); add(Dim.DATA, 1e9, "gb"); add(Dim.DATA, 1e12, "tb")
    add(Dim.DATA, 1024.0, "kib"); add(Dim.DATA, 1048576.0, "mib"); add(Dim.DATA, 1073741824.0, "gib"); add(Dim.DATA, 1099511627776.0, "tib")
    add(Dim.SPEED, 1.0, "m/s", "mps")
    add(Dim.SPEED, 1 / 3.6, "km/h", "kmh", "kph")
    add(Dim.SPEED, 0.44704, "mph")
    add(Dim.SPEED, 1852 / 3600.0, "knot", "kn", "kt")
    add(Dim.AREA, 1.0, "m2", "m²", "sqm")
    add(Dim.AREA, 1e-4, "cm2", "cm²")
    add(Dim.AREA, 1e6, "km2", "km²")
    add(Dim.AREA, 1e4, "ha", "hectare")
    add(Dim.AREA, 4046.8564224, "acre")
    add(Dim.AREA, 0.09290304, "ft2", "ft²", "sqft")
    add(Dim.TEMPERATURE, 0.0, "c", "°c", "celsius", "f", "°f", "fahrenheit", "k", "kelvin")
}

private fun unit(word: String): Pair<Dim, Double>? {
    val w = word.lowercase()
    return UNITS[w] ?: w.takeIf { it.length > 3 && it.endsWith("s") }?.let { UNITS[it.dropLast(1)] }
        ?: w.takeIf { it.endsWith("es") }?.let { UNITS[it.dropLast(2)] }
}

/** "5 km in miles", "70kg to lb", "100 °F in °C": the amount, its unit and the unit wanted. */
private val CONVERSION = Regex("""(?i)(.*?[\d)πe])\s*([a-zµ°'"²/]+[a-z0-9²/]*)\s+(?:in|to|as|into)\s+([a-zµ°'"²/]+[a-z0-9²/]*)""")

/** [text] converted to the unit it names ("5 km in miles" → "3.10686 miles"), or null when it isn't a conversion. */
internal fun convert(text: String, scope: Scope = Scope()): String? {
    val m = CONVERSION.matchEntire(text.trim()) ?: return null
    val (amountText, fromWord, toWord) = m.destructured
    val from = unit(fromWord) ?: return null
    val to = unit(toWord) ?: return null
    if (from.first != to.first) return null
    val node = longestSum(amountText, scope) { it.unknowns(scope).isEmpty() } ?: return null
    val amount = node.eval(scope).takeIf { it.isFinite() } ?: return null
    val v = if (from.first == Dim.TEMPERATURE) {
        val kelvin = when (fromWord.lowercase().trimStart('°').first()) { 'c' -> amount + 273.15; 'f' -> (amount - 32) * 5 / 9 + 273.15; else -> amount }
        when (toWord.lowercase().trimStart('°').first()) { 'c' -> kelvin - 273.15; 'f' -> (kelvin - 273.15) * 9 / 5 + 32; else -> kelvin }
    } else amount * from.second / to.second
    return formatNumber(if (abs(v) < 1e-12) 0.0 else v, 6) + " " + toWord
}
