package com.margin.app.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.margin.app.MainActivity
import com.margin.app.R
import com.margin.app.data.BlockType
import com.margin.app.data.NoteEntity
import com.margin.app.data.NoteRepository
import com.margin.app.data.displayTitle
import com.margin.app.data.isLocked
import com.margin.app.data.isObject
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Home-screen note widget: one note's title and text, opened with a tap. Like [ListWidget] it keeps no data:
 * Margin pushes fresh views whenever notes change. Each widget remembers its note; the title changes it.
 */
@AndroidEntryPoint
class NoteWidget : AppWidgetProvider() {
    @Inject lateinit var repo: NoteRepository

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val done = goAsync()
        CoroutineScope(Dispatchers.IO).launch { try { push(context, repo.liveNotes().first()) } finally { done.finish() } }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        prefs(context).edit().apply { ids.forEach { remove(key(it)) } }.apply()
    }

    companion object {
        private fun prefs(context: Context) = context.getSharedPreferences("note_widget", Context.MODE_PRIVATE)
        private fun key(widgetId: Int) = "note_$widgetId"

        fun choose(context: Context, widgetId: Int, noteId: String) = prefs(context).edit().putString(key(widgetId), noteId).apply()

        /** Notes a widget can show: the ones All Notes lists, unlocked. */
        fun choices(notes: List<NoteEntity>) = notes.filter { inAllNotes(it) && !it.isLocked }

        /** Redraws every note widget from [notes] (the live notes). */
        fun push(context: Context, notes: List<NoteEntity>) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NoteWidget::class.java))
            for (id in ids) {
                val chosen = prefs(context).getString(key(id), null)
                manager.updateAppWidget(id, views(context, id, notes.firstOrNull { it.id == chosen }))
            }
        }

        /** The note's text as the widget shows it: ticks for checklists, bullets for lists, no attachments. */
        fun text(note: NoteEntity): String = note.blocks.drop(1).filter { !it.isObject }.joinToString("\n") { b ->
            when (b.type) {
                BlockType.CHECK -> (if (b.checked) "☑ " else "☐ ") + b.text
                BlockType.BULLET, BlockType.DASHED -> "• " + b.text
                else -> b.text
            }
        }.trim()

        private fun views(context: Context, widgetId: Int, note: NoteEntity?): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_note)
            // Locked, archived or deleted since it was picked: ask for another, and show nothing of it.
            val shown = note?.takeIf { it.trashedAt == null && !it.archived && !it.isLocked }
            v.setTextViewText(R.id.note_title, shown?.displayTitle ?: context.getString(R.string.note_widget_pick))
            val body = if (shown != null && com.margin.app.data.appLockOn(context)) context.getString(R.string.widget_open_margin_to_see) // App Lock: title only
                else shown?.let(::text)?.ifEmpty { null } ?: context.getString(if (shown == null) R.string.note_widget_empty else R.string.widget_no_text)
            v.setTextViewText(R.id.note_body, body)
            v.setOnClickPendingIntent(R.id.note_pick, PendingIntent.getActivity(
                context, widgetId,
                Intent(context, ListWidgetConfigActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ))
            val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (shown != null) open.setAction(RecentWidget.ACTION_OPEN).putExtra(RecentWidget.EXTRA_NOTE, shown.id).putExtra(RecentWidget.EXTRA_FOLDER, shown.folderId)
            val tap = PendingIntent.getActivity(context, 10_000 + widgetId, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            v.setOnClickPendingIntent(R.id.note_logo, tap)
            v.setOnClickPendingIntent(R.id.note_body, tap)
            return v
        }
    }
}
