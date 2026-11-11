package com.shuva.jotter.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * OCR result: the text found, editable so mistakes can be fixed before it goes into the note. Insert adds it
 * as note text; Copy puts it on the clipboard.
 */
@Composable
fun ScanDialog(state: ScanState, onInsert: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(horizontal = 16.dp).widthIn(max = 480.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(jc.bg)
                .padding(top = 18.dp, bottom = 8.dp),
        ) {
            Text(
                when (state) { ScanState.Reading -> "Reading text…"; is ScanState.Found -> "Text found"; is ScanState.Failed -> "No text" },
                Modifier.padding(horizontal = 20.dp), style = JType.rowTitle, color = jc.ink,
            )
            when (state) {
                ScanState.Reading -> Row(
                    Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = jc.accent, strokeWidth = 2.5.dp)
                    Text("Looking for text in the photo. This stays on your phone.", style = JType.secondary, color = jc.muted)
                }
                is ScanState.Failed -> Text(state.message, Modifier.padding(20.dp), style = JType.secondary, color = jc.muted)
                is ScanState.Found -> {
                    var text by remember(state) { mutableStateOf(state.text) }
                    Text("Check it and fix any mistakes before adding it.", Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = JType.small, color = jc.muted)
                    Box(
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().heightIn(min = 120.dp, max = 360.dp)
                            .clip(RoundedCornerShape(12.dp)).border(1.dp, jc.line, RoundedCornerShape(12.dp)).background(jc.surface)
                            .verticalScroll(rememberScrollState()).padding(12.dp),
                    ) {
                        BasicTextField(
                            text, { text = it }, Modifier.fillMaxWidth().semantics { contentDescription = "Text found in the photo" },
                            textStyle = JType.body.copy(color = jc.ink), cursorBrush = SolidColor(jc.accent),
                        )
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
                        TextAction("Copy", {
                            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Scanned text", text))
                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                        })
                        TextAction("Cancel", onDismiss)
                        TextAction("Add to note", { onInsert(text) }, bold = true, enabled = text.isNotBlank())
                    }
                    return@Column
                }
            }
            Box(Modifier.fillMaxWidth().padding(end = 12.dp), contentAlignment = Alignment.CenterEnd) {
                TextAction(if (state == ScanState.Reading) "Cancel" else "OK", onDismiss)
            }
        }
    }
}
