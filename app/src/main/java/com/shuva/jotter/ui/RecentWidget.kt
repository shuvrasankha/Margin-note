package com.shuva.jotter.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.shuva.jotter.MainActivity
import com.shuva.jotter.R
import com.shuva.jotter.data.NoteEntity
import com.shuva.jotter.data.NoteRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.shuva.jotter.data.displayTitle
import com.shuva.jotter.data.isLocked
import com.shuva.jotter.data.lastEdited

/**
 * Home-screen widget with the three latest notes and a new-note button. It has no data of its own: Jotter
 * pushes it a fresh view whenever the notes change ([JotterApp] watches them, in the app or a background sync),
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
        const val ACTION_OPEN = "com.shuva.jotter.OPEN_NOTE"
        const val ACTION_NEW = "com.shuva.jotter.NEW_NOTE"
        const val EXTRA_NOTE = "note"
        const val EXTRA_FOLDER = "folder"
        private val ROWS = listOf(
            Triple(R.id.row0, R.id.title0, R.id.body0),
            Triple(R.id.row1, R.id.title1, R.id.body1),
            Triple(R.id.row2, R.id.title2, R.id.body2),
        )

        /** Shows the latest of [notes] (live notes) on every Jotter widget. */
        fun push(context: Context, notes: List<NoteEntity>) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, RecentWidget::class.java))
            if (ids.isEmpty()) return
            val latest = notes.sortedByDescending { it.lastEdited }.take(ROWS.size)
            val v = RemoteViews(context.packageName, R.layout.widget_recent)
            v.setOnClickPendingIntent(R.id.new_note, open(context, 0, Intent(ACTION_NEW)))
            v.setViewVisibility(R.id.empty, if (latest.isEmpty()) View.VISIBLE else View.GONE)
            ROWS.forEachIndexed { i, (row, title, body) ->
                val n = latest.getOrNull(i)
                // Empty rows stay (invisible) so the shown ones keep their height.
                v.setViewVisibility(row, if (n != null) View.VISIBLE else if (latest.isEmpty()) View.GONE else View.INVISIBLE)
                if (n == null) return@forEachIndexed
                v.setTextViewText(title, n.displayTitle)
                // The same second line as the note list.
                v.setTextViewText(body, if (n.isLocked) "Locked" else n.searchText.lineSequence().firstOrNull { it.isNotBlank() } ?: "No additional text")
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
