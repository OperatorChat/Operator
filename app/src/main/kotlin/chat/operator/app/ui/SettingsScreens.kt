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

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import chat.operator.app.BuildConfig
import chat.operator.app.R
import chat.operator.app.notify.OperatorNotifier
import chat.operator.core.matrix.MatrixRepository
import chat.operator.core.push.NtfyPushChannel
import chat.operator.core.push.PushRouter
import chat.operator.core.push.UnifiedPushChannel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Settings (SPEC §5.6): account, notifications, push, battery, about. */
class SettingsScreen(host: ScreenHost) : MenuScreen(host) {
    override val titleRes = R.string.settings_title

    override fun items(): List<MenuItem> {
        val ctx = host.activity
        val account = MatrixRepository.accountState()
        val appearance = listOf(
            host.string(Appearance.theme(ctx).nameRes),
            host.string(
                when (Appearance.mode(ctx)) {
                    Appearance.LIGHT -> R.string.appearance_light
                    Appearance.DARK -> R.string.appearance_dark
                    else -> R.string.appearance_auto
                },
            ),
            host.string(Appearance.textScale(ctx).nameRes),
        ).joinToString(" · ")
        // Donations first, as slim rows (title only) in their own group; each hidden when its link is empty.
        val donate = listOfNotNull(
            host.string(R.string.brand_donate_url).takeIf { it.isNotBlank() }?.let { url ->
                MenuItem(host.string(R.string.settings_donate_kofi), null, R.drawable.ic_palette, R.color.icon_red) { Links.open(ctx, url) }
            },
            host.string(R.string.brand_sponsors_url).takeIf { it.isNotBlank() }?.let { url ->
                MenuItem(host.string(R.string.settings_donate_github), null, R.drawable.ic_palette, R.color.icon_purple) { Links.open(ctx, url) }
            },
        )
        return donate + listOf(
            MenuItem(host.string(R.string.settings_account), account.userId, R.drawable.ic_person, R.color.icon_blue, newGroup = donate.isNotEmpty()) { host.push(AccountScreen(host)) },
            MenuItem(host.string(R.string.settings_notifications), null, R.drawable.ic_bell, R.color.icon_red) { host.push(NotificationsScreen(host)) },
            MenuItem(host.string(R.string.settings_appearance), appearance, R.drawable.ic_palette, R.color.icon_purple) { host.push(AppearanceScreen(host)) },
            MenuItem(host.string(R.string.setup_title), host.string(R.string.setup_settings_detail), R.drawable.ic_check, R.color.icon_green, newGroup = true) { host.push(PhoneSetupScreen(host)) },
            MenuItem(host.string(R.string.settings_report), host.string(R.string.settings_report_detail), R.drawable.ic_bell, R.color.icon_grey) { ProblemReport.send(host.activity) },
        ) + listOf(
            MenuItem(host.string(R.string.settings_about), host.string(R.string.app_name) + " " + BuildConfig.VERSION_NAME, R.drawable.ic_info, R.color.icon_grey) { host.push(AboutScreen(host)) },
        )
    }
}

class AccountScreen(host: ScreenHost) : MenuScreen(host) {
    override val titleRes = R.string.settings_account
    private var approved: Boolean? = null

    override fun onShow() {
        super.onShow()
        host.scope.launch {
            approved = runCatching { MatrixRepository.e2eeState().verified }.getOrNull()
            refresh()
        }
    }

    override fun items(): List<MenuItem> {
        val account = MatrixRepository.accountState()
        return listOf(
            MenuItem(host.string(R.string.account_signed_in_as), account.userId ?: "—"),
            MenuItem(host.string(R.string.account_server), account.homeserver ?: "—"),
            MenuItem(
                host.string(R.string.account_approval),
                host.string(
                    when (approved) {
                        true -> R.string.account_approved
                        false -> R.string.account_not_approved
                        null -> R.string.account_checking
                    },
                ),
            ) { if (approved == false) host.push(RecoveryKeyScreen(host)) },
            MenuItem(host.string(R.string.account_other_device), host.string(R.string.account_other_device_detail)) {
                if (approved == false) host.push(OtherDeviceApprovalScreen(host))
            },
            MenuItem(host.string(R.string.account_sign_out), host.string(R.string.account_sign_out_detail)) { confirmSignOut() },
        )
    }

