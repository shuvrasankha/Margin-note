package com.shuva.jotter.data

import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private val LIST_TYPES = setOf(BlockType.BULLET, BlockType.DASHED, BlockType.NUMBERED, BlockType.CHECK)

/**
 * A note as Markdown. [attachmentRef] gives a relative path for an attachment's file (null: it's shown as a
 * placeholder); [linkUrl] reads a web link's address.
 */
fun noteMarkdown(
    blocks: List<Block>,
    attachmentRef: (Attachment) -> String? = { null },
    linkUrl: (Attachment) -> String? = { null },
): String {
    // Each line as (group, text). Lines of one list, quote or code group are joined by one newline, others by a blank line.
    var number = 0
    val items = blocks.mapIndexedNotNull { i, b ->
        if (b.type == BlockType.NUMBERED) number++ else number = 0
        val a = b.attachment
        val code = b.type == BlockType.MONOSPACED && a == null
        if (!code && a == null && b.text.isBlank()) return@mapIndexedNotNull null
        val body = when {
            b.table != null -> b.table.markdown()
            a != null -> {
                val name = escapeInline(a.name)
                when {
                    a.isLink -> linkUrl(a)?.let { "[$name](${it.replace(")", "%29").replace(" ", "%20")})" } ?: "*$name*"
                    a.isDrawing -> "*[Drawing]*"
                    else -> attachmentRef(a)?.let { if (a.isImage) "![$name]($it)" else "[$name]($it)" } ?: "*[${name}]*"
                }
            }
            code -> b.text
            i == 0 -> "# " + inlineMarkdown(b.text, b.marks)
            else -> {
                val text = inlineMarkdown(b.text, b.marks)
                val marker = when (b.type) {
                    BlockType.HEADING -> "## "
                    BlockType.SUBHEADING -> "### "
                    BlockType.BULLET, BlockType.DASHED -> "- "
                    BlockType.NUMBERED -> "$number. "
                    BlockType.CHECK -> if (b.checked) "- [x] " else "- [ ] "
                    else -> ""
                }
                // A body line that starts like Markdown syntax ("#tag", "- ", "1. ") stays plain text.
                val safe = when {
                    marker.isNotEmpty() -> text
                    Regex("^[#>+\\-]").containsMatchIn(text) -> "\\$text"
                    else -> text.replaceFirst(Regex("^(\\d+)([.)])"), "$1\\\\$2") // "2. x" -> "2\\. x": only punctuation can be escaped
                }
                (if (b.type in LIST_TYPES) "  ".repeat(b.indent) else "") + marker + safe
            }
        }
        val group = when {
            code -> "code"
            b.quote -> "quote"
            a == null && b.type in LIST_TYPES -> "list"
            else -> null
        }
        group to (if (b.quote && !code) "> $body" else body)
    }
    val out = StringBuilder()
    var prev: String? = null
    items.forEachIndexed { i, (group, text) ->
        val same = group != null && group == prev
        if (prev == "code" && !same) out.append("\n```")
        if (i > 0) out.append(if (same) "\n" else "\n\n")
        if (group == "code" && !same) out.append("```\n")
        out.append(text)
        prev = group
    }
    if (prev == "code") out.append("\n```")
    return out.toString()
}

private fun escapeInline(s: String) = s.replace(Regex("([\\\\*_`\\[\\]])"), "\\\\$1")

/**
 * Text with bold as **, italic as *, underline as <u> and highlight as <mark>. Open styles form a stack: at each
 * change only the styles that end (and any opened inside them) are closed, so the result is always nested.
 */
// shortcut: partly overlapping bold and italic close and reopen as "****", and a style starting or ending on a space shows its stars; fine for most notes.
fun inlineMarkdown(text: String, marks: List<MarkSpan>): String {
    if (marks.isEmpty()) return escapeInline(text)
    val order = listOf(Mark.BOLD, Mark.ITALIC, Mark.UNDERLINE, Mark.HIGHLIGHT)
    fun open(m: Mark) = when (m) { Mark.BOLD -> "**"; Mark.ITALIC -> "*"; Mark.UNDERLINE -> "<u>"; Mark.HIGHLIGHT -> "<mark>" }
    fun close(m: Mark) = when (m) { Mark.BOLD -> "**"; Mark.ITALIC -> "*"; Mark.UNDERLINE -> "</u>"; Mark.HIGHLIGHT -> "</mark>" }
    val out = StringBuilder()
    val stack = mutableListOf<Mark>()
    for (i in 0..text.length) {
        val active = if (i == text.length) emptySet() else marks.filter { i >= it.start && i < it.end }.map { it.mark }.toSet()
        val keep = stack.indexOfFirst { it !in active }.let { if (it < 0) stack.size else it }
        while (stack.size > keep) out.append(close(stack.removeAt(stack.lastIndex)))
        order.filter { it in active && it !in stack }.forEach { out.append(open(it)); stack += it }
        if (i < text.length) out.append(escapeInline(text[i].toString()))
    }
    return out.toString()
}

