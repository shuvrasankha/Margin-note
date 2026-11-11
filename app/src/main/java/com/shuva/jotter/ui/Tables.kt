package com.shuva.jotter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shuva.jotter.data.MAX_TABLE_COLUMNS
import com.shuva.jotter.data.MAX_TABLE_ROWS
import com.shuva.jotter.data.NoteTable

const val ICON_TABLE = "M3 5h18v14H3zM3 10h18M3 14.5h18M9 5v14M15 5v14"

/** One row of a table in the note. */
val TABLE_ROW = 36.dp
/** Space above and below a table card. */
val TABLE_GAP = 6.dp

/** How tall a table's line is: its rows plus the gap around the card. */
fun tableHeight(t: NoteTable): Dp = TABLE_ROW * t.rowCount + TABLE_GAP * 2 + 1.dp

/** A table in the note, read-only: the first row is the header. Tapping it opens [onOpen] (the editor). */
@Composable
fun TableCard(table: NoteTable, onOpen: () -> Unit, onRemove: (() -> Unit)?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Box(modifier) {
        Column(
            Modifier.fillMaxWidth().clip(shape).border(1.dp, jc.line, shape).clickable(role = Role.Button, onClick = onOpen)
                .semantics { contentDescription = "Table, ${table.rowCount} rows, ${table.columnCount} columns. Tap to edit." },
        ) {
            table.rows.forEachIndexed { r, cells ->
                if (r > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(jc.line))
                Row(Modifier.fillMaxWidth().height(TABLE_ROW - if (r > 0) 1.dp else 0.dp).background(if (r == 0) jc.surface else jc.bg)) {
                    cells.forEachIndexed { c, text ->
                        if (c > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(jc.line))
                        Box(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
                            Text(
                                text, maxLines = 1, overflow = TextOverflow.Ellipsis, color = jc.ink,
                                style = JType.secondary.copy(fontWeight = if (r == 0) FontWeight.SemiBold else FontWeight.Normal),
                            )
                        }
                    }
                }
            }
        }
        if (onRemove != null) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp).clip(RoundedCornerShape(12.dp)).background(jc.bg.copy(alpha = 0.9f))
                    .clickable(role = Role.Button, onClick = onRemove).semantics { contentDescription = "Remove table" },
                contentAlignment = Alignment.Center,
            ) { JIcon(Ico.CLOSE, jc.muted, size = 12.dp, stroke = 2.4f) }
        }
    }
}

/**
 * Editing a table full screen: every cell is a field (Next moves along the row, then down). The cell with the
 * cursor picks which row and column the bottom bar adds next to or removes. [onDone] gets the edited table.
 */