    private fun confirmSignOut() {
        host.push(
            OptionsScreen(
                host, host.string(R.string.account_sign_out_confirm),
                listOf<Pair<CharSequence, () -> Unit>>(
                    host.string(android.R.string.cancel) to {},
                    host.string(R.string.account_sign_out) to {
                        host.scope.launch {
                            MatrixRepository.logout()
                            host.replaceAll(WelcomeScreen(host))
                        }
                    },
                ),
            ),
        )
    }
}

class NotificationsScreen(host: ScreenHost) : MenuScreen(host) {
    override val titleRes = R.string.settings_notifications

    override fun items(): List<MenuItem> {
        val ctx = host.activity
        val enabled = ctx.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        return listOf(
            MenuItem(
                host.string(R.string.notifications_allowed),
                host.string(if (enabled) R.string.on else R.string.off),
            ) {
                ctx.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName),
                )
            },
            MenuItem(host.string(R.string.notifications_sound), host.string(R.string.notifications_sound_detail)) {
                ctx.startActivity(
                    Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                        .putExtra(Settings.EXTRA_CHANNEL_ID, OperatorNotifier.CHANNEL_MESSAGES),
                )
            },
            MenuItem(
                host.string(R.string.notifications_wake),
                host.string(if (OperatorNotifier.isWakeScreenEnabled(ctx)) R.string.notifications_wake_on else R.string.notifications_wake_off),
            ) {
                OperatorNotifier.setWakeScreenEnabled(ctx, !OperatorNotifier.isWakeScreenEnabled(ctx))
                refresh()
            },
            MenuItem(
                host.string(R.string.notifications_background),
                host.string(if (MatrixRepository.isSyncEnabled) R.string.notifications_background_on else R.string.notifications_background_off),
            ) {
                host.scope.launch {
                    MatrixRepository.setSyncEnabled(!MatrixRepository.isSyncEnabled)
                    refresh()
                }
            },
        )
    }
}

/** Push diagnostics (SPEC §5.5): distributor, endpoint, pusher, last push, test. Live-updating. */
class PushScreen(host: ScreenHost) : MenuScreen(host) {
    override val titleRes = R.string.settings_push
    private var job: Job? = null
    private var testResult: String? = null

    override fun onShow() {
        super.onShow()
        job?.cancel()
        job = host.scope.launch {
            while (isActive) {
                delay(2_000)
                refresh()
            }
        }
    }

    override fun onHide() {
        job?.cancel()
        job = null
    }

    override fun items(): List<MenuItem> {
        val ctx = host.activity
        val external = PushRouter.usingExternal
        val distributors = UnifiedPushChannel.distributors(ctx)
        val distributorLabel = when {
            external -> appLabel(UnifiedPushChannel.distributor(ctx)) ?: host.string(R.string.push_distributor_external)
            else -> host.string(R.string.push_distributor_builtin)
        }
        val items = mutableListOf(
            MenuItem(
                host.string(R.string.push_distributor),
                distributorLabel + if (distributors.isNotEmpty()) "\n" + host.string(R.string.push_distributor_switch) else "",
            ) {
                if (distributors.isNotEmpty()) {
                    val options = mutableListOf<Pair<CharSequence, () -> Unit>>(
                        host.string(R.string.push_distributor_builtin) to { PushRouter.setBuiltInForced(ctx, true); refresh() },
                    )
                    distributors.forEach { pkg ->
                        options += (appLabel(pkg) ?: pkg) to {
                            PushRouter.setBuiltInForced(ctx, false)
                            UnifiedPushChannel.relink(host.activity) { refresh() }
                        }
                    }
                    host.push(OptionsScreen(host, host.string(R.string.push_distributor), options))
                }
            },
        )
        if (!external) items += MenuItem(host.string(R.string.push_server), NtfyPushChannel.serverUrl + "\n" + host.string(R.string.push_server_select)) { host.push(PushServerScreen(host)) }
        items += MenuItem(
            host.string(R.string.push_connection),
            host.string(if (PushRouter.isConnected) R.string.push_connected else R.string.push_not_connected) +
                (UnifiedPushChannel.lastError?.takeIf { external }?.let { "\n$it" } ?: ""),
        )
        val registered = if (external) UnifiedPushChannel.pusherRegistered else NtfyPushChannel.pusherRegistered
        val gateway = if (external) UnifiedPushChannel.gatewayUrl else NtfyPushChannel.notifyUrl
        items += MenuItem(host.string(R.string.push_pusher), if (registered) gateway ?: host.string(R.string.yes) else host.string(R.string.no))
        items += MenuItem(host.string(R.string.push_last), lastPushText())
        if (!external) items += MenuItem(host.string(R.string.push_test), testResult ?: host.string(R.string.push_test_detail)) { runTest() }
        return items
    }

