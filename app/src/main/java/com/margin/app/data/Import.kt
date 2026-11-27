package com.margin.app.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64

/** Where an import reads from: a zip's entries or one picked file. Names use '/'. */
interface ImportSource {
    val names: List<String>
    fun read(name: String): ByteArray?
    /** When the file last changed, when known (a zip entry's time). */
    fun modified(name: String): Long? = null
}

/** A zip's entries. An entry larger than [maxBytes] reads as missing, so a zip bomb can't fill the memory. */
class ZipSource(private val zip: java.util.zip.ZipFile, private val maxBytes: Long = 64_000_000L) : ImportSource {
    private val entries = zip.entries().asSequence().filter { !it.isDirectory }.associateBy { it.name.replace('\\', '/') }
    override val names = entries.keys.toList()
    override fun read(name: String): ByteArray? {
        val e = entries[name] ?: return null
        if (e.size > maxBytes) return null
        return zip.getInputStream(e).use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > maxBytes) return null
                out.write(buf, 0, n)
            }
            out.toByteArray()
        }
    }
    override fun modified(name: String) = entries[name]?.time?.takeIf { it > 0 }
}

/** One picked file, read as [name]. */
class FileSource(private val name: String, private val file: java.io.File) : ImportSource {
    override val names = listOf(name)
    override fun read(name: String) = if (name == this.name) file.readBytes() else null
    override fun modified(name: String) = file.lastModified().takeIf { it > 0 && name == this.name }
}

class MapSource(private val files: Map<String, ByteArray>, private val times: Map<String, Long> = emptyMap()) : ImportSource {
    override val names = files.keys.toList()
    override fun read(name: String) = files[name]
    override fun modified(name: String) = times[name]
}

/**
 * One note to import. Its first block is the title. An attachment block holds, in [Attachment.file], the key
 * of its bytes in [ImportResult.file] until the file is stored. [folder] is the folder path it goes in.
 */
data class ImportedNote(
    val blocks: List<Block>,
    val folder: List<String>,
    val createdAt: Long? = null,
    val editedAt: Long? = null,
    val pinned: Boolean = false,
    val archived: Boolean = false,
)

class ImportResult(
    val notes: List<ImportedNote>,
    /** Files that looked like notes but couldn't be read. */
    val failed: Int,
    private val embedded: Map<String, ByteArray>,
    private val source: ImportSource,
) {
    /** The bytes of an attachment block's file. */
    fun file(key: String): ByteArray? = embedded[key] ?: source.read(key)
}

const val KEEP_FOLDER = "Google Keep"
const val EVERNOTE_FOLDER = "Evernote"
const val IMPORT_FOLDER = "Imported"

private val NOTE_EXTENSIONS = setOf("md", "markdown", "txt", "html", "htm", "json", "enex")

/**
 * Reads every note in [source]: Google Keep Takeout (.json beside its photos), Evernote (.enex), Markdown
 * (.md, with the images it uses), plain text and HTML. Other files are only used as attachments.
 */
