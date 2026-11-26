package com.margin.app.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.margin.app.data.color
import com.margin.app.data.invoke
import kotlinx.coroutines.launch
import com.margin.app.R
import androidx.compose.ui.res.stringResource

// ---------- Launch ----------

/**
 * The logo on a cold start, centred: the mark writes itself in (the amber rule, then its three lines), then the
 * "Margin" wordmark (Inter Bold, -4.5% tracking) and the tagline rise in under it. Sized in dp, not sp, so the
 * lockup keeps its proportions at any font size. Under a second in all; system animation scale applies.
 */
@Composable
fun LaunchScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val px = LocalDensity.current
    val icon = 76.dp
    val word = with(px) { 40.dp.toSp() }
    val tagline = with(px) { 16.dp.toSp() }
    val mark = remember { androidx.compose.animation.core.Animatable(0f) }
    val words = remember { androidx.compose.animation.core.Animatable(0f) }
    val line = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { mark.animateTo(1f, tween(520, easing = JMotion.ease)) }
        launch { words.animateTo(1f, tween(360, delayMillis = 220, easing = JMotion.ease)) }
        launch { line.animateTo(1f, tween(360, delayMillis = 340, easing = JMotion.ease)) }
    }
    // Fades up from a little below.
    fun Modifier.rise(a: androidx.compose.animation.core.Animatable<Float, *>) = graphicsLayer {
        alpha = a.value
        translationY = (1f - a.value) * 10.dp.toPx()
    }
    Box(
        Modifier.fillMaxSize().background(jc.bg).semantics { contentDescription = context.getString(R.string.welcome_margin_write_first_organise_later) },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MarginMark(icon, Modifier.graphicsLayer {
                val k = 0.92f + 0.08f * mark.value.coerceAtMost(0.5f) * 2f
                scaleX = k; scaleY = k
            }, drawn = { mark.value })
            Text(
                "Margin", Modifier.padding(top = 24.dp).rise(words),
                style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = word, lineHeight = word, letterSpacing = (-0.045).em),
                color = jc.ink,
            )
            Text(
                stringResource(R.string.welcome_write_first_organise_later), Modifier.padding(top = 10.dp).rise(line),
                style = TextStyle(fontFamily = Inter, fontSize = tagline, lineHeight = tagline * 1.3f), color = jc.muted,
            )
        }
    }
}

// ---------- Welcome ----------

/** Shown once, on first launch. */
@Composable
fun WelcomeScreen(onStart: () -> Unit, onBackup: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(jc.bg).systemBarsPadding().padding(horizontal = 32.dp).padding(top = 72.dp, bottom = 24.dp),
    ) {
        MarginMark(72.dp)
        Text("Margin", Modifier.padding(top = 28.dp, bottom = 8.dp), style = JType.pageTitle.copy(fontSize = 40.sp, lineHeight = 46.sp, letterSpacing = (-0.03).em), color = jc.ink)
        Text(stringResource(R.string.welcome_write_first_organise_later), Modifier.padding(bottom = 48.dp), style = JType.body.copy(fontSize = 20.sp, lineHeight = 28.sp), color = jc.muted)
        Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
            listOf(
                Triple("M5 3h10l4 4v14H5zM9 12h6M9 16h4", stringResource(R.string.welcome_opens_to_a_blank_page), stringResource(R.string.welcome_tap_start_writing_and_type)),
                Triple("M4 12a8 8 0 0 1 14-5l2-2v6h-6l2-2a5 5 0 1 0 1 6", stringResource(R.string.welcome_syncs_quietly), stringResource(R.string.welcome_every_change_is_saved_and)),
                Triple("M6 11V8a6 6 0 0 1 12 0v3M5 11h14v10H5z", stringResource(R.string.welcome_private_by_default), stringResource(R.string.welcome_lock_any_note_with_your)),
            ).forEach { (icon, title, text) ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { JIcon(icon, jc.accent, size = 24.dp) }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(title, style = JType.rowTitle, color = jc.ink)
                        Text(text, style = JType.para, color = jc.muted)
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton(stringResource(R.string.welcome_start_writing), onStart, Modifier.fillMaxWidth())
        Box(
            Modifier.fillMaxWidth().padding(top = 8.dp).height(44.dp).clickable(role = Role.Button, onClick = onBackup),
            contentAlignment = Alignment.Center,
        ) { Text(stringResource(R.string.welcome_turn_on_backup), style = JType.secondary.copy(fontWeight = FontWeight.Medium), color = jc.accent) }
    }
}
