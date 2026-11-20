package com.margin.app.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/*
 * Frosted glass (Settings > Appearance): a screen's bars blur what scrolls under them. Off by default, and only
 * offered on Android 12+, where blur is drawn by the system; older phones keep solid bars.
 *
 * A screen opts in: it calls [rememberGlass], marks its scrolling content with [glassSource], hands the state to
 * NavBar / BottomBar, and lets its list scroll under the bars (Scaffold's padding as contentPadding, see [plus]).
 * With glass off, rememberGlass gives null and every piece falls back to today's solid look.
 */

/** A pane of glass with a glint: the Frosted glass setting. */
const val ICON_GLASS = "M4 8a4 4 0 0 1 4-4h8a4 4 0 0 1 4 4v8a4 4 0 0 1-4 4H8a4 4 0 0 1-4-4zM8.5 13.5l5-5M11 16l5-5"

/** Frosted glass is on and this phone can draw it. Set by MarginTheme. */
val LocalGlass = staticCompositionLocalOf { false }

/** Dark theme is showing. Set by MarginTheme. */
val LocalDark = staticCompositionLocalOf { false }

/** Android 12+ blurs with RenderEffect; below that there's no real blur to offer. */
fun glassSupported() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** This screen's glass when frosted glass is on, else null. */
@Composable
fun rememberGlass(): HazeState? {
    val state = remember { HazeState() }
    return if (LocalGlass.current) state else null
}

/**
 * Marks what the screen's glass bars blur. Must not contain the bars themselves. It paints the colour wash too
 * (lined up with the one behind the whole app), so a bar over empty page frosts the wash instead of plain black.
 */
@Composable
fun Modifier.glassSource(glass: HazeState?): Modifier = if (glass == null) this else hazeSource(glass).backdrop(Color.Unspecified)

/** A bar's background: solid [color], or [color] tinted over a blur of what's under it. */
@Composable
fun Modifier.glassBackground(glass: HazeState?, color: Color): Modifier {
    if (glass == null) return background(color)
    // Enough tint that text on the bar stays readable over busy content, little enough to see it's glass.
    // A clear page colour tints with the solid one under it.
    val tint = (if (color.alpha < 1f) jc.well else color).copy(alpha = if (LocalDark.current) 0.35f else 0.45f)
    // A hairline along the bottom marks where the glass ends and the page begins.
    val edge = jc.line
    return hazeEffect(glass, HazeStyle(backgroundColor = color, tint = HazeTint(tint), blurRadius = 20.dp, noiseFactor = 0.08f))
        .drawWithContent {
            drawContent()
            drawLine(edge, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), strokeWidth = 1f)
        }
}

/** These paddings and [other] added, side by side: Scaffold's bar padding plus a list's own. */
@Composable
operator fun PaddingValues.plus(other: PaddingValues): PaddingValues {
    val dir = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(dir) + other.calculateStartPadding(dir),
        top = calculateTopPadding() + other.calculateTopPadding(),
        end = calculateEndPadding(dir) + other.calculateEndPadding(dir),
        bottom = calculateBottomPadding() + other.calculateBottomPadding(),
    )
}

/** A scrolling screen body: inside the bars as before, or with glass under them as the blur's source. */
@Composable
fun Modifier.glassBody(glass: HazeState?, padding: PaddingValues): Modifier = if (glass == null) padding(padding) else glassSource(glass)

/** Inside a scrolling Column's scroll: with glass, the room for the bars that [glassBody] left out. */
fun Modifier.glassInner(glass: HazeState?, padding: PaddingValues): Modifier = if (glass == null) this else padding(padding)

/** A lazy list's content padding: its own, plus room for the bars when it scrolls under them. */
@Composable
fun glassPadding(glass: HazeState?, padding: PaddingValues, own: PaddingValues = PaddingValues(0.dp)): PaddingValues =
    if (glass == null) own else padding + own

/**
 * A screen's background: [solid], or with frosted glass a soft wash of colour (the accent's amber, a violet and
 * a teal glow) for the clear pages and frosted cards to sit on.
 */
