package com.shuva.jotter.data

import java.util.Locale

/**
 * A tag is '#' then letters, digits, '_' or '-', with at least one letter, not glued to a word before
 * it ("a#b" and "#1" are not tags). Tags are derived from note text, so they need no storage and
 * sync with the note itself.
 */
private val TAG = Regex("""(?<![\p{L}\p{N}_#&])#([\p{L}\p{N}_-]*\p{L}[\p{L}\p{N}_-]*)""")

/** Case-insensitive identity of a tag: "#Work" and "#work" are the same tag. */
fun tagKey(name: String) = name.lowercase(Locale.ROOT)

/** Each match's range (including '#') and name (without it). */
fun findTags(text: String): List<Pair<IntRange, String>> =
    TAG.findAll(text).map { m ->
        val name = m.groupValues[1].trimEnd('-')
        m.range.first..(m.range.first + name.length) to name
    }.toList()

val NoteEntity.tagKeys: Set<String>
    get() = findTags(blocks.joinToString("\n") { it.text }).map { tagKey(it.second) }.toSet()

data class TagCount(val key: String, val name: String, val count: Int)

/** All tags in these notes with how many notes use each, A to Z. A tag shows as it was first written. */
fun tagCounts(notes: List<NoteEntity>): List<TagCount> {
    val names = mutableMapOf<String, String>()
    val counts = mutableMapOf<String, Int>()
    notes.sortedBy { it.createdAt }.forEach { note ->
        findTags(note.blocks.joinToString("\n") { it.text })
            .distinctBy { tagKey(it.second) }
            .forEach { (_, name) ->
                val key = tagKey(name)
                names.putIfAbsent(key, name)
                counts[key] = (counts[key] ?: 0) + 1
            }
    }
    return names.map { (key, name) -> TagCount(key, name, counts.getValue(key)) }.sortedBy { it.key }
}

private fun isTagChar(c: Char) = c.isLetterOrDigit() || c == '_' || c == '-'

/** True if [name] (without '#') is a whole valid tag on its own. */
fun isValidTagName(name: String) = findTags("#$name").singleOrNull()?.second == name

/**
 * The tag being typed at [cursor]: the offset of its first character after '#', and what's typed so far
 * (possibly empty). Null when the cursor isn't right after "#" plus tag characters.
 */
fun tagPrefixAt(text: String, cursor: Int): Pair<Int, String>? {
    if (cursor !in 0..text.length) return null
    var start = cursor
    while (start > 0 && isTagChar(text[start - 1])) start--
    val hash = start - 1
    if (hash < 0 || text[hash] != '#') return null
    if (hash > 0 && (isTagChar(text[hash - 1]) || text[hash - 1] == '#' || text[hash - 1] == '&')) return null
    return start to text.substring(start, cursor)
}

/** Replaces every use of the tag [key] in the line with "#[newName]". */
fun replaceTag(block: Block, key: String, newName: String): Block =
    block.replacing(findTags(block.text).filter { tagKey(it.second) == key }.map { it.first to "#$newName" })
