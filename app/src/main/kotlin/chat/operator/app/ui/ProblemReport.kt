/*
 * Operator: chat for keypad phones.
 * Copyright (C) 2026 Spanorak (https://www.reddit.com/user/Spanorak)
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version. It is distributed WITHOUT ANY WARRANTY; see
 * the GNU General Public License (LICENSE) for details.
 */
package chat.operator.app.ui

import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import chat.operator.app.BuildConfig
import chat.operator.app.R
import chat.operator.core.matrix.MatrixRepository
import chat.operator.core.matrix.MessageFile
import chat.operator.core.push.PushRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Report a problem": a short technical report for the support address. It
 * holds the phone model, Android and Operator versions, the push state and
 * recent log lines from Operator's own process. Operator never logs message
 * text, names or tokens (see CONTRIBUTING.md), so the report carries none. Sent through
 * whatever mail app the phone has; saved to Downloads if there is none.
 */
object ProblemReport {
    private const val LOG_LINES = 400

    fun send(activity: MainActivity) {
        activity.scope.launch {
            val report = runCatching { withContext(Dispatchers.IO) { build(activity) } }.getOrNull()
            if (report == null) { Toast.makeText(activity, R.string.report_failed, Toast.LENGTH_LONG).show(); return@launch }
            val name = "operator-report-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.UK).format(Date()) + ".txt"
            val file = withContext(Dispatchers.IO) {
                File(activity.cacheDir, "shared").apply { mkdirs() }.resolve(name).also { it.writeText(report) }
            }
            val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.files", file)
            val email = activity.getString(R.string.brand_support_email)
            val intent = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
                .putExtra(Intent.EXTRA_SUBJECT, activity.getString(R.string.report_subject, BuildConfig.VERSION_NAME))
                .putExtra(Intent.EXTRA_TEXT, activity.getString(R.string.report_body))
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val hasMail = Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:")).resolveActivity(activity.packageManager) != null
            val save: () -> Unit = {
                activity.scope.launch {
                    val saved = Attachments.store(activity, MessageFile(name, "text/plain", report.toByteArray()))
                    Toast.makeText(activity, if (saved) activity.getString(R.string.report_saved_to, name, email) else activity.getString(R.string.report_failed), Toast.LENGTH_LONG).show()
                }
            }
            // Keypad phones often have no mail app: offer the two real choices rather than a share
            // sheet full of unrelated apps. Saving is listed first when there is no mail app.
            val share: () -> Unit = { activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.settings_report))) }
            val options = if (hasMail) listOf(
                (activity.getString(R.string.report_send_email) as CharSequence) to share,
                (activity.getString(R.string.report_save) as CharSequence) to save,
            ) else listOf(
                (activity.getString(R.string.report_save) as CharSequence) to save,
                (activity.getString(R.string.report_send_other) as CharSequence) to share,
            )
            val details = mapOf(
                0 to (activity.getString(if (hasMail) R.string.report_send_email_detail else R.string.report_save_detail, email) as CharSequence),
                1 to (activity.getString(if (hasMail) R.string.report_save_detail else R.string.report_send_other_detail, email) as CharSequence),
            )
            activity.push(OptionsScreen(activity, activity.getString(R.string.settings_report), options, details))
        }
    }

    private fun build(activity: MainActivity): String = buildString {
        appendLine("Operator problem report")
        appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.UK).format(Date())}")
        appendLine("Operator: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_TYPE})")
        appendLine("Phone: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.SUPPORTED_ABIS.joinToString()}")
        val dm = activity.resources.displayMetrics
        appendLine("Screen: ${dm.widthPixels}x${dm.heightPixels} @ ${dm.densityDpi} dpi")
        val account = MatrixRepository.accountState()
        appendLine("Signed in: ${account.loggedIn}; server: ${account.homeserver ?: "-"}")
        appendLine("Connection: ${MatrixRepository.connectionState.value}")
        appendLine("Push: ${if (PushRouter.usingExternal) "external distributor" else "built-in ntfy"}, connected=${PushRouter.isConnected}, last push ${lastPush()}")
        appendLine("Theme: ${Appearance.theme(activity).id}, mode ${Appearance.mode(activity)}, text ${Appearance.textScale(activity).id}")
        appendLine()
        appendLine("Recent log (Operator's own process only; no message content is ever logged):")
        appendLine(recentLog())
    }

    private fun lastPush(): String {
        val at = PushRouter.lastPushAtMs
        if (at == 0L) return "never"
        val s = (android.os.SystemClock.elapsedRealtime() - at) / 1000
        return if (s < 3600) "${s / 60} min ago" else "${s / 3600} h ago"
    }

    /** The tail of this process's logcat; Android only shows an app its own lines. */
    private fun recentLog(): String = runCatching {
        val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time", "-t", LOG_LINES.toString()))
        process.inputStream.bufferedReader().use { it.readText() }
    }.getOrElse { "(log unavailable: ${it.javaClass.simpleName})" }
}
