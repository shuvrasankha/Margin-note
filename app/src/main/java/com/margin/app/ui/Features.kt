package com.margin.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** One thing Margin can do. [opens]: the screen it lives on, when it has one of its own. */
internal data class Feature(val icon: String, val title: String, val text: String, val opens: Screen? = null)

/** Every feature, grouped as the page shows them. Keep this in step with the app when features change. */
internal val FEATURES: List<Pair<String, List<Feature>>> = listOf(
    "Writing" to listOf(
        Feature(Ico.TEXT, "Rich notes", "The first line is the title. Lines can be headings, body, monospaced, quotes, bulleted, dashed or numbered lists, and checklists."),
        Feature(Ico.TEXT_SIZE, "Note fonts and paper", "Pick a font for each note (serif, mono, handwriting…) and a paper: ruled, squares, graph or four-line."),
        Feature(ICON_TEMPLATE, "Templates", "Start from Meeting, Lecture, Lab note, Recipe, Trip or Daily plan, save your own, or give a folder a default template."),
        Feature(Ico.LINK, "Linked notes", "Type [[ to link another note, and see every note that links back."),
        Feature(Ico.GRAMMAR, "Spelling and grammar", "Mistakes are underlined as you type; tap one to see why and fix it. Works offline, with an optional fuller online check."),
        Feature(Ico.SEARCH, "Find and replace", "Find in a note and replace words in it."),
        Feature(ICON_HISTORY, "Version history", "Go back to an earlier version of a note."),
    ),
    "Math" to listOf(
        Feature(Ico.NUMBERS, "Answers as you type", "End a line with = to get the answer: sums, percentages, powers, roots, trig, logs and factorials."),
        Feature(Ico.EDIT, "Variables and equations", "Name values and functions (rent = 1200, f(x) = x^2) and use them below, or solve an equation for its unknown."),
        Feature(Ico.GRID, "Graphs", "Plot every y = … in a note, zoom and pan, then add the graph to the note as a picture."),
        Feature(Ico.TEXT_SIZE, "Math keyboard", "Symbols like √, π, ^ and ° one tap away."),
    ),
    "Attach and create" to listOf(
        Feature(Ico.CAMERA, "Photos and files", "Add photos, files and PDFs. The photo editor crops, rotates, flips and resizes."),
        Feature(Ico.PEN, "Drawings", "Sketch in a note with pens, colours, line weights and an eraser."),
        Feature(Ico.HANDWRITING, "Handwriting to text", "Turn handwriting into typed text in English or বাংলা, on the phone."),
        Feature(Ico.SCAN, "Text from photos", "Scan a page, or pick a photo in a note, and get its text. Runs on the phone."),
        Feature(ICON_SCAN_DOC, "Document scanner", "Scan paper documents straight into a note."),
        Feature(Ico.MIC, "Voice notes", "Record audio in a note and play it back with its waveform."),
        Feature(ICON_TABLE, "Tables", "Add a table and edit it cell by cell."),
        Feature(Ico.GLOBE, "Web links", "Paste a web address to get a preview card."),
        Feature(ICON_PDF, "PDF tools", "Open PDFs with Margin, find text in them and edit their text."),
    ),
    "AI" to listOf(
        Feature(Ico.SPARKLE, "Ask AI", "Summarise, rewrite, translate or ask about a note or the text you selected, and keep chatting. Use your own key from Gemini, OpenAI, Claude, Groq or NVIDIA."),
    ),
    "Organise" to listOf(
        Feature(Ico.FOLDER, "Folders inside folders", "Nest folders, move notes between them, and sort each folder its own way."),
        Feature(Ico.SMART_FOLDER, "Smart folders", "Show every note with certain tags, a checklist, a pin, or a recent edit."),
        Feature(Ico.PIN, "Pins and tags", "Pin notes to the top, and type #word to tag a note."),
        Feature(Ico.SEARCH, "Search everything", "Search titles, bodies and the text inside attachments."),
        Feature(Ico.TRASH, "Recently Deleted", "Deleted notes wait here so you can bring them back."),
    ),
    "Plan your days" to listOf(
        Feature(Ico.CHECKLIST, "To-dos", "Every checklist in one place: tick and add tasks there, see your progress, and start a new list.", Screen.Todos),
        Feature(Ico.BELL, "Reminders", "Remind me on a note, once or on repeat, with an optional alarm and full-screen alert.", Screen.Reminders),
        Feature(Ico.CALENDAR, "Journal", "A note for each day on a calendar, behind your passcode, with a daily reminder.", Screen.Journal),
        Feature(Ico.CHECK, "Habits", "Tick habits off each day and see your streaks.", Screen.Habits),
        Feature(Ico.LINES, "Trackers", "Lists like “Movies watched”, counted year by year.", Screen.Trackers),
    ),
    "Privacy" to listOf(
        Feature(Ico.LOCK, "Locked notes", "Lock a note with your passcode; it's encrypted on the phone and in your backups."),
        Feature(Ico.FINGERPRINT, "App lock", "Ask for your passcode or fingerprint whenever Margin opens."),
        Feature(Ico.KEY, "Passwords", "A password vault with a generator, strength check and history.", Screen.Passwords),
    ),
    "Sync, backup and sharing" to listOf(
        Feature(Ico.SYNC, "Google Drive sync", "Optional. Notes sync through hidden app storage in your Drive."),
        Feature(Ico.CLOUD, "Backups anywhere", "Password-sealed backups to any cloud folder or a WebDAV server."),
        Feature(Ico.ARROW_DOWN, "Import", "Bring notes in from Google Keep, Evernote, Markdown, text or HTML."),
        Feature(Ico.ARCHIVE, "Export", "Export every note as Markdown, with its photos and files."),
        Feature(Ico.SHARE, "Share as image", "Turn a note into a styled card in many designs, sizes and fonts, or share it as text, PDF or Markdown, or print it."),
    ),
    "Around your phone" to listOf(
        Feature(Ico.COMPOSE, "Widget and quick note", "A home screen widget, a Quick Settings tile and app shortcuts for writing a note fast."),
        Feature(Ico.MOON, "Light and dark", "Follows your phone's theme, or choose one. Text size and list previews are yours to set."),
        Feature(Ico.RESIZE, "Tablets", "On a wide screen the list and the note sit side by side."),
    ),
)

