package com.margin.app.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.margin.app.MainActivity
import com.margin.app.R
import com.margin.app.data.NoteEntity
import com.margin.app.data.NoteRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.margin.app.data.displayTitle
import com.margin.app.data.isLocked
import com.margin.app.data.lastEdited

/**
 * Home-screen widget with the three latest notes and a new-note button. It has no data of its own: Margin
 * pushes it a fresh view whenever the notes change ([MarginApp] watches them, in the app or a background sync),
 * and once here when a widget is added.
 */
@AndroidEntryPoint
class RecentWidget : AppWidgetProvider() {
    @Inject lateinit var repo: NoteRepository

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val done = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { push(context, repo.liveNotes().first()) } finally { done.finish() }
        }
    }

    companion object {
        const val ACTION_OPEN = "com.margin.app.OPEN_NOTE"
        const val ACTION_NEW = "com.margin.app.NEW_NOTE"
        /** App shortcuts: a new note that opens the document scanner or starts recording. */
        const val ACTION_SCAN = "com.margin.app.SCAN"
        const val ACTION_VOICE = "com.margin.app.VOICE_NOTE"
        /** The app shortcut that opens Passwords. */
        const val ACTION_PASSWORDS = "com.margin.app.PASSWORDS"
        const val EXTRA_NOTE = "note"
        const val EXTRA_FOLDER = "folder"
        private val ROWS = listOf(
            Triple(R.id.row0, R.id.title0, R.id.body0),
            Triple(R.id.row1, R.id.title1, R.id.body1),
            Triple(R.id.row2, R.id.title2, R.id.body2),
        )

        /** Shows the latest of [notes] (live notes) on every Margin widget. */
        fun push(context: Context, notes: List<NoteEntity>) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, RecentWidget::class.java))
            if (ids.isEmpty()) return
            val latest = notes.filter { !it.archived }.sortedByDescending { it.lastEdited }.take(ROWS.size)
            val v = RemoteViews(context.packageName, R.layout.widget_recent)
            // + writes a note in a popup over the home screen; the app stays closed.
            v.setOnClickPendingIntent(R.id.new_note, PendingIntent.getActivity(
                context, 0, Intent(context, QuickNoteActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ))
            v.setViewVisibility(R.id.empty, if (latest.isEmpty()) View.VISIBLE else View.GONE)
            ROWS.forEachIndexed { i, (row, title, body) ->
                val n = latest.getOrNull(i)
                // Empty rows stay (invisible) so the shown ones keep their height.
                v.setViewVisibility(row, if (n != null) View.VISIBLE else if (latest.isEmpty()) View.GONE else View.INVISIBLE)
                if (n == null) return@forEachIndexed
                v.setTextViewText(title, n.displayTitle)
                // The same second line as the note list.
                v.setTextViewText(body, if (n.isLocked) context.getString(R.string.widget_recent_locked) else n.searchText.lineSequence().firstOrNull { it.isNotBlank() } ?: context.getString(R.string.notes_snippet_no_text))
                v.setOnClickPendingIntent(row, open(context, i + 1, Intent(ACTION_OPEN).putExtra(EXTRA_NOTE, n.id).putExtra(EXTRA_FOLDER, n.folderId)))
            }
            manager.updateAppWidget(ids, v)
        }

        private fun open(context: Context, code: Int, intent: Intent) = PendingIntent.getActivity(
            context, code, intent.setClass(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