fun parseImport(source: ImportSource): ImportResult {
    val names = source.names.filter { n ->
        val file = n.substringAfterLast('/')
        !n.endsWith("/") && !n.startsWith("__MACOSX/") && !file.startsWith(".")
    }
    val byLower = names.associateBy { it.lowercase() }
    val keepDirs = mutableSetOf<String>()
    val jsonBases = names.filter { it.endsWith(".json", true) }.map { it.dropLast(5).lowercase() }.toSet()
    val embedded = mutableMapOf<String, ByteArray>()
    val notes = mutableListOf<ImportedNote>()
    var failed = 0

    fun text(name: String) = source.read(name)?.toString(Charsets.UTF_8)?.removePrefix("﻿")
    fun dirOf(name: String) = name.substringBeforeLast('/', "")

    /** A file another file points at ([ref], relative to [from]), as an attachment; null when it isn't there. */
    fun resolve(from: String, ref: String): Attachment? {
        val clean = runCatching { java.net.URLDecoder.decode(ref.replace("+", "%2B"), "UTF-8") }.getOrDefault(ref)
            .substringBefore('#').substringBefore('?').trim().removePrefix("<").removeSuffix(">")
        if (clean.isEmpty() || Regex("^[a-z]+:", RegexOption.IGNORE_CASE).containsMatchIn(clean)) return null
        val parts = ArrayDeque<String>()
        val start = if (clean.startsWith("/")) "" else dirOf(from)
        (start.split('/') + clean.split('/')).forEach { p ->
            when (p) { "", "." -> {}; ".." -> parts.removeLastOrNull(); else -> parts.addLast(p) }
        }
        val found = byLower[parts.joinToString("/").lowercase()]
            // Obsidian's ![[photo.png]] names only the file.
            ?: names.firstOrNull { !clean.contains('/') && it.substringAfterLast('/').equals(clean, true) }
            ?: return null
        val ext = found.substringAfterLast('.', "").lowercase()
        if (ext in NOTE_EXTENSIONS) return null
        return Attachment(found, found.substringAfterLast('/'), mimeFor(ext))
    }

    // Keep first, so its folders are known before their Labels.txt and .html copies come up.
    val ordered = names.sortedBy { if (it.endsWith(".json", true)) 0 else 1 }
    for (name in ordered) {
        val ext = name.substringAfterLast('.', "").lowercase()
        val file = name.substringAfterLast('/')
        val base = file.substringBeforeLast('.')
        try {
            when (ext) {
                "json" -> keepNote(name, text(name) ?: continue, names)?.let { n ->
                    keepDirs += dirOf(name)
                    n.blocks.firstOrNull()?.let { notes += n }
                }
                "enex" -> notes += enexNotes(base, text(name) ?: continue, embedded)
                "md", "markdown", "txt" -> {
                    // Keep's Takeout lists its labels in Labels.txt beside the notes.
                    if (file.equals("Labels.txt", true) && dirOf(name) in keepDirs) continue
                    val folder = listOf(IMPORT_FOLDER) + dirOf(name).split('/').filter { it.isNotEmpty() }
                    notes += markdownNote(base, text(name) ?: continue, markdown = ext != "txt") { resolve(name, it) }
                        .copy(folder = folder, createdAt = source.modified(name), editedAt = source.modified(name))
                }
                "html", "htm" -> {
                    // Keep's Takeout has each note twice; the .json one is read instead. Takeout's index page isn't a note.
                    if (name.dropLast(ext.length + 1).lowercase() in jsonBases || file.equals("archive_browser.html", true)) continue
                    val folder = listOf(IMPORT_FOLDER) + dirOf(name).split('/').filter { it.isNotEmpty() }
                    notes += htmlNote(base, text(name) ?: continue) { resolve(name, it) }
                        .copy(folder = folder, createdAt = source.modified(name), editedAt = source.modified(name))
                }
            }
        } catch (e: Exception) {
            failed++
        }
    }
    return ImportResult(notes, failed, embedded, source)
}

fun mimeFor(ext: String): String = when (ext.lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "heic" -> "image/heic"
    "pdf" -> "application/pdf"
    "m4a", "aac" -> "audio/mp4"
    "mp3" -> "audio/mpeg"
    "wav" -> "audio/wav"
    "ogg", "opus" -> "audio/ogg"
    "3gp", "amr" -> "audio/3gpp"
    "txt" -> "text/plain"
    else -> "application/octet-stream"
}

fun extensionFor(mime: String): String = when (mime.lowercase()) {
    "image/jpeg", "image/jpg" -> "jpg"
    "image/png" -> "png"
    "image/gif" -> "gif"
    "image/webp" -> "webp"
    "image/heic" -> "heic"
    "application/pdf" -> "pdf"
    "audio/mp4", "audio/m4a", "audio/x-m4a" -> "m4a"
    "audio/mpeg" -> "mp3"
    "audio/wav", "audio/x-wav" -> "wav"
    "audio/ogg" -> "ogg"
    "audio/3gpp", "audio/amr" -> "3gp"
    else -> "bin"
}

/** A tag line for labels from another app: "#Work #to_do". Labels with no letter can't be tags and are left out. */
fun tagLine(labels: List<String>): String? =
    labels.map { it.trim().replace(Regex("[^\\p{L}\\p{N}_-]+"), "_").trim('_', '-') }
        .filter { l -> l.any { it.isLetter() } }.distinct()
        .joinToString(" ") { "#$it" }.ifEmpty { null }

private fun attachmentBlock(a: Attachment) = Block(text = ATTACHMENT_CHAR.toString(), attachment = a)

