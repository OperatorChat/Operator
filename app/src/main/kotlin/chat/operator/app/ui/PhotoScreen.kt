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

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import chat.operator.app.R
import chat.operator.core.matrix.MatrixRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shows one photo, loaded on demand and downscaled by the Matrix layer
 * (SPEC §5.3: "Photo, press to load"). Left soft key saves it to the phone.
 */
class PhotoScreen(host: ScreenHost, private val roomId: String, private val eventId: String) : Screen(host) {

    private lateinit var image: ImageView
    private lateinit var status: TextView
    private var loaded = false

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup): View =
        inflate(R.layout.screen_photo, parent).also { v ->
            image = v.findViewById(R.id.image)
            status = v.findViewById(R.id.status)
        }

    override fun onShow() {
        host.setSoftKeys(if (loaded) host.string(R.string.photo_save) else null, host.string(R.string.softkey_back))
        if (loaded) return
        status.text = host.string(R.string.photo_loading)
        status.isVisible = true
        host.scope.launch {
            val bytes = runCatching { MatrixRepository.getMessageMedia(roomId, eventId, allowMobileData = true) }.getOrNull()
            val bitmap = bytes?.let { withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(it, 0, it.size) } }
            if (bitmap == null) {
                status.text = host.string(R.string.photo_failed)
                return@launch
            }
            image.setImageBitmap(bitmap)
            status.isVisible = false
            loaded = true
            withContext(Dispatchers.Default) { ThumbnailCache.put(host.activity, eventId, bytes) }
            host.setSoftKeys(host.string(R.string.photo_save), host.string(R.string.softkey_back))
        }
    }

    override fun onMenu(): Boolean {
        if (!loaded) return true
        status.text = host.string(R.string.photo_saving)
        status.isVisible = true
        host.scope.launch {
            val ok = runCatching { MatrixRepository.saveMessageImage(roomId, eventId) }.getOrDefault(false)
            status.text = host.string(if (ok) R.string.photo_saved else R.string.photo_save_failed)
        }
        return true
    }
}
