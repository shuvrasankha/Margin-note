package com.margin.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.ripple
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.margin.app.data.Attachment
import com.margin.app.data.decodeImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.margin.app.data.NoteEntity
import com.margin.app.data.TRACKER_STARTERS
import com.margin.app.data.TrackerEntry
import com.margin.app.data.averageRating
import com.margin.app.data.countInYear
import com.margin.app.data.countSameTimeLastYear
import com.margin.app.data.isTracker
import com.margin.app.data.monthCounts
import com.margin.app.data.replacing
import com.margin.app.data.TrackerKind
import com.margin.app.data.trackerKind
import com.margin.app.data.trackerEntries
import com.margin.app.data.years
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle as DateStyle
import java.util.Locale

/** A list with a tick-mark count: the trackers. */
const val ICON_TRACKERS = "M4 6h11M4 12h11M4 18h7M17 16l2 2 3-4M18 4v6M15 7h6"

/** The big count on a tracker. */
private val BigNumber = TextStyle(fontFamily = Inter, fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.03).em)
private val HeroNumber = BigNumber.copy(fontSize = 60.sp, lineHeight = 64.sp)

/** The "No date" choice among the years. */
private const val NO_YEAR = 0

/** An entry being added ([original] null) or edited, in the tracker [noteId]. Found by value, so a sync can't shift it. */
private data class EntryEdit(val noteId: String, val original: TrackerEntry?)

/**
 * Where tracker photos go: [compress] stores a picked or taken photo as a small JPEG, [file] finds one to show, and
 * [cameraTarget] is a new file (and its URI) for the camera app to write into.
 */
class TrackerPhotos(
    val compress: suspend (Uri, String) -> Attachment,
    val file: (Attachment) -> File,
    val cameraTarget: () -> Pair<File, Uri>,
)

private fun liveTrackers(notes: List<NoteEntity>) = notes.filter { isTracker(it) && it.trashedAt == null && !it.deleted }.sortedBy { it.createdAt }

