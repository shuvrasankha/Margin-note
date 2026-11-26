package com.margin.app.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.margin.app.R
import androidx.compose.ui.res.stringResource

/**
 * One key: what it shows, what it types (spaces around an operator are dropped where there already is one),
 * how many characters the cursor steps back after typing ("sin()" leaves it between the brackets), what it's
 * read out as, and whether it stands out (operators) or holds on press (delete, arrows).
 */
private class MathKey(
    val label: String,
    val types: String = label,
    val back: Int = 0,
    val spoken: String = label,
    @androidx.annotation.StringRes val spokenRes: Int = 0,
    val strong: Boolean = false,
    val action: KeyAction? = null,
    val weight: Float = 1f,
)

private enum class KeyAction { DELETE, ENTER, LEFT, RIGHT, LETTERS, PAGE }

private fun op(label: String, types: String, @androidx.annotation.StringRes spoken: Int) = MathKey(label, types, spokenRes = spoken, strong = true)
private fun fn(name: String, @androidx.annotation.StringRes spoken: Int = 0) = MathKey(name, "$name()", back = 1, spokenRes = spoken)

private val DELETE = MathKey("⌫", spokenRes = R.string.math_delete, action = KeyAction.DELETE)

private val NUMBERS = listOf(
    listOf(MathKey("7"), MathKey("8"), MathKey("9"), op("÷", " ÷ ", R.string.math_divide), MathKey("("), MathKey(")")),
    listOf(MathKey("4"), MathKey("5"), MathKey("6"), op("×", " × ", R.string.math_times), MathKey("x"), MathKey("^", spokenRes = R.string.math_power)),
    listOf(MathKey("1"), MathKey("2"), MathKey("3"), op("−", " - ", R.string.math_minus), MathKey("y"), MathKey("√", spokenRes = R.string.math_square_root)),
    listOf(MathKey("0"), MathKey("."), MathKey("π", spokenRes = R.string.math_pi), op("+", " + ", R.string.math_plus), op("=", " = ", R.string.math_equals), DELETE),
)

private val FUNCTIONS = listOf(
    listOf(fn("sin", R.string.math_sine), fn("cos", R.string.math_cosine), fn("tan", R.string.math_tangent), fn("ln", R.string.math_natural_log), fn("log", R.string.math_log), MathKey("e")),
    listOf(MathKey("²", spokenRes = R.string.math_squared), MathKey("!", spokenRes = R.string.math_factorial), MathKey("°", spokenRes = R.string.math_degrees), MathKey("%", spokenRes = R.string.math_percent), MathKey("|", spokenRes = R.string.math_absolute_value_bar), MathKey("at", " at ")),
    listOf(op("<", " < ", R.string.math_less_than), op(">", " > ", R.string.math_greater_than), op("≤", " ≤ ", R.string.math_at_most), op("≥", " ≥ ", R.string.math_at_least), MathKey(",", ", ", spokenRes = R.string.math_comma), MathKey("mod", " mod ")),
    listOf(MathKey("d/dx", "d/dx ", spokenRes = R.string.math_derivative), MathKey("∫", "∫ ", spokenRes = R.string.math_integral), MathKey("dx", " dx"), MathKey("from", " from "), MathKey("to", " to "), DELETE),
)

/** Greek letters, sub- and superscript digits and the arrows science notes need (H₂O, x², A → B). */
private val SYMBOLS = listOf(
    listOf(MathKey("α"), MathKey("β"), MathKey("γ"), MathKey("δ"), MathKey("θ"), MathKey("λ"), MathKey("μ"), MathKey("ω")),
    listOf(MathKey("Δ"), MathKey("Σ"), MathKey("Ω"), MathKey("ρ"), MathKey("σ"), MathKey("φ"), MathKey("ε"), MathKey("ν")),
    listOf(MathKey("₀"), MathKey("₁"), MathKey("₂"), MathKey("₃"), MathKey("₄"), MathKey("⁰"), MathKey("¹"), MathKey("²")),
    listOf(MathKey("₅"), MathKey("₆"), MathKey("₇"), MathKey("₈"), MathKey("₉"), MathKey("³"), MathKey("⁴"), MathKey("⁵")),
    listOf(MathKey("→", spokenRes = R.string.math_arrow), MathKey("⇌", spokenRes = R.string.math_equilibrium), MathKey("±"), MathKey("≈"), MathKey("∝"), MathKey("∞"), MathKey("·"), DELETE),
)

/**
 * A keypad for math in place of the phone's keyboard: numbers and operators on one page, functions,
 * comparisons and calculus words on the second, Greek letters and symbols on the third. It types into the note like any keyboard; "ABC" goes back to
 * the phone's keyboard.
 */
