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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Picks the list a [ListWidget] shows: when it's added, and from its long-press menu ("Reconfigure"). */
@AndroidEntryPoint
class ListWidgetConfigActivity : ComponentActivity() {
    @Inject lateinit var repo: NoteRepository
    @Inject lateinit var appPrefs: AppPrefs

    private val widgetId by lazy { intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Backing out of the first pick doesn't add the widget (before Android 12).
        setResult(RESULT_CANCELED, result())
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return finish()
        enableEdgeToEdge()
        setContent {
            val appearance by appPrefs.appearance.collectAsState()
            val notes by repo.liveNotes().collectAsState(emptyList())
            val lists = ListWidgetData.lists(notes)
            MarginTheme(appearance) {
                Column(Modifier.fillMaxSize().backdrop(jc.page).safeDrawingPadding()) {
                    Row(Modifier.padding(horizontal = 8.dp).heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextAction("Cancel", ::finish)
                    }
                    Text("Show which list?", style = JType.pageTitle, color = jc.ink, modifier = Modifier.padding(horizontal = 20.dp))
                    Text(
                        "Tick items off on the home screen, and add more with + or the mic.",
                        style = JType.para, color = jc.muted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                    LazyColumn(Modifier.fillMaxWidth()) {
                        items(lists, key = { it.id }) { n ->
                            val count = todoCount(n.blocks)
                            Row(
                                Modifier.fillMaxWidth().clickable(role = Role.Button) { choose(n.id) }.padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(n.title.ifBlank { "Untitled list" }, style = JType.rowTitle, color = jc.ink, modifier = Modifier.weight(1f))
                                Text("${count.total - count.done} open", style = JType.secondary, color = jc.muted)
                            }
                        }
                        if (lists.none { it.title.contains("shopping", ignoreCase = true) }) item {
                            Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { choose(null) }.padding(horizontal = 20.dp, vertical = 14.dp)) {
                                Text("New shopping list", style = JType.rowTitle, color = jc.accent)
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
        if (noteId != null) ListWidget.choose(this, widgetId, noteId)
        lifecycleScope.launch {
            ListWidget.push(this@ListWidgetConfigActivity, repo.liveNotes().first())
            setResult(RESULT_OK, result())
            finish()
        }
    }

    private fun result() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
}