/**
 * Every tracker as a card: its icon, this year's count, how that compares with this time last year and a bar per
 * month. Tapping a card opens it; its + adds an entry straight away. Below, one-tap starters for common trackers.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrackersScreen(
    notes: List<NoteEntity>, onOpen: (NoteEntity) -> Unit, onAdd: (String) -> Unit,
    onSave: (NoteEntity, List<TrackerEntry>) -> Unit, onBack: () -> Unit, photos: TrackerPhotos,
) {
    val today = LocalDate.now()
    val trackers = remember(notes) { liveTrackers(notes) }
    val parsed = remember(trackers) { trackers.associate { it.id to trackerEntries(it) } }
    var naming by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<EntryEdit?>(null) }
    if (naming) NameDialog("New tracker", "", onDismiss = { naming = false }, label = "Like Movies watched") { naming = false; onAdd(it) }
    editing?.let { EntryEditor(trackers, it, photos, onSave) { editing = null } }

    val glass = rememberGlass()
    Scaffold(containerColor = jc.page, topBar = { NavBar("Folders", onBack, color = jc.page, glass = glass) }) { padding ->
        LazyColumn(Modifier.glassBody(glass, padding).fillMaxSize(), contentPadding = glassPadding(glass, padding, PaddingValues(bottom = 40.dp))) {
            item { PageTitle("Trackers") }
            if (trackers.isEmpty()) item { EmptyHero() }
            else item {
                val total = remember(trackers) { parsed.values.sumOf { it.countInYear(today.year) } }
                Text(
                    "${plural(total, "thing")} logged in ${today.year}", Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                    style = JType.secondary, color = jc.muted,
                )
            }
            items(trackers, key = { it.id }) { note ->
                TrackerCard(note, parsed.getValue(note.id), today, onOpen = { onOpen(note) }, onAdd = { editing = EntryEdit(note.id, null) }, Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            }
            item {
                // Leave out a starter you already have, by name or by kind ("Places visited" covers Places).
                val taken = trackers.map { it.title.trim().lowercase() }.toSet()
                val kinds = trackers.mapNotNull { trackerKind(it.title) }.toSet()
                val starters = TRACKER_STARTERS.filter { it.lowercase() !in taken && trackerKind(it) !in kinds }
                Text(
                    if (trackers.isEmpty()) "Pick one to start" else "Start another",
                    Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 10.dp), style = JType.section, color = jc.muted,
                )
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    starters.forEach { name -> StarterChip(trackerKind(name)?.let(::kindIcon), name) { onAdd(name) } }
                    StarterChip(null, "Your own…") { naming = true }
                }
            }
        }
    }
}

@Composable
private fun EmptyHero() {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).appear(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
            listOf(TrackerKind.MOVIE, TrackerKind.BOOK, TrackerKind.PLACE).forEachIndexed { i, kind ->
                Box(
                    Modifier.padding(top = if (i == 1) 0.dp else 10.dp).size(64.dp).clip(CircleShape).background(jc.selected).border(3.dp, jc.well, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { JIcon(kindIcon(kind), jc.accent, size = 30.dp) }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Watch it, read it, log it", style = JType.subheading, color = jc.ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            "Keep a list of the movies you watch, books you read and places you go, and watch your year add up.",
            style = JType.para, color = jc.muted, textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun StarterChip(icon: String?, label: String, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    Row(
        Modifier.height(40.dp).pressScale(press, 0.94f).clip(JShape.pill).glassFill(jc.card, JShape.pill).border(1.dp, jc.line, JShape.pill)
            .clickable(press, ripple(), role = Role.Button, onClick = tapped(onClick)).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        JIcon(icon ?: Ico.PLUS, jc.accent, size = 18.dp, stroke = if (icon == null) 2.2f else 1.8f)
        Text(label, style = JType.label, color = jc.ink, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun TrackerCard(note: NoteEntity, entries: List<TrackerEntry>, today: LocalDate, onOpen: () -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val thisYear = entries.countInYear(today.year)
    val latest = entries.firstOrNull { it.date != null } ?: entries.firstOrNull()
    val press = remember { MutableInteractionSource() }
    Column(
        modifier.fillMaxWidth().pressScale(press, 0.98f).clip(JShape.lg).glassFill(jc.card, JShape.lg)
            .clickable(press, ripple(), role = Role.Button, onClick = tapped(onOpen)).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KindTile(note.title, 44.dp)
            Column(Modifier.weight(1f)) {
                Text(note.title, style = JType.rowTitle, color = jc.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    latest?.let { "Latest: " + it.title.ifBlank { "Untitled" } } ?: "Nothing added yet",
                    style = JType.small, color = jc.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            AddButton("Add to ${note.title}", onAdd)
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$thisYear", style = BigNumber, color = jc.ink)
                    Text(" in ${today.year}", Modifier.padding(bottom = 6.dp), style = JType.secondary, color = jc.muted)
                }
                Trend(thisYear, entries.countSameTimeLastYear(today), short = true)
            }
            MonthBars(remember(entries) { entries.monthCounts(today.year) }, today.monthValue - 1, Modifier.width(132.dp).height(48.dp))
        }
    }
}

/** How this year so far compares with the same point last year; nothing when there's nothing to compare. */
@Composable
private fun Trend(thisYear: Int, lastYear: Int, short: Boolean = false) {
    if (lastYear == 0) return
    val diff = thisYear - lastYear
    Text(
        when {
            short && diff > 0 -> "↑ $diff vs last year"
            short && diff < 0 -> "↓ ${-diff} vs last year"
            short -> "Level with last year"
            diff > 0 -> "↑ $diff more than this time last year"
            diff < 0 -> "↓ ${-diff} fewer than this time last year"
            else -> "Level with this time last year"
        },
        style = JType.small, color = if (diff > 0) jc.accent else jc.muted, fontWeight = if (diff > 0) FontWeight.Medium else FontWeight.Normal,
    )
}

/** The tracker's icon on a warm tile, or its first letter when the name doesn't suggest one. */
@Composable
private fun KindTile(name: String, size: Dp) {
    val icon = trackerKind(name)?.let(::kindIcon)
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(jc.selected), contentAlignment = Alignment.Center) {
        if (icon != null) JIcon(icon, jc.accent, size = size * 0.52f, stroke = 1.8f)
        else Text(
            name.trim().take(1).uppercase(), fontSize = with(LocalDensity.current) { (size * 0.46f).toSp() },
            fontWeight = FontWeight.Bold, color = jc.accent, fontFamily = Inter,
        )
    }
}

