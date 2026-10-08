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

import android.text.InputType
import chat.operator.app.R
import chat.operator.core.beeper.BeeperAuth
import kotlinx.coroutines.launch

/** Step 1 of the Beeper sign-in: the account email. */
class BeeperEmailScreen(host: ScreenHost) : FormScreen(host) {
    override val titleRes = R.string.beeper_email_title
    override val explanationRes = R.string.beeper_email_explanation
    override val hintRes = R.string.beeper_email_hint
    override val buttonRes = R.string.beeper_email_button
    override val inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
    override val initialValue: String? get() = BeeperAuth.pendingEmail(host.activity)

    override fun onSubmit(value: String) {
        if (!value.contains('@')) {
            showError(host.string(R.string.beeper_email_invalid))
            return
        }
        setBusy(host.string(R.string.beeper_email_sending))
        host.scope.launch {
            BeeperAuth.requestCode(host.activity, value)
                .onSuccess { host.push(BeeperCodeScreen(host)) ; setBusy(null) }
                .onFailure {
                    android.util.Log.w("BeeperSignIn", "request code failed: ${it.javaClass.simpleName}: ${it.message?.take(300)}")
                    showError(host.string(R.string.beeper_email_failed))
                }
        }
    }
}

/** Step 2: the code from the email. */
class BeeperCodeScreen(host: ScreenHost) : FormScreen(host) {
    override val titleRes = R.string.beeper_code_title
    override val explanationRes = R.string.beeper_code_explanation
    override val hintRes = R.string.beeper_code_hint
    override val buttonRes = R.string.beeper_code_button
    override val inputType = InputType.TYPE_CLASS_NUMBER

    override fun onSubmit(value: String) {
        if (value.length < 4) {
            showError(host.string(R.string.beeper_code_invalid))
            return
        }
        setBusy(host.string(R.string.beeper_code_signing_in))
        host.scope.launch {
            BeeperAuth.login(host.activity, value)
                .onSuccess { host.replaceAll(RecoveryKeyScreen(host)) }
                .onFailure {
                    android.util.Log.w("BeeperSignIn", "sign-in failed: ${it.javaClass.simpleName}: ${it.message?.take(300)}")
                    if (it is BeeperAuth.ClientNotAcceptedException) {
                        host.replaceAll(WelcomeScreen(host))
                        host.push(
                            OtherServerScreen(
                                host,
                                prefillServer = BeeperAuth.HOMESERVER,
                                explanation = host.string(R.string.beeper_password_fallback),
                            ),
                        )
                    } else {
                        showError(host.string(R.string.beeper_code_failed))
                    }
                }
        }
    }
}
