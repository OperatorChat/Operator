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
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import chat.operator.app.R
import chat.operator.core.beeper.BeeperChats
import chat.operator.core.matrix.MatrixRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Start a chat: type part of a name, pick the person, and the bridge opens
 * (or creates) the direct chat. Contacts come from the networks connected in
 * Beeper, with the network shown under each name.
 */
class NewChatScreen(host: ScreenHost, private val initialQuery: String? = null) : Screen(host) {

    private lateinit var search: EditText
    private lateinit var status: TextView
    private lateinit var results: LinearLayout
    private var contacts: List<BeeperChats.Contact> = emptyList()
    private var contactsRefused = false
    private var loaded = false
    private var job: Job? = null

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup): View =
        inflate(R.layout.screen_new_chat, parent).also { v ->
            v.findViewById<TextView>(R.id.title).setText(R.string.new_chat_title)
            search = v.findViewById(R.id.search)
            status = v.findViewById(R.id.status)
            results = v.findViewById(R.id.results)
            search.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = render()
                override fun afterTextChanged(s: android.text.Editable?) = Unit
            })
        }

    override fun onShow() {
        host.setSoftKeys(null, host.string(R.string.softkey_back))
        search.requestFocus()
        if (loaded) return
        if (!initialQuery.isNullOrBlank() && search.text.isEmpty()) {
            search.setText(initialQuery)
            search.setSelection(initialQuery.length)
        }
        status.text = host.string(R.string.new_chat_loading)
        status.isVisible = true
        render() // existing chats are available at once; contacts arrive when the bridges answer
        job = host.scope.launch {
            val result = runCatching { BeeperChats.contacts() }.getOrNull()
            contacts = result?.contacts.orEmpty()
            contactsRefused = result?.refused ?: true
            loaded = true
            render()
        }
    }

    override fun onHide() {
        job?.cancel()
    }

    private fun render() {
        val query = search.text.toString().trim().lowercase()
        val inflater = LayoutInflater.from(results.context)
        results.removeAllViews()
        // A full Matrix id (@name:server) starts a direct chat on any server, Beeper or not.
        val typedId = search.text.toString().trim()
        if (Regex("""^@[^:\s]+:[^\s]+\.[^\s]+$""").matches(typedId)) {
            val row = inflater.inflate(R.layout.row_menu, results, false)
            row.findViewById<TextView>(R.id.title).text = host.string(R.string.new_chat_start_with, typedId)
            row.findViewById<TextView>(R.id.detail).text = host.string(R.string.new_chat_start_with_detail)
            row.setOnClickListener { startDirect(typedId) }
            results.addView(row)
        }
        // 1. Chats you already have (no API involved), including archived ones.
        val rooms = MatrixRepository.roomList.value
            .filter { query.isNotEmpty() && it.name.lowercase().contains(query) }
            .take(MAX_ROWS)
        rooms.forEach { room ->
            val row = inflater.inflate(R.layout.row_menu, results, false)
            row.findViewById<TextView>(R.id.title).text = room.name
            row.findViewById<TextView>(R.id.detail).text = host.string(R.string.new_chat_existing, room.network?.let { " · " + networkLabel(it) } ?: "")
            row.setOnClickListener { host.pop(); host.push(ChatScreen(host, room.id)) }
            results.addView(row)
        }
        // 2. People from the bridges' contact lists who don't match an existing chat.
        val roomNames = rooms.map { it.name.lowercase() }.toSet()
        val matches = contacts.filter { c ->
            (query.isEmpty() || c.name.lowercase().contains(query) || c.identifiers.any { it.lowercase().contains(query) }) &&
                c.name.lowercase() !in roomNames
        }.take(MAX_ROWS)
        matches.forEach { contact ->
            val row = inflater.inflate(R.layout.row_menu, results, false)
            row.findViewById<TextView>(R.id.title).text = contact.name
            row.findViewById<TextView>(R.id.detail).text = networkLabel(contact.network)
            row.setOnClickListener { open(contact) }
            results.addView(row)
        }
        val beeper = MatrixRepository.isBeeper()
        status.isVisible = results.childCount == 0 || !loaded || (contactsRefused && beeper)
        status.text = when {
            !beeper && results.childCount == 0 -> host.string(R.string.new_chat_matrix_hint)
            !beeper -> ""
            !loaded && results.childCount == 0 -> host.string(R.string.new_chat_loading)
            !loaded -> host.string(R.string.new_chat_loading_more)
            contactsRefused -> host.string(R.string.new_chat_refused)
            contacts.isEmpty() && results.childCount == 0 -> host.string(R.string.new_chat_none)
            results.childCount == 0 -> host.string(R.string.new_chat_no_match)
            else -> ""
        }
    }

    private fun startDirect(userId: String) {
        status.text = host.string(R.string.new_chat_opening); status.isVisible = true
        host.scope.launch {
            runCatching { MatrixRepository.createDirectChat(userId) }
                .onSuccess { roomId -> host.pop(); host.push(ChatScreen(host, roomId)) }
                .onFailure { e ->
                    status.text = host.string(R.string.new_chat_start_failed, e.message?.take(120) ?: e.javaClass.simpleName)
                    status.isVisible = true
                }
        }
    }

    private fun open(contact: BeeperChats.Contact) {
        status.text = host.string(R.string.new_chat_opening)
        status.isVisible = true
        host.scope.launch {
            val roomId = BeeperChats.openChat(contact)
            if (roomId == null) {
                status.text = host.string(R.string.new_chat_create_failed)
                status.isVisible = true
                return@launch
            }
            // Make sure the room is in the list before showing it, then swap this screen for the chat.
            runCatching { MatrixRepository.getRooms() }
            host.pop()
            host.push(ChatScreen(host, roomId))
        }
    }

    private fun networkLabel(network: String): String = when (network) {
        "whatsapp" -> "WhatsApp"
        "signal" -> "Signal"
        "telegram" -> "Telegram"
        "instagramgo" -> "Instagram"
        "facebookgo" -> "Messenger"
        "imessagego" -> "iMessage"
        "gmessages" -> "Google Messages"
        "googlechat" -> "Google Chat"
        "discordgo" -> "Discord"
        "slackgo" -> "Slack"
        "twitter" -> "X"
        "linkedin" -> "LinkedIn"
        "line" -> "LINE"
        "tumblrdms" -> "Tumblr"
        else -> network
    }

    companion object {
        private const val MAX_ROWS = 40
    }
}
