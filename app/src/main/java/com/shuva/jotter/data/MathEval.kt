package com.shuva.jotter.data

import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.abs

/*
 * Math in notes. A line ending in "=" gets its answer ("12 × 4 + 3 =" → 51), an equation with one unknown
 * gets solved ("2x + 3 = 11" → x = 4), and "y = x^2" or "f(x) = sin(x)" can be graphed. Earlier lines can
 * name values ("rent = 1200") and functions ("f(x) = x^2 + 1") that later lines use.
 */

/** What the editor offers for the line the cursor ends. */
sealed interface MathSuggestion {
    /** The answer to a sum ending in "=": typed after it. */
    data class Answer(val text: String) : MathSuggestion

    /** The solutions of an equation: typed after it as " → x = 4". */
    data class Solution(val variable: String, val values: List<String>) : MathSuggestion {
        val text get() = "$variable = " + values.joinToString(" or ")
    }

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
    if ('=' !in line || '→' in line || "==" in line) return null
    val above = text.substring(0, lineStart).lines()
    TOTAL.matchEntire(line)?.let { m -> return totalOf(m.groupValues[1], above.drop(1).dropLast(1))?.let(MathSuggestion::Answer) }
    return suggestionFor(line, mathScope(above))
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
        convert(line.dropLast(1).substringAfterLast('=').trim(), scope)?.let { return MathSuggestion.Answer(it) }
        // "a = 5 + 3 =": the sum is what comes after the earlier "=".
        val body = line.dropLast(1).substringAfterLast('=')
        val node = longestSum(body, scope) { it.unknowns(scope).isEmpty() } ?: return null
        if (node is Num) return null // "5 =" isn't a sum
        val v = node.eval(scope)
        return if (v.isFinite()) MathSuggestion.Answer(formatNumber(v)) else null
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
    val roots = solve { x -> val s = scope.with(name, x); left.eval(s) - right.eval(s) }
    return if (roots.isEmpty()) null else MathSuggestion.Solution(name, roots.map(::formatRoot))
}

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
            if (name.lowercase() in FUNCTIONS || name == "y") return@let
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
 */
