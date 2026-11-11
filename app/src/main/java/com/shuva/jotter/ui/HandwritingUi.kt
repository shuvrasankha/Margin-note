package com.shuva.jotter.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shuva.jotter.data.InkLanguage
import com.shuva.jotter.data.PenStroke
import com.shuva.jotter.data.recognizeHandwriting
import kotlinx.coroutines.CancellationException
import java.util.Locale

private sealed interface InkState {
    data object Downloading : InkState
    data object Reading : InkState
    data class Found(val text: String) : InkState
    data class Failed(val message: String) : InkState
}

private const val PREFS = "jotter_ink"
private const val K_LANGUAGE = "language"

/** The language last used, or Bengali on a phone set to Bengali. */
private fun savedLanguage(context: Context): InkLanguage =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(K_LANGUAGE, null)
        ?.let { name -> InkLanguage.entries.firstOrNull { it.name == name } }
        ?: if (Locale.getDefault().language == "bn") InkLanguage.BENGALI else InkLanguage.ENGLISH

/**
 * Handwriting in [strokes] read as text, in English or Bengali (switching reads it again). The text can be
 * fixed before [onAdd] gets it, with whether to remove the handwriting from the drawing.
 */
@Composable
fun HandwritingDialog(strokes: List<PenStroke>, onAdd: (text: String, removeInk: Boolean) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var language by remember { mutableStateOf(savedLanguage(context)) }
    var state by remember { mutableStateOf<InkState>(InkState.Reading) }
    var text by remember { mutableStateOf("") }
    var removeInk by remember { mutableStateOf(true) }
    LaunchedEffect(language) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(K_LANGUAGE, language.name).apply()
        state = InkState.Reading
        state = try {
            val found = recognizeHandwriting(strokes, language) { state = InkState.Downloading }
            if (found.isBlank()) InkState.Failed("No writing found. Write a little larger, one line at a time.")
            else InkState.Found(found).also { text = found }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            InkState.Failed(
                if (state == InkState.Downloading) "Couldn't get the ${language.label} handwriting model. Connect to the internet once and try again."
                else "Couldn't read the handwriting: " + (e.message ?: "unknown error"),
            )
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(horizontal = 16.dp).widthIn(max = 480.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(jc.bg)
                .padding(top = 18.dp, bottom = 8.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Handwriting to text", Modifier.weight(1f), style = JType.rowTitle, color = jc.ink)
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InkLanguage.entries.forEach { l ->
                    val on = l == language
                    Box(
                        Modifier.clip(CircleShape).background(if (on) jc.selected else jc.surface)
                            .border(1.dp, if (on) jc.accent else jc.line, CircleShape)
                            .clickable(role = Role.RadioButton) { language = l }
                            .semantics { selected = on; contentDescription = "Read as ${l.label}" }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) { Text(l.label, style = JType.secondary, color = if (on) jc.accent else jc.ink) }
                }
            }
            when (val s = state) {
                InkState.Downloading, InkState.Reading -> Row(
                    Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = jc.accent, strokeWidth = 2.5.dp)
                    Text(
                        if (s == InkState.Downloading) "Getting ${language.label} handwriting (about 20 MB, only once)…" else "Reading handwriting…",
                        style = JType.secondary, color = jc.muted,
                    )
                }
                is InkState.Failed -> Text(s.message, Modifier.padding(horizontal = 20.dp, vertical = 16.dp), style = JType.secondary, color = jc.muted)
                is InkState.Found -> {
                    Box(
                        Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().heightIn(min = 96.dp, max = 320.dp)
                            .clip(RoundedCornerShape(12.dp)).border(1.dp, jc.line, RoundedCornerShape(12.dp)).background(jc.surface)
                            .verticalScroll(rememberScrollState()).padding(12.dp),
                    ) {
                        BasicTextField(
                            text, { text = it }, Modifier.fillMaxWidth().semantics { contentDescription = "Text read from the handwriting" },
                            textStyle = JType.body.copy(color = jc.ink), cursorBrush = SolidColor(jc.accent),
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { removeInk = !removeInk }.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(removeInk, null, Modifier.padding(8.dp), colors = CheckboxDefaults.colors(checkedColor = jc.accent, checkmarkColor = jc.bg))
                        Text("Remove the handwriting", style = JType.secondary, color = jc.ink)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
                if (state is InkState.Found) TextAction("Copy", {
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Handwriting", text))
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                })
                TextAction("Cancel", onDismiss)
                if (state is InkState.Found) TextAction("Add to note", { onAdd(text, removeInk) }, bold = true, enabled = text.isNotBlank())
            }
        }
    }
}
