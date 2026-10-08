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
// Data model shared between the Matrix layer and the UI. The shapes follow the
// fenleon/chats service contract (MIT, Copyright (c) 2026 Fenn) so the ported
// repository code is unchanged; the Light SDK transport is gone.
package chat.operator.core.matrix

import kotlinx.serialization.Serializable

/** One row of the chat list. */
@Serializable
data class ChatRoom(
    val id: String,
    val name: String,
    val lastMessage: String,
    val unreadCount: Long,
    val lastTimestampMs: Long,
    /** Id of the newest timeline event, for the thread's initial cursor. */
    val lastEventId: String? = null,
    /** Direct (1:1) chat: the thread can hide per-message sender names. */
    val isDirect: Boolean = false,
    val contactId: String? = null,
    val contactPhone: String? = null,
    /** Bridge network ("whatsapp", "signal", …) when known. */
    val network: String? = null,
    val community: String? = null,
    val muted: Boolean = false,
    val archived: Boolean = false,
    val pinned: Boolean = false,
)

/** One row of a chat's timeline. */
@Serializable
data class ChatMessage(
    val id: String,
    val sender: String,
    val senderName: String,
    val body: String,
    val timestampMs: Long,
    val isMine: Boolean,
    /** Bridge send status for an outgoing message ("PENDING", "FAIL_RETRIABLE", …), null = none. */
    val sendStatus: String? = null,
    /** "text" | "image" | "audio" | "notice" | "redacted". */
    val contentType: String = "text",
    val read: Boolean = false,
    val reactions: List<String> = emptyList(),
    val durationMs: Long? = null,
    val caption: String? = null,
    val edited: Boolean = false,
    val forwarded: Boolean = false,
    val canEdit: Boolean = true,
    val canUnsend: Boolean = true,
    val formattedHtml: String? = null,
    val replyToId: String? = null,
    val replyToSender: String? = null,
    val replyToExcerpt: String? = null,
)

/** One person in a room, for @mentions. */
data class RoomMember(val userId: String, val name: String)

/** An attachment fetched for opening or saving: its name, type and bytes. */
class MessageFile(val name: String, val mimeType: String, val bytes: ByteArray)

/** A page of timeline rows plus paging and playback state. */
@Serializable
data class MessagesPage(
    val messages: List<ChatMessage>,
    val nextBeforeEventId: String? = null,
    val hasMore: Boolean = false,
    val encrypted: Boolean = false,
    val audioPlayingEventId: String? = null,
    val audioPositionMs: Long? = null,
)

@Serializable
data class SendResult(
    val transactionId: String,
    val eventId: String? = null,
)

@Serializable
data class E2eeState(
    val verified: Boolean,
    val canVerify: Boolean,
    val detail: String? = null,
)

/** state: none | waiting | accept | start | verifying | compare | done | cancelled | error */
@Serializable
data class VerificationState(
    val state: String,
    val emoji: List<String>? = null,
    val deviceId: String? = null,
    val detail: String? = null,
)

@Serializable
data class AccountState(
    val loggedIn: Boolean,
    val userId: String? = null,
    val homeserver: String? = null,
    val loginMode: String? = null,
)

/** state: logged_out | connecting | syncing | offline */
@Serializable
data class ConnectionState(
    val state: String,
    val detail: String? = null,
    val roomsTotal: Int = 0,
    val roomsResolved: Int = 0,
    val syncEnabled: Boolean = true,
    val restoreScanning: Boolean = false,
    val restoreScanned: Int = 0,
    val restoreRoomsTotal: Int = 0,
    val restoreCompleted: Boolean = false,
    val roomsProjected: Int = 0,
    val roomsJoined: Int = 0,
    val backfillRoomsDone: Int = 0,
    val backfillRoomsTotal: Int = 0,
    val roomListReady: Boolean = false,
)

/** Media stream volume for the in-app volume indicator while a voice note plays. */
data class VolumeLevel(val level: Int, val max: Int)
