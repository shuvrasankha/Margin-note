package com.margin.app.ui

import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.margin.app.R
import androidx.compose.ui.res.stringResource

/** One thing Margin can do. [opens]: the screen it lives on, when it has one of its own. */
internal data class Feature(val icon: String, @androidx.annotation.StringRes val title: Int, @androidx.annotation.StringRes val text: Int, val opens: Screen? = null)

/** Every feature, grouped as the page shows them. Keep this in step with the app when features change. */
internal val FEATURES: List<Pair<Int, List<Feature>>> = listOf(
    R.string.features_writing to listOf(
        Feature(Ico.TEXT, R.string.features_rich_notes, R.string.features_the_first_line_is_the),
        Feature(Ico.TEXT_SIZE, R.string.features_note_fonts_and_paper, R.string.features_pick_a_font_for_each),
        Feature(ICON_TEMPLATE, R.string.features_templates, R.string.features_start_from_meeting_lecture_lab),
        Feature(Ico.LINK, R.string.features_linked_notes, R.string.features_type_to_link_another_note),
        Feature(Ico.GRAMMAR, R.string.features_spelling_and_grammar, R.string.features_mistakes_are_underlined_as_you),
        Feature(Ico.SEARCH, R.string.features_find_and_replace, R.string.features_find_in_a_note_and),
        Feature(ICON_HISTORY, R.string.features_version_history, R.string.features_go_back_to_an_earlier),
        Feature(Ico.PEN, R.string.features_writing_stats, R.string.features_word_count_reading_time_and),
    ),
    R.string.features_math to listOf(
        Feature(Ico.NUMBERS, R.string.features_answers_as_you_type, R.string.features_end_a_line_with_to),
        Feature(Ico.EDIT, R.string.features_variables_and_equations, R.string.features_name_values_and_functions_rent),
        Feature(Ico.GRID, R.string.features_graphs, R.string.features_plot_every_y_in_a),
        Feature(Ico.TEXT_SIZE, R.string.features_math_keyboard, R.string.features_symbols_like_and_one_tap),
    ),
    R.string.features_attach_and_create to listOf(
        Feature(Ico.CAMERA, R.string.features_photos_and_files, R.string.features_add_photos_files_and_pdfs),
        Feature(Ico.PEN, R.string.features_drawings, R.string.features_sketch_in_a_note_with),
        Feature(Ico.HANDWRITING, R.string.features_handwriting_to_text, R.string.features_turn_handwriting_into_typed_text),
        Feature(Ico.SCAN, R.string.features_text_from_photos, R.string.features_scan_a_page_or_pick),
        Feature(ICON_SCAN_DOC, R.string.features_document_scanner, R.string.features_scan_paper_documents_straight_into),
        Feature(Ico.MIC, R.string.features_voice_notes, R.string.features_record_audio_in_a_note),
        Feature(ICON_TABLE, R.string.features_tables, R.string.features_add_a_table_and_edit),
        Feature(Ico.GLOBE, R.string.features_web_links, R.string.features_paste_a_web_address_to),
        Feature(ICON_PDF, R.string.features_pdf_tools, R.string.features_open_pdfs_with_margin_find),
    ),
    R.string.features_ai to listOf(
        Feature(Ico.SPARKLE, R.string.features_ask_ai, R.string.features_summarise_rewrite_translate_or_ask),
    ),
    R.string.features_organise to listOf(
        Feature(Ico.FOLDER, R.string.features_folders_inside_folders, R.string.features_nest_folders_move_notes_between),
        Feature(Ico.SMART_FOLDER, R.string.features_smart_folders, R.string.features_show_every_note_with_certain),
        Feature(Ico.PIN, R.string.features_pins_and_tags, R.string.features_pin_notes_to_the_top),
        Feature(Ico.SEARCH, R.string.features_search_everything, R.string.features_search_titles_bodies_and_the),
        Feature(Ico.TRASH, R.string.features_recently_deleted, R.string.features_deleted_notes_wait_here_so),
    ),
    R.string.features_plan_your_days to listOf(
        Feature(Ico.CHECKLIST, R.string.features_to_dos, R.string.features_every_checklist_in_one_place, Screen.Todos),
        Feature(Ico.BELL, R.string.features_reminders, R.string.features_remind_me_on_a_note, Screen.Reminders),
        Feature(Ico.CALENDAR, R.string.features_journal, R.string.features_a_note_for_each_day, Screen.Journal),
        Feature(Ico.CHECK, R.string.features_habits, R.string.features_tick_habits_off_each_day, Screen.Habits),
        Feature(Ico.LINES, R.string.features_trackers, R.string.features_lists_like_movies_watched_counted, Screen.Trackers),
    ),
    R.string.features_privacy to listOf(
        Feature(Ico.LOCK, R.string.features_locked_notes, R.string.features_lock_a_note_with_your),
        Feature(Ico.FINGERPRINT, R.string.features_app_lock, R.string.features_ask_for_your_passcode_or),
        Feature(Ico.KEY, R.string.features_passwords, R.string.features_a_password_vault_with_a, Screen.Passwords),
    ),
    R.string.features_sync_backup_and_sharing to listOf(
        Feature(Ico.SYNC, R.string.features_google_drive_sync, R.string.features_optional_notes_sync_through_hidden),
        Feature(Ico.CLOUD, R.string.features_backups_anywhere, R.string.features_password_sealed_backups_to_any),
        Feature(Ico.ARROW_DOWN, R.string.common_import, R.string.features_bring_notes_in_from_google),
        Feature(Ico.ARCHIVE, R.string.common_export, R.string.features_export_every_note_as_markdown),
        Feature(Ico.SHARE, R.string.features_share_as_image, R.string.features_turn_a_note_into_a),
    ),
    R.string.features_around_your_phone to listOf(
        Feature(Ico.COMPOSE, R.string.features_widget_and_quick_note, R.string.features_a_home_screen_widget_a),
        Feature(Ico.MOON, R.string.features_light_and_dark, R.string.features_follows_your_phones_theme_or),
        Feature(Ico.RESIZE, R.string.features_tablets, R.string.features_on_a_wide_screen_the),
    ),
)

