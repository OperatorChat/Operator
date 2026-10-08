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
import android.os.Build
import android.util.Log
import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.clientserverapi.model.push.PusherData
import de.connect2x.trixnity.clientserverapi.model.push.SetPushers
import kotlinx.serialization.json.buildJsonObject

/**
 * Matrix HTTP pusher registration shared by both push channels (SPEC §5.5):
 * `event_id_only`, so no message content or keys go through the push server;
 * app_id + pushkey identify the pusher, so re-registering is idempotent.
 */
object MatrixPusher {
    private const val TAG = "MatrixPusher"

    fun appId(context: Context): String = context.packageName

    /** Registers (or refreshes) the pusher routed through [gatewayUrl] for [pushkey]. */
    suspend fun register(context: Context, client: MatrixClient, pushkey: String, gatewayUrl: String): Boolean {
        val set = SetPushers.Request.Set(
            appId = appId(context),
            pushkey = pushkey,
            kind = "http",
            appDisplayName = context.applicationInfo.loadLabel(context.packageManager).toString(),
            deviceDisplayName = Build.MODEL,
            lang = "en",
            data = PusherData(format = "event_id_only", url = gatewayUrl, customFields = buildJsonObject {}),
            append = false,
            profileTag = "",
        )
        return runCatching { client.api.push.setPushers(set).getOrThrow() }
            .onSuccess { Log.i(TAG, "pusher registered -> $gatewayUrl") }
            .onFailure { Log.w(TAG, "pusher registration failed: ${it.message}") }
            .isSuccess
    }

    /** Best-effort removal of one pusher. */
    suspend fun remove(context: Context, client: MatrixClient, pushkey: String) {
        runCatching { client.api.push.setPushers(SetPushers.Request.Remove(appId(context), pushkey)).getOrThrow() }
            .onSuccess { Log.i(TAG, "pusher removed") }
            .onFailure { Log.w(TAG, "pusher removal failed (best-effort): ${it.message}") }
    }

    /**
     * Removes every pusher this app registered on the account except [keep]
     * (a stale topic from an earlier install, server or distributor), so a
     * change of distributor never leaves a dead pusher behind.
     */
    suspend fun removeStale(context: Context, client: MatrixClient, keep: String) {
        val appId = appId(context)
        val pushers = runCatching { client.api.push.getPushers().getOrThrow() }.getOrNull() ?: return
        pushers.devices
            .filter { it.appId == appId && it.pushkey != keep }
            .forEach { stale -> remove(context, client, stale.pushkey) }
    }
}
