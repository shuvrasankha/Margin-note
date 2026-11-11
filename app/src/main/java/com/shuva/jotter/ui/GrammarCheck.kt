package com.shuva.jotter.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.shuva.jotter.data.GrammarIssue
import com.shuva.jotter.data.LanguageTool
import com.shuva.jotter.data.grammarRules
import com.shuva.jotter.data.mergeIssues
import com.shuva.jotter.data.stillIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Grammar mistakes in [text]: the offline rules, checked after a short pause in typing, plus LanguageTool's
 * when [cloud] is on (after a longer pause, to stay inside its free limit of 20 checks a minute). Until a
 * check finishes, earlier results stay, minus any whose text has changed. A failed cloud check (offline,
 * rate-limited) just leaves the offline results.
 */
@Composable
fun rememberGrammarIssues(text: String, enabled: Boolean, cloud: Boolean): List<GrammarIssue> {
    var local by remember { mutableStateOf(emptyList<GrammarIssue>()) }
    var remote by remember { mutableStateOf(emptyList<GrammarIssue>()) }
    LaunchedEffect(text, enabled) {
        if (!enabled) { local = emptyList(); return@LaunchedEffect }
        delay(400)
        local = withContext(Dispatchers.Default) { grammarRules(text) }
    }
    LaunchedEffect(text, enabled && cloud) {
        if (!enabled || !cloud) { remote = emptyList(); return@LaunchedEffect }
        delay(2000)
        if (text.isBlank()) { remote = emptyList(); return@LaunchedEffect }
        withContext(Dispatchers.IO) { runCatching { LanguageTool.check(text) } }.onSuccess { remote = it }
    }
    if (!enabled) return emptyList()
    // LanguageTool understands the sentence, so its findings win where both flag the same words.
    return mergeIssues(remote.stillIn(text), local.stillIn(text))
}
