package com.margin.app.ui

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.sp

import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.margin.app.data.KEEP_DELETED_DAYS
import com.margin.app.data.NoteSort
import com.margin.app.data.TextSize
import com.margin.app.data.ThemeMode
import com.margin.app.data.Toggle
import com.margin.app.data.color
import com.margin.app.data.invoke
import com.margin.app.sync.signInWithGoogle
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import com.margin.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource

// ---------- Settings ----------

/** Google's four-colour "G" (48 x 48). */
internal val GoogleG: androidx.compose.ui.graphics.vector.ImageVector by lazy {
    androidx.compose.ui.graphics.vector.ImageVector.Builder("GoogleG", 18.dp, 18.dp, 48f, 48f).apply {
        listOf(
            0xFFEA4335 to "M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z",
            0xFF4285F4 to "M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z",
            0xFFFBBC05 to "M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z",
            0xFF34A853 to "M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z",
        ).forEach { (color, d) ->
            addPath(androidx.compose.ui.graphics.vector.addPathNodes(d), fill = androidx.compose.ui.graphics.SolidColor(Color(color)))
        }
    }.build()
}

@Composable
internal fun SettingsLabel(text: String) {
    Text(text.uppercase(), Modifier.padding(start = 16.dp, top = 28.dp, bottom = 8.dp), style = JType.groupLabel, color = jc.muted)
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun SettingsScreen(vm: SettingsViewModel, backups: BackupsViewModel, onBack: () -> Unit, onFeatures: () -> Unit = {}) {
    val status by vm.status.collectAsState()
    val pending by vm.pending.collectAsState()
    val online by vm.online.collectAsState()
    val lockedCount by vm.lockedCount.collectAsState()
    val unlocked by vm.unlocked.collectAsState()
    val fingerprint by vm.fingerprint.collectAsState()
    var askCurrent by remember { mutableStateOf<(() -> Unit)?>(null) }
    var newPassword by remember { mutableStateOf(false) }
    val appLockOn = vm.appLockState.collectAsState().value != null
    /** Turning the app lock on (true) or off: the passcode step it asks for. */
    var appLockAsk by remember { mutableStateOf<Pair<PasswordMode, Boolean>?>(null) }
    val autoSort by vm.autoSortChecked.collectAsState()
    val appearance by vm.appearance.collectAsState()
    val defaultSort by vm.defaultSort.collectAsState()
    val toggles by vm.toggles.collectAsState()
    val keepDays by vm.keepDeletedDays.collectAsState()
    val exportContext = LocalContext.current
    val exportTo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { vm.exportAll(it) { msg -> Toast.makeText(exportContext, msg, Toast.LENGTH_LONG).show() } }
    }
    val importFrom = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.importFiles(uris) { msg -> Toast.makeText(exportContext, msg, Toast.LENGTH_LONG).show() }
    }
    var shorterKeep by remember { mutableStateOf<Int?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmRestore by remember { mutableStateOf(false) }
    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        vm.onConsentResult(it.data)
    }
    LaunchedEffect(vm.consentIntent) {
        vm.consentIntent?.let { consentLauncher.launch(IntentSenderRequest.Builder(it.intentSender).build()) }
    }

    val pageScroll = rememberScrollState()
    Scaffold(
        containerColor = jc.page,
        topBar = { if (LocalTabRoot.current) TabTop() else NavBar(null, {}, color = jc.page) { TextAction(stringResource(R.string.common_done), onBack, bold = true) } },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(pageScroll)) {
            PageTitle(stringResource(R.string.common_settings))
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 48.dp)) {
                // Account and sync, compact: status with a sync button, then Restore and Sign out side by side.
                val signIn = {
                    scope.launch {
                        runCatching { signInWithGoogle(context as Activity) }
                            .onSuccess { vm.message = null; vm.onSignedIn(it) }
                            .onFailure {
                                // Backing out of the account picker is not an error.
                                vm.message = if (it is androidx.credentials.exceptions.GetCredentialCancellationException) null
                                else context.getString(R.string.settings_sign_in_failed, it.message ?: it.javaClass.simpleName)
                            }
                    }
                    Unit
                }
                Card {
                    Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Google's "G" on a white tile, as Google's sign-in branding asks.
                        Box(Modifier.size(30.dp).clip(JShape.xs).background(Color.White), contentAlignment = Alignment.Center) {
                            androidx.compose.foundation.Image(GoogleG, contentDescription = stringResource(R.string.settings_google), modifier = Modifier.size(18.dp))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (status.signedIn) {
                                val waiting = pluralStringResource(R.plurals.settings_changes, pending, pending)
                                Text(
                                    when {
                                        !online -> if (pending > 0) stringResource(R.string.settings_offline_waiting, waiting) else stringResource(R.string.settings_offline)
                                        pending > 0 -> stringResource(R.string.settings_waiting_to_sync, waiting)
                                        else -> stringResource(R.string.settings_everything_is_synced)
                                    },
                                    style = JType.item, color = jc.ink,
                                )
                                Text(
                                    // The time first: a long email is what gets cut.
                                    listOfNotNull(status.lastSyncAt?.let { stringResource(R.string.settings_last_sync, listTime(context, it)) } ?: stringResource(R.string.settings_last_sync_never), status.email ?: status.name).joinToString(" · "),
                                    style = JType.small, color = jc.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            } else {
                                Text(stringResource(R.string.settings_back_up_to_google_drive), style = JType.item, color = jc.ink)
                                Text(stringResource(R.string.settings_optional_notes_go_to_hidden), style = JType.small, color = jc.muted)
                            }
                        }
                        if (!status.signedIn) TextAction(stringResource(R.string.settings_sign_in), signIn, bold = true)
                        else if (vm.busy) Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                            androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), color = jc.accent, strokeWidth = 2.dp)
                        } else Box(
                            Modifier.size(44.dp).clip(CircleShape).background(jc.line).clickable(role = Role.Button) { vm.syncNow() }
                                .semantics { contentDescription = context.getString(R.string.settings_sync_now) },
                            contentAlignment = Alignment.Center,
                        ) { JIcon(Ico.SYNC, jc.ink, size = 20.dp, stroke = 1.8f) }
                    }
                    if (status.signedIn) {
                        CardDivider()
                        // Two equal halves with a rule between them.
                        Row(Modifier.fillMaxWidth().height(48.dp)) {
                            @Composable fun half(label: String, color: Color, onClick: () -> Unit) = Box(
                                Modifier.weight(1f).fillMaxHeight().clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center,
                            ) { Text(label, style = JType.item.copy(fontSize = 15.sp), color = color, fontWeight = FontWeight.Medium, maxLines = 1) }
                            half(stringResource(R.string.settings_restore_from_drive), jc.accent) { if (!vm.busy) confirmRestore = true }
                            Box(Modifier.width(1.dp).fillMaxHeight().background(jc.line))
                            half(stringResource(R.string.settings_sign_out), jc.danger) { if (!vm.busy) vm.signOut() }
                        }
                    }
                }
                status.lastError?.let { Text(stringResource(R.string.settings_last_error, it), Modifier.padding(start = 16.dp, top = 8.dp), style = JType.small, color = jc.danger) }
                vm.message?.let { Text(it, Modifier.padding(start = 16.dp, top = 8.dp), style = JType.small, color = jc.muted) }

                BackupsSection(backups)

                SettingsLabel(stringResource(R.string.settings_appearance))
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        @Composable
                        fun <T> choices(icon: String, title: String, options: List<Pair<T, String>>, current: T, onPick: (T) -> Unit) {
                            IconTitle(icon, title)
                            // One segmented track: the choice sits on a raised segment, the rest are muted.
                            Row(
                                Modifier.padding(start = 42.dp).fillMaxWidth().height(40.dp).clip(JShape.sm).background(jc.page).padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                options.forEach { (value, label) ->
                                    val on = value == current
                                    Box(
                                        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(9.dp)).background(if (on) jc.line else Color.Transparent)
                                            .clickable(role = Role.RadioButton) { onPick(value) }.semantics { selected = on },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(label, style = JType.small.copy(fontSize = 14.sp), color = if (on) jc.ink else jc.muted, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
                                    }
                                }
                            }
                        }
                        choices(Ico.MOON, stringResource(R.string.settings_theme), listOf(ThemeMode.SYSTEM to stringResource(R.string.settings_system), ThemeMode.LIGHT to stringResource(R.string.settings_light), ThemeMode.DARK to stringResource(R.string.settings_dark)), appearance.theme) {
                            vm.setAppearance(appearance.copy(theme = it))
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconTitle(Ico.TEXT_SIZE, stringResource(R.string.settings_text_size), Modifier.weight(1f))
                            Text(stringResource(appearance.textSize.labelRes), style = JType.secondary, color = jc.muted)
                        }
                        Row(Modifier.padding(start = 42.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("A", style = JType.small, color = jc.muted)
                            Slider(
                                value = appearance.textSize.ordinal.toFloat(),
                                onValueChange = { vm.setAppearance(appearance.copy(textSize = TextSize.entries[it.roundToInt()])) },
                                valueRange = 0f..TextSize.entries.lastIndex.toFloat(),
                                steps = TextSize.entries.size - 2,
                                modifier = Modifier.weight(1f).semantics { stateDescription = context.getString(appearance.textSize.labelRes) },
                                colors = SliderDefaults.colors(thumbColor = jc.accent, activeTrackColor = jc.accent, inactiveTrackColor = jc.line),
                                thumb = { Box(Modifier.size(24.dp).shadow(JElevation.card, CircleShape).background(jc.bg, CircleShape).border(2.dp, jc.accent, CircleShape)) },
                                track = { state -> SizeTrack(state.value, state.valueRange.endInclusive) },
                            )
                            Text("A", style = JType.heading, color = jc.muted)
                        }
                        Spacer(Modifier.height(6.dp))
                        choices(Ico.LINES, stringResource(R.string.settings_preview_lines_in_lists), listOf(0 to stringResource(R.string.settings_none), 1 to stringResource(R.string.settings_1_line), 2 to stringResource(R.string.settings_2_lines)), appearance.previewLines) {
                            vm.setAppearance(appearance.copy(previewLines = it))
                        }
                    }
                }

                SettingsLabel(stringResource(R.string.settings_writing))
                Card {
                    CardRow(stringResource(R.string.settings_move_checked_items_to_the), icon = Ico.SINK, sub = stringResource(R.string.settings_in_checklists_ticked_items_sink)) {
                        JSwitch(autoSort, { vm.setAutoSortChecked(it) })
                    }
                    CardDivider()
                    CardRow(stringResource(R.string.settings_smart_checklists), icon = Ico.CHECKLIST, sub = stringResource(R.string.settings_lines_that_start_with_become)) {
                        JSwitch(toggles(Toggle.SMART_CHECKLISTS), { vm.setToggle(Toggle.SMART_CHECKLISTS, it) })
                    }
                    CardDivider()
                    CardRow(stringResource(R.string.settings_link_with), icon = Ico.LINK, sub = stringResource(R.string.settings_type_two_brackets_to_link)) {
                        JSwitch(toggles(Toggle.LINKS), { vm.setToggle(Toggle.LINKS, it) })
                    }
                    CardDivider()
                    CardRow(stringResource(R.string.settings_check_grammar), icon = Ico.GRAMMAR, sub = stringResource(R.string.settings_underlines_common_mistakes_in_blue)) {
                        JSwitch(toggles(Toggle.GRAMMAR), { vm.setToggle(Toggle.GRAMMAR, it) })
                    }
                    CardDivider()
                    CardRow(
                        stringResource(R.string.settings_better_grammar_checking), icon = Ico.CLOUD,
                        sub = stringResource(R.string.settings_sends_note_text_to_languagetool),
                    ) {
                        JSwitch(toggles(Toggle.CLOUD_GRAMMAR), { vm.setToggle(Toggle.CLOUD_GRAMMAR, it) }, enabled = toggles(Toggle.GRAMMAR))
                    }
                }

                SettingsLabel(stringResource(R.string.settings_ai))
                AiSettingsCard(vm.ai)

                SettingsLabel(stringResource(R.string.settings_notes_list))
                Card {
                    val (sortBy, ascending) = defaultSort
                    val key = when (sortBy) { NoteSort.EDITED -> stringResource(R.string.settings_date_edited); NoteSort.CREATED -> stringResource(R.string.settings_date_created); NoteSort.TITLE -> stringResource(R.string.settings_title) }
                    val direction = if (sortBy == NoteSort.TITLE) (if (ascending) stringResource(R.string.settings_a_to_z) else stringResource(R.string.settings_z_to_a))
                    else if (ascending) stringResource(R.string.settings_oldest_first) else stringResource(R.string.settings_newest_first)
                    CardRow(stringResource(R.string.settings_sort_new_folders_by), icon = Ico.SORT, sub = "$key, $direction") {
                        SortMenu(sortBy, ascending, label = stringResource(R.string.common_change), onChange = vm::setDefaultSort)
                    }
                    CardDivider()
                    CardRow(stringResource(R.string.settings_group_by_date), icon = Ico.CALENDAR, sub = stringResource(R.string.settings_today_yesterday_previous_7_days)) {
                        JSwitch(toggles(Toggle.GROUP_BY_DATE), { vm.setToggle(Toggle.GROUP_BY_DATE, it) })
                    }
                }

                SettingsLabel(stringResource(R.string.settings_privacy_data))
                Card {
                    CardRow(stringResource(R.string.settings_hide_locked_notes_in_search), icon = Ico.EYE_OFF) {
                        JSwitch(toggles(Toggle.HIDE_LOCKED_IN_SEARCH), { vm.setToggle(Toggle.HIDE_LOCKED_IN_SEARCH, it) })
                    }
                    CardDivider()
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        IconTitle(Ico.TRASH, stringResource(R.string.settings_keep_deleted_notes))
                        Row(Modifier.padding(start = 42.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Shorter removes older deleted notes for good, so it asks first.
                            KEEP_DELETED_DAYS.forEach { d -> Pill(pluralStringResource(R.plurals.settings_keep_days_pill, d, d), d == keepDays, { if (d < keepDays) shorterKeep = d else vm.setKeepDeletedDays(d) }) }
                        }
                    }
                    CardDivider()
                    CardRow(
                        stringResource(R.string.settings_export_all_as_markdown), icon = Ico.ARCHIVE, sub = stringResource(R.string.settings_a_zip_of_every_note),
                        onClick = { exportTo.launch(context.getString(R.string.settings_margin_export_zip, java.time.LocalDate.now())) },
                    )
                    CardDivider()
                    CardRow(
                        if (vm.importing) stringResource(R.string.settings_importing) else stringResource(R.string.settings_import_notes), icon = Ico.ARROW_DOWN,
                        sub = stringResource(R.string.settings_from_google_keep_the_takeout),
                        labelColor = if (vm.importing) jc.muted else jc.accent,
                        onClick = if (vm.importing) null else ({ importFrom.launch(arrayOf("*/*")) }),
                    )
                }

                // Fingerprint unlock, wherever a passcode exists: under locked notes, or under the app lock.
                @Composable fun fingerprintRow() {
                    val activity = context as? FragmentActivity
                    if (activity != null && Biometric.available(activity)) {
                        CardDivider()
                        CardRow(stringResource(R.string.settings_unlock_with_fingerprint), icon = Ico.FINGERPRINT) {
                            JSwitch(fingerprint != null, { on ->
                                if (!on) {
                                    vm.setFingerprint(null)
                                    Biometric.forget()
                                } else {
                                    val enable = {
                                        scope.launch {
                                            val pw = vm.passwordForFingerprint() ?: return@launch
                                            val bytes = String(pw).toByteArray(Charsets.UTF_8)
                                            pw.fill('\u0000')
                                            when (val r = Biometric.encrypt(activity, bytes)) {
                                                is Biometric.Result.Ok -> {
                                                    val b64 = java.util.Base64.getUrlEncoder().withoutPadding()
                                                    vm.setFingerprint(b64.encodeToString(r.iv) + "." + b64.encodeToString(r.bytes))
                                                }
                                                is Biometric.Result.Failed -> vm.message = r.message
                                                else -> {}
                                            }
                                            bytes.fill(0)
                                        }
                                        Unit
                                    }
                                    if (unlocked) enable() else askCurrent = enable
                                }
                            })
                        }
                    }
                }
                SettingsLabel(stringResource(R.string.settings_app_lock))
                Card {
                    CardRow(
                        stringResource(R.string.settings_lock_margin), icon = Ico.LOCK,
                        sub = stringResource(if (fingerprint != null) R.string.settings_lock_margin_sub_fingerprint else R.string.settings_lock_margin_sub),
                    ) {
                        JSwitch(appLockOn, { on ->
                            scope.launch { appLockAsk = (if (on && !vm.hasPasscode()) PasswordMode.CREATE else PasswordMode.ENTER) to on }
                        })
                    }
                    if (appLockOn && lockedCount == 0) fingerprintRow()
                }

                SettingsLabel(stringResource(R.string.settings_locked_notes))
                Card {
                    if (lockedCount == 0) {
                        CardRow(
                            stringResource(R.string.settings_no_locked_notes), icon = Ico.LOCK,
                            sub = stringResource(if (appLockOn) R.string.settings_lock_a_note_app_lock else R.string.settings_lock_a_note_new),
                        )
                    } else {
                        CardRow(
                            pluralStringResource(R.plurals.settings_locked_notes_count, lockedCount, lockedCount), icon = Ico.LOCK,
                            sub = if (unlocked) stringResource(R.string.settings_unlocked_until_you_leave_the) else stringResource(R.string.settings_your_passcode_opens_them_and),
                        )
                        if (unlocked) {
                            CardDivider()
                            CardRow(stringResource(R.string.settings_lock_now), icon = Ico.LOCK, onClick = vm::lockNow)
                        }
                        CardDivider()
                        CardRow(stringResource(R.string.settings_change_passcode), icon = Ico.KEY, onClick = {
                            if (vm.busy) return@CardRow
                            if (unlocked) newPassword = true else askCurrent = { newPassword = true }
                        })
                        fingerprintRow()
                    }
                }
                SettingsLabel(stringResource(R.string.settings_about))
                Card {
                    CardRow(stringResource(R.string.settings_what_margin_can_do), icon = Ico.SPARKLE, sub = stringResource(R.string.settings_every_feature_in_one_list), onClick = onFeatures) {
                        JIcon(Ico.CHEVRON, jc.muted, size = 18.dp)
                    }
                }
                // Who and which version, quietly at the end.
                Column(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    MarginMark(36.dp)
                    Text(stringResource(R.string.settings_version, com.margin.app.BuildConfig.VERSION_NAME), Modifier.padding(top = 8.dp), style = JType.small, color = jc.muted)
                }
            }
        }
    }

    askCurrent?.let { then ->
        var old by remember { mutableStateOf(false) }
        val submit: suspend (CharArray) -> String? = { pw ->
            // An old password, or a passcode with saved passwords, brings up the switch (MarginRoot), which replaces this step.
            if (vm.checkPassword(pw)) { askCurrent = null; if (!vm.legacyPassword.value && (vm.passphrase.value || !vm.hasLogins.value)) then(); null } else com.margin.app.data.UnlockLimit.message(context, if (old) context.getString(R.string.settings_wrong_password) else context.getString(R.string.settings_wrong_passcode))
        }
        if (old) OldPasswordDialog(onDismiss = { askCurrent = null }, onSubmit = submit)
        else PasscodeDialog(PasswordMode.ENTER, onDismiss = { askCurrent = null }, onOldPassword = { old = true }, onSubmit = submit)
    }
    appLockAsk?.let { (mode, on) ->
        PasscodeDialog(
            mode, onDismiss = { appLockAsk = null },
            intro = stringResource(R.string.settings_choose_a_passcode_to_open),
            enterSub = if (on) stringResource(R.string.settings_your_notes_passcode_will_open) else stringResource(R.string.settings_to_turn_off_the_app),
        ) { code ->
            if (if (on) vm.turnOnAppLock(code) else vm.turnOffAppLock(code)) { appLockAsk = null; null } else com.margin.app.data.UnlockLimit.message(context, context.getString(R.string.settings_wrong_passcode))
        }
    }
    if (newPassword) {
        PasscodeDialog(PasswordMode.CREATE, onDismiss = { newPassword = false }, passphraseOnly = vm.hasLogins.collectAsState().value) { password ->
            vm.changePassword(password)
            newPassword = false
            null
        }
    }
    shorterKeep?.let { d ->
        MarginDialog(
            onDismissRequest = { shorterKeep = null },
            title = { Text(stringResource(R.string.settings_keep_deleted_notes_for_days, d)) },
            text = { Text(stringResource(R.string.settings_notes_in_recently_deleted_for, d)) },
            confirmButton = { TextButton(onClick = { vm.setKeepDeletedDays(d); shorterKeep = null }) { Text(stringResource(R.string.settings_keep_days, d), color = jc.danger) } },
            dismissButton = { TextButton(onClick = { shorterKeep = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
    if (confirmRestore) {
        MarginDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text(stringResource(R.string.settings_restore_from_google_account)) },
            text = { Text(stringResource(R.string.settings_notes_saved_in_your_google)) },
            confirmButton = { TextButton(onClick = { vm.restore(); confirmRestore = false }) { Text(stringResource(R.string.common_restore), fontWeight = FontWeight.SemiBold) } },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

/** The text-size track: a rounded line with a dot for each size, filled up to the chosen one. */
@Composable
internal fun SizeTrack(value: Float, last: Float) {
    val on = jc.accent
    val off = jc.line
    Canvas(Modifier.fillMaxWidth().height(24.dp)) {
        val y = size.height / 2
        val r = 12.dp.toPx() // keeps the ends under the thumb's centre
        val w = size.width - 2 * r
        val x = r + w * (value / last)
        drawLine(off, Offset(r, y), Offset(r + w, y), 4.dp.toPx(), StrokeCap.Round)
        drawLine(on, Offset(r, y), Offset(x, y), 4.dp.toPx(), StrokeCap.Round)
        for (k in 0..last.toInt()) drawCircle(if (k <= value) on else off, 4.dp.toPx(), Offset(r + w * k / last, y))
    }
}
