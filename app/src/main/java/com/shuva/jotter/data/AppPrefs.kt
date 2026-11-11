package com.shuva.jotter.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** App-wide preferences (device-local, not synced). */
@Singleton
class AppPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("jotter_app", Context.MODE_PRIVATE)
    private val _autoSortChecked = MutableStateFlow(prefs.getBoolean(K_AUTO_SORT, false))
    val autoSortChecked: StateFlow<Boolean> = _autoSortChecked

    fun setAutoSortChecked(on: Boolean) {
        prefs.edit().putBoolean(K_AUTO_SORT, on).apply()
        _autoSortChecked.value = on
    }

    /** Sort that newly created folders start with. */
    private val _defaultSort = MutableStateFlow(
        (runCatching { NoteSort.valueOf(prefs.getString(K_DEFAULT_SORT, null)!!) }.getOrDefault(NoteSort.EDITED)) to
            prefs.getBoolean(K_DEFAULT_ASC, false),
    )
    val defaultSort: StateFlow<Pair<NoteSort, Boolean>> = _defaultSort

    fun setDefaultSort(sortBy: NoteSort, ascending: Boolean) {
        prefs.edit().putString(K_DEFAULT_SORT, sortBy.name).putBoolean(K_DEFAULT_ASC, ascending).apply()
        _defaultSort.value = sortBy to ascending
    }

    /** Note lists (by folder id, tag list id, ...) whose Pinned section is collapsed. */
    private val _collapsedPinned = MutableStateFlow(prefs.getStringSet(K_COLLAPSED, emptySet())!!.toSet())
    val collapsedPinned: StateFlow<Set<String>> = _collapsedPinned

    fun togglePinnedCollapsed(listId: String) {
        val next = _collapsedPinned.value.let { if (listId in it) it - listId else it + listId }
        prefs.edit().putStringSet(K_COLLAPSED, next).apply()
        _collapsedPinned.value = next
    }

    /** Notes password encrypted by the fingerprint key ("iv.ciphertext", base64), or null when off. Device-only. */
    private val _fingerprint = MutableStateFlow(prefs.getString(K_FINGERPRINT, null))
    val fingerprint: StateFlow<String?> = _fingerprint

    fun setFingerprint(blob: String?) {
        prefs.edit().putString(K_FINGERPRINT, blob).apply()
        _fingerprint.value = blob
    }

    /** Look of the app: theme, accent, text size and list preview lines. */
    private val _appearance = MutableStateFlow(
        Appearance(
            theme = enumOr(prefs.getString(K_THEME, null), ThemeMode.SYSTEM),
            accent = enumOr(prefs.getString(K_ACCENT, null), Accent.AMBER),
            textSize = enumOr(prefs.getString(K_TEXT_SIZE, null), TextSize.DEFAULT),
            previewLines = prefs.getInt(K_PREVIEW_LINES, 1).coerceIn(0, 2),
            gridView = prefs.getBoolean(K_GRID_VIEW, false),
        ),
    )
    val appearance: StateFlow<Appearance> = _appearance

    fun setAppearance(a: Appearance) {
        prefs.edit().putString(K_THEME, a.theme.name).putString(K_ACCENT, a.accent.name)
            .putString(K_TEXT_SIZE, a.textSize.name).putInt(K_PREVIEW_LINES, a.previewLines)
            .putBoolean(K_GRID_VIEW, a.gridView).apply()
        _appearance.value = a
    }

    /** On/off settings from the Settings screen. */
    private val _toggles = MutableStateFlow(Toggle.entries.associateWith { prefs.getBoolean(it.key, it.default) })
    val toggles: StateFlow<Map<Toggle, Boolean>> = _toggles

    fun setToggle(t: Toggle, on: Boolean) {
        prefs.edit().putBoolean(t.key, on).apply()
        _toggles.value = _toggles.value + (t to on)
    }

    /** How long Recently Deleted keeps a note. */
    private val _keepDeletedDays = MutableStateFlow(prefs.getInt(K_KEEP_DELETED, 30).takeIf { it in KEEP_DELETED_DAYS } ?: 30)
    val keepDeletedDays: StateFlow<Int> = _keepDeletedDays

    fun setKeepDeletedDays(days: Int) {
        if (days !in KEEP_DELETED_DAYS) return
        prefs.edit().putInt(K_KEEP_DELETED, days).apply()
        _keepDeletedDays.value = days
    }

    /** False until the welcome screen has been dismissed once. */
    private val _welcomeDone = MutableStateFlow(prefs.getBoolean(K_WELCOME, false))
    val welcomeDone: StateFlow<Boolean> = _welcomeDone

    fun setWelcomeDone() {
        prefs.edit().putBoolean(K_WELCOME, true).apply()
        _welcomeDone.value = true
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        runCatching { enumValueOf<T>(name!!) }.getOrDefault(fallback)

    private companion object {
        const val K_THEME = "theme"
        const val K_ACCENT = "accent"
        const val K_TEXT_SIZE = "textSize"
        const val K_PREVIEW_LINES = "previewLines"
        const val K_GRID_VIEW = "gridView"
        const val K_WELCOME = "welcomeDone"
        const val K_FINGERPRINT = "fingerprintPassword"
        const val K_AUTO_SORT = "autoSortChecked"
        const val K_DEFAULT_SORT = "defaultSort"
        const val K_DEFAULT_ASC = "defaultSortAscending"
        const val K_COLLAPSED = "collapsedPinned"
        const val K_KEEP_DELETED = "keepDeletedDays"
    }
}

val KEEP_DELETED_DAYS = listOf(7, 30, 90)

enum class Toggle(val key: String, val default: Boolean) {
    /** "[ ] " at the start of a line makes it a checklist line. */
    SMART_CHECKLISTS("smartChecklists", true),
    /** Typing "[[" suggests notes to link. */
    LINKS("linkSuggestions", true),
    GROUP_BY_DATE("groupByDate", true),
    HIDE_LOCKED_IN_SEARCH("hideLockedInSearch", false),
    /** Offline grammar rules underline mistakes in the editor. */
    GRAMMAR("grammarCheck", true),
    /** Also send note text (never locked notes) to LanguageTool for a fuller grammar check. Off until chosen. */
    CLOUD_GRAMMAR("cloudGrammar", false),
}

operator fun Map<Toggle, Boolean>.invoke(t: Toggle) = this[t] ?: t.default

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class Accent { AMBER, BLUE, GREEN, ROSE, VIOLET, GRAPHITE }
/** Text size multiplies the system font scale. */
enum class TextSize(val scale: Float, val label: String) {
    SMALL(0.9f, "Small"), DEFAULT(1f, "Default"), LARGE(1.12f, "Large"), LARGER(1.24f, "Larger"), LARGEST(1.3f, "Largest"),
}

data class Appearance(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val accent: Accent = Accent.AMBER,
    val textSize: TextSize = TextSize.DEFAULT,
    /** Lines of note text under each title in lists: 0, 1 or 2. */
    val previewLines: Int = 1,
    /** Note lists as a two-column grid of cards instead of rows. */
    val gridView: Boolean = false,
)
