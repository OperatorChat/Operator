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
package chat.operator.app

import android.app.Application
import chat.operator.app.notify.OperatorNotifier
import chat.operator.core.matrix.ChatNotifier
import chat.operator.core.matrix.MatrixRepository
import chat.operator.core.matrix.PushChannel
import chat.operator.core.push.NtfyPushChannel
import chat.operator.core.push.PushRouter

class OperatorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        chat.operator.app.ui.Appearance.applyNightMode(this)

        // Brand-dependent values come from the flavour's brand.xml (SPEC §4.4).
        MatrixRepository.deviceDisplayName = getString(R.string.app_name)
        NtfyPushChannel.serverUrl = getString(R.string.brand_default_push_server)
        NtfyPushChannel.applyServerOverride(this)

        // Plug the app's notification code and the push layer into the Matrix layer.
        OperatorNotifier.ensureChannels(this)
        ChatNotifier.impl = OperatorNotifier()
        PushChannel.impl = PushRouter

        // Restores any saved session and starts syncing.
        MatrixRepository.init(this)
    }
}
