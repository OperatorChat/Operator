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
// The email-code exchange is ported from fenleon/chats MatrixRepository.kt
// (https://github.com/fenleon/chats), MIT License, Copyright (c) 2026 Fenn.
package chat.operator.core.beeper

import android.content.Context
import android.util.Log
import chat.operator.core.matrix.MatrixRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.buildJsonObject

/**
 * Beeper's email-code sign-in (SPEC §5.1 step 2). Two steps, with the user
 * reading their email in between: [requestCode] then [login]. The exchange
 * yields a Matrix JWT, which `core-matrix` turns into a normal session.
 *
 * This uses Beeper's own sign-in endpoint, which is not a public API (SPEC
 * §12): it may change without notice, and Beeper's agreement is needed before
 * a wide release. Password login to the Beeper homeserver stays available as
 * the fallback through the "other Matrix server" path.
 */
object BeeperAuth {
    private const val TAG = "BeeperAuth"

    /** Beeper refused the email-code exchange because it no longer serves non-Beeper clients. */
    class ClientNotAcceptedException(message: String) : IllegalStateException(message)

    const val HOMESERVER = "https://matrix.beeper.com"
    const val LOGIN_MODE = "beeper"

    private const val API_BASE = "https://api.beeper.com"
    private const val API_TOKEN = "BEEPER-PRIVATE-API-PLEASE-DONT-USE"

    /** Sign-in state lives in its own store so session clean-up never touches it. */
    private const val PREFS = "operator_beeper"
    private const val KEY_REQUEST_ID = "request_id"
    private const val KEY_EMAIL = "email"
    private const val KEY_REQUESTED_AT = "requested_at"

    private val json = Json { ignoreUnknownKeys = true }

    /** True when [homeserver] is Beeper's, so Beeper-only behaviour can be switched on. */
    fun isBeeper(homeserver: String?): Boolean =
        homeserver?.contains("beeper.com", ignoreCase = true) == true

    /** Step 1: ask Beeper to email a sign-in code to [email]. */
    suspend fun requestCode(context: Context, email: String): Result<Unit> = runCatching {
        val http = HttpClient()
        try {
            val init = http.post("$API_BASE/user/login") {
                header("Authorization", "Bearer $API_TOKEN")
                contentType(ContentType.Application.Json)
            }
            if (init.status.value !in 200..299) error("Beeper login init failed (HTTP ${init.status.value})")
            val requestId = parse(init.bodyAsText())["request"]?.jsonPrimitive?.content
                ?: error("Beeper login init: missing request id")
            val emailReq = http.post("$API_BASE/user/login/email") {
                header("Authorization", "Bearer $API_TOKEN")
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject { put("request", requestId); put("email", email.trim()) }.toString())
            }
            if (emailReq.status.value !in 200..299) error("Beeper code request failed (HTTP ${emailReq.status.value})")
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_REQUEST_ID, requestId)
                .putString(KEY_EMAIL, email.trim())
                .putLong(KEY_REQUESTED_AT, System.currentTimeMillis())
                .apply()
            Log.i(TAG, "sign-in code requested")
        } finally {
            http.close()
        }
    }

    /** The email a code was last requested for, if any (to prefill the screen). */
    fun pendingEmail(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_EMAIL, null)

    /** Step 2: exchange the emailed [code] for a session on the Beeper homeserver. */
    suspend fun login(context: Context, code: String): Result<Unit> = runCatching {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val requestId = prefs.getString(KEY_REQUEST_ID, null)
            ?: error("No sign-in code was requested. Ask for a new code.")
        val http = HttpClient()
        val username: String
        val token: String
        try {
            val resp = http.post("$API_BASE/user/login/response") {
                header("Authorization", "Bearer $API_TOKEN")
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject { put("request", requestId); put("response", code.trim()) }.toString())
            }
            if (resp.status.value !in 200..299) {
                // Beeper's error body is a short JSON message with no secrets; keep it for diagnostics.
                val detail = runCatching { resp.bodyAsText().take(200) }.getOrDefault("")
                val ageS = (System.currentTimeMillis() - prefs.getLong(KEY_REQUESTED_AT, 0L)) / 1000
                if (detail.contains("old-client-login-disabled")) {
                    throw ClientNotAcceptedException("Beeper no longer accepts email-code sign-in from this app (HTTP ${resp.status.value})")
                }
                error("Beeper did not accept that code (HTTP ${resp.status.value}, request ${ageS}s old): $detail")
            }
            val body = parse(resp.bodyAsText())
            val userInfo = body["whoami"]?.jsonObject?.get("userInfo")?.jsonObject ?: error("Beeper login: missing user info")
            username = userInfo["username"]?.jsonPrimitive?.content ?: error("Beeper login: missing username")
            token = body["token"]?.jsonPrimitive?.content ?: error("Beeper login: missing token")
        } finally {
            http.close()
        }
        MatrixRepository.loginWithJwt(HOMESERVER, username, token, LOGIN_MODE).getOrThrow()
        prefs.edit().remove(KEY_REQUEST_ID).apply()
    }

    private fun parse(text: String): JsonObject = json.parseToJsonElement(text).jsonObject

    // ---- Setting a password by email (standard Matrix, which Beeper's homeserver supports) ----

    /** Handle for an in-progress password set: the email validation session. */
    data class PasswordEmailSession(val sid: String, val clientSecret: String)

    /**
     * Step 1: asks the homeserver to email [email] a validation link. The
     * password itself is set in [completePassword] after the link is opened.
     * Beeper's own help recommends exactly this flow via any Matrix client.
     */
    suspend fun requestPasswordEmail(email: String): Result<PasswordEmailSession> = runCatching {
        val clientSecret = java.util.UUID.randomUUID().toString().replace("-", "")
        val http = HttpClient()
        try {
            val resp = http.post("$HOMESERVER/_matrix/client/v3/account/password/email/requestToken") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("client_secret", clientSecret)
                    put("email", email.trim())
                    put("send_attempt", 1)
                }.toString())
            }
            val body = resp.bodyAsText()
            if (resp.status.value !in 200..299) {
                val code = runCatching { parse(body)["errcode"]?.jsonPrimitive?.contentOrNull }.getOrNull()
                error(when (code) {
                    "M_THREEPID_NOT_FOUND" -> "no account with that email"
                    else -> "request failed (HTTP ${resp.status.value})"
                })
            }
            val sid = parse(body)["sid"]?.jsonPrimitive?.contentOrNull ?: error("missing sid")
            PasswordEmailSession(sid, clientSecret)
        } finally {
            http.close()
        }
    }

    /** Step 2: after the emailed link has been opened, sets [newPassword]. Fails with "not validated yet" if the link wasn't opened. */
    suspend fun completePassword(session: PasswordEmailSession, newPassword: String): Result<Unit> = runCatching {
        val http = HttpClient()
        try {
            val resp = http.post("$HOMESERVER/_matrix/client/v3/account/password") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("new_password", newPassword)
                    put("logout_devices", false)
                    put("auth", buildJsonObject {
                        put("type", "m.login.email.identity")
                        put("threepid_creds", buildJsonObject {
                            put("sid", session.sid)
                            put("client_secret", session.clientSecret)
                        })
                    })
                }.toString())
            }
            if (resp.status.value !in 200..299) {
                val code = runCatching { parse(resp.bodyAsText())["errcode"]?.jsonPrimitive?.contentOrNull }.getOrNull()
                error(if (code == "M_UNAUTHORIZED" || resp.status.value == 401) "not validated yet" else "failed (HTTP ${resp.status.value})")
            }
        } finally {
            http.close()
        }
    }
}