/** Line icons in the app's style (24 x 24, drawn with a round stroke), one per [TrackerKind]. */
private fun kindIcon(kind: TrackerKind): String = when (kind) {
    TrackerKind.MOVIE -> "M4 10h16v9a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1zM4 10l-.6-3.2 15-2.8.6 3.2M8.6 6.3l2.1 2.9M13.6 5.4l2.1 2.9"
    TrackerKind.TV -> "M3 7h18v12H3zM8 3l4 4 4-4"
    TrackerKind.BOOK -> "M12 6a2 2 0 0 0-2-2H4v14h6a2 2 0 0 1 2 2zM12 6a2 2 0 0 1 2-2h6v14h-6a2 2 0 0 0-2 2"
    TrackerKind.PODCAST -> "M4 15v-3a8 8 0 0 1 16 0v3M4 15h3v5H4zM17 15h3v5h-3z"
    TrackerKind.MUSIC -> "M9 18V5l11-2v13M3 18a3 3 0 1 0 6 0a3 3 0 1 0-6 0M14 16a3 3 0 1 0 6 0a3 3 0 1 0-6 0"
    TrackerKind.GAME -> "M6 8h12a4 4 0 0 1 4 4v1a3.5 3.5 0 0 1-6.3 2.1L15 14H9l-.7 1.1A3.5 3.5 0 0 1 2 13v-1a4 4 0 0 1 4-4zM7.5 10v4M5.5 12h4M15.5 11h.01M17.5 13h.01"
    TrackerKind.PLACE -> "M12 21s-7-6-7-11.5a7 7 0 0 1 14 0C19 15 12 21 12 21zM9.5 9.5a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0-5 0"
    TrackerKind.FOOD -> "M5 3v5a2 2 0 0 0 4 0V3M7 3v18M17 21V3c-2 1-3 4-3 8h3"
    TrackerKind.COFFEE -> "M4 9h13v5a5 5 0 0 1-5 5H9a5 5 0 0 1-5-5zM17 11h1.5a2.5 2.5 0 0 1 0 5H17M8 3v3M12 3v3"
    TrackerKind.DRINK -> "M7 3h10l-1 6a4 4 0 0 1-8 0zM12 13v8M8 21h8"
    TrackerKind.HIKE -> "M3 20l6-10 4 6 3-4 5 8zM9 10l1.5 2.5"
    TrackerKind.ACTIVITY -> "M3 12h4l3-8 4 16 3-8h4"
    TrackerKind.ART -> "M4 5h16v14H4zM4 16l5-5 4 4 2-2 5 5M14.5 8.5a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0-3 0"
    TrackerKind.PLANT -> "M12 21v-9M12 12C12 7 8.5 4 4 4c0 5 3.5 8 8 8zM12 15c0-4 3-7 8-7 0 4-3 7-8 7"
    TrackerKind.BIRD -> "M20 4C13 4 8 8 7 15l-3 5M7 15h7c3-2 5-6 6-11M10 11h6"
    TrackerKind.COURSE -> "M2 9l10-5 10 5-10 5zM6 11v5c3 2 9 2 12 0v-5M22 9v6"
    TrackerKind.PEOPLE -> "M4 5h16v11H9l-5 4z"
}

