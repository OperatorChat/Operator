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

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import chat.operator.app.R
import chat.operator.core.matrix.MatrixRepository
import chat.operator.core.matrix.MessageFile
import chat.operator.core.matrix.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Files sent in chats (documents, APKs, videos, anything that isn't a photo).
 * Operator never opens them itself: "Open file" hands the file to whatever app
 * the phone uses for that type, and "Save to Downloads" puts it in
 * Downloads/Operator. Nothing is fetched until one of those is chosen.
 */
object Attachments {
    /** New rows carry the "file" type; rows stored before 0.2.0 still read "[File]". */
    fun isFile(message: ChatMessage) = message.contentType == "file" || (message.contentType == "text" && message.body == "[File]")

    fun open(activity: MainActivity, roomId: String, eventId: String) {
        Toast.makeText(activity, R.string.chat_file_fetching, Toast.LENGTH_SHORT).show()
        activity.lifecycleScopeLaunch {
            val file = fetch(roomId, eventId) ?: return@lifecycleScopeLaunch toast(activity, R.string.chat_file_failed)
            // An app package is saved rather than opened: installing apps needs a permission
            // Operator deliberately doesn't ask for. The Files app can install it from Downloads.
            if (file.mimeType == "application/vnd.android.package-archive" || file.name.endsWith(".apk", true)) {
                if (store(activity, file)) Toast.makeText(activity, activity.getString(R.string.chat_file_apk_saved, file.name), Toast.LENGTH_LONG).show()
                else toast(activity, R.string.chat_file_failed)
                return@lifecycleScopeLaunch
            }
            val uri = withContext(Dispatchers.IO) {
                val dir = File(activity.cacheDir, "shared").apply { deleteRecursively(); mkdirs() }
                val out = File(dir, file.name.replace(Regex("[/\\\\]"), "_"))
                out.writeBytes(file.bytes)
                FileProvider.getUriForFile(activity, "${activity.packageName}.files", out)
            }
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, file.mimeType)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                activity.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                // Nothing handles this type: keep the file rather than lose it.
                if (store(activity, file)) Toast.makeText(activity, activity.getString(R.string.chat_file_no_app, file.name), Toast.LENGTH_LONG).show()
                else toast(activity, R.string.chat_file_failed)
            }
        }
    }

    fun save(activity: MainActivity, roomId: String, eventId: String) {
        if (Build.VERSION.SDK_INT < 29) {
            activity.requestPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) { granted ->
                if (granted) saveNow(activity, roomId, eventId) else toast(activity, R.string.chat_file_permission)
            }
        } else {
            saveNow(activity, roomId, eventId)
        }
    }

    private fun saveNow(activity: MainActivity, roomId: String, eventId: String) {
        Toast.makeText(activity, R.string.chat_file_fetching, Toast.LENGTH_SHORT).show()
        activity.lifecycleScopeLaunch {
            val file = fetch(roomId, eventId) ?: return@lifecycleScopeLaunch toast(activity, R.string.chat_file_failed)
            if (store(activity, file)) Toast.makeText(activity, activity.getString(R.string.chat_file_saved, file.name), Toast.LENGTH_LONG).show()
            else toast(activity, R.string.chat_file_failed)
        }
    }

    private suspend fun fetch(roomId: String, eventId: String): MessageFile? =
        runCatching { MatrixRepository.getMessageFile(roomId, eventId) }.getOrNull()

    /** Writes to Downloads/Operator: through MediaStore on Android 10+, straight to the folder before that. */
    /** Writes [file] to Downloads/Operator; also used for problem reports when no mail app exists. */
    suspend fun store(activity: MainActivity, file: MessageFile): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                    put(MediaStore.Downloads.MIME_TYPE, file.mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Operator")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = activity.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@runCatching false
                resolver.openOutputStream(uri)?.use { it.write(file.bytes) } ?: return@runCatching false
                values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Operator").apply { mkdirs() }
                File(dir, file.name.replace(Regex("[/\\\\]"), "_")).writeBytes(file.bytes)
            }
            true
        }.getOrDefault(false)
    }

    private fun toast(activity: MainActivity, res: Int) = Toast.makeText(activity, res, Toast.LENGTH_LONG).show()

    private fun MainActivity.lifecycleScopeLaunch(block: suspend () -> Unit) { scope.launch { block() } }
}
