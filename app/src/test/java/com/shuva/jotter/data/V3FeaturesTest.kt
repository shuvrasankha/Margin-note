package com.shuva.jotter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V3FeaturesTest {
    // ---- Locked notes
    private val body = listOf(Block(text = "secret line"), Block(type = BlockType.CHECK, text = "item", checked = true))

    @Test
    fun `sealed body opens with the right password only`() {
        val params = NoteCrypto.KeyParams(NoteCrypto.newSalt(), 1000) // few iterations keeps the test fast
        val sealed = NoteCrypto.seal(body, NoteCrypto.deriveKey("pass1".toCharArray(), params.salt, 1000), params)
        assertFalse(sealed.contains("secret"))
        assertEquals(params, NoteCrypto.paramsOf(sealed))
        val right = NoteCrypto.deriveKey("pass1".toCharArray(), NoteCrypto.paramsOf(sealed).salt, 1000)
        assertEquals(body, NoteCrypto.open(sealed, right))
        val wrong = NoteCrypto.deriveKey("pass2".toCharArray(), params.salt, 1000)
        assertNull(NoteCrypto.open(sealed, wrong))
    }

    @Test
    fun `sealing twice gives different text (fresh IV)`() {
        val params = NoteCrypto.KeyParams(NoteCrypto.newSalt(), 1000)
        val key = NoteCrypto.deriveKey("p".toCharArray(), params.salt, 1000)
        assertNotEquals(NoteCrypto.seal(body, key, params), NoteCrypto.seal(body, key, params))
    }

    @Test
    fun `a locked note keeps only its title readable and its tombstone drops the cipher`() {
        val note = NoteEntity(id = "n", folderId = "f", blocks = emptyList(), title = "", searchText = "", createdAt = 1, updatedAt = 1)
            .withBlocks(listOf(Block(type = BlockType.TITLE, text = "Bank #money")), 2).copy(cipher = "j1.x")
        assertTrue(note.isLocked)
        assertEquals("", note.searchText)
        assertEquals(setOf("money"), note.tagKeys) // tags in the title still count
        assertNull(note.toTombstone(3).cipher)
    }

    // ---- Nested folders
    private fun folder(id: String, parent: String? = null, smart: SmartFilter? = null, deleted: Boolean = false) =
        FolderEntity(id, id, isDefault = id == "default", createdAt = 0, updatedAt = 0, parentId = parent, smartFilter = smart, deleted = deleted)

    @Test
    fun `tree shows subfolders under their parent, smart folders last`() {
        val tree = folderTree(listOf(folder("b"), folder("a2", "a"), folder("a"), folder("default"), folder("a1", "a"), folder("s", smart = SmartFilter()), folder("x", "a1")))
        assertEquals(listOf("default" to 0, "a" to 0, "a1" to 1, "x" to 2, "a2" to 1, "b" to 0, "s" to 0), tree.map { it.folder.id to it.depth })
    }

    @Test
    fun `missing parents, smart parents and loops put a folder at the top`() {
        val folders = listOf(
            folder("orphan", "gone"), folder("dead", deleted = true), folder("underDead", "dead"),
            folder("s", smart = SmartFilter()), folder("inSmart", "s"),
            folder("p", "q"), folder("q", "p"), folder("child", "p"),
        )
        val parents = effectiveParents(folders)
        assertNull(parents["orphan"]); assertNull(parents["underDead"]); assertNull(parents["inSmart"])
        assertNull(parents["p"]); assertNull(parents["q"])
        assertEquals("p", parents["child"]) // a loop above it doesn't detach it
        assertEquals(setOf("p", "child"), subtreeIds(folders, "p"))
    }

    // ---- Smart folders
    private val now = 100L * 24 * 60 * 60 * 1000
    private fun note(text: String, pinned: Boolean = false, editedDaysAgo: Int = 0, check: Boolean = false, trashed: Boolean = false) =
        NoteEntity(
            id = text, folderId = "f", title = "", searchText = "", isPinned = pinned, createdAt = 0, updatedAt = 0,
            blocks = listOf(Block(type = BlockType.TITLE, text = text)) + if (check) listOf(Block(type = BlockType.CHECK, text = "x")) else emptyList(),
            editedAt = now - editedDaysAgo * 24L * 60 * 60 * 1000, trashedAt = if (trashed) 1 else null,
        )

    @Test
    fun `every set condition must match`() {
        val notes = listOf(note("#work #urgent"), note("#work", pinned = true), note("#home", check = true), note("old #work", editedDaysAgo = 40), note("#work bin", trashed = true))
        fun ids(f: SmartFilter) = notes.filter { f.matches(it, now) }.map { it.id }
        assertEquals(listOf("#work #urgent", "#work", "#home", "old #work"), ids(SmartFilter()))
        assertEquals(listOf("#work #urgent", "#work", "old #work"), ids(SmartFilter(tags = listOf("work"))))
        assertEquals(listOf("#work #urgent", "#work", "#home", "old #work"), ids(SmartFilter(tags = listOf("work", "home"))))
        assertEquals(listOf("#work #urgent"), ids(SmartFilter(tags = listOf("work", "urgent"), allTags = true)))
        assertEquals(listOf("#work"), ids(SmartFilter(tags = listOf("work"), pinnedOnly = true)))
        assertEquals(listOf("#home"), ids(SmartFilter(hasChecklist = true)))
        assertEquals(listOf("#work #urgent", "#work"), ids(SmartFilter(tags = listOf("work"), editedWithinDays = 30)))
    }

    @Test
    fun `new fields round-trip through sync JSON, and old records still read`() {
        val f = folder("s", parent = null, smart = SmartFilter(tags = listOf("a"), editedWithinDays = 7))
        assertEquals(f, JotterJson.decodeFromString<FolderEntity>(JotterJson.encodeToString(f)))
        val old = """{"id":"o","name":"Old","createdAt":1,"updatedAt":2}"""
        val parsed = JotterJson.decodeFromString<FolderEntity>(old)
        assertNull(parsed.parentId); assertNull(parsed.smartFilter)
    }

    @Test
    fun `has attachments rule and trash retention`() {
        val photo = note("pic").let { it.copy(blocks = it.blocks + Block(text = "\uFFFC", attachment = Attachment("f", "p.jpg", "image/jpeg"))) }
        assertEquals(listOf("pic"), listOf(note("plain"), photo).filter { SmartFilter(hasAttachments = true).matches(it, now) }.map { it.id })
        val day = 24L * 60 * 60 * 1000
        val trashed = note("bin").copy(trashedAt = now - 10 * day)
        assertEquals(false, trashed.isTrashExpired(now)) // 30 days by default
        assertEquals(true, trashed.isTrashExpired(now, 7 * day))
    }

    @Test
    fun `sort order beats names within a group, default stays first`() {
        val folders = listOf(
            folder("default"), folder("b").copy(sortOrder = 0), folder("a").copy(sortOrder = 1), folder("c").copy(sortOrder = 1),
            folder("b2", parent = "b"), folder("b1", parent = "b").copy(sortOrder = 5), folder("s", smart = SmartFilter()),
        )
        assertEquals(listOf("default", "b", "b2", "b1", "a", "c", "s"), folderTree(folders).map { it.folder.id })
        val parents = effectiveParents(folders)
        assertEquals(null, reorderGroup(folders[0], parents))
        assertEquals("in:null", reorderGroup(folders[1], parents))
        assertEquals("in:b", reorderGroup(folders[4], parents))
        assertEquals("smart", reorderGroup(folders[6], parents))
    }
}
