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

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import chat.operator.app.R

class WelcomeScreen(host: ScreenHost) : Screen(host) {

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup): View =
        inflate(R.layout.screen_welcome, parent).also { v ->
            // Beeper stopped accepting its email-code sign-in from other apps on
            // 2 October 2026 (old-client-login-disabled), so the Beeper path is
            // standard password sign-in to matrix.beeper.com. The email-code
            // screens stay in the code base in case Beeper agrees to it later.
            v.findViewById<View>(R.id.sign_in_beeper).setOnClickListener { host.push(BeeperSignInScreen(host)) }
            v.findViewById<View>(R.id.other_server).setOnClickListener { host.push(OtherServerScreen(host)) }
        }

    override fun onShow() {
        host.setSoftKeys(null, host.string(R.string.softkey_exit))
        find<View>(R.id.sign_in_beeper).requestFocus()
    }
}

/** Shown while the saved session is being restored at start-up. */
class ConnectingScreen(host: ScreenHost) : Screen(host) {
    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup): View =
        inflate(R.layout.screen_connecting, parent)

    override fun onShow() = host.setSoftKeys(null, host.string(R.string.softkey_exit))
}
