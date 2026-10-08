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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.view.isVisible
import chat.operator.app.R
import chat.operator.core.matrix.MatrixRepository
import chat.operator.core.matrix.MatrixRepository.VerificationUi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * "Approve this phone" (SPEC §5.1 step 4). Phase 0 found approval from another
 * device unreliable with Beeper, so the recovery key is offered first and the
 * other-device route runs alongside: if the other device answers, the emoji
 * comparison appears here.
 */
class OtherDeviceApprovalScreen(host: ScreenHost) : Screen(host) {

    private lateinit var status: TextView
    private lateinit var emoji: TextView
    private lateinit var primary: Button
    private lateinit var secondary: Button
    private var job: Job? = null

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup): View =
        inflate(R.layout.screen_approve, parent).also { v ->
            status = v.findViewById(R.id.status)
            emoji = v.findViewById(R.id.emoji)
            primary = v.findViewById(R.id.primary)
            secondary = v.findViewById(R.id.secondary)
        }

    override fun onShow() {
        host.setSoftKeys(null, host.string(R.string.softkey_back))
        job?.cancel()
        job = host.scope.launch {
            if (MatrixRepository.e2eeState().verified) {
                host.pop()
                return@launch
            }
            // This route is only entered deliberately; ask the other devices now.
            runCatching { MatrixRepository.startDeviceVerification() }
            MatrixRepository.verification.collectLatest { render(it) }
        }
    }

    override fun onHide() {
        job?.cancel()
        job = null
    }

    override fun onBack(): Boolean = false

    private fun render(ui: VerificationUi) {
        emoji.isVisible = false
        when (ui) {
            is VerificationUi.Compare -> {
                status.text = host.string(R.string.approve_compare)
                emoji.text = ui.emoji.joinToString("\n")
                emoji.isVisible = true
                primary.setText(R.string.approve_match)
                primary.setOnClickListener { act("match") }
                secondary.setText(R.string.approve_no_match)
                secondary.setOnClickListener { act("no_match") }
                secondary.isVisible = true
                primary.requestFocus()
            }
            VerificationUi.Accept, VerificationUi.Start -> {
                // The other device answered; carry on without asking.
                status.text = host.string(R.string.approve_waiting_device)
                act("accept")
                showRecoveryKeyOption()
            }
            VerificationUi.Verifying -> {
                status.text = host.string(R.string.approve_verifying)
                primary.isVisible = false
                secondary.isVisible = false
            }
            VerificationUi.Done -> {
                status.text = host.string(R.string.approve_done)
                primary.isVisible = false
                secondary.isVisible = false
                host.scope.launch { host.pop() }
            }
            is VerificationUi.Error -> {
                status.text = host.string(R.string.approve_error)
                showRecoveryKeyOption(retry = true)
            }
            VerificationUi.Cancelled -> {
                status.text = host.string(R.string.approve_cancelled)
                showRecoveryKeyOption(retry = true)
            }
            VerificationUi.Idle, VerificationUi.Waiting -> {
                status.text = host.string(R.string.approve_waiting_device)
                showRecoveryKeyOption()
            }
        }
    }

    private fun showRecoveryKeyOption(retry: Boolean = false) {
        primary.isVisible = true
        primary.setText(R.string.approve_use_recovery_key)
        primary.setOnClickListener { host.push(RecoveryKeyScreen(host)) }
        secondary.isVisible = true
        secondary.setText(if (retry) R.string.approve_ask_again else R.string.approve_not_now)
        secondary.setOnClickListener {
            if (retry) host.scope.launch { MatrixRepository.startDeviceVerification() }
            else host.pop()
        }
        if (!primary.isFocused && !secondary.isFocused) primary.requestFocus()
    }

    private fun act(action: String) {
        host.scope.launch { MatrixRepository.verifyAction(action) }
    }
}
