package com.margin.app.data

/** One task as the list widget shows it. */
data class WidgetTask(val blockId: String, val text: String, val done: Boolean)

/** What the list widget needs to know about lists, apart from Android. */
object ListWidgetData {
    /** Names that make a list the widget's first pick when none was chosen. */
    private val SHOPPING = setOf("shopping", "grocery", "groceries")

    /** The tasks of [note]: open ones first, then done ones, each in the note's order. */
    fun tasks(note: NoteEntity): List<WidgetTask> = note.blocks
        .filter { it.type == BlockType.CHECK && it.attachment == null && it.text.isNotBlank() }
        .map { WidgetTask(it.id, it.text, it.checked) }
        .sortedBy { it.done }

    /** The lists the widget can show: live, unlocked notes with tasks, newest edit first. */
    fun lists(notes: List<NoteEntity>): List<NoteEntity> = notes
        .filter { !it.deleted && it.trashedAt == null && it.cipher == null && !it.id.startsWith(LOGIN_PREFIX) && todoCount(it.blocks).total > 0 }
        .sortedByDescending { it.updatedAt }

    /** The list for a widget: the [chosen] one while it's still around, else a shopping list, else the latest list. */
    fun pick(notes: List<NoteEntity>, chosen: String?): NoteEntity? {
        val live = notes.filter { !it.deleted && it.trashedAt == null && it.cipher == null }
        chosen?.let { id -> live.firstOrNull { it.id == id }?.let { return it } }
        val lists = lists(notes)
        return lists.firstOrNull { VoiceCommands.listKey(it.title) in SHOPPING } ?: lists.firstOrNull()
    }

    /** [blocks] with the task [blockId] ticked or unticked. */
    fun toggled(blocks: List<Block>, blockId: String): List<Block> =
        blocks.map { if (it.id == blockId && it.type == BlockType.CHECK) it.copy(checked = !it.checked) else it }
}
