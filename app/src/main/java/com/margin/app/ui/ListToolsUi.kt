package com.margin.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.margin.app.data.ListSort
import com.margin.app.data.dueLabel
import com.margin.app.data.splitQty
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters
import com.margin.app.R
import androidx.compose.ui.res.stringResource

/**
 * Above the toolbar while the cursor is on a task: items from your other lists that finish what you're typing,
 * the task's due date, and how many of it.
 */
@Composable
internal fun TaskBar(vm: EditorViewModel, onDue: (Int) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val line = vm.taskAtCursor ?: return
    val items by vm.pastItems.collectAsState()
    val suggestions = vm.itemSuggestions(items)
    val text = vm.doc.texts.getOrNull(line) ?: return
    val (name, qty) = splitQty(text)
    val due = vm.doc.lines[line].due
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        suggestions.forEach { s -> Chip(s.text, onClick = { vm.completeItem(s.text) }) }
        val overdue = due != null && due < LocalDate.now().toEpochDay() && !vm.doc.lines[line].checked
        Row(
            Modifier.height(36.dp).clip(JShape.pill).border(1.dp, if (overdue) jc.danger else jc.line, JShape.pill)
                .clickable(role = Role.Button) { onDue(line) }.padding(horizontal = 12.dp)
                .semantics { contentDescription = if (due == null) context.getString(R.string.list_tools_add_a_due_date) else context.getString(R.string.list_tools_due_desc, dueText(context, due)) },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            JIcon(Ico.ALARM, if (overdue) jc.danger else jc.accent, size = 16.dp, stroke = 2f)
            Text(if (due == null) stringResource(R.string.list_tools_due_date) else dueText(context, due), style = JType.secondary, color = if (overdue) jc.danger else jc.ink, maxLines = 1)
        }
        if (name.isNotBlank()) Row(
            Modifier.height(36.dp).clip(JShape.pill).border(1.dp, jc.line, JShape.pill),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepButton("−", stringResource(R.string.list_tools_one_fewer), enabled = qty > 1) { vm.stepQty(-1) }
            Text("×$qty", Modifier.semantics { contentDescription = context.getString(R.string.list_tools_quantity_desc, qty.toString()) }, style = JType.secondary, color = jc.ink)
            StepButton("+", stringResource(R.string.list_tools_one_more)) { vm.stepQty(1) }
        }
    }
}

@Composable
private fun StepButton(text: String, label: String, enabled: Boolean = true, onClick: () -> Unit) = Box(
    Modifier.size(40.dp, 36.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick).semantics { contentDescription = label },
    contentAlignment = Alignment.Center,
) { Text(text, style = JType.row, color = if (enabled) jc.accent else jc.line) }

/** Picks a task's due date: today, tomorrow, next Monday, or any day; or takes it off. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DueDateDialog(current: Long?, onPick: (Long?) -> Unit, onDismiss: () -> Unit) {
    val today = LocalDate.now()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = (current?.let(LocalDate::ofEpochDay) ?: today).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    MarginDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.list_tools_due_date)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(stringResource(R.string.common_today), current == today.toEpochDay(), { onPick(today.toEpochDay()) })
                    Pill(stringResource(R.string.common_tomorrow), current == today.plusDays(1).toEpochDay(), { onPick(today.plusDays(1).toEpochDay()) })
                    val monday = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                    Pill(stringResource(R.string.list_tools_next_week), current == monday.toEpochDay(), { onPick(monday.toEpochDay()) })
                }
                DatePicker(
                    state, title = null, headline = null, showModeToggle = false,
                    colors = DatePickerDefaults.colors(
                        containerColor = jc.bg, selectedDayContainerColor = jc.accent, selectedDayContentColor = jc.bg,
                        todayDateBorderColor = jc.accent, todayContentColor = jc.accent,
                        selectedYearContainerColor = jc.accent, selectedYearContentColor = jc.bg,
                    ),
                )
            }
        },
        dismissButton = if (current != null) ({ TextButton(onClick = { onPick(null) }) { Text(stringResource(R.string.common_remove), color = jc.danger) } }) else null,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()) } },
                enabled = state.selectedDateMillis != null,
            ) { Text(stringResource(R.string.common_set)) }
        },
    )
}

/** How to sort a list's tasks. */
@Composable
internal fun SortListDialog(anyDue: Boolean, onPick: (ListSort) -> Unit, onDismiss: () -> Unit) {
    MarginDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.list_tools_sort_list)) },
        text = {
            Column {
                ListSort.entries.filter { it != ListSort.DUE || anyDue }.forEach { s ->
                    Text(
                        stringResource(s.labelRes),
                        Modifier.fillMaxWidth().clip(JShape.sm).clickable(role = Role.Button) { onPick(s) }.padding(vertical = 14.dp),
                        style = JType.row, color = jc.ink,
                    )
                }
                Text(stringResource(R.string.list_tools_headings_and_other_lines_stay), style = JType.small, color = jc.muted)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/** [dueLabel] with "Today", "Tomorrow" and "Yesterday" in the app's language. */
internal fun dueText(context: android.content.Context, day: Long): String = dueLabel(
    day,
    todayWord = context.getString(R.string.common_today),
    tomorrowWord = context.getString(R.string.common_tomorrow),
    yesterdayWord = context.getString(R.string.common_yesterday),
)