/** Title first, no newlines in a line, and an empty line after a closing photo so there's somewhere to type. */
private fun finished(title: String, body: List<Block>): List<Block> {
    val lines = body.map { if ('\n' in it.text) it.copy(text = it.text.replace('\n', ' ')) else it }
        .dropWhile { !it.isObject && it.text.isBlank() }.dropLastWhile { !it.isObject && it.text.isBlank() }
    return listOf(Block(type = BlockType.TITLE, text = title.replace('\n', ' ').trim().take(200))) + lines +
        listOfNotNull(Block().takeIf { lines.lastOrNull()?.isObject == true })
}

// ---------- Google Keep ----------

private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
private fun JsonObject.bool(key: String) = (this[key] as? JsonPrimitive)?.booleanOrNull == true
private fun JsonObject.long(key: String) = (this[key] as? JsonPrimitive)?.longOrNull

/** A Keep note from Takeout's .json; null for other JSON and for notes in Keep's trash. */
fun keepNote(name: String, json: String, names: List<String>): ImportedNote? {
    val o = runCatching { MarginJson.parseToJsonElement(json) }.getOrNull() as? JsonObject ?: return null
    if ("userEditedTimestampUsec" !in o || ("textContent" !in o && "listContent" !in o)) return null
    if (o.bool("isTrashed")) return ImportedNote(emptyList(), emptyList())
    val dir = name.substringBeforeLast('/', "")
    val body = mutableListOf<Block>()
    o.str("textContent")?.replace("\r\n", "\n")?.split('\n')?.forEach { body += Block(text = it) }
    (o["listContent"] as? JsonArray)?.forEach { item ->
        val i = item as? JsonObject ?: return@forEach
        body += Block(type = BlockType.CHECK, text = i.str("text").orEmpty().replace('\n', ' '), checked = i.bool("isChecked"))
    }
    (o["annotations"] as? JsonArray)?.forEach { item ->
        val url = (item as? JsonObject)?.str("url") ?: return@forEach
        body += Block(text = url)
    }
    (o["attachments"] as? JsonArray)?.forEach { item ->
        val a = item as? JsonObject ?: return@forEach
        val path = a.str("filePath") ?: return@forEach
        val want = (if (dir.isEmpty()) path else "$dir/$path").lowercase()
        // Takeout sometimes names a photo .jpg in the note and .jpeg on disk: match on the name without its extension.
        val found = names.firstOrNull { it.lowercase() == want }
            ?: names.firstOrNull { it.lowercase().substringBeforeLast('.') == want.substringBeforeLast('.') && !it.endsWith(".json", true) && !it.endsWith(".html", true) }
            ?: return@forEach
        val mime = a.str("mimetype")?.takeIf { it.contains('/') } ?: mimeFor(found.substringAfterLast('.', ""))
        body += attachmentBlock(Attachment(found, found.substringAfterLast('/'), mime))
    }
    tagLine((o["labels"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.str("name") })?.let { body += Block(); body += Block(text = it) }
    val edited = o.long("userEditedTimestampUsec")?.div(1000)?.takeIf { it > 0 }
    val created = o.long("createdTimestampUsec")?.div(1000)?.takeIf { it > 0 } ?: edited
    // A Keep note without a title: its first line becomes one, as Margin shows it in the list anyway.
    var title = o.str("title").orEmpty().trim()
    if (title.isEmpty()) {
        val first = body.indexOfFirst { !it.isObject && it.text.isNotBlank() }
        if (first >= 0 && body[first].type == BlockType.BODY && body[first].text.length <= 100) title = body.removeAt(first).text.trim()
    }
    return ImportedNote(
        finished(title, body),
        listOf(KEEP_FOLDER),
        created, edited, pinned = o.bool("isPinned"), archived = o.bool("isArchived"),
    )
}

// ---------- Evernote ----------

private val ENEX_NOTE = Regex("<note>(.*?)</note>", RegexOption.DOT_MATCHES_ALL)
private val ENEX_RESOURCE = Regex("<resource>(.*?)</resource>", RegexOption.DOT_MATCHES_ALL)
private val ENEX_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

private fun tagText(xml: String, tag: String) =
    Regex("<$tag(?:\\s[^>]*)?>(.*?)</$tag>", RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1)

private fun enexTime(s: String?) = s?.trim()?.let { runCatching { LocalDateTime.parse(it, ENEX_TIME).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull() }

/** The notes of an Evernote export; [notebook] (the file's name) becomes their folder. Photos go in [embedded]. */
// shortcut: reads the whole .enex into memory; a notebook of several hundred MB could run out of it.
fun enexNotes(notebook: String, enex: String, embedded: MutableMap<String, ByteArray>): List<ImportedNote> =
    ENEX_NOTE.findAll(enex).map { m ->
        val xml = m.groupValues[1]
        val resources = linkedMapOf<String, Attachment>()
        ENEX_RESOURCE.findAll(xml).forEach { r ->
            val res = r.groupValues[1]
            val data = tagText(res, "data")?.filterNot { it.isWhitespace() } ?: return@forEach
            val bytes = runCatching { Base64.getDecoder().decode(data) }.getOrNull() ?: return@forEach
            val hash = MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }
            val mime = tagText(res, "mime")?.trim() ?: "application/octet-stream"
            val name = tagText(res, "file-name")?.let(::decodeEntities)?.trim()?.takeIf { it.isNotEmpty() } ?: "Attachment.${extensionFor(mime)}"
            embedded["enex:$hash"] = bytes
            resources[hash] = Attachment("enex:$hash", name, mime)
        }
        val raw = tagText(xml, "content").orEmpty().trim()
        val content = Regex("^<!\\[CDATA\\[(.*)]]>$", RegexOption.DOT_MATCHES_ALL).find(raw)?.groupValues?.get(1) ?: decodeEntities(raw)
        val used = mutableSetOf<String>()
        val body = HtmlLines { tag, attrs ->
            if (tag == "en-media") attrs["hash"]?.lowercase()?.let { h -> resources[h]?.also { used += h } } else null
        }.convert(content)
        val rest = resources.filterKeys { it !in used }.values.map(::attachmentBlock)
        val tags = Regex("<tag>(.*?)</tag>", RegexOption.DOT_MATCHES_ALL).findAll(xml).map { decodeEntities(it.groupValues[1]) }.toList()
        val created = enexTime(tagText(xml, "created"))
        ImportedNote(
            finished(decodeEntities(tagText(xml, "title").orEmpty()), body + rest + listOfNotNull(tagLine(tags)?.let { Block(text = it) })),
            listOf(EVERNOTE_FOLDER, notebook), created, enexTime(tagText(xml, "updated")) ?: created,
        )
    }.toList()

// ---------- Markdown and text ----------

private val HEADING_LINE = Regex("^(#{1,6})\\s+(.*?)(\\s+#+)?\\s*$")
private val CHECK_LINE = Regex("^(\\s*)[-*+]\\s+\\[([ xX])]\\s?(.*)$")
private val BULLET_LINE = Regex("^(\\s*)([-*+])\\s+(.*)$")
private val NUMBER_LINE = Regex("^(\\s*)\\d{1,9}[.)]\\s+(.*)$")
private val RULE_LINE = Regex("^\\s*([-*_])(\\s*\\1){2,}\\s*$")
private val TABLE_RULE = Regex("^\\s*\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)*\\|?\\s*$")

/**
 * A Markdown (or, with [markdown] false, plain text) file as a note. Its title is a leading "# " heading, or the
 * first line of a text file, or else [fileName]. [resolve] finds a file an image or link points at.
 */
fun markdownNote(fileName: String, source: String, markdown: Boolean = true, resolve: (String) -> Attachment? = { null }): ImportedNote {
    var lines = source.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    // Front matter (Obsidian, Bear, Jekyll) is metadata, not text.
    if (markdown && lines.firstOrNull()?.trim() == "---") {
        val end = lines.drop(1).indexOfFirst { it.trim() == "---" }
        if (end in 0..60) lines = lines.drop(end + 2)
    }
    val listIndents = lines.mapNotNull { l -> Regex("^( +)([-*+]|\\d+[.)])\\s").find(l.replace("\t", "    "))?.groupValues?.get(1)?.length }
    val unit = listIndents.minOrNull()?.coerceIn(2, 4) ?: 2
    var title: String? = null
    if (!markdown) {
        val first = lines.indexOfFirst { it.isNotBlank() }
        if (first >= 0) { title = lines[first].trim(); lines = lines.drop(first + 1) }
    }
    val body = mutableListOf<Block>()
    var code = false
    var blanks = 0
    for (raw in lines) {
        if (markdown && raw.trimStart().startsWith("```")) {
            if (blanks >= 2 && body.isNotEmpty() && !code) body += Block()
            blanks = 0; code = !code; continue
        }
        if (code) { body += Block(type = BlockType.MONOSPACED, text = raw.replace("\t", "    ")); continue }
        if (raw.isBlank()) { blanks++; continue }
        // One blank line only separates paragraphs; more than one was meant as space.
        if (blanks >= 2 && body.isNotEmpty()) body += Block()
        blanks = 0
        var line = raw.replace("\t", "    ")
        var quote = false
        if (markdown && line.trimStart().startsWith(">")) {
            quote = true
            line = line.trimStart().removePrefix(">").removePrefix(" ")
        }
        if (markdown && (RULE_LINE.matches(line) || TABLE_RULE.matches(line))) continue
        var type = BlockType.BODY
        var checked = false
        var indent = 0
        var content = line.trim()
        val heading = if (markdown) HEADING_LINE.find(line) else null
        val check = CHECK_LINE.find(line)
        val bullet = BULLET_LINE.find(line)
        val number = NUMBER_LINE.find(line)
        when {
            heading != null -> {
                val level = heading.groupValues[1].length
                if (title == null && body.isEmpty() && level == 1) { title = inlineText(heading.groupValues[2], resolve).joinToString("") { (it as? Piece.Text)?.text.orEmpty() }; continue }
                type = if (level <= 2) BlockType.HEADING else BlockType.SUBHEADING
                content = heading.groupValues[2]
            }
            check != null -> { type = BlockType.CHECK; checked = check.groupValues[2] != " "; indent = check.groupValues[1].length / unit; content = check.groupValues[3] }
            bullet != null -> { type = if (bullet.groupValues[2] == "-") BlockType.DASHED else BlockType.BULLET; indent = bullet.groupValues[1].length / unit; content = bullet.groupValues[3] }
            number != null -> { type = BlockType.NUMBERED; indent = number.groupValues[1].length / unit; content = number.groupValues[2] }
        }
        indent = indent.coerceIn(0, MAX_INDENT)
        if (!markdown) { body += Block(type = type, text = content, checked = checked, indent = indent); continue }
        // shortcut: a Markdown table is kept as its text rows, not turned into a table.
        inlineText(content, resolve).forEach { p ->
            when (p) {
                is Piece.Media -> body += attachmentBlock(p.attachment)
                is Piece.Text -> if (p.text.isNotBlank()) body += Block(type = type, text = p.text, checked = checked, marks = p.marks, quote = quote, indent = indent)
            }
        }
    }
    return ImportedNote(finished(title ?: fileName, body), listOf(IMPORT_FOLDER))
}

sealed interface Piece {
    data class Text(val text: String, val marks: List<MarkSpan>) : Piece
    data class Media(val attachment: Attachment) : Piece
}

/** A line of Markdown as text with styles; an image (or a link to a file that's there) splits it into its own piece. */
fun inlineText(s: String, resolve: (String) -> Attachment? = { null }): List<Piece> {
    val pieces = mutableListOf<Piece>()
    val out = StringBuilder()
    val marks = mutableListOf<MarkSpan>()
    val openAt = mutableMapOf<Mark, Int>()
    fun toggle(m: Mark) {
        val at = openAt.remove(m)
        if (at == null) openAt[m] = out.length else if (at < out.length) marks += MarkSpan(at, out.length, m)
    }
    fun cut() {
        openAt.forEach { (m, at) -> if (at < out.length) marks += MarkSpan(at, out.length, m) }
        val text = out.toString()
        val lead = text.length - text.trimStart().length
        val kept = text.trim()
        if (kept.isNotEmpty()) pieces += Piece.Text(kept, marks.map { it.copy(start = maxOf(0, it.start - lead), end = it.end - lead) }.clampedTo(kept.length).filter { it.start < it.end })
        out.clear(); marks.clear()
        openAt.keys.toList().forEach { openAt[it] = 0 }
    }
    /** A "*" or "_" run at [i] can open only if it closes later in the line. */
    fun closes(i: Int, marker: String): Boolean {
        val next = s.getOrNull(i + marker.length) ?: return false
        if (next.isWhitespace()) return false
        var j = s.indexOf(marker, i + marker.length + 1)
        while (j > 0) {
            if (!s[j - 1].isWhitespace() && (marker.length == 2 || s.getOrNull(j + 1) != marker[0])) return true
            j = s.indexOf(marker, j + 1)
        }
        return false
    }
    fun wordChar(c: Char?) = c != null && c.isLetterOrDigit()
    var i = 0
    while (i < s.length) {
        val c = s[i]
        when {
            c == '\\' && i + 1 < s.length && s[i + 1] in "\\`*_{}[]()#+-.!|>~<" -> { out.append(s[i + 1]); i += 2 }
            s.startsWith("![[", i) && s.indexOf("]]", i) > 0 -> {
                val end = s.indexOf("]]", i)
                val ref = s.substring(i + 3, end).substringBefore('|')
                val a = resolve(ref)
                if (a != null) { cut(); pieces += Piece.Media(a) } else out.append(ref)
                i = end + 2
            }
            (c == '!' && s.startsWith("![", i)) || (c == '[' && !s.startsWith("[[", i)) -> {
                val textStart = if (c == '!') i + 2 else i + 1
                val close = s.indexOf("](", textStart)
                val end = if (close > 0) s.indexOf(')', close + 2) else -1
                if (close < 0 || end < 0 || s.indexOf(']', textStart) != close) { out.append(c); i++; continue }
                val label = s.substring(textStart, close).replace(Regex("\\\\(.)"), "$1")
                val target = s.substring(close + 2, end).trim().substringBefore(" \"")
                val a = resolve(target)
                when {
                    a != null -> { cut(); pieces += Piece.Media(if (label.isNotBlank()) a.copy(name = label) else a) }
                    c == '!' -> out.append(label)
                    else -> {
                        out.append(label)
                        if (target.startsWith("http", true) && target != label) out.append(" (").append(target).append(')')
                    }
                }
                i = end + 1
            }
            s.startsWith("[[", i) -> { out.append("[["); i += 2 }
            s.startsWith("<u>", i, true) -> { toggle(Mark.UNDERLINE); i += 3 }
            s.startsWith("</u>", i, true) -> { toggle(Mark.UNDERLINE); i += 4 }
            s.startsWith("<mark>", i, true) -> { toggle(Mark.HIGHLIGHT); i += 6 }
            s.startsWith("</mark>", i, true) -> { toggle(Mark.HIGHLIGHT); i += 7 }
            s.startsWith("<br>", i, true) || s.startsWith("<br/>", i, true) -> { out.append(' '); i += if (s[i + 3] == '>') 4 else 5 }
            c == '`' -> { val end = s.indexOf('`', i + 1); if (end > i) { out.append(s, i + 1, end); i = end + 1 } else { out.append(c); i++ } }
            (s.startsWith("**", i) || s.startsWith("__", i)) && (Mark.BOLD in openAt || closes(i, s.substring(i, i + 2))) -> { toggle(Mark.BOLD); i += 2 }
            // "_" inside a word (snake_case) is just a character.
            (c == '*' || (c == '_' && !wordChar(s.getOrNull(if (Mark.ITALIC in openAt) i + 1 else i - 1)))) &&
                (Mark.ITALIC in openAt || closes(i, c.toString())) -> { toggle(Mark.ITALIC); i++ }
            else -> { out.append(c); i++ }
        }
    }
    cut()
    return pieces
}

// ---------- HTML (Evernote's ENML, saved web pages, other apps' HTML exports) ----------

private val ENTITY = Regex("&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[a-zA-Z]{2,8});")
private val NAMED = mapOf(
    "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ", "ndash" to "–", "mdash" to "—",
    "hellip" to "…", "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“", "rdquo" to "”", "bull" to "•", "copy" to "©",
    "reg" to "®", "trade" to "™", "middot" to "·", "times" to "×", "euro" to "€", "pound" to "£", "deg" to "°",
)

fun decodeEntities(s: String): String = ENTITY.replace(s) { m ->
    val e = m.groupValues[1]
    when {
        e.startsWith("#x") || e.startsWith("#X") -> e.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it.coerceIn(1, 0x10FFFF))) }
        e.startsWith("#") -> e.drop(1).toIntOrNull()?.let { String(Character.toChars(it.coerceIn(1, 0x10FFFF))) }
        else -> NAMED[e]
    } ?: m.value
}