@Composable
private fun AddButton(label: String, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    Box(
        Modifier.size(40.dp).pressScale(press, 0.88f).clip(CircleShape).background(jc.accent)
            .clickable(press, ripple(), role = Role.Button, onClick = tapped(onClick)).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { JIcon(Ico.PLUS, jc.bg, size = 20.dp, stroke = 2.6f) }
}

/**
 * A bar per month, grown in once when first shown. [highlight] is the month drawn in full colour (this month), or -1.
 * With [labels], each month's initial sits under its bar and each count above it.
 */
@Composable
private fun MonthBars(counts: IntArray, highlight: Int, modifier: Modifier = Modifier, labels: Boolean = false) {
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(700, 80, JMotion.ease)) }
    val accent = jc.accent
    val faint = jc.accent.copy(alpha = 0.3f)
    val empty = jc.line
    val locale = Locale.getDefault()
    val described = remember(counts) {
        counts.withIndex().filter { it.value > 0 }.joinToString { "${Month.of(it.index + 1).getDisplayName(DateStyle.SHORT, locale)} ${it.value}" }
            .ifEmpty { "Nothing yet" }
    }
    val max = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
    Column(modifier.semantics { contentDescription = "By month: $described" }) {
        if (labels) Row(Modifier.fillMaxWidth()) {
            counts.forEachIndexed { i, c ->
                Text(
                    if (c > 0) "$c" else "", Modifier.weight(1f), style = JType.caption, textAlign = TextAlign.Center,
                    color = if (i == highlight) accent else jc.muted, fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val slot = size.width / 12
            val w = slot * 0.62f
            val nub = w * 0.55f
            counts.forEachIndexed { i, c ->
                val h = if (c == 0) nub else (nub + (size.height - nub) * c / max * grow.value)
                drawRoundRect(
                    if (c == 0) empty else if (i == highlight) accent else faint,
                    topLeft = Offset(i * slot + (slot - w) / 2, size.height - h), size = Size(w, h), cornerRadius = CornerRadius(w / 2, w / 2),
                )
            }
        }
        if (labels) Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            for (i in 0 until 12) {
                Text(
                    Month.of(i + 1).getDisplayName(DateStyle.NARROW, locale), Modifier.weight(1f), style = JType.caption, textAlign = TextAlign.Center,
                    color = if (i == highlight) accent else jc.muted, fontWeight = if (i == highlight) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

/**
 * One tracker: a year at a time with its big count, a month chart and a few numbers, then its entries month by
 * month. The button at the bottom adds an entry; the menu renames or deletes the tracker.
 */
@Composable
fun TrackerScreen(
    notes: List<NoteEntity>, id: String, backLabel: String, onRename: (NoteEntity, String) -> Unit, onDelete: (NoteEntity) -> Unit,
    onSave: (NoteEntity, List<TrackerEntry>) -> Unit, onBack: () -> Unit, photos: TrackerPhotos,
) {
    val trackers = remember(notes) { liveTrackers(notes) }
    val note = trackers.firstOrNull { it.id == id }
    if (note == null) {
        // Deleted here or on another device: nothing to show.
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val today = LocalDate.now()
    val entries = remember(note) { trackerEntries(note) }
    var year by rememberSaveable { mutableIntStateOf(today.year) }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<EntryEdit?>(null) }
    editing?.let { EntryEditor(trackers, it, photos, onSave) { editing = null } }
    if (renaming) NameDialog("Rename tracker", note.title, onDismiss = { renaming = false }, label = "Tracker") { renaming = false; onRename(note, it) }
    if (deleting) MarginDialog(
        onDismissRequest = { deleting = false },
        title = { Text("Delete \"${note.title}\"?") },
        text = { Text("It moves to Recently Deleted with its ${plural(entries.size, "entry", "entries")}.") },
        confirmButton = { TextButton(onClick = { deleting = false; onDelete(note); onBack() }) { Text("Delete", color = jc.danger) } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } },
    )

    val undated = entries.count { it.date == null }
    val years = entries.years(today.year) + if (undated > 0) listOf(NO_YEAR) else emptyList()
    val shown = entries.filter { if (year == NO_YEAR) it.date == null else it.date?.year == year }
    val glass = rememberGlass()
    Scaffold(
        containerColor = jc.page,
        topBar = {
            NavBar(backLabel, onBack, color = jc.page, glass = glass) {
                Box {
                    IconAction(Ico.MORE, "More", { menu = true })
                    DropdownMenu(menu, { menu = false }, containerColor = jc.solidCard) {
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = true })
                        DropdownMenuItem(text = { Text("Delete", color = jc.danger) }, onClick = { menu = false; deleting = true })
                    }
                }
            }
        },
        bottomBar = {
            Box(Modifier.fillMaxWidth().glassBackground(glass, jc.page).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
                PrimaryButton("Add to ${note.title}", { editing = EntryEdit(note.id, null) }, Modifier.fillMaxWidth())
            }
        },
    ) { padding ->
        LazyColumn(Modifier.glassBody(glass, padding).fillMaxSize(), contentPadding = glassPadding(glass, padding, PaddingValues(bottom = 24.dp))) {
            item {
                Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    KindTile(note.title, 56.dp)
                    Column(Modifier.weight(1f)) {
                        Text(note.title, style = JType.noteTitle, color = jc.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${plural(entries.size, "entry", "entries")} in all", style = JType.secondary, color = jc.muted)
                    }
                }
            }
            if (years.size > 1) item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    years.forEach { y -> Pill(if (y == NO_YEAR) "No date" else "$y", year == y, { year = y }) }
                }
                Spacer(Modifier.height(12.dp))
            }
            item { YearCard(entries, shown, year, today, Modifier.padding(horizontal = 16.dp)) }
            if (shown.isEmpty()) item {
                Text(
                    if (year == today.year) "Nothing yet this year. Tap Add when you finish one." else "Nothing in $year.",
                    Modifier.fillMaxWidth().padding(24.dp), style = JType.secondary, color = jc.muted, textAlign = TextAlign.Center,
                )
            }
            shown.groupBy { it.date?.monthValue }.forEach { (month, group) ->
                item(key = "m$month") {
                    Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            month?.let { Month.of(it).getDisplayName(DateStyle.FULL_STANDALONE, Locale.getDefault()) } ?: "No date",
                            Modifier.weight(1f), style = JType.section, color = jc.ink,
                        )
                        Text("${group.size}", style = JType.section, color = jc.muted)
                    }
                }
                item(key = "g$month") {
                    DividedCard(group, 76.dp) { entry ->
                        EntryRow(entry, photos) { editing = EntryEdit(note.id, entry) }
                    }
                }
            }
        }
    }
}

