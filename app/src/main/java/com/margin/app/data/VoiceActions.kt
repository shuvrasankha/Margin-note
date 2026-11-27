package com.margin.app.data

import kotlinx.coroutines.flow.first
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Carries out a [VoiceCommand]: a reminder note, tasks on a to-do list, or a plain note, all in Notes. */
@Singleton
class VoiceActions @Inject constructor(private val repo: NoteRepository, private val reminders: Reminders) {
    /** The note the command made or changed. */
    data class Done(val noteId: String, val folderId: String)

    val remindersOnTime get() = reminders.canRingOnTime()

    suspend fun run(command: VoiceCommand): Done {
        repo.ensureDefaultFolder()
        return when (command) {
            is VoiceCommand.Remind -> {
                val id = newId()
                repo.saveNote(id, DEFAULT_FOLDER_ID, sharedNoteBlocks(command.what, null, null))
                reminders.set(id, command.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), command.repeat)
                Done(id, DEFAULT_FOLDER_ID)
            }
            is VoiceCommand.AddToList -> {
                // Read fresh and save once, so every item lands on the list.
                val list = findList(repo.liveNotes().first(), command.list)
                if (list != null) {
                    repo.saveNote(list.id, list.folderId, command.items.fold(list.blocks) { blocks, item -> withTask(blocks, item) })
                    Done(list.id, list.folderId)
                } else {
                    val id = newId()
                    repo.saveNote(id, DEFAULT_FOLDER_ID, listOf(Block(type = BlockType.TITLE, text = command.list)) + command.items.map { Block(type = BlockType.CHECK, text = it) })
                    Done(id, DEFAULT_FOLDER_ID)
                }
            }
            is VoiceCommand.Note -> {
                val id = newId()
                repo.saveNote(id, DEFAULT_FOLDER_ID, sharedNoteBlocks(null, command.text, null))
                Done(id, DEFAULT_FOLDER_ID)
            }
        }
    }

    companion object {
        /** Notes that hold something other than notes (passwords, habits, trackers, journal days). */
        private val SPECIAL = listOf(LOGIN_PREFIX, "habit-", TRACKER_PREFIX, "journal-")

        /**
         * The note [title] names among [notes]: a live, unlocked, ordinary note whose title means the same list
         * ("Shopping" for "Shopping list"); one with tasks first, then the latest edited.
         */
        fun findList(notes: List<NoteEntity>, title: String): NoteEntity? {
            val key = VoiceCommands.listKey(title)
            return notes.filter { n ->
                !n.deleted && n.trashedAt == null && n.cipher == null && SPECIAL.none { n.id.startsWith(it) } &&
                    VoiceCommands.listKey(n.title) == key
            }.sortedWith(compareByDescending<NoteEntity> { todoCount(it.blocks).total > 0 }.thenByDescending { it.updatedAt }).firstOrNull()
        }
    }
}
