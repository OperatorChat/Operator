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
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Beeper keeps bridged chats (WhatsApp, Signal, …) on a second, per-user
 * server ("hungryserv") beside the main homeserver. Pushers registered with
 * the main server only cover its own rooms; hungryserv pushes for the
 * bridged rooms, and only to pushers registered with it directly.
 */
object BeeperPush {
    private const val TAG = "BeeperPush"

    /** hungryserv's client API base for [userId] (`@local:beeper.com`). */
    fun hungryservUrl(homeserver: String, userId: String): String {
        val local = userId.removePrefix("@").substringBefore(':')
        return homeserver.trimEnd('/') + "/_hungryserv/" + local
    }

    /** Lists hungryserv's pushers for this account, as a short report line. */
    suspend fun listPushers(homeserver: String, userId: String, accessToken: String): String {
        val http = HttpClient()
        return try {
            val resp = http.get(hungryservUrl(homeserver, userId) + "/_matrix/client/v3/pushers") {
                header("Authorization", "Bearer $accessToken")
            }
            val body = resp.bodyAsText()
            "hungryserv pushers: HTTP ${resp.status.value} " + body.take(600).replace(Regex("\"pushkey\":\"[^\"]*(.{6})\""), "\"pushkey\":\"…$1\"")
        } catch (e: Exception) {
            "hungryserv pushers: failed (${e.message?.take(120)})"
        } finally {
            http.close()
        }
    }

    /** hungryserv's own view of the push rules, raw (ids/actions only; room ids shortened). */
    suspend fun pushRules(homeserver: String, userId: String, accessToken: String): String {
        val http = HttpClient()
        return try {
            val resp = http.get(hungryservUrl(homeserver, userId) + "/_matrix/client/v3/pushrules/") {
                header("Authorization", "Bearer $accessToken")
            }
            val body = resp.bodyAsText().replace(Regex("![A-Za-z0-9]{6}[A-Za-z0-9]*:"), "!…:")
            "hungryserv pushrules: HTTP ${resp.status.value} " + body.take(4000)
        } catch (e: Exception) {
            "hungryserv pushrules: failed (${e.message?.take(120)})"
        } finally {
            http.close()
        }
    }

    /** Registers (idempotently) the same http pusher with hungryserv. */
    suspend fun registerPusher(
        homeserver: String,
        userId: String,
        accessToken: String,
        appId: String,
        pushkey: String,
        gatewayUrl: String,
        appName: String,
        deviceName: String,
    ): String {
        val http = HttpClient()
        return try {
            val body = buildJsonObject {
                put("app_id", appId)
                put("pushkey", pushkey)
                put("kind", "http")
                put("app_display_name", appName)
                put("device_display_name", deviceName)
                put("lang", "en")
                put("append", false)
                putJsonObject("data") {
                    put("format", "event_id_only")
                    put("url", gatewayUrl)
                }
            }
            val resp = http.post(hungryservUrl(homeserver, userId) + "/_matrix/client/v3/pushers/set") {
                header("Authorization", "Bearer $accessToken")
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
            val text = resp.bodyAsText().take(300)
            Log.i(TAG, "hungryserv pusher set: HTTP ${resp.status.value}")
            "hungryserv pusher set: HTTP ${resp.status.value} $text"
        } catch (e: Exception) {
            "hungryserv pusher set: failed (${e.message?.take(120)})"
        } finally {
            http.close()
        }
    }
}
