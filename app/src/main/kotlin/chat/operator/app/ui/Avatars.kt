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
import android.widget.TextView
import androidx.core.content.ContextCompat
import chat.operator.app.R

/**
 * Initials avatars for chats and senders: one or two letters on a coloured
 * circle. The colour is chosen by hashing the name, so a chat always gets the
 * same one. No images are fetched.
 */
object Avatars {
    private val palette = intArrayOf(
        R.color.avatar_1, R.color.avatar_2, R.color.avatar_3, R.color.avatar_4,
        R.color.avatar_5, R.color.avatar_6, R.color.avatar_7, R.color.avatar_8,
    )

    fun colour(context: Context, key: String): Int {
        var h = 0
        for (c in key) h = 31 * h + c.code
        return ContextCompat.getColor(context, palette[Math.floorMod(h, palette.size)])
    }

    /** First letter of the first two words, skipping emoji and punctuation. */
    fun initials(name: String): String {
        val words = name.split(' ', ' ').map { w -> w.filter { it.isLetterOrDigit() } }.filter { it.isNotEmpty() }
        return when {
            words.isEmpty() -> "#"
            words.size == 1 -> words[0].take(1).uppercase()
            else -> (words[0].take(1) + words[1].take(1)).uppercase()
        }
    }

    fun bind(view: TextView, name: String, key: String = name) {
        view.text = initials(name)
        view.background = view.background.mutate().also { it.setTint(colour(view.context, key)) }
    }
}