/** A name that's safe as a file or folder name everywhere: no path or reserved characters, not empty. */
fun safeFileName(name: String): String =
    name.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"), "_").trim().trimEnd('.').take(80).trim().ifEmpty { "Untitled" }

/**
 * Where each note goes in an export: "Folder/Sub folder/Title.md". Notes whose folder is gone go in the default
 * folder; equal names (ignoring case) get " 2", " 3", ...
 */
fun exportPaths(notes: List<NoteEntity>, folders: List<FolderEntity>): Map<String, String> {
    val byId = folders.filter { !it.deleted }.associateBy { it.id }
    fun folderPath(id: String): String {
        val parts = mutableListOf<String>()
        var f = byId[id] ?: byId[DEFAULT_FOLDER_ID]
        val seen = mutableSetOf<String>()
        while (f != null && seen.add(f.id)) {
            parts += safeFileName(f.name)
            f = f.parentId?.let(byId::get)
        }
        return parts.asReversed().joinToString("/").ifEmpty { DEFAULT_FOLDER_NAME }
    }
    val taken = mutableSetOf<String>()
    return notes.sortedBy { it.createdAt }.associate { n ->
        val dir = folderPath(n.folderId)
        val base = safeFileName(n.displayTitle)
        var name = "$dir/$base.md"
        var k = 2
        while (!taken.add(name.lowercase())) name = "$dir/$base ${k++}.md"
        n.id to name
    }
}

/**
 * Writes every live note (not Recently Deleted) as Markdown into a zip, with the files they use in
 * "attachments/". A locked note exports as its title and a line saying it's locked: its text stays encrypted.
 * [fileOf] finds an attachment's file (null or missing: shown as a placeholder). Returns the number of notes.
 */
fun writeMarkdownZip(out: OutputStream, notes: List<NoteEntity>, folders: List<FolderEntity>, fileOf: (Attachment) -> File?): Int {
    val live = notes.filter { !it.deleted && it.trashedAt == null }
    val paths = exportPaths(live, folders)
    val used = linkedMapOf<String, File>()
    ZipOutputStream(out).use { zip ->
        live.forEach { n ->
            val path = paths.getValue(n.id)
            val up = "../".repeat(path.count { it == '/' })
            val md = if (n.isLocked) "# ${inlineMarkdown(n.displayTitle, emptyList())}\n\n*This note is locked. Open it in Margin to read it.*"
            else noteMarkdown(
                n.blocks,
                attachmentRef = { a -> fileOf(a)?.takeIf { it.exists() }?.let { used[a.file] = it; up + "attachments/" + a.file } },
                linkUrl = { a -> fileOf(a)?.takeIf { it.exists() }?.readText()?.trim() },
            )
            zip.putNextEntry(ZipEntry(path).apply { time = n.lastEdited })
            zip.write((md + "\n").toByteArray())
            zip.closeEntry()
        }
        used.forEach { (name, file) ->
            zip.putNextEntry(ZipEntry("attachments/$name"))
            file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }
    return live.size
}

/**
 * A note made from something shared into Jotter: the subject (or the text's first line) is the title, the
 * rest of the text follows, then the shared file.
 */
fun sharedNoteBlocks(subject: String?, text: String?, attachment: Attachment?): List<Block> {
    val lines = text.orEmpty().replace("\r\n", "\n").split('\n').dropLastWhile { it.isBlank() }.toMutableList()
    val title = subject?.trim()?.takeIf { it.isNotEmpty() }
        ?: lines.removeFirstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        ?: attachment?.name
        ?: ""
    return listOf(Block(type = BlockType.TITLE, text = title.take(200))) +
        lines.map { Block(text = it) } +
        listOfNotNull(attachment?.let { Block(text = ATTACHMENT_CHAR.toString(), attachment = it) }, Block())
}
