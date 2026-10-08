/*
 * Operator: chat for keypad phones.
 * Adapted from Chats by Fenn (https://github.com/fenleon/chats), MIT License,
 * Copyright (c) 2026 Fenn; see core-matrix/LICENSE-fenleon-chats.txt.
 * Modifications Copyright (C) 2026 Spanorak (https://www.reddit.com/user/Spanorak).
 *
 * As part of Operator this file is distributed under the GNU General Public
 * License, version 3 or (at your option) any later version, WITHOUT ANY
 * WARRANTY; see LICENSE for details.
 */
package chat.operator.core.matrix

import android.content.Context

/**
 * What the Matrix layer needs from the app's notification code. The app
 * module implements it (channels, sound, vibration, tap-to-open live there;
 * SPEC §5.5 says sound and vibration are decided locally) and installs it
 * with [ChatNotifier.impl].
 */
interface MessageNotifier {
    fun notifyMessage(
        context: Context,
        roomId: String,
        roomName: String,
        senderName: String?,
        preview: String,
        direct: Boolean,
        unreadCount: Long,
        /** The message names the signed-in user: it may cut through a "mentions only" mute. */
        mention: Boolean = false,
    )

    fun cancelRoom(context: Context, roomId: String)

    /** Low-key "checking for messages failed, will retry" signal. */
    fun notifySyncPending(context: Context)

    fun clearSyncPending(context: Context)

    fun clearAll(context: Context)
}

/** Delegating facade kept so the ported repository code reads as it did in Chats. */
object ChatNotifier : MessageNotifier {
    @Volatile
    var impl: MessageNotifier = object : MessageNotifier {
        override fun notifyMessage(
            context: Context, roomId: String, roomName: String, senderName: String?,
            preview: String, direct: Boolean, unreadCount: Long, mention: Boolean,
        ) = Unit
        override fun cancelRoom(context: Context, roomId: String) = Unit
        override fun notifySyncPending(context: Context) = Unit
        override fun clearSyncPending(context: Context) = Unit
        override fun clearAll(context: Context) = Unit
    }

    override fun notifyMessage(
        context: Context, roomId: String, roomName: String, senderName: String?,
        preview: String, direct: Boolean, unreadCount: Long, mention: Boolean,
    ) {
        impl.notifyMessage(context, roomId, roomName, senderName, preview, direct, unreadCount, mention)
        MatrixRepository.pendingNotifyRoomId = roomId
        // Diagnostics only: no ids, no names, no content.
        android.util.Log.i("ChatNotifier", "notification posted (${if (direct) "direct" else "group"} chat, unread=$unreadCount${if (mention) ", mention" else ""})")
    }

    override fun cancelRoom(context: Context, roomId: String) = impl.cancelRoom(context, roomId)
    override fun notifySyncPending(context: Context) = impl.notifySyncPending(context)
    override fun clearSyncPending(context: Context) = impl.clearSyncPending(context)
    override fun clearAll(context: Context) = impl.clearAll(context)
}
