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
import de.connect2x.trixnity.client.MatrixClient

/**
 * What the Matrix layer needs from the push layer. `core-push` implements it
 * (UnifiedPush connector, built-in ntfy fallback, pusher registration) and
 * installs it with [PushChannel.impl]; pushes come back through
 * [MatrixRepository.onPushDelivered].
 */
interface PushChannelHooks {
    /** Register the pusher for [client] (if needed) and open the wake-up channel. */
    fun start(context: Context, client: MatrixClient)

    /** Close the wake-up channel; the pusher stays registered. */
    fun stop()

    /** Remove this install's pusher from the homeserver (logout). */
    suspend fun unregister(context: Context, client: MatrixClient)

    /** True while a wake-up channel is actually open right now. */
    val isConnected: Boolean
}

/** Delegating facade kept so the ported repository code reads as it did in Chats. */
object PushChannel : PushChannelHooks {
    @Volatile
    var impl: PushChannelHooks = object : PushChannelHooks {
        override fun start(context: Context, client: MatrixClient) = Unit
        override fun stop() = Unit
        override suspend fun unregister(context: Context, client: MatrixClient) = Unit
        override val isConnected: Boolean get() = false
    }

    override fun start(context: Context, client: MatrixClient) = impl.start(context, client)
    override fun stop() = impl.stop()
    override suspend fun unregister(context: Context, client: MatrixClient) = impl.unregister(context, client)
    override val isConnected: Boolean get() = impl.isConnected
}
