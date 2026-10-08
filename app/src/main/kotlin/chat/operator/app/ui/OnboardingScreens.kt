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

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.IntentCompat
import androidx.core.content.PackageManagerCompat
import androidx.core.content.UnusedAppRestrictionsConstants
import androidx.core.view.isVisible
import chat.operator.app.R
import chat.operator.core.beeper.BeeperAuth
import chat.operator.core.matrix.MatrixRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/* The sign-in and set-up flow, in the order a new user actually needs it:
 * Welcome → Sign in → Approve (recovery key) → Getting your chats → Set up your phone → Chats. */

/** Step 2: Beeper username and password, with the way to get a password when there isn't one. */
class BeeperSignInScreen(host: ScreenHost) : Screen(host) {
    private lateinit var user: EditText
    private lateinit var password: EditText
    private lateinit var button: Button
    private lateinit var status: TextView
    private var busy = false

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup): View =
        inflate(R.layout.screen_sign_in, parent).also { v ->
            user = v.findViewById(R.id.user)
            password = v.findViewById(R.id.password)
            button = v.findViewById(R.id.button)
            status = v.findViewById(R.id.status)
            button.setOnClickListener { submit() }
            password.setOnEditorActionListener { _, _, _ -> submit(); true }
            v.findViewById<View>(R.id.set_password).setOnClickListener { host.push(SetPasswordScreen(host)) }
        }

    override fun onShow() {
        host.setSoftKeys(host.string(R.string.other_server_button), host.string(R.string.softkey_back))
        if (user.text.isNullOrEmpty()) user.requestFocus() else password.requestFocus()
    }

    override fun onMenu(): Boolean { submit(); return true }

    private fun submit() {
        if (busy) return
        val u = user.text.toString().trim().removePrefix("@").substringBefore(":")
        val p = password.text.toString()
        if (u.isEmpty() || p.isEmpty()) {
            status.text = host.string(R.string.sign_in_incomplete); status.isVisible = true; return
        }
        busy = true; button.isEnabled = false
        status.isVisible = true
        // Visible progress: the dots move while the server is being asked (user feedback: a static
        // line read as nothing happening on a slow connection).
        val pulse = Pulse.start(host, status, host.string(R.string.other_server_signing_in))
        host.scope.launch {
            MatrixRepository.login(BeeperAuth.HOMESERVER, u, p, tokenLogin = false)
                .onSuccess { pulse.cancel(); host.replaceAll(RecoveryKeyScreen(host)) }
                .onFailure {
                    pulse.cancel()
                    busy = false; button.isEnabled = true
                    status.text = host.string(R.string.sign_in_failed)
                    password.requestFocus()
                }
        }
    }
}

/** Sets a Beeper password by email: new password here, validation link by email, then confirm. */
class SetPasswordScreen(host: ScreenHost) : Screen(host) {
    private lateinit var email: EditText
    private lateinit var newPassword: EditText
    private lateinit var button: Button
    private lateinit var status: TextView
    private var session: BeeperAuth.PasswordEmailSession? = null
    private var busy = false

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup): View =
        inflate(R.layout.screen_set_password, parent).also { v ->
            email = v.findViewById(R.id.email)
            newPassword = v.findViewById(R.id.new_password)
            button = v.findViewById(R.id.button)
            status = v.findViewById(R.id.status)
            button.setOnClickListener { submit() }
            newPassword.setOnEditorActionListener { _, _, _ -> submit(); true }
        }

    override fun onShow() {
        host.setSoftKeys(host.string(if (session == null) R.string.set_password_send else R.string.set_password_done), host.string(R.string.softkey_back))
        if (session == null) email.requestFocus() else button.requestFocus()
    }

    override fun onMenu(): Boolean { submit(); return true }

    private fun submit() {
        if (busy) return
        val s = session
        if (s == null) {
            val e = email.text.toString().trim()
            val pw = newPassword.text.toString()
            if (!e.contains('@') || pw.length < 8) {
                status.text = host.string(R.string.set_password_invalid); status.isVisible = true; return
            }
            busy = true; button.isEnabled = false
            status.text = host.string(R.string.set_password_sending); status.isVisible = true
            host.scope.launch {
                BeeperAuth.requestPasswordEmail(e)
                    .onSuccess { sess ->
                        session = sess
                        busy = false; button.isEnabled = true
                        email.isEnabled = false; newPassword.isEnabled = false
                        status.text = host.string(R.string.set_password_check_email)
                        button.setText(R.string.set_password_done)
                        host.setSoftKeys(host.string(R.string.set_password_done), host.string(R.string.softkey_back))
                        button.requestFocus()
                    }
                    .onFailure {
                        busy = false; button.isEnabled = true
                        status.text = host.string(if (it.message?.contains("no account") == true) R.string.set_password_no_account else R.string.set_password_failed)
                    }
            }
        } else {
            busy = true; button.isEnabled = false
            status.text = host.string(R.string.set_password_saving)
            host.scope.launch {
                BeeperAuth.completePassword(s, newPassword.text.toString())
                    .onSuccess {
                        status.text = host.string(R.string.set_password_success)
                        delay(1500)
                        host.pop()
                    }
                    .onFailure {
                        busy = false; button.isEnabled = true
                        status.text = host.string(if (it.message?.contains("not validated") == true) R.string.set_password_not_yet else R.string.set_password_failed)
                        button.requestFocus()
                    }
            }
        }
    }
}

