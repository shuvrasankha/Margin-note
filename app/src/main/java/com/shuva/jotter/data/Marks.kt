package com.shuva.jotter.data

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/** Character styles inside a line. */
@Serializable
enum class Mark { BOLD, ITALIC, UNDERLINE, HIGHLIGHT }

/** Highlight colours. */
@Serializable
enum class Tint { AMBER, BLUE, GREEN, GREY }

/**
 * [mark] on characters [start] until [end] of a line (or of the whole note in the editor). [tint] is a
 * highlight's colour; null is amber, and the key is left out of the JSON then, so older versions read it as before.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class MarkSpan(
    val start: Int,
    val end: Int,
    val mark: Mark,
    // shortcut: a Tint added later would fail to decode on older versions (enum), add values only with a version check.
    @EncodeDefault(EncodeDefault.Mode.NEVER) val tint: Tint? = null,
)

val MarkSpan.color get() = tint ?: Tint.AMBER

/**
 * This span after the text [p] until [oldEnd] was replaced by [inserted] characters. Text typed right at
 * a span's end joins it; text typed at its start doesn't. Null when nothing of the span is left.
 */
fun MarkSpan.shifted(p: Int, oldEnd: Int, inserted: Int): MarkSpan? {
    fun map(x: Int) = when {
        x < p -> x
        x >= oldEnd -> x + inserted - (oldEnd - p)
        else -> p // inside the replaced text
    }
    val s = map(start)
    val e = map(end)
    return if (s < e) copy(start = s, end = e) else null
}

/** Spans from storage or sync, kept inside a text of [length] characters. */
fun List<MarkSpan>.clampedTo(length: Int) =
    mapNotNull { m -> m.copy(start = m.start.coerceIn(0, length), end = m.end.coerceIn(0, length)).takeIf { it.start < it.end } }

/** Replaces each range with its text (ranges in order, not overlapping); the styles move with the text. */
fun Block.replacing(replacements: List<Pair<IntRange, String>>): Block =
    replacements.asReversed().fold(this) { b, (range, with) ->
        b.copy(
            text = b.text.replaceRange(range, with),
            marks = b.marks.mapNotNull { it.shifted(range.first, range.last + 1, with.length) },
        )
    }
