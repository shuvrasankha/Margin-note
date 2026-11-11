package com.shuva.jotter.data

import java.util.Locale

/**
 * A link to another note is its title in double brackets, "[[Shopping list]]", stored in the note text
 * like a #tag. It needs no extra storage and syncs, searches and shares with the text.
 */
private val LINK = Regex("""\[\[([^\[\]\n]+)]]""")

/** Case-insensitive identity of a link target: "[[Plan]]" and "[[plan ]]" point at the same note. */
fun linkKey(title: String) = title.trim().lowercase(Locale.ROOT)

/** Each link's range (including the brackets) and the title inside. Blank titles aren't links. */
fun findLinks(text: String): List<Pair<IntRange, String>> =
    LINK.findAll(text).mapNotNull { m -> m.groupValues[1].trim().takeIf { it.isNotEmpty() }?.let { m.range to it } }.toList()

/**
 * While typing a link: the position just after an unclosed "[[" before [cursor] on the same line, and
 * what has been typed since. Null when the cursor isn't inside an unfinished link.
 */
fun linkPrefixAt(text: String, cursor: Int): Pair<Int, String>? {
    if (cursor !in 0..text.length) return null
    val open = text.lastIndexOf("[[", cursor - 2).takeIf { it >= 0 } ?: return null
    val typed = text.substring(open + 2, cursor)
    if (typed.any { it == '\n' || it == '[' || it == ']' }) return null
    return open + 2 to typed
}

/** The note a link points at: a live note with that title (ignoring case), the most recently edited if several. */
fun resolveLink(title: String, notes: List<NoteEntity>): NoteEntity? {
    val key = linkKey(title)
    return notes.filter { !it.deleted && it.trashedAt == null && linkKey(it.title) == key }.maxByOrNull { it.lastEdited }
}

/** Replaces every link to [oldTitle] in the line with a link to [newTitle]. */
fun replaceLinks(block: Block, oldTitle: String, newTitle: String): Block {
    val key = linkKey(oldTitle)
    return block.replacing(findLinks(block.text).filter { linkKey(it.second) == key }.map { it.first to "[[${newTitle.trim()}]]" })
}

/** Live notes other than [noteId] that link to [title], newest edit first. Locked bodies can't be read, so only their titles count. */
fun backlinks(title: String, noteId: String, notes: List<NoteEntity>): List<NoteEntity> {
    if (title.isBlank()) return emptyList()
    val key = linkKey(title)
    return notes.filter { n ->
        n.id != noteId && !n.deleted && n.trashedAt == null && n.blocks.any { b -> findLinks(b.text).any { linkKey(it.second) == key } }
    }.sortedByDescending { it.lastEdited }
}
