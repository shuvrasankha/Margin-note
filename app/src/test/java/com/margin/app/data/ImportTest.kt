package com.margin.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64

class ImportTest {
    private fun source(vararg files: Pair<String, String>) = MapSource(files.associate { (n, t) -> n to t.toByteArray() })
    private fun ImportedNote.texts() = blocks.map { it.text }

    @Test
    fun `Keep Takeout note with list, labels, photo and archive`() {
        val json = """
            {"title":"Shopping","isPinned":true,"isArchived":false,"isTrashed":false,
             "listContent":[{"text":"Milk","isChecked":false},{"text":"Eggs","isChecked":true}],
             "labels":[{"name":"Home stuff"},{"name":"2024"}],
             "attachments":[{"filePath":"photo.jpg","mimetype":"image/jpeg"}],
             "createdTimestampUsec":1700000000000000,"userEditedTimestampUsec":1700000500000000}
        """.trimIndent()
        val archived = """{"title":"","textContent":"Old idea\nsecond line","isArchived":true,"userEditedTimestampUsec":1}"""
        val trashed = """{"title":"Gone","textContent":"x","isTrashed":true,"userEditedTimestampUsec":1}"""
        val r = parseImport(MapSource(mapOf(
            "Takeout/Keep/Shopping.json" to json.toByteArray(),
            "Takeout/Keep/Shopping.html" to "<html>dup</html>".toByteArray(),
            "Takeout/Keep/photo.jpeg" to byteArrayOf(1, 2, 3), // Takeout's .jpg/.jpeg mix-up
            "Takeout/Keep/Idea.json" to archived.toByteArray(),
            "Takeout/Keep/Gone.json" to trashed.toByteArray(),
            "Takeout/Keep/Labels.txt" to "Home stuff\n2024".toByteArray(),
            "Takeout/archive_browser.html" to "<html>index</html>".toByteArray(),
        )))
        assertEquals(2, r.notes.size)
        val shop = r.notes.first { it.blocks[0].text == "Shopping" }
        assertTrue(shop.pinned)
        assertEquals(listOf(KEEP_FOLDER), shop.folder)
        assertEquals(1_700_000_000_000L, shop.createdAt)
        assertEquals(1_700_000_500_000L, shop.editedAt)
        assertEquals(BlockType.CHECK, shop.blocks[1].type)
        assertTrue(shop.blocks[2].checked)
        val photo = shop.blocks.first { it.attachment != null }.attachment!!
        assertEquals("Takeout/Keep/photo.jpeg", photo.file)
        assertEquals(3, r.file(photo.file)?.size)
        assertEquals("#Home_stuff", shop.blocks.last().text.substringBefore(" #2024").trim()) // a label with no letter isn't a tag
        assertFalse("#2024" in shop.blocks.last().text)
        val idea = r.notes.first { it.folder == listOf(KEEP_FOLDER, "Archive") }
        assertEquals(listOf("Old idea", "second line"), idea.texts()) // no title: the first line becomes it
    }

    @Test
    fun `Evernote note with checklist, styles, photo and tags`() {
        val png = byteArrayOf(9, 8, 7)
        val hash = MessageDigest.getInstance("MD5").digest(png).joinToString("") { "%02x".format(it) }
        val content = """<?xml version="1.0"?><!DOCTYPE en-note SYSTEM "x"><en-note>
            <div>Hello <b>bold</b> &amp; <i>italic</i>&nbsp;text</div>
            <div><br/></div><div><br/></div><div><br/></div>
            <div><en-todo checked="true"/>Done thing</div>
            <div><en-todo/>Open thing</div>
            <ul><li>one</li><li>two<ul><li>deep</li></ul></li></ul>
            <ol><li>first</li></ol>
            <ul style="--en-todo:true;"><li style="--en-checked:true;"><div>new style</div></li></ul>
            <en-media hash="$hash" type="image/png"/>
            <div><a href="https://example.com">site</a></div>
            </en-note>"""
        val enex = """<?xml version="1.0" encoding="UTF-8"?>
            <en-export><note><title>Trip &amp; plans</title><content><![CDATA[$content]]></content>
            <created>20240102T030405Z</created><updated>20240103T000000Z</updated><tag>travel</tag><tag>to do</tag>
            <resource><data encoding="base64">${Base64.getEncoder().encodeToString(png)}</data><mime>image/png</mime>
            <resource-attributes><file-name>map.png</file-name></resource-attributes></resource></note></en-export>"""
        val r = parseImport(source("Work.enex" to enex))
        val n = r.notes.single()
        assertEquals(listOf(EVERNOTE_FOLDER, "Work"), n.folder)
        assertEquals("Trip & plans", n.blocks[0].text)
        assertEquals(java.time.Instant.parse("2024-01-02T03:04:05Z").toEpochMilli(), n.createdAt)
        val hello = n.blocks[1]
        assertEquals("Hello bold & italic text", hello.text)
        assertEquals(listOf(MarkSpan(6, 10, Mark.BOLD), MarkSpan(13, 19, Mark.ITALIC)), hello.marks)
        assertEquals("", n.blocks[2].text) // three empty lines become one
        assertEquals("Done thing", n.blocks[3].text)
        assertEquals(BlockType.CHECK, n.blocks[3].type); assertTrue(n.blocks[3].checked)
        assertEquals(BlockType.CHECK, n.blocks[4].type); assertFalse(n.blocks[4].checked)
        val deep = n.blocks.first { it.text == "deep" }
        assertEquals(BlockType.BULLET, deep.type); assertEquals(1, deep.indent)
        assertEquals(BlockType.NUMBERED, n.blocks.first { it.text == "first" }.type)
        n.blocks.first { it.text == "new style" }.let { assertEquals(BlockType.CHECK, it.type); assertTrue(it.checked) }
        val media = n.blocks.first { it.attachment != null }.attachment!!
        assertEquals("map.png", media.name)
        assertTrue(png.contentEquals(r.file(media.file)))
        assertTrue(n.blocks.any { it.text == "site (https://example.com)" })
        assertEquals("#travel #to_do", n.blocks.last().text)
    }

