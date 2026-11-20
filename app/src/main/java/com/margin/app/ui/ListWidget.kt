package com.margin.app.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.core.widget.RemoteViewsCompat
import com.margin.app.MainActivity
import com.margin.app.R
import com.margin.app.data.ListWidgetData
import com.margin.app.data.NoteEntity
import com.margin.app.data.NoteRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Home-screen list widget: one to-do or shopping list, ticked off with a tap, plus voice and typed add. Like
 * [RecentWidget] it keeps no data: Margin pushes it fresh views whenever notes change. Each widget remembers
 * which list it shows; until one is picked it shows a shopping list (or the latest list).
 */
@AndroidEntryPoint
class ListWidget : AppWidgetProvider() {
    @Inject lateinit var repo: NoteRepository

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = async { push(context, repo.liveNotes().first()) }

    override fun onDeleted(context: Context, ids: IntArray) {
        prefs(context).edit().apply { ids.forEach { remove(key(it)) } }.apply()
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent) // also where Hilt injects [repo]; other actions it ignores
        // After an app update the launcher shows the bare layout until it's sent fresh views.
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) return async { push(context, repo.liveNotes().first()) }
        if (intent.action != ACTION_TOGGLE) return
        val noteId = intent.getStringExtra(EXTRA_NOTE) ?: return
        val blockId = intent.getStringExtra(EXTRA_BLOCK) ?: return
        async {
            // Saving fires the app's note watcher, which pushes the new views; push here too in case it isn't running.
            repo.toggleTask(noteId, blockId)
            push(context, repo.liveNotes().first())
        }
    }

    private fun async(work: suspend () -> Unit) {
        val done = goAsync()
        CoroutineScope(Dispatchers.IO).launch { try { work() } finally { done.finish() } }
    }

    companion object {
        private const val ACTION_TOGGLE = "com.margin.app.LIST_WIDGET_TOGGLE"
        private const val EXTRA_NOTE = "note"
        private const val EXTRA_BLOCK = "block"

        private fun prefs(context: Context) = context.getSharedPreferences("list_widget", Context.MODE_PRIVATE)
        private fun key(widgetId: Int) = "list_$widgetId"

        /** Shows the list [noteId] on widget [widgetId]. */
        fun choose(context: Context, widgetId: Int, noteId: String) = prefs(context).edit().putString(key(widgetId), noteId).apply()

        /** Redraws every list widget from [notes] (the live notes). */
        fun push(context: Context, notes: List<NoteEntity>) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ListWidget::class.java))
            for (id in ids) manager.updateAppWidget(id, views(context, id, ListWidgetData.pick(notes, prefs(context).getString(key(id), null))))
        }

        private fun views(context: Context, widgetId: Int, list: NoteEntity?): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_list)
            if (list == null) {
                // No list yet: the title picks one, and + or the mic starts a shopping list.
                v.setTextViewText(R.id.list_title, context.getString(R.string.list_widget_pick))
                v.setOnClickPendingIntent(R.id.list_title, configure(context, widgetId))
            } else {
                v.setTextViewText(R.id.list_title, list.title.ifBlank { "List" })
                v.setOnClickPendingIntent(R.id.list_title, PendingIntent.getActivity(
                    context, widgetId,
                    Intent(context, MainActivity::class.java).setAction(RecentWidget.ACTION_OPEN)
                        .putExtra(RecentWidget.EXTRA_NOTE, list.id).putExtra(RecentWidget.EXTRA_FOLDER, list.folderId)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ))
            }
            val listName = list?.title?.ifBlank { null } ?: "Shopping list"
            v.setOnClickPendingIntent(R.id.list_voice, add(context, widgetId * 2, listName, typed = false))
            v.setOnClickPendingIntent(R.id.list_add, add(context, widgetId * 2 + 1, listName, typed = true))

            val tasks = list?.let(ListWidgetData::tasks).orEmpty()
            v.setViewVisibility(R.id.list_empty, if (tasks.isEmpty()) View.VISIBLE else View.GONE)
            v.setViewVisibility(R.id.list_items, if (tasks.isEmpty()) View.GONE else View.VISIBLE)
            if (list != null) {
                v.setPendingIntentTemplate(R.id.list_items, PendingIntent.getBroadcast(
                    context, widgetId, Intent(context, ListWidget::class.java).setAction(ACTION_TOGGLE),
                    // Mutable: each row fills in its note and task.
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                ))
            }
            val items = RemoteViewsCompat.RemoteCollectionItems.Builder().setHasStableIds(true).setViewTypeCount(1)
            tasks.forEach { t ->
                val row = RemoteViews(context.packageName, R.layout.widget_list_item)
                row.setTextViewText(R.id.item_text, if (!t.done) t.text else android.text.SpannableString(t.text).apply {
                    setSpan(android.text.style.StrikethroughSpan(), 0, length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                })
                row.setImageViewResource(R.id.item_check, if (t.done) R.drawable.widget_check_on else R.drawable.widget_check_off)
                row.setTextColor(R.id.item_text, context.getColor(if (t.done) R.color.widget_muted else R.color.widget_ink))
                row.setContentDescription(R.id.item, (if (t.done) "Done: " else "") + t.text)
                row.setOnClickFillInIntent(R.id.item, Intent().putExtra(EXTRA_NOTE, list!!.id).putExtra(EXTRA_BLOCK, t.blockId))
                items.addItem(t.blockId.hashCode().toLong(), row)
            }
            RemoteViewsCompat.setRemoteAdapter(context, v, widgetId, R.id.list_items, items.build())
            return v
        }

        /** The mic or + : the voice command card, aimed at [listName] (listening, or typing when [typed]). */
        private fun add(context: Context, code: Int, listName: String, typed: Boolean) = PendingIntent.getActivity(
            context, code,
            Intent(context, VoiceCommandActivity::class.java).setAction(VoiceCommandActivity.ACTION)
                .putExtra(VoiceCommandActivity.EXTRA_LIST, listName).putExtra(VoiceCommandActivity.EXTRA_TYPED, typed)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private fun configure(context: Context, widgetId: Int) = PendingIntent.getActivity(
            context, widgetId,
            Intent(context, ListWidgetConfigActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