    private fun appLabel(packageName: String?): String? = packageName?.let { pkg ->
        runCatching { host.activity.packageManager.getApplicationLabel(host.activity.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrNull()
    }

    private fun lastPushText(): String {
        val at = PushRouter.lastPushAtMs
        if (at == 0L) return host.string(R.string.push_last_never)
        val seconds = (android.os.SystemClock.elapsedRealtime() - at) / 1000
        return when {
            seconds < 60 -> host.string(R.string.ago_seconds, seconds)
            seconds < 3600 -> host.string(R.string.ago_minutes, seconds / 60)
            else -> host.string(R.string.ago_hours, seconds / 3600)
        }
    }

    private fun runTest() {
        testResult = host.string(R.string.push_test_running)
        refresh()
        host.scope.launch {
            val ms = NtfyPushChannel.sendTestPush()
            testResult = if (ms != null) host.string(R.string.push_test_ok, ms / 1000.0) else host.string(R.string.push_test_failed)
            refresh()
        }
    }
}

/** The one battery prompt (SPEC §5.1 step 7): optimisation exemption, and DuraSpeed where present. */
class BatteryScreen(host: ScreenHost) : MenuScreen(host) {
    override val titleRes = R.string.settings_battery

    override fun items(): List<MenuItem> {
        val ctx = host.activity
        val power = ctx.getSystemService(PowerManager::class.java)
        val exempt = power.isIgnoringBatteryOptimizations(ctx.packageName)
        val items = mutableListOf(
            MenuItem(
                host.string(R.string.battery_optimisation),
                host.string(if (exempt) R.string.battery_exempt else R.string.battery_not_exempt),
            ) {
                if (!exempt) {
                    @Suppress("BatteryLife")
                    ctx.startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")),
                    )
                }
            },
        )
        if (hasDuraSpeed(ctx)) {
            items += MenuItem(host.string(R.string.battery_duraspeed), host.string(R.string.battery_duraspeed_detail)) {
                runCatching { ctx.startActivity(Intent(Settings.ACTION_SETTINGS)) }
            }
        }
        return items
    }

    override fun onShow() {
        super.onShow()
        refresh()
    }

    companion object {
        const val DURASPEED_PACKAGE = "com.mediatek.duraspeed"
        fun hasDuraSpeed(context: Context): Boolean =
            runCatching { context.packageManager.getPackageInfo(DURASPEED_PACKAGE, 0) }.isSuccess
    }
}

class AboutScreen(host: ScreenHost) : MenuScreen(host) {
    override val titleRes = R.string.settings_about

