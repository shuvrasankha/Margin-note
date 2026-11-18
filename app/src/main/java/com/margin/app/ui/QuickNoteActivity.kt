package com.margin.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.margin.app.data.AppPrefs
import com.margin.app.data.Block
import com.margin.app.data.BlockType
import com.margin.app.data.DEFAULT_FOLDER_ID
import com.margin.app.data.NoteRepository
import com.margin.app.data.newId
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The widget's new-note popup: a title and some text over the home screen, saved to the default folder
 * without opening the app. An empty note is never saved.
 */
@AndroidEntryPoint
class QuickNoteActivity : ComponentActivity() {
    @Inject lateinit var repo: NoteRepository
    @Inject lateinit var appPrefs: AppPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appearance by appPrefs.appearance.collectAsState()
            MarginTheme(appearance) {
                QuickNote(onCancel = ::finish, onSave = { title, text -> save(title, text) })
            }
        }
    }

    private fun save(title: String, text: String) {
        val blocks = quickNoteBlocks(title, text) ?: return finish()
        lifecycleScope.launch {
            repo.ensureDefaultFolder()
            repo.saveNote(newId(), DEFAULT_FOLDER_ID, blocks)
            finish()
        }
    }
}

/** The note's lines: the title (or, without one, the text's first line) then one body line per line. Null when empty. */
internal fun quickNoteBlocks(title: String, text: String): List<Block>? {
    val lines = text.trimEnd().lines().dropWhile { it.isBlank() }
    val head = title.replace('\n', ' ').trim()
    if (head.isEmpty() && lines.isEmpty()) return null
    val (t, rest) = if (head.isNotEmpty()) head to lines else lines.first().trim() to lines.drop(1).dropWhile { it.isBlank() }
    return listOf(Block(type = BlockType.TITLE, text = t.take(200))) + rest.map { Block(type = BlockType.BODY, text = it) }
}

@Composable
private fun QuickNote(onCancel: () -> Unit, onSave: (String, String) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var text by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    BackHandler(onBack = onCancel)
    val empty = title.isBlank() && text.isBlank()
    // Tapping the dimmed home screen around the card cancels.
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onCancel)
            .safeDrawingPadding().imePadding().padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxWidth().clip(JShape.lg).background(jc.bg)
                .clickable(remember { MutableInteractionSource() }, indication = null) {}
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextAction("Cancel", onCancel)
                Spacer(Modifier.weight(1f))
                Text("New note", style = JType.rowTitle, color = jc.ink)
                Spacer(Modifier.weight(1f))
                TextAction("Save", { saving = true; onSave(title, text) }, bold = true, enabled = !empty && !saving)
            }
            Field(
                title, { title = it.replace('\n', ' ') }, "Title", JType.noteTitle,
                Modifier.padding(top = 14.dp).focusRequester(focus), single = true,
            )
            Field(
                text, { text = it }, "Note", JType.body,
                Modifier.padding(top = 8.dp, bottom = 6.dp).heightIn(min = 120.dp, max = 320.dp).verticalScroll(rememberScrollState()),
            )
        }
    }
}

@Composable
private fun Field(
    value: String, onChange: (String) -> Unit, hint: String, style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier, single: Boolean = false,
) {
    BasicTextField(
        value, onChange, modifier.fillMaxWidth(),
        textStyle = style.copy(color = jc.ink), cursorBrush = SolidColor(jc.accent), singleLine = single,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = if (single) ImeAction.Next else ImeAction.Default),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(hint, style = style, color = jc.muted)
                inner()
            }
        },
    )
}