/** Step 3: approve this phone with the recovery key (the only dependable route with Beeper). */
class RecoveryKeyScreen(host: ScreenHost) : FormScreen(host) {
    override val titleRes = R.string.recovery_title
    override val explanationRes = R.string.recovery_explanation
    override val hintRes = R.string.recovery_hint
    override val buttonRes = R.string.recovery_button
    override val softKeyRes = R.string.recovery_softkey
    override val inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS

    override fun onShow() {
        super.onShow()
        host.scope.launch {
            if (runCatching { MatrixRepository.e2eeState().verified }.getOrDefault(false)) host.replaceAll(SyncingScreen(host))
        }
    }

    /** Back here means "not now": the chats still work for unencrypted content; approval can be done from Settings. */
    override fun onBack(): Boolean {
        host.replaceAll(SyncingScreen(host))
        return true
    }

    override fun onSubmit(value: String) {
        val key = value.filter { it.isLetterOrDigit() }
        if (key.length < 48) {
            showError(host.string(R.string.recovery_too_short, key.length)); return
        }
        setBusy(host.string(R.string.recovery_checking))
        host.scope.launch {
            MatrixRepository.recoverWithKey(key)
                .onSuccess { host.replaceAll(SyncingScreen(host)) }
                .onFailure { showError(host.string(R.string.recovery_failed)) }
        }
    }
}

/** Step 4: the first sync, with a moving indicator and a live count so it never looks frozen. */
class SyncingScreen(host: ScreenHost) : Screen(host) {
    private lateinit var title: TextView
    private lateinit var count: TextView
    private var job: Job? = null
    private var startedAt = 0L

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup): View =
        inflate(R.layout.screen_syncing, parent).also { v ->
            title = v.findViewById(R.id.title)
            count = v.findViewById(R.id.count)
            v.findViewById<Button>(R.id.skip).setOnClickListener { finish() }
        }

    override fun onShow() {
        host.setSoftKeys(null, null)
        startedAt = android.os.SystemClock.elapsedRealtime()
        job?.cancel()
        job = host.scope.launch {
            var dots = 0
            while (isActive) {
                dots = (dots + 1) % 4
                title.text = host.string(R.string.syncing_title) + ".".repeat(dots)
                val state = MatrixRepository.connectionState()
                val rooms = MatrixRepository.roomList.value.size
                count.text = if (rooms > 0) host.string(R.string.syncing_rooms, rooms) else ""
                val elapsed = android.os.SystemClock.elapsedRealtime() - startedAt
                if ((state.roomListReady && rooms > 0 && elapsed > MIN_SHOW_MS) || elapsed > MAX_WAIT_MS) {
                    finish(); break
                }
                delay(500)
            }
        }
    }

    override fun onHide() { job?.cancel(); job = null }
    override fun onBack(): Boolean = true // nothing to go back to; let it finish

    private fun finish() {
        job?.cancel()
        host.replaceAll(PhoneSetupScreen(host, onboarding = true))
    }

    companion object {
        private const val MIN_SHOW_MS = 3_000L
        private const val MAX_WAIT_MS = 6 * 60_000L
    }
}

/**
 * Step 5 (and Settings → Phone set-up): the phone-side permissions that keep
 * messages arriving, as a checklist with ✓ for each one done. Rows open the
 * right system screen; the list re-checks when the app comes back.
 */
class PhoneSetupScreen(host: ScreenHost, private val onboarding: Boolean = false) : MenuScreen(host) {
    override val titleRes = R.string.setup_title
    private var duraSpeedOpened = false

    override fun onShow() {
        super.onShow()
        host.setSoftKeys(host.string(R.string.softkey_select), host.string(if (onboarding) R.string.setup_later else R.string.softkey_back))
    }

    override fun onResume() { refresh() }

    override fun onBack(): Boolean {
        if (onboarding) { Onboarding.markSetupShown(host.activity); host.replaceAll(ChatListScreen(host)); return true }
        return false
    }

    /** A green tick when the step is done, an empty grey ring when it is not. */
    private fun step(done: Boolean, title: String, detail: String, onSelect: () -> Unit) = MenuItem(
        title, detail,
        if (done) R.drawable.ic_check else R.drawable.ic_circle,
        if (done) R.color.icon_green else R.color.icon_grey,
        onSelect = onSelect,
    )

