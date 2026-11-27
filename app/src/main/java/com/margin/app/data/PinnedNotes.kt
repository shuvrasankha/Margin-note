package com.margin.app.data

import com.margin.app.R

import android.content.Context
import com.margin.app.ui.PinReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** What a note pinned to the notification shade shows. */
data class PinContent(
    val title: String,
    /** The note's text, or a list's tasks still to do. */
    val lines: List<String>,
    /** "2 of 5 done" for a list. */
    val summary: String?,
    /** The first task still to do, ticked from the notification: its block id and text. */
    val next: Pair<String, String>?,
    val locked: Boolean,
)

private const val MAX_LINES = 8

/** The words [pinContent] shows, in the app's language; English by default. */
data class PinWords(
    val lockedNote: String = "Locked note",
    val allDone: String = "All done",
    val noText: String = "No additional text",
    val doneOf: (done: Int, total: Int) -> String = { d, t -> "$d of $t done" },
)

fun pinContent(note: NoteEntity, words: PinWords = PinWords()): PinContent {
    if (note.isLocked) return PinContent(note.displayTitle, listOf(words.lockedNote), null, null, locked = true)
    val count = todoCount(note.blocks)
    if (count.total > 0) {
        val open = note.blocks.filter { it.type == BlockType.CHECK && !it.checked && it.attachment == null && it.text.isNotBlank() }
        return PinContent(
            note.displayTitle,
            if (open.isEmpty()) listOf(words.allDone) else open.take(MAX_LINES).map { "○ " + it.text.trim() },
            words.doneOf(count.done, count.total),
            open.firstOrNull()?.let { it.id to it.text.trim() },
            locked = false,
        )
    }
    val body = note.blocks.drop(1).map { it.text.trim() }.filter { it.isNotEmpty() }
    return PinContent(note.displayTitle, body.take(MAX_LINES).ifEmpty { listOf(words.noText) }, null, null, locked = false)
}

/**
 * Notes kept in the notification shade as an ongoing notification (a list for the shop, where the car is,
 * a door code), on this device only. [refresh] keeps them current as the notes change.
 */
@Singleton
class PinnedNotes @Inject constructor(@ApplicationContext private val context: Context) {
    private val prefs = context.getSharedPreferences("pinned_notifications", Context.MODE_PRIVATE)
    private val _ids = MutableStateFlow(prefs.getStringSet(KEY, emptySet())!!.toSet())
    val ids: StateFlow<Set<String>> = _ids
    /** What each pinned note last showed, so unrelated edits don't repost it. */
    private val shown = mutableMapOf<String, PinContent>()

    fun isPinned(id: String) = id in _ids.value

    fun pin(note: NoteEntity) {
        save(_ids.value + note.id)
        show(note)
    }

    fun unpin(id: String) {
        save(_ids.value - id)
        synchronized(shown) { shown.remove(id) }
        PinReceiver.cancel(context, id)
    }

    /** With every note change (and at app start, which puts them back after a restart). */
    fun refresh(notes: List<NoteEntity>) {
        if (_ids.value.isEmpty()) return
        val byId = notes.associateBy { it.id }
        for (id in _ids.value) {
            val note = byId[id]
            // Deleted or in Recently Deleted: no longer pinned.
            if (note == null) unpin(id) else show(note)
        }
    }

    private fun show(note: NoteEntity) {
        val content = pinContent(note, PinWords(
            lockedNote = context.getString(R.string.pin_locked_note),
            allDone = context.getString(R.string.pin_all_done),
            noText = context.getString(R.string.notes_snippet_no_text),
            doneOf = { d, t -> context.getString(R.string.pin_done_of, d.toString(), t.toString()) },
        ))
        synchronized(shown) {
            if (shown[note.id] == content) return
            shown[note.id] = content
        }
        PinReceiver.post(context, note.id, note.folderId, content)
    }

    private fun save(ids: Set<String>) {
        prefs.edit().putStringSet(KEY, ids).apply()
        _ids.value = ids
    }

    private companion object { const val KEY = "ids" }
}
