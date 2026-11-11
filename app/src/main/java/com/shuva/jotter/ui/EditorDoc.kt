package com.shuva.jotter.ui

import com.shuva.jotter.data.ATTACHMENT_CHAR
import com.shuva.jotter.data.Attachment
import com.shuva.jotter.data.Block
import com.shuva.jotter.data.NoteTable
import com.shuva.jotter.data.isObject
import com.shuva.jotter.data.replacing
import com.shuva.jotter.data.BlockType
import com.shuva.jotter.data.MAX_INDENT
import com.shuva.jotter.data.Tint
import com.shuva.jotter.data.color
import com.shuva.jotter.data.Mark
import com.shuva.jotter.data.MarkSpan
import com.shuva.jotter.data.clampedTo
import com.shuva.jotter.data.shifted
import com.shuva.jotter.data.newId
import com.shuva.jotter.data.sortCheckedRun

/** Style of one line. Line 0 is always the title. */
data class LineMeta(
    val id: String,
    val type: BlockType,
    val checked: Boolean = false,
    val attachment: Attachment? = null,
    val quote: Boolean = false,
    val indent: Int = 0,
    val table: NoteTable? = null,
) {
    /** A line that is one card (attachment or table) rather than text. */
    val isObject get() = attachment != null || table != null
}

/** Styles that carry over to the next line on Enter; Enter on an empty one ends the style. */
val CONTINUING_TYPES = setOf(BlockType.BULLET, BlockType.DASHED, BlockType.NUMBERED, BlockType.CHECK, BlockType.MONOSPACED)

fun nextType(type: BlockType) = if (type in CONTINUING_TYPES) type else BlockType.BODY

/**
 * The whole note as one string (lines joined by '\n') plus one [LineMeta] per line. Editing happens
 * in a single text field, so Enter never moves focus between fields and no keystroke can be lost.
 * Invariant: lines.size == number of lines in [text].
 */
data class EditorDoc(val text: String, val lines: List<LineMeta>, val marks: List<MarkSpan> = emptyList()) {
    val texts: List<String> get() = text.split('\n')

    /** Each line with the part of every style span that falls on it. */
    fun toBlocks(): List<Block> {
        var start = 0
        return texts.zip(lines) { t, m ->
            val end = start + t.length
            val lineMarks = marks.mapNotNull { s ->
                s.copy(start = maxOf(s.start, start) - start, end = minOf(s.end, end) - start).takeIf { it.start < it.end }
            }
            start = end + 1
            Block(m.id, m.type, t, m.checked, lineMarks, m.attachment, m.quote, m.indent, m.table)
        }
    }

    /** True when every selected character has [mark] (in [tint], when given). */
    fun hasMark(mark: Mark, from: Int, to: Int, tint: Tint? = null) =
        from < to && (from until to).all { i ->
            marks.any { it.mark == mark && i >= it.start && i < it.end && (tint == null || it.color == tint) }
        }

    /**
     * Toolbar: adds [mark] to the selection, or removes it when the whole selection already has it. A
     * highlight takes [tint]; selected text highlighted in another colour is recoloured.
     */
    fun withMark(mark: Mark, from: Int, to: Int, tint: Tint = Tint.AMBER): EditorDoc {
        if (from >= to) return this
        val tinted = mark == Mark.HIGHLIGHT
        val on = arrayOfNulls<Tint>(text.length) // non-null: the character has the mark, in that colour
        marks.filter { it.mark == mark }.forEach { for (i in it.start until minOf(it.end, on.size)) on[i] = it.color }
        val add = !hasMark(mark, from, to, if (tinted) tint else null)
        for (i in from until minOf(to, on.size)) on[i] = if (add) tint else null
        val runs = mutableListOf<MarkSpan>()
        var i = 0
        while (i < on.size) {
            val c = on[i]
            if (c == null) { i++; continue }
            val runStart = i
            while (i < on.size && on[i] == c) i++
            runs += MarkSpan(runStart, i, mark, c.takeIf { tinted && it != Tint.AMBER })
        }
        return copy(marks = marks.filter { it.mark != mark } + runs)
    }

    fun lineAt(offset: Int): Int = text.substring(0, offset.coerceIn(0, text.length)).count { it == '\n' }

    fun lineStart(line: Int): Int {
        var index = 0
        repeat(line) { index = text.indexOf('\n', index) + 1 }
        return index
    }

    /** Line 0 is the title; nothing else is; only checklist lines can be checked. */
    fun normalized() = copy(
        lines = lines.mapIndexed { i, m ->
            when {
                i == 0 -> m.copy(type = BlockType.TITLE, checked = false, quote = false, indent = 0)
                m.isObject -> m.copy(type = BlockType.BODY, checked = false, quote = false, indent = 0)
                m.type == BlockType.TITLE -> m.copy(type = BlockType.BODY)
                m.type != BlockType.CHECK && m.checked -> m.copy(checked = false)
                else -> m
            }.let { if (it.indent in 0..MAX_INDENT) it else it.copy(indent = it.indent.coerceIn(0, MAX_INDENT)) }
        },
    )

