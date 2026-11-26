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
import com.margin.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource

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
    val left = if (selected > 0) pluralStringResource(R.plurals.writing_selected_of_words, words, words, selected.toString())
        else stringResource(R.string.writing_count_reading, pluralStringResource(R.plurals.common_words, words, words), readingText(words, R.string.writing_under_a_minute_short))
    val right = if (goal > 0) stringResource(R.string.writing_today_of_goal_short, today.toString(), goal.toString()) else null
    val barDesc = if (right != null) stringResource(R.string.writing_bar_desc_two, left, right) else stringResource(R.string.writing_bar_desc_one, left)
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpen).padding(horizontal = 20.dp, vertical = 6.dp)
            .semantics { contentDescription = barDesc },
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
        title = { Text(stringResource(R.string.writing_stats_writing_stats)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Heading(stringResource(R.string.writing_stats_this_note))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("$words", pluralStringResource(R.plurals.writing_word_unit, words), Modifier.weight(1f))
                    StatTile("${body.count { !it.isWhitespace() }}", stringResource(R.string.writing_characters_unit), Modifier.weight(1f))
                    paragraphs(blocks).let { p -> StatTile("$p", pluralStringResource(R.plurals.writing_paragraph_unit, p), Modifier.weight(1f)) }
                }
                Text(
                    listOfNotNull(readingText(words, R.string.writing_under_a_minute_to_read), stringResource(R.string.writing_n_selected, selected.toString()).takeIf { selected > 0 }).joinToString(" · "),
                    style = JType.small, color = jc.muted,
                )
                Heading(stringResource(R.string.common_today))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (goal > 0) ProgressRing(today.toFloat() / goal, Modifier.size(52.dp), stroke = 5.dp) {
                        Text("${(100 * today / goal).coerceAtMost(999)}%", style = JType.caption, color = jc.ink)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            if (goal > 0) pluralStringResource(R.plurals.writing_today_of_goal, goal, goal, today.toString()) else pluralStringResource(R.plurals.writing_words_written, today, today),
                            style = JType.row, color = jc.ink,
                        )
                        Text(
                            when {
                                goal == 0 -> stringResource(R.string.writing_stats_across_all_your_notes_set)
                                today >= goal && streak > 1 -> pluralStringResource(R.plurals.writing_goal_reached_streak, streak, streak)
                                today >= goal -> stringResource(R.string.writing_stats_goal_reached_for_today)
                                streak > 0 -> pluralStringResource(R.plurals.writing_to_go_streak, goal - today, goal - today, (streak + 1).toString())
                                else -> pluralStringResource(R.plurals.writing_to_go, goal - today, goal - today)
                            },
                            style = JType.small, color = jc.muted,
                        )
                    }
                }
                WeekBars(days, goal, now)
                Heading(stringResource(R.string.writing_stats_daily_goal))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GOALS.forEach { g -> Pill(if (g == 0) stringResource(R.string.writing_stats_off) else "$g", g == goal, { vm.writing.setGoal(g) }) }
                }
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Switch) { vm.writing.setShowCount(!showCount) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.writing_stats_show_word_count_while_writing), style = JType.row, color = jc.ink)
                        Text(stringResource(R.string.writing_stats_under_every_note_above_the), style = JType.small, color = jc.muted)
                    }
                    JSwitch(showCount, { vm.writing.setShowCount(it) })
                }
                Text(stringResource(R.string.writing_stats_words_written_are_counted_on), style = JType.caption, color = jc.muted)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_done)) } },
    )
}

/** Words written each of the last seven days, as small bars; days that reached the goal are in the accent colour. */
@Composable
private fun WeekBars(days: Map<LocalDate, Int>, goal: Int, today: LocalDate) {
    val week = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val counts = week.map { (days[it] ?: 0).coerceAtLeast(0) }
    val top = maxOf(counts.max(), goal, 1)
    val lastWeek = stringResource(R.string.writing_last_7_days)
    Row(
        Modifier.fillMaxWidth().semantics {
            contentDescription = lastWeek.format(week.zip(counts).joinToString(", ") { (d, n) -> "${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} $n" })
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

/** [com.margin.app.data.readingLabel] in the app's language; [under] is said for a minute or less. */
@Composable
internal fun readingText(words: Int, @androidx.annotation.StringRes under: Int = R.string.writing_under_a_minute): String =
    com.margin.app.data.readingMinutes(words).let { m -> if (m <= 1) stringResource(under) else pluralStringResource(R.plurals.writing_min_read, m, m) }
