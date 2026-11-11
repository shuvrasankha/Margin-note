package com.shuva.jotter.data

/**
 * A note as plain text for sharing: the title, then each line with a simple marker for list lines
 * ("• ", "- ", "1. ", "[ ] " / "[x] "), two spaces per indent step and "> " for a quote. Numbering restarts
 * after any non-numbered line.
 */
fun shareText(blocks: List<Block>): String {
    var number = 0
    return blocks.joinToString("\n") { b ->
        if (b.type == BlockType.NUMBERED) number++ else number = 0
        "  ".repeat(b.indent.coerceIn(0, MAX_INDENT)) + (if (b.quote) "> " else "") + when (b.type) {
            BlockType.BULLET -> "• "
            BlockType.DASHED -> "- "
            BlockType.NUMBERED -> "$number. "
            BlockType.CHECK -> if (b.checked) "[x] " else "[ ] "
            else -> ""
        } + (b.attachment?.let { "[${it.name}]" } ?: b.table?.plainText ?: b.text)
    }.trimEnd()
}
