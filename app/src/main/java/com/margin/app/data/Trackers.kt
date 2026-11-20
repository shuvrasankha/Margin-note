package com.margin.app.data

import java.time.LocalDate

/**
 * A tracker ("Movies watched", "Books read") is a note whose id starts with [TRACKER_PREFIX]: its title is the
 * tracker's name and each body line is one entry, written as `2026-03-04 · Dune · ★4` (date and rating optional).
 * Plain text, so it syncs, backs up, searches and goes to Recently Deleted like any note, and older versions keep it.
 */
const val TRACKER_PREFIX = "tracker-"

fun isTracker(note: NoteEntity) = note.id.startsWith(TRACKER_PREFIX)

/** [photo]: a cover or snapshot (a compressed JPEG), kept as the attachment of the entry's line. */
data class TrackerEntry(val date: LocalDate?, val title: String, val rating: Int? = null, val photo: Attachment? = null)

private const val SEP = " · "
private val RATING = Regex("^★([1-5])$")
private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")

/** One body line as an entry; null for a blank line. A line that isn't in the format keeps its text as the title. */
fun parseTrackerEntry(line: String): TrackerEntry? {
    val text = line.trim()
    if (text.isEmpty()) return null
    var parts = text.split(SEP)
    val date = runCatching { LocalDate.parse(parts.first().trim()) }.getOrNull()
    if (date != null) parts = parts.drop(1)
    val rating = parts.lastOrNull()?.trim()?.let { RATING.find(it)?.groupValues?.get(1)?.toInt() }
    if (rating != null) parts = parts.dropLast(1)
    return TrackerEntry(date, parts.joinToString(SEP).trim(), rating)
}

fun formatTrackerEntry(entry: TrackerEntry): String =
    listOfNotNull(entry.date?.toString(), entry.title.replace('\n', ' ').trim(), entry.rating?.takeIf { it in 1..5 }?.let { "★$it" })
        .joinToString(SEP)

/** The tracker's entries, newest first; undated ones last, in the order they were written. */
fun trackerEntries(note: NoteEntity): List<TrackerEntry> =
    sortEntries(note.blocks.drop(1).mapNotNull { b -> parseTrackerEntry(b.text)?.copy(photo = b.attachment?.takeIf { it.isImage }) })

fun sortEntries(entries: List<TrackerEntry>): List<TrackerEntry> =
    entries.sortedWith(compareBy<TrackerEntry> { it.date == null }.thenByDescending { it.date })

/** The tracker's blocks for [entries]: the title line, then one line per entry, newest first. */
fun trackerBlocks(note: NoteEntity, entries: List<TrackerEntry>): List<Block> =
    listOf(note.blocks.first()) + sortEntries(entries).map { Block(text = formatTrackerEntry(it), attachment = it.photo) }

fun List<TrackerEntry>.countInYear(year: Int) = count { it.date?.year == year }

/** Entries dated from the start of last year up to the same day last year, to compare with this year so far. */
fun List<TrackerEntry>.countSameTimeLastYear(today: LocalDate): Int {
    val cutoff = today.minusYears(1)
    return count { it.date != null && it.date.year == cutoff.year && !it.date.isAfter(cutoff) }
}

/** How many entries fall in each month of [year], January first. */
fun List<TrackerEntry>.monthCounts(year: Int): IntArray =
    IntArray(12).also { c -> forEach { e -> if (e.date?.year == year) c[e.date.monthValue - 1]++ } }

/** The average star rating of the rated entries, or null when none is rated. */
fun List<TrackerEntry>.averageRating(): Double? = mapNotNull { it.rating }.takeIf { it.isNotEmpty() }?.average()

/** Years that have entries, plus [current], newest first. */
fun List<TrackerEntry>.years(current: Int): List<Int> = (mapNotNull { it.date?.year } + current).distinct().sortedDescending()

/** Replaces the first entry equal to [old] with [new] (or adds [new] when [old] is null or gone). Null [new] removes it. */
fun List<TrackerEntry>.replacing(old: TrackerEntry?, new: TrackerEntry?): List<TrackerEntry> {
    val i = if (old == null) -1 else indexOf(old)
    return when {
        i < 0 -> if (new == null) this else this + new
        new == null -> filterIndexed { k, _ -> k != i }
        else -> mapIndexed { k, e -> if (k == i) new else e }
    }
}

/** What a tracker is about, guessed from its name; the screen draws an icon for each. */
enum class TrackerKind { MOVIE, TV, BOOK, PODCAST, MUSIC, GAME, PLACE, FOOD, COFFEE, DRINK, HIKE, ACTIVITY, ART, PLANT, BIRD, COURSE, PEOPLE }

private val KINDS = listOf(
    listOf("movie", "film", "cinema") to TrackerKind.MOVIE,
    listOf("tv", "show", "series", "episode", "anime") to TrackerKind.TV,
    listOf("book", "read", "novel", "comic", "manga") to TrackerKind.BOOK,
    listOf("podcast") to TrackerKind.PODCAST,
    listOf("music", "album", "song", "concert", "gig") to TrackerKind.MUSIC,
    listOf("game") to TrackerKind.GAME,
    listOf("place", "travel", "trip", "country", "countries", "city", "cities", "visit") to TrackerKind.PLACE,
    listOf("restaurant", "food", "cafe", "café", "eat", "dish", "recipe", "cook") to TrackerKind.FOOD,
    listOf("coffee") to TrackerKind.COFFEE,
    listOf("wine", "beer", "cocktail") to TrackerKind.DRINK,
    listOf("hike", "trail", "mountain") to TrackerKind.HIKE,
    listOf("run", "workout", "gym", "swim", "ride", "cycle") to TrackerKind.ACTIVITY,
    listOf("museum", "art", "gallery", "exhibit") to TrackerKind.ART,
    listOf("plant", "garden") to TrackerKind.PLANT,
    listOf("bird") to TrackerKind.BIRD,
    listOf("course", "class", "learn") to TrackerKind.COURSE,
    listOf("people", "friend", "date") to TrackerKind.PEOPLE,
)

/** The kind that fits the tracker's name ("Movies watched" is a movie tracker); null when nothing matches. */
fun trackerKind(name: String): TrackerKind? {
    // Match the start of a word, so "art" finds "Art I've seen" but not "Party".
    val words = name.lowercase().split(NON_WORD).filter { it.isNotEmpty() }
    return KINDS.firstOrNull { (keys, _) -> keys.any { k -> words.any { it.startsWith(k) } } }?.second
}

/** Trackers offered with one tap, in order. */
val TRACKER_STARTERS = listOf("Movies", "Books", "TV shows", "Games", "Places", "Restaurants", "Albums")