@Composable
fun Modifier.backdrop(solid: Color): Modifier {
    if (!LocalGlass.current) return background(solid)
    val dark = LocalDark.current
    val base = if (dark) Color(0xFF050506) else Color(0xFFEFEEF3)
    val glows = if (dark) listOf(
        Triple(Color(0xFFFFB340).copy(alpha = 0.22f), Offset(0.05f, 0.04f), 0.85f),
        Triple(Color(0xFF6E5BFF).copy(alpha = 0.21f), Offset(1.0f, 0.42f), 0.9f),
        Triple(Color(0xFF2EC4B6).copy(alpha = 0.15f), Offset(0.15f, 0.95f), 0.85f),
    ) else listOf(
        Triple(Color(0xFFFFB340).copy(alpha = 0.30f), Offset(0.05f, 0.04f), 0.85f),
        Triple(Color(0xFF8C7BFF).copy(alpha = 0.24f), Offset(1.0f, 0.42f), 0.9f),
        Triple(Color(0xFF4FD1C5).copy(alpha = 0.20f), Offset(0.15f, 0.95f), 0.85f),
    )
    // Laid out against the whole window, so a wash painted by any part of the screen lines up with every other.
    val window = LocalWindowInfo.current.containerSize
    val origin = remember { mutableStateOf(Offset.Zero) }
    return onGloballyPositioned { origin.value = it.positionInWindow() }.drawBehind {
        val w = if (window.width > 0) window.width.toFloat() else size.width
        val h = if (window.height > 0) window.height.toFloat() else size.height
        drawRect(base)
        translate(-origin.value.x, -origin.value.y) {
            glows.forEach { (c, at, r) ->
                val center = Offset(w * at.x, h * at.y)
                drawRect(Brush.radialGradient(listOf(c, c.copy(alpha = 0f)), center, w * r), Offset.Zero, Size(w, h))
            }
        }
        drawRect(GRAIN, alpha = 0.02f)
    }
}

/**
 * A card's fill, already clipped to [shape]: [color], and with frosted glass a bright rim where light catches the
 * pane, strongest along the top. A card made of several rows (a group) draws it per row: [openTop] and
 * [openBottom] leave out the rim where the next row carries on.
 */
@Composable
fun Modifier.glassFill(color: Color, shape: Shape, openTop: Boolean = false, openBottom: Boolean = false): Modifier {
    if (!LocalGlass.current) return background(color)
    val dark = LocalDark.current
    val rim = Brush.verticalGradient(
        if (dark) listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.05f))
        else listOf(Color.White, Color.White.copy(alpha = 0.45f)),
    )
    val grain = if (dark) 0.04f else 0.045f
    return background(color).drawWithContent {
        // Frosting: a fine grain over the fill, the way etched glass scatters light.
        drawRect(GRAIN, alpha = grain)
        drawContent()
        // Grow the outline past an open edge so its rim falls outside this row, then cut it off.
        val reach = size.minDimension
        val top = if (openTop) -reach else 0f
        val bottom = if (openBottom) size.height + reach else size.height
        val outline = shape.createOutline(Size(size.width, bottom - top), layoutDirection, this)
        clipRect {
            drawContext.transform.translate(0f, top)
            drawOutline(outline, rim, style = Stroke(width = 2.dp.toPx()))
            drawContext.transform.translate(0f, -top)
        }
    }
}

/** A tile of fine light and dark specks, repeated over a card as its frosting. Made once. */
private val GRAIN: ShaderBrush by lazy {
    // Specks two pixels across, so the grain reads on a sharp screen instead of melting into a flat grey.
    val n = 128
    val rnd = java.util.Random(7)
    val cells = IntArray(n * n) {
        val v = if (rnd.nextBoolean()) 255 else 0
        android.graphics.Color.argb(40 + rnd.nextInt(170), v, v, v)
    }
    val px = IntArray(4 * n * n) { i -> val x = (i % (2 * n)) / 2; val y = (i / (2 * n)) / 2; cells[y * n + x] }
    val bmp = android.graphics.Bitmap.createBitmap(px, 2 * n, 2 * n, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
    ShaderBrush(ImageShader(bmp, TileMode.Repeated, TileMode.Repeated))
}

/** A popup menu's fill: menus open in their own window, where nothing can be blurred, so with frosted glass it is a solid, lit pane. */
@Composable
fun menuColor(): Color = if (!LocalGlass.current) jc.bg
    else if (LocalDark.current) Color(0xFF232328) else Color(0xFFF8F8FB)

/** A popup menu's edge: a hairline, or with frosted glass the same lit rim as the cards. */
@Composable
fun menuBorder(): BorderStroke = if (!LocalGlass.current) BorderStroke(1.dp, jc.line)
    else if (LocalDark.current) BorderStroke(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.06f))))
    else BorderStroke(1.dp, Brush.verticalGradient(listOf(Color.White, Color.Black.copy(alpha = 0.08f))))

/** Where a screen's frosted menus draw: on the screen itself, so they can blur it (a popup's own window can't). */
private class MenuHostState(val glass: HazeState) {
    var origin by mutableStateOf(Offset.Zero)
    var menu by mutableStateOf<(@Composable () -> Unit)?>(null)
}