    /**
     * Maps a text change from the field onto line styles. [cursor] is the new caret position; it
     * anchors the diff so repeated characters (like several newlines in a row) resolve correctly.
     */
    fun applyEdit(newText: String, cursor: Int, newId: () -> String = ::newId): EditorDoc {
        if (newText == text) return this
        val old = text
        val maxSuffix = (newText.length - cursor.coerceIn(0, newText.length))
        var s = 0
        while (s < old.length && s < maxSuffix && old[old.length - 1 - s] == newText[newText.length - 1 - s]) s++
        var p = 0
        val maxPrefix = minOf(old.length, newText.length) - s
        while (p < maxPrefix && old[p] == newText[p]) p++

        val removedText = old.substring(p, old.length - s)
        val insertedText = newText.substring(p, newText.length - s)
        val line = old.substring(0, p).count { it == '\n' }
        val removed = removedText.count { it == '\n' }
        val added = insertedText.count { it == '\n' }
        val current = lines[line]
        val currentText = texts[line]

        // Enter on an empty list, monospaced or quote line ends that style instead of adding a line.
        if (removedText.isEmpty() && insertedText == "\n" && currentText.isEmpty() && (current.type in CONTINUING_TYPES || current.quote)) {
            return copy(lines = lines.toMutableList().apply { this[line] = current.copy(type = BlockType.BODY, checked = false, quote = false) })
        }

        val newLines = if (removed == 0 && added > 0 && insertedText.all { it == '\n' } &&
            p == lineStart(line) && currentText.isNotEmpty()
        ) {
            // Enter at the very start of a line: new empty line(s) go above; this line keeps its style.
            val type = if (current.type in CONTINUING_TYPES) current.type else BlockType.BODY
            lines.subList(0, line) + List(added) { LineMeta(newId(), type, quote = current.quote, indent = current.indent) } + lines.subList(line, lines.size)
        } else {
            lines.subList(0, line + 1) +
                List(added) { LineMeta(newId(), nextType(current.type), quote = current.quote, indent = current.indent) } +
                lines.subList(line + 1 + removed, lines.size)
        }
        val newMarks = marks.mapNotNull { it.shifted(p, old.length - s, insertedText.length) }
        var result = EditorDoc(newText, newLines, newMarks).normalized()
        if (ATTACHMENT_CHAR in newText || ATTACHMENT_CHAR in old) result = result.withAttachmentsOnOwnLines()
        // Safety net: never let styles and lines drift apart.
        return if (result.lines.size == result.texts.size) result else fromBlocks(result.texts.map { Block(text = it) })
    }

    /**
     * After an edit: an attachment keeps a line of its own. Text typed next to it moves to a line before or
     * after; an attachment whose placeholder was deleted is gone, and a stray placeholder (from joining
     * lines) is dropped, which removes that attachment. The title can't hold one.
     */
    fun withAttachmentsOnOwnLines(): EditorDoc {
        val marker = ATTACHMENT_CHAR.toString()
        if (lines.none { it.isObject } && marker !in text) return this
        val out = toBlocks().flatMapIndexed { i, b ->
            val k = b.text.indexOf(ATTACHMENT_CHAR)
            when {
                !b.isObject || i == 0 || k < 0 ->
                    listOf(b.replacing(Regex(marker).findAll(b.text).map { it.range to "" }.toList()).copy(attachment = null, table = null))
                b.text == marker -> listOf(b)
                else -> {
                    val before = b.text.substring(0, k)
                    val after = b.text.substring(k + 1)
                    val afterMarks = b.marks.map { it.copy(start = it.start - k - 1, end = it.end - k - 1) }.clampedTo(after.length)
                    listOfNotNull(
                        before.takeIf { it.isNotEmpty() }?.let { Block(type = BlockType.BODY, text = it, marks = b.marks.clampedTo(k)) },
                        b.copy(text = marker, marks = emptyList()),
                        after.takeIf { it.isNotEmpty() }?.let { Block(type = BlockType.BODY, text = it, marks = afterMarks) },
                    )
                }
            }
        }
        return fromBlocks(out)
    }

    /** Toolbar: style the selected lines (not the title). Applying a style they all have resets them to body. */
    fun withType(type: BlockType, fromLine: Int, toLine: Int): EditorDoc {
        val range = maxOf(fromLine, 1)..minOf(toLine, lines.lastIndex)
        if (range.isEmpty()) return this
        val newType = if (range.all { lines[it].type == type }) BlockType.BODY else type
        return copy(lines = lines.mapIndexed { i, m -> if (i in range) m.copy(type = newType) else m }).normalized()
    }

