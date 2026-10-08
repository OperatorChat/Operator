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
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import android.graphics.Canvas
import android.graphics.Rect
import chat.operator.app.R
import chat.operator.core.matrix.ChatRoom
import chat.operator.core.matrix.MatrixRepository
import chat.operator.core.matrix.MatrixRepository.ChatConnectionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** The chat list (SPEC §5.2): most recent first, name, one-line preview, time, unread marker. */
class ChatListScreen(host: ScreenHost) : Screen(host) {

    private lateinit var list: RecyclerView
    private lateinit var empty: TextView
    private lateinit var headerDetail: TextView
    private val adapter = RoomAdapter { room -> host.push(ChatScreen(host, room.id)) }
    private var job: Job? = null
    private var focusedRoomId: String? = null

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup): View =
        inflate(R.layout.screen_list, parent).also { v ->
            list = v.findViewById(R.id.list)
            empty = v.findViewById(R.id.empty)
            v.findViewById<TextView>(R.id.title).setText(R.string.chats_title)
            headerDetail = v.findViewById(R.id.detail)
            list.layoutManager = LinearLayoutManager(v.context)
            list.adapter = adapter
            list.itemAnimator = null
            list.addItemDecoration(PinnedGroupDecoration(v.context) { adapter.currentList.takeWhile { it.pinned }.size })
        }

    override fun onShow() {
        host.setSoftKeys(host.string(R.string.softkey_options), host.string(R.string.softkey_new_chat))
        job?.cancel()
        job = host.scope.launch {
            combine(MatrixRepository.roomList, MatrixRepository.connectionState) { rooms, state -> rooms to state }
                .collectLatest { (rooms, state) -> render(rooms.filterNot { it.archived }, state) }
        }
    }

    override fun onHide() {
        job?.cancel()
        job = null
        focusedRoomId = currentRoom()?.id
    }

    /** The right soft key on the home screen starts a new chat; leaving the app is the Home or red key. */
    override fun onBack(): Boolean {
        host.push(NewChatScreen(host))
        return true
    }

    private fun render(rooms: List<ChatRoom>, state: ChatConnectionState) {
        adapter.submitList(rooms) {
            // Focus is never lost after a list update (SPEC §6).
            if (rooms.isEmpty()) return@submitList
            val target = rooms.indexOfFirst { it.id == focusedRoomId }.takeIf { it >= 0 } ?: 0
            if (list.focusedChild == null) {
                list.post { list.layoutManager?.findViewByPosition(target)?.requestFocus() ?: list.scrollToPosition(target) }
            }
        }
        empty.isVisible = rooms.isEmpty()
        val unread = rooms.count { it.unreadCount > 0 }
        headerDetail.text = when {
            state is ChatConnectionState.Connecting -> host.string(R.string.connecting)
            state is ChatConnectionState.Offline -> host.string(R.string.offline)
            unread > 0 -> host.activity.resources.getQuantityString(R.plurals.chats_unread_count, unread, unread)
            rooms.isNotEmpty() -> host.string(R.string.chats_all_read)
            else -> ""
        }
        empty.text = when {
            rooms.isNotEmpty() -> ""
            state is ChatConnectionState.Connecting -> host.string(R.string.connecting)
            state is ChatConnectionState.Offline -> host.string(R.string.offline)
            else -> host.string(R.string.chats_empty)
        }
    }

    /** The highlighted chat, for the sideways preview. */
    fun currentRoom(): ChatRoom? {
        val child = list.focusedChild ?: return null
        val pos = list.getChildAdapterPosition(child)
        return adapter.currentList.getOrNull(pos)
    }

    override fun onMenu(): Boolean {
        val room = currentRoom()
        val options = mutableListOf<Pair<CharSequence, () -> Unit>>(
            host.string(R.string.settings_title) to { host.push(SettingsScreen(host)) },
        )
        if (room != null) {
            options += host.string(R.string.chats_mark_read) to {
                host.scope.launch { room.lastEventId?.let { MatrixRepository.markRead(room.id, it) } }
            }
            options += host.string(if (room.pinned) R.string.chats_unpin else R.string.chats_pin) to {
                host.scope.launch { MatrixRepository.setRoomPinned(room.id, !room.pinned) }
            }
            options += MuteOptions.forRoom(host, room)
        }
        host.push(OptionsScreen(host, room?.name ?: host.string(R.string.app_name), options))
        return true
    }
}

