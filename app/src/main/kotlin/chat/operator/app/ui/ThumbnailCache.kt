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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File
import java.security.MessageDigest

/**
 * Thumbnails for photos the user has chosen to load (SPEC §5.3: nothing is
 * downloaded automatically). Once a photo has been viewed, a small copy is
 * kept in the app's cache directory, so the chat shows a thumbnail from then
 * on, across restarts, until the cache is cleared.
 */
object ThumbnailCache {
    private const val DIR = "operator_thumbs"
    private const val MAX_SIDE = 160
    private val memory = LruCache<String, Bitmap>(48)

    private fun file(context: Context, eventId: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(eventId.toByteArray())
        val name = digest.joinToString("") { "%02x".format(it) }.take(32)
        return File(context.cacheDir, DIR).apply { mkdirs() }.resolve("$name.jpg")
    }

    /** True if a thumbnail exists without decoding anything. */
    fun has(context: Context, eventId: String): Boolean =
        memory.get(eventId) != null || file(context, eventId).exists()

    /** The thumbnail, decoding from disk if needed (small; fine off the main thread). */
    fun get(context: Context, eventId: String): Bitmap? {
        memory.get(eventId)?.let { return it }
        val f = file(context, eventId)
        if (!f.exists()) return null
        return BitmapFactory.decodeFile(f.absolutePath)?.also { memory.put(eventId, it) }
    }

    /** Stores a thumbnail made from the full display JPEG. */
    fun put(context: Context, eventId: String, jpeg: ByteArray) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > MAX_SIDE * 2 || bounds.outHeight / sample > MAX_SIDE * 2) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return
        val scale = minOf(MAX_SIDE.toFloat() / bitmap.width, MAX_SIDE.toFloat() / bitmap.height, 1f)
        val thumb = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
        memory.put(eventId, thumb)
        runCatching { file(context, eventId).outputStream().use { thumb.compress(Bitmap.CompressFormat.JPEG, 80, it) } }
    }

    fun clear(context: Context) {
        memory.evictAll()
        File(context.cacheDir, DIR).deleteRecursively()
    }
}