    /** Format panel: quote the selected lines (not the title or attachments), or unquote them when all are quoted. */
    fun withQuote(fromLine: Int, toLine: Int): EditorDoc {
        val range = (maxOf(fromLine, 1)..minOf(toLine, lines.lastIndex)).filter { !lines[it].isObject }
        if (range.isEmpty()) return this
        val on = !range.all { lines[it].quote }
        return copy(lines = lines.mapIndexed { i, m -> if (i in range) m.copy(quote = on) else m })
    }

    /** Format panel: indent the selected lines one step more ([by] = 1) or less (-1), between 0 and [MAX_INDENT]. */
    fun withIndent(fromLine: Int, toLine: Int, by: Int): EditorDoc =
        copy(lines = lines.mapIndexed { i, m -> if (i in fromLine..toLine) m.copy(indent = (m.indent + by).coerceIn(0, MAX_INDENT)) else m })
            .normalized()

    /** Toggle a checklist line; with [autoSort], checked lines sink below unchecked ones in that checklist. */
    fun toggleChecked(line: Int, autoSort: Boolean): EditorDoc {
        if (line !in lines.indices || lines[line].type != BlockType.CHECK) return this
        // Reordering moves whole lines, so go through blocks: each line takes its styles with it.
        val blocks = toBlocks().toMutableList()
        blocks[line] = blocks[line].copy(checked = !blocks[line].checked)
        val result = if (autoSort) blocks.sortCheckedRun(line, isCheck = { it.type == BlockType.CHECK }, isChecked = { it.checked }) else blocks
        return fromBlocks(result)
    }

    /** Smart checklists: a body line typed as "[ ] text" (or "[] ", "[x] ") becomes a checklist line without the brackets. */
    fun withChecklistShortcut(line: Int): EditorDoc {
        val meta = lines.getOrNull(line) ?: return this
        if (line == 0 || meta.type != BlockType.BODY || meta.isObject) return this
        val t = texts[line]
        val prefix = CHECK_PREFIXES.firstOrNull { t.startsWith(it, ignoreCase = true) } ?: return this
        val start = lineStart(line)
        return EditorDoc(
            text.removeRange(start, start + prefix.length),
            lines.toMutableList().apply { this[line] = meta.copy(type = BlockType.CHECK, checked = prefix == "[x] ") },
            marks.mapNotNull { it.shifted(start, start + prefix.length, 0) },
        )
    }

    /**
     * Automatic lists: "- " just typed at the start of a body line makes it a dashed list line, "* " a bulleted one
     * and "1. " (any number, or "1) ") a numbered one. Only right after the space, so editing old text never converts it.
     */
    fun withListShortcut(line: Int, cursor: Int): EditorDoc {
        val meta = lines.getOrNull(line) ?: return this
        if (line == 0 || meta.type != BlockType.BODY || meta.isObject) return this
        val start = lineStart(line)
        if (cursor !in start + 2..start + texts[line].length) return this
        val typed = text.substring(start, cursor)
        val type = when {
            typed == "- " -> BlockType.DASHED
            typed == "* " || typed == "• " -> BlockType.BULLET
            LIST_NUMBER.matches(typed) -> BlockType.NUMBERED
            else -> return this
        }
        return EditorDoc(
            text.removeRange(start, cursor),
            lines.toMutableList().apply { this[line] = meta.copy(type = type) },
            marks.mapNotNull { it.shifted(start, cursor, 0) },
        )
    }

    companion object {
        private val CHECK_PREFIXES = listOf("[ ] ", "[] ", "[x] ")
        private val LIST_NUMBER = Regex("""\d{1,3}[.)] """)

        fun fromBlocks(blocks: List<Block>): EditorDoc {
            val list = blocks.ifEmpty { listOf(Block(type = BlockType.TITLE)) }
            var offset = 0
            val marks = list.flatMap { b ->
                b.marks.clampedTo(b.text.length).map { it.copy(start = it.start + offset, end = it.end + offset) }
                    .also { offset += b.text.length + 1 }
            }
            return EditorDoc(
                list.joinToString("\n") { it.text.replace('\n', ' ') },
                list.map { LineMeta(it.id, it.type, it.checked, it.attachment, it.quote, it.indent, it.table) },
                marks,
            ).normalized()
        }
    }
}

/**
 * Where the cursor goes when the doc rewrote what was typed ([typed] became [result]; [cursor] is in [typed]).
 * Nothing before the cursor changed (text typed in front of an attachment, split onto its own line at the
 * cursor): it stays put. Otherwise (Enter on an empty list line, text typed after an attachment) the change is
 * before it, so it keeps its distance from the end.
 */
fun cursorAfterRewrite(typed: String, cursor: Int, result: String): Int =
    if (cursor <= result.length && result.regionMatches(0, typed, 0, cursor)) cursor
    else (cursor + result.length - typed.length).coerceIn(0, result.length)