/** The chosen year at a glance: the count, the trend, the month chart and three small numbers. */
@Composable
private fun YearCard(all: List<TrackerEntry>, shown: List<TrackerEntry>, year: Int, today: LocalDate, modifier: Modifier = Modifier) {
    val locale = Locale.getDefault()
    Card(modifier) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${shown.size}", style = HeroNumber, color = jc.ink)
                Text(if (year == NO_YEAR) "  without a date" else "  in $year", Modifier.padding(bottom = 10.dp), style = JType.secondary, color = jc.muted)
            }
            if (year == today.year) Trend(shown.size, all.countSameTimeLastYear(today))
            if (year != NO_YEAR) {
                val counts = remember(all, year) { all.monthCounts(year) }
                Spacer(Modifier.height(20.dp))
                MonthBars(counts, if (year == today.year) today.monthValue - 1 else -1, Modifier.fillMaxWidth().height(130.dp), labels = true)
                Spacer(Modifier.height(18.dp))
                HorizontalDivider(color = jc.line)
                Spacer(Modifier.height(14.dp))
                val busiest = counts.withIndex().maxByOrNull { it.value }?.takeIf { it.value > 0 }
                Row {
                    Stat("Average", shown.averageRating()?.let { "★ " + String.format(locale, "%.1f", it) } ?: "—", Modifier.weight(1f))
                    Stat("Busiest", busiest?.let { Month.of(it.index + 1).getDisplayName(DateStyle.SHORT_STANDALONE, locale) } ?: "—", Modifier.weight(1f))
                    Stat("Per month", if (shown.isEmpty()) "—" else String.format(locale, "%.1f", shown.size / (if (year == today.year) today.monthValue else 12).toDouble()), Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = JType.rowTitle, color = jc.ink)
        Text(label, style = JType.small, color = jc.muted)
    }
}

@Composable
private fun EntryRow(entry: TrackerEntry, photos: TrackerPhotos, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        DateBadge(entry.date)
        Text(entry.title.ifBlank { "Untitled" }, Modifier.weight(1f), style = JType.item, color = jc.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        entry.rating?.let { Stars(it) }
        entry.photo?.let { PhotoThumb(photos.file(it), Modifier.size(40.dp, 52.dp).clip(JShape.xs).background(jc.inset)) }
    }
}

/** Whether the clipboard holds a photo, from its description alone (reading the clip itself shows a "pasted" notice). */
private fun clipHasImage(clipboard: android.content.ClipboardManager?): Boolean =
    runCatching { clipboard?.primaryClipDescription?.hasMimeType("image/*") == true }.getOrDefault(false)

