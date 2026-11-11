package com.shuva.jotter.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import java.util.UUID

/** Fixed ID so every device agrees on the one default "Notes" folder. */
const val DEFAULT_FOLDER_ID = "00000000-0000-0000-0000-000000000001"
const val DEFAULT_FOLDER_NAME = "Notes"
const val TRASH_RETENTION_MS = 30L * 24 * 60 * 60 * 1000

fun newId(): String = UUID.randomUUID().toString()

val JotterJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** How a folder orders its notes. Pinned notes always come first. */
@Serializable
enum class NoteSort { EDITED, CREATED, TITLE }

@Serializable
enum class BlockType { TITLE, HEADING, SUBHEADING, BODY, MONOSPACED, BULLET, DASHED, NUMBERED, CHECK }

/**
 * Auto-sort for checklists: stable-sorts the unbroken run of checklist lines around [index] so
 * unchecked lines come first and checked lines last. Order within each group is kept.
 */
fun <T> List<T>.sortCheckedRun(index: Int, isCheck: (T) -> Boolean, isChecked: (T) -> Boolean): List<T> {
    if (index !in indices || !isCheck(this[index])) return this
    var start = index
    while (start > 0 && isCheck(this[start - 1])) start--
    var end = index
    while (end < lastIndex && isCheck(this[end + 1])) end++
    val run = subList(start, end + 1)
    return subList(0, start) + run.filterNot(isChecked) + run.filter(isChecked) + subList(end + 1, size)
}

/**
 * A smart folder's filter (schema v3). Every set condition must match, like the source's "and" query
 * (SPEC 3.9). Notes in Recently Deleted never match.
 */
@Serializable
data class SmartFilter(
    /** Tag keys (see [tagKey]). Empty = any note. */
    val tags: List<String> = emptyList(),
    /** True: the note needs every tag. False: any one of them. */
    val allTags: Boolean = false,
    val hasChecklist: Boolean = false,
    val pinnedOnly: Boolean = false,
    /** Only notes with a photo, file, drawing or voice note. A locked note's attachments can't be seen. */
    val hasAttachments: Boolean = false,
    /** Only notes edited in the last N days; null = no limit. */
    val editedWithinDays: Int? = null,
)

fun SmartFilter.matches(note: NoteEntity, now: Long): Boolean {
    if (note.deleted || note.trashedAt != null) return false
    if (tags.isNotEmpty()) {
        val keys = note.tagKeys
        if (if (allTags) !keys.containsAll(tags) else tags.none { it in keys }) return false
    }
    if (hasChecklist && note.blocks.none { it.type == BlockType.CHECK }) return false
    if (pinnedOnly && !note.isPinned) return false
    if (hasAttachments && note.blocks.none { it.attachment != null }) return false
    if (editedWithinDays != null && now - note.lastEdited > editedWithinDays * 24L * 60 * 60 * 1000) return false
    return true
}

/** One line of a note. The first block is always the title. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Block(
    val id: String = newId(),
    val type: BlockType = BlockType.BODY,
    val text: String = "",
    val checked: Boolean = false,
    /** Bold, italic, ... ranges in [text]. Left out of the JSON when empty, so unstyled notes store as before. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val marks: List<MarkSpan> = emptyList(),
    /** A photo or file shown as this whole line; [text] is then [ATTACHMENT_CHAR]. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val attachment: Attachment? = null,
    /** Shown as a quote: a bar on the left. Older versions don't know it and show plain text. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val quote: Boolean = false,
    /** Indent steps, 0 to [MAX_INDENT]. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val indent: Int = 0,
    /** A table shown as this whole line; [text] is then [ATTACHMENT_CHAR]. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val table: NoteTable? = null,
)

/** True for a line that is one card (attachment or table) rather than text. */
val Block.isObject get() = attachment != null || table != null

const val MAX_INDENT = 4

