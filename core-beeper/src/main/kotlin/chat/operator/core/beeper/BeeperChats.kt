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
package chat.operator.core.beeper

import android.util.Log
import chat.operator.core.matrix.MatrixRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLPathPart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Starting a new chat through Beeper's bridges. Each bridge's provisioning
 * API lists the user's contacts on that network and can open (or create) the
 * direct chat with one of them. Same endpoints Chats uses for contact names
 * (`/_matrix/provision/v3/contacts`, `/resolve_identifier`) plus
 * `/create_chat`. Beeper-only; `network` is the bridge id ("whatsapp", "signal", …).
 */
object BeeperChats {
    private const val TAG = "BeeperChats"
    private const val PER_BRIDGE_TIMEOUT_MS = 8_000L

    data class Contact(
        val network: String,
        val id: String,
        val name: String,
        val identifiers: List<String>,
        val dmRoomId: String?,
    )

    private val json = Json { ignoreUnknownKeys = true }

    private fun bridgeBase(network: String): String? {
        val hs = MatrixRepository.homeserverUrl()?.trimEnd('/') ?: return null
        return "$hs/_matrix/client/unstable/com.beeper.bridge/$network/_matrix/provision/v3"
    }

    /** Outcome of a contact-list fetch across the bridges. */
    data class ContactsResult(val contacts: List<Contact>, val refused: Boolean)

    /** Set when any bridge answers 401/403 or a client-gating 400, i.e. Beeper no longer serves this app. */
    @Volatile private var refusedSeen = false

    /** All contacts across the user's bridges, sorted by name. Bridges without a list (404) are skipped. */
    suspend fun contacts(): ContactsResult = withContext(Dispatchers.IO) {
        val token = MatrixRepository.currentAccessToken() ?: return@withContext ContactsResult(emptyList(), false)
        val userId = MatrixRepository.currentUserId() ?: return@withContext ContactsResult(emptyList(), false)
        refusedSeen = false
        val http = HttpClient()
        try {
            val all = coroutineScope {
                MatrixRepository.BRIDGE_KEYS.map { network ->
                    async {
                        withTimeoutOrNull(PER_BRIDGE_TIMEOUT_MS) { fetchContacts(http, token, userId, network) } ?: emptyList()
                    }
                }.awaitAll().flatten()
            }.sortedWith(compareBy<Contact> { it.name.firstOrNull()?.isLetter() != true }.thenBy { it.name.lowercase() })
            ContactsResult(all, refusedSeen && all.isEmpty())
        } finally {
            http.close()
        }
    }

    private suspend fun fetchContacts(http: HttpClient, token: String, userId: String, network: String): List<Contact> {
        val base = bridgeBase(network) ?: return emptyList()
        return runCatching {
            val resp = http.get("$base/contacts") {
                parameter("user_id", userId)
                header("Authorization", "Bearer $token")
            }
            if (resp.status.value !in 200..299) {
                if (resp.status.value in setOf(400, 401, 403)) refusedSeen = true
                return emptyList()
            }
            val body = json.parseToJsonElement(resp.bodyAsText()).jsonObject
            body["contacts"]?.jsonArray.orEmpty().mapNotNull { el ->
                val o = el.jsonObject
                val id = o["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val name = o["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                Contact(
                    network = network,
                    id = id,
                    name = name,
                    identifiers = o["identifiers"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty(),
                    dmRoomId = o["dm_room_mxid"]?.jsonPrimitive?.contentOrNull,
                )
            }
        }.onFailure { Log.w(TAG, "contacts for $network failed: ${it.message}") }.getOrDefault(emptyList())
    }

    /** Opens the direct chat with [contact], creating it on the bridge if needed; returns the room id. */
    suspend fun openChat(contact: Contact): String? = withContext(Dispatchers.IO) {
        contact.dmRoomId?.let { return@withContext it }
        val token = MatrixRepository.currentAccessToken() ?: return@withContext null
        val userId = MatrixRepository.currentUserId() ?: return@withContext null
        val base = bridgeBase(contact.network) ?: return@withContext null
        val http = HttpClient()
        try {
            // Ask again first: the chat may exist by now.
            runCatching {
                val resp = http.get("$base/resolve_identifier/${contact.id.encodeURLPathPart()}") {
                    parameter("user_id", userId)
                    header("Authorization", "Bearer $token")
                }
                if (resp.status.value in 200..299) roomIdFrom(resp.bodyAsText()) else null
            }.getOrNull()?.let { return@withContext it }
            val resp = http.post("$base/create_chat/${contact.id.encodeURLPathPart()}") {
                parameter("user_id", userId)
                header("Authorization", "Bearer $token")
            }
            if (resp.status.value !in 200..299) {
                Log.w(TAG, "create_chat on ${contact.network} failed: HTTP ${resp.status.value}")
                return@withContext null
            }
            roomIdFrom(resp.bodyAsText())
        } catch (e: Exception) {
            Log.w(TAG, "create_chat on ${contact.network} failed: ${e.message}")
            null
        } finally {
            http.close()
        }
    }

    private fun roomIdFrom(body: String): String? {
        val o = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        return listOf("dm_room_mxid", "room_id", "roomId", "mxid").firstNotNullOfOrNull { key ->
            o[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.startsWith("!") }
        }
    }
}