@Composable
fun MathKeyboard(value: TextFieldValue, onChange: (TextFieldValue) -> Unit, onLetters: () -> Unit) {
    var page by rememberSaveable { mutableStateOf(0) } // 0 numbers, 1 functions, 2 symbols
    val current by rememberUpdatedState(value)
    fun type(text: String, back: Int) {
        val v = current
        val t = v.text
        val start = v.selection.min
        val end = v.selection.max
        var insert = text
        if (insert.startsWith(' ') && (start == 0 || t[start - 1] == ' ' || t[start - 1] == '\n')) insert = insert.trimStart()
        if (insert.endsWith(' ') && t.getOrNull(end) == ' ') insert = insert.trimEnd()
        onChange(TextFieldValue(t.replaceRange(start, end, insert), TextRange(start + insert.length - back)))
    }
    fun press(key: MathKey) {
        val v = current
        val t = v.text
        val sel = v.selection
        when (key.action) {
            null -> type(key.types, key.back)
            KeyAction.ENTER -> type("\n", 0)
            KeyAction.DELETE -> when {
                !sel.collapsed -> type("", 0)
                sel.start > 0 -> {
                    val from = if (sel.start >= 2 && Character.isLowSurrogate(t[sel.start - 1])) sel.start - 2 else sel.start - 1
                    onChange(TextFieldValue(t.removeRange(from, sel.start), TextRange(from)))
                }
            }
            KeyAction.LEFT -> onChange(v.copy(selection = TextRange((sel.min - if (sel.collapsed) 1 else 0).coerceAtLeast(0))))
            KeyAction.RIGHT -> onChange(v.copy(selection = TextRange((sel.max + if (sel.collapsed) 1 else 0).coerceAtMost(t.length))))
            KeyAction.LETTERS -> onLetters()
            KeyAction.PAGE -> page = (page + 1) % 3
        }
    }
    val bottom = listOf(
        MathKey("ABC", spokenRes = R.string.math_letters_keyboard, action = KeyAction.LETTERS),
        MathKey(listOf("f(x)", "αβ", "123")[page], spokenRes = listOf(R.string.math_functions, R.string.math_symbols, R.string.math_numbers)[page], action = KeyAction.PAGE),
        MathKey("←", spokenRes = R.string.math_move_left, action = KeyAction.LEFT),
        MathKey("space", " ", spokenRes = R.string.math_space, weight = 2f),
        MathKey("→", spokenRes = R.string.math_move_right, action = KeyAction.RIGHT),
        MathKey("↵", spokenRes = R.string.math_new_line, action = KeyAction.ENTER, strong = true),
    )
    HorizontalDivider(color = jc.line)
    Column(
        Modifier.fillMaxWidth().background(jc.surface).padding(horizontal = 6.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf(NUMBERS, FUNCTIONS, SYMBOLS)[page].plusElement(bottom).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { key -> Key(key, Modifier.weight(key.weight)) { press(key) } }
            }
        }
    }
}

@Composable
private fun Key(key: MathKey, modifier: Modifier, onPress: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val press by rememberUpdatedState(onPress)
    var down by remember { mutableStateOf(false) }
    val repeats = key.action == KeyAction.DELETE || key.action == KeyAction.LEFT || key.action == KeyAction.RIGHT
    val face = when {
        down -> jc.line
        key.strong -> jc.accent.copy(alpha = 0.16f)
        key.action != null -> jc.surface
        else -> jc.bg
    }
    Box(
        modifier.height(46.dp).clip(JShape.xs).background(face)
            .semantics {
                role = Role.Button
                contentDescription = if (key.spokenRes != 0) context.getString(key.spokenRes) else key.spoken
                onClick { press(); true }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown().consume()
                    down = true
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    press()
                    // Delete and the arrows keep going while held.
                    val held = if (repeats) scope.launch { delay(400); while (true) { press(); delay(60) } } else null
                    waitForUpOrCancellation()
                    held?.cancel()
                    down = false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            key.label,
            style = JType.body.copy(fontSize = if (key.label.length > 3) 15.sp else 20.sp),
            color = if (key.strong) jc.accent else jc.ink,
        )
    }
}

/**
 * Keeps the phone's keyboard away from the text fields in [content] while [block] is true, so the math keypad
 * can stand in for it. A field that already has the phone's keyboard keeps it until its input restarts.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun BlockPhoneKeyboard(block: Boolean, content: @Composable () -> Unit) {
    val blocking by rememberUpdatedState(block)
    val interceptor = remember {
        PlatformTextInputInterceptor { request, next -> if (blocking) awaitCancellation() else next.startInputMethod(request) }
    }
    InterceptPlatformTextInput(interceptor, content)
}
