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
import chat.operator.core.matrix.PushChannelHooks
import de.connect2x.trixnity.client.MatrixClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Chooses the push channel (SPEC §5.5): an external UnifiedPush distributor
 * when one is installed, otherwise the built-in ntfy channel. Re-evaluated on
 * every start, and when the distributor disappears; switching removes the
 * other channel's pusher so nothing stale is left on the account.
 */
object PushRouter : PushChannelHooks {
    private const val TAG = "PushRouter"
    private const val PREFS = "operator_push_router"
    private const val KEY_MODE = "mode"
    private const val MODE_EXTERNAL = "external"
    private const val MODE_BUILTIN = "builtin"
    private const val KEY_FORCE_BUILTIN = "force_builtin"
    /** Opt-in: use an installed UnifiedPush app instead of the built-in channel. */
    private const val KEY_USE_EXTERNAL = "use_external"
    /** If the external distributor has not produced a registered pusher by then, fall back. */
    private const val EXTERNAL_GRACE_MS = 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var client: MatrixClient? = null
    @Volatile private var appContext: Context? = null

    /** True while an external distributor is the active channel. */
    @Volatile
    var usingExternal: Boolean = false
        private set

    val active: PushChannelHooks get() = if (usingExternal) UnifiedPushChannel else NtfyPushChannel

    override fun start(context: Context, client: MatrixClient) {
        appContext = context.applicationContext
        this.client = client
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Built-in by default: the external path is only used when the user picked an
        // app in Settings (SPEC §5.5 prefers external, but that path is not yet proven
        // to deliver end to end on the test phones; the choice stays available).
        val external = UnifiedPushChannel.distributors(context).isNotEmpty() &&
            prefs.getBoolean(KEY_USE_EXTERNAL, false) && !prefs.getBoolean(KEY_FORCE_BUILTIN, false)
        val previous = prefs.getString(KEY_MODE, null)
        val mode = if (external) MODE_EXTERNAL else MODE_BUILTIN
        if (previous != null && previous != mode) {
            // The distributor situation changed since last time: drop the old channel's pusher.
            Log.i(TAG, "push channel changed: $previous -> $mode")
            val old: PushChannelHooks = if (previous == MODE_EXTERNAL) UnifiedPushChannel else NtfyPushChannel
            scope.launch { runCatching { old.unregister(context, client) } }
        }
        prefs.edit().putString(KEY_MODE, mode).apply()
        usingExternal = external
        Log.i(TAG, "using ${if (external) "external distributor" else "built-in ntfy channel"}")
        active.start(context, client)
        if (external) {
            // A distributor that never answers (seen with ntfy 1.25.2) must not leave the
            // phone without push: give it a minute, then use the built-in channel.
            scope.launch {
                kotlinx.coroutines.delay(EXTERNAL_GRACE_MS)
                if (usingExternal && !UnifiedPushChannel.pusherRegistered) {
                    Log.w(TAG, "external distributor produced no pusher within ${EXTERNAL_GRACE_MS / 1000}s — falling back to the built-in channel")
                    // Remember it, so a restart does not flip back and forth between channels.
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_FORCE_BUILTIN, true).apply()
                    onExternalLost(context)
                }
            }
        }
    }

    /** True when the user has chosen the built-in channel even though a distributor is installed. */
    fun isBuiltInForced(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_FORCE_BUILTIN, false)

    /** User choice from Settings: built-in channel regardless of installed distributors. */
    fun setBuiltInForced(context: Context, forced: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_FORCE_BUILTIN, forced)
            .putBoolean(KEY_USE_EXTERNAL, !forced)
            .apply()
        restart(context)
    }

    override fun stop() = active.stop()

    override suspend fun unregister(context: Context, client: MatrixClient) {
        UnifiedPushChannel.unregister(context, client)
        NtfyPushChannel.unregister(context, client)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    override val isConnected: Boolean get() = active.isConnected

    /** The external distributor went away: fall back to the built-in channel now. */
    internal fun onExternalLost(context: Context) {
        val c = client ?: return
        if (!usingExternal) return
        usingExternal = false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_MODE, MODE_BUILTIN).apply()
        Log.i(TAG, "falling back to the built-in ntfy channel")
        NtfyPushChannel.start(context, c)
    }

    /** Switches channel after the user picked (or removed) a distributor in settings. */
    fun restart(context: Context) {
        val c = client ?: return
        active.stop()
        start(context, c)
    }

    val lastPushAtMs: Long get() = maxOf(NtfyPushChannel.lastPushAtMs, UnifiedPushChannel.lastPushAtMs)
}
