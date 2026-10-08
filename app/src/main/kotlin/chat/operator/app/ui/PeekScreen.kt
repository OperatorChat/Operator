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

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import chat.operator.app.R
import chat.operator.core.matrix.ChatMessage
import chat.operator.core.matrix.MatrixRepository
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * The sideways preview (turn the phone with auto-rotate on while a chat is
 * highlighted): the last few messages, newest at the bottom, read from the
 * local store. It never opens the chat, so no read receipt is sent and the
 * unread count stays. Turning the phone upright closes it; Back works too.
 */
class PeekScreen(host: ScreenHost, private val roomId: String, private val roomName: String) : Screen(host) {

    private lateinit var items: LinearLayout

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup): View =
        inflate(R.layout.screen_peek, parent).also { v ->
            v.findViewById<TextView>(R.id.title).text = roomName
            v.findViewById<TextView>(R.id.detail).setText(R.string.peek_title)
            items = v.findViewById(R.id.items)
        }

    override fun onShow() {
        host.setSoftKeys(null, host.string(R.string.softkey_back))
        find<View>(R.id.scroll).requestFocus()
        host.scope.launch {
            val page = runCatching { MatrixRepository.getMessages(roomId, null, LIMIT) }.getOrNull()
            render(page?.messages.orEmpty())
        }
    }

    private fun render(messages: List<ChatMessage>) {
        items.removeAllViews()
        val ctx = items.context
        val density = ctx.resources.displayMetrics.density
        if (messages.isEmpty()) {
            items.addView(TextView(ctx).apply { setText(R.string.peek_empty); setTextColor(themeColour(R.attr.opTextSecondary)) })
            return
        }
        val time = DateFormat.getTimeInstance(DateFormat.SHORT)
        messages.forEach { m ->
            val who = when {
                m.isMine -> ctx.getString(R.string.peek_you)
                m.senderName.isNotBlank() -> m.senderName
                else -> m.sender
            }
            items.addView(TextView(ctx).apply {
                text = "$who · ${time.format(Date(m.timestampMs))}"
                textSize = 12f
                typeface = androidx.core.content.res.ResourcesCompat.getFont(ctx, R.font.inter_semibold)
                setTextColor(if (m.isMine) themeColour(R.attr.opAccent) else Avatars.colour(ctx, m.sender))
                setPadding(0, (6 * density).toInt(), 0, 0)
            })
            items.addView(TextView(ctx).apply {
                text = when (m.contentType) {
                    "image" -> ctx.getString(R.string.chat_photo_placeholder)
                    "audio" -> ctx.getString(R.string.chat_voice_placeholder)
                    "redacted" -> ctx.getString(R.string.chat_message_unsent)
                    else -> m.body.ifBlank { ctx.getString(R.string.chat_cannot_open_yet) }
                }
                setTextColor(themeColour(R.attr.opText))
                setPadding(0, (1 * density).toInt(), 0, (2 * density).toInt())
            })
        }
        find<View>(R.id.scroll).post { find<android.widget.ScrollView>(R.id.scroll).fullScroll(View.FOCUS_DOWN) }
        find<View>(R.id.hint).isVisible = true
    }

    private fun themeColour(attr: Int): Int {
        val tv = android.util.TypedValue()
        host.activity.theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    companion object {
        private const val LIMIT = 12
    }
}
