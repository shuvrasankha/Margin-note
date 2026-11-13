package com.shuva.jotter.ui

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
    val strong: Boolean = false,
    val action: KeyAction? = null,
    val weight: Float = 1f,
)

private enum class KeyAction { DELETE, ENTER, LEFT, RIGHT, LETTERS, PAGE }

private fun op(label: String, types: String, spoken: String) = MathKey(label, types, spoken = spoken, strong = true)
private fun fn(name: String, spoken: String = name) = MathKey(name, "$name()", back = 1, spoken = spoken)

private val DELETE = MathKey("⌫", spoken = "Delete", action = KeyAction.DELETE)

private val NUMBERS = listOf(
    listOf(MathKey("7"), MathKey("8"), MathKey("9"), op("÷", " ÷ ", "Divide"), MathKey("("), MathKey(")")),
    listOf(MathKey("4"), MathKey("5"), MathKey("6"), op("×", " × ", "Times"), MathKey("x"), MathKey("^", spoken = "Power")),
    listOf(MathKey("1"), MathKey("2"), MathKey("3"), op("−", " - ", "Minus"), MathKey("y"), MathKey("√", spoken = "Square root")),
    listOf(MathKey("0"), MathKey("."), MathKey("π", spoken = "Pi"), op("+", " + ", "Plus"), op("=", " = ", "Equals"), DELETE),
)

private val FUNCTIONS = listOf(
    listOf(fn("sin", "Sine"), fn("cos", "Cosine"), fn("tan", "Tangent"), fn("ln", "Natural log"), fn("log", "Log"), MathKey("e")),
    listOf(MathKey("²", spoken = "Squared"), MathKey("!", spoken = "Factorial"), MathKey("°", spoken = "Degrees"), MathKey("%", spoken = "Percent"), MathKey("|", spoken = "Absolute value bar"), MathKey("at", " at ")),
    listOf(op("<", " < ", "Less than"), op(">", " > ", "Greater than"), op("≤", " ≤ ", "At most"), op("≥", " ≥ ", "At least"), MathKey(",", ", ", spoken = "Comma"), MathKey("mod", " mod ")),
    listOf(MathKey("d/dx", "d/dx ", spoken = "Derivative"), MathKey("∫", "∫ ", spoken = "Integral"), MathKey("dx", " dx"), MathKey("from", " from "), MathKey("to", " to "), DELETE),
)

/**
 * A keypad for math in place of the phone's keyboard: numbers and operators on one page, functions,
 * comparisons and calculus words on the other. It types into the note like any keyboard; "ABC" goes back to
 * the phone's keyboard.
 */
@Composable
fun MathKeyboard(value: TextFieldValue, onChange: (TextFieldValue) -> Unit, onLetters: () -> Unit) {
    var functions by rememberSaveable { mutableStateOf(false) }
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
            KeyAction.PAGE -> functions = !functions
        }
    }
    val bottom = listOf(
        MathKey("ABC", spoken = "Letters keyboard", action = KeyAction.LETTERS),
        MathKey(if (functions) "123" else "f(x)", spoken = if (functions) "Numbers" else "Functions", action = KeyAction.PAGE),
        MathKey("←", spoken = "Move left", action = KeyAction.LEFT),
        MathKey("space", " ", spoken = "Space", weight = 2f),
        MathKey("→", spoken = "Move right", action = KeyAction.RIGHT),
        MathKey("↵", spoken = "New line", action = KeyAction.ENTER, strong = true),
    )
    HorizontalDivider(color = jc.line)
    Column(
        Modifier.fillMaxWidth().background(jc.surface).padding(horizontal = 6.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        (if (functions) FUNCTIONS else NUMBERS).plusElement(bottom).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { key -> Key(key, Modifier.weight(key.weight)) { press(key) } }
            }
        }
    }
}

@Composable
private fun Key(key: MathKey, modifier: Modifier, onPress: () -> Unit) {
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
        modifier.height(46.dp).clip(RoundedCornerShape(8.dp)).background(face)
            .semantics {
                role = Role.Button
                contentDescription = key.spoken
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
