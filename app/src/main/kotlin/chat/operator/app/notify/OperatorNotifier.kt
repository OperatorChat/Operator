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
package chat.operator.app.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.PowerManager
import chat.operator.app.R
import chat.operator.app.ui.MainActivity
import chat.operator.core.matrix.MessageNotifier

/**
 * Message notifications (SPEC §5.5): one "Messages" channel with sound and
 * vibration on by default, decided locally and never by the account's push
 * rules. One notification per chat, replaced as more messages arrive; a tap
 * opens that chat.
 */
class OperatorNotifier : MessageNotifier {

    override fun notifyMessage(
        context: Context,
        roomId: String,
        roomName: String,
        senderName: String?,
        preview: String,
        direct: Boolean,
        unreadCount: Long,
        mention: Boolean,
    ) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        ensureChannels(context)
        val id = notificationId(roomId)
        // A mention says so, and goes through its own channel so it can have its own sound.
        val text = when {
            mention && !senderName.isNullOrBlank() -> context.getString(R.string.notify_mentioned_you, senderName, preview)
            mention -> context.getString(R.string.notify_mentioned_you_direct, preview)
            direct || senderName.isNullOrBlank() -> preview
            else -> "$senderName: $preview"
        }
        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_ROOM_ID, roomId)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = Notification.Builder(context, if (mention) CHANNEL_MENTIONS else CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(roomName)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
        if (unreadCount > 1) builder.setNumber(unreadCount.toInt())
        manager.notify(id, builder.build())
        wakeScreenIfWanted(context)
    }

    /**
     * Lights the screen for a few seconds when a message arrives while it is
     * off (SPEC-adjacent: keypad phones have no "wake for notifications"
     * feature of their own). A setting, on by default.
     */
    private fun wakeScreenIfWanted(context: Context) {
        if (!isWakeScreenEnabled(context)) return
        val power = context.getSystemService(PowerManager::class.java)
        if (power.isInteractive) return
        @Suppress("DEPRECATION")
        val lock = power.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "operator:message",
        )
        lock.acquire(WAKE_SCREEN_MS)
    }

    override fun cancelRoom(context: Context, roomId: String) {
        context.getSystemService(NotificationManager::class.java).cancel(notificationId(roomId))
    }

    override fun notifySyncPending(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        ensureChannels(context)
        val open = PendingIntent.getActivity(
            context,
            SYNC_PENDING_ID,
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        manager.notify(
            SYNC_PENDING_ID,
            Notification.Builder(context, CHANNEL_STATUS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(context.getString(R.string.notify_sync_pending))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .build(),
        )
    }

    override fun clearSyncPending(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(SYNC_PENDING_ID)
    }

    override fun clearAll(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancelAll()
    }

    companion object {
        private const val PREFS = "operator_notifications"
        private const val KEY_WAKE_SCREEN = "wake_screen"
        private const val WAKE_SCREEN_MS = 6_000L

        fun isWakeScreenEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_WAKE_SCREEN, true)

        fun setWakeScreenEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_WAKE_SCREEN, enabled).apply()
        }

        const val CHANNEL_MESSAGES = "messages"
        const val CHANNEL_MENTIONS = "mentions"
        const val CHANNEL_STATUS = "status"
        const val EXTRA_ROOM_ID = "chat.operator.roomId"
        private const val SYNC_PENDING_ID = 72
        private const val ID_BASE = 1000

        /** Idempotent; called at app start and before every post. */
        fun ensureChannels(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_MESSAGES, context.getString(R.string.channel_messages), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = context.getString(R.string.channel_messages_description)
                    enableVibration(true)
                    setSound(
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                },
            )
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_MENTIONS, context.getString(R.string.channel_mentions), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = context.getString(R.string.channel_mentions_description)
                    enableVibration(true)
                    setSound(
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                },
            )
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_STATUS, context.getString(R.string.channel_status), NotificationManager.IMPORTANCE_LOW).apply {
                    description = context.getString(R.string.channel_status_description)
                    setShowBadge(false)
                },
            )
        }

        private fun notificationId(roomId: String): Int = ID_BASE + Math.floorMod(roomId.hashCode(), 100_000)
    }
}
