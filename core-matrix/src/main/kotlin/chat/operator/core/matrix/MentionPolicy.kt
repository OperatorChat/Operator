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
package chat.operator.core.matrix

import android.content.Context

/**
 * Operator's local mention rules (SPEC §5.5: notification decisions are made
 * on the phone). A muted chat can be "mentions only": silent except when
 * someone names you. Also remembers which chats hold an unseen mention, so
 * the chat list can mark them.
 */
object MentionPolicy {
    private const val PREFS = "operator_mentions"
    private const val KEY_MENTIONS_ONLY = "mentions_only"
    private const val KEY_PENDING = "pending"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isMentionsOnly(ctx: Context, roomId: String): Boolean = roomId in prefs(ctx).getStringSet(KEY_MENTIONS_ONLY, emptySet()).orEmpty()

    fun setMentionsOnly(ctx: Context, roomId: String, on: Boolean) = update(ctx, KEY_MENTIONS_ONLY, roomId, on)

    /** Chats with a mention of you that you have not opened yet. */
    fun hasPendingMention(ctx: Context, roomId: String): Boolean = roomId in prefs(ctx).getStringSet(KEY_PENDING, emptySet()).orEmpty()

    fun addPendingMention(ctx: Context, roomId: String) = update(ctx, KEY_PENDING, roomId, true)

    fun clearPendingMention(ctx: Context, roomId: String) = update(ctx, KEY_PENDING, roomId, false)

    private fun update(ctx: Context, key: String, roomId: String, add: Boolean) {
        val set = prefs(ctx).getStringSet(key, emptySet()).orEmpty().toMutableSet()
        if (if (add) !set.add(roomId) else !set.remove(roomId)) return
        prefs(ctx).edit().putStringSet(key, set).apply()
    }
}
