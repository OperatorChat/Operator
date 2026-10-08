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
// Ported from fenleon/chats PushChannel.kt (https://github.com/fenleon/chats),
// MIT License, Copyright (c) 2026 Fenn. See core-matrix/LICENSE-fenleon-chats.txt.
package chat.operator.core.push

import android.content.Context
import android.util.Log
import chat.operator.core.matrix.MatrixRepository
import chat.operator.core.matrix.PushChannelHooks
import de.connect2x.trixnity.client.MatrixClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * The built-in fallback distributor (SPEC §5.5): a Matrix HTTP pusher whose
 * `data.url` is an ntfy server's Matrix push gateway
 * (`<server>/_matrix/push/v1/notify`), routed to a private per-install topic
 * that doubles as the pushkey, plus a long-lived subscription to that topic's
 * JSON stream which the phone holds open. The pusher uses `event_id_only`, so
 * no message content or keys pass through the push server; each push wakes
 * exactly one sync via [MatrixRepository.onPushDelivered].
 *
 * The server is configurable ([serverUrl]; the brand default is ntfy.sh for
 * development). Changing it, or the pushkey, re-registers the pusher on the
 * next [start]; re-registration is idempotent (app_id + pushkey identify it).
 */
object NtfyPushChannel : PushChannelHooks {

    private const val PREFS = "operator_push"
    private const val KEY_SERVER = "ntfy_server"
    private const val KEY_SSE_URL = "ntfy_sse_url"
    private const val KEY_NOTIFY_URL = "ntfy_notify_url"
    private const val KEY_PUSHKEY = "ntfy_pushkey"
    private const val KEY_LAST_MSG_ID = "ntfy_last_msg_id"
    private const val TAG = "NtfyPushChannel"

    private const val RECONNECT_BASE_MS = 5_000L
    private const val RECONNECT_MAX_MS = 60_000L

    /** ntfy server base URL (no trailing slash). Set by the app from the brand config before [start]. */
    @Volatile
    var serverUrl: String = "https://ntfy.sh"

    private const val KEY_SERVER_OVERRIDE = "ntfy_server_override"

    /** A server the user entered in Settings, or null for the brand default. */
    fun serverOverride(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SERVER_OVERRIDE, null)