/** A photo cropped to fill [modifier], read small off the main thread. */
@Composable
private fun PhotoThumb(file: File, modifier: Modifier) {
    val bitmap by produceState<ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.IO) { runCatching { decodeImage(file, 320)?.asImageBitmap() }.getOrNull() }
    }
    Box(modifier) { bitmap?.let { Image(it, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop) } }
}

/** The day of the month over its weekday, like a tear-off calendar. */
@Composable
private fun DateBadge(day: LocalDate?) {
    Column(
        Modifier.size(48.dp).clip(JShape.sm).background(jc.inset),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        if (day == null) Text("–", style = JType.rowTitle, color = jc.muted)
        else {
            Text("${day.dayOfMonth}", style = JType.rowTitle.copy(lineHeight = 20.sp), color = jc.ink)
            Text(day.dayOfWeek.getDisplayName(DateStyle.SHORT, Locale.getDefault()).uppercase(), style = JType.caption.copy(fontSize = 10.sp, letterSpacing = 0.06.em), color = jc.accent, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Stars(rating: Int) {
    Text(
        "★".repeat(rating) + "☆".repeat(5 - rating), style = JType.small.copy(letterSpacing = 1.sp), color = jc.accent,
        modifier = Modifier.semantics { contentDescription = "$rating out of 5" },
    )
}

/** Shows the add or edit dialog for [edit], then saves the tracker's whole new list through [onSave]. */
@Composable
private fun EntryEditor(trackers: List<NoteEntity>, edit: EntryEdit, photos: TrackerPhotos, onSave: (NoteEntity, List<TrackerEntry>) -> Unit, onClose: () -> Unit) {
    // The note as it is now, so a sync that landed while the dialog was open isn't overwritten.
    val note = trackers.firstOrNull { it.id == edit.noteId } ?: return
    EntryDialog(
        if (edit.original == null) "Add to ${note.title}" else "Edit entry", edit.original, photos, onDismiss = onClose,
        onDelete = edit.original?.let { old -> { onClose(); onSave(note, trackerEntries(note).replacing(old, null)) } },
    ) { entry -> onClose(); onSave(note, trackerEntries(note).replacing(edit.original, entry)) }
}

private fun dayLabel(day: LocalDate): String = day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

/** Adds or edits one entry: what it is, when, an optional rating out of 5 and an optional photo. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryDialog(title: String, initial: TrackerEntry?, photos: TrackerPhotos, onDismiss: () -> Unit, onDelete: (() -> Unit)?, onSave: (TrackerEntry) -> Unit) {
    var name by remember { mutableStateOf(initial?.title.orEmpty()) }
    var date by remember { mutableStateOf(if (initial == null) LocalDate.now() else initial.date) }
    var rating by remember { mutableStateOf(initial?.rating) }
    var picking by remember { mutableStateOf(false) }
    var photo by remember { mutableStateOf(initial?.photo) }
    var busy by remember { mutableStateOf(false) }
    var shot by remember { mutableStateOf<Pair<File, Uri>?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Every photo is stored small: a few MB from the camera becomes a JPEG of a few hundred KB at most.
    fun store(uri: Uri, then: suspend () -> Unit = {}) {
        busy = true
        scope.launch {
            try { photo = photos.compress(uri, name.trim().ifBlank { "Photo" } + ".jpg") }
            catch (_: Exception) { Toast.makeText(context, "Couldn't add that photo.", Toast.LENGTH_SHORT).show() }
            finally { then(); busy = false }
        }
    }
    // Paste shows while the clipboard holds a photo (copied from a browser, Photos, a chat...). Only the clip's
    // description is checked until Paste is tapped, so Android doesn't announce a paste that didn't happen.
    val clipboard = remember { context.getSystemService(android.content.ClipboardManager::class.java) }
    var canPaste by remember { mutableStateOf(clipHasImage(clipboard)) }
    DisposableEffect(clipboard) {
        val listener = android.content.ClipboardManager.OnPrimaryClipChangedListener { canPaste = clipHasImage(clipboard) }
        clipboard?.addPrimaryClipChangedListener(listener)
        onDispose { clipboard?.removePrimaryClipChangedListener(listener) }
    }
    fun paste() {
        val uri = runCatching { clipboard?.primaryClip?.let { clip -> (0 until clip.itemCount).firstNotNullOfOrNull { clip.getItemAt(it).uri } } }.getOrNull()
        if (uri == null) Toast.makeText(context, "There's no photo to paste.", Toast.LENGTH_SHORT).show() else store(uri)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { store(it) } }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val (file, uri) = shot ?: return@rememberLauncherForActivityResult
        shot = null
        // The camera's full-size file is only a step on the way: drop it once compressed.
        if (ok) store(uri) { withContext(Dispatchers.IO) { file.delete() } } else file.delete()
    }
    if (picking) EntryDateDialog(date ?: LocalDate.now(), onDismiss = { picking = false }) { date = it; picking = false }
    MarginDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Title") }, shape = JShape.sm)
                Row(
                    Modifier.fillMaxWidth().clip(JShape.sm).background(jc.surface).clickable(role = Role.Button) { picking = true }.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    JIcon(Ico.CALENDAR, jc.accent, size = 18.dp)
                    Text(date?.let(::dayLabel) ?: "No date", Modifier.weight(1f), style = JType.secondary, color = jc.ink)
                    Text(if (date == null) "Set" else "Change", style = JType.label, color = jc.accent)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    for (n in 1..5) {
                        val on = (rating ?: 0) >= n
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button) { rating = if (rating == n) null else n }
                                .semantics { contentDescription = "Rate $n out of 5" },
                            contentAlignment = Alignment.Center,
                        ) { Text(if (on) "★" else "☆", fontSize = 28.sp, color = if (on) jc.accent else jc.muted) }
                    }
                    Spacer(Modifier.weight(1f))
                    if (rating != null) TextAction("Clear", { rating = null }, color = jc.muted)
                }
                Row(
                    Modifier.fillMaxWidth().clip(JShape.sm).background(jc.surface).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(52.dp, 68.dp).clip(JShape.xs).background(jc.inset), contentAlignment = Alignment.Center) {
                        val p = photo
                        when {
                            busy -> CircularProgressIndicator(Modifier.size(22.dp), color = jc.accent, strokeWidth = 2.dp)
                            p != null -> PhotoThumb(photos.file(p), Modifier.matchParentSize())
                            else -> JIcon(Ico.CAMERA, jc.muted, size = 22.dp)
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            when { busy -> "Shrinking the photo…"; photo != null -> "Photo"; else -> "Add a cover or photo" },
                            style = JType.secondary, color = jc.ink,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextAction(if (photo == null) "Choose" else "Change", {
                                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }, enabled = !busy)
                            TextAction("Camera", {
                                val target = photos.cameraTarget()
                                shot = target
                                try { camera.launch(target.second) } catch (_: android.content.ActivityNotFoundException) {
                                    shot = null; target.first.delete()
                                    Toast.makeText(context, "No camera app on this phone.", Toast.LENGTH_SHORT).show()
                                }
                            }, enabled = !busy)
                            if (canPaste) TextAction("Paste", ::paste, enabled = !busy)
                            if (photo != null) TextAction("Remove", { photo = null }, color = jc.danger, enabled = !busy)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(TrackerEntry(date, name.trim(), rating, photo)) }, enabled = name.isNotBlank() && !busy) { Text("Save", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", color = jc.danger) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryDateDialog(current: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    // The picker's days are UTC midnights.
    val state = rememberDatePickerState(initialSelectedDateMillis = current.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(horizontal = 16.dp).widthIn(max = 400.dp).fillMaxWidth().clip(JShape.lg).background(jc.bg)
                .verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
        ) {
            DatePicker(
                state, title = null, headline = null, showModeToggle = false,
                colors = DatePickerDefaults.colors(
                    containerColor = jc.bg, selectedDayContainerColor = jc.accent, selectedDayContentColor = jc.bg,
                    todayDateBorderColor = jc.accent, todayContentColor = jc.accent,
                    selectedYearContainerColor = jc.accent, selectedYearContentColor = jc.bg,
                ),
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                TextAction("Cancel", onDismiss)
                TextAction("Set", {
                    state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                }, bold = true, enabled = state.selectedDateMillis != null)
            }
        }
    }
}
