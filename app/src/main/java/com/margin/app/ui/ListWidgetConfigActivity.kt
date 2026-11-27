package com.margin.app.ui

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.margin.app.data.AppPrefs
import com.margin.app.data.ListWidgetData
import com.margin.app.data.NoteRepository
import com.margin.app.data.todoCount
import com.margin.app.data.displayTitle
import com.margin.app.data.lastEdited
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.margin.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource

/** Picks the list a [ListWidget] shows: when it's added, and from its long-press menu ("Reconfigure"). */
@AndroidEntryPoint
class ListWidgetConfigActivity : ComponentActivity() {
    @Inject lateinit var repo: NoteRepository
    @Inject lateinit var appPrefs: AppPrefs
    @Inject lateinit var appLock: com.margin.app.data.AppLock

    /** The note widget shares this picker: any note, not just lists. */
    private val forNote by lazy { AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)?.provider?.className == NoteWidget::class.java.name }

    private val widgetId by lazy { intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Backing out of the first pick doesn't add the widget (before Android 12).
        setResult(RESULT_CANCELED, result())
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return finish()
        enableEdgeToEdge()
        appLock.relock() // App Lock asks here too: the list below shows note titles
        setContent {
            val appearance by appPrefs.appearance.collectAsState()
            val open by appLock.open.collectAsState()
            if (!open) {
                MarginTheme(appearance) { AppLockScreen(appLock, null, onLeave = ::finish) }
                return@setContent
            }
            val notes by repo.liveNotes().collectAsState(emptyList())
            val lists = if (forNote) NoteWidget.choices(notes).sortedByDescending { it.lastEdited } else ListWidgetData.lists(notes)
            MarginTheme(appearance) {
                Column(Modifier.fillMaxSize().background(jc.page).safeDrawingPadding()) {
                    Row(Modifier.padding(horizontal = 8.dp).heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextAction(stringResource(R.string.common_cancel), ::finish)
                    }
                    Text(if (forNote) stringResource(R.string.list_widget_config_show_which_note) else stringResource(R.string.list_widget_config_show_which_list), style = JType.pageTitle, color = jc.ink, modifier = Modifier.padding(horizontal = 20.dp))
                    Text(
                        if (forNote) stringResource(R.string.list_widget_config_its_text_shows_on_the) else stringResource(R.string.list_widget_config_tick_items_off_on_the),
                        style = JType.para, color = jc.muted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                    LazyColumn(Modifier.fillMaxWidth()) {
                        items(lists, key = { it.id }) { n ->
                            val count = todoCount(n.blocks)
                            Row(
                                Modifier.fillMaxWidth().clickable(role = Role.Button) { choose(n.id) }.padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(if (forNote) n.displayTitle else n.title.ifBlank { stringResource(R.string.list_widget_config_untitled_list) }, style = JType.rowTitle, color = jc.ink, modifier = Modifier.weight(1f))
                                if (!forNote) Text(pluralStringResource(R.plurals.widget_n_open, count.total - count.done, count.total - count.done), style = JType.secondary, color = jc.muted)
                            }
                        }
                        if (!forNote && lists.none { it.title.contains("shopping", ignoreCase = true) }) item {
                            Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { choose(null) }.padding(horizontal = 20.dp, vertical = 14.dp)) {
                                Text(stringResource(R.string.list_widget_config_new_shopping_list), style = JType.rowTitle, color = jc.accent)
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }

    /** Shows [noteId] on the widget; null leaves it to start a shopping list with its first item. */
    private fun choose(noteId: String?) {
        if (noteId != null) if (forNote) NoteWidget.choose(this, widgetId, noteId) else ListWidget.choose(this, widgetId, noteId)
        lifecycleScope.launch {
            val notes = repo.liveNotes().first()
            if (forNote) NoteWidget.push(this@ListWidgetConfigActivity, notes) else ListWidget.push(this@ListWidgetConfigActivity, notes)
            setResult(RESULT_OK, result())
            finish()
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) appLock.relock() // unlocking here doesn't open the app later
    }

    private fun result() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
}
