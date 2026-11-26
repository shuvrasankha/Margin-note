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
import com.margin.app.data.NoteEntity
import com.margin.app.data.NoteRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/**
 * Home-screen Today widget: open to-dos, habits left and whether today's journal is written; each row opens
 * its screen. Pushed when notes change, and every 30 minutes so a new day shows without opening Margin.
 */
@AndroidEntryPoint
class TodayWidget : AppWidgetProvider() {
    @Inject lateinit var repo: NoteRepository

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val done = goAsync()
        CoroutineScope(Dispatchers.IO).launch { try { push(context, repo.liveNotes().first()) } finally { done.finish() } }
    }

    companion object {
        const val ACTION_TODAY = "com.margin.app.TODAY"

        fun push(context: Context, notes: List<NoteEntity>) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidget::class.java))
            if (ids.isEmpty()) return
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val g = todayGlance(notes.filter { !it.archived }, emptyMap(), today, zone, {}, {}, {})
            val v = RemoteViews(context.packageName, R.layout.widget_today)
            v.setTextViewText(R.id.today_date, today.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())))
            v.setTextViewText(R.id.today_todos, when {
                g.openTodos == 0 -> context.getString(R.string.widget_all_done)
                g.overdue > 0 -> context.resources.getQuantityString(R.plurals.widget_today_open_overdue, g.overdue, g.openTodos, g.overdue)
                else -> context.resources.getQuantityString(R.plurals.widget_n_open, g.openTodos, g.openTodos)
            })
            v.setTextViewText(R.id.today_habits, when {
                g.habits == 0 -> context.getString(R.string.widget_today_none_yet)
                g.habitsLeft.isEmpty() -> context.getString(R.string.widget_all_done)
                else -> context.resources.getQuantityString(R.plurals.widget_today_habits_left, g.habitsLeft.size, g.habitsLeft.size, g.habitsLeft.take(2).joinToString(", "))
            })
            v.setTextViewText(R.id.today_journal, context.getString(if (g.journaled) R.string.widget_today_written else R.string.widget_today_write_today))
            v.setOnClickPendingIntent(R.id.today_header, open(context, 1, Intent(ACTION_TODAY)))
            v.setOnClickPendingIntent(R.id.today_todos_row, open(context, 2, Intent(ACTION_TODAY)))
            v.setOnClickPendingIntent(R.id.today_habits_row, open(context, 3, Intent(RecentWidget.ACTION_OPEN).putExtra(RecentWidget.EXTRA_NOTE, "habit-")))
            v.setOnClickPendingIntent(R.id.today_journal_row, open(context, 4, Intent(RecentWidget.ACTION_OPEN).putExtra(RecentWidget.EXTRA_NOTE, JOURNAL_REMINDER)))
            manager.updateAppWidget(ids, v)
        }

        private fun open(context: Context, code: Int, intent: Intent) = PendingIntent.getActivity(
            context, 20_000 + code, intent.setClass(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
