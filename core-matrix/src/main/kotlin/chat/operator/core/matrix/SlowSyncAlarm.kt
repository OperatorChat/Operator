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
package chat.operator.core.matrix

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.os.SystemClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Wakes the slow-sync rounds on the phone's alarm clock.
 *
 * A coroutine `delay` runs on a clock that stops while the phone is in deep
 * sleep, so a "15-minute" round took over an hour on a phone that slept
 * three quarters of the time (TT990, 13 Oct 2026). An alarm on the
 * elapsed-realtime clock fires on time regardless; the receiver holds a short
 * wakelock so the round can finish before the phone dozes off again.
 */
object SlowSyncAlarm {
    private const val TAG = "SlowSyncAlarm"
    private const val ACTION = "chat.operator.core.matrix.SLOW_SYNC_ROUND"
    private const val WAKELOCK_TIMEOUT_MS = 120_000L

    @Volatile private var pending: CompletableDeferred<Unit>? = null
    @Volatile private var wakeLock: PowerManager.WakeLock? = null

    /** Suspends for [delayMs]; returns early when the alarm fires first. */
    suspend fun sleep(context: Context, delayMs: Long) {
        val signal = CompletableDeferred<Unit>()
        pending = signal
        schedule(context, delayMs)
        try {
            withTimeoutOrNull(delayMs) { signal.await() }
        } finally {
            cancel(context)
            if (pending === signal) pending = null
        }
    }

    /** Releases the wakelock the alarm took, once the round it woke is done. */
    fun roundFinished() {
        wakeLock?.let { if (it.isHeld) runCatching { it.release() } }
        wakeLock = null
    }

    internal fun onAlarm(context: Context) {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        roundFinished()
        wakeLock = power?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "operator:slow-round")?.also {
            it.acquire(WAKELOCK_TIMEOUT_MS)
        }
        android.util.Log.d(TAG, "alarm fired")
        pending?.complete(Unit)
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, SlowSyncAlarmReceiver::class.java).setAction(ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun schedule(context: Context, delayMs: Long) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching {
            alarms.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + delayMs,
                pendingIntent(context),
            )
        }.onFailure { android.util.Log.w(TAG, "could not schedule: ${it.message}") }
    }

    private fun cancel(context: Context) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching { alarms.cancel(pendingIntent(context)) }
    }
}

class SlowSyncAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SlowSyncAlarm.onAlarm(context.applicationContext)
    }
}