/** An HTML file as a note: its <title> (or [fileName]) and its text. [resolve] finds the file an <img> points at. */
fun htmlNote(fileName: String, html: String, resolve: (String) -> Attachment? = { null }): ImportedNote {
    val title = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).find(html)
        ?.groupValues?.get(1)?.let { decodeEntities(it).trim() }?.takeIf { it.isNotEmpty() }
    val body = HtmlLines { tag, attrs -> if (tag == "img") attrs["src"]?.let(resolve) else null }.convert(html)
    return ImportedNote(finished(title ?: fileName, body), listOf(IMPORT_FOLDER))
}

private val TOKEN = Regex(
    """<!--.*?-->|<!\[CDATA\[(.*?)]]>|<(/?)([a-zA-Z][a-zA-Z0-9:-]*)((?:\s+[^\s=>/]+(?:\s*=\s*(?:"[^"]*"|'[^']*'|[^\s>]+))?)*)\s*/?>|<[!?][^>]*>""",
    RegexOption.DOT_MATCHES_ALL,
)
private val ATTR = Regex("""([^\s=/]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+)))?""")
private val BLOCK_TAGS = setOf("p", "div", "section", "article", "header", "footer", "main", "aside", "nav", "figure", "figcaption", "dl", "dt", "dd", "table", "tbody", "thead", "tr", "address", "center")
private val SKIP_TAGS = setOf("head", "style", "script", "title", "noscript", "template", "svg")

