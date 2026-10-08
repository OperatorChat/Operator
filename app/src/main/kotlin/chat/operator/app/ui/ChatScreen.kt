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
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import chat.operator.app.R
import chat.operator.core.matrix.ChatMessage
import chat.operator.core.matrix.MatrixRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * One chat (SPEC §5.3, §5.4): plain message list, newest at the bottom, with
 * the compose field underneath. Up/down move message by message; centre on a
 * message opens its options; the field submits with centre or the left soft
 * key; right soft key goes back.
 */
class ChatScreen(host: ScreenHost, private val roomId: String) : Screen(host) {

    private lateinit var title: TextView
    private lateinit var headerDetail: TextView
    private lateinit var list: RecyclerView
    private lateinit var compose: EditText
    private val adapter = MessageAdapter { message -> showMessageOptions(message) }
    private var job: Job? = null
    private var replyTo: ChatMessage? = null
    private var editing: ChatMessage? = null
    private var isDirect = false

    /** People in this chat, fetched the first time an @ is typed or sent. */
    private var members: List<chat.operator.core.matrix.RoomMember>? = null
    private var pickerOpen = false

    /** Older pages loaded on demand (up at the top of the list), oldest first. */
    private val older = mutableListOf<ChatMessage>()
    private var olderCursor: String? = null
    private var hasMore = false
    private var loadingOlder = false

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup): View =
        inflate(R.layout.screen_chat, parent).also { v ->
            title = v.findViewById(R.id.title)
            headerDetail = v.findViewById(R.id.detail)
            list = v.findViewById(R.id.list)
            compose = v.findViewById(R.id.compose)
            list.layoutManager = LinearLayoutManager(v.context).apply { stackFromEnd = true }
            list.adapter = adapter
            list.itemAnimator = null
            compose.imeOptions = EditorInfo.IME_ACTION_SEND
            compose.setOnEditorActionListener { _, _, _ -> send(); true }
            compose.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                    updateSoftKeys()
                    // An @ at the start or after a space (so not part of an e-mail address) offers the chat's members.
                    if (c == 1 && s != null && a < s.length && s[a] == '@' && (a == 0 || s[a - 1].isWhitespace())) offerMentions(a + 1)
                }
                override fun afterTextChanged(s: android.text.Editable?) = Unit
            })
        }

    override fun onShow() {
        updateSoftKeys()
        val room = MatrixRepository.roomList.value.firstOrNull { it.id == roomId }
        title.text = room?.name ?: ""
        headerDetail.text = room?.network?.replaceFirstChar { it.uppercase() } ?: ""
        isDirect = room?.isDirect == true
        adapter.showSenders = !isDirect
        adapter.myId = MatrixRepository.currentUserId()
        if (!isDirect && members == null) host.scope.launch { loadMembers() }
        if (adapter.myName == null) host.scope.launch { adapter.myName = runCatching { MatrixRepository.myDisplayName(roomId) }.getOrNull() }
        MatrixRepository.setActiveRoom(roomId)
        host.activity.pendingPhotoUri?.let { uri ->
            host.activity.pendingPhotoUri = null
            if (host.activity.pendingPhotoRoom == roomId || host.activity.pendingPhotoRoom == null) { host.activity.pendingPhotoRoom = null; sendPhoto(uri) }
        }
        if (adapter.itemCount > 0) {
            // Coming back from a photo: the rows' data is unchanged, but a thumbnail may now exist.
            adapter.notifyItemRangeChanged(0, adapter.itemCount)
        } else {
            compose.requestFocus()
        }
        job?.cancel()
        job = host.scope.launch {
            var revision = -1L
            while (isActive) {
                val page = MatrixRepository.getMessages(roomId, null, PAGE_SIZE)
                if (older.isEmpty() && olderCursor == null) {
                    olderCursor = page.nextBeforeEventId
                    hasMore = page.hasMore
                }
                val wasAtBottom = !list.canScrollVertically(1)
                val merged = merged(page.messages)
                adapter.submitList(merged) {
                    if (wasAtBottom && merged.isNotEmpty()) list.scrollToPosition(merged.size - 1)
                }
                page.messages.lastOrNull()?.let { newest ->
                    if (!newest.isMine) MatrixRepository.markRead(roomId, newest.id)
                }
                revision = MatrixRepository.waitForChange("page", roomId, revision, WAIT_MS)
            }
        }
    }

    override fun onHide() {
        job?.cancel()
        job = null
        MatrixRepository.setActiveRoom(null)
    }

    private fun merged(latest: List<ChatMessage>): List<ChatMessage> {
        val latestIds = latest.map { it.id }.toSet()
        return older.filter { it.id !in latestIds } + latest
    }

    /** Up on the first row: fetch the previous page and prepend it; focus stays where it was. */
    override fun onKey(keyCode: Int): Boolean {
        if (keyCode != android.view.KeyEvent.KEYCODE_DPAD_UP) return false
        val focused = list.focusedChild ?: return false
        if (list.getChildAdapterPosition(focused) != 0 || !hasMore || loadingOlder) return false
        val cursor = olderCursor ?: return false
        loadingOlder = true
        host.scope.launch {
            val page = runCatching { MatrixRepository.getMessages(roomId, cursor, PAGE_SIZE) }.getOrNull()
            if (page != null) {
                older.addAll(0, page.messages.filter { m -> older.none { it.id == m.id } })
                olderCursor = page.nextBeforeEventId
                hasMore = page.hasMore && page.messages.isNotEmpty()
                adapter.submitList(merged(adapter.currentList.filter { c -> older.none { it.id == c.id } }))
            }
            loadingOlder = false
        }
        return true
    }

    private fun updateSoftKeys() {
        val left = when {
            editing != null -> R.string.softkey_save
            compose.text.isNotBlank() -> R.string.softkey_send
            else -> R.string.softkey_options
        }
        host.setSoftKeys(host.string(left), host.string(if (replyTo != null || editing != null) R.string.softkey_cancel else R.string.softkey_back))
    }

    override fun onMenu(): Boolean {
        if (compose.text.isNotBlank()) send() else showChatOptions()
        return true
    }

    override fun onBack(): Boolean {
        if (replyTo != null || editing != null) {
            replyTo = null
            editing = null
            compose.setText("")
            find<TextView>(R.id.reply_quote).isVisible = false
            updateSoftKeys()
            return true
        }
        return false
    }

    /** Green key: jump straight to the compose field (SPEC §6). */
    override fun onCall(): Boolean {
        compose.requestFocus()
        return true
    }

    private fun send() {
        val body = compose.text.toString().trim()
        if (body.isEmpty()) return
        editing?.let { target ->
            editing = null
            compose.setText("")
            find<TextView>(R.id.reply_quote).isVisible = false
            updateSoftKeys()
            host.scope.launch {
                runCatching { MatrixRepository.editMessage(roomId, target.id, body) }
                    .onFailure { android.widget.Toast.makeText(host.activity, R.string.chat_edit_failed, android.widget.Toast.LENGTH_LONG).show() }
            }
            return
        }
        val reply = replyTo
        compose.setText("")
        replyTo = null
        find<TextView>(R.id.reply_quote).isVisible = false
        updateSoftKeys()
        host.scope.launch {
            // "@Name" in the text becomes a real mention when Name is in this chat.
            val mentions = if (body.contains('@')) chat.operator.core.matrix.MentionLogic.mentionedMembers(body, loadMembers()) else emptyMap()
            runCatching { MatrixRepository.sendMessage(roomId, body, reply?.id, mentions) }
                .onFailure {
                    compose.setText(body)
                    compose.setSelection(body.length)
                    android.widget.Toast.makeText(host.activity, R.string.chat_send_failed, android.widget.Toast.LENGTH_LONG).show()
                }
        }
    }

    /** Who a message mentions (other than me): pills first, then "@Name" tokens matched to the chat's members. */
    private fun mentionedPeople(message: ChatMessage): List<Pair<String, String>> {
        val me = MatrixRepository.currentUserId()
        val people = linkedMapOf<String, String>()
        chat.operator.core.matrix.MentionLogic.pills(message.formattedHtml).forEach { (id, name) -> if (id != me && name.isNotBlank()) people[id] = name }
        members?.let { known ->
            chat.operator.core.matrix.MentionLogic.mentionedMembers(message.body, known).forEach { (name, id) -> if (id != me) people.putIfAbsent(id, name) }
        }
        return people.toList()
    }

    /** An existing direct chat with this person: by their user id, else by name on the same network. */
    private fun directChatWith(userId: String, name: String): chat.operator.core.matrix.ChatRoom? {
        val rooms = MatrixRepository.roomList.value.filter { it.isDirect }
        rooms.firstOrNull { it.contactId == userId }?.let { return it }
        val network = MatrixRepository.roomList.value.firstOrNull { it.id == roomId }?.network
        return rooms.firstOrNull { it.name.equals(name, ignoreCase = true) && (network == null || it.network == network) }
    }

    private suspend fun loadMembers(): List<chat.operator.core.matrix.RoomMember> =
        members ?: runCatching { MatrixRepository.roomMembers(roomId) }.getOrDefault(emptyList()).also { members = it }

    /** A sheet of the chat's members; choosing one writes "Name " at [insertAt] in the field. */
    private fun offerMentions(insertAt: Int) {
        if (pickerOpen) return
        pickerOpen = true
        host.scope.launch {
            val people = loadMembers()
            pickerOpen = false
            if (people.isEmpty()) {
                android.widget.Toast.makeText(host.activity, R.string.chat_mention_nobody, android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }
            host.push(
                OptionsScreen(
                    host, host.string(R.string.chat_mention_pick),
                    people.map { m ->
                        (m.name as CharSequence) to {
                            val text = compose.text
                            val at = insertAt.coerceIn(0, text.length)
                            text.insert(at, m.name + " ")
                            compose.requestFocus()
                            compose.setSelection((at + m.name.length + 1).coerceAtMost(compose.text.length))
                        }
                    },
                ),
            )
        }
    }

    private fun onPhotoResult(uri: android.net.Uri?) {
        host.activity.pendingPhotoRoom = null
        if (uri == null) {
            android.widget.Toast.makeText(host.activity, R.string.chat_photo_none, android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        sendPhoto(uri)
    }

    private fun sendPhoto(uri: android.net.Uri) {
        android.widget.Toast.makeText(host.activity, R.string.chat_photo_sending, android.widget.Toast.LENGTH_SHORT).show()
        host.scope.launch {
            val ok = PhotoSender.send(host.activity, roomId, uri)
            if (!ok) android.widget.Toast.makeText(host.activity, R.string.chat_photo_send_failed, android.widget.Toast.LENGTH_LONG).show()
            // A captured photo lives in our cache; drop it once sent.
            if (uri.authority == "${host.activity.packageName}.files") runCatching { host.activity.contentResolver.delete(uri, null, null) }
        }
    }

    private fun showChatOptions() {
        val room = MatrixRepository.roomList.value.firstOrNull { it.id == roomId }
        val options = mutableListOf<Pair<CharSequence, () -> Unit>>(
            host.string(R.string.chats_mark_read) to {
                adapter.currentList.lastOrNull()?.let { host.scope.launch { MatrixRepository.markRead(roomId, it.id) } }
            },
            host.string(R.string.chat_jump_newest) to {
                if (adapter.itemCount > 0) list.scrollToPosition(adapter.itemCount - 1)
                compose.requestFocus()
            },
            host.string(R.string.chat_send_photo) to {
                host.activity.pendingPhotoRoom = roomId
                host.activity.pickPhoto { uri -> onPhotoResult(uri) }
            },
            host.string(R.string.chat_take_photo) to {
                host.activity.pendingPhotoRoom = roomId
                host.activity.takePhoto { uri -> onPhotoResult(uri) }
            },
        )
        if (!isDirect) {
            options += host.string(R.string.chat_mention_someone) to {
                val text = compose.text
                val at = compose.selectionEnd.coerceIn(0, text.length)
                val prefix = if (at > 0 && !text[at - 1].isWhitespace()) " @" else "@"
                text.insert(at, prefix)
                compose.requestFocus()
                compose.setSelection(at + prefix.length)
                // The watcher opens the picker for the @ just inserted.
            }
        }
        if (room != null) {
            options += MuteOptions.forRoom(host, room)
            options += host.string(if (room.pinned) R.string.chats_unpin else R.string.chats_pin) to {
                host.scope.launch { MatrixRepository.setRoomPinned(room.id, !room.pinned) }
            }
            options += host.string(if (room.archived) R.string.chat_unarchive else R.string.chat_archive) to {
                host.scope.launch { MatrixRepository.setRoomArchived(room.id, !room.archived) }
                if (!room.archived) host.pop()
            }
            options += host.string(R.string.chat_details) to { host.push(ChatDetailsScreen(host, room)) }
        }
        host.push(OptionsScreen(host, title.text, options))
    }

    private fun showMessageOptions(message: ChatMessage) {
        val reactions = listOf("👍", "❤️", "😂", "😮", "😢", "💯", "🤜🤛", "😶", "😐", "🙃", "🖖", "👎", "🤩", "😍", "🤬")
        val options = mutableListOf<Pair<CharSequence, () -> Unit>>()
        val details = mutableMapOf<Int, CharSequence>()
        // Links come first, one row each, with the address underneath; nothing opens until chosen.
        if (message.contentType == "text") {
            Links.find(message.body, message.formattedHtml).forEach { url ->
                details[options.size] = Links.label(url)
                options += (host.string(R.string.chat_open_link) as CharSequence) to { Links.open(host.activity, url) }
            }
        }
        // People mentioned in the message: one row each, opening (or finding) a direct chat with them.
        if (message.contentType == "text") {
            mentionedPeople(message).forEach { (userId, name) ->
                val existing = directChatWith(userId, name)
                val network = existing?.network
                details[options.size] = when {
                    existing == null -> host.string(R.string.chat_message_person_new)
                    network != null -> host.string(R.string.chat_message_person_detail, network.replaceFirstChar { c -> c.uppercase() })
                    else -> host.string(R.string.chat_message_person_open)
                }
                options += (host.string(R.string.chat_message_person, name) as CharSequence) to {
                    if (existing != null) host.push(ChatScreen(host, existing.id)) else host.push(NewChatScreen(host, name))
                }
            }
        }
        // Looking comes first: "View photo" for a photo, "Open file" for anything else; then saving.
        if (message.contentType == "image") {
            options += (host.string(R.string.chat_view_photo) as CharSequence) to { host.push(PhotoScreen(host, roomId, message.id)) }
        } else if (Attachments.isFile(message) || message.contentType == "video") {
            options += (host.string(R.string.chat_open_file) as CharSequence) to { Attachments.open(host.activity, roomId, message.id) }
        }
        if (Attachments.isFile(message) || message.contentType == "video" || message.contentType == "image") {
            options += (host.string(R.string.chat_save_file) as CharSequence) to { Attachments.save(host.activity, roomId, message.id) }
        }
        when (message.contentType) {
            "audio" -> options += (host.string(
                if (MatrixRepository.audioPlayingEventId() == message.id) R.string.chat_pause_voice else R.string.chat_play_voice,
            ) as CharSequence) to {
                host.scope.launch {
                    val (_, error) = MatrixRepository.playVoiceNote(roomId, message.id)
                    if (error != null) android.widget.Toast.makeText(host.activity, R.string.chat_voice_failed, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
        options += (host.string(R.string.chat_reply) as CharSequence) to {
                replyTo = message
                find<TextView>(R.id.reply_quote).apply {
                    text = host.string(R.string.chat_replying_to, message.body.lineSequence().firstOrNull().orEmpty().take(60))
                    isVisible = true
                }
                compose.requestFocus()
                updateSoftKeys()
        }
        options += (host.string(R.string.chat_copy) as CharSequence) to {
            val clipboard = host.activity.getSystemService(android.content.ClipboardManager::class.java)
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("", message.body))
        }
        // Reactions sit in a grid at this point in the list; one you have already sent is ringed and selecting it takes it back.
        fun plain(k: String) = k.replace("\uFE0F", "")
        val mine = message.reactions.filter { it.startsWith("You reacted ") }.map { plain(it.removePrefix("You reacted ").trim()) }
        val gridAt = options.size
        val cells = mutableListOf<Pair<CharSequence, () -> Unit>>()
        val alreadySent = mutableSetOf<Int>()
        reactions.forEachIndexed { index, emoji ->
            val already = plain(emoji) in mine
            if (already) alreadySent += index
            cells += (emoji as CharSequence) to {
                host.scope.launch {
                    runCatching {
                        if (already) {
                            // Nothing found to remove (it may have been removed from another device): say so, never re-send.
                            if (!MatrixRepository.unsendReaction(roomId, message.id, emoji)) error("no reaction to remove")
                        } else {
                            MatrixRepository.sendReaction(roomId, message.id, emoji)
                        }
                    }.onFailure { android.widget.Toast.makeText(host.activity, R.string.chat_react_failed, android.widget.Toast.LENGTH_SHORT).show() }
                }
            }
        }
        if (message.isMine && message.canEdit && message.contentType == "text") {
            options += (host.string(R.string.chat_edit) as CharSequence) to {
                editing = message
                replyTo = null
                compose.setText(message.body)
                compose.setSelection(message.body.length)
                find<TextView>(R.id.reply_quote).apply {
                    text = host.string(R.string.chat_editing)
                    isVisible = true
                }
                compose.requestFocus()
                updateSoftKeys()
            }
        }
        if (message.isMine && message.canUnsend && message.contentType != "redacted") {
            // Always "delete for everyone": a redaction, which the bridge passes on.
            options += (host.string(R.string.chat_delete) as CharSequence) to {
                host.push(
                    OptionsScreen(
                        host, host.string(R.string.chat_delete_confirm),
                        listOf<Pair<CharSequence, () -> Unit>>(
                            host.string(android.R.string.cancel) to {},
                            host.string(R.string.chat_delete) to {
                                host.scope.launch { runCatching { MatrixRepository.unsendMessage(roomId, message.id) } }
                            },
                        ),
                    ),
                )
            }
        }
        host.push(OptionsScreen(host, message.body.lineSequence().firstOrNull().orEmpty().take(40), options, details, OptionsScreen.GridChoices(gridAt, cells, alreadySent)))
    }

    companion object {
        private const val PAGE_SIZE = 30
        private const val WAIT_MS = 30_000L
    }
}

private class MessageAdapter(private val onSelect: (ChatMessage) -> Unit) :
    ListAdapter<ChatMessage, MessageAdapter.Holder>(Diff) {

    var showSenders = true
    var myId: String? = null
    var myName: String? = null

    object Diff : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(a: ChatMessage, b: ChatMessage) = a.id == b.id
        override fun areContentsTheSame(a: ChatMessage, b: ChatMessage) = a == b
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val bubble: android.widget.LinearLayout = view.findViewById(R.id.bubble)
        val sender: TextView = view.findViewById(R.id.sender)
        val thumb: android.widget.ImageView = view.findViewById(R.id.thumb)
        val body: TextView = view.findViewById(R.id.body)
        val meta: TextView = view.findViewById(R.id.meta)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.row_message, parent, false)).also { h ->
            // Photos sit inside the bubble with rounded corners.
            h.thumb.clipToOutline = true
            h.thumb.outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    val r = view.resources.getDimension(R.dimen.bubble_radius) - 5f
                    outline.setRoundRect(0, 0, view.width, view.height, r)
                }
            }
        }

    /** Mentioned names in semibold and the accent (or the on-bubble colour on my own messages). */
    private fun highlightMentions(text: CharSequence, m: ChatMessage, colour: Int): CharSequence {
        if (m.contentType != "text") return text
        val ranges = chat.operator.core.matrix.MentionLogic.highlightRanges(text.toString(), m.formattedHtml)
        if (ranges.isEmpty()) return text
        val s = if (text is android.text.Spannable) text else android.text.SpannableString(text)
        ranges.forEach { r ->
            if (r.last < s.length) {
                s.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), r.first, r.last + 1, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                s.setSpan(android.text.style.ForegroundColorSpan(colour), r.first, r.last + 1, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return s
    }

    private fun themeColour(ctx: android.content.Context, attr: Int): Int {
        val tv = android.util.TypedValue()
        ctx.theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val m = getItem(position)
        val ctx = holder.itemView.context
        // Mine on the right, theirs on the left (SPEC §5.3 wants it unmistakable).
        (holder.bubble.layoutParams as android.widget.FrameLayout.LayoutParams).gravity =
            if (m.isMine) android.view.Gravity.END else android.view.Gravity.START
        // My bubble's colour follows the status mark: yellow sending, blue sent,
        // green read (by anyone, in a group), red failed. Never the only signal.
        val sendStatus = m.sendStatus
        val state = when {
            !m.isMine -> null
            sendStatus == "PENDING" || m.id.startsWith("local-") -> "sending"
            sendStatus != null && sendStatus.startsWith("FAIL") -> "failed"
            m.read -> "read"
            else -> "sent"
        }
        holder.bubble.setBackgroundResource(
            when (state) {
                "sending" -> R.drawable.bubble_mine_sending
                "failed" -> R.drawable.bubble_mine_failed
                "read" -> R.drawable.bubble_mine_read
                "sent" -> R.drawable.bubble_mine_sent
                else -> R.drawable.bubble_theirs
            },
        )
        // Theirs: text in the theme's ink, sender named in their avatar colour.
        // Mine: the bubble is coloured, so the text uses the on-bubble colour.
        val mineText = themeColour(ctx, R.attr.opBubbleMineText)
        val ink = themeColour(ctx, R.attr.opText)
        val inkSecondary = themeColour(ctx, R.attr.opTextSecondary)
        holder.body.setTextColor(if (m.isMine) mineText else ink)
        holder.meta.setTextColor(if (m.isMine) androidx.core.graphics.ColorUtils.setAlphaComponent(mineText, 190) else inkSecondary)
        holder.sender.isVisible = showSenders && !m.isMine && m.senderName.isNotBlank()
        holder.sender.text = m.senderName
        holder.sender.setTextColor(Avatars.colour(ctx, m.sender))
        // A photo the user has already loaded shows as a thumbnail; an unloaded
        // one shows a polaroid with "Select to view photo" (nothing is fetched until asked).
        holder.thumb.isVisible = false
        holder.thumb.setImageDrawable(null)
        holder.thumb.tag = m.id
        var photoLoaded = false
        if (m.contentType == "image") {
            val bitmap = if (ThumbnailCache.has(ctx, m.id)) ThumbnailCache.get(ctx, m.id) else null
            if (bitmap != null) {
                holder.thumb.setImageBitmap(bitmap)
                photoLoaded = true
            } else {
                holder.thumb.setImageResource(R.drawable.photo_placeholder)
            }
            holder.thumb.isVisible = true
        }
        holder.body.setTypeface(null, if (m.contentType == "image" && !photoLoaded) android.graphics.Typeface.ITALIC else android.graphics.Typeface.NORMAL)
        holder.body.text = when (m.contentType) {
            "image" -> (if (photoLoaded) m.caption.orEmpty() else ctx.getString(R.string.chat_photo_select) + (m.caption?.let { "\n$it" } ?: ""))
            "audio" -> ctx.getString(R.string.chat_voice_placeholder) +
                (m.durationMs?.let { " · " + String.format("%d:%02d", it / 60000, (it / 1000) % 60) } ?: "") +
                (if (MatrixRepository.audioPlayingEventId() == m.id) " ▶" else "")
            "redacted" -> ctx.getString(R.string.chat_message_unsent)
            "file" -> m.body
            else -> if (m.body == "[File]") ctx.getString(R.string.chat_file_placeholder) else if (m.body.isBlank()) ctx.getString(R.string.chat_cannot_open_yet) else
                highlightMentions(Links.highlight(m.body, if (m.isMine) mineText else themeColour(ctx, R.attr.opAccent)), m, if (m.isMine) mineText else themeColour(ctx, R.attr.opAccent))
        }
        val mentionsMe = !m.isMine && m.contentType == "text" &&
            chat.operator.core.matrix.MentionLogic.mentionsMe(m.body, m.formattedHtml, null, myId, myName)
        holder.body.isVisible = holder.body.text.isNotEmpty()
        // Status mark on my messages: sending … / sent ✓ / read ✓✓ / failed !
        val mark = when (state) {
            "sending" -> " " + ctx.getString(R.string.chat_mark_sending)
            "failed" -> " " + ctx.getString(R.string.chat_mark_failed) + " " + ctx.getString(R.string.chat_status_failed)
            "read" -> " " + ctx.getString(R.string.chat_mark_read)
            "sent" -> " " + ctx.getString(R.string.chat_mark_sent)
            else -> ""
        }
        val extras = buildString {
            if (mentionsMe) append(" · ").append(ctx.getString(R.string.chat_mentioned_you))
            if (m.edited) append(" · ").append(ctx.getString(R.string.chat_edited))
            if (m.reactions.isNotEmpty()) append(" · ").append(m.reactions.joinToString(" "))
        }
        holder.meta.text = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(m.timestampMs)) + mark + extras
        holder.itemView.setOnClickListener { onSelect(m) }
    }
}

/** Plain facts about a chat: name, network, kind, and the room id for support. */
class ChatDetailsScreen(host: ScreenHost, private val room: chat.operator.core.matrix.ChatRoom) : MenuScreen(host) {
    override val titleRes = R.string.chat_details

    override fun items(): List<MenuItem> = listOfNotNull(
        MenuItem(host.string(R.string.details_name), room.name),
        room.network?.let { MenuItem(host.string(R.string.details_network), it.replaceFirstChar { c -> c.uppercase() }) },
        MenuItem(host.string(R.string.details_kind), host.string(if (room.isDirect) R.string.details_direct else R.string.details_group)),
        MenuItem(
            host.string(R.string.details_status),
            listOfNotNull(
                if (room.pinned) host.string(R.string.details_pinned) else null,
                MuteOptions.statusLabel(host, room),
                if (room.archived) host.string(R.string.details_archived) else null,
            ).joinToString(", ").ifEmpty { host.string(R.string.details_none) },
        ),
        MenuItem(host.string(R.string.details_room_id), room.id),
    )
}
