package com.shuva.jotter.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import com.shuva.jotter.R
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.shuva.jotter.data.Accent
import com.shuva.jotter.data.Appearance
import com.shuva.jotter.data.ThemeMode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Jotter's palette: plain white (or near-black) pages, one warm accent, grey for everything secondary. */
@Immutable
data class JotterColors(
    val bg: Color,
    val surface: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val accent: Color,
    val danger: Color,
    /** Background of grouped screens (Settings), with [card] for the groups on it. */
    val page: Color,
    val card: Color,
    /** Tint behind a selected choice. */
    val selected: Color,
    val switchOff: Color,
)

private val Light = JotterColors(
    bg = Color(0xFFFFFFFF), surface = Color(0xFFF2F2F4), ink = Color(0xFF111111), muted = Color(0xFF5C5C63),
    line = Color(0xFFE6E6EA), accent = Color(0xFFB25E00), danger = Color(0xFFC4291C),
    page = Color(0xFFF2F2F4), card = Color(0xFFFFFFFF), selected = Color(0xFFFBF1E6), switchOff = Color(0xFFD4D4D8),
)

private val Dark = JotterColors(
    bg = Color(0xFF0B0B0C), surface = Color(0xFF1C1C1E), ink = Color(0xFFF5F5F7), muted = Color(0xFFA1A1A8),
    line = Color(0xFF2A2A2E), accent = Color(0xFFFFB340), danger = Color(0xFFFF6B5E),
    page = Color(0xFF000000), card = Color(0xFF1C1C1E), selected = Color(0xFF2E2416), switchOff = Color(0xFF3A3A3E),
)

private val LocalJotterColors = staticCompositionLocalOf { Light }

/** The current palette. */
val jc: JotterColors @Composable get() = LocalJotterColors.current

/** Text sizes from the design: a large bold page title, 17sp rows and body, small grey labels. */
/** Inter, the design's typeface on Android (bundled, SIL Open Font License; see licenses/Inter-OFL.txt). */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

object JType {
    val pageTitle = TextStyle(fontFamily = Inter, fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em)
    val noteTitle = TextStyle(fontFamily = Inter, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em)
    val heading = TextStyle(fontFamily = Inter, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
    val subheading = TextStyle(fontFamily = Inter, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)
    val body = TextStyle(fontFamily = Inter, fontSize = 17.sp, lineHeight = 26.sp)
    val row = TextStyle(fontFamily = Inter, fontSize = 17.sp, lineHeight = 22.sp)
    val rowTitle = TextStyle(fontFamily = Inter, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    val secondary = TextStyle(fontFamily = Inter, fontSize = 15.sp, lineHeight = 20.sp)
    val small = TextStyle(fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp)
    val section = TextStyle(fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
}

/** Accent colour for light and dark pages. */
fun accentColors(accent: Accent): Pair<Color, Color> = when (accent) {
    Accent.AMBER -> Color(0xFFB25E00) to Color(0xFFFFB340)
    Accent.BLUE -> Color(0xFF0A60C2) to Color(0xFF6AAEFF)
    Accent.GREEN -> Color(0xFF1C7A3A) to Color(0xFF5FD37E)
    Accent.ROSE -> Color(0xFFC0245E) to Color(0xFFFF7EAA)
    Accent.VIOLET -> Color(0xFF6B40C4) to Color(0xFFB9A0FF)
    Accent.GRAPHITE -> Color(0xFF111111) to Color(0xFFF5F5F7)
}

@Composable
fun isDark(theme: ThemeMode) = when (theme) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** Light and dark follow the system setting unless [appearance] picks one; accent and text size come from it too. */
@Composable
fun JotterTheme(appearance: Appearance = Appearance(), content: @Composable () -> Unit) {
    val dark = isDark(appearance.theme)
    val base0 = if (dark) Dark else Light
    val accent = accentColors(appearance.accent).let { if (dark) it.second else it.first }
    // The amber tint is tuned by hand; other accents get the same 10-12% mix of accent over the page.
    val c = if (appearance.accent == Accent.AMBER) base0
    else base0.copy(accent = accent, selected = lerp(base0.bg, accent, if (dark) 0.16f else 0.10f))
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = c.accent, onPrimary = c.bg, secondary = c.accent,
        background = c.bg, onBackground = c.ink, surface = c.bg, onSurface = c.ink,
        surfaceVariant = c.surface, onSurfaceVariant = c.muted,
        surfaceContainerLowest = c.bg, surfaceContainerLow = c.bg, surfaceContainer = c.bg,
        surfaceContainerHigh = c.bg, surfaceContainerHighest = c.surface,
        secondaryContainer = c.selected, onSecondaryContainer = c.ink,
        outline = c.muted, outlineVariant = c.line, error = c.danger,
        inverseSurface = c.ink, inverseOnSurface = c.bg, inversePrimary = c.accent,
    )
    val t0 = Typography()
    // Every Material text style in Inter too, so dialogs, menus and fields match the rest.
    val t = t0.copy(
        displayLarge = t0.displayLarge.copy(fontFamily = Inter), displayMedium = t0.displayMedium.copy(fontFamily = Inter),
        displaySmall = t0.displaySmall.copy(fontFamily = Inter), headlineLarge = t0.headlineLarge.copy(fontFamily = Inter),
        headlineMedium = t0.headlineMedium.copy(fontFamily = Inter), headlineSmall = t0.headlineSmall.copy(fontFamily = Inter),
        titleLarge = t0.titleLarge.copy(fontFamily = Inter), titleMedium = t0.titleMedium.copy(fontFamily = Inter),
        titleSmall = t0.titleSmall.copy(fontFamily = Inter), bodyLarge = t0.bodyLarge.copy(fontFamily = Inter),
        bodyMedium = t0.bodyMedium.copy(fontFamily = Inter), bodySmall = t0.bodySmall.copy(fontFamily = Inter),
        labelLarge = t0.labelLarge.copy(fontFamily = Inter), labelMedium = t0.labelMedium.copy(fontFamily = Inter),
        labelSmall = t0.labelSmall.copy(fontFamily = Inter),
    )
    val typography = t.copy(
        bodyLarge = t.bodyLarge.merge(JType.body), bodyMedium = t.bodyMedium.merge(JType.secondary),
        bodySmall = t.bodySmall.merge(JType.small), titleLarge = t.titleLarge.merge(TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold)),
        labelLarge = t.labelLarge.merge(TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Normal)),
    )
    val density = LocalDensity.current
    // Text size scales every sp in the app on top of the system font size.
    val scaled = Density(density.density, density.fontScale * appearance.textSize.scale)
    CompositionLocalProvider(LocalJotterColors provides c, LocalDensity provides scaled) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
