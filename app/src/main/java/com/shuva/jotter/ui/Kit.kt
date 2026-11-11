package com.shuva.jotter.ui

import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Line icons on a 24-unit grid, drawn with round caps. */
object Ico {
    private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

    const val BACK = "M15 5l-7 7 7 7"
    const val CHEVRON = "M9 5l7 7-7 7"
    val SEARCH = circle(11f, 11f, 7f) + "M20 20l-3.5-3.5"
    val MORE = circle(5f, 12f, 1.6f) + circle(12f, 12f, 1.6f) + circle(19f, 12f, 1.6f)
    const val COMPOSE = "M20 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h6M17.5 3.5a2.1 2.1 0 0 1 3 3L12 15l-4 1 1-4z"
    const val FOLDER = "M3 6h7l2 2h9v11H3z"
    const val NEW_FOLDER = "M3 6h7l2 2h9v11H3zM12 11v5M9.5 13.5h5"
    const val SMART_FOLDER = "M3 6h7l2 2h9v11H3zM9 13l2 2 4-4"
    const val MOVE = "M3 6h7l2 2h9v11H3zM10 13h6M13 10l3 3-3 3"
    const val TRASH = "M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"
    const val PIN = "M12 17v5M8 3h8l-1 6 3 4H6l3-4z"
    const val SHARE = "M12 15V3M8 7l4-4 4 4M6 11H5v10h14V11h-1"
    const val LINK = "M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1"
    const val ATTACH = "M20 11.5l-8.2 8.2a5 5 0 0 1-7.1-7.1l8.5-8.5a3.3 3.3 0 0 1 4.7 4.7l-8.5 8.5a1.7 1.7 0 0 1-2.4-2.4l7.8-7.8"
    const val PEN = "M3 21c3 0 4-2 5-4M14 4l6 6-9 9-4 1 1-4z"
    const val ERASER = "M9 20h11M5 15l9-9 5 5-8 8H8zM9.5 10.5l5 5"
    const val REDO = "M15 14l5-5-5-5M20 9H9.5a5.5 5.5 0 0 0 0 11H13"
    const val UNDO = "M9 14L4 9l5-5M4 9h10.5a5.5 5.5 0 0 1 0 11H11"
    const val MIC = "M9 3h6v11H9zM5 11a7 7 0 0 0 14 0M12 18v3"
    const val FILE = "M6 3h8l4 4v14H6zM14 3v4h4"
    const val COPY = "M8 8h12v12H8zM4 16V4h12"
    val INFO = circle(12f, 12f, 9f) + "M12 11v6M12 7.5v.5"
    const val HANDWRITING = "M3 17l4-11 4 11M4.5 13h5M13 16c1.5 0 2-5 3.5-5s1 5 2.5 5 1.5-2 2-2M3 21h18"
    const val SCAN = "M4 8V5a1 1 0 0 1 1-1h3M16 4h3a1 1 0 0 1 1 1v3M20 16v3a1 1 0 0 1-1 1h-3M8 20H5a1 1 0 0 1-1-1v-3M8 10h8M8 14h5"
    const val BELL = "M6 16V10a6 6 0 0 1 12 0v6l1.5 2h-15zM10 21h4"
    const val PLAY = "M8 5v14l11-7z"
    const val PAUSE = "M7 5h3.5v14H7zM13.5 5H17v14h-3.5z"
    const val CHECKLIST = "M4 6l1.5 1.5L8 5M4 12l1.5 1.5L8 11M4 18l1.5 1.5L8 17M12 6h8M12 12h8M12 18h8"
    const val LOCK = "M7 11V8a5 5 0 0 1 10 0v3M5 11h14v10H5zM12 15v2"
    const val CHECK = "M5 12l5 5 9-10"
    const val CLOSE = "M6 6l12 12M18 6L6 18"
    const val PLUS = "M12 5v14M5 12h14"
    const val EMPTY_NOTE = "M5 3h10l4 4v14H5zM15 3v4h4M9 12h6M9 16h4"
    val BULLETS = circle(5f, 6f, 1f) + circle(5f, 12f, 1f) + circle(5f, 18f, 1f) + "M10 6h10M10 12h10M10 18h10"
    const val DASHES = "M3 6h3M3 12h3M3 18h3M10 6h10M10 12h10M10 18h10"
    const val NUMBERS = "M4 5h1v4M4 9h2M4 14h2l-2 3h2M10 6h10M10 12h10M10 18h10"
    const val RULER = "M3 16L16 3l5 5L8 21zM7 12l2 2M10 9l2 2M13 6l2 2"
    const val GRID = "M4 4h16v16H4zM4 9.3h16M4 14.7h16M9.3 4v16M14.7 4v16"
    const val CLOUD = "M7 18h10a4 4 0 0 0 .5-8A6 6 0 0 0 6 9.5 4.3 4.3 0 0 0 7 18z"
    const val CLOUD_OFF = "M7 18h10a4 4 0 0 0 .5-8A6 6 0 0 0 6 9.5 4.3 4.3 0 0 0 7 18zM4 4l16 16"
    const val MARKDOWN = "M3 6h18v12H3zM6 15V9l3 3 3-3v6M17 9v6M15 13l2 2 2-2"
    const val TEXT = "M5 6h14M12 6v13M9 19h6"
    const val PRINT = "M7 9V4h10v5M7 17H4V9h16v8h-3M7 14h10v6H7z"
    const val CAMERA = "M3 8h4l2-3h6l2 3h4v11H3zM12 16.5a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7"
    val GLOBE = circle(12f, 12f, 9f) + "M3 12h18M12 3a14 14 0 0 1 0 18M12 3a14 14 0 0 0 0 18"
    const val ROTATE = "M20 12a8 8 0 1 1-2.3-5.7M20 3v5h-5"
    const val QUOTE = "M5 5v14M10 8h10M10 12h10M10 16h6"
    const val INDENT = "M4 5h16M11 10h9M11 14h9M4 19h16M4 9l3 3-3 3"
    const val OUTDENT = "M4 5h16M11 10h9M11 14h9M4 19h16M7 9l-3 3 3 3"
    const val HIDE_KEYBOARD = "M4 4h16a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2zM6 8h1M10 8h1M14 8h1M18 8h0M7 11h10M9 19l3 2 3-2"
}

