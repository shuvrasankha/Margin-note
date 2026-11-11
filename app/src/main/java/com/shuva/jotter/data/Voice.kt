package com.shuva.jotter.data

val Attachment.isAudio get() = mime.startsWith("audio/")

/** 30 minutes of 64 kbps mono AAC is about 14 MB, under the attachment limit. */
const val MAX_RECORDING_MS = 30 * 60 * 1000

/** A duration as m:ss, or h:mm:ss from an hour up. */
fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
