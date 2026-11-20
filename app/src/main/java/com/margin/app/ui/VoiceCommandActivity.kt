package com.margin.app.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.margin.app.MainActivity
import com.margin.app.data.AppPrefs
import com.margin.app.data.Repeat
import com.margin.app.data.VoiceActions
import com.margin.app.data.VoiceCommand
import com.margin.app.data.VoiceCommands
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Voice commands over the home screen: "Remind me to call Sam tomorrow at 5", "Add milk and eggs to my
 * shopping list", "Take a note…". It listens with the phone's speech recogniser (or takes text from the
 * Assistant's "create a note"), shows what it understood, and acts only once Save is tapped, since speech is
 * often misheard and other apps can hand it text.
 */
@AndroidEntryPoint
class VoiceCommandActivity : ComponentActivity() {
    @Inject lateinit var actions: VoiceActions
    @Inject lateinit var appPrefs: AppPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val given = givenText(intent)
        setContent {
            val appearance by appPrefs.appearance.collectAsState()
            MarginTheme(appearance) {
                VoiceCommandCard(given, actions, onClose = ::finish, onOpen = { done ->
                    startActivity(
                        Intent(this, MainActivity::class.java).setAction(RecentWidget.ACTION_OPEN)
                            .putExtra(RecentWidget.EXTRA_NOTE, done.noteId).putExtra(RecentWidget.EXTRA_FOLDER, done.folderId)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    )
                    finish()
                })
            }
        }
    }

    companion object {
        const val ACTION = "com.margin.app.VOICE_COMMAND"
        private const val GMS_NAME = "com.google.android.gms.actions.extra.NAME"
        private const val GMS_TEXT = "com.google.android.gms.actions.extra.TEXT"

        /** Text handed in by the Assistant ("create a note") or a test, as one phrase; null to listen. */
        fun givenText(intent: Intent?): String? {
            intent ?: return null
            val name = intent.getStringExtra(GMS_NAME) ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)
            val text = intent.getStringExtra(GMS_TEXT) ?: intent.getStringExtra(Intent.EXTRA_TEXT)
            return listOfNotNull(name, text).map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(" ").ifEmpty { null }
        }
    }
}

/** One line saying what Save will do. */
internal fun describe(c: VoiceCommand?, now: LocalDateTime = LocalDateTime.now()): String = when (c) {
    null -> "Say or type what to do"
    is VoiceCommand.Remind -> "Reminder · ${whenText(c.at, now)}" + if (c.repeat != Repeat.NEVER) " · ${c.repeat.label}" else ""
    is VoiceCommand.AddToList -> "Add to ${c.list}"
    is VoiceCommand.Note -> "New note"
}

private fun whenText(at: LocalDateTime, now: LocalDateTime): String {
    val time = at.format(DateTimeFormatter.ofPattern("h:mm a"))
    return when (at.toLocalDate()) {
        now.toLocalDate() -> "Today, $time"
        now.toLocalDate().plusDays(1) -> "Tomorrow, $time"
        else -> at.format(DateTimeFormatter.ofPattern(if (at.year == LocalDate.now().year) "EEE d MMM" else "EEE d MMM yyyy")) + ", $time"
    }
}

/** The details under the summary: what the reminder says, or the items, or the note's text. */
private fun details(c: VoiceCommand?): List<String> = when (c) {
    null -> emptyList()
    is VoiceCommand.Remind -> listOf(c.what)
    is VoiceCommand.AddToList -> c.items
    is VoiceCommand.Note -> listOf(c.text)
}

@Composable
private fun VoiceCommandCard(given: String?, actions: VoiceActions, onClose: () -> Unit, onOpen: (VoiceActions.Done) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var said by rememberSaveable { mutableStateOf(given.orEmpty()) }
    var listened by rememberSaveable { mutableStateOf(given != null) }
    var noRecogniser by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    // Saveable, so turning the phone on the "Saved" card doesn't offer Save (and a second reminder) again.
    var savedId by rememberSaveable { mutableStateOf<String?>(null) }
    var savedFolder by rememberSaveable { mutableStateOf<String?>(null) }
    val done = savedId?.let { VoiceActions.Done(it, savedFolder ?: com.margin.app.data.DEFAULT_FOLDER_ID) }
    val command = remember(said) { VoiceCommands.parse(said, LocalDateTime.now()) }

    val listen = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { said = it }
        // Nothing heard on the first try: there's nothing to show, so go back.
        if (said.isBlank() && !listened) onClose()
        listened = true
    }
    fun startListening() = try {
        listen.launch(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PROMPT, "Try “Remind me to call Sam at 6”"),
        )
    } catch (_: ActivityNotFoundException) {
        noRecogniser = true; listened = true
    }
    LaunchedEffect(Unit) { if (!listened) startListening() }

    fun save() {
        val c = command ?: return
        saving = true
        scope.launch { actions.run(c).let { savedId = it.noteId; savedFolder = it.folderId }; saving = false }
    }
    // A reminder needs notifications to be seen; ask once, then save whatever the answer.
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { save() }
    fun onSave() {
        if (command is VoiceCommand.Remind && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifyPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS) else save()
    }

    BackHandler(onBack = onClose)
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClose)
            .safeDrawingPadding().imePadding().padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxWidth().clip(JShape.lg).background(jc.bg)
                .clickable(remember { MutableInteractionSource() }, indication = null) {}
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            val finished = done
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextAction(if (finished != null) "Done" else "Cancel", onClose, bold = finished != null)
                Spacer(Modifier.weight(1f))
                Text(if (finished != null) "Saved" else "Voice command", style = JType.rowTitle, color = jc.ink)
                Spacer(Modifier.weight(1f))
                if (finished != null) TextAction("Open", { onOpen(finished) })
                else TextAction("Save", ::onSave, bold = true, enabled = command != null && !saving)
            }
            if (finished == null) {
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Top) {
                    BasicTextField(
                        said, { said = it }, Modifier.weight(1f).heightIn(min = 48.dp),
                        textStyle = JType.body.copy(color = jc.ink), cursorBrush = SolidColor(jc.accent),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        decorationBox = { inner ->
                            Box {
                                if (said.isEmpty()) Text(if (noRecogniser) "Type a command" else "Listening…", style = JType.body, color = jc.muted)
                                inner()
                            }
                        },
                    )
                    if (!noRecogniser) Box(
                        Modifier.padding(start = 8.dp).size(44.dp).clip(CircleShape).background(jc.accent)
                            .clickable(role = Role.Button, onClickLabel = "Speak again") { startListening() },
                        contentAlignment = Alignment.Center,
                    ) { JIcon(Ico.MIC, jc.bg, size = 22.dp) }
                }
            }
            Column(Modifier.padding(top = 12.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(describe(command), style = JType.section, color = jc.accent)
                details(command).take(8).forEach { Text(if (command is VoiceCommand.AddToList) "• $it" else it, style = JType.row, color = jc.ink) }
                if (command is VoiceCommand.Remind && !actions.remindersOnTime) {
                    Text("Reminders may ring a few minutes late: allow alarms in Settings.", style = JType.small, color = jc.muted)
                }
                if (finished == null && said.isEmpty()) {
                    Text("“Add milk and eggs to my shopping list” · “Remind me every day at 8 am to take vitamins” · “Take a note…”", style = JType.small, color = jc.muted)
                }
            }
        }
    }
}
