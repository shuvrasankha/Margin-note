package com.margin.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.margin.app.data.bodyText
import com.margin.app.data.countWords
import com.margin.app.data.goalStreak
import com.margin.app.data.paragraphs
import com.margin.app.data.readingLabel
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Word goals to pick from, in words a day; 0 is no goal. */
private val GOALS = listOf(0, 100, 250, 500, 1000, 2000)

/** Words in the selection, or 0 when nothing (or only the cursor) is selected. */
private fun selectedWords(vm: EditorViewModel): Int {
    val sel = vm.value.selection
    if (sel.collapsed) return 0
    val text = vm.value.text
    return countWords(text.substring(sel.min.coerceIn(0, text.length), sel.max.coerceIn(0, text.length)))
}

/** The live count under the note: "312 words · 2 min read", the selection's words, and today's goal. Tap for more. */
@Composable
internal fun WordCountBar(vm: EditorViewModel, onOpen: () -> Unit) {
    val words = remember(vm.doc) { countWords(bodyText(vm.doc.toBlocks())) }
    val selected = selectedWords(vm)
    val goal by vm.writing.goal.collectAsState()
    val days by vm.writing.days.collectAsState()
    val today = (days[LocalDate.now()] ?: 0).coerceAtLeast(0)
    val left = if (selected > 0) "$selected of ${plural(words, "word")}" else "${plural(words, "word")} · ${readingLabel(words).replace("Under a minute", "<1 min read")}"
    val right = if (goal > 0) "Today $today / $goal" else null
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpen).padding(horizontal = 20.dp, vertical = 6.dp)
            .semantics { contentDescription = listOfNotNull(left, right).joinToString(". ") + ". Tap for writing stats." },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(left, Modifier.weight(1f), style = JType.small, color = jc.muted, maxLines = 1)
        if (right != null) {
            Text(right, style = JType.small, color = if (today >= goal) jc.accent else jc.muted, maxLines = 1)
            ProgressRing(today.toFloat() / goal, Modifier.padding(start = 6.dp).size(14.dp), stroke = 2.dp)
        }
    }
}

/** Writing stats: this note's counts, today's words against the goal, the streak and the last week. */
@Composable
internal fun WritingStatsDialog(vm: EditorViewModel, onDismiss: () -> Unit) {
    val blocks = remember(vm.doc) { vm.doc.toBlocks() }
    val body = remember(blocks) { bodyText(blocks) }
    val words = countWords(body)
    val selected = selectedWords(vm)
    val goal by vm.writing.goal.collectAsState()
    val days by vm.writing.days.collectAsState()
    val showCount by vm.writing.showCount.collectAsState()
    val now = LocalDate.now()
    val today = (days[now] ?: 0).coerceAtLeast(0)
    val streak = goalStreak(days, goal, now)
    MarginDialog(
        onDismissRequest = onDismiss,
        title = { Text("Writing stats") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Heading("This note")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("$words", if (words == 1) "word" else "words", Modifier.weight(1f))
                    StatTile("${body.count { !it.isWhitespace() }}", "characters", Modifier.weight(1f))
                    paragraphs(blocks).let { p -> StatTile("$p", if (p == 1) "paragraph" else "paragraphs", Modifier.weight(1f)) }
                }
                Text(
                    listOfNotNull(readingLabel(words).let { if (it.startsWith("Under")) "Under a minute to read" else it }, "$selected selected".takeIf { selected > 0 }).joinToString(" · "),
                    style = JType.small, color = jc.muted,
                )
                Heading("Today")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (goal > 0) ProgressRing(today.toFloat() / goal, Modifier.size(52.dp), stroke = 5.dp) {
                        Text("${(100 * today / goal).coerceAtMost(999)}%", style = JType.caption, color = jc.ink)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            if (goal > 0) "$today of ${plural(goal, "word")}" else "${plural(today, "word")} written",
                            style = JType.row, color = jc.ink,
                        )
                        Text(
                            when {
                                goal == 0 -> "Across all your notes. Set a goal to keep a streak."
                                today >= goal && streak > 1 -> "Goal reached. $streak days in a row."
                                today >= goal -> "Goal reached for today."
                                streak > 0 -> "${plural(goal - today, "word")} to go to make it ${streak + 1} days in a row."
                                else -> "${plural(goal - today, "word")} to go."
                            },
                            style = JType.small, color = jc.muted,
                        )
                    }
                }
                WeekBars(days, goal, now)
                Heading("Daily goal")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GOALS.forEach { g -> Pill(if (g == 0) "Off" else "$g", g == goal, { vm.writing.setGoal(g) }) }
                }
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Switch) { vm.writing.setShowCount(!showCount) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Show word count while writing", style = JType.row, color = jc.ink)
                        Text("Under every note, above the toolbar.", style = JType.small, color = jc.muted)
                    }
                    JSwitch(showCount, { vm.writing.setShowCount(it) })
                }
                Text("Words written are counted on this phone only. Deleting words takes them off today's count.", style = JType.caption, color = jc.muted)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

/** Words written each of the last seven days, as small bars; days that reached the goal are in the accent colour. */
@Composable
private fun WeekBars(days: Map<LocalDate, Int>, goal: Int, today: LocalDate) {
    val week = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val counts = week.map { (days[it] ?: 0).coerceAtLeast(0) }
    val top = maxOf(counts.max(), goal, 1)
    Row(
        Modifier.fillMaxWidth().semantics {
            contentDescription = "Last 7 days: " + week.zip(counts).joinToString(", ") { (d, n) -> "${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} $n" }
        },
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom,
    ) {
        week.zip(counts).forEach { (d, n) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.height(56.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier.fillMaxWidth().height((56f * n / top).coerceAtLeast(3f).dp).clip(JShape.xs)
                            .background(if (goal > 0 && n >= goal) jc.accent else if (n > 0) jc.accent.copy(alpha = 0.35f) else jc.line),
                    )
                }
                Text(d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()), style = JType.caption, color = if (d == today) jc.ink else jc.muted)
            }
        }
    }
}

/** A section title inside the sheet, lined up with its content. */
@Composable
private fun Heading(text: String) = Text(text, Modifier.padding(top = 8.dp), style = JType.section, color = jc.muted)