/** Turns HTML into lines with styles. [media] makes an attachment for an <img> or <en-media>, or null to leave it out. */
class HtmlLines(private val media: (tag: String, attrs: Map<String, String>) -> Attachment?) {
    private val out = mutableListOf<Block>()
    private val text = StringBuilder()
    private val marks = mutableListOf<MarkSpan>()
    private val count = mutableMapOf<Mark, Int>()
    private val start = mutableMapOf<Mark, Int>()
    private val inline = mutableListOf<Pair<String, List<Mark>>>()
    private val lists = mutableListOf<Pair<Boolean, Boolean>>() // (numbered, checklist)
    private var type = BlockType.BODY
    private var checked = false
    private var indent = 0
    private var quote = 0
    private var pre = 0
    private var skip = 0
    private var linkStart = -1
    private var linkHref: String? = null

    private fun on(m: Mark) { if ((count[m] ?: 0) == 0) start[m] = text.length; count[m] = (count[m] ?: 0) + 1 }
    private fun off(m: Mark) {
        val n = count[m] ?: 0
        if (n == 0) return
        count[m] = n - 1
        if (n == 1) start.remove(m)?.let { if (it < text.length) marks += MarkSpan(it, text.length, m) }
    }

    /** Ends the line. An empty line is kept only for [force] (a <br>). */
    private fun flush(force: Boolean = false) {
        start.forEach { (m, at) -> if (at < text.length) marks += MarkSpan(at, text.length, m) }
        val t = text.toString().trimEnd()
        if (t.isNotEmpty() || force) {
            val kept = marks.clampedTo(t.length).filter { it.start < it.end }
            out += Block(type = if (t.isEmpty()) BlockType.BODY else type, text = t, checked = checked && type == BlockType.CHECK, marks = kept, quote = quote > 0 && t.isNotEmpty(), indent = indent)
        }
        text.clear(); marks.clear()
        start.keys.toList().forEach { start[it] = 0 }
        if (lists.isEmpty() && pre == 0) { type = BlockType.BODY; indent = 0 }
        if (type != BlockType.CHECK || lists.isEmpty()) checked = false
    }

