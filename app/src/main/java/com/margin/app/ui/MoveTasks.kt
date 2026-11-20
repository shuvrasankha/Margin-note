package com.margin.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.margin.app.data.Block
import com.margin.app.data.NoteEntity
import com.margin.app.data.displayTitle
import com.margin.app.data.todoCount

/**
 * Moves or copies some of a list's tasks to another list, or to a new one: unfinished tasks to tomorrow's list,
 * a few items to the shopping list. First the tasks (those still to do are picked), then where to.
 * [tasks] are the note's task lines with their line numbers; [canMove] is false when only copying is allowed.
 */
@Composable
fun MoveTasksDialog(
    tasks: List<Pair<Int, Block>>,
    lists: List<NoteEntity>,
    canMove: Boolean,
    onDone: (lines: Set<Int>, to: NoteEntity?, newTitle: String, copy: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember { mutableStateOf(tasks.filter { !it.second.checked }.map { it.first }.toSet().ifEmpty { tasks.map { it.first }.toSet() }) }
    var copy by remember { mutableStateOf(!canMove) }
    var choosing by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }
    val verb = if (copy) "Copy" else "Move"
    val n = picked.size
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(horizontal = 16.dp).widthIn(max = 420.dp).fillMaxWidth().clip(JShape.lg).background(jc.bg)
                .verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
        ) {
            if (!choosing) {
                Text("$verb items", Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = JType.rowTitle, color = jc.ink)
                Text("Pick the items, then the list they go to.", Modifier.padding(horizontal = 20.dp), style = JType.small, color = jc.muted)
                if (canMove) Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Move", !copy, { copy = false })
                    Pill("Copy", copy, { copy = true })
                }
                tasks.forEach { (line, block) ->
                    val on = line in picked
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Checkbox) { picked = if (on) picked - line else picked + line }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        CheckCircle(on)
                        Text(
                            block.text.ifBlank { "(empty)" }, Modifier.weight(1f), style = JType.item,
                            color = if (block.checked) jc.muted else jc.ink, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        if (block.checked) Text("Done", style = JType.small, color = jc.muted)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Cancel", onDismiss, Modifier.weight(1f))
                    PrimaryButton(if (n == 0) "Next" else "$verb $n", { choosing = true }, Modifier.weight(1f), enabled = n > 0)
                }
            } else {
                Text("$verb ${if (n == 1) "1 item" else "$n items"} to", Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = JType.rowTitle, color = jc.ink)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PlainField(newTitle, { newTitle = it }, "New list name", Modifier.weight(1f)) {
                        if (newTitle.isNotBlank()) onDone(picked, null, newTitle, copy)
                    }
                    TextAction("Create", { onDone(picked, null, newTitle, copy) }, enabled = newTitle.isNotBlank(), bold = true)
                }
                if (lists.isNotEmpty()) SectionLabel("Lists")
                lists.forEach { list ->
                    val count = todoCount(list.blocks)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button) { onDone(picked, list, "", copy) }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        JIcon(Ico.CHECKLIST, jc.accent, size = 20.dp, stroke = 1.9f)
                        Text(list.displayTitle, Modifier.weight(1f), style = JType.item, color = jc.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${count.done}/${count.total}", style = JType.small, color = jc.muted)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
                    TextAction("Back", { choosing = false })
                }
            }
        }
    }
}