@Composable
fun JIcon(d: String, color: Color, modifier: Modifier = Modifier, size: Dp = 22.dp, stroke: Float = 1.8f, fill: Boolean = false) {
    val path = remember(d) { PathParser().parsePathString(d).toPath() }
    Canvas(modifier.size(size)) {
        scale(this.size.width / 24f, pivot = Offset.Zero) {
            if (fill) drawPath(path, color)
            else drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** A 44dp tap target holding an icon. */
@Composable
fun IconAction(d: String, label: String, onClick: () -> Unit, color: Color = jc.accent, enabled: Boolean = true, size: Dp = 22.dp, fill: Boolean = false) {
    Box(
        Modifier.size(44.dp).clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { JIcon(d, if (enabled) color else jc.muted.copy(alpha = 0.4f), size = size, fill = fill) }
}

/** Plain text button in the accent colour (or [color]). */
@Composable
fun TextAction(text: String, onClick: () -> Unit, color: Color = jc.accent, bold: Boolean = false, enabled: Boolean = true) {
    Box(
        Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, style = JType.row, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            color = if (enabled) color else jc.muted.copy(alpha = 0.5f), maxLines = 1,
        )
    }
}

/** Top row of a page: "‹ Back label" on the left, actions on the right. Sits under the status bar. */
@Composable
fun NavBar(backLabel: String?, onBack: () -> Unit, color: Color = jc.bg, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().background(color).statusBarsPadding().height(52.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (backLabel != null) {
            Row(
                Modifier.heightIn(min = 44.dp).widthIn(max = 220.dp).clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button, onClick = onBack).padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                JIcon(Ico.BACK, jc.accent, stroke = 2.2f)
                Text(backLabel, style = JType.row, color = jc.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.weight(1f))
        actions()
    }
}

@Composable
fun PageTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp), style = JType.pageTitle, color = jc.ink)
}

/** Uppercase grey label above a group of rows. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 4.dp), style = JType.section, color = jc.muted)
}

/** The rounded grey search field. */
@Composable
fun SearchField(query: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, autoFocus: Boolean = false) {
    val focus = remember { FocusRequester() }
    if (autoFocus) LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(12.dp)).background(jc.surface).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        JIcon(Ico.SEARCH, jc.muted, size = 17.dp, stroke = 2f)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text(placeholder, style = JType.row, color = jc.muted, maxLines = 1)
            BasicTextField(
                query, onChange, singleLine = true, textStyle = JType.row.copy(color = jc.ink),
                cursorBrush = SolidColor(jc.accent), modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = placeholder },
            )
        }
        if (query.isNotEmpty()) {
            Box(
                Modifier.size(20.dp).clip(CircleShape).background(jc.muted).clickable(role = Role.Button) { onChange("") }
                    .semantics { contentDescription = "Clear search" },
                contentAlignment = Alignment.Center,
            ) { JIcon(Ico.CLOSE, jc.bg, size = 10.dp, stroke = 3.5f) }
        }
    }
}