private class RoomAdapter(private val onOpen: (ChatRoom) -> Unit) :
    ListAdapter<ChatRoom, RoomAdapter.Holder>(Diff) {

    object Diff : DiffUtil.ItemCallback<ChatRoom>() {
        override fun areItemsTheSame(a: ChatRoom, b: ChatRoom) = a.id == b.id
        override fun areContentsTheSame(a: ChatRoom, b: ChatRoom) = a == b
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val avatar: TextView = view.findViewById(R.id.avatar)
        val name: TextView = view.findViewById(R.id.name)
        val preview: TextView = view.findViewById(R.id.preview)
        val time: TextView = view.findViewById(R.id.time)
        val unread: TextView = view.findViewById(R.id.unread)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.row_room, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val room = getItem(position)
        val unread = room.unreadCount > 0
        // Pinned chats carry a small pin; unread chats go bold and show a count pill.
        Avatars.bind(holder.avatar, room.name, room.id)
        holder.name.text = room.name
        holder.preview.text = room.lastMessage
        holder.preview.setTypeface(
            androidx.core.content.res.ResourcesCompat.getFont(holder.itemView.context, if (unread) R.font.inter_semibold else R.font.inter_regular),
        )
        holder.preview.setTextColor(
            if (unread) androidx.core.content.ContextCompat.getColorStateList(holder.itemView.context, R.color.focusable_text)
            else androidx.core.content.ContextCompat.getColorStateList(holder.itemView.context, R.color.focusable_secondary_text),
        )
        holder.time.text = formatTime(room.lastTimestampMs)
        holder.unread.isVisible = unread
        // "@" on the pill when someone has mentioned you in there since you last looked.
        val mentioned = chat.operator.core.matrix.MentionPolicy.hasPendingMention(holder.itemView.context, room.id)
        holder.unread.text = (if (mentioned) "@ " else "") + (if (room.unreadCount > 99) "99+" else room.unreadCount.toString())
        holder.itemView.setOnClickListener { onOpen(room) }
    }

    companion object {
        fun formatTime(ms: Long): String {
            if (ms <= 0) return ""
            val now = System.currentTimeMillis()
            val sameDay = now - ms < 24 * 60 * 60 * 1000L && Date(now).date == Date(ms).date
            return if (sameDay) DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))
            else DateFormat.getDateInstance(DateFormat.SHORT).format(Date(ms))
        }
    }
}


/**
 * The mute choices for a chat, three-way: notify / mute except mentions / mute
 * completely. "Except mentions" is a Matrix mute plus a local rule that lets a
 * message naming you through (SPEC §5.5: decided on the phone).
 */
object MuteOptions {
    fun forRoom(host: ScreenHost, room: ChatRoom): List<Pair<CharSequence, () -> Unit>> {
        val ctx = host.activity
        val mentionsOnly = room.muted && chat.operator.core.matrix.MentionPolicy.isMentionsOnly(ctx, room.id)
        val options = mutableListOf<Pair<CharSequence, () -> Unit>>()
        if (room.muted) {
            options += host.string(R.string.chats_unmute) to {
                chat.operator.core.matrix.MentionPolicy.setMentionsOnly(ctx, room.id, false)
                host.scope.launch { MatrixRepository.setRoomMuted(room.id, false) }
            }
        }
        if (!mentionsOnly) {
            options += host.string(R.string.chats_mentions_only) to {
                chat.operator.core.matrix.MentionPolicy.setMentionsOnly(ctx, room.id, true)
                if (!room.muted) host.scope.launch { MatrixRepository.setRoomMuted(room.id, true) }
            }
        }
        if (!room.muted || mentionsOnly) {
            options += host.string(if (mentionsOnly) R.string.chats_mute_fully else R.string.chats_mute) to {
                chat.operator.core.matrix.MentionPolicy.setMentionsOnly(ctx, room.id, false)
                if (!room.muted) host.scope.launch { MatrixRepository.setRoomMuted(room.id, true) }
            }
        }
        return options
    }

    fun statusLabel(host: ScreenHost, room: ChatRoom): String? = when {
        room.muted && chat.operator.core.matrix.MentionPolicy.isMentionsOnly(host.activity, room.id) -> host.string(R.string.details_mentions_only)
        room.muted -> host.string(R.string.details_muted)
        else -> null
    }
}


/**
 * Draws one rounded surface behind the pinned chats at the top of the list,
 * so they read as a group, with a little air between them and the rest.
 */
private class PinnedGroupDecoration(context: android.content.Context, private val pinnedCount: () -> Int) : RecyclerView.ItemDecoration() {
    private val background = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.pinned_group)!!
    private val inset = (4 * context.resources.displayMetrics.density).toInt()
    private val gap = (8 * context.resources.displayMetrics.density).toInt()
    private val pad = (3 * context.resources.displayMetrics.density).toInt()

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val n = pinnedCount()
        val pos = parent.getChildAdapterPosition(view)
        outRect.set(0, 0, 0, 0)
        if (n == 0 || pos == RecyclerView.NO_POSITION) return
        if (pos == 0) outRect.top = pad
        if (pos == n - 1) outRect.bottom = pad + gap
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val n = pinnedCount()
        if (n == 0) return
        var top = Int.MAX_VALUE
        var bottom = Int.MIN_VALUE
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val pos = parent.getChildAdapterPosition(child)
            if (pos == RecyclerView.NO_POSITION || pos >= n) continue
            top = minOf(top, child.top - (if (pos == 0) pad else 0))
            bottom = maxOf(bottom, child.bottom + (if (pos == n - 1) pad else 0))
        }
        if (top == Int.MAX_VALUE) return
        // If the first pinned row is scrolled off, extend the band upwards; if the last is off, downwards.
        val first = parent.layoutManager?.findViewByPosition(0)
        if (first == null) top = -inset * 4
        val last = parent.layoutManager?.findViewByPosition(n - 1)
        if (last == null) bottom = parent.height + inset * 4
        background.setBounds(inset, top, parent.width - inset, bottom)
        background.draw(c)
    }
}
