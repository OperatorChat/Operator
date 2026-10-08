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
package chat.operator.core.push

import android.content.Context
import android.util.Log
import chat.operator.core.matrix.MatrixRepository
import chat.operator.core.matrix.PushChannelHooks
import de.connect2x.trixnity.client.MatrixClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.UnifiedPush
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage

/**
 * Push through an external UnifiedPush distributor (ntfy app, Sunup, or one
 * built into the phone's firmware), preferred when one is installed (SPEC
 * §5.5). The distributor gives us an endpoint URL; the push server behind it
 * must serve the Matrix gateway at `<origin>/_matrix/push/v1/notify`, which
 * we check before registering the pusher. The pushkey is the endpoint URL,
 * so a changed endpoint re-registers and the old pusher is removed.
 */
object UnifiedPushChannel : PushChannelHooks {
    private const val TAG = "UnifiedPushChannel"
    private const val PREFS = "operator_unifiedpush"
    private const val KEY_ENDPOINT = "endpoint"
    private const val KEY_GATEWAY = "gateway"
    const val INSTANCE = "operator"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile private var appContext: Context? = null
    @Volatile private var client: MatrixClient? = null

    /** The distributor-issued endpoint currently registered as the pusher, if any. */
    val endpoint: String? get() = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.getString(KEY_ENDPOINT, null)

    /** The Matrix gateway the pusher points at, if registered. */
    val gatewayUrl: String? get() = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.getString(KEY_GATEWAY, null)

    @Volatile
    var pusherRegistered: Boolean = false
        private set

    @Volatile
    var lastPushAtMs: Long = 0L
        private set

    @Volatile
    var lastError: String? = null
        private set

    /** The distributor (package name) in use, or null. */
    fun distributor(context: Context): String? = UnifiedPush.getAckDistributor(context) ?: UnifiedPush.getSavedDistributor(context)

    /** Installed distributors (package names). */
    fun distributors(context: Context): List<String> = UnifiedPush.getDistributors(context)

    /** Lets the user choose among installed distributors (the connector's own picker). */
    fun pickDistributor(activity: android.app.Activity, onDone: (Boolean) -> Unit) =
        UnifiedPush.tryPickDistributor(activity) { onDone(it) }

    override fun start(context: Context, client: MatrixClient) {
        val ctx = context.applicationContext
        appContext = ctx
        this.client = client
        // Pick a distributor if none is saved yet (the first installed one; the
        // user can change it in Settings), then (re)register: the distributor
        // answers with NEW_ENDPOINT, possibly the same URL, and we re-check the pusher.
        val installed = UnifiedPush.getDistributors(ctx)
        Log.i(TAG, "distributors installed: $installed")
        val saved = UnifiedPush.getSavedDistributor(ctx)?.takeIf { it in installed }
        val chosen = saved ?: chooseDistributor(ctx, installed)
        if (chosen == null) {
            lastError = "no distributor"
            Log.w(TAG, "no UnifiedPush distributor installed")
            return
        }
        if (chosen != saved) {
            UnifiedPush.saveDistributor(ctx, chosen)
            Log.i(TAG, "using distributor $chosen")
        }
        // UnifiedPush 3: link to the distributor first (it may show UI), then register.
        UnifiedPush.tryUseCurrentOrDefaultDistributor(ctx) { linked ->
            Log.i(TAG, "distributor link: ${if (linked) "ok" else "failed"}")
            if (linked) {
                UnifiedPush.register(ctx, INSTANCE)
            } else {
                lastError = "could not link to distributor"
            }
        }
    }

    /** From Settings: link/register again with an Activity, so the distributor can show its consent UI. */
    fun relink(activity: android.app.Activity, onDone: (Boolean) -> Unit) {
        UnifiedPush.tryUseCurrentOrDefaultDistributor(activity) { linked ->
            Log.i(TAG, "distributor link (interactive): ${if (linked) "ok" else "failed"}")
            if (linked) UnifiedPush.register(activity.applicationContext, INSTANCE)
            onDone(linked)
        }
    }

    override fun stop() {
        // The distributor keeps the subscription; nothing to hold open here.
    }