/** Bar along the bottom of a page, above the navigation bar, with a hairline on top. */
@Composable
fun BottomBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Column(modifier.background(jc.bg)) {
        HorizontalDivider(color = jc.line)
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(56.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            content = content,
        )
    }
}

/** The big solid button (ink on page colour). */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier.height(52.dp).clip(RoundedCornerShape(14.dp)).background(if (enabled) jc.ink else jc.switchOff)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = JType.rowTitle, color = if (enabled) jc.bg else jc.muted) }
}

/**
 * The Margin mark: an amber margin rule beside three lines on a rounded square (the app icon, from a 1024 master),
 * with a hairline edge on dark pages so it stands off them.
 */
@Composable
fun MarginMark(size: Dp, modifier: Modifier = Modifier) {
    // The ink icon on both themes (same as the launcher icon); on a dark page a hairline edge keeps it off the page.
    val dark = jc.bg.luminance() < 0.5f
    val ground = Color(0xFF111111)
    val rule = Color(0xFFFFB340)
    val ink = Color.White
    Canvas(modifier.size(size)) {
        val k = this.size.width / 1024f
        drawRoundRect(ground, cornerRadius = CornerRadius(230 * k))
        if (dark) drawRoundRect(Color(0xFF2A2A2E), Offset(4 * k, 4 * k), Size(1016 * k, 1016 * k), CornerRadius(228 * k), style = Stroke(8 * k))
        val w = 56 * k
        drawLine(rule, Offset(302 * k, 232 * k), Offset(302 * k, 792 * k), w, StrokeCap.Round)
        listOf(360f to 722f, 512f to 662f, 664f to 562f).forEach { (y, x2) ->
            drawLine(ink, Offset(402 * k, y * k), Offset(x2 * k, y * k), w, StrokeCap.Round)
        }
    }
}

/** One choice in a row of pills; the selected one is filled. */
@Composable
fun Pill(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.height(32.dp).clip(RoundedCornerShape(16.dp))
            .background(if (selected) jc.ink else Color.Transparent)
            .border(1.dp, if (selected) jc.ink else jc.line, RoundedCornerShape(16.dp))
            .clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, style = JType.small.copy(fontSize = 14.sp), color = if (selected) jc.bg else jc.ink,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1,
        )
    }
}

/** A rounded tag-like pill. */
@Composable
fun Chip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, trailing: String? = null, onLongClick: (() -> Unit)? = null) {
    Row(
        modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).border(1.dp, jc.line, RoundedCornerShape(18.dp))
            .combinedClickableCompat(onClick, onLongClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text, style = JType.secondary, color = jc.ink, maxLines = 1)
        trailing?.let { Text(it, style = JType.small, color = jc.muted) }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit, onLongClick: (() -> Unit)?) =
    if (onLongClick == null) clickable(onClick = onClick)
    else combinedClickable(onClick = onClick, onLongClick = onLongClick)