private val LocalMenuHost = staticCompositionLocalOf<MenuHostState?> { null }

/** Lets [GlassMenu]s inside [content] open as frosted panes over it. Without glass, menus stay ordinary popups. */
@Composable
fun MenuHost(glass: HazeState?, content: @Composable () -> Unit) {
    if (glass == null) return content()
    val host = remember(glass) { MenuHostState(glass) }
    Box(Modifier.fillMaxSize().onGloballyPositioned { host.origin = it.positionInWindow() }) {
        CompositionLocalProvider(LocalMenuHost provides host) { content() }
        host.menu?.invoke()
    }
}

/**
 * A drop-down menu for the button it sits beside (in the same Box). Inside a [MenuHost] with frosted glass it is a
 * pane of frosted glass over the screen, opening below the button, or above it when there's no room below;
 * otherwise an ordinary [DropdownMenu].
 */
@Composable
fun GlassMenu(
    expanded: Boolean, onDismissRequest: () -> Unit, width: Dp = 262.dp,
    /** False leaves the keyboard up while the popup menu is open (the frosted one never takes focus). */
    focusable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val host = LocalMenuHost.current
    if (host == null) {
        DropdownMenu(
            expanded = expanded, onDismissRequest = onDismissRequest,
            modifier = Modifier.width(width), shape = JShape.md, containerColor = menuColor(),
            border = menuBorder(), shadowElevation = 12.dp,
            properties = androidx.compose.ui.window.PopupProperties(focusable = focusable), content = content,
        )
        return
    }
    var anchor by remember { mutableStateOf(Rect.Zero) }
    Spacer(Modifier.onGloballyPositioned { c -> c.parentLayoutCoordinates?.let { anchor = it.boundsInWindow() } })
    val visible = remember { MutableTransitionState(false) }
    visible.targetState = expanded
    val shown = visible.currentState || visible.targetState
    val dark = LocalDark.current
    val menu: @Composable () -> Unit = {
        BackHandler(expanded) { onDismissRequest() }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // A tap anywhere off the menu closes it, and goes no further.
            if (expanded) Box(
                Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onDismissRequest() } }
                    .semantics { contentDescription = "Close menu" },
            )
            val density = LocalDensity.current
            val gap = with(density) { 4.dp.toPx() }
            val margin = with(density) { 8.dp.toPx() }
            val top = anchor.bottom - host.origin.y + gap
            val bottomRoom = constraints.maxHeight - top - margin
            val topRoom = anchor.top - host.origin.y - gap - margin - with(density) { WindowInsets.statusBars.getTop(this).toFloat() }
            val below = bottomRoom >= topRoom
            val right = (anchor.right - host.origin.x).coerceAtMost(constraints.maxWidth - margin)
            val maxH = with(density) { (if (below) bottomRoom else topRoom).coerceAtLeast(0f).toDp() }
            AnimatedVisibility(
                visible, Modifier.layout { m, c ->
                    val p = m.measure(c.copy(minWidth = 0, minHeight = 0))
                    layout(c.maxWidth, c.maxHeight) {
                        val x = (right - p.width).toInt().coerceAtLeast(margin.toInt())
                        val y = if (below) top.toInt() else (anchor.top - host.origin.y - gap - p.height).toInt()
                        p.place(x, y)
                    }
                },
                enter = fadeIn(tween(120)) + scaleIn(tween(160), initialScale = 0.92f, transformOrigin = TransformOrigin(1f, if (below) 0f else 1f)),
                exit = fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 0.96f, transformOrigin = TransformOrigin(1f, if (below) 0f else 1f)),
            ) {
                Column(
                    Modifier.width(width).heightIn(max = maxH).shadow(16.dp, JShape.md).clip(JShape.md)
                        .hazeEffect(
                            host.glass,
                            HazeStyle(
                                backgroundColor = jc.well,
                                tint = HazeTint(if (dark) Color(0xFF1E1E23).copy(alpha = 0.55f) else Color.White.copy(alpha = 0.6f)),
                                blurRadius = 28.dp, noiseFactor = 0.08f,
                            ),
                        )
                        .glassFill(Color.Transparent, JShape.md)
                        .verticalScroll(rememberScrollState()),
                    content = content,
                )
            }
        }
    }
    // The menu draws in the host, above the whole screen; this keeps it current while it's open or closing.
    if (shown) SideEffect { host.menu = menu }
    DisposableEffect(shown) { onDispose { if (host.menu != null && shown) host.menu = null } }
}