    override fun items(): List<MenuItem> {
        val ctx = host.activity
        val items = mutableListOf<MenuItem>()
        // 1. Notifications
        val notificationsOk = notificationsAllowed(ctx)
        items += step(notificationsOk, host.string(R.string.setup_notifications), host.string(if (notificationsOk) R.string.setup_done else R.string.setup_notifications_detail)) {
            if (!notificationsOk) host.activity.requestNotificationPermission { refresh() }
        }
        // 2. Battery optimisation
        val batteryOk = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
        items += step(batteryOk, host.string(R.string.setup_battery), host.string(if (batteryOk) R.string.setup_done else R.string.setup_battery_detail)) {
            if (!batteryOk) {
                @Suppress("BatteryLife")
                runCatching { ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))) }
            }
        }
        // 3. Pause if unused
        val unused = unusedStatus(ctx)
        if (unused != null) {
            val unusedOk = unused == UnusedAppRestrictionsConstants.DISABLED || unused == UnusedAppRestrictionsConstants.FEATURE_NOT_AVAILABLE
            items += step(unusedOk, host.string(R.string.setup_unused), host.string(if (unusedOk) R.string.setup_done else R.string.setup_unused_detail)) {
                if (!unusedOk) runCatching { ctx.startActivity(IntentCompat.createManageUnusedAppRestrictionsIntent(ctx, ctx.packageName)) }
            }
        }
        // 4. DuraSpeed (MediaTek phones only)
        if (BatteryScreen.hasDuraSpeed(ctx)) {
            items += step(duraSpeedOpened, host.string(R.string.setup_duraspeed), host.string(if (duraSpeedOpened) R.string.setup_duraspeed_opened else R.string.setup_duraspeed_detail)) {
                // DuraSpeed's own screen cannot be opened by other apps (not exported);
                // open the phone's Settings, where it is an entry near the bottom.
                val opened = runCatching { ctx.startActivity(Intent(Settings.ACTION_SETTINGS)); true }.getOrDefault(false)
                duraSpeedOpened = opened
                refresh()
            }
        }
        items += MenuItem(host.string(R.string.setup_duplicates), host.string(R.string.setup_duplicates_detail), R.drawable.ic_info, R.color.icon_grey, newGroup = true)
        if (onboarding) {
            val allDone = notificationsOk && batteryOk && (unused == null || unused == UnusedAppRestrictionsConstants.DISABLED || unused == UnusedAppRestrictionsConstants.FEATURE_NOT_AVAILABLE)
            items += MenuItem(host.string(if (allDone) R.string.setup_continue else R.string.setup_later), null, null, null, newGroup = true) {
                Onboarding.markSetupShown(ctx)
                host.replaceAll(ChatListScreen(host))
            }
        }
        return items
    }

    companion object {
        fun notificationsAllowed(ctx: Context): Boolean =
            ctx.getSystemService(NotificationManager::class.java).areNotificationsEnabled()

        /** Hibernation status, or null when it cannot be read yet (the API is asynchronous; we read it synchronously with a short wait). */
        fun unusedStatus(ctx: Context): Int? = runCatching {
            val future = PackageManagerCompat.getUnusedAppRestrictionsStatus(ctx)
            future.get(400, java.util.concurrent.TimeUnit.MILLISECONDS)
        }.getOrNull()

        fun allEssentialDone(ctx: Context): Boolean {
            val battery = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
            return notificationsAllowed(ctx) && battery
        }
    }
}

/** The hand-over from sign-in to the chats, plus the once-a-day reminder while set-up is incomplete. */
object Onboarding {
    private const val PREFS = "operator_onboarding"
    private const val KEY_SETUP_SHOWN_AT = "setup_shown_at"
    private const val REMIND_EVERY_MS = 24 * 60 * 60_000L

    fun markSetupShown(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_SETUP_SHOWN_AT, System.currentTimeMillis()).apply()

    /** On a normal launch: nudge towards an unfinished phone set-up at most once a day. */
    fun shouldRemind(ctx: Context): Boolean {
        if (PhoneSetupScreen.allEssentialDone(ctx)) return false
        val last = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_SETUP_SHOWN_AT, 0L)
        return System.currentTimeMillis() - last > REMIND_EVERY_MS
    }
}


/** Animated dots after a status line while something runs; cancel the returned job when it ends. */
object Pulse {
    fun start(host: ScreenHost, view: TextView, base: String): kotlinx.coroutines.Job = host.scope.launch {
        var dots = 0
        val stem = base.trimEnd('…', '.')
        while (coroutineContext[kotlinx.coroutines.Job]?.isActive == true) {
            view.text = stem + ".".repeat(dots)
            dots = (dots + 1) % 4
            kotlinx.coroutines.delay(400)
        }
    }
}
