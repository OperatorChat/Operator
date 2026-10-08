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
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import chat.operator.core.matrix.MatrixRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Turns a picked or captured image into a downscaled JPEG and sends it
 * through the Matrix layer (encrypted when the room is). Long edge capped so
 * uploads stay small on a phone that is often on mobile data.
 */
object PhotoSender {
    private const val TAG = "PhotoSender"
    private const val MAX_EDGE = 1280
    private const val QUALITY = 82

    suspend fun send(context: Context, roomId: String, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val payload = runCatching { load(context, uri) }
            .onFailure { android.util.Log.w(TAG, "photo load failed: ${it.javaClass.simpleName}: ${it.message?.take(160)}") }
            .getOrNull()
        if (payload == null) {
            android.util.Log.w(TAG, "photo load returned nothing for ${uri.scheme}://${uri.authority}")
            return@withContext false
        }
        android.util.Log.i(TAG, "sending photo ${payload.width}x${payload.height}, ${payload.jpeg.size / 1024} KB")
        runCatching { MatrixRepository.sendPhoto(roomId, payload) }
            .onFailure { android.util.Log.w(TAG, "photo send failed: ${it.javaClass.simpleName}: ${it.message?.take(160)}") }
            .getOrDefault(false)
            .also { if (!it) android.util.Log.w(TAG, "photo send returned false") }
    }

    private fun load(context: Context, uri: Uri): MatrixRepository.PhotoPayload? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // Measure-only decode: it always returns null, so judge by the stream and the measured size.
        val stream = resolver.openInputStream(uri) ?: return null
        stream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > MAX_EDGE * 2 || bounds.outHeight / sample > MAX_EDGE * 2) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val rotation = resolver.openInputStream(uri)?.use { stream ->
            runCatching { ExifInterface(stream).rotationDegrees }.getOrDefault(0)
        } ?: 0
        val scale = minOf(MAX_EDGE.toFloat() / decoded.width, MAX_EDGE.toFloat() / decoded.height, 1f)
        val matrix = Matrix().apply {
            if (scale < 1f) postScale(scale, scale)
            if (rotation != 0) postRotate(rotation.toFloat())
        }
        val bitmap = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
        return MatrixRepository.PhotoPayload(
            jpeg = out.toByteArray(),
            fileName = "photo-${System.currentTimeMillis()}.jpg",
            mimeType = "image/jpeg",
            width = bitmap.width,
            height = bitmap.height,
        )
    }
}