    /** Stores the user's server (null clears it) and makes it current; the next [start] re-provisions. */
    fun setServerOverride(context: Context, url: String?, brandDefault: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (url == null) remove(KEY_SERVER_OVERRIDE) else putString(KEY_SERVER_OVERRIDE, url)
        }.apply()
        serverUrl = url ?: brandDefault
    }

    /** Applies a saved override at start-up, after the brand default has been set. */
    fun applyServerOverride(context: Context) {
        serverOverride(context)?.let { serverUrl = it }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var job: Job? = null
    @Volatile private var currentCall: Call? = null
    @Volatile private var pushkey: String? = null
    @Volatile private var lastMessageId: String? = null
    @Volatile private var appContext: Context? = null
    @Volatile private var startedFor: MatrixClient? = null

    @Volatile
    override var isConnected: Boolean = false
        private set

    /** Pusher endpoint currently registered (for the diagnostics screen). */
    val notifyUrl: String? get() = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.getString(KEY_NOTIFY_URL, null)

    /** Elapsed-realtime of the last push received, 0 if none (for diagnostics). */
    @Volatile
    var lastPushAtMs: Long = 0L
        private set

    /** True once the homeserver accepted this install's pusher in this process. */
    @Volatile
    var pusherRegistered: Boolean = false
        private set

    /**
     * Diagnostics: publishes a message to this install's topic and waits for it
     * to come back down the stream. Returns the round-trip in ms, or null if
     * nothing arrived within 15 s or no topic is provisioned yet.
     */
    suspend fun sendTestPush(): Long? {
        val key = pushkey ?: return null
        val before = lastPushAtMs
        val t0 = android.os.SystemClock.elapsedRealtime()
        val ok = kotlinx.coroutines.withContext(Dispatchers.IO) {
            runCatching {
                OkHttpClient().newCall(
                    Request.Builder().url(key)
                        .post(okhttp3.RequestBody.create(null, """{"notification":{}}"""))
                        .build(),
                ).execute().use { it.isSuccessful }
            }.getOrDefault(false)
        }
        if (!ok) return null
        while (android.os.SystemClock.elapsedRealtime() - t0 < 15_000) {
            if (lastPushAtMs != before) return android.os.SystemClock.elapsedRealtime() - t0
            delay(200)
        }
        return null
    }

    private fun appId(context: Context) = context.packageName

    @Synchronized
    override fun start(context: Context, client: MatrixClient) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val server = serverUrl.trimEnd('/')
        var sseUrl = prefs.getString(KEY_SSE_URL, null)
        var notifyUrl = prefs.getString(KEY_NOTIFY_URL, null)
        var key = prefs.getString(KEY_PUSHKEY, null)
        // Provision a private topic on first start, or re-provision when the
        // configured server changed (SPEC §5.5: re-register on any change).
        if (sseUrl == null || notifyUrl == null || key == null || prefs.getString(KEY_SERVER, null) != server) {
            val topic = "op-" + java.util.UUID.randomUUID().toString().replace("-", "")
            key = "$server/$topic"
            sseUrl = "$server/$topic/json"
            notifyUrl = "$server/_matrix/push/v1/notify"
            prefs.edit()
                .putString(KEY_SERVER, server)
                .putString(KEY_SSE_URL, sseUrl)
                .putString(KEY_NOTIFY_URL, notifyUrl)
                .putString(KEY_PUSHKEY, key)
                .remove(KEY_LAST_MSG_ID)
                .apply()
            Log.i(TAG, "provisioned push topic on $server")
        }
        if (job?.isActive == true && startedFor === client) return
        stop()
        startedFor = client
        lastMessageId = prefs.getString(KEY_LAST_MSG_ID, null)
        appContext = context.applicationContext
        pushkey = key
        job = scope.launch {
            register(context.applicationContext, client, notifyUrl)
            connect(sseUrl)
        }
    }

    override fun stop() {
        currentCall?.cancel()
        currentCall = null
        job?.cancel()
        job = null
        isConnected = false
    }

    override suspend fun unregister(context: Context, client: MatrixClient) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = prefs.getString(KEY_PUSHKEY, null)
        try {
            if (key != null) MatrixPusher.remove(context, client, key)
        } finally {
            // Forget the topic whatever happened server-side (it works as a bearer for the
            // wake-up stream); a server the user chose in Settings is kept.
            pusherRegistered = false
            pushkey = null
            lastMessageId = null
            val override = prefs.getString(KEY_SERVER_OVERRIDE, null)
            prefs.edit().clear().apply {
                if (override != null) putString(KEY_SERVER_OVERRIDE, override)
            }.apply()
        }
    }

    /** Stops, removes the pusher and forgets the topic (sign-out, or "reset push" in diagnostics). */
    suspend fun clear(context: Context, client: MatrixClient) {
        stop()
        unregister(context, client)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        lastMessageId = null
    }

    /** This install's topic URL (the pushkey), once provisioned. */
    val currentPushkey: String? get() = pushkey

    private suspend fun register(context: Context, client: MatrixClient, notifyUrl: String) {
        val key = pushkey ?: return
        pusherRegistered = MatrixPusher.register(context, client, pushkey = key, gatewayUrl = notifyUrl)
        if (pusherRegistered) MatrixPusher.removeStale(context, client, keep = key)
    }

    private suspend fun CoroutineScope.connect(sseUrl: String) {
        val http = OkHttpClient.Builder()
            // ntfy's JSON stream keeps a ~45 s keepalive; 90 s of silence means a dead socket.
            .readTimeout(90, TimeUnit.SECONDS)
            .build()
        var delayMs = RECONNECT_BASE_MS
        while (isActive) {
            val url = resumeUrl(sseUrl)
            val call = http.newCall(Request.Builder().url(url).build())
            currentCall = call
            try {
                call.execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("stream HTTP ${resp.code}")
                    Log.i(TAG, "push stream connected")
                    isConnected = true
                    delayMs = RECONNECT_BASE_MS
                    val source = resp.body?.source() ?: throw IOException("stream empty body")
                    while (isActive) {
                        val line = source.readUtf8Line() ?: break
                        val trimmed = line.trim()
                        if (trimmed.isEmpty()) continue
                        val payload = if (trimmed.startsWith("data: ")) trimmed.removePrefix("data: ").trim() else trimmed
                        if (payload.startsWith("{")) onNotification(payload)
                    }
                }
            } catch (e: Exception) {
                if (isActive) Log.w(TAG, "push stream dropped: ${e.message}")
            }
            isConnected = false
            currentCall = null
            if (!isActive) break
            delay(delayMs)
            delayMs = (delayMs * 2).coerceAtMost(RECONNECT_MAX_MS)
        }
    }

    /** Resume from the last seen message so a reconnect replays pushes published during the gap. */
    private fun resumeUrl(sseUrl: String): String {
        val since = lastMessageId ?: "all"
        return if (sseUrl.contains("?")) "$sseUrl&since=$since" else "$sseUrl?since=$since"
    }

    private suspend fun onNotification(json: String) {
        val outer = runCatching { Json { ignoreUnknownKeys = true }.parseToJsonElement(json).jsonObject }
            .getOrNull() ?: return
        if (outer["event"]?.jsonPrimitive?.contentOrNull == "message") {
            outer["id"]?.jsonPrimitive?.contentOrNull?.let { id ->
                if (id != lastMessageId) {
                    lastMessageId = id
                    appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        ?.edit()?.putString(KEY_LAST_MSG_ID, id)?.apply()
                }
            }
        }
        if (outer["event"]?.jsonPrimitive?.contentOrNull in setOf("open", "keepalive")) return
        val notifJson = outer["notification"]?.toString()
            ?: outer["message"]?.jsonPrimitive?.contentOrNull
            ?: json
        val notif = runCatching { Json.parseToJsonElement(notifJson).jsonObject }.getOrNull() ?: return
        val n = notif["notification"]?.jsonObject ?: notif
        val eventId = n["event_id"]?.jsonPrimitive?.contentOrNull
        val roomId = n["room_id"]?.jsonPrimitive?.contentOrNull
        lastPushAtMs = android.os.SystemClock.elapsedRealtime()
        // Never log ids that identify a chat; a count is enough for diagnostics.
        Log.i(TAG, if (eventId != null || roomId != null) "push received -> waking one sync" else "push received (counts only) -> waking one sync")
        MatrixRepository.onPushDelivered(
            countsOnly = eventId == null && roomId == null,
            eventId = eventId,
            roomId = roomId,
        )
    }
}
