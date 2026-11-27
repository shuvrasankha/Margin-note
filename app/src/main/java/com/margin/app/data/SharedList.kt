package com.margin.app.data

/**
 * A checklist someone shared as text: Margin's own "[ ] Milk" lines (Share → Text), or the "☐ / ✓" and
 * "- [ ]" styles other apps use. Shared back into Margin, it updates the list with the same name.
 */
data class SharedList(val title: String, val items: List<Item>) {
    data class Item(val text: String, val checked: Boolean)
}

/** What merging a shared list changed. */
data class ListMerge(val blocks: List<Block>, val added: Int, val ticked: Int) {
    val changed get() = added > 0 || ticked > 0
}

private val CHECK_LINE = Regex("""^\s*(?:[-*•]\s+)?(?:\[( |x|X|✓)]|(☐|□|◻|⬜)|(☑|✅|✓|✔|☒))\s*(.*\S)\s*$""")

/** A checklist line's text and whether it's ticked; null for any other line. */
fun checkLine(line: String): SharedList.Item? {
    val m = CHECK_LINE.matchEntire(line) ?: return null
    val (box, empty, ticked, text) = m.destructured
    val checked = ticked.isNotEmpty() || (empty.isEmpty() && box.isNotBlank())
    return SharedList.Item(text, checked)
}

/**
 * Reads shared text as a checklist: a title (the [subject], else the first line that isn't a task) and at
 * least one task line. Null if it has no tasks, or more ordinary lines than tasks (a note that happens to hold a task).
 */
fun parseSharedList(subject: String?, text: String?): SharedList? {
    val lines = text.orEmpty().replace("\r\n", "\n").split('\n').filter { it.isNotBlank() }
    val items = lines.mapNotNull(::checkLine)
    if (items.isEmpty()) return null
    val others = lines.filter { checkLine(it) == null }
    val title = subject?.trim()?.takeIf { it.isNotEmpty() } ?: others.firstOrNull()?.trim().orEmpty()
    val extra = others.size - (if (subject.isNullOrBlank() && others.isNotEmpty()) 1 else 0)
    if (extra > items.size) return null
    return SharedList(title.take(200), items)
}

private fun key(text: String) = text.trim().lowercase().replace(Regex("\\s+"), " ")

/**
 * Brings a shared copy of a list into [blocks]: tasks it doesn't have yet are added (as the sender had them),
 * and tasks the sender ticked off are ticked here too. Nothing is unticked or removed: a shared list only adds news.
 */
fun mergeSharedList(blocks: List<Block>, list: SharedList): ListMerge {
    val out = blocks.toMutableList()
    var added = 0
    var ticked = 0
    val have = blocks.filter { it.type == BlockType.CHECK }.map { key(it.text) }.toMutableSet()
    for (item in list.items) {
        val k = key(item.text)
        if (k !in have) {
            // After the list's last task, or at the end when it has none yet.
            val last = out.indexOfLast { it.type == BlockType.CHECK }
            val task = Block(type = BlockType.CHECK, text = item.text, checked = item.checked)
            if (last >= 0) out.add(last + 1, task) else {
                if (out.size > 1 && out.last().type == BlockType.BODY && out.last().text.isBlank() && out.last().attachment == null) out.removeAt(out.lastIndex)
                out.add(task)
            }
            have += k
            added++
        } else if (item.checked) {
            val i = out.indexOfFirst { it.type == BlockType.CHECK && !it.checked && key(it.text) == k }
            if (i >= 0) { out[i] = out[i].copy(checked = true); ticked++ }
        }
    }
    return ListMerge(out, added, ticked)
}

/** A shared checklist as a new note: the title, then its tasks. */
fun sharedListBlocks(list: SharedList): List<Block> =
    listOf(Block(type = BlockType.TITLE, text = list.title)) + list.items.map { Block(type = BlockType.CHECK, text = it.text, checked = it.checked) }
