package com.shuva.jotter.data

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OCR: the text in a photo, read on the phone by ML Kit (Latin script: English and other languages written
 * with the Latin alphabet). Nothing is uploaded. Empty when there's no text in it.
 */
suspend fun recognizeText(context: Context, image: Uri): String {
    val input = InputImage.fromFilePath(context, image) // reads the photo's rotation too
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    return try {
        suspendCancellableCoroutine { cont ->
            recognizer.process(input)
                .addOnSuccessListener { result -> cont.resume(ocrToNoteText(result.textBlocks.map { b -> b.lines.map { it.text } })) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
    } finally {
        recognizer.close()
    }
}

/**
 * Recognised [blocks] (each a list of lines, top to bottom) as note text: lines as the photo had them, a
 * blank line between blocks, and no empty or space-only lines otherwise.
 */
fun ocrToNoteText(blocks: List<List<String>>): String =
    blocks.map { lines -> lines.map { it.trim().replace(Regex("\\s+"), " ") }.filter { it.isNotEmpty() }.joinToString("\n") }
        .filter { it.isNotEmpty() }
        .joinToString("\n\n")