    override fun items(): List<MenuItem> = listOf(
        MenuItem(host.string(R.string.about_version), BuildConfig.VERSION_NAME),
        MenuItem(host.string(R.string.app_name), host.string(R.string.brand_about)),
        MenuItem(host.string(R.string.about_credits), host.string(R.string.about_credits_detail)) {
            host.push(TextScreen(host, host.string(R.string.about_credits), host.string(R.string.about_licences_text)))
        },
        MenuItem(host.string(R.string.about_support), host.string(R.string.brand_support_url)),
    )
}

/** Theme, light/dark, and text size. Changes apply by recreating the activity, which returns here. */
class AppearanceScreen(host: ScreenHost) : MenuScreen(host) {
    override val titleRes = R.string.settings_appearance

    override fun onShow() {
        super.onShow()
        // After a theme/mode/size change the activity was rebuilt: put focus back on the row that changed.
        val row = MainActivity.reopenAppearanceRow
        if (row > 0) {
            MainActivity.reopenAppearanceRow = 0
            focusRow(row)
        }
    }

    override fun items(): List<MenuItem> {
        val ctx = host.activity
        val theme = Appearance.theme(ctx)
        val scale = Appearance.textScale(ctx)
        return listOf(
            MenuItem(host.string(R.string.appearance_theme), host.string(theme.nameRes) + "\n" + host.string(R.string.select_to_change)) {
                val next = Appearance.themes[(Appearance.themes.indexOf(theme) + 1) % Appearance.themes.size]
                Appearance.setTheme(ctx, next)
                // High contrast is for people who find the screen hard to read: start it at Large.
                if (next.id == "highcontrast" && Appearance.textScale(ctx).id == "normal") {
                    Appearance.setTextScale(ctx, Appearance.textScales[1])
                }
                recreateHere(0)
            },
            MenuItem(
                host.string(R.string.appearance_mode),
                host.string(
                    when (Appearance.mode(ctx)) {
                        Appearance.LIGHT -> R.string.appearance_light
                        Appearance.DARK -> R.string.appearance_dark
                        else -> R.string.appearance_system
                    },
                ) + "\n" + host.string(R.string.select_to_change),
            ) {
                Appearance.setMode(ctx, (Appearance.mode(ctx) + 1) % 3)
                recreateHere(1)
            },
            MenuItem(host.string(R.string.appearance_text_size), host.string(scale.nameRes) + "\n" + host.string(R.string.select_to_change)) {
                val next = Appearance.textScales[(Appearance.textScales.indexOf(scale) + 1) % Appearance.textScales.size]
                Appearance.setTextScale(ctx, next)
                recreateHere(2)
            },
            MenuItem(
                host.string(R.string.appearance_peek),
                host.string(if (Appearance.peek(ctx)) R.string.appearance_peek_on else R.string.appearance_peek_off),
            ) {
                Appearance.setPeek(ctx, !Appearance.peek(ctx))
                refresh()
            },
            MenuItem(
                host.string(R.string.appearance_motion),
                host.string(if (Appearance.motion(ctx)) R.string.appearance_motion_on else R.string.appearance_motion_off),
            ) {
                Appearance.setMotion(ctx, !Appearance.motion(ctx))
                refresh()
            },
        )
    }

    private fun recreateHere(row: Int) {
        MainActivity.reopenAppearanceAfterRecreate = true
        MainActivity.reopenAppearanceRow = row
        host.activity.recreate()
    }
}


/** Lets someone running their own ntfy server point Operator at it (SPEC §5.5: the relay is replaceable). */
class PushServerScreen(host: ScreenHost) : FormScreen(host) {
    override val titleRes = R.string.push_server_title
    override val explanationRes = R.string.push_server_explanation
    override val hintRes = R.string.push_server_hint
    override val buttonRes = R.string.push_server_save
    override val inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
    override val initialValue: String? get() = NtfyPushChannel.serverOverride(host.activity)

    override fun onSubmit(value: String) {
        val url = value.trim().trimEnd('/')
        if (url.isNotEmpty() && !(url.startsWith("https://") && url.length > 10 && !url.contains(' '))) {
            showError(host.string(R.string.push_server_invalid)); return
        }
        NtfyPushChannel.setServerOverride(host.activity, url.ifEmpty { null }, host.string(R.string.brand_default_push_server))
        PushRouter.restart(host.activity)
        host.pop()
    }
}
