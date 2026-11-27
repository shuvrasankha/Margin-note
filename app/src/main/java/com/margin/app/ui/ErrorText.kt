package com.margin.app.ui

import android.content.Context
import com.margin.app.R
import com.margin.app.data.AiException
import com.margin.app.data.AttachmentFiles
import com.margin.app.data.MAX_ATTACHMENT_BYTES
import com.margin.app.data.NewerBackupException
import com.margin.app.data.NotABackupException
import com.margin.app.data.WrongPassword

/** What went wrong, for a toast or a line under a button: the app's own failures in its language, anything else as it came. */
internal fun errorText(context: Context, e: Throwable): String? = when (e) {
    is AiException -> e.text(context)
    is AttachmentFiles.TooLarge -> context.getString(R.string.error_file_too_large, (MAX_ATTACHMENT_BYTES / 1_000_000).toString())
    is NotABackupException -> context.getString(R.string.error_not_a_backup)
    is NewerBackupException -> context.getString(R.string.error_newer_backup)
    is WrongPassword -> context.getString(R.string.error_pdf_wrong_password)
    else -> e.message
}