    @Test
    fun `Margin's own Markdown export comes back as it was`() {
        val original = listOf(
            Block(type = BlockType.TITLE, text = "Trip"),
            Block(type = BlockType.HEADING, text = "Plan"),
            Block(text = "#tags at the start stay text"),
            Block(text = "2. not a list"),
            Block(type = BlockType.NUMBERED, text = "one"),
            Block(type = BlockType.DASHED, text = "nested", indent = 1),
            Block(type = BlockType.CHECK, text = "done", checked = true),
            Block(text = "said once", quote = true),
            Block(type = BlockType.MONOSPACED, text = "val x = 1"),
            Block(text = "a *star* and bold", marks = listOf(MarkSpan(13, 17, Mark.BOLD))),
            Block(text = "snake_case_name stays"),
        )
        val md = noteMarkdown(original)
        val back = markdownNote("ignored", md).blocks
        assertEquals(original.map { Triple(it.type, it.text, it.checked) }, back.map { Triple(it.type, it.text, it.checked) })
        assertEquals(original.map { it.indent to it.quote }, back.map { it.indent to it.quote })
        assertEquals(listOf(MarkSpan(13, 17, Mark.BOLD)), back[9].marks)
    }

    @Test
    fun `Markdown zip with folders and an image beside the note`() {
        val r = parseImport(MapSource(mapOf(
            "export/Notes/Work/Plan.md" to "# Plan\n\nSee ![the map](../../attachments/map%20one.png) here\n\n- [ ] call".toByteArray(),
            "export/attachments/map one.png" to byteArrayOf(5),
            "export/Notes/Plain.txt" to "Groceries\nmilk\n- bread".toByteArray(),
        )))
        val plan = r.notes.first { it.blocks[0].text == "Plan" }
        assertEquals(listOf(IMPORT_FOLDER, "export", "Notes", "Work"), plan.folder)
        assertEquals(listOf("Plan", "See", ATTACHMENT_CHAR.toString(), "here", "call"), plan.texts())
        assertEquals("the map", plan.blocks[2].attachment?.name)
        assertEquals("export/attachments/map one.png", plan.blocks[2].attachment?.file)
        val txt = r.notes.first { it.blocks[0].text == "Groceries" }
        assertEquals(listOf("Groceries", "milk", "bread"), txt.texts())
        assertEquals(BlockType.DASHED, txt.blocks[2].type)
    }

    @Test
    fun `Markdown inline styles, links and unclosed stars`() {
        val p = inlineText("5 * 3 = **15**, *nice*, <mark>hi</mark> [docs](https://x.org) [[Other note]]")
        val t = p.single() as Piece.Text
        assertEquals("5 * 3 = 15, nice, hi docs (https://x.org) [[Other note]]", t.text)
        assertEquals(setOf(MarkSpan(8, 10, Mark.BOLD), MarkSpan(12, 16, Mark.ITALIC), MarkSpan(18, 20, Mark.HIGHLIGHT)), t.marks.toSet())
    }

    @Test
    fun `front matter is dropped and the file name is the title`() {
        val n = markdownNote("Daily", "---\ntags: [a]\n---\nBody line\n\n\n\nAfter gap")
        assertEquals(listOf("Daily", "Body line", "", "After gap"), n.texts())
    }

    @Test
    fun `HTML page keeps its title and skips style and script`() {
        val n = htmlNote("file", "<html><head><title>Recipe</title><style>p{}</style></head><body><h1>Soup</h1><p>Boil <strong>water</strong>.</p><script>x()</script><pre>a\n  b</pre></body></html>")
        assertEquals(listOf("Recipe", "Soup", "Boil water.", "a", "  b"), n.texts())
        assertEquals(BlockType.HEADING, n.blocks[1].type)
        assertEquals(BlockType.MONOSPACED, n.blocks[4].type)
    }

    @Test
    fun `broken files are counted and other files are ignored`() {
        val r = parseImport(source("a.json" to "{not json", "b.json" to """{"some":"other app"}""", "pic.png" to "x"))
        assertEquals(0, r.notes.size)
        assertEquals(0, r.failed)
        assertNull(tagLine(listOf("123", "  ")))
    }
}
