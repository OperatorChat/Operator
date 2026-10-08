/*
 * Operator: chat for keypad phones.
 * Adapted from Chats by Fenn (https://github.com/fenleon/chats), MIT License,
 * Copyright (c) 2026 Fenn; see core-matrix/LICENSE-fenleon-chats.txt.
 * Modifications Copyright (C) 2026 Spanorak (https://www.reddit.com/user/Spanorak).
 *
 * As part of Operator this file is distributed under the GNU General Public
 * License, version 3 or (at your option) any later version, WITHOUT ANY
 * WARRANTY; see LICENSE for details.
 */
// Ported from fenleon/chats (https://github.com/fenleon/chats), MIT License,
// Copyright (c) 2026 Fenn. See core-matrix/LICENSE-fenleon-chats.txt.
package chat.operator.core.matrix

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Reboot recovery (docs/WAKE-COMPARISON.md #1). A rebooted LP3 has no
 * process, so the FGS (and the sync loop it holds) stays dead until the user
 * opens the app — messages stay silent. BOOT_COMPLETED fires after first
 * unlock, so credential-protected storage (the session store) is readable;
 * [ServerBootstrapProvider] has already run [MatrixRepository.init] by then,
 * and [ChatSyncService] re-applies the screen → cadence decision (a boot
 * with the screen dark → slow-sync rounds; screen on → long-poll).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!MatrixRepository.isSyncEnabled) {
            Log.d(TAG, "boot completed — sync disabled by user, not restarting")
            return
        }
        Log.i(TAG, "boot completed — restarting sync service")
        ChatSyncService.tryStart(context)
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