/** The features page: everything Margin does, grouped and searchable; features with a screen of their own open it. */
@Composable
fun FeaturesScreen(onBack: () -> Unit, onOpen: (Screen) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim()
    val context = androidx.compose.ui.platform.LocalContext.current
    val groups = remember(q) {
        if (q.isEmpty()) FEATURES
        else FEATURES.mapNotNull { (name, list) ->
            list.filter { context.getString(it.title).contains(q, true) || context.getString(it.text).contains(q, true) || context.getString(name).contains(q, true) }
                .takeIf { it.isNotEmpty() }?.let { name to it }
        }
    }
    val pageList = rememberLazyListState()
    Scaffold(
        containerColor = jc.page,
        topBar = { NavBar(stringResource(R.string.common_settings), onBack, color = jc.page) },
    ) { padding ->
        LazyColumn(state = pageList, modifier = Modifier.padding(padding).fillMaxSize()) {
            item {
                PageTitle(stringResource(R.string.features_what_margin_can_do))
                Text(
                    stringResource(R.string.features_count_intro, FEATURES.sumOf { it.second.size }.toString()),
                    Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp), style = JType.para, color = jc.muted,
                )
                SearchField(query, { query = it }, stringResource(R.string.features_search_features), Modifier.padding(horizontal = 16.dp), fill = jc.card)
            }
            if (groups.isEmpty()) item {
                Text(stringResource(R.string.features_no_features_match, q), Modifier.padding(20.dp), style = JType.para, color = jc.muted)
            }
            groups.forEach { (name, list) ->
                item(key = name) {
                    SettingsLabel(stringResource(name))
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
            Text(stringResource(f.title), style = JType.item, color = jc.ink)
            Text(stringResource(f.text), style = JType.small, color = jc.muted)
        }
        if (f.opens != null) JIcon(Ico.CHEVRON, jc.muted, size = 18.dp)
    }
}
