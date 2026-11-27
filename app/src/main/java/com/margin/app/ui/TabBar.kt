package com.margin.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.VerticalDivider
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.margin.app.R
import androidx.compose.ui.res.stringResource

/** True while a screen is shown as a tab's root: it has no Back, and the tab bar does what its own bars did. */
val LocalTabRoot = staticCompositionLocalOf { false }

/** The screens the tab bar switches between, each a whole stack's root. */
enum class Tab(val label: String, @androidx.annotation.StringRes val labelRes: Int, val icon: String, val root: Screen) {
    NOTES("Notes", R.string.tab_notes, Ico.EMPTY_NOTE, Screen.Notes(ALL_ID)),
    TODAY("Today", R.string.tab_today, Ico.SUN, Screen.Todos),
    LIBRARY("Library", R.string.tab_library, Ico.GRID, Screen.Folders),
    SETTINGS("Settings", R.string.tab_settings, Ico.GEAR, Screen.Settings),
    ;

    companion object {
        fun of(screen: Screen): Tab? = entries.firstOrNull { it.root == screen }
    }
}

private val tabShape = RoundedCornerShape(18.dp)

/** 12 sp that grows with the user's text size only up to 1.3x: past that, five labels can't fit in 68 dp each. */
@Composable
private fun tabLabelSize() = (12f * minOf(androidx.compose.ui.platform.LocalDensity.current.fontScale, 1.3f) /
    androidx.compose.ui.platform.LocalDensity.current.fontScale).sp

/** Notes · Today · New · Library · Settings, along the bottom of every tab's root screen, or down the left as a rail when [vertical]. */
@Composable
fun TabBar(current: Tab, onSelect: (Tab) -> Unit, onNew: () -> Unit, modifier: Modifier = Modifier, vertical: Boolean = false) {
    val items: @Composable () -> Unit = {
        TabItem(Tab.NOTES, current, onSelect)
        TabItem(Tab.TODAY, current, onSelect)
        NewItem(onNew)
        TabItem(Tab.LIBRARY, current, onSelect)
        TabItem(Tab.SETTINGS, current, onSelect)
    }
    if (vertical) {
        // Landscape phones: a side rail keeps the full height for content.
        Row(modifier.fillMaxHeight().background(jc.bg)) {
            Column(
                Modifier.fillMaxHeight().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical))
                    .width(84.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            ) { items() }
            VerticalDivider(color = jc.line)
        }
        return
    }
    Column(modifier.fillMaxWidth().background(jc.bg)) {
        HorizontalDivider(color = jc.line)
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
        ) { items() }
    }
}

/** New note: a tab like the rest, its icon on a small accent pill so it still reads as the main action. */
@Composable
private fun NewItem(onNew: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val press = remember { MutableInteractionSource() }
    Column(
        Modifier.width(68.dp).heightIn(min = 48.dp).clip(JShape.sm).pressScale(press, 0.92f)
            .clickable(press, androidx.compose.material3.ripple(), role = Role.Button, onClick = tapped(onNew))
            .semantics(mergeDescendants = true) { contentDescription = context.getString(R.string.tab_bar_new_note) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(width = 56.dp, height = 30.dp).clip(JShape.pill).background(jc.accent), contentAlignment = Alignment.Center) {
            JIcon(Ico.PLUS, jc.bg, size = 18.dp, stroke = 2.4f)
        }
        Text(stringResource(R.string.tab_bar_new), style = JType.caption.copy(fontSize = tabLabelSize()), color = jc.muted, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun TabItem(tab: Tab, current: Tab, onSelect: (Tab) -> Unit) {
    val on = tab == current
    val color = if (on) jc.accent else jc.muted
    Column(
        Modifier.width(68.dp).heightIn(min = 48.dp).clip(JShape.sm)
            .clickable(role = Role.Tab, onClick = { onSelect(tab) })
            .semantics { selected = on },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
    ) {
        // The current tab sits on a soft pill, the same size as New's, so it reads at a glance and not by colour alone.
        val pill by androidx.compose.animation.animateColorAsState(if (on) jc.selected else jc.bg, JMotion.standard(), label = "tab")
        Box(Modifier.size(width = 56.dp, height = 30.dp).clip(JShape.pill).background(pill), contentAlignment = Alignment.Center) {
            JIcon(tab.icon, color, size = 22.dp, stroke = if (on) 2.1f else 1.8f)
        }
        Text(stringResource(tab.labelRes), style = JType.caption.copy(fontSize = tabLabelSize()), color = color, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
    }
}