    private fun append(raw: String) {
        if (skip > 0) return
        if (pre > 0) {
            val parts = raw.split('\n')
            parts.forEachIndexed { k, p -> if (k > 0) flush(force = true); text.append(p) }
            return
        }
        raw.forEach { c ->
            if (c.isWhitespace()) { if (text.isNotEmpty() && text.last() != ' ') text.append(' ') } else text.append(c)
        }
    }

    fun convert(html: String): List<Block> {
        var last = 0
        TOKEN.findAll(html).forEach { m ->
            if (m.range.first > last) append(decodeEntities(html.substring(last, m.range.first)))
            last = m.range.last + 1
            m.groups[1]?.let { append(it.value); return@forEach }
            val name = m.groupValues[3].lowercase().takeIf { it.isNotEmpty() } ?: return@forEach
            val closing = m.groupValues[2] == "/"
            val selfClosing = m.value.endsWith("/>")
            val attrs = ATTR.findAll(m.groupValues[4]).associate { a ->
                a.groupValues[1].lowercase() to decodeEntities(a.groupValues[2].ifEmpty { a.groupValues[3].ifEmpty { a.groupValues[4] } })
            }
            if (name in SKIP_TAGS) { if (!selfClosing) skip = (skip + if (closing) -1 else 1).coerceAtLeast(0); return@forEach }
            if (skip > 0) return@forEach
            tag(name, closing, selfClosing, attrs)
        }
        if (last < html.length) append(decodeEntities(html.substring(last)))
        flush()
        // Several empty lines in a row (Evernote's spacing) become one.
        return out.filterIndexed { i, b -> b.text.isNotEmpty() || b.isObject || (i > 0 && (out[i - 1].text.isNotEmpty() || out[i - 1].isObject)) }
    }