/** Round checkbox: filled accent with a tick when checked, an empty ring when not. */
@Composable
fun CheckCircle(checked: Boolean, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    if (checked) {
        Box(modifier.size(size).clip(CircleShape).background(jc.accent), contentAlignment = Alignment.Center) {
            JIcon(Ico.CHECK, jc.bg, size = size * 0.6f, stroke = 3.2f)
        }
    } else {
        Box(modifier.size(size).border(1.5.dp, jc.muted, CircleShape))
    }
}

@Composable
fun JSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Switch(
        checked = checked, onCheckedChange = onCheckedChange, enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedTrackColor = jc.ink, checkedThumbColor = jc.bg, checkedBorderColor = jc.ink,
            uncheckedTrackColor = jc.switchOff, uncheckedThumbColor = Color.White, uncheckedBorderColor = jc.switchOff,
        ),
    )
}

/** A rounded group on a grouped page (Settings). Rows inside are separated by inset hairlines. */
@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(jc.card), content = content)
}

@Composable
fun CardDivider() = HorizontalDivider(Modifier.padding(start = 16.dp), color = jc.line)

/** One row in a [Card]: a label (with optional grey line under it) and something on the right. */
@Composable
fun CardRow(label: String, sub: String? = null, onClick: (() -> Unit)? = null, labelColor: Color = jc.ink, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = JType.row.copy(fontSize = 16.sp), color = labelColor)
            sub?.let { Text(it, style = JType.small, color = jc.muted) }
        }
        trailing()
    }
}

/**
 * Bottom sheet for choosing a folder: [rows] indented as in the folder tree, the [currentId] folder marked
 * "Current" and not tappable. With [topLevel], "Top level" (null) comes first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderSheet(
    title: String,
    subject: String?,
    subjectNote: String?,
    rows: List<FolderRow>,
    currentId: String?,
    topLevel: Boolean = false,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = jc.bg, shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Box(Modifier.fillMaxWidth().height(52.dp)) {
                Box(Modifier.align(Alignment.CenterStart)) { TextAction("Cancel", onDismiss) }
                Text(title, Modifier.align(Alignment.Center).padding(horizontal = 80.dp), style = JType.rowTitle, color = jc.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
            if (subject != null) {
                Column(
                    Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp).clip(RoundedCornerShape(12.dp)).background(jc.surface).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(subject, style = JType.rowTitle, color = jc.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    subjectNote?.let { Text(it, style = JType.small, color = jc.muted) }
                }
            }
            Text("Choose a folder", Modifier.padding(top = 12.dp, bottom = 4.dp), style = JType.section, color = jc.muted)
            Column(Modifier.verticalScroll(rememberScrollState())) {
                @Composable
                fun row(name: String, depth: Int, note: String?, icon: String, onClick: (() -> Unit)?) {
                    Row(
                        Modifier.fillMaxWidth().height(52.dp).let { if (onClick != null) it.clickable(onClick = onClick) else it }
                            .padding(start = 20.dp * depth),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        JIcon(icon, if (onClick != null) jc.accent else jc.muted, size = 20.dp)
                        Text(name, Modifier.weight(1f), style = JType.row, color = if (onClick != null) jc.ink else jc.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        note?.let { Text(it, style = JType.small, color = jc.muted) }
                    }
                    HorizontalDivider(Modifier.padding(start = 20.dp * depth), color = jc.line)
                }
                if (topLevel) row("Top level", 0, null, Ico.FOLDER) { onPick(null) }
                rows.forEach { r ->
                    val current = r.folder.id == currentId
                    row(r.folder.name, r.depth, if (current) "Current" else null, Ico.FOLDER, if (current) null else ({ onPick(r.folder.id) }))
                }
            }
            Spacer(Modifier.width(1.dp).navigationBarsPadding())
        }
    }
}

/** Small "Aa"-style text in a 44dp target. */
@Composable
fun GlyphAction(text: String, label: String, onClick: () -> Unit, style: TextStyle, color: Color = jc.accent, enabled: Boolean = true) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Text(text, style = style, color = if (enabled) color else jc.muted.copy(alpha = 0.4f)) }
}