/** The features page: everything Margin does, grouped and searchable; features with a screen of their own open it. */
@Composable
fun FeaturesScreen(onBack: () -> Unit, onOpen: (Screen) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim()
    val groups = remember(q) {
        if (q.isEmpty()) FEATURES
        else FEATURES.mapNotNull { (name, list) ->
            list.filter { it.title.contains(q, true) || it.text.contains(q, true) || name.contains(q, true) }
                .takeIf { it.isNotEmpty() }?.let { name to it }
        }
    }
    Scaffold(
        containerColor = jc.page,
        topBar = { NavBar("Settings", onBack, color = jc.page) },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                PageTitle("What Margin can do")
                Text(
                    "${FEATURES.sumOf { it.second.size }} features, all in one app. Tap one with an arrow to go there.",
                    Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp), style = JType.para, color = jc.muted,
                )
                SearchField(query, { query = it }, "Search features", Modifier.padding(horizontal = 16.dp), fill = jc.card)
            }
            if (groups.isEmpty()) item {
                Text("No features match “$q”.", Modifier.padding(20.dp), style = JType.para, color = jc.muted)
            }
            groups.forEach { (name, list) ->
                item(key = name) {
                    SettingsLabel(name)
                    DividedCard(list, inset = 58.dp) { f -> FeatureRow(f, onOpen) }
                }
            }
            item { Box(Modifier.padding(bottom = 48.dp)) }
        }
    }
}

@Composable
private fun FeatureRow(f: Feature, onOpen: (Screen) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .let { m -> f.opens?.let { s -> m.clickable(role = Role.Button) { onOpen(s) } } ?: m }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTile(f.icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(f.title, style = JType.item, color = jc.ink)
            Text(f.text, style = JType.small, color = jc.muted)
        }
        if (f.opens != null) JIcon(Ico.CHEVRON, jc.muted, size = 18.dp)
    }
}
