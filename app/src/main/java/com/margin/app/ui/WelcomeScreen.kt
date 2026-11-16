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

// ---------- Launch ----------

/**
 * The full logo on a cold start: the icon beside the "Margin" wordmark (Inter Bold, -4.5% tracking) with the
 * tagline under it. Sized in dp, not sp, so the lockup keeps its proportions at any font size.
 */
@Composable
fun LaunchScreen() {
    val px = LocalDensity.current
    val icon = 56.dp
    val word = with(px) { (icon * 1.06f).toSp() } // the icon is about 1.3x the wordmark's capital height
    val tagline = with(px) { (icon * 0.3f).toSp() }
    Box(
        Modifier.fillMaxSize().background(jc.bg).semantics { contentDescription = "Margin. Write first. Organise later." },
        contentAlignment = Alignment.Center,
    ) {
        // The lockup fades up and settles from slightly smaller.
        val shown = remember { androidx.compose.animation.core.Animatable(0f) }
        LaunchedEffect(Unit) { shown.animateTo(1f, tween(420, easing = JMotion.ease)) }
        Column(Modifier.graphicsLayer {
            alpha = shown.value
            val k = 0.94f + 0.06f * shown.value
            scaleX = k; scaleY = k
            translationY = (1f - shown.value) * 12.dp.toPx()
        }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(icon * 0.32f)) {
                MarginMark(icon)
                Text(
                    "Margin",
                    style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = word, lineHeight = word, letterSpacing = (-0.045).em),
                    color = jc.ink,
                )
            }
            Text(
                "Write first. Organise later.", Modifier.padding(top = icon * 0.36f),
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
        Text("Write first. Organise later.", Modifier.padding(bottom = 48.dp), style = JType.body.copy(fontSize = 20.sp, lineHeight = 28.sp), color = jc.muted)
        Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
            listOf(
                Triple("M5 3h10l4 4v14H5zM9 12h6M9 16h4", "Opens to a blank page", "Tap Start writing and type. The first line becomes the title."),
                Triple("M4 12a8 8 0 0 1 14-5l2-2v6h-6l2-2a5 5 0 1 0 1 6", "Syncs quietly", "Every change is saved and backs up to your Google Drive when you turn it on."),
                Triple("M6 11V8a6 6 0 0 1 12 0v3M5 11h14v10H5z", "Private by default", "Lock any note with your passcode. Nothing is shared unless you share it."),
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
        PrimaryButton("Start writing", onStart, Modifier.fillMaxWidth())
        Box(
            Modifier.fillMaxWidth().padding(top = 8.dp).height(44.dp).clickable(role = Role.Button, onClick = onBackup),
            contentAlignment = Alignment.Center,
        ) { Text("Turn on backup", style = JType.secondary.copy(fontWeight = FontWeight.Medium), color = jc.accent) }
    }
}
