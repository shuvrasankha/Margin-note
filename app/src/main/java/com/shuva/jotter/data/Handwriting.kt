package com.shuva.jotter.data

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import com.google.mlkit.vision.digitalink.recognition.RecognitionContext
import com.google.mlkit.vision.digitalink.recognition.WritingArea
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Languages handwriting can be read in. [label] is the language's own name. */
enum class InkLanguage(val tag: String, val label: String) {
    ENGLISH("en-US", "English"),
    BENGALI("bn", "বাংলা"),
}

/**
 * Handwriting to text, read on the phone by ML Kit Digital Ink from a drawing's pen strokes. Each language
 * needs its model (about 20 MB) downloaded once; after that it works offline. [onDownloading] is called
 * before a download starts.
 */
suspend fun recognizeHandwriting(strokes: List<PenStroke>, language: InkLanguage, onDownloading: () -> Unit = {}): String {
    val id = DigitalInkRecognitionModelIdentifier.fromLanguageTag(language.tag) ?: error("${language.label} isn't supported")
    val model = DigitalInkRecognitionModel.builder(id).build()
    val models = RemoteModelManager.getInstance()
    if (!models.isModelDownloaded(model).await()) {
        onDownloading()
        models.download(model, DownloadConditions.Builder().build()).await()
    }
    val recognizer = DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(model).build())
    try {
        val out = mutableListOf<String>()
        for (line in inkLines(strokes)) {
            val ink = Ink.builder()
            for (s in line) {
                val b = Ink.Stroke.builder()
                for (k in 0 until s.points.size / 2) b.addPoint(Ink.Point.create(s.points[2 * k], s.points[2 * k + 1]))
                ink.addStroke(b.build())
            }
            val box = line.bounds(line.indices.toSet())!!
            // The line's size, and the text before it, help tell a "o" from an "O" and pick the right word.
            val context = RecognitionContext.builder().setPreContext(out.joinToString(" ").takeLast(20))
                .setWritingArea(WritingArea(box[2] - box[0] + 1f, box[3] - box[1] + 1f)).build()
            recognizer.recognize(ink.build(), context).await().candidates.firstOrNull()?.text?.trim()
                ?.takeIf { it.isNotEmpty() }?.let { out += it }
        }
        return out.joinToString("\n")
    } finally {
        recognizer.close()
    }
}

/**
 * The ink strokes of [strokes] grouped into lines of writing, top to bottom, each in the order it was
 * written. A stroke joins a line when its height overlaps the line's (stretched by a third, so dots, accents
 * and the matra above Bengali letters stay with their word). Eraser strokes are left out; dots stay.
 */
fun inkLines(strokes: List<PenStroke>): List<List<PenStroke>> {
    // shortcut: ink an eraser later rubbed out is still read; the eraser only hides it.
    val ink = strokes.withIndex().filter { (_, s) -> !s.erase && s.points.size >= 2 }
    class Line(var top: Float, var bottom: Float, val members: MutableList<Int>)
    val lines = mutableListOf<Line>()
    // Tallest first, so a line is set by its letters before small marks look for one.
    for ((i, s) in ink.sortedByDescending { (_, s) -> s.yRange().let { it.second - it.first } }) {
        val (top, bottom) = s.yRange()
        val line = lines.filter { l ->
            val slack = (l.bottom - l.top) / 3
            top <= l.bottom + slack && bottom >= l.top - slack
        }.maxByOrNull { l -> minOf(bottom, l.bottom) - maxOf(top, l.top) }
        if (line == null) lines += Line(top, bottom, mutableListOf(i))
        else { line.members += i; line.top = minOf(line.top, top); line.bottom = maxOf(line.bottom, bottom) }
    }
    return lines.sortedBy { it.top }.map { l -> l.members.sorted().map { strokes[it] } }
}

private fun PenStroke.yRange(): Pair<Float, Float> {
    val ys = points.filterIndexed { k, _ -> k % 2 == 1 }
    return ys.min() to ys.max()
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