/** A file stored in app storage as [file]; [name] is what the user picked, [mime] its type. */
@Serializable
data class Attachment(val file: String, val name: String, val mime: String) {
    val isImage get() = mime.startsWith("image/")
}

/** Stands in for an attachment in the note text, so it takes one character the cursor can step over. */
const val ATTACHMENT_CHAR = '\uFFFC'

/** What search, previews and sharing see for a line: an attachment's file name, else the text. */
val Block.plainText get() = attachment?.name ?: table?.plainText ?: text

/**
 * Room row and Drive JSON record are the same shape. [syncedUpdatedAt] is local-only: the
 * [updatedAt] both sides agreed on at the last successful sync (null = never synced).
 * A record is waiting in the offline queue whenever updatedAt != syncedUpdatedAt.
 */
@Serializable
@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isDefault: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    @Transient val syncedUpdatedAt: Long? = null,
    /** Per-folder sort (added in schema v2; synced with the folder). */
    @ColumnInfo(defaultValue = "EDITED") val sortBy: NoteSort = NoteSort.EDITED,
    @ColumnInfo(defaultValue = "0") val sortAscending: Boolean = false,
    /** Folder this one sits in (schema v3); null = top level. */
    val parentId: String? = null,
    /** Non-null makes this a smart folder (schema v3): it holds no notes, it shows the notes that match. */
    val smartFilter: SmartFilter? = null,
    /** Position among the folders it's listed with (schema v4); ties go A to Z. Set by Edit folders. */
    @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0,
)

val FolderEntity.isSmart get() = smartFilter != null

@Serializable
@Entity(tableName = "notes", indices = [Index("folderId")])
data class NoteEntity(
    @PrimaryKey val id: String,
    val folderId: String,
    val blocks: List<Block>,
    val title: String,
    val searchText: String,
    val isPinned: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    /** Non-null = in Recently Deleted since this time. */
    val trashedAt: Long? = null,
    /** Tombstone: permanently deleted, kept so the deletion syncs. */
    val deleted: Boolean = false,
    @Transient val syncedUpdatedAt: Long? = null,
    /**
     * When the text last changed (schema v2). Unlike [updatedAt], pinning, deleting and recovering don't
     * touch it. 0 = written by an older version; use [lastEdited], which falls back to [updatedAt].
     */
    @ColumnInfo(defaultValue = "0") val editedAt: Long = 0,
    /**
     * Locked note (schema v3): the body, everything after the title line, encrypted with the notes password
     * (see NoteCrypto). [blocks] then holds only the title, and [searchText] is empty.
     */
    val cipher: String? = null,
    /**
     * To-do list target end date (schema v5), as a day number (LocalDate.toEpochDay), so it means the same
     * calendar day on every phone. Null = none. Older versions ignore it when syncing.
     */
    val targetDate: Long? = null,
)

val NoteEntity.isLocked get() = cipher != null

val NoteEntity.isDirty get() = updatedAt != syncedUpdatedAt
val FolderEntity.isDirty get() = updatedAt != syncedUpdatedAt

val NoteEntity.lastEdited get() = if (editedAt > 0) editedAt else updatedAt

val NoteEntity.displayTitle get() = title.ifBlank { "New Note" }
val NoteEntity.snippet get() = searchText.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()

fun NoteEntity.withBlocks(blocks: List<Block>, now: Long) = copy(
    blocks = blocks,
    title = blocks.firstOrNull()?.text?.trim().orEmpty(),
    searchText = blocks.drop(1).joinToString("\n") { it.plainText },
    updatedAt = now,
    editedAt = now,
)

fun NoteEntity.isTrashExpired(now: Long, retentionMs: Long = TRASH_RETENTION_MS) =
    !deleted && trashedAt != null && now - trashedAt >= retentionMs

fun NoteEntity.toTombstone(now: Long) = copy(
    deleted = true, blocks = emptyList(), title = "", searchText = "", isPinned = false, cipher = null, updatedAt = now,
)