private fun proseDefinitions(line: String, scope: Scope): Scope {
    var out = scope
    for (part in line.split(PROSE_SPLIT)) {
        if (part.count { it == '=' } != 1) continue
        val m = PROSE_DEF.find(part) ?: continue
        val name = m.groupValues[1]
        if (name.lowercase() in FUNCTIONS) continue
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

/** [d] as people write it: no trailing zeros, rounded to 12 significant digits (so 0.1 + 0.2 is 0.3). */
internal fun formatNumber(d: Double, digits: Int = 12): String {
    val s = BigDecimal(d).round(MathContext(digits)).stripTrailingZeros().toPlainString()
    return if (s == "-0") "0" else s
}

// A root found by bisection is good to about 10 digits; 1e-12 off zero is zero.
private fun formatRoot(d: Double) = formatNumber(if (abs(d) < 1e-9) 0.0 else d, 10)

/**
 * Where [f] crosses zero, nearest to 0 first (at most 4), searched from -1,000,000 to 1,000,000 with more
 * points near 0. Misses roots where f only touches zero without crossing, unless a point lands on it.
 */
internal fun solve(f: (Double) -> Double): List<Double> {
    val xs = buildList {
        for (k in -4000..4000) add(k / 40.0) // -100..100 in steps of 0.025
        for (k in 1..400) { val big = 100 * Math.pow(10.0, k / 100.0); add(big); add(-big) }
    }.sorted()
    val roots = mutableListOf<Double>()
    var px = xs[0]
    var py = f(px)
    for (x in xs.drop(1)) {
        val y = f(x)
        when {
            y == 0.0 -> roots += x
            py.isFinite() && y.isFinite() && py != 0.0 && (py < 0) != (y < 0) -> {
                var a = px; var b = x; var fa = py
                repeat(200) {
                    val m = (a + b) / 2
                    val fm = f(m)
                    if (fm == 0.0 || !fm.isFinite()) { a = m; b = m; return@repeat }
                    if ((fa < 0) == (fm < 0)) { a = m; fa = fm } else b = m
                }
                val r = (a + b) / 2
                // A jump across an asymptote (tan, 1/x) changes sign too; a real root makes f small.
                val fr = f(r)
                if (fr.isFinite() && abs(fr) < 1e-6 * maxOf(1.0, abs(py), abs(y))) roots += r
            }
        }
        px = x; py = y
    }
    return roots.map { if (abs(it - Math.rint(it)) < 1e-9) Math.rint(it) else it }
        .distinctBy { formatRoot(it) }.sortedBy { abs(it) }.take(4).sorted()
}

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
    "asin" to Math::asin, "acos" to Math::acos, "atan" to Math::atan,
    "arcsin" to Math::asin, "arccos" to Math::acos, "arctan" to Math::atan,
    "sinh" to Math::sinh, "cosh" to Math::cosh, "tanh" to Math::tanh,
    "sqrt" to Math::sqrt, "cbrt" to Math::cbrt, "abs" to Math::abs, "exp" to Math::exp,
    "ln" to Math::log, "log" to Math::log10, "log2" to { x -> Math.log(x) / Math.log(2.0) },
    "floor" to Math::floor, "ceil" to Math::ceil, "round" to { x -> Math.rint(x) },
)

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

private class Unary(val a: Node, val op: (Double) -> Double) : Node {
    override fun eval(s: Scope) = op(a.eval(s))
    override fun unknowns(s: Scope) = a.unknowns(s)
}

private class Binary(val a: Node, val b: Node, val op: (Double, Double) -> Double) : Node {
    override fun eval(s: Scope) = op(a.eval(s), b.eval(s))
    override fun unknowns(s: Scope) = a.unknowns(s) + b.unknowns(s)
}

/** A function named in an earlier line ("f(x) = x^2"), looked up when evaluated. */
private class Call(val name: String, val arg: Node) : Node {
    override fun eval(s: Scope): Double {
        val (param, body) = s.function(name) ?: return Double.NaN
        return body.eval(s.with(param, arg.eval(s)))
    }
    override fun unknowns(s: Scope) = if (s.function(name) == null) setOf(name) + arg.unknowns(s) else arg.unknowns(s)
}

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

/** Splits [s] into numbers, names and symbols. "5x3" multiplies; "2x" is 2 times x. */
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
                out += Tok.N(sb.toString().toDoubleOrNull() ?: throw Fail())
            }
            c.isLetter() -> {
                val start = i
                while (i < s.length && s[i].isLetter()) i++
                // log2 is a name with a digit in it.
                if (s.substring(start, i) == "log" && s.getOrNull(i) == '2') i++
                val word = s.substring(start, i)
                // "5x3": x between numbers is times.
                val prev = out.lastOrNull()
                val next = s.substring(i).trimStart().firstOrNull()
                if ((word == "x" || word == "X") && (prev is Tok.N || prev == Tok.Op(')')) && next != null && next.isDigit()) out += Tok.Op('*')
                else out += Tok.Id(word)
            }
            else -> {
                out += Tok.Op(
                    when (c) {
                        '−', '–' -> '-'
                        '×', '·', '⋅' -> '*'
                        '÷' -> '/'
                        '²' -> { out += Tok.Op('^'); out += Tok.N(2.0); i++; continue }
                        '³' -> { out += Tok.Op('^'); out += Tok.N(3.0); i++; continue }
                        in "+-*/^%!()√°|" -> c
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
 * + − × ÷ ^ % ! √ °, brackets, |abs|, functions (sin, log, ...), constants (π, e), names from earlier lines and
 * multiplying by writing side by side ("2x", "3(4 + 1)", "2sin(x)"). Angles are radians unless marked with °.
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
            val left = v
            // "a + 15%" adds 15 percent of a, as on a calculator.
            v = when {
                r is Percent && plus -> Binary(left, r) { a, b -> a + a * b }
                r is Percent -> Binary(left, r) { a, b -> a - a * b }
                plus -> Binary(left, r, Double::plus)
                else -> Binary(left, r, Double::minus)
            }
        }
        return v
    }

    private fun term(): Node {
        var v = unary()
        while (true) {
            v = when {
                isOp('*') -> { i++; Binary(v, unary(), Double::times) }
                isOp('/') -> { i++; Binary(v, unary(), Double::div) }
                // Side by side: 2x, 2(3), 2√9, 2sin(x), (a)(b). Not 2 3, and not after a closing "|".
                isOp('(') || isOp('√') || peek() is Tok.Id -> Binary(v, power(), Double::times)
                else -> return v
            }
        }
    }

    private fun unary(): Node = when {
        isOp('-') -> { i++; Unary(unary()) { -it } }
        isOp('+') -> { i++; unary() }
        else -> power()
    }

    private fun power(): Node {
        val base = postfix()
        if (!isOp('^')) return base
        i++
        val e = unary()
        return Binary(base, e) { a, b -> Math.pow(a, b) }
    }

    private fun postfix(): Node {
        var v = primary()
        while (true) {
            v = when {
                isOp('%') -> { i++; Percent(v) }
                isOp('!') -> { i++; Unary(v, ::factorial) }
                isOp('°') -> { i++; Unary(v, Math::toRadians) }
                else -> return v
            }
        }
    }

    private fun primary(): Node {
        val tok = peek() ?: throw Fail()
        i++
        return when {
            tok is Tok.N -> Num(tok.v)
            tok == Tok.Op('(') -> expr().also { if (!isOp(')')) throw Fail(); i++ }
            tok == Tok.Op('|') -> expr().also { if (!isOp('|')) throw Fail(); i++ }.let { Unary(it, Math::abs) }
            tok == Tok.Op('√') -> Unary(postfix(), Math::sqrt)
            tok is Tok.Id -> {
                val fn = FUNCTIONS[tok.s.lowercase()]
                when {
                    // sin(x), or sin x / sin 30° without brackets.
                    fn != null -> Unary(if (isOp('(')) primary() else power(), fn)
                    isOp('(') -> { i++; val a = expr(); if (!isOp(')')) throw Fail(); i++; CallOrTimes(tok.s, a) }
                    else -> Name(tok.s)
                }
            }
            else -> throw Fail()
        }
    }
}

/** "f(3)": calls f when an earlier line defines it, otherwise multiplies (so "a(b + 1)" still works). */
private class CallOrTimes(val name: String, val arg: Node) : Node {
    override fun eval(s: Scope): Double = if (s.function(name) != null) Call(name, arg).eval(s) else Name(name).eval(s) * arg.eval(s)
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