    private fun tag(name: String, closing: Boolean, selfClosing: Boolean, attrs: Map<String, String>) {
        val style = attrs["style"].orEmpty().replace(" ", "").lowercase()
        when (name) {
            "br" -> flush(force = true)
            "hr" -> flush()
            "img", "en-media" -> media(name, attrs)?.let { flush(); out += attachmentBlock(it) }
                ?: attrs["alt"]?.takeIf { it.isNotBlank() }?.let { append(it) }
            "en-todo" -> { type = BlockType.CHECK; checked = attrs["checked"] == "true" }
            "ul", "ol" -> {
                flush()
                if (closing) lists.removeLastOrNull() else if (!selfClosing) lists += (name == "ol") to style.contains("--en-todo:true")
                indent = (lists.size - 1).coerceIn(0, MAX_INDENT)
                type = if (lists.isEmpty()) BlockType.BODY else type
            }
            "li" -> {
                flush()
                if (!closing) {
                    val (numbered, todo) = lists.lastOrNull() ?: (false to false)
                    type = when { todo -> BlockType.CHECK; numbered -> BlockType.NUMBERED; else -> BlockType.BULLET }
                    checked = todo && style.contains("--en-checked:true")
                    indent = (lists.size - 1).coerceIn(0, MAX_INDENT)
                }
            }
            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                flush()
                type = if (closing) BlockType.BODY else if (name <= "h2") BlockType.HEADING else BlockType.SUBHEADING
            }
            "pre" -> { flush(); pre = (pre + if (closing) -1 else 1).coerceAtLeast(0); type = if (pre > 0) BlockType.MONOSPACED else BlockType.BODY }
            "blockquote" -> { flush(); quote = (quote + if (closing) -1 else 1).coerceAtLeast(0) }
            "td", "th" -> if (!closing && text.isNotEmpty()) text.append("  ")
            "a" -> if (!closing) { linkStart = text.length; linkHref = attrs["href"] } else {
                val href = linkHref
                val label = if (linkStart in 0..text.length) text.substring(linkStart).trim() else ""
                if (href != null && href.startsWith("http", true) && label.isNotEmpty() && label != href && href !in label) append(" ($href)")
                linkStart = -1; linkHref = null
            }
            in BLOCK_TAGS -> flush()
            else -> inlineTag(name, closing, selfClosing, style)
        }
    }

    private fun inlineTag(name: String, closing: Boolean, selfClosing: Boolean, style: String) {
        if (selfClosing) return
        if (closing) {
            val at = inline.indexOfLast { it.first == name }
            if (at < 0) return
            inline.subList(at, inline.size).asReversed().forEach { (_, ms) -> ms.forEach(::off) }
            inline.subList(at, inline.size).clear()
            return
        }
        val ms = buildList {
            when (name) {
                "b", "strong" -> add(Mark.BOLD)
                "i", "em", "cite" -> add(Mark.ITALIC)
                "u", "ins" -> add(Mark.UNDERLINE)
                "mark" -> add(Mark.HIGHLIGHT)
            }
            if (Regex("font-weight:(bold|[6-9]00)").containsMatchIn(style)) add(Mark.BOLD)
            if ("font-style:italic" in style) add(Mark.ITALIC)
            if ("text-decoration:underline" in style || "text-decoration-line:underline" in style) add(Mark.UNDERLINE)
            if (Regex("background(-color)?:(?!(transparent|#fff|#ffffff|white|rgb\\(255,255,255\\)))").containsMatchIn(style)) add(Mark.HIGHLIGHT)
        }.distinct()
        ms.forEach(::on)
        inline += name to ms
    }
}