    /**
     * Some apps embed a distributor that serves only themselves (Element X's
     * "KeepInternalDistributor"); registering with one of those is silent
     * failure. Prefer well-known general distributors, then the system's
     * default, then anything whose receiver isn't marked internal.
     */
    private fun chooseDistributor(context: Context, installed: List<String>): String? {
        PREFERRED.firstOrNull { it in installed }?.let { return it }
        runCatching { UnifiedPush.resolveDefaultDistributor(context) }.getOrNull()?.let { resolved ->
            installed.firstOrNull { resolved.toString().contains(it) }?.let { return it }
        }
        val internal = runCatching {
            context.packageManager.queryBroadcastReceivers(
                android.content.Intent("org.unifiedpush.android.distributor.REGISTER"), 0,
            ).filter { it.activityInfo.name.contains("Internal", ignoreCase = true) }
                .map { it.activityInfo.packageName }.toSet()
        }.getOrDefault(emptySet())
        return installed.firstOrNull { it !in internal } ?: installed.firstOrNull()
    }

    private val PREFERRED = listOf(
        "io.heckel.ntfy",
        "org.unifiedpush.distributor.sunup",
        "org.unifiedpush.distributor.nextpush",
        "io.heckel.ntfy.f_droid",
    )

    override suspend fun unregister(context: Context, client: MatrixClient) {
        endpoint?.let { MatrixPusher.remove(context, client, it) }
        UnifiedPush.unregister(context, INSTANCE)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        pusherRegistered = false
    }

    /** "Connected" here means the pusher is registered against a live endpoint. */
    override val isConnected: Boolean get() = pusherRegistered

    // ---- callbacks from OperatorPushService ----

    internal fun onNewEndpoint(context: Context, url: String) {
        Log.i(TAG, "distributor issued an endpoint")
        val c = client ?: run { Log.w(TAG, "endpoint arrived before login; will register on next start"); return }
        scope.launch {
            val gateway = discoverGateway(url) ?: run {
                lastError = "push server has no Matrix gateway"
                Log.w(TAG, "endpoint's server offers no Matrix gateway; staying unregistered")
                pusherRegistered = false
                return@launch
            }
            val ok = MatrixPusher.register(context, c, pushkey = url, gatewayUrl = gateway)
            pusherRegistered = ok
            if (ok) {
                lastError = null
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(KEY_ENDPOINT, url).putString(KEY_GATEWAY, gateway).apply()
                MatrixPusher.removeStale(context, c, keep = url)
            }
        }
    }

    internal fun onMessage(content: ByteArray) {
        lastPushAtMs = android.os.SystemClock.elapsedRealtime()
        val text = String(content)
        val notif = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
        val n = notif?.get("notification")?.jsonObject ?: notif
        val eventId = n?.get("event_id")?.jsonPrimitive?.contentOrNull
        val roomId = n?.get("room_id")?.jsonPrimitive?.contentOrNull
        Log.i(TAG, if (eventId != null || roomId != null) "push received -> waking one sync" else "push received (counts only) -> waking one sync")
        scope.launch {
            MatrixRepository.onPushDelivered(countsOnly = eventId == null && roomId == null, eventId = eventId, roomId = roomId)
        }
    }

    internal fun onRegistrationFailed(reason: FailedReason) {
        lastError = reason.name
        pusherRegistered = false
        Log.w(TAG, "distributor registration failed: $reason")
    }

    internal fun onUnregistered(context: Context) {
        Log.w(TAG, "distributor unregistered us")
        pusherRegistered = false
        val c = client
        val old = endpoint
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        if (c != null && old != null) scope.launch { MatrixPusher.remove(context, c, old) }
        // Let the router fall back to the built-in channel.
        PushRouter.onExternalLost(context)
    }

    /** UnifiedPush gateway discovery: `GET <origin>/_matrix/push/v1/notify` → `{"unifiedpush":{"gateway":"matrix"}}`. */
    private suspend fun discoverGateway(endpoint: String): String? = withContext(Dispatchers.IO) {
        val origin = runCatching { java.net.URL(endpoint) }.getOrNull()?.let { "${it.protocol}://${it.authority}" } ?: return@withContext null
        val url = "$origin/_matrix/push/v1/notify"
        runCatching {
            OkHttpClient().newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val gw = json.parseToJsonElement(body).jsonObject["unifiedpush"]?.jsonObject?.get("gateway")?.jsonPrimitive?.contentOrNull
                if (gw == "matrix") url else null
            }
        }.getOrNull()
    }
}

/** Receives the distributor's callbacks; declared in this module's manifest. */
class OperatorPushService : PushService() {
    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) = UnifiedPushChannel.onNewEndpoint(this, endpoint.url)
    override fun onMessage(message: PushMessage, instance: String) = UnifiedPushChannel.onMessage(message.content)
    override fun onRegistrationFailed(reason: FailedReason, instance: String) = UnifiedPushChannel.onRegistrationFailed(reason)
    override fun onUnregistered(instance: String) = UnifiedPushChannel.onUnregistered(this)
}