@Composable
fun TableEditor(start: NoteTable, onDone: (NoteTable) -> Unit, onDelete: () -> Unit) {
    var table by remember { mutableStateOf(start.normalized()) }
    var at by remember { mutableStateOf(0 to 0) }
    val focus = LocalFocusManager.current
    val focusers = remember(table.rowCount, table.columnCount) { List(table.rowCount * table.columnCount) { FocusRequester() } }
    /** Moves the cursor to the cell after ([r], [c]), along the row and then down; off the last cell it closes the keyboard. */
    fun next(r: Int, c: Int) {
        val i = r * table.columnCount + c + 1
        if (i < focusers.size) runCatching { focusers[i].requestFocus() } else focus.clearFocus()
    }
    Dialog(onDismissRequest = { onDone(table) }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // Some phones keep a dialog's window narrower than the screen even without the default width; make it fill it.
        val view = LocalView.current
        LaunchedEffect(view) {
            (view.parent as? DialogWindowProvider)?.window?.apply {
                setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            }
        }
        Column(Modifier.fillMaxSize().background(jc.bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextAction("Delete", onDelete, color = jc.danger)
                Text("Table", Modifier.weight(1f), style = JType.rowTitle, color = jc.ink, textAlign = TextAlign.Center)
                TextAction("Done", { onDone(table) }, bold = true)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(jc.line))
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                // Columns share the screen width; past what fits at a readable width, the table scrolls sideways.
                val page = maxWidth - 32.dp
                val cell = maxOf(page / table.columnCount, MIN_CELL)
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(16.dp)) {
                    val shape = RoundedCornerShape(10.dp)
                    Column(Modifier.width(cell * table.columnCount).clip(shape).border(1.dp, jc.line, shape)) {
                        table.rows.forEachIndexed { r, cells ->
                            if (r > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(jc.line))
                            Row(Modifier.fillMaxWidth().background(if (r == 0) jc.surface else jc.bg)) {
                                cells.forEachIndexed { c, text ->
                                    if (c > 0) Box(Modifier.width(1.dp).height(CELL_HEIGHT).background(jc.line))
                                    val last = r == table.rowCount - 1 && c == table.columnCount - 1
                                    BasicTextField(
                                        text, { table = table.withCell(r, c, it) },
                                        Modifier.weight(1f).height(CELL_HEIGHT)
                                            .focusRequester(focusers[r * table.columnCount + c])
                                            .onFocusChanged { if (it.isFocused) at = r to c }
                                            // A hardware Enter or Tab moves on, like the keyboard's Next key.
                                            .onPreviewKeyEvent { e ->
                                                val k = e.key
                                                val move = k == Key.Enter || k == Key.Tab || k == Key.NumPadEnter
                                                if (move && e.type == KeyEventType.KeyDown) next(r, c)
                                                move
                                            }
                                            .background(if (at == r to c) jc.accent.copy(alpha = 0.10f) else Color.Transparent)
                                            .padding(horizontal = 10.dp, vertical = 12.dp)
                                            .semantics { contentDescription = if (r == 0) "Header, column ${c + 1}" else "Row $r, column ${c + 1}" },
                                        singleLine = true,
                                        textStyle = JType.secondary.copy(color = jc.ink, fontWeight = if (r == 0) FontWeight.SemiBold else FontWeight.Normal),
                                        cursorBrush = SolidColor(jc.accent),
                                        keyboardOptions = KeyboardOptions(imeAction = if (last) ImeAction.Done else ImeAction.Next),
                                        keyboardActions = KeyboardActions(onNext = { next(r, c) }, onDone = { focus.clearFocus() }),
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        "Tap a cell, then use the buttons below to add or delete its row or column.",
                        Modifier.padding(top = 12.dp).width(page), style = JType.small, color = jc.muted,
                    )
                }
            }
            // Acts on the row and column of the cell with the cursor.
            val (r, c) = at
            Box(Modifier.fillMaxWidth().height(1.dp).background(jc.line))
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
                TableTool(ICON_ROW_ADD, "Add row", table.rowCount < MAX_TABLE_ROWS) { table = table.addRow(r + 1) }
                TableTool(ICON_ROW_DELETE, "Delete row", table.rowCount > 1) {
                    table = table.removeRow(r); at = r.coerceAtMost(table.rowCount - 1) to c
                }
                TableTool(ICON_COLUMN_ADD, "Add column", table.columnCount < MAX_TABLE_COLUMNS) { table = table.addColumn(c + 1) }
                TableTool(ICON_COLUMN_DELETE, "Delete column", table.columnCount > 1) {
                    table = table.removeColumn(c); at = r to c.coerceAtMost(table.columnCount - 1)
                }
            }
        }
    }
}

/** One button of the table editor's bottom bar: an icon over a short label, sharing the width equally. */
@Composable
private fun RowScope.TableTool(icon: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    val color = if (enabled) jc.accent else jc.muted.copy(alpha = 0.5f)
    Column(
        Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(10.dp)).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        JIcon(icon, color, size = 20.dp)
        Text(label, style = JType.small, color = color, maxLines = 1)
    }
}

private val MIN_CELL = 104.dp
private val CELL_HEIGHT = 44.dp
private const val ICON_ROW_ADD = "M3 4h18v7H3zM12 14v7M8.5 17.5h7"
private const val ICON_ROW_DELETE = "M3 4h18v7H3zM8.5 17.5h7"
private const val ICON_COLUMN_ADD = "M4 3h7v18H4zM17.5 8.5v7M14 12h7"
private const val ICON_COLUMN_DELETE = "M4 3h7v18H4zM14 12h7"
